package com.cybersuraksha.app

import android.content.Intent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Wires the shared `nav_bottom.xml` bottom navigation bar (Home / Detect / Learn / Report /
 *  More) included at the bottom of every top-level screen. Each tab navigates to its target
 *  Activity with REORDER_TO_FRONT so repeated taps don't pile up duplicate instances. */
object NavBar {
    enum class Tab { HOME, DETECT, LEARN, REPORT, MORE, NONE }

    fun wire(activity: AppCompatActivity, navRoot: LinearLayout, current: Tab) {
        val home = navRoot.findViewById<LinearLayout>(R.id.navHome)
        val detect = navRoot.findViewById<LinearLayout>(R.id.navDetect)
        val learn = navRoot.findViewById<LinearLayout>(R.id.navLearn)
        val report = navRoot.findViewById<LinearLayout>(R.id.navReport)
        val more = navRoot.findViewById<LinearLayout>(R.id.navMore)

        setItem(activity, home, T.navHome, current == Tab.HOME) { go(activity, MainActivity::class.java) }
        setItem(activity, detect, T.navDetect, current == Tab.DETECT) { go(activity, MessageDetectorActivity::class.java) }
        setItem(activity, learn, T.navLearn, current == Tab.LEARN) { go(activity, LearnActivity::class.java) }
        setItem(activity, report, T.navReport, current == Tab.REPORT) { go(activity, ReportActivity::class.java) }
        setItem(activity, more, T.navMore, current == Tab.MORE) { go(activity, MoreActivity::class.java) }
    }

    private fun go(activity: AppCompatActivity, target: Class<*>) {
        if (activity.javaClass == target) return
        val intent = Intent(activity, target)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        activity.startActivity(intent)
    }

    private fun setItem(activity: AppCompatActivity, item: LinearLayout, label: String, active: Boolean, onClick: () -> Unit) {
        val iconView = item.getChildAt(0) as ImageView
        val labelView = item.getChildAt(1) as TextView
        labelView.text = label
        val color = ContextCompat.getColor(activity, if (active) R.color.primary else R.color.nav_inactive)
        iconView.setColorFilter(color)
        labelView.setTextColor(color)
        item.setOnClickListener { onClick() }
    }
}
