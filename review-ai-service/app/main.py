"""Review AI service (FastAPI): classifies marketplace reviews for the Shoppi review hub.

  GET  /health    status, whether a trained model is loaded
  POST /analyze   {items: [{id, rating, content}]} -> sentiment, issues, alarming words per review
  POST /similar   {items: [{id, content}], threshold?} -> pairs of near-identical reviews (spam / copied reviews)
  POST /train     {items: [{content, rating, issues, sentiment?}]} -> trains the classifier, returns its metrics
  GET  /return-risk/status   whether the return abuse models are trained
  POST /return-risk/train    {items: [{features: {name: number}, label?}]} -> trains them on the case history
  POST /return-risk/score    {features: {name: number}} -> how unusual the case is, probability of abuse
  GET  /reco/status          whether the recommendation model is trained, its test metrics
  POST /reco/train           {interactions: [{user, item, weight, ts}], items: [{id, category, parent}]} -> trains it
  POST /reco/recommend       {history: [{item, weight}], exclude?, k?} -> products for one buyer
  POST /reco/similar         {item, k?} -> products interesting to the same people
  GET  /risk/{churn|fraud}/status   whether the churn / order fraud model is trained, its test metrics
  POST /risk/{churn|fraud}/train    {rows: [{features, label, rule, ts}]} -> trains it on the history
  POST /risk/{churn|fraud}/score    {items: [{id, features}]} -> probability, main factors (+ how unusual, fraud)
  GET  /vision/status        whether the photo -> category model is trained, its test scores
  POST /vision/category      {image: base64} -> the most likely categories and industries of a product photo
  POST /vision/embed         {image: base64} -> the photo's MobileNetV2 vector (image search) and likely categories
  POST /reload               reads the model files again (after training with train_from_db.py)

Run:  uvicorn app.main:app --port 8000      (the backend calls it when REVIEW_AI_URL=http://localhost:8000)
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from . import lexicon, model, recommender, return_risk, tabular, vision

app = FastAPI(title="Shoppi Review AI", version="1.0.0")


class Review(BaseModel):
    id: int | None = None
    rating: int = Field(ge=1, le=5)
    content: str | None = ""


class AnalyzeRequest(BaseModel):
    items: list[Review] = Field(max_length=2000)


class TrainItem(BaseModel):
    content: str | None = ""
    rating: int = Field(ge=1, le=5)
    issues: list[str] = []
    sentiment: str | None = None


class TrainRequest(BaseModel):
    items: list[TrainItem] = Field(max_length=50000)


class SimilarItem(BaseModel):
    id: int
    content: str | None = ""


class SimilarRequest(BaseModel):
    items: list[SimilarItem] = Field(max_length=5000)
    threshold: float = Field(default=0.85, ge=0.5, le=1.0)


class RiskScoreRequest(BaseModel):
    features: dict[str, float]


class RiskTrainItem(BaseModel):
    features: dict[str, float]
    label: str | None = None


class RiskTrainRequest(BaseModel):
    items: list[RiskTrainItem] = Field(max_length=50000)


@app.get("/return-risk/status")
def return_risk_status():
    return return_risk.info()


@app.post("/return-risk/score")
def return_risk_score(req: RiskScoreRequest):
    return return_risk.score(req.features)


@app.post("/return-risk/train")
def return_risk_train(req: RiskTrainRequest):
    try:
        return return_risk.train([i.model_dump() for i in req.items])
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/health")
def health():
    return {"status": "UP", "engine": "model+lexicon" if model.load() else "lexicon", "model": model.info()}


@app.post("/analyze")
def analyze(req: AnalyzeRequest):
    items = [r.model_dump() for r in req.items]
    learned = model.predict(items) if items else None
    results = []
    for k, r in enumerate(items):
        base = lexicon.analyze(r["rating"], r["content"] or "")
        issues = list(base["issues"])
        sentiment = base["sentiment"]
        if learned is not None:
            m = learned[k]
            # the model's labels, plus the lexicon's high-precision ones it may have missed
            issues = list(dict.fromkeys(m["issues"] + [i for i in issues if i != "OTHER"]))
            if m["sentiment"] and (base["polarity"] == 0 or r["rating"] == 3):
                sentiment = m["sentiment"]
            if sentiment == "POSITIVE" and r["rating"] >= 4 and not base["severe"]:
                issues = []
            if not issues and r["rating"] <= 2:
                issues = ["OTHER"]
        results.append({"id": r["id"], "sentiment": sentiment, "score": base["score"], "issues": issues, "severe": base["severe"],
                        "engine": "model" if learned is not None else "lexicon"})
    return {"results": results}


@app.post("/similar")
def similar(req: SimilarRequest):
    items = [i for i in req.items if len(lexicon.normalize(i.content).split()) >= 6]
    if len(items) < 2:
        return {"pairs": []}
    x = TfidfVectorizer(ngram_range=(1, 3)).fit_transform([lexicon.normalize(i.content) for i in items])
    sim = cosine_similarity(x)
    pairs = []
    for a in range(len(items)):
        for b in range(a + 1, len(items)):
            if sim[a, b] >= req.threshold:
                pairs.append({"a": items[a].id, "b": items[b].id, "similarity": round(float(sim[a, b]), 3)})
    return {"pairs": pairs}


@app.post("/train")
def train(req: TrainRequest):
    try:
        return model.train([i.model_dump() for i in req.items])
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


class RecoInteraction(BaseModel):
    user: str
    item: int
    weight: float = 1.0
    ts: float = 0


class RecoItem(BaseModel):
    id: int
    category: int | None = None
    parent: int | None = None


class RecoTrainRequest(BaseModel):
    interactions: list[RecoInteraction] = Field(max_length=2_000_000)
    items: list[RecoItem] = Field(max_length=500_000)


class RecoHistory(BaseModel):
    item: int
    weight: float = 1.0


class RecoRequest(BaseModel):
    history: list[RecoHistory] = Field(max_length=500)
    exclude: list[int] = Field(default=[], max_length=2000)
    k: int = Field(default=12, ge=1, le=100)


class SimilarProductRequest(BaseModel):
    item: int
    k: int = Field(default=12, ge=1, le=100)


@app.get("/reco/status")
def reco_status():
    return recommender.info()


@app.post("/reco/train")
def reco_train(req: RecoTrainRequest):
    try:
        return recommender.train([i.model_dump() for i in req.interactions], [i.model_dump() for i in req.items])
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/reco/recommend")
def reco_recommend(req: RecoRequest):
    return recommender.recommend([h.model_dump() for h in req.history], req.exclude, req.k)


@app.post("/reco/similar")
def reco_similar(req: SimilarProductRequest):
    return recommender.similar(req.item, req.k)


# ==================== churn & fraud (app/tabular.py) ====================

class RiskRow(BaseModel):
    features: dict[str, float]
    label: int | None = None
    rule: float = 0
    ts: float = 0


class RiskTrain(BaseModel):
    rows: list[RiskRow] = Field(max_length=500_000)


class RiskCase(BaseModel):
    id: int | None = None
    features: dict[str, float]


class RiskScore(BaseModel):
    items: list[RiskCase] = Field(max_length=100_000)


RISK_MODELS = {"churn": tabular.churn, "fraud": tabular.fraud}


def _risk(kind: str):
    if kind not in RISK_MODELS:
        raise HTTPException(status_code=404, detail="không có mô hình " + kind)
    return RISK_MODELS[kind]


@app.get("/risk/{kind}/status")
def risk_status(kind: str):
    return _risk(kind).info()


@app.post("/risk/{kind}/train")
def risk_train(kind: str, req: RiskTrain):
    try:
        return _risk(kind).train([r.model_dump() for r in req.rows])
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/risk/{kind}/score")
def risk_score(kind: str, req: RiskScore):
    return _risk(kind).score([i.model_dump() for i in req.items])


@app.post("/reload")
def reload_models():
    """train_from_db.py trains in its own process and saves the files: this makes the running service use them."""
    recommender.reload()
    model.reload()
    vision.reload()
    for m in RISK_MODELS.values():
        m.reload()
    return {"vision": vision.info().get("trained"), "reviews": model.info().get("trained"), "reco": recommender.info().get("trained"), "churn": tabular.churn.info().get("trained"), "fraud": tabular.fraud.info().get("trained")}


# ==================== computer vision (app/vision.py) ====================

class PhotoRequest(BaseModel):
    image: str = Field(max_length=15_000_000)  # base64 of a jpg / png / webp
    k: int = Field(default=3, ge=1, le=10)


@app.get("/vision/status")
def vision_status():
    return vision.info()


@app.post("/vision/category")
def vision_category(req: PhotoRequest):
    import base64
    try:
        data = base64.b64decode(req.image.split(",")[-1])
        return vision.predict(data, req.k)
    except Exception as e:
        raise HTTPException(status_code=400, detail="không đọc được ảnh: " + str(e)[:200])


class EmbedRequest(BaseModel):
    image: str = Field(max_length=15_000_000)
    categories: int = Field(default=0, ge=0, le=50)  # also the k most likely categories (needs the trained model)


@app.post("/vision/embed")
def vision_embed(req: EmbedRequest):
    """For image search: the photo's 1280 MobileNetV2 numbers (base64 float32, length 1) and, optionally,
    the categories it most likely belongs to."""
    import base64
    try:
        data = base64.b64decode(req.image.split(",")[-1])
        vec = vision.embed([data])[0].astype("<f4")
    except Exception as e:
        raise HTTPException(status_code=400, detail="không đọc được ảnh: " + str(e)[:200])
    out = {"embedding": base64.b64encode(vec.tobytes()).decode(), "dim": int(vec.shape[0]), "categories": []}
    if req.categories:
        out["categories"] = vision.predict(data, req.categories, vec).get("categories", [])
    return out
