"""Build the scam-trend dashboard data file from the National Cybercrime
Reporting Portal complaint-category sample (Dev523/Crime-Reports-Dataset) and
the ScamShield-IN fraud_type tags.

The source data has NO location/district field, so district-wise and
month-wise breakdowns are synthesised (proportionally, with randomised noise)
purely for demo purposes. This is stated explicitly in dashboard.json and on
the dashboard page — the *category totals themselves* are real, derived from
the actual complaint-category counts.
"""
import json
import os
import random
import collections

random.seed(7)

RAW = os.path.join(os.path.dirname(__file__), "..", "data", "raw")
OUT = os.path.join(os.path.dirname(__file__), "..", "site", "data", "dashboard.json")

# Map raw (category, sub_category) -> a clean bilingual scam-type label.
# Only financial-fraud-relevant categories are kept in scope for this platform.
TYPE_MAP = {
    ("Online Financial Fraud", "UPI Related Frauds"): ("upi", "फर्जी UPI भुगतान", "Fake UPI payment"),
    ("Online Financial Fraud", "DebitCredit Card FraudSim Swap Fraud"): ("card_sim", "कार्ड / सिम-स्वैप धोखाधड़ी", "Card / SIM-swap fraud"),
    ("Online Financial Fraud", "Internet Banking Related Fraud"): ("netbanking", "इंटरनेट बैंकिंग धोखाधड़ी", "Internet banking fraud"),
    ("Online Financial Fraud", "Fraud CallVishing"): ("vishing", "फर्जी कॉल (विशिंग)", "Fraud call (vishing)"),
    ("Online Financial Fraud", "EWallet Related Fraud"): ("ewallet", "ई-वॉलेट धोखाधड़ी", "E-wallet fraud"),
    ("Online Financial Fraud", "Demat  Depository Fraud"): ("investment", "डीमैट / निवेश धोखाधड़ी", "Demat / investment fraud"),
    ("Online Financial Fraud", "Business Email CompromiseEmail Takeover"): ("email", "ईमेल अधिग्रहण धोखाधड़ी", "Email takeover fraud"),
    ("Online and Social Media Related Crime", "Online Job Fraud"): ("job", "फर्जी नौकरी / टास्क धोखाधड़ी", "Fake job / task fraud"),
    ("Online and Social Media Related Crime", "FakeImpersonating Profile"): ("impersonation", "फर्जी प्रोफ़ाइल / प्रतिरूपण", "Fake profile / impersonation"),
    ("Online and Social Media Related Crime", "Cheating by Impersonation"): ("impersonation", "फर्जी प्रोफ़ाइल / प्रतिरूपण", "Fake profile / impersonation"),
    ("Online Gambling  Betting", None): ("gambling", "ऑनलाइन सट्टा / जुआ", "Online gambling / betting"),
    ("Cryptocurrency Crime", None): ("crypto", "क्रिप्टोकरेंसी धोखाधड़ी", "Cryptocurrency fraud"),
}

DISTRICTS = [
    ("रायपुर", "Raipur", "रायपुर संभाग", "Raipur Division", 1.6),
    ("दुर्ग", "Durg", "दुर्ग संभाग", "Durg Division", 1.3),
    ("बिलासपुर", "Bilaspur", "बिलासपुर संभाग", "Bilaspur Division", 1.2),
    ("कोरबा", "Korba", "बिलासपुर संभाग", "Bilaspur Division", 0.8),
    ("रायगढ़", "Raigarh", "बिलासपुर संभाग", "Bilaspur Division", 0.7),
    ("बस्तर (जगदलपुर)", "Bastar (Jagdalpur)", "बस्तर संभाग", "Bastar Division", 0.5),
    ("सरगुजा (अंबिकापुर)", "Surguja (Ambikapur)", "सरगुजा संभाग", "Surguja Division", 0.6),
    ("राजनांदगांव", "Rajnandgaon", "दुर्ग संभाग", "Durg Division", 0.7),
    ("महासमुंद", "Mahasamund", "रायपुर संभाग", "Raipur Division", 0.5),
    ("धमतरी", "Dhamtari", "रायपुर संभाग", "Raipur Division", 0.4),
]

MONTHS_HI = ["जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर"]
MONTHS_EN = ["Jun", "Jul", "Aug", "Sep", "Oct", "Nov"]


def load_categories():
    counts = collections.Counter()
    with open(os.path.join(RAW, "crime_reports_sample.jsonl"), encoding="utf-8") as f:
        for line in f:
            r = json.loads(line)
            key = (r.get("category"), r.get("sub_category"))
            if key in TYPE_MAP:
                counts[TYPE_MAP[key]] += 1
            elif (r.get("category"), None) in TYPE_MAP:
                counts[TYPE_MAP[(r.get("category"), None)]] += 1
    return counts


def load_fraud_types():
    counts = collections.Counter()
    path = os.path.join(RAW, "scamshield_in.jsonl")
    if not os.path.exists(path):
        return counts
    with open(path, encoding="utf-8") as f:
        for line in f:
            r = json.loads(line)
            ft = (r.get("fraud_type") or "").strip()
            if ft:
                counts[ft] += 1
    return counts


def main():
    cat_counts = load_categories()
    scam_types = []
    total = sum(cat_counts.values())
    for (code, hi, en), n in sorted(cat_counts.items(), key=lambda kv: -kv[1]):
        scam_types.append({
            "code": code, "hi": hi, "en": en, "count": n,
            "pct": round(100 * n / total, 1) if total else 0,
        })

    # Synthesise a district x scam-type matrix proportionally from the real
    # national totals, weighted by an illustrative district "activity" factor.
    random.shuffle(DISTRICTS)
    districts_out = []
    weight_sum = sum(d[4] for d in DISTRICTS)
    for hi, en, div_hi, div_en, weight in DISTRICTS:
        district_total = 0
        breakdown = {}
        for t in scam_types:
            base = t["count"] / total * weight / weight_sum * total * 0.11
            noisy = max(1, int(base * random.uniform(0.75, 1.3)))
            breakdown[t["code"]] = noisy
            district_total += noisy
        districts_out.append({
            "hi": hi, "en": en, "division_hi": div_hi, "division_en": div_en,
            "total": district_total, "breakdown": breakdown,
        })
    districts_out.sort(key=lambda d: -d["total"])

    # Monthly trend (illustrative growth curve) per top 4 scam types
    top4 = [t["code"] for t in scam_types[:4]]
    monthly = []
    for i, (m_hi, m_en) in enumerate(zip(MONTHS_HI, MONTHS_EN)):
        row = {"month_hi": m_hi, "month_en": m_en}
        for code in top4:
            base_t = next(t for t in scam_types if t["code"] == code)
            trend_factor = 1 + i * random.uniform(0.03, 0.14)
            row[code] = int(base_t["count"] / 40 * trend_factor)
        monthly.append(row)

    out = {
        "source_note_hi": "स्कैम-प्रकार एवं कुल गणनाएँ राष्ट्रीय साइबर अपराध रिपोर्टिंग पोर्टल के 93,686 शिकायत रिकॉर्ड के 8,000 के नमूने पर आधारित वास्तविक आँकड़े हैं। मूल डेटा में जिला/स्थान फ़ील्ड नहीं है, इसलिए जिलेवार एवं माहवार विभाजन केवल प्रदर्शन हेतु आनुपातिक रूप से संश्लेषित किया गया है।",
        "source_note_en": "Scam-type categories and totals are real, derived from an 8,000-row sample of the 93,686-row National Cybercrime Reporting Portal complaint dataset. The source data has no district/location field, so the district-wise and month-wise splits are proportionally synthesised for demonstration only.",
        "total_sample": total,
        "scam_types": scam_types,
        "districts": districts_out,
        "monthly": monthly,
        "monthly_types": top4,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("scam types:", [(t["code"], t["count"]) for t in scam_types])
    print("wrote", OUT)


if __name__ == "__main__":
    main()
