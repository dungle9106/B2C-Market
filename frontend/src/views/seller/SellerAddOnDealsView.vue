<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Mua Kèm Deal Sốc</h2>
      <span class="sc-card-sub">Khách mua sản phẩm chính được mua kèm sản phẩm khác với giá sốc, hoặc nhận quà khi mua đủ số tiền</span>
      <button type="button" class="btn new" @click="openForm(null)">+ Tạo chương trình</button>
    </div>

    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="!deals.length" class="sc-empty">Chưa có chương trình nào. Ví dụ: mua áo khoác được mua kèm mũ chỉ ₫49.000, hoặc mua từ ₫300.000 tặng túi vải.</div>
    <div v-else class="list">
      <article v-for="d in deals" :key="d.id" class="deal">
        <div class="deal-main">
          <div class="deal-head">
            <span class="kind" :class="d.type">{{ d.type === 'GIFT' ? '🎁 Mua để nhận quà' : '🔥 Mua kèm deal sốc' }}</span>
            <strong>{{ d.name }}</strong>
            <span class="state" :class="d.status">{{ STATUS[d.status] }}</span>
          </div>
          <div class="small">Sản phẩm chính ({{ d.mainProductCount }}): {{ d.mainProductNames.join(', ') }}{{ d.mainProductCount > d.mainProductNames.length ? '...' : '' }}</div>
          <div class="items">
            <span v-for="it in d.items" :key="it.productId" class="item">
              {{ it.productName }}<template v-if="d.type === 'ADDON'"> · {{ formatVnd(it.dealPrice) }}</template><template v-else> · Miễn phí</template>
            </span>
          </div>
          <div class="small text-faint">
            {{ formatDateTime(d.startAt) }} → {{ formatDateTime(d.endAt) }} · tối đa {{ d.purchaseLimit }} mỗi sản phẩm / đơn
            <template v-if="d.type === 'GIFT' && d.minSpend"> · đơn sản phẩm chính từ {{ formatVnd(d.minSpend) }}</template>
          </div>
        </div>
        <div v-if="d.status !== 'ENDED'" class="deal-actions">
          <button type="button" class="link" @click="openForm(d)">Sửa</button>
          <button type="button" class="link" @click="onToggle(d)">{{ d.active ? 'Tạm dừng' : 'Kích hoạt' }}</button>
          <button type="button" class="link danger" @click="onEnd(d)">Kết thúc</button>
        </div>
      </article>
    </div>

    <BaseModal v-if="editing" :title="editing.id ? 'Sửa chương trình' : 'Tạo chương trình Mua Kèm Deal Sốc'" :width="720" @close="editing = null">
      <form id="deal-form" class="form" novalidate @submit.prevent="onSave">
        <div class="types">
          <label class="type" :class="{ on: form.type === 'ADDON' }"><input v-model="form.type" type="radio" value="ADDON" />
            <strong>🔥 Mua kèm deal sốc</strong><span>Mua sản phẩm chính → sản phẩm mua kèm có giá sốc</span></label>
          <label class="type" :class="{ on: form.type === 'GIFT' }"><input v-model="form.type" type="radio" value="GIFT" />
            <strong>🎁 Mua để nhận quà</strong><span>Mua sản phẩm chính đủ số tiền → tặng quà miễn phí</span></label>
        </div>
        <label>Tên chương trình <span class="req">*</span></label>
        <input v-model.trim="form.name" maxlength="100" placeholder="VD: Mua áo khoác, mua kèm mũ giá sốc" />
        <div class="row">
          <div><label>Bắt đầu</label><input v-model="form.startAt" type="datetime-local" /></div>
          <div><label>Kết thúc</label><input v-model="form.endAt" type="datetime-local" /></div>
        </div>
        <div class="row">
          <div><label>Giới hạn mỗi đơn <span class="hint">(số lượng mỗi sản phẩm mua kèm / quà)</span></label>
            <input v-model.number="form.purchaseLimit" type="number" min="1" max="10" /></div>
          <div v-if="form.type === 'GIFT'"><label>Giá trị sản phẩm chính tối thiểu</label>
            <input v-model.number="form.minSpend" type="number" min="0" step="1000" placeholder="0 = mua bất kỳ" /></div>
        </div>

        <label class="mt">1. Sản phẩm chính <span class="req">*</span> <span class="hint">(khách mua một trong các sản phẩm này)</span></label>
        <input v-model.trim="filterMain" placeholder="Lọc sản phẩm" />
        <div class="picker">
          <label v-for="p in pick(filterMain, form.items.map(i => i.productId))" :key="p.id" class="pick">
            <input v-model="form.mainProductIds" type="checkbox" :value="p.id" />
            <img :src="p.thumbnailUrl || placeholder" alt="" /><span>{{ p.name }}</span><span class="price">{{ formatVnd(p.price) }}</span>
          </label>
        </div>
        <p class="small">Đã chọn {{ form.mainProductIds.length }} sản phẩm chính</p>

        <label class="mt">2. {{ form.type === 'GIFT' ? 'Quà tặng' : 'Sản phẩm mua kèm và giá sốc' }} <span class="req">*</span></label>
        <input v-model.trim="filterItem" placeholder="Lọc sản phẩm" />
        <div class="picker">
          <label v-for="p in pick(filterItem, form.mainProductIds)" :key="p.id" class="pick">
            <input type="checkbox" :checked="form.items.some(i => i.productId === p.id)" @change="toggleItem(p)" />
            <img :src="p.thumbnailUrl || placeholder" alt="" /><span>{{ p.name }}</span><span class="price">{{ formatVnd(p.price) }}</span>
          </label>
        </div>
        <table v-if="form.items.length" class="items-table">
          <tr v-for="it in form.items" :key="it.productId">
            <td>{{ nameOf(it.productId) }}</td>
            <td class="price">{{ formatVnd(priceOf(it.productId)) }}</td>
            <td v-if="form.type === 'ADDON'">Giá mua kèm <input v-model.number="it.dealPrice" type="number" min="1000" step="1000" /></td>
            <td v-else class="free">Miễn phí</td>
          </tr>
        </table>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn ghost" @click="editing = null">Hủy</button>
        <button type="submit" form="deal-form" class="btn" :disabled="busy">{{ busy ? 'Đang lưu...' : 'Lưu chương trình' }}</button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import { myAddOnDeals, createAddOnDeal, updateAddOnDeal, toggleAddOnDeal, endAddOnDeal } from '../../api/marketing'
import { myProducts } from '../../api/products'
import { formatVnd, formatDateTime, toLocalInput } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const STATUS = { RUNNING: 'Đang diễn ra', UPCOMING: 'Sắp diễn ra', PAUSED: 'Tạm dừng', ENDED: 'Đã kết thúc' }
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="%23f0f0f0"/></svg>'

const deals = ref([])
const products = ref([])
const loading = ref(true)
const editing = ref(null)
const form = ref(null)
const filterMain = ref('')
const filterItem = ref('')
const busy = ref(false)
const error = ref('')

// products for a picker: active ones matching the filter, minus those already used on the other side
function pick(q, exclude) {
  const f = q.toLowerCase()
  return products.value.filter(p => p.active && !exclude.includes(p.id) && (!f || p.name.toLowerCase().includes(f)))
}
const nameOf = (id) => products.value.find(p => p.id === id)?.name || ''
const priceOf = (id) => products.value.find(p => p.id === id)?.price || 0

function toggleItem(p) {
  const i = form.value.items.findIndex(x => x.productId === p.id)
  if (i >= 0) form.value.items.splice(i, 1)
  else form.value.items.push({ productId: p.id, dealPrice: Math.max(1000, Math.round(Number(p.price) * 0.5 / 1000) * 1000) })
}

async function load() {
  loading.value = true
  try {
    deals.value = (await myAddOnDeals()).data
  } finally {
    loading.value = false
  }
}

async function openForm(d) {
  error.value = ''
  filterMain.value = ''
  filterItem.value = ''
  if (!products.value.length) products.value = (await myProducts()).data
  const now = new Date()
  form.value = d
    ? { name: d.name, type: d.type, startAt: toLocalInput(d.startAt), endAt: toLocalInput(d.endAt), purchaseLimit: d.purchaseLimit,
        minSpend: d.minSpend ? Number(d.minSpend) : null, mainProductIds: [...d.mainProductIds],
        items: d.items.map(i => ({ productId: i.productId, dealPrice: i.dealPrice ? Number(i.dealPrice) : null })) }
    : { name: '', type: 'ADDON', startAt: toLocalInput(now), endAt: toLocalInput(new Date(now.getTime() + 14 * 86400000)),
        purchaseLimit: 1, minSpend: null, mainProductIds: [], items: [] }
  editing.value = d || {}
}

function validate() {
  const f = form.value
  if (f.name.length < 3) return 'Tên chương trình từ 3 ký tự'
  if (!f.startAt || !f.endAt || new Date(f.endAt) <= new Date(f.startAt)) return 'Thời gian kết thúc phải sau thời gian bắt đầu'
  if (!f.mainProductIds.length) return 'Vui lòng chọn sản phẩm chính'
  if (!f.items.length) return f.type === 'GIFT' ? 'Vui lòng chọn quà tặng' : 'Vui lòng chọn sản phẩm mua kèm'
  if (f.type === 'ADDON') {
    const bad = f.items.find(i => !(i.dealPrice >= 1000) || i.dealPrice >= Number(priceOf(i.productId)))
    if (bad) return `Giá mua kèm của "${nameOf(bad.productId)}" phải từ ₫1.000 và thấp hơn giá bán`
  }
  return ''
}

async function onSave() {
  error.value = validate()
  if (error.value) return
  busy.value = true
  try {
    const f = form.value
    const body = { ...f, startAt: f.startAt + ':00', endAt: f.endAt + ':00', minSpend: f.type === 'GIFT' ? f.minSpend || 0 : null }
    if (editing.value.id) await updateAddOnDeal(editing.value.id, body)
    else await createAddOnDeal(body)
    editing.value = null
    await load()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function onToggle(d) {
  await toggleAddOnDeal(d.id)
  await load()
}

async function onEnd(d) {
  if (!confirm(`Kết thúc chương trình "${d.name}" ngay?`)) return
  await endAddOnDeal(d.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.new { margin-left: auto; }
.list { display: flex; flex-direction: column; gap: 10px; }
.deal { display: flex; gap: 16px; justify-content: space-between; border: 1px solid var(--color-border); border-radius: 4px; padding: 14px; }
.deal-main { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.deal-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.kind { font-size: 12px; padding: 2px 8px; border-radius: 2px; background: #fff0eb; color: var(--color-primary); }
.kind.GIFT { background: #f3f0ff; color: #5b3cc4; }
.items { display: flex; gap: 6px; flex-wrap: wrap; }
.item { background: var(--color-primary-light); color: var(--color-primary); border: 1px dashed var(--color-primary); font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.small { font-size: 12px; }
.deal-actions { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; white-space: nowrap; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.link.danger { color: #d33; }
.state { font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.state.RUNNING { background: #eaf7ee; color: #1e9e4a; }
.state.UPCOMING { background: #e8f1fd; color: #2673dd; }
.state.PAUSED { background: #fff4e5; color: #d97706; }
.state.ENDED { background: #f5f5f5; color: #888; }
.types { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; }
.type { display: flex !important; flex-direction: column; gap: 2px; border: 1px solid var(--color-border); border-radius: 4px; padding: 10px 12px; cursor: pointer; }
.type input { display: none; }
.type.on { border-color: var(--color-primary); background: #fff8f6; }
.type span { font-size: 12px; color: var(--color-text-faint); }
.form label { display: block; font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; }
.req { color: var(--color-primary); }
.hint { font-size: 12px; color: var(--color-text-faint); font-weight: 400; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.mt { margin-top: 14px; }
.picker { max-height: 180px; overflow-y: auto; border: 1px solid var(--color-border); border-radius: 4px; padding: 6px; }
.pick { display: flex !important; align-items: center; gap: 8px; padding: 4px; font-size: 13px; color: var(--color-text) !important; margin: 0 !important; cursor: pointer; }
.pick input { width: auto; margin: 0; accent-color: var(--color-primary); }
.pick img { width: 28px; height: 28px; object-fit: cover; border-radius: 2px; }
.pick span:not(.price) { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.price { color: var(--color-text-faint); font-size: 12px; }
.items-table { width: 100%; margin-top: 8px; font-size: 13px; border-collapse: collapse; }
.items-table td { padding: 6px 4px; border-bottom: 1px solid var(--color-border); }
.items-table input { width: 120px; margin: 0 0 0 6px; }
.free { color: #1e9e4a; font-weight: 600; }
.error { color: #d33; font-size: 13px; }
</style>
