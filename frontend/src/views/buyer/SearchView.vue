<template>
  <div>
    <AppHeader />
    <div class="container page">
      <ProductBrowser :base="base" :categories="topCategories" :default-sort="keyword ? 'relevance' : 'bestselling'">
        <template #header="{ total }">
          <p class="result-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9V16h7v-2.1A6 6 0 0 0 12 3Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" /></svg>
            <template v-if="shopId">
              Sản phẩm của shop <span class="text-faint">({{ total }} sản phẩm)</span>
            </template>
            <template v-else-if="keyword">
              <span>Kết quả tìm kiếm cho từ khoá '<span class="text-primary">{{ keyword }}</span>'</span>
              <span class="text-faint">({{ total }} sản phẩm)</span>
            </template>
            <template v-else>Tất cả sản phẩm <span class="text-faint">({{ total }})</span></template>
          </p>
        </template>
      </ProductBrowser>

      <!-- AI: products close in meaning even without a matching word (Tailwind utilities) -->
      <section v-if="keyword && !shopId && semantic.length" class="tw-mt-6 tw-rounded tw-bg-white tw-p-4 tw-shadow-sm">
        <h2 class="tw-m-0 tw-mb-3 tw-flex tw-items-center tw-gap-2 tw-text-base tw-font-medium">
          <span class="tw-rounded tw-bg-primary-light tw-px-2 tw-py-0.5 tw-text-xs tw-font-semibold tw-text-primary">AI</span>
          Có thể bạn đang tìm
          <span class="tw-text-xs tw-font-normal tw-text-neutral-400">gợi ý theo ý nghĩa của "{{ keyword }}"</span>
        </h2>
        <div class="tw-grid tw-grid-cols-2 tw-gap-2.5 sm:tw-grid-cols-3 lg:tw-grid-cols-6">
          <ProductCard v-for="p in semantic" :key="p.id" :product="p" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import ProductBrowser from '../../components/ProductBrowser.vue'
import ProductCard from '../../components/ProductCard.vue'
import { listCategories, semanticProducts } from '../../api/products'

const route = useRoute()
const keyword = computed(() => (route.query.q || '').trim())
// "Xem Shop" on a product page lands here with ?shopId=
const shopId = computed(() => (route.query.shopId ? Number(route.query.shopId) : undefined))
const base = computed(() => ({ q: keyword.value || undefined, shopId: shopId.value }))
const topCategories = ref([])
const semantic = ref([])

watch(keyword, async (q) => {
  semantic.value = []
  if (!q || shopId.value) return
  try {
    semantic.value = (await semanticProducts(q, 12)).data
  } catch {
    // AI unavailable: the keyword results are enough
  }
}, { immediate: true })

onMounted(async () => {
  const { data } = await listCategories()
  topCategories.value = data
})
</script>

<style scoped>
.page { padding-top: 16px; padding-bottom: 40px; }
.result-title { display: flex; align-items: center; gap: 8px; color: var(--color-text-muted); margin: 0 0 12px; font-size: 15px; }
</style>
