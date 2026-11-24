package com.cybersuraksha.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.cybersuraksha.app.databinding.ActivityOnboardingBinding
import java.util.Locale

/** Icon-only, first-launch walkthrough: "paste or speak → we check → see the result."
 *
 * Deliberately **not** wired into the trilingual `T` system used everywhere else in the app —
 * the user asked for this one screen to always narrate in Chhattisgarhi text (falling back to
 * Hindi wording where a distinct Chhattisgarhi phrasing isn't meaningfully different), spoken by
 * the Hindi TTS voice, with **no English at all**, regardless of whatever language the rest of
 * the app is currently set to. Three pages, each: one or two big icons (no paragraphs — a short
 * caption only), a "listen again" button, and audio narration that plays automatically when the
 * page is shown. Fully offline: the icons are bundled vector drawables and the narration uses the
 * same on-device `TextToSpeech` engine as the rest of the app (see `TtsHelper` and the "Offline
 * voice pack" note on the More screen — this screen doesn't do speech *recognition*, only speech
 * *synthesis*, which Android TTS engines ship fully-offline voice data for once installed, unlike
 * the recognizer's occasional pack-download gap).
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "onboarding"
        private const val KEY_SHOWN = "shown"

        fun shouldShow(context: Context): Boolean =
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOWN, false)

        fun markShown(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOWN, true).apply()
        }
    }

    private lateinit var b: ActivityOnboardingBinding
    private var page = 0

    private data class Page(val captionText: String, val narration: String)

    private val pages = listOf(
        Page(
            "मैसेज पेस्ट करव या बोलव",
            "कोनो संदेश ल पेस्ट करव, या फिर बोलके बताव।"
        ),
        Page(
            "हमर ऐप खुदे जाँच करही",
            "आपके फोन म ही, बिना इंटरनेट के, हमर ऐप मैसेज के जाँच करही।"
        ),
        Page(
            "नतीजा देखव अउ सुरक्षित रहव",
            "नतीजा देखव अउ सुरक्षित रहव — स्कैम लगे त सावधान हो जाव।"
        ),
    )

    private val hindiLocale = Locale("hi", "IN")
    private lateinit var swipeDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.skipBtn.setOnClickListener { finishOnboarding() }
        b.nextBtn.setOnClickListener {
            if (page < pages.lastIndex) showPage(page + 1) else finishOnboarding()
        }
        b.replayBtn.setOnClickListener { narrate(page) }

        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (Math.abs(dx) < 80 || Math.abs(velocityX) < Math.abs(velocityY)) return false
                if (dx < 0 && page < pages.lastIndex) showPage(page + 1)
                else if (dx > 0 && page > 0) showPage(page - 1)
                return true
            }
        })

        showPage(0)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun showPage(index: Int) {
        page = index
        b.page1.visibility = if (index == 0) View.VISIBLE else View.GONE
        b.page2.visibility = if (index == 1) View.VISIBLE else View.GONE
        b.page3.visibility = if (index == 2) View.VISIBLE else View.GONE
        b.captionText.text = pages[index].captionText
        b.nextBtnText.text = if (index == pages.lastIndex) "सुरू करव" else "आगू बढ़व"
        tintDot(b.dot1, index == 0)
        tintDot(b.dot2, index == 1)
        tintDot(b.dot3, index == 2)
        narrate(index)
    }

    private fun narrate(index: Int) {
        TtsHelper.speakIn(this, pages[index].narration, hindiLocale)
    }

    private fun tintDot(dot: View, active: Boolean) {
        dot.setBackgroundResource(if (active) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
    }

    private fun finishOnboarding() {
        markShown(this)
        TtsHelper.stop()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        TtsHelper.stop()
    }
}
