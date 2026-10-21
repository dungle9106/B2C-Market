<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Hồ Sơ Shop</h2>
      <span class="sc-card-sub">Xem tình trạng Shop và cập nhật hồ sơ Shop của bạn</span>
      <router-link v-if="form.id" :to="{ name: 'shop', params: { id: form.id } }" target="_blank" class="sc-link">Xem trang Shop ›</router-link>
    </div>

    <div class="shop-layout">
      <!-- live preview of how the shop header looks to buyers -->
      <div class="preview">
        <div class="cover" :style="form.coverUrl ? { backgroundImage: `url(${form.coverUrl})` } : null">
          <div class="preview-avatar">
            <img v-if="form.logoUrl" :src="form.logoUrl" alt="" />
            <span v-else>{{ (form.name || 'S').charAt(0).toUpperCase() }}</span>
          </div>
          <div class="preview-name">
            {{ form.name || 'Tên shop' }}
            <span v-if="form.verified" class="verified" title="Shop đã xác minh">✓</span>
          </div>
        </div>
        <p class="text-faint preview-note">Xem trước ảnh bìa và logo của Shop</p>

        <div class="status-box">
          <div class="status-row">
            <span>Xác minh</span>
            <router-link :to="{ name: 'seller-verification' }" :class="form.verified ? 'ok' : 'todo'">
              {{ form.verified ? `✓ ${BUSINESS_TYPES[form.businessType] || 'Đã xác minh'}` : 'Chưa xác minh ›' }}
            </router-link>
          </div>
          <div class="status-row">
            <span>Chính sách</span>
            <router-link :to="{ name: 'seller-policies' }">{{ returnDaysLabel(form.defaultReturnDays) }} ›</router-link>
          </div>
          <div v-if="form.createdAt" class="status-row">
            <span>Tham gia</span>
            <span>{{ new Date(form.createdAt).toLocaleDateString('vi-VN') }}</span>
          </div>
        </div>
      </div>

      <form class="sc-form" novalidate @submit.prevent="onSave">
        <label>Tên Shop <span class="req">*</span></label>
        <input v-model.trim="form.name" maxlength="60" placeholder="Nhập tên shop (3-60 ký tự, không trùng shop khác)" />

        <div class="sc-form-row">
          <div>
            <label>Logo Shop <span class="text-faint">(ảnh vuông, tối thiểu 300×300)</span></label>
            <MediaUploader v-model="logoList" :max="1" allow-url add-label="Tải logo" />
          </div>
          <div>
            <label>Ảnh bìa <span class="text-faint">(tỉ lệ khoảng 3:1)</span></label>
            <MediaUploader v-model="coverList" :max="1" allow-url add-label="Tải ảnh bìa" />
          </div>
        </div>

        <label>Mô tả Shop</label>
        <textarea v-model="form.description" rows="5" maxlength="1000" placeholder="Giới thiệu shop: sản phẩm chính, cam kết chất lượng, thời gian làm việc..."></textarea>
        <div class="counter">{{ (form.description || '').length }}/1000</div>

        <h3 class="group-title">Địa chỉ lấy hàng</h3>
        <div class="sc-form-row">
          <div>
            <label>Tỉnh/Thành phố <span class="req">*</span></label>
            <select v-model="form.province">
              <option :value="null" disabled>-- Chọn tỉnh/thành phố --</option>
              <option v-for="p in PROVINCES" :key="p" :value="p">{{ p }}</option>
            </select>
          </div>
          <div>
            <label>Số điện thoại lấy hàng <span class="req">*</span></label>
            <input v-model.trim="form.pickupPhone" type="tel" maxlength="12" placeholder="VD: 0901234567" />
          </div>
        </div>
        <div class="sc-form-row">
          <div>
            <label>Quận/Huyện</label>
            <input v-model.trim="form.pickupDistrict" maxlength="80" placeholder="VD: Quận 1" />
          </div>
          <div>
            <label>Phường/Xã</label>
            <input v-model.trim="form.pickupWard" maxlength="80" placeholder="VD: Phường Bến Nghé" />
          </div>
        </div>
        <label>Địa chỉ chi tiết <span class="req">*</span></label>
        <input v-model.trim="form.pickupAddress" maxlength="255" placeholder="Số nhà, tên đường" />
        <p class="hint">Tỉnh/thành hiển thị ở mục "Gửi từ" và dùng để tính phí vận chuyển. Địa chỉ và số điện thoại chỉ gửi cho đơn vị vận chuyển, không hiển thị công khai. Quận/huyện và phường/xã cần thiết để Giao Hàng Nhanh báo giá và đến lấy hàng.</p>

        <p v-if="error" class="form-error">{{ error }}</p>
        <div class="form-actions">
          <span v-if="saved" class="saved">✓ Đã lưu thay đổi</span>
          <button class="btn" type="submit" :disabled="saving">{{ saving ? 'Đang lưu...' : 'Lưu' }}</button>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import MediaUploader from '../../components/MediaUploader.vue'
import { getMyShop, saveMyShop } from '../../api/shop'
import { extractError } from '../../utils/apiError'
import { PROVINCES } from '../../utils/provinces'
import { BUSINESS_TYPES, returnDaysLabel } from '../../utils/shop'

const form = ref({ name: '', description: '', logoUrl: '', coverUrl: '', pickupAddress: '', pickupPhone: '', pickupDistrict: '', pickupWard: '', province: null })
const saving = ref(false)
const saved = ref(false)
const error = ref('')

// MediaUploader works with a list; the shop has one logo and one cover
const single = (key) => computed({
  get: () => (form.value[key] ? [form.value[key]] : []),
  set: (list) => { form.value[key] = list[0] || '' }
})
const logoList = single('logoUrl')
const coverList = single('coverUrl')

onMounted(async () => {
  const { data } = await getMyShop()
  form.value = { ...form.value, ...data }
})

const PHONE = /^(0|\+84)\d{9,10}$/

async function onSave() {
  saved.value = false
  const f = form.value
  error.value = !f.name || f.name.length < 3 ? 'Tên shop phải có ít nhất 3 ký tự'
    : !f.province ? 'Vui lòng chọn tỉnh/thành phố'
    : !f.pickupPhone ? 'Vui lòng nhập số điện thoại lấy hàng'
    : !PHONE.test(f.pickupPhone) ? 'Số điện thoại lấy hàng không hợp lệ'
    : !f.pickupAddress ? 'Vui lòng nhập địa chỉ lấy hàng' : ''
  if (error.value) return

  saving.value = true
  try {
    const { name, description, logoUrl, coverUrl, pickupAddress, pickupPhone, pickupDistrict, pickupWard, province } = f
    const { data } = await saveMyShop({ name, description, logoUrl, coverUrl, pickupAddress, pickupPhone,
      pickupDistrict: pickupDistrict || '', pickupWard: pickupWard || '', province })
    form.value = { ...form.value, ...data }
    saved.value = true
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.shop-layout { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 32px; align-items: start; }
.cover {
  height: 150px; border-radius: 4px;
  background: linear-gradient(135deg, var(--color-primary), #d0011b) center / cover;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px;
  color: white;
}
.preview-avatar {
  width: 64px; height: 64px; border-radius: 50%; overflow: hidden;
  border: 3px solid rgba(255, 255, 255, 0.8); background: white; color: var(--color-primary);
  display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 700;
}
.preview-avatar img { width: 100%; height: 100%; object-fit: cover; }
.preview-name { font-weight: 600; font-size: 16px; text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3); display: flex; align-items: center; gap: 6px; }
.verified { background: #2673dd; color: white; border-radius: 50%; width: 18px; height: 18px; font-size: 11px; display: inline-flex; align-items: center; justify-content: center; text-shadow: none; }
.preview-note { font-size: 12px; text-align: center; }
.status-box { border: 1px solid var(--color-border); border-radius: 4px; padding: 4px 14px; }
.status-row { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; font-size: 13px; border-bottom: 1px solid #f5f5f5; }
.status-row:last-child { border-bottom: none; }
.status-row > span:first-child { color: var(--color-text-muted); }
.status-row a { color: #2673dd; }
.status-row a.ok { color: #1e9e4a; }
.status-row a.todo { color: var(--color-primary); }
.group-title { font-size: 15px; font-weight: 600; margin: 8px 0 12px; padding-top: 14px; border-top: 1px solid var(--color-border); }
.counter { text-align: right; font-size: 12px; color: var(--color-text-faint); margin: -8px 0 4px; }
.hint { font-size: 12px; color: var(--color-text-faint); margin: -4px 0 16px; line-height: 1.5; }
.form-error { color: #d33; font-size: 13px; margin: 0 0 12px; }
.form-actions { display: flex; align-items: center; justify-content: flex-end; gap: 16px; }
.saved { color: #1e9e4a; font-size: 14px; }
.sc-form :deep(.uploader) { margin-bottom: 12px; }

@media (max-width: 900px) {
  .shop-layout { grid-template-columns: 1fr; }
}
</style>
