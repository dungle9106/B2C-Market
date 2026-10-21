<template>
  <div v-if="vouchers.length" class="strip">
    <span class="label">{{ label }}</span>
    <div class="list">
      <div v-for="v in vouchers" :key="v.id" class="ticket">
        <div class="ticket-main">
          <strong>{{ v.title }}</strong>
          <span class="cond">{{ v.minOrder > 0 ? `Đơn từ ${formatVnd(v.minOrder)}` : 'Không cần đơn tối thiểu' }}<template v-if="v.productIds.length"> · Sản phẩm chọn lọc</template></span>
          <span class="cond">HSD {{ formatDay(v.endAt) }}<template v-if="v.usageLimit"> · Còn {{ v.usageLimit - v.usedCount }} lượt</template></span>
        </div>
        <button type="button" class="copy" @click="copy(v)">{{ copied === v.id ? 'Đã chép ✓' : v.code }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { shopVouchers } from '../api/vouchers'
import { formatVnd } from '../utils/format'

// productId: only the vouchers that apply to that product
const props = defineProps({
  shopId: { type: Number, required: true },
  productId: { type: Number, default: null },
  label: { type: String, default: 'Mã giảm giá của Shop' }
})

const vouchers = ref([])
const copied = ref(null)
const formatDay = (d) => new Date(d).toLocaleDateString('vi-VN')

// the buyer picks shop vouchers at checkout; copying the code also works for typing it in
async function copy(v) {
  try {
    await navigator.clipboard.writeText(v.code)
  } catch {
    // clipboard blocked (http, old browser): the code is visible on the button anyway
  }
  copied.value = v.id
  setTimeout(() => { if (copied.value === v.id) copied.value = null }, 2000)
}

watch(() => [props.shopId, props.productId], async () => {
  try {
    vouchers.value = (await shopVouchers(props.shopId, props.productId || undefined)).data
  } catch {
    vouchers.value = []
  }
}, { immediate: true })
</script>

<style scoped>
.strip { display: flex; gap: 16px; align-items: flex-start; }
.label { color: var(--color-text-muted); font-size: 14px; min-width: 110px; padding-top: 10px; }
.list { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 4px; }
.ticket { display: flex; align-items: stretch; flex-shrink: 0; border: 1px solid #f2b8a9; background: #fff6f3; border-radius: 3px; position: relative; }
.ticket-main { display: flex; flex-direction: column; gap: 2px; padding: 8px 12px; border-right: 1px dashed #f2b8a9; min-width: 160px; }
.ticket-main strong { color: var(--color-primary); font-size: 14px; }
.cond { font-size: 11px; color: var(--color-text-muted); }
.copy { background: var(--color-primary); color: white; border: none; padding: 0 12px; cursor: pointer; font-size: 12px; font-weight: 600; letter-spacing: 0.5px; min-width: 90px; }
.copy:hover { background: var(--color-primary-dark); }
</style>
