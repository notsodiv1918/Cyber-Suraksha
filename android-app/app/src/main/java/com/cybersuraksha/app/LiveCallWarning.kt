package com.cybersuraksha.app

import android.content.Context

/** Shared on/off flag for the opt-in "live call warning (beta)" feature — toggled from
 *  MoreActivity, read by CallStateReceiver. See LiveCallWarningService for how it actually
 *  listens and warns. */
object LiveCallWarning {
    private const val PREFS = "cs_live_call_warning"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
