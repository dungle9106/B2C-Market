<template>
  <div class="auth-page">
    <AuthHeader title="Xác thực email" />
    <main class="auth-main simple">
      <section class="auth-card center">
        <template v-if="state === 'loading'"><div class="icon">⏳</div><p>Đang xác thực...</p></template>
        <template v-else-if="state === 'ok'">
          <div class="icon">✅</div>
          <h2 class="card-title">Email đã được xác thực</h2>
          <p class="hint">{{ email }}</p>
          <router-link to="/" class="btn block lg">TIẾP TỤC MUA SẮM</router-link>
        </template>
        <template v-else>
          <div class="icon">⚠️</div>
          <h2 class="card-title">Không xác thực được</h2>
          <p class="hint">{{ error }}</p>
          <router-link v-if="auth.isLoggedIn" to="/account/security" class="btn block lg">GỬI LẠI EMAIL XÁC THỰC</router-link>
        </template>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AuthHeader from '../components/auth/AuthHeader.vue'
import { verifyEmail } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { extractError } from '../utils/apiError'

const route = useRoute()
const auth = useAuthStore()
const state = ref('loading')
const email = ref('')
const error = ref('')

onMounted(async () => {
  try {
    email.value = (await verifyEmail(route.query.token)).data.email
    state.value = 'ok'
    if (auth.isLoggedIn) auth.refreshMe().catch(() => {})
  } catch (e) {
    error.value = extractError(e)
    state.value = 'error'
  }
})
</script>

<style scoped>
.simple { display: flex; justify-content: center; padding: 48px 16px; background: #f5f5f5; min-height: calc(100vh - 84px); }
.auth-card { max-width: 420px; width: 100%; }
.center { text-align: center; }
.icon { font-size: 40px; }
.hint { font-size: 14px; color: var(--color-text-muted); }
</style>
