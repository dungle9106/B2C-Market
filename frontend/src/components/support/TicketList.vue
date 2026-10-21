<template>
  <div>
    <nav class="tabs">
      <button type="button" :class="{ active }" @click="switchTab(true)">Đang xử lý</button>
      <button type="button" :class="{ active: !active }" @click="switchTab(false)">Đã đóng</button>
    </nav>

    <div v-if="loading" class="empty">Đang tải...</div>
    <div v-else-if="!page.items.length" class="empty">
      {{ active ? 'Bạn không có yêu cầu hỗ trợ nào đang xử lý.' : 'Chưa có yêu cầu nào đã đóng.' }}
    </div>
    <ul v-else class="list">
      <li v-for="t in page.items" :key="t.id">
        <router-link :to="detailBase + t.id" class="row">
          <div class="main">
            <div class="subject">
              <span v-if="t.lastReplyByStaff && t.status !== 'CLOSED'" class="new-reply" title="Có phản hồi mới từ Shoppi"></span>
              {{ t.subject }}
            </div>
            <div class="meta">{{ t.code }} · {{ TICKET_CATEGORIES[t.category] }}<template v-if="t.orderCode"> · Đơn {{ t.orderCode }}</template> · Cập nhật {{ timeAgo(t.updatedAt) }}</div>
          </div>
          <span class="ticket-status" :class="t.status">{{ TICKET_STATUS[t.status] }}</span>
        </router-link>
      </li>
    </ul>
    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PaginationBar from '../PaginationBar.vue'
import { myTickets } from '../../api/support'
import { TICKET_CATEGORIES, TICKET_STATUS } from '../../utils/support'
import { timeAgo } from '../../utils/notifications'

// '/help/tickets/' for buyers, '/seller/support/' inside the seller centre
defineProps({ detailBase: { type: String, required: true } })

const active = ref(true)
const page = ref({ items: [], page: 0, totalPages: 0 })
const loading = ref(true)

async function load(p = 0) {
  loading.value = true
  try {
    const { data } = await myTickets({ active: active.value, page: p })
    page.value = data
  } finally {
    loading.value = false
  }
}

function switchTab(value) {
  active.value = value
  load(0)
}

load(0)
</script>

<style scoped>
.tabs { display: flex; border-bottom: 1px solid var(--color-border); margin-bottom: 4px; }
.tabs button { padding: 10px 16px; background: none; border: none; border-bottom: 2px solid transparent; font-size: 14px; cursor: pointer; color: var(--color-text); }
.tabs button.active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: 500; }
.list { list-style: none; margin: 0; padding: 0; }
.row { display: flex; align-items: center; gap: 12px; padding: 14px 4px; border-bottom: 1px solid var(--color-border); }
.row:hover { background: #fafafa; }
.main { flex: 1; min-width: 0; }
.subject { font-weight: 500; display: flex; align-items: center; gap: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.new-reply { width: 8px; height: 8px; border-radius: 50%; background: var(--color-primary); flex-shrink: 0; }
.meta { font-size: 12px; color: var(--color-text-faint); margin-top: 4px; }
.empty { text-align: center; color: var(--color-text-faint); padding: 32px 0; font-size: 14px; }
</style>
