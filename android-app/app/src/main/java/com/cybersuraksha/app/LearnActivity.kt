package com.cybersuraksha.app

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cybersuraksha.app.databinding.ActivityLearnBinding

private data class Lesson(
    val cat: String,
    val icon: Int,
    val chipBg: Int,
    val tint: Int,
    val titleEn: String, val titleHi: String, val titleCg: String,
    val descEn: String, val descHi: String, val descCg: String,
)

class LearnActivity : AppCompatActivity() {

    private lateinit var b: ActivityLearnBinding
    private var activeFilter: String = "all"

    private val lessons = listOf(
        Lesson(
            "upi", R.drawable.ic_lock, R.drawable.bg_card_round_green, R.color.primary,
            "UPI Safety Basics", "UPI सुरक्षा बुनियाद", "UPI सुरक्षा बुनियाद",
            "Your UPI PIN is only ever needed to SEND money — never to receive it. Anyone asking for your PIN to 'accept' a payment is scamming you.",
            "आपका UPI पिन केवल पैसे भेजने हेतु चाहिए — पैसे प्राप्त करने हेतु कभी नहीं। 'पैसे स्वीकार करने' के नाम पर पिन माँगने वाला धोखेबाज़ है।",
            "तुम्हर UPI पिन सिरिफ पइसा भेजे बर चाही — पइसा मिले बर कभू नइ। 'पइसा स्वीकार करे' के नाव म पिन मांगइया धोखेबाज हे।"
        ),
        Lesson(
            "kyc", R.drawable.ic_mail, R.drawable.bg_card_round_blue, R.color.accent_blue,
            "Spot a Fake KYC Message", "फर्ज़ी KYC मैसेज पहचानें", "फर्जी KYC मैसेज पहिचानव",
            "Banks never ask you to 'update KYC' by clicking a link in an SMS. Visit your bank branch or the official app only.",
            "बैंक कभी भी SMS में लिंक पर क्लिक करके 'KYC अपडेट' करने को नहीं कहते। केवल बैंक शाखा या आधिकारिक ऐप पर जाएँ।",
            "बैंक कभू SMS म लिंक क्लिक करके 'KYC अपडेट' करे बर नइ कहय। सिरिफ बैंक शाखा या आधिकारिक ऐप म जाव।"
        ),
        Lesson(
            "calls", R.drawable.ic_users, R.drawable.bg_card_round_yellow, R.color.accent_amber,
            "Digital Arrest Scams", "'डिजिटल अरेस्ट' स्कैम", "'डिजिटल अरेस्ट' स्कैम",
            "No police, CBI, customs or court ever arrests anyone over a video call or demands money/UPI payment to avoid arrest. Hang up and report.",
            "कोई भी पुलिस, CBI, कस्टम या अदालत वीडियो कॉल पर किसी को गिरफ़्तार नहीं करती, न ही पैसे माँगती है। कॉल काटें और रिपोर्ट करें।",
            "कोनो पुलिस, CBI, कस्टम या अदालत वीडियो कॉल म कोनो ला गिरफ्तार नइ करय, न पइसा मांगय। कॉल काटव अउ रिपोर्ट करव।"
        ),
        Lesson(
            "calls", R.drawable.ic_shield_alert, R.drawable.bg_card_round_yellow, R.color.accent_amber,
            "Fraud Call Red Flags", "फ्रॉड कॉल के चेतावनी संकेत", "फ्रॉड कॉल के चेतावनी संकेत",
            "Urgency, threats of account block, requests for OTP/PIN on a call, and caller claiming to be bank/police are all red flags.",
            "जल्दबाज़ी, खाता ब्लॉक की धमकी, कॉल पर OTP/पिन माँगना, और खुद को बैंक/पुलिस बताना — सभी खतरे के संकेत हैं।",
            "जल्दी, खाता ब्लॉक के धमकी, कॉल म OTP/पिन मांगना, अउ अपन ला बैंक/पुलिस बताना — सब्भो खतरा के संकेत हे।"
        ),
        Lesson(
            "upi", R.drawable.ic_search, R.drawable.bg_card_round_green, R.color.primary,
            "Safe QR / Online Payments", "सुरक्षित QR/ऑनलाइन भुगतान", "सुरक्षित QR/ऑनलाइन भुगतान",
            "Before paying a QR code, confirm the payee name out loud with the shopkeeper. A scanned QR never GIVES you money, only takes it.",
            "QR कोड से भुगतान से पहले दुकानदार से नाम बोलकर पुष्टि करें। QR स्कैन करने से पैसे कभी नहीं मिलते, केवल जाते हैं।",
            "QR कोड ले भुगतान के पहिली दुकानदार ले नाम बोलके पुष्टि करव। QR स्कैन करे ले पइसा कभू नइ मिलय, सिरिफ जावय।"
        ),
        Lesson(
            "kyc", R.drawable.ic_lock, R.drawable.bg_card_round_blue, R.color.accent_blue,
            "Keep Your Accounts Safe", "अपने खाते सुरक्षित रखें", "अपन खाता सुरक्षित रखव",
            "Use a different password for banking apps, enable transaction SMS alerts, and never save your PIN/password on your phone.",
            "बैंकिंग ऐप हेतु अलग पासवर्ड रखें, लेनदेन SMS अलर्ट चालू रखें, और पिन/पासवर्ड फोन पर सेव न करें।",
            "बैंकिंग ऐप बर अलग पासवर्ड रखव, लेनदेन SMS अलर्ट चालू रखव, अउ पिन/पासवर्ड फोन म सेव झन करव।"
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLearnBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.backBtn.setOnClickListener { finish() }
        b.langBtn.setOnClickListener { T.next(); applyLanguage() }

        NavBar.wire(this, b.navBottom.root, NavBar.Tab.LEARN)
        applyLanguage()
    }

    private fun applyLanguage() {
        b.langBtn.text = T.label()
        b.titleText.text = T.learnTitle
        buildFilters()
        buildLessons()
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.LEARN)
    }

    private fun buildFilters() {
        b.filterRow.removeAllViews()
        val filters = listOf("all" to T.filterAll, "upi" to T.filterUpi, "kyc" to T.filterKyc, "calls" to T.filterCalls)
        filters.forEach { (key, label) ->
            val chip = TextView(this)
            chip.text = label
            chip.textSize = 12.5f
            chip.setPadding(dp(14), dp(8), dp(14), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(8)
            chip.layoutParams = lp
            val active = key == activeFilter
            chip.background = ContextCompat.getDrawable(this, if (active) R.drawable.bg_pill_selected else R.drawable.bg_pill_outline)
            chip.setTextColor(ContextCompat.getColor(this, if (active) R.color.white else R.color.text_main))
            chip.setOnClickListener { activeFilter = key; applyLanguage() }
            b.filterRow.addView(chip)
        }
    }

    private fun buildLessons() {
        b.lessonsList.removeAllViews()
        val filtered = if (activeFilter == "all") lessons else lessons.filter { it.cat == activeFilter }
        filtered.forEach { b.lessonsList.addView(lessonCard(it)) }
    }

    private fun lessonCard(lesson: Lesson): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.TOP
        row.background = ContextCompat.getDrawable(this, R.drawable.bg_card_white_round)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        row.layoutParams = lp
        row.setPadding(dp(14), dp(14), dp(14), dp(14))

        val chip = FrameLayout(this)
        chip.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        chip.background = ContextCompat.getDrawable(this, lesson.chipBg)
        val icon = ImageView(this)
        icon.layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER }
        icon.setImageResource(lesson.icon)
        icon.setColorFilter(ContextCompat.getColor(this, lesson.tint))
        chip.addView(icon)
        row.addView(chip)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val colLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        colLp.marginStart = dp(12)
        col.layoutParams = colLp

        val title = TextView(this)
        title.text = when (T.lang) { Lang.EN -> lesson.titleEn; Lang.HI -> lesson.titleHi; Lang.CG -> lesson.titleCg }
        title.setTextColor(ContextCompat.getColor(this, R.color.text_main))
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.textSize = 14.5f
        col.addView(title)

        val desc = TextView(this)
        desc.text = when (T.lang) { Lang.EN -> lesson.descEn; Lang.HI -> lesson.descHi; Lang.CG -> lesson.descCg }
        desc.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        desc.textSize = 12.5f
        val dLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dLp.topMargin = dp(4)
        desc.layoutParams = dLp
        col.addView(desc)

        row.addView(col)
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
