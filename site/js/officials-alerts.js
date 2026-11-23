/* Officials-facing view of emerging scam-pattern alerts (officials.html). Reads from
   the local alert backend (backend/app.py) — see report-share.js for how reports get
   there. Polls on a timer so a newly-clustered alert shows up without a manual reload. */
(function () {
  const API_BASE = window.CG_ALERT_API_BASE || "http://localhost:5000";
  const districtFilter = document.getElementById("district-filter");
  const refreshBtn = document.getElementById("refresh-btn");
  const statusText = document.getElementById("alerts-status-text");
  const listEl = document.getElementById("alerts-list");
  if (!listEl) return;

  let districts = [];
  let pollTimer = null;

  function currentLang() {
    return document.documentElement.lang === "en" ? "en" : "hi";
  }

  function relTime(iso) {
    const diffMs = Date.now() - new Date(iso + "Z").getTime();
    const mins = Math.round(diffMs / 60000);
    const lang = currentLang();
    if (mins < 60) return lang === "en" ? mins + " min ago" : mins + " मिनट पहले";
    const hrs = Math.round(mins / 60);
    if (hrs < 48) return lang === "en" ? hrs + " hr ago" : hrs + " घंटे पहले";
    const days = Math.round(hrs / 24);
    return lang === "en" ? days + " day(s) ago" : days + " दिन पहले";
  }

  function renderDistrictOptions() {
    const lang = currentLang();
    const current = districtFilter.value;
    districtFilter.innerHTML = "";
    const allOpt = document.createElement("option");
    allOpt.value = "";
    allOpt.textContent = lang === "en" ? "All districts" : "सभी जिले";
    districtFilter.appendChild(allOpt);
    districts.forEach((d) => {
      const opt = document.createElement("option");
      // Keyed by English name — matches the canonical value report-share.js sends,
      // regardless of which language the report was submitted in.
      opt.value = d.en;
      opt.textContent = lang === "en" ? d.en : d.hi;
      districtFilter.appendChild(opt);
    });
    districtFilter.value = current || "";
  }

  function districtLabel(enName) {
    const lang = currentLang();
    if (lang === "en") return enName;
    const match = districts.find((d) => d.en === enName);
    return match ? match.hi : enName;
  }

  function renderAlerts(alerts) {
    listEl.innerHTML = "";
    const lang = currentLang();
    if (alerts.length === 0) {
      const p = document.createElement("p");
      p.style.color = "var(--text-muted)";
      p.textContent =
        lang === "en"
          ? "No emerging pattern flagged right now."
          : "अभी कोई उभरता पैटर्न चिन्हित नहीं हुआ।";
      listEl.appendChild(p);
      return;
    }
    alerts.forEach((a) => {
      const card = document.createElement("div");
      card.className = "card";
      card.style.borderTop = "4px solid var(--danger)";
      card.innerHTML =
        '<div style="display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap;">' +
        '<span class="badge badge-red">' +
        (lang === "en" ? a.report_count + " reports" : a.report_count + " रिपोर्ट") +
        "</span>" +
        '<strong style="color:var(--navy);">' +
        districtLabel(a.district) +
        "</strong>" +
        "</div>" +
        '<p style="margin:12px 0;">' +
        a.sample_text.replace(/</g, "&lt;") +
        "</p>" +
        '<p class="hint" style="margin:0;">' +
        (lang === "en" ? "First seen: " : "पहली बार: ") +
        relTime(a.first_seen) +
        " · " +
        (lang === "en" ? "Last seen: " : "अंतिम बार: ") +
        relTime(a.last_seen) +
        "</p>";
      listEl.appendChild(card);
    });
  }

  function load() {
    const district = districtFilter.value;
    statusText.textContent = currentLang() === "en" ? "Loading…" : "लोड हो रहा है…";
    const url = API_BASE + "/api/alerts" + (district ? "?district=" + encodeURIComponent(district) : "");
    fetch(url)
      .then((r) => {
        if (!r.ok) throw new Error("bad status");
        return r.json();
      })
      .then((data) => {
        renderAlerts(data.alerts || []);
        statusText.textContent = "";
      })
      .catch(() => {
        listEl.innerHTML = "";
        statusText.textContent =
          currentLang() === "en"
            ? "Could not reach the alert server. Is backend/app.py running locally?"
            : "अलर्ट सर्वर से संपर्क नहीं हो सका। क्या backend/app.py स्थानीय रूप से चल रहा है?";
      });
  }

  fetch("../data/dashboard.json")
    .then((r) => r.json())
    .then((data) => {
      districts = (data.districts || []).map((d) => ({ hi: d.hi, en: d.en }));
      renderDistrictOptions();
      load();
    })
    .catch(load);

  document.addEventListener("cg:langchange", () => {
    renderDistrictOptions();
    load();
  });
  districtFilter.addEventListener("change", load);
  refreshBtn.addEventListener("click", load);

  pollTimer = setInterval(load, 15000);
  window.addEventListener("beforeunload", () => clearInterval(pollTimer));
})();
