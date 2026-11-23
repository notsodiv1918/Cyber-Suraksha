# Project Brain — Cyber Suraksha & Digital Financial Literacy Portal (Chhattisgarh)

> **Purpose of this file**: a living memory of this project — what it is, how it's built, every
> design/content decision made across our sessions, and the conventions to follow so future
> changes stay consistent. Whenever a change is made to this project in a session, update the
> **Change Log** section at the bottom (newest entry on top) and any other section it affects.
> This file is the first thing to read before making changes, and the last thing to update after.

---

## 1. What this is

A hackathon prototype for the problem statement **"Cyber Fraud Detection & Digital Financial
Literacy Platform"**: an offline, regional-language web app for Chhattisgarh that:
- detects scam SMS/WhatsApp messages and scam-call scripts using a real trained ML model,
- teaches digital payment safety in Hindi and Chhattisgarhi,
- connects citizens to official fraud reporting (1930, cybercrime.gov.in, Chakshu),
- gives banks/district administration a scam-trend dashboard.

Visual design is inspired by `cgstate.gov.in` (the real Chhattisgarh state portal), but the site
is explicitly labelled throughout as a **hackathon prototype, not an official production site**
(see the topbar disclaimer on every page) — this label must never be removed.

The user has **explicit permission from the hackathon** to use the real Government of India
emblem (Ashoka Lion Capital) and the real Government of Chhattisgarh seal as image assets in the
header (see `site/assets/ashoka-emblem.png`, `site/assets/cg-seal.webp`). Without that permission
these would not normally be used verbatim on an unofficial site — don't reuse this precedent on
other projects without the same explicit sign-off.

## 2. Repository layout

```
D:\hackasol\
  PROJECT_BRAIN.md          <- this file
  data/
    raw/                    <- downloaded dataset JSONL/parquet (india_spam_sms, scamshield_in,
                               crime_reports_sample, hindi_translated_sms, hinglish_subset,
                               synthetic_domain)
    model_report.json        <- held-out accuracy report from the last training run
  scripts/
    fetch_datasets.py         <- original HF datasets-server fetch (rate-limited, superseded)
    parquet_to_jsonl.py       <- converts downloaded .parquet -> .jsonl (the reliable path)
    generate_synthetic.py     <- templated domain-specific genuine/scam examples (see §4)
    train_model.py            <- trains the Naive Bayes model, writes site/data/model.json
    build_dashboard.py        <- aggregates crime_reports_sample.jsonl -> site/data/dashboard.json
  site/                      <- the actual deliverable, static HTML/CSS/JS, no build step
    index.html               <- homepage
    pages/
      detector.html           <- message scam detector
      call-checker.html       <- call scam detector (paste-transcript + experimental live mic)
      report.html             <- reporting handoff (1930 / cybercrime.gov.in / Chakshu)
      learn.html              <- lessons (Hindi/Chhattisgarhi) + quiz
      dashboard.html          <- scam-trend dashboard (charts + district table)
      about.html              <- datasets, architecture, accuracy, limitations
    css/style.css             <- single shared stylesheet, one design system for every page
    js/
      app.js                  <- shared chrome: language toggle, text-size, mobile nav, emergency
                                 widget, tabs
      nb-model.js              <- CGScam global: loads model.json, classifies text, renders verdict
    assets/
      icons.svg                <- hand-authored inline SVG icon sprite (~35 icons, no emoji anywhere)
      ashoka-emblem.png, cg-seal.webp   <- real government emblem assets (see §1 permission note)
      cg-map.png                        <- real Chhattisgarh district map photo (user-provided)
      hero-photo-1..4.{webp,png}         <- the 4 hero illustration/photo assets (user-provided)
    data/
      model.json               <- trained Naive Bayes model (generated, don't hand-edit)
      dashboard.json            <- aggregated scam-trend data (generated, don't hand-edit)
  .claude/launch.json         <- `python -m http.server 8080 --directory site` preview config
```

## 3. Running / previewing

Use the Browser tool's `preview_start` with name `cg-cyber-site` (reads `.claude/launch.json`),
which serves `site/` at `http://localhost:8080`. There is no build step — HTML/CSS/JS are edited
directly and served as static files.

## 3b. Git / GitHub

The repo is pushed to `https://github.com/notsodiv1918/Cyber-Suraksha` (`main` branch). `data/raw/`
(the ~96MB of raw downloaded training datasets) is gitignored on purpose — the site only needs
`site/data/model.json` (the trained model) and `site/data/dashboard.json` at runtime, so the raw
dumps would just bloat the repo for no benefit. **Convention: commit and push after every
meaningful change from here on** (the user asked for this explicitly) — `git add -A && git commit
-m "..." && git push`, same attribution footer as any other commit in this project.

## 4. The ML model (message/call scam detector)

- **Datasets** (real, downloaded via Hugging Face `datasets-server` parquet export, not the
  rate-limited rows API): `CloveAI/india-spam-sms` (20,010 rows), `haxrits/ScamShield-IN` (8,066
  rows, has `fraud_type`), `Dev523/Crime-Reports-Dataset` (93,686 rows, sampled to 12,000 — this
  is the National Cybercrime Reporting Portal complaint taxonomy, used for the dashboard, not
  the classifier), `dbarbedillo/SMS_Spam_Multilingual_Collection_Dataset` (5,572 rows, machine-
  translated to Hindi — needed because the India-spam/ScamShield corpora are ~100% English/Latin
  script and the model couldn't score Devanagari text at all without this), and a ~44-row genuine
  Hinglish subset mined from `bolewara/hinglish-scam-text-dataset`.
- **Known dataset bias found and fixed**: the raw corpora almost had **zero genuine courier/
  OTP/UPI confirmation examples** (those words appeared mostly in scam text), causing false
  positives on plain "your order has shipped" style messages. Fixed by `generate_synthetic.py`,
  which templates ~700 domain-realistic genuine *and* scam examples (bank alerts, OTP messages,
  KYC/digital-arrest/courier scam scripts) in English/Hindi/Hinglish, weighted 3x in training.
- **Tokenizer**: lowercase, strip URLs to a literal `http` token, keep Devanagari + Latin +
  digits, word tokens + char-4-grams for words >4 chars, with a **stopword list shared between
  Python (`train_model.py`) and JS (`nb-model.js`) — if you touch one, touch the other**, since
  without it common Hindi/Hinglish function words swamp the few strong scam keywords in a short
  message (this was a real bug, documented the hard way).
- **Model**: Multinomial Naive Bayes, hand-rolled (no sklearn available in this environment),
  Laplace smoothing, vocab pruned to tokens seen ≥3 times to keep `model.json` small (~1.3MB).
  **97.2% held-out accuracy** (15% split), documented honestly in `about.html` including the
  caveat that genuine/scam classes partly come from different source datasets so some of that
  separability may be dataset-style, not pure content signal.
- **Serving-time hybrid**: `nb-model.js` combines the NB probability with a hand-curated bilingual
  **red-flag lexicon** (OTP, KYC, prize, link, urgency, payment, courier, arrest, job, investment,
  loan, Aadhaar) via a **noisy-OR**: `prob = 1 - (1-nbProb)*(1-lexiconProb)`. This exists because
  the trained model alone under-detects real-world Hinglish fraud phrasing — the lexicon acts as
  a deterministic backstop. Verdict thresholds: `≥0.6` scam, `≥0.3` suspicious, else genuine.
- To retrain after changing data/synthetic templates: `python scripts/train_model.py` (writes
  `site/data/model.json` + `data/model_report.json`). To rebuild dashboard data after changing
  `crime_reports_sample.jsonl` or the region groupings: `python scripts/build_dashboard.py`.

## 5. Design system conventions (follow these for any new page/component)

- **Bilingual text pattern**: Hindi is the literal content of the element; add `data-en="..."`
  for the English version. `app.js`'s language toggle swaps `innerHTML` and remembers the Hindi
  original in `data-hi`. If the English value needs inline HTML (e.g. an icon), use **single
  quotes** for the nested tag's attributes (`data-en="<svg class='icon'>..."`) — double quotes
  there will break the attribute and corrupt the page (this happened twice, see Change Log).
  For `placeholder`, use `data-en-ph`.
- **Icons**: never emoji. Use `<svg class="icon"><use href="../assets/icons.svg#i-name"></use></svg>`
  (path is `assets/icons.svg` from `index.html`, `../assets/icons.svg` from `pages/*.html`). Add
  new glyphs to the sprite's `<defs>` rather than inlining ad-hoc SVG paths elsewhere.
- **Corners**: sharp, not rounded. `--radius: 2px` is the global token; only the circular emblem/
  seal and the emergency SOS floating button stay round (they're meant to read as badges/FABs).
- **Container width**: `--container` max-width is 1480px with 32px side padding — this was
  widened from an original 1180px specifically to kill large empty side-gutters on wide monitors.
- **Colour palette**: saffron `#FF9933`, india-green `#138808`, navy `#0b2447` as the core
  tricolor-government palette. "Dark pastel" tiles (cycling stat tiles, coverflow accents) use
  muted-dark tones, not bright pastel and not near-black — e.g. mustard `#9c8426`, crimson
  `#9c3240`, moss-green `#43815a`.
- **Every page** repeats the same topbar → header (emblem + brand + gov-tag) → nav → page-head →
  content → footer → emergency-widget skeleton. When editing one page's chrome, the other 6 files
  usually need the identical edit (header/nav/footer/emergency-widget markup is duplicated, not
  templated — there's no server-side include).
- **Hero section** (`index.html` only): the photo slideshow is a `position:absolute` sibling of
  `.container` directly inside `<section class="hero">`, **not** a flex child inside the padded
  container — it must stay that way or the container's own padding reintroduces gaps at the top/
  right edges (this was a real bug, see Change Log). The image uses `object-fit:cover` (fills the
  box completely, by design — "don't crop" and "fill completely" were both asked for at different
  points; cover-with-no-letterbox is the final, current preference). The left-edge blend into the
  dark hero background is a `::before` gradient overlay (not CSS `mask-image` — that caused
  GPU-compositing screenshot artifacts in the preview tool; gradient overlay is simpler and
  equally effective). Hero photos are hidden below 900px viewport width.
- **Coverflow / carousel pattern** (services section): a `position:relative` track with
  `position:absolute` cards; JS computes `diff = index - active` (shortest-path wraparound) and
  applies transform/opacity/blur per diff value. Card height must be **`height:auto`**, not a
  fixed px value — a fixed height plus a show/hide-on-active content toggle caused a visible
  resize-glitch mid-transition (see Change Log). Don't reintroduce a fixed height.
- **Government emblems**: a protected national/state symbol (Ashoka emblem, state seal) should
  **not** normally be used verbatim on an unofficial-looking government-style site — that's an
  impersonation risk. It's only in this project because the user confirmed hackathon-granted
  permission (§1). If this project is ever repurposed without that permission, swap back to a
  generic stylized pillar+seal icon (an earlier, abstracted version existed — see Change Log).

## 6. Known limitations (stated honestly in `about.html` — keep them true or update both places)

- Call-scam "detector" is really the same text classifier run on a pasted/typed transcript; there
  is no real on-device speech-to-text in this prototype (described as a production-roadmap item).
- District-level numbers on the dashboard are **synthesised** (the real source data — National
  Cybercrime Reporting Portal — has no location field); only the scam-*type* totals are real.
- Chhattisgarhi lesson text is hand-written for this prototype, not a linguist-verified corpus.
- Model accuracy (97.2%) is a held-out split of a corpus partly assembled from different source
  datasets per class — treat as a prototype baseline, not a production guarantee.

---

## Change Log (newest first)

- **Android app UI redesign + a real false-positive fixed in BOTH the model and the classifier
  logic, on the website and the app.**
  1. **UI redesign**: replaced the plain single-screen layout with one matching the site's actual
     visual identity instead of a generic "AI mockup" look (the user's reaction to a reference
     mockup: "this is sooo AI... make it eye pleasing and match the colour theme") — sharp 3-4dp
     corners (not heavy rounding), flat colors, no gradients, no shadows beyond a hairline border,
     same navy/saffron/danger/warn/safe palette as `css/style.css`. Added 8 hand-drawn vector icons
     (`ic_shield_check`, `ic_mail`, `ic_phone`, `ic_upload`, `ic_alert_triangle`, `ic_check_circle`,
     `ic_x_circle`, `ic_chevron_right`) ported directly from `site/assets/icons.svg`'s own path data
     for visual continuity between the app and the website. Status rows now use colored dots
     (green/gray) instead of plain text; the verdict display is a proper colored banner+icon
     (matching the site's `.alert` component) instead of a plain colored `TextView`; history rows
     are now icon-chip cards instead of bare text blocks.
  2. **Real bug found and fixed**: a genuine "Yooo how are you? Long time no see" text from a
     friend was flagged as a likely scam (confirmed live on-device during testing). Diagnosed with
     a standalone Python port of the classifier run directly against `model.json`: the words "how"/
     "long"/"time"/"see" had drastically skewed per-class log-probabilities (e.g. "how":
     `[-13.85, -7.46]`) because the real training corpora's genuine/"ham" class is almost entirely
     transactional SMS (bank alerts, OTP, delivery notices) with essentially zero casual
     conversational text, while the scam class is full of short informal messages — so any plain
     short chat statistically looked "scammy" to the model, with zero lexicon support either way.
     Two-part fix:
     - **Data**: added `GENUINE_CASUAL_EN/HI/HINGLISH` template sets to
       `scripts/generate_synthetic.py` (everyday chat: "how are you", "running late", "happy
       birthday", etc. in English/Hindi/Hinglish) and bumped the synthetic set's training-weight
       multiplier in `scripts/train_model.py` from 3x to 8x. Retrained — accuracy actually went
       **up** (97.15% → 97.27%, recall 92.8% → 93.4%, still only ~15 false positives / ~167 false
       negatives out of ~6000 held-out examples), confirming this wasn't a precision/recall
       trade-off, just a real gap being filled. The target phrase's score dropped from ~0.9999997
       to 0.33 from retraining alone.
     - **Logic**: added a genuine classifier-level safeguard — **in both** `site/js/nb-model.js`
       and `android-app/.../Classifier.kt`, verdicts with zero lexicon hits (no concrete red-flag
       phrase to point to, pure NB statistics only) now need higher confidence to alarm:
       scam threshold 0.6→0.85, suspicious 0.3→0.5, vs. the original thresholds unchanged when a
       real red-flag phrase backs the verdict up. This is deliberately more conservative only when
       there's nothing to show the user as "why" — explainability-gated, not a blanket threshold
       change — and is what actually pushed the target phrase (0.33, zero hits) under the
       suspicious line to "genuine", since the data fix alone wasn't sufficient on its own.
     - Verified: the target phrase and a few other casual messages ("running a bit late", "good
       morning") now read genuine on both the website and the rebuilt/reinstalled Android app (via
       `adb`, confirmed with screenshots); real scams (KYC phishing, lottery, "mom my phone broke"
       — including ones with zero lexicon hits) all still score 1.0/scam, unaffected.
     - `site/data/model.json` and `android-app/app/src/main/assets/model.json` kept in sync (same
       file, copied) — **always re-copy after retraining**, they drift silently otherwise.

- **New native Android app (`android-app/`) — the auto-scan roadmap item, actually built.**
  Started as a native Kotlin/Gradle project (Android Studio + SDK + `adb` were already installed
  on this machine, so built it for real rather than leaving it as a pitch slide). Package
  `com.cybersuraksha.app`, minSdk 26, compileSdk/targetSdk 35, Gradle wrapper pinned to 8.9 (AGP
  8.7.3, Kotlin 2.0.21) — builds clean with `./gradlew.bat :app:assembleDebug` using the JDK 21
  bundled inside Android Studio's own `jbr/` folder (the system `java` on this machine is only
  Java 8, too old for modern Gradle — always point `JAVA_HOME` at
  `C:\Program Files\Android\Android Studio\jbr` when building this project, not the system JDK).
  - **`Classifier.kt`** is a line-for-line Kotlin port of `site/js/nb-model.js`'s `classify()` —
    same tokenizer (Devanagari+Latin, word + char-4-gram tokens, shared stopword list), same
    12-category lexicon, same noisy-OR combination, same 0.6/0.3 thresholds — reading the
    identical `site/data/model.json` (copied into `app/src/main/assets/`), so a message gets the
    same verdict on-device in the app as it would on the website. No retraining, no new model.
  - **`SmsReceiver.kt`**: a `RECEIVE_SMS` broadcast receiver (not SMS Retriever — that API only
    sees messages carrying a special app-signature hash, useless for scanning arbitrary incoming
    SMS; corrected this out loud to the user when asked, since an earlier conversation turn had
    said the wrong API). Classifies every incoming SMS immediately and posts a notification if
    scam/suspicious (silent for genuine, so it doesn't nag).
  - **`CallScreenService.kt`** (`CallScreeningService`, Android 10+): deliberately **never
    auto-blocks or auto-labels a call as scam** — we have no real caller-reputation/phone-number
    dataset (the training data is message text), so pretending to classify a phone *number* would
    be a false claim. It logs the number and shows a generic caution notification instead,
    nudging the user toward the same transcript-check flow as the website's Call Scam Detector.
  - **UI**: one Activity, plain Views (no Compose — kept dependencies light), matching the
    website's palette via `colors.xml` (`navy` #4A9BDB, `saffron` #FF9933, `danger`/`warn`/`safe`
    tokens lifted straight from `css/style.css`). Trilingual EN/Hindi/Chhattisgarhi via a manual
    `T` object (`Strings.kt`) cycling languages on a button tap — same "no Android locale-qualifier
    juggling, just a plain data lookup" approach as the site's `data-en`/`data-hi` pattern, since
    Chhattisgarhi has no well-supported Android resource qualifier. `HistoryStore.kt` is a simple
    SharedPreferences-backed JSON list (last 30 scans) — no Room/SQLite, intentionally, for a
    prototype's "recent activity" feed.
  - Debug APK builds successfully (~6MB, `app/build/outputs/apk/debug/app-debug.apk`). Added a
    `.gitignore` for `build/`, `.gradle/`, `local.properties` (machine-specific SDK path, never
    commit it) before pushing.

- **Filled the blank space beside the scam-scripts accordion.** The new accordion (see previous
  entry) is deliberately narrower than the container, which left a wide empty gap to its right on
  desktop. Added a sticky `.script-aside` card there — not decorative filler, but the actual
  cross-cutting insight across all 6 scripts: "the pattern behind all of them" (claims authority →
  creates urgency → demands OTP/PIN/UPI payment), plus a "Report a call like this →" link to
  report.html. `.script-file` and the new `.script-aside` sit in a `.script-layout` grid
  (`1fr 320px`, collapses to one column under 900px). Verified at 1100px width — card renders
  fully, nothing clipped, stays sticky alongside the accordion while scrolling.

- **Call Scam Detector: audio file upload + "known scam call scripts" redesigned as a minimalist
  accordion.**
  1. **Audio upload** (`pages/call-checker.html`): investigated whether an uploaded audio FILE
     could be auto-transcribed in-browser before building anything. Tried the classic
     "spoof `navigator.mediaDevices.getUserMedia` to feed a file's `MediaStreamAudioDestinationNode`
     into `SpeechRecognition`" trick — **tested it for real** with a genuine synthesized-speech WAV
     (via Windows SAPI/PowerShell `System.Speech`, not just a tone) and confirmed it does NOT work
     in this Chromium build: `webkitSpeechRecognition.start()` triggers a real OS mic-permission
     prompt regardless of the override, meaning Chrome's implementation doesn't actually route
     through the page-overridable `getUserMedia` the way some blog posts claim. So there is no
     reliable way to transcribe an arbitrary audio file offline in-browser (confirms what the
     page's own disclaimer already said). Built the honest version instead: a third tab "Upload
     audio" next to Paste/Live-mic, with a styled upload button (matching the detector page's own
     photo/video upload button), an `<audio controls>` player for the uploaded file, and its own
     transcript textarea + analyse button — upload plays back, user listens and types, same
     `analyseCall()`/classifier path as the other two tabs. Verified the full pipeline end-to-end
     with a synthetic WAV (built in-browser from an `OfflineAudioContext` + a hand-rolled WAV
     encoder, since no test audio asset existed in the repo).
  2. **"Known scam call scripts" section**: replaced the 6-box `.grid.cols-3` (same static-card
     pattern as the old message-detector red-flags grid) with a single-column "case file" accordion
     — numbered rows (`01`–`06`) with icon + title, one row open at a time showing its description,
     danger-red accent (`var(--danger)`) on the open row's number/icon/chevron. Deliberately a
     different interaction pattern from the message-detector's scroll-driven radial diagram (per
     explicit instruction not to repeat that), while staying equally minimalist and using only
     existing tokens (`var(--danger)`, `var(--border)`, `var(--text-muted)`, `var(--bg)`).

- **Fixed the radial red-flags section getting cut off on wide/short windows.** The sticky panel
  had no height limit of its own, so on a wide-but-not-tall browser window (confirmed with the
  user's own 1920×~750 screenshot) the 460px-wide circle and the 12-item timeline both overflowed
  past the bottom of the viewport with no way to scroll and reveal the rest — the whole point of
  `position:sticky` is that the page scroll is "spent" driving the animation, so anything that
  doesn't fit in that one sticky slice was just invisible. Fixed by giving `.flag-sticky` an
  explicit `height:calc(100vh - var(--header-h) - 24px)` (clamped between 420px and 640px) instead
  of unconstrained `padding:30px 0`, then making both children fit exactly inside that: `.flag-radial`
  now derives its size from `height:100%` + `aspect-ratio:1/1` (instead of a fixed `max-width:460px`
  that ignored available height) so the circle always fits and stays centered, and `.flag-timeline`
  is now its own `overflow-y:auto` scroll box with the scrollbar hidden, with the active item kept
  centered by setting `timelineEl.scrollTop` directly in JS (replaced the old
  `el.scrollIntoView()` call, which would have scrolled the whole page instead of just the inner
  list and fought with the progress calculation). Mobile (`max-width:760px`) resets all of this
  back to `height:auto`/`overflow:visible` since that breakpoint already drops the sticky-pin
  behavior entirely. Verified by emulating a 1920×768 window (matching the bug report) — full
  circle now visible and centered, timeline scrolls to keep the active item in view — and
  re-checked mobile still renders unaffected.

- **"What the model looks for" (detector.html) rebuilt as a scroll-driven radial diagram +
  timeline**, replacing the static 12-box `.grid.cols-4`. Matches a reference design the user
  provided (left vertical timeline with one expanded active item, right circular layout of all 12
  categories around a center icon with a tooltip bubble pointing at the active one), but recolored
  to the site's own palette instead of the reference's colors — green (`var(--safe)`/
  `var(--safe-bg)`) for the active state, `var(--text)`/`var(--text-muted)`/`var(--border)`/
  `var(--bg)` for everything inactive, no new hex values introduced.
  - Reused the exact sticky-pin scrollytelling mechanism from the homepage's "how it works"
    walking character (`#walk-wrapper`/`#walk-sticky`, tall spacer + `position:sticky` docked at a
    JS-measured `--header-h`, progress computed from `rect.top` only within the pinned range) —
    new analogous `#flag-wrapper`/`#flag-sticky` IIFE in `detector.html`, independent of the
    homepage's script (each page sets its own `--header-h`).
  - 12 radial nodes are positioned with trig (`angle = i/12 * 2π - π/2`, `x/y = 50 + 42*cos/sin`,
    as percentages) rather than hardcoded coordinates, so re-ordering or adding categories doesn't
    require re-positioning anything by hand.
  - Added a short bilingual one-line description (`dhi`/`den`) per category to `catMeta` — these
    only show for the active timeline item (CSS hides `.flag-item:not(.active) p`), matching the
    reference's "collapsed list, one item expands" look.
  - Mobile (`max-width:760px`): sticky/pin is dropped (`position:static`, `height:auto`, same
    fallback pattern as `.walk-wrapper`) and the radial diagram is hidden outright
    (`max-width:900px`); every timeline item's description stays visible instead of only the
    active one, since there's no pinned viewport to reveal it progressively. The scroll-reactive
    highlighting still runs in this layout (verified: the active item does progress as the page
    scrolls, it just isn't visually pinned).
  - Verified end-to-end in the browser: tooltip tracks the correct node, left/right stay in sync
    across the full scroll range, last category (`Aadhaar/PAN harvesting`) ends up active at the
    bottom of the pinned range, and mobile fallback degrades sensibly.

- **1930 bug fix + 4 cheap wins from a teammate's gap-analysis doc.** All verified against the
  actual codebase (via a fork audit) before touching anything, to separate "already exists" from
  genuine gaps.
  1. **Real bug fixed**: the topbar's "1930" was plain `<strong>1930</strong>` text — not a link —
     on all 7 pages (including index.html, despite index.html having tappable 1930 links
     elsewhere on the page). Wrapped in `<a href="tel:1930">` on every page; added
     `.topbar-left a{color:inherit;text-decoration:none;}` + hover underline to
     `css/style.css` so it blends into the existing topbar palette instead of turning blue/purple
     (link-default) against the blue bar.
  2. **Scam "type" headline** (`css/style.css`/`js/nb-model.js`): `classify()` now picks the
     most-frequent lexicon category among the hits (skipping `urgency`, which is a tactic not a
     type) and returns it as `result.scamType`; `renderVerdict()` shows it as a red badge next to
     the score, e.g. "Fake KYC" / "Lottery/prize scam" — via a new `CATEGORY_LABELS` map, bilingual.
  3. **Structured "What to do now" card** in `renderVerdict()`: replaced the single "Report this"
     button with 4 bulleted steps (don't click/share OTP, don't call back, block sender, then
     either "call 1930 now" for a `scam` verdict or "report on Chakshu within 30 days" for
     `suspicious`) — uses the existing `i-x-circle`/`i-shield-check`/`i-phone` icons and
     `var(--danger)`/`var(--safe)` colors already in the palette, no new colors introduced.
  4. **Chakshu 30-day reminder** added in two places on `pages/report.html`: a line under the
     Chakshu card's description, and appended to the Chakshu row in the official-channels table.
  5. **Golden Hour banner** on `pages/report.html`: pulled the "banks can freeze within the first
     hour" messaging out of a buried sentence inside the "lost money" card and into its own
     `.alert.alert-danger` banner above both cards (reusing the alert component's existing danger
     palette) — shortened the card's own paragraph afterward so the point isn't said twice.

- **Added photo/video upload to the Message Detector, with on-device OCR.** New "Upload photo /
  video" button next to Check/Clear on `pages/detector.html`, backed by a new
  `site/js/ocr-upload.js`. Images are OCR'd directly with Tesseract.js
  (`eng+hin`); videos have up to 3 frames grabbed from a playing (muted, autoplaying) `<video>`
  via `<canvas>` roughly 600ms apart, each OCR'd, then the recognized lines are deduped and
  merged. Extracted text is dropped into `#msg-text` and `runCheck()` is called automatically, so
  it flows straight into the same NB+lexicon classifier used for pasted text. Tesseract.js is
  loaded from `cdn.jsdelivr.net` (pinned `tesseract.js@5.1.1`) — this is the one piece of the site
  that needs an internet connection (one-time, to fetch the OCR engine + `eng`/`hin` language
  data; the actual recognition and classification both still run on-device, nothing is uploaded
  anywhere). Said so explicitly in the UI copy under the upload button.
  **Bug found and fixed during testing**: the video path originally sought to fixed fractions of
  `video.duration` (15%/50%/85%), but some recordings (confirmed with a `MediaRecorder`-captured
  webm, which is a very plausible real "screen recording of a scam text" input) report an
  unreliable/`Infinity` duration, so every seek silently failed and no frames were ever grabbed.
  Rewrote it to capture frames by playing the video and grabbing whatever's on-screen every
  ~600ms instead of seeking to duration-relative timestamps — verified end-to-end (image path and
  video path both) by synthesizing a canvas-drawn test image/video with known scam text in the
  browser and confirming the extracted text and the resulting verdict.

- **Cycling stat tiles (the yellow/red/green boxes right under the hero) lightened.** `.cycle-tile
  .c1/.c2/.c3` backgrounds in `css/style.css` went from dark, muddy tones (`#9c8426` ochre,
  `#9c3240` brick red, `#43815a` forest green) to brighter, lighter ones (`#d9a62b` gold,
  `#d8465a` crimson, `#2fa066` green) — user felt the originals were too dark. Checked the white
  tile text still reads clearly against the lightest one (gold) before calling it done.

- **Repo pushed to GitHub for the first time.** `git init`'d at the repo root, added a
  `.gitignore` excluding `data/raw/` (large third-party dataset dumps, not needed at runtime) plus
  the usual `__pycache__`/OS-junk/`.claude/settings.local.json`, committed all 40 remaining
  tracked files, and pushed to `https://github.com/notsodiv1918/Cyber-Suraksha` on `main`. Per the
  user's request, commit + push after every meaningful change from now on (see section 3b).

- **`.page-head` banner (the colored strip under the nav tabs on every inner page) swapped from
  blue to a pale mint green, topbar left alone.** User supplied an exact swatch (`#eaf7e8`, which
  happens to already equal the existing `--safe-bg` token) and asked specifically for the box
  "under that tab" to change, not the blue topbar strip at the very top. Scope was deliberately
  narrow — only `.page-head{background:...}` (now `var(--safe-bg)`, `color:var(--text)` instead of
  white) plus its dependent child styles that assumed white-on-dark and would've been illegible
  on a light bg: `.breadcrumb`/`.breadcrumb a` (were `#aec2e6`/`#dce6f5` → now
  `var(--text-muted)`/`var(--safe)`), the per-page inline `style="color:#dce6f5"` paragraphs in
  `call-checker.html`/`dashboard.html`/`detector.html`/`learn.html`/`report.html` (→
  `var(--text-muted)`), and dashboard's "POLICY VIEW" badge pill
  (`background:rgba(255,255,255,.15)` on white text → `rgba(12,107,5,.12)` dark-green tint, text
  now dark already via the inherited page-head color change). `.topbar` (background:var(--navy),
  the very top "Prototype — Hackathon build..." strip) and the homepage (which never uses
  `.page-head`) were intentionally left untouched. Buttons/footer/table-headers/tooltips that
  still use `var(--navy)` as a background were NOT touched by this change — only `.page-head`
  itself and its dependents, since that's the one component the user pointed at.

- **Headings/text reverted to black; `--navy` is backgrounds-only now.** The earlier navy→blue
  recolor had changed `var(--navy)` everywhere it appeared, but that variable was doing double
  duty: blue BACKGROUNDS (topbar, buttons, footer, page-head banners, table `th`, tooltip, badge —
  meant to stay blue, white text on top) and blue TEXT (brand title, every `.section-title h2`,
  `.service-card h3`, `.stat-tile .num`, `.cta-panel h3`, labels, active tab text, outline-button
  borders/text, big stat numbers on about/dashboard/learn pages — meant to be plain black per this
  feedback, since it looked unintentionally AI-generated to have every heading/number in blue).
  Fixed by changing every TEXT-color usage of `var(--navy)` to `var(--text)` (`#1b2430`, the
  existing near-black body-text token) in `css/style.css`, plus the equivalent inline
  `style="color:var(--navy)"` occurrences in `pages/about.html`, `pages/detector.html`,
  `pages/learn.html` (including the JS-driven dialect-toggle button and quiz score bits), and
  `pages/report.html`, and the one JS-templated stat number in `pages/dashboard.html`. Left every
  BACKGROUND usage of `var(--navy)` untouched — those are supposed to stay blue with white text on
  top, and the hero section's own white text is separately hardcoded and was never affected by
  any of this. **Lesson for next time a "replace this color" request comes in: check whether the
  token is used as a background (keep) vs. a foreground/text color (usually should stay black)
  before doing a blanket variable swap.**

- **Lighter, single-tone sky blue everywhere except the homepage.** Per feedback that the first
  navy→blue pass (`#2f72bb`) still wasn't light enough and risked looking like inconsistent
  "AI-generated" shade variation, `--navy`/`--navy-light` in `css/style.css` are now both the
  *same* flat `#4a9bdb` (previously `--navy-light` was a different, slightly lighter stop than
  `--navy` — now identical, so there is exactly one blue, no gradient/shade drift between
  elements). The homepage is explicitly exempted: `index.html` now has its own `<style>` block in
  `<head>` right after the stylesheet link that re-pins `:root{--navy:#2f72bb;--navy-light:#4f96dd;}`
  back to the previous (hero-matching) shade, so only the other 6 pages pick up the new lighter
  tone. Also updated `pages/dashboard.html`'s inline chart `PALETTE` first entry to `#4a9bdb` to
  match. If a future ask changes this again, remember: **homepage look lives in that inline
  `<style>` override in `index.html`'s `<head>`, not in `style.css`'s `:root`.**

- **Site-wide dark navy → hero's light blue.** The `--navy` design token (`#0b2447`, a near-black
  navy) changed to `#2f72bb` — the middle stop of the hero's sky-blue gradient — and
  `--navy-light` changed to `#4f96dd` (the hero's lightest stop) to match. Since nearly every
  "dark blue" element in the site (header brand title, section headings, `.btn-navy`, footer
  background, page-head banners, table headers, tooltips, badges, coverflow icon boxes, tab
  labels) already referenced `var(--navy)` rather than a hardcoded hex, changing the one token
  recoloured the whole site automatically — checked visually afterward (index.html header/
  footer/coverflow + dashboard.html page-head/table/charts) to confirm white-text-on-navy-bg
  spots (footer, page-head, table `th`) still read clearly against the lighter blue. Also updated
  the one place that had its own hardcoded copy of the old hex instead of the variable: the chart
  `PALETTE` array in `pages/dashboard.html`'s inline script (`#0b2447` → `#2f72bb`). Left
  `--shadow`/`--shadow-lg`'s `rgba(11,36,71,...)` box-shadow tints alone — those are shadow
  darkening, not a visible "navy vs blue" color choice.

- **CTA phone art enlarged to fill its box.** `.cta-art-phone` grew from `width:220px` to `340px`
  and switched from a bottom-anchored position to vertically centered (`top:50%;
  transform:translateY(-50%)`, `right:-14px`), with the mask-image vignette widened to match
  (`ellipse 62% 62%`, fade starting at `25%`) so the bigger image still blends into the panel
  with no visible edge. Bounding boxes now technically overlap the heading/button (the mask's
  fully-transparent regions cover those corners) — checked visually, no visible collision at
  default text size.

- **Follow-up fixes: masked fade on the CTA phone art, dev server no longer serves stale
  cached assets, FAB hover now shows a "Quick Links" label.**
  1. `.cta-art-phone img` now gets a `mask-image`/`-webkit-mask-image` radial-gradient
     vignette (`ellipse 72% 75% at 50% 50%, #000 40%, transparent 100%`) so the illustration's
     own rectangular canvas/light background fades into the green panel instead of showing a
     visible box edge — the same "fade into the background" idea as the hero photo blend, just
     via a mask instead of a matching-color gradient overlay (masking was necessary here since
     the panel's flat `--safe-bg` colour can't be baked into a gradient the way the hero's photo
     gradient was).
  2. **Root-caused why the new `i-plus` icon rendered as a blank circle**: it was never a CSS/SVG
     bug — Chrome was serving a stale cached copy of `assets/icons.svg` (fetched before the symbol
     was added) for the `<use>` reference, and because Python's bare `http.server` sends no
     `Cache-Control`/`ETag` headers, the browser kept reusing that cached copy even across hard
     reloads and brand-new tabs. Fixed two ways: (a) added `site/devserver.py`, a tiny
     `http.server.SimpleHTTPRequestHandler` subclass that sends
     `Cache-Control: no-store, no-cache, must-revalidate` on every response, and pointed
     `.claude/launch.json`'s `cg-cyber-site` config at it instead of plain `-m http.server`
     (chdir's to its own directory so it always serves `site/` regardless of invocation cwd); (b)
     added a `?v=2` cache-busting query param to every `icons.svg#i-...` reference across all 7
     HTML files, to invalidate the already-poisoned browser cache entry from earlier in this
     session. Going forward, bump that version query string again if an icon ever appears to not
     update after an edit.
  3. The FAB now shows a text label on hover, not just the pill-morph: added
     `<span class="emergency-fab-label" data-en="Quick Links">त्वरित लिंक</span>` inside the
     button (all 7 pages) and `.emergency-fab-label` CSS (collapsed to `max-width:0;opacity:0`
     by default, expanding on `.emergency-fab:hover`). `.emergency-fab` itself switched from a
     fixed circle width that jumped on hover to `min-width:58px` + symmetric `padding:0 17px`, so
     it's a true circle at rest and grows into a pill as the label's `max-width` transitions open.

- **CTA art swapped to a phone illustration, Quick Links section removed, emergency FAB
  repurposed into a quick-links launcher.** Three changes from one user message, all on
  `index.html` + (for the FAB) all 7 HTML pages:
  1. The green `.cta-panel`'s `.cta-art` now shows `assets/cta-phone.webp` (cropped/resized from
     a reference "Suspicious Message" SMS illustration, trimmed of surrounding whitespace via a
     PIL bounding-box scan) instead of `walk-body-2.png`; the old `.cta-bubble` ("Scam or
     Genuine?") overlay was removed since the new image already shows its own warning banner.
     New `.cta-art-phone` CSS rule (`width:200px;right:14px;bottom:86px;`) keeps the wider/shorter
     image clear of the panel's CTA button — verified no overlap via `getBoundingClientRect` on
     both elements.
  2. The "QUICK LINKS" `.svc-tiles` section (9 tiles: 1930/cybercrime.gov.in/Chakshu/Message
     Detector/Call Detector/Lessons & Quiz/Dashboard/Data Sources/Report Fraud) was deleted
     outright from `index.html`. `.svc-tiles`/`.svc-tile` CSS left in place (unused, harmless).
  3. The floating `.emergency-widget` FAB (previously a red circle with a life-buoy icon,
     opening a panel of emergency contacts) is now a **blue** circle with a new `i-plus` icon
     (added to `assets/icons.svg`), which on `:hover` widens from 58px to 76px with the icon
     rotating 90° (circle → pill morph, `transition:width .22s ease`), and on click opens a panel
     now titled "Quick Links" listing only Message Detector / Call Detector / Lessons & Quiz /
     Dashboard / Data Sources — deliberately excluding 1930 Helpline, cybercrime.gov.in, Chakshu,
     and Report Fraud since those are already surfaced elsewhere on the homepage (the blue CTA
     panel, the Emergency Help Strip, topbar, footer). Applied identically to all 7 duplicated
     chrome blocks (`index.html` + 6 `pages/*.html`, with relative hrefs/icon paths adjusted per
     page). Also fixed a latent bug in `initEmergencyWidget()` in `js/app.js`: the outside-click
     handler compared `e.target !== fab`, but clicks on the icon `<svg>`/`<use>` inside the button
     made `e.target` the icon, not the button, so the panel opened and immediately re-closed on
     the same click. Changed to `!fab.contains(e.target)`.

- **"Try it" section replaced with a two-panel CTA** (was an inline quick-check textarea+button
  that classified text right on the homepage — removed that, along with the now-dead
  `quick-check-btn`/`js/nb-model.js` wiring on `index.html`, which only the detector/call-checker
  pages still need). New layout: a green `.cta-panel` ("Have a suspicious message...") linking
  straight to `pages/detector.html`, reusing `walk-body-2.png` (the same real character art from
  the walking animation) plus a small "Scam or Genuine?" bubble; and a blue `.cta-panel` ("Received
  a scam...") with two `.cta-link-row`s to `cybercrime.gov.in` and `tel:1930`. Built from a
  reference image but restyled to the site's own palette/sharp corners rather than copying its
  pastel colours and heavy rounding.
- **Walking character swapped for real cropped artwork**: replaced the hand-drawn flat-SVG figure
  with actual PNG crops taken from a user-provided reference sprite sheet (`site/assets/walk-
  body-1..4.png` for the per-step body pose, `walk-face-1..4.png` for the per-step face close-up
  shown in the speech bubble). Cropped with Pillow (`scripts/` has no permanent script for this —
  it was a one-off crop) **preserving the source's alpha channel** — the first attempt flattened
  to RGB and lost transparency, showing an ugly opaque white box behind the character; re-cropped
  keeping RGBA mode. The character element is now bottom-anchored (`transform:translate(-50%,
  -100%)` instead of a fixed top-left offset), so it always stands *on* the path markers and can
  never visually dip down into the step cards below, however the waypoint Y values are tuned —
  this was the fix for "character getting below the tab boxes". Face/body/bubble now swap via
  plain `<img src>` changes per step instead of SVG path/attribute swapping.
- **Walking-character animation made sticky-pinned (real scrollytelling)**: the first version
  just read scroll progress passively, so the whole section (and the character reaching step 4)
  scrolled past and out of view before the animation finished, and progress started ticking
  before the section was actually in view. Restructured to the standard pattern: `#walk-wrapper`
  is a tall spacer (`100vh + 700px`) around a `#walk-sticky` inner (`position:sticky`) that docks
  just below the site's own sticky header (header height measured via JS into a `--header-h` CSS
  var, recomputed on resize) and stays pinned on screen for the whole 700px of extra scroll
  distance — only during that pinned window does progress move 0→1; before and after it's
  clamped to 0/1. Collapses back to a plain static (non-sticky) layout under 760px, matching the
  existing mobile fallback that hides the walk-stage entirely. Verified by scrolling through: the
  heading/stage/cards hold still on screen while the character walks and the bubble/expression/
  active-card change through all 4 steps, then release naturally into the next section once step
  4 is reached.
- **"How it works" walking-character scroll animation**: added a hand-built flat-SVG character
  (backpack, phone in hand, swappable eyebrows/mouth) that walks along a dotted wavy path with 4
  markers above the existing 4 step cards. Position, facial expression, a themed speech bubble
  ("Suspicious message?" → "Analysing on your device..." → "Scam detected" → "Reported ✓"), and
  which step-card gets the active highlight are all driven by **one scroll-progress number**
  computed from how far `#how-it-works`'s own bounding box has travelled through the viewport
  (0 when the section's top reaches the viewport bottom, 1 when its bottom leaves the viewport
  top) — no scroll-jacking, no extra tall spacer, just a plain `scroll`/`resize` listener
  throttled with `requestAnimationFrame`. Position interpolates smoothly between the 4 waypoints;
  face/bubble/card-highlight change in discrete quartile steps. Hidden below 760px (no room for
  the stage; the 4 cards alone still explain the flow). Uses the site's existing palette
  (saffron/navy/safe/danger), not the pink/teal reference image's colours, per instruction.
- **Stat tiles got a `desc` field** (one extra sentence per stat, bilingual) rendered below the
  label — tiles felt too empty with just icon+number+label. **Coverflow cards**: removed the
  small coloured badge pill from all 5 cards (`ML · OFFLINE`, `BETA`, `OFFICIAL LINKS`, `OFFLINE`,
  `POLICY VIEW`) per feedback that they weren't wanted. **Coverflow nav/dots overlap fixed
  properly**: `.coverflow` no longer has a fixed `height:440px` — the JS `render()` now sets
  `#coverflow-track`'s height in px to match the active card's actual `offsetHeight` on every
  slide change, so the nav row below it can never overlap a taller-than-expected card again
  (this was the root cause, not a spacing/margin tweak).
- **Root-caused the hero seam line (for real this time)**: the visible vertical line at the hero
  photo's left edge wasn't a gradient-width problem — `.hero`'s background was a **radial**
  gradient (colour varies by both X and Y), while the photo's blend overlay (`.hero-slide::before`)
  was a flat left-to-right linear gradient starting from a single hardcoded colour. Those two
  only matched at one specific Y, so everywhere else on the vertical edge showed a visible seam.
  Fixed by changing `.hero`'s background to a pure horizontal `linear-gradient` (colour now
  depends only on X), so the overlay's starting colour matches the hero background at every row.
  Also recoloured `.hero-ticker` to the same pastel-yellow gradient as the nav bar (was navy),
  with ticker text switched to dark brown for contrast.
- **Nav bar recoloured + hero blend smoothed**: `.site-nav` changed from the green gradient to a
  pastel light-yellow gradient (`#fdf0c8 → #fae3a0`), with link text switched from white to a dark
  warm-brown (`#5a4612`) for contrast. The hero photo's left-edge blend (`.hero-slide::before`)
  was replaced with a much longer multi-stop gradient (6 stops spread over ~52% of the image
  width instead of a hard 2-stop fade to 18%) — the old version left a visible seam/line where
  opacity hit zero abruptly; the new one fades gradually with no hard edge.
- **Hero recolour + ticker/divider rework**: hero background changed from the tricolor
  saffron/navy/green diagonal gradient to a solid sky-blue radial gradient (`#4f96dd → #2f72bb →
  #1b4e86`), per a reference screenshot — only the colour changed, same hero photos/layout. The
  hero photo's left-edge blend gradient (`.hero-slide::before`) was updated to match (was hard-
  coded to navy, would have shown a seam against the new blue). The trend ticker (`.hero-ticker`)
  was moved out of `<section class="hero">` to sit as its own bar directly below it, with its own
  solid navy background (previously relied on the hero's dark backdrop via `rgba(0,0,0,.28)`).
  The `.toran-strip` divider pattern was redrawn from a triangular-bunting look to a thin chain-
  of-petals motif (small orange bud shapes linked by a scalloped line, on white), matching a
  cgstate.gov.in-style reference image.
- **Project brain file created** (`PROJECT_BRAIN.md`), and cleaned up 4 unused leftover
  `site/assets/hero-slide-*.png` files (superseded by `hero-photo-*`, no longer referenced
  anywhere).
- **Hero full-bleed fix**: moved `.hero-slideshow` out of the padded `.container` flex row to be
  an absolutely-positioned direct child of `.hero` (`top:0;right:0;bottom:0;width:54%`) — this
  was the actual fix for persistent top/right gaps that survived two earlier attempted fixes.
  Confirmed via `getBoundingClientRect` that image edges now exactly match hero section edges.
- **Hero image fit tuning**: switched `object-fit` from `contain` back to `cover` (user wanted the
  box fully filled, not letterboxed) and narrowed the left-edge gradient blend from ~38% of the
  image width down to ~18%.
- **Hero images replaced (again) with 4 user-specified exact files**: yellow phone-warning
  illustration, blue/white shield illustration (with tricolor wave variant), village-woman+agent
  UPI photo, family+shopkeeper UPI photo. Fixed served file extensions to match true format
  (`.webp`/`.png`) since Python's `http.server` infers `Content-Type` from extension.
- **Services section → coverflow carousel**: replaced the plain 5-card grid with a center-focused
  sliding carousel (prev/next arrows, dot indicators, "Tool X of 5: <subtitle>" caption), each
  card showing icon/badge/title/description + two deep-link action buttons (e.g. "Instant Call
  1930" + "Open Evidence Checklist"). Iterated twice more on user feedback: removed the chip-tag
  row entirely, centered the icon-box, and changed `.coverflow-card` from a fixed height (460px,
  with chips/buttons toggling visibility on the active card) to `height:auto` — the fixed-height
  + content-toggle combination was causing a visible resize glitch mid-slide.
- **Cycling stat tiles**: replaced the original static 8-tile "at a glance" grid with 3 dark
  tiles that auto-rotate their content (icon/number/label) every 2 seconds with a fade and dot
  indicators; tile colours iterated from "dark navy/plum/moss" → "yellow/crimson/green" (dark) →
  lightened per feedback.
- **Real government emblem + map swap-in** (with confirmed hackathon permission): replaced the
  earlier stylized pillar+seal SVG icon with the real Ashoka emblem + Chhattisgarh seal images in
  the header (all 7 pages), and replaced the hand-drawn SVG Chhattisgarh map with the user's real
  map photo, overlaying interactive region markers at **pixel-measured** coordinates (sampled the
  red dot positions in the source image programmatically) rather than guessed ones.
- **District map v2**: redrew the hand-drawn SVG map with a more detailed silhouette, division
  boundary lines, a capital star for Raipur, compass rose, India-location inset box, and caption
  — then reduced the UI from 5 division tabs to **3 regional tabs** (North/Central/South) with a
  genuine sliding-carousel transition instead of an abrupt tab swap. Recoloured the map fill to a
  dark pastel orange on request (with matching dark-navy markers/labels for contrast).
- **Layout/accessibility pass**: widened the shared container (1180px → 1480px) to remove large
  side-gutters on wide screens; set global corner radius to sharp (2px); replaced every emoji
  site-wide with a hand-authored inline SVG icon sprite (`site/assets/icons.svg`, ~35 icons) —
  caught and fixed a real bug along the way where 3 emoji→icon swaps had landed inside
  `data-en="..."` attributes and corrupted the HTML (fixed by using single quotes for the nested
  SVG's attributes).
- **Hero v1**: added an auto-rotating image slideshow + a scrolling ticker marquee inside the
  hero section, plus a stat-tile strip and a toran/bunting decorative divider (iterated a few
  times on placement — stats moved in and back out of the hero section per feedback).
- **Initial site build**: researched and downloaded real datasets (see §4), trained the first
  Naive Bayes model, and built the full static site (homepage + 6 inner pages) styled after
  `cgstate.gov.in`, Hindi-default with an English toggle, clearly labelled as a hackathon
  prototype throughout.
