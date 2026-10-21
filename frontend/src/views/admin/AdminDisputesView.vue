<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Tranh Chấp Người Mua – Người Bán</h2>
      <span class="sc-card-sub">Người mua khiếu nại khi người bán từ chối trả hàng/hoàn tiền. Người bán có {{ SELLER_HOURS }} giờ để giải trình; quyết định của Shoppi là cuối cùng.</span>
    </div>

    <nav class="sc-tabs">
      <router-link v-for="t in TABS" :key="t.key" :to="{ query: { status: t.key } }" :class="{ active: status === t.key }">
        {{ t.label }} <span class="count">({{ counts[t.key] ?? 0 }})</span>
      </router-link>
    </nav>

    <div class="layout">
      <aside class="list">
        <div v-if="loading && !page.items.length" class="sc-empty">Đang tải...</div>
        <div v-else-if="!page.items.length" class="sc-empty">{{ status === 'OPEN' ? 'Không có tranh chấp nào chờ xử lý 👍' : 'Không có tranh chấp nào' }}</div>
        <button v-for="r in page.items" :key="r.id" type="button" class="item" :class="{ active: selectedId === r.id }" @click="select(r.id)">
          <div class="item-top">
            <strong>{{ r.orderCode }}</strong>
            <span v-if="r.status === 'ESCALATED'" class="wait" :class="{ long: r.waitingHours >= 48 }">chờ {{ waiting(r.waitingHours) }}</span>
            <span v-else class="dec" :class="r.decision">{{ DISPUTE_DECISION[r.decision] || RETURN_STATUS[r.status] }}</span>
          </div>
          <div class="faint">{{ r.buyerName }} ⟷ {{ r.shopName }}</div>
          <div class="item-bottom">
            <span>{{ RETURN_REASONS[r.reason] }} · <strong>{{ formatVnd(r.refundAmount) }}</strong></span>
            <span v-if="r.sellerOverdue" class="flag">Shop quá hạn giải trình</span>
            <span v-else-if="r.status === 'ESCALATED' && r.sellerAnswered" class="flag ok">Shop đã giải trình</span>
          </div>
        </button>
        <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
      </aside>

      <div class="panel">
        <div v-if="!selectedId" class="sc-empty">Chọn một tranh chấp để xem chi tiết</div>
        <div v-else-if="!d" class="sc-empty">{{ detailError || 'Đang tải...' }}</div>
        <template v-else>
          <div class="facts">
            <div><span>Đơn hàng</span><router-link v-if="can('ORDERS')" :to="{ name: 'admin-orders', query: { id: d.order.id, q: d.order.code } }">{{ d.order.code }}</router-link></div>
            <div><span>Tổng đơn</span><strong>{{ formatVnd(d.order.total) }}</strong></div>
            <div><span>Thanh toán</span>{{ PAYMENT_METHOD[d.order.paymentMethod] }} · {{ PAYMENT_STATUS[d.order.paymentStatus] }}</div>
            <div><span>Giao hàng</span>{{ d.order.deliveredAt ? formatDateTime(d.order.deliveredAt) : '—' }}</div>
            <div><span>Khiếu nại lúc</span>{{ formatDateTime(d.escalatedAt) || '—' }}</div>
            <div><span>Lịch sử shop</span><strong :class="{ bad: d.shopDisputes >= 3 }">{{ d.shopDisputes }} tranh chấp</strong></div>
          </div>

          <ReturnCaseCard :rr="d.request" />

          <DisputeThread :return-id="d.id" side="ADMIN" :open="d.status === 'ESCALATED'" :deadline="d.sellerResponseDeadline"
                         :seller-answered="d.sellerAnswered" :reload-key="threadKey" />

          <div v-if="d.refunds.length" class="refunds">
            <strong>Hoàn tiền của đơn</strong>
            <div v-for="r in d.refunds" :key="r.id">{{ formatVnd(r.amount) }} · {{ REFUND_SOURCE[r.source] }} · {{ REFUND_STATUS[r.status] }}<span v-if="r.reference" class="faint"> · {{ r.reference }}</span></div>
          </div>

          <!-- the decision -->
          <div v-if="d.status === 'ESCALATED'" class="decide">
            <strong>Quyết định của Shoppi</strong>
            <div class="options">
              <label v-for="o in DECISIONS" :key="o.key" class="opt" :class="{ on: decision === o.key }">
                <input v-model="decision" type="radio" :value="o.key" />
                <span><b>{{ o.label }}</b><small>{{ o.hint(d) }}</small></span>
              </label>
            </div>
            <label v-if="decision === 'REFUND_PARTIAL'" class="amount">
              Số tiền hoàn <input v-model.number="amount" type="number" min="1" :max="d.refundAmount - 1" />
              <span class="faint">nhỏ hơn {{ formatVnd(d.refundAmount) }}</span>
            </label>
            <textarea v-model="note" rows="3" maxlength="1000" placeholder="Kết luận (gửi cho cả người mua và người bán, hiển thị trong trao đổi)"></textarea>
            <p v-if="error" class="error">{{ error }}</p>
            <div class="decide-actions">
              <button type="button" class="btn" :disabled="!decision || !note.trim() || busy || (decision === 'REFUND_PARTIAL' && !(amount > 0))" @click="resolve">
                {{ busy ? 'Đang xử lý...' : 'Xác nhận quyết định' }}
              </button>
            </div>
          </div>
          <div v-else class="resolved">
            Đã giải quyết {{ formatDateTime(d.resolvedAt) }}: <strong>{{ DISPUTE_DECISION[d.decision] || RETURN_STATUS[d.status] }}</strong>
            <template v-if="d.decision && d.decision !== 'REJECT'"> · {{ formatVnd(d.refundAmount) }}</template>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import ReturnCaseCard from '../../components/orders/ReturnCaseCard.vue'
import DisputeThread from '../../components/orders/DisputeThread.vue'
import { adminDisputes, adminDisputeCounts, adminDispute, resolveDispute } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { PAYMENT_METHOD, PAYMENT_STATUS } from '../../utils/adminLabels'
import { RETURN_STATUS, RETURN_REASONS, REFUND_STATUS, REFUND_SOURCE, DISPUTE_DECISION } from '../../utils/returns'

const SELLER_HOURS = 48
const TABS = [{ key: 'OPEN', label: 'Chờ xử lý' }, { key: 'RESOLVED', label: 'Đã giải quyết' }, { key: 'ALL', label: 'Tất cả' }]
const DECISIONS = [
  { key: 'REFUND_FULL', label: 'Hoàn toàn bộ', hint: (d) => `${formatVnd(d.refundAmount)} cho người mua${d.request.type === 'RETURN_AND_REFUND' ? ', hàng được trả về shop' : ''}` },
  { key: 'REFUND_PARTIAL', label: 'Hoàn một phần', hint: () => 'Thỏa hiệp: người mua giữ hàng, nhận lại một phần tiền' },
  { key: 'REJECT', label: 'Không hoàn tiền', hint: () => 'Giữ nguyên quyết định của người bán' }
]

const route = useRoute()
const router = useRouter()
const { can } = useAdminMe()
const page = ref({ items: [], page: 0, totalPages: 0 })
const counts = ref({})
const loading = ref(false)
const d = ref(null)
const detailError = ref('')
const decision = ref('')
const amount = ref(null)
const note = ref('')
const busy = ref(false)
const error = ref('')
const threadKey = ref(0)

const status = computed(() => route.query.status || 'OPEN')
const selectedId = computed(() => (route.query.id ? Number(route.query.id) : null))

const waiting = (h) => (h == null ? '' : h < 24 ? `${h} giờ` : `${Math.floor(h / 24)} ngày`)

async function load(p = 0) {
  loading.value = true
  try {
    page.value = (await adminDisputes({ status: status.value, page: p })).data
  } finally {
    loading.value = false
  }
}
const loadCounts = () => adminDisputeCounts().then(({ data }) => { counts.value = data }).catch(() => {})

async function loadDetail() {
  d.value = null
  detailError.value = ''
  decision.value = ''
  amount.value = null
  note.value = ''
  error.value = ''
  if (!selectedId.value) return
  try {
    d.value = (await adminDispute(selectedId.value)).data
  } catch (e) {
    detailError.value = extractError(e)
  }
}

const select = (id) => router.replace({ query: { ...route.query, id } })

async function resolve() {
  const label = DECISIONS.find(o => o.key === decision.value).label
  if (!confirm(`${label}${decision.value === 'REFUND_PARTIAL' ? ' ' + formatVnd(amount.value) : ''} cho đơn ${d.value.orderCode}? Quyết định không thể thay đổi.`)) return
  busy.value = true
  error.value = ''
  try {
    d.value = (await resolveDispute(d.value.id, decision.value, decision.value === 'REFUND_PARTIAL' ? amount.value : null, note.value.trim())).data
    threadKey.value++
    load(page.value.page)
    loadCounts()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

watch(status, () => load(0), { immediate: true })
watch(selectedId, loadDetail, { immediate: true })
onMounted(loadCounts)
</script>

<style scoped>
.count { color: var(--color-text-faint); margin-left: 2px; }
.layout { display: grid; grid-template-columns: 340px minmax(0, 1fr); gap: 16px; align-items: start; }
.list { border: 1px solid var(--color-border); border-radius: 6px; max-height: 78vh; overflow: auto; }
.item { display: block; width: 100%; text-align: left; border: none; border-bottom: 1px solid var(--color-border); background: white; padding: 10px 12px; cursor: pointer; font-size: 13px; }
.item:hover { background: #fafafa; }
.item.active { background: var(--color-primary-light); box-shadow: inset 3px 0 0 var(--color-primary); }
.item-top, .item-bottom { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.item-bottom { margin-top: 4px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.wait { font-size: 11px; color: #b35c00; }
.wait.long { color: #d33; font-weight: 600; }
.dec { font-size: 11px; padding: 1px 6px; border-radius: 3px; background: #f2f2f2; color: #666; }
.dec.REFUND_FULL { background: #eaf7ef; color: #1e9e4a; }
.dec.REFUND_PARTIAL { background: #e8f1fd; color: #2673dd; }
.flag { font-size: 11px; color: #d33; white-space: nowrap; }
.flag.ok { color: #1e9e4a; }
.panel { min-width: 0; display: flex; flex-direction: column; gap: 14px; }
.panel :deep(.case) { margin-bottom: 0; }
.facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px 16px; font-size: 13px; padding: 12px 14px; border: 1px solid var(--color-border); border-radius: 6px; background: #fafafa; }
.facts span { display: block; color: var(--color-text-faint); font-size: 12px; }
.facts a { color: #2673dd; }
.bad { color: #d33; }
.refunds { font-size: 13px; display: flex; flex-direction: column; gap: 4px; }
.decide { border: 1px solid #e0d8ff; background: #faf8ff; border-radius: 6px; padding: 14px; display: flex; flex-direction: column; gap: 10px; }
.options { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.opt { display: flex; gap: 8px; align-items: flex-start; border: 1px solid var(--color-border); background: white; border-radius: 6px; padding: 10px; cursor: pointer; font-size: 13px; }
.opt.on { border-color: #6941c6; box-shadow: 0 0 0 1px #6941c6; }
.opt b { display: block; }
.opt small { color: var(--color-text-muted); }
.amount { font-size: 13px; display: flex; align-items: center; gap: 8px; }
.amount input { width: 160px; }
.decide textarea { width: 100%; }
.decide-actions { display: flex; justify-content: flex-end; }
.resolved { font-size: 14px; padding: 12px 14px; border-radius: 6px; background: #eaf7ef; }
.error { color: #d33; font-size: 13px; margin: 0; }
@media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } .options, .facts { grid-template-columns: 1fr; } }
</style>
