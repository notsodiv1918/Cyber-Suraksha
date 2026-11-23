package com.cybersuraksha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Restarts the live-call-warning watcher after a device reboot, if the user had it on.
 *  Unlike PHONE_STATE, BOOT_COMPLETED IS on Android's implicit-broadcast exemption list, so a
 *  manifest-declared receiver for it reliably fires — this one is safe to rely on. */
class BootReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "CyberSuraksha" }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            if (LiveCallWarning.isEnabled(context)) {
                Log.d(TAG, "Boot completed, live call warning was on — restarting watcher")
                LiveCallWarning.setEnabled(context, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "BootReceiver failed", e)
        }
    }
}
