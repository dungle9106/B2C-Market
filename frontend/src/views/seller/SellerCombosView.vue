<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Combo Khuyến Mãi</h2>
      <span class="sc-card-sub">"Mua nhiều giảm nhiều" — tự động áp dụng ở bước thanh toán khi khách mua đủ số lượng</span>
      <button type="button" class="btn new" @click="openForm(null)">+ Tạo combo</button>
    </div>

    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="!combos.length" class="sc-empty">Chưa có combo nào. Ví dụ: "Mua 2 giảm 5%, mua 3 giảm 10%" cho các mẫu áo thun.</div>
    <div v-else class="list">
      <article v-for="c in combos" :key="c.id" class="combo">
        <div class="combo-main">
          <div class="combo-head">
            <strong>{{ c.name }}</strong>
            <span class="state" :class="c.status">{{ STATUS[c.status] }}</span>
          </div>
          <div class="tiers">
            <span v-for="t in c.tiers" :key="t.minQuantity" class="tier">Mua {{ t.minQuantity }} giảm {{ t.discountPercent }}%</span>
          </div>
          <div class="small text-faint">{{ formatDateTime(c.startAt) }} → {{ formatDateTime(c.endAt) }}</div>
          <div class="small">{{ c.productCount }} sản phẩm: {{ c.productNames.join(', ') }}{{ c.productCount > c.productNames.length ? '...' : '' }}</div>
        </div>
        <div v-if="c.status !== 'ENDED'" class="combo-actions">
          <button type="button" class="link" @click="openForm(c)">Sửa</button>
          <button type="button" class="link" @click="onToggle(c)">{{ c.active ? 'Tạm dừng' : 'Kích hoạt' }}</button>
          <button type="button" class="link danger" @click="onEnd(c)">Kết thúc</button>
        </div>
      </article>
    </div>

    <BaseModal v-if="editing" :title="editing.id ? 'Sửa combo' : 'Tạo combo khuyến mãi'" :width="640" @close="editing = null">
      <form id="combo-form" class="form" novalidate @submit.prevent="onSave">
        <label>Tên combo <span class="req">*</span></label>
        <input v-model.trim="form.name" maxlength="100" placeholder="VD: Combo áo thun mùa hè" />
        <div class="row">
          <div><label>Bắt đầu</label><input v-model="form.startAt" type="datetime-local" /></div>
          <div><label>Kết thúc</label><input v-model="form.endAt" type="datetime-local" /></div>
        </div>

        <label>Mức giảm <span class="hint">(tối đa 3 mức, mua nhiều hơn giảm nhiều hơn)</span></label>
        <div v-for="(t, i) in form.tiers" :key="i" class="tier-row">
          Mua từ <input v-model.number="t.minQuantity" type="number" min="2" max="100" /> sản phẩm, giảm
          <input v-model.number="t.discountPercent" type="number" min="1" max="50" /> %
          <button v-if="form.tiers.length > 1" type="button" class="link danger" @click="form.tiers.splice(i, 1)">Xóa</button>
        </div>
        <button v-if="form.tiers.length < 3" type="button" class="btn sm ghost" @click="addTier">+ Thêm mức</button>

        <label class="mt">Sản phẩm trong combo <span class="req">*</span> <span class="hint">(khách có thể mua lẫn các sản phẩm này)</span></label>
        <input v-model.trim="filter" placeholder="Lọc sản phẩm" />
        <div class="picker">
          <label v-for="p in visibleProducts" :key="p.id" class="pick">
            <input v-model="form.productIds" type="checkbox" :value="p.id" />
            <img :src="p.thumbnailUrl || placeholder" alt="" />
            <span>{{ p.name }}</span>
            <span class="price">{{ formatVnd(p.price) }}</span>
          </label>
        </div>
        <p class="small">Đã chọn {{ form.productIds.length }} sản phẩm</p>
        <p class="preview">Người mua thấy: <strong>{{ preview }}</strong></p>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn ghost" @click="editing = null">Hủy</button>
        <button type="submit" form="combo-form" class="btn" :disabled="busy">{{ busy ? 'Đang lưu...' : 'Lưu combo' }}</button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import { myCombos, createCombo, updateCombo, toggleCombo, endCombo } from '../../api/marketing'
import { myProducts } from '../../api/products'
import { formatVnd, formatDateTime, toLocalInput } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const STATUS = { RUNNING: 'Đang diễn ra', UPCOMING: 'Sắp diễn ra', PAUSED: 'Tạm dừng', ENDED: 'Đã kết thúc' }
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="%23f0f0f0"/></svg>'

const combos = ref([])
const products = ref([])
const loading = ref(true)
const editing = ref(null)
const form = ref(null)
const filter = ref('')
const busy = ref(false)
const error = ref('')

const visibleProducts = computed(() => {
  const q = filter.value.toLowerCase()
  return products.value.filter(p => p.active && (!q || p.name.toLowerCase().includes(q)))
})
const preview = computed(() => (form.value?.tiers || []).filter(t => t.minQuantity && t.discountPercent)
  .map(t => `Mua ${t.minQuantity} giảm ${t.discountPercent}%`).join(', ') || '—')

async function load() {
  loading.value = true
  try {
    const { data } = await myCombos()
    combos.value = data
  } finally {
    loading.value = false
  }
}

async function openForm(c) {
  error.value = ''
  filter.value = ''
  if (!products.value.length) products.value = (await myProducts()).data
  const now = new Date()
  form.value = c
    ? { name: c.name, startAt: toLocalInput(c.startAt), endAt: toLocalInput(c.endAt), tiers: c.tiers.map(t => ({ ...t })), productIds: [...c.productIds] }
    : { name: '', startAt: toLocalInput(now), endAt: toLocalInput(new Date(now.getTime() + 30 * 86400000)), tiers: [{ minQuantity: 2, discountPercent: 5 }], productIds: [] }
  editing.value = c || {}
}

function addTier() {
  const last = form.value.tiers[form.value.tiers.length - 1]
  form.value.tiers.push({ minQuantity: (last?.minQuantity || 1) + 1, discountPercent: Math.min(50, (last?.discountPercent || 0) + 5) })
}

function validate() {
  const f = form.value
  if (f.name.length < 3) return 'Tên combo từ 3 ký tự'
  if (!f.startAt || !f.endAt || new Date(f.endAt) <= new Date(f.startAt)) return 'Thời gian kết thúc phải sau thời gian bắt đầu'
  const tiers = [...f.tiers].sort((a, b) => a.minQuantity - b.minQuantity)
  if (tiers.some(t => !(t.minQuantity >= 2) || !(t.discountPercent >= 1 && t.discountPercent <= 50))) return 'Số lượng từ 2, mức giảm từ 1% đến 50%'
  for (let i = 1; i < tiers.length; i++) {
    if (tiers[i].minQuantity === tiers[i - 1].minQuantity) return 'Các mức giảm phải có số lượng khác nhau'
    if (tiers[i].discountPercent <= tiers[i - 1].discountPercent) return 'Mua nhiều hơn phải được giảm nhiều hơn'
  }
  if (!f.productIds.length) return 'Vui lòng chọn sản phẩm'
  return ''
}

async function onSave() {
  error.value = validate()
  if (error.value) return
  busy.value = true
  try {
    const f = form.value
    const body = { ...f, startAt: f.startAt + ':00', endAt: f.endAt + ':00' }
    if (editing.value.id) await updateCombo(editing.value.id, body)
    else await createCombo(body)
    editing.value = null
    await load()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function onToggle(c) {
  try {
    await toggleCombo(c.id)
    await load()
  } catch (e) {
    alert(extractError(e))
  }
}

async function onEnd(c) {
  if (!confirm(`Kết thúc combo "${c.name}" ngay?`)) return
  await endCombo(c.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.new { margin-left: auto; }
.list { display: flex; flex-direction: column; gap: 10px; }
.combo { display: flex; gap: 16px; justify-content: space-between; border: 1px solid var(--color-border); border-radius: 4px; padding: 14px; }
.combo-main { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.combo-head { display: flex; align-items: center; gap: 10px; }
.tiers { display: flex; gap: 6px; flex-wrap: wrap; }
.tier { background: var(--color-primary-light); color: var(--color-primary); border: 1px dashed var(--color-primary); font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.small { font-size: 12px; }
.combo-actions { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; white-space: nowrap; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.link.danger { color: #d33; }
.state { font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.state.RUNNING { background: #eaf7ee; color: #1e9e4a; }
.state.UPCOMING { background: #e8f1fd; color: #2673dd; }
.state.PAUSED { background: #fff4e5; color: #d97706; }
.state.ENDED { background: #f5f5f5; color: #888; }
.form label { display: block; font-size: 14px; color: var(--color-text-muted); margin-bottom: 6px; }
.req { color: var(--color-primary); }
.hint { font-size: 12px; color: var(--color-text-faint); font-weight: 400; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.tier-row { display: flex; align-items: center; gap: 6px; font-size: 14px; margin-bottom: 8px; flex-wrap: wrap; }
.tier-row input { width: 80px; margin: 0; }
.mt { margin-top: 14px; }
.picker { max-height: 220px; overflow-y: auto; border: 1px solid var(--color-border); border-radius: 4px; padding: 6px; }
.pick { display: flex !important; align-items: center; gap: 8px; padding: 4px; font-size: 13px; color: var(--color-text) !important; margin: 0 !important; cursor: pointer; }
.pick input { width: auto; margin: 0; accent-color: var(--color-primary); }
.pick img { width: 28px; height: 28px; object-fit: cover; border-radius: 2px; }
.pick span:not(.price) { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.price { color: var(--color-text-faint); font-size: 12px; }
.preview { background: #fff8f6; padding: 8px 10px; border-radius: 3px; font-size: 14px; }
.error { color: #d33; font-size: 13px; }
</style>
