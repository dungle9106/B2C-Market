"""Train Shoppi's AI models straight from the database, in Python, on every product of every category.

    python train_from_db.py              all three models
    python train_from_db.py reco         only the recommendation model (or: churn, fraud, reviews, vision; several allowed)

What it does, for each model:
  1. reads the history from PostgreSQL (orders, views, carts, likes, reviews, logins...)
  2. builds the training rows (the same features as the backend: ChurnService / FraudService / RecommendationService)
  3. trains with scikit-learn and evaluates on data the model never saw (the newest cases), against the old rules
  4. saves the model in models/ (the AI service uses it at once: the script calls its /reload)
  5. writes charts and a summary in reports/ (PNG + Markdown, ready for the project report)

The recommendation model is also scored per industry (top-level category: Điện tử, Thời trang, Mỹ phẩm...).
The review classifier (issues + sentiment) is tested on hand-written reviews against the keyword rules (lexicon).
Computer vision: the category of a product photo, MobileNetV2 features + logistic regression, against colour matching.

Needs:    pip install -r requirements-train.txt (psycopg, matplotlib on top of the service libraries)
Database: PGHOST (127.0.0.1), PGPORT (5433), PGUSER (postgres), PGPASSWORD (123456), PGDATABASE (marketplace).
AI service to reload: AI_URL (http://localhost:8000).
"""
import json
import os
import sys
import time
import urllib.request
from collections import defaultdict
from datetime import datetime, timedelta

import matplotlib

matplotlib.use("Agg")  # files only, no window
import matplotlib.pyplot as plt
import numpy as np
import psycopg
from psycopg.rows import dict_row
from sklearn.metrics import f1_score, roc_curve
from sklearn.preprocessing import MultiLabelBinarizer

from app import lexicon, model as review_model, recommender, review_data, tabular

HERE = os.path.dirname(os.path.abspath(__file__))
REPORTS = os.path.join(HERE, "reports")
AI_URL = os.environ.get("AI_URL", "http://localhost:8000")
HORIZON = 60      # churn: "left" = no order in the next 60 days
CUTOFFS = 12      # churn: monthly cut-off dates replayed

# ==================== database ====================


def connect():
    return psycopg.connect(host=os.environ.get("PGHOST", "127.0.0.1"), port=int(os.environ.get("PGPORT", "5433")),
                           user=os.environ.get("PGUSER", "postgres"), password=os.environ.get("PGPASSWORD", "123456"),
                           dbname=os.environ.get("PGDATABASE", "marketplace"), row_factory=dict_row)


def rows(db, sql, params=None):
    with db.cursor() as cur:
        cur.execute(sql, params or {})
        return cur.fetchall()


def num(v):
    return 0.0 if v is None else float(v)


# ==================== 1. recommendation ====================

# every signal of interest, weighted: view 1, like 2, cart 3, purchase 5, good review +2, bad review -3
INTERACTIONS = """
    select user_id as u, product_id as i, 1.0 as w, extract(epoch from viewed_at) as ts from view_history
    union all select user_id, product_id, 3.0, extract(epoch from created_at) from product_events
        where type = 'ADD_TO_CART' and user_id is not null
    union all select user_id, product_id, 2.0, extract(epoch from created_at) from product_likes
    union all select o.buyer_id, oi.product_id, 5.0, extract(epoch from o.created_at)
        from order_items oi join orders o on o.id = oi.order_id where o.status <> 'CANCELLED'
    union all select buyer_id, product_id, case when rating >= 4 then 2.0 when rating <= 2 then -3.0 else 0 end,
        extract(epoch from created_at) from reviews
"""


def train_reco(db):
    print("\n=== 1. MÔ HÌNH GỢI Ý SẢN PHẨM (collaborative filtering) ===")
    interactions = [{"user": str(r["u"]), "item": int(r["i"]), "weight": float(r["w"]), "ts": num(r["ts"])}
                    for r in rows(db, f"select u, i, w, ts from ({INTERACTIONS}) x where w <> 0")]
    items = rows(db, """
        select p.id, p.name, p.category_id as category, coalesce(c.parent_id, c.id) as parent, coalesce(pc.name, c.name, 'Khác') as industry
        from products p left join categories c on c.id = p.category_id left join categories pc on pc.id = c.parent_id
        where p.active""")
    industry = {int(i["id"]): i["industry"] for i in items}
    per_industry = defaultdict(int)
    for i in items:
        per_industry[i["industry"]] += 1
    print(f"Dữ liệu: {len(interactions)} lượt tương tác, {len({r['user'] for r in interactions})} người mua, "
          f"{len(items)} sản phẩm thuộc {len(per_industry)} ngành hàng")

    t0 = time.time()
    info = recommender.train(interactions, [{"id": i["id"], "category": i["category"], "parent": i["parent"]} for i in items])
    print(f"Huấn luyện xong trong {time.time() - t0:.1f}s, tham số chọn trên tập validation: {info['params']}")
    m = info["metrics"]
    if not m:
        print("Chưa đủ người mua có từ 3 sản phẩm để đánh giá.")
        return {"info": info}
    print(f"\nĐánh giá trên {m['testUsers']} người mua (giấu sản phẩm cuối cùng của mỗi người):")
    table = [("Bán chạy nhất (luật cũ)", m["popular"]), ("Cùng danh mục (luật cũ)", m["sameCategory"]), ("MÔ HÌNH", m["model"])]
    print(f"  {'Cách gợi ý':26}{'HitRate@10':>11}{'NDCG@10':>9}{'Độ phủ':>8}")
    for name, v in table:
        print(f"  {name:26}{v['hitRate']:>11.3f}{v['ndcg']:>9.3f}{v['coverage']:>8.1%}")

    # the same test, split by the industry of the hidden product
    by = by_industry(interactions, items, info["params"], industry)
    print("\nTheo ngành hàng (ngành của sản phẩm bị giấu):")
    print(f"  {'Ngành hàng':30}{'Người':>6}{'Mô hình':>9}{'Luật':>7}")
    for name, v in sorted(by.items(), key=lambda kv: -kv[1]["n"]):
        print(f"  {name[:30]:30}{v['n']:>6}{v['model']:>9.3f}{v['rule']:>7.3f}")

    chart_reco(table, by, per_industry)
    return {"info": info, "byIndustry": by, "products": dict(per_industry)}


def by_industry(interactions, items, params, industry):
    """Re-runs recommender's test step and groups each hit (NDCG@10) by the industry of the hidden product."""
    pairs, sequences = recommender._aggregate(interactions)
    _, _, test = recommender._split(sequences)
    before = {u: s[:-1] if u in test else s for u, s in sequences.items()}
    tr = recommender._restrict(pairs, before)
    scorer = recommender._Scorer([{"id": i["id"], "category": i["category"], "parent": i["parent"]} for i in items], tr, list(before))
    model, _, same_category = recommender._rankers(scorer, tr, before)
    rank_model = model(params["beta"], params["gamma"])
    out = defaultdict(lambda: {"n": 0, "model": 0.0, "rule": 0.0})

    def gain(ranked, target):
        ranked = ranked[:recommender.K]
        return 1 / np.log2(ranked.index(target) + 2) if target in ranked else 0.0

    for u, target in test.items():
        g = out[industry.get(target, "Khác")]
        g["n"] += 1
        g["model"] += gain(rank_model(u), target)
        g["rule"] += gain(same_category(u), target)
    for g in out.values():
        g["model"] = round(g["model"] / g["n"], 4)
        g["rule"] = round(g["rule"] / g["n"], 4)
    return dict(out)


def chart_reco(table, by, per_industry):
    fig, ax = plt.subplots(1, 2, figsize=(14, 5.5), gridspec_kw={"width_ratios": [1, 2]})
    names = [t[0] for t in table]
    ax[0].bar(range(3), [t[1]["ndcg"] for t in table], color=["#bbbbbb", "#bbbbbb", "#ee4d2d"])
    ax[0].set_xticks(range(3), [n.replace(" (luật cũ)", "\n(luật cũ)") for n in names], fontsize=9)
    ax[0].set_title("NDCG@10 trên tập test")
    for k, t in enumerate(table):
        ax[0].text(k, t[1]["ndcg"], f"{t[1]['ndcg']:.3f}", ha="center", va="bottom")
    inds = sorted(by, key=lambda k: -by[k]["n"])
    x = np.arange(len(inds))
    ax[1].bar(x - 0.2, [by[k]["rule"] for k in inds], 0.4, label="Luật: cùng danh mục", color="#bbbbbb")
    ax[1].bar(x + 0.2, [by[k]["model"] for k in inds], 0.4, label="Mô hình", color="#ee4d2d")
    ax[1].set_xticks(x, [f"{k}\n({by[k]['n']} người)" for k in inds], rotation=35, ha="right", fontsize=8)
    ax[1].set_title("NDCG@10 theo ngành hàng")
    ax[1].legend()
    fig.suptitle(f"Mô hình gợi ý: {sum(per_industry.values())} sản phẩm, {len(per_industry)} ngành hàng")
    save(fig, "reco.png")


# ==================== 2. churn ====================

CHURN_FEATURES = """
    with b as (
      select o.buyer_id as u, max(o.created_at) as last_at, min(o.created_at) as first_at, count(*) as n,
        count(*) filter (where o.created_at >= %(t)s - interval '90 days') as n90, sum(o.total) as spend
      from orders o where o.created_at < %(t)s and o.status <> 'CANCELLED' group by o.buyer_id
    )
    select b.u, b.spend, b.n, b.n90,
      extract(epoch from (%(t)s - b.last_at)) / 86400 as recency,
      case when b.n > 1 then extract(epoch from (b.last_at - b.first_at)) / 86400 / (b.n - 1) end as gap,
      (select count(*) from orders o where o.buyer_id = b.u and o.created_at < %(t)s and o.status = 'CANCELLED') as cancelled,
      (select count(*) from view_history v where v.user_id = b.u and v.viewed_at >= %(t)s - interval '30 days' and v.viewed_at < %(t)s) as views,
      (select count(*) from product_events e where e.user_id = b.u and e.type = 'ADD_TO_CART'
          and e.created_at >= %(t)s - interval '30 days' and e.created_at < %(t)s) as carts,
      (select count(*) from product_likes l where l.user_id = b.u and l.created_at >= %(t)s - interval '30 days' and l.created_at < %(t)s) as likes,
      (select count(*) from login_events l where l.user_id = b.u and l.result = 'SUCCESS'
          and l.created_at >= %(t)s - interval '30 days' and l.created_at < %(t)s) as logins,
      (select count(*) from return_requests r join orders o on o.id = r.order_id where o.buyer_id = b.u and r.created_at < %(t)s) as returns,
      (select count(*) from reviews r where r.buyer_id = b.u and r.rating <= 2 and r.created_at < %(t)s) as bad,
      (select avg(r.rating) from reviews r where r.buyer_id = b.u and r.created_at < %(t)s) as rating,
      extract(epoch from (%(t)s - u.created_at)) / 86400 as tenure
    from b join users u on u.id = b.u
    where u.role = 'BUYER' and u.deleted_at is null
"""


def churn_features(r):
    orders, cancelled = num(r["n"]), num(r["cancelled"])
    usual = HORIZON if r["gap"] is None else max(1.0, float(r["gap"]))
    recency = max(0.0, num(r["recency"]))
    return {"recencyDays": recency, "recencyRatio": recency / usual, "ordersTotal": orders, "orders90d": num(r["n90"]),
            "spendTotal": num(r["spend"]) / 1000, "avgGapDays": usual, "views30d": num(r["views"]), "carts30d": num(r["carts"]),
            "likes30d": num(r["likes"]), "logins30d": num(r["logins"]), "cancelRatio": cancelled / (orders + cancelled),
            "returns": num(r["returns"]), "badReviews": num(r["bad"]), "avgRating": num(r["rating"]), "tenureDays": max(0.0, num(r["tenure"]))}


def train_churn(db):
    print("\n=== 2. MÔ HÌNH DỰ ĐOÁN RỜI BỎ ===")
    latest = datetime.now() - timedelta(days=HORIZON)
    data = []
    for k in range(CUTOFFS):
        t = latest - timedelta(days=30 * k)
        came = {r["buyer_id"] for r in rows(db, """select distinct buyer_id from orders where status <> 'CANCELLED'
            and created_at >= %(t)s and created_at < %(t)s + interval '60 days'""", {"t": t})}
        found = rows(db, CHURN_FEATURES, {"t": t})
        for r in found:
            f = churn_features(r)
            data.append({"features": f, "label": 0 if r["u"] in came else 1, "rule": f["recencyDays"], "ts": t.timestamp()})
        if found:
            print(f"  mốc {t:%d/%m/%Y}: {len(found)} người mua, {sum(1 for r in found if r['u'] not in came)} không quay lại trong {HORIZON} ngày")
    return train_tabular(tabular.churn, data, "churn", "Dự đoán rời bỏ", "người mua × mốc thời gian", "Luật: số ngày chưa mua")


# ==================== 3. fraud ====================

FRAUD_FEATURES = """
    select o.id, o.created_at,
      o.total / 1000.0 as amount,
      (select count(*) from orders p where p.buyer_id = o.buyer_id and p.created_at < o.created_at) as prior_orders,
      coalesce(o.total / nullif((select percentile_cont(0.5) within group (order by p.total) from orders p
          where p.buyer_id = o.buyer_id and p.created_at < o.created_at and p.status <> 'CANCELLED'), 0), 1) as amount_ratio,
      extract(epoch from (o.created_at - u.created_at)) / 86400 as account_age,
      (select count(*) from orders p where p.buyer_id = o.buyer_id
          and p.created_at between o.created_at - interval '1 hour' and o.created_at) as orders_hour,
      (select count(*) from orders p where p.buyer_id = o.buyer_id
          and p.created_at between o.created_at - interval '24 hours' and o.created_at) as orders_day,
      case when pay.method = 'COD' then 1 else 0 end as cod,
      coalesce((select avg(case when p.status = 'CANCELLED' and p.shipped_at is not null
              or exists (select 1 from tracking_events t where t.order_id = p.id and t.stage = 'DELIVERY_FAILED') then 1.0 else 0 end)
          from orders p where p.buyer_id = o.buyer_id and p.created_at < o.created_at), 0) as prior_fail,
      (select count(distinct p.recipient_phone) from orders p where p.buyer_id = o.buyer_id
          and p.created_at between o.created_at - interval '7 days' and o.created_at) as phones_week,
      (select count(distinct p.buyer_id) from orders p where o.recipient_phone is not null
          and p.recipient_phone = o.recipient_phone and p.buyer_id <> o.buyer_id and p.created_at <= o.created_at) as phone_shared,
      (coalesce(o.discount, 0) + coalesce(o.combo_discount, 0) + coalesce(o.coin_discount, 0)) / nullif(o.subtotal, 0) as discount_ratio,
      (select coalesce(sum(i.quantity), 0) from order_items i where i.order_id = o.id) as quantity,
      case when exists (select 1 from login_events l where l.user_id = o.buyer_id and l.new_device and l.result = 'SUCCESS'
          and l.created_at between o.created_at - interval '24 hours' and o.created_at) then 1 else 0 end as new_device,
      (select count(*) from login_events l where l.user_id = o.buyer_id and l.result in ('BAD_PASSWORD', 'TWO_FACTOR_FAILED')
          and l.created_at between o.created_at - interval '24 hours' and o.created_at) as failed_logins,
      case when extract(hour from o.created_at) < 5 then 1 else 0 end as night,
      case when r.verdict = 'FRAUD' then 1 when r.verdict = 'SAFE' then 0
           when exists (select 1 from tracking_events t where t.order_id = o.id and t.stage = 'DELIVERY_FAILED')
                or (o.status = 'CANCELLED' and o.shipped_at is not null) then 1
           when o.status in ('DELIVERED', 'RETURN_REQUESTED', 'REFUNDED') then 0 end as label
    from orders o join users u on u.id = o.buyer_id left join payments pay on pay.order_id = o.id
      left join order_risks r on r.order_id = o.id
    order by o.created_at desc limit 200000
"""


def fraud_rule(f):
    """The backend's FraudRules.evaluate (same points), the baseline."""
    s = 0
    s += 25 if f["accountAgeDays"] < 1 else 10 if f["accountAgeDays"] < 7 else 0
    s += 20 if f["ordersLastHour"] >= 3 else 0
    s += 20 if f["priorOrders"] > 0 and f["amountRatio"] >= 5 else 0
    s += 10 if f["cod"] >= 1 and f["amount"] >= 5000 else 0
    s += 20 if f["phoneSharedAccounts"] >= 3 else 10 if f["phoneSharedAccounts"] >= 2 else 0
    s += 25 if f["priorOrders"] >= 2 and f["priorFailRatio"] >= 0.5 else 0
    s += 10 if f["discountRatio"] >= 0.5 else 0
    s += 25 if f["newDeviceLogin"] >= 1 and f["failedLogins24h"] >= 3 else 0
    s += 10 if f["quantity"] >= 20 else 0
    return min(100, s)


def train_fraud(db):
    print("\n=== 3. MÔ HÌNH PHÁT HIỆN GIAN LẬN ĐƠN ===")
    data = []
    for r in rows(db, FRAUD_FEATURES):
        f = {"amount": num(r["amount"]), "amountRatio": num(r["amount_ratio"]), "accountAgeDays": max(0.0, num(r["account_age"])),
             "priorOrders": num(r["prior_orders"]), "ordersLastHour": num(r["orders_hour"]), "ordersLastDay": num(r["orders_day"]),
             "cod": num(r["cod"]), "priorFailRatio": num(r["prior_fail"]), "phonesLast7d": num(r["phones_week"]),
             "phoneSharedAccounts": num(r["phone_shared"]), "discountRatio": num(r["discount_ratio"]), "quantity": num(r["quantity"]),
             "newDeviceLogin": num(r["new_device"]), "failedLogins24h": num(r["failed_logins"]), "nightHour": num(r["night"])}
        data.append({"features": f, "label": r["label"], "rule": fraud_rule(f), "ts": r["created_at"].timestamp()})
    known = [d for d in data if d["label"] is not None]
    print(f"Dữ liệu: {len(data)} đơn, {len(known)} đã biết kết quả ({sum(d['label'] for d in known)} bom hàng / gian lận)")
    return train_tabular(tabular.fraud, data, "fraud", "Phát hiện gian lận đơn", "đơn", "Luật thủ công")


# ==================== shared: churn & fraud ====================

def train_tabular(model, data, key, title, unit, rule_name):
    t0 = time.time()
    try:
        info = model.train(data)
    except ValueError as e:
        print(f"Chưa huấn luyện được: {e}")
        return {"error": str(e)}
    print(f"Huấn luyện xong trong {time.time() - t0:.1f}s: {info['algorithm']}, {info['labelled']} trường hợp có nhãn ({info['positives']} có)")
    m = info["metrics"]
    if not m:
        print("Quá ít trường hợp để chia tập theo thời gian: chưa có số đánh giá.")
        return {"info": info}
    print(f"\nĐánh giá trên {info['testCases']} {unit} mới nhất (mô hình chưa từng thấy). Validation chọn: {m['validation']}")
    print(f"  {'Cách chấm':24}{'ROC-AUC':>9}{'PR-AUC':>9}{'Prec@10%':>10}{'Recall@10%':>12}")
    names = {"rule": rule_name, "anomaly": "Chỉ Isolation Forest", "logistic": "Hồi quy logistic", "model": "MÔ HÌNH ĐƯỢC CHỌN"}
    for k in ("rule", "anomaly", "logistic", "model"):
        if k in m:
            v = m[k]
            print(f"  {names[k]:24}{v['rocAuc']:>9.3f}{v['prAuc']:>9.3f}{v['precisionTop']:>10.1%}{v['recallTop']:>12.1%}")
    print("Đặc trưng quan trọng nhất (trọng số hồi quy logistic, + là tăng rủi ro):")
    for f in info["importance"][:6]:
        print(f"  {LABELS.get(f['feature'], f['feature']):28}{f['weight']:+.3f}")
    chart_tabular(model.last_test, info, key, title, rule_name)
    return {"info": info}


def chart_tabular(test, info, key, title, rule_name):
    fig, ax = plt.subplots(1, 2, figsize=(13, 5))
    if test is not None and 0 < test["y"].sum() < len(test["y"]):
        for scores, name, color in ((test["rule"], rule_name, "#999999"), (test["model"], "Mô hình", "#ee4d2d")):
            fpr, tpr, _ = roc_curve(test["y"], scores)
            auc = info["metrics"]["model" if name == "Mô hình" else "rule"]["rocAuc"]
            ax[0].plot(fpr, tpr, color=color, lw=2, label=f"{name} (AUC = {auc:.3f})")
        ax[0].plot([0, 1], [0, 1], "--", color="#dddddd")
        ax[0].set_xlabel("Tỉ lệ báo nhầm")
        ax[0].set_ylabel("Tỉ lệ phát hiện đúng")
        ax[0].set_title(f"Đường ROC trên {len(test['y'])} trường hợp test")
        ax[0].legend(loc="lower right")
    imp = info["importance"][:8][::-1]
    ax[1].barh([LABELS.get(f["feature"], f["feature"]) for f in imp], [f["weight"] for f in imp], color=["#ee4d2d" if f["weight"] > 0 else "#26aa99" for f in imp])
    ax[1].axvline(0, color="#999999", lw=0.8)
    ax[1].set_title("Đặc trưng quan trọng (đỏ: tăng rủi ro, xanh: giảm)")
    fig.suptitle(f"{title}: {info['algorithm']}")
    save(fig, f"{key}.png")


# ==================== 4. review classifier ====================

def train_reviews(db):
    print("\n=== 4. BỘ PHÂN LOẠI ĐÁNH GIÁ (vấn đề + cảm xúc) ===")
    # what sellers corrected in the review hub, and what the LLM labelled
    own = [{"content": r["content"], "rating": int(r["rating"] or 3),
            "issues": [k for k in (r["issues"] or "").split(",") if k],
            **({"sentiment": r["sentiment"]} if r["sentiment"] in ("POSITIVE", "NEUTRAL", "NEGATIVE") else {})}
           for r in rows(db, """select content, rating, issues, sentiment from review_hub_items
                                where (issues_manual or analyzed_by = 'AI') and coalesce(trim(content), '') <> ''""")]
    base = review_data.base_set()
    print(f"Dữ liệu: {len(base)} đánh giá nền có nhãn + {len(own)} đánh giá người bán / AI đã gán nhãn trên sàn")
    t0 = time.time()
    info = review_model.train(own)
    print(f"Huấn luyện xong trong {time.time() - t0:.1f}s trên {info['samples']} đánh giá, {len(info['labels'])} loại vấn đề")

    test = review_data.test_set()
    from app import main  # the service's /analyze: model + keyword rules together, as used on the platform
    learned = review_model.predict(test)
    combined = main.analyze(main.AnalyzeRequest(items=[main.Review(id=k, rating=t["rating"], content=t["content"]) for k, t in enumerate(test)]))["results"]
    systems = {
        "Từ điển (luật cũ)": [lexicon.analyze(t["rating"], t["content"]) for t in test],
        "Mô hình": [{"issues": m["issues"], "sentiment": m["sentiment"] or review_model.sentiment_of(t["rating"])} for m, t in zip(learned, test)],
        "Mô hình + từ điển (đang dùng)": combined,
    }
    keys = list(lexicon.ISSUES)
    mlb = MultiLabelBinarizer(classes=keys).fit([keys])
    gold = mlb.transform([t["issues"] for t in test])
    print(f"\nĐánh giá trên {len(test)} đánh giá viết tay (câu chữ khác hẳn dữ liệu huấn luyện):")
    print(f"  {'Cách phân loại':32}{'F1 vấn đề':>10}{'Đúng hết':>10}{'Cảm xúc':>9}")
    scores = {}
    for name, out in systems.items():
        pred = mlb.transform([[i for i in o["issues"] if i in keys] for o in out])
        scores[name] = {
            "f1": round(float(f1_score(gold, pred, average="micro", zero_division=0)), 3),
            "exact": round(float(np.mean([set(o["issues"]) == set(t["issues"]) for o, t in zip(out, test)])), 3),
            "sentiment": round(float(np.mean([o["sentiment"] == t["sentiment"] for o, t in zip(out, test)])), 3),
            "perIssue": {k: round(float(f1_score(gold[:, j], pred[:, j], zero_division=0)), 3) for j, k in enumerate(keys) if gold[:, j].any()},
        }
        v = scores[name]
        print(f"  {name:32}{v['f1']:>10.3f}{v['exact']:>10.1%}{v['sentiment']:>9.1%}")
    best = "Mô hình + từ điển (đang dùng)"
    print("\nF1 theo từng loại vấn đề (từ điển → đang dùng):")
    for k, f in scores[best]["perIssue"].items():
        print(f"  {lexicon.ISSUES[k]:30}{scores['Từ điển (luật cũ)']['perIssue'][k]:>6.2f} → {f:.2f}")
    misses = [(t, o) for t, o in zip(test, combined) if set(o["issues"]) != set(t["issues"])]
    if misses:
        print(f"\nVí dụ phân loại chưa đúng ({len(misses)} câu):")
        for t, o in misses[:6]:
            print(f"  \"{t['content'][:70]}\"  đúng: {t['issues'] or '-'}  máy: {o['issues'] or '-'}")
    chart_reviews(scores)
    return {"info": info, "scores": scores, "test": len(test)}


def chart_reviews(scores):
    fig, ax = plt.subplots(1, 2, figsize=(14, 5), gridspec_kw={"width_ratios": [1, 1.6]})
    names = list(scores)
    colors = ["#bbbbbb", "#f5a38f", "#ee4d2d"]
    x = np.arange(3)
    for k, name in enumerate(names):
        vals = [scores[name]["f1"], scores[name]["exact"], scores[name]["sentiment"]]
        ax[0].bar(x + (k - 1) * 0.27, vals, 0.27, label=name, color=colors[k])
    ax[0].set_xticks(x, ["F1 vấn đề", "Đúng hết vấn đề", "Đúng cảm xúc"])
    ax[0].set_ylim(0, 1.05)
    ax[0].legend(fontsize=8, loc="lower right")
    ax[0].set_title("Trên đánh giá viết tay")
    issues = list(scores[names[-1]]["perIssue"])
    y = np.arange(len(issues))
    ax[1].barh(y + 0.2, [scores[names[0]]["perIssue"][i] for i in issues], 0.4, label=names[0], color="#bbbbbb")
    ax[1].barh(y - 0.2, [scores[names[-1]]["perIssue"][i] for i in issues], 0.4, label=names[-1], color="#ee4d2d")
    ax[1].set_yticks(y, [lexicon.ISSUES[i] for i in issues], fontsize=9)
    ax[1].set_xlim(0, 1.05)
    ax[1].invert_yaxis()
    ax[1].legend(fontsize=8)
    ax[1].set_title("F1 theo loại vấn đề")
    fig.suptitle("Bộ phân loại đánh giá: TF-IDF + hồi quy logistic")
    save(fig, "reviews.png")


# ==================== 5. computer vision ====================

UPLOADS = os.environ.get("UPLOAD_DIR", os.path.join(HERE, "..", "uploads"))


def train_vision(db):
    print("\n=== 5. THỊ GIÁC MÁY TÍNH: ĐOÁN DANH MỤC TỪ ẢNH SẢN PHẨM ===")
    from sklearn.model_selection import GroupKFold
    from app import vision
    found = rows(db, """
        select pi.url, p.id as product, p.name, p.category_id as category, c.name as category_name,
               coalesce(c.parent_id, c.id) as industry, coalesce(pc.name, c.name) as industry_name
        from product_images pi join products p on p.id = pi.product_id join categories c on c.id = p.category_id
        left join categories pc on pc.id = c.parent_id
        where p.active order by p.id, pi.position""")
    data, images = [], []
    for r in found:
        path = os.path.join(UPLOADS, os.path.basename(r["url"] or ""))
        if os.path.isfile(path):
            images.append(open(path, "rb").read())
            data.append(r)
    names = {r["category"]: r["category_name"] for r in data} | {r["industry"]: r["industry_name"] for r in data}
    print(f"Dữ liệu: {len(data)} ảnh của {len({r['product'] for r in data})} sản phẩm, "
          f"{len({r['category'] for r in data})} danh mục, {len({r['industry'] for r in data})} ngành hàng")
    t0 = time.time()
    emb = vision.embed(images)
    col = np.stack([vision.colour(b) for b in images])
    print(f"MobileNetV2 trích đặc trưng {len(images)} ảnh trong {time.time() - t0:.1f}s")
    groups = np.array([r["product"] for r in data])

    results = {}
    for task, title in (("industry", "Ngành hàng"), ("category", "Danh mục")):
        y = np.array([r[task] for r in data])
        # only classes with at least 2 products can be tested on a product the model never saw
        per_class = {}
        for label, g in zip(y, groups):
            per_class.setdefault(label, set()).add(g)
        ok = np.array([len(per_class[label]) >= 2 for label in y])
        idx = np.nonzero(ok)[0]
        folds = GroupKFold(n_splits=5).split(idx, y[idx], groups[idx])
        hits = {m: [0, 0] for m in ("colour", "nearest", "model")}
        per_industry = {}
        for tr_i, te_i in folds:
            tr, te = idx[tr_i], idx[te_i]
            clf = vision.fit(emb[tr], y[tr])
            proba = clf.predict_proba(emb[te])
            for k, j in enumerate(te):
                ranked = {
                    "colour": nearest_labels(col[tr], y[tr], col[j]),
                    "nearest": nearest_labels(emb[tr], y[tr], emb[j]),
                    "model": [clf.classes_[c] for c in np.argsort(-proba[k])[:3]],
                }
                for m, labels in ranked.items():
                    hits[m][0] += labels[0] == y[j]
                    hits[m][1] += y[j] in labels[:3]
                if task == "industry":
                    s = per_industry.setdefault(names[y[j]], [0, 0])
                    s[0] += 1
                    s[1] += ranked["model"][0] == y[j]
        n = len(idx)
        results[task] = {m: {"top1": round(h[0] / n, 3), "top3": round(h[1] / n, 3)} for m, h in hits.items()}
        results[task]["tested"] = n
        results[task]["classes"] = len({label for label in y[idx]})
        if task == "industry":
            results["perIndustry"] = {k: round(v[1] / v[0], 3) for k, v in sorted(per_industry.items(), key=lambda kv: -kv[1][0])}
            results["perIndustryCount"] = {k: v[0] for k, v in per_industry.items()}
        print(f"\n{title}: test trên {n} ảnh của sản phẩm chưa từng thấy ({results[task]['classes']} lớp, chia 5 phần theo sản phẩm)")
        print(f"  {'Cách đoán':36}{'Đúng top-1':>11}{'Đúng top-3':>11}")
        for m, label in (("colour", "So màu ảnh giống nhất (cách cũ)"), ("nearest", "MobileNet, ảnh giống nhất"),
                         ("model", "MobileNet + hồi quy logistic")):
            print(f"  {label:36}{results[task][m]['top1']:>11.1%}{results[task][m]['top3']:>11.1%}")
    print("\nĐúng top-1 theo ngành hàng (mô hình):")
    for k, v in results["perIndustry"].items():
        print(f"  {k:30}{results['perIndustryCount'][k]:>4} ảnh  {v:.0%}")

    # the model the platform uses: every photo
    vision.save(vision.fit(emb, [r["category"] for r in data]), vision.fit(emb, [r["industry"] for r in data]),
                {"images": len(data), "products": len(set(groups)), "categories": len({r["category"] for r in data}),
                 "industries": len({r["industry"] for r in data}), "metrics": {k: results[k] for k in ("industry", "category")}})
    chart_vision(results)
    return results


def nearest_labels(train_x, train_y, x, k=3):
    """Labels of the most similar training photos (cosine), without repeats: what an image search would answer."""
    order = np.argsort(-(train_x @ x))
    out = []
    for j in order:
        if train_y[j] not in out:
            out.append(train_y[j])
            if len(out) == k:
                break
    return out


def chart_vision(results):
    fig, ax = plt.subplots(1, 2, figsize=(14, 5), gridspec_kw={"width_ratios": [1, 1.3]})
    methods = [("colour", "So màu (cách cũ)", "#bbbbbb"), ("nearest", "MobileNet, ảnh giống nhất", "#f5a38f"), ("model", "MobileNet + logistic", "#ee4d2d")]
    x = np.arange(4)
    for k, (m, label, color) in enumerate(methods):
        vals = [results["industry"][m]["top1"], results["industry"][m]["top3"], results["category"][m]["top1"], results["category"][m]["top3"]]
        ax[0].bar(x + (k - 1) * 0.27, vals, 0.27, label=label, color=color)
    ax[0].set_xticks(x, ["Ngành hàng\ntop-1", "Ngành hàng\ntop-3", "Danh mục\ntop-1", "Danh mục\ntop-3"])
    ax[0].set_ylim(0, 1.05)
    ax[0].legend(fontsize=8, loc="upper right")
    ax[0].set_title("Tỉ lệ đoán đúng trên sản phẩm chưa từng thấy")
    per = results["perIndustry"]
    names = list(per)[::-1]
    ax[1].barh(names, [per[k] for k in names], color="#ee4d2d")
    ax[1].set_yticks(range(len(names)), [f"{k} ({results['perIndustryCount'][k]} ảnh)" for k in names], fontsize=8)
    ax[1].set_xlim(0, 1.05)
    ax[1].set_title("Đúng ngành hàng (top-1) theo từng ngành")
    fig.suptitle("Thị giác máy tính: đoán danh mục từ ảnh sản phẩm (MobileNetV2 + hồi quy logistic)")
    save(fig, "vision.png")


# the features in Vietnamese, for the console and the charts
LABELS = {
    "recencyDays": "Số ngày chưa mua", "recencyRatio": "Chưa mua / nhịp mua thường", "ordersTotal": "Tổng số đơn",
    "orders90d": "Số đơn 90 ngày", "spendTotal": "Tổng chi tiêu", "avgGapDays": "Nhịp mua (ngày/đơn)", "views30d": "Lượt xem 30 ngày",
    "carts30d": "Thêm giỏ 30 ngày", "likes30d": "Lượt thích 30 ngày", "logins30d": "Đăng nhập 30 ngày", "cancelRatio": "Tỉ lệ hủy đơn",
    "returns": "Số lần trả hàng", "badReviews": "Số đánh giá 1-2 sao", "avgRating": "Điểm đánh giá TB", "tenureDays": "Thâm niên (ngày)",
    "amount": "Giá trị đơn", "amountRatio": "Giá trị / thói quen", "accountAgeDays": "Tuổi tài khoản", "priorOrders": "Số đơn trước đó",
    "ordersLastHour": "Đơn trong 1 giờ", "ordersLastDay": "Đơn trong 24 giờ", "cod": "Thanh toán COD", "priorFailRatio": "Tỉ lệ bom hàng trước",
    "phonesLast7d": "Số SĐT trong 7 ngày", "phoneSharedAccounts": "SĐT dùng chung", "discountRatio": "Tỉ lệ giảm giá",
    "quantity": "Số lượng", "newDeviceLogin": "Thiết bị mới", "failedLogins24h": "Đăng nhập sai 24h", "nightHour": "Đặt lúc 0h-5h",
}


# ==================== output ====================

def save(fig, name):
    os.makedirs(REPORTS, exist_ok=True)
    fig.tight_layout()
    fig.savefig(os.path.join(REPORTS, name), dpi=130)
    plt.close(fig)
    print(f"→ biểu đồ: reports/{name}")


def write_summary(results):
    lines = [f"# Kết quả huấn luyện ({datetime.now():%d/%m/%Y %H:%M})", ""]
    r = results.get("reco", {})
    if r.get("info", {}).get("metrics"):
        m = r["info"]["metrics"]
        lines += ["## Gợi ý sản phẩm", "", f"{r['info']['users']} người mua, {r['info']['items']} sản phẩm, {len(r['products'])} ngành hàng. "
                  f"Test: {m['testUsers']} người mua.", "", "| Cách gợi ý | HitRate@10 | NDCG@10 | Độ phủ |", "|---|---|---|---|"]
        for name, k in (("Bán chạy nhất", "popular"), ("Cùng danh mục", "sameCategory"), ("**Mô hình**", "model")):
            lines.append(f"| {name} | {m[k]['hitRate']:.3f} | {m[k]['ndcg']:.3f} | {m[k]['coverage']:.1%} |")
        lines += ["", "| Ngành hàng | Người mua test | NDCG mô hình | NDCG luật |", "|---|---|---|---|"]
        for name, v in sorted(r["byIndustry"].items(), key=lambda kv: -kv[1]["n"]):
            lines.append(f"| {name} | {v['n']} | {v['model']:.3f} | {v['rule']:.3f} |")
        lines += ["", "![](reco.png)", ""]
    r = results.get("reviews", {})
    if r.get("scores"):
        lines += ["## Bộ phân loại đánh giá", "", f"Huấn luyện trên {r['info']['samples']} đánh giá ({r['info'].get('own') or 0} từ sàn). "
                  f"Test trên {r['test']} đánh giá viết tay.", "", "| Cách phân loại | F1 vấn đề | Đúng hết vấn đề | Đúng cảm xúc |", "|---|---|---|---|"]
        for name, v in r["scores"].items():
            lines.append(f"| {name} | {v['f1']:.3f} | {v['exact']:.1%} | {v['sentiment']:.1%} |")
        lines += ["", "![](reviews.png)", ""]
    v = results.get("vision")
    if v:
        lines += ["## Thị giác máy tính: đoán danh mục từ ảnh", "", "Test theo sản phẩm (5 phần, ảnh của một sản phẩm không nằm ở cả hai phía).", "",
                  "| Cách đoán | Ngành top-1 | Ngành top-3 | Danh mục top-1 | Danh mục top-3 |", "|---|---|---|---|---|"]
        for m, label in (("colour", "So màu (cách cũ)"), ("nearest", "MobileNet, ảnh giống nhất"), ("model", "**MobileNet + hồi quy logistic**")):
            lines.append(f"| {label} | {v['industry'][m]['top1']:.1%} | {v['industry'][m]['top3']:.1%} | {v['category'][m]['top1']:.1%} | {v['category'][m]['top3']:.1%} |")
        lines += ["", "![](vision.png)", ""]
    for key, title in (("churn", "Dự đoán rời bỏ"), ("fraud", "Phát hiện gian lận đơn")):
        info = results.get(key, {}).get("info")
        if info and info.get("metrics"):
            m = info["metrics"]
            lines += [f"## {title}", "", f"Thuật toán: {info['algorithm']}. {info['labelled']} trường hợp có nhãn ({info['positives']} có), "
                      f"test trên {info['testCases']} trường hợp mới nhất.", "", "| Cách chấm | ROC-AUC | PR-AUC | Precision top 10% | Recall top 10% |",
                      "|---|---|---|---|---|"]
            for k, name in (("rule", "Luật"), ("anomaly", "Isolation Forest"), ("logistic", "Hồi quy logistic"), ("model", "**Mô hình**")):
                if k in m:
                    lines.append(f"| {name} | {m[k]['rocAuc']:.3f} | {m[k]['prAuc']:.3f} | {m[k]['precisionTop']:.1%} | {m[k]['recallTop']:.1%} |")
            lines += ["", f"![]({key}.png)", ""]
    os.makedirs(REPORTS, exist_ok=True)
    with open(os.path.join(REPORTS, "summary.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("\n→ tóm tắt: reports/summary.md")


def reload_service():
    try:
        req = urllib.request.Request(AI_URL.rstrip("/") + "/reload", method="POST")
        with urllib.request.urlopen(req, timeout=10) as r:
            print(f"→ dịch vụ AI ({AI_URL}) đã nạp mô hình mới: {json.loads(r.read())}")
    except Exception:
        print(f"→ dịch vụ AI chưa chạy ({AI_URL}): lần sau khởi động nó sẽ tự dùng mô hình mới")


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8")  # Vietnamese in the Windows console
    wanted = [a for a in sys.argv[1:] if a in ("reco", "churn", "fraud", "reviews", "vision")] or ["reco", "churn", "fraud", "reviews", "vision"]
    results = {}
    with connect() as db:
        for name, fn in (("reco", train_reco), ("churn", train_churn), ("fraud", train_fraud), ("reviews", train_reviews), ("vision", train_vision)):
            if name in wanted:
                results[name] = fn(db)
    write_summary(results)
    reload_service()
