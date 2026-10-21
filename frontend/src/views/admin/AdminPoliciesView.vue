<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Chính Sách Chung</h2>
      <span class="sc-card-sub">Mỗi lần xuất bản tạo một phiên bản mới; người dùng luôn xem phiên bản mới nhất, các phiên bản cũ được lưu lại</span>
    </div>
    <div class="layout">
      <aside class="list">
        <button v-for="p in list" :key="p.key" type="button" class="item" :class="{ active: selected === p.key }" @click="open(p.key)">
          <strong>{{ p.title }}</strong>
          <span class="faint">{{ AUDIENCE[p.audience] }} · Phiên bản {{ p.version }}{{ p.builtin ? ' (mặc định)' : '' }}</span>
          <span v-if="p.publishedAt" class="faint">Cập nhật {{ formatDateTime(p.publishedAt) }}</span>
        </button>
      </aside>

      <div class="panel">
        <div v-if="!doc" class="sc-empty">{{ error || 'Đang tải...' }}</div>
        <template v-else>
          <div class="panel-head">
            <span>Đang hiệu lực: <b>phiên bản {{ doc.version }}</b>{{ doc.publishedByName ? ' · ' + doc.publishedByName + ', ' + formatDateTime(doc.publishedAt) : ' · nội dung mặc định' }}</span>
            <router-link :to="`/policies/${doc.slug}`" target="_blank" class="view">Xem trang công khai ↗</router-link>
          </div>
          <label>Tiêu đề<input v-model.trim="form.title" maxlength="200" /></label>
          <label>Nội dung <span class="faint">(mỗi dòng một đoạn; dòng bắt đầu bằng "#" là tiêu đề mục)</span>
            <textarea v-model="form.content" rows="16"></textarea>
          </label>
          <label>Ghi chú thay đổi <span class="faint">(hiển thị cho người đọc và trong thông báo)</span>
            <input v-model.trim="form.changeNote" maxlength="500" placeholder="VD: Bổ sung quy định về hoàn tiền đơn COD" />
          </label>
          <label class="check"><input v-model="form.notify" type="checkbox" /> Thông báo cho {{ AUDIENCE[doc.audience].toLowerCase() }} về phiên bản mới</label>
          <div class="actions">
            <span v-if="msg" class="ok">{{ msg }}</span>
            <span v-if="error" class="error">{{ error }}</span>
            <button type="button" class="btn ghost" :disabled="!changed" @click="reset">Hoàn tác</button>
            <button type="button" class="btn" :disabled="!changed || saving" @click="publish">{{ saving ? 'Đang xuất bản...' : 'Xuất bản phiên bản ' + (doc.version + 1) }}</button>
          </div>

          <h4 class="hist-h">Lịch sử phiên bản</h4>
          <div v-if="!doc.history.length" class="faint">Chưa có phiên bản nào ngoài nội dung mặc định.</div>
          <div v-for="h in doc.history" :key="h.version" class="hist">
            <b>v{{ h.version }}</b>
            <span>{{ h.title }}</span>
            <span class="faint">{{ formatDateTime(h.publishedAt) }} · {{ h.publishedByName }}</span>
            <span v-if="h.changeNote" class="note">{{ h.changeNote }}</span>
            <router-link :to="`/policies/${doc.slug}?version=${h.version}`" target="_blank" class="view">Xem</router-link>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminPolicies, publishPolicy } from '../../api/admin'
import { policy } from '../../api/policies'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const AUDIENCE = { ALL: 'Mọi người dùng', BUYER: 'Người mua', SELLER: 'Người bán' }
const list = ref([])
const selected = ref(null)
const doc = ref(null)
const form = ref({})
const saving = ref(false)
const msg = ref('')
const error = ref('')

const changed = computed(() => doc.value && (form.value.title !== doc.value.title || form.value.content !== doc.value.content))
const reset = () => { form.value = { title: doc.value.title, content: doc.value.content, changeNote: '', notify: false } }

async function open(key) {
  selected.value = key
  doc.value = null
  msg.value = ''
  error.value = ''
  try {
    doc.value = (await policy(key)).data
    reset()
  } catch (e) {
    error.value = extractError(e)
  }
}

async function publish() {
  if (!confirm(`Xuất bản phiên bản ${doc.value.version + 1} của "${form.value.title}"? Phiên bản mới có hiệu lực ngay.`)) return
  saving.value = true
  error.value = ''
  try {
    const { data } = await publishPolicy(doc.value.key, form.value)
    msg.value = `Đã xuất bản phiên bản ${data.version}` + (data.notified ? ` · đã thông báo ${data.notified} người dùng` : '')
    list.value = (await adminPolicies()).data
    const keep = msg.value
    await open(doc.value.key)
    msg.value = keep
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  list.value = (await adminPolicies()).data
  if (list.value.length) open(list.value[0].key)
})
</script>

<style scoped>
.layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 16px; align-items: start; }
.list { border: 1px solid var(--color-border); border-radius: 6px; overflow: hidden; }
.item { display: flex; flex-direction: column; gap: 2px; width: 100%; text-align: left; padding: 10px 12px; border: none; border-bottom: 1px solid var(--color-border); background: white; cursor: pointer; font-size: 14px; }
.item:hover { background: #fafafa; }
.item.active { background: var(--color-primary-light); box-shadow: inset 3px 0 0 var(--color-primary); }
.faint { color: var(--color-text-faint); font-size: 12px; }
.panel { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
.panel-head { display: flex; justify-content: space-between; gap: 10px; font-size: 13px; color: var(--color-text-muted); flex-wrap: wrap; }
.view { color: #2673dd; font-size: 13px; }
.panel label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--color-text-muted); }
.panel textarea { width: 100%; font-family: inherit; line-height: 1.6; }
.check { flex-direction: row !important; align-items: center; }
.check input { width: auto; }
.actions { display: flex; justify-content: flex-end; align-items: center; gap: 10px; }
.hist-h { margin: 14px 0 4px; font-size: 14px; }
.hist { display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; padding: 6px 0; border-bottom: 1px dashed var(--color-border); font-size: 13px; }
.note { flex-basis: 100%; color: var(--color-text-muted); padding-left: 28px; }
.ok { color: #1e9e4a; font-size: 13px; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
</style>
