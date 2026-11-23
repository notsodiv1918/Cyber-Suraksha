package com.cybersuraksha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val type: String, // "sms" | "call" | "manual"
    val who: String,  // sender number or phone number
    val text: String,
    val verdict: String,
    val time: Long,
)

/** Simple SharedPreferences-backed list (JSON array), capped at 30 entries — no Room/SQLite
 *  needed for a prototype's "recent activity" feed. */
object HistoryStore {
    private const val PREFS = "cs_history"
    private const val KEY = "items"
    private const val MAX = 30

    fun add(context: Context, item: HistoryItem) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val obj = JSONObject()
        obj.put("type", item.type)
        obj.put("who", item.who)
        obj.put("text", item.text)
        obj.put("verdict", item.verdict)
        obj.put("time", item.time)
        val newArr = JSONArray()
        newArr.put(obj)
        val limit = minOf(arr.length(), MAX - 1)
        for (i in 0 until limit) newArr.put(arr.getJSONObject(i))
        prefs.edit().putString(KEY, newArr.toString()).apply()
    }

    fun all(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val out = mutableListOf<HistoryItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(HistoryItem(o.getString("type"), o.getString("who"), o.getString("text"), o.getString("verdict"), o.getLong("time")))
        }
        return out
    }
}
