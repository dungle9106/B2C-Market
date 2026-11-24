<template>
  <div>
    <AppHeader />
    <div class="container page">
      <section class="shopee-section box">
        <div class="code">404</div>
        <h1>Không tìm thấy trang</h1>
        <p class="text-muted">Địa chỉ <code>{{ route.fullPath }}</code> không tồn tại. Hãy kiểm tra lại đường dẫn, ví dụ dấu chấm hoặc dấu gạch thừa ở cuối.</p>
        <div class="actions">
          <router-link v-if="suggestion" :to="suggestion" class="btn">Đến {{ suggestion }}</router-link>
          <router-link to="/" class="btn secondary">Về trang chủ</router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'

const route = useRoute()
const router = useRouter()

// a typo at the end ("/admin.", "/cart/") usually means the address without it
const suggestion = computed(() => {
  const trimmed = route.path.replace(/[./\s]+$/, '')
  if (!trimmed || trimmed === route.path) return null
  return router.resolve(trimmed).name !== 'not-found' ? trimmed : null
})
</script>

<style scoped>
.page { padding-top: 40px; padding-bottom: 40px; }
.box { text-align: center; padding: 48px 24px; }
.code { font-size: 64px; font-weight: 700; color: var(--color-primary); line-height: 1; }
h1 { font-size: 20px; font-weight: 500; margin: 12px 0 8px; }
code { background: #f5f5f5; padding: 1px 6px; border-radius: 3px; }
.actions { display: flex; gap: 10px; justify-content: center; margin-top: 20px; flex-wrap: wrap; }
</style>
