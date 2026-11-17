import React, { useState } from 'react'
import { Alert, FlatList, Image, Modal, Pressable, RefreshControl, ScrollView, Text, TextInput, View } from 'react-native'
import { get, post, fileUrl, errorText } from '../api'
import { formatDateTime, formatVnd } from '../format'
import { Badge, Button, C, Empty, Field, Img, Loading, Row, Stars, styles, Tabs, useLoad } from '../ui'
import { RETURN_REASONS, RETURN_STATUS } from '../buyer/ReturnScreens'

// =====================================================================
// "Tra hang/Hoan tien" of the shop (same rules as the web page /seller/returns)
// =====================================================================
const RETURN_TABS = [
  { key: 'TODO', label: 'Cần xử lý', match: r => r.status === 'REQUESTED' },
  { key: 'DISPUTE', label: 'Khiếu nại', match: r => r.status === 'ESCALATED' },
  { key: 'DONE', label: 'Đã xong', match: r => ['REFUNDED', 'CLOSED', 'REJECTED'].includes(r.status) }
]

export function SellerReturnsScreen() {
  const { data, error, reload, refresh, refreshing } = useLoad(() => get('/seller/returns'))
  const [tab, setTab] = useState('TODO')
  const [photo, setPhoto] = useState(null)
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const list = data.filter(RETURN_TABS.find(t => t.key === tab).match)
  return (
    <View style={styles.page}>
      <Tabs items={RETURN_TABS.map(t => ({ ...t, count: data.filter(t.match).length }))} value={tab} onChange={setTab} />
      <FlatList data={list} keyExtractor={r => String(r.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                ListEmptyComponent={<Empty text="Không có yêu cầu nào" />}
                renderItem={({ item }) => <ReturnItem r={item} onChanged={reload} onPhoto={setPhoto} />} />
      <Modal visible={!!photo} transparent onRequestClose={() => setPhoto(null)}>
        <Pressable style={{ flex: 1, backgroundColor: 'rgba(0,0,0,.9)', justifyContent: 'center' }} onPress={() => setPhoto(null)}>
          {photo ? <Image source={{ uri: fileUrl(photo) }} style={{ width: '100%', height: '70%' }} resizeMode="contain" /> : null}
        </Pressable>
      </Modal>
    </View>
  )
}

function ReturnItem({ r, onChanged, onPhoto }) {
  const [rejecting, setRejecting] = useState(false)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [label, color] = RETURN_STATUS[r.status] || [r.status, C.muted]
  const held = r.heldUntil && new Date(r.heldUntil) > new Date()

  async function run(fn, done) {
    setBusy(true)
    try {
      await fn()
      Alert.alert(done)
      setRejecting(false)
      onChanged()
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const approve = () => Alert.alert('Đồng ý hoàn tiền?', `Hoàn ${formatVnd(r.refundAmount)} cho ${r.buyerName}.` + (r.type === 'RETURN_AND_REFUND' ? ' Hàng trả về sẽ được cộng lại kho.' : ''),
    [{ text: 'Hủy' }, { text: 'Đồng ý', onPress: () => run(() => post(`/seller/returns/${r.id}/approve`), 'Đã đồng ý hoàn tiền') }])

  return (
    <View style={[styles.card, { marginBottom: 8 }]}>
      <Row style={{ justifyContent: 'space-between' }}>
        <Text style={{ fontWeight: '600' }}>{r.buyerName} · {r.orderCode}</Text>
        <Text style={{ color, fontWeight: '600', fontSize: 12 }}>{label}</Text>
      </Row>
      <Text style={styles.muted}>{formatDateTime(r.createdAt)} · {r.type === 'REFUND_ONLY' ? 'Chỉ hoàn tiền' : 'Trả hàng và hoàn tiền'}</Text>
      {r.items?.map(i => (
        <Row key={i.orderItemId} style={{ gap: 8, marginTop: 8 }}>
          <Img uri={i.thumbnailUrl} style={{ width: 40, height: 40 }} />
          <Text style={{ flex: 1 }} numberOfLines={2}>{i.productName}{i.variantName ? ` (${i.variantName})` : ''} x{i.quantity}</Text>
        </Row>
      ))}
      <Text style={{ marginTop: 8 }}><Text style={{ fontWeight: '600' }}>{RETURN_REASONS.find(x => x.value === r.reason)?.label}: </Text>{r.description}</Text>
      {r.evidenceUrls?.length ? (
        <ScrollView horizontal style={{ marginTop: 8 }} contentContainerStyle={{ gap: 6 }}>
          {r.evidenceUrls.map(u => <Pressable key={u} onPress={() => onPhoto(u)}><Img uri={u} style={{ width: 64, height: 64, borderRadius: 4 }} /></Pressable>)}
        </ScrollView>
      ) : null}
      <Text style={{ marginTop: 8 }}>Số tiền hoàn: <Text style={{ color: C.primary, fontWeight: '700' }}>{formatVnd(r.refundAmount)}</Text></Text>
      {held ? (
        <View style={{ marginTop: 8, padding: 8, backgroundColor: '#eaf2fd', borderRadius: 4 }}>
          <Text style={{ color: '#2673dd', fontSize: 13 }}>🛡 Shoppi đang kiểm tra yêu cầu này (dấu hiệu bất thường). Bạn có thể từ chối ngay, còn hoàn tiền được sau {formatDateTime(r.heldUntil)} hoặc khi Shoppi xác nhận.</Text>
        </View>
      ) : null}
      {r.sellerNote ? <Text style={[styles.muted, { marginTop: 6 }]}>Ghi chú của shop: {r.sellerNote}</Text> : null}
      {r.complaint ? <Text style={{ marginTop: 6 }}><Text style={{ fontWeight: '600' }}>Khiếu nại: </Text>{r.complaint}</Text> : null}

      {r.status === 'REQUESTED' && !rejecting ? (
        <Row style={{ gap: 8, marginTop: 10 }}>
          <Button small kind="ghost" title="Từ chối" onPress={() => setRejecting(true)} style={{ flex: 1 }} disabled={busy} />
          <Button small title={held ? 'Đang tạm giữ' : 'Đồng ý hoàn tiền'} onPress={approve} style={{ flex: 1 }} disabled={busy || held} />
        </Row>
      ) : null}
      {rejecting ? (
        <View style={{ marginTop: 10 }}>
          <Field label="Lý do từ chối (người mua sẽ thấy)" value={note} onChangeText={setNote} multiline maxLength={1000} />
          <Row style={{ gap: 8 }}>
            <Button small kind="ghost" title="Đóng" onPress={() => setRejecting(false)} style={{ flex: 1 }} />
            <Button small kind="danger" title="Từ chối yêu cầu" style={{ flex: 1 }} disabled={busy || note.trim().length < 5}
                    onPress={() => run(() => post(`/seller/returns/${r.id}/reject`, { note: note.trim() }), 'Đã từ chối. Người mua có thể khiếu nại lên Shoppi.')} />
          </Row>
        </View>
      ) : null}
    </View>
  )
}

// =====================================================================
// "Du bao nhap hang (AI)" (same model as the web page /seller/inventory/forecast)
// =====================================================================
const STOCK_STATUS = {
  OUT: ['Hết hàng', C.danger], URGENT: ['Nhập gấp', C.danger], REORDER: ['Nên nhập', '#d97706'],
  OK: ['Ổn', C.green], OVERSTOCK: ['Tồn nhiều', '#2673dd'], SLOW: ['Bán chậm', C.muted]
}
const LEAD_TIMES = [3, 7, 14, 30]

export function StockForecastScreen() {
  const [leadTime, setLeadTime] = useState(7)
  const { data, error, reload, refresh, refreshing } = useLoad(() => get('/seller/inventory/forecast', { leadTime, cover: 30, serviceLevel: 95 }), [leadTime])
  const [filter, setFilter] = useState('BUY')
  const [advice, setAdvice] = useState(null)
  const [busy, setBusy] = useState(false)
  const [restock, setRestock] = useState(null)   // the row being restocked
  const [qty, setQty] = useState('')

  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const rows = data.items.filter(r => (filter === 'BUY' ? r.suggestQty > 0 : filter === 'ALL' ? true : ['OVERSTOCK', 'SLOW'].includes(r.status)))

  async function ask() {
    setBusy(true)
    try {
      setAdvice(await post('/seller/inventory/forecast/advice', null, { leadTime, cover: 30, serviceLevel: 95 }))
    } catch (e) {
      Alert.alert('Không lấy được lời khuyên', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  async function saveRestock() {
    const n = parseInt(qty, 10)
    if (!(n > 0)) return Alert.alert('Nhập số lượng lớn hơn 0')
    setBusy(true)
    try {
      await post('/seller/inventory/adjust', { productId: restock.productId, variantId: restock.variantId, mode: 'ADD', quantity: n, reason: 'RESTOCK', note: 'Nhập hàng theo dự báo' })
      setRestock(null)
      Alert.alert('Đã cộng kho', `+${n} ${restock.name}${restock.variantName ? ' (' + restock.variantName + ')' : ''}`)
      reload()
    } catch (e) {
      Alert.alert('Không cập nhật được kho', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const s = data.summary
  return (
    <View style={styles.page}>
      <FlatList data={rows} keyExtractor={r => r.key} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
        ListHeaderComponent={(
          <View>
            <View style={styles.card}>
              <Text style={styles.label}>Thời gian chờ hàng về</Text>
              <Row style={{ gap: 6, marginBottom: 10 }}>
                {LEAD_TIMES.map(d => (
                  <Pressable key={d} onPress={() => setLeadTime(d)} style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 14, borderWidth: 1,
                    borderColor: d === leadTime ? C.primary : C.border, backgroundColor: d === leadTime ? C.primarySoft : 'white' }}>
                    <Text style={{ color: d === leadTime ? C.primary : C.text }}>{d} ngày</Text>
                  </Pressable>
                ))}
              </Row>
              <Row style={{ gap: 6, flexWrap: 'wrap' }}>
                <Badge text={`Hết hàng ${s.OUT}`} color={C.danger} bg="#fdecea" />
                <Badge text={`Nhập gấp ${s.URGENT}`} color={C.danger} bg="#fdecea" />
                <Badge text={`Nên nhập ${s.REORDER}`} color="#d97706" bg="#fff4e5" />
                <Badge text={`Tồn nhiều ${s.OVERSTOCK}`} color="#2673dd" bg="#e8f0fc" />
              </Row>
              <Text style={{ marginTop: 10 }}>Nên nhập tổng <Text style={{ color: C.primary, fontWeight: '700' }}>{s.suggestUnits.toLocaleString('vi-VN')}</Text> sản phẩm ({s.suggestSkus} mã) để đủ bán 30 ngày.</Text>
              {data.accuracy.accuracy != null ? (
                <Text style={[styles.muted, { marginTop: 4, fontSize: 12 }]}>Độ chính xác khi thử trên 14 ngày qua: {data.accuracy.accuracy}% (trung bình đơn giản: {data.accuracy.naiveAccuracy}%)</Text>
              ) : null}
              <Button small title={busy ? 'Đang phân tích...' : '✨ AI tư vấn nhập hàng'} onPress={ask} disabled={busy} style={{ marginTop: 10 }} />
              {advice ? <Text style={{ marginTop: 10, lineHeight: 20 }}>{advice.advice}</Text> : null}
            </View>
            <Tabs items={[{ key: 'BUY', label: 'Cần nhập' }, { key: 'EXCESS', label: 'Tồn nhiều / chậm' }, { key: 'ALL', label: 'Tất cả' }]} value={filter} onChange={setFilter} />
          </View>
        )}
        ListEmptyComponent={<Empty text="Không có mã hàng nào trong mục này" />}
        renderItem={({ item: r }) => {
          const [label, color] = STOCK_STATUS[r.status]
          return (
            <Pressable onPress={() => { setRestock(r); setQty(r.suggestQty ? String(r.suggestQty) : '') }} style={[styles.card, { marginBottom: 1 }]}>
              <Row style={{ gap: 10, alignItems: 'flex-start' }}>
                <Img uri={r.thumbnailUrl} style={{ width: 48, height: 48 }} />
                <View style={{ flex: 1 }}>
                  <Text numberOfLines={1} style={{ fontWeight: '500' }}>{r.name}</Text>
                  {r.variantName ? <Text style={styles.muted}>{r.variantName}</Text> : null}
                  <Text style={{ fontSize: 13, marginTop: 2 }}>Tồn {r.stock} · bán {r.avgDaily}/ngày · dự báo {r.forecastDaily}/ngày</Text>
                  <Text style={{ fontSize: 12, color: C.muted }}>{r.reason}</Text>
                </View>
                <View style={{ alignItems: 'flex-end' }}>
                  <Text style={{ color, fontWeight: '600', fontSize: 12 }}>{label}</Text>
                  {r.suggestQty ? <Text style={{ color: C.primary, fontWeight: '700', marginTop: 4 }}>+{r.suggestQty}</Text> : null}
                </View>
              </Row>
            </Pressable>
          )
        }} />

      <Modal visible={!!restock} transparent animationType="slide" onRequestClose={() => setRestock(null)}>
        <Pressable style={{ flex: 1, backgroundColor: 'rgba(0,0,0,.4)' }} onPress={() => setRestock(null)} />
        {restock ? (
          <View style={{ backgroundColor: 'white', padding: 16 }}>
            <Text style={styles.h2}>Nhập kho: {restock.name}{restock.variantName ? ` (${restock.variantName})` : ''}</Text>
            <Text style={[styles.muted, { marginBottom: 10 }]}>Tồn hiện tại {restock.stock}. Điểm đặt hàng lại {restock.reorderPoint}, tồn an toàn {restock.safetyStock}.</Text>
            <Field label="Số lượng nhập thêm" value={qty} onChangeText={setQty} keyboardType="number-pad" />
            <Button title={busy ? 'Đang lưu...' : 'Cộng vào kho'} onPress={saveRestock} disabled={busy} />
          </View>
        ) : null}
      </Modal>
    </View>
  )
}

// =====================================================================
// "Tra loi danh gia" (review hub inbox, with an AI draft)
// =====================================================================
export function ReviewInboxScreen() {
  const [status, setStatus] = useState('OPEN')
  const { data, error, reload, refresh, refreshing } = useLoad(() => get('/seller/review-hub/items', { status, sort: 'priority', size: 30 }), [status])
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <View style={styles.page}>
      <Tabs items={[{ key: 'OPEN', label: 'Cần trả lời' }, { key: 'REPLIED', label: 'Đã trả lời' }]} value={status} onChange={setStatus} />
      <FlatList data={data.items} keyExtractor={i => String(i.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                ListEmptyComponent={<Empty text={status === 'OPEN' ? 'Đã trả lời hết đánh giá 🎉' : 'Chưa có phản hồi nào'} />}
                renderItem={({ item }) => <ReviewItem item={item} onChanged={reload} />} />
    </View>
  )
}

const PRIORITY = { URGENT: ['Khẩn cấp', C.danger], HIGH: ['Ưu tiên cao', '#d97706'], NORMAL: ['Bình thường', '#2673dd'], LOW: ['Thấp', C.muted] }

function ReviewItem({ item, onChanged }) {
  const [text, setText] = useState(item.draftReply || '')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const done = item.status === 'REPLIED'
  const [plabel, pcolor] = PRIORITY[item.priorityLevel] || [null, C.muted]

  async function draft() {
    setBusy(true)
    try {
      const r = await post(`/seller/review-hub/items/${item.id}/draft`)
      setText(r.draftReply || '')
    } catch (e) {
      Alert.alert('AI chưa soạn được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  async function send() {
    if (text.trim().length < 2) return Alert.alert('Nhập nội dung phản hồi')
    setBusy(true)
    try {
      await post(`/seller/review-hub/items/${item.id}/reply`, { text: text.trim() })
      setOpen(false)
      onChanged()
    } catch (e) {
      Alert.alert('Không gửi được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={[styles.card, { marginBottom: 8 }]}>
      <Row style={{ justifyContent: 'space-between' }}>
        <Row style={{ gap: 6 }}><Stars value={item.rating} /><Text style={styles.muted}>{item.reviewerName}</Text></Row>
        {!done && plabel ? <Text style={{ color: pcolor, fontSize: 12, fontWeight: '600' }}>{plabel}{item.overdue ? ' · quá hạn' : ''}</Text> : null}
      </Row>
      <Text style={[styles.muted, { fontSize: 12 }]} numberOfLines={1}>{item.productName} · {formatDateTime(item.reviewedAt)}</Text>
      {item.content ? <Text style={{ marginTop: 6 }}>{item.content}</Text> : <Text style={[styles.muted, { marginTop: 6 }]}>(Không có nội dung)</Text>}
      {item.issues?.length ? (
        <Row style={{ gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
          {item.issues.map(x => <Badge key={x.key} text={x.label} color="#d97706" bg="#fff4e5" />)}
        </Row>
      ) : null}
      {done ? (
        <View style={{ marginTop: 8, padding: 8, backgroundColor: C.bg, borderRadius: 4 }}>
          <Text style={{ fontWeight: '600', fontSize: 12 }}>Phản hồi của shop</Text>
          <Text>{item.replyText}</Text>
        </View>
      ) : open ? (
        <View style={{ marginTop: 10 }}>
          <TextInput value={text} onChangeText={setText} multiline maxLength={item.replyMax || 500} placeholder="Viết phản hồi cho khách..."
                     placeholderTextColor={C.faint} style={[styles.input, { minHeight: 90, textAlignVertical: 'top' }]} />
          <Row style={{ gap: 8, marginTop: 8 }}>
            <Button small kind="ghost" title={busy ? '...' : '✨ AI soạn'} onPress={draft} disabled={busy} style={{ flex: 1 }} />
            <Button small title="Gửi phản hồi" onPress={send} disabled={busy} style={{ flex: 1 }} />
          </Row>
        </View>
      ) : (
        <Button small kind="ghost" title={item.draftReply ? 'Xem bản nháp AI & trả lời' : 'Trả lời'} onPress={() => setOpen(true)} style={{ marginTop: 10 }} />
      )}
    </View>
  )
}
