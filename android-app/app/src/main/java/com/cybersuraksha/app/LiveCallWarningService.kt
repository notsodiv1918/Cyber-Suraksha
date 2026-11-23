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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Opt-in "live call warning (beta)": while a call is active, listens through the normal
 * microphone (not the restricted in-call audio stream — Android blocks that for all apps on
 * modern SDKs) and runs Android's on-device-preferred SpeechRecognizer continuously, feeding
 * each recognized chunk through the same Classifier used everywhere else in the app. If a
 * chunk reads as a scam, it fires an urgent notification immediately, while the call is still
 * going — not after.
 *
 * Honest limits, also stated in the More screen's toggle copy:
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
    }

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastWarnedNormalized: String = ""
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopSelf()
            }
            else -> startForegroundAndListen()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundAndListen() {
        if (running) return
        Classifier.load(this)
        ensureForegroundChannel()
        val notification = NotificationCompat.Builder(this, FG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(T.liveWarningActiveTitle)
            .setContentText(T.liveWarningActiveBody)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FG_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(FG_NOTIF_ID, notification)
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Notifier.notifyVerdict(this, T.liveWarningUnavailableTitle, T.liveWarningUnavailableBody, "suspicious")
            stopSelf()
            return
        }

        running = true
        lastWarnedNormalized = ""
        mainHandler.post { startRecognizer() }
    }

    private fun startRecognizer() {
        if (!running) return
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                handleResults(partialResults)
            }

            override fun onResults(results: Bundle?) {
                handleResults(results)
                restartSoon()
            }

            override fun onError(error: Int) {
                // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT are routine in a continuous loop —
                // just restart. A short delay avoids a tight error loop on persistent failures.
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
            restartSoon()
        }
    }

    private fun restartSoon() {
        if (!running) return
        mainHandler.postDelayed({ startRecognizer() }, 400)
    }

    private fun handleResults(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val text = matches.firstOrNull()?.trim() ?: return
        if (text.length < 8) return // too short to mean anything
        val normalized = text.lowercase()
        if (normalized == lastWarnedNormalized) return

        val result = Classifier.classify(text)
        if (result.verdict == "scam" || (result.verdict == "suspicious" && result.hits.isNotEmpty())) {
            lastWarnedNormalized = normalized
            val typeLine = result.scamType?.let { " · " + T.categoryLabel(it) } ?: ""
            Notifier.notifyVerdict(
                this,
                T.liveWarningAlertTitle,
                text.take(100) + typeLine,
                result.verdict
            )
            HistoryStore.add(this, HistoryItem("call", "-", text, result.verdict, System.currentTimeMillis()))
        }
    }

    private fun stopListening() {
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.let {
            try { it.stopListening(); it.destroy() } catch (e: Exception) {}
        }
        recognizer = null
    }

    private fun ensureForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(FG_CHANNEL_ID) == null) {
                val ch = NotificationChannel(FG_CHANNEL_ID, "Live call warning", NotificationManager.IMPORTANCE_LOW)
                ch.description = "Shows while live call warning (beta) is actively listening"
                mgr.createNotificationChannel(ch)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
