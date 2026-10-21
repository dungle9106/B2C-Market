<template>
  <nav v-if="totalPages > 1" class="pager" aria-label="Phân trang">
    <button :disabled="page === 0" aria-label="Trang trước" @click="go(page - 1)">‹</button>
    <template v-for="(p, i) in pages" :key="i">
      <span v-if="p === null" class="gap">…</span>
      <button v-else :class="{ active: p === page }" @click="go(p)">{{ p + 1 }}</button>
    </template>
    <button :disabled="page >= totalPages - 1" aria-label="Trang sau" @click="go(page + 1)">›</button>
  </nav>
</template>

<script setup>
import { computed } from 'vue'

// page is 0-based, like the backend's PageResponse
const props = defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, required: true }
})
const emit = defineEmits(['change'])

// 1 … 4 5 [6] 7 8 … 20
const pages = computed(() => {
  const n = props.totalPages
  const cur = props.page
  if (n <= 7) return Array.from({ length: n }, (_, i) => i)
  const set = new Set([0, n - 1, cur - 1, cur, cur + 1].filter(p => p >= 0 && p < n))
  const sorted = [...set].sort((a, b) => a - b)
  const out = []
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) out.push(null)
    out.push(p)
  })
  return out
})

function go(p) {
  if (p >= 0 && p < props.totalPages && p !== props.page) emit('change', p)
}
</script>

<style scoped>
.pager { display: flex; justify-content: center; align-items: center; gap: 6px; margin: 24px 0 8px; flex-wrap: wrap; }
.pager button {
  min-width: 36px; height: 32px; padding: 0 8px;
  border: none; background: transparent; border-radius: 2px;
  font-size: 15px; color: var(--color-text-muted); cursor: pointer;
}
.pager button:hover:not(:disabled) { color: var(--color-primary); }
.pager button.active { background: var(--color-primary); color: white; }
.pager button:disabled { opacity: 0.35; cursor: default; }
.gap { color: var(--color-text-faint); }
</style>
