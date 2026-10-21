<template>
  <div class="report">
    <AnalyticsToolbar />
    <div v-if="error" class="sc-card sc-empty">{{ error }}</div>

    <template v-else>
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Lượt Xem & Tỷ Lệ Chuyển Đổi</h2>
          <span class="sc-card-sub">Mỗi khách được tính 1 lượt truy cập / sản phẩm trong 30 phút · không tính lượt xem của chính Shop</span>
        </div>
        <div class="kpis">
          <KpiCard label="Lượt truy cập" hint="Số khách khác nhau đã xem trang sản phẩm của Shop" :loading="!data"
                   :value="s.visitors" :current="s.visitors" :previous="p.visitors" :previous-text="p.visitors" />
          <KpiCard label="Lượt xem trang" hint="Tổng số lượt xem trang sản phẩm" :loading="!data"
                   :value="s.views" :current="s.views" :previous="p.views" :previous-text="p.views" />
          <KpiCard label="Xem / khách" hint="Trung bình mỗi khách xem bao nhiêu trang sản phẩm" :loading="!data"
                   :value="num(s.viewsPerVisitor)" :current="s.viewsPerVisitor" :previous="p.viewsPerVisitor" :previous-text="num(p.viewsPerVisitor)" />
          <KpiCard label="Tỷ lệ thêm vào giỏ" hint="Khách thêm sản phẩm vào giỏ / lượt truy cập" :loading="!data" points
                   :value="percent(s.cartRate)" :current="s.cartRate" :previous="p.cartRate" :previous-text="percent(p.cartRate)" />
          <KpiCard label="Người mua" hint="Số khách khác nhau đã đặt hàng (không tính đơn hủy/hoàn tiền)" :loading="!data"
                   :value="s.buyers" :current="s.buyers" :previous="p.buyers" :previous-text="p.buyers" />
          <KpiCard label="Tỷ lệ chuyển đổi" hint="Người mua / lượt truy cập" :loading="!data" points
                   :value="percent(s.conversion)" :current="s.conversion" :previous="p.conversion" :previous-text="percent(p.conversion)" />
        </div>
      </section>

      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Xu Hướng</h2>
          <span class="sc-card-sub">Theo {{ granLabel }}</span>
        </div>
        <TrendChart v-if="data" :labels="series.map(b => b.label)" :series="chartSeries" :height="280" :right-tick-format="(v) => v.toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + '%'" />
        <div v-else class="sc-empty">Đang tải...</div>
      </section>

      <div class="two-col">
        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">Phễu Chuyển Đổi</h2>
          </div>
          <div class="funnel">
            <div v-for="(f, i) in data?.funnel || []" :key="f.step" class="step">
              <div class="step-head">
                <span>{{ f.step }}</span>
                <strong>{{ f.value }}</strong>
              </div>
              <div class="step-bar"><span :style="{ width: funnelWidth(f.value) + '%', background: FUNNEL_COLORS[i] }"></span></div>
              <div v-if="i > 0" class="step-rate">
                ↳ {{ rate(f.value, data.funnel[i - 1].value) }} từ bước trước
              </div>
            </div>
          </div>
          <p class="note">Người mua có thể đến từ đường link trực tiếp hoặc giỏ hàng cũ nên đôi khi nhiều hơn số khách đã thêm vào giỏ trong kỳ.</p>
        </section>

        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">Sản Phẩm Cần Tối Ưu</h2>
            <span class="sc-card-sub">≥ {{ data?.attentionThreshold }} lượt truy cập nhưng chưa có người mua</span>
          </div>
          <div v-if="data && !data.attention.length" class="sc-empty ok">👍 Không có sản phẩm nào cần chú ý</div>
          <ul v-else class="attention">
            <li v-for="pr in data?.attention || []" :key="pr.id">
              <img :src="pr.thumbnailUrl || placeholder" alt="" />
              <div class="att-info">
                <router-link :to="{ name: 'seller-product-edit', params: { id: pr.id } }" class="name">{{ pr.name }}</router-link>
                <div class="text-faint small">{{ pr.visitors }} lượt truy cập · {{ pr.cartVisitors }} thêm vào giỏ · {{ formatVnd(pr.price) }}<template v-if="pr.stock <= 0"> · <span class="bad">Hết hàng</span></template></div>
              </div>
            </li>
          </ul>
          <div v-if="data?.attention.length" class="tips">
            Gợi ý: kiểm tra lại giá so với sản phẩm tương tự, thêm ảnh thật/video, viết mô tả rõ hơn
            (<router-link :to="{ name: 'seller-product-new', query: { ai: '1' } }">dùng AI</router-link>), hoặc tạo
            <router-link :to="{ name: 'seller-vouchers' }">voucher</router-link> / <router-link :to="{ name: 'seller-combos' }">combo</router-link> để thúc đẩy quyết định mua.
          </div>
        </section>
      </div>

      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Sản Phẩm Được Xem Nhiều Nhất</h2>
          <router-link :to="{ name: 'seller-analytics-products', query: route.query }" class="sc-link">Xem tất cả sản phẩm ›</router-link>
        </div>
        <div v-if="data && !data.topProducts.length" class="sc-empty">Chưa có lượt xem nào trong kỳ này</div>
        <div v-else class="sc-table-wrap">
          <table class="sc-table">
            <thead><tr><th>Sản phẩm</th><th class="r">Lượt truy cập</th><th class="r">Lượt xem</th><th class="r">Thêm vào giỏ</th><th class="r">Người mua</th><th class="r">Chuyển đổi</th></tr></thead>
            <tbody>
              <tr v-for="pr in data?.topProducts || []" :key="pr.id">
                <td>
                  <div class="prod">
                    <img :src="pr.thumbnailUrl || placeholder" alt="" />
                    <router-link :to="`/products/${pr.id}`" target="_blank" class="name">{{ pr.name }}</router-link>
                  </div>
                </td>
                <td class="r">{{ pr.visitors }}</td>
                <td class="r">{{ pr.views }}</td>
                <td class="r">{{ pr.cartVisitors }}</td>
                <td class="r">{{ pr.buyers }}</td>
                <td class="r"><span :class="{ good: pr.conversion >= 5, bad: pr.conversion === 0 }">{{ percent(pr.conversion) }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import AnalyticsToolbar from '../../../components/analytics/AnalyticsToolbar.vue'
import KpiCard from '../../../components/analytics/KpiCard.vue'
import TrendChart from '../../../components/analytics/TrendChart.vue'
import { trafficReport } from '../../../api/analytics'
import { useAnalyticsRange, percent } from '../../../composables/useAnalyticsRange'
import { formatVnd } from '../../../utils/format'
import { extractError } from '../../../utils/apiError'

const route = useRoute()
const { params } = useAnalyticsRange()
const data = ref(null)
const error = ref('')
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'
const FUNNEL_COLORS = ['#2673dd', '#f5a623', '#ee4d2d']

const s = computed(() => data.value?.summary || {})
const p = computed(() => data.value?.previous || {})
const series = computed(() => data.value?.series || [])
const granLabel = computed(() => ({ day: 'ngày', week: 'tuần', month: 'tháng' })[params.value.granularity])
const num = (v) => Number(v || 0).toLocaleString('vi-VN', { maximumFractionDigits: 2 })

const chartSeries = computed(() => {
  const v = (k) => series.value.map(b => Number(b[k]))
  return [
    { name: 'Lượt truy cập', values: v('visitors'), color: '#2673dd', type: 'bar', integer: true },
    { name: 'Lượt xem', values: v('views'), color: '#9cc3f5', type: 'line', integer: true, dashed: true },
    { name: 'Tỷ lệ chuyển đổi', values: v('conversion'), color: '#ee4d2d', type: 'line', axis: 'right', format: percent }
  ]
})

const funnelMax = computed(() => Math.max(1, ...(data.value?.funnel || []).map(f => f.value)))
const funnelWidth = (v) => Math.max(v ? 3 : 0, (v / funnelMax.value) * 100)
const rate = (v, prev) => (prev ? percent(Math.round((v / prev) * 10000) / 100) : '—')

async function load() {
  error.value = ''
  try {
    const { data: d } = await trafficReport(params.value)
    data.value = d
  } catch (e) {
    error.value = extractError(e)
  }
}
watch(params, load, { immediate: true })
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }
.two-col { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; align-items: stretch; margin-bottom: 16px; }
.two-col .sc-card { margin-bottom: 0; }
.funnel .step { margin-bottom: 14px; }
.step-head { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 6px; }
.step-bar { height: 22px; background: #f5f5f5; border-radius: 3px; overflow: hidden; }
.step-bar span { display: block; height: 100%; border-radius: 3px; transition: width 0.3s; }
.step-rate { font-size: 12px; color: var(--color-text-muted); margin-top: 4px; }
.note { font-size: 12px; color: var(--color-text-faint); margin: 4px 0 0; }
.attention { list-style: none; margin: 0; padding: 0; }
.attention li { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f5f5f5; }
.attention img, .prod img { width: 40px; height: 40px; object-fit: cover; border-radius: 2px; flex-shrink: 0; }
.att-info { min-width: 0; flex: 1; }
.name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.name:hover { color: var(--color-primary); }
.small { font-size: 12px; }
.tips { margin-top: 12px; font-size: 13px; background: #fff8e6; padding: 10px 12px; border-radius: 4px; line-height: 1.6; }
.tips a { color: #2673dd; }
.ok { padding: 32px 0; }
.prod { display: flex; align-items: center; gap: 10px; max-width: 420px; min-width: 0; }
.r { text-align: right; white-space: nowrap; }
.sc-table td { padding: 10px 12px; }
.good { color: #1e9e4a; font-weight: 500; }
.bad { color: #d33; }
@media (max-width: 1200px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 1000px) { .two-col { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .kpis { grid-template-columns: 1fr 1fr; } }
</style>
