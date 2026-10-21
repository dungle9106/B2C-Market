import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

// The report period lives in the URL (?from=&to=&g=) so it is shared by the four report tabs,
// survives a reload and can be bookmarked. Default: the last 30 days, by day.

export function isoDate(d) {
  const pad = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function daysAgo(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d
}

const DATE = /^\d{4}-\d{2}-\d{2}$/

export function useAnalyticsRange() {
  const route = useRoute()
  const router = useRouter()

  const params = computed(() => {
    const q = route.query
    const to = DATE.test(q.to || '') ? q.to : isoDate(new Date())
    const from = DATE.test(q.from || '') ? q.from : isoDate(daysAgo(29))
    const granularity = ['day', 'week', 'month'].includes(q.g) ? q.g : 'day'
    return { from, to, granularity }
  })

  function setRange(patch) {
    const next = { ...params.value, ...patch }
    router.replace({ query: { ...route.query, from: next.from, to: next.to, g: next.granularity } })
  }

  return { params, setRange }
}

// "▲ 12,5%" vs the previous period; for rates (already in %) the change is in percentage points
export function changeOf(current, previous, { points = false, pointsUnit = 'điểm %', invert = false } = {}) {
  const c = Number(current || 0)
  const p = Number(previous || 0)
  if (c === p) return { dir: 'flat', text: '— 0%' }
  let text
  if (points) {
    text = Math.abs(c - p).toLocaleString('vi-VN', { maximumFractionDigits: 2 }) + ' ' + pointsUnit
  } else if (p === 0) {
    return { dir: invert ? 'down' : 'up', text: '▲ Mới' }
  } else {
    text = Math.abs(((c - p) / p) * 100).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + '%'
  }
  const up = c > p
  return { dir: up !== invert ? 'up' : 'down', text: (up ? '▲ ' : '▼ ') + text }
}

// 1250000 -> "1,25tr", 3500 -> "3,5k" for chart axes
export function compact(n) {
  const v = Number(n || 0)
  const abs = Math.abs(v)
  const fmt = (x) => x.toLocaleString('vi-VN', { maximumFractionDigits: 2 })
  if (abs >= 1e9) return fmt(v / 1e9) + ' tỷ'
  if (abs >= 1e6) return fmt(v / 1e6) + 'tr'
  if (abs >= 1e3) return fmt(v / 1e3) + 'k'
  return fmt(v)
}

export const percent = (v) => Number(v || 0).toLocaleString('vi-VN', { maximumFractionDigits: 2 }) + '%'
