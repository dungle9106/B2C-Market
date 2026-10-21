<template>
  <BaseModal :title="voucher ? `Sửa Shoppi Voucher ${voucher.code}` : 'Tạo Shoppi Voucher'" :width="680" @close="$emit('close')">
    <form id="pv-form" class="form" novalidate @submit.prevent="save">
      <p v-if="locked" class="lock">Voucher đã bắt đầu: chỉ sửa được thời gian kết thúc, tổng lượt dùng, ngân sách, chương trình và chế độ hiển thị.</p>

      <div class="row">
        <label>Mã voucher *
          <input v-model.trim="f.code" maxlength="20" :disabled="!!voucher" placeholder="VD: SIEUSALE50K" @input="f.code = f.code.toUpperCase()" />
        </label>
        <label>Thuộc chương trình
          <select v-model="f.campaignId">
            <option :value="null">— Không —</option>
            <option v-for="c in campaigns" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
      </div>

      <div class="types">
        <label v-for="(name, key) in TYPES" :key="key" class="opt" :class="{ on: f.type === key }">
          <input v-model="f.type" type="radio" :value="key" :disabled="locked" /> {{ name }}
        </label>
      </div>
      <div class="row">
        <label>{{ f.type === 'PERCENT' ? 'Mức giảm (%) *' : 'Mức giảm (₫) *' }}
          <input v-model.number="f.value" type="number" min="1" :disabled="locked" />
        </label>
        <label v-if="f.type === 'PERCENT'">Giảm tối đa (₫)
          <input v-model.number="f.maxDiscount" type="number" min="1" :disabled="locked" placeholder="Không giới hạn" />
        </label>
        <label>Đơn tối thiểu (₫)
          <input v-model.number="f.minOrder" type="number" min="0" :disabled="locked" />
        </label>
      </div>
      <div class="row">
        <label>Tổng lượt dùng
          <input v-model.number="f.usageLimit" type="number" min="0" placeholder="0 = không giới hạn" />
        </label>
        <label>Lượt / người mua
          <input v-model.number="f.perUserLimit" type="number" min="0" :disabled="locked" />
        </label>
        <label>Ngân sách Shoppi (₫)
          <input v-model.number="f.budget" type="number" min="0" placeholder="Không giới hạn" />
        </label>
      </div>
      <p class="hint">Ngân sách = tổng tiền giảm Shoppi chi cho voucher này. Khi chạm ngân sách, voucher tự ngừng áp dụng.</p>

      <fieldset>
        <legend>Đối tượng áp dụng</legend>
        <label class="check"><input v-model="f.newBuyerOnly" type="checkbox" :disabled="locked" /> Chỉ dành cho <b>khách hàng mới</b> (chưa từng đặt đơn thành công)</label>
        <div class="cats">
          <span class="hint">Ngành hàng áp dụng ({{ f.categoryIds.length ? f.categoryIds.length + ' đã chọn' : 'tất cả' }}):</span>
          <div class="cat-list">
            <template v-for="root in tree" :key="root.id">
              <label class="cat root"><input v-model="f.categoryIds" type="checkbox" :value="root.id" :disabled="locked" /> {{ root.name }}</label>
              <label v-for="c in root.children || []" :key="c.id" class="cat"><input v-model="f.categoryIds" type="checkbox" :value="c.id" :disabled="locked || f.categoryIds.includes(root.id)" /> {{ c.name }}</label>
            </template>
          </div>
        </div>
      </fieldset>

      <div class="row">
        <label>Bắt đầu *<input v-model="f.startAt" type="datetime-local" :disabled="locked" /></label>
        <label>Kết thúc *<input v-model="f.endAt" type="datetime-local" /></label>
      </div>
      <label class="check"><input v-model="f.publicVisible" type="checkbox" /> Hiển thị công khai (bỏ chọn = voucher ẩn, chỉ dùng khi nhập mã)</label>
      <p class="preview">Người mua thấy: <strong>{{ preview }}</strong></p>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="btn ghost" @click="$emit('close')">Hủy</button>
      <button type="submit" form="pv-form" class="btn" :disabled="saving">{{ saving ? 'Đang lưu...' : voucher ? 'Lưu' : 'Tạo voucher' }}</button>
    </template>
  </BaseModal>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaseModal from '../BaseModal.vue'
import { createPlatformVoucher, updatePlatformVoucher } from '../../api/vouchers'
import { adminCampaigns } from '../../api/admin'
import { categoryTree } from '../../api/products'
import { formatVnd, toLocalInput } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const props = defineProps({ voucher: Object, campaignId: Number })
const emit = defineEmits(['close', 'saved'])

const TYPES = { PERCENT: 'Giảm theo %', FIXED: 'Giảm số tiền', SHIPPING: 'Miễn phí vận chuyển' }
const v = props.voucher
const f = ref(v ? {
  code: v.code, type: v.type, value: v.value, maxDiscount: v.maxDiscount, minOrder: v.minOrder || 0, usageLimit: v.usageLimit,
  perUserLimit: v.perUserLimit, budget: v.budget, campaignId: v.campaignId ?? null, newBuyerOnly: v.newBuyerOnly,
  categoryIds: [...(v.categoryIds || [])], startAt: toLocalInput(v.startAt), endAt: toLocalInput(v.endAt), publicVisible: v.publicVisible
} : {
  code: '', type: 'PERCENT', value: null, maxDiscount: null, minOrder: 0, usageLimit: 1000, perUserLimit: 1, budget: null,
  campaignId: props.campaignId ?? null, newBuyerOnly: false, categoryIds: [],
  startAt: toLocalInput(new Date()), endAt: toLocalInput(new Date(Date.now() + 7 * 86400000)), publicVisible: true
})
const locked = computed(() => !!v && new Date(v.startAt) <= new Date())
const campaigns = ref([])
const tree = ref([])
const saving = ref(false)
const error = ref('')

const short = (n) => (n >= 1000 && n % 1000 === 0 ? `${n / 1000}k` : formatVnd(n))
const preview = computed(() => {
  const x = f.value
  if (!x.value) return '—'
  const base = x.type === 'PERCENT' ? `Giảm ${x.value}%${x.maxDiscount ? ' tối đa ' + short(x.maxDiscount) : ''}`
    : x.type === 'FIXED' ? `Giảm ${short(x.value)}` : `Giảm ${short(x.value)} phí vận chuyển`
  return base + (x.minOrder ? ` · đơn từ ${short(x.minOrder)}` : '') + (x.newBuyerOnly ? ' · khách hàng mới' : '')
    + (x.categoryIds.length ? ` · ${x.categoryIds.length} ngành hàng` : '')
})

function validate() {
  const x = f.value
  if (!/^[A-Z0-9]{4,20}$/.test(x.code)) return 'Mã voucher gồm 4-20 chữ cái hoặc số, không dấu'
  if (!(x.value > 0)) return 'Vui lòng nhập mức giảm'
  if (x.type === 'PERCENT' && x.value > 90) return 'Mức giảm phần trăm tối đa 90%'
  if (x.type !== 'PERCENT' && x.value < 1000) return 'Mức giảm tối thiểu ₫1.000'
  if (!x.startAt || !x.endAt || new Date(x.endAt) <= new Date(x.startAt)) return 'Thời gian kết thúc phải sau thời gian bắt đầu'
  return ''
}

async function save() {
  error.value = validate()
  if (error.value) return
  saving.value = true
  try {
    const x = f.value
    // a parent category covers its children: don't send both
    const parents = new Set(tree.value.filter(r => x.categoryIds.includes(r.id)).flatMap(r => (r.children || []).map(c => c.id)))
    const body = { ...x, categoryIds: x.categoryIds.filter(id => !parents.has(id)), budget: x.budget || null, maxDiscount: x.maxDiscount || null }
    const { data } = v ? await updatePlatformVoucher(v.id, body) : await createPlatformVoucher(body)
    emit('saved', data)
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  adminCampaigns().then(({ data }) => { campaigns.value = data.filter(c => c.status !== 'ENDED' || c.id === f.value.campaignId) }).catch(() => {})
  categoryTree().then(({ data }) => { tree.value = data }).catch(() => {})
})
</script>

<style scoped>
.form { display: flex; flex-direction: column; gap: 12px; font-size: 14px; }
.row { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
.row label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--color-text-muted); }
.types { display: flex; gap: 8px; flex-wrap: wrap; }
.opt { border: 1px solid var(--color-border); border-radius: 6px; padding: 6px 12px; cursor: pointer; font-size: 13px; }
.opt.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.opt input { display: none; }
fieldset { border: 1px solid var(--color-border); border-radius: 6px; padding: 10px 12px; margin: 0; }
legend { font-size: 13px; font-weight: 600; padding: 0 4px; }
.check { display: flex; gap: 8px; align-items: center; font-size: 13px; }
.check input { width: auto; }
.cats { margin-top: 8px; }
.cat-list { display: flex; flex-wrap: wrap; gap: 4px 14px; max-height: 150px; overflow: auto; margin-top: 6px; }
.cat { display: flex; gap: 4px; align-items: center; font-size: 13px; }
.cat input { width: auto; }
.cat.root { font-weight: 600; flex-basis: 100%; margin-top: 4px; }
.hint { font-size: 12px; color: var(--color-text-faint); margin: -4px 0 0; }
.lock { font-size: 13px; background: #fff7e6; color: #b35c00; padding: 8px 10px; border-radius: 4px; margin: 0; }
.preview { font-size: 13px; color: var(--color-text-muted); margin: 0; }
.error { color: #d33; font-size: 13px; margin: 0; }
</style>
