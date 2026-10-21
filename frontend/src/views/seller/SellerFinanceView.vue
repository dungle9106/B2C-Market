<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Doanh Thu & Đối Soát</h2>
        <span class="sc-card-sub">Shoppi thu tiền của người mua và chuyển cho shop theo từng kỳ đối soát</span>
      </div>

      <div v-if="!f" class="sc-empty">{{ error || 'Đang tải...' }}</div>
      <template v-else>
        <div class="kpis">
          <div class="kpi accent">
            <span>Doanh thu chưa đối soát (ước tính)</span>
            <strong>{{ formatVnd(f.unsettled.netPayout) }}</strong>
            <small>{{ f.unsettled.orders }} đơn đã giao · {{ f.readyOrders }} đơn đủ điều kiện kỳ tới</small>
          </div>
          <div class="kpi"><span>Chờ Shoppi chuyển khoản</span><strong>{{ formatVnd(f.pendingPayment) }}</strong><small>đã chốt đối soát</small></div>
          <div class="kpi"><span>Đã nhận</span><strong>{{ formatVnd(f.paidTotal) }}</strong><small>tổng các kỳ đã thanh toán</small></div>
        </div>

        <div class="how">
          <strong>Cách tính số tiền shop nhận</strong>
          <dl>
            <dt>Tiền hàng</dt><dd>{{ formatVnd(f.unsettled.merchandise) }}</dd>
            <dt>− Khuyến mãi do shop tài trợ (combo, voucher của shop)</dt><dd>-{{ formatVnd(f.unsettled.shopDiscounts) }}</dd>
            <dt>− Phí cố định {{ f.commissionRate }}% trên giá trị sau khuyến mãi</dt><dd>-{{ formatVnd(f.unsettled.commission) }}</dd>
            <dt>− Hoàn tiền cho người mua (trả hàng, khiếu nại)</dt><dd>-{{ formatVnd(f.unsettled.refunds) }}</dd>
            <dt class="b">= Shop nhận</dt><dd class="b">{{ formatVnd(f.unsettled.netPayout) }}</dd>
          </dl>
          <p class="faint">Shoppi Voucher do sàn tài trợ và phí vận chuyển không ảnh hưởng đến số tiền shop nhận.
            Đơn được đối soát sau {{ f.holdDays }} ngày kể từ khi giao thành công (thời gian người mua có thể yêu cầu trả hàng) và không còn yêu cầu trả hàng đang xử lý.</p>
        </div>

        <h3 class="sec">Lịch sử đối soát</h3>
        <div v-if="!f.settlements.length" class="sc-empty">Chưa có kỳ đối soát nào</div>
        <div v-else class="sc-table-wrap">
          <table class="sc-table">
            <thead><tr><th>Mã đối soát</th><th>Đơn giao đến hết</th><th class="r">Số đơn</th><th class="r">Tiền hàng</th><th class="r">Phí & khấu trừ</th><th class="r">Shop nhận</th><th>Trạng thái</th></tr></thead>
            <tbody>
              <tr v-for="s in f.settlements" :key="s.id">
                <td><a href="#" class="code" @click.prevent="open(s.id)">{{ s.code }}</a></td>
                <td>{{ fmtDay(s.periodTo) }}</td>
                <td class="r">{{ s.orderCount }}</td>
                <td class="r">{{ formatVnd(s.merchandise) }}</td>
                <td class="r faint">-{{ formatVnd(Number(s.shopDiscounts) + Number(s.commission) + Number(s.refunds)) }}</td>
                <td class="r"><strong>{{ formatVnd(s.netPayout) }}</strong></td>
                <td>
                  <span class="st" :class="s.status">{{ s.status === 'PAID' ? 'Đã thanh toán' : 'Chờ chuyển khoản' }}</span>
                  <div v-if="s.paidAt" class="faint">{{ formatDateTime(s.paidAt) }} · {{ s.paymentReference }}</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
    <SettlementDetailModal v-if="detail" :s="detail" @close="detail = null" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SettlementDetailModal from '../../components/SettlementDetailModal.vue'
import { sellerFinance, sellerSettlement } from '../../api/orders'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const f = ref(null)
const detail = ref(null)
const error = ref('')
const fmtDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '')

async function open(id) {
  try {
    detail.value = (await sellerSettlement(id)).data
  } catch (e) {
    error.value = extractError(e)
  }
}

onMounted(async () => {
  try {
    f.value = (await sellerFinance()).data
  } catch (e) {
    error.value = extractError(e)
  }
})
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: 1.3fr 1fr 1fr; gap: 12px; margin-bottom: 16px; }
.kpi { border: 1px solid var(--color-border); border-radius: 6px; padding: 14px; display: flex; flex-direction: column; gap: 4px; }
.kpi span, .kpi small { font-size: 12px; color: var(--color-text-muted); }
.kpi strong { font-size: 22px; }
.kpi.accent { background: var(--color-primary-light); border-color: #ffd9cc; }
.kpi.accent strong { color: var(--color-primary); }
.how { border: 1px solid var(--color-border); border-radius: 6px; padding: 14px; font-size: 14px; margin-bottom: 18px; }
.how dl { display: grid; grid-template-columns: 1fr 160px; gap: 4px 12px; margin: 10px 0; max-width: 620px; }
.how dt { color: var(--color-text-muted); }
.how dd { margin: 0; text-align: right; }
.how .b { font-weight: 600; color: var(--color-text); }
.sec { font-size: 16px; margin: 0 0 10px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.how p.faint { margin: 0; line-height: 1.5; }
.code { font-family: ui-monospace, monospace; font-size: 13px; color: #2673dd; }
.r { text-align: right; white-space: nowrap; }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #eaf7ef; color: #1e9e4a; white-space: nowrap; }
.st.PENDING_PAYMENT { background: #fff4e5; color: #b35c00; }
@media (max-width: 760px) { .kpis { grid-template-columns: 1fr; } }
</style>
