<template>
  <!-- chat with the shop without leaving the product page; the product rides along with the first message -->
  <section class="tw-fixed tw-bottom-0 tw-right-4 tw-z-50 tw-flex tw-h-[480px] tw-max-h-[calc(100vh-16px)] tw-w-[360px] tw-max-w-[calc(100vw-32px)]
                  tw-flex-col tw-overflow-hidden tw-rounded-t-lg tw-bg-white tw-shadow-2xl max-sm:tw-right-0 max-sm:tw-w-full max-sm:tw-max-w-full"
           role="dialog" :aria-label="`Chat với ${shopName}`">
    <header class="tw-flex tw-items-center tw-gap-2 tw-bg-primary tw-px-3 tw-py-2.5 tw-text-white">
      <span class="tw-min-w-0 tw-flex-1 tw-truncate tw-text-sm tw-font-semibold">💬 {{ shopName }}</span>
      <router-link v-if="conv" :to="{ name: 'messages', query: { c: conv.id } }" class="tw-text-xs tw-text-white tw-underline">Mở trang Tin nhắn</router-link>
      <button type="button" class="tw-cursor-pointer tw-border-0 tw-bg-transparent tw-px-1 tw-text-lg tw-leading-none tw-text-white" aria-label="Đóng" @click="$emit('close')">×</button>
    </header>

    <div ref="list" class="tw-flex-1 tw-space-y-2 tw-overflow-y-auto tw-bg-neutral-50 tw-p-3">
      <p v-if="loading" class="tw-text-center tw-text-sm tw-text-neutral-400">Đang tải...</p>
      <p v-else-if="error" class="tw-text-center tw-text-sm tw-text-red-600">{{ error }}</p>
      <template v-else>
        <p v-if="!messages.length" class="tw-text-center tw-text-xs tw-text-neutral-400">Hãy hỏi shop về sản phẩm này, shop sẽ trả lời bạn tại đây.</p>
        <div v-for="m in messages" :key="m.id" class="tw-flex" :class="m.senderSide === 'BUYER' ? 'tw-justify-end' : 'tw-justify-start'">
          <div class="tw-max-w-[80%] tw-rounded-lg tw-px-3 tw-py-2 tw-text-sm"
               :class="m.senderSide === 'BUYER' ? 'tw-bg-primary-light' : 'tw-bg-white tw-shadow-sm'">
            <router-link v-if="m.product" :to="{ name: 'product-detail', params: { id: m.product.id } }"
                         class="tw-mb-1 tw-flex tw-items-center tw-gap-2 tw-rounded tw-border tw-border-solid tw-border-neutral-200 tw-bg-white tw-p-1.5 tw-text-neutral-800 tw-no-underline">
              <img v-if="m.product.thumbnailUrl" :src="m.product.thumbnailUrl" alt="" class="tw-h-10 tw-w-10 tw-rounded tw-object-cover" />
              <span class="tw-min-w-0">
                <span class="tw-line-clamp-2 tw-text-xs">{{ m.product.name }}</span>
                <span class="tw-text-xs tw-font-semibold tw-text-primary">{{ formatVnd(m.product.price) }}</span>
              </span>
            </router-link>
            <img v-if="m.imageUrl" :src="m.imageUrl" alt="Ảnh" class="tw-max-h-40 tw-max-w-full tw-rounded" />
            <div v-if="m.order" class="tw-text-xs">Đơn hàng {{ m.order.code }}</div>
            <div v-if="m.content" class="tw-whitespace-pre-wrap tw-break-words">{{ m.content }}</div>
            <div class="tw-mt-0.5 tw-text-[10px] tw-text-neutral-400">{{ m.autoReply ? 'Tự động · ' : '' }}{{ time(m.createdAt) }}</div>
          </div>
        </div>
      </template>
    </div>

    <!-- the product being asked about, sent with the next message -->
    <div v-if="attach && !loading && !error" class="tw-flex tw-items-center tw-gap-2 tw-border-0 tw-border-t tw-border-solid tw-border-neutral-200 tw-bg-white tw-px-3 tw-py-2">
      <img v-if="product.thumbnailUrl" :src="product.thumbnailUrl" alt="" class="tw-h-9 tw-w-9 tw-rounded tw-object-cover" />
      <span class="tw-min-w-0 tw-flex-1">
        <span class="tw-block tw-truncate tw-text-xs">{{ product.name }}</span>
        <span class="tw-text-xs tw-text-neutral-400">Sẽ gửi kèm tin nhắn</span>
      </span>
      <button type="button" class="tw-cursor-pointer tw-border-0 tw-bg-transparent tw-text-neutral-400" aria-label="Bỏ đính kèm sản phẩm" @click="attach = false">×</button>
    </div>

    <form class="tw-flex tw-gap-2 tw-border-0 tw-border-t tw-border-solid tw-border-neutral-200 tw-bg-white tw-p-2" @submit.prevent="send">
      <input ref="input" v-model="text" maxlength="1000" placeholder="Nhập tin nhắn..." :disabled="loading || !!error"
             class="tw-min-w-0 tw-flex-1" />
      <button type="submit" class="btn sm" :disabled="sending || (!text.trim() && !attach) || loading || !!error">Gửi</button>
    </form>
  </section>
</template>

<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { startChat, chatMessages, sendChatMessage, markChatRead } from '../../api/chat'
import { onRealtime, connected } from '../../realtime/socket'
import { useNotificationStore } from '../../stores/notifications'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'

const props = defineProps({
  // { id, name, thumbnailUrl, shopId }
  product: { type: Object, required: true },
  shopName: { type: String, default: 'Shop' }
})
defineEmits(['close'])

const conv = ref(null)
const messages = ref([])
const loading = ref(true)
const error = ref('')
const text = ref('')
const sending = ref(false)
const attach = ref(true)
const list = ref(null)
const input = ref(null)
const store = useNotificationStore()
let timer = null
let unlisten = null

const time = (t) => (t ? new Date(t).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : '')

async function toBottom() {
  await nextTick()
  if (list.value) list.value.scrollTop = list.value.scrollHeight
}

async function loadNew() {
  if (!conv.value) return
  const last = messages.value.length ? messages.value[messages.value.length - 1].id : undefined
  try {
    const { data } = await chatMessages('buyer', conv.value.id, last ? { after: last } : {})
    const known = new Set(messages.value.map(m => m.id))
    const fresh = data.filter(m => !known.has(m.id))
    if (!fresh.length) return
    messages.value.push(...fresh)
    if (fresh.some(m => m.senderSide !== 'BUYER')) markChatRead('buyer', conv.value.id).then(() => store.refresh()).catch(() => {})
    toBottom()
  } catch { /* the next event or tick retries */ }
}

async function send() {
  const content = text.value.trim()
  if (!content && !attach.value) return
  sending.value = true
  try {
    const { data } = await sendChatMessage('buyer', conv.value.id, {
      content: content || null,
      productId: attach.value ? props.product.id : null
    })
    messages.value.push(data)
    text.value = ''
    attach.value = false
    toBottom()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  try {
    conv.value = (await startChat(props.product.shopId)).data
    messages.value = (await chatMessages('buyer', conv.value.id)).data
    // already sent this product in the latest messages: no need to attach it again
    attach.value = !messages.value.slice(-10).some(m => m.product?.id === props.product.id)
    markChatRead('buyer', conv.value.id).then(() => store.refresh()).catch(() => {})
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
  toBottom()
  nextTick(() => input.value?.focus())
  // the shop's replies arrive by WebSocket; a slow poll is the fallback
  unlisten = onRealtime((e) => { if (e.type === 'chat' && e.conversationId === conv.value?.id) loadNew() })
  let tick = 0
  timer = setInterval(() => { if (!document.hidden && (!connected.value || ++tick % 6 === 0)) loadNew() }, 5000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  unlisten?.()
})
</script>
