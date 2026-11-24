/* Offline cache for the core, same-origin part of the site: once a page has been opened at
 * least once (even over a slow connection), the detector/call-checker/QR-checker/learn/report
 * pages, their JS/CSS and the trained model.json load from this cache afterwards with no network
 * at all. This can't do anything for things that are inherently online by nature:
 *  - the CDN-hosted OCR engine (tesseract.js) and Google Fonts stylesheet — cross-origin,
 *    left to the network; if offline, OCR/photo-upload just won't run (paste/type still works),
 *  - the Web Speech API mic button and the "report a new-scam pattern to your district" backend —
 *    both need a live network connection by design, same as documented on their own pages.
 * Paths are written relative to this file (site root), so this works whether the site is
 * deployed at a domain root or under a subpath (e.g. a GitHub Pages project page). */

const CACHE_NAME = "cg-cyber-suraksha-v1";

const PRECACHE_URLS = [
  "index.html",
  "css/style.css",
  "js/app.js",
  "js/nb-model.js",
  "js/ocr-upload.js",
  "js/qr-check.js",
  "js/voice-input.js",
  "js/tts-read.js",
  "js/warning-poster.js",
  "js/scam-signs-poster.js",
  "js/report-share.js",
  "js/officials-alerts.js",
  "data/model.json",
  "data/dashboard.json",
  "assets/icons.svg",
  "assets/ashoka-emblem.png",
  "assets/cg-seal.webp",
  "pages/detector.html",
  "pages/call-checker.html",
  "pages/qr-checker.html",
  "pages/report.html",
  "pages/learn.html",
  "pages/poster.html",
  "pages/dashboard.html",
  "pages/officials.html",
  "pages/about.html",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) =>
      Promise.all(
        PRECACHE_URLS.map((url) =>
          cache.add(new URL(url, self.location)).catch(() => {
            // a single missing/renamed asset shouldn't block the whole install
          })
        )
      )
    )
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return; // leave CDN fonts/Tesseract to the network

  event.respondWith(
    caches.match(req).then((cached) => {
      const network = fetch(req)
        .then((res) => {
          if (res.ok) {
            const copy = res.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(req, copy));
          }
          return res;
        })
        .catch(() => cached);
      // Cache-first for anything already saved (instant + works offline), but still refresh
      // the cache in the background so a later online visit picks up any site update.
      return cached || network;
    })
  );
});
