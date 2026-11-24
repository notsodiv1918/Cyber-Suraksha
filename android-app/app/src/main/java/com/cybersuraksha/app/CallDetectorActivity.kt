package com.cybersuraksha.app

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
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
import com.cybersuraksha.app.databinding.ActivityCallDetectorBinding

private const val CALL_MAX_LEN = 500

class CallDetectorActivity : AppCompatActivity() {

    private lateinit var b: ActivityCallDetectorBinding
    private var lastResult: ClassifyResult? = null
    private var rememberedText: String = ""
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var swipeDetector: android.view.GestureDetector

    private val pickAudio = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) setupAudio(uri)
    }
    private val voiceInput = VoiceInput(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCallDetectorBinding.inflate(layoutInflater)
        setContentView(b.root)
        Classifier.load(this)

        b.messageInput.filters = arrayOf(InputFilter.LengthFilter(CALL_MAX_LEN))
        b.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                b.charCountText.text = "${s?.length ?: 0}/$CALL_MAX_LEN"
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
        b.uploadBtn.setOnClickListener { launchAudioPicker() }
        b.micBtn.setOnClickListener {
            voiceInput.start(
                onResult = { text -> b.messageInput.setText(text) },
                onListening = { listening -> b.micBtnText.text = if (listening) T.listening else T.micBtn }
            )
        }
        b.audioPlayBtn.setOnClickListener { toggleAudioPlayback() }
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
        ShareReport.wire(this, b.shareBtn, b.shareStatusText, { rememberedText }, { lastResult?.verdict ?: "" }, "call")

        swipeDetector = DetectTabs.gestureDetector(this, DetectTabs.Tab.CALL)
        buildSamples()
        applyLanguage()
        showInput()
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
        b.titleText.text = T.callDetectorTitle
        b.headingText.text = T.callDetectorHeading
        b.subText.text = T.callDetectorSub
        b.messageInput.hint = T.callPlaceholder
        b.charCountText.text = "${b.messageInput.text?.length ?: 0}/$CALL_MAX_LEN"
        b.checkBtnText.text = T.checkCallBtn
        b.uploadBtnText.text = T.uploadAudio
        b.micBtnText.text = T.micBtn
        b.audioHintText.text = T.audioPlayHint
        b.listenBtnText.text = T.listenBtn
        b.shareImageBtnText.text = T.shareImageBtn
        b.trustedContactBtnText.text = T.trustedContactBtn
        b.trySampleLabel.text = T.trySampleCall
        b.historyLabel.text = T.historyCalls
        b.reportBtnText.text = T.reportThis
        b.shareBtnText.text = T.sharePattern
        b.checkAnotherBtnText.text = T.checkAnother
        b.onDeviceNoteText.text = T.onDeviceNote
        buildSamples()
        DetectTabs.buildRow(this, b.detectTabRow, DetectTabs.Tab.CALL)
        lastResult?.let { showResult(it) }
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.DETECT)
        renderHistory()
    }

    private fun buildSamples() {
        b.sampleList.removeAllViews()
        val samples = listOf(T.sampleArrest, T.sampleOtpCall)
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

    private fun launchAudioPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "audio/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        pickAudio.launch(Intent.createChooser(intent, T.uploadAudio))
    }

    private fun setupAudio(uri: Uri) {
        releaseAudio()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(this@CallDetectorActivity, uri)
                prepare()
                setOnCompletionListener { b.audioPlayBtnText.text = "▶" }
            } catch (e: Exception) {
                mediaPlayer = null
            }
        }
        b.audioPlayerCard.visibility = View.VISIBLE
        b.audioPlayBtnText.text = "▶"
    }

    private fun toggleAudioPlayback() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            b.audioPlayBtnText.text = "▶"
        } else {
            mp.start()
            b.audioPlayBtnText.text = "❚❚"
        }
    }

    private fun releaseAudio() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        b.audioPlayBtnText.text = "▶"
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAudio()
        TtsHelper.stop()
        voiceInput.destroy()
    }

    private fun runCheck() {
        val text = b.messageInput.text.toString().trim()
        if (text.isEmpty()) return
        rememberedText = text
        val result = Classifier.classify(text)
        lastResult = result
        HistoryStore.add(this, HistoryItem("call", "-", text, result.verdict, System.currentTimeMillis()))
        showResult(result)
    }

    private fun showResult(result: ClassifyResult) {
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
        HistoryView.render(this, b.historyList, setOf("call"), T.emptyHistoryCalls)
    }

    private data class Quad(val text: String, val fg: Int, val bg: Int, val icon: Int)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
