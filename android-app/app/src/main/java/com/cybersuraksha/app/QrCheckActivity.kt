package com.cybersuraksha.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.cybersuraksha.app.databinding.ActivityQrBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera-scan / gallery-upload QR checker. Decoding is done fully on-device via the
 * bundled ML Kit barcode-scanning model (no network, no Play-Services download) — the
 * resulting text is then scored by QrAnalyzer, a line-for-line Kotlin port of
 * site/js/qr-check.js, so this gives the identical verdict the website would.
 */
private data class QrVerdictStyle(val bg: Int, val fg: Int, val icon: Int, val label: String)

class QrCheckActivity : AppCompatActivity() {

    private lateinit var b: ActivityQrBinding
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val scanning = AtomicBoolean(false)
    private val decodeHandled = AtomicBoolean(false)
    private lateinit var swipeDetector: android.view.GestureDetector

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            b.camStatusText.text = T.qrCameraPermNeeded
            Toast.makeText(this, T.qrCameraPermNeeded, Toast.LENGTH_LONG).show()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) handlePickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQrBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.backBtn.setOnClickListener { finish() }
        b.langBtn.setOnClickListener { T.next(); applyLanguage() }

        b.tabCamera.setOnClickListener { showTab(camera = true) }
        b.tabUpload.setOnClickListener { showTab(camera = false) }

        b.camStartBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) startCamera() else requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        }
        b.camStopBtn.setOnClickListener { stopCamera() }
        b.uploadBtn.setOnClickListener { pickImage.launch("image/*") }

        swipeDetector = DetectTabs.gestureDetector(this, DetectTabs.Tab.QR)
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.NONE)
        applyLanguage()
        showTab(camera = true)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        renderHistory()
    }

    private fun applyLanguage() {
        b.langBtn.text = T.label()
        b.titleText.text = T.qrTitle
        b.subtitleText.text = T.qrSubtitle
        b.tabCamera.text = T.qrTabCamera
        b.tabUpload.text = T.qrTabUpload
        b.camStartBtn.text = T.qrStartCamera
        b.camStopBtn.text = T.qrStopCamera
        b.uploadBtn.text = T.qrUploadBtn
        b.qrScanHintText.text = T.qrScanHint
        b.historyLabel.text = T.historyQr
        DetectTabs.buildRow(this, b.detectTabRow, DetectTabs.Tab.QR)
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.NONE)
        renderHistory()
    }

    private fun renderHistory() {
        HistoryView.render(this, b.historyList, setOf("qr"), T.emptyHistoryQr)
    }

    private fun showTab(camera: Boolean) {
        b.cameraPanel.visibility = if (camera) View.VISIBLE else View.GONE
        b.uploadPanel.visibility = if (camera) View.GONE else View.VISIBLE
        val activeBg = R.drawable.bg_btn_primary_green
        val inactiveBg = R.drawable.bg_btn_outline_green
        b.tabCamera.setBackgroundResource(if (camera) activeBg else inactiveBg)
        b.tabCamera.setTextColor(ContextCompat.getColor(this, if (camera) R.color.white else R.color.text_main))
        b.tabUpload.setBackgroundResource(if (camera) inactiveBg else activeBg)
        b.tabUpload.setTextColor(ContextCompat.getColor(this, if (camera) R.color.text_main else R.color.white))
        if (!camera) stopCamera()
    }

    // ---------------- Camera scan ----------------

    private fun startCamera() {
        decodeHandled.set(false)
        b.previewView.visibility = View.VISIBLE
        b.camStartBtn.visibility = View.GONE
        b.camStopBtn.visibility = View.VISIBLE
        b.camStatusText.text = ""
        if (cameraExecutor == null) cameraExecutor = Executors.newSingleThreadExecutor()

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(b.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val scanner = BarcodeScanning.getClient()
            analysis.setAnalyzer(cameraExecutor!!) { imageProxy -> analyzeFrame(imageProxy, scanner) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                scanning.set(true)
            } catch (e: Exception) {
                b.camStatusText.text = "${T.qrCameraPermNeeded} (${e.message})"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyzeFrame(imageProxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner) {
        if (!scanning.get() || decodeHandled.get()) { imageProxy.close(); return }
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull { it.valueType == Barcode.TYPE_URL || it.rawValue != null }?.rawValue
                if (value != null && decodeHandled.compareAndSet(false, true)) {
                    runOnUiThread {
                        stopCamera()
                        showResult(value)
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun stopCamera() {
        scanning.set(false)
        cameraProvider?.unbindAll()
        b.previewView.visibility = View.GONE
        b.camStartBtn.visibility = View.VISIBLE
        b.camStopBtn.visibility = View.GONE
    }

    // ---------------- Gallery upload ----------------

    private fun handlePickedImage(uri: Uri) {
        b.uploadPreview.setImageURI(uri)
        b.uploadPreview.visibility = View.VISIBLE
        b.uploadStatusText.text = ""
        try {
            val image = InputImage.fromFilePath(this, uri)
            BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstOrNull { it.rawValue != null }?.rawValue
                    if (value != null) {
                        showResult(value)
                    } else {
                        b.uploadStatusText.text = T.qrNoQrFound
                    }
                }
                .addOnFailureListener {
                    b.uploadStatusText.text = T.qrNoQrFound
                }
        } catch (e: Exception) {
            b.uploadStatusText.text = T.qrNoQrFound
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
    }

    // ---------------- Result rendering (mirrors CGQr.renderQrVerdict in qr-check.js) ----------------

    private fun showResult(rawText: String) {
        val result = QrAnalyzer.analysePayload(rawText)
        HistoryStore.add(this, HistoryItem("qr", "-", rawText, when (result.verdict) {
            QrVerdict.DANGER -> "scam"
            QrVerdict.WARN -> "suspicious"
            QrVerdict.SAFE -> "genuine"
        }, System.currentTimeMillis()))
        renderHistory()

        val style = when (result.verdict) {
            QrVerdict.DANGER -> QrVerdictStyle(R.color.danger_bg, R.color.danger, R.drawable.ic_x_circle, T.qrVerdictDanger)
            QrVerdict.WARN -> QrVerdictStyle(R.color.warn_bg, R.color.warn, R.drawable.ic_alert_triangle, T.qrVerdictWarn)
            QrVerdict.SAFE -> QrVerdictStyle(R.color.safe_bg, R.color.safe, R.drawable.ic_check_circle, T.qrVerdictSafe)
        }
        val bgCol = style.bg
        val fgCol = style.fg
        val icon = style.icon
        val title = style.label
        val typeLabel = when (result.type) {
            QrType.UPI -> T.qrTypeUpi
            QrType.URL -> T.qrTypeUrl
            QrType.OTHER -> T.qrTypeOther
        }

        val container = b.resultContainer
        container.removeAllViews()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@QrCheckActivity, R.drawable.bg_banner)?.mutate()
            (background as GradientDrawable).setColor(ContextCompat.getColor(this@QrCheckActivity, bgCol))
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        // Title row: icon + verdict text + type badge
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            setImageResource(icon)
            setColorFilter(ContextCompat.getColor(this@QrCheckActivity, fgCol))
        }
        titleRow.addView(titleIcon)
        val titleText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) }
            text = title
            setTextColor(ContextCompat.getColor(this@QrCheckActivity, fgCol))
            setTypeface(null, android.graphics.Typeface.BOLD)
            textSize = 15f
        }
        titleRow.addView(titleText)
        val typeBadge = TextView(this).apply {
            text = typeLabel
            background = ContextCompat.getDrawable(this@QrCheckActivity, R.drawable.bg_badge)?.mutate()
            (background as GradientDrawable).setColor(ContextCompat.getColor(this@QrCheckActivity, R.color.warn_bg))
            setTextColor(ContextCompat.getColor(this@QrCheckActivity, R.color.warn))
            setPadding(dp(8), dp(3), dp(8), dp(3))
            textSize = 10.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        titleRow.addView(typeBadge)
        card.addView(titleRow)

        // Fields (UPI ID / name / amount, or Host)
        if (result.fields.isNotEmpty()) {
            val fieldsBox = kvList(result.fields.map { it.first to it.second }, fgCol)
            fieldsBox.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) }
            card.addView(fieldsBox)
        }

        // Reasons
        val reasonRows = result.reasons.map { r ->
            val label = when (r.sev) { QrSeverity.DANGER -> T.qrSevRisk; QrSeverity.WARN -> T.qrSevCaution; QrSeverity.INFO -> T.qrSevInfo }
            (if (T.lang == Lang.EN) r.en else r.hi) to label
        }
        val reasonsBox = kvList(reasonRows, fgCol)
        (reasonsBox.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
        card.addView(reasonsBox)

        // Raw decoded text box
        val rawBox = TextView(this).apply {
            text = result.raw
            setTextColor(ContextCompat.getColor(this@QrCheckActivity, R.color.text_muted))
            textSize = 11.5f
            typeface = android.graphics.Typeface.MONOSPACE
            background = ContextCompat.getDrawable(this@QrCheckActivity, R.drawable.bg_input)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        card.addView(rawBox)

        // Next steps
        if (result.verdict != QrVerdict.SAFE) {
            val stepsHeader = TextView(this).apply {
                text = T.qrWhatNow
                setTextColor(ContextCompat.getColor(this@QrCheckActivity, R.color.text_main))
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 13f
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(14)
                layoutParams = lp
            }
            card.addView(stepsHeader)
            val steps = if (result.type == QrType.UPI)
                listOf(T.qrStepConfirmName, T.qrStepTellShopkeeper, T.qrStepPayCash)
            else
                listOf(T.qrStepDontOpen, T.qrStepDontEnter)
            steps.forEach { stepText ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = dp(6)
                    layoutParams = lp
                }
                val dot = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(14), dp(14))
                    setImageResource(R.drawable.ic_x_circle)
                    setColorFilter(ContextCompat.getColor(this@QrCheckActivity, R.color.danger))
                }
                row.addView(dot)
                val tv = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) }
                    text = stepText
                    setTextColor(ContextCompat.getColor(this@QrCheckActivity, R.color.text_main))
                    textSize = 13f
                }
                row.addView(tv)
                card.addView(row)
            }
        }

        container.addView(card)
    }

    private fun kvList(rows: List<Pair<String, String>>, @androidx.annotation.ColorRes fgCol: Int): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rows.forEach { (k, v) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(4)
                layoutParams = lp
            }
            val keyTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = k
                setTextColor(ContextCompat.getColor(this@QrCheckActivity, R.color.text_main))
                textSize = 12.5f
            }
            val valTv = TextView(this).apply {
                text = v
                setTextColor(ContextCompat.getColor(this@QrCheckActivity, fgCol))
                textSize = 12.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
            }
            row.addView(keyTv)
            row.addView(valTv)
            box.addView(row)
        }
        return box
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
