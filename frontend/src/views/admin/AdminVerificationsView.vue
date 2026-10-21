<template>
  <div>
    <div class="container page">
      <router-link to="/admin" class="back">‹ Tổng quan toàn sàn</router-link>
      <section class="shopee-section">
        <div class="head">
          <h2 class="title">Xác Minh Shop</h2>
          <nav class="tabs">
            <button type="button" :class="{ active: pending }" @click="switchTab(true)">Chờ duyệt</button>
            <button type="button" :class="{ active: !pending }" @click="switchTab(false)">Đã xử lý</button>
          </nav>
        </div>
        <p class="sub">Đối chiếu ảnh giấy tờ với thông tin khai báo. Shop được duyệt hiển thị huy hiệu "Đã xác minh" cho người mua.</p>

        <div v-if="loading" class="empty">Đang tải...</div>
        <div v-else-if="!list.length" class="empty">{{ pending ? 'Không có hồ sơ nào chờ duyệt.' : 'Chưa xử lý hồ sơ nào.' }}</div>

        <article v-for="v in list" :key="v.id" class="case">
          <header class="case-head">
            <router-link :to="{ name: 'shop', params: { id: v.shopId } }" target="_blank" class="shop">{{ v.shopName }}</router-link>
            <span class="text-faint">{{ v.sellerEmail }}</span>
            <span class="type">{{ BUSINESS_TYPES[v.businessType] }}</span>
            <span class="v-status" :class="v.status">{{ VERIFICATION_STATUS[v.status] }}</span>
          </header>
          <div class="case-body">
            <dl class="kv">
              <dt>{{ v.businessType === 'INDIVIDUAL' ? 'Họ tên' : 'Tên đăng ký' }}</dt><dd>{{ v.legalName }}</dd>
              <dt>Số CCCD</dt><dd>{{ v.idNumber }}</dd>
              <template v-if="v.taxCode"><dt>Mã số thuế</dt><dd>{{ v.taxCode }}</dd></template>
              <template v-if="v.licenseNumber"><dt>Số GCN ĐKKD</dt><dd>{{ v.licenseNumber }}</dd></template>
              <template v-if="v.businessAddress"><dt>Địa chỉ KD</dt><dd>{{ v.businessAddress }}</dd></template>
              <dt>Gửi lúc</dt><dd>{{ formatDateTime(v.submittedAt) }}</dd>
              <template v-if="v.reviewedAt"><dt>Xử lý lúc</dt><dd>{{ formatDateTime(v.reviewedAt) }}</dd></template>
              <template v-if="v.rejectReason"><dt>Lý do từ chối</dt><dd>{{ v.rejectReason }}</dd></template>
            </dl>
            <div class="docs">
              <a v-for="url in v.documentUrls" :key="url" :href="url" target="_blank" rel="noopener"><img :src="url" alt="Giấy tờ" /></a>
            </div>
          </div>
          <footer v-if="v.status === 'PENDING'" class="case-actions">
            <button type="button" class="btn ghost sm" @click="openReject(v)">Từ chối</button>
            <button type="button" class="btn sm" :disabled="busyId === v.id" @click="onApprove(v)">Duyệt & cấp huy hiệu</button>
          </footer>
        </article>
      </section>
    </div>

    <BaseModal v-if="rejecting" title="Từ chối hồ sơ xác minh" :width="480" @close="rejecting = null">
      <p class="hint">Lý do được gửi cho người bán để họ bổ sung và gửi lại.</p>
      <div class="quick">
        <button v-for="r in REASONS" :key="r" type="button" @click="reason = r">{{ r }}</button>
      </div>
      <textarea v-model.trim="reason" rows="3" maxlength="500" placeholder="Lý do từ chối..."></textarea>
      <p v-if="error" class="error">{{ error }}</p>
      <template #footer>
        <button type="button" class="btn ghost" @click="rejecting = null">Hủy</button>
        <button type="button" class="btn" :disabled="!reason || busyId" @click="onReject">Từ chối</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import { adminVerifications, approveVerification, rejectVerification } from '../../api/shop'
import { BUSINESS_TYPES, VERIFICATION_STATUS } from '../../utils/shop'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const REASONS = ['Ảnh giấy tờ bị mờ hoặc mất góc', 'Thông tin khai báo không khớp với giấy tờ', 'Thiếu Giấy chứng nhận đăng ký kinh doanh', 'Giấy tờ đã hết hạn']

const list = ref([])
const pending = ref(true)
const loading = ref(true)
const busyId = ref(null)
const rejecting = ref(null)
const reason = ref('')
const error = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await adminVerifications(pending.value)
    list.value = data
  } finally {
    loading.value = false
  }
}

function switchTab(p) {
  pending.value = p
  load()
}

async function onApprove(v) {
  if (!confirm(`Duyệt hồ sơ của "${v.shopName}"?`)) return
  busyId.value = v.id
  try {
    await approveVerification(v.id)
    await load()
  } catch (e) {
    alert(extractError(e))
  } finally {
    busyId.value = null
  }
}

function openReject(v) {
  rejecting.value = v
  reason.value = ''
  error.value = ''
}

async function onReject() {
  busyId.value = rejecting.value.id
  try {
    await rejectVerification(rejecting.value.id, reason.value)
    rejecting.value = null
    await load()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.page { padding-bottom: 40px; }
.back { display: inline-block; margin: 16px 0 0; color: var(--color-text-muted); }
.head { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.title { font-size: 19px; margin: 0; }
.tabs { display: flex; }
.tabs button { padding: 8px 14px; background: none; border: none; border-bottom: 2px solid transparent; cursor: pointer; font-size: 14px; }
.tabs button.active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: 500; }
.sub { font-size: 13px; color: var(--color-text-faint); margin: 4px 0 16px; }
.empty { text-align: center; color: var(--color-text-faint); padding: 40px 0; }

.case { border: 1px solid var(--color-border); border-radius: 4px; margin-bottom: 14px; }
.case-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 16px; background: #fafafa; border-bottom: 1px solid var(--color-border); font-size: 13px; }
.shop { font-weight: 600; font-size: 15px; color: var(--color-text); }
.shop:hover { color: var(--color-primary); }
.type { background: #e8f1fd; color: #2673dd; padding: 2px 8px; border-radius: 2px; }
.v-status { margin-left: auto; font-weight: 500; }
.v-status.PENDING { color: #d97706; }
.v-status.APPROVED { color: #1e9e4a; }
.v-status.REJECTED { color: #d33; }
.case-body { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 20px; padding: 14px 16px; }
.kv { display: grid; grid-template-columns: 120px 1fr; gap: 8px 12px; margin: 0; font-size: 14px; }
.kv dt { color: var(--color-text-muted); }
.kv dd { margin: 0; word-break: break-word; }
.docs { display: flex; flex-wrap: wrap; gap: 8px; align-content: flex-start; }
.docs img { width: 130px; height: 90px; object-fit: cover; border-radius: 4px; border: 1px solid var(--color-border); }
.case-actions { display: flex; justify-content: flex-end; gap: 10px; padding: 0 16px 14px; }
.hint { font-size: 14px; color: var(--color-text-muted); margin: 0 0 10px; }
.quick { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.quick button { background: #f5f5f5; border: none; border-radius: 999px; padding: 4px 10px; font-size: 12px; cursor: pointer; }
.quick button:hover { color: var(--color-primary); }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
@media (max-width: 800px) { .case-body { grid-template-columns: 1fr; } }
</style>
