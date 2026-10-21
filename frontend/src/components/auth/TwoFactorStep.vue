<template>
  <!-- sign-in step 2: the 6-digit code of the authenticator app, or a backup code -->
  <form class="two-factor" novalidate @submit.prevent="submit">
    <div class="icon">🔐</div>
    <h3>Xác thực 2 lớp</h3>
    <p class="hint">{{ backup ? 'Nhập một mã dự phòng (dạng XXXX-XXXX). Mỗi mã chỉ dùng được một lần.'
      : 'Mở ứng dụng xác thực (Google Authenticator, Microsoft Authenticator...) và nhập mã 6 số cho Shoppi.' }}</p>
    <p v-if="email" class="who">{{ email }}</p>
    <input ref="input" v-model.trim="code" :inputmode="backup ? 'text' : 'numeric'" :maxlength="backup ? 9 : 6"
           :placeholder="backup ? 'XXXX-XXXX' : '123456'" class="code" autocomplete="one-time-code" />
    <p v-if="error" class="error">{{ error }}</p>
    <button class="btn block lg" type="submit" :disabled="loading || code.length < (backup ? 8 : 6)">{{ loading ? 'ĐANG XÁC THỰC...' : 'XÁC NHẬN' }}</button>
    <div class="links">
      <a href="#" @click.prevent="backup = !backup; code = ''; error = ''">{{ backup ? 'Dùng mã từ ứng dụng' : 'Dùng mã dự phòng' }}</a>
      <a href="#" @click.prevent="$emit('cancel')">Quay lại đăng nhập</a>
    </div>
  </form>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { extractError } from '../../utils/apiError'

const props = defineProps({ challenge: { type: String, required: true }, email: String })
const emit = defineEmits(['done', 'cancel'])

const auth = useAuthStore()
const code = ref('')
const backup = ref(false)
const loading = ref(false)
const error = ref('')
const input = ref(null)

async function submit() {
  loading.value = true
  error.value = ''
  try {
    emit('done', await auth.loginTwoFactor(props.challenge, code.value))
  } catch (e) {
    error.value = extractError(e)
    code.value = ''
  } finally {
    loading.value = false
  }
}

onMounted(() => input.value?.focus())
</script>

<style scoped>
.two-factor { text-align: center; }
.icon { font-size: 34px; }
h3 { margin: 6px 0 8px; font-size: 18px; }
.hint { font-size: 13px; color: var(--color-text-muted); line-height: 1.5; margin: 0 0 6px; }
.who { font-size: 13px; font-weight: 600; margin: 0 0 12px; }
.code { text-align: center; font-size: 24px; letter-spacing: 6px; font-family: ui-monospace, monospace; }
.links { display: flex; justify-content: space-between; margin-top: 12px; font-size: 13px; }
.links a { color: #2673dd; }
.error { color: #d33; font-size: 13px; }
</style>
