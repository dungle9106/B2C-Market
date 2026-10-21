"""
LoRA fine-tuning of Vistral-7B-Chat on the marketplace Q&A dataset.

RUN THIS ON GOOGLE COLAB OR KAGGLE WITH A GPU RUNTIME (T4 16GB minimum,
A100 recommended if available). It will NOT run on a normal laptop CPU in
any reasonable time, and a 7B model needs >12GB VRAM even in 4-bit.

Colab setup:
  1. Runtime -> Change runtime type -> GPU (T4 or better)
  2. !pip install -r requirements.txt
  3. Upload data/train.jsonl and data/eval.jsonl (from 01_generate_dataset.py)
  4. Run this script: !python 02_finetune_vistral.py

Uses 4-bit quantization (QLoRA) so it fits on a free-tier T4 GPU (16GB VRAM).
Only trains a small LoRA adapter (a few % of total params) instead of the
full 7B weights - this is what makes fine-tuning feasible without a data
center GPU cluster.
"""

import json
import os

import torch
from datasets import load_dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
)
from trl import SFTTrainer, SFTConfig

BASE_MODEL = "Viet-Mistral/Vistral-7B-Chat"
DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "..", "vistral-marketplace-lora")

# ---- 1. Load dataset ----
print(">> Loading dataset...")
dataset = load_dataset("json", data_files={
    "train": os.path.join(DATA_DIR, "train.jsonl"),
    "eval": os.path.join(DATA_DIR, "eval.jsonl"),
})


def format_chat(example):
    """Vistral uses the Mistral chat template; the tokenizer applies it for us."""
    return {"text": tokenizer.apply_chat_template(example["messages"], tokenize=False)}


# ---- 2. Load base model in 4-bit (QLoRA) ----
print(">> Loading base model in 4-bit (this downloads ~4GB, may take a few minutes)...")

bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_compute_dtype=torch.bfloat16,
    bnb_4bit_use_double_quant=True,
)

tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
if tokenizer.pad_token is None:
    tokenizer.pad_token = tokenizer.eos_token

model = AutoModelForCausalLM.from_pretrained(
    BASE_MODEL,
    quantization_config=bnb_config,
    device_map="auto",
)
model = prepare_model_for_kbit_training(model)

dataset = dataset.map(format_chat)

# ---- 3. Attach LoRA adapter ----
# rank 16 / alpha 32 is a standard, GPU-friendly starting point for a
# small-to-medium sized fine-tuning dataset like ours.
lora_config = LoraConfig(
    r=16,
    lora_alpha=32,
    lora_dropout=0.05,
    bias="none",
    task_type="CAUSAL_LM",
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
)
model = get_peft_model(model, lora_config)
model.print_trainable_parameters()  # sanity check: should show ~1-2% of total params

# ---- 4. Train ----
sft_config = SFTConfig(
    output_dir=OUTPUT_DIR,
    num_train_epochs=3,
    per_device_train_batch_size=2,
    gradient_accumulation_steps=4,
    learning_rate=2e-4,
    logging_steps=10,
    eval_strategy="epoch",
    save_strategy="epoch",
    bf16=True,
    max_seq_length=1024,
    dataset_text_field="text",
    report_to="none",
)

trainer = SFTTrainer(
    model=model,
    args=sft_config,
    train_dataset=dataset["train"],
    eval_dataset=dataset["eval"],
)

print(">> Starting training...")
trainer.train()

# ---- 5. Save the LoRA adapter (a few hundred MB, NOT the full 7B model) ----
trainer.save_model(OUTPUT_DIR)
tokenizer.save_pretrained(OUTPUT_DIR)
print(f">> Done. LoRA adapter saved to {OUTPUT_DIR}")
print(">> Next: push this adapter to the Hugging Face Hub with 03_push_to_hub.py,")
print("   then deploy it behind an Inference Endpoint with 04_deploy_hf_endpoint.md")
