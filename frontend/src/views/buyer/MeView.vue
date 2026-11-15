<template>
  <div>
    <AppHeader />
    <div class="container page">
      <!-- profile card -->
      <section class="me-head">
        <div class="avatar">{{ (auth.fullName || 'S').charAt(0).toUpperCase() }}</div>
        <div v-if="auth.isLoggedIn" class="who">
          <strong>{{ auth.fullName }}</strong>
          <span>{{ auth.email }}</span>
          <span v-if="tier" class="tier">{{ tier }}</span>
        </div>
        <div v-else class="who">
          <strong>Chào mừng bạn đến Shoppi</strong>
          <div class="auth-btns">
            <router-link :to="{ name: 'login', query: { redirect: '/me' } }" class="btn sm light">Đăng nhập</router-link>
            <router-link to="/register" class="btn sm outline">Đăng ký</router-link>
          </div>
        </div>
      </section>

      <!-- seller / admin: their console is the place to be -->
      <section v-if="auth.isSeller || auth.isAdmin" class="block">
        <router-link :to="auth.isSeller ? '/seller' : '/admin'" class="row">
          <span>{{ auth.isSeller ? 'Kênh Người Bán' : 'Trang Quản Trị' }}</span><span class="chev">›</span>
        </router-link>
      </section>

      <template v-if="auth.isBuyer">
        <section class="block">
          <router-link to="/orders" class="row head-row"><span>Đơn mua</span><span class="text-muted small">Xem lịch sử mua hàng ›</span></router-link>
          <div class="order-steps">
            <router-link v-for="s in steps" :key="s.tab" :to="{ name: 'my-orders', query: { tab: s.tab } }" class="step">
              <span class="ico" v-html="s.icon"></span>
              <b v-if="counts[s.tab]" class="count">{{ counts[s.tab] }}</b>
              <span>{{ s.label }}</span>
            </router-link>
          </div>
        </section>

        <section class="block money">
          <router-link to="/account/wallet" class="cell">
            <b>{{ wallet == null ? '—' : formatVnd(wallet) }}</b><span>Ví ShoppiPay</span>
          </router-link>
          <router-link to="/account/coins" class="cell">
            <b>{{ coins == null ? '—' : coins.toLocaleString('vi-VN') }}</b><span>Shoppi Xu</span>
          </router-link>
          <router-link to="/account/vouchers" class="cell">
            <b>🎟</b><span>Kho Voucher</span>
          </router-link>
        </section>

        <section class="block">
          <router-link to="/account/likes" class="row"><span>❤️ Đã thích</span><span class="chev">›</span></router-link>
          <router-link to="/account/price-watches" class="row"><span>🔔 Theo dõi giá</span><span class="chev">›</span></router-link>
          <router-link to="/account/following" class="row"><span>🏬 Shop đang theo dõi</span><span class="chev">›</span></router-link>
          <router-link to="/messages" class="row"><span>💬 Tin nhắn</span><span class="chev">›</span></router-link>
          <router-link to="/chat" class="row"><span>✨ Trợ lý AI</span><span class="chev">›</span></router-link>
        </section>
      </template>

      <section class="block">
        <router-link v-if="auth.isLoggedIn" to="/account/security" class="row"><span>🔒 Tài khoản &amp; Bảo mật</span><span class="chev">›</span></router-link>
        <router-link to="/help" class="row"><span>❓ Trung tâm hỗ trợ</span><span class="chev">›</span></router-link>
        <router-link v-if="!auth.isLoggedIn" to="/seller/login" class="row"><span>🏪 Kênh Người Bán</span><span class="chev">›</span></router-link>
        <router-link v-else-if="auth.isBuyer" to="/seller/register" class="row"><span>🏪 Trở thành Người Bán</span><span class="chev">›</span></router-link>
      </section>

      <button v-if="auth.isLoggedIn" type="button" class="btn secondary logout" @click="logout">Đăng xuất</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import { useAuthStore } from '../../stores/auth'
import { myOrders } from '../../api/orders'
import { myCoins, myWallet, myMembership } from '../../api/coins'
import { formatVnd } from '../../utils/format'

// "Toi": the account hub of the phone tab bar (also reachable on a computer at /me)
const auth = useAuthStore()
const router = useRouter()
const wallet = ref(null)
const coins = ref(null)
const tier = ref('')
const counts = reactive({})

const icon = (d) => `<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round">${d}</svg>`
const steps = [
  { tab: 'AWAITING_PAYMENT', label: 'Chờ thanh toán', icon: icon('<rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18M7 15h3"/>') },
  { tab: 'PENDING', label: 'Chờ xác nhận', icon: icon('<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h3"/>') },
  { tab: 'CONFIRMED', label: 'Chờ lấy hàng', icon: icon('<path d="M3 8l9-5 9 5v8l-9 5-9-5V8Z"/><path d="M3 8l9 5 9-5M12 13v8"/>') },
  { tab: 'SHIPPING', label: 'Đang giao', icon: icon('<path d="M2 6h11v10H2zM13 10h4l4 3v3h-8"/><circle cx="6" cy="18" r="1.8"/><circle cx="17" cy="18" r="1.8"/>') },
  { tab: 'DELIVERED', label: 'Đánh giá', icon: icon('<path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3Z"/>') }
]

onMounted(async () => {
  if (!auth.isBuyer) return
  // each block fills in on its own: one slow call does not hold the page
  myWallet().then(({ data }) => { wallet.value = data.balance }).catch(() => {})
  myCoins().then(({ data }) => { coins.value = data.balance }).catch(() => {})
  myMembership().then(({ data }) => { tier.value = data.tier?.name ? `Hạng ${data.tier.name}` : '' }).catch(() => {})
  myOrders().then(({ data }) => {
    for (const o of data) counts[o.status] = (counts[o.status] || 0) + 1
    // "Danh gia": delivered orders with a line not reviewed yet
    counts.DELIVERED = data.filter(o => o.status === 'DELIVERED' && o.items?.some(i => i.reviewed === false)).length
  }).catch(() => {})
})

function logout() {
  auth.logout()
  router.push('/')
}
</script>

<style scoped>
.page { padding-top: 12px; padding-bottom: 24px; max-width: 760px; }
.me-head { display: flex; align-items: center; gap: 14px; padding: 20px 16px; border-radius: 4px; color: white;
  background: linear-gradient(-180deg, #f53d2d, #f63); }
.avatar { width: 60px; height: 60px; border-radius: 50%; background: white; color: var(--color-primary); font-size: 26px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0; border: 2px solid rgba(255, 255, 255, 0.6); }
.who { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.who strong { font-size: 17px; }
.who span { font-size: 13px; opacity: 0.9; overflow: hidden; text-overflow: ellipsis; }
.tier { align-self: flex-start; background: rgba(0, 0, 0, 0.2); padding: 1px 8px; border-radius: 10px; font-size: 12px !important; }
.auth-btns { display: flex; gap: 8px; margin-top: 6px; }
.btn.light { background: white; color: var(--color-primary); }
.btn.outline { background: transparent; color: white; border: 1px solid white; }
.block { background: white; margin-top: 10px; border-radius: 4px; box-shadow: var(--shadow-card); }
.row { display: flex; justify-content: space-between; align-items: center; padding: 13px 16px; color: var(--color-text); border-top: 1px solid #f2f2f2; }
.block .row:first-child { border-top: none; }
.chev { color: #bbb; font-size: 18px; }
.head-row span:first-child { font-weight: 600; }
.order-steps { display: grid; grid-template-columns: repeat(5, 1fr); padding: 6px 4px 14px; border-top: 1px solid #f2f2f2; }
.step { display: flex; flex-direction: column; align-items: center; gap: 4px; color: #555; font-size: 12px; text-align: center; position: relative; padding-top: 8px; }
.ico { color: #444; height: 26px; }
.count { position: absolute; top: 2px; left: calc(50% + 6px); min-width: 18px; height: 18px; border-radius: 9px; padding: 0 5px;
  background: var(--color-primary); color: white; font-size: 11px; line-height: 18px; }
.money { display: grid; grid-template-columns: repeat(3, 1fr); }
.cell { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 14px 6px; color: var(--color-text); font-size: 12px; }
.cell + .cell { border-left: 1px solid #f2f2f2; }
.cell b { color: var(--color-primary); font-size: 15px; }
.logout { width: 100%; margin-top: 16px; }
</style>
