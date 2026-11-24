"""The trainable part: TF-IDF features + one logistic regression per issue (multi-label) and one for sentiment.

Trained on a base set of labelled Vietnamese reviews (app/review_data.py) plus the reviews labelled by sellers
(their corrections in the review hub) and by the LLM. Small, fast, CPU only: a few thousand reviews train in
seconds, and classifying costs nothing per call.
"""
import os
import threading
import warnings
from datetime import datetime, timezone

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score
from sklearn.multiclass import OneVsRestClassifier
from sklearn.pipeline import FeatureUnion
from sklearn.preprocessing import MultiLabelBinarizer

from .lexicon import ISSUES, normalize
from .review_data import base_set, test_set

MODEL_PATH = os.environ.get("MODEL_PATH", os.path.join(os.path.dirname(__file__), "..", "models", "review_model.joblib"))
# scipy/scikit-learn version chatter, not a problem
warnings.filterwarnings("ignore", message="Unknown solver options")
_lock = threading.Lock()
_model = None


def _features():
    # words and word pairs, plus character n-grams for typos and teen code ("ko", "dc", "sp")
    return FeatureUnion([
        ("words", TfidfVectorizer(ngram_range=(1, 2), min_df=1, sublinear_tf=True)),
        ("chars", TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), min_df=1, sublinear_tf=True)),
    ])


def _text(item) -> str:
    return f"sao{int(item['rating'])} " + normalize(item.get("content") or "")


def sentiment_of(rating: int) -> str:
    return "NEGATIVE" if rating <= 2 else "NEUTRAL" if rating == 3 else "POSITIVE"


def reload():
    global _model
    with _lock:
        _model = None


def load():
    global _model
    with _lock:
        if _model is None and os.path.exists(MODEL_PATH):
            _model = joblib.load(MODEL_PATH)
        return _model


def info() -> dict:
    m = load()
    if not m:
        return {"trained": False}
    return {"trained": True, "samples": m["samples"], "own": m.get("own"), "labels": m["labels"], "trainedAt": m["trainedAt"], "metrics": m["metrics"]}


def train(items: list[dict], with_base: bool = True) -> dict:
    """items: the platform's labelled reviews. with_base adds the base set, so a few corrections are enough."""
    own = [i for i in items if (i.get("content") or "").strip()]
    items = own + (base_set() if with_base else [])
    if len(items) < 20:
        raise ValueError(f"cần ít nhất 20 đánh giá có nội dung, có {len(items)}")
    texts = [_text(i) for i in items]
    labels = [[k for k in (i.get("issues") or []) if k in ISSUES] for i in items]
    sentiments = [i.get("sentiment") or sentiment_of(int(i["rating"])) for i in items]

    mlb = MultiLabelBinarizer(classes=[k for k in ISSUES if any(k in l for l in labels)])
    y = mlb.fit_transform(labels)

    metrics = {}
    # score reported to the admin: on the hand-written test reviews (never trained on, worded differently from the
    # base set). A random split of the base set would score ~1.0 and say nothing about new sentences.
    test = test_set()
    if y.shape[1] > 0:
        f = _features()
        clf = OneVsRestClassifier(LogisticRegression(max_iter=2000, class_weight="balanced"))
        clf.fit(f.fit_transform(texts), y)
        pred = clf.predict(f.transform([_text(t) for t in test]))
        gold = mlb.transform([[k for k in t["issues"] if k in mlb.classes_] for t in test])
        metrics["issuesMicroF1"] = round(float(f1_score(gold, pred, average="micro", zero_division=0)), 3)
        metrics["heldOut"] = len(test)
        metrics["testSet"] = "đánh giá viết tay (app/review_data.py)"

    features = _features()
    x = features.fit_transform(texts)
    issue_clf = None
    if y.shape[1] > 0:
        issue_clf = OneVsRestClassifier(LogisticRegression(max_iter=2000, class_weight="balanced"))
        issue_clf.fit(x, y)
    sent_clf = None
    if len(set(sentiments)) > 1:
        sent_clf = LogisticRegression(max_iter=2000, class_weight="balanced")
        sent_clf.fit(x, sentiments)

    model = {"features": features, "issues": issue_clf, "mlb": mlb, "sentiment": sent_clf, "samples": len(items), "own": len(own),
             "labels": list(mlb.classes_), "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"), "metrics": metrics}
    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    joblib.dump(model, MODEL_PATH)
    global _model
    with _lock:
        _model = model
    return info()


def predict(items: list[dict]) -> list[dict] | None:
    m = load()
    if not m:
        return None
    x = m["features"].transform([_text(i) for i in items])
    out = []
    issue_probs = m["issues"].predict_proba(x) if m["issues"] is not None else None
    sentiments = m["sentiment"].predict(x) if m["sentiment"] is not None else None
    for k in range(len(items)):
        issues = []
        if issue_probs is not None:
            issues = [label for label, p in zip(m["mlb"].classes_, issue_probs[k]) if p >= 0.5]
        out.append({"issues": issues, "sentiment": None if sentiments is None else str(sentiments[k])})
    return out
