<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Quản Lý Hoàn Tiền</h2>
        <span class="sc-card-sub">Đơn thanh toán online được hoàn tự động về phương thức gốc. Đơn COD cần bộ phận tài chính chuyển khoản và ghi lại mã giao dịch.</span>
      </div>

      <nav class="sc-tabs">
        <router-link v-for="t in TABS" :key="t.key" :to="{ query: { status: t.key } }" :class="{ active: status === t.key }">
          {{ t.label }} <span class="count">({{ t.key === 'ALL' ? total : counts[t.key] ?? 0 }})</span>
        </router-link>
      </nav>
      <div class="sc-toolbar">
        <input v-model.trim="q" type="search" placeholder="Mã đơn, tên người mua, mã giao dịch" @input="onSearch" />
      </div>

      <div v-if="loading && !page.items.length" class="sc-empty">Đang tải...</div>
      <div v-else-if="!page.items.length" class="sc-empty">{{ status === 'PENDING' ? 'Không có khoản hoàn tiền nào chờ xử lý 👍' : 'Không có khoản hoàn tiền nào' }}</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead><tr><th>Đơn hàng</th><th>Người nhận</th><th class="r">Số tiền</th><th>Nguồn</th><th>Kênh hoàn</th><th>Trạng thái</th><th>Tạo lúc</th><th></th></tr></thead>
          <tbody>
            <tr v-for="r in page.items" :key="r.id">
              <td>
                <router-link v-if="can('ORDERS')" :to="{ name: 'admin-orders', query: { id: r.orderId, q: r.orderCode } }" class="code">{{ r.orderCode }}</router-link>
                <span v-else class="code">{{ r.orderCode }}</span>
                <div class="faint">{{ r.shopName }}</div>
              </td>
              <td>{{ r.buyerName }}<div class="faint">{{ r.buyerPhone }}</div></td>
              <td class="r"><strong>{{ formatVnd(r.amount) }}</strong></td>
              <td>{{ REFUND_SOURCE[r.source] }}<div v-if="r.note" class="faint clip" :title="r.note">{{ r.note }}</div></td>
              <td>{{ r.channel === 'BANK_TRANSFER' ? 'Chuyển khoản' : 'Phương thức gốc' }}<div class="faint">{{ PAYMENT_METHOD[r.paymentMethod] }}</div></td>
              <td>
                <span class="st" :class="r.status">{{ REFUND_STATUS[r.status] }}</span>
                <div v-if="r.reference" class="faint">{{ r.reference }}</div>
                <div v-if="r.status === 'PENDING' && ageDays(r.createdAt) >= 3" class="old">Chờ {{ ageDays(r.createdAt) }} ngày</div>
              </td>
              <td class="faint nowrap">{{ formatDateTime(r.createdAt) }}<template v-if="r.completedAt"><br />Xong {{ formatDateTime(r.completedAt) }}</template></td>
              <td class="actions">
                <button v-if="r.status !== 'COMPLETED'" type="button" class="btn sm" @click="open('complete', r)">Đã chuyển khoản</button>
                <button v-if="r.status === 'PENDING'" type="button" class="link warn" @click="open('fail', r)">Thất bại</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <BaseModal v-if="dialog" :title="dialog.mode === 'complete' ? 'Xác nhận đã hoàn tiền' : 'Đánh dấu hoàn tiền thất bại'" :width="480" @close="dialog = null">
      <p class="hint">Đơn <strong>{{ dialog.r.orderCode }}</strong> · {{ dialog.r.buyerName }} · <strong>{{ formatVnd(dialog.r.amount) }}</strong></p>
      <template v-if="dialog.mode === 'complete'">
        <label class="field">Mã giao dịch chuyển khoản<input v-model.trim="reference" placeholder="Ví dụ: FT26101512345" /></label>
        <label class="field">Ghi chú (không bắt buộc)<input v-model.trim="note" /></label>
      </template>
      <template v-else>
        <div class="presets"><button v-for="p in FAIL_REASONS" :key="p" type="button" @click="note = p">{{ p }}</button></div>
        <textarea v-model="note" rows="3" maxlength="500" placeholder="Lý do (gửi cho người mua)"></textarea>
      </template>
      <p v-if="dialogError" class="error">{{ dialogError }}</p>
      <template #footer>
        <button type="button" class="btn ghost" @click="dialog = null">Hủy</button>
        <button type="button" class="btn" :disabled="busy || (dialog.mode === 'complete' ? reference.length < 3 : note.trim().length < 5)" @click="submit">Xác nhận</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import BaseModal from '../../components/BaseModal.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { adminRefunds, adminRefundCounts, completeRefund, failRefund } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { PAYMENT_METHOD } from '../../utils/adminLabels'
import { REFUND_STATUS, REFUND_SOURCE } from '../../utils/returns'

const TABS = [{ key: 'PENDING', label: 'Chờ chuyển khoản' }, { key: 'FAILED', label: 'Thất bại' }, { key: 'COMPLETED', label: 'Đã hoàn' }, { key: 'ALL', label: 'Tất cả' }]
const FAIL_REASONS = ['Người mua chưa cung cấp số tài khoản', 'Số tài khoản không đúng / không tồn tại', 'Tên chủ tài khoản không khớp']

const route = useRoute()
const { can } = useAdminMe()
const page = ref({ items: [], page: 0, totalPages: 0 })
const counts = ref({})
const q = ref('')
const loading = ref(false)
const error = ref('')
const dialog = ref(null)
const reference = ref('')
const note = ref('')
const busy = ref(false)
const dialogError = ref('')
let timer = null

const status = computed(() => route.query.status || 'PENDING')
const total = computed(() => ['PENDING', 'COMPLETED', 'FAILED'].reduce((a, k) => a + (counts.value[k] || 0), 0))
const ageDays = (t) => Math.floor((Date.now() - new Date(t)) / 86400000)

async function load(p = 0) {
  loading.value = true
  error.value = ''
  try {
    page.value = (await adminRefunds({ status: status.value, q: q.value, page: p })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}
const loadCounts = () => adminRefundCounts().then(({ data }) => { counts.value = data }).catch(() => {})

function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => load(0), 300)
}

function open(mode, r) {
  dialog.value = { mode, r }
  reference.value = ''
  note.value = ''
  dialogError.value = ''
}

async function submit() {
  busy.value = true
  dialogError.value = ''
  try {
    const { mode, r } = dialog.value
    await (mode === 'complete' ? completeRefund(r.id, reference.value, note.value) : failRefund(r.id, note.value.trim()))
    dialog.value = null
    load(page.value.page)
    loadCounts()
  } catch (e) {
    dialogError.value = extractError(e)
  } finally {
    busy.value = false
  }
}

watch(status, () => load(0), { immediate: true })
onMounted(loadCounts)
</script>

<style scoped>
.count { color: var(--color-text-faint); margin-left: 2px; }
.sc-toolbar input { max-width: 360px; }
.code { font-family: ui-monospace, monospace; font-size: 13px; color: #2673dd; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.clip { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nowrap { white-space: nowrap; }
.r { text-align: right; white-space: nowrap; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; white-space: nowrap; background: #eaf7ef; color: #1e9e4a; }
.st.PENDING { background: #fff4e5; color: #b35c00; }
.st.FAILED { background: #fdecea; color: #d33; }
.old { font-size: 12px; color: #d33; margin-top: 2px; }
.actions { white-space: nowrap; text-align: right; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 6px; }
.link.warn { color: #d33; }
.hint { font-size: 14px; margin: 0 0 12px; }
.field { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--color-text-muted); margin-bottom: 10px; }
.presets { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.presets button { font-size: 12px; border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 3px 10px; cursor: pointer; }
textarea { width: 100%; }
.error { color: #d33; font-size: 13px; }
</style>
