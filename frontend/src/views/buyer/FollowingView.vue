<template>
  <div>
    <AppHeader />
    <div class="container page">
      <nav class="tabs">
        <router-link :to="{ name: 'my-likes' }">♥ Sản phẩm yêu thích</router-link>
        <router-link :to="{ name: 'price-watches' }">🔔 Theo dõi giá</router-link>
        <router-link :to="{ name: 'my-follows' }" class="active">Shop đang theo dõi</router-link>
      </nav>
      <div v-if="loading" class="shopee-section empty">Đang tải...</div>
      <div v-else-if="!page.items.length" class="shopee-section empty">
        Bạn chưa theo dõi shop nào. Theo dõi shop để nhận tin khuyến mãi và voucher dành riêng cho người theo dõi.
      </div>
      <template v-else>
        <ul class="list">
          <li v-for="s in page.items" :key="s.id" class="shopee-section row">
            <router-link :to="{ name: 'shop', params: { id: s.id } }" class="logo">
              <img v-if="s.logoUrl" :src="s.logoUrl" alt="" />
              <span v-else>{{ s.name.charAt(0).toUpperCase() }}</span>
            </router-link>
            <div class="meta">
              <router-link :to="{ name: 'shop', params: { id: s.id } }" class="name">{{ s.name }}</router-link>
              <span class="text-faint small">{{ s.productCount }} sản phẩm · {{ formatCount(s.followers) }} người theo dõi<template v-if="s.province"> · {{ s.province }}</template></span>
              <span v-if="!s.active" class="paused">Shop đang tạm ngưng</span>
            </div>
            <router-link :to="{ name: 'shop', params: { id: s.id } }" class="btn secondary sm">Xem Shop</router-link>
            <button type="button" class="btn sm ghost" @click="remove(s)">Bỏ theo dõi</button>
          </li>
        </ul>
        <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { myFollows, unfollowShop } from '../../api/social'
import { formatCount } from '../../utils/format'

const page = ref({ items: [], page: 0, totalPages: 0 })
const loading = ref(true)

async function load(p = 0) {
  loading.value = true
  try {
    page.value = (await myFollows(p)).data
  } finally {
    loading.value = false
  }
}

async function remove(s) {
  if (!confirm(`Bỏ theo dõi ${s.name}?`)) return
  await unfollowShop(s.id)
  page.value.items = page.value.items.filter(x => x.id !== s.id)
}

onMounted(() => load(0))
</script>

<style scoped>
.page { padding-top: 20px; padding-bottom: 40px; }
.tabs { display: flex; background: white; box-shadow: var(--shadow-card); margin-bottom: 12px; }
.tabs a { flex: 1; text-align: center; padding: 14px; color: var(--color-text); border-bottom: 2px solid transparent; }
.tabs a.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.empty { text-align: center; padding: 48px 16px; color: var(--color-text-muted); }
.list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.row { display: flex; align-items: center; gap: 14px; padding: 16px 20px; }
.logo { width: 56px; height: 56px; border-radius: 50%; overflow: hidden; background: #f5f5f5; display: flex; align-items: center; justify-content: center; font-size: 22px; color: var(--color-primary); flex-shrink: 0; }
.logo img { width: 100%; height: 100%; object-fit: cover; }
.meta { flex: 1; display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.name { font-weight: 600; color: var(--color-text); }
.paused { color: #b26a00; font-size: 12px; }
</style>
