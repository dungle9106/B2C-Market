"""Review AI service (FastAPI): classifies marketplace reviews for the Shoppi review hub.

  GET  /health    status, whether a trained model is loaded
  POST /analyze   {items: [{id, rating, content}]} -> sentiment, issues, alarming words per review
  POST /similar   {items: [{id, content}], threshold?} -> pairs of near-identical reviews (spam / copied reviews)
  POST /train     {items: [{content, rating, issues, sentiment?}]} -> trains the classifier, returns its metrics

Run:  uvicorn app.main:app --port 8000      (the backend calls it when REVIEW_AI_URL=http://localhost:8000)
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from . import lexicon, model

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
