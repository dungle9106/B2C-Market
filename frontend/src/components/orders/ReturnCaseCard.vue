<template>
  <article class="case">
    <header class="case-head">
      <span>Đơn <strong>{{ rr.orderCode }}</strong> · {{ rr.shopName }} · Người mua: {{ rr.buyerName }}</span>
      <span class="state" :class="rr.status">{{ RETURN_STATUS[rr.status] }}</span>
    </header>
    <div class="case-body">
      <div class="lines">
        <div v-for="l in rr.items" :key="l.orderItemId" class="line">
          <img :src="l.thumbnailUrl || placeholder" alt="" />
          <div>
            <div>{{ l.productName }}</div>
            <div class="text-faint small">{{ l.variantName ? l.variantName + ' · ' : '' }}{{ formatVnd(l.price) }} x{{ l.quantity }}</div>
          </div>
        </div>
      </div>
      <dl class="kv">
        <dt>Yêu cầu</dt><dd>{{ RETURN_TYPES[rr.type] }}</dd>
        <dt>Lý do</dt><dd>{{ RETURN_REASONS[rr.reason] }}</dd>
        <template v-if="rr.description"><dt>Mô tả</dt><dd>{{ rr.description }}</dd></template>
        <dt>Hoàn tiền</dt><dd class="text-primary"><strong>{{ formatVnd(rr.refundAmount) }}</strong></dd>
        <dt>Gửi lúc</dt><dd>{{ formatDateTime(rr.createdAt) }}</dd>
        <template v-if="rr.sellerNote"><dt>Shop phản hồi</dt><dd>{{ rr.sellerNote }}</dd></template>
        <template v-if="rr.complaint"><dt>Khiếu nại</dt><dd>{{ rr.complaint }}</dd></template>
        <template v-if="rr.decision"><dt>Quyết định</dt><dd><strong>{{ DISPUTE_DECISION[rr.decision] }}</strong><template v-if="rr.decision !== 'REJECT'"> · {{ formatVnd(rr.refundAmount) }}</template></dd></template>
        <template v-if="rr.adminNote"><dt>Kết luận Shoppi</dt><dd>{{ rr.adminNote }}</dd></template>
      </dl>
    </div>
    <div class="evidence">
      <template v-for="url in rr.evidenceUrls" :key="url">
        <video v-if="/\.(mp4|webm)$/i.test(url)" :src="url" controls preload="metadata"></video>
        <a v-else :href="url" target="_blank" rel="noopener"><img :src="url" alt="Bằng chứng" /></a>
      </template>
    </div>
    <div v-if="$slots.extra" class="case-extra"><slot name="extra" /></div>
    <footer v-if="$slots.default" class="case-actions"><slot /></footer>
  </article>
</template>

<script setup>
import { formatVnd, formatDateTime } from '../../utils/format'
import { RETURN_STATUS, RETURN_TYPES, RETURN_REASONS, DISPUTE_DECISION } from '../../utils/returns'

defineProps({ rr: { type: Object, required: true } })
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48"><rect width="48" height="48" fill="%23f0f0f0"/></svg>'
</script>

<style scoped>
.case { border: 1px solid var(--color-border); border-radius: 4px; margin-bottom: 14px; overflow: hidden; }
.case-head { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 10px 16px; background: #fafafa; font-size: 14px; }
.state { font-size: 12px; padding: 2px 8px; border-radius: 2px; background: #f5f5f5; }
.state.REQUESTED { background: #fff4e5; color: #d97706; }
.state.REJECTED { background: #fdecea; color: #d33; }
.state.ESCALATED { background: #e8f1fd; color: #2673dd; }
.state.REFUNDED { background: #eaf7ee; color: #1e9e4a; }
.case-body { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr); gap: 20px; padding: 14px 16px; }
.line { display: flex; gap: 10px; align-items: center; font-size: 14px; margin-bottom: 8px; }
.line img { width: 48px; height: 48px; object-fit: cover; border: 1px solid var(--color-border); }
.small { font-size: 12px; }
.kv { display: grid; grid-template-columns: 110px 1fr; gap: 6px 12px; margin: 0; font-size: 14px; }
.kv dt { color: var(--color-text-muted); }
.kv dd { margin: 0; }
.evidence { display: flex; gap: 8px; flex-wrap: wrap; padding: 0 16px 14px; }
.evidence img { width: 72px; height: 72px; object-fit: cover; border: 1px solid var(--color-border); }
.evidence video { width: 140px; height: 80px; background: #000; }
.case-extra { padding: 0 16px 14px; }
.case-actions { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 16px; border-top: 1px solid var(--color-border); background: #fffefb; }
@media (max-width: 760px) { .case-body { grid-template-columns: 1fr; } }
</style>
