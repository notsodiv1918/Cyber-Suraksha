package com.cybersuraksha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** Watches the system's call-state broadcast (works for any app with READ_PHONE_STATE, no
 *  call-screening role needed) to start/stop the opt-in live-call-warning foreground service.
 *  Only acts when the user has turned the feature on in More > Protection status. */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (!LiveCallWarning.isEnabled(context)) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val svc = Intent(context, LiveCallWarningService::class.java).setAction(LiveCallWarningService.ACTION_START)
                ContextCompat.startForegroundService(context, svc)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                context.startService(Intent(context, LiveCallWarningService::class.java).setAction(LiveCallWarningService.ACTION_STOP))
            }
        }
    }
}
