"""Offline benchmark of the recommender on a SIMULATED marketplace (for the report, when the real database is still
too small to measure anything). Not real data: the numbers show the method works, the admin page shows the real ones.

    python -m app.benchmark                 1000 buyers, 400 products, 20 categories
    python -m app.benchmark --users 3000    bigger

How the shoppers are simulated:
  - every product has a category and a hidden "style" (8 numbers); every buyer likes 1-3 categories and has a style;
  - some products go together across categories (phone -> phone case, crib -> baby bottle): looking at one makes the
    other more likely next;
  - a buyer looks at 4-30 products in a row, each chosen with probability ~ exp(category liking + style match +
    "goes with the previous one" + popularity); about 1 in 4 views becomes add-to-cart, 1 in 3 of those a purchase.
None of these hidden rules are given to the model: it only sees who interacted with what, like on the real site.
"""
import argparse
import os
import tempfile

import numpy as np


def simulate(users=1000, items=400, categories=20, seed=7):
    rng = np.random.default_rng(seed)
    cat = rng.integers(0, categories, items)
    parent = cat // 4
    style = rng.normal(size=(items, 8))
    pop = rng.pareto(2.0, items)
    pop = np.log1p(pop / pop.max() * 20)
    # complementary pairs across categories
    partner = {}
    for i in rng.choice(items, items // 3, replace=False):
        j = int(rng.integers(0, items))
        if cat[j] != cat[i]:
            partner.setdefault(int(i), []).append(j)
            partner.setdefault(j, []).append(int(i))

    interactions, ts = [], 0
    for u in range(users):
        likes = np.zeros(categories)
        likes[rng.choice(categories, rng.integers(1, 4), replace=False)] = 2.5
        taste = rng.normal(size=8)
        prev = None
        for _ in range(int(rng.integers(4, 31))):
            logit = likes[cat] + 0.6 * (style @ taste) / np.sqrt(8) + 0.8 * pop
            if prev is not None:
                for j in partner.get(prev, []):
                    logit[j] += 3.0
            p = np.exp(logit - logit.max())
            p /= p.sum()
            i = int(rng.choice(items, p=p))
            ts += 1
            interactions.append({"user": f"u{u}", "item": i + 1, "weight": 1, "ts": ts})
            if rng.random() < 0.25:
                interactions.append({"user": f"u{u}", "item": i + 1, "weight": 3, "ts": ts})
                if rng.random() < 0.35:
                    interactions.append({"user": f"u{u}", "item": i + 1, "weight": 5, "ts": ts})
            prev = i
    catalogue = [{"id": i + 1, "category": int(cat[i]), "parent": int(parent[i])} for i in range(items)]
    return interactions, catalogue


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--users", type=int, default=1000)
    ap.add_argument("--items", type=int, default=400)
    ap.add_argument("--categories", type=int, default=20)
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()

    # train into a throw-away file: never replace the real model
    os.environ["RECO_MODEL_PATH"] = os.path.join(tempfile.mkdtemp(), "bench.joblib")
    from . import recommender
    recommender.MODEL_PATH = os.environ["RECO_MODEL_PATH"]

    interactions, catalogue = simulate(a.users, a.items, a.categories, a.seed)
    r = recommender.train(interactions, catalogue)
    m = r["metrics"]
    print(f"Du lieu mo phong: {r['users']} nguoi mua, {r['items']} san pham, {len(interactions)} su kien, "
          f"{r['interactions']} cap (nguoi, san pham); danh gia tren {m['testUsers']} nguoi")
    print(f"Tham so chon tren tap validation: beta={r['params']['beta']}, gamma={r['params']['gamma']}\n")
    print(f"{'Phuong phap':<34}{'HitRate@10':>12}{'NDCG@10':>10}{'Coverage':>10}")
    for key, name in (("popular", "Ban chay nhat (luat cu)"), ("sameCategory", "Cung danh muc da xem (luat cu)"),
                      ("model", "Mo hinh hoc tu hanh vi (moi)")):
        x = m[key]
        print(f"{name:<34}{x['hitRate']:>12.3f}{x['ndcg']:>10.3f}{x['coverage']:>10.3f}")
    best_rule = max(m["popular"]["ndcg"], m["sameCategory"]["ndcg"])
    if best_rule > 0:
        print(f"\nNDCG@10 cua mo hinh cao hon luat tot nhat {100 * (m['model']['ndcg'] / best_rule - 1):.0f}%")


if __name__ == "__main__":
    main()
