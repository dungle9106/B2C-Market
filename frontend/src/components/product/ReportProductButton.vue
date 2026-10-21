<template>
  <span class="report-wrap">
    <button type="button" class="report-btn" @click="openDialog">🚩 Báo cáo</button>
    <BaseModal v-if="open" title="Báo cáo sản phẩm vi phạm" @close="open = false">
      <div class="dialog">
        <p v-if="done" class="ok">{{ done }}</p>
        <template v-else>
          <p class="sub">Sản phẩm này vi phạm chính sách của Shoppi như thế nào?</p>
          <label v-for="r in reasons" :key="r.key" class="reason">
            <input v-model="reason" type="radio" :value="r.key" /> {{ r.label }}
          </label>
          <textarea v-model="detail" rows="3" maxlength="1000" placeholder="Mô tả thêm (không bắt buộc)"></textarea>
          <p v-if="error" class="error">{{ error }}</p>
          <div class="btns">
            <button type="button" class="btn ghost" @click="open = false">Hủy</button>
            <button type="button" class="btn" :disabled="!reason || busy" @click="submit">{{ busy ? 'Đang gửi...' : 'Gửi báo cáo' }}</button>
          </div>
        </template>
      </div>
    </BaseModal>
  </span>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseModal from '../BaseModal.vue'
import { reportReasons, reportProduct } from '../../api/products'
import { useAuthStore } from '../../stores/auth'
import { extractError } from '../../utils/apiError'

const props = defineProps({ productId: { type: Number, required: true } })
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const open = ref(false)
const reasons = ref([])
const reason = ref('')
const detail = ref('')
const busy = ref(false)
const error = ref('')
const done = ref('')

async function openDialog() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  open.value = true
  done.value = ''
  error.value = ''
  if (!reasons.value.length) reasons.value = (await reportReasons()).data
}

async function submit() {
  busy.value = true
  error.value = ''
  try {
    done.value = (await reportProduct(props.productId, reason.value, detail.value.trim() || undefined)).data.message
    reason.value = ''
    detail.value = ''
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.report-wrap { display: inline-flex; }
.report-btn { background: none; border: none; color: var(--color-text-faint); font-size: 13px; cursor: pointer; padding: 0; }
.report-btn:hover { color: var(--color-primary); }
.dialog { min-width: min(440px, 85vw); font-size: 14px; }
.sub { margin: 0 0 10px; color: var(--color-text-muted); }
.reason { display: flex; align-items: center; gap: 8px; padding: 5px 0; cursor: pointer; }
.reason input { margin: 0; width: 16px; height: 16px; accent-color: var(--color-primary); }
textarea { width: 100%; margin: 10px 0 0; font-family: inherit; }
.btns { display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px; }
.ok { color: #1e9e4a; margin: 0; }
.error { color: #d33; font-size: 13px; }
</style>
