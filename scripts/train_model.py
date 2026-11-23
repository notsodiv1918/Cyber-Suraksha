"""Train a bag-of-words / char-4-gram Multinomial Naive Bayes classifier on the
combined India-focused SMS/scam corpus, evaluate on a held-out split, and export
a compact JSON model consumed by site/js/nb-model.js for fully offline,
on-device inference (matches the tokenizer in nb-model.js exactly).

No sklearn dependency (not installed in this environment) — Naive Bayes is
simple enough to implement directly with numpy/pandas, and doing so keeps full
control over the exported model format.
"""
import json
import math
import os
import random
import re
import sys

RAW_DIR = os.path.join(os.path.dirname(__file__), "..", "data", "raw")
OUT_MODEL = os.path.join(os.path.dirname(__file__), "..", "site", "data", "model.json")
OUT_REPORT = os.path.join(os.path.dirname(__file__), "..", "data", "model_report.json")

random.seed(42)

WORD_RE = re.compile(r"[ऀ-ॿa-z0-9]+")

# High-frequency function words in Hindi/Hinglish/English. Left in, these
# dominate the Naive Bayes log-odds sum for short messages (many weak
# genuine-leaning stopwords outvote a handful of strong scam keywords like
# "kyc"/"otp"/"link"), so they are stripped before counting/scoring. This
# list MUST stay identical to the STOPWORDS set in site/js/nb-model.js.
STOPWORDS = set("""
hai hain tha the thi ho hoga hogi ke ka ki ko me mein par se ye yeh wo voh
vo ye aur ya bhi to hi jo ki is us ek kuch sabhi aap hum main tum apna apne
kar kare karen kiya kisi koi bina liye sath baad pehle abhi ab yahan wahan
kaise kyon kab kahan nahi haan ji gaya gayi gaye raha rahi rahe hoti hote
hota wala wali wale banaa bana rha rhi rhe
है हैं था थे थी हो होगा होगी के का की को में पर से यह वह ये वो और या भी तो
ही जो कि इस उस एक कुछ सभी आप हम मैं तुम अपना अपने कर करें किया किसी कोई
बिना लिए साथ बाद पहले अभी अब यहाँ वहाँ कैसे क्यों कब कहाँ नहीं हां जी गया
गई गए रहा रही रहे होती होते होता वाला वाली वाले बना रहे रहें हुआ हुई हुए
the is are was were a an and or to of in on at for with this that these
those you your i we my me it as be by from has have had will would can
could please dear not so if but just now your u ur
""".split())


def normalise(text):
    text = text.lower()
    text = re.sub(r"https?://\S+", " http ", text)
    text = re.sub(r"[^ऀ-ॿ a-z0-9]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def tokenize(norm):
    # Char-4-gram augmentation (kept until now to catch misspelled/obfuscated scam
    # keywords) turned out to be the main driver of the systemic vocabulary-skew bug:
    # every word longer than 4 chars added several correlated sub-tokens, all pulling
    # toward whichever class has broader raw-corpus coverage — which for us is "scam"
    # (the ham class is narrow transactional boilerplate). For a 10-word casual sentence
    # this compounded into 20-30 extra tokens all voting the same way, overwhelming any
    # amount of added genuine training data (confirmed: log-odds diffs of 12-25 on plain
    # everyday sentences, enough to pin probability to 1.0 regardless of threshold).
    # Plain word tokens only — the lexicon regexes already catch scam-keyword variants.
    if not norm:
        return []
    words = norm.split(" ")
    return [w for w in words if w and w not in STOPWORDS]


def load_jsonl(name):
    path = os.path.join(RAW_DIR, name)
    rows = []
    if not os.path.exists(path):
        print(f"WARNING: missing {path}, skipping")
        return rows
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def build_corpus():
    """Returns list of (text, label) with label 1=scam/spam, 0=genuine/ham."""
    examples = []

    # 1) India-specific SMS, already labeled 0/1
    for row in load_jsonl("india_spam_sms.jsonl"):
        text = row.get("text", "")
        label = int(row.get("label", 0))
        if text:
            examples.append((text, label))

    # 2) ScamShield-IN: every row is a confirmed scam message
    for row in load_jsonl("scamshield_in.jsonl"):
        text = row.get("message", "")
        if text:
            examples.append((text, 1))

    # 3) Hindi (Devanagari) coverage: SMS Spam Collection machine-translated
    #    to Hindi (dbarbedillo/SMS_Spam_Multilingual_Collection_Dataset).
    #    Without this, the corpus above is ~100% English/Latin script and the
    #    model cannot score Hindi text at all (every token falls to the OOV
    #    bucket). Duplicated 2x so Hindi tokens get adequate vocabulary weight
    #    relative to the much larger English-only corpus.
    hindi_examples = []
    for row in load_jsonl("hindi_translated_sms.jsonl"):
        text = row.get("text", "")
        label = int(row.get("label", 0))
        if text:
            hindi_examples.append((text, label))
    examples.extend(hindi_examples)
    examples.extend(hindi_examples)

    # 4) Small genuine Hinglish (code-mixed) subset mined from the
    #    bolewara/hinglish-scam-text-dataset SMS Spam Collection copy.
    for row in load_jsonl("hinglish_subset.jsonl"):
        text = row.get("text", "")
        label = int(row.get("label", 0))
        if text:
            examples.append((text, label))

    # 5) Templated domain-specific synthetic set (see generate_synthetic.py)
    #    correcting two measured biases in the real datasets above: genuine
    #    courier/OTP/UPI confirmations were almost absent (causing false
    #    positives on plain shipping/OTP texts), and authentic Hindi/Hinglish
    #    fraud phrasing (KYC, digital arrest, UPI PIN) was under-represented.
    #    Weighted 3x so these on-domain terms carry real influence against
    #    the much larger generic English corpus.
    synth = []
    for row in load_jsonl("synthetic_domain.jsonl"):
        text = row.get("text", "")
        label = int(row.get("label", 0))
        if text:
            synth.append((text, label))
    for _ in range(8):
        examples.extend(synth)

    return examples


def split_train_test(examples, test_frac=0.15):
    examples = examples[:]
    random.shuffle(examples)
    n_test = int(len(examples) * test_frac)
    return examples[n_test:], examples[:n_test]


def train_naive_bayes(train_examples, alpha=0.5):
    vocab_counts = [{}, {}]  # [ham, scam] -> token -> count
    total_tokens = [0, 0]
    class_counts = [0, 0]

    for text, label in train_examples:
        class_counts[label] += 1
        toks = tokenize(normalise(text))
        bucket = vocab_counts[label]
        for t in toks:
            bucket[t] = bucket.get(t, 0) + 1
            total_tokens[label] += 1

    all_tokens = set(vocab_counts[0]) | set(vocab_counts[1])
    # Prune very rare tokens (seen <=1 time total) to keep the exported model
    # small for an offline/low-bandwidth client, with negligible accuracy loss.
    vocab = {
        t for t in all_tokens
        if vocab_counts[0].get(t, 0) + vocab_counts[1].get(t, 0) >= 3
    }
    V = len(vocab)

    priors = [
        math.log(class_counts[0] / len(train_examples)),
        math.log(class_counts[1] / len(train_examples)),
    ]

    vocab_logprob = {}
    for t in vocab:
        c0 = vocab_counts[0].get(t, 0)
        c1 = vocab_counts[1].get(t, 0)
        lp0 = math.log((c0 + alpha) / (total_tokens[0] + alpha * (V + 1)))
        lp1 = math.log((c1 + alpha) / (total_tokens[1] + alpha * (V + 1)))
        vocab_logprob[t] = [round(lp0, 6), round(lp1, 6)]

    default_lp = [
        round(math.log(alpha / (total_tokens[0] + alpha * (V + 1))), 6),
        round(math.log(alpha / (total_tokens[1] + alpha * (V + 1))), 6),
    ]

    model = {
        "classes": ["genuine", "scam"],
        "priors": [round(priors[0], 6), round(priors[1], 6)],
        "vocab": vocab_logprob,
        "default": default_lp,
        "vocab_size": V,
        "alpha": alpha,
        "n_train": len(train_examples),
    }
    return model


def predict(model, text):
    toks = tokenize(normalise(text))
    log0 = model["priors"][0]
    log1 = model["priors"][1]
    vocab = model["vocab"]
    default = model["default"]
    for t in toks:
        lp = vocab.get(t, default)
        log0 += lp[0]
        log1 += lp[1]
    return 1 if log1 > log0 else 0


def evaluate(model, test_examples):
    tp = tn = fp = fn = 0
    for text, label in test_examples:
        pred = predict(model, text)
        if pred == 1 and label == 1:
            tp += 1
        elif pred == 0 and label == 0:
            tn += 1
        elif pred == 1 and label == 0:
            fp += 1
        else:
            fn += 1
    total = tp + tn + fp + fn
    acc = (tp + tn) / total if total else 0
    precision = tp / (tp + fp) if (tp + fp) else 0
    recall = tp / (tp + fn) if (tp + fn) else 0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0
    return {
        "n_test": total,
        "accuracy": round(acc, 4),
        "precision_scam": round(precision, 4),
        "recall_scam": round(recall, 4),
        "f1_scam": round(f1, 4),
        "confusion": {"tp": tp, "tn": tn, "fp": fp, "fn": fn},
    }


def main():
    examples = build_corpus()
    print(f"Total labelled examples: {len(examples)}")
    n_scam = sum(1 for _, l in examples if l == 1)
    print(f"  scam/spam: {n_scam}  genuine: {len(examples) - n_scam}")
    if len(examples) < 50:
        print("FATAL: not enough data yet (dataset download probably still in progress). Aborting.")
        sys.exit(1)

    train_ex, test_ex = split_train_test(examples)
    model = train_naive_bayes(train_ex)
    report = evaluate(model, test_ex)
    report["n_train"] = len(train_ex)
    report["vocab_size"] = model["vocab_size"]
    print(json.dumps(report, indent=2, ensure_ascii=False))

    os.makedirs(os.path.dirname(OUT_MODEL), exist_ok=True)
    with open(OUT_MODEL, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False)
    with open(OUT_REPORT, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"Model written to {OUT_MODEL}")
    print(f"Report written to {OUT_REPORT}")


if __name__ == "__main__":
    main()
