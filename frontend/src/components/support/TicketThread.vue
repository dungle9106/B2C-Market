<template>
  <div v-if="loadError" class="empty">{{ loadError }}</div>
  <div v-else-if="!ticket" class="empty">Đang tải...</div>

  <div v-else class="thread">
    <header class="head">
      <div class="head-main">
        <h2 class="subject">{{ ticket.subject }}</h2>
        <div class="meta">
          <span>{{ ticket.code }}</span>
          <span>{{ TICKET_CATEGORIES[ticket.category] }}</span>
          <span>Tạo lúc {{ formatDateTime(ticket.createdAt) }}</span>
          <router-link v-if="ticket.orderId && orderLinks" :to="`/orders/${ticket.orderId}`">Đơn {{ ticket.orderCode }}</router-link>
          <span v-else-if="ticket.orderCode">Đơn {{ ticket.orderCode }}</span>
          <span v-if="ticket.shopName">Shop: {{ ticket.shopName }}</span>
        </div>
        <div v-if="staff" class="meta">
          Người gửi: <strong>{{ ticket.requesterName }}</strong> ({{ ticket.requesterRole === 'SELLER' ? 'Người bán' : 'Người mua' }}) · {{ ticket.requesterEmail }}
        </div>
      </div>
      <span class="ticket-status" :class="ticket.status">{{ statusNames[ticket.status] }}</span>
    </header>

    <!-- staff controls -->
    <div v-if="staff" class="staff-bar">
      <label>Mức ưu tiên
        <select :value="ticket.priority" @change="update({ priority: $event.target.value })">
          <option v-for="(label, value) in TICKET_PRIORITY" :key="value" :value="value">{{ label }}</option>
        </select>
      </label>
      <label>Trạng thái
        <select :value="ticket.status" @change="update({ status: $event.target.value })">
          <option v-for="(label, value) in TICKET_STATUS_STAFF" :key="value" :value="value">{{ label }}</option>
        </select>
      </label>
    </div>

    <ol class="messages">
      <li v-for="m in ticket.messages" :key="m.id" :class="m.systemLine ? 'system' : m.staff ? 'staff' : 'user'">
        <template v-if="m.systemLine">{{ m.content }} · {{ formatDateTime(m.createdAt) }}</template>
        <template v-else>
          <div class="who">
            <span class="avatar">{{ m.staff ? 'S' : (m.senderName || '?').charAt(0).toUpperCase() }}</span>
            <strong>{{ m.senderName }}</strong>
            <span class="time">{{ formatDateTime(m.createdAt) }}</span>
          </div>
          <div class="content">{{ m.content }}</div>
          <div v-if="m.attachmentUrls.length" class="files">
            <a v-for="url in m.attachmentUrls" :key="url" :href="url" target="_blank" rel="noopener"><img :src="url" alt="Tệp đính kèm" /></a>
          </div>
        </template>
      </li>
    </ol>

    <!-- requester: confirm + rate once it's solved -->
    <section v-if="!staff && canRate" class="rate-box">
      <div class="rate-title">{{ ticket.status === 'RESOLVED' ? 'Vấn đề của bạn đã được giải quyết chưa?' : 'Đánh giá chất lượng hỗ trợ' }}</div>
      <div class="stars" role="radiogroup" aria-label="Mức độ hài lòng">
        <button v-for="s in 5" :key="s" type="button" :class="{ on: s <= rating }" :aria-label="`${s} sao`" @click="rating = s">★</button>
        <span class="rate-label">{{ RATING_LABELS[rating] }}</span>
      </div>
      <textarea v-model="ratingComment" rows="2" maxlength="500" placeholder="Góp ý thêm cho đội ngũ hỗ trợ (không bắt buộc)"></textarea>
      <button type="button" class="btn sm" :disabled="!rating || busy" @click="onRate">Gửi đánh giá{{ ticket.status === 'RESOLVED' ? ' & đóng yêu cầu' : '' }}</button>
    </section>
    <p v-else-if="ticket.rating" class="rated">{{ staff ? 'Người gửi' : 'Bạn' }} đã đánh giá {{ '★'.repeat(ticket.rating) }}<template v-if="ticket.ratingComment"> — "{{ ticket.ratingComment }}"</template></p>

    <form v-if="ticket.status !== 'CLOSED'" class="reply" novalidate @submit.prevent="onReply">
      <textarea v-model="draft" rows="4" maxlength="4000" :placeholder="staff ? 'Phản hồi người dùng...' : 'Nhập phản hồi bổ sung cho Shoppi...'"></textarea>
      <MediaUploader v-model="files" :max="5" add-label="Thêm ảnh" />
      <p v-if="error" class="error">{{ error }}</p>
      <div class="reply-actions">
        <button v-if="!staff && ticket.status !== 'RESOLVED'" type="button" class="btn ghost" :disabled="busy" @click="onClose">Vấn đề đã được giải quyết – Đóng yêu cầu</button>
        <template v-if="staff">
          <select v-model="replyStatus" class="status-pick" aria-label="Trạng thái sau khi gửi">
            <option :value="null">Giữ trạng thái hiện tại</option>
            <option value="WAITING_CUSTOMER">Gửi & chờ người dùng phản hồi</option>
            <option value="RESOLVED">Gửi & đánh dấu đã giải quyết</option>
          </select>
        </template>
        <button class="btn" type="submit" :disabled="!draft.trim() || busy">{{ busy ? 'Đang gửi...' : 'Gửi phản hồi' }}</button>
      </div>
    </form>
    <p v-else class="closed-note">Yêu cầu đã đóng{{ ticket.closedAt ? ' lúc ' + formatDateTime(ticket.closedAt) : '' }}. <template v-if="!staff">Nếu vẫn cần hỗ trợ, vui lòng tạo yêu cầu mới.</template></p>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import MediaUploader from '../MediaUploader.vue'
import { getTicket, replyTicket, closeTicket, rateTicket, adminGetTicket, adminReplyTicket, adminUpdateTicket } from '../../api/support'
import { useNotificationStore } from '../../stores/notifications'
import { TICKET_CATEGORIES, TICKET_STATUS, TICKET_STATUS_STAFF, TICKET_PRIORITY } from '../../utils/support'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const props = defineProps({
  ticketId: { type: Number, required: true },
  staff: { type: Boolean, default: false },  // support desk (admin) view
  orderLinks: { type: Boolean, default: false } // link the related order (buyers have an order page)
})
const emit = defineEmits(['changed'])

const RATING_LABELS = { 0: '', 1: 'Rất không hài lòng', 2: 'Không hài lòng', 3: 'Bình thường', 4: 'Hài lòng', 5: 'Rất hài lòng' }

const store = useNotificationStore()
const ticket = ref(null)
const loadError = ref('')
const draft = ref('')
const files = ref([])
const replyStatus = ref(null)
const rating = ref(0)
const ratingComment = ref('')
const busy = ref(false)
const error = ref('')

const statusNames = computed(() => (props.staff ? TICKET_STATUS_STAFF : TICKET_STATUS))
const canRate = computed(() => !ticket.value.rating && ['RESOLVED', 'CLOSED'].includes(ticket.value.status))

async function load() {
  loadError.value = ''
  try {
    const { data } = props.staff ? await adminGetTicket(props.ticketId) : await getTicket(props.ticketId)
    ticket.value = data
    if (!props.staff) store.refresh() // opening it marked its notifications read
  } catch (e) {
    loadError.value = extractError(e)
  }
}

async function run(action) {
  busy.value = true
  error.value = ''
  try {
    const { data } = await action()
    ticket.value = data
    emit('changed', data)
    return true
  } catch (e) {
    error.value = extractError(e)
    return false
  } finally {
    busy.value = false
  }
}

async function onReply() {
  const ok = await run(() => props.staff
    ? adminReplyTicket(props.ticketId, draft.value.trim(), replyStatus.value, files.value)
    : replyTicket(props.ticketId, draft.value.trim(), files.value))
  if (ok) {
    draft.value = ''
    files.value = []
    replyStatus.value = null
  }
}

const onClose = () => run(() => closeTicket(props.ticketId))
const onRate = () => run(() => rateTicket(props.ticketId, rating.value, ratingComment.value))
const update = (body) => run(() => adminUpdateTicket(props.ticketId, body))

watch(() => props.ticketId, () => {
  ticket.value = null
  load()
}, { immediate: true })
</script>

<style scoped>
.head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; padding-bottom: 14px; border-bottom: 1px solid var(--color-border); }
.head-main { min-width: 0; }
.subject { font-size: 18px; font-weight: 500; margin: 0 0 6px; word-break: break-word; }
.meta { display: flex; flex-wrap: wrap; gap: 4px 14px; font-size: 13px; color: var(--color-text-faint); margin-top: 4px; }
.meta a { color: #2673dd; }

.staff-bar { display: flex; gap: 16px; flex-wrap: wrap; padding: 12px 0; border-bottom: 1px solid var(--color-border); }
.staff-bar label { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--color-text-muted); }
.staff-bar select { margin: 0; width: auto; padding: 5px 8px; }

.messages { list-style: none; margin: 0; padding: 16px 0; display: flex; flex-direction: column; gap: 14px; }
.messages li.system { align-self: center; font-size: 12px; color: var(--color-text-faint); background: #f5f5f5; border-radius: 999px; padding: 4px 12px; text-align: center; }
.messages li.user, .messages li.staff { border: 1px solid var(--color-border); border-radius: 6px; padding: 12px 14px; max-width: 88%; }
.messages li.user { align-self: flex-end; background: #fff; }
.messages li.staff { align-self: flex-start; background: #fff7f5; border-color: #ffd9cf; }
.who { display: flex; align-items: center; gap: 8px; font-size: 13px; margin-bottom: 6px; }
.avatar { width: 26px; height: 26px; border-radius: 50%; background: #e8eef6; color: #2673dd; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; }
.staff .avatar { background: var(--color-primary); color: white; }
.time { color: var(--color-text-faint); font-size: 12px; margin-left: auto; padding-left: 12px; }
.content { white-space: pre-wrap; line-height: 1.55; font-size: 14px; word-break: break-word; }
.files { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.files img { width: 72px; height: 72px; object-fit: cover; border-radius: 4px; border: 1px solid var(--color-border); }

.rate-box { border: 1px dashed var(--color-primary); border-radius: 6px; padding: 14px; margin-bottom: 16px; background: #fffbfa; }
.rate-title { font-weight: 500; margin-bottom: 8px; }
.stars { display: flex; align-items: center; gap: 2px; margin-bottom: 10px; }
.stars button { background: none; border: none; font-size: 26px; color: #ddd; cursor: pointer; padding: 0 2px; line-height: 1; }
.stars button.on { color: var(--color-star); }
.rate-label { font-size: 13px; color: var(--color-text-muted); margin-left: 8px; }
.rated { font-size: 13px; color: var(--color-text-muted); }

.reply { border-top: 1px solid var(--color-border); padding-top: 14px; }
.reply-actions { display: flex; justify-content: flex-end; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
.status-pick { width: auto; margin: 0; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
.closed-note { text-align: center; color: var(--color-text-faint); font-size: 13px; border-top: 1px solid var(--color-border); padding-top: 14px; }
.empty { text-align: center; color: var(--color-text-faint); padding: 40px 0; }

@media (max-width: 600px) {
  .head { flex-direction: column-reverse; }
  .messages li.user, .messages li.staff { max-width: 100%; }
}
</style>
