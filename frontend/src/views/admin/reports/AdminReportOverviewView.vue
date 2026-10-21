<template>
  <div class="report">
    <AnalyticsToolbar :tabs="REPORT_TABS" :exporter="exportReport" file-prefix="bao-cao-toan-san" />
    <div v-if="error" class="sc-card sc-empty">{{ error }}</div>

    <template v-else>
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Tổng Quan Kinh Doanh Toàn Sàn</h2>
          <span class="sc-card-sub">{{ periodText }}</span>
        </div>
        <div class="kpis">
          <KpiCard label="Giá trị giao dịch (GMV)" hint="Tổng giá trị đơn đặt trong kỳ trên toàn sàn (không tính đơn hủy, hoàn tiền, chưa thanh toán)" :loading="!data"
                   :value="formatVnd(s.gmv)" :current="s.gmv" :previous="p.gmv" :previous-text="formatVnd(p.gmv)" />
          <KpiCard label="Đơn hàng" :loading="!data" :value="num(s.orders)" :current="s.orders" :previous="p.orders" :previous-text="num(p.orders)" />
          <KpiCard label="Giá trị trung bình / đơn" :loading="!data" :value="formatVnd(s.avgOrderValue)" :current="s.avgOrderValue" :previous="p.avgOrderValue" :previous-text="formatVnd(p.avgOrderValue)" />
          <KpiCard label="Người dùng mới" hint="Tài khoản mới đăng ký trong kỳ (không tính nhân viên quản trị)" :loading="!data"
                   :value="num(s.newUsers)" :current="s.newUsers" :previous="p.newUsers" :previous-text="num(p.newUsers)" />
          <KpiCard label="Shop mới" :loading="!data" :value="num(s.newShops)" :current="s.newShops" :previous="p.newShops" :previous-text="num(p.newShops)" />
          <KpiCard label="Người mua đặt hàng" hint="Số người mua khác nhau có đơn trong kỳ" :loading="!data"
                   :value="num(s.buyers)" :current="s.buyers" :previous="p.buyers" :previous-text="num(p.buyers)" />
          <KpiCard label="Người mua lần đầu" hint="Người mua có đơn đầu tiên trên Shoppi trong kỳ" :loading="!data"
                   :value="num(s.firstTimeBuyers)" :current="s.firstTimeBuyers" :previous="p.firstTimeBuyers" :previous-text="num(p.firstTimeBuyers)" />
          <KpiCard label="Shop có đơn" :loading="!data" :value="num(s.shopsSelling)" :current="s.shopsSelling" :previous="p.shopsSelling" :previous-text="num(p.shopsSelling)" />
          <KpiCard label="Phí sàn (ước tính)" :hint="`${data?.commissionRate ?? ''}% trên giá trị hàng sau khuyến mãi của shop`" :loading="!data"
                   :value="formatVnd(s.commission)" :current="s.commission" :previous="p.commission" :previous-text="formatVnd(p.commission)" />
          <KpiCard label="Chi phí Shoppi Voucher" hint="Tiền giảm giá Shoppi tài trợ trên các đơn" :loading="!data" invert
                   :value="formatVnd(s.platformVoucherCost)" :current="s.platformVoucherCost" :previous="p.platformVoucherCost" :previous-text="formatVnd(p.platformVoucherCost)" />
          <KpiCard label="Tỷ lệ hủy / hoàn tiền" :loading="!data" points invert
                   :value="percent(s.cancelRate)" :current="s.cancelRate" :previous="p.cancelRate" :previous-text="percent(p.cancelRate)" />
          <div class="kpi-total">
            <span>Đến cuối kỳ</span>
            <b>{{ data ? num(data.totals.users) : '–' }}</b> người dùng
            <b>{{ data ? num(data.totals.shops) : '–' }}</b> shop
          </div>
        </div>
      </section>

      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Diễn Biến Theo {{ granLabel }}</h2>
          <div class="metric-pick">
            <button v-for="m in METRICS" :key="m.key" type="button" :class="{ active: metric === m.key }" @click="metric = m.key">{{ m.label }}</button>
          </div>
        </div>
        <TrendChart v-if="data" :labels="labels" :series="chartSeries" :height="290" />
        <div v-else class="sc-empty">Đang tải...</div>
      </section>

      <div class="two-col">
        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">Doanh Thu Theo Ngành Hàng</h2>
            <router-link :to="{ name: 'admin-report-top', query: route.query }" class="more">Chi tiết ›</router-link>
          </div>
          <div v-if="data && !data.categories.length" class="sc-empty">Chưa có đơn hàng trong kỳ</div>
          <ul v-else-if="data" class="bars">
            <li v-for="(c, i) in data.categories" :key="c.name">
              <div class="bar-top"><span><i :style="{ background: COLORS[i % COLORS.length] }"></i>{{ c.name }}</span><span>{{ formatVnd(c.revenue) }} · {{ c.share }}%</span></div>
              <div class="bar"><span :style="{ width: c.share + '%', background: COLORS[i % COLORS.length] }"></span></div>
            </li>
          </ul>
        </section>

        <section class="sc-card">
          <div class="sc-card-head"><h2 class="sc-card-title">Trạng Thái Đơn Hàng</h2></div>
          <ul v-if="data" class="bars">
            <li v-for="st in data.statuses" :key="st.status">
              <div class="bar-top"><span>{{ statusLabel(st.status) }}</span><span>{{ num(st.orders) }} đơn · {{ formatVnd(st.value) }}</span></div>
              <div class="bar"><span :style="{ width: (st.orders / totalOrders * 100) + '%', background: STATUS_COLOR[st.status] || '#999' }"></span></div>
            </li>
          </ul>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import AnalyticsToolbar from '../../../components/analytics/AnalyticsToolbar.vue'
import KpiCard from '../../../components/analytics/KpiCard.vue'
import TrendChart from '../../../components/analytics/TrendChart.vue'
import { useAnalyticsRange, percent } from '../../../composables/useAnalyticsRange'
import { platformOverview } from '../../../api/admin'
import { statusLabel } from '../../../utils/orderStatus'
import { formatVnd } from '../../../utils/format'
import { extractError } from '../../../utils/apiError'
import { REPORT_TABS, exportReport } from './reportTabs'

const COLORS = ['#ee4d2d', '#2673dd', '#1e9e4a', '#f59e0b', '#6941c6', '#0891b2', '#db2777', '#64748b']
const STATUS_COLOR = { DELIVERED: '#1e9e4a', SHIPPING: '#2673dd', CONFIRMED: '#0891b2', PENDING: '#f59e0b', AWAITING_PAYMENT: '#a3a3a3',
  CANCELLED: '#d33', REFUNDED: '#6941c6', RETURN_REQUESTED: '#db2777' }
const METRICS = [{ key: 'gmv', label: 'GMV & đơn hàng' }, { key: 'users', label: 'Người dùng & shop mới' }, { key: 'buyers', label: 'Người mua' }]

const route = useRoute()
const { params } = useAnalyticsRange()
const data = ref(null)
const error = ref('')
const metric = ref('gmv')

const s = computed(() => data.value?.summary || {})
const p = computed(() => data.value?.previous || {})
const series = computed(() => data.value?.series || [])
const labels = computed(() => series.value.map(b => b.label))
const totalOrders = computed(() => (data.value?.statuses || []).reduce((a, x) => a + x.orders, 0) || 1)
const granLabel = computed(() => ({ day: 'Ngày', week: 'Tuần', month: 'Tháng' })[params.value.granularity])
const num = (v) => Number(v || 0).toLocaleString('vi-VN')
const fmtDay = (d) => new Date(d).toLocaleDateString('vi-VN')
const periodText = computed(() => data.value
  ? `${fmtDay(data.value.from)} – ${fmtDay(data.value.to)} · so với ${fmtDay(data.value.previousFrom)} – ${fmtDay(data.value.previousTo)}` : '')

const chartSeries = computed(() => {
  const v = (k) => series.value.map(b => Number(b[k]))
  if (metric.value === 'gmv') {
    return [
      { name: 'GMV', values: v('gmv'), color: '#ee4d2d', type: 'bar', format: formatVnd },
      { name: 'Đơn hàng', values: v('orders'), color: '#2673dd', type: 'line', axis: 'right', integer: true }
    ]
  }
  if (metric.value === 'users') {
    return [
      { name: 'Người dùng mới', values: v('newUsers'), color: '#2673dd', type: 'bar', integer: true },
      { name: 'Shop mới', values: v('newShops'), color: '#f59e0b', type: 'bar', integer: true }
    ]
  }
  return [{ name: 'Người mua đặt hàng', values: v('buyers'), color: '#1e9e4a', type: 'line', integer: true }]
})

async function load() {
  error.value = ''
  try {
    data.value = (await platformOverview(params.value)).data
  } catch (e) {
    error.value = extractError(e)
  }
}
watch(params, load, { immediate: true })
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }
.kpi-total { border: 1px dashed var(--color-border); border-radius: 6px; padding: 12px; font-size: 13px; color: var(--color-text-muted); display: flex; flex-direction: column; gap: 2px; justify-content: center; }
.kpi-total b { font-size: 16px; color: var(--color-text); }
.metric-pick { margin-left: auto; display: flex; gap: 6px; flex-wrap: wrap; }
.metric-pick button { background: white; border: 1px solid #ddd; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.metric-pick button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: start; }
.two-col .sc-card { margin-bottom: 0; }
.more { margin-left: auto; color: var(--color-primary); font-size: 13px; }
.bars { list-style: none; margin: 0; padding: 0; }
.bars li { padding: 7px 0; }
.bar-top { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 5px; gap: 8px; }
.bar-top i { width: 8px; height: 8px; border-radius: 50%; display: inline-block; margin-right: 6px; }
.bar { height: 7px; background: #f2f2f2; border-radius: 999px; overflow: hidden; }
.bar span { display: block; height: 100%; border-radius: 999px; }
@media (max-width: 1200px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } .two-col { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .kpis { grid-template-columns: 1fr 1fr; } }
</style>
