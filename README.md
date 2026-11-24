# साइबर सुरक्षा | Cyber Suraksha

**Offline-first, regional-language cyber-fraud detection and digital financial literacy platform for Chhattisgarh.**

Paste a suspicious SMS/WhatsApp message, check a scam call script, or scan a QR code before you pay — a trained on-device ML model scores it instantly, in Hindi, English and Chhattisgarhi, with nothing sent over the internet. Built for the hackathon problem statement **"Cyber Fraud Detection & Digital Financial Literacy Platform"**, as both a website and a native Android app.

> **This is a hackathon prototype**, not an official Government of Chhattisgarh product. The site carries this disclaimer on every page, and the real Ashoka Emblem / Chhattisgarh state seal used in the header are included with explicit hackathon permission — see [`PROJECT_BRAIN.md`](PROJECT_BRAIN.md) §1 before reusing either elsewhere.

---

## What it does

| Tool | What it catches |
|---|---|
| **Message Scam Detector** | SMS / WhatsApp text or links — fake KYC, lottery/prize bait, courier-customs scams, "new number, send money" family scams, and more |
| **Call Scam Detector** | Call transcripts (paste, upload a recording, or live mic) — fake-bank-officer, "digital arrest", customs-fine scripts |
| **QR Code Checker** | Camera scan or uploaded QR image — malformed UPI payee IDs, suspicious amounts, phishing links disguised as payment QRs |
| **Scam Trend Dashboard** | Real NCRP complaint-data breakdown by scam type, for banks/district administration |
| **New-Scam District Alerts** | Opt-in, clusters similar reports from the *same district* in real time to flag emerging local scam patterns (the one piece of this project that needs a server) |
| **Safety Lessons & Quiz** | Short Hindi/Chhattisgarhi lessons on UPI PIN safety, KYC scams, fraud-call red flags |
| **Report Fraud** | One-tap links to 1930, cybercrime.gov.in and Chakshu, plus an evidence checklist |
| **Shareable posters** | Auto-generated "this is a scam" warning image, and a standalone "5 signs of a scam" poster designed to be forwarded village-to-village |

Both the **website** (`site/`) and the **Android app** (`android-app/`) ship the same underlying classifier and cover the same core tools — the app additionally has SMS auto-scan, call screening, a beta live-call voice warning, and a first-launch spotlight tour that walks through the app's main features in Chhattisgarhi.

## Why it's different

- **Actually offline.** The classifier, OCR (photo/video upload), and QR scanning all run entirely on-device — the Android app works with the phone in airplane mode. The website is a static site with a service worker that caches the core pages after a first visit, though a browser's mic input and the CDN-hosted OCR engine inherently need a connection (see [`PROJECT_BRAIN.md`](PROJECT_BRAIN.md) §8 for the full, honest per-feature offline audit).
- **Trilingual, not translated-after-the-fact.** Hindi and Chhattisgarhi text is authored directly, with English as the toggle-able alternative — including audio narration (Android's on-device TextToSpeech) for low-literacy users, since Chhattisgarhi has no distinct synthesis voice, it's read by the Hindi voice.
- **A real trained model, not keyword rules alone.** A from-scratch multinomial Naive Bayes classifier (no scikit-learn available in the training environment) trained on ~30k real + synthetic messages, combined via noisy-OR with a hand-curated bilingual red-flag lexicon as a deterministic backstop for phrasing the model alone under-detects.
- **Honest about its limits.** Known gaps (district-level dashboard numbers are synthesised, Chhattisgarhi lesson text isn't linguist-verified, held-out accuracy partly reflects dataset style) are stated in-product on the About page, not hidden.

## Screens

**Website** — `index.html`, Message/Call/QR detectors, Report, Learn, Dashboard, Officials (new-scam alerts), a shareable scam-signs poster, and an About page documenting the datasets and model honestly.

**Android app** — a 5-tab home screen (Message / Call / QR detectors, Learn, Report) plus a More screen for SMS auto-scan, call screening, the live-call-warning beta, and the trusted-contact one-tap-call feature.

---

## Running it

### Website
No build step — plain HTML/CSS/JS.
```powershell
cd site
python -m http.server 8080
```
Then open `http://localhost:8080`.

### Android app
Open `android-app/` in Android Studio (or build from the CLI):
```powershell
cd android-app
./gradlew assembleDebug
```
`app-debug.apk` lands in `app/build/outputs/apk/debug/`. Minimum SDK 26, target/compile SDK 35.

### Backend (optional — new-scam district alerts only)
The one non-offline piece of this project. Everything else works without it.
```powershell
cd backend
pip install -r requirements.txt
python app.py
```
Serves on `0.0.0.0:5000`. For the Android app to reach a backend running on your dev machine over USB: `adb reverse tcp:5000 tcp:5000`.

---

## Repository layout

```
site/            the website — static HTML/CSS/JS, no build step
android-app/     the native Android app (Kotlin)
backend/         Flask + SQLite server for district scam-pattern clustering (opt-in feature)
scripts/         dataset fetch/convert, synthetic data generation, model training, dashboard build
data/            raw datasets (gitignored) and the held-out accuracy report
PROJECT_BRAIN.md the living project memory — every design decision, bug found/fixed, and why
```

## The ML model

Trained on real datasets (`CloveAI/india-spam-sms`, `haxrits/ScamShield-IN`, a machine-translated Hindi SMS-spam corpus) plus ~700 hand-templated domain-realistic genuine/scam examples added to fix dataset gaps found during testing (e.g. almost no genuine courier/OTP/UPI messages in the raw corpora, causing false positives). Tokenizer and stopword list are shared line-for-line between the Python trainer and the JS/Kotlin inference code. Verdict = noisy-OR of the trained model's probability and a bilingual red-flag lexicon match (OTP, KYC, prize, urgency, payment, courier, "digital arrest", job/investment/loan offers, Aadhaar/PAN requests).

Held-out test split: **98.8% accuracy, 97.5% recall on the scam class** — with the honest caveat (stated on the About page) that genuine/scam examples partly come from different source datasets, so real-world accuracy on genuinely novel phrasing is likely lower. Treat this as a prototype baseline, not a production guarantee.

Retrain after changing data: `python scripts/train_model.py` (writes `site/data/model.json`, synced to `android-app/app/src/main/assets/model.json`).

## Tech stack

- **Web**: static HTML/CSS/vanilla JS, Tesseract.js (OCR, CDN), jsQR equivalent for QR decoding, Web Speech API, a service worker for offline caching.
- **Android**: Kotlin, ViewBinding, CameraX + ML Kit (on-device OCR and QR/barcode scanning), Android `SpeechRecognizer`/`TextToSpeech`.
- **Model**: hand-rolled multinomial Naive Bayes (Python training, parallel JS/Kotlin inference), no ML framework dependency at inference time.
- **Backend**: Flask + SQLite, Jaccard-similarity clustering (not a vendored ML library — this project explains what it actually does).

## Contributing

This is a hackathon submission. [`PROJECT_BRAIN.md`](PROJECT_BRAIN.md) is the canonical source of project history — read it before making changes, and update its Change Log after.
