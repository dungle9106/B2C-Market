<template>
  <button v-if="shown" type="button" class="to-top" :class="{ lifted: lifted }" aria-label="Lên đầu trang" title="Lên đầu trang" @click="up">
    <svg viewBox="0 0 24 24" fill="none" width="22" height="22"><path d="M6 14l6-6 6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
  </button>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

// lifted: sits above the phone tab bar when that is shown
defineProps({ lifted: { type: Boolean, default: false } })
const shown = ref(false)
const onScroll = () => { shown.value = window.scrollY > 700 }
const up = () => window.scrollTo({ top: 0, behavior: 'smooth' })
onMounted(() => { window.addEventListener('scroll', onScroll, { passive: true }); onScroll() })
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<style scoped>
.to-top { position: fixed; right: 20px; bottom: 28px; z-index: 790; width: 44px; height: 44px; border-radius: 50%;
  border: 1px solid #e5e5e5; background: white; color: #555; cursor: pointer; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  display: flex; align-items: center; justify-content: center; }
.to-top:hover { color: var(--color-primary); border-color: var(--color-primary); }
@media (max-width: 760px) {
  .to-top { right: 12px; width: 40px; height: 40px; }
  .to-top.lifted { bottom: calc(68px + env(safe-area-inset-bottom)); }
}
</style>
