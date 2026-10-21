<template>
  <div>
    <AppHeader />
    <div v-if="notFound" class="container"><div class="shopee-section empty">Shop không tồn tại.</div></div>

    <template v-else-if="shop">
      <!-- ===== header: cover, logo, stats ===== -->
      <section class="shop-head">
        <div class="container head-inner">
          <div class="card-box" :style="shop.coverUrl ? { backgroundImage: `linear-gradient(rgba(0,0,0,.45), rgba(0,0,0,.45)), url(${shop.coverUrl})` } : null">
            <div class="logo">
              <img v-if="shop.logoUrl" :src="shop.logoUrl" alt="" />
              <span v-else>{{ shop.name.charAt(0).toUpperCase() }}</span>
            </div>
            <div class="identity">
              <h1>{{ shop.name }}</h1>
              <span v-if="shop.verified" class="verified-badge" :title="`${BUSINESS_TYPES[shop.businessType] || 'Shop'} đã được Shoppi xác minh giấy tờ`">
                ✓ Đã xác minh<template v-if="shop.businessType && shop.businessType !== 'INDIVIDUAL'"> · {{ BUSINESS_TYPES[shop.businessType] }}</template>
              </span>
              <span v-if="!shop.active" class="paused">Shop đang tạm ngưng</span>
              <div class="actions">
                <router-link v-if="!auth.isSeller && !auth.isAdmin" :to="{ name: 'messages', query: { shopId: shop.id } }" class="btn sm ghost-light">💬 Chat</router-link>
              </div>
            </div>
          </div>

          <ul class="stats">
            <li><span class="k">Sản phẩm</span><span class="v">{{ shop.productCount }}</span></li>
            <li><span class="k">Đánh giá</span><span class="v">{{ shop.ratingAvg ? `${shop.ratingAvg.toFixed(1)} ★ (${shop.ratingCount})` : 'Chưa có' }}</span></li>
            <li><span class="k">Gửi từ</span><span class="v">{{ shop.province || '–' }}</span></li>
            <li v-if="shop.chatResponseRate != null"><span class="k">Phản hồi chat</span><span class="v">{{ shop.chatResponseRate.toLocaleString('vi-VN') }}%<template v-if="shop.chatResponseTime"> ({{ shop.chatResponseTime.toLowerCase() }})</template></span></li>
            <li><span class="k">Tham gia</span><span class="v">{{ joinedAgo(shop.joinedAt) }}</span></li>
            <li><span class="k">Chuẩn bị hàng</span><span class="v">{{ shop.handlingDays }} ngày</span></li>
          </ul>
        </div>
      </section>

      <div class="container page">
        <!-- ===== about + policies ===== -->
        <section class="shopee-section about">
          <div v-if="shop.description" class="desc">
            <h2>Giới thiệu Shop</h2>
            <p>{{ shop.description }}</p>
          </div>
          <div class="policy">
            <h2>Chính sách của Shop</h2>
            <ul>
              <li><span class="ico">↩️</span><div><strong>{{ returnDaysLabel(shop.defaultReturnDays) }}</strong><span v-if="shop.returnConditions" class="pre">{{ shop.returnConditions }}</span></div></li>
              <li><span class="ico">🚚</span><div><strong>Vận chuyển</strong><span>{{ shop.carrierNames.join(', ') }} · chuẩn bị hàng trong {{ shop.handlingDays }} ngày</span></div></li>
              <li v-if="shop.freeShipMinOrder"><span class="ico">🎁</span><div><strong>Miễn phí vận chuyển</strong><span>Cho đơn từ {{ formatVnd(shop.freeShipMinOrder) }} (tối đa bằng phí gói Tiết kiệm)</span></div></li>
            </ul>
          </div>
        </section>

        <ShopVoucherStrip :shop-id="shop.id" class="shopee-section" />

        <!-- "Danh muc cua Shop" tabs -->
        <nav v-if="categories.length" class="cat-tabs">
          <router-link :to="{ query: {} }" :class="{ active: !activeCat }">Tất cả</router-link>
          <router-link v-for="c in categories" :key="c.id" :to="{ query: { cat: c.id } }" :class="{ active: activeCat === c.id }">{{ c.name }}</router-link>
        </nav>

        <ProductBrowser :base="{ shopId: shop.id, shopCategoryId: activeCat || undefined }" default-sort="bestselling">
          <template #header="{ total }">
            <p class="result-title">{{ activeCatName || 'Tất cả sản phẩm của shop' }} <span class="text-faint">({{ total }})</span></p>
          </template>
        </ProductBrowser>
      </div>
    </template>

    <div v-else class="container"><div class="shopee-section empty">Đang tải...</div></div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import ProductBrowser from '../../components/ProductBrowser.vue'
import ShopVoucherStrip from '../../components/ShopVoucherStrip.vue'
import { publicShop } from '../../api/shop'
import { publicShopCategories } from '../../api/products'
import { useAuthStore } from '../../stores/auth'
import { BUSINESS_TYPES, returnDaysLabel, joinedAgo } from '../../utils/shop'
import { formatVnd } from '../../utils/format'

const route = useRoute()
const auth = useAuthStore()
const shop = ref(null)
const notFound = ref(false)
const categories = ref([])
const activeCat = computed(() => (route.query.cat ? Number(route.query.cat) : null))
const activeCatName = computed(() => categories.value.find(c => c.id === activeCat.value)?.name)

watch(() => route.params.id, async (id) => {
  shop.value = null
  notFound.value = false
  try {
    const { data } = await publicShop(id)
    shop.value = data
    publicShopCategories(id).then(({ data: cats }) => { categories.value = cats }).catch(() => { categories.value = [] })
  } catch {
    notFound.value = true
  }
}, { immediate: true })
</script>

<style scoped>
.shop-head { background: white; box-shadow: var(--shadow-card); }
.head-inner { display: grid; grid-template-columns: 400px 1fr; gap: 32px; align-items: center; padding-top: 20px; padding-bottom: 20px; }
.card-box {
  position: relative; height: 136px; border-radius: 4px; padding: 16px 20px;
  display: flex; align-items: center; gap: 16px; color: white;
  background: linear-gradient(135deg, #5b3a2e, #2b2b2b) center / cover;
}
.logo { width: 76px; height: 76px; border-radius: 50%; overflow: hidden; border: 3px solid rgba(255, 255, 255, 0.4); background: white; color: var(--color-primary); font-size: 30px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.logo img { width: 100%; height: 100%; object-fit: cover; }
.identity { min-width: 0; display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.identity h1 { font-size: 19px; margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.verified-badge { background: #2673dd; color: white; font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.paused { background: rgba(255, 255, 255, 0.2); font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.ghost-light { background: transparent; border: 1px solid rgba(255, 255, 255, 0.8); color: white; }
.ghost-light:hover { background: rgba(255, 255, 255, 0.15); }

.stats { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px 24px; }
.stats li { display: flex; gap: 8px; font-size: 14px; }
.stats .k { color: var(--color-text-muted); min-width: 110px; }
.stats .v { color: var(--color-primary); }

.page { padding-bottom: 40px; }
.about { display: grid; grid-template-columns: 1fr; gap: 32px; }
.about:has(.desc) { grid-template-columns: 1fr 1fr; }
.about h2 { font-size: 16px; font-weight: 500; margin: 0 0 10px; }
.desc p { white-space: pre-line; line-height: 1.6; color: var(--color-text-muted); margin: 0; }
.policy ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
.policy li { display: flex; gap: 10px; font-size: 14px; }
.policy li div { display: flex; flex-direction: column; gap: 2px; }
.policy li span:not(.ico) { color: var(--color-text-muted); font-size: 13px; line-height: 1.5; }
.pre { white-space: pre-line; }
.ico { font-size: 18px; }
.result-title { margin: 0; font-size: 15px; }
.cat-tabs { display: flex; gap: 4px; background: white; border-radius: 4px; padding: 0 12px; margin-bottom: 12px; overflow-x: auto; box-shadow: var(--shadow-card); }
.cat-tabs a { padding: 14px 16px; white-space: nowrap; border-bottom: 2px solid transparent; font-size: 14px; }
.cat-tabs a:hover { color: var(--color-primary); }
.cat-tabs a.active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: 500; }
.empty { text-align: center; color: var(--color-text-faint); padding: 48px 0; }

@media (max-width: 900px) {
  .head-inner { grid-template-columns: 1fr; gap: 16px; }
  .about { grid-template-columns: 1fr; }
}
</style>
