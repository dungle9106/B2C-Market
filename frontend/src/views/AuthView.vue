<template>
  <div class="auth-page">
    <AuthHeader :title="isLogin ? 'Đăng nhập' : 'Đăng ký'" />

    <main class="auth-main auth-banner">
      <div class="container auth-main-inner">
        <!-- left promo panel (hidden on small screens) -->
        <section class="promo">
          <div class="promo-brand">
            <span class="logo-mark">S</span>
            <span>Shoppi</span>
          </div>
          <h1 class="promo-title">Sàn thương mại<br />đa người bán</h1>
          <p class="promo-sub">Mua sắm thông minh với trợ lý AI tư vấn</p>
          <ul class="promo-perks">
            <li><strong>Freeship</strong><span>đơn từ 0đ</span></li>
            <li><strong>Voucher</strong><span>giảm đến 50%</span></li>
            <li><strong>Trợ lý AI</strong><span>tư vấn 24/7</span></li>
          </ul>
        </section>

        <section class="auth-card">
          <h2 class="card-title">{{ isLogin ? 'Đăng nhập' : 'Đăng ký' }}</h2>

          <TwoFactorStep v-if="challenge" :challenge="challenge" :email="form.email" @done="afterLogin" @cancel="challenge = null" />
          <form v-else novalidate @submit.prevent="onSubmit">
            <template v-if="isLogin">
              <input v-model.trim="form.email" type="email" placeholder="Email" autocomplete="username" />
              <PasswordInput v-model="form.password" />
              <router-link :to="{ name: 'forgot-password' }" class="forgot">Quên mật khẩu?</router-link>
            </template>

            <template v-else>
              <input v-model.trim="form.fullName" placeholder="Họ và tên" autocomplete="name" />
              <input v-model.trim="form.email" type="email" placeholder="Email" autocomplete="email" />
              <input v-model.trim="form.phone" type="tel" placeholder="Số điện thoại (tùy chọn)" autocomplete="tel" />
              <PasswordInput v-model="form.password" placeholder="Mật khẩu (ít nhất 8 ký tự, gồm chữ và số)" autocomplete="new-password" />
              <PasswordInput v-model="form.confirmPassword" placeholder="Nhập lại mật khẩu" autocomplete="new-password" />
            </template>

            <p v-if="error" class="error">{{ error }}</p>

            <button class="btn block lg submit" type="submit" :disabled="!canSubmit || loading">
              {{ loading ? 'ĐANG XỬ LÝ...' : (isLogin ? 'ĐĂNG NHẬP' : 'ĐĂNG KÝ') }}
            </button>
            <p v-if="!isLogin" class="terms">
              Bằng việc đăng ký, bạn đồng ý với <router-link to="/policies/terms" target="_blank" class="text-primary">Điều khoản sử dụng</router-link>
              và <router-link to="/policies/privacy" target="_blank" class="text-primary">Chính sách bảo mật</router-link> của Shoppi.
            </p>
          </form>

          <div class="or-divider"><span>HOẶC</span></div>

          <GoogleLoginButton
            :label="isLogin ? 'Đăng nhập với Google' : 'Đăng ký với Google'"
            :redirect="route.query.redirect || null"
          />

          <p class="terms">
            Bằng việc {{ isLogin ? 'đăng nhập' : 'đăng ký' }}, bạn đồng ý với Shoppi về
            <span class="text-primary">Điều khoản dịch vụ</span> &amp;
            <span class="text-primary">Chính sách bảo mật</span>
          </p>

          <p class="switch">
            <template v-if="isLogin">
              Bạn mới biết đến Shoppi?
              <router-link :to="{ name: 'register', query: redirectQuery }" class="text-primary">Đăng ký</router-link>
            </template>
            <template v-else>
              Bạn đã có tài khoản?
              <router-link :to="{ name: 'login', query: redirectQuery }" class="text-primary">Đăng nhập</router-link>
            </template>
          </p>

          <p class="seller-hint">
            Bạn muốn kinh doanh?
            <router-link :to="{ name: 'seller-register' }" class="text-primary"><strong>Đăng ký bán hàng trên Shoppi</strong></router-link>
          </p>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AuthHeader from '../components/auth/AuthHeader.vue'
import PasswordInput from '../components/auth/PasswordInput.vue'
import GoogleLoginButton from '../components/auth/GoogleLoginButton.vue'
import TwoFactorStep from '../components/auth/TwoFactorStep.vue'
import { validateLogin, validateRegister, extractError, homeForRole } from '../utils/authForm'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isLogin = computed(() => route.meta.mode === 'login')
const redirectQuery = computed(() => (route.query.redirect ? { redirect: route.query.redirect } : {}))
const loading = ref(false)
// OAuth2RedirectView sends failed Google logins back here with ?error=...
const error = ref(route.query.error || '')

const emptyForm = () => ({ email: '', password: '', confirmPassword: '', fullName: '', phone: '' })
const form = ref(emptyForm())
// set when the account uses two-step verification: the form is replaced by the code step
const challenge = ref(route.query.challenge || null)

// like Shopee, the submit button stays disabled until the required fields are filled
const canSubmit = computed(() => {
  const f = form.value
  if (isLogin.value) return f.email && f.password
  return f.fullName && f.email && f.password && f.confirmPassword
})

watch(() => route.meta.mode, () => {
  error.value = ''
  form.value = emptyForm()
})

function afterLogin() {
  router.push(route.query.redirect || homeForRole(auth))
}

async function onSubmit() {
  error.value = isLogin.value ? validateLogin(form.value) : validateRegister(form.value)
  if (error.value) return

  loading.value = true
  try {
    if (isLogin.value) {
      const r = await auth.login({ email: form.value.email, password: form.value.password })
      if (r.twoFactorRequired) {
        challenge.value = r.challenge
        return
      }
    } else {
      const { confirmPassword, ...payload } = form.value
      await auth.register({ ...payload, phone: payload.phone || null })
    }
    afterLogin()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.forgot { display: block; text-align: right; font-size: 13px; color: #2673dd; margin: -4px 0 8px; }
.terms { font-size: 12px; color: var(--color-text-muted); text-align: center; margin: 10px 0 0; line-height: 1.5; }
.auth-banner { background: linear-gradient(135deg, var(--color-primary) 0%, #d0011b 100%); }

.promo { color: white; flex: 1; text-align: center; }
.promo-brand { display: inline-flex; align-items: center; gap: 10px; font-size: 26px; font-weight: 700; }
.logo-mark {
  width: 38px; height: 38px; border-radius: 8px;
  background: white; color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 20px;
}
.promo-title { font-size: 48px; line-height: 1.15; font-weight: 800; margin: 20px 0 12px; text-transform: uppercase; }
.promo-sub { font-size: 18px; opacity: 0.92; margin: 0 0 28px; }
.promo-perks { list-style: none; padding: 0; margin: 0; display: inline-flex; gap: 12px; flex-wrap: wrap; justify-content: center; }
.promo-perks li {
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: var(--radius-md);
  padding: 10px 18px;
  display: flex; flex-direction: column; min-width: 120px;
}
.promo-perks strong { font-size: 17px; color: #ffe58a; }
.promo-perks span { font-size: 13px; opacity: 0.9; }

.seller-hint { text-align: center; font-size: 12px; margin: 10px 0 0; color: var(--color-text-muted); }

@media (max-width: 900px) {
  .promo { display: none; }
}
</style>
