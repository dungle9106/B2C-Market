<template>
  <div>
    <AppHeader />
    <div class="container page">
      <h1 class="page-title"><span class="text-primary">Shoppi</span> | Thanh Toán</h1>

      <div v-if="loadError" class="card empty">
        <p>{{ loadError }}</p>
        <router-link to="/cart" class="btn">Quay lại giỏ hàng</router-link>
      </div>

      <template v-else-if="quote">
        <!-- ===== address ===== -->
        <section class="card address-card">
          <h2 class="card-title text-primary">📍 Địa Chỉ Nhận Hàng</h2>
          <div v-if="address" class="address-row">
            <strong>{{ address.recipientName }} ({{ address.phone }})</strong>
            <span>{{ [address.addressLine, address.ward, address.district, address.province].filter(Boolean).join(', ') }}</span>
            <span v-if="address.default" class="default-tag">Mặc định</span>
            <button type="button" class="link" @click="addressOpen = true">Thay đổi</button>
          </div>
          <div v-else class="address-row">
            <span class="text-muted">Bạn chưa có địa chỉ nhận hàng.</span>
            <button type="button" class="btn sm" @click="addressOpen = true">+ Thêm địa chỉ</button>
          </div>
        </section>

        <!-- ===== one block per shop ===== -->
        <div class="card head-row">
          <span class="col-product">Sản phẩm</span>
          <span class="col">Đơn giá</span>
          <span class="col">Số lượng</span>
          <span class="col">Thành tiền</span>
        </div>

        <section v-for="(sq, i) in quote.shops" :key="sq.shopId" class="card shop-block">
          <div class="shop-name"><span class="shop-badge">Shop</span> {{ sq.shopName }}</div>
          <div v-for="item in sq.items" :key="item.id" class="item-row">
            <div class="col-product product">
              <img :src="item.thumbnailUrl || placeholder" alt="" />
              <span class="name">{{ item.productName }}</span>
              <span v-if="item.variantName" class="variant">Loại: {{ item.variantName }}</span>
            </div>
            <span class="col">{{ formatVnd(item.unitPrice) }}</span>
            <span class="col">{{ item.quantity }}</span>
            <span class="col">{{ formatVnd(item.unitPrice * item.quantity) }}</span>
          </div>

          <div class="shop-extra">
            <!-- combo deals reached (or nearly) in this shop order -->
            <div v-for="c in sq.combos" :key="c.comboId" class="extra-row combo-row">
              <span class="label">🎁 {{ c.name }}</span>
              <span v-if="c.percent" class="applied">Đã giảm {{ c.percent }}% cho {{ c.quantity }} sản phẩm</span>
              <span v-if="c.nextMinQuantity" class="combo-hint">Mua thêm {{ c.nextMinQuantity - c.quantity }} sản phẩm để được giảm {{ c.nextPercent }}%</span>
            </div>
            <p v-for="n in sq.notices" :key="n" class="notice">⚠ {{ n }}</p>
            <div class="extra-row voucher-row">
              <span class="label">🎟️ Voucher của Shop</span>
              <span v-if="sq.shopVoucher" class="applied">{{ sq.shopVoucher.title }} <span class="code-chip">{{ sq.shopVoucher.code }}</span></span>
              <span v-else-if="sq.shopVoucherError" class="voucher-error">{{ sq.shopVoucherError }}</span>
              <button type="button" class="link" @click="openShopVoucher(i)">{{ sq.shopVoucher ? 'Đổi Voucher' : 'Chọn Voucher' }}</button>
            </div>

            <div class="split">
              <label class="note">
                <span>Lời nhắn:</span>
                <input v-model="parts[i].note" maxlength="500" placeholder="Lưu ý cho Người bán..." />
              </label>
              <div class="shipping">
                <div class="ship-head">
                  <span class="label">Đơn vị vận chuyển:</span>
                  <span class="ship-name">{{ sq.shipping.carrierName }} - {{ sq.shipping.methodName }}</span>
                  <button type="button" class="link" @click="shippingFor = i">Thay đổi</button>
                  <span class="ship-fee">
                    <span v-if="sq.shippingDiscount > 0" class="old-price">{{ formatVnd(sq.shippingFee) }}</span>
                    {{ formatVnd(sq.shippingFee - sq.shippingDiscount) }}
                  </span>
                </div>
                <div class="eta">{{ etaText(sq.shipping) }}</div>
                <div v-if="sq.freeShippingProducts" class="freeship-note">Sản phẩm được Freeship (miễn phí tối đa bằng phí vận chuyển tiết kiệm)</div>
                <div v-else-if="sq.freeShippingByShop" class="freeship-note">Shop miễn phí vận chuyển cho đơn từ {{ formatVnd(sq.freeShipMinOrder) }} (tối đa bằng phí vận chuyển tiết kiệm)</div>
                <div v-else-if="sq.freeShipMinOrder" class="freeship-hint">Mua thêm {{ formatVnd(sq.freeShipMinOrder - sq.subtotal) }} của shop để được miễn phí vận chuyển</div>
              </div>
            </div>
          </div>

          <div class="shop-total">
            <span v-if="sq.comboDiscount > 0" class="combo-saved">Combo giảm {{ formatVnd(sq.comboDiscount) }} ·</span>
            Tổng số tiền ({{ sq.quantity }} sản phẩm): <strong>{{ formatVnd(sq.total) }}</strong>
          </div>
        </section>

        <!-- ===== platform voucher ===== -->
        <section class="card voucher-card">
          <span class="label big">🎟️ Shoppi Voucher</span>
          <span v-if="quote.platformVoucher" class="applied">
            {{ quote.platformVoucher.title }} <span class="code-chip">{{ quote.platformVoucher.code }}</span>
          </span>
          <span v-else-if="quote.platformVoucherError" class="voucher-error">{{ quote.platformVoucherError }}</span>
          <button type="button" class="link" @click="platformOpen = true">{{ quote.platformVoucher ? 'Đổi Voucher' : 'Chọn Voucher' }}</button>
        </section>

        <!-- ===== payment method + totals ===== -->
        <section class="card pay-card">
          <div class="pay-head">
            <h2 class="card-title">Phương thức thanh toán</h2>
            <div class="methods" role="radiogroup">
              <button v-for="m in methods" :key="m.value" type="button" role="radio"
                      :aria-checked="paymentMethod === m.value"
                      class="method" :class="{ active: paymentMethod === m.value }"
                      @click="paymentMethod = m.value">{{ m.label }}</button>
            </div>
          </div>
          <div v-if="paymentMethod === 'E_WALLET'" class="sub-methods">
            <button v-for="w in WALLETS" :key="w.value" type="button" class="wallet"
                    :class="{ active: paymentProvider === w.value }" @click="paymentProvider = w.value">
              <span class="wallet-logo" :style="{ background: w.color }">{{ w.short }}</span> {{ w.label }}
            </button>
          </div>
          <p class="pay-note">{{ (methods.find(m => m.value === paymentMethod) || METHODS[0]).note }}</p>

          <dl class="totals">
            <dt>Tổng tiền hàng</dt><dd>{{ formatVnd(quote.merchandiseTotal) }}</dd>
            <dt>Tổng tiền phí vận chuyển</dt><dd>{{ formatVnd(quote.shippingFeeTotal) }}</dd>
            <template v-if="quote.shippingDiscountTotal > 0"><dt>Giảm giá phí vận chuyển</dt><dd>-{{ formatVnd(quote.shippingDiscountTotal) }}</dd></template>
            <template v-if="quote.comboDiscountTotal > 0"><dt>Combo khuyến mãi</dt><dd>-{{ formatVnd(quote.comboDiscountTotal) }}</dd></template>
            <template v-if="quote.shopDiscountTotal > 0"><dt>Voucher của Shop</dt><dd>-{{ formatVnd(quote.shopDiscountTotal) }}</dd></template>
            <template v-if="quote.platformDiscountTotal > 0"><dt>Shoppi Voucher</dt><dd>-{{ formatVnd(quote.platformDiscountTotal) }}</dd></template>
            <dt class="grand">Tổng thanh toán</dt><dd class="grand">{{ formatVnd(quote.total) }}</dd>
          </dl>

          <div class="place-row">
            <span class="text-muted small">Nhấn "Đặt hàng" đồng nghĩa với việc bạn đồng ý tuân theo Điều khoản Shoppi</span>
            <p v-if="error" class="error">{{ error }}</p>
            <button class="btn lg place" :disabled="placing || updating || !address" @click="onPlace">
              {{ placing ? 'Đang đặt hàng...' : 'Đặt hàng' }}
            </button>
          </div>
        </section>
      </template>

      <div v-else class="card empty">Đang tải...</div>
    </div>

    <AddressBook v-if="addressOpen" :addresses="addresses" :current="addressId"
                 @close="addressOpen = false" @changed="loadAddresses" @select="onAddressSelected" />
    <ShippingPicker v-if="shippingFor !== null" :options="quote.shops[shippingFor].shippingOptions"
                    :current="quote.shops[shippingFor].shipping.code" :shop-province="quote.shops[shippingFor].shopProvince"
                    @close="shippingFor = null" @select="onShippingSelected" />
    <VoucherPicker v-if="shopVoucherFor !== null" :title="`Voucher của ${quote.shops[shopVoucherFor].shopName}`"
                   :vouchers="vouchersForShop(quote.shops[shopVoucherFor].shopId)"
                   :amount="Number(quote.shops[shopVoucherFor].subtotal)"
                   :current="parts[shopVoucherFor].voucherCode"
                   @close="shopVoucherFor = null" @apply="onShopVoucher" />
    <VoucherPicker v-if="platformOpen" title="Chọn Shoppi Voucher"
                   :vouchers="vouchers.filter(v => v.scope === 'PLATFORM')"
                   :amount="Number(quote.merchandiseTotal) - Number(quote.comboDiscountTotal) - Number(quote.shopDiscountTotal)"
                   :current="platformVoucherCode"
                   @close="platformOpen = false" @apply="onPlatformVoucher" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import AddressBook from '../../components/checkout/AddressBook.vue'
import ShippingPicker from '../../components/checkout/ShippingPicker.vue'
import VoucherPicker from '../../components/checkout/VoucherPicker.vue'
import { useCartStore } from '../../stores/cart'
import { previewCheckout, checkout, myAddresses } from '../../api/orders'
import { availableVouchers } from '../../api/vouchers'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'
import { getPaymentMethods, vnpayUrl } from '../../api/payments'

const METHODS = [
  { value: 'COD', label: 'Thanh toán khi nhận hàng', note: 'Thanh toán bằng tiền mặt cho nhân viên giao hàng khi nhận hàng.' },
  { value: 'E_WALLET', label: 'Ví điện tử', note: 'Bạn sẽ được chuyển tới cổng thanh toán của ví sau khi đặt hàng (môi trường mô phỏng).' },
  { value: 'CARD', label: 'Thẻ Tín dụng/Ghi nợ', note: 'Hỗ trợ Visa, Mastercard, JCB. Nhập thông tin thẻ ở bước tiếp theo (môi trường mô phỏng).' },
  { value: 'BANK_TRANSFER', label: 'Chuyển khoản ngân hàng', note: 'Bạn sẽ nhận thông tin tài khoản và nội dung chuyển khoản sau khi đặt hàng.' }
]
// the real VNPay gateway, offered when the admin has connected it
const VNPAY_METHOD = { value: 'VNPAY', label: 'VNPay (ATM/QR/Thẻ)', note: 'Bạn sẽ được chuyển sang cổng thanh toán VNPay để thanh toán bằng thẻ ATM nội địa, thẻ quốc tế hoặc QR ngân hàng.' }
const WALLETS = [
  { value: 'MOMO', label: 'Ví MoMo', short: 'Mo', color: '#a50064' },
  { value: 'ZALOPAY', label: 'ZaloPay', short: 'Za', color: '#0068ff' },
  { value: 'VNPAY', label: 'VNPay', short: 'VN', color: '#005baa' }
]

const route = useRoute()
const router = useRouter()
const methods = ref(METHODS)
getPaymentMethods().then(({ data }) => {
  if (data.vnpay) methods.value = [METHODS[0], VNPAY_METHOD, ...METHODS.slice(1)]
}).catch(() => {})
const cart = useCartStore()

const quote = ref(null)
const loadError = ref('')
const error = ref('')
const updating = ref(false)
const placing = ref(false)

const addresses = ref([])
const addressId = ref(null)
const parts = ref([])                 // per shop: { cartItemIds, voucherCode, shippingOption, note }
const platformVoucherCode = ref(null)
const paymentMethod = ref('COD')
const paymentProvider = ref('MOMO')
const vouchers = ref([])

const addressOpen = ref(false)
const shippingFor = ref(null)
const shopVoucherFor = ref(null)
const platformOpen = ref(false)
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const address = computed(() => addresses.value.find(a => a.id === addressId.value) || null)
const vouchersForShop = (shopId) => vouchers.value.filter(v => v.scope === 'SHOP' && v.shopId === shopId)

const fmt = (d) => new Date(d).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })
const etaText = (o) => (o.method === 'SAME_DAY' ? 'Nhận hàng trong ngày' : `Nhận hàng từ ${fmt(o.etaFrom)} - ${fmt(o.etaTo)}`)

function body() {
  return {
    shops: parts.value,
    addressId: addressId.value,
    paymentMethod: paymentMethod.value,
    paymentProvider: paymentMethod.value === 'E_WALLET' ? paymentProvider.value : null,
    platformVoucherCode: platformVoucherCode.value
  }
}

// every change re-prices on the server, so the totals shown are what will be charged
let seq = 0
async function refresh() {
  const id = ++seq
  updating.value = true
  try {
    const { data } = await previewCheckout(body())
    if (id !== seq) return
    quote.value = data
    // remember the option the server settled on, so later previews keep it
    data.shops.forEach((s, i) => { parts.value[i].shippingOption = s.shipping.code })
    error.value = ''
  } catch (e) {
    if (id !== seq) return
    if (quote.value) error.value = extractError(e)
    else loadError.value = extractError(e)
  } finally {
    if (id === seq) updating.value = false
  }
}

async function loadAddresses() {
  const { data } = await myAddresses()
  addresses.value = data
  if (!data.some(a => a.id === addressId.value)) addressId.value = data[0]?.id ?? null // default comes first
}

function onAddressSelected(id) {
  addressId.value = id
  addressOpen.value = false
  loadAddresses()
}
function onShippingSelected(code) {
  parts.value[shippingFor.value].shippingOption = code
  shippingFor.value = null
}
function openShopVoucher(i) { shopVoucherFor.value = i }
function onShopVoucher(code) {
  parts.value[shopVoucherFor.value].voucherCode = code
  shopVoucherFor.value = null
}
function onPlatformVoucher(code) {
  platformVoucherCode.value = code
  platformOpen.value = false
}

async function onPlace() {
  error.value = ''
  placing.value = true
  try {
    const { data } = await checkout(body())
    const ordered = parts.value.flatMap(p => p.cartItemIds)
    cart.setSelected(cart.selectedIds.filter(id => !ordered.includes(id)))
    await cart.fetchCart()
    if (data.paymentRequired && paymentMethod.value === 'VNPAY') {
      // straight to VNPay; if that fails the payment page offers to try again
      try {
        const { data: gw } = await vnpayUrl(data.checkoutCode)
        window.location.assign(gw.url)
        return
      } catch {
        router.replace({ name: 'payment', params: { code: data.checkoutCode } })
        return
      }
    }
    if (data.paymentRequired) router.replace({ name: 'payment', params: { code: data.checkoutCode } })
    else router.replace({ name: 'my-orders', query: { placed: data.checkoutCode } })
  } catch (e) {
    error.value = extractError(e)
    refresh() // stock or vouchers may have changed: show the current state
  } finally {
    placing.value = false
  }
}

watch([addressId, platformVoucherCode, paymentMethod, paymentProvider,
  () => parts.value.map(p => `${p.voucherCode}|${p.shippingOption}`).join(',')], (now, before) => {
  if (before !== undefined) refresh()
})

onMounted(async () => {
  const ids = String(route.query.items || '').split(',').map(Number).filter(Boolean)
  await cart.fetchCart()
  const chosen = cart.items.filter(i => ids.includes(i.id))
  if (!chosen.length) {
    loadError.value = 'Không có sản phẩm nào được chọn để thanh toán.'
    return
  }
  // keep the cart's shop order
  const byShop = new Map()
  chosen.forEach(i => byShop.set(i.shopId, [...(byShop.get(i.shopId) || []), i.id]))
  parts.value = [...byShop.values()].map(cartItemIds => ({ cartItemIds, voucherCode: null, shippingOption: null, note: '' }))

  await loadAddresses()
  availableVouchers([...byShop.keys()]).then(({ data }) => { vouchers.value = data }).catch(() => {})
  await refresh()
})
</script>

<style scoped>
.page { padding-top: 20px; padding-bottom: 40px; }
.page-title { font-size: 22px; font-weight: 400; margin: 0 0 16px; }
.card { background: white; border-radius: 3px; padding: 20px 28px; margin-bottom: 12px; box-shadow: 0 1px 1px rgba(0, 0, 0, 0.05); }
.card-title { font-size: 17px; font-weight: 500; margin: 0 0 12px; }
.empty { text-align: center; padding: 48px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 14px; padding: 0; }

.address-card { border-top: 3px solid; border-image: repeating-linear-gradient(45deg, #6fa6d6, #6fa6d6 33px, transparent 0, transparent 41px, #f18d9b 0, #f18d9b 74px, transparent 0, transparent 82px) 1; }
.address-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; font-size: 15px; }
.default-tag { border: 1px solid var(--color-primary); color: var(--color-primary); font-size: 11px; padding: 0 4px; }

.head-row, .item-row { display: grid; grid-template-columns: minmax(0, 1fr) 140px 100px 140px; align-items: center; gap: 8px; }
.head-row { color: var(--color-text-muted); font-size: 14px; padding: 16px 28px; }
.col { text-align: right; }
.shop-block { padding: 0; }
.shop-name { padding: 16px 28px 8px; font-weight: 500; display: flex; align-items: center; gap: 8px; }
.shop-badge { background: var(--color-primary); color: white; font-size: 11px; padding: 1px 5px; border-radius: 2px; }
.item-row { padding: 12px 28px; font-size: 14px; }
.product { display: flex; align-items: center; gap: 12px; }
.product img { width: 40px; height: 40px; object-fit: cover; flex-shrink: 0; }
.name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.variant { color: var(--color-text-faint); white-space: nowrap; margin-left: auto; padding-left: 8px; }

.shop-extra { border-top: 1px dashed rgba(0, 0, 0, 0.09); margin-top: 8px; }
.extra-row { display: flex; align-items: center; gap: 16px; padding: 14px 28px; border-bottom: 1px dashed rgba(0, 0, 0, 0.09); font-size: 14px; }
.voucher-row .link { margin-left: auto; }
.applied { color: var(--color-primary); }
.code-chip { border: 1px solid var(--color-primary); padding: 0 6px; font-size: 12px; border-radius: 2px; margin-left: 4px; }
.voucher-error { color: #d33; font-size: 13px; }
.split { display: grid; grid-template-columns: 2fr 3fr; background: #fafdff; }
.note { display: flex; align-items: center; gap: 12px; padding: 16px 28px; border-right: 1px dashed rgba(0, 0, 0, 0.09); font-size: 14px; }
.note span { white-space: nowrap; }
.note input { margin: 0; }
.shipping { padding: 16px 28px; font-size: 14px; }
.ship-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.label { color: #00bfa5; }
.label.big { font-size: 17px; color: var(--color-text); }
.ship-name { font-weight: 500; }
.ship-fee { margin-left: auto; }
.old-price { font-size: 13px; margin-right: 4px; }
.eta { color: #26aa99; font-size: 13px; margin-top: 4px; }
.freeship-note { color: var(--color-text-faint); font-size: 12px; margin-top: 4px; }
.freeship-hint { color: var(--color-success); font-size: 12px; margin-top: 4px; }
.combo-row .applied { color: var(--color-success); }
.combo-hint { color: var(--color-primary); font-size: 13px; }
.notice { color: #d33; font-size: 13px; margin: 6px 28px; }
.combo-saved { color: var(--color-success); margin-right: 6px; }
.shop-total { text-align: right; padding: 16px 28px; font-size: 14px; color: var(--color-text-muted); border-top: 1px dashed rgba(0, 0, 0, 0.09); }
.shop-total strong { color: var(--color-primary); font-size: 20px; font-weight: 500; margin-left: 8px; }

.voucher-card { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.voucher-card .link { margin-left: auto; }

.pay-card { padding: 0; }
.pay-head { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; padding: 20px 28px; border-bottom: 1px dashed rgba(0, 0, 0, 0.09); }
.pay-head .card-title { margin: 0; }
.methods { display: flex; gap: 10px; flex-wrap: wrap; }
.method, .wallet { background: white; border: 1px solid rgba(0, 0, 0, 0.09); padding: 8px 14px; border-radius: 2px; cursor: pointer; font-size: 14px; }
.method.active, .wallet.active { border-color: var(--color-primary); color: var(--color-primary); box-shadow: inset 0 0 0 1px var(--color-primary); }
.sub-methods { display: flex; gap: 10px; padding: 16px 28px 0; flex-wrap: wrap; }
.wallet { display: flex; align-items: center; gap: 8px; }
.wallet-logo { width: 26px; height: 26px; border-radius: 6px; color: white; font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.pay-note { padding: 12px 28px 0; margin: 0; font-size: 13px; color: var(--color-text-muted); }

.totals { display: grid; grid-template-columns: 1fr 180px; gap: 12px 24px; justify-items: end; padding: 20px 28px; margin: 0; font-size: 14px; color: var(--color-text-muted); }
.totals dd { margin: 0; color: var(--color-text); }
.totals .grand { align-self: center; }
.totals dd.grand { color: var(--color-primary); font-size: 28px; }
.place-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 20px 28px; border-top: 1px dashed rgba(0, 0, 0, 0.09); background: #fffefb; }
.small { font-size: 13px; }
.place { margin-left: auto; min-width: 210px; }
.error { color: #d33; font-size: 14px; margin: 0; width: 100%; text-align: right; }

@media (max-width: 760px) {
  .card, .head-row, .item-row, .shop-name, .extra-row, .note, .shipping, .shop-total, .pay-head, .totals, .place-row { padding-left: 16px; padding-right: 16px; }
  .head-row { display: none; }
  .item-row { grid-template-columns: 1fr auto; }
  .item-row .col:nth-of-type(2), .item-row .col:nth-of-type(3) { display: none; }
  .split { grid-template-columns: 1fr; }
  .note { border-right: none; border-bottom: 1px dashed rgba(0, 0, 0, 0.09); }
  .totals { grid-template-columns: 1fr auto; }
  .place { width: 100%; }
}
</style>
