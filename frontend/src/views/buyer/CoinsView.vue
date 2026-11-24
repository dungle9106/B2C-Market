<template>
  <div>
    <AppHeader />
    <div class="container page">
      <div v-if="!coins" class="shopee-section empty">{{ error || 'Đang tải...' }}</div>
      <template v-else>
        <section class="shopee-section balance">
          <span class="coin">🪙</span>
          <div>
            <div class="amount">{{ coins.balance.toLocaleString('vi-VN') }} <small>Xu</small></div>
            <p>1 Xu = 1₫ khi thanh toán, dùng tối đa {{ coins.maxSpendPercent }}% giá trị đơn. Mỗi đơn hoàn tất tích {{ coins.earnPercent }}% giá trị hàng thành Xu.</p>
          </div>
        </section>

        <section v-if="member" class="shopee-section">
          <h2>Hạng thành viên: <span class="text-primary">{{ member.tier.name }}</span></h2>
          <ol class="tiers">
            <li v-for="t in member.tiers" :key="t.code" :class="{ current: t.code === member.tier.code }">
              <b>{{ t.name }}</b>
              <span>từ {{ formatVnd(t.minSpend) }}</span>
              <span>Xu x{{ t.coinMultiplierPercent / 100 }}</span>
            </li>
          </ol>
          <p class="text-muted hint">
            Đã chi {{ formatVnd(member.spend) }} trong {{ member.windowMonths }} tháng gần nhất.
            <template v-if="member.nextTier">Chi thêm {{ formatVnd(member.remaining) }} để lên hạng {{ member.nextTier.name }}.</template>
            <template v-else>Bạn đang ở hạng cao nhất.</template>
          </p>
        </section>

        <router-link to="/account/lucky-spin" class="shopee-section spin-banner">
          <span>🎡</span><div><b>Vòng quay may mắn</b><p>Mỗi ngày 1 lượt miễn phí, trúng tới 2.000 Xu</p></div><span class="go">Quay ngay ›</span>
        </router-link>

        <section class="shopee-section">
          <h2>Điểm danh mỗi ngày</h2>
          <ol class="week">
            <li v-for="(reward, i) in coins.rewards" :key="i" :class="{ done: i < coins.streak, today: !coins.checkedInToday && i === coins.streak }">
              <b>+{{ reward }}</b>
              <span>{{ i < coins.streak ? '✓' : `Ngày ${i + 1}` }}</span>
            </li>
          </ol>
          <button type="button" class="btn" :disabled="coins.checkedInToday || busy" @click="onCheckIn">
            {{ coins.checkedInToday ? 'Hôm nay đã điểm danh, mai quay lại nhé' : `Điểm danh nhận ${coins.todayReward} Xu` }}
          </button>
          <p v-if="error" class="error">{{ error }}</p>
        </section>

        <section class="shopee-section">
          <h2>Lịch sử Xu</h2>
          <p v-if="!history.items.length" class="text-muted">Chưa có giao dịch Xu nào.</p>
          <div v-for="t in history.items" :key="t.id" class="line">
            <div>
              <div>{{ t.note }}</div>
              <small class="text-muted">{{ formatDateTime(t.createdAt) }}</small>
            </div>
            <b :class="t.amount > 0 ? 'plus' : 'minus'">{{ t.amount > 0 ? '+' : '' }}{{ t.amount.toLocaleString('vi-VN') }}</b>
          </div>
          <PaginationBar :page="history.page" :total-pages="history.totalPages" @change="loadHistory" />
        </section>
      </template>
    </div>
    <AppFooter />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import AppFooter from '../../components/AppFooter.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { myCoins, checkIn, coinHistory, myMembership } from '../../api/coins'
import { formatVnd } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const coins = ref(null)
const member = ref(null)
const history = ref({ items: [], page: 0, totalPages: 0 })
const busy = ref(false)
const error = ref('')

const formatDateTime = (d) => new Date(d).toLocaleString('vi-VN')

async function loadHistory(page = 0) {
  const { data } = await coinHistory(page)
  history.value = data
}

async function onCheckIn() {
  busy.value = true
  error.value = ''
  try {
    const { data } = await checkIn()
    coins.value = data
    await loadHistory()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await myCoins()
    coins.value = data
    myMembership().then(({ data: m }) => { member.value = m }).catch(() => {})
    await loadHistory()
  } catch (e) {
    error.value = extractError(e)
  }
})
</script>

<style scoped>
.spin-banner { display: flex; align-items: center; gap: 12px; color: white; background: linear-gradient(90deg, #ff8a3d, #ee4d2d); }
.spin-banner > span:first-child { font-size: 34px; }
.spin-banner p { margin: 2px 0 0; font-size: 13px; opacity: 0.92; }
.spin-banner .go { margin-left: auto; font-weight: 600; white-space: nowrap; }
.page { padding-top: 20px; padding-bottom: 40px; }
.empty { text-align: center; padding: 48px; }
h2 { font-size: 16px; font-weight: 500; margin: 0 0 12px; }
.balance { display: flex; align-items: center; gap: 16px; }
.balance p { margin: 4px 0 0; font-size: 13px; color: var(--color-text-muted); }
.coin { font-size: 40px; }
.amount { font-size: 30px; color: var(--color-primary); font-weight: 500; }
.amount small { font-size: 15px; }
.week { list-style: none; display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 8px; padding: 0; margin: 0 0 16px; }
.week li { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 10px 4px; border: 1px solid rgba(0, 0, 0, 0.09); border-radius: 4px; font-size: 12px; color: var(--color-text-muted); }
.week b { font-size: 15px; color: var(--color-text); }
.week .done { background: #fff4e5; border-color: #ffd9a8; }
.week .today { border-color: var(--color-primary); }
.week .today b { color: var(--color-primary); }
.line { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px dashed rgba(0, 0, 0, 0.09); font-size: 14px; }
.plus { color: var(--color-success); }
.minus { color: var(--color-primary); }
.error { color: #d33; font-size: 14px; }
.tiers { list-style: none; display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 8px; padding: 0; margin: 0 0 10px; }
.tiers li { display: flex; flex-direction: column; gap: 2px; padding: 10px; border: 1px solid rgba(0, 0, 0, 0.09); border-radius: 4px; font-size: 12px; color: var(--color-text-muted); }
.tiers b { font-size: 14px; color: var(--color-text); }
.tiers .current { border-color: var(--color-primary); background: #fff4e5; }
.hint { font-size: 13px; margin: 0; }
@media (max-width: 560px) {
  .week { gap: 4px; }
  .week li { padding: 8px 2px; }
}
</style>
