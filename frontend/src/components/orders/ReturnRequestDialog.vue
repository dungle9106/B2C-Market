<template>
  <BaseModal title="Yêu Cầu Trả Hàng / Hoàn Tiền" :width="640" @close="$emit('close')">
    <form id="return-form" novalidate @submit.prevent="onSubmit">
      <h3 class="step">1. Chọn sản phẩm</h3>
      <div v-for="line in lines" :key="line.id" class="line" :class="{ off: !line.returnable }">
        <input v-model="line.checked" type="checkbox" :disabled="!line.returnable" />
        <img :src="line.thumbnailUrl || placeholder" alt="" />
        <div class="line-info">
          <div>{{ line.productName }}</div>
          <div class="text-faint small">{{ line.variantName ? 'Phân loại: ' + line.variantName + ' · ' : '' }}{{ formatVnd(line.priceAtPurchase) }}</div>
          <div v-if="!line.returnable" class="small no-return">Sản phẩm không hỗ trợ đổi trả</div>
        </div>
        <div v-if="line.checked" class="qty">
          <span class="small text-muted">Số lượng</span>
          <select v-model.number="line.returnQty">
            <option v-for="n in line.quantity" :key="n" :value="n">{{ n }}</option>
          </select>
        </div>
      </div>

      <h3 class="step">2. Yêu cầu</h3>
      <div class="types">
        <label v-for="(name, key) in RETURN_TYPES" :key="key" class="type" :class="{ active: form.type === key }">
          <input v-model="form.type" type="radio" :value="key" /> {{ name }}
        </label>
      </div>
      <select v-model="form.reason">
        <option :value="null" disabled>-- Chọn lý do --</option>
        <option v-for="(name, key) in RETURN_REASONS" :key="key" :value="key">{{ name }}</option>
      </select>
      <textarea v-model="form.description" rows="3" maxlength="2000" placeholder="Mô tả chi tiết vấn đề bạn gặp phải..."></textarea>

      <h3 class="step">3. Bằng chứng <span class="req">*</span> <span class="hint">ảnh rõ sản phẩm, tem nhãn, vết lỗi; có thể thêm 1 video mở hộp</span></h3>
      <MediaUploader v-model="images" :max="5" add-label="Thêm ảnh" />
      <div class="mt"><MediaUploader v-model="videos" kind="video" :max="1" add-label="Thêm video" /></div>

      <div class="refund">
        Số tiền hoàn dự kiến: <strong>{{ formatVnd(refundEstimate) }}</strong>
        <span class="text-faint small">· hoàn về phương thức thanh toán ban đầu sau khi được chấp nhận</span>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </form>

    <template #footer>
      <button type="button" class="btn ghost" @click="$emit('close')">Hủy</button>
      <button type="submit" form="return-form" class="btn" :disabled="busy">{{ busy ? 'Đang gửi...' : 'Gửi yêu cầu' }}</button>
    </template>
  </BaseModal>
</template>

<script setup>
import { ref, computed } from 'vue'
import BaseModal from '../BaseModal.vue'
import MediaUploader from '../MediaUploader.vue'
import { createReturn } from '../../api/orders'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'
import { RETURN_TYPES, RETURN_REASONS } from '../../utils/returns'

// order: OrderResponse (items carry `returnable`, plus the order total)
const props = defineProps({ order: { type: Object, required: true } })
const emit = defineEmits(['close', 'done'])

const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48"><rect width="48" height="48" fill="%23f0f0f0"/></svg>'
const lines = ref(props.order.items.map(i => {
  return { ...i, checked: i.returnable && props.order.items.length === 1, returnQty: i.quantity }
}))
const form = ref({ type: 'RETURN_AND_REFUND', reason: null, description: '' })
const images = ref([])
const videos = ref([])
const busy = ref(false)
const error = ref('')

// mirrors the backend: the whole order back -> full total incl. shipping, else item prices
const refundEstimate = computed(() => {
  const chosen = lines.value.filter(l => l.checked)
  const everything = lines.value.every(l => l.checked && l.returnQty === l.quantity)
  if (everything) return props.order.total
  return Math.min(props.order.total, chosen.reduce((s, l) => s + l.priceAtPurchase * l.returnQty, 0))
})

async function onSubmit() {
  const items = lines.value.filter(l => l.checked).map(l => ({ orderItemId: l.id, quantity: l.returnQty }))
  error.value = !items.length ? 'Vui lòng chọn sản phẩm cần trả'
    : !form.value.reason ? 'Vui lòng chọn lý do'
    : !images.value.length && !videos.value.length ? 'Vui lòng thêm ít nhất 1 ảnh hoặc video làm bằng chứng' : ''
  if (error.value) return
  busy.value = true
  try {
    await createReturn(props.order.id, { ...form.value, evidenceUrls: [...images.value, ...videos.value], items })
    emit('done')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.step { font-size: 15px; font-weight: 600; margin: 14px 0 10px; }
.step:first-child { margin-top: 0; }
.hint { font-size: 12px; font-weight: 400; color: var(--color-text-faint); }
.req { color: var(--color-primary); }
.line { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--color-border); font-size: 14px; }
.line.off { opacity: 0.6; }
.line input[type=checkbox] { width: 18px; margin: 0; accent-color: var(--color-primary); }
.line img { width: 48px; height: 48px; object-fit: cover; border: 1px solid var(--color-border); }
.line-info { flex: 1; min-width: 0; }
.small { font-size: 12px; }
.no-return { color: #d33; }
.qty { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.qty select { width: 70px; margin: 0; padding: 4px 6px; }
.types { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.type { display: flex; align-items: center; gap: 6px; border: 1px solid var(--color-border); padding: 8px 12px; border-radius: 3px; cursor: pointer; font-size: 14px; }
.type.active { border-color: var(--color-primary); color: var(--color-primary); }
.type input { width: auto; margin: 0; accent-color: var(--color-primary); }
.mt { margin-top: 10px; }
.refund { background: #fff8f6; padding: 12px 14px; border-radius: 3px; margin-top: 16px; font-size: 14px; }
.refund strong { color: var(--color-primary); font-size: 16px; }
.error { color: #d33; font-size: 13px; margin: 10px 0 0; }
</style>
