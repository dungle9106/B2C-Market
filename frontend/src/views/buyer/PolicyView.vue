<template>
  <div>
    <AppHeader />
    <div class="container page">
      <aside class="nav">
        <h3>Chính sách Shoppi</h3>
        <router-link v-for="p in list" :key="p.key" :to="`/policies/${p.slug}`" :class="{ active: p.slug === slug }">{{ p.title }}</router-link>
      </aside>
      <article class="doc">
        <div v-if="!doc" class="state">{{ error || 'Đang tải...' }}</div>
        <template v-else>
          <h1>{{ doc.title }}</h1>
          <div class="meta">
            Phiên bản {{ doc.version }}<template v-if="doc.publishedAt"> · có hiệu lực từ {{ formatDateTime(doc.publishedAt) }}</template>
            <template v-if="!current"> · <router-link :to="`/policies/${slug}`">Xem phiên bản hiện hành</router-link></template>
          </div>
          <p v-if="!current" class="old">Đây là phiên bản cũ, không còn hiệu lực.</p>
          <p v-if="doc.changeNote" class="change">Thay đổi: {{ doc.changeNote }}</p>
          <template v-for="(b, i) in blocks" :key="i">
            <h2 v-if="b.heading">{{ b.text }}</h2>
            <p v-else>{{ b.text }}</p>
          </template>
          <div v-if="doc.history.length > 1" class="history">
            <b>Các phiên bản:</b>
            <router-link v-for="h in doc.history" :key="h.version" :to="`/policies/${slug}?version=${h.version}`">v{{ h.version }} ({{ h.publishedAt ? new Date(h.publishedAt).toLocaleDateString('vi-VN') : 'ban đầu' }})</router-link>
          </div>
        </template>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import { policies, policy } from '../../api/policies'
import { formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const route = useRoute()
const list = ref([])
const doc = ref(null)
const error = ref('')

const slug = computed(() => route.params.slug)
const current = computed(() => !doc.value || doc.value.current)
// lines starting with "#" are section titles, the others paragraphs
const blocks = computed(() => (doc.value?.content || '').split('\n').map(l => l.trim()).filter(Boolean)
  .map(l => (l.startsWith('#') ? { heading: true, text: l.replace(/^#+\s*/, '') } : { heading: false, text: l })))

async function load() {
  doc.value = null
  error.value = ''
  try {
    doc.value = (await policy(slug.value, route.query.version)).data
    document.title = `${doc.value.title} | Shoppi`
  } catch (e) {
    error.value = extractError(e)
  }
}
watch(() => [slug.value, route.query.version], load, { immediate: true })
onMounted(() => policies().then(({ data }) => { list.value = data }).catch(() => {}))
</script>

<style scoped>
.page { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 20px; padding: 20px 0 40px; align-items: start; }
.nav { background: white; border-radius: var(--radius-md); padding: 14px; display: flex; flex-direction: column; gap: 4px; }
.nav h3 { margin: 0 0 8px; font-size: 15px; }
.nav a { padding: 6px 8px; border-radius: 4px; color: var(--color-text); font-size: 14px; }
.nav a.active, .nav a:hover { background: var(--color-primary-light); color: var(--color-primary); }
.doc { background: white; border-radius: var(--radius-md); padding: 24px 28px; min-height: 300px; }
.doc h1 { margin: 0 0 6px; font-size: 24px; }
.doc h2 { font-size: 17px; margin: 18px 0 6px; }
.doc p { line-height: 1.75; margin: 0 0 10px; font-size: 15px; }
.meta { color: var(--color-text-faint); font-size: 13px; margin-bottom: 16px; }
.meta a { color: #2673dd; }
.old { background: #fff7e6; color: #b35c00; padding: 8px 12px; border-radius: 4px; font-size: 14px; }
.change { background: #f3f0ff; padding: 8px 12px; border-radius: 4px; font-size: 14px; }
.history { margin-top: 20px; padding-top: 12px; border-top: 1px solid var(--color-border); display: flex; gap: 10px; flex-wrap: wrap; font-size: 13px; }
.history a { color: #2673dd; }
.state { color: var(--color-text-muted); text-align: center; padding: 40px 0; }
@media (max-width: 760px) { .page { grid-template-columns: 1fr; } }
</style>
