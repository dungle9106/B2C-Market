<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">⚡ Flash Sale Của Shop</h2>
      <span class="sc-card-sub">Giảm giá sâu trong khung giờ ngắn, giới hạn số suất — hiển thị ở mục FLASH SALE trang chủ kèm đồng hồ đếm ngược</span>
      <button v-if="!creating" type="button" class="btn new" @click="startCreate">+ Tạo đợt Flash Sale</button>
    </div>

    <!-- ===== create ===== -->
    <div v-if="creating" class="create">
      <h3 class="block-h">1. Chọn khung giờ</h3>
      <div class="slot-row">
        <input v-model="day" type="date" :min="today" class="day" />
        <button v-for="s in SLOTS" :key="s.label" type="button" class="slot" :class="{ on: slot === s.label }" @click="pickSlot(s)">{{ s.label }}</button>
        <button type="button" class="slot" :class="{ on: slot === 'custom' }" @click="slot = 'custom'">Tùy chỉnh</button>
      </div>
      <div v-if="slot === 'custom'" class="custom">
        <label>Bắt đầu <input v-model="customStart" type="datetime-local" /></label>
        <label>Kết thúc <input v-model="customEnd" type="datetime-local" /></label>
      </div>
      <p class="hint">{{ windowText }}</p>

      <h3 class="block-h">2. Chọn sản phẩm & giá Flash Sale</h3>
      <div class="batch">
        <input v-model.trim="filter" placeholder="Lọc sản phẩm" class="filter" />
        <span>Giảm nhanh cho sản phẩm đã chọn:</span>
        <button v-for="pct in [10, 20, 30, 50]" :key="pct" type="button" class="btn sm ghost" @click="applyPercent(pct)">-{{ pct }}%</button>
        <input v-model.number="batchStock" type="number" min="1" placeholder="Số suất" class="mini" />
        <input v-model.number="batchMax" type="number" min="1" placeholder="Tối đa/khách" class="mini" />
        <button type="button" class="btn sm ghost" @click="applyLimits">Áp dụng</button>
      </div>
      <div class="sc-table-wrap">
        <table class="sc-table">
          <thead><tr><th></th><th>Sản phẩm</th><th>Giá bán</th><th>Giá Flash Sale</th><th>Số suất <span class="th-hint">(trống = toàn bộ kho)</span></th><th>Tối đa / khách</th></tr></thead>
          <tbody>
            <tr v-for="r in pickable" :key="r.productId" :class="{ on: picked[r.productId] }">
              <td><input type="checkbox" :checked="!!picked[r.productId]" :disabled="busyStatus(r)" @change="togglePick(r)" /></td>
              <td>
                <div class="prod">
                  <img :src="r.thumbnailUrl || placeholder" alt="" />
                  <div>
                    <div class="name">{{ r.name }}</div>
                    <div class="small text-faint">Kho {{ r.stock }}<template v-if="busyStatus(r)"> · đang có Flash Sale</template></div>
                  </div>
                </div>
              </td>
              <td>{{ formatVnd(r.price) }}</td>
              <template v-if="picked[r.productId]">
                <td>
                  <input v-model.number="picked[r.productId].flashPrice" type="number" min="1000" class="cell" />
                  <div v-if="picked[r.productId].flashPrice" class="small pct">-{{ Math.round((1 - picked[r.productId].flashPrice / r.price) * 100) }}%</div>
                </td>
                <td><input v-model.number="picked[r.productId].flashStock" type="number" min="1" :max="r.stock" class="cell" /></td>
                <td><input v-model.number="picked[r.productId].maxPerBuyer" type="number" min="1" class="cell" /></td>
              </template>
              <td v-else colspan="3" class="text-faint small">Chọn để thêm vào đợt Flash Sale</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="actions">
        <button type="button" class="btn ghost" @click="creating = false">Hủy</button>
        <button type="button" class="btn" :disabled="busy || !pickedCount" @click="onSchedule">{{ busy ? 'Đang lưu...' : `Lên lịch Flash Sale (${pickedCount} sản phẩm)` }}</button>
      </div>
    </div>

    <!-- ===== list ===== -->
    <nav class="sc-tabs">
      <a v-for="t in TABS" :key="t.key" href="#" :class="{ active: tab === t.key }" @click.prevent="tab = t.key">
        {{ t.label }} <span class="count">({{ rows.filter(t.match).length }})</span>
      </a>
    </nav>
    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="!listed.length" class="sc-empty">Chưa có Flash Sale nào {{ tab === 'live' ? 'đang hoặc sắp diễn ra' : 'đã kết thúc' }}.</div>
    <div v-else class="sc-table-wrap">
      <table class="sc-table">
        <thead><tr><th>Sản phẩm</th><th>Khung giờ</th><th>Giá</th><th>Đã bán / Số suất</th><th>Trạng thái</th><th></th></tr></thead>
        <tbody>
          <tr v-for="r in listed" :key="r.productId">
            <td><div class="prod"><img :src="r.thumbnailUrl || placeholder" alt="" /><span class="name">{{ r.name }}</span></div></td>
            <td class="small">{{ formatDateTime(r.flashSaleStart) }}<br />→ {{ formatDateTime(r.flashSaleEnd) }}</td>
            <td><strong class="text-primary">{{ formatVnd(r.flashSalePrice) }}</strong><div class="small old">{{ formatVnd(r.price) }}</div></td>
            <td class="sold">
              <div>{{ r.flashSaleSold || 0 }} / {{ r.flashSaleStock || 'không giới hạn' }}</div>
              <div v-if="r.flashSaleStock" class="bar"><span :style="{ width: Math.min(100, (r.flashSaleSold / r.flashSaleStock) * 100) + '%' }"></span></div>
              <div v-if="r.flashSaleMaxPerBuyer" class="small text-faint">Tối đa {{ r.flashSaleMaxPerBuyer }}/khách</div>
            </td>
            <td>
              <span class="state" :class="r.status">{{ STATUS[r.status] }}</span>
              <router-link v-if="r.shoppiEvent && r.status !== 'NONE'" :to="{ name: 'seller-flash-events' }" class="shoppi-tag">⚡ Flash Sale Shoppi</router-link>
            </td>
            <td><button v-if="['RUNNING', 'SOLD_OUT', 'UPCOMING'].includes(r.status) && !r.shoppiEvent" type="button" class="link" @click="onEnd(r)">{{ r.status === 'UPCOMING' ? 'Hủy' : 'Kết thúc' }}</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { myFlashSales, scheduleFlashSale, endFlashSale } from '../../api/marketing'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const SLOTS = [
  { label: '00:00 - 12:00', from: 0, to: 12 },
  { label: '12:00 - 18:00', from: 12, to: 18 },
  { label: '18:00 - 21:00', from: 18, to: 21 },
  { label: '21:00 - 24:00', from: 21, to: 24 }
]
const STATUS = { RUNNING: 'Đang diễn ra', UPCOMING: 'Sắp diễn ra', SOLD_OUT: 'Hết suất', ENDED: 'Đã kết thúc' }
const TABS = [
  { key: 'live', label: 'Đang & sắp diễn ra', match: (r) => ['RUNNING', 'UPCOMING', 'SOLD_OUT'].includes(r.status) },
  { key: 'ended', label: 'Đã kết thúc', match: (r) => r.status === 'ENDED' }
]
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const pad = (n) => String(n).padStart(2, '0')
const isoDay = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const today = isoDay(new Date())

const rows = ref([])
const loading = ref(true)
const tab = ref('live')
const creating = ref(false)
const busy = ref(false)
const error = ref('')
const day = ref(today)
const slot = ref('')
const customStart = ref('')
const customEnd = ref('')
const filter = ref('')
const batchStock = ref(null)
const batchMax = ref(null)
const picked = reactive({}) // productId -> { flashPrice, flashStock, maxPerBuyer }

const listed = computed(() => rows.value.filter(TABS.find(t => t.key === tab.value).match))
const busyStatus = (r) => ['RUNNING', 'SOLD_OUT'].includes(r.status)
const pickable = computed(() => {
  const q = filter.value.toLowerCase()
  return rows.value.filter(r => r.active && r.stock > 0 && (!q || r.name.toLowerCase().includes(q)))
})
const pickedCount = computed(() => Object.keys(picked).length)

// "YYYY-MM-DDTHH:mm:ss" in local time, what the backend's LocalDateTime expects
const slotWindow = computed(() => {
  if (slot.value === 'custom') return customStart.value && customEnd.value ? { start: customStart.value + ':00', end: customEnd.value + ':00' } : null
  const s = SLOTS.find(x => x.label === slot.value)
  if (!s || !day.value) return null
  const end = s.to === 24 ? (() => { const d = new Date(day.value + 'T00:00:00'); d.setDate(d.getDate() + 1); return `${isoDay(d)}T00:00:00` })() : `${day.value}T${pad(s.to)}:00:00`
  return { start: `${day.value}T${pad(s.from)}:00:00`, end }
})
const windowText = computed(() => {
  const w = slotWindow.value
  if (!w) return 'Chọn ngày và một khung giờ.'
  const start = new Date(w.start)
  const effective = start < new Date() ? 'ngay bây giờ' : formatDateTime(w.start)
  return `Diễn ra từ ${effective} đến ${formatDateTime(w.end)}.`
})

function startCreate() {
  creating.value = true
  error.value = ''
  slot.value = ''
  Object.keys(picked).forEach(k => delete picked[k])
}

function pickSlot(s) {
  slot.value = s.label
}

function togglePick(r) {
  if (picked[r.productId]) delete picked[r.productId]
  else picked[r.productId] = { flashPrice: Math.round((r.price * 0.8) / 1000) * 1000, flashStock: null, maxPerBuyer: null }
}

function applyPercent(pct) {
  for (const id of Object.keys(picked)) {
    const r = rows.value.find(x => x.productId === Number(id))
    picked[id].flashPrice = Math.max(1000, Math.round((r.price * (100 - pct)) / 100 / 1000) * 1000)
  }
}

function applyLimits() {
  for (const id of Object.keys(picked)) {
    const r = rows.value.find(x => x.productId === Number(id))
    if (batchStock.value) picked[id].flashStock = Math.min(batchStock.value, r.stock)
    if (batchMax.value) picked[id].maxPerBuyer = batchMax.value
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await myFlashSales()
    rows.value = data
  } finally {
    loading.value = false
  }
}

async function onSchedule() {
  error.value = ''
  const w = slotWindow.value
  if (!w) return (error.value = 'Vui lòng chọn khung giờ')
  if (new Date(w.end) <= new Date()) return (error.value = 'Khung giờ đã qua, hãy chọn khung giờ khác')
  // a slot that already started begins now
  const start = new Date(w.start) < new Date() ? localNow() : w.start
  busy.value = true
  try {
    const items = Object.entries(picked).map(([productId, v]) => ({ productId: Number(productId), flashPrice: v.flashPrice, flashStock: v.flashStock || null, maxPerBuyer: v.maxPerBuyer || null }))
    await scheduleFlashSale({ startAt: start, endAt: w.end, items })
    creating.value = false
    tab.value = 'live'
    await load()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

function localNow() {
  const d = new Date()
  return `${isoDay(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function onEnd(r) {
  if (!confirm(r.status === 'UPCOMING' ? `Hủy Flash Sale sắp diễn ra của "${r.name}"?` : `Kết thúc Flash Sale của "${r.name}" ngay?`)) return
  await endFlashSale(r.productId)
  await load()
}

onMounted(load)
</script>

<style scoped>
.new { margin-left: auto; }
.create { border: 1px dashed var(--color-primary); border-radius: 6px; padding: 16px; margin-bottom: 18px; background: #fffbfa; }
.block-h { font-size: 15px; margin: 0 0 10px; }
.slot-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.day { width: auto; margin: 0; }
.slot { border: 1px solid #ddd; background: white; border-radius: 3px; padding: 8px 14px; cursor: pointer; font-size: 14px; }
.slot.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.custom { display: flex; gap: 12px; margin-top: 10px; flex-wrap: wrap; }
.custom label { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.custom input { margin: 0; width: auto; }
.hint { font-size: 13px; color: var(--color-text-muted); margin: 8px 0 16px; }
.batch { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; font-size: 13px; color: var(--color-text-muted); }
.batch .filter { width: 220px; margin: 0; }
.mini { width: 110px; margin: 0; }
.th-hint { font-weight: 400; font-size: 11px; }
tr.on td { background: #fff; }
.prod { display: flex; align-items: center; gap: 10px; }
.prod img { width: 40px; height: 40px; object-fit: cover; border-radius: 2px; }
.name { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.small { font-size: 12px; }
.cell { width: 120px; margin: 0; }
.pct { color: var(--color-primary); }
.old { text-decoration: line-through; color: var(--color-text-faint); }
.error { color: #d33; font-size: 13px; }
.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 12px; }
.sold { min-width: 140px; }
.bar { height: 6px; background: #ffe1d9; border-radius: 999px; overflow: hidden; margin-top: 4px; }
.bar span { display: block; height: 100%; background: var(--color-primary); }
.state { font-size: 12px; padding: 2px 8px; border-radius: 2px; white-space: nowrap; }
.state.RUNNING { background: #fdecea; color: #d33; }
.state.UPCOMING { background: #e8f1fd; color: #2673dd; }
.state.SOLD_OUT { background: #fff4e5; color: #d97706; }
.state.ENDED { background: #f5f5f5; color: #888; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
.shoppi-tag { display: block; margin-top: 3px; font-size: 11px; color: var(--color-primary); }
</style>
