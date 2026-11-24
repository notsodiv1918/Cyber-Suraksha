package com.cybersuraksha.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

/** Spotlight/"coach mark" tour overlay — the kind every new app shows on first launch, dimming
 *  the screen and cutting a bright hole around the real button being explained, instead of a
 *  separate static slideshow screen. Drawn directly on top of whatever Activity starts it (here,
 *  the Home screen), so the actual Message/Call/QR cards and bottom-nav tabs are the things being
 *  pointed at — nothing is faked or reproduced in a mock screen. Narrated in Chhattisgarhi text,
 *  read by the Hindi TTS voice (same precedent as the rest of this app — Chhattisgarhi has no
 *  distinct on-device speech-synthesis voice), fully offline (see `TtsHelper`).
 *
 *  Blocks interaction with the real screen underneath while active (it's added as the *last*
 *  child of the window's content FrameLayout, so it both draws on top and receives touches
 *  first), but doesn't alter or remove anything from that screen — once `finish()` is called
 *  (Skip, or completing the last step) the overlay is simply removed and every card/tab/button
 *  behaves exactly as it did before the tour ran. */
class AppTourOverlay(private val activity: AppCompatActivity) {

    data class Step(val target: View, val caption: String, val narration: String)

    private val decorContent: FrameLayout = activity.findViewById(android.R.id.content)
    private val overlay = TourOverlayView(activity)
    private val captionCard: View =
        LayoutInflater.from(activity).inflate(R.layout.view_tour_caption, overlay, false)
    private val dotsRow: LinearLayout = captionCard.findViewById(R.id.tourDotsRow)
    private val captionText: TextView = captionCard.findViewById(R.id.tourCaptionText)
    private val nextBtn: LinearLayout = captionCard.findViewById(R.id.tourNextBtn)
    private val nextBtnText: TextView = captionCard.findViewById(R.id.tourNextBtnText)
    private val skipBtn: TextView = captionCard.findViewById(R.id.tourSkipBtn)

    private var steps: List<Step> = emptyList()
    private var index = 0
    private var onFinished: (() -> Unit)? = null
    private val hindiLocale = Locale("hi", "IN")

    init {
        overlay.addView(
            captionCard,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        nextBtn.setOnClickListener { if (index < steps.lastIndex) showStep(index + 1) else finish() }
        skipBtn.setOnClickListener { finish() }
    }

    fun start(steps: List<Step>, onFinished: () -> Unit) {
        this.steps = steps
        this.onFinished = onFinished
        decorContent.addView(
            overlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        buildDots()
        showStep(0)
    }

    private fun buildDots() {
        dotsRow.removeAllViews()
        val size = dp(9)
        steps.indices.forEach { i ->
            val dot = View(activity)
            val lp = LinearLayout.LayoutParams(size, size)
            if (i != steps.lastIndex) lp.marginEnd = dp(8)
            dot.layoutParams = lp
            dot.setBackgroundResource(R.drawable.bg_dot_inactive)
            dotsRow.addView(dot)
        }
    }

    private fun updateDots() {
        for (i in 0 until dotsRow.childCount) {
            dotsRow.getChildAt(i).setBackgroundResource(if (i == index) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
        }
    }

    private fun showStep(i: Int) {
        index = i
        val step = steps[i]
        captionText.text = step.caption
        nextBtnText.text = if (i == steps.lastIndex) "समझ गयेंव" else "आगू बढ़व"
        updateDots()
        // Scrolling a card into view takes a moment (see below), so a step change can still be
        // "in flight" when the user taps Next again quickly. Capture which step this callback is
        // for and bail if a newer one has already started by the time it runs — otherwise a late
        // callback could paint an older step's hole over the current one's caption text.
        val requestedIndex = i
        scrollIntoViewIfNeeded(step.target) {
            if (index != requestedIndex) return@scrollIntoViewIfNeeded
            overlay.post {
                if (index != requestedIndex) return@post
                val rect = computeOverlayRect(step.target)
                overlay.setHole(rect)
                positionCaption(rect)
                TtsHelper.speakIn(activity, step.narration, hindiLocale)
            }
        }
    }

    private fun computeOverlayRect(target: View): RectF {
        val targetLoc = IntArray(2)
        val overlayLoc = IntArray(2)
        target.getLocationInWindow(targetLoc)
        overlay.getLocationInWindow(overlayLoc)
        val pad = dp(8).toFloat()
        val left = (targetLoc[0] - overlayLoc[0]).toFloat() - pad
        val top = (targetLoc[1] - overlayLoc[1]).toFloat() - pad
        val right = left + target.width + pad * 2
        val bottom = top + target.height + pad * 2
        return RectF(left, top, right, bottom)
    }

    /** If the target sits inside a ScrollView, scrolls it into view first (smoothly) and waits
     *  for the scroll to mostly settle before measuring its on-screen position — so the hole and
     *  caption are computed from where the target will actually be, not where it was before
     *  scrolling. Targets outside any ScrollView (e.g. the bottom nav bar) proceed immediately. */
    private fun scrollIntoViewIfNeeded(target: View, after: () -> Unit) {
        var p: ViewParent? = target.parent
        var scrollView: ScrollView? = null
        while (p != null) {
            if (p is ScrollView) { scrollView = p; break }
            p = p.parent
        }
        if (scrollView == null) {
            after()
            return
        }
        val rect = Rect()
        target.getDrawingRect(rect)
        scrollView.offsetDescendantRectToMyCoords(target, rect)
        val desiredY = (rect.top - dp(24)).coerceAtLeast(0)
        scrollView.smoothScrollTo(0, desiredY)
        scrollView.postDelayed({ after() }, 260)
    }

    /** Decides above-vs-below placement from a clean, independent measurement of the card
     *  (`View.measure()` with sane, explicit specs) *before* ever assigning it a real position —
     *  deliberately not a "place it below, measure what happened, correct if wrong" two-pass
     *  approach. That approach was tried first and broke exactly for targets near the bottom of
     *  the screen (the bottom nav tabs): an initial "below the hole" topMargin bigger than the
     *  overlay's own height starves the child's height constraint during layout (Android's
     *  measure spec packs a negative available size into invalid bits), so the card actually
     *  measured 0px tall and rendered invisible — confirmed via logging on-device, not guessed. */
    private fun positionCaption(holeRect: RectF) {
        val margin = dp(20)
        val gap = dp(16)
        val availableWidth = (overlay.width - margin * 2).coerceAtLeast(1)
        captionCard.measure(
            View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cardHeight = captionCard.measuredHeight
        val spaceBelow = overlay.height - holeRect.bottom
        val spaceAbove = holeRect.top
        val fitsBelow = spaceBelow >= cardHeight + gap + dp(24)
        val topMargin = if (fitsBelow || spaceBelow >= spaceAbove) {
            (holeRect.bottom + gap).toInt()
        } else {
            (holeRect.top - gap - cardHeight).toInt()
        }.coerceIn(dp(50), (overlay.height - cardHeight - dp(20)).coerceAtLeast(dp(50)))

        val lp = captionCard.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = margin
        lp.rightMargin = margin
        lp.gravity = Gravity.TOP
        lp.topMargin = topMargin
        captionCard.layoutParams = lp
    }

    private fun finish() {
        TtsHelper.stop()
        decorContent.removeView(overlay)
        onFinished?.invoke()
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
}

/** The dimmed scrim with a cut-out "hole" around the current target, drawn via an offscreen
 *  bitmap so the hole is a true transparent cutout (PorterDuff.CLEAR) regardless of hardware
 *  acceleration, with a thin saffron ring traced around it for visibility. Consumes all touches
 *  over the dimmed area (children, i.e. the caption card's own buttons, still get first refusal
 *  per normal Android touch dispatch, since they're on top). */
private class TourOverlayView(context: Context) : FrameLayout(context) {

    private var hole: RectF? = null
    private val holeRadius = 14f * resources.displayMetrics.density
    private val scrimPaint = Paint().apply { color = 0xB3000000.toInt() }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.saffron)
    }
    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null

    init {
        isClickable = true
        setWillNotDraw(false)
    }

    fun setHole(rect: RectF) {
        hole = rect
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            maskBitmap?.recycle()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            maskBitmap = bmp
            maskCanvas = Canvas(bmp)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val bmp = maskBitmap
        val mc = maskCanvas
        if (bmp != null && mc != null) {
            bmp.eraseColor(Color.TRANSPARENT)
            mc.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            hole?.let { r -> mc.drawRoundRect(r, holeRadius, holeRadius, clearPaint) }
            canvas.drawBitmap(bmp, 0f, 0f, null)
            hole?.let { r -> canvas.drawRoundRect(r, holeRadius, holeRadius, ringPaint) }
        }
        super.dispatchDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        maskBitmap?.recycle()
        maskBitmap = null
        maskCanvas = null
    }
}
