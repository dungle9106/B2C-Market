<template>
  <div>
    <div class="head">
      <div>
        <h2 class="title">{{ title }}</h2>
        <p class="sub">{{ subtitle }}</p>
      </div>
      <button class="btn" type="button" @click="openCreate">+ Tạo Voucher</button>
    </div>

    <nav class="tabs">
      <button v-for="t in tabs" :key="t.key" type="button" :class="{ active: tab === t.key }" @click="tab = t.key">
        {{ t.label }} <span class="count">({{ vouchers.filter(t.match).length }})</span>
      </button>
    </nav>

    <div v-if="loading" class="empty">Đang tải...</div>
    <div v-else-if="!filtered.length" class="empty">Chưa có voucher nào.</div>
    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr><th>Tên voucher | Mã</th><th>Áp dụng</th><th>Đơn tối thiểu</th><th>Đã dùng</th><th v-if="hasStats">Hiệu quả</th><th>Thời gian</th><th>Trạng thái</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="v in filtered" :key="v.id">
            <td>
              <div class="v-title">{{ v.title }}</div>
              <span class="code">{{ v.code }}</span>
              <span v-if="!v.publicVisible" class="hidden-tag" title="Không hiển thị cho người mua, chỉ dùng được khi nhập mã">Ẩn</span>
            </td>
            <td class="small">{{ TYPE_LABELS[v.type] }}<br />{{ v.productIds?.length ? `${v.productIds.length} sản phẩm` : shopMode ? 'Toàn shop' : 'Toàn sàn' }}</td>
            <td>{{ v.minOrder ? formatVnd(v.minOrder) : '—' }}</td>
            <td class="nowrap">{{ v.usedCount }}{{ v.usageLimit ? ' / ' + v.usageLimit : '' }}</td>
            <td v-if="hasStats" class="small">{{ v.ordersCount }} đơn<br />Giảm {{ formatVnd(v.discountGiven) }}<br />Doanh số {{ formatVnd(v.orderValue) }}</td>
            <td class="small">{{ formatDateTime(v.startAt) }}<br />→ {{ formatDateTime(v.endAt) }}</td>
            <td><span class="state" :class="stateOf(v).key">{{ stateOf(v).label }}</span></td>
            <td class="actions">
              <template v-if="stateOf(v).key !== 'ended'">
                <button v-if="api.update" type="button" class="link" @click="openEdit(v)">Sửa</button>
                <button type="button" class="link" @click="onToggle(v)">{{ v.active ? 'Tạm dừng' : 'Kích hoạt' }}</button>
                <button v-if="api.end" type="button" class="link danger" @click="onEnd(v)">Kết thúc</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- create dialog -->
    <BaseModal v-if="creating" :title="editing ? `Sửa voucher ${editing.code}` : 'Tạo Voucher Mới'" :width="620" @close="creating = false">
      <form id="voucher-form" class="form" novalidate @submit.prevent="onSave">
        <p v-if="locked" class="lock-note">Voucher đã bắt đầu: chỉ sửa được thời gian kết thúc, tổng lượt dùng và chế độ hiển thị.</p>
        <label>Mã voucher <span class="req">*</span></label>
        <input v-model.trim="form.code" maxlength="20" :disabled="!!editing" placeholder="VD: GIAM10K (4-20 chữ/số)" @input="form.code = form.code.toUpperCase()" />

        <label>Loại giảm giá <span class="req">*</span></label>
        <div class="types">
          <label v-for="(name, key) in TYPE_LABELS" :key="key" class="type" :class="{ active: form.type === key }">
            <input v-model="form.type" type="radio" :value="key" :disabled="locked" /> {{ name }}
          </label>
        </div>

        <div class="row">
          <div>
            <label>{{ form.type === 'PERCENT' ? 'Mức giảm (%)' : 'Mức giảm (₫)' }} <span class="req">*</span></label>
            <input v-model.number="form.value" :disabled="locked" type="number" min="1" :placeholder="form.type === 'PERCENT' ? '1 - 90' : 'VD: 20000'" />
          </div>
          <div v-if="form.type === 'PERCENT'">
            <label>Giảm tối đa (₫)</label>
            <input v-model.number="form.maxDiscount" :disabled="locked" type="number" min="1" placeholder="Không giới hạn" />
          </div>
        </div>

        <div class="row">
          <div>
            <label>Giá trị đơn tối thiểu (₫)</label>
            <input v-model.number="form.minOrder" :disabled="locked" type="number" min="0" placeholder="0" />
          </div>
          <div>
            <label>Tổng lượt sử dụng</label>
            <input v-model.number="form.usageLimit" type="number" min="0" placeholder="0 = không giới hạn" />
          </div>
        </div>
        <div class="row">
          <div>
            <label>Lượt dùng tối đa / người mua</label>
            <input v-model.number="form.perUserLimit" :disabled="locked" type="number" min="0" placeholder="1" />
          </div>
          <div></div>
        </div>
        <template v-if="shopMode">
          <label>Sản phẩm áp dụng</label>
          <div class="scope">
            <label class="type" :class="{ active: !form.someProducts }"><input v-model="form.someProducts" type="radio" :value="false" :disabled="locked" /> Toàn bộ sản phẩm của shop</label>
            <label class="type" :class="{ active: form.someProducts }"><input v-model="form.someProducts" type="radio" :value="true" :disabled="locked" @change="loadProducts" /> Sản phẩm nhất định</label>
          </div>
          <div v-if="form.someProducts" class="picker">
            <input v-model.trim="productFilter" placeholder="Lọc sản phẩm" class="filter" />
            <div class="picker-list">
              <label v-for="p in pickerProducts" :key="p.id" class="pick">
                <input v-model="form.productIds" type="checkbox" :value="p.id" :disabled="locked" />
                <img :src="p.thumbnailUrl || placeholder" alt="" />
                <span>{{ p.name }}</span>
              </label>
            </div>
            <div class="small">Đã chọn {{ form.productIds.length }} sản phẩm</div>
          </div>
        </template>
        <div class="row">
          <div>
            <label>Bắt đầu <span class="req">*</span></label>
            <input v-model="form.startAt" type="datetime-local" :disabled="locked" />
          </div>
          <div>
            <label>Kết thúc <span class="req">*</span></label>
            <input v-model="form.endAt" type="datetime-local" />
          </div>
        </div>
        <label class="check">
          <input v-model="form.publicVisible" type="checkbox" />
          Hiển thị công khai trên trang shop / sản phẩm
          <span class="small">(bỏ chọn = voucher ẩn, chỉ dùng được khi khách nhập mã bạn gửi riêng)</span>
        </label>
        <p class="preview">Hiển thị cho người mua: <strong>{{ previewTitle }}</strong></p>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn ghost" @click="creating = false">Hủy</button>
        <button type="submit" form="voucher-form" class="btn" :disabled="saving">{{ saving ? 'Đang lưu...' : 'Xác nhận' }}</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaseModal from './BaseModal.vue'
import { extractError } from '../utils/apiError'
import { formatVnd, formatDateTime, toLocalInput } from '../utils/format'
import { myProducts } from '../api/products'

// api: { list, create, toggle, update?, end? } so the same screen serves shop (seller) and platform (admin)
// vouchers; shopMode adds the product scope picker
const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  api: { type: Object, required: true },
  shopMode: { type: Boolean, default: false }
})
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="%23f0f0f0"/></svg>'

const TYPE_LABELS = { PERCENT: 'Giảm theo %', FIXED: 'Giảm số tiền', SHIPPING: 'Giảm phí vận chuyển' }

const vouchers = ref([])
const loading = ref(true)
const tab = ref('all')
const creating = ref(false)
const editing = ref(null)   // the voucher being edited, null when creating
const saving = ref(false)
const error = ref('')
const products = ref([])
const productFilter = ref('')
const hasStats = computed(() => vouchers.value.some(v => v.ordersCount != null))
// once a voucher has started its terms are fixed for buyers
const locked = computed(() => !!editing.value && new Date(editing.value.startAt) <= new Date())
const pickerProducts = computed(() => {
  const q = productFilter.value.toLowerCase()
  return products.value.filter(p => !q || p.name.toLowerCase().includes(q))
})

const now = () => new Date()
function stateOf(v) {
  if (new Date(v.endAt) <= now()) return { key: 'ended', label: 'Đã kết thúc' }
  if (!v.active) return { key: 'paused', label: 'Tạm dừng' }
  if (new Date(v.startAt) > now()) return { key: 'upcoming', label: 'Sắp diễn ra' }
  if (v.usageLimit && v.usedCount >= v.usageLimit) return { key: 'ended', label: 'Hết lượt' }
  return { key: 'running', label: 'Đang diễn ra' }
}
const tabs = [
  { key: 'all', label: 'Tất cả', match: () => true },
  { key: 'running', label: 'Đang diễn ra', match: (v) => stateOf(v).key === 'running' },
  { key: 'upcoming', label: 'Sắp diễn ra', match: (v) => stateOf(v).key === 'upcoming' },
  { key: 'ended', label: 'Đã kết thúc / Tạm dừng', match: (v) => ['ended', 'paused'].includes(stateOf(v).key) }
]
const filtered = computed(() => vouchers.value.filter(tabs.find(t => t.key === tab.value).match))

function emptyForm() {
  const start = new Date()
  const end = new Date(start.getTime() + 7 * 24 * 3600 * 1000)
  return { code: '', type: 'PERCENT', value: null, maxDiscount: null, minOrder: 0, usageLimit: 100, perUserLimit: 1,
    startAt: toLocalInput(start), endAt: toLocalInput(end), publicVisible: true, someProducts: false, productIds: [] }
}
const form = ref(emptyForm())

const short = (n) => (n >= 1000 && n % 1000 === 0 ? `${n / 1000}k` : formatVnd(n))
const previewTitle = computed(() => {
  const f = form.value
  if (!f.value) return '—'
  if (f.type === 'PERCENT') return `Giảm ${f.value}%${f.maxDiscount ? ' tối đa ' + short(f.maxDiscount) : ''}`
  if (f.type === 'FIXED') return `Giảm ${short(f.value)}`
  return `Giảm ${short(f.value)} phí vận chuyển`
})

function validate() {
  const f = form.value
  if (!/^[A-Z0-9]{4,20}$/.test(f.code)) return 'Mã voucher gồm 4-20 chữ cái hoặc số, không dấu'
  if (!(f.value > 0)) return 'Vui lòng nhập mức giảm'
  if (f.type === 'PERCENT' && f.value > 90) return 'Mức giảm phần trăm tối đa 90%'
  if (f.type !== 'PERCENT' && f.value < 1000) return 'Mức giảm tối thiểu ₫1.000'
  if (f.type === 'FIXED' && f.minOrder && f.value > f.minOrder) return 'Mức giảm không được lớn hơn đơn tối thiểu'
  if (!f.startAt || !f.endAt || new Date(f.endAt) <= new Date(f.startAt)) return 'Thời gian kết thúc phải sau thời gian bắt đầu'
  if (f.someProducts && !f.productIds.length) return 'Vui lòng chọn sản phẩm áp dụng'
  return ''
}

async function load() {
  loading.value = true
  try {
    const { data } = await props.api.list()
    vouchers.value = data
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  if (products.value.length || !props.shopMode) return
  const { data } = await myProducts()
  products.value = data
}

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  error.value = ''
  creating.value = true
}

function openEdit(v) {
  editing.value = v
  error.value = ''
  form.value = {
    code: v.code, type: v.type, value: Number(v.value), maxDiscount: v.maxDiscount ? Number(v.maxDiscount) : null,
    minOrder: v.minOrder ? Number(v.minOrder) : 0, usageLimit: v.usageLimit, perUserLimit: v.perUserLimit,
    startAt: toLocalInput(v.startAt), endAt: toLocalInput(v.endAt), publicVisible: v.publicVisible,
    someProducts: !!v.productIds?.length, productIds: [...(v.productIds || [])]
  }
  if (form.value.someProducts) loadProducts()
  creating.value = true
}

async function onSave() {
  error.value = validate()
  if (error.value) return
  saving.value = true
  try {
    const { someProducts, ...f } = form.value
    const body = { ...f, maxDiscount: f.type === 'PERCENT' ? f.maxDiscount || null : null, productIds: someProducts ? f.productIds : [] }
    if (editing.value) await props.api.update(editing.value.id, body)
    else await props.api.create(body)
    creating.value = false
    await load()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}

async function onToggle(v) {
  const { data } = await props.api.toggle(v.id)
  Object.assign(v, data)
}

async function onEnd(v) {
  if (!confirm(`Kết thúc voucher ${v.code} ngay bây giờ? Người mua sẽ không dùng được nữa.`)) return
  const { data } = await props.api.end(v.id)
  Object.assign(v, data)
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
.title { font-size: 19px; font-weight: 600; margin: 0; }
.sub { font-size: 13px; color: var(--color-text-faint); margin: 4px 0 0; }
.tabs { display: flex; border-bottom: 1px solid var(--color-border); margin-bottom: 12px; overflow-x: auto; }
.tabs button { background: none; border: none; border-bottom: 2px solid transparent; padding: 12px 16px; cursor: pointer; font-size: 14px; white-space: nowrap; }
.tabs button.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.count { color: var(--color-text-faint); }
.table-wrap { overflow-x: auto; }
.nowrap { white-space: nowrap; }
.table { width: 100%; border-collapse: collapse; font-size: 14px; }
.table th { text-align: left; font-weight: 500; color: var(--color-text-muted); background: #fafafa; padding: 12px; }
.table td { padding: 14px 12px; border-bottom: 1px solid var(--color-border); vertical-align: middle; }
.v-title { font-weight: 500; }
.code { font-size: 12px; color: var(--color-text-muted); background: #f5f5f5; padding: 1px 6px; border-radius: 2px; }
.small { font-size: 12px; color: var(--color-text-muted); white-space: nowrap; }
.state { font-size: 12px; padding: 3px 8px; border-radius: 2px; white-space: nowrap; }
.state.running { background: #eaf7ee; color: #1e9e4a; }
.state.upcoming { background: #e8f1fd; color: #2673dd; }
.state.paused { background: #fff4e5; color: #d97706; }
.state.ended { background: #f5f5f5; color: #888; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 14px; white-space: nowrap; }
.empty { text-align: center; color: var(--color-text-faint); padding: 40px 0; }

.form label { display: block; font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; }
.req { color: var(--color-primary); }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.types { display: flex; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
.type { display: flex !important; align-items: center; gap: 6px; border: 1px solid var(--color-border); padding: 8px 12px; border-radius: 3px; cursor: pointer; margin: 0 !important; color: var(--color-text) !important; }
.type.active { border-color: var(--color-primary); color: var(--color-primary) !important; }
.type input { width: auto; margin: 0; accent-color: var(--color-primary); }
.preview { font-size: 14px; background: #fff8f6; padding: 10px 12px; border-radius: 3px; margin: 0 0 8px; }
.error { color: #d33; font-size: 13px; margin: 0; }
.hidden-tag { font-size: 11px; margin-left: 6px; padding: 1px 6px; border-radius: 2px; background: #333; color: white; }
.actions { white-space: nowrap; }
.link.danger { color: #d33; }
.lock-note { background: #fff8eb; color: #a16207; font-size: 13px; padding: 8px 10px; border-radius: 3px; margin: 0 0 12px; }
.scope { display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.picker { border: 1px solid var(--color-border); border-radius: 4px; padding: 10px; margin-bottom: 14px; }
.picker .filter { margin-bottom: 8px; }
.picker-list { max-height: 200px; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; margin-bottom: 6px; }
.pick { display: flex !important; align-items: center; gap: 8px; font-size: 13px; color: var(--color-text) !important; margin: 0 !important; cursor: pointer; }
.pick input { width: auto; margin: 0; accent-color: var(--color-primary); }
.pick img { width: 28px; height: 28px; object-fit: cover; border-radius: 2px; }
.check { display: flex !important; align-items: baseline; gap: 6px; flex-wrap: wrap; color: var(--color-text) !important; cursor: pointer; margin: 4px 0 10px !important; }
.check input { width: auto; margin: 0; accent-color: var(--color-primary); }
@media (max-width: 560px) { .row { grid-template-columns: 1fr; } }
</style>
