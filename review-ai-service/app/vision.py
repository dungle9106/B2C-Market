"""Computer vision: which category a product photo belongs to ("Gợi ý danh mục từ ảnh").

  backbone     MobileNetV2 trained on ImageNet (ONNX Model Zoo, ~14 MB, run with onnxruntime on the CPU). Its last
               layer before ImageNet's 1000 classes gives 1280 numbers describing the photo: shapes, textures, parts.
  our part     a logistic regression trained on the marketplace's own product photos: those 1280 numbers -> category
               (leaf: "Tai nghe & Loa") and industry (top level: "Điện tử"). This is transfer learning: the network
               already knows how to see; only the last, small step is learned from Shoppi's photos.
  baseline     what the image search does without AI (backend ImageFeatures): a colour histogram, nearest photo.

Train with train_from_db.py vision (reads the photos of every product from the database and the uploads folder).
"""
import io
import os
import threading
import urllib.request
from datetime import datetime, timezone

import joblib
import numpy as np
from PIL import Image

HERE = os.path.dirname(__file__)
MODELS = os.path.join(HERE, "..", "models")
BACKBONE_PATH = os.environ.get("VISION_BACKBONE_PATH", os.path.join(MODELS, "mobilenetv2-12.onnx"))
BACKBONE_URL = "https://github.com/onnx/models/raw/main/validated/vision/classification/mobilenet/model/mobilenetv2-12.onnx"
EMBEDDING = "472"  # the 1280-number output before the ImageNet classifier
MODEL_PATH = os.environ.get("VISION_MODEL_PATH", os.path.join(MODELS, "vision.joblib"))
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

_lock = threading.Lock()
_session = None
_model = None
_loaded = False


# ==================== seeing ====================

def _backbone():
    """MobileNetV2 with its feature layer exposed as an output (downloaded once)."""
    global _session
    with _lock:
        if _session is None:
            import onnx
            import onnxruntime as ort
            if not os.path.exists(BACKBONE_PATH):
                os.makedirs(os.path.dirname(BACKBONE_PATH), exist_ok=True)
                urllib.request.urlretrieve(BACKBONE_URL, BACKBONE_PATH)
            graph = onnx.load(BACKBONE_PATH)
            graph.graph.output.append(onnx.helper.make_tensor_value_info(EMBEDDING, onnx.TensorProto.FLOAT, None))
            options = ort.SessionOptions()
            options.log_severity_level = 3
            _session = ort.InferenceSession(graph.SerializeToString(), options, providers=["CPUExecutionProvider"])
        return _session


def _open(data: bytes) -> Image.Image:
    img = Image.open(io.BytesIO(data))
    if img.mode in ("RGBA", "LA", "P"):
        img = img.convert("RGBA")
        white = Image.new("RGBA", img.size, (255, 255, 255, 255))
        img = Image.alpha_composite(white, img)
    return img.convert("RGB")


def _prepare(img: Image.Image) -> np.ndarray:
    # as the network was trained: shorter side 256, centre 224 x 224, ImageNet colour normalisation
    w, h = img.size
    s = 256 / min(w, h)
    img = img.resize((max(224, round(w * s)), max(224, round(h * s))), Image.BILINEAR)
    w, h = img.size
    left, top = (w - 224) // 2, (h - 224) // 2
    x = np.asarray(img.crop((left, top, left + 224, top + 224)), dtype=np.float32) / 255.0
    return ((x - MEAN) / STD).transpose(2, 0, 1)


def embed(images: list[bytes], batch: int = 8) -> np.ndarray:
    """1280 numbers per photo, scaled to length 1 (so cosine similarity is a dot product)."""
    session = _backbone()
    out = []
    for k in range(0, len(images), batch):
        x = np.stack([_prepare(_open(b)) for b in images[k:k + batch]])
        feats = session.run([EMBEDDING], {"input": x})[0].reshape(len(x), -1)
        out.append(feats / np.maximum(np.linalg.norm(feats, axis=1, keepdims=True), 1e-9))
    return np.vstack(out) if out else np.zeros((0, 1280), dtype=np.float32)


def colour(data: bytes) -> np.ndarray:
    """The baseline: 4 x 4 x 4 RGB histogram without the near-white background (like backend ImageFeatures)."""
    x = np.asarray(_open(data).resize((64, 64)), dtype=np.int32).reshape(-1, 3)
    keep = x[(x < 235).any(axis=1)]
    if len(keep) < 200:
        keep = x
    bins = (keep // 64) @ np.array([16, 4, 1])
    h = np.bincount(bins, minlength=64).astype(float)
    return h / max(np.linalg.norm(h), 1e-9)


# ==================== the learned part ====================

def fit(x: np.ndarray, labels: list):
    from sklearn.linear_model import LogisticRegression
    return LogisticRegression(max_iter=3000, C=2.0, class_weight="balanced").fit(x, labels)


def save(category_clf, industry_clf, info: dict):
    model = {"category": category_clf, "industry": industry_clf, **info,
             "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds")}
    os.makedirs(os.path.dirname(os.path.abspath(MODEL_PATH)), exist_ok=True)
    joblib.dump(model, MODEL_PATH)
    global _model, _loaded
    with _lock:
        _model, _loaded = model, True


def reload():
    global _model, _loaded
    with _lock:
        _model, _loaded = None, False


def load():
    global _model, _loaded
    with _lock:
        if not _loaded:
            _model = joblib.load(MODEL_PATH) if os.path.exists(MODEL_PATH) else None
            _loaded = True
        return _model


def info() -> dict:
    m = load()
    if not m:
        return {"trained": False}
    return {"trained": True, **{k: m.get(k) for k in ("images", "products", "categories", "industries", "metrics", "trainedAt")}}


def predict(data: bytes, k: int = 3, x: np.ndarray | None = None) -> dict:
    """The k most likely categories (ids) and industries of one photo."""
    m = load()
    if not m:
        return {"trained": False, "categories": [], "industries": []}
    x = embed([data]) if x is None else x.reshape(1, -1)

    def top(clf):
        p = clf.predict_proba(x)[0]
        order = np.argsort(-p)[:k]
        return [{"id": int(clf.classes_[j]), "probability": round(float(p[j]), 4)} for j in order]

    return {"trained": True, "categories": top(m["category"]), "industries": top(m["industry"])}
