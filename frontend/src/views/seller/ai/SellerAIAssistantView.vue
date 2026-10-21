<template>
  <section class="sc-card assistant">
    <div class="sc-card-head">
      <h2 class="sc-card-title">✨ Trợ Lý Phân Tích Doanh Thu</h2>
      <span class="sc-card-sub">Hỏi bằng tiếng Việt, trợ lý trả lời bằng số liệu thật của shop</span>
    </div>

    <div ref="scrollEl" class="thread">
      <div v-if="!messages.length" class="welcome">
        <div class="bot-icon">🤖</div>
        <p>Xin chào! Mình có thể trả lời các câu hỏi về <strong>doanh thu, đơn hàng, sản phẩm bán chạy/bán chậm, tồn kho, khách hàng,
          tỷ lệ hủy, lượt xem & chuyển đổi, đánh giá</strong> trong khoảng thời gian bạn muốn.</p>
        <div class="chips">
          <button v-for="q in examples" :key="q" type="button" @click="ask(q)">{{ q }}</button>
        </div>
      </div>

      <template v-for="(m, i) in messages" :key="i">
        <div v-if="m.role === 'user'" class="msg user"><div class="bubble">{{ m.text }}</div></div>
        <div v-else class="msg bot">
          <div class="avatar">🤖</div>
          <div class="bot-body">
            <div v-if="m.loading" class="bubble typing"><span></span><span></span><span></span></div>
            <div v-else-if="m.error" class="bubble error">{{ m.error }}</div>
            <template v-else>
              <div class="meta">
                <span v-if="m.data.period && m.data.intent !== 'HELP'" class="tag">📅 {{ periodText(m.data.period) }}</span>
                <span v-if="m.data.compare" class="tag">so với {{ periodText(m.data.compare) }}</span>
                <span v-if="m.data.product" class="tag">🛍 {{ m.data.product.name }}</span>
                <span class="tag src">{{ m.data.source === 'ai' ? 'Diễn đạt bởi AI' : 'Phân tích tự động' }}</span>
              </div>
              <div class="bubble">{{ m.data.answer }}</div>
              <div v-if="m.data.chart && m.data.chart.labels.length > 1" class="card">
                <TrendChart :labels="m.data.chart.labels" :height="200"
                            :series="m.data.chart.series.map(s => ({ name: s.name, values: s.values, color: '#ee4d2d', type: m.data.chart.type, format: s.money ? formatVnd : undefined, integer: !s.money }))" />
              </div>
              <div v-if="m.data.table && m.data.table.rows.length" class="card table-card">
                <table class="sc-table">
                  <thead><tr><th v-for="c in m.data.table.columns" :key="c">{{ c }}</th></tr></thead>
                  <tbody><tr v-for="(r, k) in m.data.table.rows" :key="k"><td v-for="(v, j) in r" :key="j">{{ v }}</td></tr></tbody>
                </table>
              </div>
              <div v-if="i === messages.length - 1 && m.data.suggestions?.length" class="chips small">
                <button v-for="q in m.data.suggestions" :key="q" type="button" @click="ask(q)">{{ q }}</button>
              </div>
            </template>
          </div>
        </div>
      </template>
    </div>

    <form class="composer" @submit.prevent="ask(draft)">
      <input v-model="draft" maxlength="300" placeholder="VD: Doanh thu tháng 9 so với tháng 8? · Sản phẩm nào bán chạy tuần này?" :disabled="busy" />
      <button class="btn" type="submit" :disabled="busy || !draft.trim()">Hỏi</button>
      <button v-if="messages.length" type="button" class="btn ghost" title="Xóa cuộc trò chuyện" @click="messages = []">Xóa</button>
    </form>
  </section>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import TrendChart from '../../../components/analytics/TrendChart.vue'
import { askSalesAssistant, assistantExamples } from '../../../api/ai'
import { formatVnd } from '../../../utils/format'
import { extractError } from '../../../utils/apiError'

const route = useRoute()
const messages = ref([])
const examples = ref([])
const draft = ref('')
const busy = ref(false)
const scrollEl = ref(null)

const day = (d) => d.split('-').reverse().slice(0, 2).join('/')
const periodText = (p) => (p.from === p.to ? `${p.label} (${day(p.from)})` : `${p.label} (${day(p.from)} – ${day(p.to)})`)

async function scroll() {
  await nextTick()
  if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
}

async function ask(text) {
  const q = (text || '').trim()
  if (!q || busy.value) return
  draft.value = ''
  busy.value = true
  messages.value.push({ role: 'user', text: q })
  const reply = { role: 'bot', loading: true }
  messages.value.push(reply)
  scroll()
  try {
    const { data } = await askSalesAssistant(q)
    Object.assign(messages.value[messages.value.length - 1], { loading: false, data })
  } catch (e) {
    Object.assign(messages.value[messages.value.length - 1], { loading: false, error: extractError(e) })
  } finally {
    busy.value = false
    scroll()
  }
}

onMounted(() => {
  assistantExamples().then(({ data }) => { examples.value = data }).catch(() => {})
  // deep link: /seller/ai/assistant?q=... asks right away
  if (route.query.q) ask(String(route.query.q))
})
</script>

<style scoped>
.assistant { display: flex; flex-direction: column; height: calc(100vh - 130px); min-height: 520px; margin-bottom: 0; }
.thread { flex: 1; overflow-y: auto; padding: 4px 4px 12px; }
.welcome { text-align: center; max-width: 640px; margin: 30px auto; color: var(--color-text-muted); line-height: 1.6; }
.bot-icon { font-size: 44px; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; margin-top: 14px; }
.chips button { background: #faf8ff; border: 1px solid #e0d8ff; color: #6941c6; border-radius: 999px; padding: 6px 14px; font-size: 13px; cursor: pointer; }
.chips button:hover { background: #ebe5ff; }
.chips.small { justify-content: flex-start; margin-top: 8px; }
.chips.small button { font-size: 12px; padding: 4px 10px; }
.msg { display: flex; margin: 12px 0; gap: 10px; }
.msg.user { justify-content: flex-end; }
.msg.user .bubble { background: var(--color-primary); color: white; border-radius: 14px 14px 3px 14px; }
.avatar { width: 32px; height: 32px; border-radius: 50%; background: #f3f0ff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.bot-body { flex: 1; min-width: 0; max-width: 820px; }
.bubble { display: inline-block; background: #f6f6f8; padding: 10px 14px; border-radius: 14px 14px 14px 3px; font-size: 14px; line-height: 1.6; white-space: pre-line; max-width: 100%; }
.bubble.error { background: #fdecea; color: #b42318; }
.meta { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 6px; }
.tag { font-size: 11px; background: #f0f5ff; color: #2673dd; border-radius: 999px; padding: 2px 8px; }
.tag.src { background: #f3f0ff; color: #6941c6; }
.card { margin-top: 8px; border: 1px solid var(--color-border); border-radius: 8px; padding: 10px 12px; background: white; }
.table-card { padding: 0; overflow-x: auto; }
.table-card .sc-table td, .table-card .sc-table th { padding: 8px 12px; font-size: 13px; }
.typing span { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #aaa; margin: 0 2px; animation: blink 1.2s infinite; }
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.2; } 40% { opacity: 1; } }
.composer { display: flex; gap: 8px; padding-top: 12px; border-top: 1px solid var(--color-border); }
.composer input { margin: 0; flex: 1; }
</style>
