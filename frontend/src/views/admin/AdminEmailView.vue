<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Máy Chủ Email (SMTP)</h2>
        <span class="status" :class="cfg?.configured ? 'ok' : 'warn'">{{ cfg?.configured ? '● Đang gửi email' : '● Chưa cấu hình: email chỉ được lưu lại' }}</span>
      </div>
      <div v-if="!cfg" class="sc-empty">Đang tải...</div>
      <template v-else>
        <div class="grid">
          <label>Máy chủ SMTP<input v-model.trim="f.host" placeholder="smtp.gmail.com (để trống = chỉ lưu)" /></label>
          <label>Cổng<input v-model.number="f.port" type="number" /></label>
          <label>Tên đăng nhập<input v-model.trim="f.username" autocomplete="off" placeholder="shoppi@gmail.com" /></label>
          <label>Mật khẩu ứng dụng<input v-model="f.password" type="password" autocomplete="new-password" :placeholder="cfg.passwordSet ? '•••••••• (đã lưu, nhập để thay)' : 'App password'" /></label>
          <label>Địa chỉ gửi<input v-model.trim="f.from" placeholder="no-reply@shoppi.vn" /></label>
          <label>Tên người gửi<input v-model.trim="f.fromName" placeholder="Shoppi" /></label>
        </div>
        <div class="checks">
          <label><input v-model="f.starttls" type="checkbox" /> STARTTLS (cổng 587)</label>
          <label><input v-model="f.ssl" type="checkbox" /> SSL (cổng 465)</label>
          <span class="faint">Gmail: smtp.gmail.com · 587 · STARTTLS · dùng "Mật khẩu ứng dụng" của Google. Mật khẩu được mã hóa khi lưu.</span>
        </div>
        <div class="actions">
          <span v-if="msg" :class="msgOk ? 'ok' : 'bad'">{{ msg }}</span>
          <input v-model.trim="testTo" type="email" placeholder="Gửi thử đến..." class="test-to" />
          <button type="button" class="btn sm ghost" :disabled="!testTo || busy" @click="sendTest">Gửi email thử</button>
          <button type="button" class="btn sm" :disabled="busy" @click="save">Lưu cấu hình</button>
        </div>
      </template>
    </section>

    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Hộp Thư Đi</h2>
        <span class="sc-card-sub">7 ngày qua: {{ cfg?.stats.SENT ?? 0 }} đã gửi · {{ cfg?.stats.QUEUED ?? 0 }} đang chờ · {{ cfg?.stats.FAILED ?? 0 }} lỗi · {{ cfg?.stats.LOGGED ?? 0 }} chỉ lưu</span>
      </div>
      <div class="toolbar">
        <div class="pills">
          <button v-for="s in STATUS_TABS" :key="s.key" type="button" class="chip" :class="{ on: status === s.key }" @click="status = s.key; load(0)">{{ s.label }}</button>
        </div>
        <select v-model="type" @change="load(0)">
          <option value="">Mọi loại email</option>
          <option v-for="(label, k) in TYPES" :key="k" :value="k">{{ label }}</option>
        </select>
        <input v-model.trim="q" type="search" placeholder="Tìm người nhận / tiêu đề" @input="onSearch" />
      </div>
      <table class="sc-table">
        <thead><tr><th>Thời gian</th><th>Người nhận</th><th>Loại</th><th>Tiêu đề</th><th>Trạng thái</th><th></th></tr></thead>
        <tbody>
          <tr v-for="m in page.items" :key="m.id" class="row" @click="open(m.id)">
            <td class="faint nowrap">{{ formatDateTime(m.createdAt) }}</td>
            <td>{{ m.to }}</td>
            <td class="small">{{ TYPES[m.type] || m.type }}</td>
            <td class="subject">{{ m.subject }}</td>
            <td><span class="st" :class="m.status">{{ STATUS[m.status] }}</span><div v-if="m.lastError" class="err" :title="m.lastError">{{ m.lastError }}</div></td>
            <td><button v-if="['FAILED', 'LOGGED'].includes(m.status) && cfg?.configured" type="button" class="link" @click.stop="retry(m)">Gửi lại</button></td>
          </tr>
          <tr v-if="!page.items.length"><td colspan="6" class="faint">Chưa có email nào</td></tr>
        </tbody>
      </table>
      <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
    </section>

    <BaseModal v-if="mail" :title="mail.subject" :width="720" @close="mail = null">
      <p class="faint">Đến {{ mail.to }} · {{ formatDateTime(mail.createdAt) }} · {{ STATUS[mail.status] }}<template v-if="mail.sentAt"> lúc {{ formatDateTime(mail.sentAt) }}</template></p>
      <!-- sandboxed: the e-mail HTML can't run scripts or touch this page -->
      <iframe :srcdoc="mail.html" sandbox="" class="preview" title="Nội dung email"></iframe>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { emailConfig, saveEmailConfig, testEmail, emailMessages, emailMessage, retryEmail } from '../../api/admin'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const STATUS = { QUEUED: 'Đang chờ', SENT: 'Đã gửi', FAILED: 'Lỗi', LOGGED: 'Chỉ lưu' }
const STATUS_TABS = [{ key: 'ALL', label: 'Tất cả' }, { key: 'SENT', label: 'Đã gửi' }, { key: 'QUEUED', label: 'Đang chờ' }, { key: 'FAILED', label: 'Lỗi' }, { key: 'LOGGED', label: 'Chỉ lưu' }]
const TYPES = { VERIFY_EMAIL: 'Xác thực email', RESET_PASSWORD: 'Đặt lại mật khẩu', NEW_DEVICE_LOGIN: 'Đăng nhập thiết bị mới',
  ACCOUNT_TEMP_LOCKED: 'Khóa tạm đăng nhập', PASSWORD_CHANGED: 'Đổi mật khẩu', PASSWORD_RESET: 'Đã đặt lại mật khẩu',
  TWO_FACTOR_ENABLED: 'Bật 2 lớp', TWO_FACTOR_DISABLED: 'Tắt 2 lớp', ORDER_PLACED: 'Đặt hàng', ORDER_PAID: 'Thanh toán',
  ORDER_PICKED_UP: 'Đang giao', ORDER_DELIVERED: 'Đã giao', ORDER_CANCELLED: 'Hủy đơn', ORDER_REFUNDED: 'Hoàn tiền', TEST: 'Email thử' }

const cfg = ref(null)
const f = ref({})
const testTo = ref('')
const msg = ref('')
const msgOk = ref(true)
const busy = ref(false)
const page = ref({ items: [], page: 0, totalPages: 0 })
const status = ref('ALL')
const type = ref('')
const q = ref('')
const mail = ref(null)
let timer = null

function fill(c) {
  cfg.value = c
  f.value = { host: c.source === 'ADMIN' ? c.host : '', port: c.port, username: c.username || '', password: '', from: c.from, fromName: c.fromName,
    starttls: c.starttls, ssl: c.ssl }
}

async function save() {
  busy.value = true
  msg.value = ''
  try {
    const body = { ...f.value }
    if (!body.password) delete body.password
    fill((await saveEmailConfig(body)).data)
    msg.value = 'Đã lưu ✓'
    msgOk.value = true
  } catch (e) {
    msg.value = extractError(e)
    msgOk.value = false
  } finally {
    busy.value = false
  }
}

async function sendTest() {
  busy.value = true
  try {
    const { data } = await testEmail(testTo.value)
    msg.value = data.message
    msgOk.value = data.ok
    load(0)
  } catch (e) {
    msg.value = extractError(e)
    msgOk.value = false
  } finally {
    busy.value = false
  }
}

const load = (p = 0) => emailMessages({ status: status.value, type: type.value, q: q.value, page: p }).then(({ data }) => { page.value = data }).catch(() => {})
const onSearch = () => { clearTimeout(timer); timer = setTimeout(() => load(0), 300) }
const open = (id) => emailMessage(id).then(({ data }) => { mail.value = data })
const retry = (m) => retryEmail(m.id).then(() => load(page.value.page))

onMounted(async () => {
  fill((await emailConfig()).data)
  load(0)
})
</script>

<style scoped>
.sc-card-head { align-items: center; }
.status { font-size: 13px; font-weight: 600; }
.ok { color: #1e9e4a; font-size: 13px; }
.status.warn { color: #b35c00; }
.bad { color: #d33; font-size: 13px; }
.grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--color-text-muted); }
.checks { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; margin: 12px 0; font-size: 13px; }
.checks label { display: flex; gap: 6px; align-items: center; }
.checks input { width: auto; }
.actions { display: flex; justify-content: flex-end; align-items: center; gap: 10px; flex-wrap: wrap; }
.test-to { max-width: 240px; }
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.toolbar select { width: auto; }
.toolbar input { max-width: 260px; }
.pills { display: flex; gap: 6px; }
.chip { border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chip.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.row { cursor: pointer; }
.row:hover td { background: #fafafa; }
.subject { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.small { font-size: 13px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.nowrap { white-space: nowrap; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #eaf7ef; color: #1e9e4a; white-space: nowrap; }
.st.QUEUED { background: #e8f1fd; color: #2673dd; }
.st.FAILED { background: #fdecea; color: #d33; }
.st.LOGGED { background: #f2f2f2; color: #666; }
.err { font-size: 11px; color: #d33; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
.preview { width: 100%; height: 520px; border: 1px solid var(--color-border); border-radius: 4px; background: #f5f5f5; }
@media (max-width: 900px) { .grid { grid-template-columns: 1fr 1fr; } }
</style>
