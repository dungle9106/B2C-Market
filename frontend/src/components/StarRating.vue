<template>
  <!-- display mode: fractional fill (4.3 -> 4 full stars + 30% of the 5th);
       editable mode: click/hover to pick 1-5 (v-model) -->
  <span
    class="stars"
    :class="{ editable }"
    :style="{ fontSize: size + 'px' }"
    :role="editable ? 'radiogroup' : 'img'"
    :aria-label="editable ? 'Chọn số sao' : `${value} trên 5 sao`"
    @mouseleave="hover = 0"
  >
    <span
      v-for="i in 5"
      :key="i"
      class="star"
      :role="editable ? 'radio' : null"
      :aria-checked="editable ? modelValue === i : null"
      @mouseenter="editable && (hover = i)"
      @click="editable && $emit('update:modelValue', i)"
    >
      <span class="bg">★</span>
      <span class="fg" :style="{ width: fill(i) + '%' }">★</span>
    </span>
  </span>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },        // display mode
  modelValue: { type: Number, default: 0 },   // editable mode
  editable: { type: Boolean, default: false },
  size: { type: Number, default: 12 }
})
defineEmits(['update:modelValue'])

const hover = ref(0)
const shown = computed(() => (props.editable ? hover.value || props.modelValue : props.value))
const fill = (i) => Math.round(Math.max(0, Math.min(1, shown.value - (i - 1))) * 100)
</script>

<style scoped>
.stars { display: inline-flex; gap: 1px; line-height: 1; vertical-align: middle; }
.star { position: relative; display: inline-block; }
.bg { color: #d5d5d5; }
.fg { position: absolute; left: 0; top: 0; overflow: hidden; white-space: nowrap; color: var(--color-star, #ffce3d); }
.editable .star { cursor: pointer; padding: 0 2px; }
.editable .fg { left: 2px; }
</style>
