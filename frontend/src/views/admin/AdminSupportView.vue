<template>
  <div>
    <div class="container page">
      <router-link to="/admin" class="back">‹ Tổng quan toàn sàn</router-link>
      <h2 class="title">Trung Tâm Hỗ Trợ – Bàn Xử Lý Yêu Cầu</h2>

      <div class="stats">
        <button v-for="s in statCards" :key="s.value" type="button" class="stat" :class="{ active: filters.status === s.value }" @click="setStatus(s.value)">
          <span class="stat-num">{{ stats.byStatus?.[s.value] ?? '–' }}</span>
          <span class="stat-label">{{ s.label }}</span>
        </button>
        <div class="stat rating">
          <span class="stat-num">{{ stats.ratingAvg ? Number(stats.ratingAvg).toFixed(1) + ' ★' : '–' }}</span>
          <span class="stat-label">Hài lòng ({{ stats.ratingCount || 0 }} đánh giá)</span>
        </div>
      </div>

      <div class="desk">
        <section class="shopee-section list-pane">
          <form class="filters" @submit.prevent="load(0)">
            <input v-model.trim="filters.q" type="search" placeholder="Mã, tiêu đề, email, tên người gửi" />
            <select v-model="filters.status" @change="load(0)">
              <option value="">Mọi trạng thái</option>
              <option v-for="(label, value) in TICKET_STATUS_STAFF" :key="value" :value="value">{{ label }}</option>
            </select>
            <select v-model="filters.category" @change="load(0)">
              <option value="">Mọi chủ đề</option>
              <option v-for="(label, value) in TICKET_CATEGORIES" :key="value" :value="value">{{ label }}</option>
            </select>
          </form>

          <div v-if="loading" class="empty">Đang tải...</div>
          <div v-else-if="!page.items.length" class="empty">Không có yêu cầu nào.</div>
          <ul v-else class="list">
            <li v-for="t in page.items" :key="t.id" :class="{ active: selectedId === t.id }" @click="select(t.id)">
              <div class="row-top">
                <span class="prio" :class="t.priority">{{ TICKET_PRIORITY[t.priority] }}</span>
                <span class="subject">{{ t.subject }}</span>
              </div>
              <div class="row-meta">
                {{ t.code }} · {{ TICKET_CATEGORIES[t.category] }} · {{ t.requesterName }} ({{ t.requesterRole === 'SELLER' ? 'Người bán' : 'Người mua' }})
              </div>
              <div class="row-meta">
                <span class="ticket-status" :class="t.status">{{ TICKET_STATUS_STAFF[t.status] }}</span>
                <span v-if="!t.lastReplyByStaff && !['RESOLVED', 'CLOSED'].includes(t.status)" class="waiting">Chờ phản hồi · {{ timeAgo(t.updatedAt) }}</span>
                <span v-else>{{ timeAgo(t.updatedAt) }}</span>
              </div>
            </li>
          </ul>
          <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
        </section>

        <section class="shopee-section detail-pane">
          <TicketThread v-if="selectedId" :key="selectedId" :ticket-id="selectedId" staff @changed="onChanged" />
          <div v-else class="empty">Chọn một yêu cầu để xem và phản hồi</div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import TicketThread from '../../components/support/TicketThread.vue'
import { adminTickets, adminTicketStats } from '../../api/support'
import { TICKET_CATEGORIES, TICKET_STATUS_STAFF, TICKET_PRIORITY } from '../../utils/support'
import { timeAgo } from '../../utils/notifications'

const route = useRoute()
const router = useRouter()

const statCards = [
  { value: 'OPEN', label: 'Chờ xử lý' },
  { value: 'IN_PROGRESS', label: 'Đang xử lý' },
  { value: 'WAITING_CUSTOMER', label: 'Chờ người dùng' },
  { value: 'RESOLVED', label: 'Đã giải quyết' }
]
const filters = reactive({ q: '', status: '', category: '' })
const page = ref({ items: [], page: 0, totalPages: 0 })
const stats = ref({})
const loading = ref(true)
// the open ticket lives in the URL so notification links (/admin/support?id=5) land on it
const selectedId = computed(() => (route.query.id ? Number(route.query.id) : null))

async function load(p = 0) {
  loading.value = true
  try {
    const { data } = await adminTickets({ ...filters, page: p })
    page.value = data
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  const { data } = await adminTicketStats()
  stats.value = data
}

function setStatus(s) {
  filters.status = filters.status === s ? '' : s
  load(0)
}

function select(id) {
  router.replace({ query: { ...route.query, id } })
}

function onChanged(t) {
  const i = page.value.items.findIndex(x => x.id === t.id)
  if (i >= 0) page.value.items[i] = { ...page.value.items[i], ...t }
  loadStats()
}

onMounted(() => {
  load(0)
  loadStats()
})
</script>

<style scoped>
.page { padding-bottom: 40px; }
.back { display: inline-block; margin: 16px 0 0; color: var(--color-text-muted); }
.title { font-size: 20px; margin: 10px 0 14px; }

.stats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; }
.stat { background: white; border: 1px solid transparent; border-radius: 6px; box-shadow: var(--shadow-card); padding: 12px 14px; display: flex; flex-direction: column; align-items: flex-start; gap: 2px; cursor: pointer; text-align: left; font: inherit; }
.stat.active { border-color: var(--color-primary); }
.stat.rating { cursor: default; }
.stat-num { font-size: 22px; font-weight: 700; }
.stat-label { font-size: 13px; color: var(--color-text-muted); }

.desk { display: grid; grid-template-columns: 400px 1fr; gap: 12px; align-items: start; }
.list-pane { padding: 12px; }
.filters { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.filters input { grid-column: 1 / -1; }
.filters input, .filters select { margin: 0; }
.list { list-style: none; margin: 10px 0 0; padding: 0; max-height: calc(100vh - 330px); overflow-y: auto; }
.list li { padding: 12px 10px; border-bottom: 1px solid var(--color-border); cursor: pointer; }
.list li:hover { background: #fafafa; }
.list li.active { background: var(--color-primary-light); }
.row-top { display: flex; align-items: center; gap: 8px; }
.subject { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--color-text-faint); margin-top: 5px; flex-wrap: wrap; }
.waiting { color: #d97706; }
.prio { font-size: 11px; padding: 1px 6px; border-radius: 2px; flex-shrink: 0; background: #f0f0f0; color: #777; }
.prio.HIGH { background: #fdecea; color: #d33; }
.prio.URGENT { background: #d33; color: white; }
.prio.LOW { background: #f5f5f5; color: #aaa; }
.detail-pane { min-height: 400px; }
.empty { text-align: center; color: var(--color-text-faint); padding: 40px 0; }

@media (max-width: 1000px) {
  .desk { grid-template-columns: 1fr; }
  .stats { grid-template-columns: repeat(2, 1fr); }
  .list { max-height: 360px; }
}
</style>
