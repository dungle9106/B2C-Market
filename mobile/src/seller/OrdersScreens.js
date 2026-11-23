import React, { useState } from 'react'
import { Alert, FlatList, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native'
import { get, post, patch, errorText } from '../api'
import { ORDER_STATUS, SELLER_CANCEL_REASONS, formatDateTime, formatVnd } from '../format'
import { OrderItems } from '../buyer/OrdersScreen'
import { BottomBar, Button, C, Empty, Field, Loading, Row, Select, styles, Tabs, useLoad } from '../ui'
import { printLabels } from './labels'

// prints, then says which orders could not be printed (not packed yet...)
export async function printAndReport(ids, done) {
  try {
    const r = await printLabels(ids)
    if (r.skipped?.length) Alert.alert(`Đã in ${r.printed} phiếu`, 'Không in được: ' + r.skipped.map(x => `${x.code} (${x.reason})`).join('; '))
    done?.()
  } catch (e) {
    Alert.alert('Không in được phiếu', errorText(e))
  }
}

const TABS = [
  { key: 'PENDING', label: 'Chờ xác nhận' },
  { key: 'CONFIRMED', label: 'Chờ lấy hàng' },
  { key: 'SHIPPING', label: 'Đang giao' },
  { key: 'DONE', label: 'Đã giao' },
  { key: 'CANCELLED', label: 'Đã hủy' },
  { key: 'RETURN', label: 'Trả hàng' }
]
const inTab = (o, tab) => ({
  DONE: o.status === 'DELIVERED',
  CANCELLED: o.status === 'CANCELLED',
  RETURN: ['RETURN_REQUESTED', 'REFUNDED'].includes(o.status)
}[tab] ?? o.status === tab)

const stageLabel = (o) => (o.status === 'CONFIRMED' ? (o.cancelRequestedAt ? 'Người mua yêu cầu hủy' : o.packedAt ? 'Chờ ĐVVC lấy hàng' : 'Chờ chuẩn bị hàng') : ORDER_STATUS[o.status])

const isoDay = (offset) => {
  const d = new Date(Date.now() + offset * 86400000)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const PICKUP_DAYS = [0, 1, 2].map(i => ({ value: isoDay(i), label: ['Hôm nay', 'Ngày mai', 'Ngày kia'][i] + ' (' + isoDay(i).slice(5).split('-').reverse().join('/') + ')' }))
const STAGES = [{ value: 'IN_TRANSIT', label: 'Đang trung chuyển' }, { value: 'OUT_FOR_DELIVERY', label: 'Đang giao đến người nhận' }, { value: 'DELIVERY_FAILED', label: 'Giao không thành công' }]

// What the shop can do with an order at its current step (same rules as the web's SellerOrderActions)
export function OrderActions({ order: o, onChanged }) {
  const [panel, setPanel] = useState(null)   // prepare | cancel | reject | tracking
  const [pickupMethod, setPickupMethod] = useState('PICKUP')
  const [pickupDate, setPickupDate] = useState(PICKUP_DAYS[1].value)
  const [reason, setReason] = useState(SELLER_CANCEL_REASONS[0])
  const [text, setText] = useState('')
  const [stage, setStage] = useState('IN_TRANSIT')
  const [busy, setBusy] = useState(false)

  async function run(fn) {
    setBusy(true)
    try {
      await fn()
      setPanel(null)
      setText('')
      onChanged()
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  const confirm = (title, fn) => Alert.alert(title, `Đơn ${o.code}`, [{ text: 'Hủy' }, { text: 'Đồng ý', onPress: () => run(fn) }])
  const status = (to, reasonText) => () => patch(`/seller/orders/${o.id}/status`, { status: to, reason: reasonText })

  const cancelRequest = o.cancelRequestedAt && o.status === 'CONFIRMED'
  return (
    <View style={{ gap: 8, marginTop: 10 }}>
      <Row style={{ gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
        {cancelRequest ? (
          <>
            <Button small title="Đồng ý hủy" disabled={busy} onPress={() => confirm('Đồng ý hủy đơn?', () => post(`/seller/orders/${o.id}/cancel-request/accept`))} />
            <Button small kind="ghost" title="Từ chối hủy" onPress={() => setPanel('reject')} />
          </>
        ) : (
          <>
            {o.status === 'PENDING' ? <Button small title="Xác nhận đơn" disabled={busy} onPress={() => run(status('CONFIRMED'))} /> : null}
            {o.status === 'CONFIRMED' && !o.packedAt ? <Button small title="Chuẩn bị hàng" onPress={() => setPanel('prepare')} /> : null}
            {o.status === 'CONFIRMED' && o.packedAt ? <Button small title="Đã giao cho ĐVVC" disabled={busy} onPress={() => confirm('Đã bàn giao cho đơn vị vận chuyển?', status('SHIPPING'))} /> : null}
            {o.status === 'SHIPPING' ? (
              <>
                <Button small kind="ghost" title="Cập nhật vận chuyển" onPress={() => setPanel('tracking')} />
                <Button small title="Đã giao thành công" disabled={busy} onPress={() => confirm('Xác nhận đơn đã giao thành công?', status('DELIVERED'))} />
              </>
            ) : null}
          </>
        )}
        {['PENDING', 'CONFIRMED'].includes(o.status) && !o.cancelRequestedAt
          ? <Button small kind="danger" title="Hủy đơn" onPress={() => setPanel('cancel')} /> : null}
      </Row>

      {panel === 'prepare' ? (
        <View style={panelStyle}>
          <Select label="Hình thức giao cho ĐVVC" value={pickupMethod} onChange={setPickupMethod}
                  options={[{ value: 'PICKUP', label: 'ĐVVC đến lấy hàng' }, { value: 'DROPOFF', label: 'Tự mang ra bưu cục' }]} />
          <Select label="Ngày" value={pickupDate} options={PICKUP_DAYS} onChange={setPickupDate} />
          <Button title="Xác nhận" disabled={busy} onPress={() => run(() => post(`/seller/orders/${o.id}/prepare`, { pickupMethod, pickupDate }))} />
        </View>
      ) : null}
      {panel === 'cancel' ? (
        <View style={panelStyle}>
          <Select label="Lý do hủy (người mua sẽ thấy)" value={reason} options={SELLER_CANCEL_REASONS} onChange={setReason} />
          <Button title="Hủy đơn hàng" kind="danger" disabled={busy} onPress={() => run(status('CANCELLED', reason))} />
        </View>
      ) : null}
      {panel === 'reject' ? (
        <View style={panelStyle}>
          <Field label={`Người mua muốn hủy vì: "${o.cancelRequestReason}". Lý do từ chối:`} value={text} onChangeText={setText} multiline />
          <Button title="Từ chối yêu cầu" disabled={busy || !text.trim()} onPress={() => run(() => post(`/seller/orders/${o.id}/cancel-request/reject`, { reason: text }))} />
        </View>
      ) : null}
      {panel === 'tracking' ? (
        <View style={panelStyle}>
          <Select label="Trạng thái" value={stage} options={STAGES} onChange={setStage} />
          <Field placeholder={stage === 'DELIVERY_FAILED' ? 'Lý do (bắt buộc)' : 'Ghi chú / vị trí (không bắt buộc)'} value={text} onChangeText={setText} />
          <Button title="Cập nhật" disabled={busy || (stage === 'DELIVERY_FAILED' && !text.trim())}
                  onPress={() => run(() => post(`/seller/orders/${o.id}/tracking`, { stage, note: text, location: null }))} />
        </View>
      ) : null}
    </View>
  )
}

const panelStyle = { backgroundColor: C.bg, padding: 10, borderRadius: 4 }

export default function SellerOrdersScreen({ navigation }) {
  const [tab, setTab] = useState('PENDING')
  const [picked, setPicked] = useState([])
  const [prepare, setPrepare] = useState(null)   // bulk "Chuan bi hang": { pickupMethod, pickupDate }
  const [busy, setBusy] = useState(false)
  const { data, error, refreshing, refresh, reload } = useLoad(() => get('/seller/orders'))
  const tabs = TABS.map(t => ({ ...t, count: ['PENDING', 'CONFIRMED'].includes(t.key) ? (data || []).filter(o => inTab(o, t.key)).length : 0 }))
  const list = (data || []).filter(o => inTab(o, tab))
  const selectable = ['PENDING', 'CONFIRMED'].includes(tab)
  const chosen = list.filter(o => picked.includes(o.id))
  const toggle = (id) => setPicked(p => (p.includes(id) ? p.filter(x => x !== id) : [...p, id]))
  const switchTab = (t) => { setTab(t); setPicked([]); setPrepare(null) }

  // "Thao tac hang loat": the backend does each order on its own and lists those it refused
  async function bulk(action, extra = {}) {
    setBusy(true)
    try {
      const r = await post('/seller/orders/bulk', { ids: chosen.map(o => o.id), action, ...extra })
      Alert.alert(`Đã cập nhật ${r.updated} đơn`, r.skipped?.length ? 'Bỏ qua: ' + r.skipped.map(x => `${x.code} (${x.reason})`).join('; ') : '')
      setPicked([])
      setPrepare(null)
      reload()
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  const unpacked = chosen.filter(o => !o.packedAt && !o.cancelRequestedAt)
  const packed = chosen.filter(o => o.packedAt)

  return (
    <View style={styles.page}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexGrow: 0 }}>
        <View style={{ width: 700 }}><Tabs items={tabs} value={tab} onChange={switchTab} /></View>
      </ScrollView>
      {selectable && list.length ? (
        <Row style={{ backgroundColor: 'white', paddingHorizontal: 12, paddingVertical: 8, justifyContent: 'space-between' }}>
          <Text style={styles.muted}>{picked.length ? `Đã chọn ${chosen.length}/${list.length} đơn` : 'Chạm ☐ để chọn nhiều đơn'}</Text>
          <Text style={{ color: C.primary }} onPress={() => setPicked(chosen.length === list.length ? [] : list.map(o => o.id))}>
            {chosen.length === list.length ? 'Bỏ chọn' : 'Chọn tất cả'}
          </Text>
        </Row>
      ) : null}
      {!data ? (error ? <Empty error={error} onRetry={reload} /> : <Loading />) : (
        <FlatList data={list} keyExtractor={o => String(o.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                  ListEmptyComponent={<Empty text="Không có đơn hàng nào" />}
                  renderItem={({ item: o }) => (
                    <View style={[styles.card, picked.includes(o.id) && { backgroundColor: C.primarySoft }]}>
                      <Pressable onPress={() => navigation.navigate('SellerOrder', { id: o.id })} onLongPress={() => selectable && toggle(o.id)}>
                        <Row style={{ justifyContent: 'space-between', marginBottom: 6 }}>
                          <Row style={{ flex: 1 }}>
                            {selectable ? (
                              <Pressable onPress={() => toggle(o.id)} hitSlop={12} style={{ marginRight: 8 }}>
                                <Text style={{ fontSize: 20, color: picked.includes(o.id) ? C.primary : C.faint }}>{picked.includes(o.id) ? '☑' : '☐'}</Text>
                              </Pressable>
                            ) : null}
                            <Text style={{ fontWeight: '600' }}>👤 {o.buyerName}</Text>
                          </Row>
                          <Text style={{ color: o.cancelRequestedAt ? C.danger : C.primary }}>{stageLabel(o)}</Text>
                        </Row>
                        <OrderItems order={o} />
                        <Row style={{ justifyContent: 'space-between', marginTop: 4 }}>
                          <Text style={styles.muted}>{o.code} · {o.carrierName}{o.labelPrintedAt ? ' · đã in phiếu' : ''}</Text>
                          <Text style={{ color: C.primary, fontWeight: '600' }}>{formatVnd(o.total)}</Text>
                        </Row>
                      </Pressable>
                      {!picked.length ? <OrderActions order={o} onChanged={reload} /> : null}
                    </View>
                  )} />
      )}
      {prepare ? (
        <View style={[panelStyle, { backgroundColor: 'white', borderTopWidth: 1, borderTopColor: C.border }]}>
          <Text style={styles.h2}>Chuẩn bị hàng cho {unpacked.length} đơn</Text>
          <Select label="Hình thức giao cho ĐVVC" value={prepare.pickupMethod} onChange={v => setPrepare(p => ({ ...p, pickupMethod: v }))}
                  options={[{ value: 'PICKUP', label: 'ĐVVC đến lấy hàng' }, { value: 'DROPOFF', label: 'Tự mang ra bưu cục' }]} />
          <Select label="Ngày" value={prepare.pickupDate} options={PICKUP_DAYS} onChange={v => setPrepare(p => ({ ...p, pickupDate: v }))} />
          <Row style={{ gap: 8 }}>
            <Button kind="ghost" title="Hủy" style={{ flex: 1 }} onPress={() => setPrepare(null)} />
            <Button title="Xác nhận" style={{ flex: 1 }} disabled={busy || !unpacked.length} onPress={() => bulk('PREPARE', prepare)} />
          </Row>
        </View>
      ) : chosen.length ? (
        <BottomBar>
          {tab === 'PENDING' ? <Button style={{ flex: 1 }} title={`Xác nhận ${chosen.length} đơn`} disabled={busy} onPress={() => bulk('CONFIRM')} /> : null}
          {tab === 'CONFIRMED' && unpacked.length ? <Button style={{ flex: 1 }} small title={`Chuẩn bị (${unpacked.length})`} disabled={busy}
                                                            onPress={() => setPrepare({ pickupMethod: 'PICKUP', pickupDate: PICKUP_DAYS[1].value })} /> : null}
          {tab === 'CONFIRMED' && packed.length ? <Button style={{ flex: 1 }} small kind="ghost" title={`🖨 In phiếu (${packed.length})`} disabled={busy}
                                                          onPress={() => printAndReport(packed.map(o => o.id), reload)} /> : null}
          {tab === 'CONFIRMED' && packed.length ? <Button style={{ flex: 1 }} small kind="ghost" title={`Đã giao ĐVVC (${packed.length})`} disabled={busy}
                                                          onPress={() => Alert.alert(`Bàn giao ${packed.length} đơn cho ĐVVC?`, '', [{ text: 'Hủy' }, { text: 'Đồng ý', onPress: () => bulk('SHIP') }])} /> : null}
        </BottomBar>
      ) : null}
    </View>
  )
}

export function SellerOrderScreen({ route, navigation }) {
  const { id } = route.params
  const { data: o, error, reload } = useLoad(() => get(`/seller/orders/${id}`), [id])
  if (!o) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 24 }}>
      <View style={[styles.card, { backgroundColor: C.green }]}>
        <Text style={{ color: 'white', fontSize: 17, fontWeight: '600' }}>{stageLabel(o)}</Text>
        <Text style={{ color: 'white' }}>Mã đơn {o.code} · {formatDateTime(o.createdAt)}</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Người mua</Text>
        <Text>{o.buyerName}</Text>
        <Text style={styles.muted}>{o.shippingAddress}</Text>
        {o.note ? <Text style={{ marginTop: 6 }}>✉ Lời nhắn: {o.note}</Text> : null}
      </View>
      <View style={styles.card}>
        <Text style={styles.h2}>Vận chuyển</Text>
        <Text>{o.carrierName} – {o.shippingMethodName}</Text>
        {o.trackingNumber ? <Text>Mã vận đơn: <Text style={{ fontWeight: '600' }}>{o.trackingNumber}</Text></Text> : null}
        {o.packedAt ? <Text style={styles.muted}>{o.pickupMethod === 'DROPOFF' ? 'Gửi bưu cục' : 'ĐVVC lấy hàng'} ngày {o.pickupDate}</Text> : null}
        {o.trackingNumber && ['CONFIRMED', 'SHIPPING'].includes(o.status) ? (
          <Button small kind="ghost" title={o.labelPrintedAt ? '🖨 In lại phiếu giao hàng' : '🖨 In phiếu giao hàng'} style={{ alignSelf: 'flex-start', marginTop: 8 }}
                  onPress={() => printAndReport([o.id], reload)} />
        ) : null}
        {[...(o.events || [])].reverse().map((e, i) => (
          <Text key={i} style={{ marginTop: 6, color: i === 0 ? C.green : C.muted }}>• {e.title} <Text style={{ fontSize: 11, color: C.faint }}>{formatDateTime(e.time)}</Text></Text>
        ))}
      </View>
      <View style={styles.card}>
        <OrderItems order={o} />
        <View style={styles.divider} />
        <Row style={{ justifyContent: 'space-between' }}><Text style={styles.muted}>Tiền hàng</Text><Text>{formatVnd(o.subtotal)}</Text></Row>
        <Row style={{ justifyContent: 'space-between' }}><Text style={styles.muted}>Phí vận chuyển</Text><Text>{formatVnd(o.shippingFee)}</Text></Row>
        {o.discount > 0 ? <Row style={{ justifyContent: 'space-between' }}><Text style={styles.muted}>Giảm giá</Text><Text>-{formatVnd(o.discount)}</Text></Row> : null}
        {o.coinDiscount > 0 ? <Row style={{ justifyContent: 'space-between' }}><Text style={styles.muted}>Shoppi Xu (sàn tài trợ)</Text><Text>-{formatVnd(o.coinDiscount)}</Text></Row> : null}
        <Row style={{ justifyContent: 'space-between' }}><Text>Tổng cộng ({o.paymentMethod === 'COD' ? 'COD' : 'đã thanh toán online'})</Text><Text style={{ color: C.primary, fontWeight: '600' }}>{formatVnd(o.total)}</Text></Row>
        <OrderActions order={o} onChanged={reload} />
      </View>
    </ScrollView>
  )
}
