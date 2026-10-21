<template>
  <div ref="root" class="picker">
    <button type="button" class="trigger" :disabled="disabled" @click="toggle">📋 Tin nhắn mẫu ▾</button>
    <div v-if="open" class="menu" :class="{ up }">
      <div v-if="!list" class="empty">Đang tải...</div>
      <div v-else-if="!list.length" class="empty">Chưa có tin nhắn mẫu</div>
      <button v-for="t in list" :key="t.id" type="button" class="item" @click="pick(t)">{{ fillPlaceholders(t.content, context) }}</button>
      <router-link :to="{ name: 'seller-care-settings', query: { tab: kind } }" class="manage" target="_blank">⚙ Quản lý tin nhắn mẫu</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useReplyTemplates, fillPlaceholders } from '../../composables/useReplyTemplates'

// kind: CHAT | QUESTION | REVIEW; context fills {ten_khach} {ten_shop} {ten_san_pham}; up: open above the button;
// raw: emit the template as written (placeholders kept), e.g. for a bulk reply the server personalises
const props = defineProps({
  kind: { type: String, required: true },
  context: { type: Object, default: () => ({}) },
  disabled: Boolean,
  up: Boolean,
  raw: Boolean
})
const emit = defineEmits(['pick'])

const { templates, load } = useReplyTemplates()
const open = ref(false)
const root = ref(null)
const list = computed(() => templates.value?.[props.kind] || (templates.value ? [] : null))

function toggle() {
  open.value = !open.value
  if (open.value) load().catch(() => {})
}

function pick(t) {
  emit('pick', props.raw ? t.content : fillPlaceholders(t.content, props.context))
  open.value = false
}

const onDoc = (e) => { if (root.value && !root.value.contains(e.target)) open.value = false }
onMounted(() => document.addEventListener('click', onDoc))
onBeforeUnmount(() => document.removeEventListener('click', onDoc))
</script>

<style scoped>
.picker { position: relative; display: inline-block; }
.trigger { background: white; border: 1px solid #ddd; border-radius: 4px; padding: 5px 10px; font-size: 13px; cursor: pointer; color: var(--color-text-muted); white-space: nowrap; }
.trigger:hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary); }
.menu {
  position: absolute; top: calc(100% + 4px); left: 0; z-index: 20; width: 360px; max-width: 80vw; max-height: 300px; overflow-y: auto;
  background: white; border: 1px solid var(--color-border); border-radius: 6px; box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12); padding: 4px 0;
}
.menu.up { top: auto; bottom: calc(100% + 4px); }
.item { display: block; width: 100%; text-align: left; background: none; border: none; padding: 8px 12px; font-size: 13px; line-height: 1.45; cursor: pointer; font-family: inherit; color: var(--color-text); }
.item:hover { background: var(--color-primary-light); }
.empty { padding: 10px 12px; font-size: 13px; color: var(--color-text-faint); }
.manage { display: block; border-top: 1px solid var(--color-border); padding: 8px 12px; font-size: 12px; color: #2673dd; }
</style>
