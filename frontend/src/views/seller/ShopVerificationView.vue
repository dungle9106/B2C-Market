<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Xác Minh Thông Tin Shop</h2>
      <span class="sc-card-sub">Không bắt buộc · Shop đã xác minh được gắn huy hiệu tin cậy trên trang shop và sản phẩm</span>
    </div>

    <div v-if="loading" class="sc-empty">Đang tải...</div>

    <!-- ===== current state ===== -->
    <template v-else>
      <div v-if="current" class="state" :class="current.status">
        <div class="state-icon">{{ current.status === 'APPROVED' ? '✓' : current.status === 'PENDING' ? '⏳' : '!' }}</div>
        <div>
          <div class="state-title">{{ VERIFICATION_STATUS[current.status] }}</div>
          <div v-if="current.status === 'PENDING'" class="state-text">Hồ sơ gửi lúc {{ formatDateTime(current.submittedAt) }}. Shoppi sẽ duyệt trong 1–2 ngày làm việc và gửi thông báo cho bạn.</div>
          <div v-else-if="current.status === 'APPROVED'" class="state-text">Duyệt lúc {{ formatDateTime(current.reviewedAt) }}. Huy hiệu "Đã xác minh" đang hiển thị với người mua.</div>
          <div v-else class="state-text">Lý do: <strong>{{ current.rejectReason }}</strong>. Vui lòng chỉnh sửa và gửi lại hồ sơ bên dưới.</div>
        </div>
      </div>

      <dl v-if="current && current.status !== 'REJECTED'" class="summary">
        <dt>Loại hình</dt><dd>{{ BUSINESS_TYPES[current.businessType] }}</dd>
        <dt>{{ current.businessType === 'INDIVIDUAL' ? 'Họ tên' : 'Tên đăng ký' }}</dt><dd>{{ current.legalName }}</dd>
        <dt>Số CCCD</dt><dd>{{ current.idNumber }}</dd>
        <template v-if="current.taxCode"><dt>Mã số thuế</dt><dd>{{ current.taxCode }}</dd></template>
        <template v-if="current.licenseNumber"><dt>Số GCN ĐKKD</dt><dd>{{ current.licenseNumber }}</dd></template>
        <template v-if="current.businessAddress"><dt>Địa chỉ kinh doanh</dt><dd>{{ current.businessAddress }}</dd></template>
        <dt>Giấy tờ</dt><dd>{{ current.documentUrls.length }} ảnh đã tải lên</dd>
      </dl>

      <!-- ===== form (first time or after a rejection) ===== -->
      <form v-if="!current || current.status === 'REJECTED'" class="sc-form" novalidate @submit.prevent="onSubmit">
        <label>Loại hình kinh doanh <span class="req">*</span></label>
        <div class="types">
          <label v-for="(label, value) in BUSINESS_TYPES" :key="value" class="type" :class="{ on: form.businessType === value }">
            <input v-model="form.businessType" type="radio" :value="value" />
            <strong>{{ label }}</strong>
            <span>{{ TYPE_HINTS[value] }}</span>
          </label>
        </div>

        <div class="sc-form-row">
          <div>
            <label>{{ isBusiness ? 'Tên hộ kinh doanh / doanh nghiệp' : 'Họ và tên (theo CCCD)' }} <span class="req">*</span></label>
            <input v-model.trim="form.legalName" maxlength="200" />
          </div>
          <div>
            <label>Số CCCD {{ isBusiness ? 'của chủ hộ / người đại diện' : '' }} <span class="req">*</span></label>
            <input v-model.trim="form.idNumber" inputmode="numeric" maxlength="12" placeholder="12 chữ số" />
          </div>
        </div>

        <div class="sc-form-row">
          <div>
            <label>Mã số thuế <span v-if="isBusiness" class="req">*</span><span v-else class="text-faint">(nếu có)</span></label>
            <input v-model.trim="form.taxCode" maxlength="14" placeholder="10 chữ số, hoặc 0123456789-001" />
          </div>
          <div v-if="isBusiness">
            <label>Số Giấy chứng nhận ĐKKD <span class="req">*</span></label>
            <input v-model.trim="form.licenseNumber" maxlength="30" />
          </div>
        </div>
        <template v-if="isBusiness">
          <label>Địa chỉ đăng ký kinh doanh <span class="req">*</span></label>
          <input v-model.trim="form.businessAddress" maxlength="300" />
        </template>

        <label>Ảnh giấy tờ <span class="req">*</span></label>
        <p class="hint">{{ isBusiness ? 'Ảnh 2 mặt CCCD và Giấy chứng nhận đăng ký kinh doanh' : 'Ảnh mặt trước và mặt sau CCCD' }} — rõ nét, đủ 4 góc, không chói sáng.</p>
        <MediaUploader v-model="form.documentUrls" :max="6" add-label="Thêm ảnh" />

        <label class="confirm">
          <input v-model="form.confirmed" type="checkbox" />
          Tôi cam kết thông tin cung cấp là chính xác và đồng ý để Shoppi sử dụng chúng cho mục đích xác minh.
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>
        <div class="form-actions">
          <button class="btn" type="submit" :disabled="busy">{{ busy ? 'Đang gửi...' : current ? 'Gửi lại hồ sơ' : 'Gửi hồ sơ xác minh' }}</button>
        </div>
      </form>
    </template>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import MediaUploader from '../../components/MediaUploader.vue'
import { myVerification, submitVerification } from '../../api/shop'
import { BUSINESS_TYPES, VERIFICATION_STATUS } from '../../utils/shop'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const TYPE_HINTS = {
  INDIVIDUAL: 'Bán hàng cá nhân, chỉ cần CCCD',
  HOUSEHOLD: 'Có giấy phép hộ kinh doanh',
  COMPANY: 'Công ty / doanh nghiệp có MST'
}

const current = ref(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const form = ref({ businessType: 'INDIVIDUAL', legalName: '', idNumber: '', taxCode: '', licenseNumber: '', businessAddress: '', documentUrls: [], confirmed: false })
const isBusiness = computed(() => form.value.businessType !== 'INDIVIDUAL')

onMounted(async () => {
  try {
    const { data } = await myVerification()
    current.value = data || null
    // after a rejection, start from what was sent (the CCCD comes back masked, so it is re-typed)
    if (data?.status === 'REJECTED') {
      const { businessType, legalName, taxCode, licenseNumber, businessAddress, documentUrls } = data
      Object.assign(form.value, { businessType, legalName, taxCode: taxCode || '', licenseNumber: licenseNumber || '', businessAddress: businessAddress || '', documentUrls: [...documentUrls] })
    }
  } finally {
    loading.value = false
  }
})

function validate() {
  const f = form.value
  if (!f.legalName) return 'Vui lòng nhập họ tên / tên doanh nghiệp'
  if (!/^\d{12}$/.test(f.idNumber)) return 'Số CCCD phải gồm 12 chữ số'
  if (f.taxCode && !/^\d{10}(-\d{3})?$/.test(f.taxCode)) return 'Mã số thuế không hợp lệ'
  if (isBusiness.value && (!f.taxCode || !f.licenseNumber || !f.businessAddress)) return 'Vui lòng nhập đủ mã số thuế, số giấy phép và địa chỉ kinh doanh'
  if (f.documentUrls.length < (isBusiness.value ? 3 : 2)) return isBusiness.value ? 'Cần ảnh 2 mặt CCCD và giấy phép kinh doanh' : 'Cần ảnh 2 mặt CCCD'
  if (!f.confirmed) return 'Vui lòng xác nhận cam kết thông tin chính xác'
  return ''
}

async function onSubmit() {
  error.value = validate()
  if (error.value) return
  busy.value = true
  try {
    const f = form.value
    const { data } = await submitVerification({ ...f, licenseNumber: isBusiness.value ? f.licenseNumber : null })
    current.value = data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.state { display: flex; gap: 14px; align-items: flex-start; padding: 16px; border-radius: 4px; margin-bottom: 18px; }
.state.PENDING { background: #fff8eb; }
.state.APPROVED { background: #eaf7ee; }
.state.REJECTED { background: #fdecea; }
.state-icon { width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0; background: white; }
.APPROVED .state-icon { color: #1e9e4a; }
.REJECTED .state-icon { color: #d33; }
.state-title { font-weight: 600; font-size: 15px; }
.state-text { font-size: 13px; color: var(--color-text-muted); margin-top: 4px; line-height: 1.5; }
.summary { display: grid; grid-template-columns: 180px 1fr; gap: 10px 16px; font-size: 14px; margin: 0; }
.summary dt { color: var(--color-text-muted); }
.summary dd { margin: 0; }

.types { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 16px; }
.type { display: flex; flex-direction: column; gap: 4px; border: 1px solid #ddd; border-radius: 4px; padding: 12px; cursor: pointer; }
.type.on { border-color: var(--color-primary); background: #fffaf8; }
.type input { display: none; }
.type span { font-size: 12px; color: var(--color-text-faint); }
.sc-form .type strong { color: var(--color-text); }
.hint { font-size: 12px; color: var(--color-text-faint); margin: -2px 0 8px; }
.confirm { display: flex; gap: 8px; align-items: flex-start; font-size: 13px; color: var(--color-text); margin-top: 14px; cursor: pointer; }
.confirm input { width: auto; margin: 2px 0 0; accent-color: var(--color-primary); }
.form-error { color: #d33; font-size: 13px; margin: 12px 0 0; }
.form-actions { display: flex; justify-content: flex-end; margin-top: 12px; }

@media (max-width: 700px) {
  .types { grid-template-columns: 1fr; }
  .summary { grid-template-columns: 1fr; }
}
</style>
