<template>
  <BaseModal :title="p ? p.name : 'Sản phẩm'" @close="$emit('close')">
    <div class="pm">
      <div v-if="!p" class="faint">{{ error || 'Đang tải...' }}</div>
      <template v-else>
        <div class="top">
          <div class="gallery">
            <img :src="current || placeholder" alt="" class="main" />
            <div class="thumbs">
              <img v-for="u in p.imageUrls" :key="u" :src="u" alt="" :class="{ on: u === current }" @click="current = u" />
            </div>
          </div>
          <div class="info">
            <div class="row"><span class="st" :class="p.status">{{ PRODUCT_STATUS[p.status] }}</span>
              <span v-if="!p.active" class="faint">· đang ẩn với người mua</span></div>
            <div class="price">{{ formatVnd(p.price) }} <s v-if="p.originalPrice" class="faint">{{ formatVnd(p.originalPrice) }}</s></div>
            <dl>
              <dt>Shop</dt><dd><router-link :to="`/shop/${p.shop.id}`" target="_blank">{{ p.shop.name }}</router-link>
                <span v-if="p.shopRemovedCount" class="bad"> · đã bị gỡ {{ p.shopRemovedCount }} sản phẩm</span></dd>
              <dt>Ngành hàng</dt><dd>{{ p.category || '—' }}</dd>
              <dt>Kho / Đã bán</dt><dd>{{ p.stock }} / {{ p.soldCount }}</dd>
              <dt>Đăng lúc</dt><dd>{{ formatDateTime(p.createdAt) }}</dd>
            </dl>
            <div v-if="p.variants.length" class="variants">Phân loại: {{ p.variants.map(v => v.name).join(', ') }}</div>
            <div v-if="p.reason" class="reason">{{ p.violationLabel ? p.violationLabel + ': ' : '' }}{{ p.reason }}</div>
          </div>
        </div>

        <div v-if="p.flags.length" class="block">
          <h4>Kiểm tra nội dung tự động <span class="risk" :class="riskClass(p.riskScore)">Rủi ro {{ p.riskScore }}/100</span></h4>
          <div v-for="f in p.flags" :key="f.code" class="flag" :class="f.severity">{{ SEVERITY[f.severity] }} · {{ f.label }}</div>
        </div>

        <div class="block">
          <h4>Mô tả</h4>
          <p class="desc">{{ p.description || '(Không có mô tả)' }}</p>
        </div>

        <div v-if="p.reports.length" class="block">
          <h4>Báo cáo từ người dùng ({{ p.openReports }} đang chờ)</h4>
          <div v-for="r in p.reports" :key="r.id" class="report" :class="{ closed: r.status !== 'OPEN' }">
            <strong>{{ r.reasonLabel }}</strong> · {{ r.reporter }} · <span class="faint">{{ formatDateTime(r.createdAt) }}</span>
            <span v-if="r.status !== 'OPEN'" class="faint"> · {{ r.status === 'RESOLVED' ? 'đã xử lý' : 'đã bỏ qua' }}</span>
            <div v-if="r.detail" class="faint">{{ r.detail }}</div>
          </div>
        </div>

        <div v-if="p.history.length" class="block">
          <h4>Lịch sử xử lý</h4>
          <div v-for="h in p.history" :key="h.id" class="faint">{{ formatDateTime(h.createdAt) }} · {{ AUDIT_ACTION[h.action] || h.action }} · {{ h.adminName }}<template v-if="h.detail"> — {{ h.detail }}</template></div>
        </div>

        <!-- actions -->
        <div v-if="mode" class="form">
          <template v-if="mode === 'remove'">
            <label>Loại vi phạm</label>
            <select v-model="violation">
              <option value="">-- Chọn --</option>
              <option v-for="v in violations" :key="v.key" :value="v.key">{{ v.label }}</option>
            </select>
          </template>
          <label>{{ mode === 'reject' ? 'Lý do từ chối (gửi cho người bán)' : 'Chi tiết vi phạm (gửi cho người bán)' }}</label>
          <textarea v-model="reason" rows="3" maxlength="500"></textarea>
          <div class="btns">
            <button type="button" class="btn ghost sm" @click="mode = null">Hủy</button>
            <button type="button" class="btn sm" :class="{ danger: mode === 'remove' }" :disabled="busy || reason.trim().length < 5 || (mode === 'remove' && !violation)" @click="submit">
              {{ mode === 'reject' ? 'Từ chối' : 'Gỡ sản phẩm' }}
            </button>
          </div>
        </div>
        <div v-else class="actions">
          <button v-if="p.status === 'PENDING' || p.status === 'REJECTED'" type="button" class="btn sm" :disabled="busy" @click="run(() => approveProduct(p.id))">Duyệt</button>
          <button v-if="p.status === 'PENDING'" type="button" class="btn sm ghost" @click="mode = 'reject'">Từ chối</button>
          <button v-if="p.status !== 'REMOVED'" type="button" class="btn sm ghost danger" @click="mode = 'remove'">Gỡ vi phạm</button>
          <button v-if="p.status === 'REMOVED'" type="button" class="btn sm" :disabled="busy" @click="run(() => restoreProduct(p.id))">Khôi phục</button>
          <button v-if="p.openReports" type="button" class="btn sm ghost" :disabled="busy" @click="run(() => dismissReports(p.id))">Bỏ qua báo cáo</button>
          <router-link :to="`/products/${p.id}`" target="_blank" class="link">Xem trang sản phẩm ›</router-link>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
      </template>
    </div>
  </BaseModal>
</template>

<script setup>
import { ref, watch } from 'vue'
import BaseModal from '../BaseModal.vue'
import { adminProduct, approveProduct, rejectProduct, removeProduct, restoreProduct, dismissReports } from '../../api/admin'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'
import { PRODUCT_STATUS, AUDIT_ACTION } from '../../utils/adminLabels'

const props = defineProps({
  productId: { type: Number, required: true },
  violations: { type: Array, default: () => [] },
  startMode: { type: String, default: null } // reject | remove
})
const emit = defineEmits(['close', 'changed'])

const SEVERITY = { HIGH: 'Nghiêm trọng', MEDIUM: 'Cần xem', LOW: 'Nhẹ' }
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="%23f0f0f0"/></svg>'
const p = ref(null)
const current = ref(null)
const error = ref('')
const busy = ref(false)
const mode = ref(props.startMode)
const reason = ref('')
const violation = ref('')
const riskClass = (s) => (s >= 50 ? 'high' : s >= 20 ? 'mid' : 'low')

async function load() {
  try {
    p.value = (await adminProduct(props.productId)).data
    current.value = p.value.imageUrls[0] || null
  } catch (e) {
    error.value = extractError(e)
  }
}

async function run(fn) {
  busy.value = true
  error.value = ''
  try {
    await fn()
    mode.value = null
    reason.value = ''
    await load()
    emit('changed')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

const submit = () => run(() => (mode.value === 'reject' ? rejectProduct(p.value.id, reason.value.trim())
  : removeProduct(p.value.id, violation.value, reason.value.trim())))

watch(() => props.productId, load, { immediate: true })
</script>

<style scoped>
.pm { width: min(760px, 88vw); max-height: 72vh; overflow-y: auto; font-size: 14px; }
.top { display: grid; grid-template-columns: 240px 1fr; gap: 16px; }
.main { width: 240px; height: 240px; object-fit: cover; border-radius: 4px; background: #f5f5f5; }
.thumbs { display: flex; gap: 4px; margin-top: 6px; flex-wrap: wrap; }
.thumbs img { width: 44px; height: 44px; object-fit: cover; border-radius: 3px; cursor: pointer; opacity: 0.7; }
.thumbs img.on { opacity: 1; outline: 2px solid var(--color-primary); }
.row { display: flex; align-items: center; gap: 6px; }
.price { font-size: 22px; color: var(--color-primary); margin: 8px 0; }
dl { display: grid; grid-template-columns: 100px 1fr; gap: 6px; margin: 0; }
dt { color: var(--color-text-muted); }
dd { margin: 0; }
dd a { color: #2673dd; }
.variants { margin-top: 8px; color: var(--color-text-muted); font-size: 13px; }
.reason { margin-top: 10px; background: #fdecea; color: #b42318; padding: 6px 10px; border-radius: 4px; font-size: 13px; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; }
.st.PENDING { background: #fff4e5; color: #b35c00; }
.st.APPROVED { background: #eaf7ef; color: #1e9e4a; }
.st.REJECTED { background: #f2f2f2; color: #666; }
.st.REMOVED { background: #fdecea; color: #d33; }
.block { margin-top: 16px; }
.block h4 { margin: 0 0 6px; font-size: 14px; display: flex; align-items: center; gap: 8px; }
.risk { font-size: 11px; font-weight: 500; padding: 1px 8px; border-radius: 999px; }
.risk.high { background: #fdecea; color: #d33; }
.risk.mid { background: #fff4e5; color: #b35c00; }
.risk.low { background: #f2f2f2; color: #666; }
.flag { font-size: 13px; padding: 4px 8px; border-left: 3px solid #ccc; margin-bottom: 4px; background: #fafafa; }
.flag.HIGH { border-color: #d33; }
.flag.MEDIUM { border-color: #e67e22; }
.desc { white-space: pre-line; margin: 0; line-height: 1.6; color: var(--color-text); max-height: 180px; overflow-y: auto; }
.report { padding: 6px 0; border-bottom: 1px solid #f3f3f3; }
.report.closed { opacity: 0.6; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.bad { color: #d33; font-size: 12px; }
.actions { display: flex; align-items: center; gap: 8px; margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--color-border); flex-wrap: wrap; }
.link { margin-left: auto; color: #2673dd; font-size: 13px; }
.btn.danger { color: #d33; border-color: #f0b4b4; }
.btn.sm.danger:not(.ghost) { background: #d33; color: white; }
.form { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--color-border); }
.form label { display: block; font-size: 13px; color: var(--color-text-muted); margin: 8px 0 4px; }
.form select, .form textarea { width: 100%; margin: 0; font-family: inherit; }
.btns { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 700px) { .top { grid-template-columns: 1fr; } }
</style>
