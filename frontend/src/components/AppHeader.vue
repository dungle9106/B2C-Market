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

        <SearchBox />

        <div v-if="auth.isLoggedIn" class="header-icons">
          <router-link v-if="auth.isBuyer || auth.isSeller" :to="auth.isSeller ? '/seller/chat' : '/messages'" class="chat-icon" title="Tin nhắn">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.5-4.9A8 8 0 1 1 21 12Z" stroke="white" stroke-width="1.8" stroke-linejoin="round" /><path d="M8.5 12h.01M12 12h.01M15.5 12h.01" stroke="white" stroke-width="2.4" stroke-linecap="round" /></svg>
            <span v-if="notifications.chat > 0" class="cart-badge">{{ notifications.chat > 99 ? '99+' : notifications.chat }}</span>
          </router-link>
          <NotificationBell tone="light" />
        </div>

        <router-link v-if="auth.isBuyer" to="/cart" class="cart-icon">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none"><path d="M3 3H5L5.4 5M7 13H17L21 5H5.4M7 13L5.4 5M7 13L4.7 15.3C4.3 15.7 4.6 16.5 5.2 16.5H17M17 16.5C15.9 16.5 15 17.4 15 18.5C15 19.6 15.9 20.5 17 20.5C18.1 20.5 19 19.6 19 18.5C19 17.4 18.1 16.5 17 16.5ZM9 18.5C9 19.6 8.1 20.5 7 20.5C5.9 20.5 5 19.6 5 18.5C5 17.4 5.9 16.5 7 16.5C8.1 16.5 9 17.4 9 18.5Z" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
          <span v-if="cart.count > 0" class="cart-badge">{{ cart.count }}</span>
        </router-link>
      </div>
    </div>

    <!-- category strip -->
    <div class="category-strip">
      <div class="container strip-inner">
        <router-link to="/orders" v-if="auth.isBuyer">Đơn hàng của tôi</router-link>
        <router-link to="/search?q=">Tất cả sản phẩm</router-link>
        <span class="tag">Freeship</span>
        <span class="tag">Voucher giảm giá</span>
      </div>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import { useNotificationStore } from '../stores/notifications'
import SearchBox from './SearchBox.vue'
import NotificationBell from './NotificationBell.vue'

const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const notifications = useNotificationStore()

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
