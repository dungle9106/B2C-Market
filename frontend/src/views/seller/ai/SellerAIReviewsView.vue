<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">✨ AI Tóm Tắt & Phân Tích Đánh Giá</h2>
        <span class="sc-card-sub">Khách khen gì, chê gì – tự động gom theo chủ đề từ nội dung đánh giá</span>
      </div>
      <div class="sc-toolbar">
        <select v-model="productId" @change="load">
          <option :value="null">Tất cả sản phẩm</option>
          <option v-for="p in products" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
        <div class="days">
          <button v-for="d in [30, 90, 180, 365]" :key="d" type="button" :class="{ active: days === d }" @click="days = d; load()">{{ d }} ngày</button>
        </div>
        <span class="spacer"></span>
        <router-link :to="{ name: 'seller-analytics-reviews' }" class="sc-link">Trả lời đánh giá ›</router-link>
      </div>

      <div v-if="loading" class="sc-empty">AI đang đọc các đánh giá...</div>
      <div v-else-if="error" class="sc-empty">{{ error }}</div>
      <template v-else-if="data">
        <div class="summary">
          <div class="sum-icon">🤖</div>
          <div>
            <p>{{ data.summary }}</p>
            <span class="src">{{ data.source === 'ai' ? 'Viết bởi mô hình AI' : 'Phân tích tự động theo chủ đề' }}</span>
          </div>
        </div>
        <div class="stats">
          <div><b>{{ data.reviews }}</b><span>đánh giá</span></div>
          <div><b>{{ data.withComment }}</b><span>có nội dung</span></div>
          <div><b>{{ data.reviews ? '★ ' + data.average : '—' }}</b><span>điểm trung bình</span></div>
          <div class="sent">
            <span class="bar"><i class="p" :style="{ flex: data.sentiment.positive }"></i><i class="n" :style="{ flex: data.sentiment.neutral }"></i><i class="x" :style="{ flex: data.sentiment.negative }"></i></span>
            <span>{{ data.sentiment.positive }} hài lòng · {{ data.sentiment.neutral }} trung lập · {{ data.sentiment.negative }} không hài lòng</span>
          </div>
        </div>
      </template>
    </section>

    <div v-if="data && data.reviews" class="two-col">
      <section class="sc-card">
        <h3 class="block-h good">👍 Điểm được khen</h3>
        <div v-if="!data.praises.length" class="faint">Chưa có lời khen theo chủ đề cụ thể</div>
        <div v-for="a in data.praises" :key="a.key" class="theme">
          <div class="theme-head"><strong>{{ a.label }}</strong><span class="count good">{{ a.positive }} lượt khen</span></div>
          <p v-for="q in a.praise" :key="q" class="quote">“{{ q }}”</p>
        </div>
      </section>
      <section class="sc-card">
        <h3 class="block-h bad">👎 Điểm bị chê</h3>
        <div v-if="!data.complaints.length" class="faint">🎉 Chưa có phàn nàn nào đáng kể</div>
        <div v-for="a in data.complaints" :key="a.key" class="theme">
          <div class="theme-head"><strong>{{ a.label }}</strong><span class="count bad">{{ a.negative }} lượt chê</span></div>
          <p v-for="q in a.complaints" :key="q" class="quote">“{{ q }}”</p>
          <p class="action">💡 {{ a.action }}</p>
        </div>
      </section>
    </div>

    <section v-if="data && data.aspects.length" class="sc-card">
      <h3 class="block-h">Mức độ hài lòng theo chủ đề</h3>
      <div v-for="a in data.aspects" :key="a.key" class="aspect">
        <span class="a-name">{{ a.label }}</span>
        <span class="a-bar">
          <i class="p" :style="{ width: (a.positive / maxMentions) * 100 + '%' }"></i>
          <i class="x" :style="{ width: (a.negative / maxMentions) * 100 + '%' }"></i>
        </span>
        <span class="a-num">{{ a.mentions }} lượt · <span class="good">{{ a.positiveRate }}% tích cực</span></span>
      </div>
    </section>

    <div v-if="data && (data.keywords.length || data.products.length)" class="two-col">
      <section v-if="data.keywords.length" class="sc-card">
        <h3 class="block-h">Từ khóa khách hay nhắc</h3>
        <div class="cloud">
          <span v-for="k in data.keywords" :key="k.word" :style="{ fontSize: 12 + Math.min(10, k.count * 2) + 'px' }">{{ k.word }} <sup>{{ k.count }}</sup></span>
        </div>
      </section>
      <section v-if="data.products.length && !productId" class="sc-card">
        <h3 class="block-h">Sản phẩm bị phàn nàn nhiều nhất</h3>
        <div v-for="p in data.products" :key="p.id" class="prod">
          <a href="#" class="prod-name" @click.prevent="productId = p.id; load()">{{ p.name }}</a>
          <span class="faint">★ {{ p.average }} · {{ p.reviews }} đánh giá</span>
          <span class="bad">{{ p.negativeMentions }} lượt chê<template v-if="p.topComplaint"> ({{ p.topComplaint.toLowerCase() }})</template></span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { reviewInsights } from '../../../api/ai'
import { myProducts } from '../../../api/products'
import { extractError } from '../../../utils/apiError'

const route = useRoute()
const data = ref(null)
const products = ref([])
const productId = ref(route.query.productId ? Number(route.query.productId) : null)
const days = ref(90)
const loading = ref(false)
const error = ref('')
const maxMentions = computed(() => Math.max(1, ...(data.value?.aspects || []).map(a => a.mentions)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = (await reviewInsights({ productId: productId.value, days: days.value })).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  myProducts().then(({ data: d }) => { products.value = d }).catch(() => {})
})
</script>

<style scoped>
.sc-toolbar select { width: auto; max-width: 320px; margin: 0; }
.days { display: flex; }
.days button { background: white; border: 1px solid #ddd; padding: 6px 10px; font-size: 13px; cursor: pointer; margin-left: -1px; }
.days button:first-child { border-radius: 4px 0 0 4px; margin-left: 0; }
.days button:last-child { border-radius: 0 4px 4px 0; }
.days button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); position: relative; }
.summary { display: flex; gap: 12px; background: linear-gradient(135deg, #faf8ff, #fff); border: 1px solid #e0d8ff; border-radius: 8px; padding: 14px 16px; }
.sum-icon { font-size: 28px; }
.summary p { margin: 0 0 6px; line-height: 1.65; font-size: 14px; }
.src { font-size: 11px; color: #6941c6; }
.stats { display: grid; grid-template-columns: 1fr 1fr 1fr 2.4fr; gap: 10px; margin-top: 14px; }
.stats > div { border: 1px solid var(--color-border); border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; font-size: 12px; color: var(--color-text-muted); }
.stats b { font-size: 20px; color: var(--color-text); font-weight: 500; }
.sent .bar { display: flex; height: 10px; border-radius: 999px; overflow: hidden; background: #eee; margin: 6px 0; }
.sent .bar i, .a-bar i { display: block; height: 100%; }
.p { background: #1e9e4a; }
.n { background: #f5a623; }
.x { background: #d33; }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: stretch; margin-bottom: 16px; }
.two-col .sc-card { margin-bottom: 0; }
.block-h { font-size: 16px; font-weight: 600; margin: 0 0 12px; }
.good { color: #1e9e4a; }
.bad { color: #d33; }
.faint { color: var(--color-text-faint); font-size: 13px; }
.theme { padding: 10px 0; border-bottom: 1px dashed var(--color-border); }
.theme:last-child { border-bottom: none; }
.theme-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.count { font-size: 12px; }
.quote { margin: 4px 0; font-size: 13px; color: var(--color-text-muted); font-style: italic; }
.action { margin: 6px 0 0; font-size: 13px; background: #fff8e6; padding: 6px 10px; border-radius: 4px; }
.aspect { display: grid; grid-template-columns: 180px 1fr 190px; align-items: center; gap: 12px; padding: 6px 0; font-size: 13px; }
.a-bar { display: flex; height: 12px; background: #f2f2f2; border-radius: 999px; overflow: hidden; }
.a-num { color: var(--color-text-muted); font-size: 12px; text-align: right; }
.cloud { display: flex; flex-wrap: wrap; gap: 8px 14px; align-items: baseline; }
.cloud span { color: #6941c6; }
.cloud sup { color: var(--color-text-faint); font-size: 10px; }
.prod { display: flex; align-items: center; gap: 10px; padding: 7px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
.prod-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #2673dd; }
@media (max-width: 1000px) { .two-col { grid-template-columns: 1fr; } .stats { grid-template-columns: 1fr 1fr; } .aspect { grid-template-columns: 120px 1fr; } .a-num { display: none; } }
</style>
