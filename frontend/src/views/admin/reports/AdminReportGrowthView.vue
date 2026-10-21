<template>
  <div class="report">
    <AnalyticsToolbar :tabs="REPORT_TABS" :exporter="exportReport" file-prefix="bao-cao-toan-san" />
    <div v-if="error" class="sc-card sc-empty">{{ error }}</div>

    <template v-else>
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Báo Cáo Tăng Trưởng</h2>
          <span class="sc-card-sub">{{ periodText }} · mỗi {{ granLabel.toLowerCase() }} so với {{ granLabel.toLowerCase() }} liền trước</span>
        </div>
        <div class="kpis">
          <div class="kpi"><span>Tổng GMV</span><b>{{ data ? formatVnd(sm.gmv) : '–' }}</b><small>{{ data ? num(sm.orders) + ' đơn hàng' : '' }}</small></div>
          <div class="kpi"><span>Tăng trưởng bình quân / {{ granLabel.toLowerCase() }}</span>
            <b :class="sm.compoundGrowth == null ? '' : cls(sm.compoundGrowth)">{{ data ? kpiPct(sm.compoundGrowth) : '–' }}</b>
            <small>{{ sm.compoundGrowth == null && data ? `Cần doanh số ở ${granLabel.toLowerCase()} đầu và cuối` : 'GMV, tính kép' }}</small></div>
          <div class="kpi"><span>{{ granLabel }} đầu → {{ granLabel.toLowerCase() }} cuối</span>
            <b :class="sm.firstToLast == null ? '' : cls(sm.firstToLast)">{{ data ? kpiPct(sm.firstToLast) : '–' }}</b>
            <small>{{ sm.firstToLast == null && data ? `${granLabel} đầu chưa có doanh số` : 'GMV' }}</small></div>
          <div class="kpi"><span>{{ granLabel }} cao nhất</span><b>{{ sm.bestLabel || '–' }}</b><small>{{ sm.bestLabel ? formatVnd(sm.bestGmv) : '' }}</small></div>
          <div class="kpi"><span>Người dùng mới</span><b>{{ data ? '+' + num(sm.newUsers) : '–' }}</b><small>tổng {{ data ? num(sm.totalUsers) : '' }}</small></div>
          <div class="kpi"><span>Shop mới</span><b>{{ data ? '+' + num(sm.newShops) : '–' }}</b><small>tổng {{ data ? num(sm.totalShops) : '' }}</small></div>
        </div>
      </section>

      <div class="two-col">
        <section class="sc-card">
          <div class="sc-card-head"><h2 class="sc-card-title">GMV & Tốc Độ Tăng Trưởng</h2></div>
          <TrendChart v-if="data" :labels="labels" :series="gmvSeries" :height="270" :right-tick-format="(v) => v + '%'" />
        </section>
        <section class="sc-card">
          <div class="sc-card-head"><h2 class="sc-card-title">Người Dùng & Người Mua</h2></div>
          <TrendChart v-if="data" :labels="labels" :series="userSeries" :height="270" />
        </section>
      </div>

      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Chi Tiết Theo {{ granLabel }}</h2>
          <span class="sc-card-sub">Mới nhất ở trên</span>
        </div>
        <div v-if="!data" class="sc-empty">Đang tải...</div>
        <div v-else class="sc-table-wrap">
          <table class="sc-table">
            <thead>
              <tr><th>{{ granLabel }}</th><th class="r">GMV</th><th class="r">+/-</th><th class="r">Đơn hàng</th><th class="r">+/-</th><th class="r">TB / đơn</th>
                <th class="r">Người mua</th><th class="r">Mua lần đầu</th><th class="r">Mua lại</th><th class="r">Người dùng mới</th><th class="r">Shop mới</th><th class="r">Shop có đơn</th><th class="r">Tổng người dùng</th></tr>
            </thead>
            <tbody>
              <tr v-for="r in rowsDesc" :key="r.key" :class="{ zero: !r.orders && !r.newUsers }">
                <td>{{ r.label }}</td>
                <td class="r"><b>{{ formatVnd(r.gmv) }}</b></td>
                <td class="r"><span class="g" :class="cls(r.gmvGrowth)">{{ pctText(r.gmvGrowth) }}</span></td>
                <td class="r">{{ num(r.orders) }}</td>
                <td class="r"><span class="g" :class="cls(r.ordersGrowth)">{{ pctText(r.ordersGrowth) }}</span></td>
                <td class="r">{{ formatVnd(r.avgOrderValue) }}</td>
                <td class="r">{{ num(r.activeBuyers) }}</td>
                <td class="r">{{ num(r.newBuyers) }}</td>
                <td class="r">{{ num(r.returningBuyers) }}</td>
                <td class="r">{{ num(r.newUsers) }}</td>
                <td class="r">{{ num(r.newShops) }}</td>
                <td class="r">{{ num(r.activeShops) }}</td>
                <td class="r">{{ num(r.totalUsers) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AnalyticsToolbar from '../../../components/analytics/AnalyticsToolbar.vue'
import TrendChart from '../../../components/analytics/TrendChart.vue'
import { useAnalyticsRange, isoDate } from '../../../composables/useAnalyticsRange'
import { platformGrowth } from '../../../api/admin'
import { formatVnd } from '../../../utils/format'
import { extractError } from '../../../utils/apiError'
import { REPORT_TABS, exportReport } from './reportTabs'

const route = useRoute()
const { params, setRange } = useAnalyticsRange()
const data = ref(null)
const error = ref('')

const sm = computed(() => data.value?.summary || {})
const rows = computed(() => data.value?.rows || [])
const rowsDesc = computed(() => [...rows.value].reverse())
const labels = computed(() => rows.value.map(r => r.label))
const granLabel = computed(() => ({ day: 'Ngày', week: 'Tuần', month: 'Tháng' })[params.value.granularity])
const num = (v) => Number(v || 0).toLocaleString('vi-VN')
const fmtDay = (d) => new Date(d).toLocaleDateString('vi-VN')
const periodText = computed(() => (data.value ? `${fmtDay(data.value.from)} – ${fmtDay(data.value.to)}` : ''))
const pctText = (v) => (v == null ? 'Mới' : (v > 0 ? '▲ ' : v < 0 ? '▼ ' : '') + Math.abs(v).toLocaleString('vi-VN') + '%')
// summary figures: no comparison possible is shown as a dash, not as "new"
const kpiPct = (v) => (v == null ? '—' : pctText(v))
const cls = (v) => (v == null || v > 0 ? 'up' : v < 0 ? 'down' : '')

const gmvSeries = computed(() => [
  { name: 'GMV', values: rows.value.map(r => Number(r.gmv)), color: '#ee4d2d', type: 'bar', format: formatVnd },
  { name: 'Tăng trưởng GMV', values: rows.value.map(r => r.gmvGrowth), color: '#1e9e4a', type: 'line', axis: 'right',
    format: (v) => (v == null ? '—' : v + '%') }
])
const userSeries = computed(() => [
  { name: 'Người dùng mới', values: rows.value.map(r => r.newUsers), color: '#2673dd', type: 'bar', integer: true },
  { name: 'Người mua lần đầu', values: rows.value.map(r => r.newBuyers), color: '#f59e0b', type: 'bar', integer: true },
  { name: 'Người mua', values: rows.value.map(r => r.activeBuyers), color: '#1e9e4a', type: 'line', integer: true }
])

async function load() {
  error.value = ''
  try {
    data.value = (await platformGrowth(params.value)).data
  } catch (e) {
    error.value = extractError(e)
  }
}

onMounted(() => {
  // growth reads best month by month: default to the last 12 months
  if (!route.query.from) {
    const now = new Date()
    setRange({ from: isoDate(new Date(now.getFullYear(), now.getMonth() - 11, 1)), to: isoDate(now), granularity: 'month' })
  }
})
watch(params, () => { if (route.query.from) load() }, { immediate: true })
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 12px; display: flex; flex-direction: column; gap: 3px; }
.kpi span, .kpi small { font-size: 12px; color: var(--color-text-muted); }
.kpi b { font-size: 19px; }
.up { color: #1e9e4a; }
.down { color: #d33; }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: start; }
.two-col .sc-card { margin-bottom: 0; }
.r { text-align: right; white-space: nowrap; }
.g { font-size: 12px; font-weight: 600; }
tr.zero td { color: var(--color-text-faint); }
@media (max-width: 1200px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } .two-col { grid-template-columns: 1fr; } }
</style>
