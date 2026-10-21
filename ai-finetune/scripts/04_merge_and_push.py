"""
Merges the trained LoRA adapter into the base Vistral-7B-Chat weights and
pushes the resulting full model to your Hugging Face Hub account.

WHY MERGE: Hugging Face's standard Inference Endpoints run a model via TGI
(Text Generation Inference), which expects one self-contained set of model
weights. Merging avoids needing a custom inference container/handler - you
get a plain "text-generation" endpoint that speaks the standard TGI HTTP API,
which the Spring Boot backend can call directly (see AIService's "custom"
provider mode).

Trade-off: the merged model is a full ~14GB (7B params, fp16) upload/download
instead of a few-hundred-MB adapter. This is normal and expected for a 7B
model - it just means the push step takes a while on a typical connection.

Prerequisite: huggingface-cli login  (or set HF_TOKEN env var)
"""

import os

import torch
from huggingface_hub import create_repo
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

BASE_MODEL = "Viet-Mistral/Vistral-7B-Chat"
ADAPTER_DIR = os.path.join(os.path.dirname(__file__), "..", "vistral-marketplace-lora")
MERGED_DIR = os.path.join(os.path.dirname(__file__), "..", "vistral-marketplace-merged")

# change this to "<your-hf-username>/vistral-marketplace-merged"
HF_REPO_ID = os.environ.get("HF_REPO_ID", "your-username/vistral-marketplace-merged")


def main():
    if HF_REPO_ID.startswith("your-username"):
        raise SystemExit(
            "Set HF_REPO_ID first, e.g.:\n"
            "  export HF_REPO_ID=nguyenvana/vistral-marketplace-merged"
        )

    print(">> Loading base model in fp16 (full precision merge, not 4-bit - "
          "needs ~14GB VRAM or a high-RAM CPU runtime; on Colab use an A100 "
          "or high-RAM instance for this step)...")
    base_model = AutoModelForCausalLM.from_pretrained(BASE_MODEL, torch_dtype=torch.float16, device_map="auto")
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)

    print(">> Loading LoRA adapter and merging into base weights...")
    model = PeftModel.from_pretrained(base_model, ADAPTER_DIR)
    merged = model.merge_and_unload()

    os.makedirs(MERGED_DIR, exist_ok=True)
    merged.save_pretrained(MERGED_DIR, safe_serialization=True)
    tokenizer.save_pretrained(MERGED_DIR)
    print(f">> Merged model saved locally to {MERGED_DIR}")

    print(f">> Creating Hub repo {HF_REPO_ID} and uploading (this is a large "
          f"upload, ~14GB - may take a while)...")
    create_repo(HF_REPO_ID, exist_ok=True, repo_type="model")
    merged.push_to_hub(HF_REPO_ID)
    tokenizer.push_to_hub(HF_REPO_ID)

    print(f">> Done. Merged model available at https://huggingface.co/{HF_REPO_ID}")
    print(">> Next: follow ../DEPLOY_HF_ENDPOINT.md to create a paid, scale-to-zero Inference Endpoint.")


if __name__ == "__main__":
    main()
