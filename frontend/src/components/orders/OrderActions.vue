<template>
  <div class="actions">
    <router-link v-if="order.status === 'AWAITING_PAYMENT'" :to="{ name: 'payment', params: { code: order.checkoutCode } }" class="btn">Thanh Toán Ngay</router-link>
    <button v-if="order.canConfirmReceived" type="button" class="btn" :disabled="busy" @click="onReceived">Đã Nhận Được Hàng</button>
    <button v-if="order.canReturn" type="button" class="btn ghost" @click="returning = true">Trả Hàng/Hoàn Tiền</button>
    <button v-if="rr && rr.status === 'REJECTED'" type="button" class="btn secondary" @click="escalating = true">Khiếu Nại</button>
    <button v-if="rr && ['REQUESTED', 'REJECTED', 'ESCALATED'].includes(rr.status)" type="button" class="btn ghost" :disabled="busy" @click="onWithdraw">Hủy Yêu Cầu Trả Hàng</button>
    <button v-if="canRebuy" type="button" class="btn" :class="{ secondary: order.status === 'DELIVERED' }" :disabled="busy" @click="onRebuy">Mua Lại</button>
    <button v-if="order.canCancel" type="button" class="btn ghost" @click="cancelling = true">Hủy Đơn</button>
    <!-- after the shop confirmed: the shop decides -->
    <button v-if="order.canRequestCancel" type="button" class="btn ghost" @click="requesting = true">Yêu Cầu Hủy Đơn</button>
    <span v-if="order.cancelRequestedAt && order.status === 'CONFIRMED'" class="pending-note">Đã gửi yêu cầu hủy, chờ Người bán phản hồi</span>
    <router-link v-if="showDetailLink" :to="{ name: 'order-detail', params: { id: order.id } }" class="btn ghost">Xem Chi Tiết</router-link>

    <CancelOrderDialog v-if="cancelling" :reasons="BUYER_CANCEL_REASONS" :action="(r) => cancelOrder(order.id, r)"
                       @close="cancelling = false" @done="done(() => (cancelling = false))" />
    <CancelOrderDialog v-if="requesting" :reasons="BUYER_CANCEL_REASONS" :action="(r) => cancelOrder(order.id, r)"
                       title="Yêu Cầu Hủy Đơn" confirm-label="Gửi Yêu Cầu"
                       hint="Người bán đã xác nhận đơn hàng nên cần Người bán đồng ý. Mỗi đơn chỉ gửi yêu cầu hủy một lần."
                       @close="requesting = false" @done="done(() => (requesting = false))" />
    <ReturnRequestDialog v-if="returning" :order="order" @close="returning = false" @done="done(() => (returning = false))" />
    <BaseModal v-if="escalating" title="Khiếu Nại Lên Shoppi" :width="520" @close="escalating = false">
      <p class="hint">Người bán đã từ chối với lý do: <em>"{{ rr.sellerNote }}"</em>.
        Shoppi sẽ xem xét bằng chứng của cả hai bên và đưa ra quyết định cuối cùng.</p>
      <textarea v-model.trim="complaint" rows="4" maxlength="1000" placeholder="Vì sao bạn không đồng ý với Người bán?"></textarea>
      <p v-if="error" class="error">{{ error }}</p>
      <template #footer>
        <button type="button" class="btn ghost" @click="escalating = false">Hủy</button>
        <button type="button" class="btn" :disabled="!complaint || busy" @click="onEscalate">Gửi khiếu nại</button>
      </template>
    </BaseModal>
  </div>
  <p v-if="error && !escalating" class="error">{{ error }}</p>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import BaseModal from '../BaseModal.vue'
import CancelOrderDialog from './CancelOrderDialog.vue'
import ReturnRequestDialog from './ReturnRequestDialog.vue'
import { cancelOrder, confirmReceived, rebuyOrder, withdrawReturn, escalateReturn } from '../../api/orders'
import { extractError } from '../../utils/apiError'
import { BUYER_CANCEL_REASONS } from '../../utils/returns'
import { useCartStore } from '../../stores/cart'

const props = defineProps({
  order: { type: Object, required: true },
  showDetailLink: { type: Boolean, default: false }
})
// changed: the order was modified, the parent should reload it
const emit = defineEmits(['changed'])

const router = useRouter()
const cart = useCartStore()
const rr = computed(() => props.order.returnRequest)
const canRebuy = computed(() => ['DELIVERED', 'CANCELLED', 'REFUNDED'].includes(props.order.status))

const busy = ref(false)
const error = ref('')
const cancelling = ref(false)
const requesting = ref(false)
const returning = ref(false)
const escalating = ref(false)
const complaint = ref('')

function done(close) {
  close()
  emit('changed')
}

async function run(fn) {
  busy.value = true
  error.value = ''
  try {
    await fn()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

const onReceived = () => run(async () => {
  if (!confirm('Xác nhận bạn đã nhận được hàng và hàng không có vấn đề?')) return
  await confirmReceived(props.order.id)
  emit('changed')
})

const onWithdraw = () => run(async () => {
  if (!confirm('Hủy yêu cầu trả hàng/hoàn tiền? Đơn hàng sẽ được xem như đã nhận bình thường.')) return
  await withdrawReturn(rr.value.id)
  emit('changed')
})

const onEscalate = () => run(async () => {
  await escalateReturn(rr.value.id, complaint.value)
  escalating.value = false
  emit('changed')
})

// "Mua lai": back into the cart, those rows pre-selected
const onRebuy = () => run(async () => {
  const { data } = await rebuyOrder(props.order.id)
  if (data.skipped.length) {
    alert(`Một số sản phẩm không còn bán hoặc đã hết hàng nên không được thêm lại:\n• ${data.skipped.join('\n• ')}`)
  }
  if (!data.cartItemIds.length) return
  await cart.fetchCart()
  router.push({ name: 'cart', query: { select: data.cartItemIds.join(',') } })
})
</script>

<style scoped>
.actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
.actions .btn { min-width: 130px; padding: 8px 14px; font-size: 14px; }
.hint { font-size: 14px; color: var(--color-text-muted); margin: 0 0 12px; line-height: 1.5; }
.error { color: #d33; font-size: 13px; margin: 6px 0 0; text-align: right; }
.pending-note { font-size: 13px; color: #d97706; align-self: center; }
</style>
