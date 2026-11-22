"""Fetch public Hugging Face datasets via the datasets-server rows API and
save them as JSONL. Used to build the scam-message training corpus and the
scam-trend taxonomy for the Chhattisgarh cyber-fraud platform prototype.
"""
import json
import os
import time
import requests

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "raw")
os.makedirs(OUT_DIR, exist_ok=True)

BASE = "https://datasets-server.huggingface.co/rows"
PAGE = 100


def fetch(dataset, config, split, out_name, max_rows=None):
    out_path = os.path.join(OUT_DIR, out_name)
    rows = []
    offset = 0
    if os.path.exists(out_path):
        with open(out_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    rows.append(json.loads(line))
        offset = len(rows)
        print(f"Resuming {dataset} from offset {offset}")
    while True:
        if max_rows is not None and offset >= max_rows:
            break
        length = PAGE
        if max_rows is not None:
            length = min(PAGE, max_rows - offset)
        params = dict(dataset=dataset, config=config, split=split, offset=offset, length=length)
        for attempt in range(8):
            r = requests.get(BASE, params=params, timeout=30)
            if r.status_code == 200:
                break
            time.sleep(3 * (attempt + 1))
        else:
            print(f"FAILED at offset {offset} for {dataset}: {r.status_code}")
            break
        time.sleep(0.6)
        data = r.json()
        page_rows = data.get("rows", [])
        if not page_rows:
            break
        for item in page_rows:
            rows.append(item["row"])
        offset += len(page_rows)
        total = data.get("num_rows_total")
        print(f"{dataset}: {offset}/{total}")
        if total is not None and offset >= total:
            break
    with open(out_path, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"Saved {len(rows)} rows -> {out_path}")


if __name__ == "__main__":
    # 1. India-specific SMS, spam/ham labeled (20,010 rows)
    fetch("CloveAI/india-spam-sms", "default", "train", "india_spam_sms.jsonl")

    # 2. ScamShield-IN: scam messages with fraud_type + explanation (8,066 rows)
    fetch("haxrits/ScamShield-IN", "default", "train", "scamshield_in.jsonl")

    # 3. National Cybercrime Reporting Portal complaint narratives + category
    #    taxonomy. Dataset has 93,686 rows; sample first 8000 for category
    #    distribution + phrase mining (representative sample, not full corpus).
    fetch("Dev523/Crime-Reports-Dataset", "default", "train", "crime_reports_sample.jsonl", max_rows=8000)
