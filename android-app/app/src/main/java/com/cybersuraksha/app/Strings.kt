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
}
