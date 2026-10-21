<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Hỏi Đáp Sản Phẩm</h2>
      <span class="sc-card-sub">Câu trả lời của bạn hiển thị công khai dưới trang sản phẩm</span>
    </div>

    <nav class="sc-tabs">
      <router-link v-for="t in TABS" :key="t.key" :to="{ name: 'seller-questions', query: t.key === 'UNANSWERED' ? {} : { status: t.key } }"
                   :class="{ active: status === t.key }">
        {{ t.label }} <span class="count">({{ counts[t.count] ?? 0 }})</span>
      </router-link>
    </nav>

    <div class="sc-toolbar">
      <input v-model.trim="search" type="search" placeholder="Tìm theo nội dung câu hỏi hoặc tên sản phẩm" @input="onSearch" />
      <span class="spacer"></span>
      <router-link :to="{ name: 'seller-care-settings', query: { tab: 'QUESTION' } }" class="sc-link">⚙ Câu trả lời mẫu</router-link>
    </div>

    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="!page.items.length" class="sc-empty">
      <template v-if="search">Không tìm thấy câu hỏi phù hợp.</template>
      <template v-else-if="status === 'UNANSWERED'">Không có câu hỏi nào đang chờ. Tuyệt vời!</template>
      <template v-else-if="status === 'HIDDEN'">Không có câu hỏi nào bị ẩn.</template>
      <template v-else>Chưa có câu hỏi nào được trả lời.</template>
    </div>

    <ul v-else class="list">
      <li v-for="q in page.items" :key="q.id" class="item">
        <div class="head">
          <img :src="q.productThumbnailUrl || placeholder" alt="" />
          <div class="head-info">
            <router-link :to="`/products/${q.productId}`" class="product" target="_blank">{{ q.productName }}</router-link>
            <div class="meta">{{ q.askerFullName }} · {{ formatDateTime(q.createdAt) }}<template v-if="waiting(q)"> · <span class="late">chờ {{ waiting(q) }}</span></template></div>
          </div>
          <div class="item-actions">
            <button v-if="!q.hidden" type="button" class="link muted" title="Ẩn khỏi trang sản phẩm (spam, không liên quan)" @click="toggleHidden(q)">Ẩn</button>
            <button v-else type="button" class="link" @click="toggleHidden(q)">Hiện lại</button>
          </div>
        </div>
        <p class="question"><span class="badge">H</span>{{ q.question }}</p>

        <div v-if="q.answer && editing !== q.id" class="answer">
          <span class="badge a">Đ</span>
          <div>
            {{ q.answer }}
            <div class="meta">Trả lời {{ formatDateTime(q.answeredAt) }}<template v-if="q.answerUpdatedAt"> · đã sửa {{ formatDateTime(q.answerUpdatedAt) }}</template>
              · <button type="button" class="link" @click="startEdit(q)">Sửa</button></div>
          </div>
        </div>
        <form v-else-if="!q.hidden || editing === q.id" class="answer-form" novalidate @submit.prevent="onAnswer(q)">
          <textarea v-model="drafts[q.id]" rows="3" maxlength="2000" placeholder="Nhập câu trả lời cho người mua..."></textarea>
          <div class="form-tools">
            <TemplatePicker kind="QUESTION" :context="{ customer: q.askerFullName, shop: shopName, product: q.productName }" @pick="(t) => (drafts[q.id] = t)" />
            <button type="button" class="suggest" :disabled="suggesting === q.id" @click="onSuggest(q)">
              {{ suggesting === q.id ? 'Đang gợi ý...' : '✨ Gợi ý trả lời' }}
            </button>
            <span v-if="sources[q.id]" class="source">{{ sources[q.id] === 'ai' ? 'Gợi ý bởi AI' : 'Gợi ý từ thông tin sản phẩm' }} · hãy kiểm tra trước khi gửi</span>
            <span class="spacer"></span>
            <button v-if="editing === q.id" type="button" class="btn ghost sm" @click="editing = null">Hủy</button>
            <button class="btn sm" type="submit" :disabled="!(drafts[q.id] || '').trim() || busyId === q.id">
              {{ busyId === q.id ? 'Đang gửi...' : editing === q.id ? 'Lưu' : 'Trả lời' }}
            </button>
          </div>
          <p v-if="errors[q.id]" class="error">{{ errors[q.id] }}</p>
        </form>
      </li>
    </ul>

    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
  </section>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import TemplatePicker from '../../components/care/TemplatePicker.vue'
import { myShopQuestions, questionCounts, answerQuestion, hideQuestion, unhideQuestion, suggestAnswer } from '../../api/questions'
import { getMyShop } from '../../api/shop'
import { extractError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/format'

const TABS = [
  { key: 'UNANSWERED', label: 'Chờ trả lời', count: 'unanswered' },
  { key: 'ANSWERED', label: 'Đã trả lời', count: 'answered' },
  { key: 'HIDDEN', label: 'Đã ẩn', count: 'hidden' }
]
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const route = useRoute()
const status = computed(() => (['ANSWERED', 'HIDDEN'].includes(route.query.status) ? route.query.status : 'UNANSWERED'))
const page = ref({ items: [], page: 0, totalPages: 0 })
const counts = ref({})
const loading = ref(true)
const search = ref('')
const drafts = reactive({})
const errors = reactive({})
const sources = reactive({})
const busyId = ref(null)
const suggesting = ref(null)
const editing = ref(null)
const shopName = ref('')
let searchTimer = null

async function load(p = 0) {
  loading.value = true
  try {
    const { data } = await myShopQuestions({ status: status.value, q: search.value, page: p })
    page.value = data
  } finally {
    loading.value = false
  }
}
const loadCounts = () => questionCounts().then(({ data }) => { counts.value = data }).catch(() => {})

function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => load(0), 300)
}

// "chờ 3 ngày" on old unanswered questions, so the oldest get answered first
function waiting(q) {
  if (q.answer || q.hidden) return ''
  const hours = (Date.now() - new Date(q.createdAt)) / 3600000
  if (hours < 24) return ''
  return Math.floor(hours / 24) + ' ngày'
}

function startEdit(q) {
  editing.value = q.id
  drafts[q.id] = q.answer
  errors[q.id] = ''
}

async function onAnswer(q) {
  errors[q.id] = ''
  busyId.value = q.id
  try {
    const { data } = await answerQuestion(q.id, drafts[q.id].trim())
    Object.assign(q, data) // shows the answer in place; it moves to "Đã trả lời" on next load
    editing.value = null
    delete sources[q.id]
    loadCounts()
  } catch (e) {
    errors[q.id] = extractError(e)
  } finally {
    busyId.value = null
  }
}

async function onSuggest(q) {
  suggesting.value = q.id
  errors[q.id] = ''
  try {
    const { data } = await suggestAnswer(q.id)
    drafts[q.id] = data.answer
    sources[q.id] = data.source
  } catch (e) {
    errors[q.id] = extractError(e)
  } finally {
    suggesting.value = null
  }
}

async function toggleHidden(q) {
  try {
    await (q.hidden ? unhideQuestion(q.id) : hideQuestion(q.id))
    page.value.items = page.value.items.filter(x => x.id !== q.id)
    loadCounts()
  } catch (e) {
    errors[q.id] = extractError(e)
  }
}

watch(status, () => load(0), { immediate: true })
onMounted(() => {
  loadCounts()
  getMyShop().then(({ data }) => { shopName.value = data.name }).catch(() => {})
})
</script>

<style scoped>
.count { color: var(--color-text-faint); margin-left: 2px; }
.sc-toolbar input { flex: 1; max-width: 420px; }
.list { list-style: none; margin: 0; padding: 0; }
.item { padding: 16px 0; border-bottom: 1px solid var(--color-border); }
.head { display: flex; align-items: center; gap: 10px; }
.head img { width: 40px; height: 40px; object-fit: cover; border-radius: 2px; flex-shrink: 0; }
.head-info { flex: 1; min-width: 0; }
.product { font-weight: 500; color: #2673dd; font-size: 14px; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { font-size: 12px; color: var(--color-text-faint); margin-top: 2px; }
.late { color: #d33; }
.question, .answer { display: flex; gap: 8px; margin: 10px 0 4px 50px; font-size: 14px; line-height: 1.5; white-space: pre-line; }
.badge { width: 20px; height: 20px; border-radius: 3px; background: #2673dd; color: white; font-size: 11px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; }
.badge.a { background: var(--color-primary); }
.answer-form { margin: 10px 0 0 78px; }
.answer-form textarea { margin-bottom: 8px; }
.form-tools { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.form-tools .spacer { flex: 1; }
.suggest { background: #f3f0ff; color: #6941c6; border: 1px solid #e0d8ff; border-radius: 4px; padding: 5px 10px; font-size: 13px; cursor: pointer; }
.suggest:hover:not(:disabled) { background: #ebe5ff; }
.source { font-size: 12px; color: #6941c6; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 2px; }
.link.muted { color: var(--color-text-muted); }
.link.muted:hover { color: #d33; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
@media (max-width: 640px) { .question, .answer, .answer-form { margin-left: 0; } }
</style>
