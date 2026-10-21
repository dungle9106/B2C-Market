<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Đối Soát & Thanh Toán Cho Người Bán</h2>
        <span class="sc-card-sub">
          Shop nhận = Tiền hàng − Khuyến mãi do shop tài trợ − Phí cố định {{ counts.commissionRate ?? '…' }}% − Hoàn tiền.
          Đơn được đối soát sau khi giao thành công {{ counts.holdDays ?? 3 }} ngày và không còn yêu cầu trả hàng.
        </span>
      </div>

      <div class="kpis">
        <div class="kpi"><span>Chờ chuyển cho shop</span><strong>{{ formatVnd(counts.pendingAmount) }}</strong><small>{{ counts.PENDING_PAYMENT ?? 0 }} bảng đối soát</small></div>
        <div class="kpi"><span>Đã thanh toán</span><strong>{{ formatVnd(counts.paidAmount) }}</strong><small>{{ counts.PAID ?? 0 }} bảng đối soát</small></div>
        <div class="kpi accent"><span>Có thể đối soát ngay</span><strong>{{ formatVnd(preview?.totalPayout) }}</strong><small>{{ preview?.shops.length ?? 0 }} shop</small></div>
      </div>

      <!-- next run -->
      <div class="run">
        <div class="run-head">
          <strong>Kỳ đối soát mới</strong>
          <label>Đơn giao đến hết ngày <input v-model="to" type="date" :max="maxTo" @change="loadPreview" /></label>
          <span class="faint">(muộn nhất {{ fmtDay(maxTo) }})</span>
          <button type="button" class="btn sm" :disabled="busy || !selectedShops.length" @click="create">
            Tạo {{ selectedShops.length }} bảng đối soát · {{ formatVnd(selectedTotal) }}
          </button>
        </div>
        <div v-if="!preview" class="faint pad">Đang tải...</div>
        <div v-else-if="!preview.shops.length" class="faint pad">Không có đơn nào đủ điều kiện đối soát.</div>
        <table v-else class="sc-table">
          <thead><tr>
            <th><input type="checkbox" :checked="selectedShops.length === preview.shops.length" @change="toggleAll" /></th>
            <th>Shop</th><th class="r">Đơn</th><th class="r">Tiền hàng</th><th class="r">KM shop</th><th class="r">Phí cố định</th><th class="r">Hoàn tiền</th><th class="r">Shop nhận</th>
          </tr></thead>
          <tbody>
            <tr v-for="s in preview.shops" :key="s.shopId">
              <td><input v-model="selectedShops" type="checkbox" :value="s.shopId" /></td>
              <td>{{ s.shopName }}</td>
              <td class="r">{{ s.orders }}</td>
              <td class="r">{{ formatVnd(s.merchandise) }}</td>
              <td class="r minus">-{{ formatVnd(s.shopDiscounts) }}</td>
              <td class="r minus">-{{ formatVnd(s.commission) }}</td>
              <td class="r minus">-{{ formatVnd(s.refunds) }}</td>
              <td class="r"><strong :class="{ neg: s.netPayout < 0 }">{{ formatVnd(s.netPayout) }}</strong></td>
            </tr>
          </tbody>
        </table>
      </div>

      <nav class="sc-tabs">
        <a v-for="t in TABS" :key="t.key" href="#" :class="{ active: status === t.key }" @click.prevent="status = t.key; load(0)">{{ t.label }}</a>
      </nav>
      <div v-if="!page.items.length" class="sc-empty">Chưa có bảng đối soát nào</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead><tr><th>Mã</th><th>Shop</th><th>Kỳ đến</th><th class="r">Đơn</th><th class="r">Shop nhận</th><th>Trạng thái</th><th></th></tr></thead>
          <tbody>
            <tr v-for="s in page.items" :key="s.id">
              <td><a href="#" class="code" @click.prevent="openDetail(s.id)">{{ s.code }}</a><div class="faint">{{ formatDateTime(s.createdAt) }}</div></td>
              <td>{{ s.shopName }}</td>
              <td>{{ fmtDay(s.periodTo) }}</td>
              <td class="r">{{ s.orderCount }}</td>
              <td class="r"><strong>{{ formatVnd(s.netPayout) }}</strong></td>
              <td>
                <span class="st" :class="s.status">{{ SETTLEMENT_STATUS[s.status] }}</span>
                <div v-if="s.paymentReference" class="faint">{{ s.paymentReference }} · {{ formatDateTime(s.paidAt) }}</div>
              </td>
              <td class="actions">
                <button v-if="s.status === 'PENDING_PAYMENT'" type="button" class="btn sm" @click="paying = s; reference = ''; payError = ''">Đã chuyển khoản</button>
                <a href="#" class="link" @click.prevent="openDetail(s.id)">Chi tiết</a>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <SettlementDetailModal v-if="detail" :s="detail" @close="detail = null" />

    <BaseModal v-if="paying" title="Xác nhận đã thanh toán cho shop" :width="460" @close="paying = null">
      <p class="hint">{{ paying.code }} · {{ paying.shopName }} · <strong>{{ formatVnd(paying.netPayout) }}</strong></p>
      <label class="field">Mã giao dịch chuyển khoản<input v-model.trim="reference" placeholder="Ví dụ: FT26101598765" /></label>
      <p v-if="payError" class="error">{{ payError }}</p>
      <template #footer>
        <button type="button" class="btn ghost" @click="paying = null">Hủy</button>
        <button type="button" class="btn" :disabled="busy || reference.length < 3" @click="pay">Xác nhận</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import SettlementDetailModal from '../../components/SettlementDetailModal.vue'
import { settlementPreview, createSettlements, adminSettlements, adminSettlementCounts, adminSettlement, paySettlement } from '../../api/admin'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { SETTLEMENT_STATUS } from '../../utils/adminLabels'

const TABS = [{ key: 'PENDING_PAYMENT', label: 'Chờ thanh toán' }, { key: 'PAID', label: 'Đã thanh toán' }, { key: 'ALL', label: 'Tất cả' }]
const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const fmtDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '')

const counts = ref({})
const preview = ref(null)
const maxTo = computed(() => iso(new Date(Date.now() - (counts.value.holdDays ?? 3) * 86400000)))
const to = ref(iso(new Date(Date.now() - 3 * 86400000)))
const selectedShops = ref([])
const status = ref('ALL')
const page = ref({ items: [], page: 0, totalPages: 0 })
const detail = ref(null)
const paying = ref(null)
const reference = ref('')
const payError = ref('')
const busy = ref(false)
const error = ref('')

const selectedTotal = computed(() => (preview.value?.shops || []).filter(s => selectedShops.value.includes(s.shopId)).reduce((a, s) => a + Number(s.netPayout), 0))

async function loadPreview() {
  try {
    preview.value = (await settlementPreview(to.value)).data
    selectedShops.value = preview.value.shops.map(s => s.shopId)
  } catch (e) {
    error.value = extractError(e)
  }
}
const loadCounts = () => adminSettlementCounts().then(({ data }) => { counts.value = data }).catch(() => {})
async function load(p = 0) {
  try {
    page.value = (await adminSettlements({ status: status.value, page: p })).data
  } catch (e) {
    error.value = extractError(e)
  }
}
const refresh = () => { loadPreview(); loadCounts(); load(0) }

function toggleAll(e) {
  selectedShops.value = e.target.checked ? preview.value.shops.map(s => s.shopId) : []
}

async function create() {
  if (!confirm(`Tạo ${selectedShops.value.length} bảng đối soát, tổng ${formatVnd(selectedTotal.value)}? Người bán sẽ được thông báo.`)) return
  busy.value = true
  error.value = ''
  try {
    await createSettlements(selectedShops.value, to.value)
    status.value = 'PENDING_PAYMENT'
    refresh()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function openDetail(id) {
  try {
    detail.value = (await adminSettlement(id)).data
  } catch (e) {
    error.value = extractError(e)
  }
}

async function pay() {
  busy.value = true
  payError.value = ''
  try {
    await paySettlement(paying.value.id, reference.value)
    paying.value = null
    loadCounts()
    load(page.value.page)
  } catch (e) {
    payError.value = extractError(e)
  } finally {
    busy.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 12px 14px; display: flex; flex-direction: column; gap: 3px; }
.kpi span, .kpi small { font-size: 12px; color: var(--color-text-muted); }
.kpi strong { font-size: 20px; }
.kpi.accent { background: #f3f0ff; border-color: #e0d8ff; }
.run { border: 1px solid var(--color-border); border-radius: 6px; margin-bottom: 18px; overflow: hidden; }
.run-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 10px 14px; background: #fafafa; border-bottom: 1px solid var(--color-border); font-size: 13px; }
.run-head .btn { margin-left: auto; }
.run-head input { width: auto; margin-left: 6px; }
.pad { padding: 14px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.r { text-align: right; white-space: nowrap; }
.minus { color: var(--color-text-muted); }
.neg { color: #d33; }
.code { font-family: ui-monospace, monospace; font-size: 13px; color: #2673dd; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #eaf7ef; color: #1e9e4a; white-space: nowrap; }
.st.PENDING_PAYMENT { background: #fff4e5; color: #b35c00; }
.actions { white-space: nowrap; text-align: right; }
.link { color: #2673dd; font-size: 13px; margin-left: 8px; }
.hint { font-size: 14px; margin: 0 0 12px; }
.field { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--color-text-muted); }
.error { color: #d33; font-size: 13px; }
@media (max-width: 760px) { .kpis { grid-template-columns: 1fr; } }
</style>
