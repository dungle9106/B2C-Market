<template>
  <div>
    <div class="toolbar">
      <nav class="tabs">
        <button v-for="t in tabs" :key="t.value || 'all'" type="button" :class="{ active: type === t.value }" @click="select(t.value)">
          {{ t.label }}
          <span v-if="unreadOf(t.value)" class="dot">{{ unreadOf(t.value) }}</span>
        </button>
      </nav>
      <button type="button" class="btn ghost sm" :disabled="!unreadOf(type)" @click="onReadAll">Đánh dấu đã đọc tất cả</button>
    </div>

    <div v-if="loading" class="empty">Đang tải...</div>
    <div v-else-if="!page.items.length" class="empty">
      <div class="empty-icon">🔔</div>
      Bạn chưa có thông báo nào{{ type ? ' trong mục này' : '' }}
    </div>

    <ul v-else class="list">
      <li v-for="n in page.items" :key="n.id" :class="{ unread: !n.read, clickable: !!n.link }" @click="open(n)">
        <img v-if="n.imageUrl" :src="n.imageUrl" alt="" class="thumb" />
        <span v-else class="thumb icon">{{ notificationIcon(n.type) }}</span>
        <div class="body">
          <div class="title">{{ n.title }}</div>
          <div class="content">{{ n.content }}</div>
          <div class="time">{{ formatDateTime(n.createdAt) }}</div>
        </div>
        <div class="actions" @click.stop>
          <button v-if="!n.read" type="button" class="link" @click="markRead(n)">Đã đọc</button>
          <button type="button" class="link muted" aria-label="Xóa thông báo" @click="remove(n)">Xóa</button>
        </div>
      </li>
    </ul>

    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PaginationBar from './PaginationBar.vue'
import { myNotifications, markNotificationRead, markAllNotificationsRead, deleteNotification } from '../api/notifications'
import { useNotificationStore } from '../stores/notifications'
import { NOTIFICATION_TYPES, notificationIcon } from '../utils/notifications'
import { formatDateTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const store = useNotificationStore()

const tabs = [{ value: null, label: 'Tất cả' }, ...NOTIFICATION_TYPES.map(t => ({ value: t.value, label: t.label }))]
// the tab lives in the URL so links like /notifications?type=PROMOTION work
const type = ref(route.query.type || null)
const page = ref({ items: [], page: 0, totalPages: 0 })
const loading = ref(true)

const unreadOf = (t) => (t ? store.byType[t] || 0 : store.total)

async function load(p = 0) {
  loading.value = true
  try {
    const { data } = await myNotifications({ type: type.value, page: p, size: 15 })
    page.value = data
  } finally {
    loading.value = false
  }
}

function select(t) {
  router.replace({ query: { ...route.query, type: t || undefined } })
}

async function markRead(n) {
  n.read = true
  await markNotificationRead(n.id)
  store.refresh()
}

function open(n) {
  if (!n.read) markRead(n)
  if (n.link) router.push(n.link)
}

async function remove(n) {
  await deleteNotification(n.id)
  page.value.items = page.value.items.filter(x => x.id !== n.id)
  if (!n.read) store.refresh()
}

async function onReadAll() {
  await markAllNotificationsRead(type.value)
  page.value.items.forEach(n => { n.read = true })
  store.refresh()
}

watch(() => route.query.type, (t) => {
  type.value = t || null
  load(0)
})
load(0)
store.refresh()
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; border-bottom: 1px solid var(--color-border); margin-bottom: 4px; flex-wrap: wrap; }
.tabs { display: flex; overflow-x: auto; }
.tabs button {
  padding: 12px 14px; background: none; border: none; border-bottom: 2px solid transparent;
  font-size: 14px; color: var(--color-text); cursor: pointer; white-space: nowrap; display: inline-flex; align-items: center; gap: 6px;
}
.tabs button:hover { color: var(--color-primary); }
.tabs button.active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: 500; }
.dot { background: var(--color-primary); color: white; border-radius: 999px; font-size: 11px; padding: 0 6px; line-height: 17px; }

.list { list-style: none; margin: 0; padding: 0; }
.list li { display: flex; gap: 14px; padding: 16px 12px; border-bottom: 1px solid var(--color-border); align-items: flex-start; }
.list li.clickable { cursor: pointer; }
.list li.clickable:hover { background: #fafafa; }
.list li.unread { background: var(--color-primary-light); }
.thumb { width: 64px; height: 64px; border-radius: 3px; object-fit: cover; flex-shrink: 0; }
.thumb.icon { display: flex; align-items: center; justify-content: center; background: #f5f5f5; font-size: 28px; }
.body { flex: 1; min-width: 0; }
.title { font-size: 15px; font-weight: 500; }
.content { font-size: 14px; color: var(--color-text-muted); margin-top: 4px; line-height: 1.5; white-space: pre-line; }
.time { font-size: 12px; color: var(--color-text-faint); margin-top: 6px; }
.actions { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; flex-shrink: 0; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.link.muted { color: var(--color-text-faint); }
.link:hover { text-decoration: underline; }
.empty { text-align: center; color: var(--color-text-faint); padding: 56px 0; }
.empty-icon { font-size: 40px; margin-bottom: 8px; opacity: 0.5; }

@media (max-width: 560px) {
  .thumb { width: 44px; height: 44px; }
  .list li { padding: 12px 4px; gap: 10px; }
}
</style>
