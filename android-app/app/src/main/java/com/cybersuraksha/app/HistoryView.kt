package com.cybersuraksha.app

import android.content.Context
import android.graphics.Color
import android.text.format.DateUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Renders a type-filtered slice of HistoryStore into a container — shared by the message,
 *  call and QR detector screens, each of which now shows only its own recent activity
 *  instead of one combined list on the home screen. */
object HistoryView {

    fun render(context: Context, container: LinearLayout, types: Set<String>, emptyText: String) {
        container.removeAllViews()
        val items = HistoryStore.all(context).filter { it.type in types }
        if (items.isEmpty()) {
            val tv = TextView(context)
            tv.text = emptyText
            tv.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            tv.textSize = 13f
            container.addView(tv)
            return
        }
        for (item in items) container.addView(row(context, item))
    }

    private fun row(context: Context, item: HistoryItem): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.background = ContextCompat.getDrawable(context, R.drawable.bg_card_white_round)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(context, 8)
        row.layoutParams = lp
        row.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        row.gravity = Gravity.TOP

        val (typeIcon, chipBg, chipColor) = when (item.type) {
            "sms" -> Triple(R.drawable.ic_mail, R.drawable.bg_card_round_green, R.color.primary)
            "call" -> Triple(R.drawable.ic_phone, R.drawable.bg_card_round_blue, R.color.accent_blue)
            "qr" -> Triple(R.drawable.ic_qr, R.drawable.bg_card_round_yellow, R.color.accent_amber)
            else -> Triple(R.drawable.ic_mail, R.drawable.bg_card_round_green, R.color.primary)
        }
        val chip = FrameLayout(context)
        val chipLp = LinearLayout.LayoutParams(dp(context, 32), dp(context, 32))
        chip.layoutParams = chipLp
        chip.background = ContextCompat.getDrawable(context, chipBg)
        val chipIcon = ImageView(context)
        val ciLp = FrameLayout.LayoutParams(dp(context, 16), dp(context, 16))
        ciLp.gravity = Gravity.CENTER
        chipIcon.layoutParams = ciLp
        chipIcon.setImageResource(typeIcon)
        chipIcon.setColorFilter(ContextCompat.getColor(context, chipColor))
        chip.addView(chipIcon)
        row.addView(chip)

        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        val colLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        colLp.marginStart = dp(context, 12)
        col.layoutParams = colLp

        val header = TextView(context)
        val who = when (item.type) {
            "sms" -> T.smsScannedLine(item.who)
            "call" -> if (item.text.isBlank()) T.callScreenedLine(item.who) else T.callDetectorTitle
            "qr" -> T.qrTitle
            else -> T.msgDetectorTitle
        }
        header.text = who
        header.setTextColor(ContextCompat.getColor(context, R.color.text_main))
        header.setTypeface(null, android.graphics.Typeface.BOLD)
        header.textSize = 13.5f
        col.addView(header)

        if (item.text.isNotBlank()) {
            val body = TextView(context)
            body.text = item.text.take(90)
            body.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            body.textSize = 12.5f
            val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            blp.topMargin = dp(context, 2)
            body.layoutParams = blp
            col.addView(body)
        }

        val footer = LinearLayout(context)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        val flp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        flp.topMargin = dp(context, 6)
        footer.layoutParams = flp

        val badge = TextView(context)
        val (bgCol, fgCol, label) = when (item.verdict) {
            "scam" -> Triple(R.color.danger_bg, R.color.danger, T.resultLikelyScam)
            "suspicious" -> Triple(R.color.warn_bg, R.color.warn, T.resultSuspicious)
            "unscreened" -> Triple(R.color.bg_blue_tint, R.color.accent_blue, T.callUnscreened)
            else -> Triple(R.color.safe_bg, R.color.safe, T.resultGenuine)
        }
        badge.text = label
        badge.setTextColor(ContextCompat.getColor(context, fgCol))
        badge.background = ContextCompat.getDrawable(context, R.drawable.bg_badge)?.mutate()
        (badge.background as android.graphics.drawable.GradientDrawable).setColor(ContextCompat.getColor(context, bgCol))
        badge.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3))
        badge.textSize = 11f
        badge.setTypeface(null, android.graphics.Typeface.BOLD)
        footer.addView(badge)

        val time = TextView(context)
        time.text = DateUtils.getRelativeTimeSpanString(item.time)
        time.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
        time.textSize = 11f
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.gravity = Gravity.END
        tlp.marginStart = dp(context, 8)
        time.layoutParams = tlp
        time.gravity = Gravity.END
        footer.addView(time)

        col.addView(footer)
        row.addView(col)
        return row
    }

    private fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
