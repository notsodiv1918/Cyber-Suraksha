package com.cybersuraksha.app

import android.content.Context
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Direct Kotlin port of site/js/nb-model.js's CGScam.classify() — same model.json,
 * same tokenizer, same lexicon, same noisy-OR combination and thresholds, so a message
 * scored here gets the identical verdict it would get on the website.
 */
data class LexEntry(val pattern: String, val cat: String, val hi: String, val en: String, val regex: Regex? = null)

data class ClassifyResult(
    val verdict: String, // "scam" | "suspicious" | "genuine"
    val probability: Double,
    val hits: List<LexEntry>,
    val scamType: String?,
)

object Classifier {

    private val LEXICON = listOf(
        // Plain "otp" as a bare substring flagged every normal OTP-delivery message too
        // ("Your OTP for transaction is 482917" is completely routine) — a real false positive
        // caught in testing. Only flag it when the message actually asks the user to hand the
        // OTP over, not when it's just delivering their own code to them. Kept identical to
        // site/js/nb-model.js's fix for the same bug.
        LexEntry(
            "share otp", "otp", "OTP माँगना/साझा करने को कहा गया", "Asks you to share your OTP",
            regex = Regex(
                "\\b(share|send|sent|give|tell|provide|read\\s*out|bata|batao|bhej|bhejo|bolo|bol\\s*do)\\w*\\b[^.?!\\n]{0,25}\\botp\\b|\\botp\\b[^.?!\\n]{0,25}\\b(share|send|give|tell|provide|bata|batao|bhej|bhejo|bolo)\\w*\\b",
                RegexOption.IGNORE_CASE
            )
        ),
        LexEntry("पिन", "otp", "पिन/OTP साझा करने को कहा गया", "Asks you to share PIN/OTP"),
        LexEntry("kyc", "kyc", "KYC अपडेट/ब्लॉक की धमकी", "KYC update/block threat"),
        LexEntry("केवाईसी", "kyc", "केवाईसी अपडेट की धमकी", "KYC update threat"),
        LexEntry("blocked", "kyc", "खाता/सिम ब्लॉक होने की धमकी", "Threatens account/SIM block"),
        LexEntry("ब्लॉक", "kyc", "खाता/सिम ब्लॉक होने की झूठी चेतावनी", "False account/SIM block warning"),
        LexEntry("lottery", "prize", "लॉटरी/इनाम जीतने का झाँसा", "Fake lottery/prize win"),
        LexEntry("लॉटरी", "prize", "लॉटरी जीतने का झाँसा", "Fake lottery claim"),
        LexEntry("जीत", "prize", "इनाम जीतने का दावा", "Claims you won a prize"),
        LexEntry("winner", "prize", "'विजेता' बनाकर झाँसा", "Declares you a 'winner'"),
        LexEntry("click", "link", "अनजान लिंक पर तुरंत क्लिक कराना", "Pushes you to click an unknown link"),
        LexEntry("क्लिक", "link", "संदिग्ध लिंक पर क्लिक करने को कहा गया", "Asks you to click a suspicious link"),
        LexEntry("http", "link", "संदेश में सीधा लिंक/URL", "Contains a direct link/URL"),
        LexEntry("bit.ly", "link", "छोटा/छिपा हुआ URL", "Shortened/hidden URL"),
        LexEntry("urgent", "urgency", "जल्दबाज़ी बनाने वाले शब्द", "Urgency-creating language"),
        LexEntry("तुरंत", "urgency", "'तुरंत कार्रवाई करें' जैसी जल्दबाज़ी", "'Act immediately' style urgency"),
        LexEntry("within", "urgency", "सीमित समय में कार्रवाई की धमकी", "Threatens action within a short time limit"),
        LexEntry("refund", "payment", "फर्जी रिफंड/कैशबैक का झाँसा", "Fake refund/cashback claim"),
        LexEntry("रिफंड", "payment", "फर्जी रिफंड का दावा", "Fake refund claim"),
        LexEntry("cashback", "payment", "कैशबैक के नाम पर झाँसा", "Fake cashback offer"),
        LexEntry("upi pin", "payment", "UPI पिन माँगना", "Asks for UPI PIN"),
        LexEntry("यूपीआई पिन", "payment", "UPI पिन साझा करने को कहा गया", "Asks you to share UPI PIN"),
        LexEntry("customs", "courier", "कस्टम/कूरियर में सामान रुकने का झाँसा", "Fake customs/courier package-held story"),
        LexEntry("कस्टम", "courier", "कस्टम विभाग के नाम पर धमकी", "Threat in the name of customs department"),
        LexEntry("parcel", "courier", "पार्सल रुकने/जब्त होने का झाँसा", "Fake parcel held/seized story"),
        LexEntry("digital arrest", "arrest", "'डिजिटल अरेस्ट' की धमकी", "'Digital arrest' threat"),
        LexEntry("arrest warrant", "arrest", "फर्जी गिरफ्तारी वारंट की धमकी", "Fake arrest warrant threat"),
        LexEntry("पुलिस केस", "arrest", "पुलिस केस की धमकी देकर पैसे माँगना", "Threatens a police case to extort money"),
        LexEntry("job offer", "job", "घर बैठे भारी कमाई वाली फर्जी नौकरी", "Fake high-earning work-from-home job"),
        LexEntry("पार्ट टाइम", "job", "आसान पार्ट-टाइम कमाई का झाँसा", "Easy part-time earning scam"),
        LexEntry("investment", "investment", "असाधारण मुनाफ़े वाली निवेश स्कीम", "Investment scheme promising unusual returns"),
        LexEntry("guaranteed return", "investment", "'गारंटीड रिटर्न' का झाँसा", "'Guaranteed return' claim"),
        LexEntry("loan approved", "loan", "बिना आवेदन लोन स्वीकृत होने का झाँसा", "Fake instant loan approval"),
        LexEntry("प्रोसेसिंग फीस", "loan", "लोन से पहले 'प्रोसेसिंग फीस' माँगना", "Demands 'processing fee' before a loan"),
        LexEntry("aadhaar", "idtheft", "आधार/पैन विवरण माँगना", "Asks for Aadhaar/PAN details"),
        LexEntry("आधार", "idtheft", "आधार विवरण साझा करने को कहा गया", "Asks you to share Aadhaar details"),
    )

    private val CATEGORY_LABEL_EN = mapOf(
        "otp" to "OTP/PIN scam", "kyc" to "Fake KYC", "prize" to "Lottery/prize scam",
        "link" to "Suspicious link", "payment" to "Fake refund/payment scam",
        "courier" to "Courier/customs scam", "arrest" to "'Digital arrest' scam",
        "job" to "Fake job offer", "investment" to "Investment scam",
        "loan" to "Fake loan offer", "idtheft" to "ID theft (Aadhaar/PAN)"
    )
    fun categoryLabel(cat: String): String = CATEGORY_LABEL_EN[cat] ?: cat

    private val STOPWORDS: Set<String> = (
        "hai hain tha the thi ho hoga hogi ke ka ki ko me mein par se ye yeh wo voh " +
        "vo ye aur ya bhi to hi jo ki is us ek kuch sabhi aap hum main tum apna apne " +
        "kar kare karen kiya kisi koi bina liye sath baad pehle abhi ab yahan wahan " +
        "kaise kyon kab kahan nahi haan ji gaya gayi gaye raha rahi rahe hoti hote " +
        "hota wala wali wale banaa bana rha rhi rhe " +
        "है हैं था थे थी हो होगा होगी के का की को में पर से यह वह ये वो और या भी तो " +
        "ही जो कि इस उस एक कुछ सभी आप हम मैं तुम अपना अपने कर करें किया किसी कोई " +
        "बिना लिए साथ बाद पहले अभी अब यहाँ वहाँ कैसे क्यों कब कहाँ नहीं हां जी गया " +
        "गई गए रहा रही रहे होती होते होता वाला वाली वाले बना रहे रहें हुआ हुई हुए " +
        "the is are was were a an and or to of in on at for with this that these " +
        "those you your i we my me it as be by from has have had will would can " +
        "could please dear not so if but just now your u ur"
    ).split(Regex("\\s+")).filter { it.isNotEmpty() }.toHashSet()

    private var vocab: JSONObject? = null
    private var priors: DoubleArray? = null
    private var defaultLp: DoubleArray? = null
    private var ready = false

    fun load(context: Context) {
        if (ready) return
        val text = context.assets.open("model.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val m = JSONObject(text)
        vocab = m.getJSONObject("vocab")
        val p = m.getJSONArray("priors")
        priors = doubleArrayOf(p.getDouble(0), p.getDouble(1))
        val d = m.getJSONArray("default")
        defaultLp = doubleArrayOf(d.getDouble(0), d.getDouble(1))
        ready = true
    }

    private fun normalise(text: String): String {
        var t = text.lowercase()
        t = Regex("https?://\\S+").replace(t, " http ")
        // keep Devanagari (U+0900-U+097F), a-z, 0-9, spaces
        val sb = StringBuilder()
        for (ch in t) {
            if (ch in 'ऀ'..'ॿ' || ch in 'a'..'z' || ch in '0'..'9' || ch == ' ') sb.append(ch) else sb.append(' ')
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun tokenize(norm: String): List<String> {
        // Char-4-gram augmentation removed — see site/js/nb-model.js's tokenize() for why:
        // it was the main driver of a systemic false-positive bug on ordinary casual text.
        if (norm.isEmpty()) return emptyList()
        return norm.split(" ").filter { it.isNotEmpty() && it !in STOPWORDS }
    }

    private fun lexiconHits(normSpaced: String): List<LexEntry> =
        LEXICON.filter { entry -> entry.regex?.containsMatchIn(normSpaced) ?: normSpaced.contains(entry.pattern) }

    private fun nbProbability(norm: String): Double {
        val tokens = tokenize(norm)
        var logHam = priors!![0]
        var logScam = priors!![1]
        val v = vocab!!
        for (t in tokens) {
            val lp = if (v.has(t)) v.getJSONArray(t) else null
            if (lp != null) {
                logHam += lp.getDouble(0)
                logScam += lp.getDouble(1)
            } else {
                logHam += defaultLp!![0]
                logScam += defaultLp!![1]
            }
        }
        val maxL = max(logHam, logScam)
        val eHam = exp(logHam - maxL)
        val eScam = exp(logScam - maxL)
        return eScam / (eHam + eScam)
    }

    private fun lexiconScore(hitCount: Int): Double =
        if (hitCount <= 0) 0.0 else 1 - 0.42.pow(hitCount)

    fun classify(rawText: String): ClassifyResult {
        val norm = normalise(rawText)
        val hits = lexiconHits(" $norm ")
        val nbProb = if (ready) nbProbability(norm) else 0.15
        val lexProb = lexiconScore(hits.size)
        val prob = 1 - (1 - nbProb) * (1 - lexProb)

        // No lexicon hits means the verdict rests purely on NB word statistics, which can
        // misfire on short casual messages (see site/js/nb-model.js for the full story —
        // kept identical here so the app and website never disagree on the same text).
        val hasLexiconSupport = hits.isNotEmpty()
        val scamThreshold = if (hasLexiconSupport) 0.6 else 0.97
        val suspiciousThreshold = if (hasLexiconSupport) 0.3 else 0.65

        val verdict = when {
            prob >= scamThreshold -> "scam"
            prob >= suspiciousThreshold -> "suspicious"
            else -> "genuine"
        }

        var scamType: String? = null
        if (verdict != "genuine") {
            val counts = mutableMapOf<String, Int>()
            for (h in hits) {
                if (h.cat == "urgency") continue
                counts[h.cat] = (counts[h.cat] ?: 0) + 1
            }
            scamType = counts.maxByOrNull { it.value }?.key
        }

        return ClassifyResult(verdict, prob, hits, scamType)
    }
}
