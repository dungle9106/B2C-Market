"""Return abuse models, trained on the platform's case history (one row of numbers per return request).

  - IsolationForest, no labels needed: how unusual a case is compared with every case seen so far, as a
    percentile (0.97 = more unusual than 97% of them);
  - LogisticRegression, once admins have labelled enough cases: the probability a case is like the ones they
    confirmed as abuse.

Both only move the backend's rule-based score; the decision stays with an admin.
"""
import os
import threading
from datetime import datetime, timezone

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import StratifiedKFold, cross_val_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

MODEL_PATH = os.environ.get("RETURN_MODEL_PATH", os.path.join(os.path.dirname(__file__), "..", "models", "return_risk.joblib"))
MIN_SAMPLES = 30
MIN_PER_CLASS = 5
_lock = threading.Lock()
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
        return {"trained": False, "minSamples": MIN_SAMPLES}
    return {"trained": True, "samples": m["samples"], "labelled": m["labelled"], "supervised": m["classifier"] is not None,
            "trainedAt": m["trainedAt"], "metrics": m["metrics"], "minSamples": MIN_SAMPLES}


def _matrix(rows: list[dict], columns: list[str]) -> np.ndarray:
    return np.array([[float(r.get(c) or 0) for c in columns] for r in rows], dtype=float)


def train(items: list[dict]) -> dict:
    if len(items) < MIN_SAMPLES:
        raise ValueError(f"cần ít nhất {MIN_SAMPLES} ca đã phân tích, có {len(items)}")
    columns = sorted({k for i in items for k in i["features"]})
    x = _matrix([i["features"] for i in items], columns)

    forest = IsolationForest(n_estimators=200, contamination="auto", random_state=42).fit(x)
    std = x.std(axis=0)

    labelled = [i for i in items if i.get("label") in ("ABUSE", "LEGIT")]
    y = np.array([1 if i["label"] == "ABUSE" else 0 for i in labelled])
    minority = min(int(y.sum()), int(len(y) - y.sum())) if labelled else 0
    classifier = None
    metrics = {}
    if minority >= MIN_PER_CLASS:
        lx = _matrix([i["features"] for i in labelled], columns)
        make = lambda: make_pipeline(StandardScaler(), LogisticRegression(max_iter=2000, class_weight="balanced"))
        folds = StratifiedKFold(n_splits=min(5, minority), shuffle=True, random_state=42)
        metrics["rocAuc"] = round(float(cross_val_score(make(), lx, y, cv=folds, scoring="roc_auc").mean()), 3)
        classifier = make().fit(lx, y)

    model = {"columns": columns, "forest": forest, "reference": np.sort(-forest.score_samples(x)), "mean": x.mean(axis=0),
             "std": np.where(std > 0, std, 1.0), "varies": std > 0, "classifier": classifier, "samples": len(items),
             "labelled": len(labelled), "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"), "metrics": metrics}
    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    joblib.dump(model, MODEL_PATH)
    global _model
    with _lock:
        _model = model
    return info()


def score(features: dict) -> dict:
    m = load()
    if not m:
        return {"trained": False}
    x = _matrix([features], m["columns"])
    raw = -m["forest"].score_samples(x)[0]
    percentile = float(np.searchsorted(m["reference"], raw, side="right") / len(m["reference"]))
    z = (x[0] - m["mean"]) / m["std"]
    # the features that make this case stand out (only high values: a low count is not suspicious)
    unusual = [m["columns"][k] for k in np.argsort(-z)[:3] if z[k] >= 2 and m["varies"][k]]
    probability = float(m["classifier"].predict_proba(x)[0, 1]) if m["classifier"] is not None else None
    return {"trained": True, "percentile": round(percentile, 4), "abuseProbability": None if probability is None else round(probability, 4), "unusual": unusual}
