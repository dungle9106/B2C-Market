<template>
  <div class="report">
    <AnalyticsToolbar :tabs="REPORT_TABS" :exporter="exportReport" file-prefix="bao-cao-toan-san" :granularity="false" />
    <div v-if="error" class="sc-card sc-empty">{{ error }}</div>

    <template v-else>
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">🏆 Top Shop Theo Doanh Số</h2>
          <span class="sc-card-sub">{{ periodText }} · GMV toàn sàn {{ data ? formatVnd(data.gmv) : '–' }}</span>
          <div class="pick">
            <button v-for="n in [10, 20, 50]" :key="n" type="button" :class="{ active: limit === n }" @click="limit = n">Top {{ n }}</button>
          </div>
        </div>
        <div v-if="!data" class="sc-empty">Đang tải...</div>
        <div v-else-if="!data.shops.length" class="sc-empty">Chưa có đơn hàng trong kỳ</div>
        <div v-else class="sc-table-wrap">
          <table class="sc-table">
            <thead><tr><th>#</th><th>Shop</th><th class="r">GMV</th><th>Tỷ trọng</th><th class="r">Đơn</th><th class="r">SP đã bán</th><th class="r">Người mua</th><th class="r">TB / đơn</th><th class="r">So với kỳ trước</th><th class="r">Đánh giá</th></tr></thead>
            <tbody>
              <tr v-for="s in data.shops" :key="s.shopId">
                <td><span class="rank" :class="'r' + s.rank">{{ s.rank }}</span></td>
                <td><router-link :to="`/shop/${s.shopId}`" target="_blank" class="name">{{ s.name }}</router-link></td>
                <td class="r"><b>{{ formatVnd(s.gmv) }}</b></td>
                <td class="share"><div class="bar"><span :style="{ width: Math.min(100, s.share) + '%' }"></span></div>{{ s.share }}%</td>
                <td class="r">{{ num(s.orders) }}</td>
                <td class="r">{{ num(s.units) }}</td>
                <td class="r">{{ num(s.buyers) }}</td>
                <td class="r">{{ formatVnd(s.avgOrderValue) }}</td>
                <td class="r"><Growth :value="s.growth" /></td>
                <td class="r">{{ s.rating ? '★ ' + s.rating : '—' }}<span v-if="s.ratingCount" class="faint"> ({{ s.ratingCount }})</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div class="two-col">
        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">🔥 Top Sản Phẩm Bán Chạy</h2>
            <div class="pick">
              <button type="button" :class="{ active: sort === 'units' }" @click="sort = 'units'">Theo số lượng</button>
              <button type="button" :class="{ active: sort === 'revenue' }" @click="sort = 'revenue'">Theo doanh thu</button>
            </div>
          </div>
          <div v-if="data && !data.products.length" class="sc-empty">Chưa có sản phẩm nào bán được trong kỳ</div>
          <div v-for="pr in data?.products || []" :key="pr.productId" class="product">
            <span class="rank" :class="'r' + pr.rank">{{ pr.rank }}</span>
            <img :src="pr.thumbnailUrl || ph" alt="" />
            <div class="pinfo">
              <router-link :to="`/products/${pr.productId}`" target="_blank" class="name">{{ pr.name }}</router-link>
              <div class="faint">{{ pr.shopName }}<template v-if="pr.category"> · {{ pr.category }}</template></div>
            </div>
            <div class="pnum">
              <b>{{ sort === 'units' ? num(pr.units) + ' đã bán' : formatVnd(pr.revenue) }}</b>
              <span class="faint">{{ sort === 'units' ? formatVnd(pr.revenue) : num(pr.units) + ' đã bán' }} · {{ pr.orders }} đơn</span>
            </div>
          </div>
        </section>

        <section class="sc-card">
          <div class="sc-card-head"><h2 class="sc-card-title">Ngành Hàng</h2></div>
          <div v-if="data && !data.categories.length" class="sc-empty">Chưa có dữ liệu</div>
          <table v-else-if="data" class="sc-table">
            <thead><tr><th>Ngành hàng</th><th class="r">Doanh thu</th><th class="r">Tỷ trọng</th><th class="r">Đã bán</th></tr></thead>
            <tbody>
              <tr v-for="c in data.categories" :key="c.name">
                <td>{{ c.name }}</td>
                <td class="r">{{ formatVnd(c.revenue) }}</td>
                <td class="r">{{ c.share }}%</td>
                <td class="r">{{ num(c.units) }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, h } from 'vue'
import AnalyticsToolbar from '../../../components/analytics/AnalyticsToolbar.vue'
import { useAnalyticsRange } from '../../../composables/useAnalyticsRange'
import { platformTop } from '../../../api/admin'
import { formatVnd } from '../../../utils/format'
import { extractError } from '../../../utils/apiError'
import { REPORT_TABS, exportReport } from './reportTabs'

// "▲ 12,5%" / "▼ 3%" / "Mới" against the previous period
const Growth = (props) => props.value == null
  ? h('span', { class: 'g up' }, 'Mới')
  : h('span', { class: 'g ' + (props.value > 0 ? 'up' : props.value < 0 ? 'down' : '') },
    (props.value > 0 ? '▲ ' : props.value < 0 ? '▼ ' : '') + Math.abs(props.value).toLocaleString('vi-VN') + '%')
Growth.props = ['value']

const ph = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44"><rect width="44" height="44" fill="%23f0f0f0"/></svg>'
const { params } = useAnalyticsRange()
const data = ref(null)
const error = ref('')
const limit = ref(10)
const sort = ref('units')

const num = (v) => Number(v || 0).toLocaleString('vi-VN')
const fmtDay = (d) => new Date(d).toLocaleDateString('vi-VN')
const periodText = computed(() => (data.value ? `${fmtDay(data.value.from)} – ${fmtDay(data.value.to)}` : ''))

async function load() {
  error.value = ''
  try {
    data.value = (await platformTop(params.value, limit.value, sort.value)).data
  } catch (e) {
    error.value = extractError(e)
  }
}
watch([() => params.value.from, () => params.value.to, limit, sort], load, { immediate: true })
</script>

<style scoped>
.pick { margin-left: auto; display: flex; gap: 6px; }
.pick button { background: white; border: 1px solid #ddd; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.pick button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.r { text-align: right; white-space: nowrap; }
.rank { display: inline-flex; width: 24px; height: 24px; border-radius: 50%; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; background: #f2f2f2; color: #666; flex-shrink: 0; }
.rank.r1 { background: #fbbf24; color: white; }
.rank.r2 { background: #a3a3a3; color: white; }
.rank.r3 { background: #c2763c; color: white; }
.name { color: var(--color-text); font-weight: 500; }
.name:hover { color: var(--color-primary); }
.share { min-width: 110px; font-size: 12px; color: var(--color-text-muted); }
.share .bar { height: 6px; background: #f2f2f2; border-radius: 3px; margin-bottom: 3px; overflow: hidden; }
.share .bar span { display: block; height: 100%; background: var(--color-primary); }
.faint { color: var(--color-text-faint); font-size: 12px; }
:deep(.g) { font-size: 12px; font-weight: 600; color: var(--color-text-muted); }
:deep(.g.up) { color: #1e9e4a; }
:deep(.g.down) { color: #d33; }
.two-col { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: 16px; align-items: start; }
.two-col .sc-card { margin-bottom: 0; }
.product { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-bottom: 1px solid var(--color-border); }
.product img { width: 44px; height: 44px; object-fit: cover; border: 1px solid var(--color-border); border-radius: 4px; }
.pinfo { flex: 1; min-width: 0; }
.pinfo .name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.pnum { display: flex; flex-direction: column; align-items: flex-end; font-size: 14px; white-space: nowrap; }
@media (max-width: 1100px) { .two-col { grid-template-columns: 1fr; } }
</style>
