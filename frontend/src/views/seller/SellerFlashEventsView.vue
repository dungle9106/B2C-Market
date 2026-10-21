<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Flash Sale Của Shoppi</h2>
        <span class="sc-card-sub">Đăng ký sản phẩm vào các khung giờ Flash Sale toàn sàn để xuất hiện ở trang chủ. Shoppi duyệt sản phẩm trước giờ bắt đầu.</span>
      </div>

      <div v-if="!data" class="sc-empty">{{ error || 'Đang tải...' }}</div>
      <template v-else>
        <div v-if="!data.events.length" class="sc-empty">Hiện chưa có khung giờ Flash Sale nào. Shoppi sẽ thông báo khi mở đăng ký.</div>
        <article v-for="e in data.events" :key="e.id" class="event">
          <header>
            <div>
              <h3>⚡ {{ e.name }} <span class="st" :class="e.status">{{ STATUS[e.status] }}</span></h3>
              <div class="meta">
                <b>{{ slot(e) }}</b> · Giảm tối thiểu {{ e.minDiscountPercent }}% · Tối đa {{ e.maxProductsPerShop }} sản phẩm/shop
                <template v-if="e.status === 'REGISTERING'"> · Hạn đăng ký <b class="deadline">{{ formatDateTime(e.registrationDeadline) }}</b></template>
              </div>
            </div>
            <button v-if="e.status === 'REGISTERING' && e.used < e.maxProductsPerShop" type="button" class="btn sm" @click="openPicker(e)">
              + Đăng ký sản phẩm ({{ e.used }}/{{ e.maxProductsPerShop }})
            </button>
          </header>
          <table v-if="e.registrations.length" class="sc-table">
            <thead><tr><th>Sản phẩm</th><th class="r">Giá Flash Sale</th><th class="r">Suất</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>
              <tr v-for="r in e.registrations" :key="r.id">
                <td><div class="prod"><img :src="r.thumbnailUrl || ph" alt="" />{{ r.productName }}</div></td>
                <td class="r"><span class="old">{{ formatVnd(r.price) }}</span> <b class="flash">{{ formatVnd(r.flashPrice) }}</b> <span class="off">-{{ r.discountPercent }}%</span></td>
                <td class="r">{{ r.flashStock ?? 'Toàn bộ kho' }}<div class="faint">{{ r.maxPerBuyer ? r.maxPerBuyer + '/khách' : '' }}<template v-if="r.sold != null">{{ r.maxPerBuyer ? ' · ' : '' }}đã bán {{ r.sold }}</template></div></td>
                <td><span class="rs" :class="r.status">{{ REG_STATUS[r.status] }}</span><div v-if="r.rejectReason" class="reason">{{ r.rejectReason }}</div></td>
                <td class="r">
                  <button v-if="['PENDING', 'APPROVED'].includes(r.status) && e.status !== 'RUNNING'" type="button" class="link" @click="onWithdraw(r)">Rút</button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="faint pad">Shop chưa đăng ký sản phẩm nào cho khung giờ này.</p>
        </article>

        <template v-if="data.history.length">
          <h3 class="sec">Đã tham gia</h3>
          <table class="sc-table">
            <thead><tr><th>Khung giờ</th><th>Sản phẩm</th><th class="r">Giá Flash Sale</th><th>Kết quả</th></tr></thead>
            <tbody>
              <tr v-for="r in data.history" :key="r.id">
                <td>{{ r.eventName }}<div class="faint">{{ formatDateTime(r.startAt) }}</div></td>
                <td>{{ r.productName }}</td>
                <td class="r">{{ formatVnd(r.flashPrice) }}</td>
                <td><span class="rs" :class="r.status">{{ REG_STATUS[r.status] }}</span><template v-if="r.eventStatus === 'CANCELLED'"> · khung giờ bị hủy</template></td>
              </tr>
            </tbody>
          </table>
        </template>
      </template>
      <p v-if="error && data" class="error">{{ error }}</p>
    </section>

    <BaseModal v-if="picking" :title="`Đăng ký: ${picking.name}`" :width="820" @close="picking = null">
      <p class="hint">Giá Flash Sale phải thấp hơn giá bán ít nhất <b>{{ picking.minDiscountPercent }}%</b>. Còn được đăng ký {{ picking.maxProductsPerShop - picking.used }} sản phẩm.</p>
      <input v-model.trim="filter" placeholder="Tìm sản phẩm" class="filter" />
      <div class="picker">
        <div v-for="p in candidates" :key="p.id" class="pick" :class="{ on: chosen[p.id] }">
          <label class="pick-head">
            <input type="checkbox" :checked="!!chosen[p.id]" :disabled="!chosen[p.id] && chosenCount >= picking.maxProductsPerShop - picking.used" @change="toggle(p)" />
            <img :src="p.thumbnailUrl || ph" alt="" />
            <span class="name">{{ p.name }}<span class="faint">{{ formatVnd(p.price) }} · kho {{ p.stock }}</span></span>
          </label>
          <div v-if="chosen[p.id]" class="terms">
            <label>Giá Flash Sale<input v-model.number="chosen[p.id].flashPrice" type="number" min="1000" :max="ceiling(p)" /></label>
            <span class="pct" :class="{ bad: chosen[p.id].flashPrice > ceiling(p) }">-{{ pct(chosen[p.id].flashPrice, p.price) }}%</span>
            <label>Số suất<input v-model.number="chosen[p.id].flashStock" type="number" min="1" :max="p.stock" placeholder="Toàn bộ kho" /></label>
            <label>Tối đa/khách<input v-model.number="chosen[p.id].maxPerBuyer" type="number" min="1" placeholder="Không giới hạn" /></label>
          </div>
        </div>
        <p v-if="!candidates.length" class="faint pad">Không có sản phẩm phù hợp (sản phẩm cần đang bán, đã được duyệt và còn hàng).</p>
      </div>
      <p v-if="pickError" class="error">{{ pickError }}</p>
      <template #footer>
        <button type="button" class="btn ghost" @click="picking = null">Hủy</button>
        <button type="button" class="btn" :disabled="!chosenCount || saving" @click="submit">{{ saving ? 'Đang gửi...' : `Gửi đăng ký ${chosenCount} sản phẩm` }}</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import { sellerFlashEvents, registerFlashEvent, withdrawFlashRegistration } from '../../api/marketing'
import { myProducts } from '../../api/products'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const STATUS = { REGISTERING: 'Đang nhận đăng ký', REVIEWING: 'Đã hết hạn đăng ký', RUNNING: 'Đang diễn ra' }
const REG_STATUS = { PENDING: 'Chờ Shoppi duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Không được duyệt', WITHDRAWN: 'Đã rút' }
const ph = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const data = ref(null)
const error = ref('')
const products = ref([])
const picking = ref(null)
const chosen = ref({})
const filter = ref('')
const pickError = ref('')
const saving = ref(false)

const slot = (e) => {
  const s = new Date(e.startAt), en = new Date(e.endAt)
  const t = (x) => x.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  return `${t(s)} – ${t(en)} ngày ${s.toLocaleDateString('vi-VN')}`
}
const ceiling = (p) => Math.floor(p.price * (100 - picking.value.minDiscountPercent) / 100)
const pct = (flash, price) => (flash ? Math.round((1 - flash / price) * 100) : 0)
const chosenCount = computed(() => Object.keys(chosen.value).length)
const candidates = computed(() => {
  if (!picking.value) return []
  const taken = new Set(picking.value.registrations.filter(r => ['PENDING', 'APPROVED'].includes(r.status)).map(r => r.productId))
  const q = filter.value.toLowerCase()
  return products.value.filter(p => p.active !== false && (p.moderationStatus || 'APPROVED') === 'APPROVED' && p.stock > 0
    && !taken.has(p.id) && (!q || p.name.toLowerCase().includes(q)))
})

async function load() {
  try {
    data.value = (await sellerFlashEvents()).data
  } catch (e) {
    error.value = extractError(e)
  }
}

async function openPicker(e) {
  picking.value = e
  chosen.value = {}
  pickError.value = ''
  if (!products.value.length) products.value = (await myProducts()).data
}

function toggle(p) {
  if (chosen.value[p.id]) delete chosen.value[p.id]
  // suggest the highest allowed flash price, rounded down to 1.000
  else chosen.value[p.id] = { flashPrice: Math.floor(ceiling(p) / 1000) * 1000, flashStock: Math.min(p.stock, 50), maxPerBuyer: 2 }
}

async function submit() {
  saving.value = true
  pickError.value = ''
  try {
    const items = Object.entries(chosen.value).map(([id, t]) => ({ productId: Number(id), flashPrice: t.flashPrice, flashStock: t.flashStock || null, maxPerBuyer: t.maxPerBuyer || null }))
    await registerFlashEvent(picking.value.id, items)
    picking.value = null
    load()
  } catch (e) {
    pickError.value = extractError(e)
  } finally {
    saving.value = false
  }
}

async function onWithdraw(r) {
  if (!confirm(`Rút "${r.productName}" khỏi Flash Sale?`)) return
  try {
    await withdrawFlashRegistration(r.id)
    load()
  } catch (e) {
    error.value = extractError(e)
  }
}

onMounted(load)
</script>

<style scoped>
.event { border: 1px solid var(--color-border); border-radius: 6px; margin-bottom: 14px; overflow: hidden; }
.event header { display: flex; justify-content: space-between; gap: 12px; align-items: center; padding: 12px 14px; background: linear-gradient(90deg, #fff4ef, #fff); }
.event h3 { margin: 0 0 4px; font-size: 16px; display: flex; gap: 8px; align-items: center; color: var(--color-primary); }
.meta { font-size: 13px; color: var(--color-text-muted); }
.deadline { color: #d33; }
.st { font-size: 11px; padding: 1px 6px; border-radius: 3px; background: #e8f1fd; color: #2673dd; font-weight: normal; }
.st.REVIEWING { background: #fff4e5; color: #b35c00; }
.st.RUNNING { background: #fdecea; color: var(--color-primary); }
.prod { display: flex; gap: 8px; align-items: center; }
.prod img { width: 40px; height: 40px; object-fit: cover; border: 1px solid var(--color-border); }
.r { text-align: right; white-space: nowrap; }
.old { color: var(--color-text-faint); text-decoration: line-through; font-size: 12px; }
.flash { color: var(--color-primary); }
.off { font-size: 12px; color: #1e9e4a; }
.rs { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #fff4e5; color: #b35c00; white-space: nowrap; }
.rs.APPROVED { background: #eaf7ef; color: #1e9e4a; }
.rs.REJECTED { background: #fdecea; color: #d33; }
.rs.WITHDRAWN { background: #f2f2f2; color: #666; }
.reason { font-size: 12px; color: #d33; margin-top: 2px; }
.faint { color: var(--color-text-faint); font-size: 12px; display: block; }
.pad { padding: 12px 14px; margin: 0; }
.sec { font-size: 15px; margin: 18px 0 8px; }
.link { background: none; border: none; color: #d33; cursor: pointer; font-size: 13px; }
.hint { font-size: 14px; margin: 0 0 10px; }
.filter { margin-bottom: 10px; }
.picker { max-height: 52vh; overflow: auto; border: 1px solid var(--color-border); border-radius: 6px; }
.pick { padding: 8px 12px; border-bottom: 1px solid var(--color-border); }
.pick.on { background: #fffaf8; }
.pick-head { display: flex; gap: 10px; align-items: center; cursor: pointer; font-size: 14px; }
.pick-head input { width: auto; }
.pick-head img { width: 40px; height: 40px; object-fit: cover; border: 1px solid var(--color-border); }
.name { display: flex; flex-direction: column; }
.terms { display: flex; gap: 12px; align-items: flex-end; margin: 8px 0 0 30px; flex-wrap: wrap; }
.terms label { display: flex; flex-direction: column; gap: 3px; font-size: 12px; color: var(--color-text-muted); }
.terms input { width: 130px; }
.pct { font-size: 13px; color: #1e9e4a; padding-bottom: 8px; }
.pct.bad { color: #d33; }
.error { color: #d33; font-size: 13px; }
</style>
