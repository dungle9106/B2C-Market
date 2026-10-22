<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head tw-items-center">
        <h2 class="sc-card-title">Trung Tâm Đánh Giá</h2>
        <span class="sc-card-sub">Đánh giá từ mọi sàn ở một chỗ: phân loại vấn đề, ưu tiên trả lời, AI soạn sẵn phản hồi, phát hiện bất thường</span>
        <span class="tw-ml-auto tw-text-xs" :class="ov?.ai.llm ? 'tw-text-green-700' : 'tw-text-neutral-400'">
          {{ ov ? (ov.ai.llm ? '● AI đang bật' : '● AI chưa bật: phân loại & soạn phản hồi theo luật') : '' }}
          <template v-if="ov?.ai.service"> · Mô hình FastAPI</template>
          <template v-if="ov?.ai.pending"> · {{ ov.ai.pending }} đánh giá chờ AI đọc</template>
        </span>
      </div>
      <nav class="sc-tabs">
        <a v-for="t in TABS" :key="t.key" href="#" :class="{ active: tab === t.key }" @click.prevent="go(t.key)">
          {{ t.label }}<span v-if="t.count && t.count() " class="count">({{ t.count() }})</span>
        </a>
      </nav>

      <!-- ===== overview ===== -->
      <template v-if="tab === 'overview'">
        <div class="tw-mb-3 tw-flex tw-items-center tw-gap-2">
          <select v-model.number="days" class="!tw-w-auto" @change="loadOverview">
            <option :value="7">7 ngày</option><option :value="30">30 ngày</option><option :value="90">90 ngày</option><option :value="365">12 tháng</option>
          </select>
          <span class="tw-flex-1"></span>
          <button type="button" class="btn sm ghost" :disabled="busy" @click="reanalyze">Phân tích lại</button>
        </div>
        <div v-if="!ov" class="sc-empty">Đang tải...</div>
        <template v-else>
          <div class="tw-grid tw-grid-cols-2 tw-gap-3 md:tw-grid-cols-4 lg:tw-grid-cols-7">
            <KpiCard label="Đánh giá" :value="ov.kpi.total" :current="ov.kpi.total" :previous="ov.kpi.totalPrev" />
            <KpiCard label="Điểm trung bình" :value="ov.kpi.avgRating.toFixed(2) + '★'" :current="ov.kpi.avgRating" :previous="ov.kpi.avgRatingPrev" />
            <KpiCard label="Tiêu cực" :value="ov.kpi.negativePct + '%'" />
            <KpiCard label="Cần trả lời" :value="ov.kpi.needReply" hint="Đánh giá ≤ 3 sao hoặc ưu tiên cao chưa xử lý" />
            <KpiCard label="Khẩn cấp" :value="ov.kpi.urgent" />
            <KpiCard label="Quá hạn" :value="ov.kpi.overdue" hint="Quá hạn trả lời theo mức ưu tiên (khẩn cấp 12 giờ, cao 24 giờ, bình thường 72 giờ)" />
            <KpiCard label="Tỷ lệ phản hồi" :value="ov.kpi.responseRate + '%'" :hint="ov.kpi.avgResponseHours != null ? `Trả lời sau trung bình ${ov.kpi.avgResponseHours} giờ` : 'Đánh giá ≤ 3 sao đã được trả lời'" />
          </div>

          <div class="tw-mt-4 tw-grid tw-gap-4 lg:tw-grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
            <div>
              <h3 class="tw-mb-2 tw-mt-0 tw-text-base">Diễn biến đánh giá</h3>
              <TrendChart :labels="ov.series.map(s => s.date.slice(8, 10) + '/' + s.date.slice(5, 7))" :height="240" :series="[
                { name: 'Đánh giá', type: 'bar', values: ov.series.map(s => s.count), color: '#9ec5fe', integer: true },
                { name: 'Tiêu cực', type: 'bar', values: ov.series.map(s => s.negative), color: '#f28b82', integer: true },
                { name: 'Điểm TB', type: 'line', axis: 'right', values: ov.series.map(s => s.avgRating), color: '#f59e0b', max: 5, format: v => v == null ? '—' : v.toFixed(2) + '★' }
              ]" />
            </div>
            <div>
              <h3 class="tw-mb-2 tw-mt-0 tw-text-base">Vấn đề khách gặp</h3>
              <p v-if="!ov.issues.length" class="tw-text-sm tw-text-neutral-400">Chưa có phàn nàn nào trong kỳ 👍</p>
              <button v-for="x in ov.issues" :key="x.key" type="button" class="tw-mb-2 tw-block tw-w-full tw-cursor-pointer tw-border-0 tw-bg-transparent tw-p-0 tw-text-left"
                      @click="openInbox({ tab: 'ALL', issue: x.key })">
                <div class="tw-flex tw-justify-between tw-text-sm"><span>{{ x.label }}</span><span class="tw-text-neutral-500">{{ x.count }} · {{ x.pct }}%</span></div>
                <div class="tw-h-2 tw-rounded tw-bg-neutral-100"><div class="tw-h-2 tw-rounded tw-bg-[#7c5cff]" :style="{ width: x.pct + '%' }"></div></div>
              </button>
              <h3 class="tw-mb-2 tw-mt-4 tw-text-base">Theo sàn</h3>
              <table class="tw-w-full tw-text-sm">
                <tr v-for="s in ov.sources" :key="s.source"><td><span class="src" :style="{ background: SOURCE_COLORS[s.source] }">{{ SOURCES[s.source] }}</span></td>
                  <td class="tw-text-right">{{ s.count }}</td><td class="tw-text-right">{{ s.avgRating.toFixed(1) }}★</td><td class="tw-text-right tw-text-red-600">{{ s.negativePct }}%</td></tr>
              </table>
            </div>
          </div>

          <div class="tw-mt-4 tw-grid tw-gap-4 lg:tw-grid-cols-2">
            <div>
              <h3 class="tw-mb-2 tw-mt-0 tw-text-base">Cần trả lời ngay</h3>
              <p v-if="!ov.urgentItems.length" class="tw-text-sm tw-text-neutral-400">Không có đánh giá khẩn cấp nào đang chờ 👍</p>
              <button v-for="i in ov.urgentItems" :key="i.id" type="button" class="tw-mb-2 tw-block tw-w-full tw-cursor-pointer tw-rounded tw-border tw-border-solid tw-border-neutral-200 tw-bg-white tw-p-2 tw-text-left hover:tw-border-primary"
                      @click="openInbox({ tab: 'OPEN', item: i.id })">
                <div class="tw-text-xs"><span class="lvl" :class="i.priorityLevel">{{ LEVELS[i.priorityLevel] }}</span> {{ SOURCES[i.source] }} · {{ '★'.repeat(i.rating) }}
                  <span v-if="i.overdue" class="tw-font-semibold tw-text-red-600"> · Quá hạn</span></div>
                <div class="tw-line-clamp-1 tw-text-sm">{{ i.issueSummary || i.content }}</div>
              </button>
            </div>
            <div>
              <h3 class="tw-mb-2 tw-mt-0 tw-text-base">Vấn đề đang lặp lại</h3>
              <p v-if="!ov.recurring.length" class="tw-text-sm tw-text-neutral-400">Chưa có vấn đề lặp lại</p>
              <button v-for="c in ov.recurring" :key="c.key + c.issue" type="button" class="tw-mb-2 tw-block tw-w-full tw-cursor-pointer tw-rounded tw-border tw-border-solid tw-bg-white tw-p-2 tw-text-left"
                      :class="c.productDefect ? 'tw-border-red-300' : 'tw-border-neutral-200'" @click="go('issues')">
                <div class="tw-text-sm"><strong>{{ c.count }}×</strong> {{ c.issueLabel }} · {{ c.productName }}</div>
                <div class="tw-text-xs" :class="c.rising ? 'tw-text-red-600' : 'tw-text-neutral-500'">{{ c.rising ? 'Đang tăng' : 'Ổn định' }}<template v-if="c.productDefect"> · nghi lỗi sản phẩm</template></div>
              </button>
              <p v-if="ov.kpi.anomalies" class="tw-text-sm">⚠ <button type="button" class="link" @click="go('anomalies')">{{ ov.kpi.anomalies }} đánh giá bất thường</button> cần kiểm tra</p>
            </div>
          </div>
        </template>
      </template>
    </section>

    <ReviewInbox v-if="tab === 'inbox'" ref="inbox" :issue-types="issueTypes" :preset="preset" @changed="loadOverview" />
    <ReviewIssues v-if="tab === 'issues'" @open="openInbox" />
    <ReviewAnomalies v-if="tab === 'anomalies'" @changed="loadOverview" />
    <ReviewImport v-if="tab === 'import'" @done="go('inbox')" @changed="loadOverview" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KpiCard from '../../components/analytics/KpiCard.vue'
import TrendChart from '../../components/analytics/TrendChart.vue'
import ReviewInbox from '../../components/reviewhub/ReviewInbox.vue'
import ReviewIssues from '../../components/reviewhub/ReviewIssues.vue'
import ReviewAnomalies from '../../components/reviewhub/ReviewAnomalies.vue'
import ReviewImport from '../../components/reviewhub/ReviewImport.vue'
import { hubOverview, hubIssueTypes, hubSync, hubReanalyze, SOURCES, SOURCE_COLORS, LEVELS } from '../../api/reviewHub'

const route = useRoute()
const router = useRouter()
const tab = ref(route.query.tab || (route.query.item ? 'inbox' : 'overview'))
const days = ref(30)
const ov = ref(null)
const issueTypes = ref([])
const preset = ref(null)
const busy = ref(false)

const TABS = [
  { key: 'overview', label: 'Tổng quan' },
  { key: 'inbox', label: 'Hộp đánh giá', count: () => ov.value?.kpi.needReply },
  { key: 'issues', label: 'Vấn đề lặp lại' },
  { key: 'anomalies', label: 'Bất thường', count: () => ov.value?.kpi.anomalies },
  { key: 'import', label: 'Nhập từ sàn khác' }
]

function go(k) {
  tab.value = k
  router.replace({ query: { tab: k } })
}

function openInbox(p) {
  tab.value = 'inbox'
  router.replace({ query: { tab: 'inbox' } })
  preset.value = { ...p, at: Date.now() }
}

async function loadOverview() {
  try {
    ov.value = (await hubOverview(days.value)).data
  } catch { /* shown as loading */ }
}

async function reanalyze() {
  busy.value = true
  try {
    await hubReanalyze()
    await loadOverview()
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  hubIssueTypes().then(({ data }) => { issueTypes.value = data })
  // first visit: bring in the Shoppi reviews written before the hub existed
  await hubSync().catch(() => {})
  await loadOverview()
  if (route.query.issue) openInbox({ tab: 'ALL', issue: route.query.issue })
})
</script>

<style scoped>
.src { color: white; font-size: 11px; padding: 1px 6px; border-radius: 3px; }
.lvl { font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 3px; background: #eee; color: #555; }
.lvl.URGENT { background: #fdecea; color: #c62828; }
.lvl.HIGH { background: #fff3e0; color: #e65100; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 14px; padding: 0; }
</style>
