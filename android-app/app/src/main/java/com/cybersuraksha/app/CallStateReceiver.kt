package com.cybersuraksha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/** Watches the system's call-state broadcast to start/stop the opt-in live-call-warning
 *  foreground service. Only acts when the user has turned the feature on in More > Protection
 *  status — and only fires at all when this app holds READ_PHONE_STATE at runtime: without it
 *  the OS silently never delivers this broadcast (a real bug here once — the toggle only asked
 *  for RECORD_AUDIO, not READ_PHONE_STATE, so nothing ever ran on a real call; fixed in
 *  MoreActivity to request both). Logs at every step (tag "CyberSuraksha") for the same reason
 *  SmsReceiver does — so a silent OEM/permission failure shows up in logcat, not just vanishes. */
class CallStateReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "CyberSuraksha" }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "CallStateReceiver.onReceive fired, action=${intent.action}, state=${intent.getStringExtra(TelephonyManager.EXTRA_STATE)}")
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        try {
            if (!LiveCallWarning.isEnabled(context)) {
                Log.d(TAG, "Live call warning is off in settings, ignoring")
                return
            }

            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d(TAG, "Call OFFHOOK — starting LiveCallWarningService")
                    val svc = Intent(context, LiveCallWarningService::class.java).setAction(LiveCallWarningService.ACTION_START)
                    ContextCompat.startForegroundService(context, svc)
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d(TAG, "Call IDLE — stopping LiveCallWarningService")
                    context.startService(Intent(context, LiveCallWarningService::class.java).setAction(LiveCallWarningService.ACTION_STOP))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CallStateReceiver failed", e)
        }
    }
}
