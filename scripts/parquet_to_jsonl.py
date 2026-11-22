import json
import os
import pandas as pd

RAW = os.path.join(os.path.dirname(__file__), "..", "data", "raw")

FILES = {
    "india_spam_sms.parquet": "india_spam_sms.jsonl",
    "scamshield_in.parquet": "scamshield_in.jsonl",
    "crime_reports.parquet": "crime_reports_sample.jsonl",
}

for src, dst in FILES.items():
    src_path = os.path.join(RAW, src)
    dst_path = os.path.join(RAW, dst)
    df = pd.read_parquet(src_path)
    print(src, "->", df.shape, list(df.columns))
    if "crime_reports" in dst and len(df) > 12000:
        df = df.sample(n=12000, random_state=42)
    with open(dst_path, "w", encoding="utf-8") as f:
        for _, row in df.iterrows():
            f.write(json.dumps(row.to_dict(), ensure_ascii=False, default=str) + "\n")
    print("wrote", dst_path)
