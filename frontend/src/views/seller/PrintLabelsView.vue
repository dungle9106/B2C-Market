<template>
  <div class="print-page">
    <!-- toolbar: hidden when printing -->
    <div class="toolbar no-print">
      <strong>In phiếu giao hàng</strong>
      <span v-if="!loading">{{ labels.length }} phiếu · khổ A6 (100 × 150 mm)</span>
      <span class="spacer"></span>
      <button type="button" class="btn" :disabled="!labels.length" @click="print">🖨 In ngay</button>
    </div>
    <div v-if="error" class="msg no-print error">{{ error }}</div>
    <div v-if="skipped.length" class="msg no-print warn">
      Không in được {{ skipped.length }} đơn: {{ skipped.map(s => `${s.code} (${s.reason})`).join('; ') }}
    </div>
    <div v-if="loading" class="msg no-print">Đang tải...</div>

    <section v-for="l in labels" :key="l.orderId" class="label">
      <header class="l-head">
        <div class="carrier">{{ l.carrierName }}<span>{{ l.shippingMethodName }}</span></div>
        <div class="brand">Shoppi</div>
      </header>

      <div class="barcode main" v-html="barcode(l.trackingNumber)"></div>
      <div class="tracking">{{ l.trackingNumber }}</div>

      <div class="parties">
        <div class="party">
          <div class="p-title">Từ (người gửi)</div>
          <div class="p-name">{{ l.senderName }}</div>
          <div>{{ l.senderPhone }}</div>
          <div>{{ l.senderAddress }}</div>
        </div>
        <div class="party to">
          <div class="p-title">Đến (người nhận)</div>
          <div class="p-name">{{ receiverName(l.receiver) }}</div>
          <div>{{ receiverRest(l.receiver) }}</div>
          <div v-if="l.receiverProvince" class="province">{{ l.receiverProvince }}</div>
        </div>
      </div>

      <div class="contents">
        <div class="p-title">Nội dung hàng ({{ totalQty(l) }} sản phẩm)</div>
        <ol>
          <li v-for="(it, i) in l.items.slice(0, 6)" :key="i">{{ it.name }}<template v-if="it.variant"> - {{ it.variant }}</template> <strong>x{{ it.quantity }}</strong></li>
          <li v-if="l.items.length > 6" class="more">... và {{ l.items.length - 6 }} sản phẩm khác</li>
        </ol>
        <div v-if="l.note" class="note">Ghi chú: {{ l.note }}</div>
      </div>

      <footer class="l-foot">
        <div class="cod">
          <div class="p-title">Tiền thu người nhận</div>
          <div class="cod-amount">{{ Number(l.codAmount) > 0 ? formatVnd(l.codAmount) : '0 ₫ (đã thanh toán)' }}</div>
        </div>
        <div class="order-ref">
          <div class="barcode small" v-html="barcode(l.orderCode)"></div>
          <div class="order-code">{{ l.orderCode }} · {{ formatDay(l.createdAt) }}</div>
        </div>
        <div class="sign">Chữ ký người nhận<br /><span>(Xác nhận hàng nguyên vẹn)</span></div>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { orderLabels } from '../../api/orders'
import { code128Svg } from '../../utils/barcode'
import { formatVnd } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const route = useRoute()
const labels = ref([])
const skipped = ref([])
const loading = ref(true)
const error = ref('')

const barcode = (text) => code128Svg(text)
const formatDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '')
const totalQty = (l) => l.items.reduce((s, i) => s + i.quantity, 0)
// the address snapshot is "Name (phone), street, ward, district, province"
const receiverName = (r) => (r || '').split(', ')[0]
const receiverRest = (r) => (r || '').split(', ').slice(1).join(', ')

function print() {
  window.print()
}

onMounted(async () => {
  const ids = String(route.query.ids || '').split(',').map(Number).filter(Boolean)
  if (!ids.length) {
    error.value = 'Chưa chọn đơn hàng để in'
    loading.value = false
    return
  }
  try {
    const { data } = await orderLabels(ids)
    labels.value = data.labels
    skipped.value = data.skipped
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.print-page { background: #e9e9e9; min-height: 100vh; padding: 70px 0 30px; }
.toolbar { position: fixed; top: 0; left: 0; right: 0; z-index: 10; display: flex; align-items: center; gap: 14px; padding: 12px 24px; background: white; box-shadow: 0 1px 6px rgba(0, 0, 0, 0.1); }
.spacer { flex: 1; }
.msg { max-width: 600px; margin: 0 auto 12px; padding: 10px 14px; background: white; border-radius: 4px; font-size: 14px; }
.msg.error { color: #d33; }
.msg.warn { color: #a16207; background: #fff8eb; }

/* one A6 label per page */
.label {
  width: 100mm; height: 150mm; box-sizing: border-box; margin: 0 auto 16px; padding: 4mm;
  background: white; color: #000; font-family: Arial, Helvetica, sans-serif; font-size: 9pt;
  display: flex; flex-direction: column; border: 1px solid #000;
}
.l-head { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #000; padding-bottom: 2mm; }
.carrier { font-size: 13pt; font-weight: 700; }
.carrier span { display: block; font-size: 8pt; font-weight: 400; }
.brand { font-size: 12pt; font-weight: 700; border: 2px solid #000; padding: 1mm 2mm; }
.barcode :deep(svg) { display: block; width: 100%; fill: #000; }
.barcode.main { margin-top: 3mm; height: 16mm; }
.barcode.main :deep(svg) { height: 16mm; }
.tracking { text-align: center; font-size: 14pt; font-weight: 700; letter-spacing: 1px; margin: 1mm 0 2mm; }
.parties { display: grid; grid-template-columns: 1fr 1fr; border: 1px solid #000; }
.party { padding: 2mm; line-height: 1.3; }
.party.to { border-left: 1px solid #000; }
.p-title { font-size: 7.5pt; text-transform: uppercase; font-weight: 700; margin-bottom: 1mm; }
.p-name { font-weight: 700; font-size: 10pt; }
.province { font-weight: 700; font-size: 11pt; margin-top: 1mm; }
.contents { flex: 1; border: 1px solid #000; border-top: none; padding: 2mm; overflow: hidden; }
.contents ol { margin: 0; padding-left: 5mm; line-height: 1.35; }
.contents .more { list-style: none; font-style: italic; }
.note { margin-top: 1mm; font-style: italic; }
.l-foot { display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: auto auto; border: 1px solid #000; border-top: none; }
.cod { padding: 2mm; border-right: 1px solid #000; }
.cod-amount { font-size: 15pt; font-weight: 700; }
.order-ref { padding: 2mm; }
.barcode.small { height: 9mm; }
.barcode.small :deep(svg) { height: 9mm; }
.order-code { font-size: 7.5pt; text-align: center; }
.sign { grid-column: 1 / -1; border-top: 1px solid #000; padding: 2mm; height: 14mm; font-size: 8pt; font-weight: 700; }
.sign span { font-weight: 400; font-size: 7pt; }

@media print {
  @page { size: 100mm 150mm; margin: 0; }
  .no-print { display: none !important; }
  .print-page { background: none; padding: 0; }
  .label { margin: 0; border: none; page-break-after: always; break-after: page; }
}
</style>
