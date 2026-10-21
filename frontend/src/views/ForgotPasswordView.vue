<template>
  <div class="auth-page">
    <AuthHeader title="Quên mật khẩu" />
    <main class="auth-main simple">
      <section class="auth-card">
        <template v-if="!sent">
          <h2 class="card-title">Đặt lại mật khẩu</h2>
          <p class="hint">Nhập email đã đăng ký. Shoppi sẽ gửi liên kết đặt lại mật khẩu (hiệu lực 30 phút).</p>
          <form novalidate @submit.prevent="submit">
            <input v-model.trim="email" type="email" placeholder="Email" autocomplete="email" />
            <p v-if="error" class="error">{{ error }}</p>
            <button class="btn block lg" type="submit" :disabled="!email || loading">{{ loading ? 'ĐANG GỬI...' : 'GỬI LIÊN KẾT' }}</button>
          </form>
        </template>
        <div v-else class="done">
          <div class="icon">📧</div>
          <h2 class="card-title">Kiểm tra hộp thư</h2>
          <p class="hint">Nếu <b>{{ email }}</b> có tài khoản Shoppi, bạn sẽ nhận được email hướng dẫn đặt lại mật khẩu trong vài phút. Hãy kiểm tra cả thư rác.</p>
        </div>
        <p class="back"><router-link :to="{ name: 'login' }">‹ Quay lại đăng nhập</router-link></p>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AuthHeader from '../components/auth/AuthHeader.vue'
import { forgotPassword } from '../api/auth'
import { extractError } from '../utils/apiError'

const email = ref('')
const sent = ref(false)
const loading = ref(false)
const error = ref('')

async function submit() {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)) return (error.value = 'Email không hợp lệ')
  loading.value = true
  error.value = ''
  try {
    await forgotPassword(email.value)
    sent.value = true
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
.done { text-align: center; }
.icon { font-size: 40px; }
.back { text-align: center; margin: 16px 0 0; font-size: 14px; }
.back a { color: #2673dd; }
.error { color: #d33; font-size: 13px; }
</style>
