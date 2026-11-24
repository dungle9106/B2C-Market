<template>
  <div class="model-card">
    <div class="status">
      <span class="dot" :class="state.cls"></span>
      <b>{{ state.text }}</b>
      <span v-if="m.trainedAt" class="faint">· Huấn luyện lúc {{ new Date(m.trainedAt).toLocaleString('vi-VN') }}</span>
      <span v-if="m.algorithm" class="faint">· {{ ALGO[m.algorithm] || m.algorithm }}</span>
      <button type="button" class="btn sm" :disabled="training || !m.configured || m.down" @click="onTrain">
        {{ training ? 'Đang huấn luyện...' : m.trained ? 'Huấn luyện lại' : 'Huấn luyện' }}
      </button>
      <span v-if="msg" class="msg">{{ msg }}</span>
    </div>

    <template v-if="metrics">
      <p class="faint small">
        Đánh giá trên {{ m.testCases }} {{ unit }} mới nhất mà mô hình chưa từng thấy (huấn luyện trên dữ liệu cũ hơn, như khi dùng thật).
      </p>
      <table class="tbl">
        <thead>
          <tr><th>Cách chấm</th><th>ROC-AUC</th><th>PR-AUC</th><th>Độ chính xác top 10%</th><th>Bắt được trong top 10%</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.key" :class="{ best: row.key === 'model' }">
            <td>{{ row.label }}</td>
            <td v-for="k in KEYS" :key="k">
              <div class="bar"><i :style="{ width: (100 * row.v[k]) + '%' }"></i></div>
              <span>{{ k === 'rocAuc' ? row.v[k].toFixed(3) : pct(row.v[k]) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="gain !== null" class="gain">
        {{ gain >= 0 ? `Mô hình bắt được nhiều hơn luật ${gain}% trong top 10% rủi ro nhất.` : `Mô hình chưa hơn luật (${gain}%): cần thêm dữ liệu.` }}
      </p>
      <p class="faint small">
        ROC-AUC: xác suất một ca "có" được xếp trên một ca "không" (0.5 = đoán bừa, 1 = hoàn hảo).
        PR-AUC: độ chính xác trung bình khi đi dần xuống danh sách. Top 10%: nếu admin chỉ xem 10% ca rủi ro nhất thì bao nhiêu đúng, bắt được bao nhiêu.
      </p>
    </template>
    <p v-else-if="m.trained && !m.supervised" class="faint small">
      Chưa đủ ca đã biết kết quả để học có giám sát: đang dùng mô hình phát hiện bất thường (Isolation Forest), không cần nhãn.
    </p>

    <div v-if="m.importance?.length" class="imp">
      <span class="faint small">Mô hình chú ý nhất:</span>
      <span v-for="f in m.importance.slice(0, 6)" :key="f.feature" class="tag" :class="f.weight > 0 ? 'up' : 'down'">
        {{ labels[f.feature] || f.feature }} {{ f.weight > 0 ? '↑' : '↓' }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { extractError } from '../../utils/apiError'

const props = defineProps({
  m: { type: Object, required: true },        // the /status answer
  train: { type: Function, required: true },  // () => Promise
  unit: { type: String, default: 'trường hợp' },
  needs: { type: String, default: '' },        // what training needs, shown before the first training
  labels: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['trained'])

const KEYS = ['rocAuc', 'prAuc', 'precisionTop', 'recallTop']
const ALGO = {
  logistic: 'Hồi quy logistic',
  boosting: 'Gradient Boosting',
  'isolation-forest': 'Isolation Forest',
  'logistic+isolation-forest': 'Hồi quy logistic + Isolation Forest',
  'boosting+isolation-forest': 'Gradient Boosting + Isolation Forest'
}
const training = ref(false)
const msg = ref('')

const state = computed(() => {
  const m = props.m
  if (!m.configured) return { cls: 'off', text: 'Chưa cấu hình dịch vụ AI (REVIEW_AI_URL): đang dùng luật' }
  if (m.down) return { cls: 'off', text: 'Không kết nối được dịch vụ AI: đang dùng luật' }
  if (!m.trained) return { cls: 'wait', text: `Chưa huấn luyện: đang dùng luật${props.needs ? ` (${props.needs})` : ''}` }
  return { cls: 'on', text: 'Đang dùng mô hình đã huấn luyện' }
})

const metrics = computed(() => props.m.metrics?.model ? props.m.metrics : null)
const rows = computed(() => metrics.value ? [
  { key: 'rule', label: 'Luật thủ công' },
  { key: 'anomaly', label: 'Chỉ phát hiện bất thường' },
  { key: 'logistic', label: 'Hồi quy logistic' },
  { key: 'model', label: 'Mô hình được chọn' }
].filter(r => metrics.value[r.key]).map(r => ({ ...r, v: metrics.value[r.key] })) : [])
const pct = (x) => `${(100 * x).toFixed(1)}%`
const gain = computed(() => {
  if (!metrics.value) return null
  const rule = metrics.value.rule.recallTop
  return rule > 0 ? Math.round(100 * (metrics.value.model.recallTop / rule - 1)) : null
})

async function onTrain() {
  training.value = true
  msg.value = ''
  try {
    await props.train()
    msg.value = 'Đã huấn luyện xong'
    emit('trained')
  } catch (e) {
    msg.value = extractError(e)
  } finally {
    training.value = false
  }
}
</script>

<style scoped>
.model-card { border: 1px solid var(--color-border); border-radius: 4px; padding: 12px 14px; margin-bottom: 16px; background: #fcfcfc; }
.status { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 14px; }
.dot { width: 10px; height: 10px; border-radius: 50%; background: #bbb; }
.dot.on { background: #26aa99; } .dot.wait { background: #d98a00; } .dot.off { background: #d0011b; }
.faint { color: var(--color-text-faint); }
.small { font-size: 12px; line-height: 1.6; margin: 8px 0 0; }
.msg { color: var(--color-text-muted); font-size: 13px; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; margin: 8px 0; }
.tbl th, .tbl td { text-align: left; padding: 7px 8px; border-bottom: 1px solid var(--color-border); }
.tbl th { color: var(--color-text-muted); font-weight: 500; }
.tbl td span { font-variant-numeric: tabular-nums; }
.tbl tr.best td:first-child { font-weight: 600; color: var(--color-primary); }
.bar { height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; margin-bottom: 3px; max-width: 120px; }
.bar i { display: block; height: 100%; background: #c5c5c5; }
.tbl tr.best .bar i { background: var(--color-primary); }
.gain { font-weight: 600; font-size: 14px; margin: 6px 0; }
.imp { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-top: 8px; }
.tag { font-size: 12px; padding: 2px 8px; border-radius: 10px; background: #f2f2f2; }
.tag.up { background: #fdecea; color: #b42318; }
.tag.down { background: #e7f6f3; color: #11756a; }
@media (max-width: 640px) { .tbl { display: block; overflow-x: auto; } }
</style>
