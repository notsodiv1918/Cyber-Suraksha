package com.cybersuraksha.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Opt-in "live call warning (beta)".
 *
 * **Real bug fixed here, found via an actual on-device call test**: the first version started
 * this service from a *manifest-declared* BroadcastReceiver listening for
 * `ACTION_PHONE_STATE_CHANGED`. That silently never fires on Android 8+ (API 26+, which this app
 * targets) — manifest-registered receivers only get implicit broadcasts that are on a short OS
 * exemption list, and `PHONE_STATE` is not on it. Confirmed by a real call producing *zero*
 * `CyberSuraksha`-tagged log lines at all, not even the unconditional one the old receiver logged
 * on every invocation — proof the receiver was never invoked, not just that something inside it
 * failed. The fix: this service is now started directly (by MoreActivity) the moment the toggle
 * is switched on — not waiting for a call — and registers a `PhoneStateListener` **dynamically**
 * via `TelephonyManager.listen()` from inside itself. A live, already-registered listener like
 * this is a direct callback subscription to the telephony framework, not an implicit broadcast,
 * so it isn't subject to that restriction — this is the same reliable mechanism real call-
 * management apps use. The honest cost: a persistent low-priority "watching for calls"
 * notification stays up the whole time the toggle is on, not just during a call — stated in the
 * toggle's own description.
 *
 * Once a call goes active, listens through the normal microphone (not the restricted in-call
 * audio stream — Android blocks that for all apps on modern SDKs) and runs Android's on-device-
 * preferred SpeechRecognizer continuously, feeding each recognized chunk through the same
 * Classifier used everywhere else in the app. If a chunk reads as a scam, it fires an urgent
 * notification immediately, while the call is still going — not after.
 *
 * Other honest limits, also stated in the More screen's toggle copy:
 *  - Works well mainly on speakerphone; held to the ear, the mic mostly hears the user's own
 *    voice, not the caller's.
 *  - SpeechRecognizer prefers on-device/offline recognition when the device has an offline
 *    language pack installed, but silently falls back to network recognition otherwise — this
 *    is the one feature in the app that isn't guaranteed fully offline.
 */
class LiveCallWarningService : Service() {

    companion object {
        const val ACTION_START = "com.cybersuraksha.app.action.START_LIVE_WARNING"
        const val ACTION_STOP = "com.cybersuraksha.app.action.STOP_LIVE_WARNING"
        private const val FG_CHANNEL_ID = "live_call_warning_fg"
        private const val FG_NOTIF_ID = 2001
        private const val TAG = "CyberSuraksha"
    }

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastWarnedNormalized: String = ""
    private var listening = false // actively running the SpeechRecognizer loop (call is active)
    private var watching = false  // the PhoneStateListener is registered (toggle is on)
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LiveCallWarningService.onStartCommand action=${intent?.action}")
        try {
            when (intent?.action) {
                ACTION_STOP -> stopWatching()
                else -> startWatching()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand failed", e)
        }
        return START_STICKY
    }

    private fun startWatching() {
        if (watching) {
            Log.d(TAG, "startWatching: already watching, ignoring")
            return
        }
        Classifier.load(this)
        ensureForegroundChannel()
        showNotification(active = false)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Missing RECORD_AUDIO or READ_PHONE_STATE, stopping")
            stopWatching()
            return
        }

        watching = true
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager = tm
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                val name = when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                    TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                    else -> "UNKNOWN($state)"
                }
                Log.d(TAG, "onCallStateChanged -> $name")
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> startListeningLoop()
                    TelephonyManager.CALL_STATE_IDLE -> stopListeningLoop()
                }
            }
        }
        phoneStateListener = listener
        try {
            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.i(TAG, "PhoneStateListener registered — now watching for calls")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PhoneStateListener", e)
            stopWatching()
        }
    }

    private fun stopWatching() {
        Log.d(TAG, "stopWatching")
        stopListeningLoop()
        watching = false
        try {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister PhoneStateListener", e)
        }
        phoneStateListener = null
        telephonyManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startListeningLoop() {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer.isRecognitionAvailable() = false on this device")
            Notifier.notifyVerdict(this, T.liveWarningUnavailableTitle, T.liveWarningUnavailableBody, "suspicious")
            return
        }
        Log.i(TAG, "Call active — starting live speech recognition")
        listening = true
        lastWarnedNormalized = ""
        showNotification(active = true)
        mainHandler.post { startRecognizer() }
    }

    private fun stopListeningLoop() {
        if (!listening) return
        Log.d(TAG, "Call ended — stopping live speech recognition")
        listening = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.let {
            try { it.stopListening(); it.destroy() } catch (e: Exception) {}
        }
        recognizer = null
        if (watching) showNotification(active = false)
    }

    private fun startRecognizer() {
        if (!listening) return
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "onReadyForSpeech") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.d(TAG, "onEndOfSpeech") }
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                handleResults(partialResults, partial = true)
            }

            override fun onResults(results: Bundle?) {
                handleResults(results, partial = false)
                restartSoon()
            }

            override fun onError(error: Int) {
                // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT are routine in a continuous loop —
                // just restart. A short delay avoids a tight error loop on persistent failures.
                Log.d(TAG, "onError code=$error (${errorName(error)})")
                restartSoon()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (T.lang == Lang.EN) "en-IN" else "hi-IN")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening threw", e)
            restartSoon()
        }
    }

    private fun errorName(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        else -> "UNKNOWN"
    }

    private fun restartSoon() {
        if (!listening) return
        mainHandler.postDelayed({ startRecognizer() }, 400)
    }

    private fun handleResults(bundle: Bundle?, partial: Boolean) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim()
        Log.d(TAG, "handleResults partial=$partial text=${text ?: "(none)"}")
        if (text.isNullOrEmpty()) return
        if (text.length < 8) return // too short to mean anything
        val normalized = text.lowercase()
        if (normalized == lastWarnedNormalized) return

        val result = Classifier.classify(text)
        Log.d(TAG, "Classified '$text' as ${result.verdict} (${result.probability})")
        if (result.verdict == "scam" || (result.verdict == "suspicious" && result.hits.isNotEmpty())) {
            lastWarnedNormalized = normalized
            val typeLine = result.scamType?.let { " · " + T.categoryLabel(it) } ?: ""
            Log.i(TAG, "SCAM DETECTED during live call — firing warning notification")
            Notifier.notifyVerdict(
                this,
                T.liveWarningAlertTitle,
                text.take(100) + typeLine,
                result.verdict
            )
            HistoryStore.add(this, HistoryItem("call", "-", text, result.verdict, System.currentTimeMillis()))
        }
    }

    private fun showNotification(active: Boolean) {
        val notification = NotificationCompat.Builder(this, FG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (active) T.liveWarningActiveTitle else T.liveWarningWatchingTitle)
            .setContentText(if (active) T.liveWarningActiveBody else T.liveWarningWatchingBody)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FG_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(FG_NOTIF_ID, notification)
        }
    }

    private fun ensureForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(FG_CHANNEL_ID) == null) {
                val ch = NotificationChannel(FG_CHANNEL_ID, "Live call warning", NotificationManager.IMPORTANCE_LOW)
                ch.description = "Shows while live call warning (beta) is on, watching for calls"
                mgr.createNotificationChannel(ch)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListeningLoop()
        try {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
        } catch (e: Exception) {}
    }
}
