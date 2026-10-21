<template>
  <router-link :to="`/products/${product.id}`" class="product-card" @click="onOpen">
    <div class="img-wrap">
      <img :src="product.thumbnailUrl || placeholder" :alt="product.name" loading="lazy" />
      <span v-if="product.discountPercent" class="discount-badge">-{{ product.discountPercent }}%</span>
      <span v-if="product.stock <= 0" class="soldout-overlay">Hết hàng</span>
      <span v-if="product.sponsored" class="sponsored">Tài trợ</span>
    </div>
    <div class="info">
      <div class="name">
        <span v-if="product.flashSale" class="tag flash">⚡ Flash Sale</span>
        {{ product.name }}
      </div>
      <div class="tags">
        <span v-if="product.freeShipping" class="tag freeship">Freeship</span>
        <span v-if="product.discountPercent && !product.flashSale" class="tag sale">Giảm {{ product.discountPercent }}%</span>
      </div>
      <div class="price-row">
        <span class="price">{{ formatVnd(product.price) }}</span>
        <span v-if="product.originalPrice" class="old-price">{{ formatVnd(product.originalPrice) }}</span>
      </div>
      <div class="meta-row">
        <span class="rating">
          <template v-if="product.ratingCount">
            <StarRating :value="product.ratingAvg" :size="10" />
          </template>
        </span>
        <span>Đã bán {{ formatCount(product.soldCount) }}</span>
      </div>
      <div v-if="product.province" class="location">{{ product.province }}</div>
    </div>
  </router-link>
</template>

<script setup>
import StarRating from './StarRating.vue'
import { formatVnd, formatCount } from '../utils/format'
import { trackAdClick } from '../api/marketing'

const props = defineProps({ product: { type: Object, required: true } })

// a sponsored search result: the shop pays for the click (charged at most once per visitor)
function onOpen() {
  if (props.product.sponsored && props.product.adId) trackAdClick(props.product.adId)
}

const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="%23f0f0f0"/></svg>'
</script>

<style scoped>
.discount-badge {
  position: absolute; top: 0; right: 0;
  background: #ffe97a; color: var(--color-primary);
  font-size: 12px; font-weight: 700; padding: 2px 5px;
}
.soldout-overlay {
  position: absolute; inset: 0; margin: auto;
  width: 80px; height: 80px; border-radius: 50%;
  background: rgba(0, 0, 0, 0.55); color: white; font-size: 13px;
  display: flex; align-items: center; justify-content: center;
}
.tags { display: flex; gap: 4px; flex-wrap: wrap; min-height: 18px; margin-top: 4px; }
.tag { font-size: 10px; padding: 1px 4px; border-radius: 2px; line-height: 14px; white-space: nowrap; }
.tag.flash { background: var(--color-primary); color: white; margin-right: 2px; vertical-align: 1px; }
.tag.freeship { color: #00bfa5; border: 1px solid #00bfa5; }
.tag.sale { color: var(--color-primary); border: 1px solid var(--color-primary); }
.old-price { font-size: 11px; color: var(--color-text-faint); text-decoration: line-through; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rating { min-height: 12px; }
.location { font-size: 11px; color: var(--color-text-faint); margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sponsored { position: absolute; left: 0; bottom: 0; background: rgba(0, 0, 0, 0.55); color: white; font-size: 11px; padding: 2px 6px; border-top-right-radius: 3px; }
</style>
