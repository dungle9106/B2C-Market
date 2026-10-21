<template>
  <div ref="rootEl" class="bell" :class="tone">
    <button type="button" class="bell-btn" :aria-label="`Thông báo (${store.total} chưa đọc)`" @click="toggle">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
      <span v-if="store.total > 0" class="count">{{ store.total > 99 ? '99+' : store.total }}</span>
    </button>

    <div v-if="open" class="panel" role="dialog" aria-label="Thông báo mới">
      <div class="panel-head">
        <span>Thông báo mới nhận</span>
        <button v-if="store.total > 0" type="button" class="link" @click="onReadAll">Đánh dấu đã đọc tất cả</button>
      </div>
      <div v-if="loading" class="empty">Đang tải...</div>
      <div v-else-if="!items.length" class="empty">Chưa có thông báo nào</div>
      <ul v-else>
        <li v-for="n in items" :key="n.id" :class="{ unread: !n.read }" @click="openItem(n)">
          <img v-if="n.imageUrl" :src="n.imageUrl" alt="" class="thumb" />
          <span v-else class="thumb icon">{{ notificationIcon(n.type) }}</span>
          <div class="body">
            <div class="title">{{ n.title }}</div>
            <div class="content">{{ n.content }}</div>
            <div class="time">{{ timeAgo(n.createdAt) }}</div>
          </div>
        </li>
      </ul>
      <router-link :to="allLink" class="see-all" @click="open = false">Xem tất cả</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'
import { myNotifications, markNotificationRead, markAllNotificationsRead } from '../api/notifications'
import { notificationIcon, timeAgo } from '../utils/notifications'

// tone: 'light' = white icon on the orange buyer header, 'dark' = grey icon on the white seller header
defineProps({ tone: { type: String, default: 'light' } })

const router = useRouter()
const auth = useAuthStore()
const store = useNotificationStore()
const open = ref(false)
const loading = ref(false)
const items = ref([])
const rootEl = ref(null)

const allLink = computed(() => (auth.isSeller ? '/seller/notifications' : '/notifications'))

async function toggle() {
  open.value = !open.value
  if (!open.value) return
  loading.value = true
  try {
    const { data } = await myNotifications({ size: 6 })
    items.value = data.items
  } finally {
    loading.value = false
  }
}

async function openItem(n) {
  open.value = false
  if (!n.read) {
    n.read = true
    markNotificationRead(n.id).then(() => store.refresh()).catch(() => {})
  }
  if (n.link) router.push(n.link)
}

async function onReadAll() {
  await markAllNotificationsRead()
  items.value.forEach(n => { n.read = true })
  store.refresh()
}

function onClickOutside(e) {
  if (open.value && rootEl.value && !rootEl.value.contains(e.target)) open.value = false
}
onMounted(() => {
  store.start()
  document.addEventListener('click', onClickOutside)
})
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<style scoped>
.bell { position: relative; }
.bell-btn {
  position: relative; display: flex; align-items: center; justify-content: center;
  width: 40px; height: 40px; border-radius: 50%; border: none; background: none; cursor: pointer;
}
.light .bell-btn { color: white; }
.dark .bell-btn { color: #555; }
.dark .bell-btn:hover { background: #f3f3f3; }
.count {
  position: absolute; top: 2px; right: 0;
  min-width: 18px; height: 18px; padding: 0 4px; border-radius: 999px;
  font-size: 11px; font-weight: 700; line-height: 18px; text-align: center;
}
.light .count { background: white; color: var(--color-primary); }
.dark .count { background: var(--color-primary); color: white; }

.panel {
  position: absolute; right: -8px; top: calc(100% + 8px); z-index: 200;
  width: 380px; max-width: calc(100vw - 24px);
  background: white; color: var(--color-text);
  border-radius: 3px; box-shadow: 0 6px 24px rgba(0, 0, 0, 0.18);
}
.panel-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px; font-size: 14px; color: var(--color-text-faint); }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
ul { list-style: none; margin: 0; padding: 0; max-height: 420px; overflow-y: auto; }
li { display: flex; gap: 10px; padding: 10px 14px; cursor: pointer; }
li:hover { background: #fafafa; }
li.unread { background: var(--color-primary-light); }
li.unread:hover { background: #ffe6df; }
.thumb { width: 44px; height: 44px; border-radius: 3px; object-fit: cover; flex-shrink: 0; }
.thumb.icon { display: flex; align-items: center; justify-content: center; background: #f5f5f5; font-size: 20px; }
.body { min-width: 0; flex: 1; }
.title { font-size: 14px; font-weight: 500; }
.content { font-size: 13px; color: var(--color-text-muted); margin-top: 2px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.time { font-size: 12px; color: var(--color-text-faint); margin-top: 4px; }
.empty { padding: 32px 0; text-align: center; color: var(--color-text-faint); font-size: 14px; }
.see-all { display: block; text-align: center; padding: 11px; border-top: 1px solid var(--color-border); font-size: 14px; }
.see-all:hover { color: var(--color-primary); }
</style>
