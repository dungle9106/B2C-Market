<template>
  <div class="browser">
    <!-- ===== filters ===== -->
    <aside class="filters" :class="{ open: filtersOpen }">
      <div class="filters-title">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M3 5h18l-7 8v6l-4 2v-8z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" /></svg>
        BỘ LỌC TÌM KIẾM
      </div>

      <section v-if="categories.length" class="group">
        <h4>Theo Danh Mục</h4>
        <router-link
          v-for="c in categories"
          :key="c.id"
          :to="{ name: 'category', params: { id: c.id } }"
          class="cat-link"
          :class="{ active: c.id === activeCategoryId }"
        >{{ c.name }}</router-link>
      </section>

      <section class="group">
        <h4>Nơi Bán</h4>
        <label v-for="p in visibleProvinces" :key="p" class="check">
          <input type="checkbox" :checked="filters.provinces.includes(p)" @change="toggleProvince(p)" />
          {{ p }}
        </label>
        <button class="more" type="button" @click="showAllProvinces = !showAllProvinces">
          {{ showAllProvinces ? 'Thu gọn' : 'Thêm' }}
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" :style="{ transform: showAllProvinces ? 'rotate(180deg)' : '' }"><path d="m6 9 6 6 6-6" stroke="currentColor" stroke-width="2.5" /></svg>
        </button>
      </section>

      <section class="group">
        <h4>Khoảng Giá</h4>
        <div class="price-range">
          <input v-model.number="priceDraft.min" type="number" min="0" placeholder="₫ TỪ" />
          <span>–</span>
          <input v-model.number="priceDraft.max" type="number" min="0" placeholder="₫ ĐẾN" />
        </div>
        <p v-if="priceError" class="price-error">{{ priceError }}</p>
        <button class="btn block apply" type="button" @click="applyPrice">ÁP DỤNG</button>
      </section>

      <section class="group">
        <h4>Đánh Giá</h4>
        <button
          v-for="s in [5, 4, 3, 2, 1]"
          :key="s"
          type="button"
          class="rating-row"
          :class="{ active: filters.minRating === s }"
          @click="update({ minRating: filters.minRating === s ? null : s })"
        >
          <StarRating :value="s" :size="13" />
          <span v-if="s < 5">trở lên</span>
        </button>
      </section>

      <section class="group">
        <h4>Dịch Vụ & Khuyến Mãi</h4>
        <label class="check">
          <input type="checkbox" :checked="filters.freeShipping" @change="update({ freeShipping: !filters.freeShipping || null })" />
          Freeship
        </label>
        <label class="check">
          <input type="checkbox" :checked="filters.onSale" @change="update({ onSale: !filters.onSale || null })" />
          Đang giảm giá
        </label>
      </section>

      <button class="btn block clear" type="button" :disabled="!hasFilters" @click="clearAll">XÓA TẤT CẢ</button>
    </aside>

    <!-- ===== results ===== -->
    <div class="results">
      <slot name="header" :total="result.totalItems" />

      <div class="sort-bar">
        <button class="filter-toggle btn ghost" type="button" @click="filtersOpen = !filtersOpen">
          Bộ lọc <span v-if="activeFilterCount">({{ activeFilterCount }})</span>
        </button>
        <span class="sort-label">Sắp xếp theo</span>
        <button
          v-for="s in sortOptions"
          :key="s.value"
          type="button"
          class="sort-btn"
          :class="{ active: filters.sort === s.value }"
          @click="update({ sort: s.value })"
        >{{ s.label }}</button>
        <select class="price-sort" :class="{ active: filters.sort.startsWith('price') }" :value="filters.sort.startsWith('price') ? filters.sort : ''" @change="update({ sort: $event.target.value })">
          <option value="" disabled>Giá</option>
          <option value="price_asc">Giá: Thấp đến Cao</option>
          <option value="price_desc">Giá: Cao đến Thấp</option>
        </select>
        <span class="mini-pager">
          <span><span class="text-primary">{{ result.totalPages ? filters.page + 1 : 0 }}</span>/{{ result.totalPages }}</span>
          <button type="button" :disabled="filters.page === 0" aria-label="Trang trước" @click="goPage(filters.page - 1)">‹</button>
          <button type="button" :disabled="filters.page >= result.totalPages - 1" aria-label="Trang sau" @click="goPage(filters.page + 1)">›</button>
        </span>
      </div>

      <div v-if="loading && !result.items.length" class="grid-products">
        <div v-for="i in 10" :key="i" class="skeleton"></div>
      </div>
      <div v-else-if="!result.items.length" class="empty">
        <svg width="72" height="72" viewBox="0 0 24 24" fill="none"><path d="M21 21l-5.5-5.5M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" /></svg>
        <p>Không tìm thấy sản phẩm phù hợp</p>
        <p class="text-faint">Hãy thử từ khóa khác hoặc bỏ bớt bộ lọc</p>
        <button v-if="hasFilters" class="btn secondary" type="button" @click="clearAll">Xóa bộ lọc</button>
      </div>
      <div v-else class="grid-products" :class="{ dim: loading }">
        <ProductCard v-for="p in result.items" :key="p.id" :product="p" />
      </div>

      <PaginationBar :page="filters.page" :total-pages="result.totalPages" @change="goPage" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductCard from './ProductCard.vue'
import StarRating from './StarRating.vue'
import PaginationBar from './PaginationBar.vue'
import { searchProducts } from '../api/products'
import { PROVINCES } from '../utils/provinces'

// base: fixed part of the query decided by the page ({ q } on search, { categoryId } on a category)
const props = defineProps({
  base: { type: Object, default: () => ({}) },
  categories: { type: Array, default: () => [] },  // sub-categories shown under "Theo danh muc"
  activeCategoryId: { type: Number, default: null },
  defaultSort: { type: String, default: 'relevance' }
})

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 30
const sortOptions = [
  { value: 'relevance', label: 'Liên quan' },
  { value: 'newest', label: 'Mới nhất' },
  { value: 'bestselling', label: 'Bán chạy' },
  { value: 'rating', label: 'Đánh giá cao' }
]

// every filter lives in the URL, so results are shareable and Back works
const toArray = (v) => (v == null ? [] : Array.isArray(v) ? v : [v])
const toNum = (v) => (v == null || v === '' || isNaN(Number(v)) ? null : Number(v))
const filters = computed(() => {
  const q = route.query
  return {
    minPrice: toNum(q.minPrice),
    maxPrice: toNum(q.maxPrice),
    minRating: toNum(q.minRating),
    provinces: toArray(q.provinces),
    freeShipping: q.freeShipping === 'true',
    onSale: q.onSale === 'true',
    sort: q.sort || props.defaultSort,
    page: Math.max(0, toNum(q.page) || 0)
  }
})

const hasFilters = computed(() => {
  const f = filters.value
  return f.minPrice != null || f.maxPrice != null || f.minRating != null || f.provinces.length > 0 || f.freeShipping || f.onSale
})
const activeFilterCount = computed(() => {
  const f = filters.value
  return (f.minPrice != null || f.maxPrice != null ? 1 : 0) + (f.minRating != null ? 1 : 0) +
    f.provinces.length + (f.freeShipping ? 1 : 0) + (f.onSale ? 1 : 0)
})

// change some filters (null removes a param) and go back to page 1
function update(changes, keepPage = false) {
  const query = { ...route.query, ...changes }
  if (!keepPage) delete query.page
  Object.keys(query).forEach(k => {
    if (query[k] == null || query[k] === '' || (Array.isArray(query[k]) && !query[k].length)) delete query[k]
  })
  router.push({ query })
}

function goPage(p) {
  update({ page: p > 0 ? p : null }, true)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function toggleProvince(p) {
  const list = filters.value.provinces
  update({ provinces: list.includes(p) ? list.filter(x => x !== p) : [...list, p] })
}

const showAllProvinces = ref(false)
const visibleProvinces = computed(() => {
  if (showAllProvinces.value) return PROVINCES
  // keep checked provinces visible even when collapsed
  const first = PROVINCES.slice(0, 6)
  return [...first, ...filters.value.provinces.filter(p => !first.includes(p))]
})

const priceDraft = reactive({ min: null, max: null })
const priceError = ref('')
watch(filters, (f) => { priceDraft.min = f.minPrice; priceDraft.max = f.maxPrice }, { immediate: true })
function applyPrice() {
  const min = toNum(priceDraft.min)
  const max = toNum(priceDraft.max)
  priceError.value = min != null && max != null && min > max ? 'Vui lòng điền khoảng giá phù hợp' : ''
  if (!priceError.value) update({ minPrice: min, maxPrice: max })
}

function clearAll() {
  const query = { ...route.query }
  ;['minPrice', 'maxPrice', 'minRating', 'provinces', 'freeShipping', 'onSale', 'page'].forEach(k => delete query[k])
  router.push({ query })
}

const filtersOpen = ref(false)
const loading = ref(false)
const result = ref({ items: [], totalItems: 0, totalPages: 0 })

let requestId = 0
async function load() {
  const f = filters.value
  const id = ++requestId
  loading.value = true
  try {
    const { data } = await searchProducts({
      ...props.base,
      minPrice: f.minPrice ?? undefined,
      maxPrice: f.maxPrice ?? undefined,
      minRating: f.minRating ?? undefined,
      provinces: f.provinces.length ? f.provinces : undefined,
      freeShipping: f.freeShipping || undefined,
      onSale: f.onSale || undefined,
      sort: f.sort,
      page: f.page,
      size: PAGE_SIZE
    })
    if (id === requestId) result.value = data // ignore stale responses
  } finally {
    if (id === requestId) loading.value = false
  }
}

watch([filters, () => props.base], load, { immediate: true, deep: true })
</script>

<style scoped>
.browser { display: flex; gap: 20px; align-items: flex-start; }

.filters { width: 200px; flex-shrink: 0; font-size: 14px; }
.filters-title { display: flex; align-items: center; gap: 8px; font-weight: 700; padding: 12px 0; border-bottom: 1px solid #e5e5e5; }
.group { padding: 16px 0; border-bottom: 1px solid #e5e5e5; }
.group h4 { margin: 0 0 10px; font-size: 14px; font-weight: 500; }
.check { display: flex; align-items: center; gap: 8px; padding: 5px 0; cursor: pointer; }
.check input { width: auto; margin: 0; accent-color: var(--color-primary); }
.more { background: none; border: none; color: var(--color-text-muted); cursor: pointer; padding: 6px 0 0; display: flex; align-items: center; gap: 4px; font-size: 13px; }
.cat-link { display: block; padding: 5px 0 5px 10px; color: var(--color-text); }
.cat-link.active, .cat-link:hover { color: var(--color-primary); }
.cat-link.active { font-weight: 600; }

.price-range { display: flex; align-items: center; gap: 6px; }
.price-range input { margin: 0; padding: 6px; font-size: 12px; min-width: 0; }
.price-error { color: #d33; font-size: 12px; margin: 6px 0 0; }
.apply { margin-top: 10px; padding: 7px; font-size: 13px; }
.rating-row {
  display: flex; align-items: center; gap: 6px; width: 100%;
  background: none; border: none; padding: 5px 8px; border-radius: 10px;
  cursor: pointer; font-size: 13px; color: var(--color-text);
}
.rating-row.active { background: #ebebeb; }
.clear { margin-top: 16px; padding: 8px; font-size: 13px; }

.results { flex: 1; min-width: 0; }
.sort-bar {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  background: #ededed; padding: 12px 16px; border-radius: 2px; margin-bottom: 12px;
  font-size: 14px;
}
.sort-label { color: var(--color-text-muted); margin-right: 4px; }
.sort-btn { background: white; border: none; padding: 8px 14px; border-radius: 2px; cursor: pointer; font-size: 14px; }
.sort-btn.active { background: var(--color-primary); color: white; }
.price-sort { width: 190px; margin: 0; padding: 8px 10px; border: none; border-radius: 2px; }
.price-sort.active { color: var(--color-primary); }
.mini-pager { margin-left: auto; display: flex; align-items: center; gap: 4px; }
.mini-pager span { margin-right: 8px; }
.mini-pager button { width: 34px; height: 34px; border: none; background: white; cursor: pointer; font-size: 16px; }
.mini-pager button:disabled { background: #f9f9f9; color: #ccc; cursor: default; }
.filter-toggle { display: none; background: white; }

.dim { opacity: 0.6; transition: opacity 0.15s; }
.skeleton { aspect-ratio: 0.62; background: linear-gradient(90deg, #f2f2f2 25%, #e9e9e9 50%, #f2f2f2 75%); background-size: 200% 100%; animation: shimmer 1.2s infinite; border-radius: 2px; }
@keyframes shimmer { to { background-position: -200% 0; } }
.empty { text-align: center; padding: 60px 0; color: var(--color-text-muted); }
.empty svg { color: #ddd; }

@media (max-width: 900px) {
  .browser { flex-direction: column; }
  .filters { display: none; width: 100%; background: white; padding: 0 16px; border-radius: 4px; }
  .filters.open { display: block; }
  .filter-toggle { display: inline-flex; }
  .sort-label, .mini-pager { display: none; }
  .price-sort { width: auto; }
}
</style>
