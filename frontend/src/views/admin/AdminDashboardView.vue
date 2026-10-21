<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Tổng Quan Toàn Sàn</h2>
        <span class="sc-card-sub">Xin chào {{ me?.fullName }} · {{ me?.superAdmin ? 'Quản trị viên cấp cao' : 'Nhân viên quản trị' }}</span>
      </div>
      <div v-if="!stats" class="sc-empty">Đang tải...</div>
      <div v-else class="stat-grid">
        <div class="stat"><span>Người mua</span><b>{{ stats.buyers }}</b></div>
        <div class="stat"><span>Người bán</span><b>{{ stats.sellers }}</b></div>
        <div class="stat"><span>Tổng đơn hàng</span><b>{{ stats.totalOrders }}</b></div>
        <div class="stat"><span>Tổng giá trị đơn (GMV)</span><b>{{ formatVnd(stats.totalRevenue) }}</b></div>
      </div>
    </section>

    <section v-if="can('REPORTS')" class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">30 Ngày Qua</h2>
        <span class="sc-card-sub">so với 30 ngày trước đó</span>
        <router-link :to="{ name: 'admin-report-overview' }" class="more">Xem báo cáo chi tiết ›</router-link>
      </div>
      <div class="kpis">
        <KpiCard label="GMV" :loading="!recent" :value="formatVnd(rs.gmv)" :current="rs.gmv" :previous="rp.gmv" :previous-text="formatVnd(rp.gmv)" />
        <KpiCard label="Đơn hàng" :loading="!recent" :value="rs.orders" :current="rs.orders" :previous="rp.orders" :previous-text="rp.orders" />
        <KpiCard label="Người dùng mới" :loading="!recent" :value="rs.newUsers" :current="rs.newUsers" :previous="rp.newUsers" :previous-text="rp.newUsers" />
        <KpiCard label="Người mua đặt hàng" :loading="!recent" :value="rs.buyers" :current="rs.buyers" :previous="rp.buyers" :previous-text="rp.buyers" />
      </div>
      <TrendChart v-if="recent" :labels="recent.series.map(b => b.label)" :height="200" :series="[
        { name: 'GMV', values: recent.series.map(b => Number(b.gmv)), color: '#ee4d2d', type: 'bar', format: formatVnd },
        { name: 'Đơn hàng', values: recent.series.map(b => b.orders), color: '#2673dd', type: 'line', axis: 'right', integer: true }]" />
    </section>

    <section v-if="stats" class="sc-card">
      <h3 class="block-h">Việc cần xử lý</h3>
      <div class="todo">
        <router-link v-if="can('SHOPS')" :to="{ name: 'admin-shops', query: { status: 'PENDING' } }" class="todo-item">
          <b :class="{ hot: stats.pendingShops }">{{ stats.pendingShops }}</b><span>Shop chờ duyệt</span>
        </router-link>
        <router-link v-if="can('SHOPS')" :to="{ name: 'admin-shops', query: { status: 'SUSPENDED' } }" class="todo-item">
          <b>{{ stats.suspendedShops }}</b><span>Shop đang bị đình chỉ</span>
        </router-link>
        <router-link v-if="can('USERS')" :to="{ name: 'admin-users', query: { status: 'LOCKED' } }" class="todo-item">
          <b>{{ stats.lockedUsers }}</b><span>Tài khoản bị khóa</span>
        </router-link>
        <router-link v-if="can('ORDERS')" :to="{ name: 'admin-disputes' }" class="todo-item">
          <b :class="{ hot: stats.openDisputes }">{{ stats.openDisputes ?? 0 }}</b><span>Tranh chấp chờ xử lý</span>
        </router-link>
        <router-link v-if="can('FINANCE')" :to="{ name: 'admin-refunds', query: { status: 'PENDING' } }" class="todo-item">
          <b :class="{ hot: stats.pendingRefunds }">{{ stats.pendingRefunds ?? 0 }}</b><span>Hoàn tiền chờ chuyển khoản</span>
        </router-link>
        <router-link v-if="can('VOUCHERS')" :to="{ name: 'admin-flash-sales' }" class="todo-item">
          <b :class="{ hot: stats.pendingFlashItems }">{{ stats.pendingFlashItems ?? 0 }}</b><span>Sản phẩm chờ duyệt Flash Sale</span>
        </router-link>
        <router-link v-if="can('SUPPORT')" :to="{ name: 'admin-support' }" class="todo-item"><b>›</b><span>Yêu cầu hỗ trợ</span></router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import KpiCard from '../../components/analytics/KpiCard.vue'
import TrendChart from '../../components/analytics/TrendChart.vue'
import { isoDate, daysAgo } from '../../composables/useAnalyticsRange'
import { adminStats, platformOverview } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatVnd } from '../../utils/format'

const { me, can } = useAdminMe()
const stats = ref(null)
const recent = ref(null)
const rs = computed(() => recent.value?.summary || {})
const rp = computed(() => recent.value?.previous || {})

onMounted(async () => {
  stats.value = (await adminStats()).data
})
// the signed-in admin may still be loading: fetch the 30-day block once we know they can see reports
watch(() => can('REPORTS'), (ok) => {
  if (!ok || recent.value) return
  platformOverview({ from: isoDate(daysAgo(29)), to: isoDate(new Date()), granularity: 'day' })
    .then(({ data }) => { recent.value = data }).catch(() => {})
}, { immediate: true })
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.more { margin-left: auto; color: var(--color-primary); font-size: 13px; }
@media (max-width: 900px) { .kpis { grid-template-columns: 1fr 1fr; } }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.stat { border: 1px solid var(--color-border); border-top: 3px solid var(--color-primary); border-radius: 4px; padding: 14px 16px; display: flex; flex-direction: column; gap: 6px; }
.stat span { color: var(--color-text-muted); font-size: 13px; }
.stat b { font-size: 24px; }
.block-h { font-size: 16px; font-weight: 600; margin: 0 0 12px; }
.todo { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }
.todo-item { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 14px 8px; border-radius: 4px; text-align: center; font-size: 14px; }
.todo-item:hover { background: #f8f8f8; }
.todo-item b { font-size: 22px; color: #2673dd; font-weight: 500; }
.todo-item b.hot { color: var(--color-primary); }
@media (max-width: 900px) { .stat-grid { grid-template-columns: 1fr 1fr; } .todo { grid-template-columns: repeat(2, 1fr); } }
</style>
