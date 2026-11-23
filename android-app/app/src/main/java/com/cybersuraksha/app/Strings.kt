package com.cybersuraksha.app

enum class Lang { EN, HI, CG }

/** Same manual trilingual-swap pattern as the website (no Android resource-qualifier
 *  juggling for Chhattisgarhi, which has no standard locale Android recognises well —
 *  just a plain data lookup, same as data-en/data-hi on the site). */
object T {
    var lang: Lang = Lang.HI

    fun next() {
        lang = when (lang) { Lang.HI -> Lang.EN; Lang.EN -> Lang.CG; Lang.CG -> Lang.HI }
    }

    fun label(): String = when (lang) { Lang.EN -> "EN"; Lang.HI -> "हिं"; Lang.CG -> "छग" }

    private fun pick(en: String, hi: String, cg: String): String = when (lang) {
        Lang.EN -> en; Lang.HI -> hi; Lang.CG -> cg
    }

    val appTitle get() = pick("Cyber Suraksha", "साइबर सुरक्षा", "साइबर सुरक्षा")
    val subtitle get() = pick(
        "On-device SMS and call scam detector",
        "डिवाइस पर ही SMS एवं कॉल स्कैम डिटेक्टर",
        "डिवाइस म ही SMS अउ कॉल स्कैम डिटेक्टर"
    )
    val statusTitle get() = pick("Protection status", "सुरक्षा स्थिति", "सुरक्षा स्थिति")
    val smsOn get() = pick("SMS auto-scan: on", "SMS ऑटो-स्कैन: चालू", "SMS ऑटो-स्कैन: चालू")
    val smsOff get() = pick("SMS auto-scan: off", "SMS ऑटो-स्कैन: बंद", "SMS ऑटो-स्कैन: बंद")
    val callOn get() = pick("Call screening: on", "कॉल स्क्रीनिंग: चालू", "कॉल स्क्रीनिंग: चालू")
    val callOff get() = pick("Call screening: off", "कॉल स्क्रीनिंग: बंद", "कॉल स्क्रीनिंग: बंद")
    val enable get() = pick("Enable", "चालू करें", "चालू करव")
    val checkLabel get() = pick("Paste a message to check", "जाँचने हेतु मैसेज पेस्ट करें", "जाँचे बर मैसेज पेस्ट करव")
    val hint get() = pick("Paste SMS text here...", "यहाँ SMS टेक्स्ट पेस्ट करें...", "इहाँ SMS टेक्स्ट पेस्ट करव...")
    val checkBtn get() = pick("Check message", "मैसेज जाँचें", "मैसेज जाँचव")
    val historyLabel get() = pick("Recent activity", "हाल की गतिविधि", "हाल के गतिविधि")
    val qrCardLabel get() = pick("QR Code Checker — scan before you pay", "QR कोड चेकर — भुगतान से पहले स्कैन करें", "QR कोड चेकर — भुगतान के पहिली स्कैन करव")
    val emptyHistory get() = pick(
        "Nothing scanned yet — incoming SMS will appear here automatically.",
        "अभी तक कुछ स्कैन नहीं हुआ — आने वाले SMS यहाँ अपने-आप दिखेंगे।",
        "अभी तक कुछू स्कैन नइ होइस — आवत SMS इहाँ अपने आप देखाही।"
    )

    val verdictScam get() = pick("Likely SCAM", "संभावित स्कैम", "संभावित स्कैम")
    val verdictSuspicious get() = pick("Suspicious", "संदिग्ध", "संदेहास्पद")
    val verdictGenuine get() = pick("Appears genuine", "असली प्रतीत होता है", "असली लगथे")
    val emptyInput get() = pick("Paste a message first.", "पहले कोई मैसेज पेस्ट करें।", "पहिली एक मैसेज पेस्ट करव।")

    fun categoryLabel(cat: String): String {
        val hi = mapOf(
            "otp" to "OTP/पिन स्कैम", "kyc" to "फर्ज़ी KYC", "prize" to "लॉटरी/इनाम स्कैम",
            "link" to "संदिग्ध लिंक", "payment" to "फर्ज़ी रिफंड/भुगतान स्कैम",
            "courier" to "कूरियर/कस्टम स्कैम", "arrest" to "'डिजिटल अरेस्ट' स्कैम",
            "job" to "फर्ज़ी नौकरी ऑफर", "investment" to "निवेश स्कैम",
            "loan" to "फर्ज़ी लोन ऑफर", "idtheft" to "पहचान चोरी"
        )
        return when (lang) {
            Lang.EN -> Classifier.categoryLabel(cat)
            else -> hi[cat] ?: cat
        }
    }

    fun smsScannedLine(sender: String) = pick("SMS from $sender", "$sender से SMS", "$sender ले SMS")
    fun callScreenedLine(number: String) = pick("Call from $number", "$number से कॉल", "$number ले कॉल")

    // ---- QR checker ----
    val qrTitle get() = pick("QR Code Checker", "QR कोड चेकर", "QR कोड चेकर")
    val qrSubtitle get() = pick(
        "Scan or upload a QR code before paying",
        "भुगतान से पहले QR कोड स्कैन या अपलोड करें",
        "भुगतान के पहिली QR कोड स्कैन या अपलोड करव"
    )
    val qrTabCamera get() = pick("Camera", "कैमरा", "कैमरा")
    val qrTabUpload get() = pick("Upload", "अपलोड", "अपलोड")
    val qrStartCamera get() = pick("Start camera", "कैमरा शुरू करें", "कैमरा चालू करव")
    val qrStopCamera get() = pick("Stop camera", "कैमरा बंद करें", "कैमरा बंद करव")
    val qrUploadBtn get() = pick("Upload QR image", "QR छवि अपलोड करें", "QR फोटो अपलोड करव")
    val qrScanHint get() = pick(
        "Point your camera at the QR code.",
        "अपना कैमरा QR कोड की ओर रखें।",
        "अपन कैमरा QR कोड कोती रखव।"
    )
    val qrNoQrFound get() = pick(
        "No QR code found in that image. Try a clearer photo.",
        "इस छवि में कोई QR कोड नहीं मिला। बेहतर फ़ोटो आज़माएँ।",
        "ए फोटो म कोनो QR कोड नइ मिलिस। बने फोटो आजमाव।"
    )
    val qrCameraPermNeeded get() = pick(
        "Camera permission is needed to scan a QR code.",
        "QR कोड स्कैन करने हेतु कैमरा अनुमति चाहिए।",
        "QR कोड स्कैन करे बर कैमरा अनुमति चाही।"
    )
    val qrVerdictDanger get() = pick("Dangerous — do not pay or click", "खतरनाक — भुगतान/क्लिक न करें", "खतरनाक — भुगतान/क्लिक झन करव")
    val qrVerdictWarn get() = pick("Suspicious — proceed with caution", "संदिग्ध — सावधानी से आगे बढ़ें", "संदेहास्पद — सावधानी ले आघू बढ़व")
    val qrVerdictSafe get() = pick("No known danger found", "कोई ज्ञात खतरा नहीं मिला", "कोनो जानत खतरा नइ मिलिस")
    val qrTypeUpi get() = pick("UPI payment QR", "UPI भुगतान QR", "UPI भुगतान QR")
    val qrTypeUrl get() = pick("Web link QR", "वेब लिंक QR", "वेब लिंक QR")
    val qrTypeOther get() = pick("Other QR content", "अन्य QR सामग्री", "अउ QR सामग्री")
    val qrWhatNow get() = pick("What to do now", "अभी क्या करें", "अभी का करव")
    val qrStepConfirmName get() = pick(
        "Confirm the payee name out loud with the shopkeeper before paying",
        "भुगतान से पहले दुकानदार से नाम बोलकर पुष्टि करें",
        "भुगतान के पहिली दुकानदार ले नाम बोलके पुष्टि करव"
    )
    val qrStepTellShopkeeper get() = pick(
        "If this looks like a sticker pasted over a shop's real QR, tell the shopkeeper",
        "अगर यह स्टिकर किसी दुकान के असली QR पर चिपका लगे, तो दुकानदार को बताएँ",
        "यदि ए स्टिकर कोनो दुकान के असली QR उपर चिपकाय लगत हे, त दुकानदार ला बताव"
    )
    val qrStepPayCash get() = pick("If in doubt, pay with cash instead", "संदेह होने पर कैश से भुगतान करें", "संदेह होय त कैश ले भुगतान करव")
    val qrStepDontOpen get() = pick("Don't open this link", "इस लिंक को न खोलें", "ए लिंक ला झन खोलव")
    val qrStepDontEnter get() = pick("Don't enter any details, OTP or PIN", "कोई भी जानकारी, OTP या पिन न भरें", "कोनो जानकारी, OTP या पिन झन भरव")
    val qrSevRisk get() = pick("Risk", "खतरा", "खतरा")
    val qrSevCaution get() = pick("Caution", "सावधानी", "सावधानी")
    val qrSevInfo get() = pick("Info", "जानकारी", "जानकारी")
    val qrFieldsNone get() = pick("No decoded fields.", "कोई डिकोडेड फ़ील्ड नहीं।", "कोनो डिकोडेड फील्ड नइहे।")
}
