<template>
  <div class="security">
    <div v-if="!data" class="card">{{ error || 'Đang tải...' }}</div>
    <template v-else>
      <p v-if="setupRequired && !data.twoFactorEnabled" class="banner">Sàn yêu cầu quản trị viên bật xác thực 2 lớp trước khi dùng trang quản trị.</p>

      <!-- email -->
      <section class="card">
        <div class="row">
          <div>
            <h3>Email</h3>
            <p>{{ data.email }}
              <span v-if="data.emailVerified" class="tag ok">✓ Đã xác thực</span>
              <span v-else class="tag warn">Chưa xác thực</span></p>
          </div>
          <button v-if="!data.emailVerified" type="button" class="btn sm ghost" :disabled="busy.verify" @click="resendVerification">
            {{ verifySent ? 'Đã gửi, kiểm tra hộp thư' : 'Gửi email xác thực' }}</button>
        </div>
      </section>

      <!-- password -->
      <section class="card">
        <div class="row">
          <div>
            <h3>Mật khẩu</h3>
            <p v-if="data.provider === 'GOOGLE'">Tài khoản đăng nhập bằng Google, không dùng mật khẩu Shoppi.</p>
            <p v-else>{{ data.passwordChangedAt ? 'Đổi lần cuối ' + formatDateTime(data.passwordChangedAt) : 'Chưa đổi mật khẩu kể từ khi đăng ký' }}</p>
          </div>
          <button v-if="data.provider !== 'GOOGLE' && !pw.open" type="button" class="btn sm ghost" @click="pw.open = true">Đổi mật khẩu</button>
        </div>
        <form v-if="pw.open" class="form" @submit.prevent="savePassword">
          <input v-model="pw.current" type="password" placeholder="Mật khẩu hiện tại" autocomplete="current-password" />
          <input v-model="pw.next" type="password" placeholder="Mật khẩu mới (≥ 8 ký tự, có chữ và số)" autocomplete="new-password" />
          <input v-model="pw.confirm" type="password" placeholder="Nhập lại mật khẩu mới" autocomplete="new-password" />
          <p class="faint">Các thiết bị khác sẽ bị đăng xuất.</p>
          <p v-if="pw.error" class="error">{{ pw.error }}</p>
          <div class="actions">
            <button type="button" class="btn sm ghost" @click="pw.open = false">Hủy</button>
            <button type="submit" class="btn sm" :disabled="busy.pw">Lưu mật khẩu</button>
          </div>
        </form>
        <p v-if="pw.msg" class="ok">{{ pw.msg }}</p>
      </section>

      <!-- two-step verification -->
      <section class="card">
        <div class="row">
          <div>
            <h3>Xác thực 2 lớp <span v-if="data.twoFactorEnabled" class="tag ok">Đang bật</span><span v-else class="tag">Đang tắt</span></h3>
            <p>Khi đăng nhập, ngoài mật khẩu bạn cần nhập mã 6 số từ ứng dụng Google Authenticator / Microsoft Authenticator.</p>
            <p v-if="data.twoFactorEnabled" class="faint">Còn {{ data.backupCodesLeft }} mã dự phòng.</p>
          </div>
          <button v-if="!data.twoFactorEnabled && !tf.secret" type="button" class="btn sm" :disabled="busy.tf" @click="startSetup">Bật</button>
        </div>

        <div v-if="tf.secret" class="setup">
          <img v-if="tf.qr" :src="tf.qr" alt="Mã QR" class="qr" />
          <div>
            <p><b>1.</b> Quét mã QR bằng ứng dụng xác thực (hoặc nhập khóa <code>{{ tf.secret }}</code>).</p>
            <p><b>2.</b> Nhập mã 6 số ứng dụng hiển thị:</p>
            <div class="inline">
              <input v-model.trim="tf.code" maxlength="6" inputmode="numeric" placeholder="123456" class="code" />
              <button type="button" class="btn sm" :disabled="tf.code.length !== 6 || busy.tf" @click="confirmSetup">Xác nhận</button>
              <button type="button" class="btn sm ghost" @click="tf.secret = null">Hủy</button>
            </div>
          </div>
        </div>

        <div v-if="tf.codes" class="codes">
          <p><b>Mã dự phòng</b> – lưu ở nơi an toàn. Mỗi mã dùng được một lần khi bạn không có điện thoại. Mã chỉ hiển thị một lần.</p>
          <div class="grid"><code v-for="c in tf.codes" :key="c">{{ c }}</code></div>
          <button type="button" class="btn sm ghost" @click="copyCodes">{{ copied ? 'Đã sao chép' : 'Sao chép' }}</button>
          <button type="button" class="btn sm" @click="tf.codes = null">Tôi đã lưu</button>
        </div>

        <div v-if="data.twoFactorEnabled && !tf.codes" class="manage">
          <input v-model.trim="tf.code" maxlength="6" inputmode="numeric" placeholder="Mã 6 số hiện tại" class="code small" />
          <input v-if="data.provider !== 'GOOGLE'" v-model="tf.password" type="password" placeholder="Mật khẩu (để tắt)" />
          <button type="button" class="btn sm ghost" :disabled="tf.code.length !== 6 || busy.tf" @click="regenerate">Tạo mã dự phòng mới</button>
          <button type="button" class="btn sm ghost danger" :disabled="tf.code.length !== 6 || busy.tf" @click="disable">Tắt xác thực 2 lớp</button>
        </div>
        <p v-if="tf.error" class="error">{{ tf.error }}</p>
      </section>

      <!-- devices -->
      <section class="card">
        <div class="row">
          <div><h3>Thiết bị đang đăng nhập</h3><p>{{ data.sessions.length }} thiết bị</p></div>
          <button v-if="data.sessions.length > 1" type="button" class="btn sm ghost danger" @click="signOutOthers">Đăng xuất các thiết bị khác</button>
        </div>
        <div v-for="s in data.sessions" :key="s.id" class="device">
          <span class="dev-icon">{{ /Android|iOS/.test(s.device) ? '📱' : '💻' }}</span>
          <div class="grow">
            <b>{{ s.device }}</b><span v-if="s.current" class="tag ok">Thiết bị này</span>
            <div class="faint">IP {{ s.ip }} · đăng nhập {{ formatDateTime(s.createdAt) }} · hoạt động {{ formatDateTime(s.lastUsedAt) }}</div>
          </div>
          <button v-if="!s.current" type="button" class="link danger" @click="signOut(s)">Đăng xuất</button>
        </div>
      </section>

      <!-- history -->
      <section class="card">
        <h3>Lịch sử đăng nhập</h3>
        <table class="history">
          <tbody>
            <tr v-for="l in data.logins" :key="l.id">
              <td class="faint nowrap">{{ formatDateTime(l.time) }}</td>
              <td><span class="res" :class="l.result === 'SUCCESS' ? 'ok' : 'bad'">{{ RESULT[l.result] || l.result }}</span>
                <span v-if="l.newDevice" class="tag warn">Thiết bị mới</span></td>
              <td>{{ l.device }}</td>
              <td class="faint">{{ l.ip }}</td>
              <td class="faint">{{ METHOD[l.method] || l.method }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import QRCode from 'qrcode'
import { accountSecurity, changePassword, sendVerificationEmail, setupTwoFactor, enableTwoFactor, disableTwoFactor, newBackupCodes,
  revokeSession, revokeOtherSessions } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { passwordProblem } from '../../utils/authForm'

defineProps({ setupRequired: Boolean })
const emit = defineEmits(['changed'])

const RESULT = { SUCCESS: 'Thành công', BAD_PASSWORD: 'Sai mật khẩu', UNKNOWN_EMAIL: 'Sai email', LOCKED: 'Tài khoản bị khóa', TEMP_LOCKED: 'Đang khóa tạm',
  RATE_LIMITED: 'Bị chặn (quá nhiều lần)', TWO_FACTOR_FAILED: 'Sai mã 2 lớp', TWO_FACTOR_REQUIRED: 'Chờ mã 2 lớp' }
const METHOD = { PASSWORD: 'Mật khẩu', GOOGLE: 'Google', TOTP: 'Mã ứng dụng', BACKUP_CODE: 'Mã dự phòng', TWO_FACTOR: 'Mã 2 lớp' }

const auth = useAuthStore()
const data = ref(null)
const error = ref('')
const busy = reactive({})
const verifySent = ref(false)
const copied = ref(false)
const pw = reactive({ open: false, current: '', next: '', confirm: '', error: '', msg: '' })
const tf = reactive({ secret: null, qr: null, code: '', password: '', codes: null, error: '' })

async function load() {
  try {
    data.value = (await accountSecurity()).data
  } catch (e) {
    error.value = extractError(e)
  }
}

async function act(key, fn, onError) {
  busy[key] = true
  try {
    await fn()
  } catch (e) {
    onError(extractError(e))
  } finally {
    busy[key] = false
  }
}

const resendVerification = () => act('verify', async () => { await sendVerificationEmail(); verifySent.value = true }, (m) => { error.value = m })

function savePassword() {
  pw.error = passwordProblem(pw.next, data.value.email) || (pw.next !== pw.confirm ? 'Mật khẩu nhập lại không khớp' : '')
  if (pw.error) return
  act('pw', async () => {
    const { data: r } = await changePassword(pw.current, pw.next)
    // the other sessions (other tabs too) were signed out, with the seed new tabs start from: make a new one
    auth.saveSeed()
    Object.assign(pw, { open: false, current: '', next: '', confirm: '', msg: `Đã đổi mật khẩu${r.signedOutDevices ? `, đăng xuất ${r.signedOutDevices} thiết bị khác` : ''}.` })
    load()
  }, (m) => { pw.error = m })
}

const startSetup = () => act('tf', async () => {
  tf.error = ''
  const { data: s } = await setupTwoFactor()
  tf.secret = s.secret
  tf.qr = await QRCode.toDataURL(s.uri, { width: 180, margin: 1 })
  tf.code = ''
}, (m) => { tf.error = m })

const confirmSetup = () => act('tf', async () => {
  const { data: r } = await enableTwoFactor(tf.code)
  tf.codes = r.backupCodes
  tf.secret = null
  tf.code = ''
  auth.setTwoFactor(true)
  emit('changed')
  load()
}, (m) => { tf.error = m })

const regenerate = () => act('tf', async () => {
  tf.codes = (await newBackupCodes(tf.code)).data.backupCodes
  tf.code = ''
  load()
}, (m) => { tf.error = m })

function disable() {
  if (!confirm('Tắt xác thực 2 lớp? Tài khoản sẽ kém an toàn hơn.')) return
  act('tf', async () => {
    await disableTwoFactor(tf.password, tf.code)
    Object.assign(tf, { code: '', password: '', error: '' })
    auth.setTwoFactor(false)
    emit('changed')
    load()
  }, (m) => { tf.error = m })
}

async function copyCodes() {
  try { await navigator.clipboard.writeText(tf.codes.join('\n')) } catch { /* clipboard blocked */ }
  copied.value = true
}

const signOut = (s) => act('dev', async () => { await revokeSession(s.id); load() }, (m) => { error.value = m })
const signOutOthers = () => confirm('Đăng xuất tất cả thiết bị khác?') && act('dev', async () => { await revokeOtherSessions(); auth.saveSeed(); load() }, (m) => { error.value = m })

onMounted(load)
</script>

<style scoped>
.security { display: flex; flex-direction: column; gap: 12px; }
.card { background: white; border-radius: 6px; padding: 16px 20px; border: 1px solid var(--color-border); }
h3 { margin: 0 0 4px; font-size: 16px; display: flex; gap: 8px; align-items: center; }
p { margin: 4px 0; font-size: 14px; color: var(--color-text-muted); }
.row { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.banner { background: #fff4e5; color: #b35c00; padding: 10px 14px; border-radius: 6px; font-weight: 600; }
.tag { font-size: 11px; padding: 1px 6px; border-radius: 3px; background: #f2f2f2; color: #666; font-weight: normal; margin-left: 6px; }
.tag.ok { background: #eaf7ef; color: #1e9e4a; }
.tag.warn { background: #fff4e5; color: #b35c00; }
.form { display: flex; flex-direction: column; gap: 8px; max-width: 380px; margin-top: 10px; }
.actions { display: flex; gap: 8px; justify-content: flex-end; }
.setup { display: flex; gap: 18px; align-items: flex-start; margin-top: 12px; padding: 12px; background: #fafafa; border-radius: 6px; }
.qr { width: 180px; height: 180px; border: 1px solid var(--color-border); background: white; }
code { font-family: ui-monospace, monospace; background: #f2f2f2; padding: 2px 6px; border-radius: 3px; word-break: break-all; }
.inline { display: flex; gap: 8px; align-items: center; }
.code { width: 130px; font-family: ui-monospace, monospace; font-size: 18px; letter-spacing: 3px; text-align: center; }
.code.small { width: 160px; font-size: 14px; letter-spacing: 1px; }
.codes { margin-top: 12px; padding: 12px; border: 1px dashed #f0b4b4; background: #fffafa; border-radius: 6px; }
.grid { display: grid; grid-template-columns: repeat(4, max-content); gap: 8px 16px; margin: 10px 0; }
.manage { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 10px; }
.manage input { width: 180px; }
.device { display: flex; gap: 12px; align-items: center; padding: 10px 0; border-top: 1px solid var(--color-border); font-size: 14px; }
.dev-icon { font-size: 22px; }
.grow { flex: 1; }
.history { width: 100%; border-collapse: collapse; font-size: 13px; }
.history td { padding: 6px 8px; border-top: 1px solid var(--color-border); }
.res.ok { color: #1e9e4a; }
.res.bad { color: #d33; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.nowrap { white-space: nowrap; }
.link { background: none; border: none; cursor: pointer; font-size: 13px; color: #2673dd; }
.danger { color: #d33 !important; }
.ok { color: #1e9e4a; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 640px) { .setup { flex-direction: column; } .grid { grid-template-columns: repeat(2, max-content); } .row { flex-wrap: wrap; } }
</style>
