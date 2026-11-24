<template>
  <!-- "Bao khi giam gia": buyers press the bell; guests are sent to login and come back -->
  <span class="pw">
    <button type="button" class="bell" :class="{ on: state.watching }" :disabled="busy" :aria-pressed="state.watching" @click="onBell">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16Z" :fill="state.watching ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" />
        <path d="M10 20.5a2 2 0 0 0 4 0" stroke="currentColor" stroke-width="1.7" />
      </svg>
      <span>{{ state.watching ? (state.targetPrice ? `Báo khi ≤ ${formatVnd(state.targetPrice)}` : 'Đang theo dõi giá') : 'Báo khi giảm giá' }}</span>
    </button>

    <BaseModal v-if="open" title="Báo khi giảm giá" :width="420" @close="open = false">
      <p class="tw-mt-0 tw-text-sm">Shoppi sẽ gửi thông báo khi giá sản phẩm này giảm (kể cả khi có Flash Sale).</p>
      <p class="tw-text-sm tw-text-neutral-500">Giá hiện tại: <strong class="text-primary">{{ formatVnd(price) }}</strong></p>
      <label class="target-check">
        <input v-model="useTarget" type="checkbox" /> Chỉ báo khi giá xuống tới mức tôi chọn
      </label>
      <input v-if="useTarget" v-model.number="target" type="number" min="1000" step="1000" class="tw-mt-2" :placeholder="`Ví dụ ${Math.round(price * 0.8 / 1000) * 1000}`" />
      <div v-if="useTarget" class="quick">
        <button v-for="p in [10, 20, 30]" :key="p" type="button" class="chip" @click="target = Math.round(price * (100 - p) / 100 / 1000) * 1000">-{{ p }}%</button>
      </div>
      <p v-if="error" class="tw-text-sm tw-text-red-600">{{ error }}</p>
      <template #footer>
        <button v-if="state.watching" type="button" class="btn secondary" :disabled="busy" @click="stop">Bỏ theo dõi</button>
        <button type="button" class="btn" :disabled="busy" @click="save">{{ state.watching ? 'Cập nhật' : 'Theo dõi giá' }}</button>
      </template>
    </BaseModal>
  </span>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseModal from './BaseModal.vue'
import { useAuthStore } from '../stores/auth'
import { priceWatchStatus, watchPrice, unwatchPrice } from '../api/priceWatches'
import { formatVnd } from '../utils/format'
import { extractError } from '../utils/apiError'

const props = defineProps({ productId: { type: Number, required: true }, price: { type: Number, required: true } })
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const state = reactive({ watching: false, targetPrice: null })
const busy = ref(false)
const open = ref(false)
const useTarget = ref(false)
const target = ref(null)
const error = ref('')

watch(() => props.productId, async (id) => {
  Object.assign(state, { watching: false, targetPrice: null })
  if (!auth.isBuyer) return
  try { Object.assign(state, (await priceWatchStatus(id)).data) } catch { /* the bell just starts off */ }
}, { immediate: true })

function onBell() {
  if (!auth.isLoggedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (!auth.isBuyer) return
  error.value = ''
  useTarget.value = !!state.targetPrice
  target.value = state.targetPrice ? Number(state.targetPrice) : null
  open.value = true
}

async function save() {
  error.value = ''
  if (useTarget.value && !(target.value > 0 && target.value < props.price)) {
    error.value = 'Giá mong muốn phải thấp hơn giá hiện tại'
    return
  }
  busy.value = true
  try {
    const { data } = await watchPrice(props.productId, useTarget.value ? target.value : null)
    Object.assign(state, { watching: true, targetPrice: data.targetPrice })
    open.value = false
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function stop() {
  busy.value = true
  try {
    await unwatchPrice(props.productId)
    Object.assign(state, { watching: false, targetPrice: null })
    open.value = false
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.bell { display: inline-flex; align-items: center; gap: 6px; background: none; border: none; cursor: pointer; color: var(--color-text-muted); font-size: 14px; padding: 4px 0; }
.bell svg { color: var(--color-primary); }
.bell.on { color: var(--color-text); }
.bell:disabled { opacity: 0.6; cursor: default; }
.target-check { display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; }
.target-check input { width: auto; margin: 0; }
.quick { display: flex; gap: 6px; margin-top: 8px; }
.chip { border: 1px solid #ddd; background: white; border-radius: 2px; padding: 3px 10px; cursor: pointer; font-size: 13px; }
.chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
</style>
