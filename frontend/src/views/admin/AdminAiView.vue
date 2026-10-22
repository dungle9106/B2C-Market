<template>
  <div>
    <div v-if="!cfg" class="sc-card sc-empty">{{ error || 'Đang tải...' }}</div>
    <template v-else>
      <!-- status & connection -->
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Trợ Lý AI & API Key</h2>
          <span class="status" :class="cfg.status === 'READY' ? 'ok' : 'warn'">{{ cfg.status === 'READY' ? '● Sẵn sàng' : '● ' + cfg.status }}</span>
          <div class="head-right">
            <span class="faint">Bật tính năng AI</span>
            <button type="button" class="switch" :class="{ on: cfg.enabled }" :disabled="saving" @click="save({ enabled: !cfg.enabled })"><span></span></button>
          </div>
        </div>
        <p v-if="!cfg.enabled" class="note warn">Đang tắt: chatbot, viết mô tả, trợ lý doanh thu... trả lời bằng quy tắc có sẵn, không gọi LLM.</p>

        <div class="grid">
          <label>Nhà cung cấp
            <select v-model="form.provider">
              <option value="openai">OpenAI (hoặc API tương thích)</option>
              <option value="claude">Anthropic Claude</option>
              <option value="huggingface">Mô hình tự train (Hugging Face)</option>
            </select>
          </label>
          <label v-if="form.provider === 'openai'">Model
            <input v-model.trim="form.openaiModel" list="models" />
            <datalist id="models"><option v-for="m in cfg.models.filter(x => x.startsWith('gpt'))" :key="m" :value="m" /></datalist>
          </label>
          <label v-if="form.provider === 'claude'">Model Claude
            <input v-model.trim="form.claudeModel" list="claude-models" />
            <datalist id="claude-models"><option v-for="m in cfg.models.filter(x => x.startsWith('claude'))" :key="m" :value="m" /></datalist>
          </label>
        </div>

        <div v-if="form.provider === 'openai'" class="key-row">
          <div>
            <strong>OpenAI API key</strong>
            <div class="faint">{{ keyText(cfg.openaiKey) }}</div>
          </div>
          <template v-if="cfg.canEditKeys">
            <input v-model.trim="form.openaiKey" type="password" autocomplete="new-password" placeholder="Dán key mới (sk-...) để thay" />
            <button v-if="cfg.openaiKey.source === 'ADMIN'" type="button" class="link danger" @click="clearKey('clearOpenaiKey')">Xóa key</button>
          </template>
        </div>
        <div v-if="form.provider === 'openai' && cfg.canEditKeys" class="grid">
          <label>Endpoint<input v-model.trim="form.openaiUrl" /></label>
        </div>
        <template v-if="form.provider === 'claude'">
          <div class="key-row">
            <div><strong>Claude API key</strong><div class="faint">{{ keyText(cfg.claudeKey) }}</div></div>
            <template v-if="cfg.canEditKeys">
              <input v-model.trim="form.claudeKey" type="password" autocomplete="new-password" placeholder="Dán key mới (sk-ant-...) để thay" />
              <button v-if="cfg.claudeKey.source === 'ADMIN'" type="button" class="link danger" @click="clearKey('clearClaudeKey')">Xóa key</button>
            </template>
          </div>
          <div v-if="cfg.canEditKeys" class="grid">
            <label>Endpoint<input v-model.trim="form.claudeUrl" /></label>
          </div>
          <p class="faint">Tìm kiếm ngữ nghĩa vẫn dùng embeddings của OpenAI: nhập thêm OpenAI key nếu muốn giữ tính năng này.</p>
        </template>
        <template v-if="form.provider === 'huggingface'">
          <div class="grid">
            <label>Endpoint Hugging Face<input v-model.trim="form.hfUrl" :disabled="!cfg.canEditKeys" placeholder="https://xxxx.endpoints.huggingface.cloud" /></label>
          </div>
          <div class="key-row">
            <div><strong>Hugging Face token</strong><div class="faint">{{ keyText(cfg.hfToken) }}</div></div>
            <template v-if="cfg.canEditKeys">
              <input v-model.trim="form.hfToken" type="password" autocomplete="new-password" placeholder="Dán token mới (hf_...) để thay" />
              <button v-if="cfg.hfToken.source === 'ADMIN'" type="button" class="link danger" @click="clearKey('clearHfToken')">Xóa token</button>
            </template>
          </div>
        </template>
        <p v-if="!cfg.canEditKeys" class="faint">Chỉ quản trị viên cấp cao được thay đổi API key và endpoint.</p>
        <p class="faint">API key được mã hóa (AES-256) khi lưu vào cơ sở dữ liệu và không bao giờ hiển thị đầy đủ. Nếu chưa nhập, hệ thống dùng biến môi trường của máy chủ.</p>

        <div class="actions">
          <span v-if="test" class="test" :class="test.ok ? 'ok' : 'bad'">{{ test.ok ? '✓' : '✗' }} {{ test.message }}<template v-if="test.latencyMs"> · {{ test.latencyMs }} ms</template></span>
          <button type="button" class="btn sm ghost" :disabled="testing" @click="runTest">{{ testing ? 'Đang kiểm tra...' : 'Kiểm tra kết nối' }}</button>
          <button type="button" class="btn sm" :disabled="saving" @click="saveConnection">Lưu cấu hình</button>
        </div>
      </section>

      <!-- review classification model (FastAPI service, folder review-ai-service/) -->
      <section v-if="rai" class="sc-card">
        <div class="sc-card-head tw-items-center">
          <h2 class="sc-card-title">Mô Hình Phân Loại Đánh Giá (FastAPI)</h2>
          <button type="button" class="btn sm ghost tw-ml-auto" :disabled="training || !rai.configured || rai.status === 'DOWN'" @click="train">
            {{ training ? 'Đang huấn luyện...' : 'Huấn luyện lại' }}</button>
        </div>
        <p v-if="!rai.configured" class="tw-m-0 tw-text-sm tw-text-neutral-500">Chưa bật. Chạy dịch vụ trong thư mục <code>review-ai-service/</code> và đặt biến
          <code>REVIEW_AI_URL</code> (ví dụ <code>http://localhost:8000</code>). Khi chưa bật, Trung tâm đánh giá dùng bộ luật tiếng Việt và LLM.</p>
        <div v-else class="tw-grid tw-grid-cols-2 tw-gap-3 tw-text-[13px] md:tw-grid-cols-4">
          <div><div class="tw-text-neutral-500">Trạng thái</div><b :class="rai.status === 'UP' ? 'tw-text-green-600' : 'tw-text-red-600'">{{ rai.status === 'UP' ? 'Đang chạy' : 'Không kết nối được' }}</b></div>
          <div><div class="tw-text-neutral-500">Mô hình</div><b>{{ rai.model?.trained ? 'Đã huấn luyện (' + rai.model.samples + ' mẫu)' : 'Chưa huấn luyện: dùng từ điển' }}</b></div>
          <div><div class="tw-text-neutral-500">F1 trên tập kiểm tra</div><b>{{ rai.model?.metrics?.issuesMicroF1 ?? '—' }}</b></div>
          <div><div class="tw-text-neutral-500">Dữ liệu đã gán nhãn</div><b>{{ rai.labelled }}</b> đánh giá</div>
        </div>
        <p class="tw-mb-0 tw-mt-2 tw-text-xs tw-text-neutral-400">Nhãn huấn luyện: các đánh giá AI (LLM) đã phân loại và các phân loại người bán tự sửa trong Trung tâm đánh giá.
          Mô hình TF-IDF + Logistic Regression chạy trên CPU, phân loại tức thì và không tốn phí API.</p>
        <p v-if="trainMsg" class="tw-mb-0 tw-mt-2 tw-text-sm" :class="trainOk ? 'tw-text-green-700' : 'tw-text-red-600'">{{ trainMsg }}</p>
      </section>

      <!-- semantic search index (embeddings, pgvector) - Tailwind utilities -->
      <section v-if="emb" class="sc-card">
        <div class="sc-card-head tw-items-center">
          <h2 class="sc-card-title">Tìm Kiếm Ngữ Nghĩa (Embeddings)</h2>
          <button type="button" class="btn sm ghost tw-ml-auto" :disabled="reindexing" @click="reindex">Tạo lại chỉ mục</button>
        </div>
        <div class="tw-grid tw-grid-cols-2 tw-gap-3 tw-text-[13px] md:tw-grid-cols-4">
          <div><div class="tw-text-neutral-500">Kho vector</div><b>{{ emb.engine }}</b></div>
          <div><div class="tw-text-neutral-500">Mô hình</div><b>{{ emb.model }}</b> · {{ emb.dimensions }} chiều</div>
          <div><div class="tw-text-neutral-500">Đã lập chỉ mục</div><b>{{ emb.indexed }}</b> / {{ emb.activeProducts }} sản phẩm đang bán</div>
          <div><div class="tw-text-neutral-500">Trạng thái</div>
            <b :class="emb.available ? 'tw-text-green-600' : 'tw-text-amber-700'">{{ emb.available ? 'Đang hoạt động' : 'Cần bật AI (OpenAI)' }}</b></div>
        </div>
        <p v-if="emb.error" class="tw-mb-0 tw-mt-2 tw-text-xs tw-text-red-600">Lỗi lần gần nhất: {{ emb.error }}</p>
        <p class="tw-mb-0 tw-mt-2 tw-text-xs tw-text-neutral-400">
          Sản phẩm mới hoặc vừa sửa được tạo vector tự động mỗi phút. Dùng cho trang tìm kiếm ("Có thể bạn đang tìm") và bước truy xuất
          của chatbot (RAG). Với PostgreSQL có extension pgvector (Neon, Supabase, Docker), việc tìm vector gần nhất do cơ sở dữ liệu thực hiện.
        </p>
      </section>

      <!-- budget -->
      <section class="sc-card">
        <div class="sc-card-head"><h2 class="sc-card-title">Ngân Sách & Bảng Giá</h2></div>
        <div v-if="usage" class="month">
          <div class="month-top">
            <span>Tháng {{ usage.month.month }}: <b>${{ fmtUsd(usage.month.costUsd) }}</b> ({{ formatVnd(usage.month.costVnd) }})
              <template v-if="usage.month.budgetUsd"> / ${{ usage.month.budgetUsd }} ngân sách</template></span>
            <span class="faint">Dự kiến cả tháng: ${{ fmtUsd(usage.month.projectedUsd) }} ({{ formatVnd(usage.month.projectedVnd) }})</span>
          </div>
          <div v-if="usage.month.budgetUsd" class="bar"><span :class="{ over: usage.month.usedPercent >= 100, near: usage.month.usedPercent >= form.alertPercent }"
                                                              :style="{ width: Math.min(100, usage.month.usedPercent) + '%' }"></span></div>
          <p v-if="usage.month.blocked" class="note bad">Đã dùng hết ngân sách: các tính năng AI đang tạm dừng đến hết tháng.</p>
        </div>
        <div class="grid four">
          <label>Ngân sách mỗi tháng (USD)<input v-model="form.budgetUsd" type="number" min="0" step="1" placeholder="Không giới hạn" /></label>
          <label>Cảnh báo khi đạt (%)<input v-model.number="form.alertPercent" type="number" min="10" max="100" /></label>
          <label>Tỷ giá (₫/USD)<input v-model.number="form.usdRate" type="number" min="1000" /></label>
          <label class="check"><input v-model="form.hardStop" type="checkbox" /> Tự dừng AI khi hết ngân sách</label>
        </div>
        <table class="sc-table prices">
          <thead><tr><th>Model</th><th class="r">Giá input / 1 triệu token (USD)</th><th class="r">Giá output / 1 triệu token (USD)</th></tr></thead>
          <tbody>
            <tr v-for="(p, m) in form.pricing" :key="m">
              <td>{{ m }}<span v-if="m === activeModel" class="tag">đang dùng</span></td>
              <td class="r"><input v-model="p.input" type="number" min="0" step="0.01" /></td>
              <td class="r"><input v-model="p.output" type="number" min="0" step="0.01" /></td>
            </tr>
          </tbody>
        </table>
        <div class="actions">
          <span v-if="msg" class="ok">{{ msg }}</span>
          <span v-if="error" class="bad">{{ error }}</span>
          <button type="button" class="btn sm" :disabled="saving" @click="saveBudget">Lưu ngân sách & bảng giá</button>
        </div>
      </section>

      <!-- usage -->
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Chi Phí Sử Dụng LLM</h2>
          <div class="head-right">
            <button v-for="d in [7, 30, 90]" :key="d" type="button" class="chip" :class="{ on: days === d }" @click="days = d; loadUsage()">{{ d }} ngày</button>
          </div>
        </div>
        <div v-if="usage" class="kpis">
          <div class="kpi"><span>Lượt gọi</span><b>{{ num(usage.summary.calls) }}</b><small>{{ usage.summary.successRate ?? '–' }}% thành công</small></div>
          <div class="kpi"><span>Chi phí</span><b>${{ fmtUsd(usage.summary.costUsd) }}</b><small>{{ formatVnd(usage.summary.costVnd) }}</small></div>
          <div class="kpi"><span>Token</span><b>{{ num(usage.summary.promptTokens + usage.summary.completionTokens) }}</b><small>{{ num(usage.summary.promptTokens) }} vào · {{ num(usage.summary.completionTokens) }} ra</small></div>
          <div class="kpi"><span>Chi phí / lượt</span><b>{{ formatVnd(usage.summary.costPerCallVnd) }}</b><small>trung bình</small></div>
          <div class="kpi"><span>Thời gian phản hồi</span><b>{{ usage.summary.avgLatencyMs ?? '–' }} ms</b><small>p95 {{ usage.summary.p95LatencyMs ?? '–' }} ms</small></div>
          <div class="kpi"><span>Lỗi / bị chặn</span><b :class="{ bad: usage.summary.failed }">{{ usage.summary.failed }} / {{ usage.summary.blocked }}</b><small>lỗi API / do tắt hoặc hết ngân sách</small></div>
        </div>
        <TrendChart v-if="usage" :labels="usage.series.map(s => s.label)" :height="220" :series="[
          { name: 'Chi phí (USD)', values: usage.series.map(s => Number(s.costUsd)), color: '#6941c6', type: 'bar', format: (v) => '$' + fmtUsd(v) },
          { name: 'Lượt gọi', values: usage.series.map(s => s.calls), color: '#ee4d2d', type: 'line', axis: 'right', integer: true }]"
          :left-tick-format="(v) => '$' + v" />
        <div class="two">
          <div>
            <h4>Theo tính năng</h4>
            <table class="sc-table">
              <thead><tr><th>Tính năng</th><th class="r">Lượt</th><th class="r">Token</th><th class="r">Chi phí</th><th class="r">Tỷ trọng</th></tr></thead>
              <tbody>
                <tr v-for="f in usage?.byFeature || []" :key="f.key">
                  <td>{{ f.label }}<span v-if="f.failed" class="bad small"> · {{ f.failed }} lỗi</span></td>
                  <td class="r">{{ num(f.calls) }}</td><td class="r">{{ num(f.tokens) }}</td>
                  <td class="r">${{ fmtUsd(f.costUsd) }}</td><td class="r">{{ f.share }}%</td>
                </tr>
                <tr v-if="usage && !usage.byFeature.length"><td colspan="5" class="faint">Chưa có lượt gọi nào</td></tr>
              </tbody>
            </table>
          </div>
          <div>
            <h4>Theo model</h4>
            <table class="sc-table">
              <thead><tr><th>Model</th><th class="r">Lượt</th><th class="r">Token</th><th class="r">Chi phí</th></tr></thead>
              <tbody>
                <tr v-for="m in usage?.byModel || []" :key="m.key"><td>{{ m.key }}</td><td class="r">{{ num(m.calls) }}</td><td class="r">{{ num(m.tokens) }}</td><td class="r">${{ fmtUsd(m.costUsd) }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="calls-head">
          <h4>Lượt gọi gần đây</h4>
          <nav class="sc-tabs">
            <a v-for="t in CALL_TABS" :key="t.key" href="#" :class="{ active: callStatus === t.key }" @click.prevent="callStatus = t.key; loadCalls(0)">{{ t.label }}</a>
          </nav>
        </div>
        <table class="sc-table">
          <thead><tr><th>Thời gian</th><th>Tính năng</th><th>Model</th><th class="r">Token vào / ra</th><th class="r">Chi phí</th><th class="r">Thời gian</th><th>Kết quả</th></tr></thead>
          <tbody>
            <tr v-for="c in calls.items" :key="c.id">
              <td class="nowrap faint">{{ formatDateTime(c.createdAt) }}</td>
              <td>{{ c.featureLabel }}</td>
              <td>{{ c.model }}</td>
              <td class="r">{{ num(c.promptTokens) }} / {{ num(c.completionTokens) }}<span v-if="c.estimated" class="faint" title="Ước tính theo độ dài văn bản"> ~</span></td>
              <td class="r">${{ fmtUsd(c.costUsd) }}</td>
              <td class="r">{{ c.latencyMs != null ? c.latencyMs + ' ms' : '–' }}</td>
              <td><span class="st" :class="c.status">{{ STATUS[c.status] }}</span><div v-if="c.error" class="err" :title="c.error">{{ c.error }}</div></td>
            </tr>
            <tr v-if="!calls.items.length"><td colspan="7" class="faint">Không có lượt gọi nào</td></tr>
          </tbody>
        </table>
        <PaginationBar :page="calls.page" :total-pages="calls.totalPages" @change="loadCalls" />
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import TrendChart from '../../components/analytics/TrendChart.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { aiConfig, saveAiConfig, testAi, aiUsage, aiCalls, aiEmbeddings, reindexEmbeddings } from '../../api/admin'
import { reviewAiStatus, trainReviewAi } from '../../api/reviewHub'
import { isoDate, daysAgo } from '../../composables/useAnalyticsRange'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const STATUS = { SUCCESS: 'Thành công', FAILED: 'Lỗi', BLOCKED: 'Bị chặn' }
const CALL_TABS = [{ key: 'ALL', label: 'Tất cả' }, { key: 'FAILED', label: 'Lỗi' }, { key: 'BLOCKED', label: 'Bị chặn' }]
const cfg = ref(null)
const form = ref({})
const usage = ref(null)
const calls = ref({ items: [], page: 0, totalPages: 0 })
const callStatus = ref('ALL')
const days = ref(30)
const test = ref(null)
const testing = ref(false)
const saving = ref(false)
const msg = ref('')
const error = ref('')

const activeModel = computed(() => (form.value.provider === 'huggingface' ? 'vistral-7b-shoppi'
  : form.value.provider === 'claude' ? form.value.claudeModel : form.value.openaiModel))
const num = (v) => Number(v || 0).toLocaleString('vi-VN')
const fmtUsd = (v) => {
  const n = Number(v || 0)
  return n === 0 ? '0' : n < 0.01 ? n.toFixed(4) : n.toLocaleString('en-US', { maximumFractionDigits: 2 })
}
const keyText = (k) => ({ ADMIN: `Đã lưu ${k.masked} (nhập trên trang này)`, ENV: `Từ biến môi trường: ${k.masked}`,
  NONE: 'Chưa có', UNREADABLE: 'Không giải mã được (khóa bí mật của máy chủ đã đổi) – hãy nhập lại' })[k.source]

function fill(c) {
  cfg.value = c
  const pricing = {}
  Object.entries(c.pricing).forEach(([m, p]) => { pricing[m] = { input: p.input, output: p.output } })
  form.value = { provider: c.provider, openaiModel: c.openaiModel, openaiUrl: c.openaiUrl, hfUrl: c.hfUrl || '', openaiKey: '', hfToken: '',
    claudeModel: c.claudeModel, claudeUrl: c.claudeUrl, claudeKey: '',
    budgetUsd: c.budgetUsd ?? '', alertPercent: c.alertPercent, hardStop: c.hardStop, usdRate: c.usdRate, pricing }
}

async function save(body, okText) {
  saving.value = true
  error.value = ''
  msg.value = ''
  try {
    fill((await saveAiConfig(body)).data)
    if (okText) msg.value = okText
    loadUsage()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}

function saveConnection() {
  const f = form.value
  const body = { provider: f.provider, openaiModel: f.openaiModel, claudeModel: f.claudeModel }
  if (cfg.value.canEditKeys) {
    body.openaiUrl = f.openaiUrl
    body.claudeUrl = f.claudeUrl
    if (f.claudeKey) body.claudeKey = f.claudeKey
    body.hfUrl = f.hfUrl
    if (f.openaiKey) body.openaiKey = f.openaiKey
    if (f.hfToken) body.hfToken = f.hfToken
  }
  save(body)
}
const clearKey = (field) => confirm('Xóa key đã lưu? Hệ thống sẽ dùng biến môi trường (nếu có).') && save({ [field]: true })
const saveBudget = () => save({ budgetUsd: form.value.budgetUsd === '' ? null : form.value.budgetUsd, alertPercent: form.value.alertPercent,
  hardStop: form.value.hardStop, usdRate: form.value.usdRate, pricing: form.value.pricing }, 'Đã lưu ✓')

async function runTest() {
  testing.value = true
  test.value = null
  try {
    test.value = (await testAi()).data
    loadUsage()
  } catch (e) {
    test.value = { ok: false, message: extractError(e) }
  } finally {
    testing.value = false
  }
}

const loadUsage = () => aiUsage(isoDate(daysAgo(days.value - 1)), isoDate(new Date())).then(({ data }) => { usage.value = data; loadCalls(0) }).catch(() => {})
const loadCalls = (p = 0) => aiCalls({ status: callStatus.value, page: p }).then(({ data }) => { calls.value = data }).catch(() => {})

const rai = ref(null)
const training = ref(false)
const trainMsg = ref('')
const trainOk = ref(true)
const loadRai = () => reviewAiStatus().then(({ data }) => { rai.value = data }).catch(() => {})
async function train() {
  training.value = true
  trainMsg.value = ''
  try {
    const { data } = await trainReviewAi()
    trainOk.value = true
    trainMsg.value = `Đã huấn luyện trên ${data.samples} đánh giá` + (data.metrics?.issuesMicroF1 != null ? `, F1 = ${data.metrics.issuesMicroF1}` : '')
    loadRai()
  } catch (e) {
    trainOk.value = false
    trainMsg.value = extractError(e)
  } finally {
    training.value = false
  }
}
const emb = ref(null)
const reindexing = ref(false)
const loadEmb = () => aiEmbeddings().then(({ data }) => { emb.value = data }).catch(() => {})
async function reindex() {
  if (!confirm('Xóa và tạo lại vector cho toàn bộ sản phẩm?')) return
  reindexing.value = true
  try {
    await reindexEmbeddings()
    await loadEmb()
  } finally {
    reindexing.value = false
  }
}

onMounted(async () => {
  try {
    fill((await aiConfig()).data)
    loadUsage()
    loadEmb()
    loadRai()
  } catch (e) {
    error.value = extractError(e)
  }
})
</script>

<style scoped>
.sc-card-head { align-items: center; }
.head-right { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.status { font-size: 13px; font-weight: 600; }
.status.ok, .ok { color: #1e9e4a; }
.status.warn { color: #b35c00; }
.bad { color: #d33; }
.small { font-size: 12px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.note { padding: 8px 12px; border-radius: 4px; font-size: 13px; margin: 0 0 12px; }
.note.warn { background: #fff7e6; color: #b35c00; }
.note.bad { background: #fdecea; }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.grid.four { grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: end; }
.grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--color-text-muted); }
.check { flex-direction: row !important; align-items: center; padding-bottom: 10px; }
.check input { width: auto; }
.key-row { display: grid; grid-template-columns: 1fr 1.3fr auto; gap: 12px; align-items: center; padding: 10px 12px; border: 1px solid var(--color-border); border-radius: 6px; margin-bottom: 12px; font-size: 14px; }
.actions { display: flex; justify-content: flex-end; align-items: center; gap: 10px; flex-wrap: wrap; }
.test { font-size: 13px; }
.test.bad { max-width: 520px; }
.switch { width: 46px; height: 26px; border-radius: 999px; border: none; background: #ccc; position: relative; cursor: pointer; }
.switch span { position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; border-radius: 50%; background: white; transition: left 0.15s; }
.switch.on { background: #1e9e4a; }
.switch.on span { left: 23px; }
.link { background: none; border: none; cursor: pointer; font-size: 13px; color: #2673dd; }
.link.danger { color: #d33; }
.month { margin-bottom: 14px; font-size: 14px; }
.month-top { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
.bar { height: 8px; background: #eee; border-radius: 4px; overflow: hidden; }
.bar span { display: block; height: 100%; background: #1e9e4a; }
.bar span.near { background: #f59e0b; }
.bar span.over { background: #d33; }
.prices input { width: 110px; text-align: right; }
.tag { margin-left: 6px; font-size: 11px; background: #eaf7ef; color: #1e9e4a; padding: 1px 6px; border-radius: 3px; }
.chip { border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chip.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.kpis { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
.kpi span, .kpi small { font-size: 12px; color: var(--color-text-muted); }
.kpi b { font-size: 18px; }
.two { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; margin-top: 12px; }
h4 { margin: 8px 0; font-size: 14px; }
.calls-head { display: flex; align-items: center; gap: 16px; margin-top: 16px; }
.calls-head .sc-tabs { margin: 0; }
.r { text-align: right; white-space: nowrap; }
.nowrap { white-space: nowrap; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #eaf7ef; color: #1e9e4a; }
.st.FAILED { background: #fdecea; color: #d33; }
.st.BLOCKED { background: #fff4e5; color: #b35c00; }
.err { font-size: 12px; color: #d33; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 1100px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } .two, .grid.four { grid-template-columns: 1fr 1fr; } }
</style>
