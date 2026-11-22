/* Shared chrome behaviour: language toggle, text size, mobile nav, emergency widget.
   Translation approach: Hindi is authored directly in the HTML. Any element that
   needs an English version carries data-en="..." (and data-en-ph="..." for
   placeholders). Toggling language swaps innerHTML/placeholder in place and
   remembers the Hindi original in data-hi so it can be restored. */

(function () {
  const LANG_KEY = "cg_cfd_lang";
  const SIZE_KEY = "cg_cfd_textsize";

  function applyLang(lang) {
    document.documentElement.lang = lang === "en" ? "en" : "hi";
    document.querySelectorAll("[data-en]").forEach((el) => {
      if (lang === "en") {
        if (!el.hasAttribute("data-hi")) el.setAttribute("data-hi", el.innerHTML);
        el.innerHTML = el.getAttribute("data-en");
      } else {
        if (el.hasAttribute("data-hi")) el.innerHTML = el.getAttribute("data-hi");
      }
    });
    document.querySelectorAll("[data-en-ph]").forEach((el) => {
      if (lang === "en") {
        if (!el.hasAttribute("data-hi-ph")) el.setAttribute("data-hi-ph", el.getAttribute("placeholder") || "");
        el.setAttribute("placeholder", el.getAttribute("data-en-ph"));
      } else if (el.hasAttribute("data-hi-ph")) {
        el.setAttribute("placeholder", el.getAttribute("data-hi-ph"));
      }
    });
    document.querySelectorAll(".lang-toggle button").forEach((b) => {
      b.classList.toggle("active", b.dataset.lang === lang);
    });
    localStorage.setItem(LANG_KEY, lang);
    document.dispatchEvent(new CustomEvent("cg:langchange", { detail: { lang } }));
  }

  function initLang() {
    const saved = localStorage.getItem(LANG_KEY) || "hi";
    applyLang(saved);
    document.querySelectorAll(".lang-toggle button").forEach((b) => {
      b.addEventListener("click", () => applyLang(b.dataset.lang));
    });
  }

  function initTextSize() {
    const saved = localStorage.getItem(SIZE_KEY);
    if (saved) document.body.classList.add(saved);
    document.querySelectorAll("[data-textsize]").forEach((b) => {
      b.addEventListener("click", () => {
        document.body.classList.remove("text-lg", "text-xl");
        const v = b.dataset.textsize;
        if (v !== "base") {
          document.body.classList.add(v);
          localStorage.setItem(SIZE_KEY, v);
        } else {
          localStorage.removeItem(SIZE_KEY);
        }
      });
    });
  }

  function initNavToggle() {
    const btn = document.querySelector(".nav-toggle");
    const nav = document.querySelector(".site-nav ul");
    if (!btn || !nav) return;
    btn.addEventListener("click", () => nav.classList.toggle("open"));
  }

  function initEmergencyWidget() {
    const fab = document.querySelector(".emergency-fab");
    const panel = document.querySelector(".emergency-panel");
    if (!fab || !panel) return;
    fab.addEventListener("click", () => panel.classList.toggle("open"));
    document.addEventListener("click", (e) => {
      if (!panel.contains(e.target) && !fab.contains(e.target)) panel.classList.remove("open");
    });
  }

  function initTabs() {
    document.querySelectorAll(".tabs").forEach((tabs) => {
      const buttons = tabs.querySelectorAll(".tab-btn");
      buttons.forEach((b) => {
        b.addEventListener("click", () => {
          buttons.forEach((x) => x.classList.remove("active"));
          b.classList.add("active");
          const panelId = b.dataset.tab;
          const group = tabs.dataset.group;
          document.querySelectorAll(`.tab-panel[data-group="${group}"]`).forEach((p) => {
            p.classList.toggle("active", p.id === panelId);
          });
        });
      });
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    initLang();
    initTextSize();
    initNavToggle();
    initEmergencyWidget();
    initTabs();
  });
})();
