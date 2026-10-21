<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">✨ Gợi Ý Thời Điểm Chạy Khuyến Mãi</h2>
        <span class="sc-card-sub">Phân tích {{ data ? data.ordersAnalyzed : '…' }} đơn hàng trong 90 ngày qua, lịch sự kiện mua sắm và tồn kho của shop</span>
      </div>
      <div v-if="loading" class="sc-empty">AI đang phân tích xu hướng bán hàng...</div>
      <div v-else-if="error" class="sc-empty">{{ error }}</div>
      <template v-else-if="data">
        <div v-if="data.scope === 'platform'" class="notice">Shop chưa đủ dữ liệu bán hàng nên phần thứ/giờ đang dùng xu hướng chung của toàn sàn Shoppi.</div>
        <div class="summary">
          <div class="sum-icon">🤖</div>
          <div>
            <p>{{ data.summary }}</p>
            <span class="src">{{ data.source === 'ai' ? 'Viết bởi mô hình AI' : 'Phân tích tự động' }}</span>
          </div>
        </div>
        <div class="kpis">
          <div><span>Ngày nên chạy</span><b>{{ data.bestWeekdays.join(', ') }}</b></div>
          <div><span>Khung Flash Sale tốt nhất</span><b>{{ bestSlot?.label }}</b></div>
          <div><span>Giờ cao điểm đặt hàng</span><b>{{ data.bestWindow }}</b></div>
          <div><span>Doanh thu 4 tuần gần đây</span>
            <b :class="data.trend.change == null ? '' : data.trend.change >= 0 ? 'good' : 'bad'">
              {{ data.trend.change == null ? 'Chưa đủ dữ liệu' : (data.trend.change >= 0 ? '▲ ' : '▼ ') + Math.abs(data.trend.change) + '%' }}
            </b>
          </div>
        </div>
        <div class="actions">
          <router-link :to="{ name: 'seller-flash-sale' }" class="btn sm">⚡ Tạo Flash Sale</router-link>
          <router-link :to="{ name: 'seller-vouchers' }" class="btn sm secondary">🎟 Tạo voucher</router-link>
          <router-link :to="{ name: 'seller-combos' }" class="btn sm secondary">🧺 Tạo combo</router-link>
        </div>
      </template>
    </section>

    <template v-if="data">
      <div class="two-col">
        <section class="sc-card">
          <h3 class="block-h">Theo thứ trong tuần</h3>
          <TrendChart :labels="['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']" :height="200"
                      :tooltip-labels="data.weekdays.map(w => w.name)"
                      :series="[{ name: 'Doanh số TB mỗi ngày', values: data.weekdays.map(w => w.avgSales), color: '#ee4d2d', type: 'bar', format: formatVnd },
                                { name: 'Lượt xem', values: data.weekdays.map(w => w.views), color: '#2673dd', type: 'line', axis: 'right', integer: true }]" />
        </section>
        <section class="sc-card">
          <h3 class="block-h">Theo giờ trong ngày</h3>
          <TrendChart :labels="hours" :height="200"
                      :series="[{ name: 'Đơn hàng', values: data.ordersByHour, color: '#ee4d2d', type: 'bar', integer: true },
                                { name: 'Lượt xem', values: data.viewsByHour, color: '#2673dd', type: 'line', axis: 'right', integer: true }]" />
          <div class="slots">
            <div v-for="s in data.slots" :key="s.label" class="slot" :class="{ best: s.best }">
              <b>{{ s.label }}</b>
              <span>{{ s.orders }} đơn · {{ s.views }} lượt xem</span>
              <span v-if="s.best" class="best-tag">Nên chọn</span>
            </div>
          </div>
        </section>
      </div>

      <div class="two-col">
        <section class="sc-card">
          <h3 class="block-h">Sự kiện mua sắm sắp tới (60 ngày)</h3>
          <div v-if="!data.events.length" class="faint">Không có sự kiện nào trong 60 ngày tới</div>
          <div v-for="e in data.events" :key="e.date" class="event" :class="e.importance.toLowerCase()">
            <div class="date"><b>{{ dm(e.date) }}</b><span>{{ e.daysLeft === 0 ? 'Hôm nay' : 'còn ' + e.daysLeft + ' ngày' }}</span></div>
            <div class="ev-body">
              <div class="ev-name">{{ e.name }} <span class="imp">{{ IMPORTANCE[e.importance] }}</span></div>
              <div class="faint">Chuẩn bị từ {{ dm(e.prepFrom) }}: {{ e.tips }}</div>
            </div>
          </div>
        </section>
        <section class="sc-card">
          <h3 class="block-h">Theo thời điểm trong tháng</h3>
          <div v-for="p in data.monthParts" :key="p.label" class="part">
            <span class="part-name">{{ p.label }}</span>
            <span class="part-bar"><i :style="{ width: (p.index / maxIndex) * 100 + '%' }" :class="{ hi: p.index >= 1.15 }"></i></span>
            <span class="part-num">{{ p.avgOrdersPerDay.toLocaleString('vi-VN') }} đơn/ngày</span>
          </div>
          <p class="faint note">Chỉ số &gt; 1 nghĩa là ngày đó bán tốt hơn trung bình. Nhiều người mua sắm sau khi nhận lương (cuối tháng, đầu tháng).</p>
        </section>
      </div>

      <section class="sc-card">
        <h3 class="block-h">Gợi ý theo sản phẩm</h3>
        <div class="groups">
          <div v-for="g in GROUPS" :key="g.key" class="group">
            <h4 :class="g.cls">{{ g.title }}</h4>
            <div v-if="!data.products[g.key].length" class="faint">Không có</div>
            <div v-for="p in data.products[g.key]" :key="p.id" class="prod">
              <img :src="p.thumbnailUrl || placeholder" alt="" />
              <div class="prod-info">
                <router-link :to="{ name: 'seller-product-edit', params: { id: p.id } }" class="prod-name">{{ p.name }}</router-link>
                <div class="faint">{{ p.reason }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import TrendChart from '../../../components/analytics/TrendChart.vue'
import { promoTiming } from '../../../api/ai'
import { formatVnd } from '../../../utils/format'
import { extractError } from '../../../utils/apiError'

const IMPORTANCE = { HIGH: 'Rất quan trọng', MEDIUM: 'Quan trọng', LOW: 'Hằng tháng' }
const GROUPS = [
  { key: 'clearance', title: '🏷 Nên giảm giá để xả hàng', cls: 'warn' },
  { key: 'interest', title: '🎟 Nhiều người xem, ít người mua – nên tặng voucher', cls: 'info' },
  { key: 'rising', title: '📈 Đang bán chạy lên – ưu tiên quảng cáo', cls: 'good' },
  { key: 'lowStock', title: '⛔ Sắp hết hàng – không nên khuyến mãi', cls: 'bad' }
]
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="36" height="36"><rect width="36" height="36" fill="%23f0f0f0"/></svg>'
const hours = Array.from({ length: 24 }, (_, h) => h + 'h')

const data = ref(null)
const loading = ref(true)
const error = ref('')
const bestSlot = computed(() => data.value?.slots.find(s => s.best))
const maxIndex = computed(() => Math.max(1, ...(data.value?.monthParts || []).map(p => p.index)))
const dm = (d) => d.split('-').reverse().slice(0, 2).join('/')

onMounted(async () => {
  try {
    data.value = (await promoTiming()).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.notice { background: #fff8e6; color: #8a5a00; font-size: 13px; padding: 8px 12px; border-radius: 4px; margin-bottom: 10px; }
.summary { display: flex; gap: 12px; background: linear-gradient(135deg, #faf8ff, #fff); border: 1px solid #e0d8ff; border-radius: 8px; padding: 14px 16px; }
.sum-icon { font-size: 28px; }
.summary p { margin: 0 0 6px; line-height: 1.65; font-size: 14px; }
.src { font-size: 11px; color: #6941c6; }
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-top: 14px; }
.kpis > div { border: 1px solid var(--color-border); border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.kpis span { font-size: 12px; color: var(--color-text-muted); }
.kpis b { font-size: 16px; font-weight: 600; }
.good { color: #1e9e4a; }
.bad { color: #d33; }
.warn { color: #e67e22; }
.info { color: #2673dd; }
.actions { display: flex; gap: 8px; margin-top: 14px; flex-wrap: wrap; }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: stretch; margin-bottom: 16px; }
.two-col .sc-card { margin-bottom: 0; }
.block-h { font-size: 16px; font-weight: 600; margin: 0 0 12px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.slots { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 10px; }
.slot { border: 1px solid var(--color-border); border-radius: 6px; padding: 8px; font-size: 12px; display: flex; flex-direction: column; gap: 2px; position: relative; }
.slot.best { border-color: #1e9e4a; background: #eaf7ef; }
.best-tag { color: #1e9e4a; font-weight: 600; }
.event { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px dashed var(--color-border); }
.event:last-child { border-bottom: none; }
.date { width: 70px; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; border-radius: 6px; padding: 6px 0; background: #f5f5f5; font-size: 11px; color: var(--color-text-muted); }
.date b { font-size: 16px; color: var(--color-text); }
.event.high .date { background: #fdecea; }
.event.high .date b { color: var(--color-primary); }
.event.medium .date { background: #fff4e5; }
.ev-body { flex: 1; min-width: 0; }
.ev-name { font-weight: 500; margin-bottom: 2px; }
.imp { font-size: 11px; font-weight: 400; color: var(--color-text-faint); border: 1px solid #ddd; border-radius: 3px; padding: 0 5px; margin-left: 6px; }
.event.high .imp { color: var(--color-primary); border-color: #f7b6a8; }
.part { display: grid; grid-template-columns: 190px 1fr 110px; gap: 10px; align-items: center; padding: 8px 0; font-size: 13px; }
.part-bar { height: 12px; background: #f2f2f2; border-radius: 999px; overflow: hidden; }
.part-bar i { display: block; height: 100%; background: #b9a6ef; border-radius: 999px; }
.part-bar i.hi { background: #6941c6; }
.part-num { text-align: right; color: var(--color-text-muted); font-size: 12px; }
.note { margin-top: 10px; }
.groups { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px 24px; }
.group h4 { margin: 0 0 8px; font-size: 14px; }
.prod { display: flex; gap: 10px; align-items: center; padding: 6px 0; }
.prod img { width: 36px; height: 36px; object-fit: cover; border-radius: 2px; flex-shrink: 0; }
.prod-info { min-width: 0; }
.prod-name { font-size: 13px; color: #2673dd; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 1100px) { .two-col, .groups { grid-template-columns: 1fr; } .kpis { grid-template-columns: 1fr 1fr; } .slots { grid-template-columns: 1fr 1fr; } }
</style>
