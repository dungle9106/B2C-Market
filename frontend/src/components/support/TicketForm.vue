<template>
  <form class="ticket-form" novalidate @submit.prevent="onSubmit">
    <label>Chủ đề <span class="req">*</span></label>
    <select v-model="form.category">
      <option v-for="(label, value) in categories" :key="value" :value="value">{{ label }}</option>
    </select>

    <!-- self-help first: the matching FAQ often answers it -->
    <div v-if="suggestions.length" class="faq-hint">
      <div class="faq-hint-title">Có thể bạn đang tìm:</div>
      <details v-for="f in suggestions" :key="f.q">
        <summary>{{ f.q }}</summary>
        <p>{{ f.a }}</p>
      </details>
    </div>

    <template v-if="orders.length">
      <label>Đơn hàng liên quan</label>
      <select v-model="form.orderId">
        <option :value="null">Không liên quan đến đơn hàng cụ thể</option>
        <option v-for="o in orders" :key="o.id" :value="o.id">{{ o.code }} · {{ auth.isSeller ? formatVnd(o.total) : o.shopName }} · {{ statusLabel(o.status) }}</option>
      </select>
    </template>

    <label>Tiêu đề <span class="req">*</span></label>
    <input v-model="form.subject" maxlength="200" placeholder="Tóm tắt vấn đề của bạn" />

    <label>Mô tả chi tiết <span class="req">*</span></label>
    <textarea v-model="form.content" rows="6" maxlength="4000" placeholder="Mô tả vấn đề, thời điểm xảy ra và mong muốn của bạn để Shoppi hỗ trợ nhanh hơn"></textarea>
    <div class="counter">{{ form.content.length }}/4000</div>

    <label>Hình ảnh đính kèm (không bắt buộc)</label>
    <MediaUploader v-model="form.attachmentUrls" :max="5" add-label="Thêm ảnh" />

    <p v-if="error" class="error">{{ error }}</p>
    <div class="actions">
      <slot name="cancel" />
      <button class="btn" type="submit" :disabled="busy">{{ busy ? 'Đang gửi...' : 'Gửi yêu cầu' }}</button>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import MediaUploader from '../MediaUploader.vue'
import { createTicket } from '../../api/support'
import { myOrders, shopOrders } from '../../api/orders'
import { useAuthStore } from '../../stores/auth'
import { TICKET_CATEGORIES, FAQ } from '../../utils/support'
import { statusLabel } from '../../utils/orderStatus'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'

const props = defineProps({
  category: { type: String, default: null },
  orderId: { type: Number, default: null }
})
const emit = defineEmits(['created'])

const auth = useAuthStore()

// "Kenh Nguoi Ban" only makes sense for sellers
const categories = computed(() => {
  const all = { ...TICKET_CATEGORIES }
  if (!auth.isSeller) delete all.SELLER
  return all
})

const form = reactive({
  category: props.category || (auth.isSeller ? 'SELLER' : 'ORDER'),
  orderId: props.orderId,
  subject: '',
  content: '',
  attachmentUrls: []
})
const orders = ref([])
const busy = ref(false)
const error = ref('')

// which FAQ topic answers which ticket category
const FAQ_TOPIC = { ORDER: 'Đơn hàng', SHIPPING: 'Vận chuyển', RETURN_REFUND: 'Trả hàng / Hoàn tiền', PAYMENT: 'Thanh toán', ACCOUNT: 'Tài khoản & Bán hàng', SELLER: 'Tài khoản & Bán hàng' }
const suggestions = computed(() => FAQ.find(t => t.topic === FAQ_TOPIC[form.category])?.items || [])

async function onSubmit() {
  error.value = ''
  if (!form.subject.trim()) return (error.value = 'Vui lòng nhập tiêu đề')
  if (form.content.trim().length < 10) return (error.value = 'Vui lòng mô tả chi tiết hơn (ít nhất 10 ký tự)')
  busy.value = true
  try {
    const { data } = await createTicket({ ...form, subject: form.subject.trim(), content: form.content.trim() })
    emit('created', data)
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = auth.isSeller ? await shopOrders() : auth.isBuyer ? await myOrders() : { data: [] }
    orders.value = data.slice(0, 30)
  } catch {
    // the order picker is optional
  }
})
</script>

<style scoped>
.ticket-form label { display: block; font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; }
.req { color: var(--color-primary); }
.counter { text-align: right; font-size: 12px; color: var(--color-text-faint); margin: -8px 0 12px; }
.faq-hint { background: #f6faff; border: 1px solid #dbe8fb; border-radius: 4px; padding: 10px 14px; margin: -4px 0 14px; }
.faq-hint-title { font-size: 13px; color: #2673dd; font-weight: 500; margin-bottom: 4px; }
.faq-hint details { font-size: 13px; padding: 4px 0; }
.faq-hint summary { cursor: pointer; }
.faq-hint p { margin: 6px 0 2px 14px; color: var(--color-text-muted); line-height: 1.5; }
.error { color: #d33; font-size: 13px; margin: 12px 0 0; }
.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
</style>
