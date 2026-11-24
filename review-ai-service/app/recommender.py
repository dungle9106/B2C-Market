"""Product recommendation model, trained on how buyers actually shop (views, likes, add-to-cart, purchases).

Item-based collaborative filtering ("people who were interested in A were also interested in B"), blended with a
little content (same category / same shop) and popularity so new or rarely seen products still get a score:

    score(user, j) = sum_i  w(user, i) * sim(i, j)              learned from everyone's behaviour
                   + beta  * share of the user's history in j's category
                   + gamma * popularity(j)

sim is the cosine similarity between the columns of the user x item matrix (log-scaled weights), with a shrinkage
term so two items seen together by a single user don't look identical, cut to the 50 nearest neighbours per item.
beta and gamma are chosen on a validation split; the reported numbers come from a separate test split:

    each user with >= 3 products: last product -> test, second to last -> validation, the rest -> training
    HitRate@10 = share of users whose held-out product is in their top 10
    NDCG@10    = the same, rewarding a higher rank (1 at rank 1, 1/log2(r+1) at rank r)
    Coverage   = share of the catalogue that appears in at least one user's top 10

compared against the two rules the shop used before: best sellers, and "same category as what you viewed".
"""
import math
import os
import threading
from collections import defaultdict
from datetime import datetime, timezone

import joblib
import numpy as np
from scipy import sparse

MODEL_PATH = os.environ.get("RECO_MODEL_PATH", os.path.join(os.path.dirname(__file__), "..", "models", "recommender.joblib"))
MIN_USERS = 10
MIN_INTERACTIONS = 50
NEIGHBOURS = 50
SHRINK = 5.0
K = 10
_lock = threading.Lock()
_model = None


# ==================== data ====================

def _aggregate(interactions):
    """(user, item) -> summed weight, and each user's items ordered by their last interaction."""
    weight = defaultdict(float)
    last = {}
    for r in interactions:
        key = (str(r["user"]), int(r["item"]))
        weight[key] += float(r.get("weight") or 1.0)
        ts = float(r.get("ts") or 0)
        last[key] = max(last.get(key, ts), ts)
    sequences = defaultdict(list)
    for (u, i), ts in last.items():
        if weight[(u, i)] > 0:
            sequences[u].append((ts, i))
    for u in sequences:
        sequences[u].sort()
    return weight, {u: [i for _, i in s] for u, s in sequences.items()}


def _matrix(pairs, users, item_index):
    """users x items CSR with log1p weights."""
    u_index = {u: k for k, u in enumerate(users)}
    rows, cols, vals = [], [], []
    for (u, i), w in pairs.items():
        if u in u_index and i in item_index and w > 0:
            rows.append(u_index[u])
            cols.append(item_index[i])
            vals.append(math.log1p(w))
    return sparse.csr_matrix((vals, (rows, cols)), shape=(len(users), len(item_index)))


def _similarity(x):
    """Item x item cosine similarity with shrinkage, top NEIGHBOURS per row, zero diagonal."""
    xc = x.tocsc()
    norms = np.sqrt(np.asarray(xc.multiply(xc).sum(axis=0)).ravel())
    co = (xc.T @ xc).tocoo()
    support = ((xc > 0).astype(np.float32).T @ (xc > 0).astype(np.float32)).tocsr()
    keep = co.row != co.col
    r, c, v = co.row[keep], co.col[keep], co.data[keep]
    denom = norms[r] * norms[c]
    n = np.asarray(support[r, c]).ravel()
    v = np.where(denom > 0, v / np.maximum(denom, 1e-12), 0) * (n / (n + SHRINK))
    sim = sparse.csr_matrix((v, (r, c)), shape=co.shape)
    # top neighbours per row
    rows, cols, vals = [], [], []
    for i in range(sim.shape[0]):
        start, end = sim.indptr[i], sim.indptr[i + 1]
        if end == start:
            continue
        idx, data = sim.indices[start:end], sim.data[start:end]
        top = np.argsort(-data)[:NEIGHBOURS]
        rows.extend([i] * len(top))
        cols.extend(idx[top])
        vals.extend(data[top])
    return sparse.csr_matrix((vals, (rows, cols)), shape=sim.shape)


class _Scorer:
    """Everything needed to score: built once per training set."""

    def __init__(self, items, pairs, users):
        self.ids = [int(i["id"]) for i in items]
        self.index = {i: k for k, i in enumerate(self.ids)}
        self.category = np.array([hash(str(i.get("category"))) if i.get("category") is not None else 0 for i in items])
        self.parent = np.array([hash(str(i.get("parent"))) if i.get("parent") is not None else 0 for i in items])
        x = _matrix(pairs, users, self.index)
        self.sim = _similarity(x)
        users_per_item = np.asarray((x > 0).sum(axis=0)).ravel().astype(float)
        self.popularity = users_per_item / users_per_item.max() if users_per_item.max() > 0 else users_per_item

    def history_vector(self, history):
        v = np.zeros(len(self.ids))
        for item, w in history:
            k = self.index.get(int(item))
            if k is not None and w > 0:
                v[k] += math.log1p(w)
        return v

    def scores(self, h, beta, gamma):
        cf = self.sim.T @ h if h.any() else np.zeros(len(self.ids))
        cf = cf / cf.max() if cf.max() > 0 else cf
        content = np.zeros(len(self.ids))
        total = h.sum()
        if total > 0:
            for k in np.nonzero(h)[0]:
                content += (h[k] / total) * ((self.category == self.category[k]) * 1.0 + (self.parent == self.parent[k]) * 0.3)
        return cf + beta * content + gamma * self.popularity

    def top(self, scores, exclude, k):
        s = scores.copy()
        for i in exclude:
            j = self.index.get(int(i))
            if j is not None:
                s[j] = -np.inf
        order = np.argsort(-s)[:k]
        return [self.ids[j] for j in order if np.isfinite(s[j])]


# ==================== evaluation ====================

def _split(sequences):
    """Leave-last-out: users with at least 3 products give one validation and one test product."""
    train_seq, val, test = {}, {}, {}
    for u, items in sequences.items():
        if len(items) >= 3:
            train_seq[u], val[u], test[u] = items[:-2], items[-2], items[-1]
        else:
            train_seq[u] = items
    return train_seq, val, test


def _restrict(pairs, sequences):
    keep = {(u, i) for u, items in sequences.items() for i in items}
    return {k: w for k, w in pairs.items() if k in keep}


def _evaluate(rank_fn, held_out, catalogue_size, k=K):
    hits, ndcg, shown = 0, 0.0, set()
    for u, target in held_out.items():
        ranked = rank_fn(u)[:k]
        shown.update(ranked)
        if target in ranked:
            hits += 1
            ndcg += 1 / math.log2(ranked.index(target) + 2)
    n = max(1, len(held_out))
    return {"hitRate": round(hits / n, 4), "ndcg": round(ndcg / n, 4), "coverage": round(len(shown) / max(1, catalogue_size), 4)}


def _rankers(scorer, pairs, sequences):
    """The learned model and the two old rules, ranking for one user of `sequences`."""

    def history(u):
        return [(i, pairs.get((u, i), 1.0)) for i in sequences.get(u, [])]

    def model(beta, gamma):
        return lambda u: scorer.top(scorer.scores(scorer.history_vector(history(u)), beta, gamma), sequences.get(u, []), K)

    def popular(u):
        return scorer.top(scorer.popularity, sequences.get(u, []), K)

    def same_category(u):
        # the old "co the ban thich": products in the categories of the last viewed ones, best sellers first
        recent = sequences.get(u, [])[-10:]
        cats = {scorer.category[scorer.index[i]] for i in recent if i in scorer.index}
        s = scorer.popularity + np.isin(scorer.category, list(cats)) * 10.0
        return scorer.top(s, sequences.get(u, []), K)

    return model, popular, same_category


BETAS = [0.0, 0.15, 0.3, 0.6, 1.0, 2.0, 4.0]
GAMMAS = [0.0, 0.05, 0.15]


def train(interactions: list[dict], items: list[dict]) -> dict:
    items = [i for i in items if i.get("id") is not None]
    known = {int(i["id"]) for i in items}
    interactions = [r for r in interactions if int(r["item"]) in known]
    pairs, sequences = _aggregate(interactions)
    if len(sequences) < MIN_USERS or len(pairs) < MIN_INTERACTIONS:
        raise ValueError(f"cần ít nhất {MIN_USERS} người mua và {MIN_INTERACTIONS} lượt tương tác, có {len(sequences)} người và {len(pairs)} lượt")

    # 1. tune on validation (trained without the validation and test products)
    train_seq, val, test = _split(sequences)
    metrics, params = {}, {"beta": 0.3, "gamma": 0.05}
    if len(test) >= 5:
        tr_pairs = _restrict(pairs, train_seq)
        scorer = _Scorer(items, tr_pairs, list(train_seq))
        model, _, _ = _rankers(scorer, tr_pairs, train_seq)
        best = -1
        for b in BETAS:
            for g in GAMMAS:
                m = _evaluate(model(b, g), val, len(items))
                if m["ndcg"] > best:
                    best, params = m["ndcg"], {"beta": b, "gamma": g}

        # 2. test: the held-out last product, trained on everything before it (validation product included)
        before_test = {u: s[:-1] if u in test else s for u, s in sequences.items()}
        te_pairs = _restrict(pairs, before_test)
        scorer = _Scorer(items, te_pairs, list(before_test))
        model, popular, same_category = _rankers(scorer, te_pairs, before_test)
        metrics = {
            "model": _evaluate(model(params["beta"], params["gamma"]), test, len(items)),
            "popular": _evaluate(popular, test, len(items)),
            "sameCategory": _evaluate(same_category, test, len(items)),
            "testUsers": len(test),
        }

    # 3. final model on all the data
    scorer = _Scorer(items, pairs, list(sequences))
    model = {"scorer": scorer, "params": params, "metrics": metrics, "users": len(sequences), "items": len(items),
             "interactions": len(pairs), "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds")}
    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    joblib.dump(model, MODEL_PATH)
    global _model
    with _lock:
        _model = model
    return info()


def reload():
    """Forget the model in memory: the next call reads the file again (trained by another process)."""
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
        return {"trained": False, "minUsers": MIN_USERS, "minInteractions": MIN_INTERACTIONS}
    return {"trained": True, "users": m["users"], "items": m["items"], "interactions": m["interactions"], "params": m["params"],
            "metrics": m["metrics"], "trainedAt": m["trainedAt"], "minUsers": MIN_USERS, "minInteractions": MIN_INTERACTIONS}


def recommend(history: list[dict], exclude: list[int], k: int = 12) -> dict:
    """history: [{item, weight}] of one buyer (any buyer, also one who joined after training)."""
    m = load()
    if not m:
        return {"trained": False, "items": []}
    s = m["scorer"]
    h = s.history_vector([(r["item"], float(r.get("weight") or 1.0)) for r in history])
    scores = s.scores(h, m["params"]["beta"], m["params"]["gamma"])
    top = s.top(scores, set(exclude) | {int(r["item"]) for r in history if r.get("seen", True)}, k)
    out = []
    for item in top:
        j = s.index[item]
        # the product of the history that pulled this one up the most: "Vì bạn đã xem ..."
        because, best = None, 0.0
        for r in history:
            i = s.index.get(int(r["item"]))
            if i is not None:
                v = s.sim[i, j]
                if v > best:
                    because, best = int(r["item"]), float(v)
        out.append({"item": item, "score": round(float(scores[j]), 4), "because": because})
    return {"trained": True, "items": out}


def similar(item: int, k: int = 12) -> dict:
    """Products bought / viewed by the same people as `item`, then same category."""
    m = load()
    if not m:
        return {"trained": False, "items": []}
    s = m["scorer"]
    j = s.index.get(int(item))
    if j is None:
        return {"trained": True, "items": []}
    row = s.sim.getrow(j).toarray().ravel()
    scores = row / row.max() if row.max() > 0 else row
    scores = scores + 0.2 * (s.category == s.category[j]) + 0.05 * s.popularity
    scores[j] = -np.inf
    order = [x for x in np.argsort(-scores)[:k] if np.isfinite(scores[x])]
    return {"trained": True, "items": [{"item": s.ids[x], "score": round(float(scores[x]), 4), "learned": bool(row[x] > 0)} for x in order]}
