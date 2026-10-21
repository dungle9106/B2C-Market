<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Quản Lý Tồn Kho</h2>
      <span class="sc-card-sub"><span class="live" :class="{ paused: !live }"></span>{{ live ? 'Đang cập nhật trực tiếp' : 'Tạm dừng' }} · {{ updatedText }}</span>
      <button type="button" class="link head-link" @click="live = !live">{{ live ? 'Tạm dừng' : 'Bật cập nhật' }}</button>
    </div>

    <div v-if="data" class="cards">
      <div class="card"><span class="num">{{ data.summary.skus }}</span><span>Mã hàng (SKU)</span></div>
      <div class="card"><span class="num">{{ data.summary.units.toLocaleString('vi-VN') }}</span><span>Tổng tồn kho</span></div>
      <button type="button" class="card warn" :class="{ on: statusFilter === 'LOW' }" @click="setStatus('LOW')"><span class="num">{{ data.summary.lowStock }}</span><span>Sắp hết (≤ {{ data.lowStockThreshold }})</span></button>
      <button type="button" class="card danger" :class="{ on: statusFilter === 'OUT' }" @click="setStatus('OUT')"><span class="num">{{ data.summary.outOfStock }}</span><span>Hết hàng</span></button>
      <div class="card"><span class="num">{{ formatVnd(data.summary.value) }}</span><span>Giá trị tồn (giá bán)</span></div>
    </div>

    <nav class="sc-tabs">
      <a href="#" :class="{ active: tab === 'stock' }" @click.prevent="tab = 'stock'">Tồn kho</a>
      <a href="#" :class="{ active: tab === 'logs' }" @click.prevent="openLogs(null)">Lịch sử nhập / xuất</a>
    </nav>

    <!-- ===== stock table ===== -->
    <template v-if="tab === 'stock'">
      <div class="sc-toolbar">
        <input v-model.trim="keyword" placeholder="Tìm tên sản phẩm, phân loại hoặc SKU" />
        <select v-model="statusFilter" class="narrow">
          <option value="">Mọi trạng thái</option>
          <option value="OK">Còn hàng</option>
          <option value="LOW">Sắp hết hàng</option>
          <option value="OUT">Hết hàng</option>
        </select>
        <label class="check"><input v-model="hideInactive" type="checkbox" /> Ẩn sản phẩm đã ẩn</label>
        <span v-if="focusProduct" class="focus-note">Đang xem 1 sản phẩm · <button type="button" class="link" @click="focusProduct = null">Xem tất cả</button></span>
        <span class="spacer"></span>
        <span class="text-muted">{{ rows.length }} dòng</span>
      </div>

      <div v-if="!data" class="sc-empty">Đang tải...</div>
      <div v-else-if="!rows.length" class="sc-empty">Không có sản phẩm phù hợp</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead>
            <tr><th>Sản phẩm / Phân loại</th><th>SKU</th><th class="num-col">Tồn kho</th><th class="num-col" title="Đã trừ kho, đơn chưa giao cho ĐVVC">Chờ giao</th><th class="num-col">Giá bán</th><th>Trạng thái</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="rowKey(r)" :class="{ inactive: !r.active, focus: focusProduct === r.productId }">
              <td>
                <div class="prod">
                  <img :src="r.thumbnailUrl || placeholder" alt="" />
                  <div class="prod-info">
                    <div class="prod-name">{{ r.name }}</div>
                    <div v-if="r.variantName" class="variant">{{ r.variantName }}</div>
                    <span v-if="!r.active" class="text-faint small">Đã ẩn</span>
                  </div>
                </div>
              </td>
              <td class="mono">{{ r.sku || '—' }}</td>
              <td class="num-col"><span class="stock" :class="[r.status, { flash: flashing.has(rowKey(r)) }]">{{ r.stock }}</span></td>
              <td class="num-col">{{ r.toShip || '—' }}</td>
              <td class="num-col">{{ formatVnd(r.price) }}</td>
              <td><span class="state" :class="r.status">{{ STATUS[r.status] }}</span></td>
              <td class="row-actions">
                <button type="button" class="btn sm" @click="openAdjust(r)">Điều chỉnh</button>
                <button type="button" class="link" @click="openLogs(r.productId)">Thẻ kho</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- ===== movement history ===== -->
    <template v-else>
      <div class="sc-toolbar">
        <span v-if="logProduct">Thẻ kho: <strong>{{ logProductName }}</strong> <button type="button" class="link" @click="openLogs(null)">Xem tất cả sản phẩm</button></span>
        <span v-else class="text-muted">Mọi thay đổi tồn kho của shop, mới nhất trước</span>
      </div>
      <div v-if="logsLoading" class="sc-empty">Đang tải...</div>
      <div v-else-if="!logs.items.length" class="sc-empty">Chưa có biến động kho nào</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead><tr><th>Thời gian</th><th>Sản phẩm</th><th>Loại</th><th class="num-col">Thay đổi</th><th class="num-col">Tồn sau</th><th>Ghi chú</th></tr></thead>
          <tbody>
            <tr v-for="l in logs.items" :key="l.id">
              <td class="nowrap">{{ formatDateTime(l.createdAt) }}</td>
              <td>{{ l.productName }}<div v-if="l.variantName" class="variant">{{ l.variantName }}</div></td>
              <td><span class="reason" :class="l.reason">{{ REASONS[l.reason] || l.reason }}</span></td>
              <td class="num-col" :class="l.change > 0 ? 'plus' : 'minus'">{{ l.change > 0 ? '+' : '' }}{{ l.change }}</td>
              <td class="num-col">{{ l.stockAfter }}</td>
              <td class="small">
                <router-link v-if="l.orderCode" :to="{ name: 'seller-orders' }" class="link">Đơn {{ l.orderCode }}</router-link>
                {{ l.note }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationBar :page="logs.page" :total-pages="logs.totalPages" @change="loadLogs" />
    </template>

    <!-- ===== adjust dialog ===== -->
    <BaseModal v-if="adjusting" title="Điều chỉnh tồn kho" :width="480" @close="adjusting = null">
      <p class="adj-target"><strong>{{ adjusting.name }}</strong><template v-if="adjusting.variantName"> · {{ adjusting.variantName }}</template><br />
        <span class="text-faint">Tồn kho hiện tại: <strong>{{ currentOf(adjusting) }}</strong></span></p>
      <div class="modes">
        <button v-for="(label, m) in MODES" :key="m" type="button" :class="{ on: adj.mode === m }" @click="setMode(m)">{{ label }}</button>
      </div>
      <label class="lbl">{{ adj.mode === 'SET' ? 'Số lượng thực tế' : 'Số lượng' }}</label>
      <input v-model.number="adj.quantity" type="number" min="0" />
      <p class="preview">Tồn kho sau điều chỉnh: <strong :class="{ bad: previewAfter < 0 }">{{ previewAfter }}</strong></p>
      <label class="lbl">Lý do</label>
      <select v-model="adj.reason">
        <option v-for="(label, r) in ADJUST_REASONS" :key="r" :value="r">{{ label }}</option>
      </select>
      <input v-model.trim="adj.note" maxlength="300" placeholder="Ghi chú (VD: Nhập lô hàng tháng 10)" />
      <p v-if="adjError" class="error">{{ adjError }}</p>
      <template #footer>
        <button type="button" class="btn ghost" @click="adjusting = null">Hủy</button>
        <button type="button" class="btn" :disabled="adjBusy || previewAfter < 0 || !(adj.quantity >= 0)" @click="onAdjust">Lưu</button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import BaseModal from '../../components/BaseModal.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { inventory, inventoryLogs, adjustStock } from '../../api/inventory'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const POLL_MS = 10000
const STATUS = { OK: 'Còn hàng', LOW: 'Sắp hết', OUT: 'Hết hàng' }
const MODES = { ADD: '+ Nhập thêm', SUBTRACT: '− Xuất bớt', SET: 'Đặt số lượng' }
const ADJUST_REASONS = { RESTOCK: 'Nhập hàng', STOCKTAKE: 'Kiểm kho (đối chiếu thực tế)', DAMAGED: 'Hàng hư hỏng / thất lạc', OTHER: 'Khác' }
const REASONS = {
  CREATE: 'Tạo sản phẩm', ORDER: 'Bán (đặt hàng)', CANCEL: 'Hủy đơn', RETURN: 'Trả hàng', EDIT: 'Sửa sản phẩm',
  SOLD_OUT: 'Đánh dấu hết hàng', IMPORT: 'Nhập Excel', ...ADJUST_REASONS
}
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const route = useRoute()
const data = ref(null)
const tab = ref('stock')
const keyword = ref('')
const statusFilter = ref('')
const hideInactive = ref(false)
const live = ref(true)
const flashing = reactive(new Set())
const focusProduct = ref(route.query.productId ? Number(route.query.productId) : null)
let timer = null

const rowKey = (r) => `${r.productId}:${r.variantId ?? ''}`
const updatedText = computed(() => (data.value ? 'cập nhật lúc ' + new Date(data.value.updatedAt).toLocaleTimeString('vi-VN') : ''))

const rows = computed(() => {
  if (!data.value) return []
  const q = keyword.value.toLowerCase()
  return data.value.rows.filter(r =>
    (!statusFilter.value || r.status === statusFilter.value)
    && (!hideInactive.value || r.active)
    && (!q || r.name.toLowerCase().includes(q) || (r.variantName || '').toLowerCase().includes(q) || (r.sku || '').toLowerCase().includes(q))
    && (!focusProduct.value || r.productId === focusProduct.value))
})

function setStatus(s) {
  statusFilter.value = statusFilter.value === s ? '' : s
  focusProduct.value = null
}

// polling: rows whose stock changed since the last load flash for a moment
async function load() {
  try {
    const { data: fresh } = await inventory()
    if (data.value) {
      const before = new Map(data.value.rows.map(r => [rowKey(r), r.stock]))
      for (const r of fresh.rows) {
        const k = rowKey(r)
        if (before.has(k) && before.get(k) !== r.stock) {
          flashing.add(k)
          setTimeout(() => flashing.delete(k), 2500)
        }
      }
    }
    data.value = fresh
  } catch {
    // network blip: the next poll retries
  }
}

// ---- logs ----
const logs = ref({ items: [], page: 0, totalPages: 0 })
const logsLoading = ref(false)
const logProduct = ref(null)
const logProductName = computed(() => data.value?.rows.find(r => r.productId === logProduct.value)?.name || '')

async function loadLogs(page = 0) {
  logsLoading.value = true
  try {
    const { data: d } = await inventoryLogs({ productId: logProduct.value, page })
    logs.value = d
  } finally {
    logsLoading.value = false
  }
}
function openLogs(productId) {
  logProduct.value = productId
  tab.value = 'logs'
  loadLogs(0)
}

// ---- adjust ----
const adjusting = ref(null)
const adj = ref({ mode: 'ADD', quantity: null, reason: 'RESTOCK', note: '' })
const adjBusy = ref(false)
const adjError = ref('')
const currentOf = (r) => data.value?.rows.find(x => rowKey(x) === rowKey(r))?.stock ?? r.stock
const previewAfter = computed(() => {
  if (!adjusting.value) return 0
  const cur = currentOf(adjusting.value)
  const q = Number(adj.value.quantity) || 0
  return adj.value.mode === 'ADD' ? cur + q : adj.value.mode === 'SUBTRACT' ? cur - q : q
})

function openAdjust(r) {
  adjusting.value = r
  adj.value = { mode: 'ADD', quantity: null, reason: 'RESTOCK', note: '' }
  adjError.value = ''
}
function setMode(m) {
  adj.value.mode = m
  adj.value.reason = m === 'ADD' ? 'RESTOCK' : m === 'SET' ? 'STOCKTAKE' : 'DAMAGED'
}

async function onAdjust() {
  adjBusy.value = true
  adjError.value = ''
  try {
    const r = adjusting.value
    await adjustStock({ productId: r.productId, variantId: r.variantId, ...adj.value })
    adjusting.value = null
    await load()
  } catch (e) {
    adjError.value = extractError(e)
  } finally {
    adjBusy.value = false
  }
}

watch(live, (on) => {
  clearInterval(timer)
  if (on) {
    load()
    timer = setInterval(() => { if (!document.hidden) load() }, POLL_MS)
  }
}, { immediate: true })
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.head-link { margin-left: auto; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.link:hover { text-decoration: underline; }
.live { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #1e9e4a; margin-right: 6px; animation: pulse 1.6s infinite; }
.live.paused { background: #bbb; animation: none; }
@keyframes pulse { 50% { opacity: 0.3; } }

.cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-bottom: 16px; }
.card { display: flex; flex-direction: column; gap: 2px; border: 1px solid var(--color-border); border-radius: 4px; padding: 12px; background: white; text-align: left; font: inherit; color: var(--color-text-muted); font-size: 13px; }
button.card { cursor: pointer; }
.card.on { border-color: currentColor; }
.card .num { font-size: 20px; font-weight: 700; color: var(--color-text); }
.card.warn .num { color: #d97706; }
.card.danger .num { color: #d33; }

.narrow { width: auto; margin: 0; }
.check { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text-muted); cursor: pointer; }
.check input { width: auto; margin: 0; }
.focus-note { font-size: 13px; color: #a16207; }
.prod { display: flex; align-items: center; gap: 10px; min-width: 240px; }
.prod img { width: 40px; height: 40px; object-fit: cover; border-radius: 2px; }
.prod-name { font-weight: 500; max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.variant { font-size: 12px; color: var(--color-text-muted); }
.small { font-size: 12px; }
.mono { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.num-col { text-align: right; white-space: nowrap; }
.nowrap { white-space: nowrap; }
tr.inactive td { opacity: 0.55; }
tr.focus td { background: #fffaf3; }
.stock { font-weight: 600; padding: 2px 6px; border-radius: 3px; transition: background 0.4s; }
.stock.LOW { color: #d97706; }
.stock.OUT { color: #d33; }
.stock.flash { background: #ffe58f; }
.state { font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.state.OK { background: #eaf7ee; color: #1e9e4a; }
.state.LOW { background: #fff4e5; color: #d97706; }
.state.OUT { background: #fdecea; color: #d33; }
.row-actions { white-space: nowrap; }
.row-actions .link { margin-left: 10px; }
.reason { font-size: 12px; padding: 2px 6px; border-radius: 2px; background: #f5f5f5; white-space: nowrap; }
.reason.ORDER { background: #e8f1fd; color: #2673dd; }
.reason.CANCEL, .reason.RETURN, .reason.RESTOCK { background: #eaf7ee; color: #1e9e4a; }
.reason.DAMAGED, .reason.SOLD_OUT { background: #fdecea; color: #d33; }
.plus { color: #1e9e4a; font-weight: 600; }
.minus { color: #d33; font-weight: 600; }

.adj-target { margin: 0 0 12px; line-height: 1.6; }
.modes { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-bottom: 12px; }
.modes button { border: 1px solid #ddd; background: white; border-radius: 3px; padding: 8px; cursor: pointer; font-size: 13px; }
.modes button.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.lbl { display: block; font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; }
.preview { margin: -4px 0 12px; font-size: 14px; }
.preview .bad { color: #d33; }
.error { color: #d33; font-size: 13px; margin: 0; }
@media (max-width: 1000px) { .cards { grid-template-columns: repeat(2, 1fr); } }
</style>
