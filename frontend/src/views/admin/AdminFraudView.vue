<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">AI Phát Hiện Gian Lận Đơn Hàng</h2>
      <span class="sc-card-sub">
        Mỗi đơn mới được chấm điểm rủi ro vài phút sau khi đặt: bom hàng COD, săn voucher bằng nhiều tài khoản, tài khoản bị chiếm.
        AI chỉ gắn cờ, admin là người kết luận.
      </span>
    </div>

    <div class="chips">
      <span class="chip HIGH">Rủi ro cao: {{ d.high ?? 0 }}</span>
      <span class="chip MEDIUM">Trung bình: {{ d.medium ?? 0 }}</span>
      <span class="chip">Cần xem: <b>{{ d.pending ?? 0 }}</b></span>
      <span class="chip">Đã chấm: {{ formatCount(d.scored ?? 0) }} / {{ formatCount(d.orders ?? 0) }} đơn</span>
      <span class="chip">Admin kết luận: {{ d.fraud ?? 0 }} gian lận · {{ d.safe ?? 0 }} an toàn</span>
    </div>

    <RiskModelCard :m="m" :train="trainFraud" unit="đơn" :labels="LABELS" @trained="refresh"
      :needs="`cần ≥ 40 đơn đã biết kết quả, hiện có ${(m.labels?.positive ?? 0) + (m.labels?.negative ?? 0)}: ${m.labels?.positive ?? 0} bom hàng/gian lận, ${m.labels?.negative ?? 0} giao thành công`" />

    <div class="sc-toolbar filters">
      <select v-model="level" @change="load(0)">
        <option value="">Mọi mức rủi ro</option>
        <option value="HIGH">Rủi ro cao</option>
        <option value="MEDIUM">Trung bình</option>
        <option value="LOW">Thấp</option>
      </select>
      <select v-model="verdict" @change="load(0)">
        <option value="">Mọi kết luận</option>
        <option value="NONE">Chưa kết luận</option>
        <option value="FRAUD">Gian lận</option>
        <option value="SAFE">An toàn</option>
      </select>
    </div>

    <p v-if="error" class="err">{{ error }}</p>
    <div v-if="loading && !page.items.length" class="sc-empty">Đang tải...</div>
    <div v-else-if="!page.items.length" class="sc-empty">Chưa có đơn nào ở mục này</div>
    <div v-else class="rows">
      <article v-for="r in page.items" :key="r.id" class="row" :class="r.level">
        <div class="score" :class="r.level">
          <b>{{ r.score }}</b>
          <small>{{ LEVEL[r.level] }}</small>
        </div>
        <div class="main">
          <div class="top">
            <router-link :to="{ name: 'admin-orders', query: { q: r.code } }" class="code">{{ r.code }}</router-link>
            <span>{{ formatVnd(r.total) }}</span>
            <span class="faint">{{ PAYMENT_METHOD[r.paymentMethod] || r.paymentMethod }} · {{ statusLabel(r.status) }} · {{ formatDateTime(r.createdAt) }}</span>
          </div>
          <div class="buyer">
            <router-link :to="{ name: 'admin-user', params: { id: r.buyerId } }">{{ r.buyerName || r.buyerEmail }}</router-link>
            <span class="faint">{{ r.buyerEmail }}</span>
          </div>
          <ul v-if="r.reasons.length" class="reasons">
            <li v-for="x in r.reasons" :key="x">{{ x }}</li>
          </ul>
          <div class="faint small">
            Luật: {{ r.ruleScore }}
            <template v-if="r.probability != null"> · Mô hình: {{ pct(r.probability) }} khả năng gian lận</template>
            <template v-if="r.anomaly != null"> · Bất thường hơn {{ pct(r.anomaly) }} số đơn</template>
          </div>
        </div>
        <div class="actions">
          <span v-if="r.verdict" class="verdict" :class="r.verdict">{{ r.verdict === 'FRAUD' ? 'Gian lận' : 'An toàn' }}</span>
          <button type="button" class="btn sm ghost" :disabled="busy === r.id" @click="judge(r, r.verdict === 'SAFE' ? null : 'SAFE')">
            {{ r.verdict === 'SAFE' ? 'Bỏ kết luận' : 'An toàn' }}
          </button>
          <button type="button" class="btn sm danger" :disabled="busy === r.id" @click="judge(r, r.verdict === 'FRAUD' ? null : 'FRAUD')">
            {{ r.verdict === 'FRAUD' ? 'Bỏ kết luận' : 'Gian lận' }}
          </button>
        </div>
      </article>
      <PaginationBar :page="page.page" :total-pages="totalPages" @change="load" />
    </div>

    <details class="how">
      <summary>AI chấm điểm thế nào?</summary>
      <ol>
        <li><b>Luật thủ công</b>: tài khoản mới tạo, nhiều đơn trong 1 giờ, giá trị gấp nhiều lần thói quen, SĐT dùng chung nhiều tài khoản,
          lịch sử bom hàng, giảm giá quá sâu, đăng nhập thiết bị mới sau nhiều lần sai mật khẩu.</li>
        <li><b>Isolation Forest</b> (không cần nhãn): đo mức "khác thường" của đơn so với mọi đơn đã thấy.</li>
        <li><b>Mô hình có giám sát</b> (hồi quy logistic hoặc Gradient Boosting, tự chọn cái tốt hơn): học từ lịch sử. Nhãn "gian lận" là đơn bị từ chối nhận /
          hủy sau khi đã gửi hàng (bom hàng) hoặc admin kết luận gian lận; nhãn "an toàn" là đơn giao thành công hoặc admin kết luận an toàn.</li>
        <li>Đơn có điểm từ 60 trở lên được báo cho admin. Không có đơn nào bị hủy tự động. Mỗi kết luận của admin trở thành dữ liệu huấn luyện cho lần sau.</li>
      </ol>
    </details>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fraudOrders, fraudStatus, setFraudVerdict, trainFraud } from '../../api/admin'
import RiskModelCard from '../../components/admin/RiskModelCard.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { PAYMENT_METHOD } from '../../utils/adminLabels'
import { extractError } from '../../utils/apiError'
import { formatCount, formatDateTime, formatVnd } from '../../utils/format'
import { statusLabel } from '../../utils/orderStatus'

const LEVEL = { HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp' }
const LABELS = {
  amount: 'Giá trị đơn', amountRatio: 'Giá trị so với thói quen', accountAgeDays: 'Tuổi tài khoản', priorOrders: 'Số đơn trước',
  ordersLastHour: 'Đơn trong 1 giờ', ordersLastDay: 'Đơn trong 24 giờ', cod: 'COD', priorFailRatio: 'Tỉ lệ bom hàng trước đó',
  phonesLast7d: 'Số SĐT trong 7 ngày', phoneSharedAccounts: 'SĐT dùng chung', discountRatio: 'Tỉ lệ giảm giá', quantity: 'Số lượng',
  newDeviceLogin: 'Thiết bị mới', failedLogins24h: 'Đăng nhập sai', nightHour: 'Đặt lúc 0h-5h'
}
const SIZE = 20

const error = ref('')
const m = ref({})
const page = ref({ items: [], total: 0, page: 0 })
const level = ref('')
const verdict = ref('NONE')
const loading = ref(false)
const busy = ref(null)

const d = computed(() => m.value.data || {})
const totalPages = computed(() => Math.ceil((page.value.total || 0) / SIZE))
const pct = (x) => `${Math.round(100 * x)}%`

async function load(p = 0) {
  loading.value = true
  error.value = ''
  try {
    page.value = (await fraudOrders({ level: level.value, verdict: verdict.value, page: p, size: SIZE })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

const loadStatus = () => fraudStatus().then(({ data }) => { m.value = data }).catch(() => {})
const refresh = () => { loadStatus(); load(page.value.page) }

async function judge(r, v) {
  busy.value = r.id
  try {
    await setFraudVerdict(r.id, v)
    r.verdict = v
    loadStatus()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = null
  }
}

onMounted(() => { loadStatus(); load() })
</script>

<style scoped>
.chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.chip { font-size: 13px; padding: 4px 10px; border-radius: 12px; background: #f2f2f2; }
.chip.HIGH { background: #fdecea; color: #b42318; }
.chip.MEDIUM { background: #fff4e0; color: #9a5b00; }
.filters { display: flex; gap: 8px; margin-bottom: 12px; }
.rows { display: flex; flex-direction: column; gap: 10px; }
.row { display: grid; grid-template-columns: 64px 1fr auto; gap: 12px; border: 1px solid var(--color-border); border-radius: 4px; padding: 12px; }
.row.HIGH { border-left: 3px solid #d0011b; }
.row.MEDIUM { border-left: 3px solid #d98a00; }
.score { display: flex; flex-direction: column; align-items: center; justify-content: center; border-radius: 4px; background: #f2f2f2; padding: 6px; }
.score b { font-size: 22px; }
.score small { font-size: 11px; }
.score.HIGH { background: #fdecea; color: #b42318; }
.score.MEDIUM { background: #fff4e0; color: #9a5b00; }
.top { display: flex; flex-wrap: wrap; gap: 10px; align-items: baseline; font-size: 14px; }
.code { font-weight: 600; }
.buyer { display: flex; gap: 8px; font-size: 13px; margin: 4px 0; }
.reasons { margin: 6px 0; padding-left: 18px; font-size: 13px; line-height: 1.6; }
.faint { color: var(--color-text-faint); }
.err { color: #d0011b; font-size: 13px; }
.small { font-size: 12px; }
.actions { display: flex; flex-direction: column; gap: 6px; align-items: stretch; min-width: 110px; }
.verdict { text-align: center; font-size: 12px; font-weight: 600; padding: 2px 6px; border-radius: 4px; }
.verdict.FRAUD { background: #fdecea; color: #b42318; }
.verdict.SAFE { background: #e7f6f3; color: #11756a; }
.btn.danger { background: #d0011b; color: #fff; border-color: #d0011b; }
.how { margin-top: 16px; font-size: 13px; }
.how summary { cursor: pointer; color: #2673dd; }
.how ol { padding-left: 18px; line-height: 1.7; color: var(--color-text-muted); }
@media (max-width: 640px) {
  .row { grid-template-columns: 52px 1fr; }
  .actions { grid-column: 1 / -1; flex-direction: row; }
}
</style>
