<template>
  <!-- the review inbox: most urgent first; a review opens on the right with its analysis and the reply to approve -->
  <div class="tw-grid tw-gap-4 lg:tw-grid-cols-[minmax(0,1fr)_420px]">
    <section class="sc-card tw-mb-0 tw-min-w-0">
      <div class="tw-mb-3 tw-flex tw-flex-wrap tw-items-center tw-gap-2">
        <button v-for="t in TABS" :key="t.key" type="button" class="chip" :class="{ on: tab === t.key }" @click="setTab(t.key)">{{ t.label }}</button>
        <span class="tw-flex-1"></span>
        <button type="button" class="btn sm ghost" :disabled="busy" @click="draftUrgent" title="Soạn sẵn phản hồi cho 20 đánh giá ưu tiên cao chưa xử lý">✨ Soạn nháp hàng loạt</button>
      </div>
      <div class="tw-mb-3 tw-grid tw-grid-cols-2 tw-gap-2 md:tw-grid-cols-3">
        <select v-model="f.source"><option value="">Mọi sàn</option><option v-for="(l, k) in SOURCES" :key="k" :value="k">{{ l }}</option></select>
        <select v-model="f.maxRating"><option value="">Mọi số sao</option><option value="2">1-2 sao</option><option value="3">≤ 3 sao</option></select>
        <select v-model="f.sentiment"><option value="">Mọi cảm xúc</option><option v-for="(l, k) in SENTIMENTS" :key="k" :value="k">{{ l }}</option></select>
        <select v-model="f.issue"><option value="">Mọi vấn đề</option><option v-for="t in issueTypes" :key="t.key" :value="t.key">{{ t.label }}</option></select>
        <select v-model="f.sort"><option value="priority">Ưu tiên cao trước</option><option value="newest">Mới nhất</option><option value="rating">Ít sao trước</option></select>
        <input v-model.trim="f.q" type="search" placeholder="Tìm nội dung, sản phẩm, khách" @input="onSearch" />
      </div>

      <div v-if="loading" class="sc-empty">Đang tải...</div>
      <div v-else-if="!page.items.length" class="sc-empty">Không có đánh giá nào khớp bộ lọc</div>
      <ul v-else class="tw-m-0 tw-list-none tw-p-0">
        <li v-for="i in page.items" :key="i.id" class="tw-cursor-pointer tw-border-0 tw-border-b tw-border-solid tw-border-neutral-100 tw-px-2 tw-py-3 hover:tw-bg-neutral-50"
            :class="{ 'tw-bg-primary-light': selected?.id === i.id }" @click="open(i.id)">
          <div class="tw-flex tw-flex-wrap tw-items-center tw-gap-1.5 tw-text-xs">
            <span class="lvl" :class="i.priorityLevel">{{ LEVELS[i.priorityLevel] }} · {{ i.priority }}</span>
            <span class="src" :style="{ background: SOURCE_COLORS[i.source] }">{{ SOURCES[i.source] }}</span>
            <span class="tw-text-amber-500">{{ '★'.repeat(i.rating) }}<span class="tw-text-neutral-300">{{ '★'.repeat(5 - i.rating) }}</span></span>
            <span v-for="x in i.issues" :key="x.key" class="issue">{{ x.label }}</span>
            <span v-if="i.anomalyStatus === 'FLAGGED'" class="anom">⚠ Bất thường</span>
            <span v-if="i.overdue" class="tw-font-semibold tw-text-red-600">Quá hạn trả lời</span>
            <span class="tw-ml-auto tw-text-neutral-400">{{ STATUSES[i.status] }} · {{ ago(i.reviewedAt) }}</span>
          </div>
          <div class="tw-mt-1 tw-text-sm tw-font-medium">{{ i.productName || 'Sản phẩm không xác định' }} <span class="tw-font-normal tw-text-neutral-400">· {{ i.reviewerName || 'Ẩn danh' }}</span></div>
          <div class="tw-mt-0.5 tw-line-clamp-2 tw-text-sm tw-text-neutral-600">{{ i.content || '(không có nội dung)' }}</div>
        </li>
      </ul>
      <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
    </section>

    <!-- ===== detail ===== -->
    <aside class="sc-card tw-mb-0 tw-self-start lg:tw-sticky lg:tw-top-4">
      <div v-if="!selected" class="sc-empty">Chọn một đánh giá để xem phân tích và soạn phản hồi</div>
      <template v-else>
        <div class="tw-flex tw-items-start tw-gap-2">
          <div class="tw-min-w-0 tw-flex-1">
            <div class="tw-text-xs tw-text-neutral-400">{{ SOURCES[selected.source] }}<template v-if="selected.orderRef"> · Đơn {{ selected.orderRef }}</template> · {{ fmt(selected.reviewedAt) }}</div>
            <div class="tw-font-semibold">{{ selected.productName }}</div>
            <div class="tw-text-sm">{{ selected.reviewerName || 'Ẩn danh' }} · <span class="tw-text-amber-500">{{ '★'.repeat(selected.rating) }}</span></div>
          </div>
          <span class="lvl" :class="selected.priorityLevel">{{ LEVELS[selected.priorityLevel] }}</span>
        </div>
        <blockquote class="tw-mx-0 tw-my-3 tw-whitespace-pre-wrap tw-rounded tw-bg-neutral-50 tw-p-3 tw-text-sm">{{ selected.content || '(không có nội dung)' }}<span v-if="selected.mediaCount" class="tw-block tw-text-xs tw-text-neutral-400">📷 {{ selected.mediaCount }} ảnh/video</span></blockquote>

        <dl class="tw-m-0 tw-grid tw-grid-cols-[110px_1fr] tw-gap-x-2 tw-gap-y-1.5 tw-text-sm">
          <dt class="tw-text-neutral-500">Cảm xúc</dt><dd class="tw-m-0" :class="sentClass(selected.sentiment)">{{ SENTIMENTS[selected.sentiment] }}</dd>
          <dt class="tw-text-neutral-500">Vấn đề</dt>
          <dd class="tw-m-0">
            <template v-if="!editIssues">
              <span v-for="x in selected.issues" :key="x.key" class="issue">{{ x.label }}</span>
              <span v-if="!selected.issues.length" class="tw-text-neutral-400">Không có</span>
              <button type="button" class="link" @click="startEditIssues">Sửa</button>
              <div v-if="selected.issueSummary" class="tw-text-xs tw-text-neutral-500">{{ selected.issueSummary }}</div>
            </template>
            <div v-else class="tw-flex tw-flex-wrap tw-gap-1">
              <label v-for="t in issueTypes" :key="t.key" class="tw-flex tw-items-center tw-gap-1 tw-text-xs">
                <input v-model="issueDraft" type="checkbox" :value="t.key" class="!tw-w-auto" /> {{ t.label }}
              </label>
              <button type="button" class="btn sm" @click="saveIssues">Lưu</button>
              <button type="button" class="link" @click="editIssues = false">Hủy</button>
            </div>
          </dd>
          <dt class="tw-text-neutral-500">Vì sao ưu tiên</dt>
          <dd class="tw-m-0"><ul class="tw-m-0 tw-pl-4 tw-text-xs"><li v-for="r in selected.priorityReasons" :key="r">{{ r }}</li></ul></dd>
          <template v-if="selected.status !== 'REPLIED' && selected.rating <= 3">
            <dt class="tw-text-neutral-500">Hạn trả lời</dt><dd class="tw-m-0" :class="selected.overdue ? 'tw-font-semibold tw-text-red-600' : ''">{{ fmt(selected.replyDueAt) }}</dd>
          </template>
          <template v-if="selected.anomalyFlags.length">
            <dt class="tw-text-neutral-500">Dấu hiệu bất thường</dt>
            <dd class="tw-m-0 tw-text-xs"><span class="anom">{{ selected.anomalyScore }}/100</span> {{ selected.anomalyNote }}</dd>
          </template>
          <dt class="tw-text-neutral-500">Phân tích bởi</dt><dd class="tw-m-0 tw-text-xs tw-text-neutral-500">{{ BY[selected.analyzedBy] || selected.analyzedBy }}<template v-if="selected.aiPending"> · AI đang xử lý...</template></dd>
        </dl>

        <!-- ===== reply ===== -->
        <div class="tw-mt-4 tw-border-0 tw-border-t tw-border-solid tw-border-neutral-200 tw-pt-3">
          <template v-if="selected.status === 'REPLIED'">
            <div class="tw-text-sm tw-font-semibold tw-text-green-700">✓ Đã trả lời {{ selected.replyChannel === 'SHOPPI' ? 'trên Shoppi' : 'trên ' + SOURCES[selected.source] }}<template v-if="selected.repliedAt"> · {{ fmt(selected.repliedAt) }}</template></div>
            <p class="tw-my-2 tw-whitespace-pre-wrap tw-text-sm">{{ selected.replyText }}</p>
            <button v-if="selected.source !== 'SHOPPI'" type="button" class="link" @click="reopen">Mở lại</button>
          </template>
          <template v-else>
            <div class="tw-mb-1 tw-flex tw-items-center tw-gap-2">
              <strong class="tw-text-sm">Phản hồi</strong>
              <span v-if="selected.draftSource" class="tw-text-xs tw-text-neutral-400">{{ selected.draftSource === 'AI' ? 'AI soạn' : selected.draftSource === 'TEMPLATE' ? 'Theo mẫu tình huống' : 'Bạn đã sửa' }}</span>
              <button type="button" class="btn sm ghost tw-ml-auto" :disabled="busy" @click="draft">{{ busy ? 'Đang soạn...' : '✨ Soạn bằng AI' }}</button>
            </div>
            <textarea v-model="reply" rows="6" :maxlength="selected.replyMax" placeholder="Nhập phản hồi hoặc bấm Soạn bằng AI"></textarea>
            <div class="tw-flex tw-items-center tw-justify-between tw-text-xs tw-text-neutral-400">
              <span>{{ selected.source === 'SHOPPI' ? 'Đăng ngay trên Shoppi' : 'Sau khi duyệt, dán phản hồi lên ' + SOURCES[selected.source] }}</span>
              <span :class="reply.length > selected.replyMax ? 'tw-text-red-600' : ''">{{ reply.length }}/{{ selected.replyMax }}</span>
            </div>
            <p v-if="error" class="tw-text-sm tw-text-red-600">{{ error }}</p>
            <div class="tw-mt-2 tw-flex tw-flex-wrap tw-gap-2">
              <button type="button" class="btn sm" :disabled="busy || !reply.trim()" @click="approve">Duyệt & {{ selected.source === 'SHOPPI' ? 'đăng' : 'sao chép' }}</button>
              <button type="button" class="btn sm ghost" :disabled="busy || !reply.trim()" @click="saveDraft">Lưu nháp</button>
              <button type="button" class="link tw-ml-auto" :disabled="busy" @click="ignore">Bỏ qua</button>
            </div>
          </template>
          <p v-if="msg" class="tw-text-sm tw-text-green-700">{{ msg }}</p>
        </div>
      </template>
    </aside>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PaginationBar from '../PaginationBar.vue'
import { hubItems, hubItem, hubDraft, hubSaveDraft, hubReply, hubIgnore, hubReopen, hubSetIssues, hubBulkDraft,
  SOURCES, SOURCE_COLORS, LEVELS, STATUSES, SENTIMENTS } from '../../api/reviewHub'
import { extractError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/format'

const props = defineProps({ issueTypes: { type: Array, default: () => [] }, preset: { type: Object, default: null } })
const emit = defineEmits(['changed'])

const TABS = [
  { key: 'OPEN', label: 'Cần xử lý' },
  { key: 'URGENT', label: 'Khẩn cấp' },
  { key: 'OVERDUE', label: 'Quá hạn' },
  { key: 'REPLIED', label: 'Đã trả lời' },
  { key: 'ALL', label: 'Tất cả' }
]
const BY = { RULE: 'Bộ luật tiếng Việt', SERVICE: 'Mô hình FastAPI', AI: 'AI (LLM)' }

const route = useRoute()
const tab = ref('OPEN')
const f = ref({ source: '', maxRating: '', sentiment: '', issue: '', sort: 'priority', q: '' })
const page = ref({ items: [], page: 0, totalPages: 0 })
const loading = ref(false)
const selected = ref(null)
const reply = ref('')
const busy = ref(false)
const error = ref('')
const msg = ref('')
const editIssues = ref(false)
const issueDraft = ref([])
let timer = null

const fmt = (t) => (t ? formatDateTime(t) : '')
const sentClass = (s) => (s === 'NEGATIVE' ? 'tw-text-red-600' : s === 'POSITIVE' ? 'tw-text-green-700' : '')
function ago(t) {
  const h = Math.floor((Date.now() - new Date(t).getTime()) / 3600000)
  return h < 1 ? 'vừa xong' : h < 24 ? `${h} giờ trước` : `${Math.floor(h / 24)} ngày trước`
}

function filters() {
  const x = { ...f.value }
  if (tab.value === 'OPEN') x.status = 'OPEN'
  if (tab.value === 'URGENT') { x.status = 'OPEN'; x.level = 'URGENT' }
  if (tab.value === 'OVERDUE') x.overdue = true
  if (tab.value === 'REPLIED') x.status = 'REPLIED'
  return x
}

async function load(p = 0) {
  loading.value = true
  try {
    page.value = (await hubItems(filters(), p)).data
  } finally {
    loading.value = false
  }
}

function setTab(k) {
  tab.value = k
  load(0)
}
function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => load(0), 350)
}

async function open(id) {
  error.value = ''
  msg.value = ''
  editIssues.value = false
  selected.value = (await hubItem(id)).data
  reply.value = selected.value.draftReply || ''
}

function replace(item) {
  selected.value = item
  const k = page.value.items.findIndex(x => x.id === item.id)
  if (k >= 0) page.value.items[k] = item
}

async function act(fn, okText) {
  busy.value = true
  error.value = ''
  msg.value = ''
  try {
    await fn()
    if (okText) msg.value = okText
    emit('changed')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

const draft = () => act(async () => {
  const { data } = await hubDraft(selected.value.id)
  replace(data)
  reply.value = data.draftReply || ''
})
const saveDraft = () => act(async () => replace((await hubSaveDraft(selected.value.id, reply.value)).data), 'Đã lưu bản nháp')
const approve = () => act(async () => {
  const { data } = await hubReply(selected.value.id, reply.value)
  replace(data)
  if (data.replyChannel === 'EXTERNAL') {
    try { await navigator.clipboard.writeText(data.replyText) } catch { /* clipboard blocked */ }
  }
}, selected.value?.source === 'SHOPPI' ? 'Đã đăng phản hồi lên Shoppi' : 'Đã sao chép phản hồi, hãy dán lên trang người bán của sàn')
const ignore = () => act(async () => replace((await hubIgnore(selected.value.id)).data), 'Đã bỏ qua')
const reopen = () => act(async () => {
  const { data } = await hubReopen(selected.value.id)
  replace(data)
  reply.value = data.replyText || data.draftReply || ''
})
function startEditIssues() {
  issueDraft.value = selected.value.issues.map(x => x.key)
  editIssues.value = true
}
const saveIssues = () => act(async () => {
  replace((await hubSetIssues(selected.value.id, issueDraft.value)).data)
  editIssues.value = false
}, 'Đã lưu phân loại (dùng để huấn luyện mô hình)')
const draftUrgent = () => act(async () => {
  const { data } = await hubBulkDraft([])
  await load(page.value.page)
  msg.value = `Đã soạn ${data.drafted} bản nháp${data.byAi ? ` (${data.byAi} bằng AI)` : ''}`
})

watch(() => [f.value.source, f.value.maxRating, f.value.sentiment, f.value.issue, f.value.sort], () => load(0))
// opened from the overview, a notification or the recurring issues
watch(() => props.preset, (p) => {
  if (!p) return
  Object.assign(f.value, { source: '', maxRating: '', sentiment: '', issue: p.issue || '', q: '' })
  tab.value = p.tab || 'ALL'
  load(0)
  if (p.item) open(p.item)
}, { immediate: false })

onMounted(() => {
  load(0)
  if (route.query.item) open(Number(route.query.item))
})
defineExpose({ load, open })
</script>

<style scoped>
.chip { border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chip.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.lvl { font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 3px; background: #eee; color: #555; white-space: nowrap; }
.lvl.URGENT { background: #fdecea; color: #c62828; }
.lvl.HIGH { background: #fff3e0; color: #e65100; }
.lvl.NORMAL { background: #e8f1fd; color: #1e63c4; }
.src { color: white; font-size: 10px; padding: 1px 6px; border-radius: 3px; }
.issue { display: inline-block; font-size: 11px; padding: 1px 6px; margin: 0 4px 2px 0; border-radius: 3px; background: #f3f0ff; color: #5b3cc4; }
.anom { font-size: 11px; padding: 1px 6px; border-radius: 3px; background: #fff8e1; color: #b26a00; font-weight: 600; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 4px; }
textarea { width: 100%; resize: vertical; font-size: 14px; }
</style>
