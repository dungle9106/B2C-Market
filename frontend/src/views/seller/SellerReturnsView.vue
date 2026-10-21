<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Trả Hàng / Hoàn Tiền</h2>
      <span class="sc-card-sub">Đồng ý để hoàn tiền cho người mua (hàng trả về được cộng lại kho). Nếu từ chối, người mua có thể khiếu nại lên Shoppi.</span>
    </div>

    <nav class="sc-tabs">
      <a v-for="t in tabs" :key="t.key" href="#" :class="{ active: tab === t.key }" @click.prevent="tab = t.key">
        {{ t.label }} <span class="count">({{ list.filter(t.match).length }})</span>
      </a>
    </nav>

    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="!filtered.length" class="sc-empty">Không có yêu cầu nào.</div>

    <ReturnCaseCard v-for="rr in filtered" :key="rr.id" :rr="rr">
      <template v-if="rr.status === 'REQUESTED'">
        <button class="btn sm ghost" type="button" @click="rejecting = rr">Từ chối</button>
        <button class="btn sm" type="button" :disabled="busyId === rr.id" @click="onApprove(rr)">Đồng ý hoàn {{ formatVnd(rr.refundAmount) }}</button>
      </template>
      <!-- the buyer complained: the shop explains its side to Shoppi -->
      <template v-if="rr.complaint" #extra>
        <DisputeThread v-if="rr.status === 'ESCALATED' || shownThreads.has(rr.id)" :return-id="rr.id" side="SELLER"
                       :open="rr.status === 'ESCALATED'" :deadline="rr.sellerResponseDeadline" />
        <button v-else type="button" class="link-btn" @click="shownThreads.add(rr.id)">Xem trao đổi tranh chấp</button>
      </template>
    </ReturnCaseCard>

    <BaseModal v-if="rejecting" title="Từ chối yêu cầu" :width="480" @close="rejecting = null">
      <p class="hint">Hãy nêu rõ lý do (ví dụ: ảnh không thể hiện lỗi, sản phẩm đã qua sử dụng...). Người mua có thể khiếu nại nếu không đồng ý.</p>
      <textarea v-model.trim="note" rows="4" maxlength="1000" placeholder="Lý do từ chối"></textarea>
      <p v-if="error" class="error">{{ error }}</p>
      <template #footer>
        <button type="button" class="btn ghost" @click="rejecting = null">Hủy</button>
        <button type="button" class="btn" :disabled="!note || busyId" @click="onReject">Xác nhận từ chối</button>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import BaseModal from '../../components/BaseModal.vue'
import ReturnCaseCard from '../../components/orders/ReturnCaseCard.vue'
import DisputeThread from '../../components/orders/DisputeThread.vue'
import { shopReturns, approveReturn, rejectReturn } from '../../api/orders'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'

const list = ref([])
const loading = ref(true)
// a dispute notification opens the dispute tab
const tab = ref(useRoute().query.tab === 'disputed' ? 'disputed' : 'pending')
const shownThreads = reactive(new Set())
const busyId = ref(null)
const rejecting = ref(null)
const note = ref('')
const error = ref('')

const tabs = [
  { key: 'pending', label: 'Chờ xử lý', match: (r) => r.status === 'REQUESTED' },
  { key: 'disputed', label: 'Đã từ chối / Khiếu nại', match: (r) => ['REJECTED', 'ESCALATED'].includes(r.status) },
  { key: 'refunded', label: 'Đã hoàn tiền', match: (r) => r.status === 'REFUNDED' },
  { key: 'closed', label: 'Đã đóng', match: (r) => r.status === 'CLOSED' }
]
const filtered = computed(() => list.value.filter(tabs.find(t => t.key === tab.value).match))

async function load() {
  try {
    const { data } = await shopReturns()
    list.value = data
  } finally {
    loading.value = false
  }
}

async function onApprove(rr) {
  if (!confirm(`Đồng ý hoàn ${formatVnd(rr.refundAmount)} cho người mua?`)) return
  busyId.value = rr.id
  try {
    const { data } = await approveReturn(rr.id)
    Object.assign(rr, data)
  } catch (e) {
    alert(extractError(e))
  } finally {
    busyId.value = null
  }
}

async function onReject() {
  error.value = ''
  busyId.value = rejecting.value.id
  try {
    const { data } = await rejectReturn(rejecting.value.id, note.value)
    Object.assign(rejecting.value, data)
    rejecting.value = null
    note.value = ''
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.link-btn { background: none; border: none; padding: 0; color: #2673dd; cursor: pointer; font-size: 13px; }
.hint { font-size: 14px; color: var(--color-text-muted); margin: 0 0 12px; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
</style>
