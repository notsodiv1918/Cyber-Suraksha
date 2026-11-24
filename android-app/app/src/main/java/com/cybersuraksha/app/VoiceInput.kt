package com.cybersuraksha.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

/** "बोलकर जाँचें" — speak a message instead of typing it. Reuses the system speech
 *  recognition UI (same underlying on-device/offline-capable engine the live-call
 *  warning feature already depends on) rather than a custom SpeechRecognizer loop,
 *  since this is a single one-shot utterance, not continuous listening. */
object VoiceInput {

    fun register(activity: Activity, onResult: (String) -> Unit): ActivityResultLauncher<Intent> {
        val caller = activity as androidx.activity.ComponentActivity
        return caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text)
            }
        }
    }

    fun launch(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        val locale = when (T.lang) {
            Lang.EN -> Locale.ENGLISH
            else -> Locale("hi", "IN")
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, T.voicePrompt)
        }
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, T.voiceUnavailable, Toast.LENGTH_LONG).show()
        }
    }
}
