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
    val servicesEyebrow get() = pick("Core services", "मुख्य सेवाएँ", "मुख्य सेवाएँ")
    val smsOn get() = pick("SMS auto-scan: on", "SMS ऑटो-स्कैन: चालू", "SMS ऑटो-स्कैन: चालू")
    val smsOff get() = pick("SMS auto-scan: off", "SMS ऑटो-स्कैन: बंद", "SMS ऑटो-स्कैन: बंद")
    val callOn get() = pick("Call screening: on", "कॉल स्क्रीनिंग: चालू", "कॉल स्क्रीनिंग: चालू")
    val callOff get() = pick("Call screening: off", "कॉल स्क्रीनिंग: बंद", "कॉल स्क्रीनिंग: बंद")
    val enable get() = pick("Enable", "चालू करें", "चालू करव")
    val checkLabel get() = pick("Paste a message to check", "जाँचने हेतु मैसेज पेस्ट करें", "जाँचे बर मैसेज पेस्ट करव")
    val hint get() = pick("Paste SMS text here...", "यहाँ SMS टेक्स्ट पेस्ट करें...", "इहाँ SMS टेक्स्ट पेस्ट करव...")
    val checkBtn get() = pick("Check message", "मैसेज जाँचें", "मैसेज जाँचव")
    val checkCallBtn get() = pick("Check Call", "कॉल जाँचें", "कॉल जाँचव")
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

    // ---- bottom nav ----
    val navHome get() = pick("Home", "होम", "होम")
    val navDetect get() = pick("Detect", "जाँच", "जाँच")
    val navLearn get() = pick("Learn", "सीखें", "सीखव")
    val navReport get() = pick("Report", "शिकायत", "शिकायत")
    val navMore get() = pick("More", "अधिक", "अउ")

    // ---- home: header / hero ----
    val headerSubtitle get() = pick("Digital Financial Literacy", "डिजिटल वित्तीय साक्षरता", "डिजिटल वित्तीय साक्षरता")
    val heroTitle get() = pick("Stay safe from\ndigital fraud", "डिजिटल धोखाधड़ी से\nसुरक्षित रहें", "डिजिटल धोखाधड़ी ले\nसुरक्षित रहव")
    val heroSubtitle get() = pick(
        "Check messages, calls and QR codes for scams — fully on your device, even offline.",
        "स्कैम हेतु मैसेज, कॉल एवं QR कोड जाँचें — पूरी तरह आपके डिवाइस पर, ऑफ़लाइन भी।",
        "स्कैम बर मैसेज, कॉल अउ QR कोड जाँचव — पूरा आपके डिवाइस म, ऑफ़लाइन घलो।"
    )
    val howToUseTitle get() = pick("How to use", "कैसे उपयोग करें", "कइसे उपयोग करव")
    val howToUseStep1 get() = pick("Paste or scan", "पेस्ट या स्कैन करें", "पेस्ट या स्कैन करव")
    val howToUseStep2 get() = pick("We check it on-device", "हम डिवाइस पर ही जाँचते हैं", "हम डिवाइस म ही जाँचथन")
    val howToUseStep3 get() = pick("See the verdict instantly", "तुरंत परिणाम देखें", "तुरते परिणाम देखव")

    val detectMessageTitle get() = pick("Check Message", "मैसेज जाँचें", "मैसेज जाँचव")
    val detectMessageDesc get() = pick("Detect scam SMS or WhatsApp", "स्कैम SMS या व्हाट्सऐप पहचानें", "स्कैम SMS या व्हाट्सऐप पहिचानव")
    val detectCallTitle get() = pick("Check Call", "कॉल जाँचें", "कॉल जाँचव")
    val detectCallDesc get() = pick("Analyse a suspicious call", "संदिग्ध कॉल का विश्लेषण करें", "संदेहास्पद कॉल के विश्लेषण करव")
    val detectQrTitle get() = pick("Check QR", "QR जाँचें", "QR जाँचव")
    val detectQrDesc get() = pick("Scan before you pay", "भुगतान से पहले स्कैन करें", "भुगतान के पहिली स्कैन करव")

    val reportFraudTitle get() = pick("Report Fraud", "धोखाधड़ी की शिकायत", "धोखाधड़ी के शिकायत")
    val reportFraudDesc get() = pick("1930, cybercrime.gov.in, Chakshu", "1930, cybercrime.gov.in, चक्षु", "1930, cybercrime.gov.in, चक्षु")
    val safetyLessonsTitle get() = pick("Safety Lessons", "सुरक्षा पाठ", "सुरक्षा पाठ")
    val safetyLessonsDesc get() = pick("Short lessons & tips", "छोटे पाठ एवं सुझाव", "छोटे पाठ अउ सुझाव")
    val scamTrendsTitle get() = pick("Scam Trends", "स्कैम ट्रेंड", "स्कैम ट्रेंड")
    val scamTrendsDesc get() = pick("What's trending in your area", "आपके क्षेत्र में क्या चल रहा है", "आपके इलाका म का चलत हे")

    val emergencyTitle get() = pick("Need immediate help?", "तुरंत मदद चाहिए?", "तुरते मदद चाही?")
    val emergencySubtitle get() = pick(
        "Call the National Cybercrime Helpline",
        "राष्ट्रीय साइबर अपराध हेल्पलाइन पर कॉल करें",
        "राष्ट्रीय साइबर अपराध हेल्पलाइन म कॉल करव"
    )

    // ---- message / call detector screens ----
    val msgDetectorTitle get() = pick("Message Detector", "मैसेज डिटेक्टर", "मैसेज डिटेक्टर")
    val msgDetectorHeading get() = pick("Check if a message is a scam", "जाँचें कि मैसेज स्कैम है या नहीं", "जाँचव कि मैसेज स्कैम हे या नइ")
    val msgDetectorSub get() = pick(
        "Paste any SMS, WhatsApp message or link below.",
        "नीचे कोई भी SMS, व्हाट्सऐप मैसेज या लिंक पेस्ट करें।",
        "तरी कोनो भी SMS, व्हाट्सऐप मैसेज या लिंक पेस्ट करव।"
    )
    val msgPlaceholder get() = pick("Paste the message here...", "यहाँ मैसेज पेस्ट करें...", "इहाँ मैसेज पेस्ट करव...")
    val callDetectorTitle get() = pick("Call Detector", "कॉल डिटेक्टर", "कॉल डिटेक्टर")
    val callDetectorHeading get() = pick("Check if a call is a scam", "जाँचें कि कॉल स्कैम है या नहीं", "जाँचव कि कॉल स्कैम हे या नइ")
    val callDetectorSub get() = pick(
        "Paste or type what the caller said, as best you remember.",
        "कॉलर ने जो कहा वह जितना याद हो, पेस्ट या टाइप करें।",
        "कॉलर जे कहिस ओ जतका याद हे, पेस्ट या टाइप करव।"
    )
    val callPlaceholder get() = pick("Type the call transcript here...", "यहाँ कॉल का विवरण टाइप करें...", "इहाँ कॉल के बात टाइप करव...")
    val uploadPhotoVideo get() = pick("Upload photo / video", "फोटो / वीडियो अपलोड करें", "फोटो / वीडियो अपलोड करव")
    val ocrRunning get() = pick("Reading text from the file…", "फ़ाइल से टेक्स्ट पढ़ा जा रहा है…", "फाइल ले टेक्स्ट पढ़े जात हे…")
    val ocrNoText get() = pick(
        "No readable text found in that file. Try a clearer photo.",
        "इस फ़ाइल में कोई पठनीय टेक्स्ट नहीं मिला। बेहतर फ़ोटो आज़माएँ।",
        "ए फाइल म कोनो पढ़े जाय वाला टेक्स्ट नइ मिलिस। बने फोटो आजमाव।"
    )
    val uploadAudio get() = pick("Upload call recording", "कॉल रिकॉर्डिंग अपलोड करें", "कॉल रिकॉर्डिंग अपलोड करव")
    val audioPlayHint get() = pick(
        "Listen to the recording, then type what the caller said below.",
        "रिकॉर्डिंग सुनें, फिर नीचे कॉलर ने जो कहा वह टाइप करें।",
        "रिकॉर्डिंग सुनव, फिर तरी कॉलर जे कहिस ओ टाइप करव।"
    )
    val audioPlay get() = pick("Play", "चलाएँ", "चलाव")
    val audioPause get() = pick("Pause", "रोकें", "रोकव")
    val trySample get() = pick("Try a sample message", "उदाहरण मैसेज आज़माएँ", "उदाहरण मैसेज आजमाव")
    val trySampleCall get() = pick("Try a sample call script", "उदाहरण कॉल स्क्रिप्ट आज़माएँ", "उदाहरण कॉल स्क्रिप्ट आजमाव")
    val charCount get() = pick("characters", "अक्षर", "अक्षर")

    val sampleKyc get() = pick("Your KYC update is pending, click the link now or your account will be blocked.", "आपका KYC अपडेट लंबित है, अभी लिंक पर क्लिक करें वरना खाता ब्लॉक हो जाएगा।", "आपके KYC अपडेट लंबित हे, अभी लिंक म क्लिक करव नइते खाता ब्लॉक हो जाही।")
    val samplePrize get() = pick("Congratulations! You won ₹25,00,000 in a lucky draw. Share OTP to claim.", "बधाई हो! आपने लकी ड्रॉ में ₹25,00,000 जीते हैं। दावा करने हेतु OTP साझा करें।", "बधाई हो! तुमन लकी ड्रॉ म ₹25,00,000 जीतेव। दावा करे बर OTP बताव।")
    val sampleCourier get() = pick("Your parcel is held at customs. Pay ₹499 fine immediately to release it.", "आपका पार्सल कस्टम में रुका है। इसे छुड़ाने हेतु तुरंत ₹499 जुर्माना भरें।", "तुम्हर पार्सल कस्टम म रुकेहे। ए ला छोड़ाय बर तुरते ₹499 जुर्माना भरव।")
    val sampleArrest get() = pick("This is TRAI. Your number is linked to illegal activity. Stay on the line or face digital arrest.", "यह TRAI है। आपका नंबर अवैध गतिविधि से जुड़ा है। लाइन पर बने रहें वरना डिजिटल अरेस्ट होगा।", "ए TRAI हे। तुम्हर नंबर अवैध काम ले जुड़े हे। लाइन म बने रहव नइते डिजिटल अरेस्ट होही।")
    val sampleOtpCall get() = pick("Sir, this is your bank. Your card is blocked, please tell me the OTP you just received to unblock it.", "सर, मैं आपका बैंक बोल रहा हूँ। आपका कार्ड ब्लॉक हो गया है, अनब्लॉक करने हेतु अभी आया OTP बताइए।", "सर, मैं तुम्हर बैंक बोलत हंव। तुम्हर कार्ड ब्लॉक हो गे हे, अनब्लॉक करे बर अभी आय OTP बताव।")
    val sampleBankGenuine get() = pick("Rs 500 debited from your account on 12-Jun for UPI txn. Avl bal Rs 4,200.", "आपके खाते से 12-जून को UPI लेनदेन हेतु ₹500 कटे। उपलब्ध शेष ₹4,200।", "तुम्हर खाता ले 12-जून ल UPI लेनदेन बर ₹500 कटिस। उपलब्ध शेष ₹4,200।")

    // ---- result ----
    val resultLikelyScam get() = pick("Likely Scam", "संभावित स्कैम", "संभावित स्कैम")
    val resultSuspicious get() = pick("Suspicious", "संदिग्ध", "संदेहास्पद")
    val resultGenuine get() = pick("Appears Genuine", "असली प्रतीत होता है", "असली लगथे")
    val riskScore get() = pick("Risk Score", "जोखिम स्कोर", "जोखिम स्कोर")
    val whySuspicious get() = pick("Why it's suspicious?", "यह संदिग्ध क्यों है?", "ए संदेहास्पद काबर हे?")
    val whySafe get() = pick("No known red flags found in this text.", "इस टेक्स्ट में कोई ज्ञात खतरे का संकेत नहीं मिला।", "ए टेक्स्ट म कोनो जानत खतरा के संकेत नइ मिलिस।")
    val reportThis get() = pick("Report This", "रिपोर्ट करें", "रिपोर्ट करव")
    val checkAnother get() = pick("Check Another", "दूसरा जाँचें", "दूसर जाँचव")
    val sharePattern get() = pick("Share with district (optional)", "जिले के साथ साझा करें (वैकल्पिक)", "जिला के साथ साझा करव (वैकल्पिक)")
    val sharePatternHint get() = pick(
        "Help officials spot this pattern spreading in your district — sends only this text and the district you pick.",
        "अधिकारियों को आपके जिले में फैलते पैटर्न का पता लगाने में मदद करें — केवल यह टेक्स्ट एवं चुना गया जिला भेजा जाता है।",
        "अधिकारी मन ला तुम्हर जिला म फैलत पैटर्न के पता लगाय म मदद करव — सिरिफ ए टेक्स्ट अउ चुने गे जिला भेजे जाथे।"
    )
    val shareSending get() = pick("Sending…", "भेजा जा रहा है…", "भेजे जात हे…")
    val shareSent get() = pick("Shared — thank you.", "साझा किया गया — धन्यवाद।", "साझा होगे — धन्यवाद।")
    val shareError get() = pick(
        "Could not reach the alert server (needs backend/app.py running).",
        "अलर्ट सर्वर से संपर्क नहीं हो सका (backend/app.py चालू होना चाहिए)।",
        "अलर्ट सर्वर ले संपर्क नइ होइस (backend/app.py चालू होना चाही)।"
    )
    val chooseDistrict get() = pick("Choose district", "जिला चुनें", "जिला चुनव")
    val onDeviceNote get() = pick(
        "Analysed on your device. No message is sent to the internet.",
        "आपके डिवाइस पर विश्लेषित। कोई मैसेज इंटरनेट पर नहीं भेजा गया।",
        "तुम्हर डिवाइस म विश्लेषित। कोनो मैसेज इंटरनेट म नइ भेजे गिस।"
    )

    // ---- per-screen history ----
    val historyMessages get() = pick("Recent message checks", "हाल की मैसेज जाँच", "हाल के मैसेज जाँच")
    val historyCalls get() = pick("Recent call checks", "हाल की कॉल जाँच", "हाल के कॉल जाँच")
    val historyQr get() = pick("Recent QR checks", "हाल की QR जाँच", "हाल के QR जाँच")
    val emptyHistoryMessages get() = pick(
        "No messages checked yet — incoming SMS will appear here automatically.",
        "अभी तक कोई मैसेज नहीं जाँचा गया — आने वाले SMS यहाँ अपने-आप दिखेंगे।",
        "अभी तक कोनो मैसेज नइ जाँचे गिस — आवत SMS इहाँ अपने आप देखाही।"
    )
    val emptyHistoryCalls get() = pick(
        "No calls checked yet — screened calls will appear here automatically.",
        "अभी तक कोई कॉल नहीं जाँची गई — स्क्रीन की गई कॉलें यहाँ अपने-आप दिखेंगी।",
        "अभी तक कोनो कॉल नइ जाँचे गिस — स्क्रीन करे गे कॉल इहाँ अपने आप देखाही।"
    )
    val emptyHistoryQr get() = pick("No QR codes checked yet.", "अभी तक कोई QR कोड नहीं जाँचा गया।", "अभी तक कोनो QR कोड नइ जाँचे गिस।")
    val callUnscreened get() = pick("Not yet checked", "अभी तक नहीं जाँची गई", "अभी तक नइ जाँचे गिस")

    // ---- report / emergency screen ----
    val reportTitle get() = pick("Report Fraud", "धोखाधड़ी की शिकायत", "धोखाधड़ी के शिकायत")
    val reportSub get() = pick("Lost money or spotted a scam? Report it right away.", "पैसे गँवाए या स्कैम दिखा? तुरंत शिकायत करें।", "पइसा गंवायेव या स्कैम दिखिस? तुरते शिकायत करव।")
    val report1930Title get() = pick("National Cybercrime Helpline", "राष्ट्रीय साइबर अपराध हेल्पलाइन", "राष्ट्रीय साइबर अपराध हेल्पलाइन")
    val report1930Desc get() = pick(
        "Call 1930 immediately if you lost money — banks can freeze transactions within the Golden Hour.",
        "पैसे गँवाने पर तुरंत 1930 पर कॉल करें — बैंक 'गोल्डन ऑवर' में लेनदेन रोक सकते हैं।",
        "पइसा गंवाय म तुरते 1930 म कॉल करव — बैंक 'गोल्डन आवर' म लेनदेन रोक सकथें।"
    )
    val reportCallBtn get() = pick("Call 1930", "1930 पर कॉल करें", "1930 म कॉल करव")
    val reportPortalTitle get() = pick("cybercrime.gov.in", "cybercrime.gov.in", "cybercrime.gov.in")
    val reportPortalDesc get() = pick(
        "File a detailed complaint on the official National Cybercrime Reporting Portal.",
        "आधिकारिक राष्ट्रीय साइबर अपराध रिपोर्टिंग पोर्टल पर विस्तृत शिकायत दर्ज करें।",
        "आधिकारिक राष्ट्रीय साइबर अपराध रिपोर्टिंग पोर्टल म विस्तृत शिकायत दर्ज करव।"
    )
    val reportOpenBtn get() = pick("Open portal", "पोर्टल खोलें", "पोर्टल खोलव")
    val reportChakshuTitle get() = pick("Chakshu (fraud call/SMS)", "चक्षु (फ्रॉड कॉल/SMS)", "चक्षु (फ्रॉड कॉल/SMS)")
    val reportChakshuDesc get() = pick(
        "Report a fraud communication (call/SMS) to DoT's Chakshu portal within 30 days.",
        "DoT के चक्षु पोर्टल पर फ्रॉड कॉल/SMS की शिकायत 30 दिनों के भीतर दर्ज करें।",
        "DoT के चक्षु पोर्टल म फ्रॉड कॉल/SMS के शिकायत 30 दिन के भीतर दर्ज करव।"
    )

    // ---- learn screen ----
    val learnTitle get() = pick("Safety Lessons", "सुरक्षा पाठ", "सुरक्षा पाठ")
    val filterAll get() = pick("All", "सभी", "सब्भो")
    val filterUpi get() = pick("UPI & Payments", "UPI एवं भुगतान", "UPI अउ भुगतान")
    val filterKyc get() = pick("KYC", "KYC", "KYC")
    val filterCalls get() = pick("Fraud Calls", "फ्रॉड कॉल", "फ्रॉड कॉल")

    // ---- scam trends screen ----
    val trendsTitle get() = pick("Scam Trends", "स्कैम ट्रेंड", "स्कैम ट्रेंड")
    val trendsRegionAll get() = pick("All Chhattisgarh", "संपूर्ण छत्तीसगढ़", "पूरा छत्तीसगढ़")
    val trendsHeading get() = pick("Top scam types in your area", "आपके क्षेत्र में शीर्ष स्कैम प्रकार", "आपके इलाका म शीर्ष स्कैम किसिम")
    fun trendsNote(total: Int) = pick(
        "Based on $total real complaint records (NCRP). District split is proportionally synthesised — see About.",
        "$total वास्तविक शिकायत रिकॉर्ड (NCRP) पर आधारित। जिलेवार विभाजन आनुपातिक रूप से संश्लेषित है — परिचय देखें।",
        "$total वास्तविक शिकायत रिकॉर्ड (NCRP) म आधारित। जिलावार बँटवारा संश्लेषित हे — परिचय देखव।"
    )

    // ---- more / protection status screen ----
    val moreTitle get() = pick("More", "अधिक", "अउ")
    val liveCallWarningTitle get() = pick("Live call warning (beta)", "लाइव कॉल चेतावनी (बीटा)", "लाइव कॉल चेतावनी (बीटा)")
    val liveCallWarningDesc get() = pick(
        "Listens through the mic during a call and warns you immediately if it sounds like a scam. Works best on speakerphone; may use mobile data if no offline voice pack is installed.",
        "कॉल के दौरान माइक्रोफ़ोन से सुनता है और स्कैम जैसा लगने पर तुरंत चेतावनी देता है। स्पीकरफ़ोन पर सबसे बेहतर काम करता है; ऑफ़लाइन वॉइस पैक न होने पर मोबाइल डेटा उपयोग हो सकता है।",
        "कॉल के बेरा माइक्रोफोन ले सुनथे अउ स्कैम जइसे लगे म तुरते चेतावनी देथे। स्पीकरफोन म सबले बने काम करथे; ऑफलाइन वॉइस पैक नइ रहे म मोबाइल डेटा लगे सकथे।"
    )
    val liveWarningActiveTitle get() = pick("Live call warning active", "लाइव कॉल चेतावनी सक्रिय", "लाइव कॉल चेतावनी सक्रिय")
    val liveWarningActiveBody get() = pick(
        "Listening for scam red-flags during this call.",
        "इस कॉल के दौरान स्कैम के संकेतों हेतु सुन रहा है।",
        "ए कॉल के बेरा स्कैम के संकेत बर सुनत हे।"
    )
    val liveWarningAlertTitle get() = pick("Warning: this call sounds like a scam", "चेतावनी: यह कॉल स्कैम जैसा लगता है", "चेतावनी: ए कॉल स्कैम जइसे लगत हे")
    val liveWarningUnavailableTitle get() = pick("Live call warning unavailable", "लाइव कॉल चेतावनी उपलब्ध नहीं", "लाइव कॉल चेतावनी उपलब्ध नइहे")
    val liveWarningUnavailableBody get() = pick(
        "This device doesn't support on-device speech recognition.",
        "यह डिवाइस ऑन-डिवाइस वाक् पहचान समर्थित नहीं करता।",
        "ए डिवाइस ऑन-डिवाइस आवाज पहिचान समर्थित नइ करय।"
    )
    val aboutTitle get() = pick("About this app", "इस ऐप के बारे में", "ए ऐप के बारे म")
    val aboutDesc get() = pick(
        "Cyber Suraksha is an offline, on-device scam detector for SMS, calls and QR codes — a hackathon prototype, not an official government app.",
        "साइबर सुरक्षा SMS, कॉल एवं QR कोड हेतु एक ऑफ़लाइन, ऑन-डिवाइस स्कैम डिटेक्टर है — यह एक हैकाथॉन प्रोटोटाइप है, आधिकारिक सरकारी ऐप नहीं।",
        "साइबर सुरक्षा SMS, कॉल अउ QR कोड बर एक ऑफ़लाइन, ऑन-डिवाइस स्कैम डिटेक्टर हे — ए एक हैकाथॉन प्रोटोटाइप हे, आधिकारिक सरकारी ऐप नइ।"
    )
}
