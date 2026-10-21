<template>
  <div class="overlay" role="dialog" aria-modal="true" aria-labelledby="review-title" @click.self="$emit('close')">
    <form class="dialog" novalidate @submit.prevent="onSubmit">
      <h2 id="review-title" class="title">Đánh Giá Sản Phẩm</h2>

      <div class="product">
        <img :src="item.thumbnailUrl || placeholder" alt="" />
        <div>
          <div class="name">{{ item.productName }}</div>
          <div v-if="item.variantName" class="text-faint">Phân loại: {{ item.variantName }}</div>
        </div>
      </div>

      <div class="rating">
        <span class="label">Chất lượng sản phẩm</span>
        <StarRating v-model="rating" editable :size="28" />
        <span class="rating-text">{{ RATING_TEXT[rating] }}</span>
      </div>

      <textarea v-model="comment" rows="5" maxlength="2000"
                placeholder="Hãy chia sẻ những điều bạn thích về sản phẩm này với những người mua khác nhé."></textarea>

      <div class="media">
        <MediaUploader v-model="images" :max="6" add-label="Thêm ảnh" />
        <MediaUploader v-model="videos" kind="video" :max="1" add-label="Thêm video" />
      </div>

      <!-- shop + delivery stars: asked once per order -->
      <div v-if="askShop" class="shop-rating">
        <div class="rating">
          <span class="label">Dịch vụ của người bán</span>
          <StarRating v-model="shopRating" editable :size="22" />
          <span class="rating-text">{{ RATING_TEXT[shopRating] }}</span>
        </div>
        <div class="rating">
          <span class="label">Tốc độ giao hàng</span>
          <StarRating v-model="shippingRating" editable :size="22" />
          <span class="rating-text">{{ RATING_TEXT[shippingRating] }}</span>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions">
        <button type="button" class="btn ghost" @click="$emit('close')">Trở lại</button>
        <button type="submit" class="btn" :disabled="sending">{{ sending ? 'Đang gửi...' : 'Hoàn thành' }}</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import StarRating from './StarRating.vue'
import MediaUploader from './MediaUploader.vue'
import { submitReview } from '../api/reviews'
import { extractError } from '../utils/apiError'

// item: an order line ({ id, productName, variantName, thumbnailUrl });
// askShop: the order's shop/delivery stars haven't been given yet
const props = defineProps({
  item: { type: Object, required: true },
  askShop: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'done'])

const RATING_TEXT = { 1: 'Tệ', 2: 'Không hài lòng', 3: 'Bình thường', 4: 'Hài lòng', 5: 'Tuyệt vời' }
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="60" height="60"><rect width="60" height="60" fill="%23f0f0f0"/></svg>'

const rating = ref(5)
const comment = ref('')
const images = ref([])
const videos = ref([])
const shopRating = ref(5)
const shippingRating = ref(5)
const sending = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  sending.value = true
  try {
    await submitReview({
      orderItemId: props.item.id,
      rating: rating.value,
      comment: comment.value.trim() || null,
      imageUrls: images.value,
      videoUrl: videos.value[0] || null,
      shopRating: props.askShop ? shopRating.value : null,
      shippingRating: props.askShop ? shippingRating.value : null
    })
    emit('done')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.overlay { position: fixed; inset: 0; z-index: 900; background: rgba(0, 0, 0, 0.4); display: flex; align-items: center; justify-content: center; padding: 16px; }
.dialog { width: 600px; max-width: 100%; max-height: 92vh; overflow-y: auto; background: white; border-radius: 4px; padding: 24px; }
.title { font-size: 20px; font-weight: 400; margin: 0 0 20px; }
.product { display: flex; gap: 12px; align-items: center; margin-bottom: 20px; }
.product img { width: 56px; height: 56px; object-fit: cover; border: 1px solid var(--color-border); }
.name { font-size: 14px; }
.rating { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.label { font-size: 15px; }
.rating-text { color: #f6a700; font-size: 15px; }
textarea { resize: vertical; }
.error { color: #d33; font-size: 13px; margin: 12px 0 0; }
.media { display: flex; flex-direction: column; gap: 10px; }
.shop-rating { border-top: 1px solid var(--color-border); margin-top: 16px; padding-top: 12px; }
.shop-rating .rating { margin-bottom: 8px; }
.shop-rating .label { width: 170px; font-size: 14px; }
.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
</style>
