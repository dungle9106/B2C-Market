<template>
  <!-- "Da thich (12)": guests are sent to login and come back -->
  <button type="button" class="like" :class="{ on: state.liked }" :disabled="busy" :aria-pressed="state.liked" @click="toggle">
    <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 21s-7.5-4.6-9.6-9.2C.9 8.4 3 4.5 6.7 4.5c2.1 0 3.6 1.1 4.3 2.4.7-1.3 2.2-2.4 4.3-2.4 3.7 0 5.8 3.9 4.3 7.3C19.5 16.4 12 21 12 21Z"
            :fill="state.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
    </svg>
    <span>{{ state.liked ? 'Đã thích' : 'Thích' }} ({{ formatCount(state.count) }})</span>
  </button>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { productLikes, likeProduct, unlikeProduct } from '../api/social'
import { formatCount } from '../utils/format'

const props = defineProps({ productId: { type: Number, required: true } })
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const state = reactive({ count: 0, liked: false })
const busy = ref(false)

watch(() => props.productId, async (id) => {
  try {
    Object.assign(state, (await productLikes(id)).data)
  } catch {
    // counts are a nicety
  }
}, { immediate: true })

async function toggle() {
  if (!auth.isLoggedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (!auth.isBuyer) return
  busy.value = true
  try {
    Object.assign(state, (await (state.liked ? unlikeProduct : likeProduct)(props.productId)).data)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.like { display: inline-flex; align-items: center; gap: 6px; background: none; border: none; cursor: pointer; color: var(--color-text-muted); font-size: 14px; padding: 4px 0; }
.like svg { color: #ee4d2d; }
.like.on { color: var(--color-text); }
.like:disabled { opacity: .6; cursor: default; }
</style>
