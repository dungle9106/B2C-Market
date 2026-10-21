<template>
  <div class="report">
    <AnalyticsToolbar :granularity="false" />
    <div v-if="error" class="sc-card sc-empty">{{ error }}</div>

    <template v-else>
      <div class="two-col">
        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">Sản Phẩm Bán Chạy</h2>
            <span class="sc-card-sub">Top 10 theo doanh số trong kỳ</span>
          </div>
          <div v-if="data && !best.length" class="sc-empty">Chưa có sản phẩm nào được bán trong kỳ này</div>
          <ol v-else class="best">
            <li v-for="(pr, i) in best" :key="pr.id">
              <span class="rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <img :src="pr.thumbnailUrl || placeholder" alt="" />
              <div class="best-info">
                <router-link :to="`/products/${pr.id}`" target="_blank" class="name">{{ pr.name }}</router-link>
                <div class="best-bar"><span :style="{ width: (Number(pr.sales) / maxSales) * 100 + '%' }"></span></div>
              </div>
              <div class="best-num">
                <strong>{{ formatVnd(pr.sales) }}</strong>
                <span class="text-faint">{{ pr.units }} đã bán</span>
              </div>
            </li>
          </ol>
        </section>

        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">Tồn Kho</h2>
            <router-link :to="{ name: 'seller-inventory' }" class="sc-link">Quản lý tồn kho ›</router-link>
          </div>
          <div class="stock-tiles">
            <div class="tile"><span class="tile-num">{{ inv.totalStock ?? '–' }}</span><span>Tổng tồn kho (sản phẩm đang bán)</span></div>
            <div class="tile"><span class="tile-num">{{ data ? formatVnd(inv.stockValue) : '–' }}</span><span>Giá trị tồn kho (theo giá bán)</span></div>
            <button type="button" class="tile warn" :class="{ active: filter === 'out' }" @click="setFilter('out')">
              <span class="tile-num">{{ inv.outOfStock ?? '–' }}</span><span>Hết hàng</span>
            </button>
            <button type="button" class="tile warn" :class="{ active: filter === 'low' }" @click="setFilter('low')">
              <span class="tile-num">{{ inv.lowStock ?? '–' }}</span><span>Sắp hết (≤ {{ data?.lowStockThreshold }})</span>
            </button>
            <button type="button" class="tile" :class="{ active: filter === 'slow' }" @click="setFilter('slow')">
              <span class="tile-num">{{ inv.slowMovers ?? '–' }}</span><span>Còn hàng nhưng chưa bán được trong kỳ</span>
            </button>
            <button type="button" class="tile" :class="{ active: filter === 'soon' }" @click="setFilter('soon')">
              <span class="tile-num">{{ data ? soonOut : '–' }}</span><span>Sẽ hết trong 7 ngày (theo tốc độ bán)</span>
            </button>
          </div>
        </section>
      </div>

      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Hiệu Quả Từng Sản Phẩm</h2>
          <span class="sc-card-sub">{{ s.productsSold ?? 0 }}/{{ s.products ?? 0 }} sản phẩm có đơn · {{ s.units ?? 0 }} sản phẩm đã bán · {{ formatVnd(s.sales) }}</span>
        </div>
        <div class="sc-toolbar">
          <div class="chips">
            <button v-for="f in FILTERS" :key="f.key" type="button" :class="{ active: filter === f.key }" @click="setFilter(f.key)">
              {{ f.label }} <span class="count">{{ countOf(f.key) }}</span>
            </button>
          </div>
          <span class="spacer"></span>
          <input v-model.trim="q" placeholder="Tìm tên sản phẩm" />
        </div>
        <div class="sc-table-wrap">
          <table class="sc-table">
            <thead>
              <tr>
                <th>Sản phẩm</th>
                <th v-for="c in COLS" :key="c.key" class="r sortable" :class="{ sorted: sort.key === c.key }" :title="c.hint" @click="toggleSort(c.key)">
                  {{ c.label }}<span class="arrow">{{ sort.key === c.key ? (sort.desc ? '▼' : '▲') : '' }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="pr in visible" :key="pr.id" :class="{ hidden: !pr.active }">
                <td>
                  <div class="prod">
                    <img :src="pr.thumbnailUrl || placeholder" alt="" />
                    <div class="prod-info">
                      <router-link :to="{ name: 'seller-product-edit', params: { id: pr.id } }" class="name">{{ pr.name }}</router-link>
                      <div class="text-faint small">{{ formatVnd(pr.price) }}<template v-if="!pr.active"> · Đã ẩn</template></div>
                    </div>
                  </div>
                </td>
                <td class="r">{{ pr.visitors }}<div class="text-faint small">{{ pr.views }} lượt xem</div></td>
                <td class="r">{{ pr.cartVisitors }}</td>
                <td class="r"><span :class="convClass(pr)">{{ percent(pr.conversion) }}</span><div class="text-faint small">{{ pr.buyers }} người mua</div></td>
                <td class="r">{{ pr.units }}</td>
                <td class="r">{{ formatVnd(pr.sales) }}</td>
                <td class="r">
                  <span :class="{ out: pr.stock <= 0, low: pr.stock > 0 && pr.stock <= data.lowStockThreshold }">{{ pr.stock <= 0 ? 'Hết hàng' : pr.stock }}</span>
                  <div v-if="pr.stock > 0 && pr.daysOfStock !== null" class="small" :class="pr.daysOfStock < 7 ? 'out' : 'text-faint'" title="Số ngày tồn kho còn đủ bán với tốc độ bán trong kỳ">
                    đủ bán {{ pr.daysOfStock.toLocaleString('vi-VN') }} ngày
                  </div>
                </td>
                <td class="r">
                  <template v-if="pr.ratingCount">★ {{ Number(pr.ratingAvg).toFixed(1) }}<div class="text-faint small">{{ pr.ratingCount }} đánh giá</div></template>
                  <span v-else class="text-faint">—</span>
                </td>
              </tr>
              <tr v-if="data && !filtered.length"><td :colspan="COLS.length + 1" class="sc-empty">Không có sản phẩm phù hợp</td></tr>
            </tbody>
          </table>
        </div>
        <div v-if="filtered.length > limit" class="more">
          <button type="button" class="btn ghost sm" @click="limit += 20">Xem thêm {{ Math.min(20, filtered.length - limit) }} sản phẩm</button>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AnalyticsToolbar from '../../../components/analytics/AnalyticsToolbar.vue'
import { productReport } from '../../../api/analytics'
import { useAnalyticsRange, percent } from '../../../composables/useAnalyticsRange'
import { formatVnd } from '../../../utils/format'
import { extractError } from '../../../utils/apiError'

const route = useRoute()
const router = useRouter()
const { params } = useAnalyticsRange()
const data = ref(null)
const error = ref('')
const q = ref('')
const limit = ref(20)
const sort = ref({ key: 'sales', desc: true })
const filter = ref(route.query.filter || 'all')
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const COLS = [
  { key: 'visitors', label: 'Lượt truy cập', hint: 'Số khách khác nhau đã xem trang sản phẩm' },
  { key: 'cartVisitors', label: 'Thêm vào giỏ', hint: 'Số khách đã thêm sản phẩm vào giỏ hàng' },
  { key: 'conversion', label: 'Chuyển đổi', hint: 'Người mua / lượt truy cập' },
  { key: 'units', label: 'Đã bán' },
  { key: 'sales', label: 'Doanh số' },
  { key: 'stock', label: 'Tồn kho', hint: 'Kèm số ngày còn đủ bán với tốc độ bán trong kỳ' },
  { key: 'ratingAvg', label: 'Đánh giá' }
]
const FILTERS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'sold', label: 'Có đơn' },
  { key: 'out', label: 'Hết hàng' },
  { key: 'low', label: 'Sắp hết' },
  { key: 'soon', label: 'Hết trong 7 ngày' },
  { key: 'slow', label: 'Bán chậm' }
]

const s = computed(() => data.value?.summary || {})
const inv = computed(() => data.value?.inventory || {})
const products = computed(() => data.value?.products || [])
const best = computed(() => products.value.filter(p => p.units > 0).slice(0, 10))
const maxSales = computed(() => Math.max(1, ...best.value.map(p => Number(p.sales))))

const low = () => data.value?.lowStockThreshold ?? 5
const MATCH = {
  all: () => true,
  sold: (p) => p.units > 0,
  out: (p) => p.active && p.stock <= 0,
  low: (p) => p.active && p.stock > 0 && p.stock <= low(),
  soon: (p) => p.active && p.stock > 0 && p.daysOfStock !== null && p.daysOfStock < 7,
  slow: (p) => p.active && p.stock > 0 && p.units === 0
}
const countOf = (key) => products.value.filter(MATCH[key]).length
const soonOut = computed(() => countOf('soon'))

const filtered = computed(() => {
  const needle = q.value.toLowerCase()
  const list = products.value.filter(MATCH[filter.value] || MATCH.all).filter(p => !needle || p.name.toLowerCase().includes(needle))
  const { key, desc } = sort.value
  // empty values ("—") always go last
  const val = (p) => (p[key] === null || p[key] === undefined ? null : Number(p[key]))
  return [...list].sort((a, b) => {
    const x = val(a), y = val(b)
    if (x === null && y === null) return 0
    if (x === null) return 1
    if (y === null) return -1
    return desc ? y - x : x - y
  })
})
const visible = computed(() => filtered.value.slice(0, limit.value))

function toggleSort(key) {
  sort.value = sort.value.key === key ? { key, desc: !sort.value.desc } : { key, desc: !['stock', 'ratingAvg'].includes(key) }
}

function setFilter(key) {
  filter.value = filter.value === key && key !== 'all' ? 'all' : key
  limit.value = 20
  if (key === 'soon') sort.value = { key: 'daysOfStock', desc: false } // soonest to run out first
  router.replace({ query: { ...route.query, filter: filter.value === 'all' ? undefined : filter.value } })
}

// green when it converts well, grey when nobody visited
function convClass(p) {
  if (!p.visitors) return 'text-faint'
  return p.conversion >= 5 ? 'good' : p.conversion === 0 ? 'bad' : ''
}

async function load() {
  error.value = ''
  try {
    const { data: d } = await productReport(params.value)
    data.value = d
  } catch (e) {
    error.value = extractError(e)
  }
}
watch(() => [params.value.from, params.value.to], load, { immediate: true })
</script>

<style scoped>
.two-col { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; align-items: stretch; margin-bottom: 16px; }
.two-col .sc-card { margin-bottom: 0; }
.best { list-style: none; margin: 0; padding: 0; }
.best li { display: flex; align-items: center; gap: 10px; padding: 7px 0; }
.rank { width: 22px; height: 22px; border-radius: 3px; background: #eee; font-size: 12px; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.rank.top { background: var(--color-primary); color: white; }
.best img { width: 36px; height: 36px; object-fit: cover; border-radius: 2px; flex-shrink: 0; }
.best-info { flex: 1; min-width: 0; }
.name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.name:hover { color: var(--color-primary); }
.best-bar { height: 6px; background: #f2f2f2; border-radius: 999px; margin-top: 5px; overflow: hidden; }
.best-bar span { display: block; height: 100%; background: linear-gradient(90deg, #ff7337, #ee4d2d); border-radius: 999px; }
.best-num { display: flex; flex-direction: column; align-items: flex-end; font-size: 13px; white-space: nowrap; }
.stock-tiles { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.tile { display: flex; flex-direction: column; gap: 4px; text-align: left; background: #fafafa; border: 1px solid var(--color-border); border-radius: 4px; padding: 12px 14px; font-size: 13px; color: var(--color-text-muted); font-family: inherit; }
button.tile { cursor: pointer; }
button.tile:hover, button.tile.active { border-color: var(--color-primary); background: var(--color-primary-light); }
.tile-num { font-size: 20px; color: var(--color-text); font-weight: 500; }
.tile.warn .tile-num { color: #d33; }
.chips { display: flex; gap: 6px; flex-wrap: wrap; }
.chips button { background: white; border: 1px solid #ddd; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chips button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.count { color: var(--color-text-faint); margin-left: 2px; }
.r { text-align: right; white-space: nowrap; }
.sortable { cursor: pointer; user-select: none; }
.sortable:hover, .sorted { color: var(--color-primary); }
.arrow { font-size: 9px; margin-left: 3px; }
.sc-table td { padding: 10px 12px; }
.prod { display: flex; align-items: center; gap: 10px; min-width: 200px; max-width: 300px; }
.prod img { width: 40px; height: 40px; object-fit: cover; border-radius: 2px; flex-shrink: 0; }
.prod-info { min-width: 0; }
.small { font-size: 12px; }
tr.hidden td { opacity: 0.6; }
.out { color: #d33; font-weight: 500; }
.low { color: #e67e22; font-weight: 500; }
.good { color: #1e9e4a; font-weight: 500; }
.bad { color: #d33; }
.more { text-align: center; margin-top: 12px; }
@media (max-width: 1100px) { .two-col { grid-template-columns: 1fr; } }
</style>
