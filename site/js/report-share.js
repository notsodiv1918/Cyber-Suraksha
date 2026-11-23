/* Opt-in "share this report with your district" widget on report.html.
   Sends {text, district, lang} to the local alert backend (backend/app.py) only
   when the user explicitly taps "Share report" — nothing is sent automatically.
   The backend clusters similar reports per district and flags an emerging pattern
   once enough similar reports land within a short window (see backend/app.py). */
(function () {
  const API_BASE = window.CG_ALERT_API_BASE || "http://localhost:5000";

  const districtSelect = document.getElementById("share-district");
  const textArea = document.getElementById("share-text");
  const submitBtn = document.getElementById("share-submit-btn");
  const statusText = document.getElementById("share-status-text");
  if (!districtSelect || !textArea || !submitBtn) return;

  let districts = [];

  function currentLang() {
    return document.documentElement.lang === "en" ? "en" : "hi";
  }

  function renderDistrictOptions() {
    const lang = currentLang();
    districtSelect.innerHTML = "";
    districts.forEach((d) => {
      const opt = document.createElement("option");
      // Always key by the English name so a Hindi-mode report and an English-mode
      // report for the same district cluster together server-side — only the
      // visible label changes with the language toggle.
      opt.value = d.en;
      opt.textContent = lang === "en" ? d.en : d.hi;
      districtSelect.appendChild(opt);
    });
  }

  fetch("../data/dashboard.json")
    .then((r) => r.json())
    .then((data) => {
      districts = (data.districts || []).map((d) => ({ hi: d.hi, en: d.en }));
      renderDistrictOptions();
    })
    .catch(() => {
      statusText.textContent =
        currentLang() === "en" ? "Could not load district list." : "जिला सूची लोड नहीं हो सकी।";
    });

  document.addEventListener("cg:langchange", renderDistrictOptions);

  function setStatus(key) {
    const lang = currentLang();
    const messages = {
      sending: ["Sending…", "भेजा जा रहा है…"],
      sent: ["Shared — thank you.", "साझा किया गया — धन्यवाद।"],
      empty: ["Paste a message or call summary first.", "पहले मैसेज या सारांश पेस्ट करें।"],
      error: [
        "Could not reach the alert server. Is backend/app.py running locally?",
        "अलर्ट सर्वर से संपर्क नहीं हो सका। क्या backend/app.py स्थानीय रूप से चल रहा है?",
      ],
    };
    const pair = messages[key] || ["", ""];
    statusText.textContent = lang === "en" ? pair[0] : pair[1];
  }

  submitBtn.addEventListener("click", () => {
    const text = textArea.value.trim();
    if (!text) {
      setStatus("empty");
      return;
    }
    setStatus("sending");
    submitBtn.disabled = true;
    fetch(API_BASE + "/api/report", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text: text,
        district: districtSelect.value,
        lang: currentLang(),
        source: "web",
      }),
    })
      .then((r) => {
        if (!r.ok) throw new Error("bad status");
        return r.json();
      })
      .then(() => {
        setStatus("sent");
        textArea.value = "";
      })
      .catch(() => setStatus("error"))
      .finally(() => {
        submitBtn.disabled = false;
      });
  });
})();
