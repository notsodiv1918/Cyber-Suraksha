package com.cybersuraksha.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cybersuraksha.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OnboardingActivity.shouldShow(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
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
    }

    override fun onResume() {
        super.onResume()
        applyLanguage()
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
