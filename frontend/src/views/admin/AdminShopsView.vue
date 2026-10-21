<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Duyệt & Quản Lý Shop</h2>
        <span class="sc-card-sub">Duyệt shop mới đăng ký, đình chỉ shop vi phạm</span>
      </div>

      <div class="setting" :class="{ on: counts.requireApproval }">
        <div>
          <strong>Duyệt shop mới trước khi hiển thị</strong>
          <p>{{ counts.requireApproval
            ? 'Đang BẬT: shop mới đăng ký ở trạng thái "Chờ duyệt", người bán vẫn thiết lập shop và đăng sản phẩm, nhưng người mua chỉ thấy sau khi được duyệt.'
            : 'Đang TẮT: shop mới hoạt động ngay sau khi đăng ký. Bật lên nếu sàn muốn kiểm duyệt người bán.' }}</p>
        </div>
        <button v-if="can('SETTINGS')" type="button" class="switch" :class="{ on: counts.requireApproval }" :disabled="savingSetting" @click="toggleSetting">
          <span></span>
        </button>
        <span v-else class="faint">Cần quyền "Cài đặt sàn" để thay đổi</span>
      </div>

      <nav class="sc-tabs">
        <router-link v-for="t in TABS" :key="t.key" :to="{ name: 'admin-shops', query: t.key === 'ALL' ? {} : { status: t.key } }" :class="{ active: status === t.key }">
          {{ t.label }} <span class="count">({{ t.key === 'ALL' ? total : counts[t.key] ?? 0 }})</span>
        </router-link>
      </nav>
      <div class="sc-toolbar">
        <input v-model.trim="q" type="search" placeholder="Tìm theo tên shop hoặc email người bán" @input="onSearch" />
      </div>

      <div v-if="loading && !page.items.length" class="sc-empty">Đang tải...</div>
      <div v-else-if="!page.items.length" class="sc-empty">{{ status === 'PENDING' ? 'Không có shop nào chờ duyệt 👍' : 'Không có shop nào' }}</div>

      <div v-for="s in page.items" :key="s.id" class="shop">
        <img v-if="s.logoUrl" :src="s.logoUrl" alt="" class="logo" />
        <span v-else class="logo ph">{{ s.name.charAt(0).toUpperCase() }}</span>
        <div class="body">
          <div class="title">
            <router-link :to="`/shop/${s.id}`" target="_blank" class="name">{{ s.name }}</router-link>
            <span class="st" :class="s.status">{{ SHOP_STATUS[s.status] }}</span>
            <span v-if="s.verified" class="tag ok">✓ Đã xác minh</span>
            <span v-else-if="s.verification === 'PENDING'" class="tag">Giấy tờ chờ xác minh</span>
            <span v-if="s.status === 'APPROVED' && !s.active" class="tag bad">Đang ẩn (tài khoản người bán bị khóa)</span>
          </div>
          <div class="meta">
            Người bán:
            <router-link v-if="can('USERS')" :to="{ name: 'admin-user', params: { id: s.seller.id } }">{{ s.seller.fullName }}</router-link>
            <template v-else>{{ s.seller.fullName }}</template>
            · {{ s.seller.email }}<template v-if="s.pickupPhone"> · {{ s.pickupPhone }}</template>
            <span v-if="!s.seller.enabled" class="bad"> · tài khoản bị khóa</span>
          </div>
          <div class="meta">📍 {{ s.pickupAddress }}<template v-if="s.province">, {{ s.province }}</template> · {{ s.products }} sản phẩm · Đăng ký {{ formatDateTime(s.createdAt) }}</div>
          <p v-if="s.description" class="desc">{{ s.description }}</p>
          <div v-if="s.missing.length && s.status === 'PENDING'" class="missing">Hồ sơ còn thiếu: {{ s.missing.join(', ') }}</div>
          <div v-if="s.statusReason" class="reason">Lý do: {{ s.statusReason }} <span class="faint">({{ formatDateTime(s.statusChangedAt) }})</span></div>
        </div>
        <div class="actions">
          <template v-if="s.status === 'PENDING'">
            <button type="button" class="btn sm" :disabled="busyId === s.id" @click="onApprove(s)">Duyệt</button>
            <button type="button" class="btn sm ghost" @click="openReason('reject', s)">Từ chối</button>
          </template>
          <button v-else-if="s.status === 'REJECTED'" type="button" class="btn sm ghost" :disabled="busyId === s.id" @click="onApprove(s)">Duyệt</button>
          <button v-else-if="s.status === 'APPROVED'" type="button" class="btn sm ghost warn" @click="openReason('suspend', s)">Đình chỉ</button>
          <button v-else-if="s.status === 'SUSPENDED' && !s.seller.deleted" type="button" class="btn sm" :disabled="busyId === s.id" @click="onReinstate(s)">Mở lại</button>
        </div>
      </div>
      <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <ReasonDialog v-if="dialog" :title="dialog.mode === 'reject' ? 'Từ chối shop' : 'Đình chỉ shop'"
                  :subtitle="dialog.shop.name" :presets="dialog.mode === 'reject' ? REJECT_REASONS : SUSPEND_REASONS"
                  :confirm-label="dialog.mode === 'reject' ? 'Từ chối' : 'Đình chỉ shop'" :busy="dialogBusy" :error="dialogError"
                  @close="dialog = null" @submit="onReason" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import ReasonDialog from '../../components/admin/ReasonDialog.vue'
import { adminShops, adminShopCounts, approveShop, rejectShop, suspendShop, reinstateShop, saveAdminSettings } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { SHOP_STATUS } from '../../utils/adminLabels'

const TABS = [
  { key: 'PENDING', label: 'Chờ duyệt' }, { key: 'APPROVED', label: 'Đang hoạt động' }, { key: 'REJECTED', label: 'Bị từ chối' },
  { key: 'SUSPENDED', label: 'Tạm đình chỉ' }, { key: 'ALL', label: 'Tất cả' }
]
const REJECT_REASONS = ['Thông tin shop chưa đầy đủ (logo, giới thiệu, địa chỉ)', 'Tên shop gây nhầm lẫn với thương hiệu khác', 'Số điện thoại lấy hàng không liên lạc được', 'Ngành hàng kinh doanh không được phép']
const SUSPEND_REASONS = ['Bán hàng giả, hàng nhái', 'Nhiều khiếu nại chưa xử lý', 'Gian lận đơn hàng / đánh giá ảo', 'Đăng sản phẩm cấm kinh doanh']

const route = useRoute()
const { can } = useAdminMe()
const page = ref({ items: [], page: 0, totalPages: 0 })
const counts = ref({})
const q = ref(route.query.q || '')
const loading = ref(false)
const error = ref('')
const busyId = ref(null)
const savingSetting = ref(false)
const dialog = ref(null)
const dialogBusy = ref(false)
const dialogError = ref('')
let timer = null

const status = computed(() => route.query.status || 'ALL')
const total = computed(() => TABS.filter(t => t.key !== 'ALL').reduce((a, t) => a + (counts.value[t.key] || 0), 0))

async function load(p = 0) {
  loading.value = true
  error.value = ''
  try {
    page.value = (await adminShops({ status: status.value, q: q.value, page: p })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}
const loadCounts = () => adminShopCounts().then(({ data }) => { counts.value = data }).catch(() => {})
const refresh = () => { load(page.value.page); loadCounts() }

function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => load(0), 300)
}

async function run(id, fn) {
  busyId.value = id
  error.value = ''
  try {
    await fn()
    refresh()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busyId.value = null
  }
}
const onApprove = (s) => run(s.id, () => approveShop(s.id))
const onReinstate = (s) => confirm(`Mở lại shop "${s.name}"?`) && run(s.id, () => reinstateShop(s.id))

function openReason(mode, shop) {
  dialog.value = { mode, shop }
  dialogError.value = ''
}

async function onReason(reason) {
  dialogBusy.value = true
  dialogError.value = ''
  try {
    const { mode, shop } = dialog.value
    await (mode === 'reject' ? rejectShop(shop.id, reason) : suspendShop(shop.id, reason))
    dialog.value = null
    refresh()
  } catch (e) {
    dialogError.value = extractError(e)
  } finally {
    dialogBusy.value = false
  }
}

async function toggleSetting() {
  const next = !counts.value.requireApproval
  if (!confirm(next ? 'Bật duyệt shop mới? Shop đăng ký từ bây giờ sẽ chờ duyệt mới hiển thị.' : 'Tắt duyệt shop mới? Shop đăng ký từ bây giờ hoạt động ngay (shop đang chờ duyệt vẫn cần duyệt).')) return
  savingSetting.value = true
  try {
    await saveAdminSettings({ requireShopApproval: next })
    loadCounts()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    savingSetting.value = false
  }
}

watch(status, () => load(0), { immediate: true })
onMounted(loadCounts)
</script>

<style scoped>
.setting { display: flex; align-items: center; gap: 16px; padding: 12px 14px; border: 1px solid var(--color-border); border-radius: 6px; background: #fafafa; margin-bottom: 14px; }
.setting.on { background: #f3f0ff; border-color: #e0d8ff; }
.setting > div { flex: 1; }
.setting p { margin: 4px 0 0; font-size: 13px; color: var(--color-text-muted); line-height: 1.5; }
.switch { width: 46px; height: 26px; border-radius: 999px; border: none; background: #ccc; position: relative; cursor: pointer; flex-shrink: 0; }
.switch span { position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; border-radius: 50%; background: white; transition: left 0.15s; }
.switch.on { background: #6941c6; }
.switch.on span { left: 23px; }
.count { color: var(--color-text-faint); margin-left: 2px; }
.sc-toolbar input { max-width: 420px; }
.shop { display: flex; gap: 14px; padding: 16px 0; border-bottom: 1px solid var(--color-border); }
.logo { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.logo.ph { background: var(--color-primary-light); color: var(--color-primary); font-weight: 700; font-size: 22px; display: flex; align-items: center; justify-content: center; }
.body { flex: 1; min-width: 0; }
.title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.name { font-weight: 600; font-size: 16px; color: var(--color-text); }
.name:hover { color: var(--color-primary); }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; }
.st.PENDING { background: #fff4e5; color: #b35c00; }
.st.APPROVED { background: #eaf7ef; color: #1e9e4a; }
.st.REJECTED { background: #f2f2f2; color: #666; }
.st.SUSPENDED { background: #fdecea; color: #d33; }
.tag { font-size: 11px; border: 1px solid #ddd; border-radius: 3px; padding: 1px 6px; color: var(--color-text-muted); }
.tag.ok { border-color: #a6dcb8; color: #1e9e4a; }
.tag.bad, .bad { color: #d33; border-color: #f0b4b4; }
.meta { font-size: 13px; color: var(--color-text-muted); margin-top: 4px; }
.meta a { color: #2673dd; }
.desc { font-size: 13px; margin: 6px 0 0; color: var(--color-text); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.missing { font-size: 12px; color: #b35c00; margin-top: 6px; }
.reason { font-size: 13px; color: #b42318; margin-top: 6px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.actions { display: flex; flex-direction: column; gap: 6px; align-items: stretch; min-width: 96px; }
.btn.warn { color: #d33; border-color: #f0b4b4; }
.error { color: #d33; font-size: 13px; }
</style>
