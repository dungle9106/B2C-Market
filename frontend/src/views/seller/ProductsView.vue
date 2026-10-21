<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Sản Phẩm</h2>
      <div class="head-actions">
        <button type="button" class="btn ghost" :disabled="exporting" @click="onExport">{{ exporting ? 'Đang xuất...' : 'Xuất Excel' }}</button>
        <router-link :to="{ name: 'seller-product-import' }" class="btn secondary">Đăng hàng loạt</router-link>
        <router-link :to="{ name: 'seller-product-new' }" class="btn">+ Thêm 1 sản phẩm mới</router-link>
      </div>
    </div>

    <nav class="sc-tabs">
      <router-link
        v-for="t in tabs"
        :key="t.key"
        :to="{ name: 'seller-products', query: t.key === 'all' ? {} : { tab: t.key } }"
        :class="{ active: activeTab === t.key }"
      >
        {{ t.label }} <span class="count">({{ products.filter(t.match).length }})</span>
      </router-link>
    </nav>

    <div class="sc-toolbar">
      <input v-model.trim="keyword" placeholder="Tìm tên sản phẩm hoặc SKU" />
      <select v-model="categoryFilter" class="narrow">
        <option :value="null">Mọi danh mục shop</option>
        <option v-for="c in shopCategories" :key="c.id" :value="c.id">{{ c.name }} ({{ c.productCount }})</option>
      </select>
      <select v-model="sort" class="narrow">
        <option value="newest">Mới nhất</option>
        <option value="bestselling">Bán chạy</option>
        <option value="stockAsc">Kho: thấp → cao</option>
        <option value="priceAsc">Giá: thấp → cao</option>
        <option value="priceDesc">Giá: cao → thấp</option>
      </select>
      <span class="spacer"></span>
      <span class="text-muted">{{ filtered.length }} sản phẩm</span>
    </div>

    <!-- bulk action bar -->
    <div v-if="selected.size" class="bulk-bar">
      <span>Đã chọn <strong>{{ selected.size }}</strong> sản phẩm</span>
      <button type="button" class="btn sm ghost" :disabled="busy" @click="bulk('SHOW')">Hiện</button>
      <button type="button" class="btn sm ghost" :disabled="busy" @click="bulk('HIDE')">Ẩn</button>
      <button type="button" class="btn sm ghost" :disabled="busy" @click="bulk('SOLD_OUT')">Đánh dấu hết hàng</button>
      <select v-if="shopCategories.length" v-model="bulkCategory" class="bulk-select" :disabled="busy" @change="onBulkCategory">
        <option :value="null">Danh mục shop...</option>
        <optgroup label="Thêm vào">
          <option v-for="c in shopCategories" :key="'a' + c.id" :value="`ADD:${c.id}`">{{ c.name }}</option>
        </optgroup>
        <optgroup label="Gỡ khỏi">
          <option v-for="c in shopCategories" :key="'r' + c.id" :value="`REMOVE:${c.id}`">{{ c.name }}</option>
        </optgroup>
      </select>
      <button type="button" class="btn sm ghost danger" :disabled="busy" @click="bulk('DELETE')">Xóa</button>
      <button type="button" class="link" @click="selected.clear()">Bỏ chọn</button>
    </div>
    <p v-if="notice" class="notice" :class="notice.kind">{{ notice.text }}</p>

    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="filtered.length === 0" class="sc-empty">
      <svg width="56" height="56" viewBox="0 0 24 24" fill="none"><path d="M3 7l9-4 9 4-9 4zM3 7v10l9 4 9-4V7M12 11v10" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round" /></svg>
      Không tìm thấy sản phẩm
    </div>

    <div v-else class="sc-table-wrap">
      <table class="sc-table">
        <thead>
          <tr>
            <th class="check"><input type="checkbox" :checked="allSelected" :indeterminate="selected.size > 0 && !allSelected" aria-label="Chọn tất cả" @change="toggleAll" /></th>
            <th>Tên sản phẩm</th>
            <th>Giá</th>
            <th>Kho hàng</th>
            <th>Đã bán</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="p in filtered" :key="p.id">
            <tr :class="{ hidden: !p.active, picked: selected.has(p.id) }">
              <td class="check"><input type="checkbox" :checked="selected.has(p.id)" :aria-label="`Chọn ${p.name}`" @change="toggle(p.id)" /></td>
              <td>
                <div class="prod">
                  <img :src="p.thumbnailUrl || placeholder" alt="" />
                  <div class="prod-info">
                    <router-link :to="`/products/${p.id}`" class="prod-name" target="_blank">{{ p.name }}</router-link>
                    <div class="tags">
                      <span v-if="p.sku" class="sku">SKU: {{ p.sku }}</span>
                      <span v-if="p.categoryName" class="text-faint">{{ p.categoryName }}</span>
                      <span v-for="c in catNames(p)" :key="c" class="shop-cat">{{ c }}</span>
                      <span v-if="p.moderationStatus && p.moderationStatus !== 'APPROVED'" class="mod-tag" :class="p.moderationStatus">{{ MODERATION[p.moderationStatus] }}</span>
                      <span v-else-if="!p.active" class="status-tag CANCELLED">Đã ẩn</span>
                      <span v-else-if="p.stock <= 0" class="status-tag RETURN_REQUESTED">Hết hàng</span>
                      <span v-else-if="p.lowStock" class="status-tag PENDING">Sắp hết hàng</span>
                    </div>
                    <div v-if="p.moderationStatus && p.moderationStatus !== 'APPROVED'" class="mod-note" :class="p.moderationStatus">
                      <template v-if="p.moderationStatus === 'PENDING'">Đang chờ Shoppi duyệt<template v-if="p.moderationReason"> – {{ p.moderationReason }}</template></template>
                      <template v-else>{{ p.violationLabel ? p.violationLabel + ': ' : '' }}{{ p.moderationReason }}.
                        Hãy <router-link :to="{ name: 'seller-product-edit', params: { id: p.id } }">sửa sản phẩm</router-link> rồi
                        <button type="button" class="link" @click="onResubmit(p)">gửi duyệt lại</button></template>
                    </div>
                    <div v-else-if="p.moderationFlags?.some(f => f.severity !== 'LOW')" class="mod-note warn">
                      ⚠ {{ p.moderationFlags.filter(f => f.severity !== 'LOW').map(f => f.label).join('; ') }}
                    </div>
                    <button v-if="p.variants.length" type="button" class="link variants-toggle" @click="toggleExpand(p.id)">
                      {{ p.variants.length }} phân loại {{ expanded.has(p.id) ? '▴' : '▾' }}
                    </button>
                  </div>
                </div>
              </td>
              <td class="price">{{ priceText(p) }}</td>
              <td class="stock">
                <template v-if="!p.variants.length">
                  <input
                    type="number" min="0" class="stock-input" :value="p.stock" :aria-label="`Kho của ${p.name}`"
                    @keydown.enter="$event.target.blur()" @change="saveStock(p, $event.target.value)"
                  />
                </template>
                <template v-else>
                  <span>{{ p.stock }}</span>
                  <button type="button" class="link" @click="toggleExpand(p.id)">Sửa</button>
                </template>
              </td>
              <td>{{ p.soldCount || 0 }}</td>
              <td class="actions">
                <router-link :to="{ name: 'seller-product-edit', params: { id: p.id } }" class="action-link">Sửa</router-link>
                <a v-if="!p.moderationStatus || p.moderationStatus === 'APPROVED'" href="#" class="action-link" @click.prevent="onToggle(p)">{{ p.active ? 'Ẩn' : 'Hiện' }}</a>
                <a href="#" class="action-link" @click.prevent="onDuplicate(p)">Sao chép</a>
                <a v-if="p.stock > 0" href="#" class="action-link" @click.prevent="soldOut(p)">Hết hàng</a>
                <a href="#" class="action-link danger" @click.prevent="onDelete(p)">Xóa</a>
              </td>
            </tr>
            <!-- per-variant stock -->
            <tr v-if="expanded.has(p.id)" class="variant-row">
              <td></td>
              <td colspan="5">
                <div class="variant-grid">
                  <div v-for="v in p.variants" :key="v.id" class="variant-cell" :class="{ out: v.stock <= 0 }">
                    <img v-if="v.imageUrl" :src="v.imageUrl" alt="" />
                    <div class="v-name">{{ v.name }}<span v-if="v.sku" class="v-sku">{{ v.sku }}</span></div>
                    <input v-model.number="drafts[v.id]" type="number" min="0" :aria-label="`Kho ${v.name}`" />
                  </div>
                </div>
                <div class="variant-actions">
                  <button type="button" class="btn sm" :disabled="busy" @click="saveVariantStock(p)">Lưu kho phân loại</button>
                  <button type="button" class="btn sm ghost" @click="toggleExpand(p.id)">Đóng</button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  myProducts, toggleProductActive, deleteProduct, updateProductStock, duplicateProduct, bulkProducts,
  exportProducts, myShopCategories, resubmitProduct
} from '../../api/products'

const MODERATION = { PENDING: 'Chờ duyệt', REJECTED: 'Không được duyệt', REMOVED: 'Bị gỡ do vi phạm' }
import { formatVnd } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { saveBlob } from '../../utils/download'

const route = useRoute()
const router = useRouter()
const products = ref([])
const shopCategories = ref([])
const loading = ref(true)
const keyword = ref('')
const categoryFilter = ref(null)
const sort = ref('newest')
const selected = reactive(new Set())
const expanded = reactive(new Set())
const drafts = reactive({})       // variantId -> stock being edited
const busy = ref(false)
const exporting = ref(false)
const bulkCategory = ref(null)
const notice = ref(null)
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56"><rect width="56" height="56" fill="%23f0f0f0"/></svg>'

const tabs = [
  { key: 'all', label: 'Tất cả', match: () => true },
  { key: 'active', label: 'Đang hoạt động', match: (p) => p.active && p.stock > 0 },
  { key: 'lowstock', label: 'Sắp hết hàng', match: (p) => p.active && p.stock > 0 && p.lowStock },
  { key: 'soldout', label: 'Hết hàng', match: (p) => p.active && p.stock <= 0 },
  { key: 'hidden', label: 'Đã ẩn', match: (p) => !p.active && p.moderationStatus === 'APPROVED' },
  { key: 'moderation', label: 'Chờ duyệt / Vi phạm', match: (p) => p.moderationStatus && p.moderationStatus !== 'APPROVED' }
]
const activeTab = computed(() => (tabs.some(t => t.key === route.query.tab) ? route.query.tab : 'all'))

const SORTS = {
  newest: (a, b) => new Date(b.createdAt) - new Date(a.createdAt),
  bestselling: (a, b) => (b.soldCount || 0) - (a.soldCount || 0),
  stockAsc: (a, b) => a.stock - b.stock,
  priceAsc: (a, b) => a.price - b.price,
  priceDesc: (a, b) => b.price - a.price
}

const filtered = computed(() => {
  const tab = tabs.find(t => t.key === activeTab.value)
  const q = keyword.value.toLowerCase()
  return products.value
    .filter(tab.match)
    .filter(p => !q || p.name.toLowerCase().includes(q) || (p.sku || '').toLowerCase().includes(q)
      || p.variants.some(v => (v.sku || '').toLowerCase().includes(q)))
    .filter(p => !categoryFilter.value || (p.shopCategoryIds || []).includes(categoryFilter.value))
    .slice().sort(SORTS[sort.value])
})
const allSelected = computed(() => filtered.value.length > 0 && filtered.value.every(p => selected.has(p.id)))

const catNames = (p) => (p.shopCategoryIds || []).map(id => shopCategories.value.find(c => c.id === id)?.name).filter(Boolean)

function priceText(p) {
  if (!p.variants.length) return formatVnd(p.price)
  const prices = p.variants.map(v => Number(v.price))
  const min = Math.min(...prices)
  const max = Math.max(...prices)
  return min === max ? formatVnd(min) : `${formatVnd(min)} - ${formatVnd(max)}`
}

function flash(text, kind = 'ok') {
  notice.value = { text, kind }
  setTimeout(() => { if (notice.value?.text === text) notice.value = null }, 5000)
}

async function load() {
  loading.value = true
  try {
    const [{ data }, { data: cats }] = await Promise.all([myProducts(), myShopCategories()])
    products.value = data
    shopCategories.value = cats
    // drop selections that no longer exist
    for (const id of [...selected]) if (!data.some(p => p.id === id)) selected.delete(id)
  } finally {
    loading.value = false
  }
}

function replace(updated) {
  const i = products.value.findIndex(p => p.id === updated.id)
  if (i >= 0) products.value[i] = updated
}

// ---- selection ----
function toggle(id) {
  selected.has(id) ? selected.delete(id) : selected.add(id)
}
function toggleAll() {
  if (allSelected.value) filtered.value.forEach(p => selected.delete(p.id))
  else filtered.value.forEach(p => selected.add(p.id))
}

// ---- stock ----
async function saveStock(p, value) {
  const stock = Number(value)
  if (!Number.isInteger(stock) || stock < 0) return flash('Số lượng kho không hợp lệ', 'error')
  try {
    const { data } = await updateProductStock(p.id, { stock })
    replace(data)
    flash(`Đã cập nhật kho "${p.name}"`)
  } catch (e) {
    flash(extractError(e), 'error')
  }
}

function toggleExpand(id) {
  if (expanded.has(id)) return expanded.delete(id)
  const p = products.value.find(x => x.id === id)
  p.variants.forEach(v => { drafts[v.id] = v.stock })
  expanded.add(id)
}

async function saveVariantStock(p) {
  const variants = {}
  for (const v of p.variants) {
    const s = Number(drafts[v.id])
    if (!Number.isInteger(s) || s < 0) return flash(`Kho của "${v.name}" không hợp lệ`, 'error')
    variants[v.id] = s
  }
  busy.value = true
  try {
    const { data } = await updateProductStock(p.id, { variants })
    replace(data)
    expanded.delete(p.id)
    flash(`Đã cập nhật kho phân loại của "${p.name}"`)
  } catch (e) {
    flash(extractError(e), 'error')
  } finally {
    busy.value = false
  }
}

// ---- single actions ----
async function onToggle(p) {
  try {
    await toggleProductActive(p.id)
    p.active = !p.active
  } catch (e) {
    alert(extractError(e))
  }
}

async function onResubmit(p) {
  try {
    const { data } = await resubmitProduct(p.id)
    Object.assign(p, data)
  } catch (e) {
    alert(extractError(e))
  }
}

async function soldOut(p) {
  if (!confirm(`Đánh dấu "${p.name}" hết hàng? Kho${p.variants.length ? ' của mọi phân loại' : ''} sẽ về 0.`)) return
  await runBulk([p.id], 'SOLD_OUT')
}

async function onDuplicate(p) {
  try {
    const { data } = await duplicateProduct(p.id)
    router.push({ name: 'seller-product-edit', params: { id: data.id } })
  } catch (e) {
    flash(extractError(e), 'error')
  }
}

async function onDelete(p) {
  if (!confirm(`Xóa sản phẩm "${p.name}"?`)) return
  try {
    await deleteProduct(p.id)
    products.value = products.value.filter(x => x.id !== p.id)
    selected.delete(p.id)
  } catch (e) {
    flash(extractError(e), 'error') // e.g. products with orders can only be hidden
  }
}

// ---- bulk ----
const BULK_LABELS = { SHOW: 'hiện', HIDE: 'ẩn', SOLD_OUT: 'đánh dấu hết hàng', DELETE: 'xóa', ADD_CATEGORY: 'thêm vào danh mục', REMOVE_CATEGORY: 'gỡ khỏi danh mục' }

async function bulk(action, categoryId) {
  const n = selected.size
  if ((action === 'DELETE' || action === 'SOLD_OUT') && !confirm(`${BULK_LABELS[action][0].toUpperCase() + BULK_LABELS[action].slice(1)} ${n} sản phẩm đã chọn?`)) return
  await runBulk([...selected], action, categoryId)
}

async function runBulk(ids, action, categoryId) {
  busy.value = true
  try {
    const { data } = await bulkProducts(ids, action, categoryId)
    const skipped = data.skipped.length ? ` · Bỏ qua ${data.skipped.length}: ${data.skipped.map(s => `"${s.name}" (${s.reason})`).join(', ')}` : ''
    flash(`Đã ${BULK_LABELS[action]} ${data.updated} sản phẩm${skipped}`, data.skipped.length ? 'warn' : 'ok')
    await load() // also drops deleted products from the selection
  } catch (e) {
    flash(extractError(e), 'error')
  } finally {
    busy.value = false
  }
}

function onBulkCategory() {
  const v = bulkCategory.value
  bulkCategory.value = null
  if (!v) return
  const [kind, id] = v.split(':')
  bulk(kind === 'ADD' ? 'ADD_CATEGORY' : 'REMOVE_CATEGORY', Number(id))
}

async function onExport() {
  exporting.value = true
  try {
    saveBlob(await exportProducts(), 'san-pham.xlsx')
  } catch (e) {
    flash('Không xuất được file Excel', 'error')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-actions { margin-left: auto; display: flex; gap: 8px; flex-wrap: wrap; }
.narrow { width: auto; max-width: 220px; margin: 0; }
.bulk-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; background: #fff8f6; border: 1px solid #ffd9cf; border-radius: 4px; padding: 8px 12px; margin-bottom: 12px; font-size: 14px; }
.bulk-bar .danger { color: #d33; }
.bulk-select { width: auto; margin: 0; padding: 5px 8px; font-size: 13px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.link:hover { text-decoration: underline; }
.notice { font-size: 13px; padding: 8px 12px; border-radius: 4px; margin: 0 0 12px; }
.notice.ok { background: #eaf7ee; color: #1e7e43; }
.notice.warn { background: #fff8eb; color: #a16207; }
.notice.error { background: #fdecea; color: #d33; }

.check { width: 36px; }
.check input { width: 16px; height: 16px; margin: 0; accent-color: var(--color-primary); }
.prod { display: flex; align-items: center; gap: 12px; min-width: 260px; }
.prod img { width: 56px; height: 56px; object-fit: cover; border-radius: 2px; border: 1px solid var(--color-border); flex-shrink: 0; }
.prod-info { min-width: 0; }
.prod-name { display: block; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 360px; }
.prod-name:hover { color: var(--color-primary); }
.tags { display: flex; gap: 8px; align-items: center; margin-top: 4px; font-size: 12px; flex-wrap: wrap; }
.sku { color: var(--color-text-muted); font-family: ui-monospace, Consolas, monospace; }
.shop-cat { background: #f0f5ff; color: #2673dd; padding: 1px 6px; border-radius: 2px; }
.variants-toggle { margin-top: 4px; font-size: 12px; }
.price { white-space: nowrap; }
.stock { white-space: nowrap; }
.stock .link { margin-left: 8px; }
.stock-input { width: 80px; margin: 0; padding: 5px 8px; }
tr.hidden td { opacity: 0.6; }
tr.hidden td.actions, tr.hidden td.check { opacity: 1; }
tr.picked td { background: #fffaf8; }
.actions { white-space: nowrap; }
.action-link { color: #2673dd; margin-right: 12px; }
.action-link:hover { text-decoration: underline; }
.action-link.danger { color: #d33; }

.variant-row td { background: #fafafa; }
.variant-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; }
.variant-cell { display: flex; align-items: center; gap: 8px; background: white; border: 1px solid var(--color-border); border-radius: 4px; padding: 6px 8px; }
.variant-cell.out { border-color: #f5c2c2; }
.variant-cell img { width: 28px; height: 28px; object-fit: cover; border-radius: 3px; }
.v-name { flex: 1; min-width: 0; font-size: 13px; }
.v-sku { display: block; font-size: 11px; color: var(--color-text-faint); }
.variant-cell input { width: 72px; margin: 0; padding: 4px 6px; }
.variant-actions { display: flex; gap: 8px; margin-top: 10px; }
.mod-tag { font-size: 11px; padding: 1px 6px; border-radius: 3px; }
.mod-tag.PENDING { background: #fff4e5; color: #b35c00; }
.mod-tag.REJECTED, .mod-tag.REMOVED { background: #fdecea; color: #d33; }
.mod-note { font-size: 12px; margin-top: 4px; padding: 4px 8px; border-radius: 3px; background: #fff8e6; color: #8a5a00; line-height: 1.5; }
.mod-note.REJECTED, .mod-note.REMOVED { background: #fdecea; color: #b42318; }
.mod-note.warn { background: #fff8e6; color: #8a5a00; }
.mod-note a, .mod-note .link { color: #2673dd; text-decoration: underline; font-size: 12px; padding: 0; }
</style>
