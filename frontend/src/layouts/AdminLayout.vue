<template>
  <div class="ad">
    <header class="ad-header">
      <button class="icon-btn menu-toggle" aria-label="Mở menu" @click="sidebarOpen = !sidebarOpen">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
      </button>
      <router-link :to="{ name: 'admin-dashboard' }" class="brand">
        <span class="logo-mark">S</span>
        <span class="logo-text">Shoppi</span>
        <span class="brand-sub">Quản Trị Sàn</span>
      </router-link>
      <div class="header-right">
        <router-link to="/" class="icon-btn" title="Trang mua sắm Shoppi">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M4 10 12 4l8 6v9a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>
        </router-link>
        <NotificationBell tone="dark" />
        <span class="divider"></span>
        <div ref="accountEl" class="account" @click="menuOpen = !menuOpen">
          <span class="avatar">{{ (auth.fullName || 'A').charAt(0).toUpperCase() }}</span>
          <span class="account-name">{{ auth.fullName }}</span>
          <div v-if="menuOpen" class="account-menu" @click.stop>
            <div class="menu-head">
              <div class="menu-name">{{ auth.fullName }}</div>
              <div class="menu-role">{{ me?.superAdmin ? 'Quản trị viên cấp cao' : 'Nhân viên quản trị' }}</div>
            </div>
            <router-link :to="{ name: 'admin-security' }" @click="menuOpen = false">Bảo mật tài khoản</router-link>
            <router-link to="/" @click="menuOpen = false">Về trang mua sắm</router-link>
            <a href="#" @click.prevent="onLogout">Đăng xuất</a>
          </div>
        </div>
      </div>
    </header>

    <div class="ad-body">
      <div v-if="sidebarOpen" class="sidebar-backdrop" @click="sidebarOpen = false"></div>
      <aside class="sidebar" :class="{ open: sidebarOpen }">
        <nav>
          <section v-for="group in visibleMenu" :key="group.title" class="nav-group">
            <div class="group-title">{{ group.title }}</div>
            <ul>
              <li v-for="item in group.items" :key="item.label">
                <router-link :to="item.to" :class="{ active: isActive(item.to) }" @click="sidebarOpen = false">
                  {{ item.label }}
                  <span v-if="item.badge && item.badge() > 0" class="nav-badge">{{ item.badge() }}</span>
                </router-link>
              </li>
            </ul>
          </section>
        </nav>
        <div v-if="me" class="perm-note">
          {{ me.superAdmin ? 'Bạn có toàn quyền quản trị' : 'Quyền của bạn: ' + me.permissions.map(p => permissionLabel[p]).join(', ') }}
        </div>
      </aside>

      <main class="ad-main">
        <div v-if="!me" class="sc-card sc-empty">{{ error || 'Đang tải...' }}</div>
        <div v-else-if="!allowed" class="sc-card sc-empty denied">
          <div class="lock">🔒</div>
          <p>Bạn không có quyền truy cập mục <strong>{{ permissionLabel[route.meta.perm] }}</strong>.</p>
          <p class="text-faint">Liên hệ quản trị viên cấp cao nếu bạn cần quyền này.</p>
        </div>
        <router-view v-else />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import NotificationBell from '../components/NotificationBell.vue'
import { useAdminMe, resetAdminMe } from '../composables/useAdminMe'
import { adminStats } from '../api/admin'
import { extractError } from '../utils/apiError'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { me, load, can, permissionLabel } = useAdminMe()
const error = ref('')
const sidebarOpen = ref(false)
const menuOpen = ref(false)
const accountEl = ref(null)
const stats = ref({})
let statsTimer = null

// perm: the AdminPermission a section needs (same mapping as the backend's SecurityConfig)
const menu = [
  { title: 'Tổng quan', items: [{ label: 'Tổng quan sàn', to: { name: 'admin-dashboard' } }] },
  {
    title: 'Thống kê & Báo cáo', perm: 'REPORTS',
    items: [
      { label: 'Tổng quan kinh doanh', to: { name: 'admin-report-overview' } },
      { label: 'Top shop & sản phẩm', to: { name: 'admin-report-top' } },
      { label: 'Tăng trưởng', to: { name: 'admin-report-growth' } }
    ]
  },
  {
    title: 'Người dùng', perm: 'USERS',
    items: [
      { label: 'Tất cả tài khoản', to: { name: 'admin-users' } },
      { label: 'Người mua', to: { name: 'admin-users', query: { role: 'BUYER' } } },
      { label: 'Người bán', to: { name: 'admin-users', query: { role: 'SELLER' } } },
      { label: 'Tài khoản bị khóa', to: { name: 'admin-users', query: { status: 'LOCKED' } }, badge: () => stats.value.lockedUsers }
    ]
  },
  {
    title: 'Shop', perm: 'SHOPS',
    items: [
      { label: 'Duyệt shop mới', to: { name: 'admin-shops', query: { status: 'PENDING' } }, badge: () => stats.value.pendingShops },
      { label: 'Tất cả shop', to: { name: 'admin-shops' } },
      { label: 'Xác minh giấy tờ', to: { name: 'admin-verifications' } }
    ]
  },
  {
    title: 'Sản phẩm', perm: 'PRODUCTS',
    items: [
      { label: 'Duyệt sản phẩm', to: { name: 'admin-products', query: { status: 'PENDING' } }, badge: () => stats.value.pendingProducts },
      { label: 'Bị người dùng báo cáo', to: { name: 'admin-products', query: { status: 'REPORTED' } }, badge: () => stats.value.openReports },
      { label: 'Tất cả sản phẩm', to: { name: 'admin-products', query: { status: 'ALL' } } },
      { label: 'Danh mục ngành hàng', to: { name: 'admin-categories' } }
    ]
  },
  {
    title: 'Giao dịch',
    items: [
      { label: 'Tất cả đơn hàng', to: { name: 'admin-orders' }, perm: 'ORDERS' },
      { label: 'Đơn hàng bị trễ', to: { name: 'admin-orders', query: { status: 'LATE' } }, perm: 'ORDERS' },
      { label: 'Tranh chấp', to: { name: 'admin-disputes' }, perm: 'ORDERS', badge: () => stats.value.openDisputes },
      { label: 'Hoàn tiền', to: { name: 'admin-refunds' }, perm: 'FINANCE', badge: () => stats.value.pendingRefunds },
      { label: 'Đối soát người bán', to: { name: 'admin-settlements' }, perm: 'FINANCE' },
      { label: 'Đối soát thanh toán', to: { name: 'admin-reconciliation' }, perm: 'FINANCE' }
    ]
  },
  {
    title: 'Marketing toàn sàn', perm: 'VOUCHERS',
    items: [
      { label: 'Chương trình khuyến mãi', to: { name: 'admin-campaigns' } },
      { label: 'Shoppi Voucher', to: { name: 'admin-vouchers' } },
      { label: 'Flash Sale toàn sàn', to: { name: 'admin-flash-sales' }, badge: () => stats.value.pendingFlashItems },
      { label: 'Banner trang chủ', to: { name: 'admin-banners' } }
    ]
  },
  {
    title: 'Vận hành',
    items: [
      { label: 'Yêu cầu hỗ trợ', to: { name: 'admin-support' }, perm: 'SUPPORT' },
      { label: 'Gửi thông báo', to: { name: 'admin-notifications' }, perm: 'BROADCAST' }
    ]
  },
  {
    title: 'Hệ thống',
    items: [
      { label: 'Nhân viên & phân quyền', to: { name: 'admin-staff' }, perm: 'STAFF' },
      { label: 'Nhật ký quản trị', to: { name: 'admin-audit' }, perm: 'STAFF' },
      { label: 'Cấu hình sàn', to: { name: 'admin-settings' }, perm: 'SETTINGS' },
      { label: 'Chính sách chung', to: { name: 'admin-policies' }, perm: 'SETTINGS' },
      { label: 'AI & chi phí LLM', to: { name: 'admin-ai' }, perm: 'SETTINGS' },
      { label: 'Thanh toán & vận chuyển', to: { name: 'admin-gateways' }, perm: 'SETTINGS' },
      { label: 'Email', to: { name: 'admin-email' }, perm: 'SETTINGS' },
      { label: 'Giám sát hệ thống', to: { name: 'admin-monitoring' }, perm: 'SETTINGS' }
    ]
  }
]

// a staff admin only sees the sections they may use
const visibleMenu = computed(() => menu
  .filter(g => !g.perm || can(g.perm))
  .map(g => ({ ...g, items: g.items.filter(i => !i.perm || can(i.perm)) }))
  .filter(g => g.items.length))
const allowed = computed(() => !route.meta.perm || can(route.meta.perm))

// the platform requires 2FA for admins and this one has not turned it on: only the security page works
watch([() => me.value?.twoFactorSetupRequired, () => route.name], ([required, name]) => {
  if (required && name !== 'admin-security') router.replace({ name: 'admin-security', query: { required: '1' } })
}, { immediate: true })

function isActive(to) {
  if (route.name !== to.name) return false
  const want = to.query || {}
  const keys = new Set([...Object.keys(want), ...Object.keys(route.query).filter(k => ['role', 'status'].includes(k))])
  return [...keys].every(k => (want[k] ?? null) === (route.query[k] ?? null))
}

const loadStats = () => adminStats().then(({ data }) => { stats.value = data }).catch(() => {})

function onLogout() {
  resetAdminMe()
  auth.logout()
  router.push({ name: 'login' })
}
function onClickOutside(e) {
  if (menuOpen.value && accountEl.value && !accountEl.value.contains(e.target)) menuOpen.value = false
}

onMounted(async () => {
  document.addEventListener('click', onClickOutside)
  try {
    await load(true)
  } catch (e) {
    error.value = extractError(e)
  }
  loadStats()
  statsTimer = setInterval(() => { if (!document.hidden) loadStats() }, 60000)
})
const stopAfterEach = router.afterEach(() => loadStats())
onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside)
  clearInterval(statsTimer)
  stopAfterEach()
})
</script>

<style scoped>
.ad { min-height: 100vh; background: #f6f6f6; }
.ad-header {
  position: sticky; top: 0; z-index: 50; height: 64px;
  display: flex; align-items: center; gap: 12px; padding: 0 24px;
  background: #1f2933; color: white;
}
.brand { display: flex; align-items: center; gap: 8px; }
.logo-mark { width: 32px; height: 32px; border-radius: 7px; background: var(--color-primary); color: white; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 18px; }
.logo-text { color: white; font-weight: 700; font-size: 21px; }
.brand-sub { font-size: 18px; color: #cbd2d9; margin-left: 2px; white-space: nowrap; }
.header-right { margin-left: auto; display: flex; align-items: center; gap: 6px; }
.icon-btn { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #cbd2d9; background: none; border: none; cursor: pointer; }
.icon-btn:hover { background: rgba(255, 255, 255, 0.08); }
.menu-toggle { display: none; }
.divider { width: 1px; height: 28px; background: rgba(255, 255, 255, 0.15); margin: 0 10px; }
.account { position: relative; display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 6px 8px; border-radius: 4px; }
.account:hover { background: rgba(255, 255, 255, 0.08); }
.avatar { width: 34px; height: 34px; border-radius: 50%; background: var(--color-primary); color: white; display: flex; align-items: center; justify-content: center; font-weight: 600; }
.account-name { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 15px; }
.account-menu { position: absolute; right: 0; top: calc(100% + 6px); width: 240px; background: white; color: var(--color-text); border-radius: 4px; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.14); padding: 8px 0; cursor: default; }
.menu-head { padding: 10px 16px 12px; border-bottom: 1px solid var(--color-border); margin-bottom: 6px; }
.menu-name { font-weight: 600; }
.menu-role { font-size: 12px; color: var(--color-text-muted); }
.account-menu a { display: block; padding: 10px 16px; font-size: 14px; }
.account-menu a:hover { background: #f7f7f7; color: var(--color-primary); }

.ad-body { display: flex; align-items: flex-start; }
.sidebar { position: sticky; top: 64px; width: 232px; flex-shrink: 0; height: calc(100vh - 64px); overflow-y: auto; background: white; padding: 12px 0 24px; box-shadow: 1px 0 3px rgba(0, 0, 0, 0.04); display: flex; flex-direction: column; }
.nav-group { margin-bottom: 8px; }
.group-title { padding: 10px 20px 6px; font-size: 13px; font-weight: 600; color: #8a8a8a; text-transform: uppercase; letter-spacing: 0.3px; }
.nav-group ul { list-style: none; margin: 0; padding: 0; }
.nav-group li a { display: flex; align-items: center; justify-content: space-between; padding: 8px 16px 8px 28px; font-size: 14px; color: var(--color-text); }
.nav-group li a:hover { color: var(--color-primary); }
.nav-group li a.active { color: var(--color-primary); font-weight: 500; background: var(--color-primary-light); }
.nav-badge { background: var(--color-primary); color: white; font-size: 11px; border-radius: 999px; padding: 0 6px; line-height: 17px; font-weight: 500; }
.perm-note { margin-top: auto; padding: 12px 20px 0; font-size: 12px; color: var(--color-text-faint); line-height: 1.5; }
.ad-main { flex: 1; min-width: 0; padding: 20px 24px 40px; }
.ad-main :deep(.container) { max-width: none; padding: 0; }
.denied .lock { font-size: 40px; }
.sidebar-backdrop { display: none; }
@media (max-width: 900px) {
  .menu-toggle { display: flex; }
  .ad-header { padding: 0 12px; }
  .sidebar { position: fixed; left: 0; top: 64px; z-index: 40; transform: translateX(-100%); transition: transform 0.2s; }
  .sidebar.open { transform: none; }
  .sidebar-backdrop { display: block; position: fixed; inset: 64px 0 0; background: rgba(0, 0, 0, 0.3); z-index: 30; }
  .ad-main { padding: 16px; }
}
@media (max-width: 600px) { .brand-sub, .account-name, .divider { display: none; } }
</style>
