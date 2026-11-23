// Shoppi Admin app screens. Same API as the web console (frontend/src/views/admin): every action checks the
// staff permission on the backend too (SecurityConfig), the app only hides what the account may not use.
import React, { useState } from 'react'
import { Alert, FlatList, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native'
import { get, post, patch, errorText } from '../api'
import { useAuth } from '../auth'
import { formatDateTime, formatVnd, compact, ORDER_STATUS } from '../format'
import { Badge, BottomBar, Button, C, Empty, Field, Img, Loading, Row, Select, styles, useLoad } from '../ui'
import { TICKET_CATEGORIES, TICKET_STATUS_STAFF, TICKET_PRIORITY } from '../support'
import DisputeThread from '../common/DisputeThread'
import {
  Chips, DISPUTE_DECISION, History, Info, PAYMENT_METHOD, PAYMENT_STATUS, PRODUCT_STATUS, PagedList, REFUND_SOURCE, REFUND_STATUS,
  RETURN_REASONS, RETURN_STATUS, ROLE, SHOP_STATUS, STATUS_COLOR, USER_STATUS, useAdmin, usePaged, useReason
} from './common'

// runs an action, shows the error, and optionally a confirmation message
async function act(setBusy, fn, done) {
  setBusy(true)
  try {
    await fn()
    if (done) Alert.alert(done)
    return true
  } catch (e) {
    Alert.alert('Không thực hiện được', errorText(e))
    return false
  } finally {
    setBusy(false)
  }
}
const confirm = (title, message, onOk, okText = 'Đồng ý') => Alert.alert(title, message, [{ text: 'Hủy', style: 'cancel' }, { text: okText, onPress: onOk }])
const Status = ({ value, labels }) => <Badge text={labels[value] || value} color={STATUS_COLOR[value] || C.muted} />

// ==================== dashboard ====================

const pad = (n) => String(n).padStart(2, '0')
const day = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

function Kpi({ label, value, prev, money }) {
  const change = prev ? Math.round(((value - prev) / prev) * 100) : null
  return (
    <View style={{ width: '50%', padding: 4 }}>
      <View style={{ backgroundColor: 'white', padding: 12, borderRadius: 4 }}>
        <Text style={styles.muted}>{label}</Text>
        <Text style={{ fontSize: 18, fontWeight: '700', marginTop: 4 }}>{money ? formatVnd(value) : Number(value || 0).toLocaleString('vi-VN')}</Text>
        {change !== null ? <Text style={{ fontSize: 12, color: change >= 0 ? C.green : C.danger }}>{change >= 0 ? '▲' : '▼'} {Math.abs(change)}% so với kỳ trước</Text> : null}
      </View>
    </View>
  )
}

function Bars({ series }) {
  const max = Math.max(1, ...series.map(s => s.gmv || 0))
  return (
    <View style={{ flexDirection: 'row', alignItems: 'flex-end', height: 90, gap: 2, marginTop: 8 }}>
      {series.map(s => <View key={s.key} style={{ flex: 1, height: Math.max(2, (s.gmv / max) * 90), backgroundColor: C.primary, opacity: 0.8, borderTopLeftRadius: 2, borderTopRightRadius: 2 }} />)}
    </View>
  )
}

export function AdminDashboardScreen({ navigation }) {
  const { me, can } = useAdmin()
  const { data, error, reload, refreshing, refresh } = useLoad(async () => {
    const [stats, overview] = await Promise.all([
      get('/admin/stats'),
      can('REPORTS') ? get('/admin/analytics/overview', { from: day(new Date(Date.now() - 29 * 86400000)), to: day(new Date()), granularity: 'day' }).catch(() => null) : null
    ])
    return { stats, overview }
  }, [me?.id])
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const { stats: s, overview: o } = data
  const tasks = [
    ['Shop chờ duyệt', s.pendingShops, 'SHOPS', () => navigation.navigate('AdminShops', { status: 'PENDING' })],
    ['Sản phẩm chờ duyệt', s.pendingProducts, 'PRODUCTS', () => navigation.navigate('AdminProducts', { status: 'PENDING' })],
    ['Sản phẩm bị báo cáo', s.openReports, 'PRODUCTS', () => navigation.navigate('AdminProducts', { status: 'REPORTED' })],
    ['Khiếu nại đang mở', s.openDisputes, 'ORDERS', () => navigation.navigate('AdminDisputes')],
    ['Hoàn tiền chờ xử lý', s.pendingRefunds, 'FINANCE', () => navigation.navigate('AdminRefunds')],
    ['Tài khoản đang khóa', s.lockedUsers, 'USERS', () => navigation.navigate('AdminTabs', { screen: 'AdminUsers', params: { status: 'LOCKED' } })]
  ].filter(t => can(t[2]))
  return (
    <ScrollView style={styles.page} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <View style={{ backgroundColor: C.primary, padding: 16, paddingTop: 48 }}>
        <Text style={{ color: 'white', fontSize: 20, fontWeight: '700' }}>Shoppi Admin</Text>
        <Text style={{ color: 'white', opacity: 0.9 }}>Xin chào {me?.fullName}{me?.superAdmin ? ' · Quản trị viên cấp cao' : ''}</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Việc cần xử lý</Text>
        {tasks.map(([label, n, , go]) => (
          <Pressable key={label} onPress={go} style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderTopWidth: 1, borderTopColor: C.border }}>
            <Text>{label}</Text>
            <Text style={{ color: n ? C.primary : C.faint, fontWeight: '600' }}>{n || 0} ›</Text>
          </Pressable>
        ))}
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', padding: 4 }}>
        <Kpi label="Tổng doanh thu (GMV)" value={s.totalRevenue} money />
        <Kpi label="Tổng đơn hàng" value={s.totalOrders} />
        <Kpi label="Người mua" value={s.buyers} />
        <Kpi label="Người bán" value={s.sellers} />
      </View>
      {o ? (
        <>
          <View style={styles.card}>
            <Text style={styles.h2}>30 ngày qua</Text>
            <Bars series={o.series || []} />
            <Row style={{ justifyContent: 'space-between', marginTop: 4 }}>
              <Text style={[styles.muted, { fontSize: 11 }]}>{o.series?.[0]?.label}</Text>
              <Text style={[styles.muted, { fontSize: 11 }]}>{o.series?.[o.series.length - 1]?.label}</Text>
            </Row>
          </View>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', padding: 4 }}>
            <Kpi label="GMV" value={o.summary.gmv} prev={o.previous.gmv} money />
            <Kpi label="Đơn hàng" value={o.summary.orders} prev={o.previous.orders} />
            <Kpi label="Hoa hồng sàn" value={o.summary.commission} prev={o.previous.commission} money />
            <Kpi label="Người dùng mới" value={o.summary.newUsers} prev={o.previous.newUsers} />
          </View>
          {o.categories?.length ? (
            <View style={styles.card}>
              <Text style={styles.h2}>Ngành hàng bán chạy</Text>
              {o.categories.map(c => (
                <View key={c.name} style={{ paddingVertical: 5 }}>
                  <Row style={{ justifyContent: 'space-between' }}><Text>{c.name}</Text><Text style={styles.muted}>{formatVnd(c.revenue)} · {c.share}%</Text></Row>
                  <View style={{ height: 4, backgroundColor: C.bg, marginTop: 3 }}><View style={{ height: 4, width: `${Math.min(100, c.share)}%`, backgroundColor: C.primary }} /></View>
                </View>
              ))}
            </View>
          ) : null}
        </>
      ) : null}
    </ScrollView>
  )
}

// ==================== moderation hub ====================

export function AdminModerationScreen({ navigation }) {
  const { can } = useAdmin()
  const { data: s, error, reload, refreshing, refresh } = useLoad(() => get('/admin/stats'))
  if (!s) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const items = [
    ['🏪', 'Duyệt & quản lý shop', s.pendingShops, 'SHOPS', 'AdminShops'],
    ['🪪', 'Xác minh shop', null, 'SHOPS', 'AdminVerifications'],
    ['🏷', 'Kiểm duyệt sản phẩm', s.pendingProducts + s.openReports, 'PRODUCTS', 'AdminProducts'],
    ['⚖️', 'Khiếu nại trả hàng', s.openDisputes, 'ORDERS', 'AdminDisputes'],
    ['💸', 'Hoàn tiền', s.pendingRefunds, 'FINANCE', 'AdminRefunds'],
    ['🎧', 'Yêu cầu hỗ trợ', null, 'SUPPORT', 'AdminTickets']
  ].filter(x => can(x[3]))
  return (
    <ScrollView style={styles.page} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      {items.length ? items.map(([icon, label, n, , screen]) => (
        <Pressable key={screen} onPress={() => navigation.navigate(screen)} style={[styles.card, { flexDirection: 'row', alignItems: 'center', marginBottom: 1 }]}>
          <Text style={{ fontSize: 22, width: 36 }}>{icon}</Text>
          <Text style={{ flex: 1, fontSize: 15 }}>{label}</Text>
          {n ? <Text style={{ backgroundColor: C.primary, color: 'white', borderRadius: 10, paddingHorizontal: 7, fontSize: 12, overflow: 'hidden', marginRight: 6 }}>{n}</Text> : null}
          <Text style={{ color: C.faint }}>›</Text>
        </Pressable>
      )) : <Empty text="Tài khoản của bạn chưa được giao quyền kiểm duyệt." />}
    </ScrollView>
  )
}

// ==================== shops ====================

export function AdminShopsScreen({ navigation, route }) {
  const [status, setStatus] = useState(route.params?.status || 'PENDING')
  const [q, setQ] = useState('')
  const [search, setSearch] = useState('')
  const counts = useLoad(() => get('/admin/shops/counts'))
  const list = usePaged('/admin/shops', { status, q: search || undefined })
  const [busy, setBusy] = useState(false)
  const [dialog, ask] = useReason()
  const c = counts.data || {}
  const reload = () => { list.reload(); counts.reload() }

  async function reason(shop, kind) {
    const presets = kind === 'reject' ? ['Thông tin shop chưa đầy đủ', 'Tên shop vi phạm quy định', 'Ảnh/logo không phù hợp']
      : ['Bán hàng giả, hàng nhái', 'Nhiều khiếu nại chưa xử lý', 'Gian lận đơn hàng']
    const r = await ask({ title: kind === 'reject' ? `Từ chối shop ${shop.name}` : `Đình chỉ shop ${shop.name}`, presets, danger: true, confirm: kind === 'reject' ? 'Từ chối' : 'Đình chỉ' })
    if (r) act(setBusy, () => post(`/admin/shops/${shop.id}/${kind}`, { reason: r }), kind === 'reject' ? 'Đã từ chối shop' : 'Đã đình chỉ shop').then(ok => ok && reload())
  }

  return (
    <View style={styles.page}>
      {dialog}
      <Chips value={status} onChange={setStatus} items={[
        { key: 'PENDING', label: 'Chờ duyệt', count: c.PENDING }, { key: 'APPROVED', label: 'Đang hoạt động', count: c.APPROVED },
        { key: 'SUSPENDED', label: 'Đình chỉ', count: c.SUSPENDED }, { key: 'REJECTED', label: 'Từ chối', count: c.REJECTED }, { key: 'ALL', label: 'Tất cả' }]} />
      <View style={{ backgroundColor: 'white', paddingHorizontal: 10, paddingTop: 8 }}>
        <Field value={q} onChangeText={setQ} placeholder="Tìm tên shop, email người bán..." returnKeyType="search" onSubmitEditing={() => setSearch(q.trim())} />
      </View>
      <PagedList list={list} empty="Không có shop nào" renderItem={({ item: s }) => (
        <View style={[styles.card, { marginBottom: 6 }]}>
          <Row>
            <Img uri={s.logoUrl} style={{ width: 44, height: 44, borderRadius: 22, marginRight: 10 }} />
            <View style={{ flex: 1 }}>
              <Text style={{ fontWeight: '600' }}>{s.name}{s.mall ? <Text style={{ color: C.danger }}>  Mall</Text> : null}{s.verified ? <Text style={{ color: C.green }}>  ✓</Text> : null}</Text>
              <Text style={styles.muted}>{s.seller?.fullName} · {s.seller?.email}</Text>
              <Text style={styles.muted}>{s.province || 'Chưa có tỉnh'} · {s.products} sản phẩm · {formatDateTime(s.createdAt)}</Text>
            </View>
            <Status value={s.status} labels={SHOP_STATUS} />
          </Row>
          {s.statusReason ? <Text style={{ color: C.danger, marginTop: 6, fontSize: 13 }}>Lý do: {s.statusReason}</Text> : null}
          {s.missing?.length ? <Text style={{ color: '#f69113', marginTop: 6, fontSize: 13 }}>Còn thiếu: {s.missing.join(', ')}</Text> : null}
          <Row style={{ gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
            {s.status === 'PENDING' ? <>
              <Button small title="Duyệt" disabled={busy} onPress={() => act(setBusy, () => post(`/admin/shops/${s.id}/approve`), 'Đã duyệt shop').then(ok => ok && reload())} />
              <Button small kind="danger" title="Từ chối" disabled={busy} onPress={() => reason(s, 'reject')} />
            </> : null}
            {s.status === 'APPROVED' ? <>
              <Button small kind="danger" title="Đình chỉ" disabled={busy} onPress={() => reason(s, 'suspend')} />
              <Button small kind="ghost" title={s.mall ? 'Bỏ Shoppi Mall' : 'Đưa vào Shoppi Mall'} disabled={busy}
                      onPress={() => act(setBusy, () => post(`/admin/shops/${s.id}/mall`, { mall: !s.mall })).then(ok => ok && reload())} />
            </> : null}
            {s.status === 'SUSPENDED' ? <Button small title="Mở lại shop" disabled={busy} onPress={() => confirm('Mở lại shop?', s.name, () => act(setBusy, () => post(`/admin/shops/${s.id}/reinstate`), 'Đã mở lại shop').then(ok => ok && reload()))} /> : null}
            {s.seller?.id ? <Button small kind="ghost" title="Người bán" onPress={() => navigation.navigate('AdminUser', { id: s.seller.id })} /> : null}
          </Row>
        </View>
      )} />
    </View>
  )
}

export function AdminVerificationsScreen() {
  const [pending, setPending] = useState(true)
  const { data, error, reload, refreshing, refresh } = useLoad(() => get('/admin/verifications', { pending }), [pending])
  const [busy, setBusy] = useState(false)
  const [dialog, ask] = useReason()
  return (
    <View style={styles.page}>
      {dialog}
      <Chips value={pending} onChange={setPending} items={[{ key: true, label: 'Chờ xác minh' }, { key: false, label: 'Tất cả' }]} />
      {!data ? (error ? <Empty error={error} onRetry={reload} /> : <Loading />) : (
        <FlatList data={data} keyExtractor={v => String(v.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                  ListEmptyComponent={<Empty text="Không có hồ sơ xác minh" />}
                  renderItem={({ item: v }) => (
                    <View style={[styles.card, { marginBottom: 6 }]}>
                      <Row style={{ justifyContent: 'space-between' }}><Text style={{ fontWeight: '600' }}>{v.shopName}</Text><Status value={v.status} labels={{ PENDING: 'Chờ xác minh', APPROVED: 'Đã xác minh', REJECTED: 'Từ chối' }} /></Row>
                      <Info label="Email">{v.sellerEmail}</Info>
                      <Info label="Loại hình">{{ INDIVIDUAL: 'Cá nhân', HOUSEHOLD: 'Hộ kinh doanh', COMPANY: 'Công ty' }[v.businessType] || v.businessType}</Info>
                      <Info label="Tên pháp lý">{v.legalName}</Info>
                      <Info label="CCCD">{v.idNumber}</Info>
                      <Info label="Mã số thuế">{v.taxCode}</Info>
                      <Info label="Giấy phép">{v.licenseNumber}</Info>
                      <Info label="Địa chỉ">{v.businessAddress}</Info>
                      <Info label="Gửi lúc">{formatDateTime(v.submittedAt)}</Info>
                      {v.rejectReason ? <Text style={{ color: C.danger }}>Lý do từ chối: {v.rejectReason}</Text> : null}
                      <Row style={{ gap: 6, flexWrap: 'wrap', marginTop: 6 }}>{(v.documentUrls || []).map(u => <Img key={u} uri={u} style={{ width: 90, height: 90 }} />)}</Row>
                      {v.status === 'PENDING' ? (
                        <Row style={{ gap: 8, marginTop: 10 }}>
                          <Button small title="Xác minh" disabled={busy} onPress={() => act(setBusy, () => post(`/admin/verifications/${v.id}/approve`, {}), 'Đã xác minh shop').then(ok => ok && reload())} />
                          <Button small kind="danger" title="Từ chối" disabled={busy} onPress={async () => {
                            const r = await ask({ title: 'Từ chối hồ sơ xác minh', presets: ['Ảnh CCCD bị mờ', 'Thông tin không khớp giấy tờ', 'Thiếu giấy phép kinh doanh'], danger: true, confirm: 'Từ chối' })
                            if (r) act(setBusy, () => post(`/admin/verifications/${v.id}/reject`, { reason: r }), 'Đã từ chối hồ sơ').then(ok => ok && reload())
                          }} />
                        </Row>
                      ) : null}
                    </View>
                  )} />
      )}
    </View>
  )
}

// ==================== products ====================

export function AdminProductsScreen({ navigation, route }) {
  const [status, setStatus] = useState(route.params?.status || 'PENDING')
  const [q, setQ] = useState('')
  const [search, setSearch] = useState('')
  const [picked, setPicked] = useState([])
  const [busy, setBusy] = useState(false)
  const counts = useLoad(() => get('/admin/products/counts'))
  const list = usePaged('/admin/products', { status, q: search || undefined })
  const c = counts.data || {}
  const toggle = (id) => setPicked(p => (p.includes(id) ? p.filter(x => x !== id) : [...p, id]))
  const bulkApprove = () => confirm(`Duyệt ${picked.length} sản phẩm?`, 'Các sản phẩm sẽ được hiển thị trên Shoppi.', () =>
    act(setBusy, async () => {
      const r = await post('/admin/products/bulk-approve', { ids: picked })
      Alert.alert('Đã duyệt', `${r.approved ?? picked.length} sản phẩm${r.skipped ? `, bỏ qua ${r.skipped}` : ''}`)
      setPicked([])
      list.reload()
      counts.reload()
    }))
  return (
    <View style={styles.page}>
      <Chips value={status} onChange={(s) => { setStatus(s); setPicked([]) }} items={[
        { key: 'PENDING', label: 'Chờ duyệt', count: c.PENDING }, { key: 'REPORTED', label: 'Bị báo cáo', count: c.openReports },
        { key: 'APPROVED', label: 'Đã duyệt', count: c.APPROVED }, { key: 'REJECTED', label: 'Từ chối', count: c.REJECTED },
        { key: 'REMOVED', label: 'Đã gỡ', count: c.REMOVED }, { key: 'ALL', label: 'Tất cả' }]} />
      <View style={{ backgroundColor: 'white', paddingHorizontal: 10, paddingTop: 8 }}>
        <Field value={q} onChangeText={setQ} placeholder="Tìm tên sản phẩm, shop..." returnKeyType="search" onSubmitEditing={() => setSearch(q.trim())} />
      </View>
      <PagedList list={list} empty="Không có sản phẩm" renderItem={({ item: p }) => (
        <Pressable onPress={() => navigation.navigate('AdminProduct', { id: p.id })} onLongPress={() => status === 'PENDING' && toggle(p.id)}
                   style={[styles.card, { flexDirection: 'row', marginBottom: 6 }, picked.includes(p.id) && { backgroundColor: C.primarySoft }]}>
          {status === 'PENDING' ? (
            <Pressable onPress={() => toggle(p.id)} hitSlop={10} style={{ justifyContent: 'center', marginRight: 8 }}>
              <Text style={{ fontSize: 20, color: picked.includes(p.id) ? C.primary : C.faint }}>{picked.includes(p.id) ? '☑' : '☐'}</Text>
            </Pressable>
          ) : null}
          <Img uri={p.thumbnailUrl} style={{ width: 64, height: 64, marginRight: 10 }} />
          <View style={{ flex: 1 }}>
            <Text numberOfLines={2}>{p.name}</Text>
            <Text style={styles.muted}>{p.shop?.name} · {p.category}</Text>
            <Row style={{ gap: 6, marginTop: 4, flexWrap: 'wrap' }}>
              <Text style={{ color: C.primary }}>{formatVnd(p.price)}</Text>
              <Status value={p.status} labels={PRODUCT_STATUS} />
              {p.openReports ? <Badge text={`${p.openReports} báo cáo`} color={C.danger} /> : null}
              {p.riskScore >= 50 ? <Badge text={`Rủi ro ${p.riskScore}`} color="#f69113" /> : null}
            </Row>
          </View>
        </Pressable>
      )} />
      {picked.length ? (
        <BottomBar>
          <Text style={{ flex: 1 }}>Đã chọn {picked.length}</Text>
          <Button kind="ghost" title="Bỏ chọn" onPress={() => setPicked([])} />
          <Button title={`Duyệt ${picked.length}`} disabled={busy} onPress={bulkApprove} />
        </BottomBar>
      ) : status === 'PENDING' && list.items?.length ? (
        <BottomBar><Button kind="ghost" style={{ flex: 1 }} title="Chọn tất cả để duyệt hàng loạt" onPress={() => setPicked(list.items.map(p => p.id))} /></BottomBar>
      ) : null}
    </View>
  )
}

export function AdminProductScreen({ route, navigation }) {
  const { id } = route.params
  const { data: p, error, reload } = useLoad(() => get(`/admin/products/${id}`), [id])
  const counts = useLoad(() => get('/admin/products/counts'))
  const [busy, setBusy] = useState(false)
  const [violation, setViolation] = useState(null)
  const [dialog, ask] = useReason()
  if (!p) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const violations = counts.data?.violations || []
  const run = (path, body, done) => act(setBusy, () => post(`/admin/products/${id}/${path}`, body), done).then(ok => ok && reload())
  return (
    <View style={styles.page}>
      {dialog}
      <ScrollView>
        <ScrollView horizontal pagingEnabled style={{ backgroundColor: 'white' }}>
          {(p.imageUrls?.length ? p.imageUrls : [p.thumbnailUrl]).map(u => <Img key={u} uri={u} style={{ width: 360, height: 300 }} />)}
        </ScrollView>
        <View style={styles.card}>
          <Text style={{ fontSize: 16 }}>{p.name}</Text>
          <Row style={{ gap: 8, marginTop: 6 }}><Text style={{ color: C.primary, fontSize: 18 }}>{formatVnd(p.price)}</Text><Status value={p.status} labels={PRODUCT_STATUS} /></Row>
          <Info label="Shop">{p.shop?.name}</Info>
          <Info label="Danh mục">{p.category}</Info>
          <Info label="Tồn kho">{p.stock}</Info>
          <Info label="Đã bán">{p.soldCount}</Info>
          <Info label="Lý do">{p.reason}</Info>
          <Info label="Vi phạm">{p.violationLabel}</Info>
          {p.flags?.length ? <Info label="Cảnh báo AI">{p.flags.join(', ')}</Info> : null}
        </View>
        {p.reports?.length ? (
          <View style={styles.card}>
            <Text style={styles.h2}>Báo cáo từ người dùng ({p.reports.length})</Text>
            {p.reports.map(r => (
              <View key={r.id} style={{ paddingVertical: 6, borderTopWidth: 1, borderTopColor: C.border }}>
                <Text style={{ fontWeight: '600' }}>{r.reasonLabel || r.reason}</Text>
                {r.detail ? <Text>{r.detail}</Text> : null}
                <Text style={[styles.muted, { fontSize: 12 }]}>{r.reporter} · {formatDateTime(r.createdAt)}</Text>
              </View>
            ))}
          </View>
        ) : null}
        <View style={styles.card}>
          <Text style={styles.h2}>Mô tả</Text>
          <Text style={{ lineHeight: 20 }}>{p.description}</Text>
        </View>
        <History items={p.history} />
        {p.status !== 'REMOVED' && violations.length ? (
          <View style={styles.card}>
            <Text style={styles.h2}>Gỡ sản phẩm vi phạm</Text>
            <Select label="Loại vi phạm" value={violation} onChange={setViolation} options={violations.map(v => ({ value: v.key, label: v.label }))} />
            <Button kind="danger" title="Gỡ sản phẩm" disabled={busy || !violation} onPress={async () => {
              const r = await ask({ title: 'Gỡ sản phẩm', message: 'Người bán sẽ nhận thông báo kèm lý do.', danger: true, confirm: 'Gỡ' })
              if (r) run('remove', { violation, reason: r }, 'Đã gỡ sản phẩm')
            }} />
          </View>
        ) : null}
        <View style={{ height: 80 }} />
      </ScrollView>
      <BottomBar>
        {p.status === 'PENDING' || p.status === 'REJECTED' ? <Button style={{ flex: 1 }} title="Duyệt" disabled={busy} onPress={() => run('approve', {}, 'Đã duyệt sản phẩm')} /> : null}
        {p.status === 'PENDING' ? <Button style={{ flex: 1 }} kind="danger" title="Từ chối" disabled={busy} onPress={async () => {
          const r = await ask({ title: 'Từ chối sản phẩm', presets: ['Ảnh không rõ ràng', 'Mô tả không đúng sản phẩm', 'Sai danh mục', 'Giá không hợp lý'], danger: true, confirm: 'Từ chối' })
          if (r) run('reject', { reason: r }, 'Đã từ chối sản phẩm')
        }} /> : null}
        {p.status === 'REMOVED' ? <Button style={{ flex: 1 }} title="Khôi phục" disabled={busy} onPress={() => run('restore', {}, 'Đã khôi phục sản phẩm')} /> : null}
        {p.openReports ? <Button style={{ flex: 1 }} kind="ghost" title="Bỏ qua báo cáo" disabled={busy} onPress={() => run('dismiss-reports', {}, 'Đã đóng các báo cáo')} /> : null}
        <Button kind="ghost" title="Người bán" onPress={() => navigation.navigate('AdminUser', { id: p.shop?.sellerId })} />
      </BottomBar>
    </View>
  )
}

// ==================== orders ====================

const ORDER_FILTERS = [{ key: 'ALL', label: 'Tất cả' }, { key: 'LATE', label: 'Trễ hạn' }, ...Object.entries(ORDER_STATUS).map(([key, label]) => ({ key, label }))]

export function AdminOrdersScreen({ navigation }) {
  const [status, setStatus] = useState('ALL')
  const [q, setQ] = useState('')
  const [search, setSearch] = useState('')
  const summary = useLoad(() => get('/admin/orders/summary'))
  const list = usePaged('/admin/orders', { status, q: search || undefined })
  const s = summary.data
  return (
    <View style={styles.page}>
      {s ? (
        <Row style={{ backgroundColor: 'white', padding: 10, justifyContent: 'space-around' }}>
          <View style={{ alignItems: 'center' }}><Text style={{ fontWeight: '700' }}>{compact(s.orders)}</Text><Text style={[styles.muted, { fontSize: 11 }]}>Đơn 30 ngày</Text></View>
          <View style={{ alignItems: 'center' }}><Text style={{ fontWeight: '700' }}>{formatVnd(s.gmv)}</Text><Text style={[styles.muted, { fontSize: 11 }]}>GMV</Text></View>
          <View style={{ alignItems: 'center' }}><Text style={{ fontWeight: '700', color: s.late ? C.danger : C.text }}>{s.late}</Text><Text style={[styles.muted, { fontSize: 11 }]}>Trễ hạn</Text></View>
          <View style={{ alignItems: 'center' }}><Text style={{ fontWeight: '700' }}>{s.cancelRate}%</Text><Text style={[styles.muted, { fontSize: 11 }]}>Tỉ lệ hủy</Text></View>
        </Row>
      ) : null}
      <Chips value={status} onChange={setStatus} items={ORDER_FILTERS.map(f => ({ ...f, count: s?.byStatus?.[f.key] }))} />
      <View style={{ backgroundColor: 'white', paddingHorizontal: 10, paddingTop: 8 }}>
        <Field value={q} onChangeText={setQ} placeholder="Mã đơn, tên / email người mua, shop..." returnKeyType="search" autoCapitalize="characters" onSubmitEditing={() => setSearch(q.trim())} />
      </View>
      <PagedList list={list} empty="Không có đơn hàng" renderItem={({ item: o }) => (
        <Pressable onPress={() => navigation.navigate('AdminOrder', { id: o.id })} style={[styles.card, { marginBottom: 6 }]}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Text style={{ fontWeight: '600' }}>{o.code}</Text>
            <Text style={{ color: o.status === 'CANCELLED' ? C.muted : C.primary }}>{ORDER_STATUS[o.status] || o.status}{o.late ? ' · trễ' : ''}</Text>
          </Row>
          <Text style={styles.muted}>{o.buyerName} → {o.shopName}</Text>
          <Row style={{ justifyContent: 'space-between', marginTop: 4 }}>
            <Text style={styles.muted}>{formatDateTime(o.createdAt)} · {PAYMENT_METHOD[o.paymentMethod] || o.paymentMethod}</Text>
            <Text style={{ fontWeight: '600' }}>{formatVnd(o.total)}</Text>
          </Row>
        </Pressable>
      )} />
    </View>
  )
}

export function AdminOrderScreen({ route, navigation }) {
  const { id } = route.params
  const { can } = useAdmin()
  const { data, error, reload, refreshing, refresh } = useLoad(() => get(`/admin/orders/${id}`), [id])
  const [busy, setBusy] = useState(false)
  const [dialog, ask] = useReason()
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const o = data.order
  return (
    <View style={styles.page}>
      {dialog}
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        <View style={styles.card}>
          <Row style={{ justifyContent: 'space-between' }}><Text style={styles.h2}>{o.code}</Text><Text style={{ color: C.primary }}>{ORDER_STATUS[o.status]}</Text></Row>
          {data.late ? <Text style={{ color: C.danger, marginBottom: 6 }}>⚠ {data.late}</Text> : null}
          <Info label="Đặt lúc">{formatDateTime(o.createdAt)}</Info>
          <Info label="Người mua">{data.buyer?.fullName} · {data.buyer?.phone}</Info>
          <Info label="Shop">{data.shop?.name}</Info>
          <Info label="Giao tới">{o.shippingAddress}</Info>
          <Info label="Vận chuyển">{o.carrierName} · {o.shippingMethodName}</Info>
          <Info label="Mã vận đơn">{o.trackingNumber}</Info>
          <Info label="Thanh toán">{PAYMENT_METHOD[data.payment?.method]} · {PAYMENT_STATUS[data.payment?.status]}</Info>
          <Info label="Lý do hủy">{o.cancelReason}</Info>
        </View>
        <View style={styles.card}>
          {o.items.map(it => (
            <Row key={it.id} style={{ marginBottom: 8 }}>
              <Img uri={it.thumbnailUrl} style={{ width: 50, height: 50, marginRight: 10 }} />
              <View style={{ flex: 1 }}>
                <Text numberOfLines={2}>{it.productName}</Text>
                {it.variantName ? <Text style={styles.muted}>{it.variantName}</Text> : null}
              </View>
              <Text>{formatVnd(it.priceAtPurchase)} × {it.quantity}</Text>
            </Row>
          ))}
          <View style={styles.divider} />
          <Info label="Tiền hàng">{formatVnd(o.subtotal)}</Info>
          <Info label="Phí vận chuyển">{formatVnd(o.shippingFee - (o.shippingDiscount || 0))}</Info>
          {o.discount ? <Info label="Giảm giá">-{formatVnd(o.discount)}</Info> : null}
          <Info label="Tổng thanh toán" bold>{formatVnd(o.total)}</Info>
        </View>
        {o.events?.length ? (
          <View style={styles.card}>
            <Text style={styles.h2}>Hành trình</Text>
            {o.events.map((e, i) => <Text key={i} style={[styles.muted, { marginBottom: 4 }]}>{formatDateTime(e.time)} · {e.title}{e.location ? ` (${e.location})` : ''}</Text>)}
          </View>
        ) : null}
        <History items={data.history} />
        {data.refunds?.length ? (
          <View style={styles.card}>
            <Text style={styles.h2}>Hoàn tiền</Text>
            {data.refunds.map(r => <Text key={r.id}>{formatVnd(r.amount)} · {REFUND_SOURCE[r.source] || r.source} · {REFUND_STATUS[r.status] || r.status}</Text>)}
          </View>
        ) : null}
        <View style={{ height: 80 }} />
      </ScrollView>
      <BottomBar>
        {data.canCancel ? <Button style={{ flex: 1 }} kind="danger" title="Hủy đơn" disabled={busy} onPress={async () => {
          const r = await ask({ title: `Hủy đơn ${o.code}`, message: 'Người mua được hoàn tiền nếu đã thanh toán.', presets: ['Shop không xử lý đơn quá hạn', 'Nghi ngờ gian lận', 'Theo yêu cầu của người mua'], danger: true, confirm: 'Hủy đơn' })
          if (r) act(setBusy, () => post(`/admin/orders/${id}/cancel`, { reason: r }), 'Đã hủy đơn').then(ok => ok && reload())
        }} /> : null}
        {can('FINANCE') && data.refundable > 0 ? <Button style={{ flex: 1 }} kind="ghost" title="Hoàn tiền thủ công" disabled={busy} onPress={async () => {
          const amount = await ask({ title: 'Số tiền hoàn', message: `Tối đa ${formatVnd(data.refundable)}`, keyboardType: 'number-pad', placeholder: 'VD: 50000', confirm: 'Tiếp' })
          if (!amount) return
          const note = await ask({ title: 'Ghi chú hoàn tiền', presets: ['Hỗ trợ khách hàng', 'Bồi thường giao trễ'], confirm: 'Hoàn tiền' })
          if (note) act(setBusy, () => post(`/admin/orders/${id}/refund`, { amount: Number(amount.replace(/\D/g, '')), note }), 'Đã tạo giao dịch hoàn tiền').then(ok => ok && reload())
        }} /> : null}
        <Button kind="ghost" title="Người mua" onPress={() => navigation.navigate('AdminUser', { id: data.buyer?.id })} />
      </BottomBar>
    </View>
  )
}

// ==================== users ====================

export function AdminUsersScreen({ navigation, route }) {
  const [status, setStatus] = useState(route.params?.status || 'ALL')
  const [role, setRole] = useState('')
  const [q, setQ] = useState('')
  const [search, setSearch] = useState('')
  const counts = useLoad(() => get('/admin/users/counts'))
  const list = usePaged('/admin/users', { status, role: role || undefined, q: search || undefined })
  const c = counts.data || {}
  React.useEffect(() => { if (route.params?.status) setStatus(route.params.status) }, [route.params?.status])
  return (
    <View style={styles.page}>
      <Chips value={role} onChange={setRole} items={[{ key: '', label: 'Tất cả', count: c.all }, { key: 'BUYER', label: 'Người mua', count: c.buyers },
        { key: 'SELLER', label: 'Người bán', count: c.sellers }, { key: 'ADMIN', label: 'Quản trị', count: c.admins }]} />
      <Chips value={status} onChange={setStatus} items={[{ key: 'ALL', label: 'Mọi trạng thái' }, { key: 'ACTIVE', label: 'Hoạt động' },
        { key: 'LOCKED', label: 'Đang khóa', count: c.locked }, { key: 'DELETED', label: 'Đã xóa', count: c.deleted }]} />
      <View style={{ backgroundColor: 'white', paddingHorizontal: 10, paddingTop: 8 }}>
        <Field value={q} onChangeText={setQ} placeholder="Tên, email, số điện thoại..." autoCapitalize="none" returnKeyType="search" onSubmitEditing={() => setSearch(q.trim())} />
      </View>
      <PagedList list={list} empty="Không có tài khoản" renderItem={({ item: u }) => (
        <Pressable onPress={() => navigation.navigate('AdminUser', { id: u.id })} style={[styles.card, { marginBottom: 6 }]}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Text style={{ fontWeight: '600', flex: 1 }} numberOfLines={1}>{u.fullName}</Text>
            <Status value={u.status} labels={USER_STATUS} />
          </Row>
          <Text style={styles.muted}>{u.email}{u.phone ? ` · ${u.phone}` : ''}</Text>
          <Text style={[styles.muted, { fontSize: 12 }]}>{ROLE[u.role]}{u.superAdmin ? ' cấp cao' : ''} · tham gia {formatDateTime(u.createdAt)}{u.lastLoginAt ? ` · đăng nhập ${formatDateTime(u.lastLoginAt)}` : ''}</Text>
        </Pressable>
      )} />
    </View>
  )
}

export function AdminUserScreen({ route, navigation }) {
  const { id } = route.params
  const { data: u, error, reload, refreshing, refresh } = useLoad(() => get(`/admin/users/${id}`), [id])
  const [busy, setBusy] = useState(false)
  const [dialog, ask] = useReason()
  if (!u) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const lock = async () => {
    const reason = await ask({ title: `Khóa tài khoản ${u.fullName}`, presets: ['Gian lận đơn hàng', 'Spam / quấy rối', 'Bán hàng cấm', 'Theo yêu cầu của chủ tài khoản'], danger: true, confirm: 'Tiếp' })
    if (!reason) return
    const go = (days) => act(setBusy, () => post(`/admin/users/${id}/lock`, { reason, days }), 'Đã khóa tài khoản').then(ok => ok && reload())
    Alert.alert('Thời hạn khóa', reason, [{ text: '7 ngày', onPress: () => go(7) }, { text: '30 ngày', onPress: () => go(30) }, { text: 'Đến khi mở khóa', style: 'destructive', onPress: () => go(null) }])
  }
  const PROVIDER = { GOOGLE: 'Google', FACEBOOK: 'Facebook', GITHUB: 'GitHub' }
  return (
    <View style={styles.page}>
      {dialog}
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        <View style={styles.card}>
          <Row style={{ justifyContent: 'space-between' }}><Text style={styles.h2}>{u.fullName}</Text><Status value={u.status} labels={USER_STATUS} /></Row>
          <Info label="Email">{u.email}</Info>
          <Info label="Điện thoại">{u.phone}</Info>
          <Info label="Vai trò">{ROLE[u.role]}{u.superAdmin ? ' cấp cao' : ''}</Info>
          <Info label="Đăng ký bằng">{PROVIDER[u.authProvider] || 'Email & mật khẩu'}</Info>
          <Info label="Tham gia">{formatDateTime(u.createdAt)}</Info>
          <Info label="Đăng nhập gần nhất">{formatDateTime(u.lastLoginAt)}</Info>
          {u.lockedReason ? <Info label="Lý do khóa">{u.lockedReason}{u.lockedUntil ? ` (đến ${formatDateTime(u.lockedUntil)})` : ''}</Info> : null}
        </View>
        {u.buyer ? (
          <View style={styles.card}>
            <Text style={styles.h2}>Mua hàng</Text>
            <Info label="Số đơn">{u.buyer.orders}</Info>
            <Info label="Đã chi tiêu">{formatVnd(u.buyer.spent)}</Info>
            <Info label="Đã hủy">{u.buyer.cancelled}</Info>
            {(u.buyer.recentOrders || []).map(o => (
              <Pressable key={o.id} onPress={() => navigation.navigate('AdminOrder', { id: o.id })} style={{ paddingVertical: 6, borderTopWidth: 1, borderTopColor: C.border }}>
                <Text>{o.code} · {ORDER_STATUS[o.status]} · {formatVnd(o.total)}</Text>
              </Pressable>
            ))}
          </View>
        ) : null}
        {u.shopDetail ? (
          <View style={styles.card}>
            <Text style={styles.h2}>Shop {u.shopDetail.name}</Text>
            <Info label="Trạng thái">{SHOP_STATUS[u.shopDetail.status]}</Info>
            <Info label="Sản phẩm">{u.shopDetail.activeProducts}/{u.shopDetail.products} đang bán</Info>
            <Info label="Đơn hàng">{u.shopDetail.orders} ({u.shopDetail.openOrders} đang xử lý)</Info>
            <Info label="Doanh thu">{formatVnd(u.shopDetail.revenue)}</Info>
          </View>
        ) : null}
        <History items={u.history} />
        <View style={{ height: 80 }} />
      </ScrollView>
      {u.status !== 'DELETED' && !u.superAdmin ? (
        <BottomBar>
          {u.status === 'LOCKED' ? <Button style={{ flex: 1 }} title="Mở khóa" disabled={busy}
                                           onPress={() => confirm('Mở khóa tài khoản?', u.email, () => act(setBusy, () => post(`/admin/users/${id}/unlock`), 'Đã mở khóa').then(ok => ok && reload()))} />
            : <Button style={{ flex: 1 }} kind="danger" title="Khóa tài khoản" disabled={busy} onPress={lock} />}
        </BottomBar>
      ) : null}
    </View>
  )
}

// ==================== disputes ====================

export function AdminDisputesScreen({ navigation }) {
  const [status, setStatus] = useState('OPEN')
  const counts = useLoad(() => get('/admin/disputes/counts'))
  const list = usePaged('/admin/disputes', { status })
  const c = counts.data || {}
  return (
    <View style={styles.page}>
      <Chips value={status} onChange={setStatus} items={[{ key: 'OPEN', label: 'Đang mở', count: c.OPEN }, { key: 'RESOLVED', label: 'Đã giải quyết', count: c.RESOLVED }, { key: 'ALL', label: 'Tất cả' }]} />
      <PagedList list={list} empty="Không có khiếu nại" renderItem={({ item: d }) => (
        <Pressable onPress={() => navigation.navigate('AdminDispute', { id: d.id })} style={[styles.card, { marginBottom: 6 }]}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Text style={{ fontWeight: '600' }}>{d.orderCode}</Text>
            {d.status === 'ESCALATED' ? <Text style={{ color: d.waitingHours >= 48 ? C.danger : '#f69113' }}>chờ {d.waitingHours ?? 0} giờ</Text>
              : <Text style={{ color: C.green }}>{DISPUTE_DECISION[d.decision] || RETURN_STATUS[d.status]}</Text>}
          </Row>
          <Text style={styles.muted}>{d.buyerName} ⟷ {d.shopName}</Text>
          <Row style={{ gap: 6, marginTop: 4, flexWrap: 'wrap' }}>
            <Text>{RETURN_REASONS[d.reason] || d.reason} · <Text style={{ fontWeight: '600' }}>{formatVnd(d.refundAmount)}</Text></Text>
            {d.riskLevel && d.riskLevel !== 'LOW' ? <Badge text={`AI rủi ro ${d.riskScore}`} color={C.danger} /> : null}
            {d.sellerOverdue ? <Badge text="Shop quá hạn giải trình" color={C.danger} /> : d.status === 'ESCALATED' && d.sellerAnswered ? <Badge text="Shop đã giải trình" color={C.green} /> : null}
          </Row>
        </Pressable>
      )} />
    </View>
  )
}

const DECISIONS = [
  { value: 'REFUND_FULL', label: 'Hoàn toàn bộ cho người mua' },
  { value: 'REFUND_PARTIAL', label: 'Hoàn một phần' },
  { value: 'REJECT', label: 'Không hoàn tiền (đứng về phía shop)' }
]

export function AdminDisputeScreen({ route }) {
  const { id } = route.params
  const { data: d, error, reload, refreshing, refresh } = useLoad(() => get(`/admin/disputes/${id}`), [id])
  const [decision, setDecision] = useState(null)
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  if (!d) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const rr = d.request || d
  const resolve = () => confirm(`${DISPUTE_DECISION[decision]} cho đơn ${d.orderCode}?`, 'Quyết định không thể thay đổi.', () =>
    act(setBusy, () => post(`/admin/disputes/${id}/resolve`, { decision, amount: decision === 'REFUND_PARTIAL' ? Number(amount) : null, note: note.trim() }), 'Đã giải quyết khiếu nại')
      .then(ok => ok && reload()))
  return (
    <ScrollView style={styles.page} keyboardShouldPersistTaps="handled" refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <View style={styles.card}>
        <Row style={{ justifyContent: 'space-between' }}><Text style={styles.h2}>{d.orderCode}</Text><Status value={d.status} labels={RETURN_STATUS} /></Row>
        <Info label="Người mua">{d.buyerName}</Info>
        <Info label="Shop">{d.shopName}</Info>
        <Info label="Lý do">{RETURN_REASONS[d.reason] || d.reason}</Info>
        <Info label="Yêu cầu">{d.type === 'RETURN_AND_REFUND' ? 'Trả hàng và hoàn tiền' : 'Chỉ hoàn tiền'}</Info>
        <Info label="Số tiền" bold>{formatVnd(d.refundAmount)}</Info>
        <Info label="Tổng đơn">{formatVnd(d.orderTotal ?? d.order?.total)}</Info>
        <Info label="Khiếu nại lúc">{formatDateTime(d.escalatedAt)}</Info>
        {d.shopDisputes != null ? <Info label="Lịch sử shop">{d.shopDisputes} tranh chấp</Info> : null}
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Nội dung</Text>
        <Info label="Người mua">{rr.description || rr.detail}</Info>
        <Info label="Người bán">{d.sellerNote}</Info>
        <Info label="Khiếu nại">{d.complaint}</Info>
        <Row style={{ gap: 6, flexWrap: 'wrap', marginTop: 6 }}>{(rr.evidenceUrls || []).map(u => <Img key={u} uri={u} style={{ width: 80, height: 80 }} />)}</Row>
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Trao đổi</Text>
        <DisputeThread returnId={id} side="admin" open={d.status === 'ESCALATED'} />
      </View>
      {d.status === 'ESCALATED' ? (
        <View style={styles.card}>
          <Text style={styles.h2}>Quyết định của Shoppi</Text>
          <Select label="Hướng giải quyết" value={decision} onChange={setDecision} options={DECISIONS} />
          {decision === 'REFUND_PARTIAL' ? <Field label={`Số tiền hoàn (nhỏ hơn ${formatVnd(d.refundAmount)})`} value={amount} onChangeText={v => setAmount(v.replace(/\D/g, ''))} keyboardType="number-pad" /> : null}
          <Field label="Kết luận gửi hai bên" value={note} onChangeText={setNote} multiline />
          <Button title="Giải quyết" disabled={busy || !decision || !note.trim() || (decision === 'REFUND_PARTIAL' && !(Number(amount) > 0))} onPress={resolve} />
        </View>
      ) : (
        <View style={[styles.card, { backgroundColor: '#eaf7ef' }]}>
          <Text>Đã giải quyết {formatDateTime(d.resolvedAt)}: <Text style={{ fontWeight: '600' }}>{DISPUTE_DECISION[d.decision] || RETURN_STATUS[d.status]}</Text></Text>
          {d.adminNote ? <Text style={styles.muted}>{d.adminNote}</Text> : null}
        </View>
      )}
    </ScrollView>
  )
}

// ==================== refunds ====================

export function AdminRefundsScreen({ navigation }) {
  const [status, setStatus] = useState('PENDING')
  const counts = useLoad(() => get('/admin/refunds/counts'))
  const list = usePaged('/admin/refunds', { status })
  const [busy, setBusy] = useState(false)
  const [dialog, ask] = useReason()
  const c = counts.data || {}
  const reload = () => { list.reload(); counts.reload() }
  return (
    <View style={styles.page}>
      {dialog}
      <Chips value={status} onChange={setStatus} items={[{ key: 'PENDING', label: 'Chờ chuyển khoản', count: c.PENDING },
        { key: 'COMPLETED', label: 'Đã hoàn', count: c.COMPLETED }, { key: 'FAILED', label: 'Thất bại', count: c.FAILED }, { key: 'ALL', label: 'Tất cả' }]} />
      <PagedList list={list} empty="Không có giao dịch hoàn tiền" renderItem={({ item: r }) => (
        <View style={[styles.card, { marginBottom: 6 }]}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Text style={{ fontWeight: '600' }} onPress={() => navigation.navigate('AdminOrder', { id: r.orderId })}>{r.orderCode} ›</Text>
            <Status value={r.status} labels={REFUND_STATUS} />
          </Row>
          <Text style={styles.muted}>{r.buyerName}{r.buyerPhone ? ` · ${r.buyerPhone}` : ''} · {r.shopName}</Text>
          <Row style={{ justifyContent: 'space-between', marginTop: 4 }}>
            <Text style={styles.muted}>{REFUND_SOURCE[r.source] || r.source} · {PAYMENT_METHOD[r.paymentMethod] || r.paymentMethod}</Text>
            <Text style={{ fontWeight: '700', color: C.primary }}>{formatVnd(r.amount)}</Text>
          </Row>
          {r.reference ? <Text style={[styles.muted, { fontSize: 12 }]}>Mã giao dịch: {r.reference}</Text> : null}
          {r.note ? <Text style={[styles.muted, { fontSize: 12 }]}>{r.note}</Text> : null}
          {r.status === 'PENDING' ? (
            <Row style={{ gap: 8, marginTop: 8 }}>
              <Button small title="Đã chuyển khoản" disabled={busy} onPress={async () => {
                const ref = await ask({ title: 'Mã giao dịch chuyển khoản', placeholder: 'VD: FT2611230001', confirm: 'Xác nhận đã hoàn' })
                if (ref) act(setBusy, () => post(`/admin/refunds/${r.id}/complete`, { reference: ref }), 'Đã ghi nhận hoàn tiền').then(ok => ok && reload())
              }} />
              <Button small kind="danger" title="Thất bại" disabled={busy} onPress={async () => {
                const why = await ask({ title: 'Lý do hoàn tiền thất bại', presets: ['Sai số tài khoản', 'Ngân hàng từ chối giao dịch'], danger: true })
                if (why) act(setBusy, () => post(`/admin/refunds/${r.id}/fail`, { reason: why })).then(ok => ok && reload())
              }} />
            </Row>
          ) : null}
        </View>
      )} />
    </View>
  )
}

// ==================== support desk ====================

export function AdminTicketsScreen({ navigation }) {
  const [status, setStatus] = useState('OPEN')
  const stats = useLoad(() => get('/admin/support/stats'))
  const list = usePaged('/admin/support/tickets', { status: status === 'ALL' ? undefined : status })
  const c = stats.data?.byStatus || {}
  return (
    <View style={styles.page}>
      <Chips value={status} onChange={setStatus} items={[...Object.entries(TICKET_STATUS_STAFF).map(([key, label]) => ({ key, label, count: c[key] })), { key: 'ALL', label: 'Tất cả' }]} />
      <PagedList list={list} empty="Không có yêu cầu hỗ trợ" renderItem={({ item: t }) => (
        <Pressable onPress={() => navigation.navigate('AdminTicket', { id: t.id })} style={[styles.card, { marginBottom: 6 }]}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Text style={styles.muted}>{t.code} · {TICKET_CATEGORIES[t.category]}</Text>
            <Text style={{ color: STATUS_COLOR[t.status], fontSize: 12 }}>{TICKET_STATUS_STAFF[t.status]}</Text>
          </Row>
          <Text style={{ fontWeight: '600', marginTop: 4 }} numberOfLines={1}>{t.subject}</Text>
          <Row style={{ justifyContent: 'space-between', marginTop: 4 }}>
            <Text style={styles.muted}>{t.requesterName} · {ROLE[t.requesterRole] || t.requesterRole}</Text>
            <Text style={[styles.muted, { fontSize: 12 }]}>{formatDateTime(t.updatedAt)}{!t.lastReplyByStaff ? ' · chờ trả lời' : ''}</Text>
          </Row>
          {t.priority && t.priority !== 'NORMAL' ? <Badge text={TICKET_PRIORITY[t.priority]} color={t.priority === 'LOW' ? C.muted : C.danger} /> : null}
        </Pressable>
      )} />
    </View>
  )
}

export function AdminTicketScreen({ route }) {
  const { id } = route.params
  const { data: t, error, reload, refreshing, refresh } = useLoad(() => get(`/admin/support/tickets/${id}`), [id])
  const [text, setText] = useState('')
  const [after, setAfter] = useState('WAITING_CUSTOMER')
  const [busy, setBusy] = useState(false)
  if (!t) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const reply = () => act(setBusy, () => post(`/admin/support/tickets/${id}/replies`, { content: text.trim(), attachmentUrls: [], status: after })).then(ok => { if (ok) { setText(''); reload() } })
  const setStatus = (status) => act(setBusy, () => patch(`/admin/support/tickets/${id}`, { status })).then(ok => ok && reload())
  return (
    <View style={styles.page}>
      <FlatList data={t.messages || []} keyExtractor={m => String(m.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                ListHeaderComponent={(
                  <View style={styles.card}>
                    <Row style={{ justifyContent: 'space-between' }}>
                      <Text style={styles.muted}>{t.code} · {TICKET_CATEGORIES[t.category]}</Text>
                      <Text style={{ color: STATUS_COLOR[t.status], fontWeight: '600' }}>{TICKET_STATUS_STAFF[t.status]}</Text>
                    </Row>
                    <Text style={[styles.h2, { marginTop: 6 }]}>{t.subject}</Text>
                    <Text style={styles.muted}>{t.requesterName} · {t.requesterEmail}</Text>
                    {t.orderCode ? <Text style={styles.muted}>Đơn hàng {t.orderCode}</Text> : null}
                    {t.shopName ? <Text style={styles.muted}>Shop {t.shopName}</Text> : null}
                    <Row style={{ gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                      {t.status !== 'IN_PROGRESS' && t.status !== 'CLOSED' ? <Button small kind="ghost" title="Nhận xử lý" disabled={busy} onPress={() => setStatus('IN_PROGRESS')} /> : null}
                      {t.status !== 'RESOLVED' && t.status !== 'CLOSED' ? <Button small kind="ghost" title="Đã giải quyết" disabled={busy} onPress={() => setStatus('RESOLVED')} /> : null}
                      {t.status !== 'CLOSED' ? <Button small kind="ghost" title="Đóng" disabled={busy} onPress={() => setStatus('CLOSED')} /> : null}
                    </Row>
                    {t.rating ? <Text style={[styles.muted, { marginTop: 6 }]}>Người dùng đánh giá {'★'.repeat(t.rating)}{t.ratingComment ? ` · “${t.ratingComment}”` : ''}</Text> : null}
                  </View>
                )}
                renderItem={({ item: m }) => (
                  <View style={[styles.card, { marginHorizontal: 8, borderLeftWidth: 3, borderLeftColor: m.staff ? C.primary : C.border }, m.systemLine && { backgroundColor: C.bg }]}>
                    <Row style={{ justifyContent: 'space-between' }}>
                      <Text style={{ fontWeight: '600', color: m.staff ? C.primary : C.text }}>{m.staff ? `Shoppi · ${m.senderName || ''}` : m.senderName}</Text>
                      <Text style={{ fontSize: 11, color: C.faint }}>{formatDateTime(m.createdAt)}</Text>
                    </Row>
                    <Text style={{ marginTop: 4, lineHeight: 20 }}>{m.content}</Text>
                    <Row style={{ gap: 6, marginTop: 4, flexWrap: 'wrap' }}>{(m.attachmentUrls || []).map(u => <Img key={u} uri={u} style={{ width: 72, height: 72 }} />)}</Row>
                  </View>
                )} />
      {t.status !== 'CLOSED' ? (
        <BottomBar>
          <View style={{ flex: 1 }}>
            <Field value={text} onChangeText={setText} placeholder="Trả lời người dùng..." multiline style={{ marginBottom: 4 }} />
            <Select value={after} onChange={setAfter} options={[{ value: 'WAITING_CUSTOMER', label: 'Sau khi gửi: chờ người dùng phản hồi' },
              { value: 'IN_PROGRESS', label: 'Sau khi gửi: vẫn đang xử lý' }, { value: 'RESOLVED', label: 'Sau khi gửi: đã giải quyết' }]} />
          </View>
          <Button title="Gửi" disabled={busy || !text.trim()} onPress={reply} />
        </BottomBar>
      ) : null}
    </View>
  )
}

// ==================== account ====================

export function AdminAccountScreen({ navigation }) {
  const { user, signOut } = useAuth()
  const { me, can } = useAdmin()
  const rows = [
    ['🔐', 'Bảo mật tài khoản', 'Security', true],
    ['🔔', 'Thông báo', 'AdminNotifications', true],
    ['⚖️', 'Khiếu nại trả hàng', 'AdminDisputes', can('ORDERS')],
    ['💸', 'Hoàn tiền', 'AdminRefunds', can('FINANCE')],
    ['🎧', 'Yêu cầu hỗ trợ', 'AdminTickets', can('SUPPORT')],
    ['📄', 'Chính sách Shoppi', 'Policies', true]
  ].filter(r => r[3])
  return (
    <ScrollView style={styles.page}>
      <View style={{ backgroundColor: C.primary, padding: 16, paddingTop: 48 }}>
        <Text style={{ color: 'white', fontSize: 18, fontWeight: '700' }}>{user?.fullName}</Text>
        <Text style={{ color: 'white', opacity: 0.9 }}>{user?.email}</Text>
        <Text style={{ color: 'white', opacity: 0.9, fontSize: 12, marginTop: 4 }}>
          {me?.superAdmin ? 'Quản trị viên cấp cao: mọi quyền' : `Quyền: ${(me?.allPermissions || []).filter(p => me?.permissions?.includes(p.key)).map(p => p.label).join(', ') || 'chưa có'}`}
        </Text>
      </View>
      {rows.map(([icon, label, screen]) => (
        <Pressable key={screen} onPress={() => navigation.navigate(screen)} style={[styles.card, { flexDirection: 'row', alignItems: 'center', marginBottom: 1 }]}>
          <Text style={{ fontSize: 20, width: 34 }}>{icon}</Text>
          <Text style={{ flex: 1, fontSize: 15 }}>{label}</Text>
          <Text style={{ color: C.faint }}>›</Text>
        </Pressable>
      ))}
      <Text style={[styles.muted, { padding: 14 }]}>Cấu hình sàn, nhân viên, banner, chiến dịch, đối soát và báo cáo Excel dùng trên trang quản trị web.</Text>
      <Button kind="ghost" title="Đăng xuất" style={{ margin: 14 }} onPress={() => confirm('Đăng xuất?', '', signOut, 'Đăng xuất')} />
    </ScrollView>
  )
}
