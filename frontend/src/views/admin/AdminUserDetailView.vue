<template>
  <div v-if="error" class="sc-card sc-empty">{{ error }}</div>
  <div v-else-if="!u" class="sc-card sc-empty">Đang tải...</div>
  <div v-else>
    <section class="sc-card head">
      <router-link :to="{ name: 'admin-users' }" class="back">‹ Danh sách người dùng</router-link>
      <div class="profile">
        <span class="avatar">{{ u.fullName.charAt(0).toUpperCase() }}</span>
        <div class="info">
          <h2>{{ u.fullName }}
            <span class="role" :class="u.role">{{ ROLE[u.role] }}</span>
            <span v-if="u.superAdmin" class="role super">Cấp cao</span>
            <span class="status" :class="u.status">{{ STATUS[u.status] }}</span>
          </h2>
          <div class="faint">{{ u.email }}<template v-if="u.phone"> · {{ u.phone }}</template> · {{ u.authProvider === 'GOOGLE' ? 'Đăng nhập Google' : 'Email & mật khẩu' }}</div>
          <div class="faint">Tham gia {{ formatDateTime(u.createdAt) }} · Đăng nhập gần nhất: {{ u.lastLoginAt ? formatDateTime(u.lastLoginAt) : 'chưa ghi nhận' }}</div>
        </div>
        <div v-if="canAct" class="head-actions">
          <button v-if="u.status === 'ACTIVE'" type="button" class="btn sm secondary" @click="action = 'lock'">Khóa tài khoản</button>
          <button v-else-if="u.status === 'LOCKED'" type="button" class="btn sm" @click="onUnlock">Mở khóa</button>
          <button v-if="u.status !== 'DELETED'" type="button" class="btn sm ghost danger" @click="action = 'delete'">Xóa tài khoản</button>
        </div>
      </div>
      <div v-if="u.status === 'LOCKED'" class="alert">
        🔒 Bị khóa {{ u.lockedUntil ? 'đến ' + formatDateTime(u.lockedUntil) : 'vĩnh viễn' }} (từ {{ formatDateTime(u.lockedAt) }}). Lý do: {{ u.lockedReason }}
      </div>
      <div v-if="u.status === 'DELETED'" class="alert grey">Tài khoản đã bị xóa (ẩn danh hóa) lúc {{ formatDateTime(u.deletedAt) }}. Lý do: {{ u.lockedReason }}</div>
    </section>

    <div class="grid">
      <section class="sc-card">
        <h3 class="block-h">Hoạt động mua hàng</h3>
        <div class="kv">
          <div><b>{{ u.buyer.orders }}</b><span>Đơn đã đặt</span></div>
          <div><b>{{ formatVnd(u.buyer.spent) }}</b><span>Đã chi tiêu</span></div>
          <div><b :class="{ bad: u.buyer.cancelled >= 3 }">{{ u.buyer.cancelled }}</b><span>Đơn đã hủy</span></div>
          <div><b>{{ u.buyer.openOrders }}</b><span>Đang xử lý</span></div>
        </div>
        <table v-if="u.buyer.recentOrders.length" class="sc-table">
          <thead><tr><th>Mã đơn</th><th>Shop</th><th>Trạng thái</th><th class="r">Tổng</th></tr></thead>
          <tbody>
            <tr v-for="o in u.buyer.recentOrders" :key="o.id">
              <td>{{ o.code }}<div class="faint">{{ formatDateTime(o.createdAt) }}</div></td>
              <td>{{ o.shopName }}</td>
              <td>{{ statusLabel(o.status) }}</td>
              <td class="r">{{ formatVnd(o.total) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="faint">Chưa có đơn hàng nào</p>
      </section>

      <section v-if="u.shopDetail" class="sc-card">
        <h3 class="block-h">Shop của người bán</h3>
        <div class="shop">
          <img v-if="u.shopDetail.logoUrl" :src="u.shopDetail.logoUrl" alt="" />
          <span v-else class="avatar sm">{{ u.shopDetail.name.charAt(0) }}</span>
          <div>
            <router-link :to="`/shop/${u.shopDetail.id}`" target="_blank" class="shop-name">{{ u.shopDetail.name }}</router-link>
            <div class="faint">{{ SHOP_STATUS[u.shopDetail.status] }}{{ u.shopDetail.verified ? ' · ✓ Đã xác minh' : '' }} · {{ u.shopDetail.province }}</div>
          </div>
          <router-link v-if="can('SHOPS')" :to="{ name: 'admin-shops', query: { q: u.shopDetail.name } }" class="link">Quản lý shop ›</router-link>
        </div>
        <div v-if="u.shopDetail.statusReason" class="alert">{{ u.shopDetail.statusReason }}</div>
        <div class="kv">
          <div><b>{{ u.shopDetail.activeProducts }}/{{ u.shopDetail.products }}</b><span>Sản phẩm đang bán</span></div>
          <div><b>{{ u.shopDetail.orders }}</b><span>Đơn hàng</span></div>
          <div><b>{{ formatVnd(u.shopDetail.revenue) }}</b><span>Doanh thu đã giao</span></div>
          <div><b>{{ u.shopDetail.openOrders }}</b><span>Đang xử lý</span></div>
        </div>
      </section>

      <section v-if="u.permissions" class="sc-card">
        <h3 class="block-h">Quyền quản trị</h3>
        <div class="perms">
          <span v-for="p in u.permissions" :key="p" class="perm">{{ permissionLabel[p] || p }}</span>
        </div>
        <router-link v-if="can('STAFF')" :to="{ name: 'admin-staff' }" class="link">Phân quyền nhân viên ›</router-link>
      </section>

      <section class="sc-card">
        <h3 class="block-h">Lịch sử xử lý</h3>
        <div v-if="!u.history.length" class="faint">Chưa có thao tác quản trị nào với tài khoản này</div>
        <ol class="history">
          <li v-for="h in u.history" :key="h.id">
            <strong>{{ AUDIT_ACTION[h.action] || h.action }}</strong> · {{ h.adminName }}
            <div class="faint">{{ formatDateTime(h.createdAt) }} — {{ h.detail }}</div>
          </li>
        </ol>
      </section>
    </div>

    <UserActionDialog v-if="action" :mode="action" :user="u" @close="action = null" @done="action = null; load()" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import UserActionDialog from '../../components/admin/UserActionDialog.vue'
import { adminUser, unlockUser } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatDateTime, formatVnd } from '../../utils/format'
import { statusLabel } from '../../utils/orderStatus'
import { extractError } from '../../utils/apiError'
import { ROLE, STATUS, SHOP_STATUS, AUDIT_ACTION } from '../../utils/adminLabels'

const route = useRoute()
const { me, can, permissionLabel } = useAdminMe()
const u = ref(null)
const error = ref('')
const action = ref(null)

const canAct = computed(() => {
  if (!me.value || !u.value || u.value.id === me.value.id || u.value.status === 'DELETED') return false
  if (u.value.role === 'ADMIN') return me.value.superAdmin && !u.value.superAdmin
  return true
})

async function load() {
  error.value = ''
  try {
    u.value = (await adminUser(route.params.id)).data
  } catch (e) {
    error.value = extractError(e)
  }
}

async function onUnlock() {
  if (!confirm('Mở khóa tài khoản này?')) return
  try {
    await unlockUser(u.value.id)
    load()
  } catch (e) {
    error.value = extractError(e)
  }
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.back { color: var(--color-text-muted); font-size: 14px; }
.profile { display: flex; align-items: center; gap: 16px; margin-top: 12px; flex-wrap: wrap; }
.avatar { width: 56px; height: 56px; border-radius: 50%; background: #eef2f7; color: #5b7083; font-size: 22px; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.avatar.sm { width: 40px; height: 40px; font-size: 16px; }
.info { flex: 1; min-width: 0; }
.info h2 { margin: 0 0 4px; font-size: 20px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.faint { color: var(--color-text-faint); font-size: 13px; }
.head-actions { display: flex; gap: 8px; }
.btn.danger { color: #d33; border-color: #f0b4b4; }
.role { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #f0f5ff; color: #2673dd; font-weight: 400; }
.role.SELLER { background: #fff4e5; color: #b35c00; }
.role.ADMIN { background: #f3f0ff; color: #6941c6; }
.role.super { background: #6941c6; color: white; }
.status { font-size: 12px; padding: 2px 8px; border-radius: 3px; font-weight: 400; }
.status.ACTIVE { background: #eaf7ef; color: #1e9e4a; }
.status.LOCKED { background: #fdecea; color: #d33; }
.status.DELETED { background: #f2f2f2; color: #888; }
.alert { margin-top: 12px; background: #fdecea; color: #b42318; padding: 8px 12px; border-radius: 4px; font-size: 13px; }
.alert.grey { background: #f2f2f2; color: #666; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: start; }
.grid .sc-card { margin-bottom: 0; }
.block-h { font-size: 16px; font-weight: 600; margin: 0 0 12px; }
.kv { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 12px; }
.kv > div { border: 1px solid var(--color-border); border-radius: 4px; padding: 8px 10px; display: flex; flex-direction: column; font-size: 12px; color: var(--color-text-muted); }
.kv b { font-size: 16px; color: var(--color-text); font-weight: 600; }
.bad { color: #d33 !important; }
.r { text-align: right; }
.sc-table td, .sc-table th { padding: 8px 10px; font-size: 13px; }
.shop { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.shop img { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.shop > div { flex: 1; }
.shop-name { font-weight: 600; color: #2673dd; }
.link { color: #2673dd; font-size: 13px; }
.perms { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.perm { background: #f3f0ff; color: #6941c6; font-size: 12px; padding: 3px 10px; border-radius: 999px; }
.history { list-style: none; padding: 0; margin: 0; }
.history li { padding: 8px 0; border-bottom: 1px solid #f3f3f3; font-size: 14px; }
@media (max-width: 1100px) { .grid { grid-template-columns: 1fr; } .kv { grid-template-columns: 1fr 1fr; } }
</style>
