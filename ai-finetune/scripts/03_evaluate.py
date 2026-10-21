"""
Compares the BASE Vistral-7B-Chat model against the FINE-TUNED (LoRA) version
on the held-out eval set, so you have concrete before/after evidence for the
thesis report (this is usually the single most convincing artifact when the
committee asks "how do you know fine-tuning actually helped?").

Produces:
  - data/eval_results.json  (raw responses from both models per question)
  - a printed summary table with a simple keyword-overlap score

This is NOT a rigorous NLP benchmark (that's out of scope for a 7-week
project) - it's a lightweight, explainable comparison: for each eval
question, does the fine-tuned model's answer share more relevant keywords
with the ground-truth answer than the base model's answer does? Explain this
limitation honestly if asked in the defense; a small qualitative comparison
table with real examples is usually more convincing to a committee than a
single opaque "accuracy" number anyway.

Run this on the same GPU environment used for training (Colab/Kaggle).
"""

import json
import os

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

BASE_MODEL = "Viet-Mistral/Vistral-7B-Chat"
ADAPTER_DIR = os.path.join(os.path.dirname(__file__), "..", "vistral-marketplace-lora")
DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
N_EVAL_SAMPLES = 20  # keep small - each generation takes a few seconds on a T4


def load_base_model():
    bnb_config = BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_compute_dtype=torch.bfloat16)
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
    model = AutoModelForCausalLM.from_pretrained(BASE_MODEL, quantization_config=bnb_config, device_map="auto")
    return model, tokenizer


def load_finetuned_model(base_model):
    return PeftModel.from_pretrained(base_model, ADAPTER_DIR)


def generate(model, tokenizer, system_prompt, user_message, max_new_tokens=200):
    messages = [{"role": "system", "content": system_prompt}, {"role": "user", "content": user_message}]
    prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
    with torch.no_grad():
        output = model.generate(**inputs, max_new_tokens=max_new_tokens, temperature=0.7, do_sample=True)
    text = tokenizer.decode(output[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True)
    return text.strip()


def keyword_overlap_score(answer, reference):
    """Crude but explainable: fraction of reference's significant words (>=3 chars)
    that also appear in the generated answer. Not a substitute for human judgment -
    use it as a rough signal alongside reading the actual transcripts."""
    ref_words = {w.lower() for w in reference.split() if len(w) >= 3}
    ans_words = {w.lower() for w in answer.split() if len(w) >= 3}
    if not ref_words:
        return 0.0
    return len(ref_words & ans_words) / len(ref_words)


def main():
    print(">> Loading eval set...")
    eval_examples = []
    with open(os.path.join(DATA_DIR, "eval.jsonl"), encoding="utf-8") as f:
        for line in f:
            eval_examples.append(json.loads(line))
    eval_examples = eval_examples[:N_EVAL_SAMPLES]

    print(">> Loading base model...")
    base_model, tokenizer = load_base_model()

    print(">> Loading fine-tuned (LoRA) model...")
    ft_model = load_finetuned_model(base_model)

    results = []
    base_scores, ft_scores = [], []

    for i, ex in enumerate(eval_examples):
        system_msg = ex["messages"][0]["content"]
        user_msg = ex["messages"][1]["content"]
        reference = ex["messages"][2]["content"]

        print(f">> [{i+1}/{len(eval_examples)}] {user_msg}")
        base_answer = generate(base_model, tokenizer, system_msg, user_msg)
        ft_answer = generate(ft_model, tokenizer, system_msg, user_msg)

        base_score = keyword_overlap_score(base_answer, reference)
        ft_score = keyword_overlap_score(ft_answer, reference)
        base_scores.append(base_score)
        ft_scores.append(ft_score)

        results.append({
            "question": user_msg,
            "reference_answer": reference,
            "base_model_answer": base_answer,
            "finetuned_model_answer": ft_answer,
            "base_score": round(base_score, 3),
            "finetuned_score": round(ft_score, 3),
        })

    with open(os.path.join(DATA_DIR, "eval_results.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    avg_base = sum(base_scores) / len(base_scores)
    avg_ft = sum(ft_scores) / len(ft_scores)

    print("\n" + "=" * 60)
    print(f"Average keyword-overlap score - BASE model:       {avg_base:.3f}")
    print(f"Average keyword-overlap score - FINE-TUNED model: {avg_ft:.3f}")
    print(f"Improvement: {(avg_ft - avg_base):+.3f}")
    print("=" * 60)
    print(f"Full transcripts saved to data/eval_results.json - pick 3-5 clear")
    print(f"before/after examples from there for the report and slide deck.")


if __name__ == "__main__":
    main()
