<template>
  <div class="trend">
    <div class="box" :style="{ height: height + 'px' }">
      <canvas ref="canvas" role="img" :aria-label="series.map(s => s.name).join(', ')"></canvas>
      <div v-if="empty" class="empty">Chưa có dữ liệu trong khoảng thời gian này</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, markRaw } from 'vue'
import {
  Chart, BarController, LineController, BarElement, LineElement, PointElement,
  CategoryScale, LinearScale, Tooltip, Legend
} from 'chart.js'
import { compact } from '../../composables/useAnalyticsRange'

Chart.register(BarController, LineController, BarElement, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Legend)

// Chart.js wrapper shared by every dashboard (seller, admin, reports).
// series: [{ name, values: (number | null)[], color, type: 'bar' | 'line', axis: 'left' | 'right',
//            format?: (v) => string, integer?: boolean, max?: number, dashed?: boolean }]
// a null value means "no data" (e.g. no review that day): the line has a gap there instead of dropping to 0
const props = defineProps({
  labels: { type: Array, required: true },
  tooltipLabels: { type: Array, default: null },
  series: { type: Array, required: true },
  height: { type: Number, default: 260 },
  leftTickFormat: { type: Function, default: compact },
  rightTickFormat: { type: Function, default: compact }
})

const canvas = ref(null)
let chart = null

const empty = computed(() => props.series.every(s => s.values.every(v => !Number(v))))
const plain = (v) => Number(v || 0).toLocaleString('vi-VN')

function axis(list, format, position, grid) {
  const fixed = list.find(s => s.max)
  const integer = list.length && list.every(s => s.integer)
  return {
    position,
    display: list.length > 0,
    beginAtZero: true,
    max: fixed ? fixed.max : undefined,
    // whole numbers for counts so the axis never shows "1,25 đơn"; one tick per star on a 0-5 scale
    ticks: { color: '#999', font: { size: 11 }, precision: integer ? 0 : undefined, stepSize: fixed && fixed.max <= 6 ? 1 : undefined,
      maxTicksLimit: 6, callback: (v) => format(v) },
    grid: { color: '#eee', display: grid, drawTicks: false },
    border: { display: false }
  }
}

function config() {
  const right = props.series.filter(s => s.axis === 'right')
  return {
    data: {
      labels: props.labels,
      datasets: props.series.map(s => ({
        type: s.type === 'bar' ? 'bar' : 'line',
        label: s.name,
        data: s.values,
        yAxisID: s.axis === 'right' ? 'right' : 'left',
        backgroundColor: s.color,
        borderColor: s.color,
        borderWidth: 2,
        borderRadius: s.type === 'bar' ? 2 : 0,
        borderDash: s.dashed ? [5, 4] : [],
        pointRadius: props.labels.length <= 31 ? 3 : 0,
        pointHoverRadius: 4.5,
        pointBorderColor: 'white',
        pointBorderWidth: 1.5,
        spanGaps: false,
        maxBarThickness: 44,
        // lines drawn over the bars
        order: s.type === 'bar' ? 2 : 1,
        _format: s.format || plain
      }))
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 300 },
      interaction: { mode: 'index', intersect: false },
      layout: { padding: { top: 4 } },
      scales: {
        x: { grid: { display: false }, ticks: { color: '#999', font: { size: 11 }, maxRotation: 0, autoSkipPadding: 16 } },
        left: axis(props.series.filter(s => s.axis !== 'right'), props.leftTickFormat, 'left', true),
        right: axis(right, props.rightTickFormat, 'right', false)
      },
      plugins: {
        legend: {
          position: 'top', align: 'start',
          labels: { boxWidth: 12, boxHeight: 10, color: '#666', font: { size: 13 }, usePointStyle: false }
        },
        tooltip: {
          backgroundColor: 'rgba(34, 34, 34, 0.92)', padding: 10, boxWidth: 8, boxHeight: 8,
          callbacks: {
            title: (items) => props.tooltipLabels?.[items[0].dataIndex] || props.labels[items[0].dataIndex],
            label: (item) => `${item.dataset.label}: ${item.dataset._format(item.raw)}`
          }
        }
      }
    }
  }
}

onMounted(() => {
  chart = markRaw(new Chart(canvas.value, config()))
})
onBeforeUnmount(() => chart?.destroy())

// new data (other date range, other tab): rebuild the datasets
watch(() => [props.labels, props.series, props.tooltipLabels], () => {
  if (!chart) return
  const c = config()
  chart.data = c.data
  chart.options = c.options
  chart.update()
}, { deep: true })
</script>

<style scoped>
.trend { width: 100%; }
.box { position: relative; width: 100%; }
.empty { position: absolute; inset: 30px 0 26px 56px; display: flex; align-items: center; justify-content: center; color: var(--color-text-faint); font-size: 13px; pointer-events: none; }
</style>
