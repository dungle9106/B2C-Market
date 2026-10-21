<template>
  <BaseModal :title="adding ? 'Địa chỉ mới' : 'Địa Chỉ Của Tôi'" :width="560" @close="$emit('close')">
    <!-- list -->
    <template v-if="!adding">
      <p v-if="!addresses.length" class="empty">Bạn chưa có địa chỉ nào.</p>
      <label v-for="a in addresses" :key="a.id" class="address">
        <input type="radio" name="address" :checked="picked === a.id" @change="picked = a.id" />
        <div class="text">
          <div><strong>{{ a.recipientName }}</strong> <span class="sep">|</span> <span class="text-muted">{{ a.phone }}</span></div>
          <div class="text-muted">{{ a.addressLine }}</div>
          <div class="text-muted">{{ [a.ward, a.district, a.province].filter(Boolean).join(', ') }}</div>
          <span v-if="a.default" class="default-tag">Mặc định</span>
        </div>
        <div class="actions">
          <button v-if="!a.default" type="button" class="link" @click.prevent="onDefault(a)">Thiết lập mặc định</button>
          <button type="button" class="link danger" @click.prevent="onDelete(a)">Xóa</button>
        </div>
      </label>
      <button type="button" class="btn ghost add" @click="adding = true">+ Thêm Địa Chỉ Mới</button>
    </template>

    <!-- new address form -->
    <form v-else id="address-form" class="form" novalidate @submit.prevent="onCreate">
      <div class="row">
        <input v-model.trim="form.recipientName" placeholder="Họ và tên" maxlength="60" />
        <input v-model.trim="form.phone" type="tel" placeholder="Số điện thoại" maxlength="10" />
      </div>
      <select v-model="form.province">
        <option :value="null" disabled>Tỉnh/Thành phố</option>
        <option v-for="p in PROVINCES" :key="p" :value="p">{{ p }}</option>
      </select>
      <div class="row">
        <input v-model.trim="form.district" placeholder="Quận/Huyện" />
        <input v-model.trim="form.ward" placeholder="Phường/Xã" />
      </div>
      <input v-model.trim="form.addressLine" placeholder="Địa chỉ cụ thể (số nhà, tên đường)" maxlength="200" />
      <label class="check"><input v-model="form.isDefault" type="checkbox" /> Đặt làm địa chỉ mặc định</label>
    </form>
    <p v-if="error" class="error">{{ error }}</p>

    <template #footer>
      <template v-if="adding">
        <button type="button" class="btn ghost" @click="adding = false">Trở Lại</button>
        <button type="submit" form="address-form" class="btn" :disabled="saving">{{ saving ? 'Đang lưu...' : 'Hoàn thành' }}</button>
      </template>
      <template v-else>
        <button type="button" class="btn ghost" @click="$emit('close')">Hủy</button>
        <button type="button" class="btn" :disabled="!picked" @click="$emit('select', picked)">Xác nhận</button>
      </template>
    </template>
  </BaseModal>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'
import BaseModal from '../BaseModal.vue'
import { createAddress, setDefaultAddress, deleteAddress } from '../../api/orders'
import { extractError } from '../../utils/apiError'
import { PROVINCES } from '../../utils/provinces'

const props = defineProps({
  addresses: { type: Array, default: () => [] },
  current: { type: Number, default: null },
  startAdding: { type: Boolean, default: false }
})
// changed: the list must be reloaded by the parent
const emit = defineEmits(['close', 'select', 'changed'])

const picked = ref(props.current)
const adding = ref(props.startAdding || !props.addresses.length)
const saving = ref(false)
const error = ref('')
const auth = useAuthStore()
// the profile's name and phone (or the last address's) are filled in for the buyer
const local = (p) => (p || '').replace(/\s+/g, '').replace(/^\+84/, '0')
const fromProfile = () => {
  const last = props.addresses.find(a => a.default) || props.addresses[0]
  return { recipientName: auth.fullName || last?.recipientName || '', phone: local(auth.phone) || last?.phone || '' }
}
const form = ref({ ...fromProfile(), province: null, district: '', ward: '', addressLine: '', isDefault: !props.addresses.length })

onMounted(async () => {
  // sessions from before the phone was part of the profile info: ask once
  if (!auth.phone && auth.isLoggedIn) {
    await auth.refreshMe().catch(() => {})
    if (!form.value.phone) form.value.phone = fromProfile().phone
    if (!form.value.recipientName) form.value.recipientName = fromProfile().recipientName
  }
})

function validate() {
  const f = form.value
  if (!f.recipientName) return 'Vui lòng nhập họ và tên'
  if (!/^0\d{9}$/.test(f.phone)) return 'Số điện thoại gồm 10 số, bắt đầu bằng 0'
  if (!f.province) return 'Vui lòng chọn tỉnh/thành phố'
  if (!f.addressLine) return 'Vui lòng nhập địa chỉ cụ thể'
  return ''
}

async function onCreate() {
  error.value = validate()
  if (error.value) return
  saving.value = true
  try {
    const { data } = await createAddress(form.value)
    emit('changed')
    emit('select', data.id)
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}

async function onDefault(a) {
  await setDefaultAddress(a.id)
  emit('changed')
}

async function onDelete(a) {
  if (!confirm('Xóa địa chỉ này?')) return
  await deleteAddress(a.id)
  if (picked.value === a.id) picked.value = null
  emit('changed')
}
</script>

<style scoped>
.empty { color: var(--color-text-faint); text-align: center; padding: 12px 0; }
.address { display: flex; gap: 12px; align-items: flex-start; padding: 16px 0; border-bottom: 1px solid var(--color-border); cursor: pointer; font-size: 14px; line-height: 1.6; }
.address input { width: 18px; margin: 4px 0 0; accent-color: var(--color-primary); }
.text { flex: 1; min-width: 0; }
.sep { color: #ddd; }
.default-tag { display: inline-block; border: 1px solid var(--color-primary); color: var(--color-primary); font-size: 11px; padding: 0 4px; margin-top: 4px; }
.actions { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; white-space: nowrap; }
.link.danger { color: #d33; }
.add { margin-top: 16px; }
.form .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.check { display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; }
.check input { width: auto; margin: 0; accent-color: var(--color-primary); }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
</style>
