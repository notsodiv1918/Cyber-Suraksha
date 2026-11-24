/* "बोलकर जाँचें" — speak instead of type. Wraps the browser's Web Speech API
 * (webkitSpeechRecognition on Chrome/Android) so a mic button can fill any
 * textarea/input by voice — much easier than typing for low-literacy users.
 * Falls back to disabling the button with a note when the API isn't available
 * (e.g. desktop Firefox) rather than pretending it works. */
(function (global) {
  "use strict";

  function supported() {
    return !!(global.SpeechRecognition || global.webkitSpeechRecognition);
  }

  function attach(buttonEl, targetEl, opts) {
    opts = opts || {};
    if (!buttonEl || !targetEl) return;
    if (!supported()) {
      buttonEl.style.display = "none";
      return;
    }
    var Recognition = global.SpeechRecognition || global.webkitSpeechRecognition;
    var recognizing = false;
    var recognition;

    function langCode() {
      return document.documentElement.lang === "en" ? "en-IN" : "hi-IN";
    }

    function start() {
      recognition = new Recognition();
      recognition.lang = langCode();
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      recognition.onstart = function () {
        recognizing = true;
        buttonEl.classList.add("mic-active");
      };
      recognition.onresult = function (e) {
        var text = e.results[0][0].transcript;
        if (targetEl.tagName === "TEXTAREA" || targetEl.tagName === "INPUT") {
          targetEl.value = text;
          targetEl.dispatchEvent(new Event("input", { bubbles: true }));
        }
        if (typeof opts.onResult === "function") opts.onResult(text);
      };
      recognition.onerror = function () {
        recognizing = false;
        buttonEl.classList.remove("mic-active");
      };
      recognition.onend = function () {
        recognizing = false;
        buttonEl.classList.remove("mic-active");
      };
      recognition.start();
    }

    buttonEl.addEventListener("click", function () {
      if (recognizing && recognition) {
        recognition.stop();
        return;
      }
      start();
    });
  }

  global.CGVoice = { supported, attach };
})(window);
