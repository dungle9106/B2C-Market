<template>
  <BaseModal :title="count > 1 ? `Chuẩn bị hàng cho ${count} đơn` : 'Chuẩn bị hàng'" :width="520" @close="$emit('close')">
    <p class="hint">Đóng gói hàng, chọn cách giao cho đơn vị vận chuyển. Mã vận đơn được cấp ngay để bạn in phiếu giao hàng.</p>
    <div class="methods">
      <label class="method" :class="{ on: method === 'PICKUP' }">
        <input v-model="method" type="radio" value="PICKUP" />
        <strong>🚚 Lấy hàng tận nơi</strong>
        <span>Shipper đến địa chỉ lấy hàng của shop</span>
      </label>
      <label class="method" :class="{ on: method === 'DROPOFF' }">
        <input v-model="method" type="radio" value="DROPOFF" />
        <strong>🏤 Gửi tại bưu cục</strong>
        <span>Bạn tự mang hàng ra bưu cục gần nhất</span>
      </label>
    </div>
    <label class="lbl">{{ method === 'PICKUP' ? 'Ngày lấy hàng' : 'Ngày gửi hàng' }}</label>
    <select v-model="date">
      <option v-for="d in days" :key="d.value" :value="d.value">{{ d.label }}</option>
    </select>
    <p v-if="error" class="error">{{ error }}</p>
    <template #footer>
      <button type="button" class="btn ghost" @click="$emit('close')">Hủy</button>
      <button type="button" class="btn" :disabled="busy" @click="onConfirm">{{ busy ? 'Đang xử lý...' : 'Xác nhận' }}</button>
    </template>
  </BaseModal>
</template>

<script setup>
import { ref } from 'vue'
import BaseModal from '../BaseModal.vue'
import { extractError } from '../../utils/apiError'

// action(method, date) does the request (one order or a bulk call)
const props = defineProps({
  action: { type: Function, required: true },
  count: { type: Number, default: 1 }
})
const emit = defineEmits(['close', 'done'])

const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const days = Array.from({ length: 7 }, (_, i) => {
  const d = new Date(Date.now() + i * 86400000)
  const label = d.toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit' })
  return { value: iso(d), label: i === 0 ? `Hôm nay (${label})` : i === 1 ? `Ngày mai (${label})` : label }
})

const method = ref('PICKUP')
const date = ref(days[1].value)
const busy = ref(false)
const error = ref('')

async function onConfirm() {
  busy.value = true
  error.value = ''
  try {
    const result = await props.action(method.value, date.value)
    emit('done', result)
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.hint { font-size: 14px; color: var(--color-text-muted); margin: 0 0 14px; }
.methods { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 14px; }
.method { display: flex; flex-direction: column; gap: 4px; border: 1px solid #ddd; border-radius: 4px; padding: 12px; cursor: pointer; }
.method.on { border-color: var(--color-primary); background: #fffaf8; }
.method input { display: none; }
.method span { font-size: 12px; color: var(--color-text-faint); }
.lbl { display: block; font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; }
.error { color: #d33; font-size: 13px; margin: 0; }
</style>
