<template>
  <div>
    <AppHeader />
    <div class="container page">
      <nav class="tabs">
        <router-link :to="{ name: 'my-likes' }">♥ Sản phẩm yêu thích</router-link>
        <router-link :to="{ name: 'price-watches' }" class="active">🔔 Theo dõi giá</router-link>
        <router-link :to="{ name: 'my-follows' }">Shop đang theo dõi</router-link>
      </nav>

      <SkeletonGrid v-if="loading" :count="6" />
      <div v-else-if="!items.length" class="shopee-section empty">
        Bạn chưa theo dõi giá sản phẩm nào. Bấm 🔔 <strong>Báo khi giảm giá</strong> ở trang sản phẩm, Shoppi sẽ thông báo ngay khi giá giảm.
        <div><router-link to="/search?q=" class="btn sm">Khám phá sản phẩm</router-link></div>
      </div>
      <template v-else>
        <p class="summary text-muted">
          Đang theo dõi {{ items.length }} sản phẩm<template v-if="dropped"> · <strong class="text-primary">{{ dropped }} sản phẩm đã giảm giá</strong> so với lúc bạn theo dõi</template>
        </p>
        <div class="grid">
          <div v-for="w in items" :key="w.productId" class="cell" :class="{ gone: !w.onSale }">
            <ProductCard :product="w.product" />
            <span v-if="!w.onSale" class="tag dark">Tạm hết hàng</span>
            <span v-else-if="w.reachedTarget" class="tag">Đã đạt giá mong muốn</span>
            <span v-else-if="Number(w.dropPercent) >= 1" class="tag">↓ {{ Math.round(w.dropPercent) }}% từ khi theo dõi</span>
            <div class="info">
              <span>Lúc theo dõi: {{ formatVnd(w.watchedPrice) }}</span>
              <span v-if="w.targetPrice">Báo khi ≤ <b>{{ formatVnd(w.targetPrice) }}</b></span>
            </div>
            <button type="button" class="remove" @click="remove(w)">Bỏ theo dõi</button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import ProductCard from '../../components/ProductCard.vue'
import SkeletonGrid from '../../components/SkeletonGrid.vue'
import { myPriceWatches, unwatchPrice } from '../../api/priceWatches'
import { formatVnd } from '../../utils/format'

const items = ref([])
const loading = ref(true)
const dropped = computed(() => items.value.filter(w => w.onSale && Number(w.dropPercent) >= 1).length)

onMounted(async () => {
  try {
    items.value = (await myPriceWatches()).data
  } finally {
    loading.value = false
  }
})

async function remove(w) {
  await unwatchPrice(w.productId)
  items.value = items.value.filter(x => x !== w)
}
</script>

<style scoped>
.page { padding-top: 20px; padding-bottom: 40px; }
.tabs { display: flex; background: white; box-shadow: var(--shadow-card); margin-bottom: 12px; }
.tabs a { flex: 1; text-align: center; padding: 14px 6px; color: var(--color-text); border-bottom: 2px solid transparent; }
.tabs a.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.empty { text-align: center; padding: 48px 16px; color: var(--color-text-muted); }
.empty .btn { margin-top: 14px; }
.summary { margin: 0 0 10px; font-size: 14px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(188px, 1fr)); gap: 10px; }
@media (max-width: 560px) { .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; } }
.cell { position: relative; display: flex; flex-direction: column; }
.cell.gone :deep(a) { opacity: 0.5; }
.tag { position: absolute; top: 8px; left: 8px; z-index: 2; background: var(--color-primary); color: white; font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.tag.dark { background: rgba(0, 0, 0, 0.65); }
.info { background: white; border: 1px solid var(--color-border); border-top: none; padding: 6px 8px; font-size: 12px; color: var(--color-text-muted); display: flex; flex-direction: column; gap: 2px; }
.info b { color: var(--color-primary); }
.remove { background: white; border: 1px solid var(--color-border); border-top: none; color: var(--color-primary); padding: 6px; cursor: pointer; font-size: 13px; }
.remove:hover { background: #fff6f3; }
</style>
