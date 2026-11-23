package com.cybersuraksha.app

import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Shared "Message / Call / QR" tab strip used at the top of all three detector screens, plus the
 * swipe-to-switch gesture and slide transition that makes moving between them feel like swiping
 * through photos in a gallery, instead of the default abrupt activity jump-cut.
 */
object DetectTabs {

    enum class Tab(val index: Int) { MESSAGE(0), CALL(1), QR(2) }

    private fun targetFor(tab: Tab): Class<out AppCompatActivity> = when (tab) {
        Tab.MESSAGE -> MessageDetectorActivity::class.java
        Tab.CALL -> CallDetectorActivity::class.java
        Tab.QR -> QrCheckActivity::class.java
    }

    /** Navigates with a left/right slide depending on whether we're moving forward or back
     *  through the Message -> Call -> QR order. */
    fun go(activity: AppCompatActivity, from: Tab, to: Tab) {
        if (from == to) return
        val intent = Intent(activity, targetFor(to))
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        activity.startActivity(intent)
        if (to.index > from.index) {
            activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    /** Builds the 3-pill tab row (Message | Call | QR) and wires taps to [go]. */
    fun buildRow(activity: AppCompatActivity, row: LinearLayout, current: Tab) {
        row.removeAllViews()
        val labels = listOf(Tab.MESSAGE to T.detectMessageTitle, Tab.CALL to T.detectCallTitle, Tab.QR to T.detectQrTitle)
        labels.forEachIndexed { i, (tab, label) ->
            val pill = TextView(activity)
            pill.text = label
            pill.textSize = 12.5f
            pill.gravity = android.view.Gravity.CENTER
            pill.setPadding(dp(activity, 10), dp(activity, 9), dp(activity, 10), dp(activity, 9))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.marginStart = dp(activity, 6)
            pill.layoutParams = lp
            val active = tab == current
            pill.background = ContextCompat.getDrawable(activity, if (active) R.drawable.bg_pill_selected else R.drawable.bg_pill_outline)
            pill.setTextColor(ContextCompat.getColor(activity, if (active) R.color.white else R.color.text_main))
            pill.setOnClickListener { go(activity, current, tab) }
            row.addView(pill)
        }
    }

    /** Attaches a fling-based swipe gesture across the whole screen (non-consuming — never
     *  blocks normal scrolling/typing — it only watches passively via dispatchTouchEvent and
     *  fires a tab switch when a clearly horizontal, fast-enough fling is seen). Call from the
     *  activity's own dispatchTouchEvent override. */
    fun gestureDetector(activity: AppCompatActivity, current: Tab): GestureDetector {
        return GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > dp(activity, 80) && abs(dx) > abs(dy) * 1.8f && abs(velocityX) > 500) {
                    val next = if (dx < 0) current.index + 1 else current.index - 1
                    val tabs = Tab.values()
                    if (next in tabs.indices) go(activity, current, tabs[next])
                    return true
                }
                return false
            }
        })
    }

    private fun dp(activity: AppCompatActivity, v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
}
