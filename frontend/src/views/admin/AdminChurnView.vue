<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">AI Dự Đoán Khách Hàng Rời Bỏ</h2>
      <span class="sc-card-sub">
        Dự đoán người mua nào sẽ không quay lại trong {{ m.horizonDays || 60 }} ngày tới, để giữ chân họ bằng voucher riêng trước khi quá muộn.
        Giỏ hàng bị bỏ quên được nhắc tự động.
      </span>
    </div>

    <RiskModelCard :m="m" :train="trainChurn" unit="trường hợp (người mua × thời điểm)" :labels="LABELS" @trained="refresh"
      :needs="`cần ≥ 40 trường hợp, mỗi loại ≥ 8; hiện có ${m.data?.buyers ?? 0} người mua đã từng đặt đơn`" />

    <div class="kpis">
      <div class="kpi"><span>Người mua đã từng đặt đơn</span><b>{{ formatCount(list.buyers ?? 0) }}</b></div>
      <div class="kpi high"><span>Nguy cơ rời bỏ cao</span><b>{{ formatCount(list.high ?? 0) }}</b></div>
      <div class="kpi mid"><span>Nguy cơ trung bình</span><b>{{ formatCount(list.medium ?? 0) }}</b></div>
      <div class="kpi"><span>Doanh thu có nguy cơ mất (ước tính)</span><b>{{ formatVnd(list.valueAtRisk ?? 0) }}</b></div>
    </div>

    <div v-if="results.length" class="results">
      <div v-for="r in results" :key="r.type" class="result">
        <b>{{ ACTION[r.type] || r.type }}</b>
        <span>Đã gửi {{ formatCount(r.sent) }} · {{ formatCount(r.returned) }} người quay lại mua trong 30 ngày
          (<b>{{ r.sent ? Math.round(100 * r.returned / r.sent) : 0 }}%</b>)</span>
      </div>
    </div>

    <div class="sc-toolbar filters">
      <select v-model="level" @change="load">
        <option value="">Mọi mức</option>
        <option value="HIGH">Nguy cơ cao</option>
        <option value="MEDIUM">Trung bình</option>
        <option value="LOW">Thấp</option>
      </select>
      <select v-model="sort" @change="load">
        <option value="risk">Xếp theo nguy cơ</option>
        <option value="value">Xếp theo doanh thu có nguy cơ mất</option>
      </select>
      <span class="faint small">{{ list.learned ? 'Điểm từ mô hình đã huấn luyện' : 'Điểm từ luật: số ngày chưa mua / 120' }}</span>
    </div>

    <div class="winback">
      <b>Gửi voucher giữ chân cho {{ selected.length }} người đã chọn</b>
      <label>Giảm <input v-model.number="form.percent" type="number" min="1" max="50" /> %</label>
      <label>tối đa <input v-model.number="form.maxDiscount" type="number" min="0" step="5000" /> đ</label>
      <label>đơn từ <input v-model.number="form.minOrder" type="number" min="0" step="10000" /> đ</label>
      <label>hạn <input v-model.number="form.days" type="number" min="1" max="60" /> ngày</label>
      <button type="button" class="btn sm" :disabled="!selected.length || sending" @click="send">{{ sending ? 'Đang gửi...' : 'Gửi voucher' }}</button>
      <span v-if="msg" class="msg">{{ msg }}</span>
    </div>

    <p v-if="error" class="err">{{ error }}</p>
    <div v-if="loading" class="sc-empty">Đang chấm điểm người mua...</div>
    <div v-else-if="!items.length" class="sc-empty">Chưa có người mua nào ở mục này</div>
    <div v-else class="table-wrap">
      <table class="tbl">
        <thead>
          <tr>
            <th><input type="checkbox" :checked="allChecked" aria-label="Chọn tất cả" @change="toggleAll" /></th>
            <th>Người mua</th><th>Nguy cơ</th><th>Vì sao</th><th>Đã chi</th><th>Đơn</th><th>Có nguy cơ mất</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in items" :key="b.userId">
            <td><input v-model="selected" type="checkbox" :value="b.userId" :aria-label="`Chọn ${b.name || b.email}`" /></td>
            <td>
              <router-link :to="{ name: 'admin-user', params: { id: b.userId } }">{{ b.name || b.email }}</router-link>
              <div class="faint small">{{ b.email }}</div>
              <div v-if="b.lastWinback" class="sent small">Đã gửi voucher {{ formatDateTime(b.lastWinback) }}</div>
            </td>
            <td>
              <span class="risk" :class="b.level">{{ Math.round(100 * b.probability) }}%</span>
            </td>
            <td class="why"><span v-for="x in b.reasons" :key="x">{{ x }}</span></td>
            <td>{{ formatVnd(b.spend) }}</td>
            <td>{{ b.orders }}</td>
            <td>{{ formatVnd(b.valueAtRisk) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="list.matching > items.length" class="faint small">Đang hiện {{ items.length }} / {{ list.matching }} người mua.</p>
    </div>

    <details class="how">
      <summary>Mô hình hoạt động thế nào?</summary>
      <ol>
        <li>Tua lại lịch sử: tại mỗi mốc thời gian trong quá khứ (mỗi tháng, 12 tháng), lấy hành vi của từng người mua tính đến mốc đó:
          bao lâu chưa mua, nhịp mua thường ngày, số đơn, tổng chi, lượt xem/thêm giỏ/đăng nhập 30 ngày gần nhất, hủy đơn, trả hàng, đánh giá xấu.</li>
        <li>Nhãn: người đó có đặt đơn nào trong {{ m.horizonDays || 60 }} ngày sau mốc không. Mô hình (hồi quy logistic hoặc Gradient Boosting) học quy luật rời bỏ.</li>
        <li>Đánh giá "trong tương lai": huấn luyện trên các mốc cũ, kiểm tra trên các mốc mới nhất. So sánh với luật "càng lâu chưa mua càng dễ rời bỏ".</li>
        <li>Voucher giữ chân là mã riêng (ẩn), được bỏ thẳng vào ví voucher của người mua kèm thông báo đẩy. Mỗi người nhận tối đa 1 lần trong 14 ngày.</li>
        <li>Nhắc giỏ hàng: sản phẩm nằm trong giỏ quá 1 ngày mà chưa đặt sẽ được nhắc (tối đa 3 ngày một lần).</li>
      </ol>
    </details>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { churnBuyers, churnStatus, sendWinback, trainChurn } from '../../api/admin'
import RiskModelCard from '../../components/admin/RiskModelCard.vue'
import { extractError } from '../../utils/apiError'
import { formatCount, formatDateTime, formatVnd } from '../../utils/format'

const LABELS = {
  recencyDays: 'Số ngày chưa mua', recencyRatio: 'Chưa mua so với nhịp thường', ordersTotal: 'Số đơn', orders90d: 'Đơn 90 ngày',
  spendTotal: 'Tổng chi', avgGapDays: 'Nhịp mua', views30d: 'Lượt xem 30 ngày', carts30d: 'Thêm giỏ 30 ngày', likes30d: 'Lượt thích 30 ngày',
  logins30d: 'Đăng nhập 30 ngày', cancelRatio: 'Tỉ lệ hủy', returns: 'Trả hàng', badReviews: 'Đánh giá xấu', avgRating: 'Điểm đánh giá', tenureDays: 'Thâm niên'
}
const ACTION = { WINBACK_VOUCHER: 'Voucher giữ chân', CART_REMINDER: 'Nhắc giỏ hàng bị bỏ quên' }

const m = ref({})
const list = ref({})
const level = ref('')
const sort = ref('value')
const loading = ref(false)
const error = ref('')
const selected = ref([])
const sending = ref(false)
const msg = ref('')
const form = ref({ percent: 15, maxDiscount: 50000, minOrder: 0, days: 14 })

const items = computed(() => list.value.items || [])
const results = computed(() => m.value.results || [])
const allChecked = computed(() => items.value.length > 0 && items.value.every(b => selected.value.includes(b.userId)))

function toggleAll() {
  selected.value = allChecked.value ? [] : items.value.map(b => b.userId)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    list.value = (await churnBuyers({ level: level.value, sort: sort.value, limit: 200 })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

const loadStatus = () => churnStatus().then(({ data }) => { m.value = data }).catch(() => {})
const refresh = () => { loadStatus(); load() }

async function send() {
  if (!confirm(`Gửi voucher giảm ${form.value.percent}% cho ${selected.value.length} người mua?`)) return
  sending.value = true
  msg.value = ''
  try {
    const { data } = await sendWinback({ userIds: selected.value, ...form.value })
    msg.value = `Đã gửi mã ${data.code} cho ${data.sent} người` + (data.skipped ? ` (bỏ qua ${data.skipped} người vừa nhận gần đây)` : '')
    selected.value = []
    refresh()
  } catch (e) {
    msg.value = extractError(e)
  } finally {
    sending.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin-bottom: 14px; }
.kpi { background: #fafafa; border: 1px solid var(--color-border); border-radius: 4px; padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.kpi span { font-size: 12px; color: var(--color-text-muted); }
.kpi b { font-size: 18px; }
.kpi.high b { color: #b42318; }
.kpi.mid b { color: #9a5b00; }
.results { display: flex; flex-direction: column; gap: 4px; font-size: 13px; margin-bottom: 14px; padding: 10px 12px; background: #f3f8ff; border-radius: 4px; }
.result { display: flex; gap: 10px; flex-wrap: wrap; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px; }
.winback { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; font-size: 13px; padding: 10px 12px; border: 1px dashed var(--color-border); border-radius: 4px; margin-bottom: 12px; }
.winback input { width: 80px; padding: 3px 6px; }
.msg { color: var(--color-text-muted); }
.err { color: #d0011b; font-size: 13px; }
.table-wrap { overflow-x: auto; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; }
.tbl th, .tbl td { text-align: left; padding: 8px; border-bottom: 1px solid var(--color-border); vertical-align: top; }
.tbl th { color: var(--color-text-muted); font-weight: 500; white-space: nowrap; }
.risk { font-weight: 600; padding: 2px 8px; border-radius: 10px; background: #e7f6f3; color: #11756a; }
.risk.HIGH { background: #fdecea; color: #b42318; }
.risk.MEDIUM { background: #fff4e0; color: #9a5b00; }
.why { display: flex; flex-direction: column; gap: 2px; min-width: 200px; }
.faint { color: var(--color-text-faint); }
.small { font-size: 12px; }
.sent { color: #2673dd; }
.how { margin-top: 16px; font-size: 13px; }
.how summary { cursor: pointer; color: #2673dd; }
.how ol { padding-left: 18px; line-height: 1.7; color: var(--color-text-muted); }
</style>
