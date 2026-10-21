<template>
  <BaseModal :title="d ? `Đơn hàng ${d.order.code}` : 'Đơn hàng'" :width="860" @close="$emit('close')">
    <div v-if="!d" class="faint">{{ error || 'Đang tải...' }}</div>
    <div v-else class="detail">
      <div class="top">
        <span class="st" :class="d.order.status">{{ statusLabel(d.order.status) }}</span>
        <span v-if="d.late" class="late">⏰ {{ d.late }}</span>
        <span v-if="d.settlementId" class="tag">Đã đối soát với người bán</span>
        <span class="faint">Đặt lúc {{ formatDateTime(d.order.createdAt) }}</span>
      </div>

      <div class="grid">
        <section>
          <h4>Người mua</h4>
          <p><strong>{{ d.buyer.fullName }}</strong><br /><span class="faint">{{ d.buyer.email }}<template v-if="d.buyer.phone"> · {{ d.buyer.phone }}</template></span></p>
          <p class="faint">📍 {{ d.order.shippingAddress }}</p>
        </section>
        <section>
          <h4>Shop</h4>
          <p><router-link :to="`/shop/${d.shop.id}`" target="_blank"><strong>{{ d.shop.name }}</strong></router-link><br /><span class="faint">{{ d.shop.phone }}</span></p>
          <p class="faint">{{ d.order.carrierName }} · {{ d.order.shippingMethodName }}<template v-if="d.order.trackingNumber"> · Mã vận đơn {{ d.order.trackingNumber }}</template></p>
        </section>
        <section>
          <h4>Thanh toán</h4>
          <template v-if="d.payment">
            <p>{{ PAYMENT_METHOD[d.payment.method] }}<template v-if="d.payment.provider"> ({{ d.payment.provider }})</template>
              · <span class="pay" :class="d.payment.status">{{ PAYMENT_STATUS[d.payment.status] }}</span></p>
            <p class="faint">{{ formatVnd(d.payment.amount) }}<template v-if="d.payment.paidAt"> · thu lúc {{ formatDateTime(d.payment.paidAt) }}</template></p>
          </template>
          <p v-else class="faint">Không có</p>
        </section>
      </div>

      <table class="sc-table items">
        <thead><tr><th>Sản phẩm</th><th class="r">Đơn giá</th><th class="r">SL</th><th class="r">Thành tiền</th></tr></thead>
        <tbody>
          <tr v-for="l in d.order.items" :key="l.id">
            <td><div class="prod"><img :src="l.thumbnailUrl || ph" alt="" /><span>{{ l.productName }}<span v-if="l.variantName" class="faint"> · {{ l.variantName }}</span></span></div></td>
            <td class="r">{{ formatVnd(l.priceAtPurchase) }}</td>
            <td class="r">{{ l.quantity }}</td>
            <td class="r">{{ formatVnd(l.priceAtPurchase * l.quantity) }}</td>
          </tr>
        </tbody>
      </table>
      <dl class="money">
        <dt>Tiền hàng</dt><dd>{{ formatVnd(d.order.subtotal) }}</dd>
        <template v-if="d.order.comboDiscount > 0"><dt>Combo của shop</dt><dd>-{{ formatVnd(d.order.comboDiscount) }}</dd></template>
        <template v-if="d.order.shopDiscount > 0"><dt>Voucher shop {{ d.order.shopVoucherCode }}</dt><dd>-{{ formatVnd(d.order.shopDiscount) }}</dd></template>
        <template v-if="d.order.platformDiscount > 0"><dt>Shoppi Voucher {{ d.order.platformVoucherCode }}</dt><dd>-{{ formatVnd(d.order.platformDiscount) }}</dd></template>
        <dt>Phí vận chuyển</dt><dd>{{ formatVnd(d.order.shippingFee - (d.order.shippingDiscount || 0)) }}</dd>
        <dt class="b">Tổng thanh toán</dt><dd class="b total">{{ formatVnd(d.order.total) }}</dd>
      </dl>
      <p v-if="d.order.cancelReason" class="note">Lý do hủy ({{ d.order.cancelledBy === 'BUYER' ? 'người mua' : d.order.cancelledBy === 'SELLER' ? 'người bán' : 'hệ thống/Shoppi' }}): {{ d.order.cancelReason }}</p>

      <div class="grid two">
        <section>
          <h4>Hành trình đơn hàng</h4>
          <ol class="timeline">
            <li v-for="(e, i) in [...d.order.events].reverse()" :key="i">
              <span class="faint">{{ formatDateTime(e.time) }}</span> {{ e.title }}<template v-if="e.location"> · {{ e.location }}</template>
            </li>
          </ol>
        </section>
        <section>
          <h4>Trả hàng / Hoàn tiền</h4>
          <div v-for="r in d.returns" :key="'r' + r.id" class="line">
            {{ RETURN_STATUS[r.status] }} · {{ formatVnd(r.refundAmount) }}
            <router-link v-if="r.complaint" :to="{ name: 'admin-disputes', query: { id: r.id, status: r.status === 'ESCALATED' ? 'OPEN' : 'ALL' } }">· xem tranh chấp</router-link>
          </div>
          <div v-for="r in d.refunds" :key="'f' + r.id" class="line">
            💸 {{ formatVnd(r.amount) }} · {{ REFUND_SOURCE[r.source] }} · <span class="pay" :class="r.status">{{ REFUND_STATUS[r.status] }}</span>
            <span v-if="r.reference" class="faint"> · {{ r.reference }}</span>
          </div>
          <p v-if="!d.returns.length && !d.refunds.length" class="faint">Không có</p>
          <h4 v-if="d.history.length" class="mt">Thao tác của quản trị viên</h4>
          <div v-for="h in d.history" :key="h.id" class="line faint">{{ formatDateTime(h.createdAt) }} · {{ h.adminName }}: {{ AUDIT_ACTION[h.action] || h.action }}<template v-if="h.detail"> – {{ h.detail }}</template></div>
        </section>
      </div>

      <!-- admin actions -->
      <div v-if="mode === 'cancel'" class="form">
        <strong>Hủy đơn hàng</strong>
        <p class="faint">Hàng được cộng lại kho; nếu đã thanh toán online, tiền tự động hoàn cho người mua. Cả người mua và người bán đều được thông báo.</p>
        <div class="presets"><button v-for="r in CANCEL_REASONS" :key="r" type="button" @click="reason = r">{{ r }}</button></div>
        <textarea v-model="reason" rows="2" maxlength="500" placeholder="Lý do hủy (ít nhất 5 ký tự)"></textarea>
      </div>
      <div v-if="mode === 'refund'" class="form">
        <strong>Hoàn tiền thủ công</strong>
        <p class="faint">Tối đa {{ formatVnd(d.refundable) }}. Thanh toán online được hoàn ngay về phương thức gốc; COD tạo khoản chờ chuyển khoản.</p>
        <input v-model.number="amount" type="number" min="1" :max="d.refundable" placeholder="Số tiền" />
        <textarea v-model="reason" rows="2" maxlength="500" placeholder="Lý do (ví dụ: bồi thường giao trễ)"></textarea>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </div>
    <template v-if="d" #footer>
      <template v-if="mode">
        <button type="button" class="btn ghost" @click="mode = null">Quay lại</button>
        <button type="button" class="btn" :disabled="busy || reason.trim().length < 5 || (mode === 'refund' && !(amount > 0))" @click="submit">
          {{ busy ? 'Đang xử lý...' : mode === 'cancel' ? 'Xác nhận hủy đơn' : 'Hoàn ' + formatVnd(amount) }}
        </button>
      </template>
      <template v-else>
        <button v-if="d.canCancel" type="button" class="btn ghost warn" @click="start('cancel')">Hủy đơn</button>
        <button v-if="d.refundable > 0 && can('FINANCE')" type="button" class="btn ghost" @click="start('refund')">Hoàn tiền thủ công</button>
        <button type="button" class="btn" @click="$emit('close')">Đóng</button>
      </template>
    </template>
  </BaseModal>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseModal from '../BaseModal.vue'
import { adminOrder, adminCancelOrder, adminManualRefund } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { statusLabel } from '../../utils/orderStatus'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { PAYMENT_METHOD, PAYMENT_STATUS, AUDIT_ACTION } from '../../utils/adminLabels'
import { RETURN_STATUS, REFUND_STATUS, REFUND_SOURCE } from '../../utils/returns'

const props = defineProps({ id: { type: Number, required: true } })
const emit = defineEmits(['close', 'changed'])

const CANCEL_REASONS = ['Nghi ngờ gian lận đơn hàng', 'Người bán không xử lý đơn quá lâu', 'Sản phẩm vi phạm chính sách bị gỡ', 'Theo yêu cầu của người mua qua Trung tâm hỗ trợ']
const ph = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'
const { can } = useAdminMe()
const d = ref(null)
const error = ref('')
const mode = ref(null)
const reason = ref('')
const amount = ref(null)
const busy = ref(false)

async function load() {
  try {
    d.value = (await adminOrder(props.id)).data
  } catch (e) {
    error.value = extractError(e)
  }
}

function start(m) {
  mode.value = m
  reason.value = ''
  amount.value = m === 'refund' ? Number(d.value.refundable) : null
  error.value = ''
}

async function submit() {
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'cancel') d.value = (await adminCancelOrder(props.id, reason.value.trim())).data
    else {
      await adminManualRefund(props.id, amount.value, reason.value.trim())
      await load()
    }
    mode.value = null
    emit('changed')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail { font-size: 14px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.top { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #f0f5ff; color: #2673dd; }
.st.DELIVERED { background: #eaf7ef; color: #1e9e4a; }
.st.CANCELLED, .st.REFUNDED { background: #f2f2f2; color: #666; }
.st.AWAITING_PAYMENT, .st.PENDING { background: #fff4e5; color: #b35c00; }
.late { font-size: 12px; color: #b42318; background: #fdecea; padding: 2px 8px; border-radius: 3px; }
.tag { font-size: 11px; border: 1px solid #a6dcb8; color: #1e9e4a; border-radius: 3px; padding: 1px 6px; }
.grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 12px; }
.grid.two { grid-template-columns: 1fr 1fr; margin-top: 14px; }
h4 { margin: 0 0 6px; font-size: 13px; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.3px; }
h4.mt { margin-top: 14px; }
.grid p { margin: 0 0 4px; }
.pay { font-size: 12px; color: #1e9e4a; }
.pay.PENDING, .pay.FAILED { color: #b35c00; }
.pay.CANCELLED, .pay.REFUNDED { color: #888; }
.items { margin: 6px 0; }
.r { text-align: right; white-space: nowrap; }
.prod { display: flex; align-items: center; gap: 8px; }
.prod img { width: 36px; height: 36px; object-fit: cover; border: 1px solid var(--color-border); }
.money { display: grid; grid-template-columns: 1fr 140px; gap: 4px 12px; margin: 8px 0 0 auto; max-width: 420px; text-align: right; }
.money dt { color: var(--color-text-muted); }
.money dd { margin: 0; }
.money .b { font-weight: 600; color: var(--color-text); }
.money .total { color: var(--color-primary); font-size: 16px; }
.note { font-size: 13px; color: #b42318; }
.timeline { margin: 0; padding-left: 16px; max-height: 200px; overflow: auto; }
.timeline li { margin-bottom: 4px; }
.line { margin-bottom: 4px; }
.line a { color: #2673dd; }
.form { margin-top: 14px; padding: 12px; border: 1px solid var(--color-border); border-radius: 6px; background: #fafafa; display: flex; flex-direction: column; gap: 8px; }
.form p { margin: 0; }
.form input { max-width: 200px; }
.presets { display: flex; flex-wrap: wrap; gap: 6px; }
.presets button { font-size: 12px; border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 3px 10px; cursor: pointer; }
.btn.warn { color: #d33; border-color: #f0b4b4; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 760px) { .grid, .grid.two { grid-template-columns: 1fr; } }
</style>
