<template>
  <div class="ads">
    <!-- ===== wallet ===== -->
    <section class="sc-card wallet">
      <div class="balance">
        <span class="label">Số dư tài khoản quảng cáo</span>
        <span class="amount">{{ wallet ? formatVnd(wallet.balance) : '–' }}</span>
        <span class="small text-faint">Trừ theo mỗi lượt click vào sản phẩm được quảng cáo</span>
      </div>
      <div class="topup">
        <span class="small">Nạp tiền <span class="text-faint">(mô phỏng, không trừ tiền thật)</span></span>
        <div class="presets">
          <button v-for="a in [100000, 200000, 500000, 1000000]" :key="a" type="button" class="btn sm ghost" :disabled="busy" @click="onTopUp(a)">+{{ formatVnd(a) }}</button>
        </div>
        <div class="custom">
          <input v-model.number="customAmount" type="number" min="50000" step="10000" placeholder="Số tiền khác (≥ 50.000)" />
          <button type="button" class="btn sm" :disabled="busy || !customAmount" @click="onTopUp(customAmount)">Nạp</button>
        </div>
      </div>
    </section>

    <!-- ===== campaigns ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Quảng Cáo Tìm Kiếm</h2>
        <span class="sc-card-sub">Sản phẩm hiện ở đầu kết quả tìm kiếm với nhãn "Tài trợ" · trả tiền theo click, mỗi khách chỉ tính 1 lần trong 30 phút</span>
        <button type="button" class="btn new" @click="openForm(null)">+ Tạo quảng cáo</button>
      </div>

      <div v-if="campaigns.length" class="totals">
        <div><span class="num">{{ sum('impressions').toLocaleString('vi-VN') }}</span>Lượt hiển thị</div>
        <div><span class="num">{{ sum('clicks').toLocaleString('vi-VN') }}</span>Lượt click</div>
        <div><span class="num">{{ totalCtr }}%</span>Tỷ lệ click (CTR)</div>
        <div><span class="num">{{ formatVnd(sum('spend')) }}</span>Chi phí</div>
        <div><span class="num">{{ sum('orders') }}</span>Đơn hàng</div>
        <div><span class="num">{{ formatVnd(sum('revenue')) }}</span>Doanh thu từ QC</div>
      </div>

      <div v-if="loading" class="sc-empty">Đang tải...</div>
      <div v-else-if="!campaigns.length" class="sc-empty">Chưa có chiến dịch quảng cáo nào.</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead>
            <tr><th>Sản phẩm</th><th>Từ khóa</th><th>Giá thầu / Ngân sách ngày</th><th class="r">Hiển thị</th><th class="r">Click (CTR)</th><th class="r">Chi phí</th><th class="r">Đơn / Doanh thu</th><th>Trạng thái</th></tr>
          </thead>
          <tbody>
            <tr v-for="c in campaigns" :key="c.id">
              <td><div class="prod"><img :src="c.thumbnailUrl || placeholder" alt="" /><span class="name">{{ c.productName }}</span></div></td>
              <td class="kw">
                <span v-for="k in c.keywords.slice(0, 4)" :key="k" class="chip">{{ k }}</span>
                <span v-if="c.keywords.length > 4" class="small text-faint">+{{ c.keywords.length - 4 }}</span>
                <span v-if="!c.keywords.length" class="small text-faint">Tự động theo tên</span>
              </td>
              <td class="small">{{ formatVnd(c.bidPerClick) }}/click<br />{{ formatVnd(c.dailyBudget) }}/ngày
                <div class="bar" :title="`Hôm nay đã chi ${formatVnd(c.todaySpend)}`"><span :style="{ width: Math.min(100, (c.todaySpend / c.dailyBudget) * 100) + '%' }"></span></div>
              </td>
              <td class="r">{{ c.impressions.toLocaleString('vi-VN') }}</td>
              <td class="r">{{ c.clicks }} <span class="text-faint small">({{ c.ctr }}%)</span></td>
              <td class="r">{{ formatVnd(c.spend) }}</td>
              <td class="r">{{ c.orders }} / {{ formatVnd(c.revenue) }}<div v-if="c.roas" class="small text-faint">ROAS {{ c.roas }}</div></td>
              <td><span class="state" :class="c.status">{{ STATUS[c.status] }}</span><div v-if="c.status === 'ACTIVE' && wallet && Number(wallet.balance) < Number(c.bidPerClick)" class="small warn">Hết số dư</div>
                <div class="actions">
                <template v-if="c.status !== 'ENDED'">
                  <button type="button" class="link" @click="openForm(c)">Sửa</button>
                  <button type="button" class="link" @click="onStatus(c, c.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE')">{{ c.status === 'ACTIVE' ? 'Tạm dừng' : 'Chạy lại' }}</button>
                  <button type="button" class="link danger" @click="onStatus(c, 'ENDED')">Kết thúc</button>
                </template>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="wallet?.transactions.items.length" class="sc-card">
      <h3 class="block-h">Lịch sử giao dịch</h3>
      <table class="sc-table">
        <tbody>
          <tr v-for="t in wallet.transactions.items" :key="t.id">
            <td class="small">{{ formatDateTime(t.createdAt) }}</td>
            <td>{{ t.note }}</td>
            <td class="r" :class="t.amount > 0 ? 'plus' : 'minus'">{{ t.amount > 0 ? '+' : '-' }}{{ formatVnd(Math.abs(t.amount)) }}</td>
            <td class="r small text-faint">Số dư {{ formatVnd(t.balanceAfter) }}</td>
          </tr>
        </tbody>
      </table>
      <PaginationBar :page="wallet.transactions.page" :total-pages="wallet.transactions.totalPages" @change="loadWallet" />
    </section>

    <!-- ===== create / edit ===== -->
    <BaseModal v-if="editing" :title="editing.id ? 'Sửa quảng cáo' : 'Tạo quảng cáo tìm kiếm'" :width="580" @close="editing = null">
      <form id="ad-form" class="form" novalidate @submit.prevent="onSave">
        <label>Sản phẩm <span class="req">*</span></label>
        <select v-model="form.productId" :disabled="!!editing.id" @change="suggestKeywords">
          <option :value="null" disabled>-- Chọn sản phẩm --</option>
          <option v-for="p in products" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>

        <label>Từ khóa <span class="hint">(Enter để thêm · bỏ trống = tự khớp theo tên sản phẩm)</span></label>
        <div class="kw-box">
          <span v-for="(k, i) in form.keywords" :key="k" class="chip">{{ k }} <button type="button" @click="form.keywords.splice(i, 1)">✕</button></span>
          <input v-model.trim="kwDraft" placeholder="VD: áo thun nam" @keydown.enter.prevent="addKeyword" />
        </div>
        <div v-if="suggestions.length" class="suggest">Gợi ý: <button v-for="s in suggestions" :key="s" type="button" @click="addKeyword(s)">+ {{ s }}</button></div>

        <div class="row">
          <div>
            <label>Giá thầu mỗi click (₫) <span class="req">*</span></label>
            <input v-model.number="form.bidPerClick" type="number" min="500" max="50000" step="100" />
          </div>
          <div>
            <label>Ngân sách mỗi ngày (₫) <span class="req">*</span></label>
            <input v-model.number="form.dailyBudget" type="number" min="20000" step="10000" />
          </div>
        </div>
        <p class="estimate">Tối đa khoảng <strong>{{ estimateClicks }}</strong> lượt click mỗi ngày. Giá thầu cao hơn được ưu tiên hiển thị trước.</p>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn ghost" @click="editing = null">Hủy</button>
        <button type="submit" form="ad-form" class="btn" :disabled="busy">{{ busy ? 'Đang lưu...' : 'Lưu quảng cáo' }}</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { adWallet, topUpAds, myAdCampaigns, createAdCampaign, updateAdCampaign, setAdStatus } from '../../api/marketing'
import { myProducts } from '../../api/products'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const STATUS = { ACTIVE: 'Đang chạy', PAUSED: 'Tạm dừng', ENDED: 'Đã kết thúc' }
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const wallet = ref(null)
const campaigns = ref([])
const products = ref([])
const loading = ref(true)
const busy = ref(false)
const customAmount = ref(null)
const editing = ref(null)
const form = ref(null)
const kwDraft = ref('')
const error = ref('')

const sum = (key) => campaigns.value.reduce((s, c) => s + Number(c[key] || 0), 0)
const totalCtr = computed(() => (sum('impressions') ? ((sum('clicks') * 100) / sum('impressions')).toFixed(2) : '0'))
const estimateClicks = computed(() => (form.value?.bidPerClick > 0 ? Math.floor(form.value.dailyBudget / form.value.bidPerClick) : 0))

// two- and three-word phrases from the product name, as keyword ideas
const suggestions = computed(() => {
  const p = products.value.find(x => x.id === form.value?.productId)
  if (!p) return []
  const words = p.name.toLowerCase().split(/\s+/).filter(w => w.length > 1)
  const out = new Set()
  for (let n = 2; n <= 3; n++) for (let i = 0; i + n <= words.length && out.size < 6; i++) out.add(words.slice(i, i + n).join(' '))
  return [...out].filter(s => !form.value.keywords.includes(s))
})

async function loadWallet(page = 0) {
  wallet.value = (await adWallet(page)).data
}
async function loadCampaigns() {
  loading.value = true
  try {
    campaigns.value = (await myAdCampaigns()).data
  } finally {
    loading.value = false
  }
}

async function onTopUp(amount) {
  if (!confirm(`Nạp ${formatVnd(amount)} vào tài khoản quảng cáo? (mô phỏng)`)) return
  busy.value = true
  try {
    wallet.value = (await topUpAds(amount)).data
    customAmount.value = null
  } catch (e) {
    alert(extractError(e))
  } finally {
    busy.value = false
  }
}

async function openForm(c) {
  error.value = ''
  kwDraft.value = ''
  if (!products.value.length) products.value = (await myProducts()).data.filter(p => p.active)
  form.value = c
    ? { productId: c.productId, keywords: [...c.keywords], bidPerClick: Number(c.bidPerClick), dailyBudget: Number(c.dailyBudget) }
    : { productId: null, keywords: [], bidPerClick: 1000, dailyBudget: 50000 }
  editing.value = c || {}
}

function suggestKeywords() {
  form.value.keywords = []
}

function addKeyword(value) {
  const k = (typeof value === 'string' ? value : kwDraft.value).trim().toLowerCase()
  if (k.length >= 2 && !form.value.keywords.includes(k) && form.value.keywords.length < 20) form.value.keywords.push(k)
  if (typeof value !== 'string') kwDraft.value = ''
}

async function onSave() {
  const f = form.value
  error.value = !f.productId ? 'Vui lòng chọn sản phẩm'
    : !(f.bidPerClick >= 500 && f.bidPerClick <= 50000) ? 'Giá thầu từ ₫500 đến ₫50.000'
    : !(f.dailyBudget >= 20000) ? 'Ngân sách ngày tối thiểu ₫20.000'
    : f.dailyBudget < f.bidPerClick ? 'Ngân sách ngày phải lớn hơn giá thầu' : ''
  if (error.value) return
  busy.value = true
  try {
    if (editing.value.id) await updateAdCampaign(editing.value.id, f)
    else await createAdCampaign(f)
    editing.value = null
    await loadCampaigns()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function onStatus(c, status) {
  if (status === 'ENDED' && !confirm(`Kết thúc quảng cáo "${c.productName}"? Không thể chạy lại.`)) return
  try {
    await setAdStatus(c.id, status)
    await loadCampaigns()
  } catch (e) {
    alert(extractError(e))
  }
}

onMounted(() => {
  loadWallet()
  loadCampaigns()
})
</script>

<style scoped>
.wallet { display: flex; gap: 24px; justify-content: space-between; flex-wrap: wrap; }
.balance { display: flex; flex-direction: column; gap: 4px; }
.balance .label { color: var(--color-text-muted); font-size: 14px; }
.amount { font-size: 28px; font-weight: 700; color: var(--color-primary); }
.small { font-size: 12px; }
.topup { display: flex; flex-direction: column; gap: 8px; }
.presets { display: flex; gap: 6px; flex-wrap: wrap; }
.custom { display: flex; gap: 6px; }
.custom input { margin: 0; width: 220px; }
.new { margin-left: auto; }
.totals { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; margin-bottom: 14px; }
.totals > div { border: 1px solid var(--color-border); border-radius: 4px; padding: 10px; font-size: 12px; color: var(--color-text-muted); display: flex; flex-direction: column; }
.totals .num { font-size: 18px; font-weight: 700; color: var(--color-text); }
.prod { display: flex; align-items: center; gap: 8px; }
.prod img { width: 36px; height: 36px; object-fit: cover; border-radius: 2px; }
.name { max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kw { min-width: 150px; max-width: 220px; }
.chip { white-space: nowrap; display: inline-flex; align-items: center; gap: 4px; background: #f0f5ff; color: #2673dd; font-size: 12px; padding: 1px 8px; border-radius: 999px; margin: 1px 2px; }
.chip button { background: none; border: none; color: inherit; cursor: pointer; padding: 0; font-size: 10px; }
.r { text-align: right; white-space: nowrap; }
.bar { height: 4px; background: #eee; border-radius: 999px; overflow: hidden; margin-top: 4px; width: 110px; }
.bar span { display: block; height: 100%; background: #2673dd; }
.state { font-size: 12px; padding: 2px 8px; border-radius: 2px; white-space: nowrap; }
.state.ACTIVE { background: #eaf7ee; color: #1e9e4a; }
.state.PAUSED { background: #fff4e5; color: #d97706; }
.state.ENDED { background: #f5f5f5; color: #888; }
.warn { color: #d33; }
.actions { margin-top: 6px; display: flex; flex-wrap: wrap; gap: 4px 10px; }
.actions .link { padding: 0; font-size: 13px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 4px; }
.link.danger { color: #d33; }
.plus { color: #1e9e4a; }
.minus { color: #d33; }
.block-h { font-size: 16px; margin: 0 0 10px; }
.form label { display: block; font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; }
.req { color: var(--color-primary); }
.hint { font-size: 12px; color: var(--color-text-faint); }
.kw-box { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; border: 1px solid #ddd; border-radius: 4px; padding: 6px; margin-bottom: 6px; }
.kw-box input { border: none; margin: 0; flex: 1; min-width: 140px; padding: 4px; }
.suggest { font-size: 12px; color: var(--color-text-faint); margin-bottom: 12px; display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
.suggest button { background: #f5f5f5; border: none; border-radius: 999px; padding: 2px 8px; font-size: 12px; cursor: pointer; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.estimate { font-size: 13px; background: #f6faff; padding: 8px 10px; border-radius: 3px; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 1000px) { .totals { grid-template-columns: repeat(3, 1fr); } }
</style>
