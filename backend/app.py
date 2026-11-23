"""
Scam-pattern alert backend — a small, self-contained prototype for "New-scam alerts:
group similar reported messages and alert officials when a new scam pattern starts
spreading in a district."

This is deliberately the ONE piece of this project that is not offline/on-device —
it needs a live server because detecting a pattern spreading *across different
users* fundamentally requires seeing reports from more than one device. Nothing is
collected automatically: the website and app only send a report here when the user
explicitly opts in and picks a district (see site/pages/report.html's share
checkbox and the app's "report this" button on scam/suspicious verdicts).

Clustering is intentionally simple and auditable rather than a black-box ML
pipeline, matching this project's "explain what the model actually does" ethic
elsewhere (see nb-model.js / Classifier.kt): normalise each report's text, compare
pairs within the same district using Jaccard similarity over the token set, union
reports that are similar enough into clusters, and raise/refresh an alert for any
cluster that reaches ALERT_MIN_COUNT within WINDOW_HOURS. Recomputed fresh on every
new report — fine at hackathon-demo scale (hundreds of reports), not meant to be a
production-scale pipeline.

Run: python backend/app.py   (serves on http://0.0.0.0:5000)
"""
import os
import re
import sqlite3
from datetime import datetime, timedelta

from flask import Flask, jsonify, request

DB_PATH = os.path.join(os.path.dirname(__file__), "reports.db")

SIM_THRESHOLD = 0.45   # Jaccard token-set similarity to count two reports as "the same pattern"
ALERT_MIN_COUNT = 3    # reports needed in a cluster before it's flagged as an emerging pattern
WINDOW_HOURS = 72      # only reports within this window are considered "currently spreading"
MAX_TEXT_LEN = 2000

app = Flask(__name__)


def normalise(text: str) -> str:
    t = text.lower()
    t = re.sub(r"https?://\S+", " http ", t)
    t = re.sub(r"\d+", "#", t)  # digits normalised the same way train_model.py dedups templates
    t = re.sub(r"[^\w\s#]", " ", t, flags=re.UNICODE)
    return re.sub(r"\s+", " ", t).strip()


def token_set(text: str):
    return {w for w in normalise(text).split(" ") if len(w) > 2}


def jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    union = len(a | b)
    return len(a & b) / union if union else 0.0


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_db()
    conn.execute(
        """CREATE TABLE IF NOT EXISTS reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            text TEXT NOT NULL,
            district TEXT NOT NULL,
            lang TEXT,
            verdict TEXT,
            source TEXT,
            created_at TEXT NOT NULL
        )"""
    )
    conn.execute(
        """CREATE TABLE IF NOT EXISTS alerts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            district TEXT NOT NULL,
            sample_text TEXT NOT NULL,
            report_count INTEGER NOT NULL,
            first_seen TEXT NOT NULL,
            last_seen TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'active'
        )"""
    )
    conn.commit()
    conn.close()


@app.after_request
def add_cors(resp):
    # Open CORS: this is a local hackathon-demo server, not a public deployment —
    # tighten this before ever exposing it beyond a local/dev network.
    resp.headers["Access-Control-Allow-Origin"] = "*"
    resp.headers["Access-Control-Allow-Headers"] = "Content-Type"
    resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
    return resp


@app.route("/api/report", methods=["POST", "OPTIONS"])
def submit_report():
    if request.method == "OPTIONS":
        return ("", 204)
    data = request.get_json(force=True, silent=True) or {}
    text = (data.get("text") or "").strip()
    district = (data.get("district") or "").strip()
    lang = (data.get("lang") or "")[:10]
    verdict = (data.get("verdict") or "")[:20]
    source = (data.get("source") or "unknown")[:20]

    if not text or not district:
        return jsonify({"error": "text and district are required"}), 400
    text = text[:MAX_TEXT_LEN]

    conn = get_db()
    now = datetime.utcnow().isoformat()
    conn.execute(
        "INSERT INTO reports (text, district, lang, verdict, source, created_at) VALUES (?,?,?,?,?,?)",
        (text, district, lang, verdict, source, now),
    )
    conn.commit()
    recompute_clusters(conn, district)
    conn.close()
    return jsonify({"ok": True})


def recompute_clusters(conn, district: str):
    cutoff = (datetime.utcnow() - timedelta(hours=WINDOW_HOURS)).isoformat()
    rows = conn.execute(
        "SELECT id, text, created_at FROM reports WHERE district=? AND created_at >= ? ORDER BY id",
        (district, cutoff),
    ).fetchall()
    items = [(r["id"], r["text"], token_set(r["text"]), r["created_at"]) for r in rows]
    n = len(items)

    parent = list(range(n))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    for i in range(n):
        for j in range(i + 1, n):
            if jaccard(items[i][2], items[j][2]) >= SIM_THRESHOLD:
                union(i, j)

    clusters = {}
    for i in range(n):
        clusters.setdefault(find(i), []).append(items[i])

    # Recompute this district's alerts fresh each time — simple and correct at this scale.
    conn.execute("DELETE FROM alerts WHERE district=?", (district,))
    for members in clusters.values():
        if len(members) >= ALERT_MIN_COUNT:
            sample = max(members, key=lambda m: len(m[1]))[1]
            times = [m[3] for m in members]
            conn.execute(
                "INSERT INTO alerts (district, sample_text, report_count, first_seen, last_seen, status) "
                "VALUES (?,?,?,?,?,'active')",
                (district, sample[:300], len(members), min(times), max(times)),
            )
    conn.commit()


@app.route("/api/alerts", methods=["GET"])
def list_alerts():
    district = request.args.get("district")
    conn = get_db()
    if district:
        rows = conn.execute(
            "SELECT * FROM alerts WHERE district=? ORDER BY report_count DESC", (district,)
        ).fetchall()
    else:
        rows = conn.execute("SELECT * FROM alerts ORDER BY last_seen DESC").fetchall()
    out = [dict(r) for r in rows]
    conn.close()
    return jsonify({"alerts": out})


@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({"ok": True})


if __name__ == "__main__":
    init_db()
    app.run(host="0.0.0.0", port=5000, debug=False)
