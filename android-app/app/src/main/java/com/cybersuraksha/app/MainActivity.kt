package com.cybersuraksha.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cybersuraksha.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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

        b.langBtn.setOnClickListener { T.next(); applyLanguage() }
        b.cardMessage.setOnClickListener { startActivity(Intent(this, MessageDetectorActivity::class.java)) }
        b.cardCall.setOnClickListener { startActivity(Intent(this, CallDetectorActivity::class.java)) }
        b.cardQr.setOnClickListener { startActivity(Intent(this, QrCheckActivity::class.java)) }
        b.cardReport.setOnClickListener { startActivity(Intent(this, ReportActivity::class.java)) }
        b.cardLearn.setOnClickListener { startActivity(Intent(this, LearnActivity::class.java)) }
        b.cardTrends.setOnClickListener { startActivity(Intent(this, DashboardActivity::class.java)) }
        b.emergencyCard.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")))
        }

        NavBar.wire(this, b.navBottom.root, NavBar.Tab.HOME)
        applyLanguage()
        b.root.post { maybeStartTour() }
    }

    override fun onResume() {
        super.onResume()
        applyLanguage()
    }

    override fun onDestroy() {
        super.onDestroy()
        TtsHelper.stop()
    }

    /** First-launch, icon-pointing guided tour (a spotlight overlay on the real Home screen, not
     *  a separate static slideshow) — Message/Call/QR cards, then the bottom nav's "जाँच" and
     *  "अधिक" tabs, each narrated in Chhattisgarhi. Shown once; see `AppTourOverlay`. */
    private fun maybeStartTour() {
        val prefs = getSharedPreferences("home_tour", MODE_PRIVATE)
        if (prefs.getBoolean("shown", false)) return
        val navRoot = b.navBottom.root
        val navDetect = navRoot.findViewById<View>(R.id.navDetect)
        val navMore = navRoot.findViewById<View>(R.id.navMore)
        val steps = listOf(
            AppTourOverlay.Step(
                b.cardMessage,
                "मैसेज जाँचव",
                "कोनो संदिग्ध मैसेज आए त इहाँ पेस्ट करव या बोलव, ऐप तुरंत जाँच दिही।"
            ),
            AppTourOverlay.Step(
                b.cardCall,
                "कॉल जाँचव",
                "संदिग्ध कॉल के बारे म इहाँ बताव, ऐप स्कैम कॉल ल पहिचान लिही।"
            ),
            AppTourOverlay.Step(
                b.cardQr,
                "QR कोड जाँचव",
                "भुगतान करे के पहिली QR कोड ल इहाँ स्कैन करके जाँच लव।"
            ),
            AppTourOverlay.Step(
                navDetect,
                "इहाँ ले सीधा जाँच पाव",
                "नीचे के 'जाँच' टैब ले आप कोनो बेरा सीधा मैसेज, कॉल अउ QR जाँच म जा सकत हव।"
            ),
            AppTourOverlay.Step(
                navMore,
                "अनुमति अउ सेटिंग इहाँ",
                "'अधिक' टैब म जाके आप SMS, कॉल अउ आवाज के अनुमति ल चालू-बंद कर सकत हव।"
            ),
        )
        AppTourOverlay(this).start(steps) {
            prefs.edit().putBoolean("shown", true).apply()
        }
    }

    private fun applyLanguage() {
        b.langBtn.text = T.label()
        b.titleText.text = T.appTitle
        b.subtitleText.text = T.headerSubtitle
        b.heroTitle.text = T.heroTitle
        b.heroSubtitle.text = T.heroSubtitle
        b.howToUseStep1.text = T.howToUseStep1
        b.howToUseStep2.text = T.howToUseStep2
        b.howToUseStep3.text = T.howToUseStep3
        b.cardMessageTitle.text = T.detectMessageTitle
        b.cardMessageDesc.text = T.detectMessageDesc
        b.cardCallTitle.text = T.detectCallTitle
        b.cardCallDesc.text = T.detectCallDesc
        b.cardQrTitle.text = T.detectQrTitle
        b.cardQrDesc.text = T.detectQrDesc
        b.cardReportTitle.text = T.reportFraudTitle
        b.cardReportDesc.text = T.reportFraudDesc
        b.cardLearnTitle.text = T.safetyLessonsTitle
        b.cardLearnDesc.text = T.safetyLessonsDesc
        b.cardTrendsTitle.text = T.scamTrendsTitle
        b.cardTrendsDesc.text = T.scamTrendsDesc
        b.emergencyTitleText.text = T.emergencyTitle
        b.emergencySubtitleText.text = T.emergencySubtitle
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.HOME)
    }
}
