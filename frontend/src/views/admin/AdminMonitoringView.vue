<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Giám Sát Hệ Thống</h2>
        <span v-if="health" class="overall" :class="health.status">{{ OVERALL[health.status] }}</span>
        <div class="head-right">
          <label class="faint"><input v-model="auto" type="checkbox" /> Tự làm mới 15 giây</label>
          <button type="button" class="btn sm ghost" @click="refresh">Làm mới</button>
        </div>
      </div>
      <nav class="sc-tabs">
        <a v-for="t in TABS" :key="t.key" href="#" :class="{ active: tab === t.key }" @click.prevent="tab = t.key">
          {{ t.label }}<span v-if="t.key === 'errors' && counts.OPEN" class="badge">{{ counts.OPEN }}</span>
        </a>
      </nav>

      <!-- ===== overview ===== -->
      <template v-if="tab === 'overview'">
        <div v-if="!health" class="sc-empty">Đang tải...</div>
        <template v-else>
          <div class="checks">
            <div v-for="c in health.checks" :key="c.name" class="check" :class="c.status">
              <i></i>
              <div><b>{{ c.name }}</b><span>{{ c.detail }}</span></div>
            </div>
          </div>
          <div class="info">
            <div>
              <h4>Ứng dụng</h4>
              <dl>
                <dt>Thời gian chạy</dt><dd>{{ health.app.uptime }}</dd>
                <dt>Bộ nhớ heap</dt><dd>{{ health.app.heapUsedMb }} / {{ health.app.heapMaxMb }} MB ({{ health.app.heapPercent }}%)</dd>
                <dt>CPU tiến trình</dt><dd>{{ health.app.cpuPercent ?? '–' }}%</dd>
                <dt>Luồng</dt><dd>{{ health.app.threads }} · {{ health.app.processors }} CPU</dd>
                <dt>Phiên bản</dt><dd>Java {{ health.app.javaVersion }} · Spring Boot {{ health.app.springBootVersion }}</dd>
              </dl>
            </div>
            <div>
              <h4>Cơ sở dữ liệu</h4>
              <dl>
                <dt>Phản hồi</dt><dd>{{ health.database.pingMs ?? '–' }} ms</dd>
                <dt>Dung lượng</dt><dd>{{ health.database.sizeMb ?? '–' }} MB</dd>
                <dt>Kết nối</dt><dd>{{ health.database.poolActive ?? '–' }} đang dùng · {{ health.database.poolIdle ?? '–' }} rảnh / {{ health.database.poolMax ?? '–' }}</dd>
                <dt>PostgreSQL</dt><dd>{{ health.database.version || health.database.error }}</dd>
              </dl>
            </div>
            <div>
              <h4>Lưu trữ & lưu lượng</h4>
              <dl>
                <dt>Ổ đĩa tệp tải lên</dt><dd>{{ health.disk.freeGb }} / {{ health.disk.totalGb }} GB trống ({{ health.disk.freePercent }}%)</dd>
                <dt>Yêu cầu / phút</dt><dd>{{ health.traffic.perMinute }} (60 phút qua)</dd>
                <dt>Thời gian xử lý TB</dt><dd>{{ health.traffic.avgMs ?? '–' }} ms</dd>
                <dt>Lỗi 5xx</dt><dd>{{ health.traffic.errorRate }}%</dd>
              </dl>
            </div>
          </div>
          <h4>Lưu lượng 60 phút qua</h4>
          <TrendChart v-if="perf60" :labels="perf60.series.map(s => hm(s.label))" :height="200" :series="[
            { name: 'Yêu cầu', values: perf60.series.map(s => s.requests), color: '#2673dd', type: 'bar', integer: true },
            { name: 'Lỗi 5xx', values: perf60.series.map(s => s.errors), color: '#d33', type: 'line', integer: true },
            { name: 'Thời gian TB (ms)', values: perf60.series.map(s => s.avgMs), color: '#f59e0b', type: 'line', axis: 'right', integer: true }]" />
        </template>
      </template>

      <!-- ===== errors ===== -->
      <template v-if="tab === 'errors'">
        <div class="toolbar">
          <div class="pills">
            <button v-for="s in ERROR_TABS" :key="s.key" type="button" class="chip" :class="{ on: errStatus === s.key }" @click="errStatus = s.key; loadErrors(0)">
              {{ s.label }} ({{ s.key === 'ALL' ? (counts.OPEN || 0) + (counts.RESOLVED || 0) + (counts.IGNORED || 0) : counts[s.key] || 0 }})
            </button>
          </div>
          <input v-model.trim="errQ" type="search" placeholder="Tìm theo loại lỗi, nội dung, đường dẫn" @input="onErrSearch" />
        </div>
        <div v-if="!errors.items.length" class="sc-empty">{{ errStatus === 'OPEN' ? 'Không có lỗi nào chưa xử lý 👍' : 'Không có lỗi nào' }}</div>
        <table v-else class="sc-table">
          <thead><tr><th>Mã</th><th>Lỗi</th><th>Yêu cầu gần nhất</th><th class="r">Số lần</th><th>Lần đầu / gần nhất</th><th>Trạng thái</th></tr></thead>
          <tbody>
            <tr v-for="e in errors.items" :key="e.id" class="row" @click="openError(e.id)">
              <td class="mono">{{ e.code }}</td>
              <td><b>{{ e.simpleType }}</b><div class="msg">{{ e.message }}</div><div class="faint mono">{{ e.location }}</div></td>
              <td class="mono small">{{ e.lastMethod }} {{ e.lastPath }}</td>
              <td class="r"><b>{{ num(e.occurrences) }}</b></td>
              <td class="faint nowrap">{{ formatDateTime(e.firstSeen) }}<br />{{ formatDateTime(e.lastSeen) }}</td>
              <td><span class="st" :class="e.status">{{ ERR_STATUS[e.status] }}</span><div v-if="e.reopenCount" class="bad small">Tái phát {{ e.reopenCount }} lần</div></td>
            </tr>
          </tbody>
        </table>
        <PaginationBar :page="errors.page" :total-pages="errors.totalPages" @change="loadErrors" />
      </template>

      <!-- ===== sign-ins ===== -->
      <template v-if="tab === 'logins'">
        <template v-if="sec">
          <div class="kpis">
            <div class="kpi"><span>Đăng nhập thành công (24 giờ)</span><b>{{ num(sec.success) }}</b></div>
            <div class="kpi"><span>Đăng nhập sai</span><b :class="{ bad: sec.failed }">{{ num(sec.failed) }}</b><small>sai mật khẩu, sai email, sai mã 2 lớp</small></div>
            <div class="kpi"><span>Bị chặn</span><b>{{ num(sec.counts.RATE_LIMITED + sec.counts.TEMP_LOCKED) }}</b><small>theo IP / khóa tạm tài khoản</small></div>
            <div class="kpi"><span>Quy tắc</span><small>Khóa {{ sec.rules.lockMinutes }} phút sau {{ sec.rules.maxFailedLogins }} lần sai · chặn IP sau
              {{ sec.rules.ipFailLimit }} lần sai / 15 phút<template v-if="sec.rules.requireAdmin2fa"> · admin bắt buộc 2 lớp</template></small>
              <router-link :to="{ name: 'admin-settings' }" class="small">Đổi quy tắc ›</router-link></div>
          </div>
          <div class="two">
            <div>
              <h4>Địa chỉ IP đăng nhập sai nhiều nhất</h4>
              <table class="sc-table">
                <thead><tr><th>IP</th><th class="r">Lần sai</th><th class="r">Số tài khoản thử</th><th></th></tr></thead>
                <tbody>
                  <tr v-for="t in sec.topIps" :key="t.ip"><td class="mono">{{ t.ip }}</td><td class="r">{{ t.failures }}</td><td class="r">{{ t.accounts }}</td>
                    <td><span v-if="t.blocked" class="st">Đang bị chặn</span></td></tr>
                  <tr v-if="!sec.topIps.length"><td colspan="4" class="faint">Không có</td></tr>
                </tbody>
              </table>
            </div>
            <div>
              <h4>Tài khoản đang khóa tạm (nhập sai nhiều lần)</h4>
              <table class="sc-table">
                <tbody>
                  <tr v-for="u in sec.lockedAccounts" :key="u.id"><td>{{ u.fullName }}<div class="faint">{{ u.email }}</div></td>
                    <td class="faint">đến {{ formatDateTime(u.until) }}</td>
                    <td><button type="button" class="link-btn" @click="onUnlock(u)">Mở khóa</button></td></tr>
                  <tr v-if="!sec.lockedAccounts.length"><td class="faint">Không có</td></tr>
                </tbody>
              </table>
            </div>
          </div>
          <h4>Lần đăng nhập không thành công gần đây</h4>
          <table class="sc-table">
            <thead><tr><th>Thời gian</th><th>Email</th><th>Kết quả</th><th>IP</th><th>Thiết bị</th></tr></thead>
            <tbody>
              <tr v-for="l in failedLogins" :key="l.id"><td class="faint nowrap">{{ formatDateTime(l.time) }}</td><td>{{ l.email }}</td>
                <td><span class="st">{{ LOGIN_RESULT[l.result] || l.result }}</span></td><td class="mono small">{{ l.ip }}</td><td class="small">{{ l.device }}</td></tr>
              <tr v-if="!failedLogins.length"><td colspan="5" class="faint">Không có</td></tr>
            </tbody>
          </table>
        </template>
      </template>

      <!-- ===== performance ===== -->
      <template v-if="tab === 'perf'">
        <div class="toolbar">
          <div class="pills">
            <button v-for="w in WINDOWS" :key="w.m" type="button" class="chip" :class="{ on: windowMin === w.m }" @click="windowMin = w.m; loadPerf()">{{ w.label }}</button>
          </div>
          <span class="faint">Số liệu tính từ lần khởi động gần nhất của máy chủ</span>
        </div>
        <template v-if="perf">
          <div class="kpis">
            <div class="kpi"><span>Yêu cầu</span><b>{{ num(perf.requests) }}</b><small>{{ perf.perMinute }} / phút</small></div>
            <div class="kpi"><span>Thời gian xử lý TB</span><b>{{ perf.avgMs ?? '–' }} ms</b><small>chậm nhất {{ perf.maxMs }} ms</small></div>
            <div class="kpi"><span>Lỗi máy chủ (5xx)</span><b :class="{ bad: perf.errors5xx }">{{ perf.errors5xx }}</b><small>{{ perf.errorRate }}% yêu cầu</small></div>
            <div class="kpi"><span>Lỗi phía người dùng (4xx)</span><b>{{ perf.errors4xx }}</b><small>sai dữ liệu, chưa đăng nhập, không có quyền...</small></div>
          </div>
          <TrendChart :labels="perf.series.map(s => hm(s.label))" :height="200" :series="[
            { name: 'Yêu cầu', values: perf.series.map(s => s.requests), color: '#2673dd', type: 'bar', integer: true },
            { name: 'Thời gian TB (ms)', values: perf.series.map(s => s.avgMs), color: '#f59e0b', type: 'line', axis: 'right', integer: true }]" />
          <div class="two">
            <div>
              <h4>Gọi nhiều nhất</h4>
              <EndpointTable :rows="perf.busiest" />
            </div>
            <div>
              <h4>Chậm nhất (trung bình, từ 3 lượt)</h4>
              <EndpointTable :rows="perf.slowest" />
            </div>
          </div>
          <template v-if="perf.failing.length">
            <h4>Có lỗi 5xx</h4>
            <EndpointTable :rows="perf.failing" />
          </template>
          <h4>Yêu cầu chậm gần đây (≥ {{ perf.slowThresholdMs }} ms)</h4>
          <table class="sc-table">
            <thead><tr><th>Thời điểm</th><th>Yêu cầu</th><th class="r">Mã</th><th class="r">Thời gian</th></tr></thead>
            <tbody>
              <tr v-for="(s, i) in perf.slowRequests" :key="i"><td class="faint nowrap">{{ formatDateTime(s.at) }}</td><td class="mono small">{{ s.method }} {{ s.path }}</td><td class="r">{{ s.status }}</td><td class="r"><b>{{ num(s.ms) }} ms</b></td></tr>
              <tr v-if="!perf.slowRequests.length"><td colspan="4" class="faint">Chưa có yêu cầu nào chậm</td></tr>
            </tbody>
          </table>
        </template>
      </template>

      <!-- ===== logs ===== -->
      <template v-if="tab === 'logs'">
        <div class="toolbar">
          <div class="pills">
            <button v-for="l in LEVELS" :key="l" type="button" class="chip" :class="{ on: level === l }" @click="level = l; loadLogs()">{{ l }}+</button>
          </div>
          <input v-model.trim="logQ" type="search" placeholder="Tìm trong log" @input="onLogSearch" />
          <span v-if="logs" class="faint">{{ logs.buffered }}/{{ logs.capacity }} dòng gần nhất · từ khi khởi động:
            <template v-for="(n, l) in logs.totalsSinceStart" :key="l">{{ l }} {{ n }} </template></span>
        </div>
        <div class="logs">
          <div v-for="e in logs?.entries || []" :key="e.seq" class="log" :class="e.level">
            <span class="t">{{ new Date(e.time).toLocaleTimeString('vi-VN') }}</span>
            <span class="lv">{{ e.level }}</span>
            <span class="lg">{{ e.logger }}</span>
            <span class="m">{{ e.message }}</span>
            <pre v-if="e.error" class="ex">{{ e.error }}</pre>
          </div>
          <div v-if="logs && !logs.entries.length" class="sc-empty">Không có dòng log nào</div>
        </div>
      </template>
    </section>

    <BaseModal v-if="err" :title="`${err.code} · ${err.simpleType}`" :width="860" @close="closeError">
      <dl class="err-info">
        <dt>Nội dung</dt><dd>{{ err.message || '(không có)' }}</dd>
        <dt>Vị trí</dt><dd class="mono">{{ err.location }}</dd>
        <dt>Yêu cầu gần nhất</dt><dd class="mono">{{ err.lastMethod }} {{ err.lastPath }}<template v-if="err.lastUserId"> · người dùng #{{ err.lastUserId }}</template></dd>
        <dt>Số lần</dt><dd>{{ num(err.occurrences) }} (từ {{ formatDateTime(err.firstSeen) }} đến {{ formatDateTime(err.lastSeen) }})</dd>
        <dt>Trạng thái</dt><dd>{{ ERR_STATUS[err.status] }}<template v-if="err.resolvedByName"> · {{ err.resolvedByName }}, {{ formatDateTime(err.resolvedAt) }}</template></dd>
        <template v-if="err.note"><dt>Ghi chú</dt><dd>{{ err.note }}</dd></template>
      </dl>
      <pre class="stack">{{ err.stackTrace }}</pre>
      <input v-model.trim="errNote" class="note-in" maxlength="500" placeholder="Ghi chú (nguyên nhân, cách xử lý...)" />
      <template #footer>
        <button v-if="err.status !== 'OPEN'" type="button" class="btn ghost" @click="setStatus('OPEN')">Mở lại</button>
        <button v-if="err.status !== 'IGNORED'" type="button" class="btn ghost" @click="setStatus('IGNORED')">Bỏ qua</button>
        <button v-if="err.status !== 'RESOLVED'" type="button" class="btn" @click="setStatus('RESOLVED')">Đánh dấu đã xử lý</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, watch, h, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseModal from '../../components/BaseModal.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import TrendChart from '../../components/analytics/TrendChart.vue'
import { systemHealth, systemMetrics, systemLogs, systemErrors, systemErrorCounts, systemError, setErrorStatus, securityOverview, securityLogins,
  unlockLogin } from '../../api/admin'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const TABS = [{ key: 'overview', label: 'Tổng quan' }, { key: 'errors', label: 'Lỗi hệ thống' }, { key: 'logins', label: 'Đăng nhập' },
  { key: 'perf', label: 'Hiệu năng API' }, { key: 'logs', label: 'Log' }]
const LOGIN_RESULT = { SUCCESS: 'Thành công', BAD_PASSWORD: 'Sai mật khẩu', UNKNOWN_EMAIL: 'Email không tồn tại', LOCKED: 'Tài khoản bị khóa',
  TEMP_LOCKED: 'Đang khóa tạm', RATE_LIMITED: 'Bị chặn theo IP', TWO_FACTOR_FAILED: 'Sai mã 2 lớp', TWO_FACTOR_REQUIRED: 'Chờ mã 2 lớp' }
const OVERALL = { UP: '● Hoạt động bình thường', WARN: '● Cần chú ý', DOWN: '● Có sự cố' }
const ERR_STATUS = { OPEN: 'Chưa xử lý', RESOLVED: 'Đã xử lý', IGNORED: 'Bỏ qua' }
const ERROR_TABS = [{ key: 'OPEN', label: 'Chưa xử lý' }, { key: 'RESOLVED', label: 'Đã xử lý' }, { key: 'IGNORED', label: 'Bỏ qua' }, { key: 'ALL', label: 'Tất cả' }]
const WINDOWS = [{ m: 60, label: '1 giờ' }, { m: 360, label: '6 giờ' }, { m: 1440, label: '24 giờ' }]
const LEVELS = ['INFO', 'WARN', 'ERROR']

// a small table of endpoint statistics
const EndpointTable = (props) => h('table', { class: 'sc-table' }, [
  h('thead', h('tr', [h('th', 'Endpoint'), h('th', { class: 'r' }, 'Lượt'), h('th', { class: 'r' }, 'TB'), h('th', { class: 'r' }, 'Max'), h('th', { class: 'r' }, '4xx/5xx')])),
  h('tbody', props.rows.length ? props.rows.map(r => h('tr', { key: r.endpoint }, [
    h('td', { class: 'mono small' }, r.endpoint), h('td', { class: 'r' }, r.count), h('td', { class: 'r' }, r.avgMs + ' ms'),
    h('td', { class: 'r' }, r.maxMs + ' ms'), h('td', { class: 'r' + (r.errors5 ? ' bad' : '') }, r.errors4 + ' / ' + r.errors5)
  ])) : [h('tr', h('td', { colspan: 5, class: 'faint' }, 'Chưa có dữ liệu'))])
])
EndpointTable.props = ['rows']

const route = useRoute()
const router = useRouter()
const tab = ref(route.query.error ? 'errors' : 'overview')
const health = ref(null)
const perf60 = ref(null)
const perf = ref(null)
const windowMin = ref(60)
const errors = ref({ items: [], page: 0, totalPages: 0 })
const counts = ref({})
const errStatus = ref('OPEN')
const errQ = ref('')
const err = ref(null)
const errNote = ref('')
const logs = ref(null)
const level = ref('INFO')
const logQ = ref('')
const auto = ref(false)
const sec = ref(null)
const failedLogins = ref([])
let timer = null
let searchTimer = null

const num = (v) => Number(v || 0).toLocaleString('vi-VN')
const hm = (t) => new Date(t).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })

const loadHealth = () => Promise.all([systemHealth(), systemMetrics(60)]).then(([a, b]) => { health.value = a.data; perf60.value = b.data }).catch(() => {})
const loadPerf = () => systemMetrics(windowMin.value).then(({ data }) => { perf.value = data }).catch(() => {})
const loadCounts = () => systemErrorCounts().then(({ data }) => { counts.value = data }).catch(() => {})
const loadErrors = (p = 0) => systemErrors({ status: errStatus.value, q: errQ.value, page: p }).then(({ data }) => { errors.value = data }).catch(() => {})
const loadLogs = () => systemLogs({ level: level.value, q: logQ.value }).then(({ data }) => { logs.value = data }).catch(() => {})

function refresh() {
  loadCounts()
  if (tab.value === 'overview') loadHealth()
  if (tab.value === 'errors') loadErrors(errors.value.page)
  if (tab.value === 'perf') loadPerf()
  if (tab.value === 'logs') loadLogs()
  if (tab.value === 'logins') loadLogins()
}
function loadLogins() {
  securityOverview(24).then(({ data }) => { sec.value = data }).catch(() => {})
  securityLogins({ failedOnly: true }).then(({ data }) => { failedLogins.value = data.items }).catch(() => {})
}
async function onUnlock(u) {
  await unlockLogin(u.id)
  loadLogins()
}
const onErrSearch = () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => loadErrors(0), 300) }
const onLogSearch = () => { clearTimeout(searchTimer); searchTimer = setTimeout(loadLogs, 300) }

async function openError(id) {
  try {
    err.value = (await systemError(id)).data
    errNote.value = err.value.note || ''
  } catch (e) {
    alert(extractError(e))
  }
}
function closeError() {
  err.value = null
  if (route.query.error) router.replace({ query: {} })
}
async function setStatus(status) {
  try {
    err.value = (await setErrorStatus(err.value.id, status, errNote.value)).data
    loadErrors(errors.value.page)
    loadCounts()
  } catch (e) {
    alert(extractError(e))
  }
}

watch(tab, refresh)
watch(auto, (on) => {
  clearInterval(timer)
  if (on) timer = setInterval(refresh, 15000)
})
onMounted(() => {
  refresh()
  if (route.query.error) openError(Number(route.query.error))
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.sc-card-head { align-items: center; }
.head-right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.head-right label { display: flex; align-items: center; gap: 4px; }
.head-right input { width: auto; }
.overall { font-weight: 600; font-size: 14px; }
.overall.UP { color: #1e9e4a; }
.overall.WARN { color: #b35c00; }
.overall.DOWN { color: #d33; }
.badge { margin-left: 6px; background: #d33; color: white; border-radius: 999px; font-size: 11px; padding: 0 6px; }
.checks { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-bottom: 16px; }
.check { display: flex; gap: 10px; align-items: flex-start; border: 1px solid var(--color-border); border-radius: 6px; padding: 10px 12px; }
.check i { width: 10px; height: 10px; border-radius: 50%; margin-top: 5px; flex-shrink: 0; background: #1e9e4a; }
.check.WARN i { background: #f59e0b; }
.check.DOWN i { background: #d33; }
.check.DOWN { border-color: #f0b4b4; background: #fff8f8; }
.check div { display: flex; flex-direction: column; font-size: 13px; }
.check span { color: var(--color-text-muted); }
.info { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 12px; }
dl { display: grid; grid-template-columns: 130px 1fr; gap: 4px 10px; margin: 0; font-size: 13px; }
dt { color: var(--color-text-muted); }
dd { margin: 0; }
h4 { margin: 14px 0 8px; font-size: 14px; }
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.toolbar input { max-width: 320px; }
.pills { display: flex; gap: 6px; }
.chip { border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chip.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.row { cursor: pointer; }
.row:hover td { background: #fafafa; }
.msg { font-size: 13px; color: var(--color-text-muted); max-width: 420px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
:deep(.mono) { font-family: ui-monospace, Consolas, monospace; }
:deep(.small) { font-size: 12px; }
:deep(.r) { text-align: right; white-space: nowrap; }
:deep(.bad) { color: #d33; }
.faint, :deep(.faint) { color: var(--color-text-faint); font-size: 12px; }
.nowrap { white-space: nowrap; }
.link-btn { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #fdecea; color: #d33; white-space: nowrap; }
.st.RESOLVED { background: #eaf7ef; color: #1e9e4a; }
.st.IGNORED { background: #f2f2f2; color: #666; }
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
.kpi span, .kpi small { font-size: 12px; color: var(--color-text-muted); }
.kpi b { font-size: 18px; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.logs { border: 1px solid var(--color-border); border-radius: 6px; max-height: 65vh; overflow: auto; background: #1e1e1e; color: #ddd;
  font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.log { padding: 3px 10px; border-bottom: 1px solid #2c2c2c; display: flex; gap: 8px; flex-wrap: wrap; }
.log .t { color: #888; }
.log .lv { width: 44px; font-weight: 700; color: #6fb7ff; }
.log.WARN .lv { color: #f5c24b; }
.log.ERROR .lv { color: #ff6b6b; }
.log .lg { color: #9ccc65; }
.log .m { flex: 1; min-width: 300px; white-space: pre-wrap; word-break: break-word; }
.ex { flex-basis: 100%; margin: 2px 0 4px 60px; color: #ff9e9e; white-space: pre-wrap; }
.err-info { grid-template-columns: 140px 1fr; margin-bottom: 12px; }
.stack { background: #1e1e1e; color: #eee; font-size: 11.5px; padding: 10px; border-radius: 4px; max-height: 340px; overflow: auto; white-space: pre; }
.note-in { width: 100%; margin-top: 10px; }
@media (max-width: 1000px) { .checks, .info { grid-template-columns: 1fr; } .two, .kpis { grid-template-columns: 1fr 1fr; } }
</style>
