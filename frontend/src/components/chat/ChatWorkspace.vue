<template>
  <div class="chat" :class="{ 'has-active': !!active, 'with-panel': showPanel }">
    <!-- ===== conversation list ===== -->
    <aside class="conv-pane">
      <div class="conv-head">
        <strong>{{ side === 'seller' ? 'Chat với Người mua' : 'Tin nhắn' }}</strong>
        <input v-model.trim="filter" type="search" :placeholder="side === 'seller' ? 'Tìm theo tên khách hàng' : 'Tìm theo tên shop'" />
        <div v-if="side === 'seller'" class="list-tabs">
          <button v-for="t in LIST_TABS" :key="t.key" type="button" :class="{ active: listTab === t.key }" @click="listTab = t.key">
            {{ t.label }}<span v-if="t.key !== 'all' && tabCount(t.key)" class="tab-count">{{ tabCount(t.key) }}</span>
          </button>
        </div>
      </div>
      <div v-if="loadingList" class="pane-empty">Đang tải...</div>
      <div v-else-if="!filtered.length" class="pane-empty">
        <template v-if="filter">Không tìm thấy cuộc trò chuyện nào</template>
        <template v-else-if="listTab === 'unread'">Không có tin nhắn chưa đọc 👍</template>
        <template v-else-if="listTab === 'awaiting'">Bạn đã trả lời tất cả khách hàng 👍</template>
        <template v-else-if="side === 'seller'">Chưa có người mua nào nhắn tin cho shop.</template>
        <template v-else>Chưa có cuộc trò chuyện nào.<br />Bấm "Chat ngay" trên trang sản phẩm để nhắn tin với shop.</template>
      </div>
      <ul v-else class="conv-list">
        <li v-for="c in filtered" :key="c.id" :class="{ active: active?.id === c.id }" @click="select(c.id)">
          <span class="avatar">
            <img v-if="side === 'buyer' && c.shopLogoUrl" :src="c.shopLogoUrl" alt="" />
            <template v-else>{{ initial(peerName(c)) }}</template>
          </span>
          <div class="conv-body">
            <div class="conv-top">
              <span class="conv-name">{{ peerName(c) }}</span>
              <span class="conv-time">{{ shortTime(c.lastMessageAt) }}</span>
            </div>
            <div class="conv-bottom">
              <span class="conv-last">{{ c.lastMessage || 'Bắt đầu trò chuyện' }}</span>
              <span v-if="c.unread" class="unread">{{ c.unread > 99 ? '99+' : c.unread }}</span>
              <span v-else-if="side === 'seller' && c.awaitingReply" class="awaiting" title="Khách đang chờ shop trả lời">Chờ trả lời</span>
            </div>
          </div>
        </li>
      </ul>
    </aside>

    <!-- ===== thread ===== -->
    <section class="thread-pane">
      <div v-if="!active" class="thread-empty">
        <div class="big-icon">💬</div>
        <p>Chọn một cuộc trò chuyện để bắt đầu</p>
      </div>

      <template v-else>
        <header class="thread-head">
          <button type="button" class="back" aria-label="Quay lại danh sách" @click="closeThread">‹</button>
          <span class="avatar sm">
            <img v-if="side === 'buyer' && active.shopLogoUrl" :src="active.shopLogoUrl" alt="" />
            <template v-else>{{ initial(peerName(active)) }}</template>
          </span>
          <div class="thread-title">
            <strong>{{ peerName(active) }}</strong>
            <span v-if="side === 'buyer' && !active.shopActive" class="text-faint small">Shop đang tạm ngưng hoạt động</span>
          </div>
          <router-link v-if="side === 'buyer'" :to="{ name: 'shop', params: { id: active.shopId } }" class="btn secondary sm">Xem Shop</router-link>
          <template v-if="side === 'seller'">
            <span v-if="stats?.responseRate !== null && stats?.responseRate !== undefined" class="stats-chip" title="Tỷ lệ phản hồi chat trong 30 ngày (trả lời trong 24 giờ)">
              Tỷ lệ phản hồi {{ stats.responseRate.toLocaleString('vi-VN') }}%<template v-if="stats.responseTime"> · {{ stats.responseTime }}</template>
            </span>
            <button type="button" class="btn ghost sm panel-toggle" @click="panelOpen = !panelOpen">{{ panelOpen ? 'Ẩn thông tin' : 'Thông tin khách' }}</button>
          </template>
        </header>

        <div ref="scrollEl" class="messages" @scroll="onScroll">
          <button v-if="hasOlder" type="button" class="older" :disabled="loadingOlder" @click="loadOlder">
            {{ loadingOlder ? 'Đang tải...' : 'Xem tin nhắn cũ hơn' }}
          </button>
          <div v-if="!messages.length && !loadingThread" class="hint-start">
            {{ side === 'buyer' ? `Hãy gửi lời chào đến ${active.shopName} 👋` : 'Chưa có tin nhắn' }}
          </div>

          <template v-for="(m, i) in messages" :key="m.id">
            <div v-if="newDay(i)" class="day">{{ dayLabel(m.createdAt) }}</div>
            <div class="msg" :class="{ mine: isMine(m) }">
              <div class="bubble-wrap">
                <router-link v-if="m.product" :to="`/products/${m.product.id}`" class="card-msg" target="_blank">
                  <img :src="m.product.thumbnailUrl || placeholder" alt="" />
                  <div>
                    <div class="card-name">{{ m.product.name }}</div>
                    <div class="text-primary">{{ formatVnd(m.product.price) }}</div>
                  </div>
                </router-link>
                <router-link v-if="m.order" :to="side === 'buyer' ? `/orders/${m.order.id}` : `/seller/orders/${m.order.id}`" class="card-msg">
                  <img :src="m.order.thumbnailUrl || placeholder" alt="" />
                  <div>
                    <div class="card-name">Đơn hàng {{ m.order.code }}</div>
                    <div class="small text-muted">{{ m.order.itemCount }} sản phẩm · {{ statusLabel(m.order.status) }}</div>
                    <div class="text-primary">{{ formatVnd(m.order.total) }}</div>
                  </div>
                </router-link>
                <a v-if="m.imageUrl" :href="m.imageUrl" target="_blank" rel="noopener" class="img-msg"><img :src="m.imageUrl" alt="Hình ảnh" @load="stickToBottom" /></a>
                <div v-if="m.content" class="bubble" :class="{ auto: m.autoReply }">{{ m.content }}</div>
                <span class="time"><template v-if="m.autoReply">Tin nhắn tự động · </template>{{ clock(m.createdAt) }}<template v-if="m.pending"> · Đang gửi</template><template v-if="m.failed"> · <span class="failed">Gửi lỗi</span></template></span>
              </div>
            </div>
          </template>
        </div>

        <div class="footer">
        <!-- attachment waiting to be sent with the next message ("Ban dang trao doi ve san pham nay") -->
        <div v-if="attachment" class="attachment">
          <img :src="attachment.thumbnailUrl || placeholder" alt="" />
          <div class="att-body">
            <div class="small text-faint">{{ attachment.kind === 'product' ? 'Bạn đang trao đổi về sản phẩm' : 'Bạn đang hỏi về đơn hàng' }}</div>
            <div class="att-name">{{ attachment.title }}</div>
          </div>
          <button type="button" class="btn sm" :disabled="sending" @click="send({ attachOnly: true })">Gửi</button>
          <button type="button" class="att-close" aria-label="Bỏ đính kèm" @click="attachment = null">✕</button>
        </div>

        <div v-if="side === 'seller'" class="seller-tools">
          <TemplatePicker kind="CHAT" up :disabled="!canSend" :context="{ customer: active.buyerName, shop: shopName }" @pick="onPickTemplate" />
          <button type="button" class="suggest" :disabled="suggesting" title="Gợi ý câu trả lời cho tin nhắn mới nhất của khách" @click="onSuggest">
            {{ suggesting ? 'Đang gợi ý...' : '✨ Gợi ý trả lời' }}
          </button>
          <span v-if="suggestSource" class="source">{{ suggestSource === 'ai' ? 'Gợi ý bởi AI' : 'Gợi ý từ thông tin sản phẩm' }} · kiểm tra trước khi gửi</span>
        </div>
        <div v-if="quickReplies.length" class="quick">
          <button v-for="q in quickReplies" :key="q" type="button" :disabled="!canSend || sending" @click="sendText(q)">{{ q }}</button>
        </div>

        <form class="composer" @submit.prevent="send()">
          <label class="icon-btn" :class="{ disabled: !canSend || uploading }" title="Gửi hình ảnh">
            <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden :disabled="!canSend || uploading" @change="onPickImage" />
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.7" /><circle cx="9" cy="10" r="2" stroke="currentColor" stroke-width="1.7" /><path d="m21 16-5-5-8 9" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>
          </label>
          <textarea
            ref="inputEl"
            v-model="draft"
            rows="1"
            maxlength="2000"
            :disabled="!canSend"
            :placeholder="canSend ? 'Nhập nội dung tin nhắn... (Enter để gửi)' : 'Shop đang tạm ngưng hoạt động'"
            @keydown.enter.exact.prevent="send()"
          ></textarea>
          <button class="btn" type="submit" :disabled="!canSend || sending || (!draft.trim() && !attachment)">Gửi</button>
        </form>
        <p v-if="error" class="error">{{ error }}</p>
        </div>
      </template>
    </section>

    <ChatCustomerPanel v-if="showPanel" :conversation-id="active.id" :can-send="canSend && !sending" @send="post" />
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listChats, getChat, startChat, chatMessages, sendChatMessage, markChatRead, chatResponseStats, suggestChatReply } from '../../api/chat'
import { getProduct } from '../../api/products'
import { getOrder } from '../../api/orders'
import { uploadFile } from '../../api/uploads'
import { useNotificationStore } from '../../stores/notifications'
import { onRealtime, connected } from '../../realtime/socket'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'
import { statusLabel } from '../../utils/orderStatus'
import { getMyShop } from '../../api/shop'
import { useReplyTemplates, fillPlaceholders } from '../../composables/useReplyTemplates'
import TemplatePicker from '../care/TemplatePicker.vue'
import ChatCustomerPanel from './ChatCustomerPanel.vue'

const props = defineProps({
  side: { type: String, required: true } // 'buyer' | 'seller'
})

const THREAD_POLL_MS = 4000
const LIST_POLL_MS = 15000
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="60" height="60"><rect width="60" height="60" fill="%23f0f0f0"/></svg>'

const route = useRoute()
const router = useRouter()
const store = useNotificationStore()

const conversations = ref([])
const loadingList = ref(true)
const filter = ref('')
const active = ref(null)
const messages = ref([])
const loadingThread = ref(false)
const hasOlder = ref(false)
const loadingOlder = ref(false)
const draft = ref('')
const attachment = ref(null) // { kind: 'product' | 'order', id, title, thumbnailUrl }
const sending = ref(false)
const uploading = ref(false)
const error = ref('')
const scrollEl = ref(null)
const inputEl = ref(null)
let atBottom = true
let threadTimer = null
let listTimer = null

// seller only: "Tất cả / Chưa đọc / Chờ trả lời"
const LIST_TABS = [{ key: 'all', label: 'Tất cả' }, { key: 'unread', label: 'Chưa đọc' }, { key: 'awaiting', label: 'Chờ trả lời' }]
const listTab = ref('all')
const tabMatch = { all: () => true, unread: (c) => c.unread > 0, awaiting: (c) => c.awaitingReply }
const tabCount = (key) => conversations.value.filter(tabMatch[key]).length
const filtered = computed(() => {
  const q = filter.value.toLowerCase()
  return conversations.value.filter(tabMatch[listTab.value]).filter(c => !q || peerName(c).toLowerCase().includes(q))
})

// seller tools
const stats = ref(null)
const shopName = ref('')
const panelOpen = ref(true)
const showPanel = computed(() => props.side === 'seller' && !!active.value && panelOpen.value)
const suggesting = ref(false)
const suggestSource = ref('')
const { templates, load: loadTemplates } = useReplyTemplates()

function onPickTemplate(text) {
  draft.value = text
  suggestSource.value = ''
  nextTick(() => inputEl.value?.focus())
}

async function onSuggest() {
  suggesting.value = true
  error.value = ''
  try {
    const { data } = await suggestChatReply(active.value.id)
    draft.value = data.answer
    suggestSource.value = data.source
    nextTick(() => inputEl.value?.focus())
  } catch (e) {
    error.value = extractError(e)
  } finally {
    suggesting.value = false
  }
}
const canSend = computed(() => !!active.value && (props.side === 'seller' || active.value.shopActive))

const quickReplies = computed(() => {
  if (!active.value) return []
  if (props.side === 'seller') {
    // the shop's own chat templates ("Tin nhắn mẫu"), first few as one-click chips
    return (templates.value?.CHAT || []).slice(0, 4).map(t => fillPlaceholders(t.content, { customer: active.value.buyerName, shop: shopName.value }))
  }
  if (attachment.value?.kind === 'product') return ['Sản phẩm này còn hàng không shop?', 'Shop có giảm giá khi mua nhiều không?', 'Sản phẩm có bảo hành không ạ?']
  if (attachment.value?.kind === 'order') return ['Khi nào đơn hàng của tôi được giao?', 'Tôi muốn đổi địa chỉ nhận hàng', 'Shop kiểm tra giúp tôi đơn này nhé']
  return messages.value.length ? [] : ['Xin chào shop!', 'Shop ơi, tư vấn giúp mình với']
})

const peerName = (c) => (props.side === 'seller' ? c.buyerName : c.shopName) || ''
const initial = (name) => (name || '?').trim().charAt(0).toUpperCase()
const isMine = (m) => (props.side === 'seller' ? m.senderSide === 'SHOP' : m.senderSide === 'BUYER')
const clock = (d) => new Date(d).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })

function shortTime(d) {
  if (!d) return ''
  const date = new Date(d)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) return clock(d)
  return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })
}
function dayLabel(d) {
  const date = new Date(d)
  const today = new Date()
  const yesterday = new Date(Date.now() - 86400000)
  if (date.toDateString() === today.toDateString()) return 'Hôm nay'
  if (date.toDateString() === yesterday.toDateString()) return 'Hôm qua'
  return date.toLocaleDateString('vi-VN')
}
function newDay(i) {
  if (i === 0) return true
  return new Date(messages.value[i - 1].createdAt).toDateString() !== new Date(messages.value[i].createdAt).toDateString()
}

// ---- list ----

async function loadList() {
  try {
    const { data } = await listChats(props.side)
    // a thread opened from "Chat ngay" has no message yet, so the server leaves it out of the list
    if (active.value && !data.some(c => c.id === active.value.id)) data.unshift(active.value)
    conversations.value = data
  } finally {
    loadingList.value = false
  }
}

// ---- thread ----

function select(id) {
  router.replace({ query: { c: id } })
}

function closeThread() {
  router.replace({ query: {} })
}

async function openThread(id) {
  stopThreadPolling()
  error.value = ''
  suggestSource.value = ''
  if (!id) {
    active.value = null
    messages.value = []
    return
  }
  loadingThread.value = true
  try {
    const known = conversations.value.find(c => c.id === id)
    active.value = known || (await getChat(props.side, id)).data
    const { data } = await chatMessages(props.side, id)
    messages.value = data
    hasOlder.value = data.length >= 30
    atBottom = true
    await scrollToBottom()
    await markRead()
    // with the WebSocket up, new messages arrive as events: poll only every 30 s as a safety net
    let tick = 0
    threadTimer = setInterval(() => { if (!connected.value || ++tick % 8 === 0) pollThread() }, THREAD_POLL_MS)
    nextTick(() => inputEl.value?.focus())
  } catch (e) {
    error.value = extractError(e)
    active.value = null
  } finally {
    loadingThread.value = false
  }
}

async function markRead() {
  if (!active.value) return
  const c = conversations.value.find(x => x.id === active.value.id)
  if (c) c.unread = 0
  await markChatRead(props.side, active.value.id).catch(() => {})
  store.refresh()
}

function lastServerId() {
  const real = messages.value.filter(m => !m.pending && !m.failed)
  return real.length ? real[real.length - 1].id : 0
}

async function pollThread() {
  if (!active.value || document.hidden) return
  const id = active.value.id
  try {
    const { data } = await chatMessages(props.side, id, { after: lastServerId() })
    if (!data.length || active.value?.id !== id) return
    const known = new Set(messages.value.map(m => m.id))
    const fresh = data.filter(m => !known.has(m.id))
    if (!fresh.length) return
    messages.value.push(...fresh)
    if (fresh.some(m => !isMine(m))) {
      await markRead()
      loadList()
    }
    if (atBottom) scrollToBottom()
  } catch {
    // transient network error: the next tick retries
  }
}

async function loadOlder() {
  if (!messages.value.length) return
  loadingOlder.value = true
  const el = scrollEl.value
  const prevHeight = el.scrollHeight
  try {
    const { data } = await chatMessages(props.side, active.value.id, { before: messages.value[0].id })
    hasOlder.value = data.length >= 30
    messages.value.unshift(...data)
    await nextTick()
    el.scrollTop = el.scrollHeight - prevHeight // keep the reader's position
  } finally {
    loadingOlder.value = false
  }
}

// ---- sending ----

async function send({ attachOnly = false } = {}) {
  const text = attachOnly ? '' : draft.value.trim()
  if (!attachOnly) suggestSource.value = ''
  if (!canSend.value || (!text && !attachment.value)) return
  const body = { content: text || undefined }
  const att = attachment.value
  if (att?.kind === 'product') body.productId = att.id
  if (att?.kind === 'order') body.orderId = att.id
  if (!attachOnly) draft.value = ''
  attachment.value = null
  await post(body, { content: text, product: att?.kind === 'product' ? { id: att.id, name: att.title, thumbnailUrl: att.thumbnailUrl, price: att.price } : null,
    order: att?.kind === 'order' ? { id: att.id, code: att.code, status: att.status, total: att.price, thumbnailUrl: att.thumbnailUrl, itemCount: att.itemCount } : null })
}

function sendText(text) {
  draft.value = text
  send()
}

async function onPickImage(e) {
  const file = e.target.files[0]
  e.target.value = ''
  if (!file) return
  uploading.value = true
  error.value = ''
  try {
    const { data } = await uploadFile(file)
    await post({ imageUrl: data.url }, { imageUrl: data.url })
  } catch (err) {
    error.value = extractError(err)
  } finally {
    uploading.value = false
  }
}

// optimistic: the bubble shows at once and is replaced by the server's copy
async function post(body, preview) {
  error.value = ''
  sending.value = true
  const temp = { id: `tmp-${Date.now()}`, senderSide: props.side === 'seller' ? 'SHOP' : 'BUYER', createdAt: new Date().toISOString(), pending: true, ...preview }
  messages.value.push(temp)
  atBottom = true
  scrollToBottom()
  try {
    const { data } = await sendChatMessage(props.side, active.value.id, body)
    const i = messages.value.indexOf(temp)
    if (i >= 0) messages.value.splice(i, 1, data)
    const c = conversations.value.find(x => x.id === active.value.id)
    if (c) {
      if (props.side === 'seller') c.awaitingReply = false
      c.lastMessage = data.content || (data.imageUrl ? '[Hình ảnh]' : data.product ? `[Sản phẩm] ${data.product.name}` : `[Đơn hàng] ${data.order?.code}`)
      c.lastMessageAt = data.createdAt
      conversations.value = [c, ...conversations.value.filter(x => x !== c)]
    }
  } catch (e) {
    temp.pending = false
    temp.failed = true
    error.value = extractError(e)
  } finally {
    sending.value = false
  }
}

// ---- scrolling ----

function onScroll() {
  const el = scrollEl.value
  atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 60
}
async function scrollToBottom() {
  await nextTick()
  if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
}
function stickToBottom() {
  if (atBottom) scrollToBottom()
}

function stopThreadPolling() {
  clearInterval(threadTimer)
  threadTimer = null
}

// ---- entry from a product / order page: /messages?shopId=3&productId=12 (buyer) ----

async function startFromQuery() {
  const shopId = Number(route.query.shopId)
  const { data: conv } = await startChat(shopId)
  if (route.query.productId) {
    const { data: p } = await getProduct(route.query.productId)
    attachment.value = { kind: 'product', id: p.id, title: p.name, thumbnailUrl: p.thumbnailUrl, price: p.price }
  } else if (route.query.orderId) {
    const { data: o } = await getOrder(route.query.orderId)
    attachment.value = { kind: 'order', id: o.id, code: o.code, status: o.status, title: `Đơn hàng ${o.code}`, price: o.total,
      thumbnailUrl: o.items[0]?.thumbnailUrl, itemCount: o.items.length }
  }
  if (!conversations.value.some(c => c.id === conv.id)) conversations.value.unshift(conv)
  await router.replace({ query: { c: conv.id } })
}

watch(() => route.query.c, (c) => openThread(c ? Number(c) : null))

onMounted(async () => {
  await loadList()
  if (props.side === 'buyer' && route.query.shopId) {
    try {
      await startFromQuery()
    } catch (e) {
      error.value = extractError(e)
    }
  } else if (route.query.c) {
    openThread(Number(route.query.c))
  }
  let tick = 0
  listTimer = setInterval(() => { if (!document.hidden && (!connected.value || ++tick % 4 === 0)) loadList() }, LIST_POLL_MS)
  // a new message for me: the open thread and the conversation list update at once
  unlisten = onRealtime((e) => {
    if (e.type !== 'chat') return
    if (active.value && e.conversationId === active.value.id) pollThread()
    loadList()
  })
  if (props.side === 'seller') {
    loadTemplates().catch(() => {})
    chatResponseStats().then(({ data }) => { stats.value = data }).catch(() => {})
    getMyShop().then(({ data }) => { shopName.value = data.name }).catch(() => {})
  }
})

let unlisten = null

onBeforeUnmount(() => {
  stopThreadPolling()
  clearInterval(listTimer)
  unlisten?.()
})
</script>

<style scoped>
.chat {
  display: grid; grid-template-columns: 320px 1fr;
  height: min(680px, calc(100vh - 200px)); min-height: 460px;
  background: white; border-radius: 4px; box-shadow: var(--shadow-card); overflow: hidden;
}

.chat.with-panel { grid-template-columns: 300px minmax(0, 1fr) 280px; }

/* ---- list ---- */
.conv-pane { border-right: 1px solid var(--color-border); display: flex; flex-direction: column; min-height: 0; }
.conv-head { padding: 14px 14px 4px; border-bottom: 1px solid var(--color-border); }
.conv-head strong { display: block; font-size: 16px; color: var(--color-primary); margin-bottom: 10px; }
.conv-head input { margin-bottom: 10px; padding: 7px 10px; font-size: 13px; }
.conv-list { list-style: none; margin: 0; padding: 0; overflow-y: auto; flex: 1; }
.conv-list li { display: flex; gap: 10px; padding: 12px 14px; cursor: pointer; border-bottom: 1px solid #f6f6f6; }
.conv-list li:hover { background: #fafafa; }
.conv-list li.active { background: var(--color-primary-light); }
.avatar {
  width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0; overflow: hidden;
  background: #ffe1d9; color: var(--color-primary); font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.avatar.sm { width: 34px; height: 34px; font-size: 14px; }
.conv-body { flex: 1; min-width: 0; }
.conv-top, .conv-bottom { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.conv-name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-time { font-size: 12px; color: var(--color-text-faint); flex-shrink: 0; }
.conv-last { font-size: 13px; color: var(--color-text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 3px; }
.unread { background: var(--color-primary); color: white; font-size: 11px; border-radius: 999px; padding: 0 6px; line-height: 18px; flex-shrink: 0; }
.list-tabs { display: flex; gap: 4px; margin-bottom: 10px; }
.list-tabs button { flex: 1; background: white; border: 1px solid var(--color-border); border-radius: 4px; padding: 4px 6px; font-size: 12px; cursor: pointer; color: var(--color-text-muted); white-space: nowrap; }
.list-tabs button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.tab-count { background: var(--color-primary); color: white; border-radius: 999px; font-size: 10px; padding: 0 5px; margin-left: 4px; }
.awaiting { font-size: 10px; color: #e67e22; border: 1px solid #f5c58a; border-radius: 3px; padding: 0 4px; flex-shrink: 0; white-space: nowrap; }
.stats-chip { font-size: 12px; color: #1e9e4a; background: #eaf7ef; border-radius: 999px; padding: 3px 10px; white-space: nowrap; }
.bubble.auto { background: #f0f5ff; border: 1px dashed #b9d0f5; }
.seller-tools { display: flex; align-items: center; gap: 8px; padding: 8px 16px 0; flex-wrap: wrap; }
.suggest { background: #f3f0ff; color: #6941c6; border: 1px solid #e0d8ff; border-radius: 4px; padding: 5px 10px; font-size: 13px; cursor: pointer; }
.suggest:hover:not(:disabled) { background: #ebe5ff; }
.source { font-size: 12px; color: #6941c6; }
.pane-empty { padding: 40px 20px; text-align: center; color: var(--color-text-faint); font-size: 13px; line-height: 1.6; }

/* ---- thread ---- */
.thread-pane { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.thread-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--color-text-faint); background: #fafafa; }
.big-icon { font-size: 54px; opacity: 0.4; }
.thread-head { display: flex; align-items: center; gap: 10px; padding: 10px 16px; border-bottom: 1px solid var(--color-border); }
.thread-title { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.thread-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.small { font-size: 12px; }
.back { display: none; background: none; border: none; font-size: 26px; line-height: 1; cursor: pointer; color: var(--color-text-muted); padding: 0 6px 0 0; }

.messages { flex: 1; overflow-y: auto; padding: 16px; background: #f7f7f7; }
.older { display: block; margin: 0 auto 12px; background: white; border: 1px solid var(--color-border); border-radius: 999px; padding: 5px 14px; font-size: 12px; cursor: pointer; color: var(--color-text-muted); }
.hint-start { text-align: center; color: var(--color-text-faint); font-size: 13px; margin: 40px 0; }
.day { text-align: center; font-size: 12px; color: var(--color-text-faint); margin: 14px 0 10px; }
.msg { display: flex; margin-bottom: 8px; }
.msg.mine { justify-content: flex-end; }
.bubble-wrap { max-width: min(72%, 460px); display: flex; flex-direction: column; gap: 4px; align-items: flex-start; }
.msg.mine .bubble-wrap { align-items: flex-end; }
.bubble { background: white; padding: 8px 12px; border-radius: 12px; border-bottom-left-radius: 3px; font-size: 14px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; box-shadow: 0 1px 1px rgba(0, 0, 0, 0.04); }
.msg.mine .bubble { background: #ffe9e3; border-bottom-left-radius: 12px; border-bottom-right-radius: 3px; }
.img-msg img { max-width: 240px; max-height: 240px; border-radius: 8px; display: block; }
.card-msg { display: flex; gap: 10px; background: white; border: 1px solid var(--color-border); border-radius: 8px; padding: 8px; width: 280px; max-width: 100%; }
.card-msg:hover { border-color: var(--color-primary); }
.card-msg img { width: 56px; height: 56px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
.card-name { font-size: 13px; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.time { font-size: 11px; color: var(--color-text-faint); }
.failed { color: #d33; }

.footer { border-top: 1px solid var(--color-border); }
.attachment { display: flex; align-items: center; gap: 10px; padding: 8px 16px; border-bottom: 1px solid var(--color-border); background: #fffaf8; }
.attachment img { width: 40px; height: 40px; border-radius: 4px; object-fit: cover; }
.att-body { flex: 1; min-width: 0; }
.att-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.att-close { background: none; border: none; cursor: pointer; color: var(--color-text-faint); font-size: 14px; }

.quick { display: flex; gap: 6px; padding: 8px 16px 0; overflow-x: auto; }
.quick button { flex-shrink: 0; background: white; border: 1px solid var(--color-border); border-radius: 999px; padding: 5px 12px; font-size: 12px; cursor: pointer; color: var(--color-text-muted); }
.quick button:hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary); }
.composer { display: flex; align-items: flex-end; gap: 8px; padding: 10px 16px 12px; }
.composer textarea { margin: 0; resize: none; max-height: 120px; min-height: 38px; line-height: 1.4; }
.icon-btn { display: flex; align-items: center; justify-content: center; width: 38px; height: 38px; color: var(--color-text-muted); cursor: pointer; flex-shrink: 0; border-radius: 4px; }
.icon-btn:hover { background: #f5f5f5; color: var(--color-primary); }
.icon-btn.disabled { opacity: 0.4; cursor: not-allowed; }
.error { color: #d33; font-size: 13px; margin: 0; padding: 0 16px 10px; }

@media (max-width: 1250px) {
  .chat.with-panel { grid-template-columns: 280px minmax(0, 1fr); }
  .chat.with-panel .panel { display: none; }
  .panel-toggle { display: none; }
}

/* one pane at a time on narrow screens */
@media (max-width: 760px) {
  .chat { grid-template-columns: 1fr; height: calc(100vh - 170px); }
  .chat.has-active .conv-pane { display: none; }
  .chat:not(.has-active) .thread-pane { display: none; }
  .back { display: block; }
  .bubble-wrap { max-width: 85%; }
}
</style>
