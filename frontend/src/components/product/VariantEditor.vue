<template>
  <div class="variant-editor">
    <div v-if="!model.tiers.length" class="off">
      <button type="button" class="btn ghost add-tier" @click="addTier">+ Thêm nhóm phân loại</button>
      <span class="hint">VD: Màu sắc, Kích cỡ — mỗi tổ hợp có giá và kho riêng</span>
    </div>

    <!-- ===== groups and their values ===== -->
    <div v-for="(tier, t) in model.tiers" :key="tier.uid" class="tier">
      <div class="tier-head">
        <label>Nhóm phân loại {{ t + 1 }}</label>
        <input v-model="tier.name" maxlength="30" :placeholder="t === 0 ? 'VD: Màu sắc' : 'VD: Kích cỡ'" class="tier-name" />
        <button type="button" class="icon-x" :aria-label="`Xóa nhóm phân loại ${t + 1}`" @click="removeTier(t)">✕</button>
      </div>
      <div class="options">
        <div v-for="(o, i) in tier.options" :key="o.uid" class="option">
          <!-- picture per value of the first group (e.g. per colour), like Shopee -->
          <label v-if="t === 0" class="opt-img" :title="o.imageUrl ? 'Đổi ảnh' : 'Thêm ảnh cho phân loại này'">
            <img v-if="o.imageUrl" :src="o.imageUrl" alt="" />
            <span v-else>{{ uploadingUid === o.uid ? '…' : '+ảnh' }}</span>
            <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden @change="onImage(o, $event)" />
          </label>
          <input v-model="o.value" maxlength="40" :placeholder="t === 0 ? 'VD: Đỏ' : 'VD: M'" />
          <button type="button" class="icon-x" aria-label="Xóa giá trị" :disabled="tier.options.length === 1" @click="tier.options.splice(i, 1)">✕</button>
        </div>
        <button type="button" class="add-option" @click="addOption(tier)">+ Thêm</button>
      </div>
    </div>
    <button v-if="model.tiers.length === 1" type="button" class="btn ghost add-tier" @click="addTier">+ Thêm nhóm phân loại 2</button>
    <p v-if="imageError" class="err">{{ imageError }}</p>

    <!-- ===== combinations ===== -->
    <template v-if="model.rows.length">
      <div class="batch">
        <span class="batch-label">Áp dụng cho tất cả phân loại:</span>
        <input v-model.number="batch.extraPrice" type="number" min="0" placeholder="Giá cộng thêm" />
        <input v-model.number="batch.stock" type="number" min="0" placeholder="Kho" />
        <button type="button" class="btn sm secondary" @click="applyBatch">Áp dụng</button>
        <button v-if="productSku" type="button" class="btn sm ghost" title="SKU = Mã SP + giá trị phân loại" @click="autoSku">Tạo SKU tự động</button>
      </div>
      <div class="sc-table-wrap">
        <table class="sc-table matrix">
          <thead>
            <tr>
              <th>{{ model.tiers[0].name || 'Nhóm 1' }}</th>
              <th v-if="model.tiers[1]">{{ model.tiers[1].name || 'Nhóm 2' }}</th>
              <th>Giá cộng thêm (₫)</th>
              <th>Giá bán</th>
              <th>Kho</th>
              <th>SKU phân loại</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in model.rows" :key="r.key">
              <td v-if="!model.tiers[1] || isFirstOfGroup(i)" :rowspan="model.tiers[1] ? groupSize(r) : 1" class="opt-cell">
                <img v-if="optionOf(0, r.uid1)?.imageUrl" :src="optionOf(0, r.uid1).imageUrl" alt="" />
                {{ optionOf(0, r.uid1)?.value || '—' }}
              </td>
              <td v-if="model.tiers[1]">{{ optionOf(1, r.uid2)?.value || '—' }}</td>
              <td><input v-model.number="r.extraPrice" type="number" min="0" placeholder="0" /></td>
              <td class="final-price">{{ basePrice > 0 ? formatVnd(basePrice + (Number(r.extraPrice) || 0)) : '—' }}</td>
              <td><input v-model.number="r.stock" type="number" min="0" placeholder="0" :class="{ low: r.stock > 0 && r.stock <= 5, zero: r.stock === 0 }" /></td>
              <td><input v-model.trim="r.sku" maxlength="50" placeholder="Không bắt buộc" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="hint">{{ model.rows.length }} phân loại · tổng kho {{ totalStock }}</p>
    </template>
  </div>
</template>


<script setup>
import { ref, computed, watch } from 'vue'
import { uploadFile } from '../../api/uploads'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'
import { newUid } from '../../utils/variants'

// model shape and API conversion: see utils/variants.js
const model = defineModel({ type: Object, required: true })
const props = defineProps({
  basePrice: { type: Number, default: 0 },
  productSku: { type: String, default: '' }
})

const batch = ref({ extraPrice: null, stock: null })
const uploadingUid = ref(null)
const imageError = ref('')

function addTier() {
  if (model.value.tiers.length >= 2) return
  model.value.tiers.push({ uid: newUid(), name: '', options: [{ uid: newUid(), value: '', imageUrl: null }] })
}
function removeTier(t) {
  model.value.tiers.splice(t, 1)
}
function addOption(tier) {
  if (tier.options.length >= 20) return
  tier.options.push({ uid: newUid(), value: '', imageUrl: null })
}

async function onImage(option, e) {
  const file = e.target.files[0]
  e.target.value = ''
  if (!file) return
  imageError.value = ''
  uploadingUid.value = option.uid
  try {
    const { data } = await uploadFile(file)
    option.imageUrl = data.url
  } catch (err) {
    imageError.value = extractError(err)
  } finally {
    uploadingUid.value = null
  }
}

const optionOf = (t, id) => model.value.tiers[t]?.options.find(o => o.uid === id)
const totalStock = computed(() => model.value.rows.reduce((s, r) => s + (Number(r.stock) || 0), 0))

// combinations follow the options; existing rows (same option uids) keep their values,
// so renaming "Do" to "Đỏ" keeps that row's price and stock
watch(() => model.value.tiers.map(t => t.options.map(o => o.uid).join(',')).join('|'), () => {
  const [t1, t2] = model.value.tiers
  if (!t1) {
    model.value.rows = []
    return
  }
  const old = new Map(model.value.rows.map(r => [r.key, r]))
  const rows = []
  for (const o1 of t1.options) {
    for (const o2 of t2 ? t2.options : [null]) {
      const key = `${o1.uid}|${o2 ? o2.uid : ''}`
      rows.push(old.get(key) || { key, uid1: o1.uid, uid2: o2?.uid ?? null, id: null, extraPrice: 0, stock: 0, sku: '' })
    }
  }
  model.value.rows = rows
}, { immediate: true })

function isFirstOfGroup(i) {
  return i === 0 || model.value.rows[i - 1].uid1 !== model.value.rows[i].uid1
}
function groupSize(r) {
  return model.value.rows.filter(x => x.uid1 === r.uid1).length
}

function applyBatch() {
  const { extraPrice, stock } = batch.value
  for (const r of model.value.rows) {
    if (typeof extraPrice === 'number') r.extraPrice = extraPrice
    if (typeof stock === 'number') r.stock = stock
  }
}

const slug = (s) => (s || '').normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/gi, 'D').toUpperCase().replace(/[^A-Z0-9]+/g, '')
function autoSku() {
  const base = slug(props.productSku)
  for (const r of model.value.rows) {
    r.sku = [base, slug(optionOf(0, r.uid1)?.value), slug(optionOf(1, r.uid2)?.value)].filter(Boolean).join('-').slice(0, 50)
  }
}
</script>

<style scoped>
.off { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.hint { font-size: 12px; color: var(--color-text-faint); }
.add-tier { border-style: dashed; margin-bottom: 14px; }
.tier { background: #fafafa; border-radius: 4px; padding: 14px; margin-bottom: 12px; }
.tier-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.tier-head label { margin: 0 !important; white-space: nowrap; }
.tier-name { margin: 0; max-width: 260px; }
.icon-x { background: none; border: none; color: var(--color-text-faint); cursor: pointer; font-size: 14px; padding: 4px 6px; }
.icon-x:hover:not(:disabled) { color: #d33; }
.icon-x:disabled { opacity: 0.3; cursor: default; }
.options { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 8px; }
.option { display: flex; align-items: center; gap: 4px; background: white; border: 1px solid #e5e5e5; border-radius: 4px; padding: 4px; }
.option input { margin: 0; border: none; padding: 6px; }
.opt-img { width: 34px; height: 34px; flex-shrink: 0; border: 1px dashed var(--color-primary); border-radius: 3px; display: flex !important; align-items: center; justify-content: center; font-size: 10px; color: var(--color-primary) !important; cursor: pointer; overflow: hidden; margin: 0 !important; }
.opt-img img { width: 100%; height: 100%; object-fit: cover; }
.add-option { border: 1px dashed #ccc; background: white; border-radius: 4px; cursor: pointer; color: var(--color-text-muted); min-height: 44px; }
.add-option:hover { border-color: var(--color-primary); color: var(--color-primary); }
.err { color: #d33; font-size: 13px; }

.batch { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 6px 0 10px; }
.batch-label { font-size: 13px; color: var(--color-text-muted); }
.batch input { margin: 0; width: 150px; }
.matrix td { padding: 8px; vertical-align: middle; }
.matrix input { margin: 0; min-width: 90px; }
.matrix input.low { border-color: #f0a020; }
.matrix input.zero { border-color: #d33; }
.opt-cell { font-weight: 500; background: #fcfcfc; }
.opt-cell img { width: 28px; height: 28px; object-fit: cover; border-radius: 3px; vertical-align: middle; margin-right: 6px; }
.final-price { white-space: nowrap; color: var(--color-primary); }
</style>
