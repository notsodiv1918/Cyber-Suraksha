package com.cybersuraksha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Receives every incoming SMS (RECEIVE_SMS permission) and classifies it immediately,
 *  entirely on-device — mirrors the website's message detector, just triggered automatically
 *  instead of pasted in. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        Classifier.load(context)

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val result = Classifier.classify(body)
        HistoryStore.add(context, HistoryItem("sms", sender, body, result.verdict, System.currentTimeMillis()))

        val title = when (result.verdict) {
            "scam" -> "⚠ Likely scam SMS from $sender"
            "suspicious" -> "Suspicious SMS from $sender"
            else -> "SMS from $sender"
        }
        Notifier.notifyVerdict(context, title, body.take(140), result.verdict)
    }
}
