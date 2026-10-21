<template>
  <section class="content-block">
    <h2 class="block-title">ĐÁNH GIÁ SẢN PHẨM</h2>

    <div class="summary">
      <div class="avg">
        <div class="avg-num"><span>{{ summary.average.toFixed(1) }}</span> trên 5</div>
        <StarRating :value="summary.average" :size="20" />
      </div>
      <div class="filters">
        <button v-for="f in filterButtons" :key="f.value" type="button"
                :class="{ active: filter === f.value }" @click="setFilter(f.value)">
          {{ f.label }}
        </button>
      </div>
    </div>

    <div v-if="loading && !page.items.length" class="empty">Đang tải...</div>
    <div v-else-if="!page.items.length" class="empty">
      <svg width="64" height="64" viewBox="0 0 24 24" fill="none"><path d="M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6 6.6 19.5l1.2-6L3.3 9.3l6.1-.7z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" /></svg>
      <p>Chưa có đánh giá</p>
    </div>

    <ul v-else class="list">
      <li v-for="r in page.items" :key="r.id" class="review">
        <span class="avatar">{{ r.buyerName.charAt(0).toUpperCase() }}</span>
        <div class="body">
          <div class="name">{{ r.buyerName }}</div>
          <StarRating :value="r.rating" :size="12" />
          <div class="meta">{{ formatDateTime(r.createdAt) }}<template v-if="r.variantName"> | Phân loại hàng: {{ r.variantName }}</template></div>
          <p v-if="r.comment" class="comment">{{ r.comment }}</p>
          <video v-if="r.videoUrl" :src="r.videoUrl" class="review-video" controls preload="metadata"></video>
          <div v-if="r.imageUrls.length" class="photos">
            <button v-for="(url, i) in r.imageUrls" :key="url" type="button" class="photo" @click="openLightbox(r.imageUrls, i)">
              <img :src="url" alt="Ảnh đánh giá" loading="lazy" />
            </button>
          </div>
          <div v-if="r.sellerReply" class="reply">
            <strong>Phản hồi của Người Bán</strong>
            <p>{{ r.sellerReply }}</p>
          </div>
        </div>
      </li>
    </ul>

    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="loadPage" />

    <!-- photo lightbox -->
    <div v-if="lightbox" class="lightbox" role="dialog" aria-label="Xem ảnh" @click.self="lightbox = null" @keydown.esc="lightbox = null">
      <button class="lb-close" type="button" aria-label="Đóng" @click="lightbox = null">✕</button>
      <button v-if="lightbox.urls.length > 1" class="lb-nav prev" type="button" aria-label="Ảnh trước" @click="stepLightbox(-1)">‹</button>
      <img :src="lightbox.urls[lightbox.index]" alt="Ảnh đánh giá" />
      <button v-if="lightbox.urls.length > 1" class="lb-nav next" type="button" aria-label="Ảnh sau" @click="stepLightbox(1)">›</button>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import StarRating from '../StarRating.vue'
import PaginationBar from '../PaginationBar.vue'
import { productReviews, reviewSummary } from '../../api/reviews'
import { formatDateTime } from '../../utils/format'

const props = defineProps({ productId: { type: Number, required: true } })

const summary = ref({ average: 0, total: 0, countByStars: {}, withMedia: 0 })
const filter = ref('all')
const page = ref({ items: [], page: 0, totalPages: 0 })
const loading = ref(false)
const lightbox = ref(null)

const filterButtons = computed(() => [
  { value: 'all', label: 'Tất Cả' },
  ...[5, 4, 3, 2, 1].map(s => ({ value: String(s), label: `${s} Sao (${summary.value.countByStars[s] || 0})` })),
  { value: 'media', label: `Có Hình Ảnh / Video (${summary.value.withMedia})` }
])

async function loadPage(p = 0) {
  loading.value = true
  try {
    const { data } = await productReviews(props.productId, { filter: filter.value, page: p, size: 6 })
    page.value = data
  } finally {
    loading.value = false
  }
}

function setFilter(value) {
  filter.value = value
  loadPage(0)
}

async function loadAll() {
  filter.value = 'all'
  const [{ data: s }] = await Promise.all([reviewSummary(props.productId), loadPage(0)])
  summary.value = s
}

function openLightbox(urls, index) { lightbox.value = { urls, index } }
function stepLightbox(d) {
  const lb = lightbox.value
  lb.index = (lb.index + d + lb.urls.length) % lb.urls.length
}
function onKey(e) {
  if (!lightbox.value) return
  if (e.key === 'Escape') lightbox.value = null
  if (e.key === 'ArrowLeft') stepLightbox(-1)
  if (e.key === 'ArrowRight') stepLightbox(1)
}

onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
watch(() => props.productId, loadAll, { immediate: true })
</script>

<style scoped>
.summary { display: flex; gap: 32px; align-items: center; background: #fffbf8; border: 1px solid #f9ede5; padding: 24px 30px; margin-bottom: 16px; flex-wrap: wrap; }
.avg { text-align: center; color: var(--color-primary); }
.avg-num { font-size: 16px; margin-bottom: 6px; }
.avg-num span { font-size: 30px; }
.filters { display: flex; flex-wrap: wrap; gap: 8px; flex: 1; }
.filters button { min-width: 100px; padding: 6px 12px; background: white; border: 1px solid rgba(0, 0, 0, 0.09); border-radius: 2px; cursor: pointer; font-size: 14px; }
.filters button.active { border-color: var(--color-primary); color: var(--color-primary); }

.list { list-style: none; margin: 0; padding: 0; }
.review { display: flex; gap: 12px; padding: 16px 0 16px 8px; border-bottom: 1px solid var(--color-border); }
.avatar { width: 40px; height: 40px; border-radius: 50%; background: #eee; display: flex; align-items: center; justify-content: center; font-weight: 600; color: #888; flex-shrink: 0; }
.body { flex: 1; min-width: 0; }
.name { font-size: 13px; margin-bottom: 4px; }
.meta { font-size: 12px; color: var(--color-text-faint); margin: 6px 0 10px; }
.comment { margin: 0 0 10px; font-size: 14px; line-height: 1.6; white-space: pre-line; }
.photos { display: flex; gap: 8px; flex-wrap: wrap; }
.review-video { width: 220px; max-width: 100%; aspect-ratio: 16 / 9; background: #000; border-radius: 2px; margin-bottom: 8px; display: block; }
.photo { width: 72px; height: 72px; padding: 0; border: none; cursor: zoom-in; background: #f2f2f2; }
.photo img { width: 100%; height: 100%; object-fit: cover; }
.reply { background: #f5f5f5; padding: 12px; margin-top: 12px; font-size: 13px; }
.reply p { margin: 6px 0 0; }
.empty { text-align: center; padding: 40px; color: var(--color-text-faint); }
.empty svg { color: #ddd; }

.lightbox { position: fixed; inset: 0; z-index: 1000; background: rgba(0, 0, 0, 0.85); display: flex; align-items: center; justify-content: center; }
.lightbox img { max-width: 90vw; max-height: 85vh; object-fit: contain; }
.lb-close { position: absolute; top: 16px; right: 20px; background: none; border: none; color: white; font-size: 26px; cursor: pointer; }
.lb-nav { position: absolute; top: 50%; transform: translateY(-50%); background: rgba(255, 255, 255, 0.15); border: none; color: white; font-size: 36px; width: 48px; height: 64px; cursor: pointer; }
.lb-nav.prev { left: 16px; }
.lb-nav.next { right: 16px; }
</style>
