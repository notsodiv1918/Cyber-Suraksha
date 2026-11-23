package com.cybersuraksha.app

import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Wires the "Share with district (optional)" button shown on a scam/suspicious Result
 *  screen — picks a district via popup menu, then opt-in POSTs the text through AlertApi. */
object ShareReport {

    fun wire(activity: AppCompatActivity, anchor: View, statusText: TextView, text: () -> String, verdict: () -> String, source: String) {
        anchor.setOnClickListener {
            val districts = Districts.load(activity)
            if (districts.isEmpty()) {
                statusText.text = T.shareError
                return@setOnClickListener
            }
            val popup = PopupMenu(activity, anchor)
            districts.forEachIndexed { idx, d ->
                popup.menu.add(0, idx, idx, if (T.lang == Lang.EN) d.en else d.hi)
            }
            popup.setOnMenuItemClickListener { item ->
                val district = districts[item.itemId]
                statusText.setTextColor(ContextCompat.getColor(activity, R.color.text_muted))
                statusText.text = T.shareSending
                AlertApi.submitReport(text(), district.en, T.lang.name.lowercase(), verdict(), source) { ok ->
                    if (ok) {
                        statusText.setTextColor(ContextCompat.getColor(activity, R.color.safe))
                        statusText.text = T.shareSent
                    } else {
                        statusText.setTextColor(ContextCompat.getColor(activity, R.color.danger))
                        statusText.text = T.shareError
                    }
                }
                true
            }
            popup.show()
        }
    }
}
