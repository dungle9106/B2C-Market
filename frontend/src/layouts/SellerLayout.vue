<template>
  <div class="sc">
    <!-- ===== top header (white, like Shopee's seller centre) ===== -->
    <header class="sc-header">
      <button class="icon-btn menu-toggle" aria-label="Mở menu" @click="sidebarOpen = !sidebarOpen">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
      </button>
      <router-link to="/seller" class="brand">
        <span class="logo-mark">S</span>
        <span class="logo-text">Shoppi</span>
        <span class="brand-sub">Kênh Người Bán</span>
      </router-link>

      <div class="header-right">
        <router-link to="/" class="icon-btn" title="Trang mua sắm Shoppi">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M4 10 12 4l8 6v9a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>
        </router-link>
        <router-link :to="{ name: 'seller-product-new' }" class="icon-btn" title="Thêm sản phẩm">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
        </router-link>
        <router-link :to="{ name: 'seller-chat' }" class="icon-btn badge-host" title="Chat với Người mua">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.5-4.9A8 8 0 1 1 21 12Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /><path d="M8.5 12h.01M12 12h.01M15.5 12h.01" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" /></svg>
          <span v-if="notifications.chat > 0" class="icon-badge">{{ notifications.chat > 99 ? '99+' : notifications.chat }}</span>
        </router-link>
        <NotificationBell tone="dark" />
        <span class="divider"></span>

        <div ref="accountEl" class="account" @click="menuOpen = !menuOpen">
          <span class="avatar">{{ initial }}</span>
          <span class="account-name">{{ auth.fullName }}</span>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="m6 9 6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <div v-if="menuOpen" class="account-menu" @click.stop>
            <div class="menu-head">
              <span class="avatar lg">{{ initial }}</span>
              <div>
                <div class="menu-name">{{ auth.fullName }}</div>
                <div class="menu-role">Người bán</div>
              </div>
            </div>
            <router-link :to="{ name: 'seller-shop' }" @click="menuOpen = false">Hồ sơ Shop</router-link>
            <router-link :to="{ name: 'seller-security' }" @click="menuOpen = false">Bảo mật tài khoản</router-link>
            <router-link to="/" @click="menuOpen = false">Về trang mua sắm</router-link>
            <a href="#" @click.prevent="onLogout">Đăng xuất</a>
          </div>
        </div>
      </div>
    </header>

    <div class="sc-body">
      <!-- ===== left sidebar with collapsible groups ===== -->
      <div v-if="sidebarOpen" class="sidebar-backdrop" @click="sidebarOpen = false"></div>
      <aside class="sidebar" :class="{ open: sidebarOpen }">
        <nav>
          <section v-for="group in menu" :key="group.title" class="nav-group">
            <button class="group-title" @click="toggleGroup(group.title)">
              <span class="group-icon" v-html="group.icon"></span>
              <span>{{ group.title }}</span>
              <svg class="chev" :class="{ collapsed: collapsed[group.title] }" width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="m6 15 6-6 6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </button>
            <ul v-show="!collapsed[group.title]">
              <li v-for="item in group.items" :key="item.label">
                <router-link :to="item.to" :class="{ active: isActive(item.to) }" @click="sidebarOpen = false">
                  {{ item.label }}
                  <span v-if="item.badge && item.badge() > 0" class="nav-badge">{{ item.badge() }}</span>
                </router-link>
              </li>
            </ul>
          </section>
        </nav>
      </aside>

      <!-- ===== page content ===== -->
      <main class="sc-main">
        <!-- shop moderation: not yet visible to buyers -->
        <div v-if="shopState && shopState.status !== 'APPROVED'" class="shop-banner" :class="shopState.status">
          <template v-if="shopState.status === 'PENDING'">
            ⏳ <strong>Shop đang chờ Shoppi duyệt.</strong> Bạn vẫn có thể hoàn thiện hồ sơ và đăng sản phẩm; shop sẽ hiển thị với người mua ngay khi được duyệt.
          </template>
          <template v-else-if="shopState.status === 'REJECTED'">
            ⚠ <strong>Đăng ký shop chưa được duyệt.</strong> Lý do: {{ shopState.statusReason }}.
            <router-link :to="{ name: 'seller-shop' }">Cập nhật hồ sơ</router-link> rồi
            <button type="button" class="banner-btn" :disabled="resubmitting" @click="onResubmit">{{ resubmitting ? 'Đang gửi...' : 'Gửi duyệt lại' }}</button>
          </template>
          <template v-else-if="shopState.status === 'SUSPENDED'">
            ⛔ <strong>Shop đang bị tạm đình chỉ</strong>, sản phẩm không hiển thị với người mua. Lý do: {{ shopState.statusReason }}.
            <router-link :to="{ name: 'seller-support' }">Liên hệ Shoppi</router-link>
          </template>
          <span v-if="bannerError" class="banner-err">{{ bannerError }}</span>
        </div>
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'
import NotificationBell from '../components/NotificationBell.vue'
import { engagementSummary } from '../api/customerCare'
import { getMyShop, resubmitShop } from '../api/shop'
import { extractError } from '../utils/apiError'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notifications = useNotificationStore()

// moderation state of the shop (banner above every page while it isn't approved)
const shopState = ref(null)
const resubmitting = ref(false)
const bannerError = ref('')
const loadShopState = () => getMyShop().then(({ data }) => { shopState.value = data }).catch(() => {})
async function onResubmit() {
  resubmitting.value = true
  bannerError.value = ''
  try {
    shopState.value = (await resubmitShop()).data
  } catch (e) {
    bannerError.value = extractError(e)
  } finally {
    resubmitting.value = false
  }
}

// badges of "Chăm sóc khách hàng": questions waiting for an answer, reviews without a reply
const engagement = ref({})
const loadEngagement = () => engagementSummary().then(({ data }) => { engagement.value = data }).catch(() => {})
let engagementTimer = null

const sidebarOpen = ref(false) // only matters on small screens
const menuOpen = ref(false)
const accountEl = ref(null)
const initial = computed(() => (auth.fullName || 'S').trim().charAt(0).toUpperCase())

const ICON = (d) => `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`

// sidebar structure; order-status entries deep-link to tabs of the orders page
const menu = [
  {
    title: 'Quản Lý Đơn Hàng',
    icon: ICON('<path d="M6 3h12v18H6z"/><path d="M9 8h6M9 12h6M9 16h4"/>'),
    items: [
      { label: 'Tất cả', to: { name: 'seller-orders' } },
      { label: 'Chờ xác nhận', to: { name: 'seller-orders', query: { status: 'PENDING' } } },
      { label: 'Chờ lấy hàng', to: { name: 'seller-orders', query: { status: 'CONFIRMED' } } },
      { label: 'Đang giao', to: { name: 'seller-orders', query: { status: 'SHIPPING' } } },
      { label: 'Yêu cầu hủy', to: { name: 'seller-orders', query: { status: 'CANCEL_REQUESTS' } } },
      { label: 'Đơn hủy / Trả hàng / Hoàn tiền', to: { name: 'seller-orders', query: { status: 'RETURNS' } } },
      { label: 'Xử Lý Trả Hàng/Hoàn Tiền', to: { name: 'seller-returns' } }
    ]
  },
  {
    title: 'Quản Lý Sản Phẩm',
    icon: ICON('<path d="M3 7l9-4 9 4-9 4z"/><path d="M3 7v10l9 4 9-4V7M12 11v10"/>'),
    items: [
      { label: 'Tất Cả Sản Phẩm', to: { name: 'seller-products' } },
      { label: 'Thêm Sản Phẩm', to: { name: 'seller-product-new' } },
      { label: 'Đăng Hàng Loạt (Excel)', to: { name: 'seller-product-import' } },
      { label: 'Quản Lý Tồn Kho', to: { name: 'seller-inventory' } },
      { label: 'Danh Mục Của Shop', to: { name: 'seller-categories' } }
    ]
  },
  {
    title: 'Kênh Marketing',
    icon: ICON('<path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/><path d="M15 9a3 3 0 0 1 0 6M18 6a7 7 0 0 1 0 12"/>'),
    items: [
      { label: 'Mã Giảm Giá Của Shop', to: { name: 'seller-vouchers' } },
      { label: 'Flash Sale Của Shop', to: { name: 'seller-flash-sale' } },
      { label: 'Flash Sale Của Shoppi', to: { name: 'seller-flash-events' } },
      { label: 'Combo Khuyến Mãi', to: { name: 'seller-combos' } },
      { label: 'Quảng Cáo Shoppi', to: { name: 'seller-ads' } }
    ]
  },
  {
    title: 'Trợ Lý AI',
    icon: ICON('<path d="M12 3l1.8 4.6L18 9l-4.2 1.4L12 15l-1.8-4.6L6 9l4.2-1.4z"/><path d="M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>'),
    items: [
      { label: 'Viết Mô Tả & Gợi Ý Giá', to: { name: 'seller-product-new', query: { ai: '1' } } },
      { label: 'Hỏi Đáp Doanh Thu', to: { name: 'seller-ai-assistant' } },
      { label: 'Phân Tích Đánh Giá', to: { name: 'seller-ai-reviews' } },
      { label: 'AI Trả Lời & Xử Lý Đánh Giá', to: { name: 'seller-review-hub', query: { tab: 'inbox' } } },
      { label: 'Gợi Ý Thời Điểm Khuyến Mãi', to: { name: 'seller-ai-promo' } }
    ]
  },
  {
    title: 'Chăm Sóc Khách Hàng',
    icon: ICON('<path d="M4 5h16v11H8l-4 4z"/><path d="M9 9h6M9 12h4"/>'),
    items: [
      { label: 'Chat với Người mua', to: { name: 'seller-chat' }, badge: () => notifications.chat },
      { label: 'Hỏi Đáp Sản Phẩm', to: { name: 'seller-questions' }, badge: () => engagement.value.unansweredQuestions },
      { label: 'Trung Tâm Đánh Giá', to: { name: 'seller-review-hub' }, badge: () => engagement.value.unrepliedReviews },
      { label: 'Tin Nhắn Tự Động & Mẫu', to: { name: 'seller-care-settings' } },
      { label: 'Thông Báo', to: { name: 'seller-notifications' }, badge: () => notifications.total },
      { label: 'Hỗ Trợ Từ Shoppi', to: { name: 'seller-support' } }
    ]
  },
  {
    title: 'Tài Chính',
    icon: ICON('<path d="M3 7h18v12H3zM3 11h18M7 15h3"/>'),
    items: [
      { label: 'Doanh Thu & Đối Soát', to: { name: 'seller-finance' } }
    ]
  },
  {
    title: 'Dữ Liệu',
    icon: ICON('<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>'),
    items: [
      { label: 'Tổng Quan Shop', to: { name: 'seller-dashboard' } },
      { label: 'Phân Tích Doanh Thu', to: { name: 'seller-analytics-revenue' } },
      { label: 'Sản Phẩm & Tồn Kho', to: { name: 'seller-analytics-products' } },
      { label: 'Lượt Xem & Chuyển Đổi', to: { name: 'seller-analytics-traffic' } },
      { label: 'Đánh Giá Khách Hàng', to: { name: 'seller-analytics-reviews' } }
    ]
  },
  {
    title: 'Quản Lý Shop',
    icon: ICON('<path d="M4 9 5.5 4h13L20 9M4 9h16v11H4zM4 9c0 2 1.5 3 3 3s3-1 3-3c0 2 1.5 3 2 3s3-1 3-3c0 2 1.5 3 3 3s2-1 2-3"/>'),
    items: [
      { label: 'Hồ Sơ Shop', to: { name: 'seller-shop' } },
      { label: 'Bảo Mật Tài Khoản', to: { name: 'seller-security' } },
      { label: 'Chính Sách Shop', to: { name: 'seller-policies' } },
      { label: 'Xác Minh Thông Tin', to: { name: 'seller-verification' } }
    ]
  }
]

// an item is active when route name AND its query match exactly, so
// "Tất cả" isn't highlighted together with "Chờ xác nhận"
function isActive(to) {
  if (route.name !== to.name) return false
  const want = to.query || {}
  const have = route.query
  const keys = new Set([...Object.keys(want), ...Object.keys(have).filter(k => ['status', 'ai', 'answered', 'tab'].includes(k))])
  return [...keys].every(k => (want[k] ?? null) === (have[k] ?? null))
}

// collapsed groups are remembered per browser (nice-to-have, safe if storage is blocked)
const STORAGE_KEY = 'seller_sidebar_collapsed'
const collapsed = ref(readCollapsed())
function readCollapsed() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {} } catch { return {} }
}
function toggleGroup(title) {
  collapsed.value = { ...collapsed.value, [title]: !collapsed.value[title] }
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(collapsed.value)) } catch { /* ignore */ }
}

function onLogout() {
  auth.logout()
  router.push({ name: 'seller-login' })
}

function onClickOutside(e) {
  if (menuOpen.value && accountEl.value && !accountEl.value.contains(e.target)) menuOpen.value = false
}
onMounted(() => {
  document.addEventListener('click', onClickOutside)
  loadEngagement()
  loadShopState()
  engagementTimer = setInterval(() => { if (!document.hidden) loadEngagement() }, 30000)
})
// answering on the Q&A / review pages updates the badges on the next navigation
const stopAfterEach = router.afterEach(() => loadEngagement())
onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside)
  clearInterval(engagementTimer)
  stopAfterEach()
})
</script>

<style scoped>
.sc { min-height: 100vh; background: #f6f6f6; }

/* ---- header ---- */
.sc-header {
  position: sticky; top: 0; z-index: 50;
  height: 64px;
  display: flex; align-items: center; gap: 12px;
  padding: 0 24px;
  background: var(--color-surface);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.brand { display: flex; align-items: center; gap: 8px; }
.logo-mark {
  width: 32px; height: 32px; border-radius: 7px;
  background: var(--color-primary); color: white;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 18px;
}
.logo-text { color: var(--color-primary); font-weight: 700; font-size: 21px; }
.brand-sub { font-size: 19px; color: var(--color-text); margin-left: 2px; white-space: nowrap; }

.header-right { margin-left: auto; display: flex; align-items: center; gap: 6px; }
.icon-btn {
  width: 40px; height: 40px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #555; background: none; border: none; cursor: pointer;
}
.icon-btn:hover { background: #f3f3f3; }
.badge-host { position: relative; }
.icon-badge {
  position: absolute; top: 2px; right: 0;
  min-width: 18px; height: 18px; padding: 0 4px; border-radius: 999px;
  background: var(--color-primary); color: white; font-size: 11px; font-weight: 700; line-height: 18px; text-align: center;
}
.menu-toggle { display: none; }
.divider { width: 1px; height: 28px; background: #e5e5e5; margin: 0 10px; }

.account { position: relative; display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 6px 8px; border-radius: 4px; }
.account:hover { background: #f7f7f7; }
.avatar {
  width: 34px; height: 34px; border-radius: 50%;
  background: #5b7083; color: white;
  display: flex; align-items: center; justify-content: center;
  font-weight: 600;
}
.avatar.lg { width: 44px; height: 44px; font-size: 18px; }
.account-name { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 15px; }
.account-menu {
  position: absolute; right: 0; top: calc(100% + 6px);
  width: 240px;
  background: var(--color-surface);
  border-radius: 4px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.14);
  padding: 8px 0;
  cursor: default;
}
.menu-head { display: flex; align-items: center; gap: 12px; padding: 10px 16px 14px; border-bottom: 1px solid var(--color-border); margin-bottom: 6px; }
.menu-name { font-weight: 600; }
.menu-role { font-size: 12px; color: var(--color-text-muted); }
.account-menu a { display: block; padding: 10px 16px; font-size: 14px; }
.account-menu a:hover { background: #f7f7f7; color: var(--color-primary); }

/* ---- body: sidebar + main ---- */
.sc-body { display: flex; align-items: flex-start; }
.sidebar {
  position: sticky; top: 64px;
  width: 232px; flex-shrink: 0;
  height: calc(100vh - 64px);
  overflow-y: auto;
  background: var(--color-surface);
  padding: 12px 0 24px;
  box-shadow: 1px 0 3px rgba(0, 0, 0, 0.04);
}
.nav-group { margin-bottom: 8px; }
.group-title {
  width: 100%;
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px 8px 20px;
  background: none; border: none; cursor: pointer;
  font-size: 15px; font-weight: 600; color: #8a8a8a; text-align: left;
}
.group-icon { display: flex; }
.chev { margin-left: auto; transition: transform 0.15s; }
.chev.collapsed { transform: rotate(180deg); }
.nav-group ul { list-style: none; margin: 0; padding: 0; }
.nav-group li a {
  display: block;
  padding: 8px 16px 8px 44px;
  font-size: 14px; color: var(--color-text); line-height: 1.4;
}
.nav-group li a:hover { color: var(--color-primary); }
.nav-group li a.active { color: var(--color-primary); font-weight: 500; background: var(--color-primary-light); }
.nav-badge { display: inline-block; margin-left: 6px; background: var(--color-primary); color: white; font-size: 11px; border-radius: 999px; padding: 0 6px; line-height: 17px; font-weight: 500; }

.sc-main { flex: 1; min-width: 0; padding: 20px 24px 40px; }
.shop-banner { border-radius: 6px; padding: 10px 14px; margin-bottom: 16px; font-size: 14px; line-height: 1.6; }
.shop-banner.PENDING { background: #fff8e6; color: #8a5a00; border: 1px solid #ffe2a8; }
.shop-banner.REJECTED { background: #fff4e5; color: #b35c00; border: 1px solid #ffd3a3; }
.shop-banner.SUSPENDED { background: #fdecea; color: #b42318; border: 1px solid #f5c2bd; }
.shop-banner a { color: #2673dd; text-decoration: underline; }
.banner-btn { background: var(--color-primary); color: white; border: none; border-radius: 4px; padding: 3px 10px; font-size: 13px; cursor: pointer; margin-left: 4px; }
.banner-err { color: #d33; margin-left: 8px; }
.sidebar-backdrop { display: none; }

@media (max-width: 900px) {
  .menu-toggle { display: flex; }
  .sc-header { padding: 0 12px; }
  .sidebar {
    position: fixed; left: 0; top: 64px; z-index: 40;
    transform: translateX(-100%); transition: transform 0.2s;
  }
  .sidebar.open { transform: none; }
  .sidebar-backdrop { display: block; position: fixed; inset: 64px 0 0; background: rgba(0, 0, 0, 0.3); z-index: 30; }
  .sc-main { padding: 16px; }
}
@media (max-width: 700px) {
  .brand-sub { display: none; }
}
@media (max-width: 560px) {
  .account-name, .divider { display: none; }
}
</style>
