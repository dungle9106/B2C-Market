<template>
  <div>
    <div class="container page">
      <router-link to="/admin" class="back">‹ Tổng quan toàn sàn</router-link>
      <div class="layout">
        <section class="shopee-section">
          <h2 class="title">Gửi Thông Báo</h2>
          <p class="sub">Thông báo xuất hiện ở biểu tượng chuông của người nhận. Voucher Shoppi mới được thông báo tự động cho người mua.</p>
          <form class="form" novalidate @submit.prevent="onSend">
            <div class="row">
              <div>
                <label>Loại thông báo</label>
                <select v-model="form.type">
                  <option value="PROMOTION">Khuyến mãi</option>
                  <option value="SYSTEM">Tin từ Shoppi</option>
                </select>
              </div>
              <div>
                <label>Người nhận</label>
                <select v-model="form.audience">
                  <option :value="null">Tất cả người dùng</option>
                  <option value="BUYER">Người mua</option>
                  <option value="SELLER">Người bán</option>
                </select>
              </div>
            </div>
            <label>Tiêu đề <span class="req">*</span></label>
            <input v-model="form.title" maxlength="200" placeholder="VD: Siêu sale 10.10 – Giảm đến 50%" />
            <label>Nội dung</label>
            <textarea v-model="form.content" rows="4" maxlength="1000" placeholder="Chi tiết chương trình..."></textarea>
            <label>Liên kết khi bấm vào (không bắt buộc)</label>
            <input v-model.trim="form.link" maxlength="300" placeholder="VD: /search?q=ao thun" />

            <div class="preview">
              <span class="pv-label">Xem trước</span>
              <div class="pv-item">
                <span class="pv-icon">{{ form.type === 'PROMOTION' ? '🎁' : '🔔' }}</span>
                <div>
                  <div class="pv-title">{{ form.title || 'Tiêu đề thông báo' }}</div>
                  <div class="pv-content">{{ form.content || 'Nội dung thông báo' }}</div>
                </div>
              </div>
            </div>

            <p v-if="error" class="error">{{ error }}</p>
            <p v-if="done" class="done">Đã gửi tới {{ done }} người dùng.</p>
            <button class="btn" type="submit" :disabled="busy || !form.title.trim()">{{ busy ? 'Đang gửi...' : 'Gửi thông báo' }}</button>
          </form>
        </section>

        <section class="shopee-section">
          <h2 class="title">Đã gửi gần đây</h2>
          <div v-if="!history.length" class="empty">Chưa gửi thông báo nào.</div>
          <ul v-else class="history">
            <li v-for="b in history" :key="b.id">
              <div class="h-title">{{ b.type === 'PROMOTION' ? '🎁' : '🔔' }} {{ b.title }}</div>
              <div v-if="b.content" class="h-content">{{ b.content }}</div>
              <div class="h-meta">{{ AUDIENCE[b.audience] }} · {{ b.recipients }} người nhận · {{ formatDateTime(b.createdAt) }}</div>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listBroadcasts, sendBroadcast } from '../../api/notifications'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const AUDIENCE = { null: 'Tất cả người dùng', BUYER: 'Người mua', SELLER: 'Người bán' }

const form = reactive({ type: 'PROMOTION', audience: null, title: '', content: '', link: '' })
const history = ref([])
const busy = ref(false)
const error = ref('')
const done = ref(0)

async function loadHistory() {
  const { data } = await listBroadcasts()
  history.value = data
}

async function onSend() {
  error.value = ''
  done.value = 0
  if (form.link && !form.link.startsWith('/')) return (error.value = 'Liên kết phải là đường dẫn trong Shoppi, bắt đầu bằng /')
  const who = AUDIENCE[form.audience].toLowerCase()
  if (!confirm(`Gửi thông báo "${form.title}" tới ${who}?`)) return
  busy.value = true
  try {
    const { data } = await sendBroadcast({ ...form, link: form.link || null })
    done.value = data.recipients
    Object.assign(form, { title: '', content: '', link: '' })
    loadHistory()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

onMounted(loadHistory)
</script>

<style scoped>
.page { padding-bottom: 40px; }
.back { display: inline-block; margin: 16px 0 0; color: var(--color-text-muted); }
.layout { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; align-items: start; }
.title { font-size: 18px; font-weight: 500; margin: 0 0 4px; }
.sub { font-size: 13px; color: var(--color-text-faint); margin: 0 0 16px; }
.form label { display: block; font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; }
.req { color: var(--color-primary); }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.preview { background: #fafafa; border-radius: 4px; padding: 10px 12px; margin-bottom: 14px; }
.pv-label { font-size: 12px; color: var(--color-text-faint); }
.pv-item { display: flex; gap: 10px; margin-top: 6px; background: var(--color-primary-light); padding: 10px; border-radius: 3px; }
.pv-icon { font-size: 22px; }
.pv-title { font-weight: 500; }
.pv-content { font-size: 13px; color: var(--color-text-muted); margin-top: 2px; white-space: pre-line; }
.error { color: #d33; font-size: 13px; }
.done { color: var(--color-success); font-size: 13px; }
.history { list-style: none; margin: 8px 0 0; padding: 0; }
.history li { padding: 12px 0; border-bottom: 1px solid var(--color-border); }
.h-title { font-weight: 500; }
.h-content { font-size: 13px; color: var(--color-text-muted); margin-top: 4px; white-space: pre-line; }
.h-meta { font-size: 12px; color: var(--color-text-faint); margin-top: 6px; }
.empty { color: var(--color-text-faint); padding: 24px 0; text-align: center; }
@media (max-width: 900px) { .layout, .row { grid-template-columns: 1fr; } }
</style>
