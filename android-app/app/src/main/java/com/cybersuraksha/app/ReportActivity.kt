package com.cybersuraksha.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cybersuraksha.app.databinding.ActivityReportBinding

class ReportActivity : AppCompatActivity() {

    private lateinit var b: ActivityReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReportBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.backBtn.setOnClickListener { finish() }
        b.langBtn.setOnClickListener { T.next(); applyLanguage() }
        b.r1930Btn.setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))) }
        b.rPortalBtn.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))) }
        b.rChakshuBtn.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sancharsaathi.gov.in/sfc/"))) }

        NavBar.wire(this, b.navBottom.root, NavBar.Tab.REPORT)
        applyLanguage()
    }

    private fun applyLanguage() {
        b.langBtn.text = T.label()
        b.titleText.text = T.reportTitle
        b.reportSubText.text = T.reportSub
        b.r1930Title.text = T.report1930Title
        b.r1930Desc.text = T.report1930Desc
        b.r1930BtnText.text = T.reportCallBtn
        b.rPortalTitle.text = T.reportPortalTitle
        b.rPortalDesc.text = T.reportPortalDesc
        b.rPortalBtnText.text = T.reportOpenBtn
        b.rChakshuTitle.text = T.reportChakshuTitle
        b.rChakshuDesc.text = T.reportChakshuDesc
        b.rChakshuBtnText.text = T.reportOpenBtn
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.REPORT)
    }
}
