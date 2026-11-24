<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Kiểm Duyệt Shoppi Video</h2>
      <span class="sc-card-sub">Video các shop đăng. Ẩn video vi phạm (shop nhận thông báo kèm lý do); mô tả đã được lọc từ khóa cấm và thông tin liên hệ ngoài sàn.</span>
    </div>
    <nav class="sc-tabs">
      <a v-for="t in TABS" :key="t.key" href="#" :class="{ active: status === t.key }" @click.prevent="setStatus(t.key)">{{ t.label }}</a>
    </nav>
    <div v-if="!page.items.length" class="sc-empty">{{ loading ? 'Đang tải...' : 'Không có video nào' }}</div>
    <div class="grid">
      <article v-for="v in page.items" :key="v.id" class="video">
        <video :src="v.videoUrl" controls preload="metadata"></video>
        <div class="body">
          <router-link v-if="v.shop" :to="`/shop/${v.shop.id}`" target="_blank"><b>{{ v.shop.name }}</b></router-link>
          <p class="caption">{{ v.caption || '(không có mô tả)' }}</p>
          <div class="faint">▶ {{ v.viewCount }} · ♥ {{ v.likeCount }} · {{ formatDateTime(v.createdAt) }}</div>
          <div class="faint">Sản phẩm: {{ v.products.map(p => p.name).join(', ') }}</div>
          <p v-if="v.hiddenReason" class="reason">Lý do ẩn: {{ v.hiddenReason }}</p>
          <button v-if="v.status === 'ACTIVE'" type="button" class="btn sm ghost warn" :disabled="busyId === v.id" @click="hide(v)">Ẩn video</button>
          <button v-else type="button" class="btn sm" :disabled="busyId === v.id" @click="restore(v)">Hiện lại</button>
        </div>
      </article>
    </div>
    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { adminVideos, hideVideo, restoreVideo } from '../../api/videos'
import { extractError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/format'

const TABS = [{ key: 'ACTIVE', label: 'Đang hiển thị' }, { key: 'HIDDEN', label: 'Đã ẩn' }]

const status = ref('ACTIVE')
const page = ref({ items: [], page: 0, totalPages: 0 })
const loading = ref(false)
const busyId = ref(null)
const error = ref('')

async function load(p = 0) {
  loading.value = true
  try {
    page.value = (await adminVideos({ status: status.value, page: p })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

function setStatus(key) {
  status.value = key
  load()
}

async function run(v, call) {
  busyId.value = v.id
  error.value = ''
  try {
    await call()
    page.value.items = page.value.items.filter(x => x.id !== v.id)
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busyId.value = null
  }
}

function hide(v) {
  const reason = prompt('Lý do ẩn video (gửi cho shop):')
  if (reason) run(v, () => hideVideo(v.id, reason))
}
const restore = (v) => run(v, () => restoreVideo(v.id))

onMounted(load)
</script>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.video { border: 1px solid rgba(0, 0, 0, 0.09); border-radius: 6px; overflow: hidden; display: flex; flex-direction: column; }
.video video { width: 100%; aspect-ratio: 9 / 16; max-height: 360px; background: black; object-fit: contain; }
.body { padding: 8px 10px; display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.caption { margin: 0; white-space: pre-line; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.reason { color: #8a5a00; margin: 0; }
.error { color: #d33; font-size: 13px; }
</style>
