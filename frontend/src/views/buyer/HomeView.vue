<template>
  <div>
    <AppHeader />
    <div class="container home">
      <HomeBanners>
        <div class="hero-banner">
          <div>
            <h2>Siêu Sale Cuối Tháng</h2>
            <p>Giá sốc mỗi ngày - Freeship đơn từ 0đ - Hoàn xu 10%</p>
          </div>
          <router-link :to="{ name: 'search', query: { onSale: 'true' } }" class="btn secondary hero-cta">Săn deal ngay</router-link>
        </div>
      </HomeBanners>

      <!-- running / coming promotion programmes -->
      <div v-if="campaigns.length" class="campaigns">
        <router-link v-for="c in campaigns" :key="c.id" :to="`/campaigns/${c.slug}`" class="campaign" :style="{ '--theme': c.themeColor }">
          <b>{{ c.name }}</b>
          <span>{{ c.status === 'RUNNING' ? 'Đang diễn ra' : 'Từ ' + new Date(c.startAt).toLocaleDateString('vi-VN') }}</span>
        </router-link>
      </div>

      <section class="shopee-section">
        <h3 class="section-title">DANH MỤC</h3>
        <CategoryStrip />
      </section>

      <FlashSaleSection />

      <section v-if="topProducts.length" class="shopee-section">
        <div class="section-head">
          <h3 class="section-title">SẢN PHẨM BÁN CHẠY</h3>
          <router-link :to="{ name: 'search', query: { sort: 'bestselling' } }" class="see-all">Xem tất cả ›</router-link>
        </div>
        <div class="grid-products">
          <ProductCard v-for="p in topProducts" :key="'top-' + p.id" :product="p" />
        </div>
      </section>

      <!-- personalised for logged-in buyers (view history), newest products otherwise -->
      <section class="daily">
        <h3 class="daily-title">{{ auth.isBuyer && personalised ? 'GỢI Ý HÔM NAY CHO BẠN' : 'SẢN PHẨM MỚI' }}</h3>
        <div v-if="loading && !feed.length" class="text-muted loading">Đang tải...</div>
        <div v-else-if="!feed.length" class="text-muted loading">Chưa có sản phẩm nào.</div>
        <div v-else class="grid-products">
          <ProductCard v-for="p in feed" :key="'feed-' + p.id" :product="p" />
        </div>
        <div v-if="hasMore" class="more-wrap">
          <button class="btn secondary more-btn" :disabled="loading" @click="loadMore">{{ loading ? 'Đang tải...' : 'Xem thêm' }}</button>
        </div>
      </section>
    </div>
    <AppFooter />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import CategoryStrip from '../../components/CategoryStrip.vue'
import ProductCard from '../../components/ProductCard.vue'
import FlashSaleSection from '../../components/FlashSaleSection.vue'
import HomeBanners from '../../components/HomeBanners.vue'
import AppFooter from '../../components/AppFooter.vue'
import { topSelling, searchProducts } from '../../api/products'
import { visibleCampaigns } from '../../api/marketing'
import { forYouRecommendations } from '../../api/ai'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const topProducts = ref([])
const feed = ref([])
const campaigns = ref([])
const personalised = ref(false)
const loading = ref(true)
const hasMore = ref(false)
let nextPage = 0

// "Xem them" pages through the newest products after the personalised picks
async function loadMore() {
  loading.value = true
  try {
    const { data } = await searchProducts({ sort: 'newest', page: nextPage, size: 18 })
    const seen = new Set(feed.value.map(p => p.id))
    feed.value.push(...data.items.filter(p => !seen.has(p.id)))
    nextPage++
    hasMore.value = nextPage < data.totalPages
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  topSelling().then(({ data }) => { topProducts.value = data.slice(0, 6) }).catch(() => {})
  visibleCampaigns().then(({ data }) => { campaigns.value = data.slice(0, 4) }).catch(() => {})

  if (auth.isBuyer) {
    try {
      const { data } = await forYouRecommendations()
      feed.value = data
      personalised.value = data.length > 0
    } catch { /* non-fatal: fall back to newest products */ }
  }
  await loadMore()
})
</script>

<style scoped>
.home { padding-bottom: 40px; }
.hero-cta { background: white; flex-shrink: 0; }
.campaigns { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 8px; margin-bottom: 12px; }
.campaign { display: flex; flex-direction: column; gap: 2px; padding: 10px 14px; border-radius: var(--radius-md); color: white;
  background: linear-gradient(120deg, var(--theme), color-mix(in srgb, var(--theme) 70%, #000)); }
.campaign b { font-size: 15px; }
.campaign span { font-size: 12px; opacity: 0.9; }
.section-title { margin: 0 0 12px; font-size: 16px; color: var(--color-text-muted); font-weight: 500; }
.section-head { display: flex; align-items: baseline; justify-content: space-between; }
.see-all { color: var(--color-primary); font-size: 14px; }
.daily { margin-top: 12px; }
.daily-title {
  background: white; margin: 0 0 12px; padding: 16px; text-align: center;
  color: var(--color-primary); font-size: 16px; border-bottom: 4px solid var(--color-primary);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}
.loading { text-align: center; padding: 24px; }
.more-wrap { text-align: center; margin-top: 20px; }
.more-btn { min-width: 280px; background: white; }
</style>
