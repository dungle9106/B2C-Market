// Reports and AI of the seller centre (web: SellerFinanceView, analytics/*ReportView, ai/SellerAIReviewsView,
// ai/SellerAIPromoView, InventoryView): numbers, small bar charts and the same AI analyses.
import React, { useState } from 'react'
import { Alert, Pressable, RefreshControl, ScrollView, Text, TextInput, View } from 'react-native'
import { get, post, errorText } from '../api'
import { compact, formatDateTime, formatVnd } from '../format'
import { Button, C, Empty, Field, Img, Loading, Row, Select, styles, Tabs, useLoad } from '../ui'

const pad = (n) => String(n).padStart(2, '0')
const day = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const range = (days) => { const to = new Date(); const from = new Date(Date.now() - (days - 1) * 86400000); return { from: day(from), to: day(to) } }
const PERIODS = [{ key: '7', label: '7 ngày' }, { key: '30', label: '30 ngày' }, { key: '90', label: '90 ngày' }]
const pct = (v) => `${Math.round((v || 0) * 10) / 10}%`

function Kpi({ label, value, prev, money }) {
  const change = prev != null && Number(prev) > 0 ? Math.round(((Number(value) - Number(prev)) / Number(prev)) * 100) : null
  return (
    <View style={{ width: '50%', padding: 8 }}>
      <Text style={[styles.muted, { fontSize: 12 }]}>{label}</Text>
      <Text style={{ fontSize: 17, fontWeight: '600' }}>{money ? formatVnd(value) : value}</Text>
      {change != null ? <Text style={{ fontSize: 11, color: change >= 0 ? C.green : C.danger }}>{change >= 0 ? '▲' : '▼'} {Math.abs(change)}% so với kỳ trước</Text> : null}
    </View>
  )
}

// a bar per point of the series (sales, views...), with a few labels under it
export function Bars({ series, field, color = C.primary, height = 90 }) {
  const values = (series || []).map(s => Number(s[field]) || 0)
  const max = Math.max(1, ...values)
  const step = Math.max(1, Math.ceil(values.length / 6))
  return (
    <View>
      <View style={{ flexDirection: 'row', alignItems: 'flex-end', height, gap: 1 }}>
        {values.map((v, i) => <View key={i} style={{ flex: 1, height: Math.max(1, (v / max) * height), backgroundColor: v ? color : C.border }} />)}
      </View>
      <Row style={{ justifyContent: 'space-between', marginTop: 2 }}>
        {(series || []).filter((_, i) => i % step === 0).map(s => <Text key={s.key} style={{ fontSize: 9, color: C.faint }}>{s.label}</Text>)}
      </Row>
    </View>
  )
}

// ==================== analytics: revenue, products, traffic, reviews ====================

const REPORTS = [{ key: 'revenue', label: 'Doanh thu' }, { key: 'products', label: 'Sản phẩm' }, { key: 'traffic', label: 'Lượt xem' }, { key: 'reviews', label: 'Đánh giá' }]
const ORDER_STATUS = { PENDING: 'Chờ xác nhận', CONFIRMED: 'Chờ lấy hàng', SHIPPING: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy', RETURN_REQUESTED: 'Trả hàng', REFUNDED: 'Hoàn tiền', AWAITING_PAYMENT: 'Chờ thanh toán' }

export function AnalyticsScreen() {
  const [report, setReport] = useState('revenue')
  const [period, setPeriod] = useState('30')
  const { data: d, error, reload, refreshing, refresh } = useLoad(() => get(`/seller/analytics/${report}`, { ...range(Number(period)), granularity: Number(period) > 31 ? 'week' : 'day' }), [report, period])
  return (
    <View style={styles.page}>
      <Tabs items={REPORTS} value={report} onChange={setReport} />
      <Tabs items={PERIODS} value={period} onChange={setPeriod} />
      {!d ? (error ? <Empty error={error} onRetry={reload} /> : <Loading />) : (
        <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
          <Text style={[styles.muted, { padding: 10, fontSize: 12 }]}>Từ {d.from} đến {d.to}{d.previousFrom ? ` · so với ${d.previousFrom} – ${d.previousTo}` : ''}</Text>
          {report === 'revenue' ? (
            <>
              <View style={[styles.card, { flexDirection: 'row', flexWrap: 'wrap', padding: 6 }]}>
                <Kpi label="Doanh số" value={d.summary.sales} prev={d.previous?.sales} money />
                <Kpi label="Đơn hàng" value={d.summary.orders} prev={d.previous?.orders} />
                <Kpi label="Giá trị TB/đơn" value={d.summary.avgOrderValue} prev={d.previous?.avgOrderValue} money />
                <Kpi label="Người mua" value={d.summary.buyers} prev={d.previous?.buyers} />
                <Kpi label="Sản phẩm bán ra" value={d.summary.items} prev={d.previous?.items} />
                <Kpi label="Tỉ lệ hủy" value={pct(d.summary.cancelRate)} />
              </View>
              <View style={styles.card}><Text style={styles.h2}>Doanh số theo {d.granularity === 'week' ? 'tuần' : 'ngày'}</Text><Bars series={d.series} field="sales" /></View>
              <View style={styles.card}>
                <Text style={styles.h2}>Đơn theo trạng thái</Text>
                {(d.statuses || []).map(s => <Row key={s.status} style={{ justifyContent: 'space-between', paddingVertical: 3 }}><Text>{ORDER_STATUS[s.status] || s.status}</Text><Text>{s.orders} đơn · {formatVnd(s.value)}</Text></Row>)}
              </View>
            </>
          ) : report === 'products' ? (
            <>
              <View style={[styles.card, { flexDirection: 'row', flexWrap: 'wrap', padding: 6 }]}>
                <Kpi label="Sản phẩm có đơn" value={`${d.summary.productsSold}/${d.summary.products}`} />
                <Kpi label="Doanh số" value={d.summary.sales} money />
                <Kpi label="Lượt xem" value={d.summary.views} />
                <Kpi label="Tỉ lệ chuyển đổi" value={pct(d.summary.conversion)} />
                <Kpi label="Tồn kho" value={`${d.inventory.totalStock} sp`} />
                <Kpi label="Giá trị tồn" value={d.inventory.stockValue} money />
              </View>
              <Text style={[styles.card, styles.muted]}>Hết hàng {d.inventory.outOfStock} · sắp hết {d.inventory.lowStock} (≤{d.lowStockThreshold}) · bán chậm {d.inventory.slowMovers} · đang ẩn {d.inventory.hidden}</Text>
              {[...(d.products || [])].sort((a, b) => b.sales - a.sales || b.views - a.views).slice(0, 30).map(p => (
                <View key={p.id} style={[styles.card, { flexDirection: 'row', gap: 10 }]}>
                  <Img uri={p.thumbnailUrl} style={{ width: 44, height: 44 }} />
                  <View style={{ flex: 1 }}>
                    <Text numberOfLines={1}>{p.name}</Text>
                    <Text style={[styles.muted, { fontSize: 12 }]}>{formatVnd(p.sales)} · {p.units} sp · {p.views} lượt xem · chuyển đổi {pct(p.conversion)}</Text>
                    <Text style={[styles.muted, { fontSize: 12 }]}>Kho {p.stock}{p.daysOfStock != null ? ` · đủ bán ~${p.daysOfStock} ngày` : ''}</Text>
                  </View>
                </View>
              ))}
            </>
          ) : report === 'traffic' ? (
            <>
              <View style={[styles.card, { flexDirection: 'row', flexWrap: 'wrap', padding: 6 }]}>
                <Kpi label="Lượt xem" value={d.summary.views} prev={d.previous?.views} />
                <Kpi label="Khách truy cập" value={d.summary.visitors} prev={d.previous?.visitors} />
                <Kpi label="Thêm vào giỏ" value={d.summary.cartVisitors} prev={d.previous?.cartVisitors} />
                <Kpi label="Tỉ lệ chuyển đổi" value={pct(d.summary.conversion)} />
              </View>
              <View style={styles.card}><Text style={styles.h2}>Lượt xem</Text><Bars series={d.series} field="views" color="#2673dd" /></View>
              <View style={styles.card}>
                <Text style={styles.h2}>Phễu chuyển đổi</Text>
                {(d.funnel || []).map((f, i) => {
                  const top = Math.max(1, d.funnel[0].value)
                  return (
                    <View key={f.step} style={{ marginBottom: 6 }}>
                      <Text style={{ fontSize: 12 }}>{f.step}: {f.value}</Text>
                      <View style={{ height: 10, width: `${Math.max(2, (f.value / top) * 100)}%`, backgroundColor: [C.primary, '#f69113', C.green][i % 3] }} />
                    </View>
                  )
                })}
              </View>
              {(d.attention || []).length ? <Text style={[styles.card, { color: '#b26a00' }]}>⚠ {d.attention.length} sản phẩm nhiều lượt xem nhưng chưa có đơn: {d.attention.slice(0, 3).map(a => a.name).join(', ')}</Text> : null}
            </>
          ) : (
            <>
              <View style={[styles.card, { flexDirection: 'row', flexWrap: 'wrap', padding: 6 }]}>
                <Kpi label="Đánh giá mới" value={d.summary.count} prev={d.previous?.count} />
                <Kpi label="Điểm trung bình" value={`★ ${Number(d.summary.average || 0).toFixed(1)}`} />
                <Kpi label="Tích cực" value={pct(d.summary.positiveRate)} />
                <Kpi label="Tỉ lệ phản hồi" value={pct(d.summary.replyRate)} />
              </View>
              <View style={styles.card}>
                <Text style={styles.h2}>Phân bố số sao</Text>
                {[5, 4, 3, 2, 1].map(s => {
                  const n = d.distribution?.[s] || 0
                  return (
                    <Row key={s} style={{ gap: 6, marginBottom: 4 }}>
                      <Text style={{ width: 28 }}>{s}★</Text>
                      <View style={{ flex: 1, height: 8, backgroundColor: C.border }}><View style={{ height: 8, width: `${d.summary.count ? (n / d.summary.count) * 100 : 0}%`, backgroundColor: '#ffce3d' }} /></View>
                      <Text style={{ width: 30, textAlign: 'right' }}>{n}</Text>
                    </Row>
                  )
                })}
                {d.summary.unrepliedNegative ? <Text style={{ color: C.danger, marginTop: 6 }}>{d.summary.unrepliedNegative} đánh giá tiêu cực chưa phản hồi</Text> : null}
              </View>
              <Text style={[styles.card, styles.muted]}>Đánh giá shop: {d.service?.shopRating ? `★ ${Number(d.service.shopRating).toFixed(1)} (${d.service.shopRatingCount})` : '—'} · Vận chuyển: {d.service?.shippingRating ? `★ ${Number(d.service.shippingRating).toFixed(1)}` : '—'}</Text>
            </>
          )}
        </ScrollView>
      )}
    </View>
  )
}

// ==================== finance: payouts and settlements ====================

const SETTLEMENT = { PENDING: ['Chờ thanh toán', '#f69113'], PAID: ['Đã thanh toán', C.green] }

export function FinanceScreen() {
  const { data: f, error, reload, refreshing, refresh } = useLoad(() => get('/seller/finance'))
  const [open, setOpen] = useState(null)
  const [detail, setDetail] = useState(null)
  if (!f) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const show = async (s) => {
    if (open === s.id) { setOpen(null); return }
    setOpen(s.id)
    setDetail(null)
    try { setDetail(await get(`/seller/finance/settlements/${s.id}`)) } catch (e) { setDetail({ error: errorText(e) }) }
  }
  const u = f.unsettled || {}
  const Line = ({ label, value, strong, minus }) => (
    <Row style={{ justifyContent: 'space-between', paddingVertical: 2 }}>
      <Text style={strong ? { fontWeight: '600' } : styles.muted}>{label}</Text>
      <Text style={strong ? { fontWeight: '600', color: C.primary } : {}}>{minus && Number(value) ? '−' : ''}{formatVnd(value)}</Text>
    </Row>
  )
  return (
    <ScrollView style={styles.page} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <View style={styles.card}>
        <Text style={styles.h2}>Chưa đối soát ({u.orders || 0} đơn đã giao)</Text>
        <Line label="Tiền hàng" value={u.merchandise} />
        <Line label="Giảm giá của shop" value={u.shopDiscounts} minus />
        <Line label={`Phí sàn (${f.commissionRate}%)`} value={u.commission} minus />
        <Line label="Hoàn tiền" value={u.refunds} minus />
        <Line label="Dự kiến nhận" value={u.netPayout} strong />
        <Text style={[styles.muted, { fontSize: 12, marginTop: 6 }]}>Đơn được đối soát sau {f.holdDays} ngày kể từ khi giao thành công. Sẵn sàng đối soát: {f.readyOrders} đơn.</Text>
      </View>
      <Row style={{ backgroundColor: 'white', marginBottom: 8 }}>
        <View style={{ flex: 1, padding: 12 }}><Text style={styles.muted}>Đang chờ thanh toán</Text><Text style={{ fontWeight: '600', fontSize: 16 }}>{formatVnd(f.pendingPayment)}</Text></View>
        <View style={{ flex: 1, padding: 12 }}><Text style={styles.muted}>Đã nhận</Text><Text style={{ fontWeight: '600', fontSize: 16, color: C.green }}>{formatVnd(f.paidTotal)}</Text></View>
      </Row>
      <Text style={[styles.h2, { paddingHorizontal: 12 }]}>Kỳ đối soát</Text>
      {!f.settlements.length ? <Text style={[styles.card, styles.muted]}>Chưa có kỳ đối soát nào</Text> : f.settlements.map(s => (
        <Pressable key={s.id} onPress={() => show(s)} style={styles.card}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Text style={{ fontWeight: '500' }}>{s.code}</Text>
            <Text style={{ color: (SETTLEMENT[s.status] || [])[1], fontSize: 12 }}>{(SETTLEMENT[s.status] || [s.status])[0]}</Text>
          </Row>
          <Text style={styles.muted}>{s.orderCount} đơn · đến {String(s.periodTo).slice(0, 10)} · nhận {formatVnd(s.netPayout)}</Text>
          {s.paidAt ? <Text style={[styles.muted, { fontSize: 12 }]}>Đã chuyển {formatDateTime(s.paidAt)}{s.paymentReference ? ` · ${s.paymentReference}` : ''}</Text> : null}
          {open === s.id ? (
            <View style={{ marginTop: 8, borderTopWidth: 1, borderTopColor: C.border, paddingTop: 6 }}>
              <Line label="Tiền hàng" value={s.merchandise} />
              <Line label="Giảm giá của shop" value={s.shopDiscounts} minus />
              <Line label="Phí sàn" value={s.commission} minus />
              <Line label="Hoàn tiền" value={s.refunds} minus />
              {!detail ? <Text style={styles.muted}>Đang tải...</Text> : detail.error ? <Text style={{ color: C.danger }}>{detail.error}</Text>
                : (detail.orders || detail.lines || []).slice(0, 30).map(o => (
                  <Row key={o.id || o.orderId || o.code} style={{ justifyContent: 'space-between', paddingVertical: 2 }}>
                    <Text style={{ fontSize: 12 }}>{o.code || o.orderCode}</Text><Text style={{ fontSize: 12 }}>{formatVnd(o.netPayout ?? o.total ?? o.merchandise)}</Text>
                  </Row>
                ))}
            </View>
          ) : null}
        </Pressable>
      ))}
    </ScrollView>
  )
}

// ==================== AI: what the reviews say ====================

export function AIReviewsScreen() {
  const [days, setDays] = useState('90')
  const { data: d, error, reload, refreshing, refresh } = useLoad(() => get('/seller/ai/review-insights', { days: Number(days) }), [days])
  return (
    <View style={styles.page}>
      <Tabs items={[{ key: '30', label: '30 ngày' }, { key: '90', label: '90 ngày' }, { key: '365', label: '1 năm' }]} value={days} onChange={setDays} />
      {!d ? (error ? <Empty error={error} onRetry={reload} /> : <Loading />) : (
        <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
          <View style={styles.card}>
            <Text style={styles.h2}>✨ Tóm tắt</Text>
            <Text style={{ lineHeight: 20 }}>{d.summary}</Text>
            <Text style={[styles.muted, { marginTop: 6, fontSize: 12 }]}>{d.reviews} đánh giá · ★ {Number(d.average || 0).toFixed(1)} · 👍 {d.sentiment?.positive} · 😐 {d.sentiment?.neutral} · 👎 {d.sentiment?.negative} · {d.source === 'rule' ? 'phân tích theo luật' : 'AI'}</Text>
          </View>
          {(d.aspects || []).map(a => (
            <View key={a.key} style={styles.card}>
              <Row style={{ justifyContent: 'space-between' }}>
                <Text style={{ fontWeight: '600' }}>{a.label}</Text>
                <Text style={{ color: a.positiveRate >= 60 ? C.green : C.danger }}>{a.mentions} lượt · {Math.round(a.positiveRate)}% tích cực</Text>
              </Row>
              {a.praise?.length ? <Text style={{ color: C.green, marginTop: 4 }}>👍 {a.praise.slice(0, 3).join(' · ')}</Text> : null}
              {a.complaints?.length ? <Text style={{ color: C.danger, marginTop: 4 }}>👎 {a.complaints.slice(0, 3).join(' · ')}</Text> : null}
              {a.action ? <Text style={{ marginTop: 6, backgroundColor: C.primarySoft, padding: 6 }}>💡 {a.action}</Text> : null}
            </View>
          ))}
          {(d.products || []).length ? (
            <View style={styles.card}>
              <Text style={styles.h2}>Sản phẩm cần chú ý</Text>
              {d.products.map(p => <Text key={p.id} style={{ paddingVertical: 3 }}>• {p.name}: ★ {Number(p.average).toFixed(1)} ({p.reviews}){p.topComplaint ? ` · hay bị chê: ${p.topComplaint}` : ''}</Text>)}
            </View>
          ) : null}
        </ScrollView>
      )}
    </View>
  )
}

// ==================== AI: when to run a promotion ====================

export function PromoTimingScreen() {
  const { data: d, error, reload, refreshing, refresh } = useLoad(() => get('/seller/ai/promo-timing'))
  if (!d) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const maxH = Math.max(1, ...(d.ordersByHour || []))
  const ProductList = ({ title, items }) => (items?.length ? (
    <View style={styles.card}>
      <Text style={styles.h2}>{title}</Text>
      {items.map(p => (
        <Row key={p.id} style={{ gap: 8, paddingVertical: 4 }}>
          <Img uri={p.thumbnailUrl} style={{ width: 36, height: 36 }} />
          <View style={{ flex: 1 }}><Text numberOfLines={1}>{p.name}</Text><Text style={[styles.muted, { fontSize: 12 }]}>{p.reason}</Text></View>
        </Row>
      ))}
    </View>
  ) : null)
  return (
    <ScrollView style={styles.page} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <View style={styles.card}>
        <Text style={styles.h2}>✨ Gợi ý</Text>
        <Text style={{ lineHeight: 20 }}>{d.summary}</Text>
        <Text style={{ marginTop: 6 }}>Ngày tốt nhất: <Text style={{ fontWeight: '600', color: C.primary }}>{(d.bestWeekdays || []).join(', ')}</Text> · Khung giờ: <Text style={{ fontWeight: '600', color: C.primary }}>{d.bestWindow}</Text></Text>
        <Text style={[styles.muted, { fontSize: 12, marginTop: 4 }]}>Phân tích {compact(d.ordersAnalyzed)} đơn {d.scope === 'platform' ? 'của toàn sàn (shop chưa đủ dữ liệu)' : 'của shop'} từ {d.from} đến {d.to}</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Đơn theo giờ trong ngày</Text>
        <View style={{ flexDirection: 'row', alignItems: 'flex-end', height: 70, gap: 1 }}>
          {(d.ordersByHour || []).map((v, h) => <View key={h} style={{ flex: 1, height: Math.max(1, (v / maxH) * 70), backgroundColor: C.primary }} />)}
        </View>
        <Row style={{ justifyContent: 'space-between' }}>{[0, 6, 12, 18, 23].map(h => <Text key={h} style={{ fontSize: 9, color: C.faint }}>{h}h</Text>)}</Row>
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Theo thứ trong tuần</Text>
        {(d.weekdays || []).map(w => (
          <Row key={w.name} style={{ gap: 6, marginBottom: 3 }}>
            <Text style={{ width: 70 }}>{w.name}</Text>
            <View style={{ flex: 1, height: 8, backgroundColor: C.border }}><View style={{ height: 8, width: `${Math.min(100, w.index * 70)}%`, backgroundColor: w.index >= 1 ? C.green : C.faint }} /></View>
            <Text style={{ width: 40, textAlign: 'right', fontSize: 12 }}>{w.index.toFixed(2)}</Text>
          </Row>
        ))}
      </View>
      {(d.events || []).length ? (
        <View style={styles.card}>
          <Text style={styles.h2}>📅 Sự kiện sắp tới</Text>
          {d.events.slice(0, 6).map(e => (
            <View key={e.date + e.name} style={{ paddingVertical: 5, borderTopWidth: 1, borderTopColor: C.border }}>
              <Text style={{ fontWeight: '500' }}>{e.name} · còn {e.daysLeft} ngày</Text>
              <Text style={[styles.muted, { fontSize: 12 }]}>Chuẩn bị từ {e.prepFrom}. {e.tips}</Text>
            </View>
          ))}
        </View>
      ) : null}
      <ProductList title="🏷 Nên xả hàng" items={d.products?.clearance} />
      <ProductList title="📈 Đang lên" items={d.products?.rising} />
      <ProductList title="👀 Nhiều người quan tâm" items={d.products?.interest} />
      <ProductList title="⚠ Sắp hết hàng" items={d.products?.lowStock} />
    </ScrollView>
  )
}

// ==================== inventory: stock, adjustments, history ====================

const STOCK = { OK: ['Còn hàng', C.green], LOW: ['Sắp hết', '#f69113'], OUT: ['Hết hàng', C.danger] }
const MODES = [{ value: 'ADD', label: '+ Nhập thêm' }, { value: 'SUBTRACT', label: '− Xuất bớt' }, { value: 'SET', label: 'Đặt số lượng' }]
const ADJUST_REASONS = { RESTOCK: 'Nhập hàng', STOCKTAKE: 'Kiểm kho (đối chiếu thực tế)', DAMAGED: 'Hàng hư hỏng / thất lạc', OTHER: 'Khác' }
const LOG_REASONS = { CREATE: 'Tạo sản phẩm', ORDER: 'Bán (đặt hàng)', CANCEL: 'Hủy đơn', RETURN: 'Trả hàng', EDIT: 'Sửa sản phẩm', SOLD_OUT: 'Đánh dấu hết hàng', IMPORT: 'Nhập Excel', ...ADJUST_REASONS }

export function InventoryScreen({ navigation }) {
  const [tab, setTab] = useState('stock')
  const [q, setQ] = useState('')
  const [filter, setFilter] = useState('')
  const [adj, setAdj] = useState(null)
  const [busy, setBusy] = useState(false)
  const { data: d, error, reload, refreshing, refresh } = useLoad(() => get('/seller/inventory'))
  const { data: logs, reload: reloadLogs } = useLoad(() => get('/seller/inventory/logs', { size: 50 }))
  if (!d) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const rows = d.rows.filter(r => (!filter || r.status === filter) && (!q || r.name.toLowerCase().includes(q.toLowerCase()) || (r.sku || '').toLowerCase().includes(q.toLowerCase())))

  async function save() {
    setBusy(true)
    try {
      await post('/seller/inventory/adjust', { productId: adj.row.productId, variantId: adj.row.variantId, mode: adj.mode, quantity: Number(adj.quantity), reason: adj.reason, note: adj.note })
      setAdj(null)
      reload()
      reloadLogs()
    } catch (e) {
      Alert.alert('Không cập nhật được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={styles.page}>
      <Tabs items={[{ key: 'stock', label: 'Tồn kho' }, { key: 'logs', label: 'Lịch sử' }]} value={tab} onChange={setTab} />
      {tab === 'stock' ? (
        <ScrollView keyboardShouldPersistTaps="handled" refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
          <Row style={{ backgroundColor: 'white', marginBottom: 8 }}>
            {[['SKU', d.summary.skus], ['Tổng tồn', d.summary.units], ['Hết hàng', d.summary.outOfStock], ['Sắp hết', d.summary.lowStock]].map(([k, v]) => (
              <View key={k} style={{ flex: 1, alignItems: 'center', padding: 10 }}><Text style={{ fontWeight: '600', fontSize: 16 }}>{v}</Text><Text style={[styles.muted, { fontSize: 11 }]}>{k}</Text></View>
            ))}
          </Row>
          <Text style={[styles.muted, { paddingHorizontal: 12, marginBottom: 6 }]}>Giá trị tồn kho {formatVnd(d.summary.value)} · <Text style={{ color: C.primary }} onPress={() => navigation.navigate('StockForecast')}>✨ Dự báo nhập hàng ›</Text></Text>
          <View style={{ paddingHorizontal: 12, gap: 6 }}>
            <TextInput value={q} onChangeText={setQ} placeholder="🔍 Tên sản phẩm hoặc SKU" placeholderTextColor={C.faint} style={styles.input} />
            <Select value={filter} onChange={setFilter} options={[{ value: '', label: 'Tất cả trạng thái' }, ...Object.entries(STOCK).map(([value, [label]]) => ({ value, label }))]} />
          </View>
          {rows.map(r => {
            const key = `${r.productId}-${r.variantId || 0}`
            return (
              <View key={key} style={styles.card}>
                <Row style={{ gap: 10 }}>
                  <Img uri={r.thumbnailUrl} style={{ width: 44, height: 44 }} />
                  <View style={{ flex: 1 }}>
                    <Text numberOfLines={1}>{r.name}{r.variantName ? ` · ${r.variantName}` : ''}</Text>
                    <Text style={[styles.muted, { fontSize: 12 }]}>{r.sku || '—'} · {formatVnd(r.price)}{r.toShip ? ` · chờ giao ${r.toShip}` : ''}</Text>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <Text style={{ fontWeight: '600', fontSize: 16 }}>{r.stock}</Text>
                    <Text style={{ fontSize: 11, color: (STOCK[r.status] || [])[1] }}>{(STOCK[r.status] || [r.status])[0]}</Text>
                  </View>
                </Row>
                {adj?.key === key ? (
                  <View style={{ marginTop: 8 }}>
                    <Select value={adj.mode} onChange={v => setAdj({ ...adj, mode: v })} options={MODES} />
                    <Field label="Số lượng" value={adj.quantity} onChangeText={v => setAdj({ ...adj, quantity: v.replace(/\D/g, '') })} keyboardType="number-pad" />
                    <Select label="Lý do" value={adj.reason} onChange={v => setAdj({ ...adj, reason: v })} options={Object.entries(ADJUST_REASONS).map(([value, label]) => ({ value, label }))} />
                    <Field label="Ghi chú" value={adj.note} onChangeText={v => setAdj({ ...adj, note: v })} />
                    <Row style={{ gap: 8 }}>
                      <Button small kind="ghost" title="Hủy" onPress={() => setAdj(null)} style={{ flex: 1 }} />
                      <Button small title="Cập nhật" disabled={busy || !adj.quantity} onPress={save} style={{ flex: 1 }} />
                    </Row>
                  </View>
                ) : <Text style={{ color: C.primary, marginTop: 6 }} onPress={() => setAdj({ key, row: r, mode: 'ADD', quantity: '', reason: 'RESTOCK', note: '' })}>Điều chỉnh tồn kho</Text>}
              </View>
            )
          })}
        </ScrollView>
      ) : (
        <ScrollView>
          {(logs?.items || []).map(l => (
            <View key={l.id} style={styles.card}>
              <Row style={{ justifyContent: 'space-between' }}>
                <Text numberOfLines={1} style={{ flex: 1 }}>{l.productName}{l.variantName ? ` · ${l.variantName}` : ''}</Text>
                <Text style={{ fontWeight: '600', color: l.change >= 0 ? C.green : C.danger }}>{l.change > 0 ? '+' : ''}{l.change}</Text>
              </Row>
              <Text style={[styles.muted, { fontSize: 12 }]}>{LOG_REASONS[l.reason] || l.reason}{l.orderCode ? ` · ${l.orderCode}` : ''} · còn {l.stockAfter} · {formatDateTime(l.createdAt)}</Text>
              {l.note ? <Text style={{ fontSize: 12 }}>{l.note}</Text> : null}
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  )
}

// ==================== AI price suggestion (product form) ====================

export function PriceSuggestion({ name, categoryId, onApply }) {
  const [d, setD] = useState(null)
  const [cost, setCost] = useState('')
  const [busy, setBusy] = useState(false)
  async function ask() {
    if (!name?.trim()) { Alert.alert('Nhập tên sản phẩm trước'); return }
    setBusy(true)
    try {
      setD(await post('/seller/ai/price-suggestion', { name, categoryId, cost: Number(cost) || undefined }))
    } catch (e) {
      Alert.alert('Chưa gợi ý được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  return (
    <View style={{ backgroundColor: C.primarySoft, padding: 10, marginBottom: 12 }}>
      <Row style={{ gap: 8 }}>
        <TextInput value={cost} onChangeText={v => setCost(v.replace(/\D/g, ''))} keyboardType="number-pad" placeholder="Giá vốn (không bắt buộc)" placeholderTextColor={C.faint} style={[styles.input, { flex: 1 }]} />
        <Button small title={busy ? '...' : '✨ AI gợi ý giá'} disabled={busy} onPress={ask} />
      </Row>
      {d ? (
        d.suggested ? (
          <View style={{ marginTop: 8 }}>
            <Row style={{ justifyContent: 'space-between' }}>
              <View>
                <Text style={{ fontSize: 18, fontWeight: '600', color: C.primary }}>{formatVnd(d.suggested)}</Text>
                <Text style={[styles.muted, { fontSize: 12 }]}>dựa trên {d.count} sản phẩm {d.basis === 'similar' ? 'tương tự' : 'cùng ngành hàng'} · {formatVnd(d.min)} – {formatVnd(d.max)}</Text>
              </View>
              <Button small kind="ghost" title="Dùng giá này" onPress={() => onApply(String(Math.round(d.suggested)))} />
            </Row>
            {(d.notes || []).map((n, i) => <Text key={i} style={[styles.muted, { fontSize: 12, marginTop: 2 }]}>• {n}</Text>)}
          </View>
        ) : <Text style={[styles.muted, { marginTop: 6 }]}>{d.message || 'Chưa đủ dữ liệu để gợi ý giá cho sản phẩm này.'}</Text>
      ) : null}
    </View>
  )
}
