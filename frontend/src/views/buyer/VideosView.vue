<template>
  <div class="videos-page">
    <AppHeader />
    <div class="bar">
      <button v-for="t in TABS" :key="t.key" type="button" :class="{ active: sort === t.key }" @click="setSort(t.key)">{{ t.label }}</button>
      <router-link v-if="filtered" :to="{ name: 'videos' }" class="clear">✕ Xem tất cả video</router-link>
    </div>

    <div ref="feedEl" class="feed" :style="feedTop ? { height: `calc(100dvh - ${feedTop}px)` } : null">
      <div v-if="!items.length" class="empty">{{ loading ? 'Đang tải...' : error || 'Chưa có video nào.' }}</div>

      <section v-for="(v, i) in items" :key="v.id" class="slide" :data-index="i">
        <div class="frame">
          <video :ref="el => setVideo(v.id, el)" :src="v.videoUrl" loop playsinline :muted="muted" preload="metadata" @click="muted = !muted"></video>
          <button type="button" class="sound" @click="muted = !muted">{{ muted ? '🔇 Chạm để bật tiếng' : '🔊' }}</button>

          <div class="side">
            <button type="button" class="like" :class="{ on: v.liked }" :disabled="busyId === v.id" @click="toggleLike(v)">
              <span>{{ v.liked ? '♥' : '♡' }}</span><small>{{ formatCount(v.likeCount) }}</small>
            </button>
            <div class="views"><span>▶</span><small>{{ formatCount(v.viewCount) }}</small></div>
          </div>

          <div class="info">
            <router-link v-if="v.shop" :to="{ name: 'shop', params: { id: v.shop.id } }" class="shop">
              <img v-if="v.shop.logoUrl" :src="v.shop.logoUrl" alt="" />
              <span v-if="v.shop.mall" class="mall">Mall</span>
              {{ v.shop.name }}
            </router-link>
            <p v-if="v.caption" class="caption">{{ v.caption }}</p>
            <div class="products">
              <router-link v-for="p in v.products" :key="p.id" :to="{ name: 'product-detail', params: { id: p.id } }" class="product">
                <img :src="p.thumbnailUrl || placeholder" alt="" />
                <span class="pname">{{ p.name }}</span>
                <b>{{ formatVnd(p.price) }}</b>
                <span class="buy">Mua ngay</span>
              </router-link>
            </div>
          </div>
        </div>
      </section>
      <div v-if="loading && items.length" class="empty">Đang tải thêm...</div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import { videoFeed, countVideoView, likeVideo, unlikeVideo } from '../../api/videos'
import { useAuthStore } from '../../stores/auth'
import { extractError } from '../../utils/apiError'
import { formatVnd, formatCount } from '../../utils/format'

const TABS = [{ key: 'hot', label: 'Nổi bật' }, { key: 'new', label: 'Mới nhất' }]
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const feedEl = ref(null)
const items = ref([])
const sort = ref('hot')
const page = ref({ page: -1, totalPages: 1 })
const loading = ref(false)
const error = ref('')
const muted = ref(true)        // browsers only autoplay muted videos
const busyId = ref(null)
const feedTop = ref(0)         // the feed fills the screen below the header, whatever its height
const videos = new Map()       // id -> <video>
const counted = new Set()      // views already counted on this visit
let observer = null

const filtered = computed(() => !!(route.query.product || route.query.shop))

function setVideo(id, el) {
  if (el) {
    videos.set(id, el)
    observer?.observe(el)
  } else {
    videos.delete(id)
  }
}

async function load(reset = false) {
  if (loading.value || (!reset && page.value.page + 1 >= page.value.totalPages)) return
  loading.value = true
  error.value = ''
  try {
    const { data } = await videoFeed({ sort: sort.value, productId: route.query.product || undefined, shopId: route.query.shop || undefined,
      page: reset ? 0 : page.value.page + 1 })
    items.value = reset ? data.items : [...items.value, ...data.items.filter(v => !items.value.some(x => x.id === v.id))]
    page.value = { page: data.page, totalPages: data.totalPages }
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

function setSort(key) {
  if (sort.value === key) return
  sort.value = key
  reload()
}

async function reload() {
  items.value = []
  page.value = { page: -1, totalPages: 1 }
  feedEl.value?.scrollTo({ top: 0 })
  await load(true)
}

// the video in view plays, the others pause; near the end, the next page loads
function onVisible(entries) {
  for (const e of entries) {
    const el = e.target
    if (e.isIntersecting) {
      el.play().catch(() => {})
      const id = [...videos.entries()].find(([, v]) => v === el)?.[0]
      if (id && !counted.has(id)) {
        counted.add(id)
        countVideoView(id).catch(() => {})
        const v = items.value.find(x => x.id === id)
        if (v) v.viewCount++
      }
      const index = items.value.findIndex(x => x.id === id)
      if (index >= items.value.length - 2) load()
    } else {
      el.pause()
    }
  }
}

async function toggleLike(v) {
  if (!auth.isBuyer) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  busyId.value = v.id
  try {
    const { data } = await (v.liked ? unlikeVideo(v.id) : likeVideo(v.id))
    v.liked = data.liked
    v.likeCount = data.likeCount
  } catch (e) {
    alert(extractError(e))
  } finally {
    busyId.value = null
  }
}

watch(() => [route.query.product, route.query.shop], () => reload())

onMounted(async () => {
  feedTop.value = Math.round(feedEl.value.getBoundingClientRect().top + window.scrollY)
  observer = new IntersectionObserver(onVisible, { root: feedEl.value, threshold: 0.6 })
  await load(true)
  await nextTick()
  videos.forEach(el => observer.observe(el))
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<style scoped>
.videos-page { background: #111; min-height: 100vh; }
.bar { display: flex; justify-content: center; gap: 20px; padding: 8px; background: #111; }
.bar button { background: none; border: none; color: #aaa; font-size: 15px; cursor: pointer; padding: 4px 2px; border-bottom: 2px solid transparent; }
.bar button.active { color: white; border-bottom-color: var(--color-primary); }
.clear { color: #ffb199; font-size: 13px; align-self: center; }
.feed { height: calc(100dvh - 170px); overflow-y: auto; scroll-snap-type: y mandatory; }
.empty { color: #bbb; text-align: center; padding: 48px 16px; }
.slide { height: 100%; scroll-snap-align: start; display: flex; justify-content: center; padding: 8px 0; box-sizing: border-box; }
.frame { position: relative; height: 100%; aspect-ratio: 9 / 16; max-width: 100%; background: black; border-radius: 8px; overflow: hidden; }
video { width: 100%; height: 100%; object-fit: cover; display: block; cursor: pointer; }
.sound { position: absolute; top: 10px; left: 10px; background: rgba(0, 0, 0, 0.45); color: white; border: none; border-radius: 14px; padding: 4px 10px; font-size: 12px; cursor: pointer; }
.side { position: absolute; right: 8px; bottom: 170px; display: flex; flex-direction: column; gap: 14px; align-items: center; color: white; }
.like, .views { display: flex; flex-direction: column; align-items: center; background: none; border: none; color: white; cursor: pointer; text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6); }
.like span, .views span { font-size: 28px; line-height: 1; }
.like.on span { color: #ff4d6d; }
.side small { font-size: 12px; margin-top: 2px; }
.info { position: absolute; left: 0; right: 0; bottom: 0; padding: 40px 12px 12px; background: linear-gradient(transparent, rgba(0, 0, 0, 0.75)); color: white; }
.shop { display: flex; align-items: center; gap: 6px; color: white; font-weight: 600; }
.shop img { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
.mall { background: #d0011b; font-size: 10px; padding: 0 4px; border-radius: 2px; }
.caption { margin: 6px 0; font-size: 14px; white-space: pre-line; max-height: 4.5em; overflow: hidden; }
.products { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 2px; }
.product { flex: 0 0 auto; display: grid; grid-template-columns: 40px 1fr; grid-template-rows: auto auto; column-gap: 8px; align-items: center;
  background: rgba(255, 255, 255, 0.95); color: var(--color-text); border-radius: 6px; padding: 6px 8px; width: 210px; }
.product img { grid-row: span 2; width: 40px; height: 40px; object-fit: cover; border-radius: 4px; }
.pname { font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.product b { color: var(--color-primary); font-size: 13px; }
.buy { grid-column: 2; justify-self: end; margin-top: -18px; background: var(--color-primary); color: white; font-size: 11px; padding: 2px 8px; border-radius: 10px; }
@media (max-width: 560px) {
  .feed { height: calc(100dvh - 150px); }
  .slide { padding: 0; }
  .frame { border-radius: 0; width: 100%; aspect-ratio: auto; }
}
</style>
