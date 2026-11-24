<template>
  <section v-if="items.length" class="shopee-section">
    <div class="section-head">
      <h3 class="section-title">TÌM KIẾM HÀNG ĐẦU</h3>
      <span class="text-faint small">Theo lượt tìm 7 ngày qua</span>
    </div>
    <div class="grid">
      <router-link v-for="(k, i) in items" :key="k.keyword" :to="{ name: 'search', query: { q: k.keyword } }" class="card">
        <span class="img">
          <img :src="k.imageUrl || placeholder" :alt="k.keyword" loading="lazy" />
          <span class="top">TOP {{ i + 1 }}</span>
        </span>
        <span class="kw">{{ k.keyword }}</span>
        <small>{{ formatCount(k.searches) }} lượt tìm tuần này</small>
      </router-link>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { topSearches } from '../../api/products'
import { formatCount } from '../../utils/format'

const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="%23f0f0f0"/></svg>'
const items = ref([])

onMounted(() => {
  topSearches(6).then(({ data }) => { items.value = data }).catch(() => {})
})
</script>

<style scoped>
.section-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.small { font-size: 12px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; }
.card { display: flex; flex-direction: column; gap: 4px; color: var(--color-text); }
.img { position: relative; aspect-ratio: 1; background: #f5f5f5; overflow: hidden; border-radius: 2px; }
.img img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.2s; }
.card:hover .img img { transform: scale(1.04); }
.top { position: absolute; top: 0; left: 0; background: var(--color-primary); color: white; font-size: 11px; font-weight: 700; padding: 2px 8px;
  border-bottom-right-radius: 6px; }
.kw { font-size: 15px; font-weight: 500; text-transform: capitalize; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
small { color: var(--color-text-faint); font-size: 12px; }
</style>
