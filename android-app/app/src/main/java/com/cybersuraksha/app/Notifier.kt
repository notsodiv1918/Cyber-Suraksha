package com.cybersuraksha.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CHANNEL_ID = "scam_alerts"
    private var nextId = 1000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, "Scam alerts", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "Alerts when an incoming SMS or call looks like a scam"
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun notifyVerdict(context: Context, title: String, body: String, verdict: String) {
        if (verdict == "genuine") return // don't interrupt the user for clean messages
        ensureChannel(context)
        val icon = android.R.drawable.ic_dialog_alert
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(nextId++, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip, the in-app history still records it
        }
    }
}
