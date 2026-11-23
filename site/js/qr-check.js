/* CGQr — offline fake/malicious QR-code checker for UPI payment QRs and general URL QRs.
   Takes the raw decoded text of a QR code (decoded client-side by jsQR — see qr-checker.html)
   and classifies it using the same "no data leaves the device" approach as the SMS/call
   detectors: parse the payload, run a small set of structural checks, and explain the
   result in plain bilingual language. No network call is made to check the QR itself. */
(function (global) {
  // Recognised UPI PSP handle suffixes (e.g. "name@ybl", "name@paytm"). Not exhaustive —
  // used only to flag handles that don't match anything recognisable, not to hard-block.
  var KNOWN_PSP_SUFFIXES = [
    "ybl", "paytm", "ibl", "axl", "okaxis", "oksbi", "okhdfcbank", "okicici",
    "upi", "apl", "axisbank", "icici", "hdfcbank", "sbi", "barodampay", "cnrb",
    "idfcbank", "kotak", "federal", "freecharge", "jupiteraxis", "fbl", "pnb",
    "rbl", "yesbank", "airtel", "jio", "indus", "unionbank", "uboi",
  ];

  var SHORTENERS = ["bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "ow.ly", "tiny.cc"];

  // Combines independent risk signals the same way the SMS/call classifier combines NB +
  // lexicon probability (noisy-OR): several weak-to-moderate flags together should read
  // as more dangerous than any single one of them, not get capped at the strongest one.
  function combine(risk, factor) {
    return 1 - (1 - risk) * (1 - factor);
  }

  function parseQuery(raw) {
    var params = {};
    var qIndex = raw.indexOf("?");
    if (qIndex === -1) return params;
    raw.slice(qIndex + 1).split("&").forEach(function (pair) {
      var idx = pair.indexOf("=");
      if (idx === -1) return;
      var k = decodeURIComponent(pair.slice(0, idx));
      var v;
      try {
        v = decodeURIComponent(pair.slice(idx + 1).replace(/\+/g, " "));
      } catch (e) {
        v = pair.slice(idx + 1);
      }
      params[k.toLowerCase()] = v;
    });
    return params;
  }

  function analyseUpi(raw) {
    var params = parseQuery(raw);
    var reasons = [];
    var risk = 0;
    var pa = params.pa || "";
    var pn = params.pn || "";
    var am = params.am;

    if (!pa) {
      reasons.push({ sev: "danger", hi: "कोई पेयी UPI ID (pa) नहीं मिला — यह असली भुगतान लिंक नहीं लगता", en: "No payee UPI ID (pa) found — this does not look like a genuine payment link" });
      risk = combine(risk, 0.75);
    } else {
      var atIdx = pa.indexOf("@");
      if (atIdx === -1) {
        reasons.push({ sev: "danger", hi: "पेयी UPI ID सही फॉर्मेट में नहीं है (name@bank जैसा नहीं)", en: "Payee UPI ID is not in the right format (not like name@bank)" });
        risk = combine(risk, 0.65);
      } else {
        var handle = pa.slice(atIdx + 1).toLowerCase();
        var handleOk = KNOWN_PSP_SUFFIXES.some(function (s) { return handle === s || handle.indexOf(s) !== -1; });
        if (!handleOk) {
          reasons.push({ sev: "warn", hi: "पेयी के बैंक/ऐप हैंडल (\"@" + handle + "\") को किसी जाने-पहचाने UPI प्रोवाइडर से नहीं मिलाया जा सका", en: "Payee's bank/app handle (\"@" + handle + "\") could not be matched to a well-known UPI provider" });
          risk = combine(risk, 0.35);
        }
      }
    }

    if (am) {
      var amt = parseFloat(am);
      if (!isNaN(amt) && amt > 10000) {
        reasons.push({ sev: "warn", hi: "QR में पहले से एक बड़ी राशि (₹" + amt.toLocaleString("en-IN") + ") भरी हुई है — भुगतान से पहले राशि ज़रूर जाँच लें", en: "A large amount (₹" + amt.toLocaleString("en-IN") + ") is already pre-filled in this QR — double-check it before paying" });
        risk = combine(risk, 0.3);
      }
    }

    if (!pn) {
      reasons.push({ sev: "info", hi: "पेयी का नाम (pn) इस QR में नहीं दिख रहा — भुगतान से पहले दुकानदार से नाम ज़रूर बोलकर पूछें", en: "This QR doesn't show a payee name (pn) — ask the shopkeeper to say their name out loud before you pay" });
    }

    return {
      reasons: reasons,
      risk: risk,
      fields: { "UPI ID (pa)": pa || "—", "Name (pn)": pn || "—", "Amount (am)": am ? "₹" + am : "—", "Note (tn)": params.tn || params.tr || "—" },
    };
  }

  function analyseUrl(raw) {
    var reasons = [];
    var risk = 0;
    var lower = raw.toLowerCase();

    // Reuse the exact SMS/call lexicon — a malicious URL carries the same red-flag
    // phrases (kyc, blocked, urgent, customs, digital arrest...) whether it arrives
    // by SMS or by QR code. Skip the "link" category (http/bit.ly/click): those exist
    // in the SMS lexicon to flag "this text message contains a link at all", which is
    // true of every single URL-type QR by definition — redundant here, and would wrongly
    // tag every ordinary link as risky. The shortener/IP/punycode checks below are the
    // QR-specific equivalent, done properly against the parsed host.
    var lexicon = (global.CGScam && global.CGScam.LEXICON) || [];
    var hits = lexicon.filter(function (entry) {
      if (entry.cat === "link") return false;
      return entry.re ? entry.re.test(lower) : lower.indexOf(entry.p) !== -1;
    });
    if (hits.length) {
      risk = combine(risk, 1 - Math.pow(0.42, hits.length));
      hits.slice(0, 5).forEach(function (h) { reasons.push({ sev: "danger", hi: h.hi, en: h.en }); });
    }

    var hostMatch = raw.match(/^https?:\/\/([^/?#]+)/i);
    var host = hostMatch ? hostMatch[1].toLowerCase() : "";

    if (lower.indexOf("https://") !== 0) {
      reasons.push({ sev: "warn", hi: "यह लिंक सुरक्षित HTTPS नहीं है", en: "This link is not secure HTTPS" });
      risk = combine(risk, 0.3);
    }
    if (SHORTENERS.some(function (s) { return host.indexOf(s) !== -1; })) {
      reasons.push({ sev: "warn", hi: "यह एक छोटा/छिपा हुआ URL है — असली पता दिखाई नहीं देता", en: "This is a shortened/hidden URL — the real destination is not visible" });
      risk = combine(risk, 0.45);
    }
    if (/^\d{1,3}(\.\d{1,3}){3}/.test(host)) {
      reasons.push({ sev: "danger", hi: "यह लिंक सीधे एक IP पते की ओर जाता है, किसी डोमेन नाम की ओर नहीं", en: "This link points to a raw IP address, not a domain name" });
      risk = combine(risk, 0.7);
    }
    if (host.indexOf("xn--") !== -1) {
      reasons.push({ sev: "danger", hi: "यह डोमेन दिखावटी/अंतरराष्ट्रीय अक्षरों का उपयोग कर रहा है — यह किसी असली डोमेन जैसा दिखने वाला नकली डोमेन हो सकता है", en: "This domain uses lookalike/internationalized characters — it could be a fake domain disguised as a real one" });
      risk = combine(risk, 0.6);
    }
    if (!reasons.length) {
      reasons.push({ sev: "info", hi: "इस लिंक में कोई ज्ञात चेतावनी-संकेत नहीं मिला, फिर भी सावधानी बरतें", en: "No known warning signs found in this link, but stay cautious regardless" });
    }

    return { reasons: reasons, risk: risk, fields: { Host: host || "—" } };
  }

  function analysePayload(rawText) {
    var raw = (rawText || "").trim();
    var type = "other";
    var result;
    if (/^upi:\/\//i.test(raw)) {
      type = "upi";
      result = analyseUpi(raw);
    } else if (/^https?:\/\//i.test(raw)) {
      type = "url";
      result = analyseUrl(raw);
    } else {
      type = "other";
      result = {
        reasons: [{ sev: "info", hi: "यह कोई UPI भुगतान लिंक या वेब लिंक नहीं लगता — नीचे असली पाठ देखें और यह सामान्य है या नहीं, यह स्वयं जाँचें", en: "This doesn't look like a UPI payment link or a web link — see the raw text below and judge for yourself whether it's expected" }],
        risk: 0,
        fields: {},
      };
    }
    var verdict = result.risk >= 0.6 ? "danger" : result.risk >= 0.3 ? "warn" : "safe";
    return { type: type, verdict: verdict, risk: result.risk, reasons: result.reasons, fields: result.fields, raw: raw };
  }

  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function renderQrVerdict(result, lang) {
    var isEn = lang === "en";
    var base = location.pathname.indexOf("/pages/") !== -1 ? "" : "pages/";
    var iconHref = (location.pathname.indexOf("/pages/") !== -1 ? "../" : "") + "assets/icons.svg";
    var cfg = {
      danger: { cls: "alert-danger", icon: "i-x-circle", hi: "खतरनाक — भुगतान/क्लिक न करें", en: "Dangerous — do not pay or click" },
      warn: { cls: "alert-warn", icon: "i-alert-triangle", hi: "संदिग्ध — सावधानी से आगे बढ़ें", en: "Suspicious — proceed with caution" },
      safe: { cls: "alert-safe", icon: "i-check-circle", hi: "कोई ज्ञात खतरा नहीं मिला", en: "No known danger found" },
    }[result.verdict];

    var typeLabel = { upi: isEn ? "UPI payment QR" : "UPI भुगतान QR", url: isEn ? "Web link QR" : "वेब लिंक QR", other: isEn ? "Other QR content" : "अन्य QR सामग्री" }[result.type];

    var fieldsHtml = "";
    var fieldKeys = Object.keys(result.fields || {});
    if (fieldKeys.length) {
      fieldsHtml = '<ul class="kv-list" style="margin-top:10px;">' +
        fieldKeys.map(function (k) { return "<li><span>" + esc(k) + "</span><span>" + esc(result.fields[k]) + "</span></li>"; }).join("") +
        "</ul>";
    }

    var reasonsHtml = '<ul class="kv-list" style="margin-top:10px;">' +
      result.reasons.map(function (r) {
        var badgeCls = r.sev === "danger" ? "badge-red" : r.sev === "warn" ? "badge-amber" : "badge-green";
        return "<li><span>" + esc(isEn ? r.en : r.hi) + "</span><span class=\"badge " + badgeCls + "\">" + (r.sev === "danger" ? (isEn ? "Risk" : "खतरा") : r.sev === "warn" ? (isEn ? "Caution" : "सावधानी") : (isEn ? "Info" : "जानकारी")) + "</span></li>";
      }).join("") + "</ul>";

    var rawBox = '<div style="margin-top:12px;padding:10px 12px;background:#f7f9fc;border:1px solid var(--border);border-radius:8px;font-family:monospace;font-size:12.5px;word-break:break-all;">' + esc(result.raw) + "</div>";

    var nextSteps = "";
    if (result.verdict !== "safe") {
      var steps = result.type === "upi"
        ? [
            { hi: "भुगतान से पहले दुकानदार से नाम बोलकर पुष्टि करें", en: "Confirm the payee name out loud with the shopkeeper before paying" },
            { hi: "अगर यह स्टिकर किसी दुकान के असली QR पर चिपका लगे, तो दुकानदार को बताएँ", en: "If this looks like a sticker pasted over a shop's real QR, tell the shopkeeper" },
            { hi: "संदेह होने पर कैश से भुगतान करें", en: "If in doubt, pay with cash instead" },
          ]
        : [
            { hi: "इस लिंक को न खोलें", en: "Don't open this link" },
            { hi: "कोई भी जानकारी, OTP या पिन न भरें", en: "Don't enter any details, OTP or PIN" },
          ];
      nextSteps = '<div style="margin-top:14px;padding-top:14px;border-top:1px solid rgba(0,0,0,.08);">' +
        "<h4 style=\"margin:0 0 8px;font-size:13.5px;\">" + (isEn ? "What to do now" : "अभी क्या करें") + "</h4>" +
        '<ul style="list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:7px;">' +
        steps.map(function (s) { return '<li style="display:flex;align-items:center;gap:8px;font-size:13.5px;"><svg class="icon" style="color:var(--danger);width:17px;height:17px;flex:none;"><use href="' + iconHref + '#i-x-circle"></use></svg><span>' + esc(isEn ? s.en : s.hi) + "</span></li>"; }).join("") +
        "</ul></div>";
    }

    return (
      '<div class="alert ' + cfg.cls + '">' +
      '<div class="icon" style="font-size:26px;"><svg class="icon"><use href="' + iconHref + "#" + cfg.icon + '"></use></svg></div>' +
      '<div style="flex:1">' +
      "<h3>" + (isEn ? cfg.en : cfg.hi) + ' <span class="badge badge-amber" style="margin-left:6px;">' + esc(typeLabel) + "</span></h3>" +
      fieldsHtml +
      reasonsHtml +
      rawBox +
      nextSteps +
      (result.verdict !== "safe" ? '<p style="margin-top:10px;"><a class="btn btn-sm btn-danger" href="' + base + 'report.html">' + (isEn ? "Report this" : "इसकी शिकायत करें") + "</a></p>" : "") +
      "</div></div>"
    );
  }

  global.CGQr = { analysePayload: analysePayload, renderQrVerdict: renderQrVerdict };
})(window);
