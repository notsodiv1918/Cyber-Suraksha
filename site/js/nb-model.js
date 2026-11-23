/* CGScam — offline Naive Bayes scam/genuine classifier for SMS/WhatsApp text.
   Works fully on-device: model.json (trained offline from the datasets in
   data/about-sources.json) is loaded once and all scoring happens in-browser
   with no network calls and no data leaving the device. */

(function (global) {
  const MODEL_URL = "../data/model.json";
  const MODEL_URL_ROOT = "data/model.json"; // when loaded from index.html at site root

  // Bilingual red-flag phrase lexicon used for the human-readable "why" explanation.
  // Each entry: pattern (lowercase substring, matched against normalised text),
  // category, and a short bilingual explanation.
  const LEXICON = [
    // Plain "otp" as a bare substring used to flag every normal OTP-delivery message too
    // ("Your OTP for transaction is 482917" is completely routine) — a real false positive
    // caught in testing. Only flag it when the message actually asks the user to hand the
    // OTP over (scam pattern), not when it's just delivering their own code to them.
    { p: "share otp", re: /\b(share|send|sent|give|tell|provide|read\s*out|bata|batao|bhej|bhejo|bolo|bol\s*do)\w*\b[^.?!\n]{0,25}\botp\b|\botp\b[^.?!\n]{0,25}\b(share|send|give|tell|provide|bata|batao|bhej|bhejo|bolo)\w*\b/i, cat: "otp", hi: "OTP माँगना/साझा करने को कहा गया — बैंक/कंपनियाँ कभी OTP नहीं माँगतीं", en: "Asks you to share your OTP — banks/companies never ask for it" },
    { p: "पिन", cat: "otp", hi: "पिन/OTP साझा करने को कहा गया", en: "Asks you to share PIN/OTP" },
    { p: "kyc", cat: "kyc", hi: "KYC अपडेट/ब्लॉक की धमकी — आम बैंक-धोखाधड़ी पैटर्न", en: "KYC update/block threat — a common bank-fraud pattern" },
    { p: "केवाईसी", cat: "kyc", hi: "केवाईसी अपडेट की धमकी", en: "KYC update threat" },
    { p: "blocked", cat: "kyc", hi: "खाता/सिम ब्लॉक होने की धमकी देकर जल्दबाज़ी कराना", en: "Threatens account/SIM block to create urgency" },
    { p: "ब्लॉक", cat: "kyc", hi: "खाता/सिम ब्लॉक होने की झूठी चेतावनी", en: "False account/SIM block warning" },
    { p: "lottery", cat: "prize", hi: "लॉटरी/इनाम जीतने का झाँसा", en: "Fake lottery/prize win" },
    { p: "लॉटरी", cat: "prize", hi: "लॉटरी जीतने का झाँसा", en: "Fake lottery claim" },
    { p: "जीत", cat: "prize", hi: "इनाम जीतने का दावा", en: "Claims you won a prize" },
    { p: "winner", cat: "prize", hi: "'विजेता' बनाकर झाँसा", en: "Declares you a 'winner'" },
    { p: "click", cat: "link", hi: "अनजान लिंक पर तुरंत क्लिक कराना", en: "Pushes you to click an unknown link immediately" },
    { p: "क्लिक", cat: "link", hi: "संदिग्ध लिंक पर क्लिक करने को कहा गया", en: "Asks you to click a suspicious link" },
    { p: "http", cat: "link", hi: "संदेश में सीधा लिंक/URL", en: "Contains a direct link/URL" },
    { p: "bit.ly", cat: "link", hi: "छोटा/छिपा हुआ URL (bit.ly जैसा)", en: "Shortened/hidden URL (bit.ly style)" },
    { p: "urgent", cat: "urgency", hi: "जल्दबाज़ी बनाने वाले शब्द ('urgent')", en: "Urgency-creating language" },
    { p: "तुरंत", cat: "urgency", hi: "'तुरंत कार्रवाई करें' जैसी जल्दबाज़ी", en: "'Act immediately' style urgency" },
    { p: "within", cat: "urgency", hi: "सीमित समय में कार्रवाई की धमकी", en: "Threatens action within a short time limit" },
    { p: "refund", cat: "payment", hi: "फर्जी रिफंड/कैशबैक का झाँसा", en: "Fake refund/cashback claim" },
    { p: "रिफंड", cat: "payment", hi: "फर्जी रिफंड का दावा", en: "Fake refund claim" },
    { p: "cashback", cat: "payment", hi: "कैशबैक के नाम पर झाँसा", en: "Fake cashback offer" },
    { p: "upi pin", cat: "payment", hi: "UPI पिन माँगना — असली ऐप्स कभी नहीं माँगते", en: "Asks for UPI PIN — real apps never ask this" },
    { p: "यूपीआई पिन", cat: "payment", hi: "UPI पिन साझा करने को कहा गया", en: "Asks you to share UPI PIN" },
    { p: "customs", cat: "courier", hi: "कस्टम/कूरियर में सामान रुकने का झाँसा", en: "Fake customs/courier package-held story" },
    { p: "कस्टम", cat: "courier", hi: "कस्टम विभाग के नाम पर धमकी", en: "Threat in the name of customs department" },
    { p: "parcel", cat: "courier", hi: "पार्सल रुकने/जब्त होने का झाँसा", en: "Fake parcel held/seized story" },
    { p: "digital arrest", cat: "arrest", hi: "'डिजिटल अरेस्ट' की धमकी — यह असली कानूनी प्रक्रिया नहीं है", en: "'Digital arrest' threat — this is not a real legal process" },
    { p: "arrest warrant", cat: "arrest", hi: "फर्जी गिरफ्तारी वारंट की धमकी", en: "Fake arrest warrant threat" },
    { p: "पुलिस केस", cat: "arrest", hi: "पुलिस केस की धमकी देकर पैसे माँगना", en: "Threatens a police case to extort money" },
    { p: "job offer", cat: "job", hi: "घर बैठे भारी कमाई वाली फर्जी नौकरी", en: "Fake high-earning work-from-home job" },
    { p: "पार्ट टाइम", cat: "job", hi: "आसान पार्ट-टाइम कमाई का झाँसा", en: "Easy part-time earning scam" },
    { p: "investment", cat: "investment", hi: "असाधारण मुनाफ़े वाली निवेश स्कीम", en: "Investment scheme promising unusual returns" },
    { p: "guaranteed return", cat: "investment", hi: "'गारंटीड रिटर्न' का झाँसा — असली निवेश में गारंटी नहीं होती", en: "'Guaranteed return' claim — real investments carry no guarantee" },
    { p: "loan approved", cat: "loan", hi: "बिना आवेदन लोन स्वीकृत होने का झाँसा", en: "Fake instant loan approval without application" },
    { p: "प्रोसेसिंग फीस", cat: "loan", hi: "लोन से पहले 'प्रोसेसिंग फीस' माँगना", en: "Demands 'processing fee' before a loan" },
    { p: "aadhaar", cat: "idtheft", hi: "आधार/पैन विवरण माँगना", en: "Asks for Aadhaar/PAN details" },
    { p: "आधार", cat: "idtheft", hi: "आधार विवरण साझा करने को कहा गया", en: "Asks you to share Aadhaar details" },
    // Classic "family member on a new number" scam: deliberately written in plain,
    // innocent-sounding language to defeat word-statistics-based detection (confirmed
    // in testing: this message alone drags NB probability down to ~genuine once the
    // genuine training vocabulary is broad enough to cover "mom"/"phone"/"send"/etc in
    // normal contexts). A fraud claiming a changed number AND asking for money/UPI in
    // the same message is a known, specific pattern worth flagging on structure alone,
    // not word frequency.
    { p: "new-number-money", cat: "familyemergency", hi: "'नया नंबर' बताकर पैसे भेजने को कहा गया — यह एक जाना-पहचाना धोखा है",
      en: "Claims a new number and asks for money — a known family-emergency scam pattern",
      re: /(?=.*\b(new number|naya number|number (badal|change)|number badla|temporary number|temp number|lost my phone|phone (broke|broken|chori|stolen))\b)(?=.*\b(send|bhej|bhejo|transfer|paytm|upi|gpay|phonepe)\b)/i },
  ];

  // Must stay identical to STOPWORDS in scripts/train_model.py.
  const STOPWORDS = new Set(`
hai hain tha the thi ho hoga hogi ke ka ki ko me mein par se ye yeh wo voh
vo ye aur ya bhi to hi jo ki is us ek kuch sabhi aap hum main tum apna apne
kar kare karen kiya kisi koi bina liye sath baad pehle abhi ab yahan wahan
kaise kyon kab kahan nahi haan ji gaya gayi gaye raha rahi rahe hoti hote
hota wala wali wale banaa bana rha rhi rhe
है हैं था थे थी हो होगा होगी के का की को में पर से यह वह ये वो और या भी तो
ही जो कि इस उस एक कुछ सभी आप हम मैं तुम अपना अपने कर करें किया किसी कोई
बिना लिए साथ बाद पहले अभी अब यहाँ वहाँ कैसे क्यों कब कहाँ नहीं हां जी गया
गई गए रहा रही रहे होती होते होता वाला वाली वाले बना रहे रहें हुआ हुई हुए
the is are was were a an and or to of in on at for with this that these
those you your i we my me it as be by from has have had will would can
could please dear not so if but just now your u ur
he she they them him her his hers their theirs its who whom which what
`.split(/\s+/).filter(Boolean));

  // Human-readable label for each LEXICON category, used to surface a scam "type"
  // headline (e.g. "Fake KYC") instead of just a verdict + score.
  const CATEGORY_LABELS = {
    otp: { hi: "OTP/पिन स्कैम", en: "OTP/PIN scam" },
    kyc: { hi: "फर्ज़ी KYC", en: "Fake KYC" },
    prize: { hi: "लॉटरी/इनाम स्कैम", en: "Lottery/prize scam" },
    link: { hi: "संदिग्ध लिंक", en: "Suspicious link" },
    urgency: { hi: "जल्दबाज़ी बनाने वाला संदेश", en: "Urgency-pressure message" },
    payment: { hi: "फर्ज़ी रिफंड/भुगतान स्कैम", en: "Fake refund/payment scam" },
    courier: { hi: "कूरियर/कस्टम स्कैम", en: "Courier/customs scam" },
    arrest: { hi: "'डिजिटल अरेस्ट' स्कैम", en: "'Digital arrest' scam" },
    job: { hi: "फर्ज़ी नौकरी ऑफर", en: "Fake job offer" },
    investment: { hi: "निवेश स्कैम", en: "Investment scam" },
    loan: { hi: "फर्ज़ी लोन ऑफर", en: "Fake loan offer" },
    idtheft: { hi: "पहचान चोरी (आधार/पैन)", en: "ID theft (Aadhaar/PAN)" },
  };
  // Categories that describe a tactic rather than the scam's actual "type" —
  // skipped when picking the headline label (they still appear as reason bullets).
  const CATEGORY_NOT_A_TYPE = { urgency: true };

  function topCategory(hits) {
    const counts = {};
    for (const h of hits) {
      if (CATEGORY_NOT_A_TYPE[h.cat]) continue;
      counts[h.cat] = (counts[h.cat] || 0) + 1;
    }
    let best = null, bestCount = 0;
    for (const cat in counts) {
      if (counts[cat] > bestCount) { best = cat; bestCount = counts[cat]; }
    }
    return best;
  }

  const state = { model: null };

  function tryFetch(url) {
    return fetch(url, { cache: "no-store" }).then((r) => {
      if (!r.ok) throw new Error("not ok");
      return r.json();
    });
  }

  const ready = tryFetch(MODEL_URL_ROOT)
    .catch(() => tryFetch(MODEL_URL))
    .then((m) => { state.model = m; })
    .catch((err) => {
      console.warn("CGScam: model.json not reachable, using fallback heuristic only.", err);
      state.model = null;
    });

  function normalise(text) {
    return (text || "")
      .toLowerCase()
      .replace(/https?:\/\/\S+/g, " http ")
      .replace(/[^ऀ-ॿ a-z0-9]/g, " ")
      .replace(/\s+/g, " ")
      .trim();
  }

  function tokenize(norm) {
    // Char-4-gram augmentation (used to help generalise across Hinglish spelling
    // variants) turned out to be the main driver of a systemic false-positive bug:
    // every word longer than 4 chars added several correlated sub-tokens, all voting
    // toward whichever class has broader raw-corpus vocabulary coverage — which for us
    // is "scam" (the genuine class is narrow transactional boilerplate). A plain 10-word
    // casual sentence compounded into 20-30 extra tokens all pulling the same way,
    // pinning probability to ~1.0 regardless of how much genuine training data was added.
    // Plain word tokens only now — the lexicon regexes already catch keyword variants.
    if (!norm) return [];
    return norm.split(" ").filter((w) => w && !STOPWORDS.has(w));
  }

  function lexiconHits(normText) {
    const hits = [];
    for (const entry of LEXICON) {
      const matched = entry.re ? entry.re.test(normText) : normText.indexOf(entry.p) !== -1;
      if (matched) hits.push(entry);
    }
    return hits;
  }

  function nbProbability(norm) {
    const m = state.model;
    const tokens = tokenize(norm);
    let logScam = m.priors[1];
    let logHam = m.priors[0];
    for (const t of tokens) {
      const lp = m.vocab[t] || m.default;
      logHam += lp[0];
      logScam += lp[1];
    }
    const maxL = Math.max(logHam, logScam);
    const eHam = Math.exp(logHam - maxL);
    const eScam = Math.exp(logScam - maxL);
    return eScam / (eHam + eScam);
  }

  // The trained model is a bag-of-words/char-4-gram Naive Bayes over ~38k
  // mostly-English + machine-translated-Hindi examples — it generalises well
  // to novel scam phrasing but under-weighs real-world Hinglish fraud terms
  // (KYC/OTP/link/block) that are rare or rendered differently in the
  // training corpus. The hand-curated bilingual LEXICON directly encodes
  // those known red flags. Combining the two (noisy-OR) means either signal
  // firing confidently is enough to flag a message, which is far more robust
  // than trusting either one alone for an under-resourced-language prototype.
  function lexiconScore(hitCount) {
    if (hitCount <= 0) return 0;
    return 1 - Math.pow(0.42, hitCount);
  }

  function classify(rawText) {
    const norm = normalise(rawText);
    const hits = lexiconHits(" " + norm + " ");
    const usedModel = !!state.model;
    const nbProb = usedModel ? nbProbability(norm) : 0.15;
    const lexProb = lexiconScore(hits.length);
    // noisy-OR: combined is high if EITHER signal is confident
    const prob = 1 - (1 - nbProb) * (1 - lexProb);

    // With zero lexicon hits, "scam"/"suspicious" rests entirely on the NB model's
    // word-frequency statistics — and short, casual messages ("Long time no see!")
    // can score misleadingly high purely because their everyday words (how/long/
    // time) are statistically more common in the scam training corpus than in the
    // mostly-transactional genuine corpus, with no actual red flag present. Require
    // more confidence before alarming when there's no concrete reason to point to;
    // keep the original, more sensitive thresholds when a real red-flag phrase
    // backs the verdict up.
    const hasLexiconSupport = hits.length > 0;
    const scamThreshold = hasLexiconSupport ? 0.6 : 0.97;
    const suspiciousThreshold = hasLexiconSupport ? 0.3 : 0.65;

    let verdict = "genuine";
    if (prob >= scamThreshold) verdict = "scam";
    else if (prob >= suspiciousThreshold) verdict = "suspicious";

    const cat = verdict !== "genuine" ? topCategory(hits) : null;

    return {
      verdict, // 'scam' | 'suspicious' | 'genuine'
      probability: prob,
      nbProbability: nbProb,
      hits,
      usedModel,
      text: rawText,
      scamType: cat, // e.g. 'kyc', 'prize', 'loan' — null if no category stood out
    };
  }

  function renderVerdict(result, lang) {
    const isEn = lang === "en";
    const pct = Math.round(result.probability * 100);
    const base = location.pathname.indexOf("/pages/") !== -1 ? "" : "pages/";
    const iconHref = (location.pathname.indexOf("/pages/") !== -1 ? "../" : "") + "assets/icons.svg";
    const cfg = {
      scam: { cls: "alert-danger", icon: "i-x-circle", hi: "संभावित स्कैम", en: "Likely SCAM" },
      suspicious: { cls: "alert-warn", icon: "i-alert-triangle", hi: "संदिग्ध — सावधान रहें", en: "Suspicious — be careful" },
      genuine: { cls: "alert-safe", icon: "i-check-circle", hi: "असली संदेश प्रतीत होता है", en: "Appears genuine" },
    }[result.verdict];

    let reasons = "";
    if (result.hits.length) {
      reasons = '<ul class="kv-list" style="margin-top:10px;">' +
        result.hits.slice(0, 6).map((h) =>
          `<li><span>${isEn ? h.en : h.hi}</span><span class="badge badge-red">${h.p}</span></li>`
        ).join("") + "</ul>";
    } else {
      reasons = `<p class="hint" style="margin-top:8px;">${isEn ? "No known red-flag phrases matched; verdict is based on the trained language model." : "कोई ज्ञात चेतावनी-वाक्यांश नहीं मिला; निर्णय प्रशिक्षित भाषा मॉडल पर आधारित है।"}</p>`;
    }

    const modelNote = result.usedModel
      ? (isEn ? "Scored by the on-device Naive Bayes model." : "ऑन-डिवाइस नैव बेज़ मॉडल द्वारा जाँचा गया।")
      : (isEn ? "Model still loading — showing rule-based estimate." : "मॉडल अभी लोड हो रहा है — नियम-आधारित अनुमान दिखाया जा रहा है।");

    const typeLabel = result.scamType && CATEGORY_LABELS[result.scamType];
    const typeBadge = typeLabel
      ? `<span class="badge badge-red" style="margin-left:6px;">${isEn ? typeLabel.en : typeLabel.hi}</span>`
      : "";

    let nextSteps = "";
    if (result.verdict !== "genuine") {
      const steps = [
        { icon: "i-x-circle", color: "var(--danger)", hi: "कोई भी लिंक न खोलें, OTP/PIN किसी को न बताएँ", en: "Don't open any link or share your OTP/PIN" },
        { icon: "i-x-circle", color: "var(--danger)", hi: "संदेश/कॉल में दिए नंबर पर वापस कॉल न करें", en: "Don't call back on any number given in the message" },
        { icon: "i-shield-check", color: "var(--safe)", hi: "भेजने वाले को ब्लॉक करें", en: "Block the sender" },
        result.verdict === "scam"
          ? { icon: "i-phone", color: "var(--safe)", hi: "पैसे गँवाए हैं तो अभी 1930 पर कॉल करें", en: "If money is already gone, call 1930 right now" }
          : { icon: "i-shield-check", color: "var(--safe)", hi: "चक्षु पर रिपोर्ट करें — 30 दिनों के भीतर", en: "Report on Chakshu — within 30 days" },
      ];
      nextSteps = `
        <div style="margin-top:14px;padding-top:14px;border-top:1px solid rgba(0,0,0,.08);">
          <h4 style="margin:0 0 8px;font-size:13.5px;">${isEn ? "What to do now" : "अभी क्या करें"}</h4>
          <ul style="list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:7px;">
            ${steps.map((s) =>
              `<li style="display:flex;align-items:center;gap:8px;font-size:13.5px;"><svg class="icon" style="color:${s.color};width:17px;height:17px;flex:none;"><use href="${iconHref}#${s.icon}"></use></svg><span>${isEn ? s.en : s.hi}</span></li>`
            ).join("")}
          </ul>
        </div>`;
    }

    return `
      <div class="alert ${cfg.cls}">
        <div class="icon" style="font-size:26px;"><svg class="icon"><use href="${iconHref}#${cfg.icon}"></use></svg></div>
        <div style="flex:1">
          <h3>${isEn ? cfg.en : cfg.hi} <span class="badge badge-amber" style="margin-left:6px;">${pct}% ${isEn ? "scam score" : "स्कैम स्कोर"}</span>${typeBadge}</h3>
          <div class="progress" style="margin:8px 0;"><span style="width:${pct}%;background:${result.verdict === "genuine" ? "var(--safe)" : result.verdict === "suspicious" ? "var(--warn)" : "var(--danger)"}"></span></div>
          <p>${modelNote}</p>
          ${reasons}
          ${nextSteps}
          ${result.verdict !== "genuine" ? `<p style="margin-top:10px;"><a class="btn btn-sm btn-danger" href="${base}report.html" data-en="Report this">इसकी शिकायत करें</a></p>` : ""}
        </div>
      </div>`;
  }

  global.CGScam = { ready, classify, renderVerdict, LEXICON };
})(window);
