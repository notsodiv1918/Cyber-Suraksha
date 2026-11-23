package com.cybersuraksha.app

import android.content.Context
import org.json.JSONObject

data class District(val hi: String, val en: String)

/** Loads the real Chhattisgarh district list bundled in assets/dashboard.json — shared by
 *  the Scam Trends screen and the opt-in "share this report" flow, so both use the exact
 *  same district names (keyed by English name server-side, see AlertApi). */
object Districts {
    private var cache: List<District>? = null

    fun load(context: Context): List<District> {
        cache?.let { return it }
        return try {
            val text = context.assets.open("dashboard.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(text).optJSONArray("districts") ?: return emptyList()
            val list = mutableListOf<District>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(District(o.getString("hi"), o.getString("en")))
            }
            cache = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
