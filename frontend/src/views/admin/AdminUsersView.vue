<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Quản Lý Người Dùng</h2>
      <span class="sc-card-sub">Tài khoản người mua, người bán và quản trị viên</span>
    </div>

    <nav class="sc-tabs">
      <router-link v-for="t in TABS" :key="t.key" :to="{ name: 'admin-users', query: t.query }" :class="{ active: tab === t.key }">
        {{ t.label }} <span class="count">({{ counts[t.count] ?? 0 }})</span>
      </router-link>
    </nav>

    <div class="sc-toolbar">
      <input v-model.trim="q" type="search" placeholder="Tìm theo email, họ tên hoặc số điện thoại" @input="onSearch" />
    </div>

    <div v-if="loading && !page.items.length" class="sc-empty">Đang tải...</div>
    <div v-else-if="!page.items.length" class="sc-empty">Không có tài khoản nào</div>
    <div v-else class="sc-table-wrap">
      <table class="sc-table">
        <thead><tr><th>Tài khoản</th><th>Vai trò</th><th>Trạng thái</th><th>Ngày tham gia</th><th>Đăng nhập gần nhất</th><th></th></tr></thead>
        <tbody>
          <tr v-for="u in page.items" :key="u.id" :class="{ dim: u.status === 'DELETED' }">
            <td>
              <router-link :to="{ name: 'admin-user', params: { id: u.id } }" class="who">
                <span class="avatar">{{ u.fullName.charAt(0).toUpperCase() }}</span>
                <span>
                  <strong>{{ u.fullName }}</strong>
                  <span class="faint">{{ u.email }}<template v-if="u.phone"> · {{ u.phone }}</template></span>
                </span>
              </router-link>
            </td>
            <td>
              <span class="role" :class="u.role">{{ ROLE[u.role] }}</span>
              <span v-if="u.superAdmin" class="role super">Cấp cao</span>
              <div v-if="u.shop" class="faint small">🏪 {{ u.shop.name }}<template v-if="u.shop.status !== 'APPROVED'"> · {{ SHOP_STATUS[u.shop.status] }}</template></div>
            </td>
            <td>
              <span class="status" :class="u.status">{{ STATUS[u.status] }}</span>
              <div v-if="u.status === 'LOCKED'" class="faint small" :title="u.lockedReason">
                {{ u.lockedUntil ? 'đến ' + formatDateTime(u.lockedUntil) : 'vĩnh viễn' }} · {{ cut(u.lockedReason, 40) }}
              </div>
            </td>
            <td class="muted">{{ formatDay(u.createdAt) }}</td>
            <td class="muted">{{ u.lastLoginAt ? formatDateTime(u.lastLoginAt) : '—' }}</td>
            <td class="actions">
              <router-link :to="{ name: 'admin-user', params: { id: u.id } }" class="link">Xem</router-link>
              <template v-if="canAct(u)">
                <button v-if="u.status === 'ACTIVE'" type="button" class="link warn" @click="openAction('lock', u)">Khóa</button>
                <button v-else-if="u.status === 'LOCKED'" type="button" class="link" @click="onUnlock(u)">Mở khóa</button>
                <button v-if="u.status !== 'DELETED'" type="button" class="link danger" @click="openAction('delete', u)">Xóa</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
    <p v-if="error" class="error">{{ error }}</p>

    <UserActionDialog v-if="action" :mode="action.mode" :user="action.user" @close="action = null" @done="onDone" />
  </section>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import UserActionDialog from '../../components/admin/UserActionDialog.vue'
import { adminUsers, adminUserCounts, unlockUser } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { ROLE, STATUS, SHOP_STATUS } from '../../utils/adminLabels'

const TABS = [
  { key: 'all', label: 'Tất cả', query: {}, count: 'all' },
  { key: 'BUYER', label: 'Người mua', query: { role: 'BUYER' }, count: 'buyers' },
  { key: 'SELLER', label: 'Người bán', query: { role: 'SELLER' }, count: 'sellers' },
  { key: 'ADMIN', label: 'Quản trị', query: { role: 'ADMIN' }, count: 'admins' },
  { key: 'LOCKED', label: 'Bị khóa', query: { status: 'LOCKED' }, count: 'locked' },
  { key: 'DELETED', label: 'Đã xóa', query: { status: 'DELETED' }, count: 'deleted' }
]

const route = useRoute()
const { me } = useAdminMe()
const page = ref({ items: [], page: 0, totalPages: 0 })
const counts = ref({})
const q = ref('')
const loading = ref(false)
const error = ref('')
const action = ref(null)
let timer = null

const tab = computed(() => route.query.status || route.query.role || 'all')
const formatDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '')
const cut = (s, n) => (s && s.length > n ? s.slice(0, n - 1) + '…' : s || '')

// same rule as the server: not yourself; admins only by the super admin, never another super admin
function canAct(u) {
  if (!me.value || u.id === me.value.id) return false
  if (u.role === 'ADMIN') return me.value.superAdmin && !u.superAdmin
  return true
}

async function load(p = 0) {
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminUsers({ role: route.query.role, status: route.query.status || 'ALL', q: q.value, page: p })
    page.value = data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}
const loadCounts = () => adminUserCounts().then(({ data }) => { counts.value = data }).catch(() => {})

function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => load(0), 300)
}

function openAction(mode, user) {
  action.value = { mode, user }
}

async function onUnlock(u) {
  if (!confirm(`Mở khóa tài khoản ${u.email}?`)) return
  try {
    await unlockUser(u.id)
    load(page.value.page)
    loadCounts()
  } catch (e) {
    error.value = extractError(e)
  }
}

function onDone() {
  action.value = null
  load(page.value.page)
  loadCounts()
}

watch(() => [route.query.role, route.query.status], () => load(0), { immediate: true })
onMounted(loadCounts)
</script>

<style scoped>
.count { color: var(--color-text-faint); margin-left: 2px; }
.sc-toolbar input { max-width: 420px; }
.who { display: flex; align-items: center; gap: 10px; min-width: 240px; }
.who strong { display: block; }
.who:hover strong { color: var(--color-primary); }
.avatar { width: 34px; height: 34px; border-radius: 50%; background: #eef2f7; color: #5b7083; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.faint { color: var(--color-text-faint); font-size: 12px; display: block; }
.small { margin-top: 3px; }
.muted { color: var(--color-text-faint); font-size: 12px; white-space: nowrap; }
.role { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #f0f5ff; color: #2673dd; white-space: nowrap; }
.role.SELLER { background: #fff4e5; color: #b35c00; }
.role.ADMIN { background: #f3f0ff; color: #6941c6; }
.role.super { background: #6941c6; color: white; margin-left: 4px; }
.status { font-size: 12px; padding: 2px 8px; border-radius: 3px; white-space: nowrap; }
.status.ACTIVE { background: #eaf7ef; color: #1e9e4a; }
.status.LOCKED { background: #fdecea; color: #d33; }
.status.DELETED { background: #f2f2f2; color: #888; }
.actions { white-space: nowrap; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 6px; }
.link.warn { color: #e67e22; }
.link.danger { color: #d33; }
tr.dim td { opacity: 0.6; }
.error { color: #d33; font-size: 13px; }
</style>
