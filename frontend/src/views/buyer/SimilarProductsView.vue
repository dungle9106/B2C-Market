<template>
  <div>
    <AppHeader />
    <div class="container page">
      <div v-if="error" class="shopee-section empty">{{ error }}</div>
      <template v-else>
        <section v-if="source" class="shopee-section source">
          <img :src="source.thumbnailUrl || placeholder" alt="" />
          <div class="meta">
            <div class="text-muted small">Sản phẩm tương tự với</div>
            <router-link :to="{ name: 'product-detail', params: { id: source.id } }" class="name">{{ source.name }}</router-link>
            <div class="price">{{ formatVnd(source.price) }}</div>
          </div>
        </section>
        <section class="shopee-section">
          <h3 class="section-title">SẢN PHẨM TƯƠNG TỰ</h3>
          <div v-if="loading" class="text-muted empty">Đang tải...</div>
          <div v-else-if="!items.length" class="text-muted empty">Chưa tìm thấy sản phẩm tương tự.</div>
          <div v-else class="grid-products">
            <ProductCard v-for="p in items" :key="p.id" :product="p" />
          </div>
        </section>
      </template>
    </div>
    <AppFooter />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import AppFooter from '../../components/AppFooter.vue'
import ProductCard from '../../components/ProductCard.vue'
import { getProduct, similarProducts } from '../../api/products'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'

const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="%23f0f0f0"/></svg>'
const route = useRoute()
const source = ref(null)
const items = ref([])
const loading = ref(true)
const error = ref('')

async function load(id) {
  loading.value = true
  error.value = ''
  try {
    const [p, s] = await Promise.all([getProduct(id), similarProducts(id)])
    source.value = p.data
    items.value = s.data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, (id) => id && load(id), { immediate: true })
</script>

<style scoped>
.page { padding-top: 16px; padding-bottom: 32px; }
.source { display: flex; gap: 14px; align-items: center; }
.source img { width: 80px; height: 80px; object-fit: cover; border: 1px solid rgba(0, 0, 0, 0.06); }
.meta { min-width: 0; }
.name { display: block; font-size: 16px; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.price { color: var(--color-primary); font-size: 18px; margin-top: 2px; }
.small { font-size: 12px; }
.empty { text-align: center; padding: 32px; }
</style>
