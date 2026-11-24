<template>
  <!-- phones only (CSS): the four tabs of the buyer app, like the big marketplaces' mobile web -->
  <nav v-if="visible" class="tabbar" aria-label="Điều hướng chính">
    <router-link to="/" class="tab" :class="{ on: route.name === 'home' }">
      <svg viewBox="0 0 24 24" fill="none"><path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1v-9.5Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>
      <span>Trang chủ</span>
    </router-link>
    <router-link to="/videos" class="tab" :class="{ on: route.name === 'videos' }">
      <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="14" rx="3" stroke="currentColor" stroke-width="1.7" /><path d="m10 9 5 3-5 3V9Z" fill="currentColor" /></svg>
      <span>Video</span>
    </router-link>
    <router-link :to="auth.isLoggedIn ? '/notifications' : { name: 'login', query: { redirect: '/notifications' } }" class="tab" :class="{ on: route.name === 'notifications' }">
      <svg viewBox="0 0 24 24" fill="none"><path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /><path d="M10 20.5a2 2 0 0 0 4 0" stroke="currentColor" stroke-width="1.7" /></svg>
      <span>Thông báo</span>
      <b v-if="auth.isLoggedIn && notifications.total > 0" class="badge">{{ notifications.total > 99 ? '99+' : notifications.total }}</b>
    </router-link>
    <router-link to="/me" class="tab" :class="{ on: route.name === 'me' }">
      <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.7" /><path d="M4 21a8 8 0 0 1 16 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>
      <span>Tôi</span>
    </router-link>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'

const route = useRoute()
const auth = useAuthStore()
const notifications = useNotificationStore()

// pages with their own bottom input / action, the sign-in pages and the seller / admin consoles have no tab bar
const HIDDEN = new Set(['login', 'register', 'forgot-password', 'reset-password', 'verify-email', 'oauth2-redirect',
  'checkout', 'payment', 'vnpay-return', 'chatbot', 'messages', 'wrong-account', 'seller-login', 'seller-register'])
const visible = computed(() => route.name && !HIDDEN.has(route.name)
  && !route.path.startsWith('/seller') && !route.path.startsWith('/admin'))

defineExpose({ visible })
</script>

<style scoped>
.tabbar { display: none; }
@media (max-width: 760px) {
  .tabbar {
    display: flex; position: fixed; left: 0; right: 0; bottom: 0; z-index: 800;
    background: white; border-top: 1px solid #eee; box-shadow: 0 -1px 6px rgba(0, 0, 0, 0.05);
    padding-bottom: env(safe-area-inset-bottom);
  }
  :global(body.has-tabbar) { padding-bottom: calc(56px + env(safe-area-inset-bottom)); }
}
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
  height: 56px; color: #666; font-size: 11px; position: relative; text-decoration: none; }
.tab svg { width: 24px; height: 24px; }
.tab.on { color: var(--color-primary); }
.badge { position: absolute; top: 4px; left: calc(50% + 4px); min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px;
  background: var(--color-primary); color: white; font-size: 10px; line-height: 16px; text-align: center; border: 1px solid white; }
</style>
