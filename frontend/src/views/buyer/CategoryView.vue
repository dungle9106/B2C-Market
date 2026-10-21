<template>
  <div>
    <AppHeader />
    <div class="container page">
      <!-- breadcrumb: Shoppi > Thoi trang > Ao nam -->
      <nav v-if="category" class="breadcrumb" aria-label="Breadcrumb">
        <router-link to="/">Shoppi</router-link>
        <template v-for="(c, i) in category.path" :key="c.id">
          <span class="sep">›</span>
          <router-link v-if="i < category.path.length - 1" :to="{ name: 'category', params: { id: c.id } }">{{ c.name }}</router-link>
          <span v-else class="current">{{ c.name }}</span>
        </template>
      </nav>

      <!-- sub-category chips -->
      <div v-if="category && category.children.length" class="sub-cats">
        <router-link
          v-for="c in category.children"
          :key="c.id"
          :to="{ name: 'category', params: { id: c.id } }"
          class="chip"
        >{{ c.name }}</router-link>
      </div>

      <ProductBrowser
        v-if="category"
        :base="{ categoryId }"
        :categories="sidebarCategories"
        :active-category-id="categoryId"
        default-sort="bestselling"
      >
        <template #header="{ total }">
          <h1 class="title">{{ category.name }} <span class="text-faint">({{ total }} sản phẩm)</span></h1>
        </template>
      </ProductBrowser>

      <div v-else-if="notFound" class="shopee-section not-found">Danh mục không tồn tại.</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import ProductBrowser from '../../components/ProductBrowser.vue'
import { getCategory } from '../../api/products'

const route = useRoute()
const categoryId = computed(() => Number(route.params.id))
const category = ref(null)
const notFound = ref(false)
const siblings = ref([])

// sidebar: this category's children, or (for a leaf) its siblings so the buyer can hop across
const sidebarCategories = computed(() =>
  category.value?.children.length ? category.value.children : siblings.value)

watch(categoryId, async (id) => {
  notFound.value = false
  try {
    const { data } = await getCategory(id)
    category.value = data
    const parent = data.path.length > 1 ? data.path[data.path.length - 2] : null
    siblings.value = parent ? (await getCategory(parent.id)).data.children : []
  } catch {
    category.value = null
    notFound.value = true
  }
}, { immediate: true })
</script>

<style scoped>
.page { padding-top: 16px; padding-bottom: 40px; }
.breadcrumb { display: flex; align-items: center; gap: 6px; font-size: 14px; margin-bottom: 12px; flex-wrap: wrap; }
.breadcrumb a { color: #05a; }
.breadcrumb .sep { color: var(--color-text-faint); }
.breadcrumb .current { color: var(--color-text-muted); }
.sub-cats { display: flex; gap: 8px; flex-wrap: wrap; background: white; padding: 14px 16px; border-radius: 4px; margin-bottom: 16px; }
.chip { padding: 6px 14px; border: 1px solid var(--color-border); border-radius: 16px; font-size: 13px; background: #fafafa; }
.chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
.title { font-size: 18px; font-weight: 500; margin: 0 0 12px; }
.not-found { text-align: center; padding: 48px; }
</style>
