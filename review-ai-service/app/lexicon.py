"""Vietnamese review lexicon: the same issue taxonomy as the backend (ReviewTextAnalyzer.java).

Used on its own until a model is trained, and afterwards for the high-precision phrases and the alarming words
that a small trained model may miss.
"""
import re
import unicodedata

ISSUES = {
    "QUALITY": "Chất lượng sản phẩm",
    "WRONG_DESCRIPTION": "Mô tả sai / không giống hình",
    "DELIVERY": "Giao hàng",
    "PACKAGING": "Đóng gói",
    "SIZE": "Size / kích thước",
    "MISSING_ITEM": "Thiếu hàng / sai hàng",
    "AUTHENTICITY": "Nghi hàng giả / an toàn",
    "PRICE": "Giá cả",
    "SERVICE": "Tư vấn / đổi trả",
    "OTHER": "Khác",
}

COMPLAINT = {
    "QUALITY": ["bi loi", "hang loi", "loi", "hong", "hu", "hu hong", "rach", "bung chi", "bung", "phai mau", "ra mau", "xu",
                "kem chat luong", "chat luong kem", "khong hoat dong", "khong len", "khong chay", "nhanh hong", "bi gay", "bi nut",
                "troc", "bong troc", "chi thua", "cau tha", "khong sac", "mui hoi", "moc", "bi moc", "het han", "bi chay"],
    "WRONG_DESCRIPTION": ["khong giong hinh", "khac hinh", "khong nhu hinh", "khong dung mo ta", "sai mo ta", "sai mau", "khac mau",
                          "khong giong mo ta", "khac mo ta", "khong giong quang cao"],
    "DELIVERY": ["giao cham", "giao lau", "giao tre", "ship cham", "ship lau", "cham tre", "lau qua", "doi lau", "qua lau",
                 "giao muon", "tre hen", "khong giao", "giao nham"],
    "PACKAGING": ["mop", "bep", "bi vo", "vo nat", "bi be", "nat", "dong goi so sai", "dong goi au", "dong goi khong can than",
                  "khong chong soc", "rach hop", "hop rach", "mop hop"],
    "SIZE": ["nho qua", "rong qua", "chat qua", "to qua", "dai qua", "ngan qua", "hoi nho", "hoi rong", "hoi chat", "hoi to", "hoi dai", "hoi ngan", "khong vua", "sai size", "lech size", "khong dung size"],
    "MISSING_ITEM": ["thieu", "giao thieu", "thieu hang", "thieu phu kien", "khong du", "giao sai", "gui sai", "nham hang", "sai hang"],
    "AUTHENTICITY": ["hang gia", "hang nhai", "fake", "nhai", "khong chinh hang", "di ung", "noi man", "ngua", "mui hac", "chay no",
                     "doc hai", "nguy hiem", "giat dien", "kich ung"],
    "PRICE": ["dat qua", "khong dang tien", "mac qua", "gia cao", "gia hoi cao", "hoi dat", "gia hoi dat", "khong xung dang"],
    "SERVICE": ["khong tra loi", "khong phan hoi", "thai do", "khong ho tro", "lua dao", "khong cho doi", "khong cho tra",
                "khong hoan tien", "chua hoan tien", "chan tin nhan", "doi tra kho", "tu van sai"],
}

# words that put a negative clause about this issue (as in the backend)
TOPIC = {
    "QUALITY": ["chat luong", "chat lieu", "vai", "duong may", "ben", "mong", "may", "pin", "hoat dong", "su dung", "mau"],
    "DELIVERY": ["giao", "giao hang", "ship", "shipper", "van chuyen", "nhan hang", "don vi van chuyen"],
    "PACKAGING": ["dong goi", "goi hang", "hop", "bao bi", "thung"],
    "SIZE": ["size", "kich thuoc", "kich co", "form", "mac"],
    "PRICE": ["gia ca", "gia tien", "muc gia", "tien"],
    "SERVICE": ["shop", "tu van", "phan hoi", "tra loi", "thai do", "ho tro", "doi tra", "doi hang", "hoan tien", "chat"],
}

POSITIVE = ["dep", "tot", "nhanh", "chac chan", "can than", "mem", "vua", "vua van", "dung mo ta", "giong hinh", "nhu hinh", "hai long",
            "ung y", "re", "dang tien", "xung dang", "nhiet tinh", "xinh", "chuan", "ok", "oke", "on", "thich", "tuyet voi", "ben",
            "day dan", "min", "thom", "hop ly", "chu dao", "than thien", "chong soc", "ky", "good", "dang mua", "ung ho", "yeu"]
NEGATIVE = ["xau", "te", "cham", "tre", "lau", "loi", "rach", "khong giong", "khac hinh", "sai", "that vong", "kem", "phai mau",
            "hong", "hu", "mop", "bep", "vo nat", "nat", "thieu", "cau tha", "xu", "chan qua", "buc", "phi tien", "khong nen mua",
            "dung mua", "lua dao", "hang gia", "nhai", "fake", "moc", "mui hoi", "di ung"]
NEGATIONS = {"khong", "ko", "k", "chua", "chang", "cha", "khg", "hok"}
SEVERE = ["lua dao", "hang gia", "hang nhai", "fake", "di ung", "noi man", "kich ung", "chay no", "giat dien", "doc hai", "nguy hiem",
          "bao cong an", "kien", "to cao", "boc phot", "dung mua", "khong nhan duoc hang", "mat tien", "chua hoan tien", "het han", "moc"]


def normalize(text: str) -> str:
    """Lower case, no accents, letters/digits only (as TextNormalizer.normalize in the backend)."""
    t = unicodedata.normalize("NFD", (text or "").replace("đ", "d").replace("Đ", "D"))
    t = "".join(c for c in t if unicodedata.category(c) != "Mn").lower()
    t = re.sub(r"[^a-z0-9.,!?;\n ]", " ", t)
    return re.sub(r" +", " ", t).strip()


def _has(text: str, word: str) -> bool:
    return f" {word} " in text


def _negated(text: str, word: str) -> bool:
    i = text.find(f" {word} ")
    if i < 0:
        return False
    before = text[:i].split()
    return any(w in NEGATIONS for w in before[-3:])


def analyze(rating: int, content: str) -> dict:
    norm = normalize(content)
    issues, pos, neg = [], 0, 0
    for clause in re.split(r"[.,!?;\n]+| nhung | tuy nhien | ma ", norm):
        c = f" {clause.strip()} "
        if not c.strip():
            continue
        cp = sum(1 for w in POSITIVE if _has(c, w) and not _negated(c, w))
        cn = sum(1 for w in POSITIVE if _has(c, w) and _negated(c, w))
        cn += sum(1 for w in NEGATIVE if _has(c, w) and not _negated(c, w))
        for key, words in COMPLAINT.items():
            if any(_has(c, w) and not _negated(c, w) for w in words):
                if key not in issues:
                    issues.append(key)
                cn += 1
        if cn > cp:
            for key, words in TOPIC.items():
                if key not in issues and any(_has(c, w) for w in words):
                    issues.append(key)
        pos += cp
        neg += cn
    polarity = 0.0 if pos + neg == 0 else (pos - neg) / (pos + neg)
    stars = {1: -1.0, 2: -0.6, 3: 0.0, 4: 0.5}.get(rating, 1.0)
    score = stars if pos + neg == 0 else stars * 0.6 + polarity * 0.4
    sentiment = "NEGATIVE" if score <= -0.25 else "POSITIVE" if score >= 0.35 else "NEUTRAL"
    if not issues and rating <= 2:
        issues = ["OTHER"]
    if sentiment == "POSITIVE" and rating >= 4 and neg == 0:
        issues = []
    plain = " " + re.sub(r" +", " ", re.sub(r"[^a-z0-9 ]", " ", norm)) + " "
    severe = [w for w in SEVERE if _has(plain, w)]
    return {"sentiment": sentiment, "score": round(score, 2), "issues": issues, "severe": severe, "polarity": round(polarity, 2)}
