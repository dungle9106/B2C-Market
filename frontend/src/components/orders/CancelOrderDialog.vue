<template>
  <BaseModal :title="title" :width="500" @close="$emit('close')">
    <p class="hint">{{ hint }}</p>
    <label v-for="r in reasons" :key="r" class="reason">
      <input v-model="picked" type="radio" name="cancel-reason" :value="r" />
      <span>{{ r }}</span>
    </label>
    <label class="reason">
      <input v-model="picked" type="radio" name="cancel-reason" value="__other" />
      <span>Khác</span>
    </label>
    <textarea v-if="picked === '__other'" v-model.trim="other" rows="2" maxlength="300" placeholder="Nhập lý do..."></textarea>
    <p v-if="error" class="error">{{ error }}</p>

    <template #footer>
      <button type="button" class="btn ghost" @click="$emit('close')">Không phải bây giờ</button>
      <button type="button" class="btn" :disabled="!reason || busy" @click="onConfirm">{{ busy ? 'Đang xử lý...' : confirmLabel }}</button>
    </template>
  </BaseModal>
</template>

<script setup>
import { ref, computed } from 'vue'
import BaseModal from '../BaseModal.vue'
import { extractError } from '../../utils/apiError'

// action(reason) performs the cancellation (buyer or seller endpoint)
const props = defineProps({
  reasons: { type: Array, required: true },
  action: { type: Function, required: true },
  hint: { type: String, default: 'Vui lòng chọn lý do hủy. Sản phẩm sẽ được hoàn lại kho và voucher (nếu có) được hoàn lại.' },
  title: { type: String, default: 'Lý Do Hủy' },
  confirmLabel: { type: String, default: 'Hủy Đơn Hàng' }
})
const emit = defineEmits(['close', 'done'])

const picked = ref(null)
const other = ref('')
const busy = ref(false)
const error = ref('')
const reason = computed(() => (picked.value === '__other' ? other.value : picked.value))

async function onConfirm() {
  busy.value = true
  error.value = ''
  try {
    await props.action(reason.value)
    emit('done')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.hint { font-size: 13px; color: var(--color-text-muted); margin: 0 0 12px; }
.reason { display: flex; align-items: flex-start; gap: 10px; padding: 9px 0; cursor: pointer; font-size: 14px; }
.reason input { width: auto; margin: 3px 0 0; accent-color: var(--color-primary); }
textarea { margin: 6px 0 0; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
</style>
