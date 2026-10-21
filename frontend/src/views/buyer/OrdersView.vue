<template>
  <div>
    <AppHeader />
    <div class="container page">
      <div v-if="placedCode" class="placed-banner">
        <div>
          <strong>✓ Đặt hàng thành công!</strong> Người Bán sẽ sớm xác nhận đơn hàng của bạn.
          <ul v-if="placedOrders.length" class="placed-list">
            <li v-for="o in placedOrders" :key="o.id">
              Mã đơn hàng <strong class="placed-code">{{ o.code }}</strong> · {{ o.shopName }} · {{ formatVnd(o.total) }}
            </li>
          </ul>
        </div>
        <button type="button" aria-label="Đóng" @click="placedCode = null">✕</button>
      </div>

      <nav class="tabs">
        <button v-for="t in tabs" :key="t.value" type="button" :class="{ active: activeTab === t.value }" @click="setTab(t.value)">
          {{ t.label }}<span v-if="countOf(t) && t.value !== 'ALL'" class="count"> ({{ countOf(t) }})</span>
        </button>
      </nav>

      <div class="search">
        <input v-model.trim="keyword" placeholder="Bạn có thể tìm kiếm theo tên Shop, Mã đơn hàng hoặc Tên sản phẩm" />
      </div>

      <div v-if="loading" class="empty">Đang tải...</div>
      <div v-else-if="!filtered.length" class="empty">
        <svg width="80" height="80" viewBox="0 0 24 24" fill="none"><path d="M6 3h12v18H6z M9 8h6M9 12h6M9 16h4" stroke="currentColor" stroke-width="1.2" /></svg>
        <p>Chưa có đơn hàng</p>
      </div>

      <article v-for="o in filtered" :key="o.id" class="order-card">
        <header class="order-top">
          <span class="shop"><span class="shop-badge">Shop</span> {{ o.shopName }}</span>
          <span class="status">
            <router-link v-if="o.trackingNumber && ['SHIPPING', 'DELIVERED'].includes(o.status)" :to="{ name: 'order-detail', params: { id: o.id } }" class="track">
              🚚 {{ trackingHint(o) }}
            </router-link>
            <span class="status-text">{{ statusText(o) }}</span>
          </span>
        </header>

        <router-link :to="{ name: 'order-detail', params: { id: o.id } }" class="items">
          <div v-for="item in o.items" :key="item.id" class="item">
            <img :src="item.thumbnailUrl || placeholder" alt="" />
            <div class="item-info">
              <div class="item-name">{{ item.productName }}</div>
              <div v-if="item.variantName" class="text-faint small">Phân loại hàng: {{ item.variantName }}</div>
              <div class="small">x{{ item.quantity }}</div>
            </div>
            <span class="item-price">{{ formatVnd(item.priceAtPurchase) }}</span>
          </div>
        </router-link>

        <!-- per-line reviews once received -->
        <div v-if="canReview(o) && o.items.some(i => !i.reviewed)" class="review-row">
          <span class="text-muted small">Đánh giá sản phẩm để giúp người mua khác và nhận xét về Shop</span>
          <button v-for="item in o.items.filter(i => !i.reviewed)" :key="item.id" type="button" class="btn sm" @click="openReview(o, item)">
            Đánh giá {{ o.items.length > 1 ? '"' + shorten(item.productName) + '"' : '' }}
          </button>
        </div>

        <footer class="order-bottom">
          <div class="total-line">Thành tiền: <strong>{{ formatVnd(o.total) }}</strong></div>
          <div class="bottom-row">
            <span class="text-faint small">{{ footNote(o) }}</span>
            <OrderActions :order="o" show-detail-link @changed="load" />
          </div>
        </footer>
      </article>
    </div>

    <ReviewDialog v-if="reviewing" :item="reviewing.item" :ask-shop="!reviewing.order.shopRating"
                  @close="reviewing = null" @done="onReviewed" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import ReviewDialog from '../../components/ReviewDialog.vue'
import OrderActions from '../../components/orders/OrderActions.vue'
import { myOrders } from '../../api/orders'
import { statusLabel } from '../../utils/orderStatus'
import { RETURN_STATUS } from '../../utils/returns'
import { formatVnd } from '../../utils/format'

const route = useRoute()
const router = useRouter()
const orders = ref([])
const loading = ref(true)
const keyword = ref('')
const reviewing = ref(null)
// set by the checkout page after a COD order
const placedCode = ref(route.query.placed || null)
const placedOrders = computed(() => orders.value.filter(o => o.checkoutCode === placedCode.value))
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="%23f0f0f0"/></svg>'

const tabs = [
  { value: 'ALL', label: 'Tất cả', match: () => true },
  { value: 'AWAITING_PAYMENT', label: 'Chờ thanh toán', match: (o) => o.status === 'AWAITING_PAYMENT' },
  { value: 'PENDING', label: 'Chờ xác nhận', match: (o) => o.status === 'PENDING' },
  { value: 'CONFIRMED', label: 'Chờ lấy hàng', match: (o) => o.status === 'CONFIRMED' },
  { value: 'SHIPPING', label: 'Đang giao', match: (o) => o.status === 'SHIPPING' },
  { value: 'DELIVERED', label: 'Hoàn thành', match: (o) => o.status === 'DELIVERED' },
  { value: 'CANCELLED', label: 'Đã hủy', match: (o) => o.status === 'CANCELLED' },
  { value: 'RETURNS', label: 'Trả hàng/Hoàn tiền', match: (o) => ['RETURN_REQUESTED', 'REFUNDED'].includes(o.status) || o.returnRequest?.status === 'REJECTED' }
]
// the tab lives in the URL so links like "Tra hang/Hoan tien" from elsewhere work
const activeTab = computed(() => (tabs.some(t => t.value === route.query.tab) ? route.query.tab : 'ALL'))
function setTab(value) {
  router.replace({ query: { ...route.query, tab: value === 'ALL' ? undefined : value } })
}
const countOf = (t) => orders.value.filter(t.match).length

const filtered = computed(() => {
  const tab = tabs.find(t => t.value === activeTab.value)
  const q = keyword.value.toLowerCase()
  return orders.value.filter(tab.match).filter(o => !q
    || o.code.toLowerCase().includes(q)
    || o.shopName.toLowerCase().includes(q)
    || o.items.some(i => i.productName.toLowerCase().includes(q)))
})

const canReview = (o) => ['DELIVERED', 'REFUNDED', 'RETURN_REQUESTED'].includes(o.status)
const shorten = (s) => (s.length > 18 ? s.slice(0, 18) + '…' : s)

function statusText(o) {
  if (o.returnRequest && ['RETURN_REQUESTED', 'REFUNDED'].includes(o.status)) return RETURN_STATUS[o.returnRequest.status].toUpperCase()
  return statusLabel(o.status).toUpperCase()
}
function trackingHint(o) {
  if (o.status === 'DELIVERED') return 'Giao hàng thành công'
  return `Đang vận chuyển · ${o.trackingNumber}`
}
const fmtDay = (d) => new Date(d).toLocaleDateString('vi-VN')
function footNote(o) {
  if (o.status === 'CANCELLED') return `Đã hủy bởi ${o.cancelledBy === 'SELLER' ? 'Người bán' : o.cancelledBy === 'SYSTEM' ? 'hệ thống' : 'bạn'}${o.cancelReason ? ': ' + o.cancelReason : ''}`
  if (o.canReturn && o.returnDeadline) return `Có thể trả hàng/hoàn tiền đến ${fmtDay(o.returnDeadline)}`
  if (o.status === 'SHIPPING' && o.estimatedDeliveryTo) return `Dự kiến nhận hàng trước ${fmtDay(o.estimatedDeliveryTo)}`
  return `Mã đơn hàng: ${o.code}`
}

function openReview(order, item) { reviewing.value = { order, item } }
function onReviewed() {
  reviewing.value.item.reviewed = true
  reviewing.value = null
  load()
}

async function load() {
  try {
    const { data } = await myOrders()
    orders.value = data
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.page { padding-top: 16px; padding-bottom: 40px; }
.tabs { display: flex; background: white; overflow-x: auto; border-radius: 2px; position: sticky; top: 0; z-index: 2; }
.tabs button { flex: 1; min-width: 110px; background: none; border: none; border-bottom: 2px solid rgba(0, 0, 0, 0.09); padding: 16px 8px; cursor: pointer; font-size: 15px; white-space: nowrap; }
.tabs button.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.count { color: inherit; }
.search { margin: 12px 0; }
.search input { margin: 0; background: #eaeaea; border: none; padding: 12px 16px; }
.empty { background: white; text-align: center; padding: 60px; color: var(--color-text-muted); }
.empty svg { color: #ddd; }

.order-card { background: white; margin-bottom: 12px; border-radius: 2px; box-shadow: 0 1px 1px rgba(0, 0, 0, 0.05); }
.order-top { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 14px 24px; border-bottom: 1px solid rgba(0, 0, 0, 0.09); flex-wrap: wrap; }
.shop { font-weight: 600; display: flex; align-items: center; gap: 8px; }
.shop-badge { background: var(--color-primary); color: white; font-size: 11px; padding: 1px 5px; border-radius: 2px; font-weight: 400; }
.status { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.track { color: #26aa99; border-right: 1px solid rgba(0, 0, 0, 0.12); padding-right: 10px; }
.status-text { color: var(--color-primary); }
.items { display: block; padding: 0 24px; }
.item { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f5f5f5; }
.item img { width: 80px; height: 80px; object-fit: cover; border: 1px solid #e1e1e1; flex-shrink: 0; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 15px; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-price { color: var(--color-primary); }
.small { font-size: 13px; }
.review-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 24px; background: #fffcf5; }
.review-row .text-muted { margin-right: auto; }
.order-bottom { padding: 16px 24px 20px; background: #fffefb; border-top: 1px dashed rgba(0, 0, 0, 0.09); }
.total-line { text-align: right; font-size: 14px; margin-bottom: 14px; }
.total-line strong { color: var(--color-primary); font-size: 22px; font-weight: 500; margin-left: 8px; }
.bottom-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }

.placed-banner { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; background: #e8f8f5; color: #0f9d8a; padding: 12px 16px; border-radius: 4px; margin-bottom: 12px; }
.placed-banner button { background: none; border: none; cursor: pointer; color: inherit; }
.placed-list { margin: 8px 0 0; padding-left: 18px; color: var(--color-text); font-size: 14px; }
.placed-code { color: var(--color-primary); letter-spacing: 0.3px; }

@media (max-width: 640px) {
  .order-top, .items, .order-bottom, .review-row { padding-left: 12px; padding-right: 12px; }
  .item img { width: 60px; height: 60px; }
}
</style>
