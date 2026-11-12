<template>
  <div>
    <AppHeader />
    <div class="container page">
      <section class="shopee-section head">
        <div class="preview" @click="fileEl.click()" title="Chọn ảnh khác">
          <img v-if="state.previewUrl" :src="state.previewUrl" alt="Ảnh tìm kiếm" />
          <span v-else>📷<br />Chọn ảnh</span>
        </div>
        <input ref="fileEl" type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden @change="onPick" />
        <div class="tw-min-w-0 tw-flex-1">
          <h1 class="tw-m-0 tw-text-lg">Tìm kiếm bằng hình ảnh</h1>
          <p v-if="!state.file" class="tw-text-sm tw-text-neutral-500">Chọn ảnh sản phẩm (JPG, PNG, tối đa 5MB) để tìm sản phẩm tương tự trên Shoppi.</p>
          <p v-else-if="loading" class="tw-text-sm tw-text-neutral-500">Đang phân tích ảnh...</p>
          <template v-else-if="result">
            <p v-if="result.description" class="tw-my-1 tw-text-sm">
              ✨ AI nhận diện: <strong>{{ result.description }}</strong>
              <router-link :to="{ name: 'search', query: { q: result.description } }" class="tw-ml-2 tw-text-[#2673dd]">Tìm theo mô tả này ›</router-link>
            </p>
            <p v-else class="tw-my-1 tw-text-sm tw-text-neutral-500">
              So sánh theo màu sắc và hình dáng với {{ result.indexed }} ảnh sản phẩm.
              <template v-if="!auth.isLoggedIn"> <router-link :to="{ name: 'login', query: { redirect: '/search/image' } }">Đăng nhập</router-link> để AI nhận diện sản phẩm chính xác hơn.</template>
            </p>
            <div v-if="result.keywords?.length" class="tw-flex tw-flex-wrap tw-gap-1.5">
              <router-link v-for="k in result.keywords" :key="k" :to="{ name: 'search', query: { q: k } }" class="kw">{{ k }}</router-link>
            </div>
          </template>
          <p v-if="error" class="tw-text-sm tw-text-red-600">{{ error }}</p>
          <button type="button" class="btn sm secondary tw-mt-2" @click="fileEl.click()">Chọn ảnh khác</button>
        </div>
      </section>

      <div v-if="result && !result.items.length" class="shopee-section empty">Không tìm thấy sản phẩm nào giống ảnh này. Hãy thử ảnh rõ hơn, chụp chính diện sản phẩm.</div>
      <div v-else-if="result" class="grid">
        <div v-for="it in result.items" :key="it.product.id" class="cell">
          <ProductCard :product="it.product" />
          <span v-if="it.similarity >= 50" class="sim">Giống {{ it.similarity }}%</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import ProductCard from '../../components/ProductCard.vue'
import { imageSearch } from '../../api/products'
import { imageSearchState as state, pickImage } from '../../utils/imageSearch'
import { useAuthStore } from '../../stores/auth'
import { extractError } from '../../utils/apiError'
import { useRoute } from 'vue-router'

const auth = useAuthStore()
const route = useRoute()
const fileEl = ref(null)
const result = ref(null)
const loading = ref(false)
const error = ref('')

async function run() {
  if (!state.file) return
  loading.value = true
  error.value = ''
  result.value = null
  try {
    result.value = (await imageSearch(state.file)).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

function onPick(e) {
  const f = e.target.files[0]
  e.target.value = ''
  if (!f) return
  pickImage(f)
  run()
}

// a new picture from the header while already on this page
watch(() => route.query.t, run)
onMounted(run)
</script>

<style scoped>
.page { padding-top: 20px; padding-bottom: 40px; }
.head { display: flex; gap: 20px; align-items: flex-start; padding: 16px 20px; margin-bottom: 12px; }
.preview { width: 140px; height: 140px; flex-shrink: 0; border: 1px dashed var(--color-border); border-radius: 4px; overflow: hidden; display: flex;
  align-items: center; justify-content: center; text-align: center; color: var(--color-text-muted); cursor: pointer; background: #fafafa; }
.preview img { width: 100%; height: 100%; object-fit: contain; }
.kw { font-size: 12px; padding: 2px 10px; border: 1px solid var(--color-primary); color: var(--color-primary); border-radius: 999px; }
.empty { text-align: center; padding: 40px 16px; color: var(--color-text-muted); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(188px, 1fr)); gap: 10px; }
.cell { position: relative; }
.sim { position: absolute; top: 8px; right: 8px; background: rgba(38, 115, 221, .9); color: white; font-size: 11px; padding: 2px 6px; border-radius: 2px; }
</style>
