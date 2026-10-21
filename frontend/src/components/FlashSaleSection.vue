<template>
  <section v-if="items.length || upcoming.length" class="flash">
    <header class="flash-head">
      <h2 class="flash-title">
        <span class="bolt">⚡</span> FLASH SALE
      </h2>
      <span v-if="tab !== 'now'" class="opens">Bắt đầu sau</span>
      <div class="countdown" aria-label="Thời gian còn lại">
        <span v-for="(p, i) in parts" :key="i" class="digit-box">{{ p }}</span>
      </div>
      <router-link :to="{ name: 'search', query: { onSale: 'true', sort: 'bestselling' } }" class="see-all">Xem tất cả ›</router-link>
    </header>

    <!-- Shoppi Flash Sale slots: what is on now, and the next time slots -->
    <nav v-if="upcoming.length" class="slots">
      <button v-if="items.length" type="button" :class="{ on: tab === 'now' }" @click="tab = 'now'">
        <b>{{ runningSlot ? hm(runningSlot.startAt) : 'Bây giờ' }}</b><span>Đang diễn ra</span>
      </button>
      <button v-for="s in upcoming" :key="s.id" type="button" :class="{ on: tab === s.id }" @click="tab = s.id">
        <b>{{ hm(s.startAt) }}</b><span>{{ dayLabel(s.startAt) }}</span>
      </button>
    </nav>

    <div v-if="tab === 'now'" class="flash-list">
      <router-link v-for="p in items" :key="p.id" :to="`/products/${p.id}`" class="flash-item">
        <div class="img">
          <img :src="p.thumbnailUrl || placeholder" :alt="p.name" loading="lazy" />
          <span v-if="p.discountPercent" class="badge">-{{ p.discountPercent }}%</span>
        </div>
        <div class="price">{{ formatVnd(p.price) }}</div>
        <!-- stock bar like Shopee's "Dang ban chay" -->
        <div class="bar">
          <div class="bar-fill" :style="{ width: soldPercent(p) + '%' }"></div>
          <span class="bar-text">{{ p.stock <= 0 ? 'Đã bán hết' : soldPercent(p) >= 70 ? 'Sắp cháy hàng'
            : p.flashSaleStock ? `Còn ${p.flashSaleStock - (p.flashSaleSold || 0)} suất` : 'Đã bán ' + formatCount(p.soldCount) }}</span>
        </div>
      </router-link>
    </div>
    <div v-else class="flash-list">
      <router-link v-for="x in slotProducts" :key="x.product.id" :to="`/products/${x.product.id}`" class="flash-item">
        <div class="img">
          <img :src="x.product.thumbnailUrl || placeholder" :alt="x.product.name" loading="lazy" />
          <span class="badge">-{{ x.discountPercent }}%</span>
        </div>
        <div class="price">{{ formatVnd(x.flashPrice) }}</div>
        <div class="bar soon"><span class="bar-text">{{ x.flashStock ? `${x.flashStock} suất · ` : '' }}Sắp mở bán</span></div>
      </router-link>
      <p v-if="!slotProducts.length" class="empty">Shoppi đang duyệt sản phẩm cho khung giờ này</p>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { flashSaleProducts } from '../api/products'
import { flashSlots } from '../api/marketing'
import { useCountdown } from '../composables/useCountdown'
import { formatVnd, formatCount } from '../utils/format'

const items = ref([])
const slots = ref([])
const tab = ref('now')
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="%23f0f0f0"/></svg>'

const runningSlot = computed(() => slots.value.find(s => s.status === 'RUNNING'))
const upcoming = computed(() => slots.value.filter(s => s.status !== 'RUNNING' && s.products.length))
const slotProducts = computed(() => upcoming.value.find(s => s.id === tab.value)?.products || [])

// now: count down to the flash sale that ends first (the list is sorted that way); a coming slot: to its start
const { parts, ended } = useCountdown(() => (tab.value === 'now' ? items.value[0]?.flashSaleEnd : upcoming.value.find(s => s.id === tab.value)?.startAt))
// with a flash quota: units of the quota sold; otherwise sold vs stock
const soldPercent = (p) => {
  if (p.flashSaleStock) return Math.min(100, Math.round(((p.flashSaleSold || 0) / p.flashSaleStock) * 100))
  const total = (p.soldCount || 0) + Math.max(p.stock || 0, 0)
  return total ? Math.round(((p.soldCount || 0) / total) * 100) : 0
}
const hm = (t) => new Date(t).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
function dayLabel(t) {
  const d = new Date(t), today = new Date()
  const diff = Math.round((new Date(d.toDateString()) - new Date(today.toDateString())) / 86400000)
  return diff === 0 ? 'Sắp diễn ra' : diff === 1 ? 'Ngày mai' : d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })
}

async function load() {
  const [running, s] = await Promise.all([flashSaleProducts(12), flashSlots(12).catch(() => ({ data: [] }))])
  items.value = running.data
  slots.value = s.data
  if (!items.value.length && upcoming.value.length) tab.value = upcoming.value[0].id
  else if (tab.value !== 'now' && !upcoming.value.some(x => x.id === tab.value)) tab.value = 'now'
}
onMounted(load)

// when a sale ends or a slot starts, reload so the lists move on
watch(ended, (isEnded) => { if (isEnded && (items.value.length || upcoming.value.length)) load() })
</script>

<style scoped>
.flash { background: white; border-radius: var(--radius-md); margin: 12px 0; }
.flash-head { display: flex; align-items: center; gap: 16px; padding: 16px 20px; border-bottom: 1px solid var(--color-border); }
.flash-title { margin: 0; font-size: 22px; font-style: italic; font-weight: 800; color: var(--color-primary); letter-spacing: 1px; }
.bolt { font-style: normal; }
.opens { font-size: 13px; color: var(--color-text-muted); margin-right: -8px; }
.countdown { display: flex; gap: 4px; }
.digit-box { background: #222; color: white; font-weight: 700; font-size: 15px; padding: 3px 6px; border-radius: 3px; font-variant-numeric: tabular-nums; }
.see-all { margin-left: auto; color: var(--color-primary); font-size: 14px; }

.slots { display: flex; overflow-x: auto; background: #3a3a3a; }
.slots button { flex: 0 0 auto; min-width: 140px; display: flex; flex-direction: column; align-items: center; gap: 1px; padding: 8px 16px;
  border: none; background: none; color: rgba(255, 255, 255, 0.75); cursor: pointer; }
.slots button b { font-size: 18px; color: white; }
.slots button span { font-size: 12px; }
.slots button.on { background: var(--color-primary); color: white; }

.flash-list { display: grid; grid-auto-flow: column; grid-auto-columns: 180px; gap: 4px; overflow-x: auto; padding: 12px 20px 20px; scrollbar-width: thin; }
.flash-item { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 8px; text-align: center; }
.flash-item:hover { box-shadow: 0 1px 8px rgba(0, 0, 0, 0.1); }
.img { position: relative; width: 100%; aspect-ratio: 1; background: #f2f2f2; }
.img img { width: 100%; height: 100%; object-fit: cover; }
.badge { position: absolute; top: 0; right: 0; background: #ffe97a; color: var(--color-primary); font-size: 12px; font-weight: 700; padding: 2px 5px; }
.price { color: var(--color-primary); font-size: 18px; font-weight: 600; }
.bar { position: relative; width: 100%; height: 16px; border-radius: 8px; background: #ffbda6; overflow: hidden; }
.bar.soon { background: #6fb76f; }
.bar-fill { position: absolute; inset: 0 auto 0 0; background: linear-gradient(90deg, #ff6f3c, var(--color-primary)); }
.bar-text { position: relative; font-size: 10px; font-weight: 700; color: white; text-transform: uppercase; line-height: 16px; }
.empty { grid-column: 1 / -1; color: var(--color-text-muted); font-size: 14px; padding: 20px 0; }
</style>
