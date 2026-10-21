<template>
  <form novalidate @submit.prevent="onSave">
    <!-- ===== returns ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Chính Sách Đổi Trả</h2>
        <span class="sc-card-sub">Hiển thị trên trang Shop và trang của từng sản phẩm</span>
      </div>

      <label class="lbl">Thời hạn đổi trả mặc định cho sản phẩm mới</label>
      <div class="chips">
        <button v-for="d in RETURN_DAY_OPTIONS" :key="d" type="button" :class="{ on: form.defaultReturnDays === d }" @click="form.defaultReturnDays = d">
          {{ d > 0 ? `${d} ngày` : 'Không đổi trả' }}
        </button>
      </div>
      <p class="hint">Người mua gửi yêu cầu Trả hàng/Hoàn tiền trong thời hạn này, tính từ khi nhận hàng. Mỗi sản phẩm vẫn có thể đặt thời hạn riêng.</p>
      <label class="check">
        <input v-model="form.applyReturnDaysToAllProducts" type="checkbox" />
        Áp dụng thời hạn này cho <strong>tất cả sản phẩm hiện có</strong> của shop
      </label>

      <label class="lbl">Điều kiện đổi trả</label>
      <textarea v-model="form.returnConditions" rows="4" maxlength="1000" placeholder="VD: Sản phẩm còn nguyên tem mác, chưa qua sử dụng; quay video khi mở hàng; shop chịu phí ship nếu lỗi do shop..."></textarea>
      <div class="templates">
        Mẫu nhanh:
        <button v-for="t in TEMPLATES" :key="t" type="button" @click="addTemplate(t)">+ {{ t.split(';')[0] }}</button>
      </div>
    </section>

    <!-- ===== shipping ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Cài Đặt Vận Chuyển</h2>
        <span class="sc-card-sub">Áp dụng khi người mua đặt hàng của shop</span>
      </div>

      <label class="lbl">Đơn vị vận chuyển <span class="req">*</span></label>
      <div class="carriers">
        <label v-for="c in carriers" :key="c.code" class="carrier" :class="{ on: form.carriers.includes(c.code) }">
          <input type="checkbox" :checked="form.carriers.includes(c.code)" @change="toggleCarrier(c.code)" />
          <div>
            <div class="carrier-name">{{ c.name }}</div>
            <div class="carrier-methods">{{ c.methods.join(' · ') }}</div>
          </div>
        </label>
      </div>
      <p class="hint">Người mua chỉ thấy các đơn vị shop bật. Gói Hỏa tốc chỉ có khi người mua cùng tỉnh với shop.</p>

      <div class="sc-form-row">
        <div>
          <label class="lbl">Thời gian chuẩn bị hàng</label>
          <select v-model.number="form.handlingDays">
            <option v-for="d in [1, 2, 3]" :key="d" :value="d">{{ d }} ngày</option>
          </select>
          <p class="hint">Thời gian dự kiến giao hàng hiển thị cho người mua sẽ cộng thêm thời gian này.</p>
        </div>
        <div>
          <label class="lbl">Miễn phí vận chuyển cho đơn từ</label>
          <div class="money">
            <span>₫</span>
            <input v-model.number="form.freeShipMinOrder" type="number" min="0" step="1000" placeholder="Để trống nếu không áp dụng" />
          </div>
          <p class="hint">Shop chi trả phí ship (tối đa bằng phí gói Tiết kiệm) khi tổng tiền hàng của shop đạt mức này.</p>
        </div>
      </div>
    </section>

    <div class="sc-card actions">
      <span v-if="saved" class="saved">✓ Đã lưu chính sách{{ appliedAll ? ' và cập nhật tất cả sản phẩm' : '' }}</span>
      <p v-if="error" class="form-error">{{ error }}</p>
      <button class="btn" type="submit" :disabled="saving || !loaded">{{ saving ? 'Đang lưu...' : 'Lưu chính sách' }}</button>
    </div>
  </form>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyShop, saveShopPolicies, carrierOptions } from '../../api/shop'
import { extractError } from '../../utils/apiError'
import { RETURN_DAY_OPTIONS } from '../../utils/shop'

const TEMPLATES = [
  'Sản phẩm còn nguyên tem mác, chưa qua sử dụng; kèm đầy đủ phụ kiện và quà tặng (nếu có).',
  'Vui lòng quay video khi mở hàng để làm bằng chứng khi cần đổi trả.',
  'Shop chịu phí vận chuyển đổi trả nếu sản phẩm lỗi hoặc giao sai.',
  'Không áp dụng đổi trả với đồ lót, mỹ phẩm đã mở niêm phong.'
]

const form = ref({ defaultReturnDays: 7, returnConditions: '', applyReturnDaysToAllProducts: false, carriers: [], handlingDays: 1, freeShipMinOrder: null })
const carriers = ref([])
const loaded = ref(false)
const saving = ref(false)
const saved = ref(false)
const appliedAll = ref(false)
const error = ref('')

function toggleCarrier(code) {
  const list = form.value.carriers
  form.value.carriers = list.includes(code) ? list.filter(c => c !== code) : [...list, code]
}

function addTemplate(t) {
  const cur = (form.value.returnConditions || '').trim()
  if (cur.includes(t)) return
  form.value.returnConditions = cur ? `${cur}\n${t}` : t
}

onMounted(async () => {
  const [{ data: shop }, { data: options }] = await Promise.all([getMyShop(), carrierOptions()])
  carriers.value = options
  form.value = {
    defaultReturnDays: shop.defaultReturnDays ?? 7,
    returnConditions: shop.returnConditions || '',
    applyReturnDaysToAllProducts: false,
    carriers: shop.carriers,
    handlingDays: shop.handlingDays ?? 1,
    freeShipMinOrder: shop.freeShipMinOrder
  }
  loaded.value = true
})

async function onSave() {
  saved.value = false
  error.value = ''
  const f = form.value
  if (!f.carriers.length) return (error.value = 'Vui lòng bật ít nhất một đơn vị vận chuyển')
  if (f.freeShipMinOrder && f.freeShipMinOrder < 10000) return (error.value = 'Giá trị đơn tối thiểu để miễn phí vận chuyển phải từ ₫10.000')
  if (f.applyReturnDaysToAllProducts && !confirm('Cập nhật thời hạn đổi trả của TẤT CẢ sản phẩm hiện có theo thiết lập mới?')) return

  saving.value = true
  try {
    await saveShopPolicies({ ...f, freeShipMinOrder: f.freeShipMinOrder || null })
    appliedAll.value = f.applyReturnDaysToAllProducts
    f.applyReturnDaysToAllProducts = false
    saved.value = true
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.lbl { display: block; font-size: 14px; color: var(--color-text-muted); margin: 4px 0 8px; }
.req { color: var(--color-primary); }
.hint { font-size: 12px; color: var(--color-text-faint); margin: 0 0 14px; line-height: 1.5; }
.chips { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.chips button { padding: 7px 16px; border: 1px solid #ddd; border-radius: 3px; background: white; cursor: pointer; font-size: 14px; }
.chips button.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.check { display: flex; align-items: center; gap: 8px; font-size: 14px; margin-bottom: 18px; cursor: pointer; }
.check input { width: auto; margin: 0; accent-color: var(--color-primary); }
.templates { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; font-size: 12px; color: var(--color-text-faint); margin-top: -4px; }
.templates button { background: #f5f5f5; border: none; border-radius: 999px; padding: 4px 10px; font-size: 12px; cursor: pointer; color: var(--color-text-muted); max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.templates button:hover { color: var(--color-primary); }

.carriers { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px; margin-bottom: 8px; }
.carrier { display: flex; align-items: center; gap: 10px; border: 1px solid #ddd; border-radius: 4px; padding: 12px; cursor: pointer; }
.carrier.on { border-color: var(--color-primary); background: #fffaf8; }
.carrier input { width: auto; margin: 0; accent-color: var(--color-primary); }
.carrier-name { font-weight: 500; }
.carrier-methods { font-size: 12px; color: var(--color-text-faint); margin-top: 2px; }
.money { display: flex; align-items: center; border: 1px solid #ddd; border-radius: var(--radius-sm); padding-left: 10px; margin-bottom: 12px; }
.money input { border: none; margin: 0; }
.money span { color: var(--color-text-faint); }

.actions { display: flex; align-items: center; justify-content: flex-end; gap: 16px; flex-wrap: wrap; }
.saved { color: #1e9e4a; font-size: 14px; }
.form-error { color: #d33; font-size: 13px; margin: 0; }
</style>
