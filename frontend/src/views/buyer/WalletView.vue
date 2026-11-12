<template>
  <div>
    <AppHeader />
    <div class="container page">
      <div v-if="!wallet" class="shopee-section empty">{{ error || 'Đang tải...' }}</div>
      <template v-else>
        <section class="shopee-section balance">
          <span class="icon">💳</span>
          <div>
            <div class="caption">Số dư ví ShoppiPay</div>
            <div class="amount">{{ formatVnd(wallet.balance) }}</div>
          </div>
        </section>

        <section class="shopee-section">
          <h2>Nạp tiền</h2>
          <p class="sandbox">Môi trường thử nghiệm: tiền nạp là mô phỏng, không trừ tiền thật.</p>
          <div class="presets">
            <button v-for="v in PRESETS" :key="v" type="button" class="preset" :class="{ active: amount === v }" @click="amount = v">{{ formatVnd(v) }}</button>
          </div>
          <form class="topup" @submit.prevent="onTopUp">
            <input v-model.number="amount" type="number" :min="wallet.minTopUp" :max="wallet.maxTopUp" step="1000" placeholder="Số tiền muốn nạp" />
            <button type="submit" class="btn" :disabled="!amount || busy">{{ busy ? 'Đang nạp...' : 'Nạp tiền' }}</button>
          </form>
          <p class="text-muted hint">Mỗi lần nạp từ {{ formatVnd(wallet.minTopUp) }} đến {{ formatVnd(wallet.maxTopUp) }}, số dư tối đa {{ formatVnd(wallet.maxBalance) }}.</p>
          <p v-if="error" class="error">{{ error }}</p>
        </section>

        <section class="shopee-section">
          <h2>Lịch sử giao dịch</h2>
          <p v-if="!history.items.length" class="text-muted">Chưa có giao dịch nào.</p>
          <div v-for="t in history.items" :key="t.id" class="line">
            <div>
              <div>{{ t.note }}</div>
              <small class="text-muted">{{ formatDateTime(t.createdAt) }}</small>
            </div>
            <b :class="t.amount > 0 ? 'plus' : 'minus'">{{ t.amount > 0 ? '+' : '-' }}{{ formatVnd(Math.abs(t.amount)) }}</b>
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
import { myWallet, topUpWallet, walletHistory } from '../../api/coins'
import { extractError } from '../../utils/apiError'
import { formatVnd, formatDateTime } from '../../utils/format'

const PRESETS = [50000, 100000, 200000, 500000, 1000000]

const wallet = ref(null)
const history = ref({ items: [], page: 0, totalPages: 0 })
const amount = ref(100000)
const busy = ref(false)
const error = ref('')

async function loadHistory(page = 0) {
  const { data } = await walletHistory(page)
  history.value = data
}

async function onTopUp() {
  busy.value = true
  error.value = ''
  try {
    const { data } = await topUpWallet(amount.value)
    wallet.value = data
    await loadHistory()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await myWallet()
    wallet.value = data
    await loadHistory()
  } catch (e) {
    error.value = extractError(e)
  }
})
</script>

<style scoped>
.page { padding-top: 20px; padding-bottom: 40px; }
.empty { text-align: center; padding: 48px; }
h2 { font-size: 16px; font-weight: 500; margin: 0 0 12px; }
.balance { display: flex; align-items: center; gap: 16px; }
.icon { font-size: 40px; }
.caption { font-size: 13px; color: var(--color-text-muted); }
.amount { font-size: 30px; color: var(--color-primary); font-weight: 500; }
.sandbox { background: #fff8e6; color: #8a6100; font-size: 13px; padding: 8px 12px; border-radius: 3px; margin: 0 0 12px; }
.presets { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.preset { background: white; border: 1px solid rgba(0, 0, 0, 0.09); padding: 8px 14px; border-radius: 2px; cursor: pointer; font-size: 14px; }
.preset.active { border-color: var(--color-primary); color: var(--color-primary); }
.topup { display: flex; gap: 8px; max-width: 420px; }
.topup input { margin: 0; flex: 1; min-width: 0; }
.hint { font-size: 12px; margin: 8px 0 0; }
.line { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px dashed rgba(0, 0, 0, 0.09); font-size: 14px; }
.plus { color: var(--color-success); }
.minus { color: var(--color-primary); }
.error { color: #d33; font-size: 14px; }
</style>
