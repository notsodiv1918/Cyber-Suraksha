package com.cybersuraksha.app

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * We deliberately never auto-block a call here: we have no real caller-reputation dataset
 * (the training data is message text, not phone numbers), so pretending to auto-detect a
 * "scam number" would be a false claim. Instead this logs every incoming number to the same
 * history feed as SMS/manual checks and nudges the user to run the call-transcript check
 * (same classifier as the website's Call Scam Detector) once the call is over.
 */
class CallScreenService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: "unknown"

        HistoryStore.add(
            applicationContext,
            HistoryItem("call", number, "", "unscreened", System.currentTimeMillis())
        )
        Notifier.notifyVerdict(
            applicationContext,
            "Incoming call: $number",
            "Not in your known contacts. If they claim to be a bank, police or government official and ask for OTP/payment, hang up and check the transcript in the app.",
            "suspicious"
        )

        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}
