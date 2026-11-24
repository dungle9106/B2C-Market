<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head tw-items-center">
        <h2 class="sc-card-title">Hiệu Quả Hoạt Động Shop</h2>
        <span class="sc-card-sub">Chỉ số 30 ngày, điểm phạt (Sao Quả Tạ) trong quý và nhãn Shop Yêu Thích</span>
        <button type="button" class="btn sm ghost tw-ml-auto" :disabled="busy" @click="evaluate">{{ busy ? 'Đang chạy...' : 'Đánh giá ngay' }}</button>
        <button type="button" class="btn sm ghost" @click="showRules = !showRules">Tiêu chí</button>
      </div>
      <p v-if="msg" class="tw-text-sm tw-text-green-700">{{ msg }}</p>

      <div v-if="showRules && rules" class="rules">
        <h3 class="tw-mt-0 tw-text-base">Ngưỡng tính điểm phạt và Shop Yêu Thích</h3>
        <div class="tw-grid tw-grid-cols-2 tw-gap-3 md:tw-grid-cols-3">
          <label v-for="(label, key) in RULE_LABELS" :key="key" class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500">
            {{ label }}<input v-model.number="rules[key]" type="number" min="0" step="0.5" />
          </label>
        </div>
        <button type="button" class="btn sm tw-mt-2" @click="saveRules">Lưu tiêu chí</button>
      </div>

      <div class="tw-my-3 tw-flex tw-flex-wrap tw-gap-2">
        <span class="chip">⭐ {{ summary.favorite || 0 }} shop Yêu thích</span>
        <span class="chip warn">⚠ {{ summary.warned || 0 }} shop từ 3 điểm phạt</span>
        <span class="chip danger">⛔ {{ summary.review || 0 }} shop cần xem xét đình chỉ</span>
        <span class="tw-flex-1"></span>
        <input v-model.trim="q" placeholder="Tìm tên shop" class="!tw-w-56" @input="debounced" />
        <select v-model="sort" class="!tw-w-auto" @change="load(0)">
          <option value="points">Điểm phạt cao nhất</option><option value="nfr">Tỷ lệ hủy cao nhất</option>
          <option value="late">Giao trễ nhiều nhất</option><option value="orders">Nhiều đơn nhất</option>
        </select>
      </div>

      <div class="sc-table-wrap">
        <table class="sc-table">
          <thead><tr><th>Shop</th><th>Đơn 30 ngày</th><th>Đơn không thành công</th><th>Giao trễ</th><th>Điểm phạt</th><th>Nhãn</th></tr></thead>
          <tbody>
            <tr v-for="s in page.items" :key="s.shopId" class="tw-cursor-pointer hover:tw-bg-neutral-50" @click="open(s.shopId)">
              <td><b>{{ s.shopName }}</b><span v-if="!s.active" class="tw-ml-1 tw-text-xs tw-text-amber-700">(tạm ngưng)</span></td>
              <td>{{ s.orders }}</td>
              <td :class="s.nfrPercent >= 10 ? 'tw-text-red-600' : ''">{{ s.nfrPercent != null ? s.nfrPercent + '%' : '—' }}</td>
              <td :class="s.latePercent >= 10 ? 'tw-text-red-600' : ''">{{ s.latePercent != null ? s.latePercent + '%' : '—' }}</td>
              <td><b :class="LEVEL_CLASS[s.level.key]">{{ s.points }}</b></td>
              <td><span v-if="s.favorite" class="fav">Yêu thích</span></td>
            </tr>
            <tr v-if="!page.items.length"><td colspan="6" class="sc-empty">Không có shop nào</td></tr>
          </tbody>
        </table>
      </div>
      <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
    </section>

    <BaseModal v-if="detail" :title="detail.shopName" :width="980" @close="detail = null">
      <PerformanceReport :report="detail">
        <template #penalty-action="{ penalty }">
          <button v-if="!penalty.revoked" type="button" class="link" @click="revoke(penalty)">Hủy</button>
        </template>
      </PerformanceReport>
      <form class="add" @submit.prevent="addManual">
        <h3 class="tw-my-0 tw-text-base">Xử phạt thủ công</h3>
        <div class="tw-flex tw-flex-wrap tw-gap-2">
          <select v-model.number="penalty.points" class="!tw-w-auto"><option v-for="n in 5" :key="n" :value="n">{{ n }} điểm</option></select>
          <input v-model.trim="penalty.reason" class="tw-flex-1" placeholder="Lý do (VD: giao sai hàng nhiều lần, lôi kéo giao dịch ngoài sàn)" />
          <button type="submit" class="btn sm" :disabled="busy">Cộng điểm phạt</button>
        </div>
        <p v-if="error" class="tw-m-0 tw-text-sm tw-text-red-600">{{ error }}</p>
      </form>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import BaseModal from '../../components/BaseModal.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import PerformanceReport from '../../components/PerformanceReport.vue'
import { shopPerformanceList, shopPerformance, addPenalty, revokePenalty, evaluatePerformance, performanceSettings,
  savePerformanceSettings, LEVEL_CLASS } from '../../api/performance'
import { extractError } from '../../utils/apiError'

const RULE_LABELS = {
  'penalty.nfrPercent': 'Phạt khi tỷ lệ đơn không thành công trong tuần ≥ (%)',
  'penalty.latePercent': 'Phạt khi tỷ lệ giao trễ trong tuần ≥ (%)',
  'penalty.minOrders': 'Số đơn tối thiểu trong tuần để xét phạt',
  'favorite.minOrders': 'Yêu thích: đơn giao thành công / 30 ngày ≥',
  'favorite.minRating': 'Yêu thích: đánh giá shop ≥ (★)',
  'favorite.maxNfrPercent': 'Yêu thích: đơn không thành công ≤ (%)',
  'favorite.maxLatePercent': 'Yêu thích: giao trễ ≤ (%)',
  'favorite.minChatPercent': 'Yêu thích: phản hồi chat ≥ (%)',
  'favorite.minAgeDays': 'Yêu thích: shop hoạt động ≥ (ngày)'
}

const route = useRoute()
const page = ref({ items: [], page: 0, totalPages: 0 })
const summary = ref({})
const q = ref('')
const sort = ref('points')
const detail = ref(null)
const penalty = ref({ points: 1, reason: '' })
const rules = ref(null)
const showRules = ref(false)
const busy = ref(false)
const msg = ref('')
const error = ref('')
let timer = null

async function load(p = 0) {
  const { data } = await shopPerformanceList({ q: q.value || undefined, sort: sort.value, page: p })
  page.value = data
  summary.value = data.summary
}
function debounced() {
  clearTimeout(timer)
  timer = setTimeout(() => load(0), 350)
}

async function open(id) {
  error.value = ''
  penalty.value = { points: 1, reason: '' }
  detail.value = (await shopPerformance(id)).data
}

async function addManual() {
  busy.value = true
  error.value = ''
  try {
    detail.value = (await addPenalty(detail.value.shopId, penalty.value)).data
    penalty.value = { points: 1, reason: '' }
    load(page.value.page)
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function revoke(p) {
  const reason = prompt(`Hủy ${p.points} điểm phạt "${p.reason}"? Nhập lý do:`)
  if (!reason) return
  try {
    detail.value = (await revokePenalty(p.id, reason)).data
    load(page.value.page)
  } catch (e) {
    alert(extractError(e))
  }
}

async function evaluate() {
  busy.value = true
  msg.value = ''
  try {
    const { data } = await evaluatePerformance()
    msg.value = `Đã đánh giá tuần ${data.week}: ${data.shopsPenalized} shop bị cộng điểm phạt, ${data.favoriteShops} shop đạt Yêu thích`
    load(0)
  } finally {
    busy.value = false
  }
}

async function saveRules() {
  try {
    rules.value = (await savePerformanceSettings(rules.value)).data
    msg.value = 'Đã lưu tiêu chí'
  } catch (e) {
    alert(extractError(e))
  }
}

onMounted(async () => {
  await load(0)
  performanceSettings().then(({ data }) => { rules.value = data }).catch(() => {})
  if (route.query.shop) open(Number(route.query.shop))
})
</script>

<style scoped>
.chip { font-size: 13px; padding: 4px 10px; border-radius: 999px; background: #fff0eb; color: var(--color-primary); white-space: nowrap; align-self: center; }
.chip.warn { background: #fff7e6; color: #b45309; }
.chip.danger { background: #fdecea; color: #c62828; }
.fav { background: var(--color-primary); color: white; font-size: 11px; padding: 1px 6px; border-radius: 2px; }
.rules { border: 1px dashed var(--color-border); border-radius: 6px; padding: 12px 14px; margin-bottom: 12px; }
.add { margin-top: 16px; border-top: 1px solid var(--color-border); padding-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
</style>
