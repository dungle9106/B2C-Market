<template>
  <!-- banners from the marketing team; the built-in hero stays as the fallback when none is showing -->
  <div v-if="loaded && (main.length || side.length)" class="banners" :class="{ 'no-side': !side.length }">
    <div class="carousel" @mouseenter="paused = true" @mouseleave="paused = false">
      <div class="track" :style="{ transform: `translateX(-${index * 100}%)` }">
        <a v-for="b in main" :key="b.id" :href="b.linkUrl || '#'" class="slide" @click.prevent="open(b)">
          <img :src="b.imageUrl" :alt="b.title" />
        </a>
      </div>
      <template v-if="main.length > 1">
        <button type="button" class="nav prev" aria-label="Banner trước" @click="go(index - 1)">‹</button>
        <button type="button" class="nav next" aria-label="Banner sau" @click="go(index + 1)">›</button>
        <div class="dots">
          <button v-for="(b, i) in main" :key="b.id" type="button" :class="{ on: i === index }" :aria-label="b.title" @click="go(i)"></button>
        </div>
      </template>
    </div>
    <div v-if="side.length" class="side">
      <a v-for="b in side" :key="b.id" :href="b.linkUrl || '#'" @click.prevent="open(b)"><img :src="b.imageUrl" :alt="b.title" /></a>
    </div>
  </div>
  <slot v-else-if="loaded" />
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { homeBanners, clickBanner } from '../api/marketing'

const router = useRouter()
const main = ref([])
const side = ref([])
const loaded = ref(false)
const index = ref(0)
const paused = ref(false)
let timer = null

const go = (i) => { index.value = (i + main.value.length) % main.value.length }

function open(b) {
  clickBanner(b.id).catch(() => {}) // counting must never block the navigation
  if (b.linkUrl) router.push(b.linkUrl)
}

onMounted(async () => {
  try {
    const { data } = await homeBanners()
    main.value = data.HOME_CAROUSEL || []
    side.value = data.HOME_SIDE || []
  } catch { /* fall back to the default hero */ }
  loaded.value = true
  timer = setInterval(() => { if (!paused.value && main.value.length > 1) go(index.value + 1) }, 5000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.banners { display: grid; grid-template-columns: 2fr 1fr; gap: 6px; margin: 16px 0 12px; }
.banners.no-side { grid-template-columns: 1fr; }
.carousel { position: relative; overflow: hidden; border-radius: var(--radius-md); aspect-ratio: 10 / 3; background: #f2f2f2; }
.banners:not(.no-side) .carousel { aspect-ratio: auto; height: 235px; }
.track { display: flex; height: 100%; transition: transform 0.5s ease; }
.slide { flex: 0 0 100%; height: 100%; }
.slide img, .side img { width: 100%; height: 100%; object-fit: cover; display: block; }
.nav { position: absolute; top: 50%; transform: translateY(-50%); width: 34px; height: 56px; border: none; background: rgba(0, 0, 0, 0.18);
  color: white; font-size: 26px; cursor: pointer; opacity: 0; transition: opacity 0.2s; }
.carousel:hover .nav { opacity: 1; }
.prev { left: 0; border-radius: 0 4px 4px 0; }
.next { right: 0; border-radius: 4px 0 0 4px; }
.dots { position: absolute; bottom: 10px; left: 0; right: 0; display: flex; justify-content: center; gap: 6px; }
.dots button { width: 9px; height: 9px; border-radius: 50%; border: none; background: rgba(255, 255, 255, 0.6); cursor: pointer; padding: 0; }
.dots button.on { background: var(--color-primary); }
.side { display: grid; grid-template-rows: 1fr 1fr; gap: 6px; height: 235px; }
.side a { overflow: hidden; border-radius: var(--radius-md); background: #f2f2f2; }
@media (max-width: 760px) {
  .banners { grid-template-columns: 1fr; }
  .banners:not(.no-side) .carousel { height: auto; aspect-ratio: 10 / 3; }
  .side { grid-template-rows: none; grid-template-columns: 1fr 1fr; height: auto; }
  .side a { aspect-ratio: 16 / 7; }
}
</style>
