<template>
  <BaseModal :title="title" @close="$emit('close')">
    <div class="dialog">
      <p v-if="subtitle" class="sub">{{ subtitle }}</p>
      <div class="presets">
        <button v-for="r in presets" :key="r" type="button" @click="reason = r">{{ r }}</button>
      </div>
      <textarea v-model="reason" rows="3" maxlength="500" :placeholder="placeholder"></textarea>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="buttons">
        <button type="button" class="btn ghost" @click="$emit('close')">Hủy</button>
        <button type="button" class="btn" :disabled="busy || reason.trim().length < 5" @click="$emit('submit', reason.trim())">{{ busy ? 'Đang xử lý...' : confirmLabel }}</button>
      </div>
    </div>
  </BaseModal>
</template>

<script setup>
import { ref } from 'vue'
import BaseModal from '../BaseModal.vue'

defineProps({
  title: { type: String, required: true },
  subtitle: String,
  presets: { type: Array, default: () => [] },
  placeholder: { type: String, default: 'Lý do (ít nhất 5 ký tự), sẽ được gửi cho người bán' },
  confirmLabel: { type: String, default: 'Xác nhận' },
  busy: Boolean,
  error: String
})
defineEmits(['close', 'submit'])
const reason = ref('')
</script>

<style scoped>
.dialog { min-width: min(460px, 85vw); }
.sub { margin: 0 0 10px; font-size: 14px; color: var(--color-text-muted); }
.presets { display: flex; flex-wrap: wrap; gap: 6px; }
.presets button { background: white; border: 1px solid #ddd; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.presets button:hover { border-color: var(--color-primary); color: var(--color-primary); }
textarea { margin: 10px 0 0; width: 100%; font-family: inherit; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
.buttons { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
</style>
