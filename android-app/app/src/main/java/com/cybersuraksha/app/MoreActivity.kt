package com.cybersuraksha.app

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cybersuraksha.app.databinding.ActivityMoreBinding

class MoreActivity : AppCompatActivity() {

    private lateinit var b: ActivityMoreBinding

    private val requestSmsPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshStatus()
    }
    private val requestCallRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshStatus()
    }
    private val requestLiveWarningPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        // Live call warning needs BOTH: RECORD_AUDIO to listen, and READ_PHONE_STATE so the
        // system will even deliver the PHONE_STATE broadcast that starts the listening service
        // in the first place — without READ_PHONE_STATE the broadcast is silently never
        // delivered at all, which was a real bug here (caught via a real on-device call test
        // producing no warning, then confirmed via `adb shell dumpsys package`: READ_PHONE_STATE
        // showed granted=false even though RECORD_AUDIO alone had been granted).
        val allGranted = grants.values.all { it }
        LiveCallWarning.setEnabled(this, allGranted)
        refreshStatus()
        if (!allGranted) {
            Toast.makeText(this, T.liveWarningNeedsBothPerms, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMoreBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.backBtn.setOnClickListener { finish() }
        b.langBtn.setOnClickListener { T.next(); applyLanguage() }
        b.smsEnableBtn.setOnClickListener {
            requestSmsPermissions.launch(arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS))
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

        NavBar.wire(this, b.navBottom.root, NavBar.Tab.MORE)
        applyLanguage()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun applyLanguage() {
        b.langBtn.text = T.label()
        b.titleText.text = T.moreTitle
        b.statusTitle.text = T.statusTitle
        b.smsEnableBtn.text = T.enable
        b.callEnableBtn.text = T.enable
        b.liveWarningTitle.text = T.liveCallWarningTitle
        b.liveWarningDesc.text = T.liveCallWarningDesc
        b.aboutTitleText.text = T.aboutTitle
        b.aboutDescText.text = T.aboutDesc
        refreshStatus()
        NavBar.wire(this, b.navBottom.root, NavBar.Tab.MORE)
    }

    private fun refreshStatus() {
        val smsGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        b.smsStatusText.text = if (smsGranted) T.smsOn else T.smsOff
        b.smsEnableBtn.visibility = if (smsGranted) android.view.View.GONE else android.view.View.VISIBLE
        tintDot(b.smsDot, smsGranted)

        var callActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val telecom = getSystemService(TELECOM_SERVICE) as android.telecom.TelecomManager
            callActive = telecom.defaultDialerPackage == packageName ||
                (getSystemService(Context.ROLE_SERVICE) as RoleManager).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
        b.callStatusText.text = if (callActive) T.callOn else T.callOff
        b.callEnableBtn.visibility = if (callActive) android.view.View.GONE else android.view.View.VISIBLE
        tintDot(b.callDot, callActive)

        // Live call warning is only really "on" if both permissions are still actually granted —
        // e.g. the user may have revoked one from system Settings since turning it on here.
        val liveWarningReallyOn = LiveCallWarning.isEnabled(this) && hasLiveWarningPermissions()
        if (liveWarningReallyOn != LiveCallWarning.isEnabled(this)) {
            LiveCallWarning.setEnabled(this, false)
        }
        b.liveWarningSwitch.setOnCheckedChangeListener(null)
        b.liveWarningSwitch.isChecked = liveWarningReallyOn
        b.liveWarningSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (hasLiveWarningPermissions()) {
                    LiveCallWarning.setEnabled(this, true)
                } else {
                    requestLiveWarningPermissions.launch(
                        arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.READ_PHONE_STATE)
                    )
                }
            } else {
                LiveCallWarning.setEnabled(this, false)
            }
        }
    }

    private fun hasLiveWarningPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun tintDot(dot: android.view.View, on: Boolean) {
        val color = ContextCompat.getColor(this, if (on) R.color.safe else R.color.text_muted)
        (dot.background.mutate() as android.graphics.drawable.GradientDrawable).setColor(color)
    }
}
