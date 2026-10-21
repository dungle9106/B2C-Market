<template>
  <section class="content-block">
    <h2 class="block-title">HỎI ĐÁP VỀ SẢN PHẨM <span v-if="total" class="count">({{ total }})</span></h2>

    <!-- ask form -->
    <form v-if="canAsk" class="ask" novalidate @submit.prevent="onAsk">
      <textarea v-model="draft" rows="2" maxlength="1000" placeholder="Đặt câu hỏi về sản phẩm này cho Người Bán (chất liệu, kích thước, bảo hành...)"></textarea>
      <div class="ask-actions">
        <span class="text-faint">{{ draft.length }}/1000</span>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="btn" type="submit" :disabled="!draft.trim() || sending">{{ sending ? 'Đang gửi...' : 'Gửi câu hỏi' }}</button>
      </div>
    </form>
    <p v-else-if="!auth.isLoggedIn" class="login-hint">
      <router-link :to="{ name: 'login', query: { redirect: $route.fullPath } }" class="text-primary">Đăng nhập</router-link>
      để đặt câu hỏi cho Người Bán.
    </p>

    <div v-if="!items.length && !loading" class="empty">Chưa có câu hỏi nào. Hãy là người đầu tiên đặt câu hỏi!</div>

    <ul class="list">
      <li v-for="q in items" :key="q.id" class="qa">
        <div class="row">
          <span class="badge q">H</span>
          <div>
            <p class="text">{{ q.question }}</p>
            <span class="meta">{{ q.askerName }} · {{ formatDateTime(q.createdAt) }}</span>
          </div>
        </div>
        <div class="row">
          <span class="badge a">Đ</span>
          <div v-if="q.answer">
            <p class="text">{{ q.answer }}</p>
            <span class="meta">Người Bán · {{ formatDateTime(q.answeredAt) }}</span>
          </div>
          <p v-else class="text pending">Người Bán chưa trả lời câu hỏi này.</p>
        </div>
      </li>
    </ul>

    <div v-if="hasMore" class="more">
      <button class="btn ghost" type="button" :disabled="loading" @click="loadMore">{{ loading ? 'Đang tải...' : 'Xem thêm câu hỏi' }}</button>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { productQuestions, askQuestion } from '../../api/questions'
import { extractError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/format'

const props = defineProps({
  productId: { type: Number, required: true },
  isOwnProduct: { type: Boolean, default: false }
})

const auth = useAuthStore()
const canAsk = computed(() => auth.isLoggedIn && !props.isOwnProduct)

const items = ref([])
const total = ref(0)
const nextPage = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const draft = ref('')
const sending = ref(false)
const error = ref('')

async function loadMore() {
  loading.value = true
  try {
    const { data } = await productQuestions(props.productId, { page: nextPage.value, size: 5 })
    items.value.push(...data.items)
    total.value = data.totalItems
    nextPage.value++
    hasMore.value = nextPage.value < data.totalPages
  } finally {
    loading.value = false
  }
}

async function onAsk() {
  error.value = ''
  sending.value = true
  try {
    const { data } = await askQuestion(props.productId, draft.value.trim())
    items.value.unshift(data)
    total.value++
    draft.value = ''
  } catch (e) {
    error.value = extractError(e)
  } finally {
    sending.value = false
  }
}

watch(() => props.productId, () => {
  items.value = []
  nextPage.value = 0
  loadMore()
}, { immediate: true })
</script>

<style scoped>
.count { color: var(--color-text-faint); font-weight: 400; }
.ask { margin-bottom: 20px; }
.ask textarea { margin-bottom: 8px; resize: vertical; }
.ask-actions { display: flex; align-items: center; gap: 12px; justify-content: flex-end; font-size: 12px; }
.ask-actions .error { color: #d33; margin: 0 auto 0 0; font-size: 13px; }
.login-hint { font-size: 14px; color: var(--color-text-muted); margin: 0 0 16px; }
.list { list-style: none; margin: 0; padding: 0; }
.qa { padding: 14px 0; border-bottom: 1px solid var(--color-border); display: flex; flex-direction: column; gap: 10px; }
.row { display: flex; gap: 10px; align-items: flex-start; }
.badge { width: 22px; height: 22px; border-radius: 3px; color: white; font-size: 12px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.badge.q { background: #2673dd; }
.badge.a { background: var(--color-primary); }
.text { margin: 0 0 4px; font-size: 14px; line-height: 1.5; white-space: pre-line; }
.text.pending { color: var(--color-text-faint); font-style: italic; }
.meta { font-size: 12px; color: var(--color-text-faint); }
.empty { color: var(--color-text-faint); padding: 16px 0; font-size: 14px; }
.more { text-align: center; margin-top: 16px; }
</style>
