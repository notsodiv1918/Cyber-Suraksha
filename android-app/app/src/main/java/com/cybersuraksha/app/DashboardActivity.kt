package com.cybersuraksha.app

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cybersuraksha.app.databinding.ActivityDashboardBinding
import org.json.JSONObject
import kotlin.math.roundToInt

private data class ScamType(val hi: String, val en: String, val pct: Double)

class DashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityDashboardBinding
    private var data: JSONObject? = null
    private var districtNames: List<Pair<String, String>> = emptyList() // (hi, en)
    private var selectedIndex: Int = -1 // -1 = all Chhattisgarh

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)

        loadData()
        b.backBtn.setOnClickListener { finish() }
        b.langBtn.setOnClickListener { T.next(); applyLanguage() }
        b.districtPicker.setOnClickListener { showDistrictMenu() }

        NavBar.wire(this, b.navBottom.root, NavBar.Tab.NONE)
        applyLanguage()
    }

    private fun loadData() {
        try {
            val text = assets.open("dashboard.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            data = JSONObject(text)
            val districts = data?.optJSONArray("districts")
            val names = mutableListOf<Pair<String, String>>()
            if (districts != null) {
                for (i in 0 until districts.length()) {
                    val d = districts.getJSONObject(i)
                    names.add(d.getString("hi") to d.getString("en"))
                }
            }
            districtNames = names
        } catch (e: Exception) {
            data = null
        }
    }

    private fun showDistrictMenu() {
        val popup = PopupMenu(this, b.districtPicker)
        popup.menu.add(0, -1, 0, T.trendsRegionAll)
        districtNames.forEachIndexed { idx, (hi, en) ->
            popup.menu.add(0, idx, idx + 1, if (T.lang == Lang.EN) en else hi)
        }
        popup.setOnMenuItemClickListener { item ->
            selectedIndex = item.itemId
            applyLanguage()
            true
        }
        popup.show()
    }

    private fun applyLanguage() {
        b.langBtn.text = T.label()
        b.titleText.text = T.trendsTitle
        b.trendsHeadingText.text = T.trendsHeading
        b.districtText.text = if (selectedIndex == -1) T.trendsRegionAll
        else districtNames.getOrNull(selectedIndex)?.let { if (T.lang == Lang.EN) it.second else it.first } ?: T.trendsRegionAll
        renderScamTypes()
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.NONE)
    }

    private fun renderScamTypes() {
        b.scamTypeList.removeAllViews()
        val d = data ?: return
        val total = d.optInt("total_sample", 0)
        val types: List<ScamType>

        if (selectedIndex == -1) {
            val arr = d.optJSONArray("scam_types") ?: return
            val list = mutableListOf<ScamType>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(ScamType(o.getString("hi"), o.getString("en"), o.getDouble("pct")))
            }
            types = list.sortedByDescending { it.pct }.take(5)
            b.trendsNoteText.text = T.trendsNote(total)
        } else {
            val districts = d.optJSONArray("districts")
            val district = districts?.optJSONObject(selectedIndex)
            val scamTypesArr = d.optJSONArray("scam_types")
            val breakdown = district?.optJSONObject("breakdown")
            val dTotal = district?.optInt("total", 0) ?: 0
            val list = mutableListOf<ScamType>()
            if (breakdown != null && scamTypesArr != null && dTotal > 0) {
                for (i in 0 until scamTypesArr.length()) {
                    val o = scamTypesArr.getJSONObject(i)
                    val code = o.getString("code")
                    val count = breakdown.optInt(code, 0)
                    if (count > 0) list.add(ScamType(o.getString("hi"), o.getString("en"), count * 100.0 / dTotal))
                }
            }
            types = list.sortedByDescending { it.pct }.take(5)
            b.trendsNoteText.text = T.trendsNote(dTotal)
        }

        types.forEach { b.scamTypeList.addView(typeRow(it)) }
    }

    private fun typeRow(type: ScamType): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(14)
        col.layoutParams = lp

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        val name = TextView(this)
        name.text = if (T.lang == Lang.EN) type.en else type.hi
        name.setTextColor(ContextCompat.getColor(this, R.color.text_main))
        name.textSize = 13.5f
        val nameLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        name.layoutParams = nameLp
        header.addView(name)

        val pctText = TextView(this)
        val pctRounded = (type.pct * 10).roundToInt() / 10.0
        pctText.text = "$pctRounded%"
        pctText.setTextColor(ContextCompat.getColor(this, R.color.primary))
        pctText.setTypeface(null, android.graphics.Typeface.BOLD)
        pctText.textSize = 13.5f
        header.addView(pctText)
        col.addView(header)

        val track = LinearLayout(this)
        track.orientation = LinearLayout.HORIZONTAL
        track.weightSum = 100f
        track.background = ContextCompat.getDrawable(this, R.drawable.bg_pill_outline)
        val trackLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
        trackLp.topMargin = dp(6)
        track.layoutParams = trackLp

        val filled = android.view.View(this)
        val filledPct = type.pct.coerceIn(1.0, 100.0).toFloat()
        filled.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, filledPct)
        filled.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        track.addView(filled)

        val empty = android.view.View(this)
        empty.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100f - filledPct)
        track.addView(empty)

        col.addView(track)
        return col
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
