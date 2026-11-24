package com.cybersuraksha.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/** "सुनें" — read the verdict and reasons aloud, for anyone who struggles to read
 *  or can't read at all. Uses Android's built-in TextToSpeech (no new dependency). */
object TtsHelper {

    private var tts: TextToSpeech? = null
    private var ready = false

    private fun ensure(context: Context, onReady: () -> Unit) {
        if (ready) { onReady(); return }
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) onReady()
        }
    }

    fun speak(context: Context, text: String) {
        val locale = when (T.lang) {
            Lang.EN -> Locale.ENGLISH
            else -> Locale("hi", "IN")
        }
        speakIn(context, text, locale)
    }

    /** Speaks fixed text in a specific locale regardless of the app's current language toggle —
     *  used by the onboarding walkthrough, which is narrated in Chhattisgarhi text read by the
     *  Hindi voice (there is no distinct Chhattisgarhi speech-synthesis voice on Android, same
     *  precedent as Chhattisgarhi reusing the Hindi speech *recognizer* elsewhere in this app;
     *  Hindi TTS still reads Devanagari Chhattisgarhi text intelligibly, just not with perfectly
     *  native pronunciation) regardless of whatever language the rest of the app is set to. */
    fun speakIn(context: Context, text: String, locale: Locale) {
        ensure(context) {
            val engine = tts ?: return@ensure
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.ENGLISH
            }
            engine.stop()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cg-verdict")
        }
    }

    fun speakVerdictResult(context: Context, verdict: String, pct: Int, reasons: List<String>) {
        val verdictLine = when (verdict) {
            "scam" -> T.ttsVerdictScam(pct)
            "suspicious" -> T.ttsVerdictSuspicious(pct)
            else -> T.ttsVerdictGenuine
        }
        val body = if (reasons.isEmpty()) verdictLine else verdictLine + ". " + reasons.joinToString(". ")
        speak(context, body)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
