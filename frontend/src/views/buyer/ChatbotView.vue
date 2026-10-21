<template>
  <div>
    <AppHeader />
    <div class="container">
      <div class="shopee-section chat-shell">
        <div class="chat-title">
          <span>✨ Trợ Lý AI Tư Vấn Mua Sắm</span>
          <button v-if="messages.length > 1" type="button" class="clear" :disabled="loading" @click="onClear">Xóa cuộc trò chuyện</button>
        </div>

        <div class="messages" ref="messagesEl">
          <template v-for="(m, idx) in messages" :key="idx">
            <div :class="['bubble', m.role]">{{ m.text }}</div>

            <div v-if="m.products?.length" class="cards">
              <router-link v-for="p in m.products" :key="p.id" :to="`/products/${p.id}`" class="p-card">
                <img :src="p.thumbnailUrl || placeholder" alt="" />
                <div class="p-name">{{ p.name }}</div>
                <div class="p-price">{{ formatVnd(p.price) }}</div>
                <div class="p-meta">
                  <span v-if="p.ratingAvg">★ {{ p.ratingAvg.toFixed(1) }}</span>
                  <span>Đã bán {{ formatCount(p.soldCount) }}</span>
                </div>
              </router-link>
            </div>

            <div v-if="m.orders?.length" class="order-links">
              <router-link v-for="o in m.orders" :key="o.id" :to="`/orders/${o.id}`" class="order-chip">
                📦 {{ o.code }} · {{ statusLabel(o.status) }}
              </router-link>
            </div>

            <div v-if="m.suggestSupport" class="escalate">
              Cần người hỗ trợ trực tiếp?
              <router-link to="/messages">Chat với Shop</router-link>
              <span>hoặc</span>
              <router-link to="/help/new">Gửi yêu cầu cho Shoppi</router-link>
            </div>
          </template>
          <div v-if="loading" class="bubble ai typing"><span></span><span></span><span></span></div>
        </div>

        <div v-if="!loading" class="suggestions">
          <button v-for="s in suggestions" :key="s" type="button" @click="send(s)">{{ s }}</button>
        </div>
        <form class="input-row" @submit.prevent="send(input)">
          <input v-model="input" maxlength="1000" placeholder="Hỏi về sản phẩm, đơn hàng, chính sách đổi trả..." />
          <button class="btn" type="submit" :disabled="loading || !input.trim()">Gửi</button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import { chatWithAI, chatbotHistory, clearChatbotHistory } from '../../api/ai'
import { formatVnd, formatCount } from '../../utils/format'
import { statusLabel } from '../../utils/orderStatus'

const GREETING = { role: 'ai', text: 'Xin chào! Tôi là trợ lý mua sắm của Shoppi. Tôi có thể gợi ý sản phẩm, kiểm tra đơn hàng hoặc giải đáp chính sách đổi trả, vận chuyển, thanh toán. Bạn cần gì hôm nay?' }
const suggestions = ['Gợi ý sản phẩm bán chạy dưới 200k', 'Đơn hàng của tôi đang ở đâu?', 'Chính sách trả hàng hoàn tiền?', 'Phí vận chuyển tính thế nào?']
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120"><rect width="120" height="120" fill="%23f0f0f0"/></svg>'

const messages = ref([GREETING])
const input = ref('')
const loading = ref(false)
const messagesEl = ref(null)

async function send(raw) {
  const text = raw.trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', text })
  input.value = ''
  loading.value = true
  await scrollDown()

  try {
    const { data } = await chatWithAI(text)
    messages.value.push({ role: 'ai', text: data.reply, products: data.products, orders: data.orders, suggestSupport: data.suggestSupport })
  } catch (e) {
    messages.value.push({ role: 'ai', text: 'Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại.', suggestSupport: true })
  } finally {
    loading.value = false
    await scrollDown()
  }
}

async function onClear() {
  if (!confirm('Xóa toàn bộ lịch sử trò chuyện với Trợ lý AI?')) return
  await clearChatbotHistory()
  messages.value = [GREETING]
}

async function scrollDown() {
  await nextTick()
  if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
}

// earlier turns (text only) so the conversation picks up where it left off
onMounted(async () => {
  try {
    const { data } = await chatbotHistory()
    messages.value = [GREETING, ...data.flatMap(t => [{ role: 'user', text: t.message }, { role: 'ai', text: t.reply }])]
    scrollDown()
  } catch {
    // no history is fine
  }
})
</script>

<style scoped>
.chat-shell { padding: 0; display: flex; flex-direction: column; height: min(640px, calc(100vh - 160px)); min-height: 440px; overflow: hidden; }
.chat-title { padding: 14px 16px; font-weight: 600; border-bottom: 1px solid var(--color-border); color: var(--color-primary); display: flex; justify-content: space-between; align-items: center; }
.clear { background: none; border: none; color: var(--color-text-faint); font-size: 13px; cursor: pointer; }
.clear:hover { color: var(--color-primary); }
.messages { flex: 1; overflow-y: auto; padding: 16px; background: #fafafa; }
.bubble { max-width: 75%; width: fit-content; padding: 10px 14px; border-radius: 14px; margin-bottom: 10px; font-size: 14px; line-height: 1.55; white-space: pre-line; }
.bubble.user { background: var(--color-primary); color: white; margin-left: auto; border-bottom-right-radius: 2px; }
.bubble.ai { background: white; box-shadow: var(--shadow-card); border-bottom-left-radius: 2px; }

.typing { display: flex; gap: 4px; }
.typing span { width: 7px; height: 7px; border-radius: 50%; background: #bbb; animation: blink 1.2s infinite; }
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.3; } 40% { opacity: 1; } }

.cards { display: flex; gap: 10px; overflow-x: auto; padding: 2px 2px 10px; margin-bottom: 4px; }
.p-card { flex: 0 0 150px; background: white; border-radius: 6px; box-shadow: var(--shadow-card); overflow: hidden; padding-bottom: 8px; transition: transform 0.15s; }
.p-card:hover { transform: translateY(-2px); }
.p-card img { width: 100%; aspect-ratio: 1; object-fit: cover; display: block; }
.p-name { font-size: 12px; line-height: 1.35; padding: 6px 8px 0; height: 38px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.p-price { color: var(--color-primary); font-weight: 600; font-size: 14px; padding: 4px 8px 0; }
.p-meta { display: flex; justify-content: space-between; font-size: 11px; color: var(--color-text-faint); padding: 2px 8px 0; }

.order-links { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.order-chip { background: white; border: 1px solid var(--color-border); border-radius: 999px; padding: 5px 12px; font-size: 13px; }
.order-chip:hover { border-color: var(--color-primary); color: var(--color-primary); }

.escalate { font-size: 13px; color: var(--color-text-muted); margin: -2px 0 12px; display: flex; gap: 6px; flex-wrap: wrap; }
.escalate a { color: #2673dd; }
.escalate a:hover { text-decoration: underline; }

.suggestions { display: flex; gap: 6px; padding: 10px 16px 0; overflow-x: auto; background: white; border-top: 1px solid var(--color-border); }
.suggestions button { flex-shrink: 0; background: white; border: 1px solid var(--color-border); border-radius: 999px; padding: 5px 12px; font-size: 12px; cursor: pointer; color: var(--color-text-muted); }
.suggestions button:hover { border-color: var(--color-primary); color: var(--color-primary); }
.input-row { display: flex; gap: 8px; padding: 10px 16px 14px; background: white; }
.input-row input { margin-bottom: 0; }

@media (max-width: 600px) {
  .bubble { max-width: 88%; }
}
</style>
