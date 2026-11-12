<template>
  <div>
    <AppHeader />
    <div class="container page">
      <nav class="tabs">
        <router-link :to="{ name: 'my-likes' }" class="active">♥ Sản phẩm yêu thích</router-link>
        <router-link :to="{ name: 'my-follows' }">Shop đang theo dõi</router-link>
      </nav>
      <div v-if="loading" class="shopee-section empty">Đang tải...</div>
      <div v-else-if="!page.items.length" class="shopee-section empty">
        Bạn chưa thích sản phẩm nào. Bấm ♥ <strong>Thích</strong> ở trang sản phẩm để lưu lại xem sau.
        <div><router-link to="/search?q=" class="btn sm">Khám phá sản phẩm</router-link></div>
      </div>
      <template v-else>
        <div class="grid">
          <div v-for="p in page.items" :key="p.id" class="cell" :class="{ gone: p.available === false }">
            <ProductCard :product="p" />
            <span v-if="p.available === false" class="gone-tag">Ngừng bán</span>
            <button type="button" class="remove" title="Bỏ thích" @click="remove(p)">♥ Bỏ thích</button>
          </div>
        </div>
        <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import ProductCard from '../../components/ProductCard.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { myLikes, unlikeProduct } from '../../api/social'

const page = ref({ items: [], page: 0, totalPages: 0 })
const loading = ref(true)

async function load(p = 0) {
  loading.value = true
  try {
    page.value = (await myLikes(p)).data
  } finally {
    loading.value = false
  }
}

async function remove(p) {
  await unlikeProduct(p.id)
  page.value.items = page.value.items.filter(x => x.id !== p.id)
}

onMounted(() => load(0))
</script>

<style scoped>
.page { padding-top: 20px; padding-bottom: 40px; }
.tabs { display: flex; background: white; box-shadow: var(--shadow-card); margin-bottom: 12px; }
.tabs a { flex: 1; text-align: center; padding: 14px; color: var(--color-text); border-bottom: 2px solid transparent; }
.tabs a.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.empty { text-align: center; padding: 48px 16px; color: var(--color-text-muted); }
.empty .btn { margin-top: 14px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(188px, 1fr)); gap: 10px; }
.cell { position: relative; display: flex; flex-direction: column; }
.cell.gone :deep(a) { opacity: .5; }
.gone-tag { position: absolute; top: 8px; left: 8px; background: rgba(0, 0, 0, .65); color: white; font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.remove { background: white; border: 1px solid var(--color-border); border-top: none; color: var(--color-primary); padding: 6px; cursor: pointer; font-size: 13px; }
.remove:hover { background: #fff6f3; }
</style>
