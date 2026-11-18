"""Benchmark of the churn and fraud models on a simulated marketplace (the real database is too small to train on).

    python -m app.benchmark_risk

Each simulated case has the same features as the backend sends (ChurnService, FraudService) and the same
hand-written rule score, so the table compares exactly what the admin pages show: rules vs the trained model,
evaluated on the newest 20% of cases, which the model never saw.
"""
import os
import tempfile

import numpy as np

from .tabular import RiskModel

rng = np.random.default_rng(7)


def churn_rows(n=3000):
    rows = []
    for k in range(n):
        rate = rng.gamma(1.5, 0.6)                     # orders per month while active
        engaged = rng.beta(2, 2)                       # how much the buyer still browses
        unhappy = rng.random() < 0.15                  # had a bad experience lately
        tenure = rng.uniform(60, 900)
        gap = 30 / max(rate, 0.05) * rng.uniform(0.7, 1.3)
        recency = rng.exponential(gap) * (1.6 if engaged < 0.3 else 1.0)
        orders_total = max(1, int(tenure / gap * rng.uniform(0.5, 1.0)))
        views = rng.poisson(40 * engaged)
        carts = rng.poisson(4 * engaged)
        logins = rng.poisson(12 * engaged)
        bad = rng.poisson(1.5) if unhappy else rng.poisson(0.1)
        returns = rng.poisson(0.8) if unhappy else rng.poisson(0.1)
        ratio = recency / gap
        # leaving depends on recency *relative to the buyer's own rhythm*, engagement and bad experiences
        logit = -1.2 + 1.1 * min(ratio, 4) - 3.0 * engaged + 0.9 * bad + 0.6 * returns - 0.15 * min(orders_total, 20) / 4
        label = int(rng.random() < 1 / (1 + np.exp(-logit)))
        features = {"recencyDays": recency, "ordersTotal": orders_total, "orders90d": rng.poisson(3 * rate * (recency < 90)),
                    "spendTotal": orders_total * rng.uniform(150, 600), "avgGapDays": gap, "recencyRatio": ratio,
                    "views30d": views, "carts30d": carts, "likes30d": rng.poisson(2 * engaged), "logins30d": logins,
                    "cancelRatio": rng.beta(1, 12), "returns": returns, "badReviews": bad,
                    "avgRating": 5 - min(4, bad) * 0.8 if orders_total else 0, "tenureDays": tenure}
        rows.append({"features": features, "label": label, "rule": recency, "ts": k})
    return rows


def fraud_rule(f):
    s = 0
    s += 25 if f["accountAgeDays"] < 1 else 10 if f["accountAgeDays"] < 7 else 0
    s += 20 if f["ordersLastHour"] >= 3 else 0
    s += 20 if f["priorOrders"] > 0 and f["amountRatio"] >= 5 else 0
    s += 10 if f["cod"] and f["amount"] >= 5000 else 0
    s += 20 if f["phoneSharedAccounts"] >= 3 else 10 if f["phoneSharedAccounts"] >= 2 else 0
    s += 25 if f["priorOrders"] >= 2 and f["priorFailRatio"] >= 0.5 else 0
    s += 10 if f["discountRatio"] >= 0.5 else 0
    s += 25 if f["newDeviceLogin"] and f["failedLogins24h"] >= 3 else 0
    s += 10 if f["quantity"] >= 20 else 0
    return min(100, s)


def fraud_rows(n=6000):
    rows = []
    for k in range(n):
        kind = rng.choice(["ok", "bom", "voucher", "takeover"], p=[0.92, 0.04, 0.025, 0.015])
        age = rng.exponential(300)
        prior = rng.poisson(6)
        amount = rng.lognormal(5.6, 0.8)
        f = {"amount": amount, "amountRatio": rng.lognormal(0, 0.5), "accountAgeDays": age, "priorOrders": prior,
             "ordersLastHour": rng.poisson(0.1), "ordersLastDay": rng.poisson(0.4), "cod": int(rng.random() < 0.55),
             "priorFailRatio": rng.beta(1, 15), "phonesLast7d": 1, "phoneSharedAccounts": int(rng.random() < 0.05),
             "discountRatio": rng.beta(1, 8), "quantity": rng.poisson(1.5) + 1, "newDeviceLogin": int(rng.random() < 0.08),
             "failedLogins24h": rng.poisson(0.1), "nightHour": int(rng.random() < 0.07)}
        if kind == "bom":       # COD orders refused at the door: young accounts, shared phones, earlier refusals
            f.update(cod=1, accountAgeDays=rng.exponential(20), priorFailRatio=rng.beta(2, 3),
                     phoneSharedAccounts=rng.poisson(1.2), ordersLastDay=rng.poisson(1.2))
        elif kind == "voucher":  # new accounts farming vouchers
            f.update(accountAgeDays=rng.exponential(3), priorOrders=rng.poisson(0.5), discountRatio=rng.beta(5, 4),
                     phoneSharedAccounts=rng.poisson(1.5), amount=rng.lognormal(5.0, 0.4))
        elif kind == "takeover":  # stolen account: new device, unusual basket, at night
            f.update(newDeviceLogin=int(rng.random() < 0.8), failedLogins24h=rng.poisson(1.5), amountRatio=rng.lognormal(1.3, 0.6),
                     nightHour=int(rng.random() < 0.4), quantity=rng.poisson(4) + 1, cod=int(rng.random() < 0.2))
        rows.append({"features": f, "label": int(kind != "ok"), "rule": fraud_rule(f), "ts": k})
    return rows


def table(title, m):
    print(f"\n{title}  (test: {m['testCases']} trường hợp mới nhất, thuật toán: {m['algorithm']})")
    print(f"{'':12}{'ROC-AUC':>9}{'PR-AUC':>9}{'Prec@10%':>10}{'Recall@10%':>12}")
    for k in ("rule", "anomaly", "logistic", "model"):
        if k in m["metrics"]:
            v = m["metrics"][k]
            print(f"{k:12}{v['rocAuc']:>9.3f}{v['prAuc']:>9.3f}{v['precisionTop']:>10.3f}{v['recallTop']:>12.3f}")


if __name__ == "__main__":
    tmp = tempfile.mkdtemp()
    churn = RiskModel("churn", "_NONE_", 40, 8)
    churn.path = os.path.join(tmp, "churn.joblib")
    table("DỰ ĐOÁN RỜI BỎ (3000 người mua mô phỏng)", churn.train(churn_rows()))
    fraud = RiskModel("fraud", "_NONE_", 40, 5, anomaly=True)
    fraud.path = os.path.join(tmp, "fraud.joblib")
    table("PHÁT HIỆN GIAN LẬN ĐƠN (6000 đơn mô phỏng, 8% gian lận)", fraud.train(fraud_rows()))
