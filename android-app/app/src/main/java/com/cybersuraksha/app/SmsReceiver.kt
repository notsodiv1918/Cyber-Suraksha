package com.cybersuraksha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/** Receives every incoming SMS (RECEIVE_SMS permission) and classifies it immediately,
 *  entirely on-device — mirrors the website's message detector, just triggered automatically
 *  instead of pasted in. Logs at every step (tag "CyberSuraksha") so a silent failure on a
 *  real device — OEM battery/autostart restrictions, a permission gap, a parse error — shows
 *  up in logcat instead of just vanishing. */
class SmsReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "CyberSuraksha" }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "SmsReceiver.onReceive fired, action=${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            Classifier.load(context)

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                Log.w(TAG, "SMS_RECEIVED broadcast had no messages in it")
                return
            }

            val sender = messages[0].originatingAddress ?: "unknown"
            val body = messages.joinToString("") { it.messageBody ?: "" }
            Log.d(TAG, "Incoming SMS from $sender, ${body.length} chars")
            if (body.isBlank()) return

            val result = Classifier.classify(body)
            Log.d(TAG, "Classified as ${result.verdict} (${result.probability})")
            HistoryStore.add(context, HistoryItem("sms", sender, body, result.verdict, System.currentTimeMillis()))

            val title = when (result.verdict) {
                "scam" -> "⚠ Likely scam SMS from $sender"
                "suspicious" -> "Suspicious SMS from $sender"
                else -> "SMS from $sender"
            }
            Notifier.notifyVerdict(context, title, body.take(140), result.verdict)
        } catch (e: Exception) {
            Log.e(TAG, "SmsReceiver failed", e)
        }
    }
}
