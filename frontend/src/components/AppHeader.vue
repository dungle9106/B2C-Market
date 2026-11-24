<template>
  <header class="shopee-header">
    <!-- top thin bar: account links, like real Shopee -->
    <div class="top-bar">
      <div class="container top-bar-inner">
        <div class="top-links">
          <router-link v-if="auth.isSeller" to="/seller">Kênh Người Bán</router-link>
          <router-link v-else-if="auth.isBuyer" to="/seller/register">Trở Thành Người Bán</router-link>
          <router-link v-else-if="!auth.isLoggedIn" to="/seller/login">Kênh Người Bán</router-link>
          <router-link v-if="auth.isAdmin" to="/admin">Quản Trị</router-link>
          <router-link v-if="auth.isBuyer" to="/chat">Trợ lý AI</router-link>
          <router-link v-if="auth.isAdmin" to="/admin/support">Yêu cầu hỗ trợ</router-link>
        </div>
        <div class="top-links right">
          <router-link to="/help">Hỗ Trợ</router-link>
          <template v-if="auth.isLoggedIn">
            <router-link to="/account/security" :title="auth.emailVerified ? 'Bảo mật tài khoản' : 'Email chưa được xác thực'">
              {{ auth.fullName }}<span v-if="!auth.emailVerified && !auth.isAdmin" class="unverified">!</span>
            </router-link>
            <a href="#" @click.prevent="onLogout">Đăng xuất</a>
          </template>
          <template v-else>
            <router-link to="/register">Đăng Ký</router-link>
            <span class="divider">|</span>
            <router-link to="/login">Đăng Nhập</router-link>
          </template>
        </div>
      </div>
    </div>

    <!-- main bar: logo + big search + cart -->
    <div class="main-bar">
      <div class="container main-bar-inner">
        <router-link to="/" class="logo">
          <span class="logo-mark">S</span>
          <span class="logo-text">Shoppi</span>
        </router-link>

        <div class="search-col">
          <SearchBox :hints="hot.map(k => k.keyword)" />
          <nav v-if="hot.length" class="hot-words" aria-label="Từ khóa hot">
            <router-link v-for="k in hot.slice(0, 8)" :key="k.keyword" :to="{ name: 'search', query: { q: k.keyword } }">{{ k.keyword }}</router-link>
          </nav>
        </div>

        <div v-if="auth.isLoggedIn" class="header-icons">
          <router-link v-if="auth.isBuyer || auth.isSeller" :to="auth.isSeller ? '/seller/chat' : '/messages'" class="chat-icon" title="Tin nhắn">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.5-4.9A8 8 0 1 1 21 12Z" stroke="white" stroke-width="1.8" stroke-linejoin="round" /><path d="M8.5 12h.01M12 12h.01M15.5 12h.01" stroke="white" stroke-width="2.4" stroke-linecap="round" /></svg>
            <span v-if="notifications.chat > 0" class="cart-badge">{{ notifications.chat > 99 ? '99+' : notifications.chat }}</span>
          </router-link>
          <NotificationBell tone="light" />
        </div>

        <div v-if="auth.isBuyer" class="cart-wrap">
        <router-link to="/cart" class="cart-icon">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none"><path d="M3 3H5L5.4 5M7 13H17L21 5H5.4M7 13L5.4 5M7 13L4.7 15.3C4.3 15.7 4.6 16.5 5.2 16.5H17M17 16.5C15.9 16.5 15 17.4 15 18.5C15 19.6 15.9 20.5 17 20.5C18.1 20.5 19 19.6 19 18.5C19 17.4 18.1 16.5 17 16.5ZM9 18.5C9 19.6 8.1 20.5 7 20.5C5.9 20.5 5 19.6 5 18.5C5 17.4 5.9 16.5 7 16.5C8.1 16.5 9 17.4 9 18.5Z" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
          <span v-if="cart.count > 0" class="cart-badge">{{ cart.count }}</span>
        </router-link>
        <!-- like Shopee: hover the cart to see what was added last -->
        <div class="cart-pop">
          <template v-if="cart.items.length">
            <div class="pop-title">Sản phẩm mới thêm</div>
            <router-link v-for="i in recentCart" :key="i.id" :to="`/products/${i.productId}`" class="pop-item">
              <img :src="i.thumbnailUrl || placeholder" alt="" />
              <span class="pop-name">{{ i.productName }}<small v-if="i.variantName">{{ i.variantName }}</small></span>
              <b>{{ formatVnd(i.unitPrice) }}</b>
            </router-link>
            <div class="pop-foot">
              <span>{{ cart.items.length > recentCart.length ? `${cart.items.length - recentCart.length} sản phẩm khác trong giỏ` : '' }}</span>
              <router-link to="/cart" class="btn sm">Xem giỏ hàng</router-link>
            </div>
          </template>
          <div v-else class="pop-empty">Chưa có sản phẩm trong giỏ hàng</div>
        </div>
        </div>
      </div>
    </div>

    <!-- category strip -->
    <div class="category-strip">
      <div class="container strip-inner">
        <router-link to="/orders" v-if="auth.isBuyer">Đơn hàng của tôi</router-link>
        <router-link to="/account/vouchers" v-if="auth.isBuyer">🎟 Kho Voucher</router-link>
        <router-link to="/account/coins" v-if="auth.isBuyer">🪙 Shoppi Xu</router-link>
        <router-link to="/account/wallet" v-if="auth.isBuyer">💳 ShoppiPay</router-link>
        <router-link to="/account/likes" v-if="auth.isBuyer">♥ Yêu thích</router-link>
        <router-link to="/account/following" v-if="auth.isBuyer">Shop theo dõi</router-link>
        <router-link to="/search?q=">Tất cả sản phẩm</router-link>
        <router-link to="/search?mall=true">Shoppi Mall</router-link>
        <router-link to="/videos">▶ Shoppi Video</router-link>
        <span class="tag">Freeship</span>
        <span class="tag">Voucher giảm giá</span>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { formatVnd } from '../utils/format'
import { loadHotKeywords } from '../utils/trending'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import { useNotificationStore } from '../stores/notifications'
import SearchBox from './SearchBox.vue'
import NotificationBell from './NotificationBell.vue'

const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const notifications = useNotificationStore()
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="42" height="42"><rect width="42" height="42" fill="%23f0f0f0"/></svg>'

const hot = ref([])
onMounted(() => { loadHotKeywords().then(list => { hot.value = list }) })

// the latest additions first, as in the cart preview of big marketplaces
const recentCart = computed(() => [...cart.items].sort((a, b) => b.id - a.id).slice(0, 5))

if (auth.isBuyer) {
  cart.fetchCart().catch(() => {})
}

function onLogout() {
  auth.logout()
  router.push('/')
}
</script>

<style scoped>
.unverified { display: inline-flex; width: 14px; height: 14px; border-radius: 50%; background: #ffe97a; color: #d0011b; font-size: 10px; font-weight: 800;
  align-items: center; justify-content: center; margin-left: 4px; vertical-align: 1px; }
.shopee-header {
  background: linear-gradient(180deg, var(--color-primary), #f9603d);
  position: sticky;
  top: 0;
  z-index: 100;
}

.top-bar { border-bottom: 1px solid rgba(255,255,255,0.2); }
.top-bar-inner {
  display: flex;
  justify-content: space-between;
  padding: 6px 16px;
  font-size: 12px;
}
.top-links { display: flex; gap: 14px; align-items: center; }
.top-links a, .top-links span { color: rgba(255,255,255,0.9); }
.top-links a:hover { opacity: 0.85; }
.divider { opacity: 0.5; }

.main-bar-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 14px 16px 10px;
}

.search-col { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.search-col :deep(.search-form) { flex: none; }
.hot-words { display: flex; gap: 12px; overflow: hidden; height: 16px; font-size: 12px; }
.hot-words a { color: rgba(255, 255, 255, 0.9); white-space: nowrap; }
.hot-words a:hover { color: white; text-decoration: underline; }
.cart-wrap { position: relative; flex-shrink: 0; }
.cart-pop { display: none; position: absolute; right: -8px; top: 100%; margin-top: 8px; width: 400px; background: white; border-radius: 3px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.18); z-index: 200; font-size: 14px; }
.cart-pop::before { content: ''; position: absolute; right: 14px; top: -8px; border: 8px solid transparent; border-top: 0; border-bottom-color: white; }
.cart-pop::after { content: ''; position: absolute; left: 0; right: 0; top: -12px; height: 12px; }
.cart-wrap:hover .cart-pop { display: block; }
.pop-title { padding: 10px 12px; color: var(--color-text-faint); font-size: 13px; }
.pop-item { display: flex; align-items: center; gap: 10px; padding: 8px 12px; color: var(--color-text); }
.pop-item:hover { background: #f8f8f8; }
.pop-item img { width: 42px; height: 42px; object-fit: cover; border: 1px solid rgba(0, 0, 0, 0.06); flex-shrink: 0; }
.pop-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pop-name small { display: block; color: var(--color-text-faint); font-size: 12px; }
.pop-item b { color: var(--color-primary); font-weight: 400; flex-shrink: 0; }
.pop-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 12px; background: #fafafa; font-size: 12px;
  color: var(--color-text-muted); }
.pop-empty { padding: 32px 12px; text-align: center; color: var(--color-text-faint); }
@media (max-width: 760px) {
  .hot-words, .cart-pop { display: none !important; }
  /* one row that scrolls sideways instead of wrapping over two lines */
  .strip-inner { flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; }
  .strip-inner > * { white-space: nowrap; flex-shrink: 0; }
}
.logo { display: flex; align-items: center; gap: 8px; color: white; flex-shrink: 0; }
.logo-mark {
  width: 34px; height: 34px; border-radius: 8px;
  background: white; color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 18px;
}
.logo-text { font-weight: 700; font-size: 19px; }


.header-icons { display: flex; align-items: center; gap: 2px; flex-shrink: 0; margin-right: -8px; }
.chat-icon { position: relative; display: flex; padding: 8px; }
.cart-icon { position: relative; padding: 6px; flex-shrink: 0; }
.cart-badge {
  position: absolute;
  top: -2px; right: -2px;
  background: white;
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 700;
  border-radius: 999px;
  min-width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 3px;
}

.category-strip { background: transparent; }
.strip-inner {
  display: flex;
  gap: 20px;
  padding: 4px 16px 10px;
  font-size: 13px;
}
.strip-inner a, .strip-inner .tag { color: rgba(255,255,255,0.92); }
.strip-inner a:hover { text-decoration: underline; }
</style>
