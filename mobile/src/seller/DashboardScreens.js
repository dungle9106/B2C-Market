import React, { useState } from 'react'
import { Alert, KeyboardAvoidingView, Pressable, RefreshControl, ScrollView, Text, TextInput, View } from 'react-native'
import { get, post, errorText } from '../api'
import { useAuth } from '../auth'
import { PROVINCES } from '../provinces'
import { compact, formatDateTime, formatVnd } from '../format'
import { MenuItem } from '../buyer/AccountScreens'
import { BottomBar, Button, C, Empty, Field, Loading, Row, Select, styles, useLoad } from '../ui'

function Todo({ value, label, onPress }) {
  return (
    <Pressable onPress={onPress} style={{ width: '33.33%', alignItems: 'center', paddingVertical: 12 }}>
      <Text style={{ fontSize: 20, color: value ? C.primary : C.text, fontWeight: '600' }}>{value}</Text>
      <Text style={{ fontSize: 12, color: C.muted, textAlign: 'center' }}>{label}</Text>
    </Pressable>
  )
}

function Change({ now, before }) {
  if (!before) return <Text style={{ fontSize: 11, color: C.faint }}>Hôm qua: 0</Text>
  const pct = Math.round(((now - before) / before) * 100)
  return <Text style={{ fontSize: 11, color: pct >= 0 ? C.green : C.danger }}>{pct >= 0 ? '▲' : '▼'} {Math.abs(pct)}% so với hôm qua</Text>
}

export default function DashboardScreen({ navigation }) {
  const { data: s, error, refreshing, refresh, reload } = useLoad(() => get('/seller/dashboard/stats'))
  const { data: shop } = useLoad(() => get('/seller/shop'))
  if (!s) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const go = (tab) => navigation.navigate('SellerTabs', { screen: tab })
  return (
    <ScrollView style={styles.page} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <View style={{ backgroundColor: C.primary, paddingTop: 52, paddingBottom: 16, paddingHorizontal: 16 }}>
        <Text style={{ color: 'white', fontSize: 18, fontWeight: '600' }}>{shop?.name || 'Shop của tôi'}</Text>
        <Text style={{ color: 'white', opacity: 0.85 }}>Kênh Người Bán Shoppi</Text>
      </View>
      {shop && shop.status !== 'APPROVED' ? (
        <Text style={[styles.card, { color: '#b26a00' }]}>
          {shop.status === 'PENDING' ? '⏳ Shop đang chờ Shoppi duyệt. Bạn vẫn có thể đăng sản phẩm.' : `⚠ Shop bị từ chối: ${shop.statusReason || ''}`}
        </Text>
      ) : null}
      <View style={styles.card}>
        <Text style={styles.h2}>Việc cần làm</Text>
        <Row style={{ flexWrap: 'wrap' }}>
          <Todo value={s.pendingOrders} label="Chờ xác nhận" onPress={() => go('SellerOrders')} />
          <Todo value={s.toShipOrders} label="Chờ lấy hàng" onPress={() => go('SellerOrders')} />
          <Todo value={s.shippingOrders} label="Đang giao" onPress={() => go('SellerOrders')} />
          <Todo value={s.pendingReturns} label="Trả hàng/Hoàn tiền" onPress={() => go('SellerOrders')} />
          <Todo value={s.outOfStockProducts} label="Sản phẩm hết hàng" onPress={() => go('SellerProducts')} />
          <Todo value={s.unansweredQuestions} label="Câu hỏi chưa trả lời" />
        </Row>
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Hôm nay</Text>
        <Row style={{ flexWrap: 'wrap', rowGap: 14 }}>
          {[['Doanh số', formatVnd(s.todaySales), s.todaySales, s.yesterdaySales], ['Đơn hàng', s.todayOrders, s.todayOrders, s.yesterdayOrders],
            ['Lượt truy cập', s.todayVisitors, s.todayVisitors, s.yesterdayVisitors], ['Người mua', s.todayBuyers, s.todayBuyers, s.yesterdayBuyers]]
            .map(([label, shown, now, before]) => (
              <View key={label} style={{ width: '50%' }}>
                <Text style={styles.muted}>{label}</Text>
                <Text style={{ fontSize: 18, fontWeight: '600' }}>{shown}</Text>
                <Change now={now} before={before} />
              </View>
            ))}
        </Row>
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Tổng quan</Text>
        <Text>Doanh thu (đơn đã giao thành công): <Text style={{ color: C.primary, fontWeight: '600' }}>{formatVnd(s.totalRevenue)}</Text></Text>
        <Text>{s.totalOrders} đơn hàng · {s.totalProducts} sản phẩm · {s.hiddenProducts} đang ẩn</Text>
      </View>
      {s.topProducts?.length ? (
        <View style={styles.card}>
          <Text style={styles.h2}>Sản phẩm bán chạy</Text>
          {s.topProducts.slice(0, 5).map((p, i) => (
            <Row key={p.id} style={{ justifyContent: 'space-between', paddingVertical: 4 }}>
              <Text numberOfLines={1} style={{ flex: 1 }}>{i + 1}. {p.name}</Text>
              <Text style={styles.muted}>Đã bán {compact(p.soldCount)}</Text>
            </Row>
          ))}
        </View>
      ) : null}
      <MenuItem icon="↩" label="Trả hàng/Hoàn tiền" onPress={() => navigation.navigate('SellerReturns')} />
      <MenuItem icon="⭐" label="Trả lời đánh giá" hint="AI soạn sẵn" onPress={() => navigation.navigate('ReviewInbox')} />
      <MenuItem icon="📦" label="Dự báo nhập hàng" hint="AI" onPress={() => navigation.navigate('StockForecast')} />
      <MenuItem icon="🤖" label="Trợ lý bán hàng AI" hint="Hỏi về doanh số" onPress={() => navigation.navigate('SalesAssistant')} />
      <MenuItem icon="📊" label="Hiệu quả hoạt động" onPress={() => navigation.navigate('Performance')} />
      <View style={{ height: 20 }} />
    </ScrollView>
  )
}

// natural-language questions about the shop's sales, answered from the shop's own data
export function SalesAssistantScreen() {
  const { data: examples } = useLoad(() => get('/seller/ai/ask/examples'))
  const [q, setQ] = useState('')
  const [items, setItems] = useState([])
  const [busy, setBusy] = useState(false)

  async function ask(question = q) {
    if (!question.trim()) return
    setBusy(true)
    setQ('')
    try {
      const r = await post('/seller/ai/ask', { question })
      setItems(x => [{ question, answer: r.answer, suggestions: r.suggestions || [] }, ...x])
    } catch (e) {
      setItems(x => [{ question, answer: '⚠ ' + errorText(e), suggestions: [] }, ...x])
    } finally {
      setBusy(false)
    }
  }

  return (
    <KeyboardAvoidingView behavior="padding" style={styles.page} keyboardVerticalOffset={90}>
      <ScrollView contentContainerStyle={{ padding: 12 }}>
        {!items.length ? (
          <View style={[styles.card, { borderRadius: 8 }]}>
            <Text style={{ marginBottom: 6 }}>Hỏi về tình hình kinh doanh của shop, ví dụ:</Text>
            {(examples || []).slice(0, 6).map(e => <Text key={e} onPress={() => ask(e)} style={{ color: C.primary, marginTop: 6 }}>› {e}</Text>)}
          </View>
        ) : null}
        {items.map((it, i) => (
          <View key={i} style={{ marginBottom: 12 }}>
            <Text style={{ alignSelf: 'flex-end', backgroundColor: C.primary, color: 'white', padding: 10, borderRadius: 8, overflow: 'hidden', maxWidth: '80%' }}>{it.question}</Text>
            <View style={{ backgroundColor: 'white', padding: 10, borderRadius: 8, marginTop: 6 }}>
              <Text>{it.answer}</Text>
              {it.suggestions.slice(0, 3).map(s => <Text key={s} onPress={() => ask(s)} style={{ color: C.primary, marginTop: 6 }}>› {s}</Text>)}
            </View>
          </View>
        ))}
      </ScrollView>
      <BottomBar>
        <TextInput value={q} onChangeText={setQ} placeholder="VD: Doanh thu tuần này?" placeholderTextColor={C.faint} style={[styles.input, { flex: 1 }]} onSubmitEditing={() => ask()} />
        <Button title={busy ? '...' : 'Hỏi'} small onPress={() => ask()} disabled={busy || !q.trim()} />
      </BottomBar>
    </KeyboardAvoidingView>
  )
}

const LEVEL_COLOR = { GOOD: C.green, WARN: '#b26a00', DANGER: C.danger }

export function PerformanceScreen() {
  const { data: r, error, reload } = useLoad(() => get('/seller/performance'))
  if (!r) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const m = r.metrics
  const pct = (v) => (v == null ? '—' : v + '%')
  return (
    <ScrollView style={styles.page}>
      <View style={[styles.card, { alignItems: 'center' }]}>
        <Text style={styles.muted}>Điểm Sao Quả Tạ (quý này)</Text>
        <Text style={{ fontSize: 40, fontWeight: '700', color: LEVEL_COLOR[r.level.key] || C.text }}>{r.points}</Text>
        <Text style={{ color: LEVEL_COLOR[r.level.key] || C.text }}>{r.level.text}</Text>
        <Text style={[styles.muted, { marginTop: 4 }]}>Đặt lại vào {formatDateTime(r.quarterEnds)}</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Chỉ số 30 ngày</Text>
        {[['Đơn hàng', m.orders], ['Tỷ lệ đơn không thành công', pct(m.nfrPercent)], ['Tỷ lệ giao hàng trễ', pct(m.latePercent)],
          ['Tỷ lệ phản hồi chat', pct(m.chatResponsePercent)], ['Đánh giá shop', m.rating ? `${m.rating}★ (${m.ratingCount})` : '—']]
          .map(([k, v]) => <Row key={k} style={{ justifyContent: 'space-between', paddingVertical: 5 }}><Text style={styles.muted}>{k}</Text><Text>{v}</Text></Row>)}
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>{r.favorite ? '⭐ Shop Yêu thích' : 'Tiêu chí Shop Yêu thích'}</Text>
        {r.favoriteCriteria.map(c => (
          <Row key={c.key} style={{ justifyContent: 'space-between', paddingVertical: 5, gap: 10 }}>
            <Text style={{ flex: 1 }}>{c.ok ? '✅' : '⬜'} {c.label}</Text>
            <Text style={styles.muted}>{c.value}</Text>
          </Row>
        ))}
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Lịch sử điểm phạt</Text>
        {r.penalties.map(p => (
          <View key={p.id} style={{ paddingVertical: 6, borderTopWidth: 1, borderTopColor: C.border, opacity: p.revoked ? 0.5 : 1 }}>
            <Text>+{p.points} điểm: {p.reason}{p.revoked ? ' (đã hủy)' : ''}</Text>
            <Text style={{ fontSize: 11, color: C.faint }}>{formatDateTime(p.createdAt)}</Text>
          </View>
        ))}
        {!r.penalties.length ? <Text style={styles.muted}>Không có điểm phạt nào 🎉</Text> : null}
      </View>
    </ScrollView>
  )
}

export function SellerAccountScreen({ navigation }) {
  const { user, signOut } = useAuth()
  const { data: shop } = useLoad(() => get('/seller/shop'))
  return (
    <ScrollView style={styles.page}>
      <View style={{ backgroundColor: C.primary, paddingTop: 56, paddingBottom: 20, paddingHorizontal: 16 }}>
        <Text style={{ color: 'white', fontSize: 18, fontWeight: '600' }}>{shop?.name}</Text>
        <Text style={{ color: 'white', opacity: 0.85 }}>{user?.fullName} · {user?.email}</Text>
      </View>
      {shop ? (
        <View style={styles.card}>
          <Text>📍 Lấy hàng: {shop.pickupAddress}{shop.province ? `, ${shop.province}` : ''}</Text>
          <Text>📞 {shop.pickupPhone}</Text>
          <Text>↩ Đổi trả {shop.defaultReturnDays} ngày · chuẩn bị hàng {shop.handlingDays} ngày</Text>
        </View>
      ) : null}
      <MenuItem icon="📊" label="Hiệu quả hoạt động" onPress={() => navigation.navigate('Performance')} />
      <MenuItem icon="🤖" label="Trợ lý bán hàng AI" onPress={() => navigation.navigate('SalesAssistant')} />
      <MenuItem icon="🔔" label="Thông báo" onPress={() => navigation.navigate('SellerNotifications')} />
      <MenuItem icon="↩" label="Trả hàng/Hoàn tiền" onPress={() => navigation.navigate('SellerReturns')} />
      <MenuItem icon="⭐" label="Trả lời đánh giá" onPress={() => navigation.navigate('ReviewInbox')} />
      <MenuItem icon="📦" label="Dự báo nhập hàng (AI)" onPress={() => navigation.navigate('StockForecast')} />
      <MenuItem icon="🔒" label="Đổi mật khẩu" onPress={() => navigation.navigate('ChangePassword')} />
      <Text style={[styles.muted, { padding: 14 }]}>Cài đặt shop, voucher, quảng cáo và báo cáo chi tiết dùng trên web Kênh Người Bán.</Text>
      <Button title="Đăng xuất" kind="ghost" style={{ margin: 16 }}
              onPress={() => Alert.alert('Đăng xuất?', '', [{ text: 'Hủy' }, { text: 'Đăng xuất', style: 'destructive', onPress: signOut }])} />
    </ScrollView>
  )
}

// a buyer account opens a shop: POST /seller/apply returns a new token with the SELLER role
export function RegisterShopScreen() {
  const { user, signIn, signOut } = useAuth()
  const [f, setF] = useState({ name: '', pickupAddress: '', pickupPhone: user?.phone || '', province: null })
  const [busy, setBusy] = useState(false)
  const set = (k) => (v) => setF(x => ({ ...x, [k]: v }))
  async function submit() {
    setBusy(true)
    try {
      const res = await post('/seller/apply', f)
      await signIn({ ...res, refreshToken: res.refreshToken ?? undefined })
    } catch (e) {
      Alert.alert('Không đăng ký được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  return (
    <ScrollView style={{ flex: 1, backgroundColor: 'white' }} contentContainerStyle={{ padding: 20, paddingTop: 64 }} keyboardShouldPersistTaps="handled">
      <Text style={{ fontSize: 22, fontWeight: '600', color: C.primary }}>Đăng ký bán hàng</Text>
      <Text style={[styles.muted, { marginBottom: 20 }]}>Xin chào {user?.fullName}, tài khoản của bạn chưa có shop. Điền thông tin để mở shop trên Shoppi.</Text>
      <Field label="Tên shop" value={f.name} onChangeText={set('name')} />
      <Field label="Địa chỉ lấy hàng" value={f.pickupAddress} onChangeText={set('pickupAddress')} />
      <Select label="Tỉnh/Thành phố" value={f.province} options={PROVINCES} onChange={set('province')} />
      <Field label="Số điện thoại" value={f.pickupPhone} onChangeText={set('pickupPhone')} keyboardType="phone-pad" />
      <Button title={busy ? 'Đang gửi...' : 'Mở shop'} onPress={submit} disabled={busy || !f.name || !f.province} />
      <Button title="Đăng nhập tài khoản khác" kind="ghost" onPress={signOut} style={{ marginTop: 12 }} />
    </ScrollView>
  )
}
