package com.cybersuraksha.app

/**
 * Direct Kotlin port of site/js/qr-check.js's CGQr.analysePayload() — same checks, same
 * risk-combination logic, same lexicon (shared with Classifier.kt), so a QR scored here
 * gets the identical verdict it would get on the website.
 */

enum class QrSeverity { DANGER, WARN, INFO }

data class QrReason(val sev: QrSeverity, val en: String, val hi: String)

enum class QrType { UPI, URL, OTHER }
enum class QrVerdict { DANGER, WARN, SAFE }

data class QrResult(
    val type: QrType,
    val verdict: QrVerdict,
    val risk: Double,
    val reasons: List<QrReason>,
    val fields: List<Pair<String, String>>,
    val raw: String,
)

object QrAnalyzer {

    // Recognised UPI PSP handle suffixes (e.g. "name@ybl", "name@paytm"). Not exhaustive —
    // used only to flag handles that don't match anything recognisable, not to hard-block.
    private val KNOWN_PSP_SUFFIXES = listOf(
        "ybl", "paytm", "ibl", "axl", "okaxis", "oksbi", "okhdfcbank", "okicici",
        "upi", "apl", "axisbank", "icici", "hdfcbank", "sbi", "barodampay", "cnrb",
        "idfcbank", "kotak", "federal", "freecharge", "jupiteraxis", "fbl", "pnb",
        "rbl", "yesbank", "airtel", "jio", "indus", "unionbank", "uboi",
    )

    private val SHORTENERS = listOf(
        "bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "ow.ly", "tiny.cc"
    )

    // Combines independent risk signals the same way the SMS/call classifier combines NB +
    // lexicon probability (noisy-OR): several weak-to-moderate flags together should read
    // as more dangerous than any single one of them, not get capped at the strongest one.
    private fun combine(risk: Double, factor: Double): Double = 1 - (1 - risk) * (1 - factor)

    private fun parseQuery(raw: String): Map<String, String> {
        val qIndex = raw.indexOf("?")
        if (qIndex == -1) return emptyMap()
        val params = mutableMapOf<String, String>()
        raw.substring(qIndex + 1).split("&").forEach { pair ->
            val idx = pair.indexOf("=")
            if (idx == -1) return@forEach
            val k = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8")
            val v = try {
                java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            } catch (e: Exception) {
                pair.substring(idx + 1)
            }
            params[k.lowercase()] = v
        }
        return params
    }

    private data class PartialResult(val reasons: List<QrReason>, val risk: Double, val fields: List<Pair<String, String>>)

    private fun analyseUpi(raw: String): PartialResult {
        val params = parseQuery(raw)
        val reasons = mutableListOf<QrReason>()
        var risk = 0.0
        val pa = params["pa"] ?: ""
        val pn = params["pn"] ?: ""
        val am = params["am"]

        if (pa.isEmpty()) {
            reasons.add(QrReason(QrSeverity.DANGER,
                "No payee UPI ID (pa) found — this does not look like a genuine payment link",
                "कोई पेयी UPI ID (pa) नहीं मिला — यह असली भुगतान लिंक नहीं लगता"))
            risk = combine(risk, 0.75)
        } else {
            val atIdx = pa.indexOf("@")
            if (atIdx == -1) {
                reasons.add(QrReason(QrSeverity.DANGER,
                    "Payee UPI ID is not in the right format (not like name@bank)",
                    "पेयी UPI ID सही फॉर्मेट में नहीं है (name@bank जैसा नहीं)"))
                risk = combine(risk, 0.65)
            } else {
                val handle = pa.substring(atIdx + 1).lowercase()
                val handleOk = KNOWN_PSP_SUFFIXES.any { handle == it || handle.contains(it) }
                if (!handleOk) {
                    reasons.add(QrReason(QrSeverity.WARN,
                        "Payee's bank/app handle (\"@$handle\") could not be matched to a well-known UPI provider",
                        "पेयी के बैंक/ऐप हैंडल (\"@$handle\") को किसी जाने-पहचाने UPI प्रोवाइडर से नहीं मिलाया जा सका"))
                    risk = combine(risk, 0.35)
                }
            }
        }

        if (am != null) {
            val amt = am.toDoubleOrNull()
            if (amt != null && amt > 10000) {
                val amtStr = String.format("%,.0f", amt)
                reasons.add(QrReason(QrSeverity.WARN,
                    "A large amount (₹$amtStr) is already pre-filled in this QR — double-check it before paying",
                    "QR में पहले से एक बड़ी राशि (₹$amtStr) भरी हुई है — भुगतान से पहले राशि ज़रूर जाँच लें"))
                risk = combine(risk, 0.3)
            }
        }

        if (pn.isEmpty()) {
            reasons.add(QrReason(QrSeverity.INFO,
                "This QR doesn't show a payee name (pn) — ask the shopkeeper to say their name out loud before you pay",
                "पेयी का नाम (pn) इस QR में नहीं दिख रहा — भुगतान से पहले दुकानदार से नाम ज़रूर बोलकर पूछें"))
        }

        val fields = listOf(
            "UPI ID (pa)" to pa.ifEmpty { "—" },
            "Name (pn)" to pn.ifEmpty { "—" },
            "Amount (am)" to (am?.let { "₹$it" } ?: "—"),
            "Note (tn)" to (params["tn"] ?: params["tr"] ?: "—"),
        )
        return PartialResult(reasons, risk, fields)
    }

    private fun analyseUrl(raw: String): PartialResult {
        val reasons = mutableListOf<QrReason>()
        var risk = 0.0
        val lower = raw.lowercase()

        // Reuse the exact SMS/call lexicon — a malicious URL carries the same red-flag
        // phrases (kyc, blocked, urgent, customs, digital arrest...) whether it arrives
        // by SMS or by QR code. Skip the "link" category (http/bit.ly/click): those exist
        // in the SMS lexicon to flag "this text message contains a link at all", which is
        // true of every single URL-type QR by definition — redundant here. The shortener/
        // IP/punycode checks below are the QR-specific equivalent against the parsed host.
        val hits = Classifier.LEXICON.filter { entry ->
            if (entry.cat == "link") return@filter false
            entry.regex?.containsMatchIn(lower) ?: lower.contains(entry.pattern)
        }
        if (hits.isNotEmpty()) {
            risk = combine(risk, 1 - Math.pow(0.42, hits.size.toDouble()))
            hits.take(5).forEach { reasons.add(QrReason(QrSeverity.DANGER, it.en, it.hi)) }
        }

        val hostMatch = Regex("^https?://([^/?#]+)", RegexOption.IGNORE_CASE).find(raw)
        val host = hostMatch?.groupValues?.get(1)?.lowercase() ?: ""

        if (!lower.startsWith("https://")) {
            reasons.add(QrReason(QrSeverity.WARN, "This link is not secure HTTPS", "यह लिंक सुरक्षित HTTPS नहीं है"))
            risk = combine(risk, 0.3)
        }
        if (SHORTENERS.any { host.contains(it) }) {
            reasons.add(QrReason(QrSeverity.WARN,
                "This is a shortened/hidden URL — the real destination is not visible",
                "यह एक छोटा/छिपा हुआ URL है — असली पता दिखाई नहीं देता"))
            risk = combine(risk, 0.45)
        }
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}").containsMatchIn(host)) {
            reasons.add(QrReason(QrSeverity.DANGER,
                "This link points to a raw IP address, not a domain name",
                "यह लिंक सीधे एक IP पते की ओर जाता है, किसी डोमेन नाम की ओर नहीं"))
            risk = combine(risk, 0.7)
        }
        if (host.contains("xn--")) {
            reasons.add(QrReason(QrSeverity.DANGER,
                "This domain uses lookalike/internationalized characters — it could be a fake domain disguised as a real one",
                "यह डोमेन दिखावटी/अंतरराष्ट्रीय अक्षरों का उपयोग कर रहा है — यह किसी असली डोमेन जैसा दिखने वाला नकली डोमेन हो सकता है"))
            risk = combine(risk, 0.6)
        }
        if (reasons.isEmpty()) {
            reasons.add(QrReason(QrSeverity.INFO,
                "No known warning signs found in this link, but stay cautious regardless",
                "इस लिंक में कोई ज्ञात चेतावनी-संकेत नहीं मिला, फिर भी सावधानी बरतें"))
        }

        return PartialResult(reasons, risk, listOf("Host" to host.ifEmpty { "—" }))
    }

    fun analysePayload(rawText: String): QrResult {
        val raw = rawText.trim()
        val type: QrType
        val result: PartialResult
        when {
            raw.startsWith("upi://", ignoreCase = true) -> {
                type = QrType.UPI
                result = analyseUpi(raw)
            }
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> {
                type = QrType.URL
                result = analyseUrl(raw)
            }
            else -> {
                type = QrType.OTHER
                result = PartialResult(
                    listOf(QrReason(QrSeverity.INFO,
                        "This doesn't look like a UPI payment link or a web link — see the raw text below and judge for yourself whether it's expected",
                        "यह कोई UPI भुगतान लिंक या वेब लिंक नहीं लगता — नीचे असली पाठ देखें और यह सामान्य है या नहीं, यह स्वयं जाँचें")),
                    0.0,
                    emptyList(),
                )
            }
        }
        val verdict = when {
            result.risk >= 0.6 -> QrVerdict.DANGER
            result.risk >= 0.3 -> QrVerdict.WARN
            else -> QrVerdict.SAFE
        }
        return QrResult(type, verdict, result.risk, result.reasons, result.fields, raw)
    }
}
