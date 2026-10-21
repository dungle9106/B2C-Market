<template>
  <div class="thread">
    <div class="thread-head">
      <strong>Trao đổi tranh chấp</strong>
      <span class="faint">Người mua, Người bán và Shoppi cùng xem các tin nhắn này</span>
    </div>
    <div v-if="deadline" class="deadline" :class="{ over: overdue }">
      {{ side === 'SELLER'
        ? (overdue ? 'Đã quá hạn giải trình. Shoppi có thể quyết định dựa trên bằng chứng hiện có.' : `Vui lòng gửi giải trình và bằng chứng trước ${formatDateTime(deadline)}`)
        : `Người bán cần giải trình trước ${formatDateTime(deadline)}` }}
    </div>

    <div v-if="loading" class="faint pad">Đang tải...</div>
    <div v-else-if="!messages.length" class="faint pad">Chưa có tin nhắn nào{{ open ? '. Hãy bổ sung thông tin, hình ảnh để Shoppi xử lý nhanh hơn.' : '.' }}</div>
    <div v-for="m in messages" :key="m.id" class="msg" :class="[m.side, { mine: m.side === side }]">
      <div class="who">
        <span class="badge" :class="m.side">{{ SIDE[m.side] }}</span>
        <span>{{ m.authorName }}</span>
        <span class="faint">· {{ formatDateTime(m.createdAt) }}</span>
      </div>
      <p v-if="m.content" class="text">{{ m.content }}</p>
      <div v-if="m.attachments.length" class="files">
        <template v-for="url in m.attachments" :key="url">
          <video v-if="isVideo(url)" :src="url" controls preload="metadata"></video>
          <a v-else :href="url" target="_blank" rel="noopener"><img :src="url" alt="Bằng chứng" /></a>
        </template>
      </div>
    </div>

    <form v-if="open" class="compose" @submit.prevent="send">
      <textarea v-model="text" rows="2" maxlength="2000" :placeholder="PLACEHOLDER[side]"></textarea>
      <div v-if="files.length" class="files pending">
        <span v-for="(url, i) in files" :key="url" class="pending-file">
          <video v-if="isVideo(url)" :src="url"></video>
          <img v-else :src="url" alt="" />
          <button type="button" aria-label="Bỏ tệp" @click="files.splice(i, 1)">✕</button>
        </span>
      </div>
      <div class="compose-actions">
        <label class="btn sm ghost attach" :class="{ disabled: files.length >= 5 || uploading }">
          {{ uploading ? 'Đang tải lên...' : '📎 Ảnh/Video' }}
          <input type="file" accept="image/*,video/mp4,video/webm" multiple hidden :disabled="files.length >= 5 || uploading" @change="onFiles" />
        </label>
        <span class="faint">{{ files.length }}/5 tệp</span>
        <button type="submit" class="btn sm" :disabled="sending || uploading || (!text.trim() && !files.length)">{{ sending ? 'Đang gửi...' : 'Gửi' }}</button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { buyerDisputeMessages, buyerPostDisputeMessage, sellerDisputeMessages, sellerPostDisputeMessage } from '../../api/orders'
import { adminDisputeMessages, adminPostDisputeMessage } from '../../api/admin'
import { uploadFile } from '../../api/uploads'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const props = defineProps({
  returnId: { type: Number, required: true },
  side: { type: String, required: true },    // BUYER | SELLER | ADMIN: who is reading
  open: { type: Boolean, default: true },     // still ESCALATED: messages can be posted
  deadline: String,                            // the seller's response deadline
  sellerAnswered: Boolean,
  reloadKey: { type: Number, default: 0 }     // bump to reload (e.g. after the admin decided)
})
const emit = defineEmits(['posted'])

const SIDE = { BUYER: 'Người mua', SELLER: 'Người bán', ADMIN: 'Shoppi' }
const PLACEHOLDER = {
  BUYER: 'Bổ sung thông tin cho khiếu nại (mô tả lỗi, hình ảnh khi mở hàng...)',
  SELLER: 'Giải trình của shop (tình trạng hàng khi gửi, video đóng gói...)',
  ADMIN: 'Yêu cầu hai bên bổ sung thông tin...'
}
const API = {
  BUYER: [buyerDisputeMessages, buyerPostDisputeMessage],
  SELLER: [sellerDisputeMessages, sellerPostDisputeMessage],
  ADMIN: [adminDisputeMessages, adminPostDisputeMessage]
}

const messages = ref([])
const loading = ref(true)
const text = ref('')
const files = ref([])
const uploading = ref(false)
const sending = ref(false)
const error = ref('')

const isVideo = (url) => /\.(mp4|webm)$/i.test(url)
const answered = computed(() => props.sellerAnswered || messages.value.some(m => m.side === 'SELLER'))
// the deadline matters only while the seller has not answered
const overdue = computed(() => props.deadline && new Date(props.deadline) < new Date())
const deadline = computed(() => props.open && props.deadline && !answered.value ? props.deadline : null)

async function load() {
  loading.value = true
  try {
    messages.value = (await API[props.side][0](props.returnId)).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

async function onFiles(e) {
  const picked = [...e.target.files].slice(0, 5 - files.value.length)
  e.target.value = ''
  uploading.value = true
  error.value = ''
  try {
    for (const f of picked) files.value.push((await uploadFile(f)).data.url)
  } catch (err) {
    error.value = extractError(err)
  } finally {
    uploading.value = false
  }
}

async function send() {
  sending.value = true
  error.value = ''
  try {
    const { data } = await API[props.side][1](props.returnId, text.value.trim(), files.value)
    messages.value.push(data)
    text.value = ''
    files.value = []
    emit('posted', data)
  } catch (e) {
    error.value = extractError(e)
  } finally {
    sending.value = false
  }
}

watch(() => [props.returnId, props.reloadKey], load, { immediate: true })
</script>

<style scoped>
.thread { border: 1px solid var(--color-border); border-radius: 4px; background: #fcfcfd; }
.thread-head { display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; padding: 10px 14px; border-bottom: 1px solid var(--color-border); font-size: 14px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.pad { padding: 14px; }
.deadline { margin: 10px 14px 0; padding: 8px 10px; border-radius: 4px; background: #fff7e6; color: #b35c00; font-size: 13px; }
.deadline.over { background: #fdecea; color: #b42318; }
.msg { margin: 10px 14px; padding: 10px 12px; border-radius: 6px; background: white; border: 1px solid var(--color-border); max-width: 86%; }
.msg.mine { margin-left: auto; background: #fff6f3; border-color: #ffd9cc; }
.msg.ADMIN { background: #f3f0ff; border-color: #e0d8ff; max-width: 100%; }
.who { display: flex; gap: 6px; align-items: center; font-size: 13px; flex-wrap: wrap; }
.badge { font-size: 11px; padding: 1px 6px; border-radius: 3px; background: #eef4ff; color: #2673dd; }
.badge.SELLER { background: #fff4e5; color: #b35c00; }
.badge.ADMIN { background: #6941c6; color: white; }
.text { margin: 6px 0 0; font-size: 14px; white-space: pre-wrap; line-height: 1.5; word-break: break-word; }
.files { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.files img { width: 72px; height: 72px; object-fit: cover; border: 1px solid var(--color-border); border-radius: 3px; }
.files video { width: 140px; height: 80px; background: #000; border-radius: 3px; }
.compose { border-top: 1px solid var(--color-border); padding: 10px 14px; }
.compose textarea { width: 100%; resize: vertical; }
.pending-file { position: relative; }
.pending-file video, .pending-file img { width: 56px; height: 56px; object-fit: cover; border-radius: 3px; border: 1px solid var(--color-border); }
.pending-file button { position: absolute; top: -6px; right: -6px; width: 18px; height: 18px; border-radius: 50%; border: none; background: #333; color: white; font-size: 10px; cursor: pointer; }
.compose-actions { display: flex; align-items: center; gap: 10px; margin-top: 8px; }
.compose-actions .btn[type=submit] { margin-left: auto; }
.attach { cursor: pointer; }
.attach.disabled { opacity: 0.5; cursor: not-allowed; }
.error { color: #d33; font-size: 13px; margin: 6px 0 0; }
</style>
