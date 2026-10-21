<template>
  <BaseModal :title="mode === 'lock' ? 'Khóa tài khoản' : 'Xóa tài khoản'" @close="$emit('close')">
    <div class="dialog">
      <p class="target"><strong>{{ user.fullName }}</strong> · {{ user.email }}</p>

      <template v-if="mode === 'lock'">
        <label>Thời hạn khóa</label>
        <div class="durations">
          <button v-for="d in DURATIONS" :key="d.label" type="button" :class="{ active: days === d.days }" @click="days = d.days">{{ d.label }}</button>
        </div>
        <p class="note">Người dùng bị đăng xuất ngay và không thể đăng nhập cho đến khi hết hạn hoặc được mở khóa.
          <template v-if="user.role === 'SELLER'"> Shop của người bán sẽ tạm ẩn khỏi sàn trong thời gian khóa.</template></p>
      </template>
      <template v-else>
        <p class="note danger">Tài khoản sẽ bị <strong>ẩn danh hóa vĩnh viễn</strong> (email, họ tên, số điện thoại bị xóa) và không thể khôi phục.
          Lịch sử đơn hàng, đánh giá vẫn được giữ để đảm bảo dữ liệu của người mua/người bán khác.
          <template v-if="user.role === 'SELLER'"> Shop sẽ bị đình chỉ.</template>
          Không thể xóa tài khoản còn đơn hàng đang xử lý.</p>
      </template>

      <label>Lý do <span class="req">*</span></label>
      <div class="presets">
        <button v-for="r in (mode === 'lock' ? LOCK_REASONS : DELETE_REASONS)" :key="r" type="button" @click="reason = r">{{ r }}</button>
      </div>
      <textarea v-model="reason" rows="3" maxlength="500" placeholder="Lý do sẽ được hiển thị cho người dùng khi đăng nhập"></textarea>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="buttons">
        <button type="button" class="btn ghost" @click="$emit('close')">Hủy</button>
        <button type="button" class="btn" :class="{ danger: mode === 'delete' }" :disabled="busy || reason.trim().length < 5" @click="submit">
          {{ busy ? 'Đang xử lý...' : mode === 'lock' ? 'Khóa tài khoản' : 'Xóa tài khoản' }}
        </button>
      </div>
    </div>
  </BaseModal>
</template>

<script setup>
import { ref } from 'vue'
import BaseModal from '../BaseModal.vue'
import { lockUser, deleteUser } from '../../api/admin'
import { extractError } from '../../utils/apiError'

const props = defineProps({
  mode: { type: String, required: true }, // lock | delete
  user: { type: Object, required: true }
})
const emit = defineEmits(['close', 'done'])

const DURATIONS = [{ label: '1 ngày', days: 1 }, { label: '3 ngày', days: 3 }, { label: '7 ngày', days: 7 }, { label: '30 ngày', days: 30 }, { label: 'Vĩnh viễn', days: null }]
const LOCK_REASONS = ['Spam / lừa đảo người dùng khác', 'Bom hàng nhiều lần', 'Bán hàng giả, hàng cấm', 'Ngôn từ xúc phạm', 'Nghi ngờ tài khoản bị chiếm đoạt']
const DELETE_REASONS = ['Người dùng yêu cầu xóa tài khoản', 'Tài khoản giả mạo', 'Vi phạm nghiêm trọng điều khoản sử dụng']

const days = ref(7)
const reason = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (props.mode === 'delete' && !confirm(`Xóa vĩnh viễn tài khoản ${props.user.email}? Thao tác không thể hoàn tác.`)) return
  busy.value = true
  error.value = ''
  try {
    if (props.mode === 'lock') await lockUser(props.user.id, reason.value.trim(), days.value)
    else await deleteUser(props.user.id, reason.value.trim())
    emit('done')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.dialog { min-width: min(480px, 85vw); }
.target { margin: 0 0 12px; font-size: 14px; }
label { display: block; font-size: 13px; color: var(--color-text-muted); margin: 12px 0 6px; }
.req { color: var(--color-primary); }
.durations, .presets { display: flex; flex-wrap: wrap; gap: 6px; }
.durations button, .presets button { background: white; border: 1px solid #ddd; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.durations button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.presets button:hover { border-color: var(--color-primary); color: var(--color-primary); }
textarea { margin: 8px 0 0; width: 100%; font-family: inherit; }
.note { font-size: 13px; color: var(--color-text-muted); background: #f7f7f7; padding: 8px 10px; border-radius: 4px; margin: 10px 0 0; line-height: 1.5; }
.note.danger { background: #fdecea; color: #b42318; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
.buttons { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.btn.danger { background: #d33; }
</style>
