<template>
  <div v-if="state !== 'hidden'" class="push" :class="{ compact }">
    <span class="push-icon" aria-hidden="true">🔔</span>
    <div class="push-text">
      <template v-if="state === 'unsupported'">
        <b>Trình duyệt này không hỗ trợ thông báo đẩy</b>
        <span>Hãy dùng Chrome, Edge hoặc Firefox bản mới.</span>
      </template>
      <template v-else-if="state === 'denied'">
        <b>Bạn đã chặn thông báo của Shoppi</b>
        <span>Bấm biểu tượng ổ khóa cạnh thanh địa chỉ → Thông báo → Cho phép, rồi tải lại trang.</span>
      </template>
      <template v-else-if="state === 'on'">
        <b>Đã bật thông báo đẩy</b>
        <span v-if="!compact">Bạn nhận tin về đơn hàng, tin nhắn và khuyến mãi ngay cả khi không mở Shoppi.</span>
      </template>
      <template v-else>
        <b>Bật thông báo đẩy</b>
        <span>Nhận tin đơn hàng, tin nhắn và khuyến mãi ngay cả khi đang không mở Shoppi.</span>
      </template>
      <span v-if="message" class="msg">{{ message }}</span>
    </div>
    <div class="push-actions">
      <template v-if="state === 'on'">
        <button v-if="!compact" type="button" class="btn-link" :disabled="busy" @click="test">Gửi thử</button>
        <button v-if="!compact" type="button" class="btn-link muted" :disabled="busy" @click="turnOff">Tắt</button>
      </template>
      <button v-else-if="state === 'off'" type="button" class="btn-on" :disabled="busy" @click="turnOn">Bật</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { pushPermission, currentSubscription, enablePush, disablePush, isPushOff, setPushOff } from '../utils/webPush'
import { pushTest } from '../api/push'

// compact: a one-line prompt (bell dropdown), hidden once push is on
const props = defineProps({ compact: { type: Boolean, default: false } })

const state = ref('hidden') // hidden | unsupported | denied | off | on
const busy = ref(false)
const message = ref('')

async function refresh() {
  const p = pushPermission()
  if (p === 'unsupported') state.value = props.compact ? 'hidden' : 'unsupported'
  else if (p === 'denied') state.value = props.compact ? 'hidden' : 'denied'
  else state.value = p === 'granted' && (await currentSubscription()) && !isPushOff() ? (props.compact ? 'hidden' : 'on') : 'off'
}

async function turnOn() {
  busy.value = true
  message.value = ''
  try {
    setPushOff(false)
    const result = await enablePush()
    if (result === 'denied') message.value = 'Bạn chưa cho phép thông báo.'
    await refresh()
  } catch (e) {
    message.value = e.response?.data?.message || 'Không bật được thông báo đẩy, thử lại sau.'
  } finally {
    busy.value = false
  }
}

async function turnOff() {
  busy.value = true
  try {
    await disablePush()
    setPushOff(true)
    message.value = ''
    state.value = 'off'
  } finally {
    busy.value = false
  }
}

async function test() {
  busy.value = true
  message.value = ''
  try {
    await pushTest()
    message.value = 'Đã gửi, thông báo sẽ hiện sau vài giây.'
  } catch (e) {
    message.value = e.response?.data?.message || 'Không gửi được.'
  } finally {
    busy.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.push {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px; margin-bottom: 12px;
  background: var(--color-primary-light); border: 1px solid #ffd6cc; border-radius: var(--radius-sm);
}
.push-icon { font-size: 22px; }
.push-text { flex: 1; display: flex; flex-direction: column; gap: 2px; font-size: 13px; color: var(--color-text-muted); min-width: 0; }
.push-text b { color: var(--color-text); font-weight: 600; }
.msg { color: var(--color-primary); }
.push-actions { display: flex; gap: 12px; align-items: center; }
.btn-on {
  border: none; background: var(--color-primary); color: white; border-radius: var(--radius-sm);
  padding: 7px 16px; font-size: 13px; cursor: pointer; white-space: nowrap;
}
.btn-on:disabled { opacity: 0.6; }
.btn-link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; white-space: nowrap; }
.btn-link.muted { color: var(--color-text-faint); }
.compact { margin: 0; border: none; border-bottom: 1px solid #f3f3f3; border-radius: 0; padding: 10px 14px; }
.compact .push-icon { font-size: 18px; }
</style>
