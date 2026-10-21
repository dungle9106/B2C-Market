<template>
  <div v-if="loadError" class="sc-card sc-empty">{{ loadError }}</div>

  <form v-else-if="ready" class="sc-form" novalidate @submit.prevent="onSubmit">
    <!-- moderation: why this product isn't visible, and what to fix -->
    <div v-if="moderation && moderation.moderationStatus !== 'APPROVED'" class="mod-banner" :class="moderation.moderationStatus">
      <strong>{{ { PENDING: 'Sản phẩm đang chờ Shoppi duyệt', REJECTED: 'Sản phẩm chưa được duyệt', REMOVED: 'Sản phẩm đã bị gỡ do vi phạm chính sách' }[moderation.moderationStatus] }}</strong>
      <template v-if="moderation.moderationReason"> – {{ moderation.violationLabel ? moderation.violationLabel + ': ' : '' }}{{ moderation.moderationReason }}</template>
      <div v-if="moderation.moderationStatus !== 'PENDING'" class="mod-hint">Sửa lại nội dung bên dưới, lưu, rồi bấm "Gửi duyệt lại" trong danh sách sản phẩm.</div>
    </div>
    <!-- ===== basic info ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">{{ isEdit ? 'Sửa sản phẩm' : 'Thông tin cơ bản' }}</h2>
      </div>

      <label>Hình ảnh sản phẩm <span class="req">*</span> <span class="hint">Ảnh đầu tiên là ảnh bìa · tối đa 9 ảnh, mỗi ảnh ≤ 5MB</span></label>
      <MediaUploader v-model="form.imageUrls" :max="9" allow-url cover-label="Ảnh bìa" />

      <label class="mt">Video sản phẩm <span class="hint">MP4/WEBM ≤ 30MB (tùy chọn)</span></label>
      <MediaUploader v-model="videoList" kind="video" :max="1" add-label="Thêm video" />

      <label class="mt">Tên sản phẩm <span class="req">*</span></label>
      <div class="counter-wrap">
        <input v-model="form.name" maxlength="120" placeholder="Tên sản phẩm + Thương hiệu + Model + Thông số kỹ thuật" />
        <span class="counter">{{ form.name.length }}/120</span>
      </div>

      <div class="sc-form-row">
        <div>
          <label>Mã sản phẩm (SKU) <span class="hint">không trùng trong shop, dùng khi đăng hàng loạt</span></label>
          <input v-model.trim="form.sku" maxlength="50" placeholder="VD: AOTHUN01" />
        </div>
        <div>
          <label>Danh mục của Shop <router-link :to="{ name: 'seller-categories' }" class="hint-link">Quản lý</router-link></label>
          <div v-if="shopCategories.length" class="shop-cats">
            <label v-for="c in shopCategories" :key="c.id" class="cat-chip" :class="{ on: form.shopCategoryIds.includes(c.id) }">
              <input v-model="form.shopCategoryIds" type="checkbox" :value="c.id" /> {{ c.name }}
            </label>
          </div>
          <p v-else class="hint no-cats">Shop chưa có danh mục. <router-link :to="{ name: 'seller-categories' }" class="hint-link">Tạo danh mục</router-link></p>
        </div>
      </div>

      <label>Ngành hàng</label>
      <select v-model="form.categoryId">
        <option :value="null">-- Chọn ngành hàng --</option>
        <optgroup v-for="c in categoryTree" :key="c.id" :label="c.name">
          <option :value="c.id">{{ c.name }} (chung)</option>
          <option v-for="sub in c.children" :key="sub.id" :value="sub.id">{{ sub.name }}</option>
        </optgroup>
      </select>
    </section>

    <!-- AI: seller-side description generator. Seller fills quick facts,
         AI drafts a description, seller reviews/edits before saving -
         AI never publishes automatically. -->
    <section ref="aiEl" class="sc-card ai-box" :class="{ highlight: route.query.ai }">
      <div class="sc-card-head">
        <h2 class="sc-card-title ai-title">✨ Trợ Lý AI: Gợi Ý Mô Tả Sản Phẩm</h2>
        <span class="sc-card-sub">Điền vài thông tin nhanh, AI viết bản nháp mô tả (dùng cả ngành hàng và phân loại bạn đã nhập), bạn xem lại trước khi lưu</span>
      </div>
      <div class="sc-form-row three">
        <input v-model="aiForm.material" placeholder="Chất liệu (vd: cotton 100%)" />
        <input v-model="aiForm.usage" placeholder="Công dụng (vd: mặc hằng ngày)" />
        <input v-model="aiForm.targetUser" placeholder="Đối tượng (vd: nam, 20-35 tuổi)" />
      </div>
      <textarea v-model="aiForm.highlights" rows="2" maxlength="1000" placeholder="Điểm nổi bật, mỗi dòng một ý (vd: Không nhăn khi giặt máy)"></textarea>
      <div class="ai-actions">
        <label class="tone">Giọng văn
          <select v-model="aiForm.tone">
            <option value="friendly">Thân thiện, có emoji</option>
            <option value="professional">Chuyên nghiệp</option>
            <option value="short">Ngắn gọn</option>
          </select>
        </label>
        <button class="btn secondary" type="button" :disabled="aiLoading" @click="onGenerateDescription">
          {{ aiLoading ? 'Đang viết...' : form.description ? 'Viết lại mô tả' : 'Tạo Mô Tả Bằng AI' }}
        </button>
        <span v-if="aiSource" class="ai-source">{{ aiSource === 'ai' ? '✨ Viết bởi mô hình AI' : '✨ Bản nháp theo mẫu (chưa cấu hình mô hình AI)' }}</span>
      </div>
      <div v-if="titleSuggestion && titleSuggestion !== form.name" class="title-suggest">
        Gợi ý tên sản phẩm chuẩn tìm kiếm: <strong>{{ titleSuggestion }}</strong>
        <button type="button" class="link" @click="form.name = titleSuggestion.slice(0, 120)">Dùng tên này</button>
      </div>
    </section>

    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Mô tả sản phẩm</h2>
      </div>
      <div class="counter-wrap">
        <textarea v-model="form.description" rows="8" maxlength="4000" placeholder="Mô tả chi tiết: chất liệu, kích thước, hướng dẫn sử dụng, bảo hành..."></textarea>
        <span class="counter">{{ (form.description || '').length }}/4000</span>
      </div>
    </section>

    <!-- ===== sales info ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Thông tin bán hàng</h2>
      </div>

      <div class="sc-form-row">
        <div>
          <label>Giá bán (₫) <span class="req">*</span></label>
          <input v-model.number="form.price" type="number" min="1" placeholder="Nhập giá" />
        </div>
        <div>
          <label>Giá gốc (₫) <span class="hint">hiển thị gạch ngang, phải lớn hơn giá bán</span></label>
          <input v-model.number="form.originalPrice" type="number" min="1" placeholder="Không bắt buộc" />
        </div>
      </div>
      <PriceSuggestion :name="form.name" :category-id="form.categoryId" :current-price="Number(form.price) || null" @apply="(p) => (form.price = p)" />

      <label>Phân loại hàng</label>
      <VariantEditor v-model="variantModel" :base-price="Number(form.price) || 0" :product-sku="form.sku" />

      <div v-if="!variantModel.rows.length" class="stock-field">
        <label>Kho hàng <span class="req">*</span></label>
        <input v-model.number="form.stock" type="number" min="0" placeholder="0" />
      </div>
      <p v-else class="hint block-hint">Kho của sản phẩm = tổng kho các phân loại.</p>
    </section>

    <!-- ===== flash sale ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">⚡ Flash Sale</h2>
        <label class="switch-label">
          <input v-model="flashOn" type="checkbox" />
          Bật Flash Sale cho sản phẩm này
        </label>
      </div>
      <div v-if="flashOn" class="sc-form-row three">
        <div>
          <label>Giá Flash Sale (₫) <span class="req">*</span></label>
          <input v-model.number="form.flashSalePrice" type="number" min="1" placeholder="Thấp hơn giá bán" />
        </div>
        <div>
          <label>Bắt đầu <span class="req">*</span></label>
          <input v-model="form.flashSaleStart" type="datetime-local" />
        </div>
        <div>
          <label>Kết thúc <span class="req">*</span></label>
          <input v-model="form.flashSaleEnd" type="datetime-local" />
        </div>
      </div>
      <div v-if="flashOn" class="sc-form-row">
        <div>
          <label>Số suất Flash Sale <span class="hint">trống = bán giá sốc đến hết kho</span></label>
          <input v-model.number="form.flashSaleStock" type="number" min="1" placeholder="VD: 50" />
        </div>
        <div>
          <label>Tối đa mỗi khách <span class="hint">trống = không giới hạn</span></label>
          <input v-model.number="form.flashSaleMaxPerBuyer" type="number" min="1" placeholder="VD: 2" />
        </div>
      </div>
      <p v-if="flashOn && form.flashSaleSold" class="hint">Đã bán {{ form.flashSaleSold }} suất trong đợt này. <router-link :to="{ name: 'seller-flash-sale' }">Quản lý Flash Sale</router-link></p>
      <p v-else class="hint">Sản phẩm đang Flash Sale được hiển thị ở mục FLASH SALE trên trang chủ kèm đồng hồ đếm ngược.</p>
    </section>

    <!-- ===== shipping & returns ===== -->
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Vận chuyển & Đổi trả</h2>
      </div>
      <label class="switch-label">
        <input v-model="form.freeShipping" type="checkbox" />
        Miễn phí vận chuyển (Freeship) — shop chịu phí ship ₫20.000/đơn
      </label>
      <label class="mt">Chính sách đổi trả</label>
      <select v-model.number="form.returnDays" class="narrow">
        <option :value="0">Không hỗ trợ đổi trả</option>
        <option v-for="d in [3, 7, 15, 30]" :key="d" :value="d">Đổi trả trong {{ d }} ngày</option>
      </select>
      <p class="policy-hint">Mặc định theo <router-link :to="{ name: 'seller-policies' }">Chính sách Shop</router-link></p>
    </section>

    <div class="sticky-actions">
      <p v-if="error" class="form-error">{{ error }}</p>
      <router-link :to="{ name: 'seller-products' }" class="btn ghost">Hủy</router-link>
      <button class="btn" type="submit" :disabled="saving">{{ saving ? 'Đang lưu...' : isEdit ? 'Lưu thay đổi' : 'Lưu & Hiển thị' }}</button>
    </div>
  </form>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MediaUploader from '../../components/MediaUploader.vue'
import { createProduct, updateProduct, myProduct, categoryTree as fetchCategoryTree, myShopCategories } from '../../api/products'
import VariantEditor from '../../components/product/VariantEditor.vue'
import { emptyVariantModel, toVariantModel, fromVariantModel, validateVariantModel } from '../../utils/variants'
import { generateDescription } from '../../api/ai'
import PriceSuggestion from '../../components/product/PriceSuggestion.vue'
import { getMyShop } from '../../api/shop'
import { extractError } from '../../utils/apiError'
import { toLocalInput } from '../../utils/format'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)

const categoryTree = ref([])
const ready = ref(false)
const loadError = ref('')
const aiLoading = ref(false)
const saving = ref(false)
const error = ref('')
const aiEl = ref(null)
const flashOn = ref(false)

const aiForm = ref({ material: '', usage: '', targetUser: '', highlights: '', tone: 'friendly' })
const aiSource = ref('')
const moderation = ref(null) // edit mode: moderation state of the product
const titleSuggestion = ref('')
const form = ref({
  name: '', sku: '', description: '', price: null, originalPrice: null, stock: 0, categoryId: null, shopCategoryIds: [],
  imageUrls: [], videoUrl: null, freeShipping: false, returnDays: 7,
  flashSalePrice: null, flashSaleStart: '', flashSaleEnd: '', flashSaleStock: null, flashSaleMaxPerBuyer: null
})
const variantModel = ref(emptyVariantModel())
const shopCategories = ref([])

// MediaUploader works with a list; the product has at most one video
const videoList = computed({
  get: () => (form.value.videoUrl ? [form.value.videoUrl] : []),
  set: (list) => { form.value.videoUrl = list[0] || null }
})


async function onGenerateDescription() {
  if (!form.value.name.trim()) {
    error.value = 'Vui lòng nhập tên sản phẩm trước khi dùng AI'
    return
  }
  error.value = ''
  aiLoading.value = true
  try {
    // variant groups as "Màu sắc: Đỏ, Xanh" so the draft lists them
    const variants = variantModel.value.tiers.filter(t => t.name && t.options.length)
      .map(t => `${t.name}: ${t.options.map(o => o.value).filter(Boolean).join(', ')}`)
    const { data } = await generateDescription({ productName: form.value.name, categoryId: form.value.categoryId, variants, ...aiForm.value })
    form.value.description = data.reply
    aiSource.value = data.source
    titleSuggestion.value = data.titleSuggestion || ''
  } catch (e) {
    error.value = extractError(e)
  } finally {
    aiLoading.value = false
  }
}

function validate() {
  const f = form.value
  if (!f.imageUrls.length) return 'Vui lòng thêm ít nhất 1 hình ảnh sản phẩm'
  if (!f.name.trim()) return 'Vui lòng nhập tên sản phẩm'
  if (!(f.price > 0)) return 'Vui lòng nhập giá bán lớn hơn 0'
  if (f.originalPrice && f.originalPrice <= f.price) return 'Giá gốc phải lớn hơn giá bán'
  if (f.sku && !/^[A-Za-z0-9._-]+$/.test(f.sku)) return 'SKU chỉ gồm chữ, số và . _ -'
  const variantError = validateVariantModel(variantModel.value)
  if (variantError) return variantError
  if (!variantModel.value.rows.length && !(f.stock >= 0)) return 'Vui lòng nhập kho hàng'
  if (flashOn.value) {
    if (!(f.flashSalePrice > 0) || !f.flashSaleStart || !f.flashSaleEnd) return 'Vui lòng nhập đủ giá và thời gian Flash Sale'
    if (f.flashSalePrice >= f.price) return 'Giá Flash Sale phải thấp hơn giá bán'
    if (new Date(f.flashSaleEnd) <= new Date(f.flashSaleStart)) return 'Thời gian kết thúc phải sau thời gian bắt đầu'
  }
  return ''
}

async function onSubmit() {
  error.value = validate()
  if (error.value) return
  const f = form.value
  const payload = {
    ...f,
    name: f.name.trim(),
    originalPrice: f.originalPrice || null,
    flashSalePrice: flashOn.value ? f.flashSalePrice : null,
    flashSaleStart: flashOn.value ? f.flashSaleStart : null,
    flashSaleEnd: flashOn.value ? f.flashSaleEnd : null,
    flashSaleStock: flashOn.value ? f.flashSaleStock || null : null,
    flashSaleMaxPerBuyer: flashOn.value ? f.flashSaleMaxPerBuyer || null : null,
    sku: f.sku || null,
    ...fromVariantModel(variantModel.value)
  }
  saving.value = true
  try {
    if (isEdit.value) await updateProduct(route.params.id, payload)
    else await createProduct(payload)
    router.push({ name: 'seller-products' })
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}

// a new product starts with the shop's return policy (Chinh sach shop)
async function useShopDefaults() {
  try {
    const { data: shop } = await getMyShop()
    if (shop.defaultReturnDays != null) form.value.returnDays = shop.defaultReturnDays
  } catch {
    // keep the built-in default
  }
}

onMounted(async () => {
  try {
    const [{ data: tree }, { data: cats }] = await Promise.all([
      fetchCategoryTree(),
      myShopCategories(),
      isEdit.value ? loadProduct() : useShopDefaults()
    ])
    categoryTree.value = tree
    shopCategories.value = cats
  } catch (e) {
    loadError.value = extractError(e)
    return
  }
  ready.value = true

  // "Cong cu AI viet mo ta" in the sidebar lands here with ?ai=1
  if (route.query.ai) {
    await nextTick()
    aiEl.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
})

async function loadProduct() {
  const { data: p } = await myProduct(route.params.id)
  moderation.value = p
  form.value = {
    name: p.name,
    sku: p.sku || '',
    shopCategoryIds: [...(p.shopCategoryIds || [])],
    description: p.description || '',
    price: Number(p.basePrice),
    originalPrice: p.listPrice ? Number(p.listPrice) : null,
    stock: p.stock,
    categoryId: p.categoryId,
    imageUrls: [...p.imageUrls],
    videoUrl: p.videoUrl,
    freeShipping: p.freeShipping,
    returnDays: p.returnDays,
    flashSalePrice: p.flashSalePrice ? Number(p.flashSalePrice) : null,
    flashSaleStart: toLocalInput(p.flashSaleStart),
    flashSaleEnd: toLocalInput(p.flashSaleEnd),
    flashSaleStock: p.flashSaleStock,
    flashSaleMaxPerBuyer: p.flashSaleMaxPerBuyer,
    flashSaleSold: p.flashSaleSold
  }
  variantModel.value = toVariantModel(p)
  flashOn.value = !!p.flashSalePrice
}
</script>

<style scoped>
.hint { font-size: 12px; color: var(--color-text-faint); font-weight: 400; }
.block-hint { margin: 0; }
.mt { margin-top: 18px; }
.counter-wrap { position: relative; }
.counter-wrap input { padding-right: 70px; }
.counter { position: absolute; right: 10px; top: 10px; font-size: 12px; color: var(--color-text-faint); }
.counter-wrap textarea + .counter { top: auto; bottom: 22px; }

.ai-box { background: linear-gradient(135deg, #fff8f6, #fff); border: 1px dashed var(--color-primary); }
.ai-box.highlight { box-shadow: 0 0 0 3px var(--color-primary-light); }
.ai-title { color: var(--color-primary); }
.ai-box textarea { margin: 0 0 10px; }
.ai-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.tone { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--color-text-muted); margin: 0; }
.tone select { margin: 0; width: auto; padding: 6px 8px; }
.ai-source { font-size: 12px; color: #6941c6; }
.title-suggest { margin-top: 10px; font-size: 13px; background: white; border: 1px solid var(--color-border); border-radius: 4px; padding: 8px 10px; }
.title-suggest .link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
.sc-form-row.three { grid-template-columns: repeat(3, 1fr); gap: 12px; }

.hint-link { font-size: 12px; color: #2673dd; margin-left: 6px; font-weight: 400; }
.shop-cats { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.cat-chip { display: inline-flex !important; align-items: center; gap: 4px; border: 1px solid #ddd; border-radius: 999px; padding: 5px 12px; font-size: 13px; cursor: pointer; color: var(--color-text) !important; margin: 0 !important; }
.cat-chip.on { border-color: var(--color-primary); color: var(--color-primary) !important; background: var(--color-primary-light); }
.cat-chip input { display: none; }
.no-cats { margin: 10px 0 12px; }
.stock-field { max-width: 50%; }

.switch-label { display: flex; align-items: center; gap: 8px; cursor: pointer; color: var(--color-text) !important; font-size: 14px; margin: 0; }
.switch-label input { width: auto; margin: 0; accent-color: var(--color-primary); }
.sc-card-head .switch-label { margin-left: auto; }
.narrow { max-width: 320px; }

.sticky-actions {
  position: sticky; bottom: 0; z-index: 5;
  display: flex; align-items: center; justify-content: flex-end; gap: 12px;
  background: var(--color-surface);
  padding: 14px 24px;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
  border-radius: 4px;
}
.form-error { color: #d33; font-size: 13px; margin: 0 auto 0 0; }

@media (max-width: 640px) {
  .sc-form-row.three { grid-template-columns: 1fr; }
  .stock-field { max-width: none; }
}
.policy-hint { font-size: 12px; color: var(--color-text-faint); margin: 6px 0 0; }
.policy-hint a { color: #2673dd; }
.mod-banner { border-radius: 6px; padding: 10px 14px; margin-bottom: 16px; font-size: 14px; line-height: 1.6; }
.mod-banner.PENDING { background: #fff8e6; color: #8a5a00; border: 1px solid #ffe2a8; }
.mod-banner.REJECTED, .mod-banner.REMOVED { background: #fdecea; color: #b42318; border: 1px solid #f5c2bd; }
.mod-hint { font-size: 13px; }
</style>
