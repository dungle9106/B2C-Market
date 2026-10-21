<template>
  <aside class="panel">
    <div v-if="!info" class="p-empty">{{ error || 'Đang tải...' }}</div>
    <template v-else>
      <section class="block who">
        <div class="avatar">{{ (info.name || '?').charAt(0).toUpperCase() }}</div>
        <div class="who-info">
          <strong>{{ info.name }}</strong>
          <div class="faint">Tham gia {{ formatDay(info.memberSince) }}</div>
        </div>
      </section>
      <section class="block stats">
        <div><b>{{ info.orderCount }}</b><span>Đơn đã mua</span></div>
        <div><b>{{ compactVnd(info.totalSpent) }}</b><span>Đã chi tiêu</span></div>
        <div><b>{{ info.reviewAvg ? '★ ' + info.reviewAvg.toFixed(1) : '—' }}</b><span>{{ info.reviewCount }} đánh giá</span></div>
      </section>
      <p v-if="info.cancelledCount" class="warn">⚠ Đã hủy {{ info.cancelledCount }} đơn với shop</p>

      <section class="block">
        <h4>Đơn hàng gần đây</h4>
        <div v-if="!info.orders.length" class="faint small">Khách chưa mua hàng của shop</div>
        <div v-for="o in info.orders" :key="o.id" class="order">
          <img :src="o.thumbnailUrl || placeholder" alt="" />
          <div class="order-info">
            <router-link :to="{ name: 'seller-order-detail', params: { id: o.id } }" target="_blank" class="code">{{ o.code }}</router-link>
            <div class="faint small">{{ statusLabel(o.status) }} · {{ formatVnd(o.total) }}</div>
          </div>
          <button type="button" class="send" :disabled="!canSend" title="Gửi thẻ đơn hàng vào cuộc trò chuyện" @click="sendOrder(o)">Gửi</button>
        </div>
      </section>

      <section class="block">
        <h4>Gửi sản phẩm cho khách</h4>
        <input v-model.trim="q" type="search" placeholder="Tìm sản phẩm của shop" @focus="loadProducts" />
        <div v-if="q || productsOpen" class="products">
          <div v-if="!products" class="faint small">Đang tải...</div>
          <div v-for="p in matches" :key="p.id" class="prod">
            <img :src="p.thumbnailUrl || placeholder" alt="" />
            <div class="prod-info">
              <div class="prod-name">{{ p.name }}</div>
              <div class="small"><span class="text-primary">{{ formatVnd(p.price) }}</span> <span class="faint">· kho {{ p.stock }}</span></div>
            </div>
            <button type="button" class="send" :disabled="!canSend" @click="sendProduct(p)">Gửi</button>
          </div>
          <div v-if="products && !matches.length" class="faint small">Không tìm thấy sản phẩm</div>
        </div>
      </section>
    </template>
  </aside>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { chatCustomer } from '../../api/chat'
import { myProducts } from '../../api/products'
import { statusLabel } from '../../utils/orderStatus'
import { formatVnd } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const props = defineProps({
  conversationId: { type: Number, required: true },
  canSend: { type: Boolean, default: true }
})
// send(body, preview): the workspace posts it like a typed message
const emit = defineEmits(['send'])

const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'
const info = ref(null)
const error = ref('')
const q = ref('')
const products = ref(null)
const productsOpen = ref(false)

const formatDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '')
const compactVnd = (v) => {
  const n = Number(v || 0)
  return n >= 1e6 ? '₫' + (n / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + 'tr' : formatVnd(n)
}
const matches = computed(() => {
  const needle = q.value.toLowerCase()
  return (products.value || []).filter(p => p.active !== false && (!needle || p.name.toLowerCase().includes(needle))).slice(0, 8)
})

async function load() {
  info.value = null
  error.value = ''
  try {
    info.value = (await chatCustomer(props.conversationId)).data
  } catch (e) {
    error.value = extractError(e)
  }
}

async function loadProducts() {
  productsOpen.value = true
  if (products.value) return
  try {
    products.value = (await myProducts()).data
  } catch {
    products.value = []
  }
}

function sendProduct(p) {
  emit('send', { productId: p.id }, { product: { id: p.id, name: p.name, thumbnailUrl: p.thumbnailUrl, price: p.price } })
}

function sendOrder(o) {
  emit('send', { orderId: o.id }, { order: { id: o.id, code: o.code, status: o.status, total: o.total, thumbnailUrl: o.thumbnailUrl, itemCount: o.itemCount } })
}

watch(() => props.conversationId, load, { immediate: true })
</script>

<style scoped>
.panel { border-left: 1px solid var(--color-border); overflow-y: auto; min-height: 0; background: #fcfcfc; }
.p-empty { padding: 30px 16px; text-align: center; color: var(--color-text-faint); font-size: 13px; }
.block { padding: 12px 14px; border-bottom: 1px solid var(--color-border); }
.block h4 { margin: 0 0 8px; font-size: 13px; color: var(--color-text-muted); font-weight: 600; }
.who { display: flex; align-items: center; gap: 10px; }
.avatar { width: 40px; height: 40px; border-radius: 50%; background: #e8f0fe; color: #2673dd; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.who-info { min-width: 0; }
.who-info strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.small { font-size: 12px; }
.stats { display: grid; grid-template-columns: repeat(3, 1fr); text-align: center; gap: 4px; }
.stats b { display: block; font-size: 15px; font-weight: 600; }
.stats span { font-size: 11px; color: var(--color-text-faint); }
.warn { margin: 0; padding: 6px 14px; font-size: 12px; color: #b42318; background: #fdecea; }
.order, .prod { display: flex; align-items: center; gap: 8px; padding: 6px 0; }
.order img, .prod img { width: 36px; height: 36px; object-fit: cover; border-radius: 3px; flex-shrink: 0; }
.order-info, .prod-info { flex: 1; min-width: 0; }
.code { font-size: 13px; color: #2673dd; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.prod-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.send { background: white; border: 1px solid var(--color-primary); color: var(--color-primary); border-radius: 3px; font-size: 12px; padding: 2px 8px; cursor: pointer; flex-shrink: 0; }
.send:hover:not(:disabled) { background: var(--color-primary-light); }
.send:disabled { opacity: 0.4; cursor: not-allowed; }
.block input { margin: 0 0 6px; padding: 6px 10px; font-size: 13px; }
.products { max-height: 260px; overflow-y: auto; }
</style>
