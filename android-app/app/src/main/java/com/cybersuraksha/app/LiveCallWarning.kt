package com.cybersuraksha.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Shared on/off flag for the opt-in "live call warning (beta)" feature — toggled from
 *  MoreActivity. Turning it on/off here also starts/stops LiveCallWarningService directly
 *  (the service itself registers a dynamic PhoneStateListener once running) so every call site
 *  that flips this flag keeps the actual watcher in sync with it, in one place. */
object LiveCallWarning {
    private const val PREFS = "cs_live_call_warning"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
        val intent = Intent(context, LiveCallWarningService::class.java)
            .setAction(if (enabled) LiveCallWarningService.ACTION_START else LiveCallWarningService.ACTION_STOP)
        if (enabled) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}
