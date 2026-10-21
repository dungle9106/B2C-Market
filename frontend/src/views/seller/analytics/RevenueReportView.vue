<template>
  <div class="report">
    <AnalyticsToolbar />
    <div v-if="error" class="sc-card sc-empty">{{ error }}</div>

    <template v-else>
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Tổng Quan Doanh Thu</h2>
          <span class="sc-card-sub">{{ periodText }}</span>
        </div>
        <div class="kpis">
          <KpiCard label="Doanh số" hint="Tổng giá trị các đơn đặt trong kỳ (không tính đơn đã hủy, hoàn tiền hoặc chưa thanh toán)" :loading="!data"
                   :value="formatVnd(s.sales)" :current="s.sales" :previous="p.sales" :previous-text="formatVnd(p.sales)" />
          <KpiCard label="Đơn hàng" hint="Số đơn đặt trong kỳ (không tính đơn đã hủy/hoàn tiền)" :loading="!data"
                   :value="s.orders" :current="s.orders" :previous="p.orders" :previous-text="p.orders" />
          <KpiCard label="Giá trị trung bình / đơn" hint="Doanh số chia cho số đơn" :loading="!data"
                   :value="formatVnd(s.avgOrderValue)" :current="s.avgOrderValue" :previous="p.avgOrderValue" :previous-text="formatVnd(p.avgOrderValue)" />
          <KpiCard label="Sản phẩm đã bán" :loading="!data" :value="s.items" :current="s.items" :previous="p.items" :previous-text="p.items" />
          <KpiCard label="Người mua" hint="Số khách khác nhau đã đặt hàng" :loading="!data" :value="s.buyers" :current="s.buyers" :previous="p.buyers" :previous-text="p.buyers" />
          <KpiCard label="Tỷ lệ hủy / hoàn tiền" hint="Đơn bị hủy hoặc hoàn tiền trên tổng số đơn đặt trong kỳ" :loading="!data" points invert
                   :value="percent(s.cancelRate)" :current="s.cancelRate" :previous="p.cancelRate" :previous-text="percent(p.cancelRate)" />
        </div>
      </section>

      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Biểu Đồ Doanh Số</h2>
          <span class="sc-card-sub">Theo {{ granLabel }}</span>
          <div class="metric-pick">
            <button v-for="m in METRICS" :key="m.key" type="button" :class="{ active: metric === m.key }" @click="metric = m.key">{{ m.label }}</button>
          </div>
        </div>
        <TrendChart v-if="data" :labels="labels" :series="chartSeries" :height="280" />
        <div v-else class="sc-empty">Đang tải...</div>
      </section>

      <div class="two-col">
        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">Chi Tiết Theo {{ granLabel }}</h2>
            <span class="sc-card-sub">Mới nhất ở trên</span>
          </div>
          <div class="sc-table-wrap table-scroll">
            <table class="sc-table">
              <thead><tr><th>{{ granLabel }}</th><th class="r">Doanh số</th><th class="r">Đơn</th><th class="r">SP đã bán</th><th class="r">TB / đơn</th></tr></thead>
              <tbody>
                <tr v-for="b in rowsDesc" :key="b.key" :class="{ zero: !b.orders }">
                  <td>{{ b.label }}</td>
                  <td class="r">{{ formatVnd(b.sales) }}</td>
                  <td class="r">{{ b.orders }}</td>
                  <td class="r">{{ b.items }}</td>
                  <td class="r">{{ b.orders ? formatVnd(Math.round(b.sales / b.orders)) : '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">Đơn Hàng Theo Trạng Thái</h2>
            <span class="sc-card-sub">Tất cả đơn đặt trong kỳ</span>
          </div>
          <div v-if="data && !data.statuses.length" class="sc-empty">Chưa có đơn hàng nào trong kỳ này</div>
          <ul v-else class="status-list">
            <li v-for="st in data?.statuses || []" :key="st.status">
              <div class="st-top">
                <span class="st-name"><i :style="{ background: STATUS_COLOR[st.status] }"></i>{{ statusLabel(st.status) }}</span>
                <span>{{ st.orders }} đơn · {{ formatVnd(st.value) }}</span>
              </div>
              <div class="st-bar"><span :style="{ width: (st.orders / totalOrders) * 100 + '%', background: STATUS_COLOR[st.status] }"></span></div>
            </li>
          </ul>
          <div v-if="data" class="delivered">
            Doanh thu từ đơn <strong>đã giao thành công</strong>: <strong class="text-primary">{{ formatVnd(s.delivered) }}</strong>
            <div class="text-faint small">Giá trị đơn đã hủy / hoàn tiền: {{ formatVnd(s.cancelledValue) }}</div>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import AnalyticsToolbar from '../../../components/analytics/AnalyticsToolbar.vue'
import KpiCard from '../../../components/analytics/KpiCard.vue'
import TrendChart from '../../../components/analytics/TrendChart.vue'
import { revenueReport } from '../../../api/analytics'
import { useAnalyticsRange, percent } from '../../../composables/useAnalyticsRange'
import { formatVnd } from '../../../utils/format'
import { statusLabel } from '../../../utils/orderStatus'
import { extractError } from '../../../utils/apiError'

const { params } = useAnalyticsRange()
const data = ref(null)
const error = ref('')
const metric = ref('sales')

const METRICS = [
  { key: 'sales', label: 'Doanh số' },
  { key: 'orders', label: 'Đơn hàng' },
  { key: 'items', label: 'Sản phẩm' },
  { key: 'buyers', label: 'Người mua' }
]
const STATUS_COLOR = {
  AWAITING_PAYMENT: '#f5a623', PENDING: '#f5a623', CONFIRMED: '#2673dd', SHIPPING: '#7b61ff',
  DELIVERED: '#1e9e4a', CANCELLED: '#999', RETURN_REQUESTED: '#e67e22', REFUNDED: '#d33'
}

const s = computed(() => data.value?.summary || {})
const p = computed(() => data.value?.previous || {})
const series = computed(() => data.value?.series || [])
const labels = computed(() => series.value.map(b => b.label))
const rowsDesc = computed(() => [...series.value].reverse())
const totalOrders = computed(() => Math.max(1, (data.value?.statuses || []).reduce((a, x) => a + x.orders, 0)))
const granLabel = computed(() => ({ day: 'Ngày', week: 'Tuần', month: 'Tháng' })[params.value.granularity])
const fmtDay = (d) => d.split('-').reverse().join('/')
const periodText = computed(() => data.value
  ? `${fmtDay(data.value.from)} – ${fmtDay(data.value.to)} · so với ${fmtDay(data.value.previousFrom)} – ${fmtDay(data.value.previousTo)}`
  : '')

// sales as bars + orders as a line on the right axis, or one count metric alone
const chartSeries = computed(() => {
  const v = (k) => series.value.map(b => Number(b[k]))
  if (metric.value === 'sales') {
    return [
      { name: 'Doanh số', values: v('sales'), color: '#ee4d2d', type: 'bar', format: formatVnd },
      { name: 'Đơn hàng', values: v('orders'), color: '#2673dd', type: 'line', axis: 'right', integer: true }
    ]
  }
  const m = METRICS.find(x => x.key === metric.value)
  return [{ name: m.label, values: v(metric.value), color: '#2673dd', type: 'bar', integer: true }]
})

async function load() {
  error.value = ''
  try {
    const { data: d } = await revenueReport(params.value)
    data.value = d
  } catch (e) {
    error.value = extractError(e)
  }
}
watch(params, load, { immediate: true })
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }
.metric-pick { margin-left: auto; display: flex; gap: 6px; }
.metric-pick button { background: white; border: 1px solid #ddd; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.metric-pick button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.two-col { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr); gap: 16px; align-items: start; }
.two-col .sc-card { margin-bottom: 0; }
.table-scroll { max-height: 420px; overflow-y: auto; }
.table-scroll thead th { position: sticky; top: 0; z-index: 1; }
.r { text-align: right; white-space: nowrap; }
.sc-table td { padding: 10px 12px; }
tr.zero td { color: var(--color-text-faint); }
.status-list { list-style: none; margin: 0; padding: 0; }
.status-list li { padding: 8px 0; }
.st-top { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 6px; gap: 8px; }
.st-name { display: inline-flex; align-items: center; gap: 6px; }
.st-name i { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.st-bar { height: 6px; background: #f2f2f2; border-radius: 999px; overflow: hidden; }
.st-bar span { display: block; height: 100%; border-radius: 999px; }
.delivered { margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--color-border); font-size: 14px; }
.small { font-size: 12px; margin-top: 4px; }
@media (max-width: 1200px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } .two-col { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .kpis { grid-template-columns: 1fr 1fr; } .metric-pick { margin-left: 0; flex-wrap: wrap; } }
</style>
