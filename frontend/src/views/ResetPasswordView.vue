<template>
  <div class="auth-page">
    <AuthHeader title="Đặt mật khẩu mới" />
    <main class="auth-main simple">
      <section class="auth-card">
        <template v-if="!done">
          <h2 class="card-title">Đặt mật khẩu mới</h2>
          <p class="hint">Mật khẩu tối thiểu 8 ký tự, gồm cả chữ và số. Sau khi đổi, tài khoản sẽ được đăng xuất khỏi mọi thiết bị.</p>
          <form novalidate @submit.prevent="submit">
            <PasswordInput v-model="password" placeholder="Mật khẩu mới" autocomplete="new-password" />
            <PasswordInput v-model="confirm" placeholder="Nhập lại mật khẩu mới" autocomplete="new-password" />
            <p v-if="error" class="error">{{ error }}</p>
            <button class="btn block lg" type="submit" :disabled="!password || !confirm || loading || !token">{{ loading ? 'ĐANG LƯU...' : 'ĐẶT MẬT KHẨU' }}</button>
          </form>
          <p v-if="!token" class="error">Liên kết không hợp lệ.</p>
        </template>
        <div v-else class="ok">
          <div class="icon">✅</div>
          <h2 class="card-title">Đã đặt mật khẩu mới</h2>
          <router-link :to="{ name: 'login' }" class="btn block lg">ĐĂNG NHẬP</router-link>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import AuthHeader from '../components/auth/AuthHeader.vue'
import PasswordInput from '../components/auth/PasswordInput.vue'
import { resetPassword } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { extractError } from '../utils/apiError'
import { passwordProblem } from '../utils/authForm'

const route = useRoute()
const token = route.query.token
const password = ref('')
const confirm = ref('')
const done = ref(false)
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = passwordProblem(password.value) || (password.value !== confirm.value ? 'Mật khẩu nhập lại không khớp' : '')
  if (error.value) return
  loading.value = true
  try {
    await resetPassword(token, password.value)
    useAuthStore().logout(false)   // every session was ended by the reset
    done.value = true
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.simple { display: flex; justify-content: center; padding: 48px 16px; background: #f5f5f5; min-height: calc(100vh - 84px); }
.auth-card { max-width: 420px; width: 100%; }
.hint { font-size: 14px; color: var(--color-text-muted); line-height: 1.6; }
.ok { text-align: center; }
.icon { font-size: 40px; }
.error { color: #d33; font-size: 13px; }
</style>
