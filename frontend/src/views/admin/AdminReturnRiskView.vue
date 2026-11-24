<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">AI Phát Hiện Lạm Dụng Đổi Trả</h2>
      <span class="sc-card-sub">Chấm điểm mọi yêu cầu trả hàng từ lịch sử ca. AI chỉ gợi ý, người xử lý là admin.</span>
    </div>

    <div class="chips">
      <span class="chip HIGH">Rủi ro cao: {{ s.HIGH ?? 0 }} <small>({{ s.highUnreviewed ?? 0 }} chưa xem)</small></span>
      <span v-if="s.onHold" class="chip hold">⏸ Đang tạm giữ: {{ s.onHold }}</span>
      <span class="chip MEDIUM">Trung bình: {{ s.MEDIUM ?? 0 }}</span>
      <span class="chip LOW">Thấp: {{ s.LOW ?? 0 }}</span>
      <span class="chip">Đã kết luận: {{ s.confirmedAbuse ?? 0 }} lạm dụng / {{ s.confirmedLegit ?? 0 }} hợp lệ</span>
    </div>

    <div class="model">
      <span>LLM: <b>{{ s.llm ? 'Đang bật' : 'Chưa cấu hình' }}</b></span>
      <span>Mô hình bất thường: <b>{{ modelText }}</b></span>
      <button type="button" class="btn sm ghost" :disabled="training || !s.model?.configured" @click="onTrain">{{ training ? 'Đang huấn luyện...' : 'Huấn luyện lại' }}</button>
      <span v-if="trainMsg" class="msg">{{ trainMsg }}</span>
    </div>

    <div class="sc-toolbar filters">
      <select v-model="level" @change="load()">
        <option v-for="o in LEVELS" :key="o.key" :value="o.key">{{ o.label }}</option>
      </select>
      <select v-model="review" @change="load()">
        <option v-for="o in REVIEWS" :key="o.key" :value="o.key">{{ o.label }}</option>
      </select>
    </div>

    <div class="layout">
      <aside class="list">
        <div v-if="loading && !page.items.length" class="sc-empty">Đang tải...</div>
        <div v-else-if="!page.items.length" class="sc-empty">Chưa có yêu cầu nào ở mục này</div>
        <button v-for="r in page.items" :key="r.returnId" type="button" class="item" :class="{ active: selectedId === r.returnId }" @click="select(r.returnId)">
          <div class="item-top">
            <strong>{{ r.orderCode }}</strong>
            <span class="badge" :class="r.level">{{ r.score }}</span>
          </div>
          <div class="faint">{{ r.buyerName }} ⟷ {{ r.shopName }}</div>
          <div class="item-bottom">
            <span>{{ RETURN_REASONS[r.reason] }} · <strong>{{ formatVnd(r.refundAmount) }}</strong></span>
            <span v-if="r.onHold" class="held">⏸ Giữ</span>
            <span v-if="r.label" class="verdict" :class="r.label">{{ r.label === 'ABUSE' ? 'Lạm dụng' : 'Hợp lệ' }}</span>
          </div>
        </button>
        <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
      </aside>

      <div class="panel">
        <div v-if="!selectedId" class="sc-empty">Chọn một yêu cầu để xem chi tiết</div>
        <ReturnRiskPanel v-else :return-id="selectedId" @changed="onChanged" />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import ReturnRiskPanel from '../../components/admin/ReturnRiskPanel.vue'
import { returnRiskList, returnRiskSummary, trainReturnRisk } from '../../api/admin'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'
import { RETURN_REASONS } from '../../utils/returns'

const LEVELS = [{ key: 'ALL', label: 'Mọi mức rủi ro' }, { key: 'HIGH', label: 'Rủi ro cao' }, { key: 'MEDIUM', label: 'Trung bình' }, { key: 'LOW', label: 'Thấp' }]
const REVIEWS = [{ key: 'ALL', label: 'Mọi kết luận' }, { key: 'TODO', label: 'Chưa kết luận' }, { key: 'ABUSE', label: 'Đã xác nhận lạm dụng' }, { key: 'LEGIT', label: 'Hợp lệ' }]

const route = useRoute()
const router = useRouter()
const s = ref({})
const page = ref({ items: [], page: 0, totalPages: 0 })
const level = ref('ALL')
const review = ref('ALL')
const loading = ref(false)
const training = ref(false)
const trainMsg = ref('')

const selectedId = computed(() => (route.query.id ? Number(route.query.id) : null))
const modelText = computed(() => {
  const m = s.value.model
  if (!m || !m.configured) return 'Chưa cấu hình REVIEW_AI_URL (chỉ dùng luật)'
  if (m.down) return 'Không kết nối được (chỉ dùng luật)'
  if (!m.trained) return `Chưa huấn luyện (cần ít nhất ${m.minSamples} ca)`
  return `${m.samples} ca, ${m.labelled} có nhãn${m.metrics?.rocAuc != null ? `, AUC ${m.metrics.rocAuc}` : ''}`
})

const loadSummary = () => returnRiskSummary().then(({ data }) => { s.value = data }).catch(() => {})

async function load(p = 0) {
  loading.value = true
  try {
    page.value = (await returnRiskList({ level: level.value, review: review.value, page: p })).data
  } finally {
    loading.value = false
  }
}

function select(id) {
  router.replace({ query: { ...route.query, id } })
}

function onChanged() {
  loadSummary()
  load(page.value.page)
}

async function onTrain() {
  training.value = true
  trainMsg.value = ''
  try {
    const { data } = await trainReturnRisk()
    trainMsg.value = `Đã huấn luyện trên ${data.samples} ca`
    loadSummary()
  } catch (e) {
    trainMsg.value = extractError(e)
  } finally {
    training.value = false
  }
}

onMounted(() => {
  loadSummary()
  load()
})
</script>

<style scoped>
.chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.chip { background: #f5f5f5; padding: 4px 12px; border-radius: 14px; font-size: 13px; }
.chip.HIGH { background: #fdecee; color: #d0011b; }
.chip.MEDIUM { background: #fff4e0; color: #b97500; }
.chip.LOW { background: #e6f7f4; color: #1a8a7a; }
.chip.hold { background: #fff4e0; color: #8a5a00; font-weight: 600; }
.held { font-size: 12px; color: #8a5a00; }
.model { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; font-size: 13px; margin-bottom: 10px; }
.msg { color: var(--color-text-muted); }
.filters { display: flex; gap: 8px; }
.filters select { margin: 0; width: auto; }
.layout { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 12px; align-items: start; }
.item { display: block; width: 100%; text-align: left; background: white; border: 1px solid rgba(0, 0, 0, 0.09); border-radius: 3px; padding: 10px; margin-bottom: 6px; cursor: pointer; font-size: 13px; }
.item.active { border-color: var(--color-primary); }
.item-top, .item-bottom { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.faint { color: var(--color-text-faint); font-size: 12px; margin: 2px 0; }
.badge { color: white; font-size: 12px; font-weight: 600; padding: 0 8px; border-radius: 10px; }
.badge.LOW { background: #26aa99; } .badge.MEDIUM { background: #d98a00; } .badge.HIGH { background: #d0011b; }
.verdict { font-size: 12px; } .verdict.ABUSE { color: #d0011b; } .verdict.LEGIT { color: #26aa99; }
@media (max-width: 860px) { .layout { grid-template-columns: 1fr; } }
</style>
