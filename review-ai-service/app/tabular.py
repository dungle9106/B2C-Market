"""Risk models on rows of numbers, trained on the platform's own history: buyer churn and order fraud.

The backend sends one row per case: {features: {name: number}, label: 1 | 0 | null, rule: number, ts: epoch}
  label  what really happened (1 = the buyer left / the order was a fraud or a "bom hang"), null = not known yet
  rule   the score of the backend's hand-written rules, the baseline the model must beat
  ts     when the case happened: the evaluation is done "in the future" (oldest 60% train, next 20% choose, last 20% test)

Two classifiers compete (logistic regression, gradient boosting); the one that ranks the validation cases best is kept
and refitted on everything. With anomaly=True an IsolationForest also learns what a usual case looks like, which
needs no label at all (useful for fraud: very few confirmed cases).
"""
import math
import os
import threading
from datetime import datetime, timezone

import joblib
import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier, IsolationForest
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, roc_auc_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

TOP = 0.1  # "the 10% riskiest cases": what an admin can actually look at


def _logistic():
    return make_pipeline(StandardScaler(), LogisticRegression(max_iter=3000, class_weight="balanced", C=0.5))


def _boosting():
    return HistGradientBoostingClassifier(max_iter=200, learning_rate=0.06, max_leaf_nodes=15, min_samples_leaf=10,
                                          l2_regularization=1.0, class_weight="balanced", random_state=42)


CANDIDATES = {"logistic": _logistic, "boosting": _boosting}


def _matrix(rows: list[dict], columns: list[str]) -> np.ndarray:
    def num(v):
        try:
            f = float(v)
            return f if math.isfinite(f) else 0.0
        except (TypeError, ValueError):
            return 0.0
    return np.array([[num(r.get(c)) for c in columns] for r in rows], dtype=float)


def metrics(y: np.ndarray, s: np.ndarray) -> dict:
    """rocAuc: chance a positive case is ranked above a negative one (0.5 = coin flip).
    prAuc: average precision. top: precision / recall within the TOP riskiest cases."""
    n = max(1, int(round(len(y) * TOP)))
    top = np.argsort(-s, kind="stable")[:n]
    pos = max(1, int(y.sum()))
    return {"rocAuc": round(float(roc_auc_score(y, s)), 4), "prAuc": round(float(average_precision_score(y, s)), 4),
            "precisionTop": round(float(y[top].mean()), 4), "recallTop": round(float(y[top].sum() / pos), 4)}


class RiskModel:
    def __init__(self, name: str, env: str, min_labelled: int, min_per_class: int, anomaly: bool = False):
        self.path = os.environ.get(env, os.path.join(os.path.dirname(__file__), "..", "models", f"{name}.joblib"))
        self.min_labelled = min_labelled
        self.min_per_class = min_per_class
        self.anomaly = anomaly
        self._lock = threading.Lock()
        self._model = None
        self._loaded = False
        self.last_test = None  # scores on the test cases of the last training (for charts), not saved

    def reload(self):
        with self._lock:
            self._loaded = False
            self._model = None

    def load(self):
        with self._lock:
            if not self._loaded:
                self._model = joblib.load(self.path) if os.path.exists(self.path) else None
                self._loaded = True
            return self._model

    def info(self) -> dict:
        m = self.load()
        base = {"minLabelled": self.min_labelled, "minPerClass": self.min_per_class}
        if not m:
            return {"trained": False, **base}
        return {"trained": True, **base, **{k: m[k] for k in ("samples", "labelled", "positives", "algorithm", "trainedAt",
                                                               "metrics", "importance", "supervised", "testCases")}}

    def train(self, rows: list[dict]) -> dict:
        self.last_test = None
        columns = sorted({k for r in rows for k in r["features"]})
        if not columns:
            raise ValueError("không có dữ liệu")
        labelled = sorted([r for r in rows if r.get("label") in (0, 1)], key=lambda r: r.get("ts") or 0)
        y = np.array([int(r["label"]) for r in labelled])
        positives = int(y.sum())
        supervised = len(labelled) >= self.min_labelled and min(positives, len(y) - positives) >= self.min_per_class
        if not supervised and not (self.anomaly and len(rows) >= self.min_labelled):
            raise ValueError(f"cần ít nhất {self.min_labelled} trường hợp đã biết kết quả, mỗi loại ít nhất {self.min_per_class} "
                             f"(hiện có {len(labelled)}: {positives} có, {len(y) - positives} không)")

        model = {"columns": columns, "samples": len(rows), "labelled": len(labelled), "positives": positives,
                 "supervised": supervised, "classifier": None, "explainer": None, "algorithm": None, "metrics": {},
                 "importance": [], "testCases": 0, "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds")}

        if self.anomaly:
            x_all = _matrix([r["features"] for r in rows], columns)
            forest = IsolationForest(n_estimators=200, contamination="auto", random_state=42).fit(x_all)
            std = x_all.std(axis=0)
            model.update(forest=forest, reference=np.sort(-forest.score_samples(x_all)), mean=x_all.mean(axis=0),
                         std=np.where(std > 0, std, 1.0), varies=std > 0)
            model["algorithm"] = "isolation-forest"

        if supervised:
            x = _matrix([r["features"] for r in labelled], columns)
            rule = np.array([float(r.get("rule") or 0) for r in labelled])
            a, b = int(len(y) * 0.6), int(len(y) * 0.8)
            parts_ok = all(0 < y[s].sum() < len(y[s]) for s in (slice(0, a), slice(a, b), slice(b, None)))
            if parts_ok:
                # pick on the middle part, report on the newest part, which neither the fit nor the choice saw
                valid = {n: roc_auc_score(y[a:b], make().fit(x[:a], y[:a]).predict_proba(x[a:b])[:, 1]) for n, make in CANDIDATES.items()}
                best = max(valid, key=valid.get)
                test_fit = CANDIDATES[best]().fit(x[:b], y[:b])
                ty, tx = y[b:], x[b:]
                self.last_test = {"y": ty, "model": test_fit.predict_proba(tx)[:, 1], "rule": rule[b:]}
                model["metrics"] = {"model": metrics(ty, test_fit.predict_proba(tx)[:, 1]),
                                    "rule": metrics(ty, rule[b:]),
                                    "logistic": metrics(ty, CANDIDATES["logistic"]().fit(x[:b], y[:b]).predict_proba(tx)[:, 1])}
                if self.anomaly:
                    model["metrics"]["anomaly"] = metrics(ty, -model["forest"].score_samples(tx))
                model["metrics"]["validation"] = {k: round(float(v), 4) for k, v in valid.items()}
                model["testCases"] = int(len(ty))
            else:
                best = "logistic"  # too few cases of one kind to split in time: a simple model, no test numbers
            model["classifier"] = CANDIDATES[best]().fit(x, y)
            model["algorithm"] = best + ("+isolation-forest" if self.anomaly else "")
            explainer = _logistic().fit(x, y)
            model["explainer"] = explainer
            coef = explainer[-1].coef_[0]
            order = np.argsort(-np.abs(coef))
            model["importance"] = [{"feature": columns[k], "weight": round(float(coef[k]), 3)} for k in order[:8]]

        os.makedirs(os.path.dirname(os.path.abspath(self.path)), exist_ok=True)
        joblib.dump(model, self.path)
        with self._lock:
            self._model = model
            self._loaded = True
        return self.info()

    def score(self, items: list[dict]) -> dict:
        m = self.load()
        if not m or not items:
            return {"trained": bool(m), "items": []}
        x = _matrix([i["features"] for i in items], m["columns"])
        prob = m["classifier"].predict_proba(x)[:, 1] if m["classifier"] is not None else None
        out = []
        for k, item in enumerate(items):
            r = {"id": item.get("id"), "probability": None if prob is None else round(float(prob[k]), 4), "factors": [], "unusual": []}
            if m["explainer"] is not None:
                scaler, lr = m["explainer"][0], m["explainer"][-1]
                contrib = lr.coef_[0] * scaler.transform(x[k:k + 1])[0]
                r["factors"] = [m["columns"][j] for j in np.argsort(-contrib)[:3] if contrib[j] > 0.3]
            if self.anomaly:
                raw = -m["forest"].score_samples(x[k:k + 1])[0]
                r["percentile"] = round(float(np.searchsorted(m["reference"], raw, side="right") / len(m["reference"])), 4)
                z = (x[k] - m["mean"]) / m["std"]
                r["unusual"] = [m["columns"][j] for j in np.argsort(-z)[:3] if z[j] >= 2 and m["varies"][j]]
            out.append(r)
        return {"trained": True, "items": out}


churn = RiskModel("churn", "CHURN_MODEL_PATH", min_labelled=40, min_per_class=8)
fraud = RiskModel("fraud", "FRAUD_MODEL_PATH", min_labelled=40, min_per_class=5, anomaly=True)
