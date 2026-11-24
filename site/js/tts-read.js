/* "सुनें" — read the verdict aloud using the browser's built-in SpeechSynthesis
 * API (no dependency). Helps anyone who struggles to read, or can't read at all. */
(function (global) {
  "use strict";

  function supported() {
    return !!global.speechSynthesis;
  }

  function speak(text, lang) {
    if (!supported() || !text) return;
    global.speechSynthesis.cancel();
    var u = new SpeechSynthesisUtterance(text);
    u.lang = lang === "en" ? "en-IN" : "hi-IN";
    u.rate = 0.95;
    global.speechSynthesis.speak(u);
  }

  function stop() {
    if (supported()) global.speechSynthesis.cancel();
  }

  function speakVerdict(result, lang) {
    var isEn = lang === "en";
    var pct = Math.round(result.probability * 100);
    var head;
    if (result.verdict === "scam") {
      head = isEn ? "Warning. This looks like a scam. Risk score " + pct + " percent."
                  : "चेतावनी। यह एक स्कैम जैसा लगता है। जोखिम स्कोर " + pct + " प्रतिशत।";
    } else if (result.verdict === "suspicious") {
      head = isEn ? "Be careful. This looks suspicious. Risk score " + pct + " percent."
                  : "सावधान रहें। यह संदिग्ध लगता है। जोखिम स्कोर " + pct + " प्रतिशत।";
    } else {
      head = isEn ? "This message appears genuine." : "यह मैसेज असली प्रतीत होता है।";
    }
    var reasons = (result.hits || []).slice(0, 4).map(function (h) {
      return isEn ? h.en : h.hi;
    });
    speak([head].concat(reasons).join(". "), lang);
  }

  global.CGTts = { supported, speak, stop, speakVerdict };
})(window);
