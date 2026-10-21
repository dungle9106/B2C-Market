<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Nhật Ký Quản Trị</h2>
      <span class="sc-card-sub">Mọi thao tác khóa/xóa tài khoản, duyệt/đình chỉ shop, phân quyền và cài đặt sàn</span>
    </div>
    <div class="sc-toolbar">
      <div class="chips">
        <button v-for="t in TYPES" :key="t.key" type="button" :class="{ active: type === t.key }" @click="type = t.key; load(0)">{{ t.label }}</button>
      </div>
    </div>
    <div v-if="!page.items.length" class="sc-empty">Chưa có thao tác nào</div>
    <table v-else class="sc-table">
      <thead><tr><th>Thời gian</th><th>Người thực hiện</th><th>Thao tác</th><th>Đối tượng</th><th>Chi tiết</th></tr></thead>
      <tbody>
        <tr v-for="a in page.items" :key="a.id">
          <td class="nowrap faint">{{ formatDateTime(a.createdAt) }}</td>
          <td>{{ a.adminName }}</td>
          <td><span class="action" :class="tone(a.action)">{{ AUDIT_ACTION[a.action] || a.action }}</span></td>
          <td>
            <router-link v-if="a.targetType === 'USER' && a.targetId && can('USERS')" :to="{ name: 'admin-user', params: { id: a.targetId } }">{{ a.targetLabel }}</router-link>
            <template v-else>{{ a.targetLabel }}</template>
          </td>
          <td class="detail">{{ a.detail }}</td>
        </tr>
      </tbody>
    </table>
    <PaginationBar :page="page.page" :total-pages="page.totalPages" @change="load" />
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { adminAudit } from '../../api/admin'
import { useAdminMe } from '../../composables/useAdminMe'
import { formatDateTime } from '../../utils/format'
import { AUDIT_ACTION } from '../../utils/adminLabels'

const TYPES = [{ key: '', label: 'Tất cả' }, { key: 'USER', label: 'Tài khoản' }, { key: 'SHOP', label: 'Shop' }, { key: 'PRODUCT', label: 'Sản phẩm' }, { key: 'CATEGORY', label: 'Danh mục' }, { key: 'SETTING', label: 'Cài đặt' }]
const { can } = useAdminMe()
const page = ref({ items: [], page: 0, totalPages: 0 })
const type = ref('')
const tone = (a) => (/LOCK_USER|DELETE|REJECT|SUSPEND|REVOKE|REMOVE/.test(a) && !a.startsWith('UNLOCK') ? 'bad' : /APPROVE|UNLOCK|REINSTATE|CREATE|RESTORE/.test(a) ? 'good' : '')

async function load(p = 0) {
  page.value = (await adminAudit({ targetType: type.value, page: p })).data
}
onMounted(() => load(0))
</script>

<style scoped>
.chips { display: flex; gap: 6px; }
.chips button { background: white; border: 1px solid #ddd; border-radius: 999px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.chips button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.sc-table td { font-size: 13px; padding: 10px 12px; }
.nowrap { white-space: nowrap; }
.faint { color: var(--color-text-faint); }
.action { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #f0f5ff; color: #2673dd; white-space: nowrap; }
.action.bad { background: #fdecea; color: #d33; }
.action.good { background: #eaf7ef; color: #1e9e4a; }
.detail { color: var(--color-text-muted); max-width: 420px; }
a { color: #2673dd; }
</style>
