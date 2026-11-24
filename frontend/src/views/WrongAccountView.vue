<template>
  <div>
    <AppHeader />
    <div class="container page">
      <section class="shopee-section box">
        <div class="icon">🔒</div>
        <h1>Trang này dành cho {{ ROLE_NAMES[need] || 'tài khoản khác' }}</h1>
        <p>
          Tab này đang đăng nhập bằng <b>{{ auth.email || auth.fullName }}</b>
          <span class="role">{{ ROLE_NAMES[auth.role] || auth.role }}</span>
        </p>
        <p class="text-muted hint">
          Mỗi tab có phiên đăng nhập riêng: đổi tài khoản ở đây không làm các tab khác bị đăng xuất.
        </p>
        <div class="actions">
          <button type="button" class="btn" :disabled="busy" @click="switchAccount">
            {{ busy ? 'Đang đăng xuất...' : `Đăng nhập tài khoản ${ROLE_NAMES[need] || 'khác'} trong tab này` }}
          </button>
          <router-link to="/" class="btn secondary">Về trang chủ</router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { useAuthStore } from '../stores/auth'

const ROLE_NAMES = { ADMIN: 'quản trị viên', SELLER: 'người bán', BUYER: 'người mua' }

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const busy = ref(false)
const need = computed(() => route.query.need)

async function switchAccount() {
  busy.value = true
  await auth.signOutTab()
  busy.value = false
  const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : undefined
  router.replace({ name: need.value === 'SELLER' ? 'seller-login' : 'login', query: redirect ? { redirect } : {} })
}
</script>

<style scoped>
.page { padding-top: 40px; padding-bottom: 40px; }
.box { text-align: center; padding: 48px 24px; }
.icon { font-size: 48px; }
h1 { font-size: 20px; font-weight: 500; margin: 12px 0; }
.role { background: #f5f5f5; border-radius: 10px; padding: 1px 8px; margin-left: 6px; font-size: 13px; }
.hint { font-size: 13px; }
.actions { display: flex; gap: 10px; justify-content: center; margin-top: 20px; flex-wrap: wrap; }
</style>
