<template>
  <div v-if="vouchers.length" class="strip">
    <span class="label">{{ label }}</span>
    <div class="list">
      <div v-for="v in vouchers" :key="v.id" class="ticket">
        <div class="ticket-main">
          <strong>{{ v.title }}</strong>
          <span v-if="v.audience && v.audience !== 'ALL'" class="aud">{{ v.audienceLabel }}</span>
          <span class="cond">{{ v.minOrder > 0 ? `Đơn từ ${formatVnd(v.minOrder)}` : 'Không cần đơn tối thiểu' }}<template v-if="v.productIds.length"> · Sản phẩm chọn lọc</template></span>
          <span class="cond">HSD {{ formatDay(v.endAt) }}<template v-if="v.usageLimit"> · Còn {{ v.usageLimit - v.usedCount }} lượt</template></span>
        </div>
        <div class="actions">
          <button v-if="auth.isBuyer || !auth.isLoggedIn" type="button" class="save" :class="{ done: saved.has(v.id) }" :disabled="saved.has(v.id)" @click="save(v)">
            {{ saved.has(v.id) ? 'Đã lưu' : 'Lưu' }}
          </button>
          <button type="button" class="copy" @click="copy(v)">{{ copied === v.id ? 'Đã chép ✓' : v.code }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { shopVouchers, saveVoucher, savedVoucherIds } from '../api/vouchers'
import { useAuthStore } from '../stores/auth'
import { useRoute, useRouter } from 'vue-router'
import { formatVnd } from '../utils/format'

// productId: only the vouchers that apply to that product
const props = defineProps({
  shopId: { type: Number, required: true },
  productId: { type: Number, default: null },
  label: { type: String, default: 'Mã giảm giá của Shop' }
})

const vouchers = ref([])
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const saved = ref(new Set())
if (auth.isBuyer) savedVoucherIds().then(({ data }) => { saved.value = new Set(data) }).catch(() => {})

// "Luu" puts it in the buyer's "Kho Voucher"; guests sign in first
async function save(v) {
  if (!auth.isLoggedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  await saveVoucher(v.id)
  saved.value = new Set([...saved.value, v.id])
}
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
.aud { align-self: flex-start; font-size: 10px; color: white; background: var(--color-primary); border-radius: 2px; padding: 0 5px; }
.copy { background: var(--color-primary); color: white; border: none; padding: 0 12px; cursor: pointer; font-size: 12px; font-weight: 600; letter-spacing: 0.5px; min-width: 90px; }
.copy:hover { background: var(--color-primary-dark); }
.actions { display: flex; flex-direction: column; justify-content: center; gap: 4px; padding: 6px 8px; }
.save { background: var(--color-primary); color: white; border: none; border-radius: 2px; font-size: 12px; padding: 4px 10px; cursor: pointer; }
.save.done { background: #f5f5f5; color: var(--color-text-faint); cursor: default; }
</style>
