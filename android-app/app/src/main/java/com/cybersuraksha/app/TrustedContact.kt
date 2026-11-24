package com.cybersuraksha.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/** "भरोसेमंद व्यक्ति को कॉल करें" — one tap on the verdict screen calls a pre-set
 *  family member, for anyone who wants human reassurance rather than trusting an
 *  app alone. Stored locally only (SharedPreferences), never sent anywhere. */
object TrustedContact {
    private const val PREFS = "trusted_contact"
    private const val KEY_NUMBER = "number"
    private const val KEY_NAME = "name"

    fun getNumber(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NUMBER, "") ?: ""

    fun getName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""

    fun set(context: Context, name: String, number: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, name)
            .putString(KEY_NUMBER, number)
            .apply()
    }

    fun isSet(context: Context): Boolean = getNumber(context).isNotBlank()

    /** Opens the dialer pre-filled with the saved number — ACTION_DIAL needs no
     *  CALL_PHONE permission and lets the user confirm before actually calling. */
    fun dialIntent(context: Context): Intent? {
        val number = getNumber(context)
        if (number.isBlank()) return null
        return Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    }
}
