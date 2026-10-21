<template>
  <div>
    <section v-for="p in PLACEMENTS" :key="p.key" class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">{{ p.label }}</h2>
        <span class="sc-card-sub">{{ p.hint }}</span>
        <button type="button" class="btn sm add" @click="open(null, p.key)">+ Thêm banner</button>
      </div>
      <div v-if="!banners[p.key]?.length" class="sc-empty">Chưa có banner nào{{ p.key === 'HOME_CAROUSEL' ? ' – trang chủ đang dùng banner mặc định' : '' }}</div>
      <div v-for="(b, i) in banners[p.key] || []" :key="b.id" class="banner" :class="{ dim: b.status !== 'SHOWING' }">
        <div class="order">
          <button type="button" :disabled="i === 0" aria-label="Lên" @click="move(p.key, i, -1)">▲</button>
          <span>{{ i + 1 }}</span>
          <button type="button" :disabled="i === banners[p.key].length - 1" aria-label="Xuống" @click="move(p.key, i, 1)">▼</button>
        </div>
        <img :src="b.imageUrl" :alt="b.title" class="thumb" :class="p.key" />
        <div class="body">
          <div class="title"><strong>{{ b.title }}</strong> <span class="st" :class="b.status">{{ STATUS[b.status] }}</span></div>
          <div class="meta">
            <template v-if="b.linkUrl">→ <router-link :to="b.linkUrl" target="_blank">{{ b.linkUrl }}</router-link></template>
            <template v-else>Không có liên kết</template>
            <template v-if="b.campaignName"> · 🎯 {{ b.campaignName }}</template>
          </div>
          <div class="meta">{{ b.startAt ? formatDateTime(b.startAt) : 'Ngay' }} → {{ b.endAt ? formatDateTime(b.endAt) : 'Không giới hạn' }}</div>
        </div>
        <div class="stats">
          <div><b>{{ b.views.toLocaleString('vi-VN') }}</b><span>lượt hiển thị</span></div>
          <div><b>{{ b.clicks.toLocaleString('vi-VN') }}</b><span>lượt nhấp</span></div>
          <div><b>{{ b.ctr }}%</b><span>CTR</span></div>
        </div>
        <div class="actions">
          <button type="button" class="link" @click="open(b)">Sửa</button>
          <button type="button" class="link" @click="run(() => toggleBanner(b.id))">{{ b.active ? 'Ẩn' : 'Hiện' }}</button>
          <button type="button" class="link danger" @click="confirm(`Xóa banner ${b.title}?`) && run(() => deleteBanner(b.id))">Xóa</button>
        </div>
      </div>
    </section>
    <p v-if="error" class="error">{{ error }}</p>

    <BaseModal v-if="form" :title="form.id ? 'Sửa banner' : 'Thêm banner'" :width="620" @close="form = null">
      <form id="banner-form" class="form" @submit.prevent="save">
        <label>Vị trí
          <select v-model="form.placement">
            <option v-for="p in PLACEMENTS" :key="p.key" :value="p.key">{{ p.label }}</option>
          </select>
        </label>
        <label>Tiêu đề *<input v-model.trim="form.title" maxlength="120" placeholder="Hiển thị khi ảnh chưa tải và trong danh sách quản lý" /></label>
        <label>Ảnh banner * <span class="faint">({{ PLACEMENTS.find(p => p.key === form.placement).size }})</span></label>
        <div class="upload">
          <img v-if="form.imageUrl" :src="form.imageUrl" alt="" />
          <label class="btn sm ghost">{{ uploading ? 'Đang tải...' : form.imageUrl ? 'Đổi ảnh' : 'Tải ảnh lên' }}
            <input type="file" accept="image/*" hidden @change="upload" />
          </label>
        </div>
        <label>Chương trình
          <select v-model="form.campaignId">
            <option :value="null">— Không —</option>
            <option v-for="c in campaigns" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
        <label>Liên kết khi nhấp <input v-model.trim="form.linkUrl" placeholder="/campaigns/sieu-sale hoặc /search?q=... (để trống: trang chương trình)" /></label>
        <div class="row">
          <label>Bắt đầu hiển thị<input v-model="form.startAt" type="datetime-local" /></label>
          <label>Kết thúc<input v-model="form.endAt" type="datetime-local" /></label>
        </div>
        <label class="check"><input v-model="form.active" type="checkbox" /> Bật hiển thị</label>
        <p v-if="formError" class="error">{{ formError }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn ghost" @click="form = null">Hủy</button>
        <button type="submit" form="banner-form" class="btn" :disabled="saving || uploading">{{ saving ? 'Đang lưu...' : 'Lưu' }}</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import { adminBanners, createBanner, updateBanner, toggleBanner, deleteBanner, reorderBanners, adminCampaigns } from '../../api/admin'
import { uploadFile } from '../../api/uploads'
import { formatDateTime, toLocalInput } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const PLACEMENTS = [
  { key: 'HOME_CAROUSEL', label: 'Banner chính trang chủ', hint: 'Trình chiếu tự động ở đầu trang chủ, tối đa 8 banner hiển thị cùng lúc theo thứ tự bên dưới.', size: 'khoảng 1200 × 360' },
  { key: 'HOME_SIDE', label: 'Banner phụ trang chủ', hint: 'Hai banner nhỏ bên phải banner chính (2 banner đầu tiên đang hiển thị).', size: 'khoảng 400 × 175' }
]
const STATUS = { SHOWING: 'Đang hiển thị', SCHEDULED: 'Đã lên lịch', EXPIRED: 'Hết hạn', HIDDEN: 'Đang ẩn' }
const banners = ref({})
const campaigns = ref([])
const error = ref('')
const form = ref(null)
const formError = ref('')
const saving = ref(false)
const uploading = ref(false)

async function load() {
  try {
    banners.value = (await adminBanners()).data
  } catch (e) {
    error.value = extractError(e)
  }
}

function open(b, placement) {
  formError.value = ''
  form.value = b ? { ...b, startAt: toLocalInput(b.startAt), endAt: toLocalInput(b.endAt) }
    : { title: '', imageUrl: null, linkUrl: '', placement, campaignId: null, startAt: '', endAt: '', active: true }
}

async function upload(e) {
  const file = e.target.files[0]
  e.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    form.value.imageUrl = (await uploadFile(file)).data.url
  } catch (err) {
    formError.value = extractError(err)
  } finally {
    uploading.value = false
  }
}

async function save() {
  saving.value = true
  formError.value = ''
  try {
    const body = { ...form.value, startAt: form.value.startAt || null, endAt: form.value.endAt || null, linkUrl: form.value.linkUrl || null }
    await (body.id ? updateBanner(body.id, body) : createBanner(body))
    form.value = null
    load()
  } catch (e) {
    formError.value = extractError(e)
  } finally {
    saving.value = false
  }
}

async function move(placement, i, d) {
  const ids = banners.value[placement].map(b => b.id)
  ;[ids[i], ids[i + d]] = [ids[i + d], ids[i]]
  await run(() => reorderBanners(placement, ids))
}

async function run(fn) {
  error.value = ''
  try {
    await fn()
    load()
  } catch (e) {
    error.value = extractError(e)
  }
}

onMounted(() => {
  load()
  adminCampaigns().then(({ data }) => { campaigns.value = data.filter(c => c.status !== 'ENDED') }).catch(() => {})
})
</script>

<style scoped>
.sc-card { margin-bottom: 16px; }
.sc-card-head { position: relative; padding-right: 160px; }
.add { position: absolute; right: 0; top: 0; }
.banner { display: flex; gap: 14px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--color-border); }
.banner.dim .thumb { opacity: 0.5; }
.order { display: flex; flex-direction: column; align-items: center; font-size: 12px; color: var(--color-text-faint); }
.order button { border: none; background: none; cursor: pointer; color: var(--color-text-muted); font-size: 11px; padding: 2px; }
.order button:disabled { opacity: 0.25; cursor: default; }
.thumb { width: 200px; height: 60px; object-fit: cover; border-radius: 4px; border: 1px solid var(--color-border); flex-shrink: 0; }
.thumb.HOME_SIDE { width: 137px; }
.body { flex: 1; min-width: 0; font-size: 13px; }
.title { display: flex; gap: 8px; align-items: center; font-size: 14px; }
.meta { color: var(--color-text-muted); margin-top: 3px; }
.meta a { color: #2673dd; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #f2f2f2; color: #666; }
.st.SHOWING { background: #eaf7ef; color: #1e9e4a; }
.st.SCHEDULED { background: #e8f1fd; color: #2673dd; }
.st.HIDDEN { background: #fff4e5; color: #b35c00; }
.stats { display: flex; gap: 16px; text-align: center; }
.stats div { display: flex; flex-direction: column; font-size: 11px; color: var(--color-text-faint); }
.stats b { font-size: 15px; color: var(--color-text); }
.actions { display: flex; flex-direction: column; align-items: flex-end; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
.link.danger { color: #d33; }
.form { display: flex; flex-direction: column; gap: 10px; font-size: 13px; }
.form > label { display: flex; flex-direction: column; gap: 4px; color: var(--color-text-muted); }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.row label { display: flex; flex-direction: column; gap: 4px; color: var(--color-text-muted); }
.upload { display: flex; align-items: center; gap: 10px; }
.upload img { height: 70px; max-width: 280px; object-fit: cover; border-radius: 4px; border: 1px solid var(--color-border); }
.check { flex-direction: row !important; align-items: center; }
.check input { width: auto; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 900px) { .banner { flex-wrap: wrap; } .stats { order: 5; } .sc-card-head { padding-right: 0; } .add { position: static; } }
</style>
