<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Dự Báo Nhập Hàng <span class="ai-tag">AI</span></h2>
      <span class="sc-card-sub">Dự báo nhu cầu từ lịch sử bán {{ data?.historyDays || 120 }} ngày, gợi ý khi nào nhập và nhập bao nhiêu cho từng mã hàng</span>
    </div>

    <!-- the seller's own situation: how long new stock takes, how long it should last, how safe -->
    <div class="params">
      <label>Thời gian chờ hàng về
        <span class="inline"><input v-model.number="form.leadTime" type="number" min="1" max="60" /> ngày</span>
      </label>
      <label>Muốn đủ hàng thêm
        <span class="inline"><input v-model.number="form.cover" type="number" min="7" max="120" /> ngày</span>
      </label>
      <label>Mức an toàn (không đứt hàng)
        <select v-model.number="form.serviceLevel">
          <option :value="90">90%</option>
          <option :value="95">95% (khuyên dùng)</option>
          <option :value="99">99%</option>
        </select>
      </label>
      <button type="button" class="btn" :disabled="loading" @click="load">{{ loading ? 'Đang tính...' : 'Tính lại' }}</button>
      <button type="button" class="btn secondary" :disabled="adviceLoading || !data" @click="getAdvice">✨ {{ adviceLoading ? 'AI đang phân tích...' : 'AI tư vấn nhập hàng' }}</button>
    </div>
    <p v-if="error" class="err">{{ error }}</p>

    <div v-if="advice" class="advice">
      <div class="advice-head">✨ Lời khuyên {{ advice.engine === 'AI' ? 'từ AI' : '(theo quy tắc, AI chưa bật)' }}</div>
      <div v-for="(l, i) in advice.advice.split('\n').filter(Boolean)" :key="i">{{ l }}</div>
    </div>

    <template v-if="data">
      <div class="cards">
        <button v-for="c in CARDS" :key="c.status" type="button" class="card" :class="[c.cls, { on: filter === c.status }]" @click="filter = filter === c.status ? '' : c.status">
          <span class="num">{{ data.summary[c.status] }}</span><span>{{ c.label }}</span>
        </button>
        <div class="card total">
          <span class="num">{{ data.summary.suggestUnits.toLocaleString('vi-VN') }}</span>
          <span>Nên nhập ({{ data.summary.suggestSkus }} mã)</span>
        </div>
      </div>

      <div class="accuracy">
        <template v-if="data.accuracy.accuracy != null">
          📈 Độ chính xác khi thử trên {{ data.accuracy.horizonDays }} ngày gần nhất của shop: <strong>{{ data.accuracy.accuracy }}%</strong>
          <span class="text-muted">(cách lấy trung bình đơn giản: {{ data.accuracy.naiveAccuracy }}%, {{ data.accuracy.skus }} mã hàng)</span>
        </template>
        <template v-else>📈 Shop cần ít nhất 6 tuần có bán hàng để đo độ chính xác của dự báo.</template>
        <span class="model" :title="data.model">ⓘ Mô hình</span>
      </div>

      <div class="sc-toolbar">
        <input v-model.trim="keyword" placeholder="Tìm tên sản phẩm, phân loại hoặc SKU" />
        <label class="check"><input v-model="hideInactive" type="checkbox" /> Ẩn sản phẩm đã ẩn</label>
        <span class="spacer"></span>
        <span class="text-muted">{{ rows.length }} mã hàng</span>
      </div>

      <div v-if="!rows.length" class="sc-empty">Không có mã hàng phù hợp</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead>
            <tr>
              <th>Sản phẩm / Phân loại</th>
              <th class="num-col">Tồn kho</th>
              <th class="num-col" title="Trung bình 28 ngày qua">Bán/ngày</th>
              <th class="num-col" title="Dự báo trung bình 7 ngày tới">Dự báo/ngày</th>
              <th class="num-col">Còn đủ bán</th>
              <th class="num-col" title="Khi tồn kho xuống mức này thì nên đặt hàng">Điểm đặt hàng</th>
              <th class="num-col">Nên nhập</th>
              <th>Đánh giá</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.key" :class="{ inactive: !r.active }">
              <td>
                <div class="prod">
                  <img :src="r.thumbnailUrl || placeholder" alt="" />
                  <div class="prod-info">
                    <div class="prod-name" :title="r.name">{{ r.name }}</div>
                    <div v-if="r.variantName" class="variant">{{ r.variantName }}</div>
                    <div v-if="r.flashSaleSoon" class="variant flash">⚡ Sắp có Flash Sale, nhu cầu có thể tăng</div>
                  </div>
                </div>
              </td>
              <td class="num-col">{{ r.stock }}</td>
              <td class="num-col">{{ r.avgDaily }}</td>
              <td class="num-col">
                {{ r.forecastDaily }}
                <span v-if="r.trendPercent != null && Math.abs(r.trendPercent) >= 10" class="trend" :class="r.trendPercent > 0 ? 'up' : 'down'">
                  {{ r.trendPercent > 0 ? '▲' : '▼' }}{{ Math.abs(r.trendPercent) }}%
                </span>
              </td>
              <td class="num-col nowrap">
                <template v-if="r.daysLeft == null">—</template>
                <template v-else>{{ r.daysLeft }} ngày<div class="small text-muted">{{ day(r.stockoutDate) }}</div></template>
              </td>
              <td class="num-col">{{ r.status === 'SLOW' ? '—' : r.reorderPoint }}</td>
              <td class="num-col"><strong v-if="r.suggestQty" class="text-primary">{{ r.suggestQty.toLocaleString('vi-VN') }}</strong><span v-else class="text-muted">0</span></td>
              <td>
                <span class="state" :class="r.status">{{ STATUS[r.status] }}</span>
                <div class="reason">{{ r.reason }}</div>
                <div v-if="r.confidence === 'LOW'" class="small text-muted">Ít dữ liệu, dự báo chỉ để tham khảo</div>
              </td>
              <td class="row-actions">
                <button type="button" class="link" @click="openDetail(r)">Biểu đồ</button>
                <router-link :to="{ name: 'seller-inventory', query: { productId: r.productId } }" class="link">Nhập kho</router-link>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <div v-else-if="loading" class="sc-empty">Đang tính dự báo...</div>

    <BaseModal v-if="detail" :title="detail.name + (detail.variantName ? ' · ' + detail.variantName : '')" :width="760" @close="detail = null">
      <div v-if="detail.labels">
        <TrendChart :labels="detail.labels" :series="chartSeries" :height="280" />
        <div class="facts">
          <span>Tồn kho <b>{{ detail.stock }}</b></span>
          <span>Tồn kho an toàn <b>{{ detail.safetyStock }}</b></span>
          <span>Điểm đặt hàng <b>{{ detail.reorderPoint }}</b></span>
          <span>Nhu cầu {{ form.leadTime + form.cover }} ngày tới <b>{{ detail.coverDemand }}</b></span>
          <span>Nên nhập <b class="text-primary">{{ detail.suggestQty }}</b></span>
        </div>
        <div v-if="detail.seasonal" class="weekdays">
          <span class="text-muted small">Bán theo ngày trong tuần (1 = trung bình):</span>
          <span v-for="(v, i) in detail.weekdayIndex" :key="i" class="wd" :class="{ hi: v >= 1.15, lo: v <= 0.85 }">{{ WEEKDAYS[i] }} {{ v.toFixed(2) }}</span>
        </div>
        <p class="small text-muted">Đường nét đứt: dự báo; vùng trên: mức cao có thể xảy ra ({{ form.serviceLevel }}%). Mô hình: {{ detail.method }}, độ lệch ±{{ detail.sigma }}/ngày.</p>
      </div>
      <div v-else class="sc-empty">Đang tải...</div>
    </BaseModal>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import TrendChart from '../../components/analytics/TrendChart.vue'
import { stockForecast, stockForecastDetail, stockForecastAdvice } from '../../api/inventory'
import { extractError } from '../../utils/apiError'

const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'
const STATUS = { OUT: 'Hết hàng', URGENT: 'Nhập gấp', REORDER: 'Nên nhập', OK: 'Ổn', OVERSTOCK: 'Tồn nhiều', SLOW: 'Bán chậm' }
const CARDS = [
  { status: 'OUT', label: 'Hết hàng', cls: 'danger' },
  { status: 'URGENT', label: 'Nhập gấp', cls: 'danger' },
  { status: 'REORDER', label: 'Đến lúc nhập', cls: 'warn' },
  { status: 'OVERSTOCK', label: 'Tồn nhiều', cls: 'info' },
  { status: 'SLOW', label: 'Bán chậm / không bán', cls: '' }
]
const WEEKDAYS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const KEY = 'shoppi.forecastParams'

const saved = (() => { try { return JSON.parse(localStorage.getItem(KEY)) || {} } catch { return {} } })()
const form = reactive({ leadTime: saved.leadTime || 7, cover: saved.cover || 30, serviceLevel: saved.serviceLevel || 95 })
const data = ref(null)
const loading = ref(false)
const error = ref('')
const filter = ref('')
const keyword = ref('')
const hideInactive = ref(true)
const advice = ref(null)
const adviceLoading = ref(false)
const detail = ref(null)

const params = () => ({ leadTime: form.leadTime, cover: form.cover, serviceLevel: form.serviceLevel })

const rows = computed(() => {
  const q = keyword.value.toLowerCase()
  return (data.value?.items || [])
    .filter(r => !filter.value || r.status === filter.value)
    .filter(r => !hideInactive.value || r.active)
    .filter(r => !q || r.name.toLowerCase().includes(q) || (r.variantName || '').toLowerCase().includes(q) || (r.sku || '').toLowerCase().includes(q))
})

async function load() {
  error.value = ''
  loading.value = true
  advice.value = null
  try {
    data.value = (await stockForecast(params())).data
    try { localStorage.setItem(KEY, JSON.stringify(params())) } catch { /* convenience only */ }
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

async function getAdvice() {
  adviceLoading.value = true
  error.value = ''
  try {
    advice.value = (await stockForecastAdvice(params())).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    adviceLoading.value = false
  }
}

async function openDetail(r) {
  detail.value = { name: r.name, variantName: r.variantName }
  try {
    detail.value = (await stockForecastDetail(r.productId, r.variantId, params())).data
  } catch (e) {
    detail.value = null
    error.value = extractError(e)
  }
}

const chartSeries = computed(() => detail.value?.labels ? [
  { name: 'Đã bán', values: detail.value.actual, color: '#ee4d2d', type: 'bar', axis: 'left', integer: true },
  { name: 'Dự báo', values: detail.value.forecast, color: '#2673dd', type: 'line', axis: 'left', dashed: true },
  { name: `Mức cao (${form.serviceLevel}%)`, values: detail.value.upper, color: '#9ab8e6', type: 'line', axis: 'left', dashed: true }
] : [])

const day = (iso) => iso ? new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }) : ''

onMounted(load)
</script>

<style scoped>
.ai-tag { font-size: 11px; background: linear-gradient(90deg, #ee4d2d, #ff8a3d); color: white; padding: 2px 6px; border-radius: 3px; vertical-align: middle; margin-left: 4px; }
.params { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; margin-bottom: 14px; }
.params label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--color-text-muted); }
.params input, .params select { width: auto; margin: 0; }
.params input { width: 70px; }
.inline { display: flex; align-items: center; gap: 6px; color: var(--color-text); }
.err { color: #d33; font-size: 13px; }
.advice { background: #fff8f5; border: 1px solid #ffd8cc; border-radius: 4px; padding: 12px 14px; margin-bottom: 14px; font-size: 14px; line-height: 1.6; }
.advice-head { font-weight: 600; color: var(--color-primary); margin-bottom: 4px; }
.cards { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; margin-bottom: 12px; }
@media (max-width: 900px) { .cards { grid-template-columns: repeat(3, 1fr); } }
.card { display: flex; flex-direction: column; gap: 2px; border: 1px solid var(--color-border); border-radius: 4px; padding: 12px; background: white; text-align: left; font: inherit; color: var(--color-text-muted); font-size: 13px; }
button.card { cursor: pointer; }
.card.on { border-color: currentColor; box-shadow: 0 0 0 1px currentColor inset; }
.card .num { font-size: 20px; font-weight: 700; color: var(--color-text); }
.card.danger .num { color: #d33; }
.card.warn .num { color: #d97706; }
.card.info .num { color: #2673dd; }
.card.total { background: #fff8f5; }
.card.total .num { color: var(--color-primary); }
.accuracy { font-size: 13px; margin-bottom: 14px; display: flex; gap: 6px; flex-wrap: wrap; align-items: baseline; }
.model { margin-left: auto; color: #2673dd; cursor: help; }
.spacer { flex: 1; }
.check { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text-muted); cursor: pointer; }
.check input { width: auto; margin: 0; }
.prod { display: flex; align-items: center; gap: 10px; min-width: 200px; }
.prod img { width: 40px; height: 40px; object-fit: cover; border-radius: 2px; }
.prod-name { font-weight: 500; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.variant { font-size: 12px; color: var(--color-text-muted); }
.variant.flash { color: #d97706; }
.num-col { text-align: right; white-space: nowrap; }
.nowrap { white-space: nowrap; }
.small { font-size: 12px; }
tr.inactive td { opacity: 0.55; }
.trend { font-size: 11px; margin-left: 2px; }
.trend.up { color: #1e9e4a; }
.trend.down { color: #d33; }
.state { font-size: 12px; padding: 2px 8px; border-radius: 2px; white-space: nowrap; }
.state.OUT, .state.URGENT { background: #fdecea; color: #d33; }
.state.REORDER { background: #fff4e5; color: #d97706; }
.state.OK { background: #eaf7ee; color: #1e9e4a; }
.state.OVERSTOCK { background: #e8f0fc; color: #2673dd; }
.state.SLOW { background: #f2f2f2; color: #777; }
.reason { font-size: 12px; color: var(--color-text-muted); margin-top: 3px; min-width: 170px; max-width: 240px; }
.row-actions { white-space: nowrap; }
.row-actions .link { display: block; margin: 2px 0; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; text-align: left; }
.facts { display: flex; flex-wrap: wrap; gap: 8px 18px; font-size: 13px; margin: 12px 0 8px; }
.weekdays { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 6px; }
.wd { font-size: 12px; padding: 1px 6px; border-radius: 2px; background: #f5f5f5; }
.wd.hi { background: #eaf7ee; color: #1e9e4a; }
.wd.lo { background: #fdecea; color: #d33; }
</style>
