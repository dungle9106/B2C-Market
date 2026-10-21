<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Đối Soát Thanh Toán</h2>
      <span class="sc-card-sub">Đối chiếu tiền đã thu (online, COD) và tiền đã hoàn với trạng thái đơn hàng; liệt kê các đơn có sai lệch cần kiểm tra</span>
    </div>

    <div class="range">
      <label>Đơn đặt từ <input v-model="from" type="date" :max="to" @change="load" /></label>
      <label>đến <input v-model="to" type="date" :min="from" @change="load" /></label>
    </div>

    <div v-if="!r" class="sc-empty">{{ error || 'Đang tải...' }}</div>
    <template v-else>
      <div class="kpis">
        <div class="kpi"><span>Thu online</span><strong>{{ formatVnd(r.totals.onlineCollected) }}</strong><small>{{ r.totals.onlineCount }} đơn</small></div>
        <div class="kpi"><span>Thu COD</span><strong>{{ formatVnd(r.totals.codCollected) }}</strong><small>{{ r.totals.codCount }} đơn · chưa thu {{ formatVnd(r.totals.codPending) }}</small></div>
        <div class="kpi"><span>Đã hoàn cho người mua</span><strong>-{{ formatVnd(r.totals.refundsCompleted) }}</strong><small>đang chờ hoàn {{ formatVnd(r.totals.refundsPending) }}</small></div>
        <div class="kpi accent"><span>Thực thu</span><strong>{{ formatVnd(r.totals.net) }}</strong><small>đã thu − đã hoàn</small></div>
      </div>

      <div v-if="Object.keys(r.totals.byMethod).length" class="methods">
        <span v-for="(v, k) in r.totals.byMethod" :key="k" class="method">{{ PAYMENT_METHOD[k] || k }}: <b>{{ formatVnd(v) }}</b></span>
      </div>

      <h3 class="sec">
        Sai lệch cần kiểm tra
        <span class="n" :class="{ ok: !r.anomalies.length }">{{ r.anomalies.length }}</span>
      </h3>
      <div v-if="Object.keys(r.anomalyCounts).length" class="filters">
        <button type="button" class="chip" :class="{ on: !type }" @click="type = ''">Tất cả</button>
        <button v-for="(n, k) in r.anomalyCounts" :key="k" type="button" class="chip" :class="{ on: type === k }" @click="type = k">{{ ANOMALY[k] || k }} ({{ n }})</button>
      </div>
      <div v-if="!r.anomalies.length" class="sc-empty">✅ Không phát hiện sai lệch nào trong kỳ</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead><tr><th>Mức độ</th><th>Loại</th><th>Đơn hàng</th><th>Trạng thái đơn / thanh toán</th><th class="r">Giá trị</th><th>Chi tiết</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(a, i) in shown" :key="i">
              <td><span class="sev" :class="a.severity">{{ a.severity === 'high' ? 'Cao' : 'Trung bình' }}</span></td>
              <td class="nowrap">{{ ANOMALY[a.type] || a.type }}</td>
              <td><span class="code">{{ a.orderCode }}</span><div class="faint">{{ formatDateTime(a.createdAt) }}</div></td>
              <td>{{ statusLabel(a.orderStatus) }}<div class="faint">{{ PAYMENT_METHOD[a.paymentMethod] }} · {{ PAYMENT_STATUS[a.paymentStatus] }}</div></td>
              <td class="r">{{ formatVnd(a.amount) }}</td>
              <td class="msg">{{ a.message }}</td>
              <td class="nowrap">
                <router-link v-if="a.refundId" :to="{ name: 'admin-refunds', query: { status: 'PENDING' } }" class="link">Xử lý hoàn tiền</router-link>
                <router-link v-else-if="can('ORDERS')" :to="{ name: 'admin-orders', query: { id: a.orderId, q: a.orderCode } }" class="link">Xem đơn</router-link>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { reconciliation } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { statusLabel } from '../../utils/orderStatus'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { PAYMENT_METHOD, PAYMENT_STATUS, ANOMALY } from '../../utils/adminLabels'

const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const { can } = useAdminMe()
const to = ref(iso(new Date()))
const from = ref(iso(new Date(Date.now() - 29 * 86400000)))
const r = ref(null)
const type = ref('')
const error = ref('')

const shown = computed(() => (type.value ? r.value.anomalies.filter(a => a.type === type.value) : r.value.anomalies))

async function load() {
  error.value = ''
  try {
    r.value = (await reconciliation(from.value, to.value)).data
    if (type.value && !r.value.anomalyCounts[type.value]) type.value = ''
  } catch (e) {
    r.value = null
    error.value = extractError(e)
  }
}

onMounted(load)
</script>

<style scoped>
.range { display: flex; gap: 10px; align-items: center; font-size: 13px; color: var(--color-text-muted); margin-bottom: 14px; }
.range input { margin-left: 4px; }
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 10px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 12px 14px; display: flex; flex-direction: column; gap: 3px; }
.kpi span, .kpi small { font-size: 12px; color: var(--color-text-muted); }
.kpi strong { font-size: 20px; }
.kpi.accent { background: #f3f0ff; border-color: #e0d8ff; }
.methods { display: flex; gap: 16px; flex-wrap: wrap; font-size: 13px; color: var(--color-text-muted); margin-bottom: 18px; }
.sec { font-size: 16px; margin: 6px 0 10px; display: flex; align-items: center; gap: 8px; }
.n { font-size: 12px; background: #d33; color: white; border-radius: 999px; padding: 1px 8px; }
.n.ok { background: #1e9e4a; }
.filters { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 10px; }
.chip { border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chip.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.sev { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #fff4e5; color: #b35c00; white-space: nowrap; }
.sev.high { background: #fdecea; color: #d33; }
.code { font-family: ui-monospace, monospace; font-size: 13px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.nowrap { white-space: nowrap; }
.r { text-align: right; white-space: nowrap; }
.msg { font-size: 13px; max-width: 320px; }
.link { color: #2673dd; font-size: 13px; }
@media (max-width: 900px) { .kpis { grid-template-columns: 1fr 1fr; } }
</style>
