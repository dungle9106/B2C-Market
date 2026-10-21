<template>
  <div class="dashboard">
    <div class="main-col">
      <!-- ===== onboarding: hidden once everything is done ===== -->
      <section v-if="setup.length && setupDone < setup.length" class="sc-card setup">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Hoàn thiện Shop của bạn</h2>
          <span class="sc-card-sub">{{ setupDone }}/{{ setup.length }} bước · Shop đầy đủ thông tin tạo được nhiều niềm tin hơn với người mua</span>
        </div>
        <div class="progress"><span :style="{ width: (setupDone / setup.length) * 100 + '%' }"></span></div>
        <ul class="setup-list">
          <li v-for="s in setup" :key="s.label" :class="{ done: s.done }">
            <span class="tick">{{ s.done ? '✓' : '' }}</span>
            <span class="setup-label">{{ s.label }}<span v-if="s.optional" class="optional">Tùy chọn</span></span>
            <router-link v-if="!s.done" :to="s.to" class="btn sm secondary">{{ s.action }}</router-link>
          </li>
        </ul>
      </section>

      <!-- ===== to-do counters ===== -->
      <section class="sc-card todo">
        <h2 class="sc-card-title">Danh sách cần làm</h2>
        <p class="sc-card-sub">Những việc bạn sẽ phải làm</p>
        <div class="todo-grid">
          <router-link v-for="t in todos" :key="t.label" :to="t.to" class="todo-item">
            <span class="todo-num">{{ stats ? t.value : '–' }}</span>
            <span class="todo-label">{{ t.label }}</span>
          </router-link>
        </div>
      </section>

      <!-- ===== sales analytics: today vs yesterday ===== -->
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Phân Tích Bán Hàng</h2>
          <span class="sc-card-sub">Hôm nay 00:00 GMT+7 {{ nowTime }} (Dữ liệu thay đổi so với hôm qua)</span>
          <router-link :to="{ name: 'seller-analytics-revenue' }" class="sc-link">Xem báo cáo chi tiết ›</router-link>
        </div>
        <div class="metrics">
          <div v-for="m in metrics" :key="m.label" class="metric">
            <div class="metric-label">
              {{ m.label }}
              <span class="hint" :title="m.hint">?</span>
            </div>
            <div class="metric-value">{{ stats ? m.value : '–' }}</div>
            <div v-if="m.change" class="metric-change" :class="m.change.dir">{{ m.change.text }}</div>
            <div v-else class="metric-change flat">Tổng từ trước đến nay</div>
          </div>
        </div>
      </section>

      <!-- ===== best sellers ===== -->
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Sản Phẩm Bán Chạy</h2>
          <router-link :to="{ name: 'seller-products' }" class="sc-link">Xem thêm ›</router-link>
        </div>
        <div v-if="stats && stats.topProducts.length === 0" class="sc-empty">
          Shop chưa có sản phẩm nào.
          <router-link :to="{ name: 'seller-product-new' }" class="text-primary">Thêm sản phẩm đầu tiên</router-link>
        </div>
        <ol v-else-if="stats" class="top-list">
          <li v-for="(p, i) in stats.topProducts" :key="p.id">
            <span class="rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
            <img :src="p.thumbnailUrl || placeholder" alt="" />
            <router-link :to="`/products/${p.id}`" class="top-name">{{ p.name }}</router-link>
            <span class="top-price">{{ formatVnd(p.price) }}</span>
            <span class="top-sold">Đã bán {{ p.soldCount || 0 }}</span>
          </li>
        </ol>
      </section>
    </div>

    <!-- ===== right column ===== -->
    <aside class="side-col">
      <section class="sc-card shop-card">
        <div class="shop-avatar">
          <img v-if="shop?.logoUrl" :src="shop.logoUrl" alt="" />
          <template v-else>{{ (shop?.name || 'S').charAt(0).toUpperCase() }}</template>
        </div>
        <div class="shop-info">
          <div class="shop-name">{{ shop?.name || 'Shop của tôi' }} <span v-if="shop?.verified" class="verified" title="Đã xác minh">✓</span></div>
          <div class="text-faint">{{ stats ? stats.totalProducts : 0 }} sản phẩm · {{ stats ? stats.totalOrders : 0 }} đơn hàng</div>
        </div>
        <router-link :to="{ name: 'seller-shop' }" class="btn secondary sm">Hồ sơ Shop</router-link>
      </section>

      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Mẹo Bán Hàng</h2>
        </div>
        <router-link v-for="tip in tips" :key="tip.title" :to="tip.to" class="tip">
          <div class="tip-title">{{ tip.icon }} {{ tip.title }}</div>
          <p class="tip-body">{{ tip.body }}</p>
        </router-link>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { sellerDashboardStats, getMyShop, myVerification } from '../../api/shop'
import { formatVnd } from '../../utils/format'

const stats = ref(null)
const shop = ref(null)
const verification = ref(null)

const setup = computed(() => {
  const s = shop.value
  if (!s || !stats.value) return []
  return [
    { label: 'Tải lên logo và ảnh bìa Shop', done: !!(s.logoUrl && s.coverUrl), to: { name: 'seller-shop' }, action: 'Cập nhật' },
    { label: 'Viết giới thiệu Shop', done: !!s.description, to: { name: 'seller-shop' }, action: 'Viết ngay' },
    { label: 'Thêm số điện thoại lấy hàng', done: !!s.pickupPhone, to: { name: 'seller-shop' }, action: 'Thêm' },
    { label: 'Cài đặt điều kiện đổi trả', done: !!s.returnConditions, to: { name: 'seller-policies' }, action: 'Cài đặt' },
    { label: 'Đăng sản phẩm đầu tiên', done: stats.value.totalProducts > 0, to: { name: 'seller-product-new' }, action: 'Thêm sản phẩm' },
    { label: verification.value?.status === 'PENDING' ? 'Xác minh Shop (đang chờ duyệt)' : 'Xác minh thông tin Shop', done: s.verified || verification.value?.status === 'PENDING',
      to: { name: 'seller-verification' }, action: 'Xác minh', optional: true }
  ]
})
const setupDone = computed(() => setup.value.filter(s => s.done).length)
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48"><rect width="48" height="48" fill="%23f0f0f0"/></svg>'
const nowTime = new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })

const todos = computed(() => [
  { label: 'Chờ Xác Nhận', value: stats.value?.pendingOrders, to: { name: 'seller-orders', query: { status: 'PENDING' } } },
  { label: 'Chờ Lấy Hàng', value: stats.value?.toShipOrders, to: { name: 'seller-orders', query: { status: 'CONFIRMED' } } },
  { label: 'Đơn Hủy / Trả hàng / Hoàn tiền', value: stats.value?.cancelledOrReturnedOrders, to: { name: 'seller-orders', query: { status: 'RETURNS' } } },
  { label: 'Sản Phẩm Hết Hàng', value: stats.value?.outOfStockProducts, to: { name: 'seller-products', query: { tab: 'soldout' } } },
  { label: 'Câu Hỏi Chờ Trả Lời', value: stats.value?.unansweredQuestions, to: { name: 'seller-questions' } },
  { label: 'Yêu Cầu Trả Hàng', value: stats.value?.pendingReturns, to: { name: 'seller-returns' } }
])

// "-- 0,00%" when flat, arrow + % otherwise; "Mới" when yesterday was 0
function change(today, yesterday) {
  const t = Number(today || 0)
  const y = Number(yesterday || 0)
  if (t === y) return { dir: 'flat', text: '— 0,00%' }
  if (y === 0) return { dir: 'up', text: '▲ Mới so với hôm qua' }
  const pct = ((t - y) / y) * 100
  const text = Math.abs(pct).toLocaleString('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + '%'
  return pct > 0 ? { dir: 'up', text: '▲ ' + text } : { dir: 'down', text: '▼ ' + text }
}

// capped at 100%: a buyer may come straight from the cart without a tracked view today
const conversion = (buyers, visitors) => (visitors ? Math.min(100, (buyers / visitors) * 100) : 0)
const conversionText = (buyers, visitors) => conversion(buyers, visitors).toLocaleString('vi-VN', { maximumFractionDigits: 2 }) + '%'

const metrics = computed(() => {
  const s = stats.value || {}
  return [
    { label: 'Doanh số', hint: 'Tổng giá trị các đơn đặt hôm nay (không tính đơn đã hủy/hoàn tiền)', value: formatVnd(s.todaySales), change: change(s.todaySales, s.yesterdaySales) },
    { label: 'Đơn hàng', hint: 'Số đơn đặt hôm nay (không tính đơn đã hủy/hoàn tiền)', value: s.todayOrders, change: change(s.todayOrders, s.yesterdayOrders) },
    { label: 'Sản phẩm đã bán', hint: 'Tổng số lượng sản phẩm trong các đơn đặt hôm nay', value: s.todayItemsSold, change: change(s.todayItemsSold, s.yesterdayItemsSold) },
    { label: 'Lượt truy cập', hint: 'Số khách khác nhau đã xem trang sản phẩm của Shop hôm nay', value: s.todayVisitors, change: change(s.todayVisitors, s.yesterdayVisitors) },
    { label: 'Lượt xem trang', hint: 'Tổng số lượt xem trang sản phẩm hôm nay', value: s.todayViews, change: change(s.todayViews, s.yesterdayViews) },
    { label: 'Tỷ lệ chuyển đổi', hint: 'Người mua / lượt truy cập hôm nay', value: conversionText(s.todayBuyers, s.todayVisitors),
      change: change(conversion(s.todayBuyers, s.todayVisitors), conversion(s.yesterdayBuyers, s.yesterdayVisitors)) },
    { label: 'Doanh thu', hint: 'Tổng giá trị các đơn đã giao thành công', value: formatVnd(s.totalRevenue) },
    { label: 'Đang giao', hint: 'Số đơn đang trên đường giao tới người mua', value: s.shippingOrders }
  ]
})

const tips = [
  { icon: '✨', title: 'Viết mô tả sản phẩm bằng AI', body: 'Nhập chất liệu, công dụng và đối tượng sử dụng, Trợ lý AI sẽ gợi ý đoạn mô tả hấp dẫn cho bạn.', to: { name: 'seller-product-new', query: { ai: '1' } } },
  { icon: '⏱️', title: 'Xác nhận đơn trong 24 giờ', body: 'Xử lý đơn "Chờ xác nhận" sớm giúp người mua yên tâm và hạn chế đơn bị hủy.', to: { name: 'seller-orders', query: { status: 'PENDING' } } },
  { icon: '📦', title: 'Luôn cập nhật tồn kho', body: 'Sản phẩm hết hàng vẫn hiển thị sẽ khiến người mua không đặt được. Hãy bổ sung kho hoặc tạm ẩn.', to: { name: 'seller-products', query: { tab: 'soldout' } } }
]

onMounted(async () => {
  const [{ data: s }, shopRes] = await Promise.all([
    sellerDashboardStats(),
    getMyShop().catch(() => ({ data: null }))
  ])
  stats.value = s
  shop.value = shopRes.data
  myVerification().then(({ data }) => { verification.value = data || null }).catch(() => {})
})
</script>

<style scoped>
.dashboard { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
.setup .sc-card-head { margin-bottom: 10px; }
.progress { height: 6px; border-radius: 999px; background: #f0f0f0; overflow: hidden; margin-bottom: 8px; }
.progress span { display: block; height: 100%; background: var(--color-primary); transition: width 0.3s; }
.setup-list { list-style: none; margin: 0; padding: 0; }
.setup-list li { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.setup-list li:last-child { border-bottom: none; }
.tick { width: 22px; height: 22px; border-radius: 50%; border: 1.5px solid #ccc; display: flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; }
.done .tick { background: #1e9e4a; border-color: #1e9e4a; color: white; }
.setup-label { flex: 1; font-size: 14px; }
.done .setup-label { color: var(--color-text-faint); text-decoration: line-through; }
.optional { font-size: 11px; color: var(--color-text-faint); border: 1px solid #ddd; border-radius: 2px; padding: 0 5px; margin-left: 8px; }
.verified { background: #2673dd; color: white; border-radius: 50%; width: 16px; height: 16px; font-size: 10px; display: inline-flex; align-items: center; justify-content: center; vertical-align: middle; }

.todo .sc-card-sub { margin: 4px 0 16px; }
.todo-grid { display: grid; grid-template-columns: repeat(6, 1fr); }
.todo-item { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 12px 8px; border-radius: 4px; text-align: center; }
.todo-item:hover { background: #f8f8f8; }
.todo-num { font-size: 22px; font-weight: 500; color: #2673dd; }
.todo-label { font-size: 14px; }

.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; background: var(--color-border); border: 1px solid var(--color-border); border-radius: 4px; overflow: hidden; }
.metric { padding: 16px 14px; min-width: 0; background: var(--color-surface); }
.metric-label { font-size: 14px; display: flex; align-items: center; gap: 4px; }
.hint {
  width: 14px; height: 14px; border-radius: 50%; border: 1px solid #aaa; color: #999;
  font-size: 10px; display: inline-flex; align-items: center; justify-content: center; cursor: help;
}
.metric-value { font-size: 20px; margin: 8px 0 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.metric-change { font-size: 13px; color: var(--color-text-muted); }
.metric-change.up { color: #1e9e4a; }
.metric-change.down { color: #d33; }
.metric-change.flat { color: var(--color-text-faint); }

.top-list { list-style: none; margin: 0; padding: 0; }
.top-list li { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--color-border); }
.top-list li:last-child { border-bottom: none; }
.rank { width: 22px; height: 22px; border-radius: 3px; background: #eee; font-size: 12px; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.rank.top { background: var(--color-primary); color: white; }
.top-list img { width: 44px; height: 44px; object-fit: cover; border-radius: 2px; flex-shrink: 0; }
.top-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top-name:hover { color: var(--color-primary); }
.top-price { color: var(--color-primary); white-space: nowrap; }
.top-sold { color: var(--color-text-muted); font-size: 13px; width: 90px; text-align: right; white-space: nowrap; }

.shop-card { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.shop-card .btn { width: 100%; }
.shop-avatar { width: 48px; height: 48px; border-radius: 50%; background: var(--color-primary-light); color: var(--color-primary); font-size: 20px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.shop-avatar { overflow: hidden; flex-shrink: 0; }
.shop-avatar img { width: 100%; height: 100%; object-fit: cover; }
.shop-info { flex: 1; min-width: 0; }
.shop-name { font-weight: 600; font-size: 16px; margin-bottom: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.tip { display: block; padding: 12px 0; border-bottom: 1px solid var(--color-border); }
.tip:last-child { border-bottom: none; padding-bottom: 0; }
.tip:first-of-type { padding-top: 0; }
.tip-title { font-weight: 600; margin-bottom: 4px; }
.tip:hover .tip-title { color: var(--color-primary); }
.tip-body { margin: 0; font-size: 13px; color: var(--color-text-muted); line-height: 1.5; }

@media (max-width: 1200px) {
  .dashboard { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .todo-grid { grid-template-columns: repeat(2, 1fr); }
  .metrics { grid-template-columns: 1fr 1fr; }
  .top-sold { display: none; }
}
</style>
