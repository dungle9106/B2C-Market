# Deploy the fine-tuned model: Hugging Face Inference Endpoint (paid, scale-to-zero)

This is the step that makes your fine-tuned Vistral-7B chatbot reachable
online 24/7 (with auto-sleep to keep cost low), so the deployed marketplace
site can call it just like it currently calls the OpenAI API.

## 1. Prerequisites

- A Hugging Face account (free to create) with a **payment method added**
  (Settings -> Billing) - Inference Endpoints are a paid feature, billed
  per-second while the endpoint is active.
- Your merged model already pushed to the Hub via `scripts/04_merge_and_push.py`
  (repo like `your-username/vistral-marketplace-merged`).

## 2. Create the endpoint

1. Go to https://ui.endpoints.huggingface.co/
2. Click **New Endpoint**
3. **Model repository**: select the merged model you pushed
   (`your-username/vistral-marketplace-merged`)
4. **Task**: Text Generation
5. **Instance type**: choose based on budget/quality:
   - `Nvidia A10G (24GB)` - ~$1.30/hour while active - recommended, comfortable headroom for a 7B model
   - `Nvidia T4 (16GB)` - ~$0.60/hour while active - cheaper, tighter fit, may need to reduce `max_new_tokens`
6. **Automatic scale-to-zero**: set idle timeout to **15 minutes** (Advanced
   configuration -> "Scale to zero after"). This is the setting that keeps
   your bill low - the endpoint fully shuts down GPU billing after 15 minutes
   of no requests, and wakes back up (cold start ~2-5 min) on the next call.
7. Click **Create Endpoint**

## 3. Get the endpoint URL and token

Once status shows "Running" (first boot takes a few minutes):

- Copy the **Endpoint URL** shown on the endpoint's page
  (looks like `https://xxxxxxxxxxxx.us-east-1.aws.endpoints.huggingface.cloud`)
- Create an **access token** at https://huggingface.co/settings/tokens (role: read is enough for calling it)

## 4. Test it directly first

```bash
curl https://xxxxxxxxxxxx.us-east-1.aws.endpoints.huggingface.cloud \
  -X POST \
  -H "Authorization: Bearer hf_xxxxxxxxxxxx" \
  -H "Content-Type: application/json" \
  -d '{"inputs": "<s>[INST] Ao thun nay gia bao nhieu? [/INST]", "parameters": {"max_new_tokens": 200}}'
```

If it's asleep (scaled to zero), the first call returns a 503 while it wakes
up - retry after ~2-5 minutes. **Important for demo day:** call it once
5-10 minutes before you present, so it's warm when the committee watches.

## 5. Wire it into the backend

In `backend/src/main/resources/application.properties`, set:

```properties
app.ai.provider=huggingface
app.ai.huggingface.endpoint-url=https://xxxxxxxxxxxx.us-east-1.aws.endpoints.huggingface.cloud
app.ai.huggingface.token=${HF_TOKEN:}
```

And set the `HF_TOKEN` environment variable on your deployed backend (Render/
Railway/Fly.io dashboard -> Environment Variables), the same way `OPENAI_API_KEY`
is already set. See `AIService.java` - it already supports switching between
`openai` and `huggingface` via `app.ai.provider`.

## 6. Cost monitoring

Check https://ui.endpoints.huggingface.co/ regularly during the project -
it shows running time and estimated cost. **Pause or delete the endpoint**
when you're not actively testing/demoing (button on the endpoint page) to
avoid accidental idle billing if scale-to-zero somehow doesn't trigger
(e.g., something is polling it in the background - check your frontend
isn't calling the chatbot on every page load).

## 7. Fallback plan

Keep `app.ai.provider=openai` as the default in your deployed environment
config until you've verified the HF endpoint works end-to-end in a test
session. Only switch the live/demo environment to `huggingface` once you've
confirmed: endpoint responds correctly, cold-start time is acceptable, and
cost is behaving as expected. This way a HF billing or quota hiccup right
before your defense can't take down the whole chatbot feature - you flip
`app.ai.provider` back to `openai` in one config change.
