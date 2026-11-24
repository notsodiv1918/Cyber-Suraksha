package com.cybersuraksha.app

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cybersuraksha.app.databinding.ActivityMessageDetectorBinding
import java.util.concurrent.Executors

private const val MAX_LEN = 500

class MessageDetectorActivity : AppCompatActivity() {

    private lateinit var b: ActivityMessageDetectorBinding
    private var lastResult: ClassifyResult? = null
    private val ocrExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var swipeDetector: android.view.GestureDetector

    private val pickMedia = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) runOcr(uri)
    }
    private lateinit var voiceLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMessageDetectorBinding.inflate(layoutInflater)
        setContentView(b.root)
        Classifier.load(this)
        voiceLauncher = VoiceInput.register(this) { text ->
            b.messageInput.setText(text)
            runCheck()
        }

        // Share-to-check: a shared SMS/WhatsApp message lands here already filled in.
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { shared ->
                b.messageInput.setText(shared.take(MAX_LEN))
            }
        }

        b.messageInput.filters = arrayOf(InputFilter.LengthFilter(MAX_LEN))
        b.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                b.charCountText.text = "${s?.length ?: 0}/$MAX_LEN"
            }
        })

        b.backBtn.setOnClickListener {
            if (b.resultSection.visibility == View.VISIBLE) showInput() else finish()
        }
        b.langBtn.setOnClickListener { T.next(); applyLanguage() }
        b.checkBtn.setOnClickListener { runCheck() }
        b.reportBtn.setOnClickListener { startActivity(Intent(this, ReportActivity::class.java)) }
        b.checkAnotherBtn.setOnClickListener {
            b.messageInput.setText("")
            showInput()
        }
        b.uploadBtn.setOnClickListener { launchMediaPicker() }
        b.micBtn.setOnClickListener { VoiceInput.launch(this, voiceLauncher) }
        b.listenBtn.setOnClickListener {
            lastResult?.let { r ->
                val pct = Math.round(r.probability * 100).toInt()
                val reasons = r.hits.distinctBy { it.cat }.map { if (T.lang == Lang.EN) it.en else it.hi }
                TtsHelper.speakVerdictResult(this, r.verdict, pct, reasons)
            }
        }
        b.shareImageBtn.setOnClickListener {
            lastResult?.let { r ->
                startActivity(Intent.createChooser(WarningPoster.shareIntent(this, r.verdict, rememberedText), T.shareImageBtn))
            }
        }
        b.trustedContactBtn.setOnClickListener {
            val dial = TrustedContact.dialIntent(this)
            if (dial != null) startActivity(dial)
            else Toast.makeText(this, T.trustedContactNotSet, Toast.LENGTH_LONG).show()
        }
        ShareReport.wire(this, b.shareBtn, b.shareStatusText, { rememberedText }, { lastResult?.verdict ?: "" }, "message")

        swipeDetector = DetectTabs.gestureDetector(this, DetectTabs.Tab.MESSAGE)
        buildSamples()
        applyLanguage()
        if (b.messageInput.text?.isNotBlank() == true) runCheck() else showInput()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        renderHistory()
    }

    private fun showInput() {
        b.inputSection.visibility = View.VISIBLE
        b.resultSection.visibility = View.GONE
        b.navBottom.root.visibility = View.VISIBLE
        renderHistory()
    }

    private fun applyLanguage() {
        b.langBtn.text = T.label()
        b.titleText.text = T.msgDetectorTitle
        b.headingText.text = T.msgDetectorHeading
        b.subText.text = T.msgDetectorSub
        b.messageInput.hint = T.msgPlaceholder
        b.charCountText.text = "${b.messageInput.text?.length ?: 0}/$MAX_LEN"
        b.checkBtnText.text = T.checkBtn
        b.uploadBtnText.text = T.uploadPhotoVideo
        b.micBtnText.text = T.micBtn
        b.listenBtnText.text = T.listenBtn
        b.shareImageBtnText.text = T.shareImageBtn
        b.trustedContactBtnText.text = T.trustedContactBtn
        b.trySampleLabel.text = T.trySample
        b.historyLabel.text = T.historyMessages
        b.reportBtnText.text = T.reportThis
        b.shareBtnText.text = T.sharePattern
        b.checkAnotherBtnText.text = T.checkAnother
        b.onDeviceNoteText.text = T.onDeviceNote
        buildSamples()
        DetectTabs.buildRow(this, b.detectTabRow, DetectTabs.Tab.MESSAGE)
        lastResult?.let { showResult(it, rememberedText) }
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.DETECT)
        renderHistory()
    }

    private var rememberedText: String = ""

    private fun buildSamples() {
        b.sampleList.removeAllViews()
        val samples = listOf(T.sampleKyc, T.samplePrize, T.sampleCourier)
        samples.forEach { sample -> b.sampleList.addView(sampleRow(sample)) }
    }

    private fun sampleRow(text: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = ContextCompat.getDrawable(this, R.drawable.bg_card_white_round)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        row.layoutParams = lp
        row.setPadding(dp(12), dp(12), dp(12), dp(12))
        row.isClickable = true
        row.isFocusable = true

        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_main))
        tv.textSize = 13.5f
        tv.maxLines = 1
        tv.ellipsize = android.text.TextUtils.TruncateAt.END
        val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tv.layoutParams = tvLp
        row.addView(tv)

        val chevron = android.widget.ImageView(this)
        val cLp = LinearLayout.LayoutParams(dp(14), dp(14))
        cLp.marginStart = dp(8)
        chevron.layoutParams = cLp
        chevron.setImageResource(R.drawable.ic_chevron_right)
        chevron.setColorFilter(ContextCompat.getColor(this, R.color.text_muted))
        row.addView(chevron)

        row.setOnClickListener {
            b.messageInput.setText(text)
            runCheck()
        }
        return row
    }

    private fun launchMediaPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        pickMedia.launch(Intent.createChooser(intent, T.uploadPhotoVideo))
    }

    private fun runOcr(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: ""
        b.uploadStatusText.visibility = View.VISIBLE
        b.uploadStatusText.text = T.ocrRunning
        ocrExecutor.execute {
            val text = try {
                if (mimeType.startsWith("video")) OcrUpload.recognizeVideo(this, uri)
                else OcrUpload.recognizeImage(this, uri)
            } catch (e: Exception) { "" }
            mainHandler.post {
                if (text.isBlank()) {
                    b.uploadStatusText.text = T.ocrNoText
                } else {
                    b.uploadStatusText.visibility = View.GONE
                    b.messageInput.setText(text.take(MAX_LEN))
                    runCheck()
                }
            }
        }
    }

    private fun runCheck() {
        val text = b.messageInput.text.toString().trim()
        if (text.isEmpty()) return
        rememberedText = text
        val result = Classifier.classify(text)
        lastResult = result
        HistoryStore.add(this, HistoryItem("message", "-", text, result.verdict, System.currentTimeMillis()))
        showResult(result, text)
    }

    private fun showResult(result: ClassifyResult, text: String) {
        b.inputSection.visibility = View.GONE
        b.resultSection.visibility = View.VISIBLE
        b.navBottom.root.visibility = View.GONE

        val pct = Math.round(result.probability * 100)
        val (verdictText, fgCol, heroBg, icon) = when (result.verdict) {
            "scam" -> Quad(T.resultLikelyScam, R.color.danger, R.drawable.bg_card_round_yellow, R.drawable.ic_shield_alert)
            "suspicious" -> Quad(T.resultSuspicious, R.color.warn, R.drawable.bg_card_round_yellow, R.drawable.ic_shield_alert)
            else -> Quad(T.resultGenuine, R.color.safe, R.drawable.bg_card_round_green, R.drawable.ic_check_circle)
        }
        b.resultHero.background = ContextCompat.getDrawable(this, heroBg)
        b.resultIcon.setImageResource(icon)
        b.resultIcon.setColorFilter(ContextCompat.getColor(this, fgCol))
        b.resultVerdictText.text = verdictText
        b.resultVerdictText.setTextColor(ContextCompat.getColor(this, fgCol))
        b.resultScoreBadge.text = "$pct%"
        (b.resultScoreBadge.background.mutate() as GradientDrawable)
            .setColor(ContextCompat.getColor(this, if (result.verdict == "genuine") R.color.safe else R.color.danger))

        b.whySuspiciousLabel.text = if (result.verdict == "genuine")
            (if (T.lang == Lang.EN) "Why it looks genuine" else "यह असली क्यों लगता है") else T.whySuspicious
        b.reasonsList.removeAllViews()
        val reasons = result.hits.distinctBy { it.cat }
        if (reasons.isEmpty()) {
            val tv = TextView(this)
            tv.text = T.whySafe
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            tv.textSize = 13.5f
            b.reasonsList.addView(tv)
        } else {
            reasons.forEach { hit ->
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                row.layoutParams = lp
                val dot = TextView(this)
                dot.text = "•"
                dot.setTextColor(ContextCompat.getColor(this, R.color.primary))
                dot.textSize = 15f
                val dLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                dLp.marginEnd = dp(8)
                dot.layoutParams = dLp
                row.addView(dot)
                val tv = TextView(this)
                tv.text = if (T.lang == Lang.EN) hit.en else hit.hi
                tv.setTextColor(ContextCompat.getColor(this, R.color.text_main))
                tv.textSize = 13.5f
                val tLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                tv.layoutParams = tLp
                row.addView(tv)
                b.reasonsList.addView(row)
            }
        }
        b.reportBtn.visibility = if (result.verdict == "genuine") View.GONE else View.VISIBLE
        b.shareBtn.visibility = if (result.verdict == "genuine") View.GONE else View.VISIBLE
        b.shareStatusText.text = ""
    }

    private fun renderHistory() {
        HistoryView.render(this, b.historyList, setOf("sms", "message"), T.emptyHistoryMessages)
    }

    override fun onDestroy() {
        super.onDestroy()
        ocrExecutor.shutdown()
        TtsHelper.stop()
    }

    private data class Quad(val text: String, val fg: Int, val bg: Int, val icon: Int)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
