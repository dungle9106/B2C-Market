<template>
  <div class="price-ai">
    <div class="row">
      <button type="button" class="ai-btn" :disabled="loading" @click="load">{{ loading ? 'Đang phân tích...' : '💡 Gợi ý giá bán' }}</button>
      <input v-model.number="cost" type="number" min="0" class="cost" placeholder="Giá vốn (không bắt buộc)" title="Nhập giá vốn để kiểm tra lợi nhuận" />
      <span class="hint">So sánh với sản phẩm tương tự của các shop khác trên Shoppi</span>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="data && data.message" class="empty">{{ data.message }}</div>
    <div v-else-if="data" class="result">
      <div class="head">
        <div>
          <div class="label">Giá đề xuất</div>
          <div class="suggested">{{ formatVnd(data.suggested) }}</div>
          <div class="faint">dựa trên {{ data.count }} sản phẩm {{ data.basis === 'similar' ? 'tương tự' : 'cùng ngành hàng' }}</div>
        </div>
        <button type="button" class="btn sm" @click="$emit('apply', data.suggested)">Dùng giá này</button>
      </div>

      <!-- p25 - p75 band on the min - max scale, with the median and the current price -->
      <div class="scale">
        <div class="band" :style="{ left: pos(data.p25) + '%', width: pos(data.p75) - pos(data.p25) + '%' }"></div>
        <div class="tick median" :style="{ left: pos(data.median) + '%' }" :title="'Trung vị ' + formatVnd(data.median)"></div>
        <div class="tick suggested-tick" :style="{ left: pos(data.suggested) + '%' }" title="Giá đề xuất"></div>
        <div v-if="currentPrice" class="tick current" :style="{ left: pos(currentPrice) + '%' }" :title="'Giá bạn nhập ' + formatVnd(currentPrice)"></div>
      </div>
      <div class="scale-labels">
        <span>{{ formatVnd(data.min) }}</span>
        <span class="mid">Phổ biến: {{ formatVnd(data.p25) }} – {{ formatVnd(data.p75) }}</span>
        <span>{{ formatVnd(data.max) }}</span>
      </div>
      <p v-if="currentPrice" class="position" :class="position.cls">{{ position.text }}</p>

      <ul class="notes">
        <li v-for="n in data.notes" :key="n">{{ n }}</li>
      </ul>

      <details>
        <summary>Sản phẩm dùng để so sánh ({{ data.comparables.length }})</summary>
        <div v-for="c in data.comparables" :key="c.id" class="comp">
          <img :src="c.thumbnailUrl || placeholder" alt="" />
          <router-link :to="`/products/${c.id}`" target="_blank" class="comp-name">{{ c.name }}</router-link>
          <span class="faint">{{ c.shopName }}</span>
          <span class="faint">Đã bán {{ c.soldCount }}</span>
          <strong>{{ formatVnd(c.price) }}</strong>
        </div>
      </details>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { suggestPrice } from '../../api/ai'
import { formatVnd } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const props = defineProps({
  name: { type: String, default: '' },
  categoryId: { type: Number, default: null },
  currentPrice: { type: Number, default: null }
})
defineEmits(['apply'])

const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="%23f0f0f0"/></svg>'
const data = ref(null)
const loading = ref(false)
const error = ref('')
const cost = ref(null)

async function load() {
  if (!props.name.trim()) {
    error.value = 'Nhập tên sản phẩm (và chọn ngành hàng) trước để AI tìm sản phẩm tương tự'
    return
  }
  error.value = ''
  loading.value = true
  try {
    data.value = (await suggestPrice({ name: props.name, categoryId: props.categoryId, cost: cost.value || undefined })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

const pos = (v) => {
  const { min, max } = data.value
  if (max === min) return 50
  return Math.max(0, Math.min(100, ((v - min) / (max - min)) * 100))
}

const position = computed(() => {
  const p = props.currentPrice
  const d = data.value
  if (!p || !d || d.message) return {}
  if (p < d.p25) return { cls: 'low', text: `Giá bạn nhập thấp hơn 75% sản phẩm tương tự – dễ bán nhưng hãy kiểm tra lợi nhuận.` }
  if (p > d.p75) return { cls: 'high', text: `Giá bạn nhập cao hơn 75% sản phẩm tương tự – cần làm nổi bật điểm khác biệt (chất lượng, quà tặng, bảo hành).` }
  return { cls: 'ok', text: 'Giá bạn nhập nằm trong khoảng phổ biến của thị trường 👍' }
})
</script>

<style scoped>
.price-ai { margin: 4px 0 16px; padding: 12px 14px; border: 1px dashed #e0d8ff; background: #faf8ff; border-radius: 6px; }
.row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ai-btn { background: #6941c6; color: white; border: none; border-radius: 4px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
.ai-btn:disabled { opacity: 0.6; }
.cost { width: 190px; margin: 0; padding: 5px 8px; font-size: 13px; }
.hint { font-size: 12px; color: var(--color-text-faint); }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
.empty { margin-top: 10px; font-size: 13px; color: var(--color-text-muted); }
.result { margin-top: 12px; }
.head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; }
.label { font-size: 12px; color: var(--color-text-muted); }
.suggested { font-size: 24px; font-weight: 600; color: #6941c6; }
.faint { font-size: 12px; color: var(--color-text-faint); }
.scale { position: relative; height: 10px; background: #ece8f7; border-radius: 999px; margin: 16px 0 6px; }
.band { position: absolute; top: 0; bottom: 0; background: #b9a6ef; border-radius: 999px; }
.tick { position: absolute; top: -4px; width: 3px; height: 18px; border-radius: 2px; transform: translateX(-50%); }
.tick.median { background: #6941c6; }
.tick.suggested-tick { background: #1e9e4a; width: 4px; }
.tick.current { background: var(--color-primary); width: 4px; }
.scale-labels { display: flex; justify-content: space-between; font-size: 12px; color: var(--color-text-muted); }
.scale-labels .mid { color: #6941c6; }
.position { font-size: 13px; margin: 8px 0 0; }
.position.ok { color: #1e9e4a; }
.position.low { color: #e67e22; }
.position.high { color: #d33; }
.notes { margin: 10px 0 6px; padding-left: 18px; font-size: 13px; line-height: 1.6; }
details summary { cursor: pointer; font-size: 13px; color: #2673dd; }
.comp { display: flex; align-items: center; gap: 10px; padding: 5px 0; font-size: 13px; }
.comp img { width: 32px; height: 32px; object-fit: cover; border-radius: 2px; }
.comp-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
