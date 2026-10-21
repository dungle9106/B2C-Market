<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Flash Sale Toàn Sàn</h2>
        <span class="sc-card-sub">Mở khung giờ Flash Sale, người bán đăng ký sản phẩm với giá Flash Sale trước hạn, Shoppi duyệt sản phẩm lên sàn.</span>
        <button type="button" class="btn sm add" @click="open(null)">+ Tạo khung giờ</button>
      </div>

      <div class="layout">
        <aside class="list">
          <div v-if="!events.length" class="sc-empty">Chưa có khung giờ nào</div>
          <button v-for="e in events" :key="e.id" type="button" class="item" :class="{ active: selectedId === e.id }" @click="select(e.id)">
            <div class="item-top"><strong>{{ e.name }}</strong><span class="st" :class="e.status">{{ STATUS[e.status] }}</span></div>
            <div class="faint">{{ slot(e) }}</div>
            <div class="counts">
              <span v-if="e.pending" class="pending">{{ e.pending }} chờ duyệt</span>
              <span>{{ e.approved }} đã duyệt</span>
              <span v-if="e.campaignName" class="camp">🎯 {{ e.campaignName }}</span>
            </div>
          </button>
        </aside>

        <div class="panel">
          <div v-if="!selectedId" class="sc-empty">Chọn một khung giờ để xem đăng ký của người bán</div>
          <div v-else-if="!d" class="sc-empty">{{ detailError || 'Đang tải...' }}</div>
          <template v-else>
            <div class="head">
              <div>
                <h3>{{ d.name }} <span class="st" :class="d.status">{{ STATUS[d.status] }}</span></h3>
                <div class="faint">{{ slot(d) }} · Hạn đăng ký {{ formatDateTime(d.registrationDeadline) }} · Giảm tối thiểu {{ d.minDiscountPercent }}% · Tối đa {{ d.maxProductsPerShop }} sản phẩm/shop</div>
              </div>
              <div class="head-actions">
                <button v-if="['REGISTERING', 'REVIEWING'].includes(d.status)" type="button" class="btn sm ghost" @click="open(d)">Sửa</button>
                <button v-if="!['ENDED', 'CANCELLED'].includes(d.status)" type="button" class="btn sm ghost warn" @click="cancelling = true">Hủy khung giờ</button>
              </div>
            </div>
            <div class="kpis">
              <div class="kpi"><span>Shop tham gia</span><strong>{{ d.shops }}</strong></div>
              <div class="kpi"><span>Sản phẩm đã duyệt</span><strong>{{ d.counts.APPROVED }}</strong></div>
              <div class="kpi"><span>Đã bán (giá Flash Sale)</span><strong>{{ d.results.units }}</strong><small>{{ d.results.orders }} đơn</small></div>
              <div class="kpi accent"><span>Doanh số Flash Sale</span><strong>{{ formatVnd(d.results.revenue) }}</strong></div>
            </div>

            <div class="tabs-row">
              <nav class="sc-tabs">
                <a v-for="t in REG_TABS" :key="t.key" href="#" :class="{ active: regStatus === t.key }" @click.prevent="regStatus = t.key; loadDetail()">
                  {{ t.label }} <span class="count">({{ t.key === 'ALL' ? Object.values(d.counts).reduce((a, b) => a + b, 0) : d.counts[t.key] }})</span>
                </a>
              </nav>
              <button v-if="d.counts.PENDING && !['ENDED', 'CANCELLED'].includes(d.status)" type="button" class="btn sm" :disabled="busy" @click="onApproveAll">Duyệt tất cả ({{ d.counts.PENDING }})</button>
            </div>
            <p v-if="skipped.length" class="skipped">Không duyệt được: <span v-for="s in skipped" :key="s.id">{{ s.reason }}; </span></p>

            <div v-if="!d.registrations.length" class="sc-empty">Không có đăng ký nào</div>
            <table v-else class="sc-table">
              <thead><tr><th>Sản phẩm</th><th>Shop</th><th class="r">Giá gốc → Flash Sale</th><th class="r">Suất</th><th>Trạng thái</th><th></th></tr></thead>
              <tbody>
                <tr v-for="r in d.registrations" :key="r.id">
                  <td><div class="prod"><img :src="r.thumbnailUrl || ph" alt="" /><router-link :to="`/products/${r.productId}`" target="_blank">{{ r.productName }}</router-link></div></td>
                  <td>{{ r.shopName }}</td>
                  <td class="r">
                    <span class="old">{{ formatVnd(r.price) }}</span> → <b class="flash">{{ formatVnd(r.flashPrice) }}</b>
                    <div class="off">-{{ r.discountPercent }}%<span v-if="r.priceChanged" class="warn-text" title="Người bán đã đổi giá gốc sau khi đăng ký"> · giá gốc đã đổi</span></div>
                  </td>
                  <td class="r">{{ r.flashStock ?? 'Toàn bộ kho' }}<div class="faint">{{ r.maxPerBuyer ? r.maxPerBuyer + '/khách' : '' }}<template v-if="r.sold != null">{{ r.maxPerBuyer ? ' · ' : '' }}đã bán {{ r.sold }}</template></div></td>
                  <td><span class="rs" :class="r.status">{{ REG_STATUS[r.status] }}</span><div v-if="r.rejectReason" class="faint">{{ r.rejectReason }}</div></td>
                  <td class="actions">
                    <template v-if="!['ENDED', 'CANCELLED'].includes(d.status)">
                      <button v-if="r.status === 'PENDING'" type="button" class="btn sm" :disabled="busy" @click="act(() => approveFlashRegistration(r.id))">Duyệt</button>
                      <button v-if="['PENDING', 'APPROVED'].includes(r.status)" type="button" class="link danger" @click="rejecting = r">Từ chối</button>
                    </template>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="error" class="error">{{ error }}</p>
          </template>
        </div>
      </div>
    </section>

    <BaseModal v-if="form" :title="form.id ? 'Sửa khung giờ Flash Sale' : 'Tạo khung giờ Flash Sale'" :width="580" @close="form = null">
      <form id="fe-form" class="form" @submit.prevent="save">
        <p v-if="form.id && form.status !== 'REGISTERING'" class="lock">Đã hết hạn đăng ký: chỉ sửa được tên và chương trình.</p>
        <label>Tên khung giờ *<input v-model.trim="form.name" maxlength="120" placeholder="VD: Flash Sale 12h – Siêu Sale 11.11" /></label>
        <div class="row">
          <label>Bắt đầu *<input v-model="form.startAt" type="datetime-local" :disabled="locked" /></label>
          <label>Kết thúc *<input v-model="form.endAt" type="datetime-local" :disabled="locked" /></label>
        </div>
        <div class="row">
          <label>Hạn đăng ký *<input v-model="form.registrationDeadline" type="datetime-local" :disabled="locked" /></label>
          <label>Thuộc chương trình
            <select v-model="form.campaignId">
              <option :value="null">— Không —</option>
              <option v-for="c in campaigns" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </label>
        </div>
        <div class="row">
          <label>Giảm tối thiểu (%)<input v-model.number="form.minDiscountPercent" type="number" min="5" max="90" :disabled="locked" /></label>
          <label>Tối đa sản phẩm / shop<input v-model.number="form.maxProductsPerShop" type="number" min="1" max="50" :disabled="locked" /></label>
        </div>
        <p class="faint">Khung giờ dài tối đa 24 giờ, hạn đăng ký trước giờ bắt đầu ít nhất 1 giờ. Tất cả người bán được thông báo khi tạo khung giờ.</p>
        <p v-if="formError" class="error">{{ formError }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn ghost" @click="form = null">Hủy</button>
        <button type="submit" form="fe-form" class="btn" :disabled="saving">{{ saving ? 'Đang lưu...' : 'Lưu' }}</button>
      </template>
    </BaseModal>

    <ReasonDialog v-if="rejecting" title="Từ chối sản phẩm" :subtitle="rejecting.productName + ' · ' + rejecting.shopName" :presets="REJECT_REASONS"
                  confirm-label="Từ chối" :busy="busy" :error="error" @close="rejecting = null" @submit="onReject" />
    <ReasonDialog v-if="cancelling" title="Hủy khung giờ Flash Sale" :subtitle="d.name" :presets="CANCEL_REASONS"
                  placeholder="Lý do (gửi cho các shop đã đăng ký)" confirm-label="Hủy khung giờ" :busy="busy" :error="error"
                  @close="cancelling = false" @submit="onCancel" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseModal from '../../components/BaseModal.vue'
import ReasonDialog from '../../components/admin/ReasonDialog.vue'
import { adminFlashEvents, createFlashEvent, updateFlashEvent, adminFlashEvent, cancelFlashEvent, approveAllFlash,
  approveFlashRegistration, rejectFlashRegistration, adminCampaigns } from '../../api/admin'
import { formatVnd, formatDateTime, toLocalInput } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const STATUS = { REGISTERING: 'Đang nhận đăng ký', REVIEWING: 'Chờ diễn ra', RUNNING: 'Đang diễn ra', ENDED: 'Đã kết thúc', CANCELLED: 'Đã hủy' }
const REG_STATUS = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối', WITHDRAWN: 'Shop đã rút' }
const REG_TABS = [{ key: 'PENDING', label: 'Chờ duyệt' }, { key: 'APPROVED', label: 'Đã duyệt' }, { key: 'REJECTED', label: 'Từ chối' }, { key: 'ALL', label: 'Tất cả' }]
const REJECT_REASONS = ['Giá Flash Sale chưa đủ hấp dẫn so với thị trường', 'Giá gốc bị nâng trước khi giảm', 'Hình ảnh sản phẩm chưa đạt chất lượng', 'Đã đủ số lượng sản phẩm cùng ngành hàng']
const CANCEL_REASONS = ['Điều chỉnh lịch chương trình khuyến mãi', 'Không đủ sản phẩm tham gia', 'Sự cố kỹ thuật']
const ph = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const route = useRoute()
const router = useRouter()
const events = ref([])
const campaigns = ref([])
const d = ref(null)
const detailError = ref('')
const regStatus = ref('PENDING')
const skipped = ref([])
const busy = ref(false)
const error = ref('')
const form = ref(null)
const formError = ref('')
const saving = ref(false)
const rejecting = ref(null)
const cancelling = ref(false)

const selectedId = computed(() => (route.query.id ? Number(route.query.id) : null))
const locked = computed(() => !!form.value?.id && form.value.status !== 'REGISTERING')
const slot = (e) => {
  const s = new Date(e.startAt), en = new Date(e.endAt)
  const t = (x) => x.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  return `${t(s)} – ${t(en)} ngày ${s.toLocaleDateString('vi-VN')}`
}

const loadEvents = () => adminFlashEvents().then(({ data }) => { events.value = data }).catch(e => { error.value = extractError(e) })
async function loadDetail() {
  if (!selectedId.value) { d.value = null; return }
  detailError.value = ''
  try {
    d.value = (await adminFlashEvent(selectedId.value, regStatus.value)).data
  } catch (e) {
    detailError.value = extractError(e)
  }
}
const select = (id) => { regStatus.value = 'PENDING'; skipped.value = []; router.replace({ query: { id } }) }

function open(e) {
  formError.value = ''
  if (e) {
    form.value = { ...e, startAt: toLocalInput(e.startAt), endAt: toLocalInput(e.endAt), registrationDeadline: toLocalInput(e.registrationDeadline) }
    return
  }
  // default: tomorrow 12:00-14:00, registration until today 23:00
  const day = new Date(Date.now() + 86400000)
  const at = (h) => { const x = new Date(day); x.setHours(h, 0, 0, 0); return x }
  const deadline = new Date(); deadline.setHours(23, 0, 0, 0)
  form.value = { name: '', startAt: toLocalInput(at(12)), endAt: toLocalInput(at(14)), registrationDeadline: toLocalInput(deadline),
    minDiscountPercent: 10, maxProductsPerShop: 10, campaignId: null }
}

async function save() {
  saving.value = true
  formError.value = ''
  try {
    const { data } = form.value.id ? await updateFlashEvent(form.value.id, form.value) : await createFlashEvent(form.value)
    form.value = null
    await loadEvents()
    if (data.id === selectedId.value) loadDetail()
    else select(data.id)
  } catch (e) {
    formError.value = extractError(e)
  } finally {
    saving.value = false
  }
}

async function act(fn) {
  busy.value = true
  error.value = ''
  try {
    await fn()
    await loadDetail()
    loadEvents()
    return true
  } catch (e) {
    error.value = extractError(e)
    return false
  } finally {
    busy.value = false
  }
}

async function onApproveAll() {
  await act(async () => { skipped.value = (await approveAllFlash(d.value.id)).data.skipped })
}
async function onReject(reason) {
  if (await act(() => rejectFlashRegistration(rejecting.value.id, reason))) rejecting.value = null
}
async function onCancel(reason) {
  if (await act(() => cancelFlashEvent(d.value.id, reason))) cancelling.value = false
}

watch(selectedId, loadDetail, { immediate: true })
onMounted(() => {
  loadEvents()
  adminCampaigns().then(({ data }) => { campaigns.value = data.filter(c => c.status !== 'ENDED') }).catch(() => {})
})
</script>

<style scoped>
.sc-card-head { position: relative; padding-right: 160px; }
.add { position: absolute; right: 0; top: 0; }
.layout { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 16px; align-items: start; }
.list { border: 1px solid var(--color-border); border-radius: 6px; max-height: 78vh; overflow: auto; }
.item { display: block; width: 100%; text-align: left; border: none; border-bottom: 1px solid var(--color-border); background: white; padding: 10px 12px; cursor: pointer; font-size: 13px; }
.item:hover { background: #fafafa; }
.item.active { background: var(--color-primary-light); box-shadow: inset 3px 0 0 var(--color-primary); }
.item-top { display: flex; justify-content: space-between; gap: 6px; align-items: center; }
.counts { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 4px; font-size: 12px; color: var(--color-text-muted); }
.pending { color: #d33; font-weight: 600; }
.camp { color: #6941c6; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.st { font-size: 11px; padding: 1px 6px; border-radius: 3px; background: #f2f2f2; color: #666; white-space: nowrap; font-weight: normal; }
.st.REGISTERING { background: #e8f1fd; color: #2673dd; }
.st.REVIEWING { background: #fff4e5; color: #b35c00; }
.st.RUNNING { background: #fdecea; color: var(--color-primary); }
.panel { min-width: 0; }
.head { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.head h3 { margin: 0 0 4px; font-size: 17px; display: flex; gap: 8px; align-items: center; }
.head-actions { display: flex; gap: 6px; align-items: flex-start; flex-shrink: 0; white-space: nowrap; }
.btn.warn { color: #d33; border-color: #f0b4b4; }
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
.kpi span, .kpi small { font-size: 12px; color: var(--color-text-muted); }
.kpi strong { font-size: 18px; }
.kpi.accent { background: #fff6f3; border-color: #ffd9cc; }
.tabs-row { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.tabs-row .sc-tabs { flex: 1; }
.count { color: var(--color-text-faint); margin-left: 2px; }
.skipped { font-size: 12px; color: #b35c00; background: #fff7e6; padding: 6px 10px; border-radius: 4px; }
.prod { display: flex; gap: 8px; align-items: center; }
.prod img { width: 40px; height: 40px; object-fit: cover; border: 1px solid var(--color-border); }
.prod a { color: var(--color-text); }
.r { text-align: right; white-space: nowrap; }
.old { color: var(--color-text-faint); text-decoration: line-through; font-size: 12px; }
.flash { color: var(--color-primary); }
.off { font-size: 12px; color: #1e9e4a; }
.warn-text { color: #d33; }
.rs { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #f2f2f2; color: #666; white-space: nowrap; }
.rs.PENDING { background: #fff4e5; color: #b35c00; }
.rs.APPROVED { background: #eaf7ef; color: #1e9e4a; }
.rs.REJECTED { background: #fdecea; color: #d33; }
.actions { white-space: nowrap; text-align: right; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
.link.danger { color: #d33; }
.form { display: flex; flex-direction: column; gap: 10px; font-size: 13px; }
.form > label, .row label { display: flex; flex-direction: column; gap: 4px; color: var(--color-text-muted); }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.lock { font-size: 13px; background: #fff7e6; color: #b35c00; padding: 8px 10px; border-radius: 4px; margin: 0; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } .kpis { grid-template-columns: 1fr 1fr; } .sc-card-head { padding-right: 0; } .add { position: static; } }
</style>
