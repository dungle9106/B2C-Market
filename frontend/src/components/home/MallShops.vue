<template>
  <section v-if="shops.length" class="shopee-section mall">
    <div class="head">
      <h3>SHOPPI MALL</h3>
      <span class="perk">✓ Cửa hàng chính hãng</span>
      <span class="perk">✓ Đã được Shoppi xác minh giấy tờ</span>
      <router-link :to="{ name: 'search', query: { mall: 'true' } }" class="see-all">Xem tất cả ›</router-link>
    </div>
    <div class="grid">
      <router-link v-for="s in shops" :key="s.id" :to="{ name: 'shop', params: { id: s.id } }" class="shop">
        <span class="logo">
          <img v-if="s.logoUrl" :src="s.logoUrl" :alt="s.name" loading="lazy" />
          <b v-else>{{ s.name.charAt(0).toUpperCase() }}</b>
        </span>
        <span class="name">{{ s.name }}</span>
        <small>{{ s.productCount }} sản phẩm</small>
      </router-link>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { mallShops } from '../../api/shop'

const shops = ref([])

onMounted(() => {
  mallShops().then(({ data }) => { shops.value = data }).catch(() => {})
})
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding-bottom: 12px; border-bottom: 1px solid rgba(0, 0, 0, 0.06); margin-bottom: 12px; }
.head h3 { margin: 0; color: #d0011b; font-size: 17px; }
.perk { font-size: 13px; color: var(--color-text-muted); }
.see-all { margin-left: auto; color: #d0011b; font-size: 14px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
.shop { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 14px 8px; border: 1px solid rgba(0, 0, 0, 0.06); border-radius: 4px;
  color: var(--color-text); text-align: center; transition: box-shadow 0.15s, transform 0.15s; }
.shop:hover { box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08); transform: translateY(-1px); }
.logo { width: 64px; height: 64px; border-radius: 50%; overflow: hidden; display: flex; align-items: center; justify-content: center; background: #fff1f0; }
.logo img { width: 100%; height: 100%; object-fit: cover; }
.logo b { color: #d0011b; font-size: 24px; }
.name { font-size: 14px; font-weight: 500; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
small { color: var(--color-text-faint); font-size: 12px; }
</style>
