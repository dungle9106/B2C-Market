<template>
  <!-- reviews that look fake (spam, copied, a competitor's attack): the seller decides, Shoppi's go to the moderators -->
  <section class="sc-card">
    <div class="sc-card-head tw-items-center">
      <h2 class="sc-card-title">Đánh Giá Bất Thường</h2>
      <span class="sc-card-sub">Trùng nội dung, dồn dập bất thường, số sao ngược nội dung, một người đánh giá liên tục... Hệ thống chỉ cảnh báo, bạn quyết định.</span>
    </div>
    <div class="tw-mb-3 tw-flex tw-gap-2">
      <button v-for="(l, k) in TABS" :key="k" type="button" class="chip" :class="{ on: status === k }" @click="status = k; load(0)">{{ l }}</button>
    </div>
    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="!page.items.length" class="sc-empty">Không có đánh giá nào ở mục này</div>
    <div v-else class="sc-table-wrap">
      <table class="sc-table">
        <thead><tr><th>Đánh giá</th><th>Dấu hiệu</th><th class="tw-w-20">Điểm</th><th class="tw-w-56"></th></tr></thead>
        <tbody>
          <tr v-for="i in page.items" :key="i.id">
            <td>
              <div class="tw-text-xs tw-text-neutral-400">{{ SOURCES[i.source] }} · {{ i.reviewerName || 'Ẩn danh' }} · <span class="tw-text-amber-500">{{ '★'.repeat(i.rating) }}</span></div>
              <div class="tw-text-sm tw-font-medium">{{ i.productName }}</div>
              <div class="tw-line-clamp-2 tw-text-sm tw-text-neutral-600">{{ i.content || '(không có nội dung)' }}</div>
            </td>
            <td class="tw-text-xs">
              <span v-for="x in i.anomalyFlags" :key="x.key" class="flag">{{ x.label }}</span>
              <div class="tw-mt-1 tw-text-neutral-500">{{ i.anomalyNote }}</div>
            </td>
            <td><span class="score" :class="i.anomalyScore >= 70 ? 'hi' : ''">{{ i.anomalyScore }}</span></td>
            <td>
              <div v-if="i.anomalyStatus === 'FLAGGED'" class="tw-flex tw-flex-wrap tw-gap-1">
                <button type="button" class="btn sm ghost" @click="decide(i, 'OK')">Đánh giá thật</button>
                <button type="button" class="btn sm" @click="decide(i, 'REPORT')">{{ i.source === 'SHOPPI' ? 'Báo cáo Shoppi' : 'Đánh dấu đã báo cáo' }}</button>
              </div>
              <button v-else type="button" class="link" @click="decide(i, 'RESET')">Hoàn tác</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
    <p class="tw-text-xs tw-text-neutral-400">Với đánh giá trên Shopee/Lazada/Tiki/TikTok: dùng chức năng báo cáo đánh giá của sàn đó, sau đó đánh dấu ở đây để theo dõi.</p>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PaginationBar from '../PaginationBar.vue'
import { hubAnomalies, hubDecideAnomaly, SOURCES } from '../../api/reviewHub'

const emit = defineEmits(['changed'])
const TABS = { FLAGGED: 'Cần kiểm tra', REPORTED: 'Đã báo cáo', CONFIRMED_OK: 'Đánh giá thật' }
const status = ref('FLAGGED')
const page = ref({ items: [], page: 0, totalPages: 0 })
const loading = ref(false)

async function load(p = 0) {
  loading.value = true
  try {
    page.value = (await hubAnomalies(status.value, p)).data
  } finally {
    loading.value = false
  }
}

async function decide(i, action) {
  if (action === 'REPORT' && !confirm(i.source === 'SHOPPI' ? 'Gửi báo cáo đánh giá này cho đội kiểm duyệt Shoppi?' : 'Đánh dấu đã báo cáo với sàn?')) return
  await hubDecideAnomaly(i.id, action)
  await load(page.value.page)
  emit('changed')
}

onMounted(() => load(0))
defineExpose({ load })
</script>

<style scoped>
.chip { border: 1px solid var(--color-border); background: white; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chip.on { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.flag { display: inline-block; padding: 1px 6px; margin: 0 4px 3px 0; border-radius: 3px; background: #fff8e1; color: #b26a00; }
.score { font-weight: 700; color: #b26a00; }
.score.hi { color: #c62828; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
</style>
