<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Đơn Hàng Toàn Sàn</h2>
        <span class="sc-card-sub">Theo dõi mọi đơn hàng, phát hiện đơn bị trễ và can thiệp khi cần</span>
      </div>

      <div class="range">
        <label>Từ <input v-model="from" type="date" :max="to" @change="reload" /></label>
        <label>Đến <input v-model="to" type="date" :min="from" @change="reload" /></label>
        <button v-for="p in PRESETS" :key="p.days" type="button" class="chip" :class="{ on: preset === p.days }" @click="setPreset(p.days)">{{ p.label }}</button>
      </div>

      <div v-if="summary" class="kpis">
        <div class="kpi"><span>Số đơn</span><strong>{{ summary.orders.toLocaleString('vi-VN') }}</strong></div>
        <div class="kpi"><span>Giá trị giao dịch (GMV)</span><strong>{{ formatVnd(summary.gmv) }}</strong></div>
        <div class="kpi"><span>Giá trị đơn trung bình</span><strong>{{ formatVnd(summary.avgOrderValue) }}</strong></div>
        <div class="kpi"><span>Tỷ lệ hủy</span><strong :class="{ bad: summary.cancelRate > 10 }">{{ summary.cancelRate }}%</strong></div>
        <router-link :to="{ query: { status: 'LATE' } }" class="kpi warn" :class="{ zero: !summary.late }">
          <span>Đơn đang trễ</span><strong>{{ summary.late }}</strong>
        </router-link>
      </div>

      <nav class="sc-tabs">
        <router-link v-for="t in TABS" :key="t.key" :to="{ query: t.key === 'ALL' ? {} : { status: t.key } }" :class="{ active: status === t.key }">
          {{ t.label }} <span class="count">({{ tabCount(t.key) }})</span>
        </router-link>
      </nav>
      <p v-if="status === 'LATE' && summary" class="late-rule">
        Đơn trễ: chờ xác nhận quá {{ summary.lateRules.confirmHours }} giờ, chờ lấy hàng quá {{ summary.lateRules.shipHours }} giờ,
        hoặc đang giao quá {{ summary.lateRules.deliveryDays }} ngày (không giới hạn theo khoảng ngày). Đơn chờ lâu nhất ở trên cùng.
      </p>

      <div class="sc-toolbar">
        <input v-model.trim="q" type="search" placeholder="Mã đơn, tên/email người mua, tên shop" @input="onSearch" />
        <select v-model="method" @change="load(0)">
          <option value="">Mọi phương thức thanh toán</option>
          <option v-for="(label, k) in METHODS" :key="k" :value="k">{{ label }}</option>
        </select>
      </div>

      <div v-if="loading && !page.items.length" class="sc-empty">Đang tải...</div>
      <div v-else-if="!page.items.length" class="sc-empty">{{ status === 'LATE' ? 'Không có đơn nào bị trễ 👍' : 'Không có đơn hàng nào' }}</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead><tr><th>Mã đơn</th><th>Người mua</th><th>Shop</th><th class="r">Tổng tiền</th><th>Thanh toán</th><th>Trạng thái</th><th>Đặt lúc</th></tr></thead>
          <tbody>
            <tr v-for="o in page.items" :key="o.id" class="row" @click="openId = o.id">
              <td><strong class="code">{{ o.code }}</strong><div class="faint">{{ o.itemCount }} sản phẩm</div></td>
              <td>{{ o.buyerName }}<div class="faint">{{ o.buyerEmail }}</div></td>
              <td>{{ o.shopName }}</td>
              <td class="r"><strong>{{ formatVnd(o.total) }}</strong></td>
              <td>
                <template v-if="o.paymentMethod">{{ PAYMENT_METHOD[o.paymentMethod] }}<div class="faint pay" :class="o.paymentStatus">{{ PAYMENT_STATUS[o.paymentStatus] }}</div></template>
              </td>
              <td>
                <span class="st" :class="o.status">{{ statusLabel(o.status) }}</span>
                <div v-if="o.late" class="late">⏰ {{ o.late }}</div>
              </td>
              <td class="faint nowrap">{{ formatDateTime(o.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <AdminOrderModal v-if="openId" :id="openId" @close="openId = null" @changed="refresh" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import AdminOrderModal from '../../components/admin/AdminOrderModal.vue'
import { adminOrders, adminOrderSummary } from '../../api/admin'
import { statusLabel } from '../../utils/orderStatus'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { PAYMENT_METHOD, PAYMENT_STATUS } from '../../utils/adminLabels'

const TABS = [
  { key: 'ALL', label: 'Tất cả' }, { key: 'LATE', label: '⏰ Đang trễ' }, { key: 'AWAITING_PAYMENT', label: 'Chờ thanh toán' },
  { key: 'PENDING', label: 'Chờ xác nhận' }, { key: 'CONFIRMED', label: 'Chờ lấy hàng' }, { key: 'SHIPPING', label: 'Đang giao' },
  { key: 'DELIVERED', label: 'Đã giao' }, { key: 'RETURN_REQUESTED', label: 'Trả hàng' }, { key: 'REFUNDED', label: 'Đã hoàn tiền' },
  { key: 'CANCELLED', label: 'Đã hủy' }
]
const METHODS = { COD: 'Thanh toán khi nhận hàng', E_WALLET: 'Ví điện tử', CARD: 'Thẻ', BANK_TRANSFER: 'Chuyển khoản' }
const PRESETS = [{ days: 7, label: '7 ngày' }, { days: 30, label: '30 ngày' }, { days: 90, label: '90 ngày' }]

const route = useRoute()
const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const daysAgo = (n) => iso(new Date(Date.now() - n * 86400000))
const to = ref(iso(new Date()))
const from = ref(daysAgo(29))
const preset = ref(30)
const q = ref(route.query.q || '')
const method = ref('')
const page = ref({ items: [], page: 0, totalPages: 0 })
const summary = ref(null)
const loading = ref(false)
const error = ref('')
const openId = ref(route.query.id ? Number(route.query.id) : null)
let timer = null

const status = computed(() => route.query.status || 'ALL')

function tabCount(key) {
  if (!summary.value) return 0
  if (key === 'ALL') return summary.value.orders
  if (key === 'LATE') return summary.value.late
  return summary.value.byStatus[key] ?? 0
}

async function load(p = 0) {
  loading.value = true
  error.value = ''
  try {
    page.value = (await adminOrders({ status: status.value, method: method.value, q: q.value, from: from.value, to: to.value, page: p })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}
const loadSummary = () => adminOrderSummary(from.value, to.value).then(({ data }) => { summary.value = data }).catch(e => { error.value = extractError(e) })
const reload = () => { preset.value = null; load(0); loadSummary() }
const refresh = () => { load(page.value.page); loadSummary() }

function setPreset(days) {
  to.value = iso(new Date())
  from.value = daysAgo(days - 1)
  load(0)
  loadSummary()
  preset.value = days
}

function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => load(0), 300)
}

watch(status, () => load(0), { immediate: true })
loadSummary()
</script>

<style scoped>
.range { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; font-size: 13px; color: var(--color-text-muted); }
.range input { margin-left: 4px; }
.chip { border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chip.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.kpis { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 12px 14px; display: flex; flex-direction: column; gap: 4px; color: var(--color-text); }
.kpi span { font-size: 12px; color: var(--color-text-muted); }
.kpi strong { font-size: 20px; }
.kpi strong.bad { color: #d33; }
.kpi.warn { background: #fff6f3; border-color: #ffd9cc; }
.kpi.warn strong { color: #d33; }
.kpi.warn.zero { background: white; border-color: var(--color-border); }
.kpi.warn.zero strong { color: #1e9e4a; }
.count { color: var(--color-text-faint); margin-left: 2px; }
.late-rule { font-size: 13px; color: #b35c00; background: #fff7e6; padding: 8px 12px; border-radius: 4px; margin: 0 0 12px; }
.sc-toolbar { display: flex; gap: 10px; flex-wrap: wrap; }
.sc-toolbar input { max-width: 360px; }
.sc-toolbar select { width: auto; min-width: 220px; }
.row { cursor: pointer; }
.row:hover td { background: #fafafa; }
.code { font-family: ui-monospace, monospace; font-size: 13px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.nowrap { white-space: nowrap; }
.r { text-align: right; white-space: nowrap; }
.pay.PAID { color: #1e9e4a; }
.pay.REFUNDED { color: #888; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #f0f5ff; color: #2673dd; white-space: nowrap; }
.st.DELIVERED { background: #eaf7ef; color: #1e9e4a; }
.st.CANCELLED, .st.REFUNDED { background: #f2f2f2; color: #666; }
.st.AWAITING_PAYMENT, .st.PENDING, .st.RETURN_REQUESTED { background: #fff4e5; color: #b35c00; }
.late { font-size: 12px; color: #b42318; margin-top: 4px; white-space: nowrap; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 900px) { .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
