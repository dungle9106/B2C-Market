<template>
  <div class="kpi">
    <div class="label">{{ label }}<span v-if="hint" class="hint" :title="hint">?</span></div>
    <div class="value">{{ loading ? '–' : value }}</div>
    <div v-if="!loading && previous !== undefined" class="change" :class="change.dir" :title="'Kỳ trước: ' + previousText">
      {{ change.text }} <span class="vs">so với kỳ trước</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { changeOf } from '../../composables/useAnalyticsRange'

// points: the metric is already a %, compare in percentage points; invert: going down is good (cancel rate...)
const props = defineProps({
  label: String,
  value: [String, Number],
  current: [Number, String],
  previous: { type: [Number, String], default: undefined },
  previousText: [String, Number],
  hint: String,
  points: Boolean,
  pointsUnit: { type: String, default: 'điểm %' },
  invert: Boolean,
  loading: Boolean
})
const change = computed(() => changeOf(props.current, props.previous, { points: props.points, pointsUnit: props.pointsUnit, invert: props.invert }))
</script>

<style scoped>
.kpi { padding: 14px 16px; border: 1px solid var(--color-border); border-radius: 4px; min-width: 0; }
.label { font-size: 13px; color: var(--color-text-muted); display: flex; align-items: center; gap: 4px; }
.hint {
  width: 14px; height: 14px; border-radius: 50%; border: 1px solid #aaa; color: #999;
  font-size: 10px; display: inline-flex; align-items: center; justify-content: center; cursor: help;
}
.value { font-size: 22px; font-weight: 500; margin: 6px 0 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.change { font-size: 12px; color: var(--color-text-faint); }
.change.up { color: #1e9e4a; }
.change.down { color: #d33; }
.vs { color: var(--color-text-faint); }
</style>
