<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Shoppi Voucher</h2>
        <span class="sc-card-sub">Voucher do Shoppi tài trợ, áp dụng cho mọi shop. Khi đơn gồm nhiều shop, mức giảm được chia theo giá trị từng shop và không trừ vào tiền người bán nhận.</span>
        <button type="button" class="btn sm add" @click="editing = {}">+ Tạo Shoppi Voucher</button>
      </div>

      <div class="kpis">
        <div class="kpi"><span>Đang chạy</span><strong>{{ list.filter(v => stateOf(v).key === 'running').length }}</strong></div>
        <div class="kpi"><span>Lượt đặt hàng dùng voucher</span><strong>{{ sum('ordersCount').toLocaleString('vi-VN') }}</strong></div>
        <div class="kpi"><span>Shoppi đã chi</span><strong>{{ formatVnd(sum('spent')) }}</strong></div>
        <div class="kpi accent"><span>Doanh số từ voucher</span><strong>{{ formatVnd(sum('orderValue')) }}</strong>
          <small v-if="sum('spent')">ROI {{ (sum('orderValue') / sum('spent')).toFixed(1) }}x</small></div>
      </div>

      <nav class="sc-tabs">
        <a v-for="t in TABS" :key="t.key" href="#" :class="{ active: tab === t.key }" @click.prevent="tab = t.key">
          {{ t.label }} <span class="count">({{ list.filter(t.match).length }})</span>
        </a>
      </nav>

      <div v-if="loading" class="sc-empty">Đang tải...</div>
      <div v-else-if="!shown.length" class="sc-empty">Chưa có voucher nào</div>
      <div v-else class="sc-table-wrap">
        <table class="sc-table">
          <thead><tr><th>Voucher</th><th>Điều kiện</th><th>Lượt dùng</th><th>Hiệu quả</th><th>Ngân sách</th><th>Thời gian</th><th>Trạng thái</th><th></th></tr></thead>
          <tbody>
            <tr v-for="v in shown" :key="v.id">
              <td>
                <strong>{{ v.title }}</strong>
                <div><span class="code">{{ v.code }}</span><span v-if="!v.publicVisible" class="tag">Ẩn</span></div>
                <router-link v-if="v.campaignName" :to="`/campaigns/${v.campaignSlug}`" target="_blank" class="camp">🎯 {{ v.campaignName }}</router-link>
              </td>
              <td class="small">
                {{ v.minOrder ? 'Đơn từ ' + formatVnd(v.minOrder) : 'Mọi đơn' }}
                <div v-if="v.newBuyerOnly" class="tag new">Khách hàng mới</div>
                <div v-if="v.categoryNames?.length" class="faint" :title="v.categoryNames.join(', ')">Ngành: {{ cut(v.categoryNames.join(', '), 40) }}</div>
              </td>
              <td class="nowrap">{{ v.usedCount }}{{ v.usageLimit ? ' / ' + v.usageLimit : '' }}<div class="faint">{{ v.perUserLimit ? v.perUserLimit + ' lượt/người' : 'Không giới hạn/người' }}</div></td>
              <td class="small nowrap">{{ v.ordersCount }} lượt đặt · {{ v.buyersCount }} người mua<br />Doanh số {{ formatVnd(v.orderValue) }}</td>
              <td class="small budget">
                <template v-if="v.budget">
                  {{ formatVnd(v.spent) }} / {{ formatVnd(v.budget) }}
                  <div class="bar"><div :class="{ full: v.spent >= v.budget }" :style="{ width: Math.min(100, v.spent / v.budget * 100) + '%' }"></div></div>
                </template>
                <template v-else>Đã chi {{ formatVnd(v.spent) }}<div class="faint">không giới hạn</div></template>
              </td>
              <td class="small nowrap">{{ formatDateTime(v.startAt) }}<br />→ {{ formatDateTime(v.endAt) }}</td>
              <td><span class="st" :class="stateOf(v).key">{{ stateOf(v).label }}</span></td>
              <td class="actions">
                <template v-if="stateOf(v).key !== 'ended'">
                  <button type="button" class="link" @click="editing = v">Sửa</button>
                  <button type="button" class="link" @click="run(() => togglePlatformVoucher(v.id))">{{ v.active ? 'Tạm dừng' : 'Tiếp tục' }}</button>
                  <button type="button" class="link danger" @click="confirm(`Kết thúc voucher ${v.code} ngay bây giờ?`) && run(() => endPlatformVoucher(v.id))">Kết thúc</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <AdminVoucherForm v-if="editing" :voucher="editing.id ? editing : null" @close="editing = null" @saved="editing = null; load()" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminVoucherForm from '../../components/admin/AdminVoucherForm.vue'
import { platformVouchers, togglePlatformVoucher, endPlatformVoucher } from '../../api/vouchers'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const list = ref([])
const loading = ref(true)
const tab = ref('all')
const editing = ref(null)
const error = ref('')

function stateOf(v) {
  const now = new Date()
  if (new Date(v.endAt) <= now) return { key: 'ended', label: 'Đã kết thúc' }
  if (!v.active) return { key: 'paused', label: 'Tạm dừng' }
  if (v.usageLimit && v.usedCount >= v.usageLimit) return { key: 'out', label: 'Hết lượt' }
  if (v.budget && v.spent >= v.budget) return { key: 'out', label: 'Hết ngân sách' }
  if (new Date(v.startAt) > now) return { key: 'upcoming', label: 'Sắp diễn ra' }
  return { key: 'running', label: 'Đang diễn ra' }
}
const TABS = [
  { key: 'all', label: 'Tất cả', match: () => true },
  { key: 'running', label: 'Đang diễn ra', match: (v) => stateOf(v).key === 'running' },
  { key: 'upcoming', label: 'Sắp diễn ra', match: (v) => stateOf(v).key === 'upcoming' },
  { key: 'ended', label: 'Đã kết thúc / Tạm dừng', match: (v) => ['ended', 'paused', 'out'].includes(stateOf(v).key) }
]
const shown = computed(() => list.value.filter(TABS.find(t => t.key === tab.value).match))
const sum = (k) => list.value.reduce((a, v) => a + Number(v[k] || 0), 0)
const cut = (s, n) => (s.length > n ? s.slice(0, n) + '…' : s)

async function load() {
  loading.value = true
  try {
    list.value = (await platformVouchers()).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

async function run(fn) {
  error.value = ''
  try {
    await fn()
    load()
  } catch (e) {
    error.value = extractError(e)
  }
}

onMounted(load)
</script>

<style scoped>
.sc-card-head { position: relative; padding-right: 180px; }
.add { position: absolute; right: 0; top: 0; }
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 12px 14px; display: flex; flex-direction: column; gap: 3px; }
.kpi span, .kpi small { font-size: 12px; color: var(--color-text-muted); }
.kpi strong { font-size: 20px; }
.kpi.accent { background: #f3f0ff; border-color: #e0d8ff; }
.count { color: var(--color-text-faint); margin-left: 2px; }
.code { font-family: ui-monospace, monospace; font-size: 12px; background: #fff4ef; color: var(--color-primary); padding: 1px 6px; border-radius: 3px; }
.tag { font-size: 11px; border: 1px solid #ddd; border-radius: 3px; padding: 0 5px; margin-left: 6px; color: var(--color-text-muted); }
.tag.new { display: inline-block; margin: 3px 0 0; border-color: #a6c8f0; color: #2673dd; }
.camp { display: inline-block; font-size: 12px; color: #6941c6; margin-top: 3px; }
.small { font-size: 13px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.nowrap { white-space: nowrap; }
.budget { min-width: 150px; }
.bar { height: 6px; background: #eee; border-radius: 3px; margin-top: 4px; overflow: hidden; }
.bar div { height: 100%; background: #6941c6; }
.bar div.full { background: #d33; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; white-space: nowrap; background: #f2f2f2; color: #666; }
.st.running { background: #eaf7ef; color: #1e9e4a; }
.st.upcoming { background: #e8f1fd; color: #2673dd; }
.st.paused, .st.out { background: #fff4e5; color: #b35c00; }
.actions { white-space: nowrap; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 5px; }
.link.danger { color: #d33; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 900px) { .kpis { grid-template-columns: 1fr 1fr; } .sc-card-head { padding-right: 0; } .add { position: static; margin-top: 8px; } }
</style>
