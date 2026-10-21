<template>
  <div>
    <AppHeader />
    <div class="container page">
      <div v-if="loadError" class="card empty">{{ loadError }}</div>

      <template v-else-if="order">
        <!-- header -->
        <div class="card head">
          <router-link :to="{ name: 'my-orders' }" class="back">‹ TRỞ LẠI</router-link>
          <span class="code">MÃ ĐƠN HÀNG. {{ order.code }}</span>
          <span class="sep">|</span>
          <span class="status">{{ headline }}</span>
        </div>

        <!-- progress stepper -->
        <div v-if="order.status !== 'CANCELLED'" class="card stepper-card">
          <ol class="stepper">
            <li v-for="(s, i) in steps" :key="s.label" :class="{ done: s.time, current: i === currentStep }">
              <span class="circle">{{ s.icon }}</span>
              <span class="label">{{ s.label }}</span>
              <span class="time">{{ s.time ? fmtTime(s.time) : '' }}</span>
            </li>
          </ol>
        </div>
        <div v-else class="card cancelled">
          <strong>Đã hủy đơn hàng</strong> vào {{ fmtTime(order.cancelledAt) }} ·
          bởi {{ order.cancelledBy === 'SELLER' ? 'Người bán' : order.cancelledBy === 'SYSTEM' ? 'Hệ thống' : 'bạn' }}
          <div class="text-muted">Lý do: {{ order.cancelReason }}</div>
        </div>

        <div v-if="order.cancelRequestedAt && order.status === 'CONFIRMED'" class="card req-note">
          Bạn đã gửi yêu cầu hủy đơn lúc {{ fmtTime(order.cancelRequestedAt) }} ({{ order.cancelRequestReason }}). Người bán sẽ phản hồi trước khi giao hàng.
        </div>
        <div v-else-if="order.cancelRequestRejectedReason && order.status !== 'CANCELLED'" class="card req-note rejected">
          Người bán đã từ chối yêu cầu hủy: {{ order.cancelRequestRejectedReason }}
        </div>

        <!-- actions -->
        <div class="card actions-card">
          <span class="text-muted small">{{ actionHint }}</span>
          <OrderActions :order="order" @changed="load" />
        </div>

        <!-- return / refund request -->
        <section v-if="order.returnRequest" class="card return-card">
          <h2 class="card-title">Yêu cầu Trả hàng/Hoàn tiền · <span class="text-primary">{{ RETURN_STATUS[order.returnRequest.status] }}</span></h2>
          <dl class="kv">
            <dt>Loại yêu cầu</dt><dd>{{ RETURN_TYPES[order.returnRequest.type] }}</dd>
            <dt>Lý do</dt><dd>{{ RETURN_REASONS[order.returnRequest.reason] }}<template v-if="order.returnRequest.description"> — {{ order.returnRequest.description }}</template></dd>
            <dt>Sản phẩm</dt><dd><div v-for="l in order.returnRequest.items" :key="l.orderItemId">{{ l.productName }} x{{ l.quantity }}</div></dd>
            <dt>Số tiền hoàn</dt><dd class="text-primary"><strong>{{ formatVnd(order.returnRequest.refundAmount) }}</strong></dd>
            <template v-if="order.returnRequest.sellerNote"><dt>Người bán phản hồi</dt><dd>{{ order.returnRequest.sellerNote }}</dd></template>
            <template v-if="order.returnRequest.complaint"><dt>Nội dung khiếu nại</dt><dd>{{ order.returnRequest.complaint }}</dd></template>
            <template v-if="order.returnRequest.adminNote"><dt>Kết luận của Shoppi</dt><dd>{{ order.returnRequest.adminNote }}</dd></template>
          </dl>
          <div class="evidence">
            <template v-for="url in order.returnRequest.evidenceUrls" :key="url">
              <video v-if="isVideo(url)" :src="url" controls preload="metadata"></video>
              <a v-else :href="url" target="_blank" rel="noopener"><img :src="url" alt="Bằng chứng" /></a>
            </template>
          </div>
          <!-- a dispute: talk with the seller and Shoppi, add evidence -->
          <DisputeThread v-if="order.returnRequest.complaint" :return-id="order.returnRequest.id" side="BUYER"
                         :open="order.returnRequest.status === 'ESCALATED'" :deadline="order.returnRequest.sellerResponseDeadline" />
        </section>

        <!-- money back to the buyer -->
        <section v-if="order.refunds?.length" class="card">
          <h2 class="card-title">Hoàn Tiền</h2>
          <div v-for="r in order.refunds" :key="r.id" class="refund">
            <div>
              <strong class="text-primary">{{ formatVnd(r.amount) }}</strong>
              <span class="refund-st" :class="r.status">{{ REFUND_STATUS[r.status] }}</span>
              <div class="text-muted small">{{ REFUND_SOURCE[r.source] }} · {{ REFUND_CHANNEL[r.channel] }}</div>
            </div>
            <div class="text-muted small right">
              <template v-if="r.status === 'COMPLETED'">Hoàn lúc {{ fmtTime(r.completedAt) }}<br />Mã giao dịch: {{ r.reference }}</template>
              <template v-else-if="r.status === 'PENDING'">Shoppi sẽ chuyển khoản trong 3-5 ngày làm việc</template>
              <template v-else>{{ r.note }} · Vui lòng liên hệ Trung tâm hỗ trợ</template>
            </div>
          </div>
        </section>

        <!-- address + tracking -->
        <section class="card">
          <div class="address-tracking">
            <div class="address">
              <h2 class="card-title">Địa Chỉ Nhận Hàng</h2>
              <p class="addr">{{ order.shippingAddress }}</p>
              <p class="text-muted small">
                {{ order.carrierName }} · {{ order.shippingMethodName }}<br />
                <template v-if="order.trackingNumber">Mã vận đơn: <strong class="tracking">{{ order.trackingNumber }}</strong></template>
                <template v-else-if="order.estimatedDeliveryFrom">Dự kiến giao {{ fmtDay(order.estimatedDeliveryFrom) }} - {{ fmtDay(order.estimatedDeliveryTo) }}</template>
              </p>
            </div>
            <ol class="timeline">
              <li v-for="(e, i) in timeline" :key="i" :class="{ latest: i === 0 }">
                <span class="dot"></span>
                <span class="t-time">{{ fmtTime(e.time) }}</span>
                <div class="t-body">
                  <div class="t-title">{{ e.title }}</div>
                  <div v-if="e.location" class="t-loc">{{ e.location }}</div>
                </div>
              </li>
            </ol>
          </div>
        </section>

        <!-- items + money -->
        <section class="card">
          <div class="shop-line">
            <span class="shop-badge">Shop</span> <strong>{{ order.shopName }}</strong>
            <router-link :to="{ name: 'messages', query: { shopId: order.shopId, orderId: order.id } }" class="btn sm chat-btn">💬 Chat với Shop</router-link>
            <router-link :to="{ name: 'help-new', query: { orderId: order.id, category: 'ORDER' } }" class="help-link">Cần Shoppi hỗ trợ?</router-link>
          </div>
          <div v-for="item in order.items" :key="item.id" class="item">
            <router-link :to="`/products/${item.productId}`"><img :src="item.thumbnailUrl || placeholder" alt="" /></router-link>
            <div class="item-info">
              <router-link :to="`/products/${item.productId}`" class="item-name">{{ item.productName }}</router-link>
              <div v-if="item.variantName" class="text-faint small">Phân loại hàng: {{ item.variantName }}</div>
              <div class="small">x{{ item.quantity }} <span v-if="!item.returnable" class="text-faint">· không áp dụng đổi trả</span></div>
            </div>
            <div class="item-right">
              <span class="text-primary">{{ formatVnd(item.priceAtPurchase) }}</span>
              <button v-if="canReview && !item.reviewed" type="button" class="btn sm" @click="reviewing = item">Đánh giá</button>
              <span v-else-if="item.reviewed" class="reviewed">✓ Đã đánh giá</span>
            </div>
          </div>

          <dl class="money">
            <dt>Tổng tiền hàng</dt><dd>{{ formatVnd(order.subtotal) }}</dd>
            <dt>Phí vận chuyển</dt><dd>{{ formatVnd(order.shippingFee) }}</dd>
            <template v-if="order.shippingDiscount > 0"><dt>Giảm giá phí vận chuyển</dt><dd>-{{ formatVnd(order.shippingDiscount) }}</dd></template>
            <template v-if="order.comboDiscount > 0"><dt>Combo khuyến mãi</dt><dd>-{{ formatVnd(order.comboDiscount) }}</dd></template>
            <template v-if="order.shopDiscount > 0"><dt>Voucher của Shop ({{ order.shopVoucherCode }})</dt><dd>-{{ formatVnd(order.shopDiscount) }}</dd></template>
            <template v-if="order.platformDiscount > 0"><dt>Shoppi Voucher ({{ order.platformVoucherCode }})</dt><dd>-{{ formatVnd(order.platformDiscount) }}</dd></template>
            <dt>Thành tiền</dt><dd class="grand">{{ formatVnd(order.total) }}</dd>
            <dt>Phương thức thanh toán</dt><dd>{{ paymentLabel(order) }}</dd>
            <template v-if="order.note"><dt>Lời nhắn cho Người bán</dt><dd>{{ order.note }}</dd></template>
          </dl>
        </section>
      </template>

      <div v-else class="card empty">Đang tải...</div>
    </div>

    <ReviewDialog v-if="reviewing" :item="reviewing" :ask-shop="!order.shopRating" @close="reviewing = null" @done="onReviewed" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import ReviewDialog from '../../components/ReviewDialog.vue'
import OrderActions from '../../components/orders/OrderActions.vue'
import DisputeThread from '../../components/orders/DisputeThread.vue'
import { getOrder } from '../../api/orders'
import { statusLabel, paymentLabel } from '../../utils/orderStatus'
import { RETURN_STATUS, RETURN_TYPES, RETURN_REASONS, REFUND_STATUS, REFUND_SOURCE, REFUND_CHANNEL } from '../../utils/returns'
import { formatVnd } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const route = useRoute()
const order = ref(null)
const loadError = ref('')
const reviewing = ref(null)
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="%23f0f0f0"/></svg>'

const fmtTime = (d) => (d ? new Date(d).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' }) : '')
const fmtDay = (d) => new Date(d).toLocaleDateString('vi-VN')
const isVideo = (url) => /\.(mp4|webm)$/i.test(url)
const canReview = computed(() => ['DELIVERED', 'REFUNDED', 'RETURN_REQUESTED'].includes(order.value?.status))

const headline = computed(() => {
  const o = order.value
  if (o.returnRequest && ['RETURN_REQUESTED', 'REFUNDED'].includes(o.status)) return RETURN_STATUS[o.returnRequest.status].toUpperCase()
  return statusLabel(o.status).toUpperCase()
})

// Shopee-style 5-step progress
const eventTime = (stage) => order.value.events.find(e => e.stage === stage)?.time
const steps = computed(() => {
  const o = order.value
  return [
    { icon: '📝', label: 'Đơn Hàng Đã Đặt', time: o.createdAt },
    { icon: '💵', label: o.paymentMethod === 'COD' ? 'Đã Xác Nhận' : 'Đã Thanh Toán', time: o.paymentMethod === 'COD' ? o.confirmedAt : eventTime('PAID') },
    { icon: '🚚', label: 'Đã Giao Cho ĐVVC', time: o.shippedAt },
    { icon: '📦', label: 'Đã Nhận Được Hàng', time: o.deliveredAt },
    { icon: '⭐', label: 'Đánh Giá', time: o.items.every(i => i.reviewed) ? (o.deliveredAt || o.createdAt) : null }
  ]
})
const currentStep = computed(() => {
  const idx = steps.value.findIndex(s => !s.time)
  return idx === -1 ? steps.value.length - 1 : idx
})

const timeline = computed(() => [...order.value.events].reverse()) // newest first

const actionHint = computed(() => {
  const o = order.value
  if (o.status === 'AWAITING_PAYMENT') return 'Vui lòng thanh toán để Người bán xử lý đơn hàng.'
  if (o.status === 'SHIPPING') return 'Vui lòng chỉ nhấn "Đã nhận được hàng" khi đơn hàng đã được giao đến bạn và sản phẩm không có vấn đề nào.'
  if (o.canReturn) return `Bạn có thể yêu cầu trả hàng/hoàn tiền đến ${fmtTime(o.returnDeadline)}.`
  if (o.returnRequest?.status === 'REJECTED') return 'Người bán đã từ chối yêu cầu. Bạn có thể khiếu nại để Shoppi xem xét.'
  return 'Cảm ơn bạn đã mua sắm tại Shoppi!'
})

async function load() {
  try {
    const { data } = await getOrder(route.params.id)
    order.value = data
  } catch (e) {
    loadError.value = extractError(e)
  }
}

function onReviewed() {
  reviewing.value = null
  load()
}

// while in transit, refresh now and then so new carrier scans show up
const timer = setInterval(() => { if (order.value?.status === 'SHIPPING') load() }, 30000)
onBeforeUnmount(() => clearInterval(timer))

watch(() => route.params.id, () => { order.value = null; load() }, { immediate: true })
</script>

<style scoped>
.page { padding-top: 16px; padding-bottom: 40px; }
.card { background: white; border-radius: 2px; padding: 20px 24px; margin-bottom: 12px; box-shadow: 0 1px 1px rgba(0, 0, 0, 0.05); }
.card-title { font-size: 17px; font-weight: 500; margin: 0 0 12px; }
.empty { text-align: center; padding: 60px; }
.small { font-size: 13px; }
.head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 14px; }
.back { color: var(--color-text-muted); margin-right: auto; }
.sep { color: #ccc; }
.status { color: var(--color-primary); }

.stepper { list-style: none; display: grid; grid-template-columns: repeat(5, 1fr); margin: 0; padding: 0; position: relative; }
.stepper li { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 6px; position: relative; }
.stepper li + li::before { content: ''; position: absolute; top: 30px; right: calc(50% + 34px); width: calc(100% - 68px); height: 4px; background: #e0e0e0; }
.stepper li.done + li.done::before, .stepper li.done + li.current::before { background: #2dc258; }
.circle { width: 60px; height: 60px; border-radius: 50%; border: 4px solid #e0e0e0; display: flex; align-items: center; justify-content: center; font-size: 24px; background: white; filter: grayscale(1); }
li.done .circle { border-color: #2dc258; filter: none; }
.label { font-size: 14px; }
.time { font-size: 12px; color: var(--color-text-faint); min-height: 15px; }
.cancelled { background: #fffcf5; border-left: 4px solid var(--color-primary); }
.cancelled .text-muted { margin-top: 6px; font-size: 14px; }

.actions-card { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; background: #fffcf5; }
.actions-card > span { flex: 1; min-width: 200px; }

.return-card .kv { display: grid; grid-template-columns: 160px 1fr; gap: 8px 16px; font-size: 14px; margin: 0 0 12px; }
.kv dt { color: var(--color-text-muted); }
.return-card .thread { margin-top: 14px; }
.refund { display: flex; justify-content: space-between; gap: 16px; padding: 10px 0; border-top: 1px dashed var(--color-border); font-size: 14px; }
.refund:first-of-type { border-top: none; }
.refund .right { text-align: right; }
.refund-st { margin-left: 8px; font-size: 12px; padding: 1px 6px; border-radius: 3px; background: #eaf7ee; color: #1e9e4a; }
.refund-st.PENDING { background: #fff4e5; color: #b35c00; }
.refund-st.FAILED { background: #fdecea; color: #d33; }
.kv dd { margin: 0; }
.evidence { display: flex; gap: 8px; flex-wrap: wrap; }
.evidence img { width: 80px; height: 80px; object-fit: cover; border: 1px solid var(--color-border); }
.evidence video { width: 160px; height: 90px; background: #000; }

.address-tracking { display: grid; grid-template-columns: 300px 1fr; gap: 24px; }
.addr { font-size: 14px; line-height: 1.6; margin: 0 0 8px; }
.tracking { color: var(--color-text); letter-spacing: 0.5px; }
.timeline { list-style: none; margin: 0; padding: 0 0 0 24px; border-left: 1px solid rgba(0, 0, 0, 0.09); }
.timeline li { position: relative; display: flex; gap: 16px; padding: 0 0 18px 8px; font-size: 14px; color: var(--color-text-faint); }
.timeline .dot { position: absolute; left: -31px; top: 4px; width: 12px; height: 12px; border-radius: 50%; background: #d8d8d8; }
.timeline li.latest { color: #26aa99; }
.timeline li.latest .dot { background: #26aa99; box-shadow: 0 0 0 4px rgba(38, 170, 153, 0.2); }
.t-time { width: 130px; flex-shrink: 0; }
.t-title { color: inherit; }
.timeline li:not(.latest) .t-title { color: var(--color-text-muted); }
.t-loc { font-size: 12px; margin-top: 2px; }

.shop-line { display: flex; align-items: center; gap: 8px; padding-bottom: 12px; border-bottom: 1px solid var(--color-border); flex-wrap: wrap; }
.chat-btn { background: var(--color-primary-light); color: var(--color-primary); border: 1px solid var(--color-primary); margin-left: 8px; }
.chat-btn:hover { background: #ffe1d9; }
.help-link { margin-left: auto; font-size: 13px; color: #2673dd; }
.help-link:hover { text-decoration: underline; }
.shop-badge { background: var(--color-primary); color: white; font-size: 11px; padding: 1px 5px; border-radius: 2px; }
.item { display: flex; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid #f5f5f5; }
.item img { width: 80px; height: 80px; object-fit: cover; border: 1px solid #e1e1e1; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 15px; display: block; margin-bottom: 4px; }
.item-name:hover { color: var(--color-primary); }
.item-right { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.reviewed { color: #1e9e4a; font-size: 13px; }
.money { display: grid; grid-template-columns: 1fr 220px; margin: 12px 0 0; font-size: 14px; }
.money dt, .money dd { padding: 10px 16px; border-bottom: 1px dotted rgba(0, 0, 0, 0.09); text-align: right; margin: 0; }
.money dt { color: var(--color-text-muted); border-right: 1px dotted rgba(0, 0, 0, 0.09); }
.money .grand { color: var(--color-primary); font-size: 22px; }

@media (max-width: 760px) {
  .stepper { grid-template-columns: repeat(5, minmax(0, 1fr)); }
  .stepper li + li::before { display: none; }
  .circle { width: 44px; height: 44px; font-size: 18px; }
  .label { font-size: 11px; }
  .address-tracking { grid-template-columns: 1fr; }
  .money { grid-template-columns: 1fr auto; }
  .return-card .kv { grid-template-columns: 1fr; }
}
.req-note { background: #fff8eb; color: #a16207; font-size: 14px; }
.req-note.rejected { background: #fdecea; color: #b42318; }
</style>
