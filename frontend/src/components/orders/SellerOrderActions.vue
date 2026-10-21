<template>
  <div class="actions" :class="{ vertical }">
    <!-- buyer asked to cancel after the shop confirmed -->
    <template v-if="order.cancelRequestedAt && order.status === 'CONFIRMED'">
      <button type="button" class="btn sm" :disabled="busy" @click="onAcceptCancel">Đồng ý hủy</button>
      <button type="button" class="btn sm ghost" :disabled="busy" @click="rejecting = true">Từ chối hủy</button>
    </template>
    <template v-else>
      <button v-if="order.status === 'PENDING'" type="button" class="btn sm" :disabled="busy" @click="onStatus('CONFIRMED')">Xác nhận đơn</button>
      <button v-if="order.status === 'CONFIRMED' && !order.packedAt" type="button" class="btn sm" :disabled="busy" @click="preparing = true">Chuẩn bị hàng</button>
      <template v-if="order.status === 'CONFIRMED' && order.packedAt">
        <button type="button" class="btn sm" :disabled="busy" @click="onStatus('SHIPPING')">Đã giao cho ĐVVC</button>
        <button type="button" class="btn sm ghost" @click="preparing = true">Đổi lịch giao</button>
      </template>
      <template v-if="order.status === 'SHIPPING'">
        <button type="button" class="btn sm" :disabled="busy" @click="tracking = true">Cập nhật vận chuyển</button>
        <button type="button" class="btn sm ghost" :disabled="busy" @click="onStatus('DELIVERED')">Đã giao thành công</button>
      </template>
    </template>
    <router-link v-if="order.trackingNumber && ['CONFIRMED', 'SHIPPING'].includes(order.status)" :to="printLink" target="_blank" class="btn sm ghost">
      🖨 {{ order.labelPrintedAt ? 'In lại phiếu' : 'In phiếu giao' }}
    </router-link>
    <router-link v-if="order.returnRequest && ['REQUESTED', 'ESCALATED'].includes(order.returnRequest.status)" :to="{ name: 'seller-returns' }" class="btn sm secondary">Xử lý trả hàng</router-link>
    <button v-if="['PENDING', 'CONFIRMED'].includes(order.status) && !order.cancelRequestedAt" type="button" class="btn sm ghost danger" @click="cancelling = true">Hủy đơn</button>
    <router-link v-if="detailLink" :to="{ name: 'seller-order-detail', params: { id: order.id } }" class="link">Xem chi tiết ›</router-link>
    <p v-if="error" class="error">{{ error }}</p>

    <PrepareOrderDialog v-if="preparing" :action="(m, d) => prepareOrder(order.id, m, d)" @close="preparing = false" @done="done(() => (preparing = false))" />

    <CancelOrderDialog v-if="cancelling" :reasons="SELLER_CANCEL_REASONS"
                       hint="Người mua sẽ được thông báo lý do hủy. Sản phẩm được hoàn lại kho."
                       :action="(r) => updateOrderStatus(order.id, 'CANCELLED', r)"
                       @close="cancelling = false" @done="done(() => (cancelling = false))" />

    <BaseModal v-if="rejecting" title="Từ chối yêu cầu hủy" :width="480" @close="rejecting = false">
      <p class="hint">Người mua muốn hủy vì: <em>"{{ order.cancelRequestReason }}"</em>. Hãy cho họ biết vì sao đơn vẫn được giao.</p>
      <div class="quick">
        <button v-for="r in REJECT_REASONS" :key="r" type="button" @click="rejectReason = r">{{ r }}</button>
      </div>
      <textarea v-model.trim="rejectReason" rows="3" maxlength="300" placeholder="Lý do từ chối..."></textarea>
      <template #footer>
        <button type="button" class="btn ghost" @click="rejecting = false">Đóng</button>
        <button type="button" class="btn" :disabled="!rejectReason || busy" @click="onRejectCancel">Từ chối yêu cầu</button>
      </template>
    </BaseModal>

    <BaseModal v-if="tracking" title="Cập nhật hành trình vận chuyển" :width="480" @close="tracking = false">
      <p class="hint">Mốc mới hiển thị ngay trong "Theo dõi đơn hàng" của người mua.</p>
      <label v-for="(label, stage) in STAGES" :key="stage" class="stage">
        <input v-model="trackStage" type="radio" :value="stage" /> {{ label }}
      </label>
      <input v-model.trim="trackLocation" maxlength="100" placeholder="Vị trí (VD: Bưu cục Cầu Giấy)" class="mt" />
      <textarea v-model.trim="trackNote" rows="2" maxlength="200" :placeholder="trackStage === 'DELIVERY_FAILED' ? 'Lý do (bắt buộc), VD: Không liên lạc được người nhận' : 'Ghi chú (không bắt buộc)'"></textarea>
      <template #footer>
        <button type="button" class="btn ghost" @click="tracking = false">Đóng</button>
        <button type="button" class="btn" :disabled="busy || (trackStage === 'DELIVERY_FAILED' && !trackNote)" @click="onTracking">Cập nhật</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import BaseModal from '../BaseModal.vue'
import CancelOrderDialog from './CancelOrderDialog.vue'
import PrepareOrderDialog from './PrepareOrderDialog.vue'
import { updateOrderStatus, prepareOrder, acceptCancelRequest, rejectCancelRequest, addShippingEvent } from '../../api/orders'
import { SELLER_CANCEL_REASONS } from '../../utils/returns'
import { extractError } from '../../utils/apiError'

const props = defineProps({
  order: { type: Object, required: true },
  detailLink: { type: Boolean, default: false },
  vertical: { type: Boolean, default: false }
})
// changed: the parent reloads the order(s)
const emit = defineEmits(['changed'])

const STAGES = { IN_TRANSIT: 'Đang trung chuyển', OUT_FOR_DELIVERY: 'Đang giao đến người nhận', DELIVERY_FAILED: 'Giao không thành công' }
const REJECT_REASONS = ['Đơn hàng đã được đóng gói và bàn giao lịch lấy hàng', 'Sản phẩm đặt riêng theo yêu cầu, không thể hủy', 'Shop đã liên hệ và người mua đồng ý nhận hàng']

const busy = ref(false)
const error = ref('')
const preparing = ref(false)
const cancelling = ref(false)
const rejecting = ref(false)
const rejectReason = ref('')
const tracking = ref(false)
const trackStage = ref('IN_TRANSIT')
const trackNote = ref('')
const trackLocation = ref('')
const printLink = computed(() => ({ name: 'seller-print-labels', query: { ids: String(props.order.id) } }))

function done(close) {
  close()
  emit('changed')
}

async function run(fn) {
  busy.value = true
  error.value = ''
  try {
    await fn()
    emit('changed')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

const LABELS = { CONFIRMED: 'Xác nhận đơn', SHIPPING: 'Xác nhận đã giao hàng cho đơn vị vận chuyển', DELIVERED: 'Xác nhận đơn đã giao thành công' }
const onStatus = (to) => {
  if (to !== 'CONFIRMED' && !confirm(`${LABELS[to]} ${props.order.code}?`)) return
  run(() => updateOrderStatus(props.order.id, to))
}
const onAcceptCancel = () => {
  if (!confirm(`Đồng ý hủy đơn ${props.order.code}? Sản phẩm sẽ được hoàn lại kho.`)) return
  run(() => acceptCancelRequest(props.order.id))
}
const onRejectCancel = () => run(async () => {
  await rejectCancelRequest(props.order.id, rejectReason.value)
  rejecting.value = false
})
const onTracking = () => run(async () => {
  await addShippingEvent(props.order.id, trackStage.value, trackNote.value, trackLocation.value)
  tracking.value = false
  trackNote.value = ''
  trackLocation.value = ''
})
</script>

<style scoped>
.actions { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.actions.vertical { flex-direction: column; align-items: stretch; }
.actions.vertical .link { text-align: center; }
.danger { color: #d33; }
.link { color: #2673dd; font-size: 13px; }
.link:hover { text-decoration: underline; }
.error { color: #d33; font-size: 12px; margin: 0; width: 100%; }
.hint { font-size: 14px; color: var(--color-text-muted); margin: 0 0 12px; }
.quick { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.quick button { background: #f5f5f5; border: none; border-radius: 999px; padding: 4px 10px; font-size: 12px; cursor: pointer; text-align: left; }
.stage { display: flex; align-items: center; gap: 8px; padding: 6px 0; cursor: pointer; font-size: 14px; }
.stage input { width: auto; margin: 0; accent-color: var(--color-primary); }
.mt { margin-top: 10px; }
</style>
