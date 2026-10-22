<template>
  <!-- complaints repeating on a product: fix the cause, not each review -->
  <section class="sc-card">
    <div class="sc-card-head tw-items-center">
      <h2 class="sc-card-title">Vấn Đề Lặp Lại</h2>
      <span class="sc-card-sub">Các phàn nàn giống nhau trên cùng sản phẩm, gom từ mọi sàn: sửa từ gốc thay vì trả lời từng đánh giá</span>
      <select v-model.number="days" class="tw-ml-auto !tw-w-auto" @change="load">
        <option :value="14">14 ngày</option><option :value="30">30 ngày</option><option :value="90">90 ngày</option><option :value="180">180 ngày</option>
      </select>
    </div>
    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="!clusters.length" class="sc-empty">Chưa có vấn đề nào lặp lại từ 2 lần trở lên trong {{ days }} ngày qua 👍</div>
    <div v-else class="tw-grid tw-gap-3 md:tw-grid-cols-2">
      <article v-for="c in clusters" :key="c.key + c.issue" class="tw-rounded-lg tw-border tw-border-solid tw-p-3"
               :class="c.productDefect ? 'tw-border-red-300 tw-bg-red-50/40' : 'tw-border-neutral-200'">
        <div class="tw-flex tw-items-start tw-gap-2">
          <div class="tw-min-w-0 tw-flex-1">
            <div class="tw-text-xs tw-font-semibold tw-text-[#5b3cc4]">{{ c.issueLabel }}</div>
            <div class="tw-truncate tw-font-medium">{{ c.productName }}</div>
          </div>
          <div class="tw-text-right">
            <div class="tw-text-2xl tw-font-bold">{{ c.count }}</div>
            <div class="tw-text-xs" :class="c.rising ? 'tw-text-red-600' : 'tw-text-green-700'">
              {{ c.previous === 0 ? 'mới xuất hiện' : (c.trendPct > 0 ? '▲ ' : '▼ ') + Math.abs(c.trendPct) + '% so với kỳ trước' }}
            </div>
          </div>
        </div>
        <p v-if="c.productDefect" class="tw-my-1 tw-text-xs tw-font-semibold tw-text-red-700">⚠ Có dấu hiệu lỗi sản phẩm thật: nên kiểm tra lô hàng</p>
        <div class="tw-text-xs tw-text-neutral-500">{{ c.avgRating }}★ trung bình · {{ c.sources.map(s => SOURCES[s]).join(', ') }} · {{ when(c.firstSeen) }} → {{ when(c.lastSeen) }}</div>
        <ul class="tw-my-2 tw-pl-4 tw-text-sm tw-text-neutral-700">
          <li v-for="s in c.samples" :key="s" class="tw-line-clamp-2">"{{ s }}"</li>
        </ul>
        <div v-if="causes[c.key + c.issue]" class="tw-rounded tw-bg-neutral-50 tw-p-2 tw-text-sm">
          <div><strong>Nguyên nhân có thể:</strong> {{ causes[c.key + c.issue].cause }}</div>
          <ul class="tw-my-1 tw-pl-4"><li v-for="a in causes[c.key + c.issue].actions" :key="a">{{ a }}</li></ul>
          <div class="tw-text-xs tw-text-neutral-400">{{ causes[c.key + c.issue].source === 'AI' ? 'AI phân tích từ ' + causes[c.key + c.issue].count + ' đánh giá' : 'Gợi ý theo nhóm vấn đề' }}</div>
        </div>
        <p v-else class="tw-my-1 tw-text-xs tw-text-neutral-500">Gợi ý: {{ c.fix }}</p>
        <div class="tw-mt-2 tw-flex tw-gap-2">
          <button type="button" class="btn sm ghost" :disabled="busy === c.key + c.issue" @click="analyze(c)">{{ busy === c.key + c.issue ? 'Đang phân tích...' : '✨ Phân tích nguyên nhân' }}</button>
          <button type="button" class="btn sm ghost" @click="$emit('open', { tab: 'ALL', issue: c.issue })">Xem đánh giá</button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { hubRecurring, hubRootCause, SOURCES } from '../../api/reviewHub'

defineEmits(['open'])
const days = ref(30)
const clusters = ref([])
const loading = ref(false)
const causes = ref({})
const busy = ref('')

const when = (t) => new Date(t).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })

async function load() {
  loading.value = true
  try {
    clusters.value = (await hubRecurring(days.value)).data.clusters
  } finally {
    loading.value = false
  }
}

async function analyze(c) {
  busy.value = c.key + c.issue
  try {
    causes.value[c.key + c.issue] = (await hubRootCause(c.key, c.issue, days.value)).data
  } finally {
    busy.value = ''
  }
}

onMounted(load)
defineExpose({ load })
</script>
