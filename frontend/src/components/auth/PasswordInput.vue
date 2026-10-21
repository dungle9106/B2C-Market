<template>
  <div class="pw-field">
    <input
      :value="modelValue"
      :type="visible ? 'text' : 'password'"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <button type="button" class="pw-toggle" :aria-label="visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'" @click="visible = !visible">
      <svg v-if="visible" width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" stroke="currentColor" stroke-width="1.6"/><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.6"/></svg>
      <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M3 10c2 3 5.2 5 9 5s7-2 9-5M6.5 13.6 5 16M17.5 13.6 19 16M12 15v3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: 'Mật khẩu' },
  autocomplete: { type: String, default: 'current-password' }
})
defineEmits(['update:modelValue'])

const visible = ref(false)
</script>

<style scoped>
.pw-field { position: relative; }
.pw-field input { padding-right: 44px; }
.pw-toggle {
  position: absolute; right: 8px; top: 7px;
  background: none; border: none; cursor: pointer;
  color: var(--color-text-muted); padding: 4px; display: flex;
}
</style>
