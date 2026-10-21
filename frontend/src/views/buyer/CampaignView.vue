<template>
  <div>
    <AppHeader />
    <div v-if="!c" class="container state">{{ error || 'Đang tải...' }}</div>
    <div v-else class="campaign" :style="{ '--theme': c.themeColor }">
      <div class="hero" :class="{ image: c.bannerUrl }">
        <img v-if="c.bannerUrl" :src="c.bannerUrl" :alt="c.name" />
        <div v-else class="container hero-text">
          <h1>{{ c.name }}</h1>
          <p v-if="c.tagline">{{ c.tagline }}</p>
        </div>
      </div>

      <div class="container body">
        <div class="when">
          <span v-if="c.status === 'UPCOMING'">Chương trình bắt đầu sau</span>
          <span v-else-if="c.status === 'RUNNING'">Kết thúc sau</span>
          <span v-else>Chương trình đã kết thúc</span>
          <div v-if="c.status !== 'ENDED'" class="countdown"><span v-for="(p, i) in parts" :key="i">{{ p }}</span></div>
          <span class="range">{{ formatDateTime(c.startAt) }} – {{ formatDateTime(c.endAt) }}</span>
        </div>

        <section v-if="c.vouchers.length" class="block">
          <h2>🎟 Shoppi Voucher</h2>
          <div class="vouchers">
            <div v-for="v in c.vouchers" :key="v.id" class="voucher">
              <div class="v-left">SHOPPI</div>
              <div class="v-body">
                <b>{{ v.title }}</b>
                <span>{{ v.minOrder ? 'Đơn tối thiểu ' + formatVnd(v.minOrder) : 'Mọi đơn hàng' }}{{ v.newBuyerOnly ? ' · Khách hàng mới' : '' }}</span>
                <span class="faint">{{ new Date(v.startAt) > new Date() ? 'Bắt đầu ' + formatDateTime(v.startAt) : 'HSD: ' + formatDateTime(v.endAt) }}
                  <template v-if="v.usageLimit"> · còn {{ Math.max(0, v.usageLimit - v.usedCount) }} lượt</template></span>
              </div>
              <button type="button" class="btn sm" @click="copy(v.code)">{{ copied === v.code ? 'Đã sao chép' : v.code }}</button>
            </div>
          </div>
          <p class="hint">Nhập mã ở mục "Shoppi Voucher" khi thanh toán.</p>
        </section>

        <section v-for="s in c.flashSales" :key="s.id" class="block">
          <h2>⚡ {{ s.name }} <span class="slot">{{ slotLabel(s) }}</span></h2>
          <div v-if="!s.products.length" class="faint">Sản phẩm đang được Shoppi chọn lọc, quay lại sau nhé!</div>
          <div class="grid">
            <router-link v-for="x in s.products" :key="x.product.id" :to="`/products/${x.product.id}`" class="item">
              <div class="img"><img :src="x.product.thumbnailUrl || ph" :alt="x.product.name" loading="lazy" /><span class="off">-{{ x.discountPercent }}%</span></div>
              <div class="name">{{ x.product.name }}</div>
              <div class="prices"><b>{{ formatVnd(x.flashPrice) }}</b><s>{{ formatVnd(x.originalPrice) }}</s></div>
              <div class="tag">{{ s.status === 'RUNNING' ? (x.flashStock ? `Đã bán ${x.flashSold}/${x.flashStock}` : 'Đang diễn ra') : 'Sắp mở bán' }}</div>
            </router-link>
          </div>
        </section>

        <section v-if="c.description" class="block rules">
          <h2>Thể lệ chương trình</h2>
          <p>{{ c.description }}</p>
        </section>

        <div v-if="!c.vouchers.length && !c.flashSales.length" class="block faint">Ưu đãi của chương trình sẽ sớm được cập nhật.</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import { campaignPage } from '../../api/marketing'
import { useCountdown } from '../../composables/useCountdown'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const route = useRoute()
const c = ref(null)
const error = ref('')
const copied = ref('')
const ph = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="%23f0f0f0"/></svg>'

const { parts } = useCountdown(() => (c.value?.status === 'UPCOMING' ? c.value.startAt : c.value?.endAt))
const slotLabel = (s) => {
  const t = (x) => new Date(x).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  return `${t(s.startAt)} – ${t(s.endAt)} ${new Date(s.startAt).toLocaleDateString('vi-VN')}${s.status === 'RUNNING' ? ' · Đang diễn ra' : ''}`
}

async function copy(code) {
  try {
    await navigator.clipboard.writeText(code)
  } catch { /* clipboard blocked: the code is on the button anyway */ }
  copied.value = code
  setTimeout(() => { if (copied.value === code) copied.value = '' }, 2000)
}

async function load() {
  c.value = null
  error.value = ''
  try {
    c.value = (await campaignPage(route.params.slug)).data
    document.title = `${c.value.name} | Shoppi`
  } catch (e) {
    error.value = extractError(e)
  }
}
watch(() => route.params.slug, load, { immediate: true })
</script>

<style scoped>
.state { padding: 60px 0; text-align: center; color: var(--color-text-muted); }
.campaign { background: color-mix(in srgb, var(--theme) 8%, #f5f5f5); min-height: 100vh; padding-bottom: 40px; }
.hero { background: linear-gradient(120deg, var(--theme), color-mix(in srgb, var(--theme) 65%, #000)); color: white; }
.hero.image { background: var(--theme); }
.hero img { display: block; width: 100%; max-height: 360px; object-fit: cover; }
.hero-text { padding: 48px 0; }
.hero-text h1 { margin: 0; font-size: 40px; font-weight: 800; letter-spacing: 0.5px; }
.hero-text p { margin: 8px 0 0; font-size: 18px; opacity: 0.95; }
.body { padding-top: 16px; }
.when { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; background: white; border-radius: var(--radius-md); padding: 12px 16px; font-size: 14px; }
.countdown { display: flex; gap: 4px; }
.countdown span { background: var(--theme); color: white; font-weight: 700; padding: 3px 7px; border-radius: 3px; font-variant-numeric: tabular-nums; }
.range { margin-left: auto; color: var(--color-text-muted); font-size: 13px; }
.block { background: white; border-radius: var(--radius-md); padding: 16px; margin-top: 12px; }
.block h2 { margin: 0 0 12px; font-size: 18px; color: var(--theme); display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
.slot { font-size: 13px; color: var(--color-text-muted); font-weight: normal; }
.vouchers { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 10px; }
.voucher { display: flex; align-items: center; gap: 12px; border: 1px solid color-mix(in srgb, var(--theme) 35%, #fff); border-radius: 6px; overflow: hidden; padding-right: 12px; }
.v-left { align-self: stretch; width: 76px; display: flex; align-items: center; justify-content: center; background: var(--theme); color: white; font-weight: 800; font-size: 13px; }
.v-body { flex: 1; display: flex; flex-direction: column; gap: 2px; padding: 10px 0; font-size: 13px; }
.v-body b { font-size: 15px; color: var(--theme); }
.voucher .btn { background: var(--theme); border-color: var(--theme); font-family: ui-monospace, monospace; }
.hint, .faint { font-size: 12px; color: var(--color-text-faint); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 10px; }
.item { display: flex; flex-direction: column; gap: 6px; border: 1px solid var(--color-border); border-radius: 6px; overflow: hidden; color: var(--color-text); padding-bottom: 10px; }
.item:hover { box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1); }
.img { position: relative; aspect-ratio: 1; background: #f2f2f2; }
.img img { width: 100%; height: 100%; object-fit: cover; }
.off { position: absolute; top: 0; right: 0; background: #ffe97a; color: var(--color-primary); font-weight: 700; font-size: 12px; padding: 2px 6px; }
.name { padding: 0 8px; font-size: 13px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 34px; }
.prices { padding: 0 8px; display: flex; gap: 6px; align-items: baseline; }
.prices b { color: var(--color-primary); font-size: 16px; }
.prices s { color: var(--color-text-faint); font-size: 12px; }
.tag { margin: 0 8px; font-size: 11px; text-align: center; background: color-mix(in srgb, var(--theme) 15%, #fff); color: var(--theme); border-radius: 8px; padding: 2px 0; font-weight: 600; }
.rules p { white-space: pre-wrap; font-size: 14px; line-height: 1.6; margin: 0; }
@media (max-width: 600px) { .hero-text h1 { font-size: 28px; } .range { margin-left: 0; } }
</style>
