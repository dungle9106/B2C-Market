<template>
  <div class="pay-page">
    <!-- simulated payment gateway: no real money moves -->
    <div v-if="pay?.method !== 'VNPAY'" class="sandbox">MÔI TRƯỜNG THỬ NGHIỆM — không nhập thông tin thẻ thật, không có tiền thật được trừ</div>
    <div v-else class="sandbox vn">Thanh toán an toàn qua cổng VNPay — Shoppi không lưu thông tin thẻ của bạn</div>

    <div v-if="loadError" class="card center">
      <p>{{ loadError }}</p>
      <router-link :to="{ name: 'my-orders' }" class="btn">Đơn hàng của tôi</router-link>
    </div>

    <div v-else-if="pay" class="card">
      <header class="gw-head">
        <span class="gw-logo" :style="{ background: brand.color }">{{ brand.short }}</span>
        <div>
          <div class="gw-name">{{ brand.name }}</div>
          <div class="text-faint small">Mã giao dịch {{ pay.checkoutCode }}</div>
        </div>
        <div v-if="pay.status === 'AWAITING_PAYMENT'" class="timer" :class="{ urgent: remainingSec < 120 }">
          Hết hạn sau <strong>{{ countdown }}</strong>
        </div>
      </header>

      <div class="amount-box">
        <span class="text-muted">Số tiền thanh toán</span>
        <span class="amount">{{ formatVnd(pay.amount) }}</span>
        <ul class="orders">
          <li v-for="o in pay.orders" :key="o.id"><span>{{ o.shopName }} · {{ o.code }}</span><span>{{ formatVnd(o.total) }}</span></li>
        </ul>
      </div>

      <!-- ===== result states ===== -->
      <div v-if="pay.status === 'PAID'" class="result ok">
        <span class="result-icon">✓</span>
        <h2>Thanh toán thành công</h2>
        <p class="text-muted">Đơn hàng đã được gửi tới Người Bán để xác nhận.</p>
        <router-link :to="{ name: 'my-orders' }" class="btn lg">Xem đơn hàng</router-link>
      </div>
      <div v-else-if="pay.status === 'CANCELLED'" class="result fail">
        <span class="result-icon">✕</span>
        <h2>Giao dịch đã hủy</h2>
        <p class="text-muted">Đơn hàng đã được hủy và sản phẩm đã được hoàn lại kho.</p>
        <router-link to="/" class="btn lg">Tiếp tục mua sắm</router-link>
      </div>

      <!-- ===== awaiting payment ===== -->
      <template v-else>
        <!-- the real VNPay gateway -->
        <div v-if="pay.method === 'VNPAY'" class="vnpay">
          <p>Bạn sẽ được chuyển sang trang của <strong>VNPay</strong> để thanh toán
            <strong class="text-primary">{{ formatVnd(pay.amount) }}</strong> bằng thẻ ATM, thẻ quốc tế hoặc QR ngân hàng.</p>
          <p v-if="pay.gatewayMessage" class="text-faint small">Lần thử trước: {{ pay.gatewayMessage }}</p>
          <button class="btn lg block" :disabled="busy" @click="onVnpay">{{ busy ? 'Đang chuyển...' : 'Thanh toán qua VNPay' }}</button>
        </div>

        <!-- e-wallet: QR-style screen -->
        <div v-else-if="pay.method === 'E_WALLET'" class="wallet">
          <div class="qr" aria-hidden="true">
            <span v-for="i in 81" :key="i" :class="{ on: qrBit(i) }"></span>
          </div>
          <p>Mở ứng dụng <strong>{{ brand.name }}</strong> và quét mã để thanh toán.<br />
            <span class="text-faint small">(Mô phỏng: bấm nút bên dưới để giả lập đã quét và xác nhận trên ứng dụng.)</span></p>
          <button class="btn lg block" :disabled="busy" @click="onPay()">{{ busy ? 'Đang xử lý...' : 'Xác nhận thanh toán' }}</button>
        </div>

        <!-- card form -->
        <form v-else-if="pay.method === 'CARD'" class="card-form" novalidate autocomplete="off" @submit.prevent="onPay(cardBody())">
          <label>Số thẻ</label>
          <input v-model="card.number" inputmode="numeric" maxlength="23" placeholder="1234 5678 9012 3456" @input="formatNumber" />
          <label>Tên chủ thẻ</label>
          <input v-model="card.holder" placeholder="NGUYEN VAN A" @input="card.holder = card.holder.toUpperCase()" />
          <div class="row">
            <div>
              <label>Ngày hết hạn</label>
              <input v-model="card.expiry" inputmode="numeric" maxlength="5" placeholder="MM/YY" @input="formatExpiry" />
            </div>
            <div>
              <label>CVV</label>
              <input v-model="card.cvv" type="password" inputmode="numeric" maxlength="4" placeholder="•••" />
            </div>
          </div>
          <p class="test-cards text-faint small">
            Thẻ thử: <code>4111 1111 1111 1111</code> (thành công) · <code>4000 0000 0000 0002</code> (bị từ chối)
          </p>
          <button class="btn lg block" type="submit" :disabled="busy">{{ busy ? 'Đang xử lý...' : `Thanh toán ${formatVnd(pay.amount)}` }}</button>
        </form>

        <!-- bank transfer instructions -->
        <div v-else-if="pay.method === 'BANK_TRANSFER'" class="bank">
          <dl>
            <dt>Ngân hàng</dt><dd>{{ pay.bank.bankName }}</dd>
            <dt>Số tài khoản</dt><dd><strong>{{ pay.bank.accountNumber }}</strong></dd>
            <dt>Chủ tài khoản</dt><dd>{{ pay.bank.accountName }}</dd>
            <dt>Số tiền</dt><dd class="text-primary"><strong>{{ formatVnd(pay.amount) }}</strong></dd>
            <dt>Nội dung</dt><dd><strong class="note-code">{{ pay.bank.transferNote }}</strong></dd>
          </dl>
          <p class="text-faint small">Vui lòng ghi đúng nội dung chuyển khoản để hệ thống tự động xác nhận.
            (Mô phỏng: bấm nút bên dưới để giả lập ngân hàng đã nhận tiền.)</p>
          <button class="btn lg block" :disabled="busy" @click="onPay()">{{ busy ? 'Đang xử lý...' : 'Tôi đã chuyển khoản' }}</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>
        <button class="cancel" type="button" :disabled="busy" @click="onCancel">Hủy giao dịch</button>
      </template>
    </div>

    <div v-else class="card center">Đang tải...</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPayment, payCheckout, cancelPayment, vnpayUrl } from '../../api/payments'
import { useCountdown } from '../../composables/useCountdown'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'

const BRANDS = {
  MOMO: { name: 'Ví MoMo', short: 'Mo', color: '#a50064' },
  ZALOPAY: { name: 'ZaloPay', short: 'Za', color: '#0068ff' },
  VNPAY: { name: 'VNPay', short: 'VN', color: '#005baa' },
  CARD: { name: 'Thẻ Tín dụng/Ghi nợ', short: '💳', color: '#1a1f71' },
  BANK_TRANSFER: { name: 'Chuyển khoản ngân hàng', short: '🏦', color: '#0f7a3d' }
}

const route = useRoute()
const pay = ref(null)
const loadError = ref('')
const error = ref('')
const busy = ref(false)
const card = ref({ number: '', holder: '', expiry: '', cvv: '' })

const brand = computed(() => {
  const p = pay.value
  if (!p) return BRANDS.CARD
  return p.method === 'E_WALLET' ? BRANDS[p.provider] || BRANDS.MOMO : BRANDS[p.method] || BRANDS.CARD
})

const { parts } = useCountdown(() => pay.value?.expiresAt)
const countdown = computed(() => `${parts.value[1]}:${parts.value[2]}`)
const remainingSec = computed(() => Number(parts.value[0]) * 3600 + Number(parts.value[1]) * 60 + Number(parts.value[2]))

// decorative pseudo-QR pattern derived from the checkout code
const qrBit = (i) => {
  const code = pay.value?.checkoutCode || ''
  const corner = [1, 2, 3, 10, 12, 19, 20, 21, 7, 8, 9, 16, 18, 25, 26, 27, 55, 56, 57, 64, 66, 73, 74, 75]
  if (corner.includes(i)) return true
  return (code.charCodeAt(i % code.length) * (i + 7)) % 3 === 0
}

function formatNumber() {
  card.value.number = card.value.number.replace(/\D/g, '').slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 ')
}
function formatExpiry() {
  const d = card.value.expiry.replace(/\D/g, '').slice(0, 4)
  card.value.expiry = d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d
}
const cardBody = () => ({ cardNumber: card.value.number, cardHolder: card.value.holder, expiry: card.value.expiry, cvv: card.value.cvv })

async function onPay(body = {}) {
  error.value = ''
  busy.value = true
  try {
    const { data } = await payCheckout(route.params.code, body)
    pay.value = data
    card.value.cvv = '' // never keep card data around longer than needed
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function onVnpay() {
  error.value = ''
  busy.value = true
  try {
    const { data } = await vnpayUrl(route.params.code)
    window.location.assign(data.url)
  } catch (e) {
    error.value = extractError(e)
    busy.value = false
  }
}

async function onCancel() {
  if (!confirm('Hủy giao dịch? Đơn hàng sẽ bị hủy.')) return
  busy.value = true
  try {
    const { data } = await cancelPayment(route.params.code)
    pay.value = data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await getPayment(route.params.code)
    pay.value = data
  } catch (e) {
    loadError.value = extractError(e)
  }
})
</script>

<style scoped>
.pay-page { min-height: 100vh; background: #f0f2f5; padding: 0 16px 40px; }
.sandbox { background: #fff4e5; color: #b45309; text-align: center; font-size: 12px; font-weight: 600; padding: 8px; margin: 0 -16px 32px; letter-spacing: 0.3px; }
.sandbox.vn { background: #e8f1fb; color: #005baa; }
.vnpay p { line-height: 1.6; margin: 0 0 14px; }
.card { max-width: 480px; margin: 0 auto; background: white; border-radius: 10px; padding: 24px; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08); }
.center { text-align: center; }
.small { font-size: 12px; }
.gw-head { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.gw-logo { width: 44px; height: 44px; border-radius: 10px; color: white; font-weight: 700; display: flex; align-items: center; justify-content: center; font-size: 18px; }
.gw-name { font-weight: 600; font-size: 16px; }
.timer { margin-left: auto; font-size: 13px; color: var(--color-text-muted); text-align: right; }
.timer strong { font-variant-numeric: tabular-nums; font-size: 16px; color: var(--color-text); }
.timer.urgent strong { color: #d33; }
.amount-box { background: #fafafa; border-radius: 8px; padding: 16px; display: flex; flex-direction: column; gap: 4px; margin-bottom: 20px; }
.amount { font-size: 30px; font-weight: 600; color: var(--color-primary); }
.orders { list-style: none; margin: 8px 0 0; padding: 8px 0 0; border-top: 1px dashed #ddd; font-size: 13px; color: var(--color-text-muted); }
.orders li { display: flex; justify-content: space-between; gap: 8px; padding: 2px 0; }

.wallet { text-align: center; }
.qr { width: 180px; height: 180px; margin: 0 auto 16px; display: grid; grid-template-columns: repeat(9, 1fr); gap: 2px; padding: 10px; border: 1px solid #eee; border-radius: 8px; }
.qr span { background: #f2f2f2; }
.qr span.on { background: #222; }
.card-form label { display: block; font-size: 13px; color: var(--color-text-muted); margin-bottom: 4px; }
.card-form .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.test-cards code { background: #f5f5f5; padding: 1px 4px; border-radius: 3px; }
.bank dl { display: grid; grid-template-columns: 120px 1fr; gap: 10px; margin: 0 0 12px; font-size: 14px; }
.bank dt { color: var(--color-text-muted); }
.bank dd { margin: 0; }
.note-code { background: #fff4e5; padding: 2px 6px; border-radius: 3px; }
.error { color: #d33; text-align: center; font-size: 14px; margin: 12px 0 0; }
.cancel { display: block; margin: 16px auto 0; background: none; border: none; color: var(--color-text-muted); text-decoration: underline; cursor: pointer; font-size: 13px; }

.result { text-align: center; padding: 12px 0; }
.result h2 { margin: 12px 0 6px; font-size: 20px; }
.result-icon { width: 64px; height: 64px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-size: 32px; color: white; }
.result.ok .result-icon { background: #26aa99; }
.result.fail .result-icon { background: #aaa; }
</style>
