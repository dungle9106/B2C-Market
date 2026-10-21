<template>
  <div v-if="loadError" class="sc-card sc-empty">{{ loadError }}</div>
  <div v-else-if="!order" class="sc-card sc-empty">Đang tải...</div>

  <div v-else class="detail">
    <section class="sc-card head">
      <router-link :to="{ name: 'seller-orders' }" class="back">‹ Danh sách đơn hàng</router-link>
      <div class="head-row">
        <h2 class="sc-card-title">Đơn hàng {{ order.code }}</h2>
        <span class="status-tag" :class="order.status">{{ statusLabel(order.status) }}</span>
        <span v-if="order.cancelRequestedAt && order.status === 'CONFIRMED'" class="badge warn">Người mua yêu cầu hủy</span>
      </div>
      <p class="text-faint small">Đặt lúc {{ formatDateTime(order.createdAt) }}<template v-if="order.confirmedAt"> · Xác nhận {{ formatDateTime(order.confirmedAt) }}</template>
        <template v-if="order.packedAt"> · Đóng gói {{ formatDateTime(order.packedAt) }}</template><template v-if="order.shippedAt"> · Giao ĐVVC {{ formatDateTime(order.shippedAt) }}</template>
        <template v-if="order.deliveredAt"> · Giao thành công {{ formatDateTime(order.deliveredAt) }}</template></p>
      <div v-if="order.cancelRequestedAt && order.status === 'CONFIRMED'" class="cancel-req">
        Lý do người mua muốn hủy: <strong>{{ order.cancelRequestReason }}</strong>. Hãy phản hồi trước khi giao hàng cho đơn vị vận chuyển.
      </div>
      <div v-if="order.cancelRequestRejectedReason" class="text-faint small">Đã từ chối yêu cầu hủy: {{ order.cancelRequestRejectedReason }}</div>
      <SellerOrderActions :order="order" class="actions" @changed="load" />
    </section>

    <div class="grid">
      <section class="sc-card">
        <h3 class="block-h">Hành trình đơn hàng</h3>
        <ol class="timeline">
          <li v-for="(e, i) in [...order.events].reverse()" :key="i" :class="{ latest: i === 0, failed: e.stage === 'DELIVERY_FAILED' }">
            <span class="dot"></span>
            <span class="t-time">{{ formatDateTime(e.time) }}</span>
            <div>
              <div class="t-title">{{ e.title }}</div>
              <div v-if="e.location" class="t-loc">{{ e.location }}</div>
            </div>
          </li>
        </ol>
      </section>

      <aside>
        <section class="sc-card">
          <h3 class="block-h">Người nhận</h3>
          <p class="addr"><strong>{{ order.buyerName }}</strong><br />{{ order.shippingAddress }}</p>
          <div v-if="order.note" class="buyer-note">✉ {{ order.note }}</div>
        </section>
        <section class="sc-card">
          <h3 class="block-h">Vận chuyển</h3>
          <dl class="kv">
            <dt>Đơn vị</dt><dd>{{ shippingLabel(order) }}</dd>
            <template v-if="order.trackingNumber"><dt>Mã vận đơn</dt><dd><strong>{{ order.trackingNumber }}</strong><span v-if="order.shippingGateway" class="gw-tag" title="Vận đơn thật trên hệ thống GHN">{{ order.shippingGateway }} · {{ order.gatewayStatus }}</span></dd></template>
            <template v-if="order.pickupMethod"><dt>Hình thức</dt><dd>{{ order.pickupMethod === 'DROPOFF' ? 'Gửi tại bưu cục' : 'Lấy hàng tận nơi' }} · {{ formatDay(order.pickupDate) }}</dd></template>
            <template v-if="order.estimatedDeliveryTo"><dt>Dự kiến giao</dt><dd>{{ formatDay(order.estimatedDeliveryFrom) }} - {{ formatDay(order.estimatedDeliveryTo) }}</dd></template>
            <dt>Phiếu giao</dt><dd>{{ order.labelPrintedAt ? 'Đã in ' + formatDateTime(order.labelPrintedAt) : 'Chưa in' }}</dd>
          </dl>
        </section>
      </aside>
    </div>

    <section class="sc-card">
      <h3 class="block-h">Sản phẩm</h3>
      <div v-for="item in order.items" :key="item.id" class="item">
        <img :src="item.thumbnailUrl || placeholder" alt="" />
        <div class="item-info">
          <router-link :to="`/products/${item.productId}`" target="_blank" class="item-name">{{ item.productName }}</router-link>
          <div v-if="item.variantName" class="text-faint small">Phân loại: {{ item.variantName }}</div>
        </div>
        <span>{{ formatVnd(item.priceAtPurchase) }} × {{ item.quantity }}</span>
        <strong class="line-total">{{ formatVnd(item.priceAtPurchase * item.quantity) }}</strong>
      </div>
      <dl class="money">
        <dt>Tổng tiền hàng</dt><dd>{{ formatVnd(order.subtotal) }}</dd>
        <template v-if="order.comboDiscount > 0"><dt>Combo khuyến mãi</dt><dd>-{{ formatVnd(order.comboDiscount) }}</dd></template>
        <template v-if="order.shopDiscount > 0"><dt>Voucher của Shop ({{ order.shopVoucherCode }})</dt><dd>-{{ formatVnd(order.shopDiscount) }}</dd></template>
        <template v-if="order.platformDiscount > 0"><dt>Shoppi Voucher (sàn tài trợ)</dt><dd>-{{ formatVnd(order.platformDiscount) }}</dd></template>
        <dt>Phí vận chuyển</dt><dd>{{ formatVnd(order.shippingFee) }}</dd>
        <template v-if="order.shippingDiscount > 0"><dt>Giảm phí vận chuyển</dt><dd>-{{ formatVnd(order.shippingDiscount) }}</dd></template>
        <dt>Người mua thanh toán</dt><dd class="grand">{{ formatVnd(order.total) }}</dd>
        <dt>Thanh toán</dt><dd>{{ paymentLabel(order) }}</dd>
      </dl>
    </section>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { shopOrder } from '../../api/orders'
import SellerOrderActions from '../../components/orders/SellerOrderActions.vue'
import { statusLabel, paymentLabel, shippingLabel } from '../../utils/orderStatus'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const route = useRoute()
const order = ref(null)
const loadError = ref('')
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56"><rect width="56" height="56" fill="%23f0f0f0"/></svg>'
const formatDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '')

async function load() {
  try {
    const { data } = await shopOrder(route.params.id)
    order.value = data
  } catch (e) {
    loadError.value = extractError(e)
  }
}
watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.back { color: var(--color-text-muted); font-size: 14px; }
.head-row { display: flex; align-items: center; gap: 12px; margin: 10px 0 4px; flex-wrap: wrap; }
.small { font-size: 13px; }
.badge.warn { background: #fdecea; color: #d33; font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.cancel-req { background: #fdecea; color: #b42318; padding: 8px 12px; border-radius: 4px; font-size: 13px; margin: 8px 0; }
.actions { margin-top: 12px; }
.grid { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 16px; align-items: start; }
.block-h { font-size: 16px; font-weight: 600; margin: 0 0 12px; }
.timeline { list-style: none; margin: 0; padding: 0; }
.timeline li { position: relative; display: grid; grid-template-columns: 130px 1fr; gap: 12px; padding: 0 0 16px 20px; border-left: 1px solid #e5e5e5; margin-left: 6px; }
.timeline li:last-child { border-left-color: transparent; }
.dot { position: absolute; left: -6px; top: 3px; width: 11px; height: 11px; border-radius: 50%; background: #ccc; }
.latest .dot { background: var(--color-success); box-shadow: 0 0 0 3px #d9f2ee; }
.failed .dot { background: #d33; }
.t-time { font-size: 12px; color: var(--color-text-faint); }
.t-title { font-size: 14px; }
.latest .t-title { color: var(--color-success); font-weight: 500; }
.failed .t-title { color: #d33; }
.t-loc { font-size: 12px; color: var(--color-text-faint); }
.addr { margin: 0; line-height: 1.6; font-size: 14px; }
.buyer-note { font-size: 13px; margin-top: 8px; background: #fff8e6; padding: 6px 8px; border-radius: 3px; }
.kv { display: grid; grid-template-columns: 100px 1fr; gap: 8px; margin: 0; font-size: 14px; }
.kv dt { color: var(--color-text-muted); }
.kv dd { margin: 0; }
.item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.item img { width: 56px; height: 56px; object-fit: cover; border-radius: 2px; }
.item-info { flex: 1; min-width: 0; }
.item-name:hover { color: var(--color-primary); }
.line-total { min-width: 100px; text-align: right; }
.money { display: grid; grid-template-columns: 1fr 160px; gap: 8px; margin: 14px 0 0 auto; max-width: 480px; font-size: 14px; }
.money dt { text-align: right; color: var(--color-text-muted); }
.money dd { margin: 0; text-align: right; }
.grand { color: var(--color-primary); font-size: 20px; font-weight: 600; }
@media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
.gw-tag { margin-left: 8px; font-size: 11px; padding: 1px 6px; border-radius: 3px; background: #fff1e6; color: #e8590c; }
</style>
