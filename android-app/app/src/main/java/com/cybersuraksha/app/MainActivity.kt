package com.cybersuraksha.app

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.format.DateUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cybersuraksha.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val requestSmsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshStatus()
    }
    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestCallRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        Classifier.load(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        b.langBtn.setOnClickListener {
            T.next()
            applyLanguage()
        }
        b.smsEnableBtn.setOnClickListener {
            requestSmsPermission.launch(android.Manifest.permission.RECEIVE_SMS)
        }
        b.callEnableBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    requestCallRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                } else {
                    Toast.makeText(this, "Call screening role not available on this device", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Needs Android 10+", Toast.LENGTH_LONG).show()
            }
        }
        b.checkBtn.setOnClickListener { runCheck() }

        applyLanguage()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        renderHistory()
    }

    private fun applyLanguage() {
        b.langBtn.text = T.label()
        b.titleText.text = T.appTitle
        b.subtitleText.text = T.subtitle
        b.statusTitle.text = T.statusTitle
        b.checkLabel.text = T.checkLabel
        b.messageInput.hint = T.hint
        b.checkBtn.text = T.checkBtn
        b.historyLabel.text = T.historyLabel
        refreshStatus()
        renderHistory()
    }

    private fun refreshStatus() {
        val smsGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        b.smsStatusText.text = if (smsGranted) T.smsOn else T.smsOff
        b.smsEnableBtn.visibility = if (smsGranted) android.view.View.GONE else android.view.View.VISIBLE

        var callActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            callActive = telecom.defaultDialerPackage == packageName ||
                (getSystemService(Context.ROLE_SERVICE) as RoleManager).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
        b.callStatusText.text = if (callActive) T.callOn else T.callOff
        b.callEnableBtn.visibility = if (callActive) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun runCheck() {
        val text = b.messageInput.text.toString().trim()
        if (text.isEmpty()) {
            b.resultText.visibility = android.view.View.VISIBLE
            b.resultText.text = T.emptyInput
            b.resultText.setBackgroundColor(ContextCompat.getColor(this, R.color.warn_bg))
            b.resultText.setTextColor(ContextCompat.getColor(this, R.color.warn))
            return
        }
        val result = Classifier.classify(text)
        HistoryStore.add(this, HistoryItem("manual", "-", text, result.verdict, System.currentTimeMillis()))
        showResult(result)
        renderHistory()
    }

    private fun showResult(result: ClassifyResult) {
        val (bg, fg, label) = when (result.verdict) {
            "scam" -> Triple(R.color.danger_bg, R.color.danger, T.verdictScam)
            "suspicious" -> Triple(R.color.warn_bg, R.color.warn, T.verdictSuspicious)
            else -> Triple(R.color.safe_bg, R.color.safe, T.verdictGenuine)
        }
        val pct = Math.round(result.probability * 100)
        val typeLine = result.scamType?.let { " · " + T.categoryLabel(it) } ?: ""
        b.resultText.visibility = android.view.View.VISIBLE
        b.resultText.text = "$label ($pct%)$typeLine"
        b.resultText.setBackgroundColor(ContextCompat.getColor(this, bg))
        b.resultText.setTextColor(ContextCompat.getColor(this, fg))
    }

    private fun renderHistory() {
        b.historyList.removeAllViews()
        val items = HistoryStore.all(this)
        if (items.isEmpty()) {
            val tv = TextView(this)
            tv.text = T.emptyHistory
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            tv.textSize = 13f
            b.historyList.addView(tv)
            return
        }
        for (item in items) {
            b.historyList.addView(historyRow(item))
        }
    }

    private fun historyRow(item: HistoryItem): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setBackgroundColor(Color.WHITE)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        row.layoutParams = lp
        row.setPadding(dp(12), dp(10), dp(12), dp(10))

        val header = TextView(this)
        val who = when (item.type) {
            "sms" -> T.smsScannedLine(item.who)
            "call" -> T.callScreenedLine(item.who)
            else -> if (T.lang == Lang.EN) "Manual check" else "मैन्युअल जाँच"
        }
        header.text = who
        header.setTextColor(ContextCompat.getColor(this, R.color.text_main))
        header.setTypeface(null, android.graphics.Typeface.BOLD)
        header.textSize = 13.5f
        row.addView(header)

        if (item.text.isNotBlank()) {
            val body = TextView(this)
            body.text = item.text.take(90)
            body.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            body.textSize = 12.5f
            val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            blp.topMargin = dp(2)
            body.layoutParams = blp
            row.addView(body)
        }

        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        val flp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        flp.topMargin = dp(6)
        footer.layoutParams = flp

        val badge = TextView(this)
        val (bg, fg, label) = when (item.verdict) {
            "scam" -> Triple(R.color.danger_bg, R.color.danger, T.verdictScam)
            "suspicious", "unscreened" -> Triple(R.color.warn_bg, R.color.warn, T.verdictSuspicious)
            else -> Triple(R.color.safe_bg, R.color.safe, T.verdictGenuine)
        }
        badge.text = label
        badge.setTextColor(ContextCompat.getColor(this, fg))
        badge.setBackgroundColor(ContextCompat.getColor(this, bg))
        badge.setPadding(dp(8), dp(3), dp(8), dp(3))
        badge.textSize = 11f
        footer.addView(badge)

        val time = TextView(this)
        time.text = DateUtils.getRelativeTimeSpanString(item.time)
        time.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        time.textSize = 11f
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.gravity = Gravity.END
        tlp.marginStart = dp(8)
        time.layoutParams = tlp
        time.gravity = Gravity.END
        footer.addView(time)

        row.addView(footer)
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
