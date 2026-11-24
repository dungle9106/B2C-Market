<template>
  <!-- one shop's "Hieu qua hoat dong": shared by the seller page and the admin detail -->
  <div class="tw-flex tw-flex-col tw-gap-4">
    <div class="tw-grid tw-grid-cols-2 tw-gap-3 md:tw-grid-cols-4">
      <div class="box">
        <div class="k">Tỷ lệ đơn không thành công</div>
        <div class="v" :class="bad(m.nfrPercent, rules.nfr) ? 'tw-text-red-600' : ''">{{ pct(m.nfrPercent) }}</div>
        <div class="s">{{ m.sellerCancelled }}/{{ m.orders }} đơn do shop hủy · mục tiêu &lt; {{ rules.nfr }}%</div>
      </div>
      <div class="box">
        <div class="k">Tỷ lệ giao hàng trễ</div>
        <div class="v" :class="bad(m.latePercent, rules.late) ? 'tw-text-red-600' : ''">{{ pct(m.latePercent) }}</div>
        <div class="s">{{ m.lateShipped }}/{{ m.shipped }} đơn giao cho ĐVVC trễ · mục tiêu &lt; {{ rules.late }}%</div>
      </div>
      <div class="box">
        <div class="k">Tỷ lệ phản hồi chat</div>
        <div class="v">{{ pct(m.chatResponsePercent) }}</div>
        <div class="s">Trong 30 ngày</div>
      </div>
      <div class="box">
        <div class="k">Đánh giá shop</div>
        <div class="v">{{ m.rating != null ? m.rating + '★' : '—' }}</div>
        <div class="s">{{ m.ratingCount }} lượt · {{ m.delivered }} đơn giao thành công / 30 ngày</div>
      </div>
    </div>

    <div class="tw-grid tw-gap-4 lg:tw-grid-cols-2">
      <section class="panel">
        <h3>Điểm phạt (Sao Quả Tạ) quý này</h3>
        <div class="tw-flex tw-items-baseline tw-gap-3">
          <span class="points" :class="LEVEL_CLASS[r.level.key]">{{ r.points }}</span>
          <span :class="LEVEL_CLASS[r.level.key]" class="tw-text-sm tw-font-semibold">{{ r.level.text }}</span>
        </div>
        <p class="tw-my-1 tw-text-xs tw-text-neutral-500">Điểm được tính lại từ đầu mỗi quý (quý này đến {{ day(r.quarterEnds) }}).
          3 điểm: không được nhãn Yêu thích · 6 điểm: không được đăng ký Flash Sale Shoppi · 12 điểm: bị xem xét đình chỉ.</p>
        <div class="bar"><span v-for="n in [3, 6, 12]" :key="n" class="mark" :style="{ left: n / 15 * 100 + '%' }">{{ n }}</span>
          <span class="fill" :class="r.level.key" :style="{ width: Math.min(100, r.points / 15 * 100) + '%' }"></span></div>
        <ul class="hist">
          <li v-if="!r.penalties.length" class="tw-text-sm tw-text-neutral-400">Chưa có điểm phạt nào 👍</li>
          <li v-for="p in r.penalties" :key="p.id" :class="{ off: !p.counted }">
            <span class="pts">+{{ p.points }}</span>
            <div class="tw-min-w-0 tw-flex-1">
              <div class="tw-text-sm"><b>{{ PENALTY_CODES[p.code] }}</b> · {{ p.reason }}</div>
              <div class="tw-text-xs tw-text-neutral-400">{{ formatDateTime(p.createdAt) }}
                <template v-if="p.revoked"> · Đã hủy: {{ p.revokeReason }}</template>
                <template v-else-if="!p.counted"> · Quý trước</template>
              </div>
            </div>
            <slot name="penalty-action" :penalty="p"></slot>
          </li>
        </ul>
      </section>

      <section class="panel">
        <h3>Shop Yêu Thích <span v-if="r.favorite" class="fav">Đang có nhãn</span></h3>
        <p class="tw-my-1 tw-text-xs tw-text-neutral-500">Nhãn được xét tự động mỗi ngày khi shop đạt đủ các tiêu chí dưới đây.
          <template v-if="r.favoriteSince"> Đạt từ {{ day(r.favoriteSince) }}.</template></p>
        <ul class="crit">
          <li v-for="c in r.favoriteCriteria" :key="c.key">
            <span :class="c.ok ? 'tw-text-green-600' : 'tw-text-red-500'">{{ c.ok ? '✓' : '✗' }}</span>
            <span class="tw-flex-1">{{ c.label }}</span>
            <span class="tw-text-neutral-500">{{ c.value }}</span>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { LEVEL_CLASS, PENALTY_CODES } from '../api/performance'
import { formatDateTime } from '../utils/format'

const props = defineProps({ report: { type: Object, required: true } })
const r = computed(() => props.report)
const m = computed(() => props.report.metrics)
// targets shown next to the rates: the "Yeu thich" limits from the checklist
const rules = computed(() => {
  const find = (k) => props.report.favoriteCriteria.find(c => c.key === k)?.label.match(/([\d.]+)%/)?.[1] || '—'
  return { nfr: find('NFR'), late: find('LATE') }
})
const pct = (v) => (v == null ? '—' : v.toLocaleString('vi-VN') + '%')
const bad = (v, limit) => v != null && limit !== '—' && v > Number(limit)
const day = (t) => (t ? new Date(t).toLocaleDateString('vi-VN') : '')
</script>

<style scoped>
.box { border: 1px solid var(--color-border); border-radius: 6px; padding: 12px 14px; background: white; }
.k { font-size: 13px; color: var(--color-text-muted); }
.v { font-size: 24px; font-weight: 600; margin: 4px 0; }
.s { font-size: 12px; color: var(--color-text-faint); }
.panel { border: 1px solid var(--color-border); border-radius: 6px; padding: 14px 16px; background: white; }
.panel h3 { margin: 0 0 8px; font-size: 16px; display: flex; align-items: center; gap: 8px; }
.points { font-size: 40px; font-weight: 700; line-height: 1; }
.bar { position: relative; height: 8px; background: #f0f0f0; border-radius: 4px; margin: 22px 0 12px; }
.fill { position: absolute; left: 0; top: 0; bottom: 0; border-radius: 4px; background: #16a34a; }
.fill.WARNING { background: #d97706; }
.fill.RESTRICTED { background: #ea580c; }
.fill.REVIEW { background: #dc2626; }
.mark { position: absolute; top: -18px; transform: translateX(-50%); font-size: 11px; color: var(--color-text-faint); }
.mark::after { content: ''; position: absolute; left: 50%; top: 16px; width: 1px; height: 12px; background: #bbb; }
.hist, .crit { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.hist li { display: flex; gap: 10px; align-items: flex-start; }
.hist li.off { opacity: .55; }
.pts { color: #dc2626; font-weight: 700; min-width: 28px; }
.crit li { display: flex; gap: 8px; font-size: 14px; border-bottom: 1px dashed var(--color-border); padding-bottom: 6px; }
.fav { background: var(--color-primary); color: white; font-size: 11px; padding: 1px 6px; border-radius: 2px; }
</style>
