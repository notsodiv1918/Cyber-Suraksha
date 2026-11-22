"""Generate a templated, domain-specific synthetic corpus of Indian digital-
payment SMS/WhatsApp messages (genuine + scam) in English, Hindi and Hinglish.

Why: the public datasets fetched earlier (india-spam-sms, ScamShield-IN) are
real but skew the model in two ways for THIS app's exact domain —
  1. Almost no genuine examples use words like "shipped"/"OTP"/"delivery"
     (those appear mostly in the scam set, which is heavy on courier/KYC
     scams), so the model flags plain shipping/OTP confirmations as scam.
  2. Hindi-script training text is machine-translated generic SMS spam, not
     authentic Indian fraud phrasing — "KYC"/"UPI PIN"/"digital arrest" style
     terms barely occur, so Hindi scams are under-detected.
Templated generation with randomised fill-ins (bank names, amounts, order
IDs, OTP codes, parcel trackers) directly targets both gaps with on-topic,
labelled, varied examples — not hand-written one-offs, but combinatorially
diverse enough for Naive Bayes to learn real term associations.
"""
import itertools
import json
import os
import random

random.seed(11)
OUT = os.path.join(os.path.dirname(__file__), "..", "data", "raw")

BANKS = ["SBI", "HDFC Bank", "ICICI Bank", "Axis Bank", "PNB", "Bank of Baroda", "Kotak Bank", "Union Bank"]
NAMES = ["Ramesh", "Suresh", "Anita", "Pooja", "Vikas", "Deepak", "Sunita", "Rahul", "Priya", "Manoj"]
AMOUNTS = ["250", "480", "1,200", "2,500", "5,000", "99", "15,750", "640", "3,200", "850"]
COURIERS = ["Delhivery", "Blue Dart", "India Post", "Ekart", "DTDC", "Amazon Logistics"]

# ---------------- GENUINE (label 0) ----------------
GENUINE_TEMPLATES_EN = [
    "Your {bank} A/c XX{acct} is credited with Rs.{amt} on {date}. Avl Bal Rs.{bal}.",
    "Your {bank} A/c XX{acct} is debited with Rs.{amt} on {date} for UPI/P2M. Avl Bal Rs.{bal}.",
    "Your OTP for {bank} transaction is {otp}. Valid for 10 minutes. Do not share this OTP with anyone.",
    "Your order #{order} has been shipped via {courier} and will be delivered by {date}.",
    "Your parcel from {courier} is out for delivery today, tracking ID {order}.",
    "Rs.{amt} received from {name} via UPI. UPI Ref No {order}.",
    "Your recharge of Rs.{amt} for mobile number is successful. Validity till {date}.",
    "Reminder: your appointment with Dr. {name} is scheduled for {date} at the clinic.",
    "Your electricity bill of Rs.{amt} has been paid successfully. Thank you.",
    "Hi {name}, are we still meeting tomorrow at 6pm for the project review?",
    "Your salary of Rs.{amt} has been credited to your {bank} account.",
    "Thank you for shopping with us. Your invoice for Rs.{amt} has been generated.",
]
GENUINE_TEMPLATES_HI = [
    "आपके {bank} खाते में {date} को {amt} रुपये जमा हुए हैं। उपलब्ध शेष {bal} रुपये।",
    "आपके {bank} खाते से {date} को UPI भुगतान हेतु {amt} रुपये डेबिट हुए। शेष {bal} रुपये।",
    "आपका {bank} लेनदेन हेतु OTP {otp} है। यह 10 मिनट के लिए मान्य है, किसी को न बताएं।",
    "आपका ऑर्डर #{order} {courier} द्वारा भेज दिया गया है, {date} तक डिलीवर होगा।",
    "{name} से UPI द्वारा {amt} रुपये प्राप्त हुए। संदर्भ संख्या {order}।",
    "आपका मोबाइल रिचार्ज {amt} रुपये सफल रहा, वैधता {date} तक।",
    "डॉ. {name} के साथ आपकी अपॉइंटमेंट {date} को निर्धारित है।",
    "आपका बिजली बिल {amt} रुपये सफलतापूर्वक जमा हो गया है, धन्यवाद।",
    "नमस्ते {name}, क्या कल शाम 6 बजे मीटिंग पक्की है?",
    "आपका वेतन {amt} रुपये आपके {bank} खाते में जमा कर दिया गया है।",
]
GENUINE_TEMPLATES_HINGLISH = [
    "Aapke {bank} account me {amt} rupaye credit hue hain, available balance {bal} rupaye hai.",
    "Aapka {bank} transaction OTP {otp} hai, 10 minute valid hai, kisi ko mat batana.",
    "Aapka order #{order} {courier} se ship ho gaya hai, {date} tak deliver hoga.",
    "{name} se UPI se {amt} rupaye mile hain, reference number {order}.",
    "Aapka mobile recharge {amt} rupaye ka successful ho gaya hai.",
    "Hi {name}, kal shaam 6 baje meeting confirm hai kya?",
    "Aapki salary {amt} rupaye {bank} account me credit ho gayi hai.",
]

# ---------------- SCAM (label 1) ----------------
SCAM_TEMPLATES_EN = [
    "Dear customer, your {bank} KYC is pending. Update within 24 hours via this link or your account will be blocked: http://{bank_lc}-kyc-update.in/verify",
    "URGENT: Your {bank} account will be suspended today. Share the OTP {otp} sent to you to reactivate immediately.",
    "Congratulations! You have won Rs.{amt},000 in the lucky draw. Share your OTP to claim the prize now.",
    "Your parcel from {courier} is held at customs with restricted items. Pay Rs.{amt} fine immediately to avoid legal action.",
    "This is {name} from cyber crime cell. A case is registered in your name, you are under digital arrest, pay Rs.{amt} immediately to avoid arrest.",
    "Your electricity connection will be disconnected tonight due to unpaid bill. Click here to pay now: http://bit.ly/elec-{order}",
    "Earn Rs.{amt} daily working from home, no experience needed. Send your bank details to start today.",
    "Your loan of Rs.{amt},000 is pre-approved. Pay a processing fee of Rs.{amt} to get instant disbursal.",
    "Invest Rs.{amt} and get guaranteed double returns in 7 days. Limited slots, UPI payment only.",
    "Your {bank} debit card is blocked. Call this number immediately and share your card number and PIN to unblock.",
]
SCAM_TEMPLATES_HI = [
    "प्रिय ग्राहक, आपका {bank} KYC अपडेट लंबित है। तुरंत इस लिंक पर क्लिक कर अपडेट करें वरना खाता ब्लॉक हो जाएगा।",
    "चेतावनी: आपका {bank} खाता आज ब्लॉक हो जाएगा। तुरंत अपना OTP {otp} साझा कर खाता सक्रिय करें।",
    "बधाई हो! आपने लकी ड्रॉ में {amt},000 रुपये जीते हैं। इनाम पाने हेतु अभी अपना OTP भेजें।",
    "आपका पार्सल कस्टम में रुका है, प्रतिबंधित सामान मिला है। कानूनी कार्रवाई से बचने हेतु तुरंत {amt} रुपये जुर्माना भरें।",
    "मैं साइबर क्राइम सेल से बोल रहा हूँ। आपके नाम केस दर्ज है, आप डिजिटल अरेस्ट में हैं, गिरफ़्तारी से बचने हेतु तुरंत {amt} रुपये भेजें।",
    "आपका बिजली कनेक्शन आज रात कट जाएगा, बकाया बिल भरने हेतु तुरंत इस लिंक पर क्लिक करें।",
    "घर बैठे रोज़ {amt} रुपये कमाएँ, बिना अनुभव के। शुरू करने हेतु अपनी बैंक डिटेल भेजें।",
    "आपका {amt},000 रुपये का लोन स्वीकृत है। तुरंत डिसबर्सल हेतु {amt} रुपये प्रोसेसिंग फीस भेजें।",
    "{amt} रुपये निवेश करें और 7 दिन में दोगुना गारंटीड रिटर्न पाएं। सीमित सीटें, केवल UPI भुगतान।",
    "आपका {bank} डेबिट कार्ड ब्लॉक हो गया है। तुरंत कॉल कर अपना कार्ड नंबर एवं पिन साझा करें।",
]
SCAM_TEMPLATES_HINGLISH = [
    "Dear customer, aapka {bank} KYC pending hai. Turant is link par click karke update karo warna account block ho jayega.",
    "URGENT: Aapka {bank} account aaj block ho jayega. Turant apna OTP {otp} share karke reactivate karo.",
    "Badhai ho! Aapne lucky draw mein {amt},000 rupaye jeete hain. Prize claim karne ke liye abhi OTP bhejo.",
    "Aapka parcel customs mein ruka hai, restricted items mile hain. Legal action se bachne ke liye turant {amt} rupaye fine bharo.",
    "Mai cyber crime cell se bol raha hu. Aapke naam case register hai, aap digital arrest mein ho, turant {amt} rupaye bhejo.",
    "Aapka bijli connection aaj raat cut ho jayega, turant is link par click karke bill bharo.",
    "Ghar baithe roz {amt} rupaye kamao, bina experience ke. Shuru karne ke liye bank details bhejo.",
    "Aapka {amt},000 ka loan pre-approved hai. Turant disbursal ke liye {amt} rupaye processing fee bhejo.",
    "{amt} rupaye invest karo aur 7 din mein double guaranteed return pao. Limited slots, sirf UPI payment.",
    "Aapka {bank} debit card block ho gaya hai. Turant call karke apna card number aur PIN share karo.",
]

DATES = ["12-Nov", "15-Nov", "18-Nov", "20-Nov", "22-Nov", "25-Nov", "02-Dec", "05-Dec"]


def fill(template):
    return template.format(
        bank=random.choice(BANKS),
        bank_lc=random.choice(BANKS).lower().replace(" ", ""),
        name=random.choice(NAMES),
        amt=random.choice(AMOUNTS),
        bal=random.choice(AMOUNTS),
        otp=str(random.randint(100000, 999999)),
        order=str(random.randint(100000, 999999)),
        acct=str(random.randint(1000, 9999)),
        date=random.choice(DATES),
        courier=random.choice(COURIERS),
    )


def generate(templates, label, n_per_template):
    out = []
    for t in templates:
        seen = set()
        tries = 0
        while len(seen) < n_per_template and tries < n_per_template * 6:
            tries += 1
            msg = fill(t)
            if msg not in seen:
                seen.add(msg)
                out.append({"text": msg, "label": label})
    return out


def main():
    rows = []
    rows += generate(GENUINE_TEMPLATES_EN, 0, 14)
    rows += generate(GENUINE_TEMPLATES_HI, 0, 14)
    rows += generate(GENUINE_TEMPLATES_HINGLISH, 0, 14)
    rows += generate(SCAM_TEMPLATES_EN, 1, 14)
    rows += generate(SCAM_TEMPLATES_HI, 1, 14)
    rows += generate(SCAM_TEMPLATES_HINGLISH, 1, 14)
    random.shuffle(rows)
    path = os.path.join(OUT, "synthetic_domain.jsonl")
    with open(path, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    n_scam = sum(1 for r in rows if r["label"] == 1)
    print(f"wrote {len(rows)} synthetic examples ({n_scam} scam, {len(rows)-n_scam} genuine) -> {path}")


if __name__ == "__main__":
    main()
