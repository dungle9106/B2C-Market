import { ref, computed, watch, onBeforeUnmount } from 'vue'

// Live "hh:mm:ss" until `endRef` (a ref/getter of an ISO date string).
export function useCountdown(endRef) {
  const now = ref(Date.now())
  const timer = setInterval(() => { now.value = Date.now() }, 1000)
  onBeforeUnmount(() => clearInterval(timer))

  const remaining = computed(() => {
    const end = typeof endRef === 'function' ? endRef() : endRef.value
    return end ? Math.max(0, new Date(end).getTime() - now.value) : 0
  })
  const parts = computed(() => {
    const s = Math.floor(remaining.value / 1000)
    const pad = (x) => String(x).padStart(2, '0')
    return [pad(Math.floor(s / 3600)), pad(Math.floor((s % 3600) / 60)), pad(s % 60)]
  })
  const ended = computed(() => remaining.value <= 0)
  return { parts, ended }
}
