<template>
  <div class="auth-page">
    <AuthHeader :title="isLogin ? 'Kênh Người Bán' : 'Đăng ký'" />

    <main class="auth-main" :class="isLogin ? 'login-bg' : 'register-bg'">
      <!-- faint city skyline along the bottom of the signup page, like Shopee's -->
      <svg v-if="!isLogin" class="skyline" viewBox="0 0 1200 160" preserveAspectRatio="none" aria-hidden="true">
        <rect v-for="(b, i) in skyline" :key="i" :x="b[0]" :y="160 - b[1]" :width="b[2]" :height="b[1]" />
      </svg>

      <div class="container auth-main-inner">
        <!-- ===== left panel ===== -->
        <section v-if="isLogin" class="intro">
          <h1 class="intro-title">Bán hàng chuyên nghiệp</h1>
          <p class="intro-sub">
            Quản lý shop của bạn một cách hiệu quả hơn trên Shoppi với
            Shoppi - Kênh Người Bán
          </p>
          <!-- storefront + delivery truck illustration -->
          <svg class="illustration" viewBox="0 0 520 300" aria-hidden="true">
            <g fill="#dbeaf5"><ellipse cx="62" cy="58" rx="30" ry="18" /><ellipse cx="90" cy="64" rx="24" ry="14" /><ellipse cx="40" cy="66" rx="20" ry="12" /></g>
            <!-- truck, partly behind the shop -->
            <rect x="60" y="150" width="120" height="82" rx="6" fill="#2a9d8f" />
            <rect x="22" y="176" width="46" height="56" rx="6" fill="#43b3a5" />
            <rect x="30" y="184" width="26" height="20" rx="3" fill="#fff" />
            <g v-for="cx in [52, 150]" :key="cx"><circle :cx="cx" cy="236" r="17" fill="#333d47" /><circle :cx="cx" cy="236" r="7" fill="#fff" /></g>
            <!-- shop drawn as a monitor screen -->
            <rect x="160" y="96" width="220" height="150" rx="4" fill="#fdf7ea" stroke="#3a4754" stroke-width="7" />
            <rect x="252" y="246" width="36" height="22" fill="#3a4754" />
            <rect x="214" y="266" width="112" height="9" rx="4" fill="#3a4754" />
            <!-- awning -->
            <rect x="150" y="64" width="240" height="12" rx="2" fill="#e24a2f" />
            <g v-for="(s, i) in awning" :key="'a' + i">
              <rect :x="s.x" y="76" width="30" height="24" :fill="s.color" />
              <circle :cx="s.x + 15" cy="100" r="15" :fill="s.color" />
            </g>
            <!-- windows with a dress and a t-shirt -->
            <rect x="180" y="132" width="58" height="72" fill="#fff" stroke="#3a4754" stroke-width="5" />
            <path d="M203 146h12l-3 10 12 38h-30l12-38z" fill="#ee4d2d" />
            <rect x="302" y="132" width="58" height="72" fill="#fff" stroke="#3a4754" stroke-width="5" />
            <path d="M318 150l8-5h12l8 5 6 10-8 4-3-4v30h-26v-30l-3 4-8-4z" fill="#ee4d2d" />
            <!-- door -->
            <rect x="252" y="160" width="36" height="84" fill="#56636f" />
            <rect x="255" y="172" width="30" height="12" rx="2" fill="#ee4d2d" />
            <text x="270" y="181" text-anchor="middle" font-size="8" font-weight="700" fill="#fff">OPEN</text>
            <!-- trees -->
            <g v-for="t in trees" :key="t.x">
              <rect :x="t.x - 2" :y="t.y" width="4" :height="274 - t.y" fill="#5b4636" />
              <ellipse :cx="t.x" :cy="t.y" :rx="t.r" :ry="t.r * 1.5" :fill="t.color" />
            </g>
          </svg>
        </section>

        <section v-else class="intro">
          <p class="intro-brand">Shoppi Việt Nam</p>
          <h1 class="intro-title big">Trở thành Người bán<br />ngay hôm nay</h1>
          <ul class="perks">
            <li>
              <svg viewBox="0 0 40 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M5 15 8 6h24l3 9M5 15c0 3 2 5 5 5s5-2 5-5c0 3 2 5 5 5s5-2 5-5c0 3 2 5 5 5s5-2 5-5M5 15h30M8 20v14h24V20M16 34v-8h8v8" /></svg>
              <span>Sàn thương mại điện tử đa người bán, mở shop miễn phí</span>
            </li>
            <li>
              <svg viewBox="0 0 40 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M6 14h28v20H6zM4 8h32v6H4zM20 8v26M20 8c-2-4-8-5-8-1s8 1 8 1c2-4 8-5 8-1s-8 1-8 1" /></svg>
              <span>Trợ lý AI giúp viết mô tả sản phẩm hấp dẫn</span>
            </li>
            <li>
              <svg viewBox="0 0 40 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"><path d="m4 18 7-7 6 3 5-3 6 2 8 7M4 18l4 4M36 20l-4 4M8 22l6 6c1 1 3 1 4 0M12 20l7 7c1 1 3 1 4 0l1-1M17 17l8 8c1 1 3 1 4 0l3-4M22 11l-5 5c-1 1-3 1-4 0" /></svg>
              <span>Tiếp cận người mua trên toàn sàn Shoppi</span>
            </li>
          </ul>
        </section>

        <!-- ===== right: card ===== -->
        <div class="card-col">
          <section class="auth-card">
            <!-- ---------- LOGIN ---------- -->
            <template v-if="isLogin">
              <h2 class="card-title">Đăng nhập</h2>
              <TwoFactorStep v-if="challenge" :challenge="challenge" :email="account.email" @done="goToSellerArea" @cancel="challenge = null" />
              <form v-else novalidate @submit.prevent="onLogin">
                <input v-model.trim="account.email" type="email" placeholder="Email" autocomplete="username" />
                <PasswordInput v-model="account.password" />
                <p v-if="error" class="error">{{ error }}</p>
                <button class="btn block lg submit" type="submit" :disabled="!account.email || !account.password || loading">
                  {{ loading ? 'ĐANG XỬ LÝ...' : 'ĐĂNG NHẬP' }}
                </button>
              </form>
              <div class="or-divider"><span>HOẶC</span></div>
              <!-- a BUYER landing on /seller is sent on to the shop step by the router guard -->
              <GoogleLoginButton label="Đăng nhập với Google" :redirect="route.query.redirect || '/seller'" />
              <p class="terms">
                Bằng việc đăng nhập, bạn đồng ý với
                <span class="text-primary">Điều khoản dịch vụ</span> &amp;
                <span class="text-primary">Chính sách bảo mật</span> của Shoppi
              </p>
              <p class="switch">
                Bạn mới biết đến Shoppi?
                <router-link :to="{ name: 'seller-register' }" class="text-primary">Đăng ký</router-link>
              </p>
            </template>

            <!-- ---------- REGISTER, step 1: account ---------- -->
            <template v-else-if="step === 1">
              <h2 class="card-title">Đăng ký</h2>
              <Steps :current="1" />
              <form novalidate @submit.prevent="onCreateAccount">
                <input v-model.trim="account.fullName" placeholder="Họ và tên" autocomplete="name" />
                <input v-model.trim="account.email" type="email" placeholder="Email" autocomplete="email" />
                <input v-model.trim="account.phone" type="tel" placeholder="Số điện thoại" autocomplete="tel" />
                <PasswordInput v-model="account.password" placeholder="Mật khẩu (ít nhất 6 ký tự)" autocomplete="new-password" />
                <PasswordInput v-model="account.confirmPassword" placeholder="Nhập lại mật khẩu" autocomplete="new-password" />
                <p v-if="error" class="error">{{ error }}</p>
                <button class="btn block lg submit" type="submit" :disabled="!canCreateAccount || loading">
                  {{ loading ? 'ĐANG XỬ LÝ...' : 'TIẾP THEO' }}
                </button>
              </form>
              <div class="or-divider"><span>HOẶC</span></div>
              <GoogleLoginButton label="Đăng ký với Google" redirect="/seller/register" />
              <p class="terms">
                Bằng việc đăng ký, bạn đã đồng ý với Shoppi về
                <span class="text-primary">Điều khoản dịch vụ</span> &amp;
                <span class="text-primary">Chính sách bảo mật</span>
              </p>
              <p class="switch">
                Bạn đã có tài khoản?
                <router-link :to="{ name: 'seller-login' }" class="text-primary">Đăng nhập</router-link>
              </p>
            </template>

            <!-- ---------- REGISTER, step 2: shop info ---------- -->
            <template v-else-if="step === 2">
              <h2 class="card-title">Thông tin Shop</h2>
              <Steps :current="2" />
              <p class="account-note">
                Đăng ký bán hàng cho tài khoản <strong>{{ auth.fullName }}</strong>.
                <a href="#" class="text-primary" @click.prevent="onSwitchAccount">Dùng tài khoản khác</a>
              </p>
              <form novalidate @submit.prevent="onCreateShop">
                <input v-model.trim="shop.name" placeholder="Tên shop" />
                <select v-model="shop.province" aria-label="Tỉnh/Thành phố">
                  <option :value="null" disabled>-- Tỉnh/Thành phố của shop --</option>
                  <option v-for="p in PROVINCES" :key="p" :value="p">{{ p }}</option>
                </select>
                <input v-model.trim="shop.pickupAddress" placeholder="Địa chỉ lấy hàng (số nhà, đường, phường/xã)" />
                <input v-model.trim="shop.pickupPhone" type="tel" maxlength="12" placeholder="Số điện thoại lấy hàng" />
                <textarea v-model.trim="shop.description" placeholder="Mô tả shop (tùy chọn)" rows="3"></textarea>
                <p v-if="error" class="error">{{ error }}</p>
                <button class="btn block lg submit" type="submit" :disabled="!shop.name || !shop.province || !shop.pickupAddress || !shop.pickupPhone || loading">
                  {{ loading ? 'ĐANG XỬ LÝ...' : 'HOÀN TẤT ĐĂNG KÝ' }}
                </button>
              </form>
            </template>

            <!-- ---------- admin can't open a shop ---------- -->
            <template v-else>
              <h2 class="card-title">Đăng ký</h2>
              <p class="account-note">
                Tài khoản quản trị không thể đăng ký bán hàng.
                <a href="#" class="text-primary" @click.prevent="onSwitchAccount">Đăng xuất để dùng tài khoản khác</a>
              </p>
            </template>
          </section>

          <router-link v-if="isLogin" :to="{ name: 'seller-register' }" class="side-link">
            Chưa có shop? Đăng ký bán hàng ngay
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="m9 6 6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </router-link>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { becomeSeller } from '../../api/shop'
import AuthHeader from '../../components/auth/AuthHeader.vue'
import PasswordInput from '../../components/auth/PasswordInput.vue'
import GoogleLoginButton from '../../components/auth/GoogleLoginButton.vue'
import TwoFactorStep from '../../components/auth/TwoFactorStep.vue'
import { validateLogin, validateRegister, extractError } from '../../utils/authForm'
import { PROVINCES } from '../../utils/provinces'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const challenge = ref(null)   // two-step verification in progress

const isLogin = computed(() => route.meta.mode === 'login')
// signup is Shopee-style two steps: 1 = create account, 2 = shop info.
// A logged-in buyer (incl. right after Google signup) goes straight to step 2.
const step = computed(() => {
  if (!auth.isLoggedIn) return 1
  if (auth.isBuyer) return 2
  return 0 // admin
})

const loading = ref(false)
// OAuth2RedirectView sends failed Google logins back with ?error=...
const error = ref(route.query.error || '')
const account = ref({ fullName: '', email: '', phone: '', password: '', confirmPassword: '' })
const shop = ref({ name: '', province: null, pickupAddress: '', pickupPhone: '', description: '' })

const canCreateAccount = computed(() => {
  const a = account.value
  return a.fullName && a.email && a.phone && a.password && a.confirmPassword
})

// tiny step indicator: "1 Tạo tài khoản — 2 Thông tin shop"
const Steps = (props) => h('ol', { class: 'steps' },
  ['Tạo tài khoản', 'Thông tin Shop'].map((label, i) =>
    h('li', { class: { active: props.current === i + 1, done: props.current > i + 1 } }, [
      h('span', { class: 'dot' }, props.current > i + 1 ? '✓' : String(i + 1)),
      label
    ])))
Steps.props = ['current']

function goToSellerArea() {
  if (auth.isSeller) router.replace(route.query.redirect || '/seller')
  else if (auth.isAdmin) router.replace('/admin')
  else router.replace({ name: 'seller-register' })
}

onMounted(() => {
  // already a seller: nothing to sign up / log in for
  if (auth.isSeller) goToSellerArea()
})

watch(() => route.meta.mode, () => { error.value = '' })

async function run(action) {
  loading.value = true
  error.value = ''
  try {
    await action()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

async function onLogin() {
  error.value = validateLogin(account.value)
  if (error.value) return
  await run(async () => {
    const r = await auth.login({ email: account.value.email, password: account.value.password })
    if (r.twoFactorRequired) {
      challenge.value = r.challenge
      return
    }
    goToSellerArea()
  })
}

async function onCreateAccount() {
  error.value = validateRegister(account.value)
  if (error.value) return
  await run(async () => {
    const { confirmPassword, ...payload } = account.value
    await auth.register(payload) // logs in as BUYER -> step becomes 2
  })
}

async function onCreateShop() {
  if (shop.value.name.length < 3) return (error.value = 'Tên shop phải có ít nhất 3 ký tự')
  if (!/^(0|\+84)\d{9,10}$/.test(shop.value.pickupPhone)) return (error.value = 'Số điện thoại lấy hàng không hợp lệ')
  await run(async () => {
    const { data } = await becomeSeller(shop.value)
    // fresh JWT with role=SELLER, so seller pages unlock without re-login
    auth.setSession(data)
    router.push('/seller')
  })
}

function onSwitchAccount() {
  auth.logout()
  error.value = ''
}

// static drawing data for the SVGs above
const AWNING_COLORS = ['#ff6a3d', '#ffd24d']
const awning = Array.from({ length: 8 }, (_, i) => ({ x: 150 + i * 30, color: AWNING_COLORS[i % 2] }))
const trees = [
  { x: 420, y: 214, r: 14, color: '#4f8fd6' },
  { x: 446, y: 196, r: 18, color: '#6cbf4a' },
  { x: 472, y: 222, r: 12, color: '#3eb6a8' }
]
// [x, height, width] of each building in the skyline
const skyline = [
  [40, 60, 50], [95, 110, 30], [130, 80, 60], [200, 140, 26], [232, 95, 44], [290, 70, 70],
  [370, 120, 36], [412, 150, 18], [436, 90, 60], [510, 60, 80], [600, 130, 40], [646, 100, 30],
  [690, 75, 70], [770, 145, 24], [800, 110, 40], [850, 65, 60], [920, 125, 34], [960, 90, 50],
  [1020, 150, 20], [1046, 100, 44], [1100, 70, 60]
]
</script>

<style scoped>
.auth-main { position: relative; overflow: hidden; }
.login-bg { background: var(--color-surface); }
.register-bg { background: linear-gradient(180deg, #fff 0%, #fdf1ee 100%); }
.skyline { position: absolute; left: 0; right: 0; bottom: 0; width: 100%; height: 160px; fill: var(--color-primary); opacity: 0.07; }
.auth-main-inner { position: relative; }

/* left panel */
.intro { flex: 1; max-width: 560px; }
.intro-brand { color: var(--color-primary); font-size: 30px; margin: 0 0 12px; }
.intro-title { color: var(--color-primary); font-size: 42px; font-weight: 400; line-height: 1.25; margin: 0 0 16px; }
.intro-title.big { font-size: 48px; margin-bottom: 32px; }
.intro-sub { color: var(--color-text-muted); font-size: 20px; line-height: 1.6; margin: 0 0 24px; }
.illustration { width: 100%; max-width: 520px; }

.perks { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 28px; }
.perks li { display: flex; align-items: center; gap: 22px; color: var(--color-primary); font-size: 19px; line-height: 1.5; }
.perks svg { width: 42px; height: 42px; flex-shrink: 0; }

/* right column: card + the small link box under it (login page) */
.card-col { display: flex; flex-direction: column; gap: 16px; }
.side-link {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  background: var(--color-surface);
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.1);
  border-radius: 4px;
  padding: 16px;
  font-size: 15px;
}
.side-link:hover { color: var(--color-primary); }

.account-note { font-size: 13px; color: var(--color-text-muted); line-height: 1.6; margin: 0 0 16px; }
.auth-card textarea { resize: vertical; }

/* step indicator (rendered by the Steps function component) */
.auth-card :deep(.steps) { list-style: none; display: flex; gap: 10px; padding: 0; margin: -10px 0 22px; font-size: 13px; color: var(--color-text-faint); }
.auth-card :deep(.steps li) { display: flex; align-items: center; gap: 6px; }
.auth-card :deep(.steps li + li)::before { content: ''; width: 24px; border-top: 1px solid #ddd; margin-right: 4px; }
.auth-card :deep(.steps .dot) {
  width: 20px; height: 20px; border-radius: 50%;
  border: 1px solid currentColor;
  display: inline-flex; align-items: center; justify-content: center; font-size: 11px;
}
.auth-card :deep(.steps .active), .auth-card :deep(.steps .done) { color: var(--color-primary); }
.auth-card :deep(.steps .active .dot) { background: var(--color-primary); border-color: var(--color-primary); color: white; }

@media (max-width: 900px) {
  .intro { display: none; }
}
@media (max-width: 480px) {
  .card-col { width: 100%; }
}
</style>
