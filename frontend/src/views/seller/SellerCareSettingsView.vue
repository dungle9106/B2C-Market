<template>
  <div>
    <!-- ===== chat auto-replies ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Tin Nhắn Tự Động</h2>
        <span class="sc-card-sub">Shop tự trả lời khi khách nhắn tin, kể cả khi bạn đang bận hoặc ngoài giờ làm việc</span>
      </div>
      <div v-if="!settings" class="sc-empty">Đang tải...</div>
      <form v-else class="auto" novalidate @submit.prevent="onSaveSettings">
        <div class="auto-block">
          <label class="switch-row">
            <input v-model="settings.autoReplyEnabled" type="checkbox" />
            <span><strong>Tin nhắn chào</strong> — gửi một lần khi khách nhắn tin cho shop lần đầu</span>
          </label>
          <textarea v-model="settings.autoReplyMessage" rows="2" maxlength="500" :disabled="!settings.autoReplyEnabled"
                    placeholder="VD: Chào {ten_khach}, cảm ơn bạn đã ghé {ten_shop}! Shop sẽ phản hồi bạn ngay ạ."></textarea>
        </div>
        <div class="auto-block">
          <label class="switch-row">
            <input v-model="settings.awayEnabled" type="checkbox" />
            <span><strong>Tin nhắn ngoài giờ làm việc</strong> — gửi khi khách nhắn ngoài giờ (tối đa 1 lần / 12 giờ mỗi cuộc trò chuyện)</span>
          </label>
          <div class="hours">
            Giờ làm việc
            <input v-model="settings.hoursStart" type="time" :disabled="!settings.awayEnabled" />
            –
            <input v-model="settings.hoursEnd" type="time" :disabled="!settings.awayEnabled" />
            <span v-if="settings.hoursStart && settings.hoursEnd" class="now" :class="{ open: settings.openNow }">
              {{ settings.openNow ? '● Đang trong giờ làm việc' : '● Đang ngoài giờ làm việc' }}
            </span>
          </div>
          <textarea v-model="settings.awayMessage" rows="2" maxlength="500" :disabled="!settings.awayEnabled"
                    placeholder="VD: Shop đã nghỉ, shop sẽ trả lời {ten_khach} vào 8h sáng mai nhé!"></textarea>
        </div>
        <p class="hint">Có thể dùng <code>{ten_khach}</code>, <code>{ten_shop}</code> trong tin nhắn. Tin nhắn tự động không được tính vào tỷ lệ phản hồi chat.</p>
        <div class="actions">
          <span v-if="settingsMsg" class="ok">{{ settingsMsg }}</span>
          <span v-if="settingsError" class="error">{{ settingsError }}</span>
          <button class="btn" type="submit" :disabled="savingSettings">{{ savingSettings ? 'Đang lưu...' : 'Lưu cài đặt' }}</button>
        </div>
      </form>
    </section>

    <!-- ===== templates ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Tin Nhắn Mẫu</h2>
        <span class="sc-card-sub">Câu trả lời soạn sẵn, chọn nhanh khi chat, trả lời hỏi đáp hoặc phản hồi đánh giá (tối đa 20 mẫu mỗi loại)</span>
      </div>
      <nav class="sc-tabs">
        <a v-for="k in KINDS" :key="k.key" href="#" :class="{ active: tab === k.key }" @click.prevent="setTab(k.key)">
          {{ k.label }} <span class="count">({{ templates?.[k.key]?.length ?? 0 }})</span>
        </a>
      </nav>

      <div v-if="!templates" class="sc-empty">Đang tải...</div>
      <template v-else>
        <ul class="tpl-list">
          <li v-for="t in templates[tab]" :key="t.id">
            <template v-if="editingId === t.id">
              <textarea v-model="editDraft" rows="2" :maxlength="maxLen"></textarea>
              <div class="tpl-actions">
                <span class="text-faint small">{{ editDraft.length }}/{{ maxLen }}</span>
                <button type="button" class="btn ghost sm" @click="editingId = null">Hủy</button>
                <button type="button" class="btn sm" :disabled="!editDraft.trim()" @click="onUpdate(t)">Lưu</button>
              </div>
            </template>
            <template v-else>
              <p class="tpl-text">{{ t.content }}</p>
              <div class="tpl-actions">
                <span class="text-faint small">Xem trước: {{ fillPlaceholders(t.content, preview) }}</span>
                <button type="button" class="link" @click="startEdit(t)">Sửa</button>
                <button type="button" class="link danger" @click="onDelete(t)">Xóa</button>
              </div>
            </template>
          </li>
          <li v-if="!templates[tab].length" class="sc-empty">Chưa có tin nhắn mẫu nào</li>
        </ul>

        <form class="new" novalidate @submit.prevent="onCreate">
          <textarea v-model="newDraft" rows="2" :maxlength="maxLen" :placeholder="`Thêm mẫu ${KINDS.find(k => k.key === tab).label.toLowerCase()} mới...`"></textarea>
          <div class="tpl-actions">
            <span class="vars">Chèn:
              <button v-for="p in PLACEHOLDERS" :key="p.key" type="button" class="var" @click="newDraft += p.key">{{ p.label }}</button>
            </span>
            <span class="text-faint small">{{ newDraft.length }}/{{ maxLen }}</span>
            <button class="btn sm" type="submit" :disabled="!newDraft.trim() || templates[tab].length >= 20">+ Thêm mẫu</button>
          </div>
        </form>
        <p v-if="tplError" class="error">{{ tplError }}</p>
      </template>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chatSettings, saveChatSettings, createTemplate, updateTemplate, deleteTemplate } from '../../api/customerCare'
import { getMyShop } from '../../api/shop'
import { useReplyTemplates, fillPlaceholders, PLACEHOLDERS } from '../../composables/useReplyTemplates'
import { extractError } from '../../utils/apiError'

const KINDS = [
  { key: 'CHAT', label: 'Chat' },
  { key: 'QUESTION', label: 'Hỏi đáp' },
  { key: 'REVIEW', label: 'Phản hồi đánh giá' }
]

const route = useRoute()
const router = useRouter()
const { templates, load } = useReplyTemplates()
const settings = ref(null)
const savingSettings = ref(false)
const settingsMsg = ref('')
const settingsError = ref('')
const tab = ref(KINDS.some(k => k.key === route.query.tab) ? route.query.tab : 'CHAT')
const newDraft = ref('')
const editingId = ref(null)
const editDraft = ref('')
const tplError = ref('')
const preview = ref({ customer: 'Nguyễn Văn An', shop: 'Shop của bạn', product: 'Áo thun cotton' })
// review replies are capped at 255 characters
const maxLen = computed(() => (tab.value === 'REVIEW' ? 255 : 500))

function setTab(k) {
  tab.value = k
  editingId.value = null
  tplError.value = ''
  router.replace({ query: { ...route.query, tab: k } })
}

async function onSaveSettings() {
  savingSettings.value = true
  settingsMsg.value = ''
  settingsError.value = ''
  try {
    const { data } = await saveChatSettings(settings.value)
    settings.value = data
    settingsMsg.value = 'Đã lưu ✓'
  } catch (e) {
    settingsError.value = extractError(e)
  } finally {
    savingSettings.value = false
  }
}

async function onCreate() {
  tplError.value = ''
  try {
    const { data } = await createTemplate(tab.value, newDraft.value)
    templates.value[tab.value].push(data)
    newDraft.value = ''
  } catch (e) {
    tplError.value = extractError(e)
  }
}

function startEdit(t) {
  editingId.value = t.id
  editDraft.value = t.content
}

async function onUpdate(t) {
  tplError.value = ''
  try {
    const { data } = await updateTemplate(t.id, editDraft.value)
    Object.assign(t, data)
    editingId.value = null
  } catch (e) {
    tplError.value = extractError(e)
  }
}

async function onDelete(t) {
  if (!confirm('Xóa tin nhắn mẫu này?')) return
  try {
    await deleteTemplate(t.id)
    templates.value[tab.value] = templates.value[tab.value].filter(x => x.id !== t.id)
  } catch (e) {
    tplError.value = extractError(e)
  }
}

onMounted(async () => {
  load(true).catch(e => { tplError.value = extractError(e) })
  chatSettings().then(({ data }) => {
    settings.value = { ...data, hoursStart: data.hoursStart || '08:00', hoursEnd: data.hoursEnd || '22:00' }
  }).catch(e => { settingsError.value = extractError(e) })
  getMyShop().then(({ data }) => { preview.value.shop = data.name }).catch(() => {})
})
</script>

<style scoped>
.auto-block { padding: 12px 0; border-bottom: 1px dashed var(--color-border); }
.switch-row { display: flex; align-items: center; gap: 10px; font-size: 14px; margin-bottom: 8px; cursor: pointer; }
.switch-row input { width: 18px; height: 18px; margin: 0; accent-color: var(--color-primary); }
.auto textarea { margin: 0; }
.auto textarea:disabled { background: #fafafa; color: var(--color-text-faint); }
.hours { display: flex; align-items: center; gap: 8px; font-size: 14px; margin-bottom: 8px; flex-wrap: wrap; }
.hours input { width: 120px; margin: 0; padding: 5px 8px; }
.now { font-size: 12px; color: #d33; }
.now.open { color: #1e9e4a; }
.hint { font-size: 12px; color: var(--color-text-faint); margin: 10px 0; }
code { background: #f5f5f5; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
.actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.ok { color: #1e9e4a; font-size: 13px; }
.error { color: #d33; font-size: 13px; }
.count { color: var(--color-text-faint); }
.tpl-list { list-style: none; margin: 0; padding: 0; }
.tpl-list li { padding: 12px 0; border-bottom: 1px solid var(--color-border); }
.tpl-text { margin: 0 0 4px; font-size: 14px; line-height: 1.5; white-space: pre-line; }
.tpl-list textarea, .new textarea { margin: 0 0 6px; }
.tpl-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tpl-actions .small { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.link.danger { color: #d33; }
.new { margin-top: 14px; }
.vars { flex: 1; font-size: 12px; color: var(--color-text-faint); display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.var { background: #f0f5ff; color: #2673dd; border: none; border-radius: 999px; padding: 2px 10px; font-size: 12px; cursor: pointer; }
</style>
