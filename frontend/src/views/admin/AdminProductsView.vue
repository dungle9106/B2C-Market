<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Kiểm Duyệt Sản Phẩm</h2>
      <span class="sc-card-sub">Duyệt sản phẩm mới, xử lý báo cáo của người dùng, gỡ sản phẩm vi phạm chính sách</span>
    </div>

    <div class="setting" :class="{ on: counts.requireApproval }">
      <div>
        <strong>Duyệt sản phẩm mới trước khi hiển thị</strong>
        <p>{{ counts.requireApproval
          ? 'Đang BẬT: mọi sản phẩm mới đều chờ duyệt.'
          : 'Đang TẮT: sản phẩm mới hiển thị ngay. Hệ thống vẫn tự kiểm tra nội dung và giữ lại sản phẩm chứa từ khóa cấm để duyệt.' }}
          <router-link v-if="can('SETTINGS')" :to="{ name: 'admin-settings' }">Quản lý từ khóa cấm ›</router-link></p>
      </div>
      <button v-if="can('SETTINGS')" type="button" class="switch" :class="{ on: counts.requireApproval }" @click="toggleSetting"><span></span></button>
    </div>

    <nav class="sc-tabs">
      <router-link v-for="t in TABS" :key="t.key" :to="{ name: 'admin-products', query: { status: t.key } }" :class="{ active: status === t.key }">
        {{ t.label }} <span v-if="t.key !== 'ALL'" class="count">({{ counts[t.key] ?? 0 }})</span>
      </router-link>
    </nav>
    <div class="sc-toolbar">
      <input v-model.trim="q" type="search" placeholder="Tìm theo tên sản phẩm hoặc tên shop" @input="onSearch" />
      <select v-model="categoryId" @change="load(0)">
        <option :value="null">Tất cả ngành hàng</option>
        <template v-for="c in categories" :key="c.id">
          <option :value="c.id">{{ c.name }}</option>
          <option v-for="s in c.children" :key="s.id" :value="s.id">&nbsp;&nbsp;└ {{ s.name }}</option>
        </template>
      </select>
      <span class="spacer"></span>
      <button v-if="status === 'PENDING' && selected.length" type="button" class="btn sm" :disabled="busy" @click="onBulkApprove">Duyệt {{ selected.length }} sản phẩm</button>
    </div>

    <div v-if="loading && !page.items.length" class="sc-empty">Đang tải...</div>
    <div v-else-if="!page.items.length" class="sc-empty">{{ EMPTY[status] || 'Không có sản phẩm nào' }}</div>
    <table v-else class="sc-table">
      <thead>
        <tr>
          <th v-if="status === 'PENDING'" class="ck"><input type="checkbox" :checked="allSelected" @change="toggleAll" /></th>
          <th>Sản phẩm</th><th>Shop</th><th class="r">Giá</th><th>Kiểm tra tự động</th><th>Trạng thái</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in page.items" :key="p.id">
          <td v-if="status === 'PENDING'" class="ck"><input v-model="selected" type="checkbox" :value="p.id" /></td>
          <td>
            <div class="prod" @click="open(p)">
              <img :src="p.thumbnailUrl || placeholder" alt="" />
              <div>
                <div class="name">{{ p.name }}</div>
                <div class="faint">{{ p.category || 'Chưa chọn ngành hàng' }} · {{ formatDateTime(p.createdAt) }}</div>
              </div>
            </div>
          </td>
          <td class="shop">{{ p.shop.name }}</td>
          <td class="r">{{ formatVnd(p.price) }}</td>
          <td class="flags">
            <span v-if="!p.flags.length && !p.openReports" class="faint">Không phát hiện vấn đề</span>
            <span v-for="f in p.flags" :key="f.code" class="flag" :class="f.severity" :title="f.label">{{ f.label }}</span>
            <span v-if="p.openReports" class="flag HIGH">🚩 {{ p.openReports }} báo cáo</span>
          </td>
          <td>
            <span class="st" :class="p.status">{{ PRODUCT_STATUS[p.status] }}</span>
            <div v-if="p.reason" class="faint reason" :title="p.reason">{{ p.violationLabel || p.reason }}</div>
          </td>
          <td class="actions">
            <button type="button" class="link" @click="open(p)">Xem</button>
            <button v-if="p.status === 'PENDING'" type="button" class="link good" :disabled="busy" @click="quick(() => approveProduct(p.id))">Duyệt</button>
            <button v-if="p.status !== 'REMOVED'" type="button" class="link danger" @click="open(p, 'remove')">Gỡ</button>
          </td>
        </tr>
      </tbody>
    </table>
    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
    <p v-if="error" class="error">{{ error }}</p>

    <ProductModerationModal v-if="viewing" :product-id="viewing.id" :start-mode="viewing.mode" :violations="counts.violations || []"
                            @close="viewing = null" @changed="refresh" />
  </section>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import ProductModerationModal from '../../components/admin/ProductModerationModal.vue'
import { adminProducts, adminProductCounts, approveProduct, bulkApproveProducts, adminCategories, saveAdminSettings } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { PRODUCT_STATUS } from '../../utils/adminLabels'

const TABS = [
  { key: 'PENDING', label: 'Chờ duyệt' }, { key: 'REPORTED', label: 'Bị báo cáo' }, { key: 'APPROVED', label: 'Đã duyệt' },
  { key: 'REJECTED', label: 'Bị từ chối' }, { key: 'REMOVED', label: 'Đã gỡ' }, { key: 'ALL', label: 'Tất cả' }
]
const EMPTY = { PENDING: 'Không có sản phẩm nào chờ duyệt 👍', REPORTED: 'Không có báo cáo nào cần xử lý 👍' }
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44"><rect width="44" height="44" fill="%23f0f0f0"/></svg>'

const route = useRoute()
const { can } = useAdminMe()
const page = ref({ items: [], page: 0, totalPages: 0 })
const counts = ref({})
const categories = ref([])
const categoryId = ref(null)
const q = ref('')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const selected = ref([])
const viewing = ref(null)
let timer = null

const status = computed(() => route.query.status || 'PENDING')
const allSelected = computed(() => page.value.items.length > 0 && page.value.items.every(p => selected.value.includes(p.id)))

async function load(p = 0) {
  loading.value = true
  error.value = ''
  selected.value = []
  try {
    page.value = (await adminProducts({ status: status.value, categoryId: categoryId.value, q: q.value, page: p })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}
const loadCounts = () => adminProductCounts().then(({ data }) => { counts.value = data }).catch(() => {})
const refresh = () => { load(page.value.page); loadCounts() }

function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => load(0), 300)
}
function toggleAll() {
  selected.value = allSelected.value ? [] : page.value.items.map(p => p.id)
}
function open(p, mode = null) {
  viewing.value = { id: p.id, mode }
}

async function quick(fn) {
  busy.value = true
  error.value = ''
  try {
    await fn()
    refresh()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
const onBulkApprove = () => confirm(`Duyệt ${selected.value.length} sản phẩm đã chọn?`) && quick(() => bulkApproveProducts(selected.value))

async function toggleSetting() {
  const next = !counts.value.requireApproval
  if (!confirm(next ? 'Bật duyệt sản phẩm mới? Mọi sản phẩm đăng từ bây giờ sẽ chờ duyệt mới hiển thị.' : 'Tắt duyệt sản phẩm mới?')) return
  await quick(() => saveAdminSettings({ requireProductApproval: next }))
}

watch(status, () => load(0), { immediate: true })
onMounted(() => {
  loadCounts()
  adminCategories().then(({ data }) => { categories.value = data }).catch(() => {})
})
</script>

<style scoped>
.setting { display: flex; align-items: center; gap: 16px; padding: 12px 14px; border: 1px solid var(--color-border); border-radius: 6px; background: #fafafa; margin-bottom: 14px; }
.setting.on { background: #f3f0ff; border-color: #e0d8ff; }
.setting > div { flex: 1; }
.setting p { margin: 4px 0 0; font-size: 13px; color: var(--color-text-muted); line-height: 1.5; }
.setting a { color: #2673dd; }
.switch { width: 46px; height: 26px; border-radius: 999px; border: none; background: #ccc; position: relative; cursor: pointer; flex-shrink: 0; }
.switch span { position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; border-radius: 50%; background: white; transition: left 0.15s; }
.switch.on { background: #6941c6; }
.switch.on span { left: 23px; }
.count { color: var(--color-text-faint); margin-left: 2px; }
.sc-toolbar input { max-width: 340px; }
.sc-toolbar select { width: auto; max-width: 240px; margin: 0; }
.sc-table td { padding: 10px; vertical-align: top; }
.ck { width: 28px; }
.ck input { width: 16px; height: 16px; margin: 0; accent-color: var(--color-primary); }
.prod { display: flex; gap: 10px; cursor: pointer; min-width: 220px; max-width: 320px; }
.prod img { width: 44px; height: 44px; object-fit: cover; border-radius: 3px; flex-shrink: 0; }
.prod:hover .name { color: var(--color-primary); }
.name { font-size: 14px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.shop { font-size: 13px; max-width: 150px; }
.r { text-align: right; white-space: nowrap; }
.flags { max-width: 280px; }
.flag { display: inline-block; font-size: 11px; padding: 1px 6px; border-radius: 3px; margin: 0 4px 4px 0; background: #f2f2f2; color: #666; max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.flag.HIGH { background: #fdecea; color: #d33; }
.flag.MEDIUM { background: #fff4e5; color: #b35c00; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; white-space: nowrap; }
.st.PENDING { background: #fff4e5; color: #b35c00; }
.st.APPROVED { background: #eaf7ef; color: #1e9e4a; }
.st.REJECTED { background: #f2f2f2; color: #666; }
.st.REMOVED { background: #fdecea; color: #d33; }
.reason { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 4px; }
.actions { white-space: nowrap; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 5px; }
.link.good { color: #1e9e4a; }
.link.danger { color: #d33; }
.error { color: #d33; font-size: 13px; }
</style>
