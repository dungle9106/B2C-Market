<template>
  <!-- one payout statement, shared by the admin and the seller centre -->
  <BaseModal :title="`Bảng đối soát ${s.code}`" :width="760" @close="$emit('close')">
    <div class="head">
      <div><span>Shop</span>{{ s.shopName }}</div>
      <div><span>Đơn giao đến hết</span>{{ fmtDay(s.periodTo) }}</div>
      <div><span>Trạng thái</span><b :class="s.status">{{ s.status === 'PAID' ? 'Đã thanh toán' : 'Chờ thanh toán' }}</b></div>
      <div v-if="s.paidAt"><span>Chuyển khoản</span>{{ formatDateTime(s.paidAt) }} · {{ s.paymentReference }}</div>
    </div>
    <dl class="sum">
      <dt>Tiền hàng ({{ s.orderCount }} đơn)</dt><dd>{{ formatVnd(s.merchandise) }}</dd>
      <dt>Khuyến mãi do shop tài trợ (combo, voucher shop)</dt><dd>-{{ formatVnd(s.shopDiscounts) }}</dd>
      <dt>Phí cố định {{ s.commissionRate }}%</dt><dd>-{{ formatVnd(s.commission) }}</dd>
      <dt>Hoàn tiền cho người mua</dt><dd>-{{ formatVnd(s.refunds) }}</dd>
      <dt class="b">Shop nhận</dt><dd class="b total">{{ formatVnd(s.netPayout) }}</dd>
    </dl>
    <table class="sc-table">
      <thead><tr><th>Đơn hàng</th><th>Giao lúc</th><th class="r">Tiền hàng</th><th class="r">KM shop</th><th class="r">Phí</th><th class="r">Shop nhận</th></tr></thead>
      <tbody>
        <tr v-for="o in s.orders" :key="o.id">
          <td class="code">{{ o.code }}</td>
          <td class="faint">{{ formatDateTime(o.deliveredAt) }}</td>
          <td class="r">{{ formatVnd(o.merchandise) }}</td>
          <td class="r">-{{ formatVnd(o.shopDiscounts) }}</td>
          <td class="r">-{{ formatVnd(o.commission) }}</td>
          <td class="r">{{ formatVnd(o.net) }}</td>
        </tr>
        <tr v-for="r in s.refundLines" :key="'r' + r.id" class="refund">
          <td class="code">{{ r.orderCode }}</td>
          <td class="faint" colspan="4">Hoàn tiền: {{ REFUND_SOURCE[r.source] }}<template v-if="r.note"> – {{ r.note }}</template></td>
          <td class="r">-{{ formatVnd(r.amount) }}</td>
        </tr>
      </tbody>
    </table>
  </BaseModal>
</template>

<script setup>
import BaseModal from './BaseModal.vue'
import { formatVnd, formatDateTime } from '../utils/format'
import { REFUND_SOURCE } from '../utils/returns'

defineProps({ s: { type: Object, required: true } })
defineEmits(['close'])
const fmtDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '')
</script>

<style scoped>
.head { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px 16px; font-size: 13px; margin-bottom: 12px; }
.head span { display: block; font-size: 12px; color: var(--color-text-faint); }
.head b.PAID { color: #1e9e4a; }
.head b.PENDING_PAYMENT { color: #b35c00; }
.sum { display: grid; grid-template-columns: 1fr 160px; gap: 4px 12px; margin: 0 0 14px auto; max-width: 480px; font-size: 14px; text-align: right; }
.sum dt { color: var(--color-text-muted); }
.sum dd { margin: 0; }
.sum .b { font-weight: 600; color: var(--color-text); }
.sum .total { color: var(--color-primary); font-size: 17px; }
.code { font-family: ui-monospace, monospace; font-size: 13px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.r { text-align: right; white-space: nowrap; }
.refund td { background: #fff8f6; }
@media (max-width: 700px) { .head { grid-template-columns: 1fr 1fr; } }
</style>
