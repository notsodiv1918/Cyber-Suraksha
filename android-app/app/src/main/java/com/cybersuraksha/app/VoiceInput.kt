package com.cybersuraksha.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** "बोलकर जाँचें" — speak a message instead of typing it.
 *
 * **Real bug fixed here**: the first version launched the system's `RecognizerIntent
 * .ACTION_RECOGNIZE_SPEECH` as a separate *activity* (`startActivityForResult`) — that needs a
 * dedicated "voice search" app/activity to resolve the intent, and on a device without Google's
 * voice search UI installed/enabled (or with it disabled), there's nothing to resolve it, so the
 * system shows "Voice search isn't available" and nothing happens — confirmed to fail that way on
 * a real test. `LiveCallWarningService` already proved the fix for this exact problem: use
 * `SpeechRecognizer.createSpeechRecognizer()` directly and drive it with a `RecognitionListener` —
 * that only needs the on-device speech recognition *service*, not a separate app with its own UI,
 * and keeps everything in-app (our own mic button shows the "listening" state, not a system
 * overlay). This class is the one-shot ("speak, get text back, stop") sibling of that same
 * mechanism, with a runtime `RECORD_AUDIO` permission request added since this flow isn't gated
 * behind the More-screen toggle like the live-call feature is.
 *
 * Chhattisgarhi has no distinct on-device speech-recognition locale — same precedent as the rest
 * of this app (e.g. `Classifier`'s category labels), Chhattisgarhi mode listens using the Hindi
 * (`hi-IN`) recognizer, which still correctly transcribes Devanagari speech either way.
 *
 * **Second real bug, also caught via a live on-device test**: `EXTRA_LANGUAGE` needs a BCP-47 tag
 * like `"hi-IN"` — an earlier version built it from `Locale("hi","IN").toString()`, which actually
 * returns `"hi_IN"` (underscore, Java's legacy format, not BCP-47), so the recognizer never matched
 * an installed language. Fixed by using the literal tags directly, same as `LiveCallWarningService`
 * already does correctly. Separately, `EXTRA_PREFER_OFFLINE=true` can fail hard with
 * `ERROR_LANGUAGE_UNAVAILABLE` on a device with no offline pack for that language, instead of
 * silently falling back to network recognition as Android's docs suggest it should — confirmed on
 * a real device (`SodaSpeechRecognizer: Failed to get language pack ... error 13`). Fixed by
 * retrying once without `EXTRA_PREFER_OFFLINE` whenever that specific error occurs.
 */
class VoiceInput(private val activity: ComponentActivity) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var pendingOnResult: ((String) -> Unit)? = null
    private var pendingOnListening: ((Boolean) -> Unit)? = null

    private val permissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                beginListening(pendingOnResult ?: return@registerForActivityResult, pendingOnListening ?: {})
            } else {
                Toast.makeText(activity, T.voiceNeedsMicPerm, Toast.LENGTH_LONG).show()
                pendingOnListening?.invoke(false)
            }
        }

    fun start(onResult: (String) -> Unit, onListening: (Boolean) -> Unit = {}) {
        pendingOnResult = onResult
        pendingOnListening = onListening
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginListening(onResult, onListening)
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stop() {
        onListeningChange(false)
        recognizer?.let {
            try { it.stopListening(); it.destroy() } catch (e: Exception) {}
        }
        recognizer = null
    }

    private var currentOnListening: ((Boolean) -> Unit)? = null
    private fun onListeningChange(listening: Boolean) {
        currentOnListening?.invoke(listening)
    }

    private fun beginListening(onResult: (String) -> Unit, onListening: (Boolean) -> Unit, preferOffline: Boolean = true) {
        currentOnListening = onListening
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            Toast.makeText(activity, T.voiceUnavailable, Toast.LENGTH_LONG).show()
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListeningChange(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onListeningChange(false) }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onResults(results: Bundle?) {
                onListeningChange(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) onResult(text)
                r.destroy()
                if (recognizer === r) recognizer = null
            }

            override fun onError(error: Int) {
                onListeningChange(false)
                android.util.Log.d("CyberSuraksha", "VoiceInput onError code=$error preferOffline=$preferOffline")
                // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT mean the recognizer genuinely listened —
                // retrying instantly would just miss the same moment, so only treat every OTHER
                // error (device has no offline language pack, server/client wiring issues, etc.)
                // as a reason to fall back to the network recognizer once. Confirmed on-device
                // that a missing offline pack can surface through this vendor's speech service as
                // more than one error code, not just the documented ERROR_LANGUAGE_UNAVAILABLE —
                // so this fallback no longer pattern-matches a specific code.
                val genuinelyHeardNothing = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (preferOffline && !genuinelyHeardNothing && error != SpeechRecognizer.ERROR_CLIENT) {
                    r.destroy()
                    if (recognizer === r) recognizer = null
                    // Recreating a SpeechRecognizer synchronously inside the failing one's own
                    // onError callback raced with the service unbind on-device (the retry itself
                    // immediately failed with ERROR_SERVER_DISCONNECTED) — a short delay, the same
                    // pattern LiveCallWarningService already uses for its own restart loop, gives
                    // the old binding time to actually tear down first.
                    mainHandler.postDelayed({ beginListening(onResult, onListening, preferOffline = false) }, 300)
                    return
                }
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    Toast.makeText(activity, T.voiceUnavailable, Toast.LENGTH_SHORT).show()
                }
                r.destroy()
                if (recognizer === r) recognizer = null
            }
        })
        val languageTag = if (T.lang == Lang.EN) "en-IN" else "hi-IN"
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
        }
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            onListeningChange(false)
            Toast.makeText(activity, T.voiceUnavailable, Toast.LENGTH_LONG).show()
        }
    }

    fun destroy() {
        recognizer?.let { try { it.destroy() } catch (e: Exception) {} }
        recognizer = null
    }
}
