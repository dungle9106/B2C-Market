<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Quản Lý Đơn Hàng</h2>
      <span class="sc-card-sub">Tự cập nhật mỗi 30 giây · lần cuối {{ lastLoaded }}</span>
    </div>

    <nav class="sc-tabs">
      <router-link
        v-for="t in tabs"
        :key="t.key"
        :to="{ name: 'seller-orders', query: t.key === 'ALL' ? {} : { status: t.key } }"
        :class="{ active: activeTab === t.key, alert: t.key === 'CANCEL_REQUESTS' && countFor(t.key) }"
      >
        {{ t.label }} <span v-if="countFor(t.key)" class="count">({{ countFor(t.key) }})</span>
      </router-link>
    </nav>

    <!-- "Cho lay hang" is split like Shopee: not packed yet / packed and waiting for the carrier -->
    <div v-if="activeTab === 'CONFIRMED'" class="sub-tabs">
      <button type="button" :class="{ on: packedFilter === 'todo' }" @click="packedFilter = 'todo'">Chưa xử lý ({{ confirmedCount(false) }})</button>
      <button type="button" :class="{ on: packedFilter === 'done' }" @click="packedFilter = 'done'">Đã xử lý ({{ confirmedCount(true) }})</button>
    </div>

    <div class="filters">
      <input v-model.trim="keyword" placeholder="Mã đơn, mã vận đơn, tên người mua hoặc sản phẩm" class="search" />
      <label>Từ <input v-model="dateFrom" type="date" /></label>
      <label>Đến <input v-model="dateTo" type="date" /></label>
      <select v-model="payment">
        <option value="">Mọi thanh toán</option>
        <option value="COD">Thanh toán khi nhận hàng</option>
        <option value="ONLINE">Đã thanh toán online</option>
      </select>
      <select v-model="carrier">
        <option value="">Mọi đơn vị vận chuyển</option>
        <option v-for="c in carriers" :key="c.code" :value="c.code">{{ c.name }}</option>
      </select>
      <select v-model="sort">
        <option value="newest">Mới nhất</option>
        <option value="oldest">Cũ nhất (xử lý trước)</option>
      </select>
      <button v-if="hasFilters" type="button" class="link" @click="clearFilters">Xóa bộ lọc</button>
    </div>

    <!-- bulk actions -->
    <div v-if="filtered.length" class="bulk-bar" :class="{ active: selected.size }">
      <label class="check-all">
        <input type="checkbox" :checked="allSelected" :indeterminate="selected.size > 0 && !allSelected" @change="toggleAll" />
        {{ selected.size ? `Đã chọn ${selected.size} đơn` : 'Chọn tất cả' }}
      </label>
      <template v-if="selected.size">
        <button type="button" class="btn sm" :disabled="busy || !canBulk('CONFIRM')" @click="bulk('CONFIRM')">Xác nhận ({{ countSelected(o => o.status === 'PENDING') }})</button>
        <button type="button" class="btn sm" :disabled="busy || !canBulk('PREPARE')" @click="bulkPreparing = true">Chuẩn bị hàng ({{ countSelected(isToPack) }})</button>
        <button type="button" class="btn sm ghost" :disabled="!countSelected(isPrintable)" @click="printSelected">🖨 In phiếu ({{ countSelected(isPrintable) }})</button>
        <button type="button" class="btn sm ghost" :disabled="busy || !canBulk('SHIP')" @click="bulk('SHIP')">Đã giao cho ĐVVC ({{ countSelected(isPacked) }})</button>
        <button type="button" class="link" @click="selected.clear()">Bỏ chọn</button>
      </template>
      <span class="spacer"></span>
      <span class="text-muted">{{ filtered.length }} đơn hàng</span>
    </div>
    <p v-if="notice" class="notice" :class="notice.kind">{{ notice.text }}</p>

    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="filtered.length === 0" class="sc-empty">
      <svg width="56" height="56" viewBox="0 0 24 24" fill="none"><path d="M6 3h12v18H6z M9 8h6M9 12h6M9 16h4" stroke="currentColor" stroke-width="1.4" /></svg>
      Không có đơn hàng nào
    </div>

    <article v-for="o in filtered" v-else :key="o.id" class="order" :class="{ picked: selected.has(o.id), fresh: freshIds.has(o.id) }">
      <header class="order-head">
        <input type="checkbox" :checked="selected.has(o.id)" :aria-label="`Chọn đơn ${o.code}`" @change="toggle(o.id)" />
        <span class="buyer">👤 {{ o.buyerName }}</span>
        <router-link :to="{ name: 'seller-order-detail', params: { id: o.id } }" class="code">Mã đơn {{ o.code }}</router-link>
        <span class="text-faint">{{ formatDateTime(o.createdAt) }}</span>
        <span v-if="o.paymentMethod === 'COD'" class="badge cod">COD</span>
        <span v-if="o.cancelRequestedAt && o.status === 'CONFIRMED'" class="badge warn">Yêu cầu hủy</span>
        <span v-if="o.labelPrintedAt" class="badge ok">Đã in phiếu</span>
        <span class="spacer"></span>
        <span class="status-tag" :class="o.status">{{ statusText(o) }}</span>
      </header>
      <div class="order-body">
        <div class="items">
          <div v-for="item in o.items" :key="item.id" class="item">
            <img :src="item.thumbnailUrl || placeholder" alt="" />
            <div class="item-info">
              <div class="item-name">{{ item.productName }}</div>
              <div v-if="item.variantName" class="text-faint small">Phân loại: {{ item.variantName }}</div>
            </div>
            <span class="qty">x{{ item.quantity }}</span>
          </div>
          <div v-if="o.note" class="buyer-note">✉ Lời nhắn: {{ o.note }}</div>
          <div v-if="o.cancelRequestedAt && o.status === 'CONFIRMED'" class="cancel-req">
            Người mua yêu cầu hủy lúc {{ formatDateTime(o.cancelRequestedAt) }}: <strong>{{ o.cancelRequestReason }}</strong>
          </div>
        </div>
        <div class="money">
          <div class="total">{{ formatVnd(o.total) }}</div>
          <div class="text-faint small">{{ paymentLabel(o) }}</div>
        </div>
        <div class="ship">
          <div>{{ shippingLabel(o) }}</div>
          <div v-if="o.trackingNumber" class="small">Mã vận đơn: <strong>{{ o.trackingNumber }}</strong></div>
          <div v-if="o.packedAt && o.status === 'CONFIRMED'" class="small text-faint">
            {{ o.pickupMethod === 'DROPOFF' ? 'Gửi bưu cục' : 'Lấy hàng' }} ngày {{ formatDay(o.pickupDate) }}
          </div>
          <div v-if="o.status === 'CANCELLED' && o.cancelReason" class="small text-faint">
            Hủy bởi {{ o.cancelledBy === 'SELLER' ? 'shop' : o.cancelledBy === 'SYSTEM' ? 'hệ thống' : 'người mua' }}: {{ o.cancelReason }}
          </div>
          <div v-if="o.returnRequest" class="small text-faint">{{ RETURN_STATUS[o.returnRequest.status] }}</div>
        </div>
        <SellerOrderActions :order="o" detail-link vertical class="order-actions" @changed="load(true)" />
      </div>
    </article>

    <PrepareOrderDialog v-if="bulkPreparing" :count="countSelected(isToPack)"
                        :action="(m, d) => runBulk('PREPARE', m, d)" @close="bulkPreparing = false" @done="bulkPreparing = false" />
  </section>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { shopOrders, bulkOrders } from '../../api/orders'
import { carrierOptions } from '../../api/shop'
import { formatVnd, formatDateTime } from '../../utils/format'
import { statusLabel, paymentLabel, shippingLabel, RETURN_STATUSES } from '../../utils/orderStatus'
import { RETURN_STATUS } from '../../utils/returns'
import SellerOrderActions from '../../components/orders/SellerOrderActions.vue'
import PrepareOrderDialog from '../../components/orders/PrepareOrderDialog.vue'
import { extractError } from '../../utils/apiError'

const REFRESH_MS = 30000
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48"><rect width="48" height="48" fill="%23f0f0f0"/></svg>'

const route = useRoute()
const router = useRouter()
const orders = ref([])
const carriers = ref([])
const loading = ref(true)
const lastLoaded = ref('')
const keyword = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const payment = ref('')
const carrier = ref('')
const sort = ref('newest')
const packedFilter = ref('todo')
const selected = reactive(new Set())
const freshIds = reactive(new Set()) // orders that arrived since the page was opened
const busy = ref(false)
const bulkPreparing = ref(false)
const notice = ref(null)
let timer = null

const tabs = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'AWAITING_PAYMENT', label: 'Chờ thanh toán' },
  { key: 'PENDING', label: 'Chờ xác nhận' },
  { key: 'CONFIRMED', label: 'Chờ lấy hàng' },
  { key: 'SHIPPING', label: 'Đang giao' },
  { key: 'DELIVERED', label: 'Đã giao' },
  { key: 'CANCEL_REQUESTS', label: 'Yêu cầu hủy' },
  { key: 'RETURNS', label: 'Đơn hủy / Trả hàng / Hoàn tiền' }
]
const activeTab = computed(() => route.query.status || 'ALL')

const isCancelRequest = (o) => o.status === 'CONFIRMED' && !!o.cancelRequestedAt
const isToPack = (o) => o.status === 'CONFIRMED' && !o.packedAt && !o.cancelRequestedAt
const isPacked = (o) => o.status === 'CONFIRMED' && !!o.packedAt && !o.cancelRequestedAt
const isPrintable = (o) => !!o.trackingNumber && ['CONFIRMED', 'SHIPPING'].includes(o.status)

function inTab(order, key) {
  if (key === 'ALL') return true
  if (key === 'RETURNS') return RETURN_STATUSES.includes(order.status)
  if (key === 'CANCEL_REQUESTS') return isCancelRequest(order)
  return order.status === key
}
const countFor = (key) => (key === 'ALL' ? 0 : orders.value.filter(o => inTab(o, key)).length)
const confirmedCount = (packed) => orders.value.filter(o => o.status === 'CONFIRMED' && !!o.packedAt === packed).length

const hasFilters = computed(() => keyword.value || dateFrom.value || dateTo.value || payment.value || carrier.value)
function clearFilters() {
  keyword.value = dateFrom.value = dateTo.value = payment.value = carrier.value = ''
}

const filtered = computed(() => {
  const q = keyword.value.toLowerCase()
  const from = dateFrom.value ? new Date(dateFrom.value + 'T00:00:00') : null
  const to = dateTo.value ? new Date(dateTo.value + 'T23:59:59') : null
  const list = orders.value.filter(o => {
    if (!inTab(o, activeTab.value)) return false
    if (activeTab.value === 'CONFIRMED' && !!o.packedAt !== (packedFilter.value === 'done')) return false
    if (q && !(o.code.toLowerCase().includes(q) || (o.trackingNumber || '').toLowerCase().includes(q)
      || (o.buyerName || '').toLowerCase().includes(q) || o.items.some(i => i.productName.toLowerCase().includes(q)))) return false
    const created = new Date(o.createdAt)
    if (from && created < from) return false
    if (to && created > to) return false
    if (payment.value === 'COD' && o.paymentMethod !== 'COD') return false
    if (payment.value === 'ONLINE' && o.paymentMethod === 'COD') return false
    if (carrier.value && o.carrier !== carrier.value) return false
    return true
  })
  return sort.value === 'oldest' ? list.slice().reverse() : list
})

const allSelected = computed(() => filtered.value.length > 0 && filtered.value.every(o => selected.has(o.id)))
const selectedOrders = computed(() => orders.value.filter(o => selected.has(o.id)))
const countSelected = (pred) => selectedOrders.value.filter(pred).length
const canBulk = (action) => countSelected({ CONFIRM: o => o.status === 'PENDING', PREPARE: isToPack, SHIP: isPacked }[action]) > 0

function statusText(o) {
  if (o.status === 'CONFIRMED') return o.packedAt ? 'Chờ ĐVVC lấy hàng' : 'Chờ chuẩn bị hàng'
  return statusLabel(o.status)
}
const formatDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '')

function toggle(id) {
  selected.has(id) ? selected.delete(id) : selected.add(id)
}
function toggleAll() {
  if (allSelected.value) filtered.value.forEach(o => selected.delete(o.id))
  else filtered.value.forEach(o => selected.add(o.id))
}

function flash(text, kind = 'ok') {
  notice.value = { text, kind }
  setTimeout(() => { if (notice.value?.text === text) notice.value = null }, 6000)
}

// quiet: background refresh (no spinner); new orders are highlighted
async function load(quiet = false) {
  if (!quiet) loading.value = true
  try {
    const { data } = await shopOrders()
    if (orders.value.length) {
      const known = new Set(orders.value.map(o => o.id))
      data.filter(o => !known.has(o.id)).forEach(o => freshIds.add(o.id))
    }
    orders.value = data
    for (const id of [...selected]) if (!data.some(o => o.id === id)) selected.delete(id)
    lastLoaded.value = new Date().toLocaleTimeString('vi-VN')
  } finally {
    loading.value = false
  }
}

const BULK_LABELS = { CONFIRM: 'xác nhận', PREPARE: 'chuẩn bị hàng cho', SHIP: 'giao cho ĐVVC' }
async function runBulk(action, pickupMethod, pickupDate) {
  const ids = [...selected]
  const { data } = await bulkOrders(ids, action, pickupMethod, pickupDate)
  const skipped = data.skipped.length ? ` · Bỏ qua ${data.skipped.length} đơn: ${data.skipped.map(s => `${s.code} (${s.reason})`).join('; ')}` : ''
  flash(`Đã ${BULK_LABELS[action]} ${data.updated} đơn${skipped}`, data.skipped.length ? 'warn' : 'ok')
  await load(true)
  return data
}

async function bulk(action) {
  if (action === 'SHIP' && !confirm(`Xác nhận đã giao ${countSelected(isPacked)} đơn cho đơn vị vận chuyển?`)) return
  busy.value = true
  try {
    await runBulk(action)
  } catch (e) {
    flash(extractError(e), 'error')
  } finally {
    busy.value = false
  }
}

function printSelected() {
  const ids = selectedOrders.value.filter(isPrintable).map(o => o.id)
  const url = router.resolve({ name: 'seller-print-labels', query: { ids: ids.join(',') } }).href
  window.open(url, '_blank')
}

watch(activeTab, () => selected.clear())

onMounted(async () => {
  load()
  carrierOptions().then(({ data }) => { carriers.value = data }).catch(() => {})
  timer = setInterval(() => { if (!document.hidden) load(true) }, REFRESH_MS)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.sc-tabs a.alert { color: #d97706; }
.sub-tabs { display: flex; gap: 8px; margin: -4px 0 14px; }
.sub-tabs button { border: 1px solid #ddd; background: white; border-radius: 999px; padding: 5px 14px; font-size: 13px; cursor: pointer; }
.sub-tabs button.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }

.filters { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 12px; }
.filters input, .filters select { margin: 0; width: auto; }
.filters .search { flex: 1; min-width: 240px; }
.filters label { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text-muted); }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }

.bulk-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 8px 12px; border: 1px solid var(--color-border); border-radius: 4px; margin-bottom: 12px; font-size: 14px; }
.bulk-bar.active { background: #fff8f6; border-color: #ffd9cf; }
.check-all { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.check-all input, .order-head input { width: 16px; height: 16px; margin: 0; accent-color: var(--color-primary); }
.spacer { flex: 1; }
.notice { font-size: 13px; padding: 8px 12px; border-radius: 4px; margin: 0 0 12px; }
.notice.ok { background: #eaf7ee; color: #1e7e43; }
.notice.warn { background: #fff8eb; color: #a16207; }
.notice.error { background: #fdecea; color: #d33; }

.order { border: 1px solid var(--color-border); border-radius: 4px; margin-bottom: 12px; transition: box-shadow 0.3s; }
.order.picked { border-color: #ffbfae; }
.order.fresh { box-shadow: 0 0 0 2px #ffd38a; }
.order-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 10px 14px; background: #fafafa; border-bottom: 1px solid var(--color-border); font-size: 13px; }
.buyer { font-weight: 500; }
.code { color: var(--color-text); }
.code:hover { color: var(--color-primary); }
.badge { font-size: 11px; padding: 1px 6px; border-radius: 2px; }
.badge.cod { background: #fff4e5; color: #d97706; }
.badge.warn { background: #fdecea; color: #d33; }
.badge.ok { background: #eaf7ee; color: #1e9e4a; }
.order-body { display: grid; grid-template-columns: minmax(0, 1fr) 150px 200px 170px; gap: 16px; padding: 12px 14px; align-items: start; }
.item { display: flex; align-items: center; gap: 10px; padding: 4px 0; }
.item img { width: 48px; height: 48px; object-fit: cover; border-radius: 2px; border: 1px solid var(--color-border); }
.item-info { flex: 1; min-width: 0; }
.item-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qty { color: var(--color-text-muted); }
.small { font-size: 12px; }
.buyer-note { font-size: 12px; margin-top: 6px; background: #fff8e6; padding: 4px 8px; border-radius: 3px; }
.cancel-req { font-size: 12px; margin-top: 6px; background: #fdecea; color: #b42318; padding: 6px 8px; border-radius: 3px; }
.total { color: var(--color-primary); font-weight: 500; }
.ship { font-size: 13px; display: flex; flex-direction: column; gap: 3px; }

@media (max-width: 1100px) {
  .order-body { grid-template-columns: 1fr 1fr; }
}
</style>
