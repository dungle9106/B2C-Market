<template>
  <form ref="rootEl" class="search-form" role="search" @submit.prevent="submit(keyword)">
    <input
      v-model="keyword"
      type="text"
      placeholder="Tìm kiếm sản phẩm, thương hiệu và shop..."
      autocomplete="off"
      role="combobox"
      aria-autocomplete="list"
      :aria-expanded="open"
      aria-controls="search-suggestions"
      @focus="open = true"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="onEnter"
      @keydown.esc="open = false"
    />
    <button class="search-btn" type="submit" aria-label="Tìm kiếm">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M21 21L15.5 15.5M18 10.5C18 14.6421 14.6421 18 10.5 18C6.35786 18 3 14.6421 3 10.5C3 6.35786 6.35786 3 10.5 3C14.6421 3 18 6.35786 18 10.5Z" stroke="white" stroke-width="2" stroke-linecap="round"/></svg>
    </button>

    <ul v-if="open && entries.length" id="search-suggestions" class="dropdown" role="listbox">
      <li v-if="!keyword.trim()" class="dropdown-head">
        <span>Tìm kiếm gần đây</span>
        <a href="#" @mousedown.prevent="clearRecent">Xóa</a>
      </li>
      <li
        v-for="(e, i) in entries"
        :key="e.key"
        role="option"
        :aria-selected="i === active"
        :class="['entry', { active: i === active }]"
        @mousedown.prevent="choose(e)"
        @mouseenter="active = i"
      >
        <svg v-if="e.type === 'recent'" class="entry-icon" width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M12 7v5l3 2M21 12a9 9 0 1 1-9-9 9 9 0 0 1 9 9Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
        <svg v-else-if="e.type === 'category'" class="entry-icon" width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" stroke="currentColor" stroke-width="2" /></svg>
        <template v-if="e.type === 'category'">
          Tìm trong danh mục <strong class="text-primary">{{ e.label }}</strong>
        </template>
        <template v-else>
          <span>{{ e.parts[0] }}</span><strong>{{ e.parts[1] }}</strong><span>{{ e.parts[2] }}</span>
        </template>
      </li>
    </ul>
  </form>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { suggestProducts } from '../api/products'

const route = useRoute()
const router = useRouter()

const keyword = ref(route.query.q || '')
const open = ref(false)
const active = ref(-1)
const suggestions = ref({ keywords: [], categories: [] })
const recent = ref(loadRecent())
const rootEl = ref(null)

// keep the box in sync when navigating between searches
watch(() => route.query.q, (q) => { keyword.value = q || '' })

// accent/case-insensitive, same idea as the backend's TextNormalizer;
// it maps 1 char -> 1 char, so indexes in the normalised text match the original
const normalize = (s) => s.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase()

function highlight(text, typed) {
  const at = typed ? normalize(text).indexOf(normalize(typed)) : -1
  if (at < 0) return [text, '', '']
  return [text.slice(0, at), text.slice(at, at + typed.length), text.slice(at + typed.length)]
}

const entries = computed(() => {
  const typed = keyword.value.trim()
  if (!typed) {
    return recent.value.map(k => ({ key: 'r-' + k, type: 'recent', value: k, parts: [k, '', ''] }))
  }
  return [
    ...suggestions.value.categories.map(c => ({ key: 'c-' + c.id, type: 'category', id: c.id, label: c.name })),
    ...suggestions.value.keywords.map(k => ({ key: 'k-' + k, type: 'keyword', value: k, parts: highlight(k, typed) }))
  ]
})

// debounce typing so we don't hit the API on every keystroke
let timer = null
let lastRequest = 0
watch(keyword, (value) => {
  active.value = -1
  clearTimeout(timer)
  const typed = value.trim()
  if (!typed) { suggestions.value = { keywords: [], categories: [] }; return }
  timer = setTimeout(async () => {
    const requestId = ++lastRequest
    try {
      const { data } = await suggestProducts(typed)
      if (requestId === lastRequest) suggestions.value = data // ignore out-of-order replies
    } catch { /* suggestions are best-effort */ }
  }, 250)
})

function move(step) {
  open.value = true
  const n = entries.value.length
  if (!n) return
  active.value = (active.value + step + n) % n
}

function onEnter() {
  const e = entries.value[active.value]
  if (open.value && e) choose(e)
  else submit(keyword.value)
}

function choose(e) {
  if (e.type === 'category') {
    open.value = false
    router.push({ name: 'category', params: { id: e.id } })
  } else {
    submit(e.value)
  }
}

function submit(value) {
  const q = (value || '').trim()
  open.value = false
  if (!q) return
  keyword.value = q
  saveRecent(q)
  router.push({ name: 'search', query: { q } })
}

// recent searches are a per-browser convenience; storage may be unavailable
const RECENT_KEY = 'recent_searches'
function loadRecent() {
  try { return JSON.parse(localStorage.getItem(RECENT_KEY)) || [] } catch { return [] }
}
function saveRecent(q) {
  recent.value = [q, ...recent.value.filter(x => x.toLowerCase() !== q.toLowerCase())].slice(0, 8)
  try { localStorage.setItem(RECENT_KEY, JSON.stringify(recent.value)) } catch { /* ignore */ }
}
function clearRecent() {
  recent.value = []
  try { localStorage.removeItem(RECENT_KEY) } catch { /* ignore */ }
}

function onClickOutside(e) {
  if (rootEl.value && !rootEl.value.contains(e.target)) open.value = false
}
onMounted(() => document.addEventListener('mousedown', onClickOutside))
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onClickOutside)
  clearTimeout(timer)
})
</script>

<style scoped>
.search-form { flex: 1; display: flex; background: white; border-radius: 3px; position: relative; }
.search-form input {
  flex: 1; border: none; margin: 0; padding: 10px 14px;
  border-radius: 3px 0 0 3px; font-size: 14px;
}
.search-form input:focus { outline: none; }
.search-btn {
  background: var(--color-primary-dark);
  border: none; border-radius: 0 3px 3px 0;
  padding: 0 18px; margin: 3px; cursor: pointer;
  display: flex; align-items: center;
}
.search-btn:hover { background: #b8280d; }

.dropdown {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0; z-index: 200;
  list-style: none; margin: 0; padding: 6px 0;
  background: white; border-radius: 2px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  max-height: 420px; overflow-y: auto;
}
.dropdown-head { display: flex; justify-content: space-between; padding: 6px 14px; font-size: 12px; color: var(--color-text-faint); }
.dropdown-head a { color: var(--color-primary); }
.entry { display: flex; align-items: center; gap: 8px; padding: 9px 14px; font-size: 14px; color: var(--color-text); cursor: pointer; }
.entry.active { background: #fafafa; }
.entry strong { font-weight: 700; }
.entry-icon { color: var(--color-text-faint); flex-shrink: 0; }
</style>
