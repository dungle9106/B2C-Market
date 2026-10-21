<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Nhân Viên & Phân Quyền</h2>
        <span class="sc-card-sub">Mỗi nhân viên chỉ thấy và dùng được các mục quản trị được cấp quyền</span>
        <button v-if="!creating" type="button" class="btn sm add" @click="creating = true">+ Thêm nhân viên</button>
      </div>

      <form v-if="creating" class="create" novalidate @submit.prevent="onCreate">
        <div class="row3">
          <input v-model.trim="form.fullName" placeholder="Họ tên" />
          <input v-model.trim="form.email" type="email" placeholder="Email đăng nhập" />
          <input v-model="form.password" type="password" placeholder="Mật khẩu tạm (≥ 8 ký tự)" />
        </div>
        <div class="perm-grid">
          <label v-for="p in me?.allPermissions || []" :key="p.key" :class="{ disabled: p.key === 'STAFF' && !me.superAdmin }">
            <input v-model="form.permissions" type="checkbox" :value="p.key" :disabled="p.key === 'STAFF' && !me.superAdmin" /> {{ p.label }}
          </label>
        </div>
        <div class="form-actions">
          <span v-if="createError" class="error">{{ createError }}</span>
          <button type="button" class="btn ghost sm" @click="creating = false">Hủy</button>
          <button class="btn sm" type="submit" :disabled="saving">{{ saving ? 'Đang tạo...' : 'Tạo tài khoản nhân viên' }}</button>
        </div>
        <p class="hint">Nhân viên đăng nhập bằng email & mật khẩu này tại trang đăng nhập thông thường và được chuyển thẳng vào trang quản trị.</p>
      </form>

      <div class="sc-table-wrap">
        <table class="sc-table">
          <thead>
            <tr>
              <th>Nhân viên</th>
              <th v-for="p in me?.allPermissions || []" :key="p.key" class="c" :title="p.label">{{ SHORT[p.key] }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in staff" :key="s.id">
              <td>
                <strong>{{ s.fullName }}</strong>
                <span v-if="s.superAdmin" class="super">Cấp cao</span>
                <span v-if="s.id === me?.id" class="you">Bạn</span>
                <div class="faint">{{ s.email }} · {{ s.lastLoginAt ? 'đăng nhập ' + formatDateTime(s.lastLoginAt) : 'chưa đăng nhập' }}</div>
              </td>
              <td v-for="p in me?.allPermissions || []" :key="p.key" class="c">
                <input type="checkbox" :checked="draftOf(s).includes(p.key)" :disabled="!editable(s, p.key)" @change="togglePerm(s, p.key)" />
              </td>
              <td class="actions">
                <template v-if="dirty(s)">
                  <button type="button" class="btn sm" :disabled="busyId === s.id" @click="onSave(s)">Lưu</button>
                  <button type="button" class="link" @click="discard(s)">Hủy</button>
                </template>
                <button v-else-if="canEdit(s)" type="button" class="link danger" :disabled="busyId === s.id" @click="onRevoke(s)">Thu hồi quyền</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <ul class="rules">
        <li>Quản trị viên cấp cao có toàn quyền và không thể bị thay đổi hay thu hồi.</li>
        <li>Không ai tự thay đổi quyền của chính mình.</li>
        <li>Chỉ quản trị viên cấp cao được cấp hoặc thu hồi quyền "Nhân viên & phân quyền".</li>
        <li>Thu hồi quyền chuyển tài khoản về người mua thông thường. Mọi thay đổi được ghi vào Nhật ký quản trị.</li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminStaff, createStaff, updateStaff, revokeStaff } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const SHORT = { USERS: 'Người dùng', SHOPS: 'Shop', PRODUCTS: 'Sản phẩm', ORDERS: 'Đơn hàng', FINANCE: 'Tài chính', VOUCHERS: 'Marketing', SUPPORT: 'Hỗ trợ', BROADCAST: 'Thông báo', REPORTS: 'Báo cáo', SETTINGS: 'Hệ thống', STAFF: 'Nhân viên' }

const { me } = useAdminMe()
const staff = ref([])
const drafts = reactive({})
const error = ref('')
const busyId = ref(null)
const creating = ref(false)
const saving = ref(false)
const createError = ref('')
const form = ref({ fullName: '', email: '', password: '', permissions: ['SUPPORT'] })

const canEdit = (s) => !!me.value && !s.superAdmin && s.id !== me.value.id
// only the super admin touches the STAFF permission
const editable = (s, key) => canEdit(s) && (key !== 'STAFF' || me.value.superAdmin) && !(s.permissions.includes('STAFF') && !me.value.superAdmin)
const draftOf = (s) => drafts[s.id] || s.permissions
const dirty = (s) => !!drafts[s.id] && [...drafts[s.id]].sort().join() !== [...s.permissions].sort().join()

const discard = (s) => { delete drafts[s.id] }

function togglePerm(s, key) {
  const cur = new Set(draftOf(s))
  cur.has(key) ? cur.delete(key) : cur.add(key)
  drafts[s.id] = [...cur]
}

async function load() {
  try {
    staff.value = (await adminStaff()).data
  } catch (e) {
    error.value = extractError(e)
  }
}

async function onSave(s) {
  busyId.value = s.id
  error.value = ''
  try {
    await updateStaff(s.id, drafts[s.id])
    delete drafts[s.id]
    await load()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busyId.value = null
  }
}

async function onRevoke(s) {
  if (!confirm(`Thu hồi quyền quản trị của ${s.fullName}? Tài khoản sẽ trở thành người mua thông thường.`)) return
  busyId.value = s.id
  error.value = ''
  try {
    await revokeStaff(s.id)
    await load()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busyId.value = null
  }
}

async function onCreate() {
  saving.value = true
  createError.value = ''
  try {
    await createStaff(form.value)
    form.value = { fullName: '', email: '', password: '', permissions: ['SUPPORT'] }
    creating.value = false
    await load()
  } catch (e) {
    createError.value = extractError(e)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.add { margin-left: auto; }
.create { border: 1px dashed #e0d8ff; background: #faf8ff; border-radius: 6px; padding: 14px; margin-bottom: 16px; }
.row3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }
.row3 input { margin: 0; }
.perm-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px 12px; margin: 12px 0; font-size: 13px; }
.perm-grid label { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.perm-grid label.disabled { color: var(--color-text-faint); cursor: not-allowed; }
.perm-grid input { margin: 0; width: 16px; height: 16px; accent-color: #6941c6; }
.form-actions { display: flex; justify-content: flex-end; align-items: center; gap: 8px; }
.hint { font-size: 12px; color: var(--color-text-faint); margin: 8px 0 0; }
.c { text-align: center; font-size: 12px; white-space: nowrap; }
.c input { width: 16px; height: 16px; margin: 0; accent-color: #6941c6; }
.super { background: #6941c6; color: white; font-size: 11px; border-radius: 3px; padding: 1px 6px; margin-left: 6px; }
.you { background: #f0f5ff; color: #2673dd; font-size: 11px; border-radius: 3px; padding: 1px 6px; margin-left: 6px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.actions { white-space: nowrap; text-align: right; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
.link.danger { color: #d33; }
.error { color: #d33; font-size: 13px; }
.rules { margin: 14px 0 0; padding-left: 18px; font-size: 12px; color: var(--color-text-muted); line-height: 1.7; }
@media (max-width: 900px) { .row3 { grid-template-columns: 1fr; } .perm-grid { grid-template-columns: 1fr 1fr; } }
</style>
