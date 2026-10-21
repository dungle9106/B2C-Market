<template>
  <div class="overlay" role="dialog" aria-modal="true" :aria-label="title" @click.self="$emit('close')">
    <div class="modal" :style="{ width: width + 'px' }">
      <header class="modal-head">
        <h2>{{ title }}</h2>
        <button type="button" class="close" aria-label="Đóng" @click="$emit('close')">✕</button>
      </header>
      <div class="modal-body"><slot /></div>
      <footer v-if="$slots.footer" class="modal-foot"><slot name="footer" /></footer>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'

defineProps({
  title: { type: String, required: true },
  width: { type: Number, default: 520 }
})
const emit = defineEmits(['close'])

const onKey = (e) => { if (e.key === 'Escape') emit('close') }
onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
</script>

<style scoped>
.overlay { position: fixed; inset: 0; z-index: 900; background: rgba(0, 0, 0, 0.4); display: flex; align-items: center; justify-content: center; padding: 16px; }
.modal { max-width: 100%; max-height: 90vh; display: flex; flex-direction: column; background: white; border-radius: 3px; box-shadow: 0 8px 30px rgba(0, 0, 0, 0.2); }
.modal-head { display: flex; align-items: center; justify-content: space-between; padding: 18px 24px; border-bottom: 1px solid var(--color-border); }
.modal-head h2 { margin: 0; font-size: 18px; font-weight: 500; }
.close { background: none; border: none; font-size: 18px; cursor: pointer; color: var(--color-text-muted); }
.modal-body { padding: 16px 24px; overflow-y: auto; }
.modal-foot { display: flex; justify-content: flex-end; gap: 10px; padding: 14px 24px; border-top: 1px solid var(--color-border); }
</style>
