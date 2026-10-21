<template>
  <div class="report">
    <AnalyticsToolbar />
    <div v-if="error" class="sc-card sc-empty">{{ error }}</div>

    <template v-else>
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Báo Cáo Đánh Giá</h2>
          <span class="sc-card-sub">Đánh giá sản phẩm người mua viết trong kỳ</span>
          <router-link :to="{ name: 'seller-ai-reviews' }" class="sc-link">✨ AI tóm tắt khen/chê ›</router-link>
        </div>
        <div class="kpis">
          <KpiCard label="Đánh giá mới" :loading="!data" :value="s.count" :current="s.count" :previous="p.count" :previous-text="p.count" />
          <KpiCard label="Điểm trung bình" :loading="!data" points points-unit="điểm"
                   :value="s.count ? '★ ' + s.average.toFixed(1) : '—'" :current="s.average" :previous="p.average" :previous-text="p.average" />
          <KpiCard label="Hài lòng (4–5★)" :loading="!data" points
                   :value="percent(s.positiveRate)" :current="s.positiveRate" :previous="p.positiveRate" :previous-text="percent(p.positiveRate)" />
          <KpiCard label="Tiêu cực (1–2★)" :loading="!data" invert
                   :value="s.negative" :current="s.negative" :previous="p.negative" :previous-text="p.negative" />
          <KpiCard label="Tỷ lệ phản hồi" hint="Đánh giá đã được Shop trả lời / tổng đánh giá" :loading="!data" points
                   :value="percent(s.replyRate)" :current="s.replyRate" :previous="p.replyRate" :previous-text="percent(p.replyRate)" />
          <KpiCard label="Có ảnh / video" :loading="!data" :value="s.withMedia ?? '–'" />
        </div>
        <div v-if="s.unrepliedNegative" class="alert">
          ⚠ Có <strong>{{ s.unrepliedNegative }}</strong> đánh giá tiêu cực chưa được phản hồi.
          <button type="button" class="link" @click="setListFilter('negative')">Phản hồi ngay ›</button>
        </div>
      </section>

      <div class="two-col">
        <section class="sc-card">
          <div class="sc-card-head"><h2 class="sc-card-title">Phân Bố Số Sao</h2></div>
          <div class="dist">
            <button v-for="[star, count] in distEntries" :key="star" type="button" class="dist-row" @click="setListFilter(String(star))">
              <span class="stars">{{ star }} ★</span>
              <span class="dist-bar"><span :style="{ width: (count / distMax) * 100 + '%' }" :class="{ neg: star <= 2 }"></span></span>
              <span class="dist-num">{{ count }}</span>
            </button>
          </div>
          <div class="service">
            <div>
              <div class="text-faint small">Đánh giá dịch vụ Shop</div>
              <strong>{{ svc.shopRating ? '★ ' + svc.shopRating.toFixed(1) : '—' }}</strong>
              <span class="text-faint small"> ({{ svc.shopRatingCount || 0 }} lượt)</span>
            </div>
            <div>
              <div class="text-faint small">Đánh giá vận chuyển</div>
              <strong>{{ svc.shippingRating ? '★ ' + svc.shippingRating.toFixed(1) : '—' }}</strong>
              <span class="text-faint small"> ({{ svc.shippingRatingCount || 0 }} lượt)</span>
            </div>
          </div>
        </section>

        <section class="sc-card">
          <div class="sc-card-head">
            <h2 class="sc-card-title">Xu Hướng Đánh Giá</h2>
            <span class="sc-card-sub">Theo {{ granLabel }}</span>
          </div>
          <TrendChart v-if="data" :labels="(data.series || []).map(b => b.label)" :series="chartSeries" :height="220"
                      :right-tick-format="(v) => v + '★'" />
        </section>
      </div>

      <section v-if="data?.products.length" class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Đánh Giá Theo Sản Phẩm</h2>
          <span class="sc-card-sub">Điểm thấp nhất ở trên</span>
        </div>
        <div class="sc-table-wrap">
          <table class="sc-table">
            <thead><tr><th>Sản phẩm</th><th class="r">Đánh giá</th><th class="r">Điểm TB</th><th class="r">Tiêu cực</th><th class="r">Chưa phản hồi</th><th></th></tr></thead>
            <tbody>
              <tr v-for="pr in data.products.slice(0, productLimit)" :key="pr.id">
                <td>
                  <div class="prod">
                    <img :src="pr.thumbnailUrl || placeholder" alt="" />
                    <router-link :to="`/products/${pr.id}`" target="_blank" class="name">{{ pr.name }}</router-link>
                  </div>
                </td>
                <td class="r">{{ pr.count }}</td>
                <td class="r"><span :class="{ bad: pr.average < 3, good: pr.average >= 4.5 }">★ {{ pr.average.toFixed(1) }}</span></td>
                <td class="r"><span :class="{ bad: pr.negative > 0 }">{{ pr.negative }}</span></td>
                <td class="r">{{ pr.unreplied }}</td>
                <td class="r"><button type="button" class="link" @click="filterProduct(pr)">Xem đánh giá</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="data.products.length > productLimit" class="more">
          <button type="button" class="btn ghost sm" @click="productLimit += 10">Xem thêm</button>
        </div>
      </section>

      <!-- ===== all reviews of the shop, with replies ===== -->
      <section ref="listCard" class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Tất Cả Đánh Giá</h2>
          <span class="sc-card-sub">Mọi thời điểm · trả lời để người mua thấy Shop quan tâm</span>
        </div>
        <div class="sc-toolbar">
          <div class="chips">
            <button v-for="f in LIST_FILTERS" :key="f.key" type="button" :class="{ active: listFilter === f.key }" @click="setListFilter(f.key)">{{ f.label }}</button>
          </div>
          <span v-if="productFilter" class="product-chip">
            {{ productFilter.name }} <button type="button" title="Bỏ lọc" @click="productFilter = null; loadList(0)">✕</button>
          </span>
        </div>
        <!-- "Phản hồi hàng loạt": one template to the selected unanswered reviews -->
        <div v-if="unrepliedOnPage.length" class="bulk">
          <label class="bulk-all">
            <input type="checkbox" :checked="allSelected" @change="toggleAll" />
            Chọn tất cả đánh giá chưa phản hồi trên trang ({{ unrepliedOnPage.length }})
          </label>
          <template v-if="selected.length">
            <textarea v-model="bulkDraft" rows="2" maxlength="255" placeholder="Nội dung phản hồi chung, VD: Cảm ơn {ten_khach} đã ủng hộ {ten_shop} ❤️"></textarea>
            <div class="reply-actions">
              <TemplatePicker kind="REVIEW" raw :context="{ shop: shopName }" @pick="(t) => (bulkDraft = t)" />
              <span class="text-faint small">{ten_khach} và {ten_san_pham} được điền riêng cho từng người</span>
              <span v-if="bulkMsg" class="ok">{{ bulkMsg }}</span>
              <span v-if="replyError && !editing" class="err">{{ replyError }}</span>
              <button type="button" class="btn sm" :disabled="saving || !bulkDraft.trim()" @click="onBulkReply">
                {{ saving ? 'Đang gửi...' : `Phản hồi ${selected.length} đánh giá` }}
              </button>
            </div>
          </template>
        </div>
        <div v-if="list && !list.items.length" class="sc-empty">Không có đánh giá nào</div>
        <div v-for="r in list?.items || []" :key="r.id" class="review">
          <div class="rv-head">
            <input v-if="!r.sellerReply" v-model="selected" type="checkbox" :value="r.id" class="pick" title="Chọn để phản hồi hàng loạt" />
            <img :src="r.thumbnailUrl || placeholder" alt="" />
            <div class="rv-meta">
              <router-link :to="`/products/${r.productId}`" target="_blank" class="name">{{ r.productName }}</router-link>
              <div class="text-faint small">
                {{ r.buyerName }} · {{ formatDateTime(r.createdAt) }}<template v-if="r.variantName"> · Phân loại: {{ r.variantName }}</template>
                <template v-if="r.orderId"> · <router-link :to="{ name: 'seller-order-detail', params: { id: r.orderId } }" class="order-link">{{ r.orderCode }}</router-link></template>
              </div>
            </div>
            <span class="rv-stars" :class="{ neg: r.rating <= 2 }">{{ '★'.repeat(r.rating) }}<span class="off">{{ '★'.repeat(5 - r.rating) }}</span></span>
          </div>
          <p v-if="r.comment" class="rv-comment">{{ r.comment }}</p>
          <p v-else class="rv-comment text-faint">(Không có nội dung)</p>
          <div v-if="r.imageUrls.length || r.videoUrl" class="rv-media">
            <a v-for="u in r.imageUrls" :key="u" :href="u" target="_blank"><img :src="u" alt="" /></a>
            <video v-if="r.videoUrl" :src="r.videoUrl" controls preload="metadata"></video>
          </div>

          <div v-if="editing === r.id" class="reply-form">
            <textarea v-model="draft" rows="3" maxlength="255" placeholder="Cảm ơn bạn đã ủng hộ Shop..."></textarea>
            <div class="reply-actions">
              <TemplatePicker kind="REVIEW" :context="{ customer: r.buyerName, shop: shopName, product: r.productName }" @pick="(t) => (draft = t)" />
              <span class="text-faint small">{{ draft.length }}/255</span>
              <span v-if="replyError" class="err">{{ replyError }}</span>
              <button type="button" class="btn ghost sm" @click="editing = null">Hủy</button>
              <button type="button" class="btn sm" :disabled="saving || !draft.trim()" @click="saveReply(r)">{{ saving ? 'Đang gửi...' : 'Gửi phản hồi' }}</button>
            </div>
          </div>
          <div v-else-if="r.sellerReply" class="reply">
            <div class="reply-h">Phản hồi của Shop <button type="button" class="link" @click="startReply(r)">Sửa</button></div>
            {{ r.sellerReply }}
          </div>
          <button v-else type="button" class="btn sm secondary reply-btn" @click="startReply(r)">Trả lời</button>
        </div>
        <PaginationBar v-if="list && list.totalPages > 1" :page="list.page" :total-pages="list.totalPages" @change="loadList" />
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import AnalyticsToolbar from '../../../components/analytics/AnalyticsToolbar.vue'
import KpiCard from '../../../components/analytics/KpiCard.vue'
import TrendChart from '../../../components/analytics/TrendChart.vue'
import PaginationBar from '../../../components/PaginationBar.vue'
import { reviewReport, shopReviews, replyReview } from '../../../api/analytics'
import { bulkReplyReviews } from '../../../api/customerCare'
import { getMyShop } from '../../../api/shop'
import TemplatePicker from '../../../components/care/TemplatePicker.vue'
import { useAnalyticsRange, percent } from '../../../composables/useAnalyticsRange'
import { formatDateTime } from '../../../utils/format'
import { extractError } from '../../../utils/apiError'

const { params } = useAnalyticsRange()
const data = ref(null)
const error = ref('')
const productLimit = ref(10)
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const s = computed(() => data.value?.summary || {})
const p = computed(() => data.value?.previous || {})
const svc = computed(() => data.value?.service || {})
const granLabel = computed(() => ({ day: 'ngày', week: 'tuần', month: 'tháng' })[params.value.granularity])
// [[5, count], ..., [1, count]] (an object with numeric keys would iterate 1 → 5)
const distEntries = computed(() => [5, 4, 3, 2, 1].map(k => [k, Number(data.value?.distribution?.[k] || 0)]))
const distMax = computed(() => Math.max(1, ...distEntries.value.map(([, c]) => c)))

const chartSeries = computed(() => {
  const series = data.value?.series || []
  return [
    { name: 'Số đánh giá', values: series.map(b => b.count), color: '#2673dd', type: 'bar', integer: true },
    { name: 'Tiêu cực (1–2★)', values: series.map(b => b.negative), color: '#d33', type: 'bar', integer: true },
    { name: 'Điểm trung bình', values: series.map(b => (b.count ? b.average : null)), color: '#f5a623', type: 'line', axis: 'right', max: 5,
      format: (v) => (v ? '★ ' + Number(v).toFixed(1) : '—') }
  ]
})

async function load() {
  error.value = ''
  try {
    const { data: d } = await reviewReport(params.value)
    data.value = d
  } catch (e) {
    error.value = extractError(e)
  }
}
watch(params, load, { immediate: true })

// ---- review list ----
const LIST_FILTERS = [
  { key: 'all', label: 'Tất cả' },
  { key: 'unreplied', label: 'Chưa phản hồi' },
  { key: 'negative', label: 'Tiêu cực chưa phản hồi' },
  { key: '5', label: '5 sao' }, { key: '4', label: '4 sao' }, { key: '3', label: '3 sao' }, { key: '2', label: '2 sao' }, { key: '1', label: '1 sao' }
]
const list = ref(null)
const listFilter = ref('all')
const productFilter = ref(null)
const listCard = ref(null)
const editing = ref(null)
const draft = ref('')
const saving = ref(false)
const replyError = ref('')

async function loadList(page = 0) {
  const f = listFilter.value
  const query = { page, size: 10, productId: productFilter.value?.id }
  if (f === 'unreplied' || f === 'negative') query.replied = false
  if (f === 'negative') query.maxRating = 2
  if (/^[1-5]$/.test(f)) query.rating = Number(f)
  list.value = (await shopReviews(query)).data
}
loadList()

function setListFilter(key) {
  listFilter.value = key
  loadList(0)
  nextTick(() => listCard.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function filterProduct(pr) {
  productFilter.value = pr
  setListFilter('all')
}

function startReply(r) {
  editing.value = r.id
  draft.value = r.sellerReply || ''
  replyError.value = ''
}

// ---- bulk reply ----
const selected = ref([])
const bulkDraft = ref('')
const bulkMsg = ref('')
const shopName = ref('')
const unrepliedOnPage = computed(() => (list.value?.items || []).filter(r => !r.sellerReply))
const allSelected = computed(() => unrepliedOnPage.value.length > 0 && unrepliedOnPage.value.every(r => selected.value.includes(r.id)))
getMyShop().then(({ data }) => { shopName.value = data.name }).catch(() => {})

function toggleAll() {
  selected.value = allSelected.value ? [] : unrepliedOnPage.value.map(r => r.id)
}

async function onBulkReply() {
  saving.value = true
  replyError.value = ''
  bulkMsg.value = ''
  try {
    const { data } = await bulkReplyReviews(selected.value, bulkDraft.value)
    bulkMsg.value = `Đã phản hồi ${data.replied} đánh giá`
    selected.value = []
    bulkDraft.value = ''
    await loadList(list.value.page)
    load()
  } catch (e) {
    replyError.value = extractError(e)
  } finally {
    saving.value = false
  }
}

async function saveReply(r) {
  saving.value = true
  replyError.value = ''
  try {
    const { data: updated } = await replyReview(r.id, draft.value)
    Object.assign(r, updated)
    editing.value = null
    if (listFilter.value === 'unreplied' || listFilter.value === 'negative') list.value.items = list.value.items.filter(x => x.id !== r.id)
    load()
  } catch (e) {
    replyError.value = extractError(e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }
.alert { margin-top: 12px; background: #fdecea; color: #b42318; padding: 8px 12px; border-radius: 4px; font-size: 14px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 4px; }
.two-col { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); gap: 16px; align-items: stretch; margin-bottom: 16px; }
.two-col .sc-card { margin-bottom: 0; }
.dist-row { display: flex; align-items: center; gap: 10px; width: 100%; background: none; border: none; padding: 5px 0; cursor: pointer; font-family: inherit; font-size: 14px; }
.dist-row:hover .stars { color: var(--color-primary); }
.stars { width: 36px; text-align: left; color: #f5a623; white-space: nowrap; }
.dist-bar { flex: 1; height: 10px; background: #f2f2f2; border-radius: 999px; overflow: hidden; }
.dist-bar span { display: block; height: 100%; background: #f5a623; border-radius: 999px; }
.dist-bar span.neg { background: #d33; }
.dist-num { width: 36px; text-align: right; color: var(--color-text-muted); }
.service { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--color-border); }
.small { font-size: 12px; }
.prod { display: flex; align-items: center; gap: 10px; max-width: 420px; min-width: 0; }
.prod img, .rv-head img { width: 40px; height: 40px; object-fit: cover; border-radius: 2px; flex-shrink: 0; }
.name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.name:hover { color: var(--color-primary); }
.r { text-align: right; white-space: nowrap; }
.sc-table td { padding: 10px 12px; }
.good { color: #1e9e4a; font-weight: 500; }
.bad { color: #d33; font-weight: 500; }
.more { text-align: center; margin-top: 12px; }
.chips { display: flex; gap: 6px; flex-wrap: wrap; }
.chips button { background: white; border: 1px solid #ddd; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chips button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.product-chip { background: #f0f5ff; color: #2673dd; border-radius: 999px; padding: 4px 10px; font-size: 13px; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-chip button { background: none; border: none; color: inherit; cursor: pointer; }
.bulk { background: #fafafa; border: 1px solid var(--color-border); border-radius: 4px; padding: 10px 12px; margin-bottom: 6px; }
.bulk-all { display: flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; }
.bulk-all input, .pick { width: 16px; height: 16px; margin: 0; accent-color: var(--color-primary); flex-shrink: 0; }
.bulk textarea { margin: 8px 0 6px; font-family: inherit; width: 100%; }
.ok { color: #1e9e4a; font-size: 13px; }
.review { padding: 14px 0; border-bottom: 1px solid var(--color-border); }
.review:last-of-type { border-bottom: none; }
.rv-head { display: flex; align-items: center; gap: 10px; }
.rv-meta { flex: 1; min-width: 0; }
.order-link { color: #2673dd; }
.rv-stars { color: #f5a623; font-size: 15px; white-space: nowrap; letter-spacing: 1px; }
.rv-stars.neg { color: #d33; }
.rv-stars .off { color: #ddd; }
.rv-comment { margin: 8px 0 0 50px; font-size: 14px; line-height: 1.5; white-space: pre-line; }
.rv-media { display: flex; gap: 6px; margin: 8px 0 0 50px; flex-wrap: wrap; }
.rv-media img, .rv-media video { width: 72px; height: 72px; object-fit: cover; border-radius: 2px; }
.reply { margin: 10px 0 0 50px; background: #f8f8f8; padding: 8px 12px; border-radius: 4px; font-size: 14px; }
.reply-h { font-size: 12px; color: var(--color-text-muted); margin-bottom: 4px; }
.reply-btn { margin: 10px 0 0 50px; }
.reply-form { margin: 10px 0 0 50px; }
.reply-form textarea { width: 100%; margin: 0; font-family: inherit; }
.reply-actions { display: flex; gap: 8px; align-items: center; justify-content: flex-end; margin-top: 6px; }
.reply-actions .small { margin-right: auto; }
.err { color: #d33; font-size: 13px; }
@media (max-width: 1200px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 1000px) { .two-col { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .kpis { grid-template-columns: 1fr 1fr; } .rv-comment, .rv-media, .reply, .reply-btn, .reply-form { margin-left: 0; } }
</style>
