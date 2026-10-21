<template>
  <section class="sc-card toolbar-card">
    <nav class="sc-tabs report-tabs">
      <router-link v-for="t in tabs" :key="t.name" :to="{ name: t.name, query: route.query }" :class="{ active: route.name === t.name }">{{ t.label }}</router-link>
    </nav>
    <div class="bar">
      <div class="presets">
        <button v-for="p in presets" :key="p.key" type="button" class="preset" :class="{ active: activePreset === p.key }" @click="apply(p)">{{ p.label }}</button>
      </div>
      <div class="dates">
        <input type="date" :value="params.from" :max="params.to" @change="onDate('from', $event.target.value)" />
        <span>–</span>
        <input type="date" :value="params.to" :min="params.from" :max="today" @change="onDate('to', $event.target.value)" />
      </div>
      <div v-if="granularity" class="gran">
        <button v-for="g in GRANS" :key="g.key" type="button" :class="{ active: params.granularity === g.key }" @click="setRange({ granularity: g.key })">{{ g.label }}</button>
      </div>
      <button type="button" class="btn sm secondary export" :disabled="exporting" @click="onExport">{{ exporting ? 'Đang xuất...' : '⬇ Xuất báo cáo Excel' }}</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAnalyticsRange, isoDate, daysAgo } from '../../composables/useAnalyticsRange'
import { exportReport } from '../../api/analytics'
import { saveBlob } from '../../utils/download'
import { extractError } from '../../utils/apiError'

// tabs / exporter: the admin reports reuse this bar with their own pages and export
const props = defineProps({
  granularity: { type: Boolean, default: true },
  tabs: { type: Array, default: null },
  exporter: { type: Function, default: null },
  filePrefix: { type: String, default: 'bao-cao' }
})

const route = useRoute()
const { params, setRange } = useAnalyticsRange()
const today = isoDate(new Date())
const exporting = ref(false)
const error = ref('')

const SELLER_TABS = [
  { name: 'seller-analytics-revenue', label: 'Doanh thu' },
  { name: 'seller-analytics-products', label: 'Sản phẩm & Tồn kho' },
  { name: 'seller-analytics-traffic', label: 'Lượt xem & Chuyển đổi' },
  { name: 'seller-analytics-reviews', label: 'Đánh giá khách hàng' }
]
const tabs = computed(() => props.tabs || SELLER_TABS)
const GRANS = [{ key: 'day', label: 'Ngày' }, { key: 'week', label: 'Tuần' }, { key: 'month', label: 'Tháng' }]

const now = new Date()
const firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1)
const presets = [
  { key: '7', label: '7 ngày qua', from: isoDate(daysAgo(6)), to: today, granularity: 'day' },
  { key: '30', label: '30 ngày qua', from: isoDate(daysAgo(29)), to: today, granularity: 'day' },
  { key: 'month', label: 'Tháng này', from: isoDate(firstOfMonth), to: today, granularity: 'day' },
  { key: 'lastMonth', label: 'Tháng trước', from: isoDate(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
    to: isoDate(new Date(now.getFullYear(), now.getMonth(), 0)), granularity: 'day' },
  { key: '12w', label: '12 tuần', from: isoDate(daysAgo(83)), to: today, granularity: 'week' },
  { key: '12m', label: '12 tháng', from: isoDate(new Date(now.getFullYear(), now.getMonth() - 11, 1)), to: today, granularity: 'month' }
]
const activePreset = computed(() => presets.find(p => p.from === params.value.from && p.to === params.value.to)?.key)

function apply(p) {
  error.value = ''
  setRange({ from: p.from, to: p.to, granularity: p.granularity })
}

function onDate(key, value) {
  if (!value) return
  const next = { ...params.value, [key]: value }
  if (next.from > next.to) {
    error.value = 'Ngày bắt đầu phải trước ngày kết thúc'
    return
  }
  if ((new Date(next.to) - new Date(next.from)) / 86400000 + 1 > 366) {
    error.value = 'Mỗi lần chỉ xem được tối đa 366 ngày'
    return
  }
  error.value = ''
  setRange({ [key]: value })
}

async function onExport() {
  exporting.value = true
  error.value = ''
  try {
    saveBlob(await (props.exporter || exportReport)(params.value), `${props.filePrefix}-${params.value.from}_${params.value.to}.xlsx`)
  } catch (e) {
    error.value = extractError(e) || 'Không xuất được báo cáo'
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.toolbar-card { padding-bottom: 14px; }
.report-tabs { margin-top: -8px; }
.bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.presets { display: flex; gap: 6px; flex-wrap: wrap; }
.preset, .gran button {
  background: white; border: 1px solid #ddd; border-radius: 4px; padding: 6px 10px; font-size: 13px; cursor: pointer; color: var(--color-text);
}
.preset:hover, .gran button:hover { border-color: var(--color-primary); color: var(--color-primary); }
.preset.active, .gran button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.dates { display: flex; align-items: center; gap: 6px; }
.dates input { margin: 0; padding: 5px 8px; font-size: 13px; width: 140px; }
.gran { display: flex; }
.gran button { border-radius: 0; }
.gran button:first-child { border-radius: 4px 0 0 4px; }
.gran button:last-child { border-radius: 0 4px 4px 0; }
.gran button + button { margin-left: -1px; }
.export { margin-left: auto; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
</style>
