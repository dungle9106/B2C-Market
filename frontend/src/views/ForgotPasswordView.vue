<template>
  <div class="auth-page">
    <AuthHeader title="Quên mật khẩu" />
    <main class="auth-main simple">
      <section class="auth-card">
        <ol class="steps">
          <li v-for="(s, i) in STEPS" :key="s" :class="{ on: i <= step }">{{ s }}</li>
        </ol>

        <!-- 1. e-mail -->
        <template v-if="step === 0">
          <h2 class="card-title">Đặt lại mật khẩu</h2>
          <p class="hint">Nhập email đã đăng ký. Shoppi sẽ gửi <b>mã xác minh 6 số</b> (hiệu lực 30 phút). Tài khoản đăng ký bằng Google, Facebook hoặc GitHub cũng dùng cách này để tạo mật khẩu.</p>
          <form novalidate @submit.prevent="send">
            <input v-model.trim="email" type="email" placeholder="Email" autocomplete="email" />
            <p v-if="error" class="error">{{ error }}</p>
            <button class="btn block lg" type="submit" :disabled="!email || loading">{{ loading ? 'ĐANG GỬI...' : 'GỬI MÃ XÁC MINH' }}</button>
          </form>
        </template>

        <!-- 2. the code from the e-mail -->
        <template v-else-if="step === 1">
          <h2 class="card-title">Nhập mã xác minh</h2>
          <p class="hint">Nếu <b>{{ email }}</b> có tài khoản Shoppi, mã 6 số đã được gửi tới hộp thư. Hãy kiểm tra cả thư rác.</p>
          <form novalidate @submit.prevent="verify">
            <input ref="codeInput" v-model="code" class="code" inputmode="numeric" maxlength="6" placeholder="••••••"
                   autocomplete="one-time-code" @input="code = code.replace(/\D/g, '')" />
            <p v-if="error" class="error">{{ error }}</p>
            <button class="btn block lg" type="submit" :disabled="code.length !== 6 || loading">{{ loading ? 'ĐANG KIỂM TRA...' : 'XÁC NHẬN' }}</button>
          </form>
          <p class="resend">
            <button type="button" class="link" :disabled="wait > 0 || loading" @click="send">
              {{ wait > 0 ? `Gửi lại mã sau ${wait}s` : 'Gửi lại mã' }}
            </button>
            · <button type="button" class="link" @click="step = 0; error = ''">Đổi email</button>
          </p>
        </template>

        <!-- 3. new password -->
        <template v-else-if="step === 2">
          <h2 class="card-title">Đặt mật khẩu mới</h2>
          <p class="hint">Mật khẩu tối thiểu 8 ký tự, gồm cả chữ và số. Sau khi đổi, tài khoản sẽ được đăng xuất khỏi mọi thiết bị.</p>
          <form novalidate @submit.prevent="save">
            <PasswordInput v-model="password" placeholder="Mật khẩu mới" autocomplete="new-password" />
            <PasswordInput v-model="confirm" placeholder="Nhập lại mật khẩu mới" autocomplete="new-password" />
            <p v-if="error" class="error">{{ error }}</p>
            <button class="btn block lg" type="submit" :disabled="!password || !confirm || loading">{{ loading ? 'ĐANG LƯU...' : 'ĐẶT MẬT KHẨU' }}</button>
          </form>
        </template>

        <div v-else class="done">
          <div class="icon">✅</div>
          <h2 class="card-title">Đã đặt mật khẩu mới</h2>
          <p class="hint">Bạn có thể đăng nhập bằng email và mật khẩu vừa tạo.</p>
          <router-link :to="{ name: loginRoute }" class="btn block lg">ĐĂNG NHẬP</router-link>
        </div>

        <p v-if="step < 3" class="back"><router-link :to="{ name: loginRoute }">‹ Quay lại đăng nhập</router-link></p>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import AuthHeader from '../components/auth/AuthHeader.vue'
import PasswordInput from '../components/auth/PasswordInput.vue'
import { forgotPassword, verifyResetCode, resetPassword } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { extractError } from '../utils/apiError'
import { passwordProblem } from '../utils/authForm'

const STEPS = ['Email', 'Mã xác minh', 'Mật khẩu mới']
const route = useRoute()
const loginRoute = computed(() => (route.query.from === 'seller' ? 'seller-login' : 'login'))
const step = ref(0)
const email = ref('')
const code = ref('')
const resetToken = ref('')
const password = ref('')
const confirm = ref('')
const loading = ref(false)
const error = ref('')
const wait = ref(0)
const codeInput = ref(null)
let timer = null

async function run(fn) {
  loading.value = true
  error.value = ''
  try {
    await fn()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

function countdown() {
  wait.value = 60
  clearInterval(timer)
  timer = setInterval(() => { if (--wait.value <= 0) clearInterval(timer) }, 1000)
}

function send() {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)) return (error.value = 'Email không hợp lệ')
  return run(async () => {
    await forgotPassword(email.value)
    code.value = ''
    step.value = 1
    countdown()
    await nextTick()
    codeInput.value?.focus()
  })
}

function verify() {
  return run(async () => {
    const { data } = await verifyResetCode(email.value, code.value)
    resetToken.value = data.resetToken
    step.value = 2
  })
}

function save() {
  error.value = passwordProblem(password.value) || (password.value !== confirm.value ? 'Mật khẩu nhập lại không khớp' : '')
  if (error.value) return
  return run(async () => {
    await resetPassword(resetToken.value, password.value)
    useAuthStore().logout(false)   // every session was ended by the reset
    step.value = 3
  })
}

onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.simple { display: flex; justify-content: center; padding: 48px 16px; background: #f5f5f5; min-height: calc(var(--vh) - 84px); }
.auth-card { max-width: 420px; width: 100%; }
.steps { display: flex; gap: 6px; list-style: none; padding: 0; margin: 0 0 18px; counter-reset: s; }
.steps li { flex: 1; font-size: 12px; color: var(--color-text-muted); border-top: 3px solid var(--color-border); padding-top: 6px; counter-increment: s; }
.steps li::before { content: counter(s) '. '; }
.steps li.on { color: var(--color-primary); border-color: var(--color-primary); }
.hint { font-size: 14px; color: var(--color-text-muted); line-height: 1.6; }
.code { text-align: center; font-size: 26px; letter-spacing: 10px; font-weight: 600; }
.resend { text-align: center; font-size: 13px; color: var(--color-text-muted); margin: 12px 0 0; }
.link { background: none; border: 0; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.link:disabled { color: var(--color-text-muted); cursor: default; }
.done { text-align: center; }
.icon { font-size: 40px; }
.back { text-align: center; margin: 16px 0 0; font-size: 14px; }
.back a { color: #2673dd; }
.error { color: #d33; font-size: 13px; }
</style>
