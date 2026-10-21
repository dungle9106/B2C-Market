# Fine-tuning Vistral-7B-Chat cho tro ly mua sam

Day la phan "tu train AI" cua do an: fine-tune model Vistral-7B-Chat (model
tieng Viet mo nguon, dua tren Mistral-7B) bang ky thuat LoRA/QLoRA de model
tra loi tot hon ve catalog san pham cua san.

## Vi sao lam theo huong nay (doc truoc khi bat dau)

- **Khong train tu dau.** Train mot LLM 7B tu con so 0 can hang ty token du
  lieu va cum GPU - khong kha thi cho do an. Thay vao do, ta lay mot model da
  duoc huan luyen san (Vistral-7B, da gioi tieng Viet) roi **fine-tune** them
  mot lop nho (LoRA adapter) de no chuyen ve nghiep vu tu van san pham.
- **LoRA/QLoRA** chi train ~1-2% tong so tham so cua model (adapter nho vai
  tram MB) thay vi toan bo 7 ty tham so - day la ky thuat giup fine-tune kha
  thi tren GPU mien phi (Colab/Kaggle) thay vi can data center.
- **So sanh voi chatbot dung API (da co san trong project):** chatbot API
  (GPT-4o-mini + RAG) van la lua chon **on dinh nhat cho ban deploy chinh
  thuc**. Phan fine-tune nay la **minh chung ky thuat bo sung** cho thay
  nhom co the tu huan luyen model that su - dung `app.ai.provider` de chuyen
  qua lai giua 2 huong ma khong doi code.

## Quy trinh (chay tren Google Colab hoac Kaggle - CAN GPU)

```
scripts/
├── 01_generate_dataset.py    Tao du lieu train tu DB that + sinh them bang OpenAI (tuy chon)
├── 02_finetune_vistral.py    Fine-tune Vistral-7B bang QLoRA (4-bit, chay duoc tren T4 free)
├── 03_evaluate.py            So sanh cau tra loi truoc/sau fine-tune (bang chung cho bao cao)
└── 04_merge_and_push.py      Gop LoRA vao model goc, day len Hugging Face Hub
```

Sau khi chay xong 4 buoc tren, lam theo `DEPLOY_HF_ENDPOINT.md` de dua model
len chay online (co tra phi, xem chi tiet chi phi trong file do).

### Buoc 0: Cai dat (tren Colab)

```python
!pip install -r requirements.txt
```

### Buoc 1: Sinh du lieu huan luyen

```bash
export MARKETPLACE_DB_DSN="postgresql://user:pass@host:5432/marketplace"
export OPENAI_API_KEY="sk-..."   # tuy chon, de sinh them du lieu da dang
python scripts/01_generate_dataset.py
```

Output: `data/train.jsonl` va `data/eval.jsonl`.

**Luu y quan trong:** ket qua fine-tune chi tot khi database co **du san
pham that** (it nhat vai chuc san pham voi mo ta day du). Neu database con
trong hoac it du lieu, hay dang vai chuc san pham mau truoc (qua giao dien
seller cua chinh project) roi moi chay buoc nay.

### Buoc 2: Fine-tune

```bash
python scripts/02_finetune_vistral.py
```

Mat khoang 30-90 phut tren GPU T4 (Colab free tier), tuy vao so luong du
lieu. Ket qua: adapter LoRA luu tai `vistral-marketplace-lora/`.

### Buoc 3: Danh gia (bang chung cho bao cao)

```bash
python scripts/03_evaluate.py
```

Xem ket qua trong `data/eval_results.json` - chon 3-5 vi du ro rang (cau tra
loi truoc vs sau fine-tune) de dua vao slide bao ve.

### Buoc 4: Merge va day len Hugging Face Hub

```bash
huggingface-cli login   # dan token co quyen "write" tu huggingface.co/settings/tokens
export HF_REPO_ID="ten-cua-ban/vistral-marketplace-merged"
python scripts/04_merge_and_push.py
```

### Buoc 5: Deploy online (co phi)

Xem `DEPLOY_HF_ENDPOINT.md` - phan quan trong nhat can doc ky truoc khi tao
endpoint that su (co lien quan den chi phi thuc te).

## Viet gi vao bao cao / noi gi khi bao ve

- **Kien truc:** Base model (Vistral-7B-Chat, mo nguon) + LoRA fine-tuning
  tren dataset rieng cua san (ket hop du lieu that tu catalog + sinh them
  bang LLM de tang da dang cau hoi).
- **Ky thuat:** QLoRA (4-bit quantization + LoRA) - giai thich duoc vi sao
  chon ky thuat nay (kha thi tren GPU mien phi, chi train ~1-2% tham so).
- **Danh gia:** so sanh truoc/sau fine-tune bang keyword-overlap score +
  vi du cu the - trung thuc ve gioi han cua phuong phap danh gia nay (khong
  phai benchmark hoc thuat chinh thong, la cach danh gia don gian, de giai
  thich, phu hop pham vi do an).
- **Trien khai:** Hugging Face Inference Endpoint voi scale-to-zero - giai
  thich duoc tai sao chon scale-to-zero (kiem soat chi phi) va danh doi
  (do tre khi model "thuc day" sau khi ngu).
- **So sanh trung thuc voi phuong an goi API:** neu duoc hoi "vay sao khong
  dung ca 2", tra loi: chatbot API (GPT-4o-mini) on dinh hon, khong ton phi
  ha tang, phu hop lam tinh nang chinh thuc; model tu train la minh chung
  nhom co kha nang tu fine-tune mot LLM thuc su, co the dung song song hoac
  thay the tuy ngu canh (kien truc pluggable qua `app.ai.provider`).
