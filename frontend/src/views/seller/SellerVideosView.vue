<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">{{ editing ? 'Sửa Video' : 'Đăng Video Mới' }}</h2>
        <span class="sc-card-sub">Video ngắn (MP4/WEBM, tối đa 30MB, nên quay dọc) gắn sản phẩm của shop: người mua xem trong mục Shoppi Video và bấm mua ngay.</span>
      </div>

      <div class="form">
        <div v-if="!editing">
          <div class="label">Video</div>
          <MediaUploader v-model="files" kind="video" :max="1" add-label="Tải video lên" />
        </div>
        <label>
          <span class="label">Mô tả ({{ caption.length }}/300)</span>
          <textarea v-model="caption" rows="3" maxlength="300" placeholder="Giới thiệu ngắn về sản phẩm trong video"></textarea>
        </label>
        <div>
          <div class="label">Sản phẩm gắn vào video ({{ picked.length }}/{{ MAX_PRODUCTS }})</div>
          <input v-model.trim="q" type="search" placeholder="Tìm sản phẩm của shop" class="search" />
          <div class="picker">
            <label v-for="p in shown" :key="p.id" class="pick" :class="{ on: picked.includes(p.id) }">
              <input type="checkbox" :checked="picked.includes(p.id)" :disabled="!picked.includes(p.id) && picked.length >= MAX_PRODUCTS" @change="toggle(p.id)" />
              <img :src="p.thumbnailUrl || placeholder" alt="" />
              <span>{{ p.name }}</span>
              <b>{{ formatVnd(p.price) }}</b>
            </label>
            <p v-if="!shown.length" class="faint">Không có sản phẩm đang bán nào khớp.</p>
          </div>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="actions">
          <button v-if="editing" type="button" class="btn ghost" @click="reset">Hủy sửa</button>
          <button type="button" class="btn" :disabled="busy || !picked.length || (!editing && !files.length)" @click="save">
            {{ busy ? 'Đang lưu...' : editing ? 'Lưu thay đổi' : 'Đăng video' }}
          </button>
        </div>
      </div>
    </section>

    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Video Của Shop</h2>
        <router-link :to="{ name: 'videos', query: shopId ? { shop: shopId } : {} }" target="_blank" class="sc-card-sub">Xem như người mua ›</router-link>
      </div>
      <div v-if="!list.length" class="sc-empty">{{ loading ? 'Đang tải...' : 'Shop chưa có video nào.' }}</div>
      <div class="grid">
        <article v-for="v in list" :key="v.id" class="video" :class="{ hidden: v.status === 'HIDDEN' }">
          <video :src="v.videoUrl" controls preload="metadata"></video>
          <div class="body">
            <p v-if="v.status === 'HIDDEN'" class="error">Đã bị Shoppi ẩn: {{ v.hiddenReason }}</p>
            <p class="caption">{{ v.caption || '(không có mô tả)' }}</p>
            <div class="faint">▶ {{ v.viewCount }} lượt xem · ♥ {{ v.likeCount }} · {{ formatDateTime(v.createdAt) }}</div>
            <div class="tags"><span v-for="p in v.products" :key="p.id" :class="{ off: !p.active }">{{ p.name }}</span></div>
            <div class="row">
              <button type="button" class="btn sm ghost" @click="edit(v)">Sửa</button>
              <button type="button" class="btn sm ghost danger" :disabled="busy" @click="remove(v)">Xóa</button>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import MediaUploader from '../../components/MediaUploader.vue'
import { myVideos, createVideo, updateVideo, deleteVideo } from '../../api/videos'
import { myProducts } from '../../api/products'
import { extractError } from '../../utils/apiError'
import { formatVnd, formatDateTime } from '../../utils/format'

const MAX_PRODUCTS = 6
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const list = ref([])
const products = ref([])
const files = ref([])
const caption = ref('')
const picked = ref([])
const q = ref('')
const editing = ref(null)
const busy = ref(false)
const loading = ref(true)
const error = ref('')

const shopId = computed(() => products.value[0]?.shopId)
const shown = computed(() => {
  const term = q.value.toLowerCase()
  return products.value.filter(p => p.active && (!term || p.name.toLowerCase().includes(term))).slice(0, 60)
})

const toggle = (id) => {
  picked.value = picked.value.includes(id) ? picked.value.filter(x => x !== id) : [...picked.value, id]
}

function reset() {
  editing.value = null
  files.value = []
  caption.value = ''
  picked.value = []
  error.value = ''
}

function edit(v) {
  editing.value = v.id
  caption.value = v.caption || ''
  picked.value = [...v.productIds]
  error.value = ''
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function save() {
  busy.value = true
  error.value = ''
  try {
    if (editing.value) {
      const { data } = await updateVideo(editing.value, { caption: caption.value, productIds: picked.value })
      list.value = list.value.map(v => (v.id === data.id ? data : v))
    } else {
      const { data } = await createVideo({ videoUrl: files.value[0], caption: caption.value, productIds: picked.value })
      list.value = [data, ...list.value]
    }
    reset()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function remove(v) {
  if (!confirm('Xóa video này? Lượt xem và lượt thích cũng bị xóa.')) return
  busy.value = true
  try {
    await deleteVideo(v.id)
    list.value = list.value.filter(x => x.id !== v.id)
    if (editing.value === v.id) reset()
  } catch (e) {
    alert(extractError(e))
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    const [videos, mine] = await Promise.all([myVideos(), myProducts()])
    list.value = videos.data
    products.value = mine.data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.form { display: flex; flex-direction: column; gap: 14px; max-width: 720px; }
.label { font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; display: block; }
textarea { width: 100%; margin: 0; }
.search { margin: 0 0 8px; max-width: 320px; }
.picker { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 6px; max-height: 300px; overflow-y: auto; }
.pick { display: grid; grid-template-columns: auto 36px 1fr; grid-template-rows: auto auto; column-gap: 8px; align-items: center; border: 1px solid rgba(0, 0, 0, 0.09);
  border-radius: 4px; padding: 6px; font-size: 13px; cursor: pointer; }
.pick.on { border-color: var(--color-primary); background: #fff6f3; }
.pick input { grid-row: span 2; margin: 0; width: auto; }
.pick img { grid-row: span 2; width: 36px; height: 36px; object-fit: cover; }
.pick span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pick b { color: var(--color-primary); font-weight: 500; }
.actions { display: flex; gap: 8px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.video { border: 1px solid rgba(0, 0, 0, 0.09); border-radius: 6px; overflow: hidden; display: flex; flex-direction: column; }
.video.hidden { opacity: 0.7; }
.video video { width: 100%; aspect-ratio: 9 / 16; max-height: 360px; background: black; object-fit: contain; }
.body { padding: 8px 10px; display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
.caption { margin: 0; white-space: pre-line; }
.tags { display: flex; flex-wrap: wrap; gap: 4px; }
.tags span { background: #f5f5f5; border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.tags span.off { text-decoration: line-through; color: var(--color-text-faint); }
.row { display: flex; gap: 6px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.danger { color: #d33; }
.error { color: #d33; font-size: 13px; margin: 0; }
</style>
