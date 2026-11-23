import React, { useState } from 'react'
import { Alert, Pressable, ScrollView, Text, View } from 'react-native'
import * as ImagePicker from 'expo-image-picker'
import { post, upload, errorText } from '../api'
import { formatDateTime, formatVnd } from '../format'
import { BottomBar, Button, C, Field, Img, Row, Select, styles } from '../ui'
import DisputeThread from '../common/DisputeThread'

export const RETURN_REASONS = [
  { value: 'DAMAGED', label: 'Hàng bị hư hỏng / lỗi' },
  { value: 'WRONG_ITEM', label: 'Giao sai hàng / sai phân loại' },
  { value: 'NOT_AS_DESCRIBED', label: 'Không đúng mô tả' },
  { value: 'MISSING_ITEM', label: 'Thiếu hàng' },
  { value: 'FAKE', label: 'Hàng giả / hàng nhái' },
  { value: 'OTHER', label: 'Lý do khác' }
]
const TYPES = [
  { value: 'RETURN_AND_REFUND', label: 'Trả hàng và hoàn tiền', hint: 'Gửi trả sản phẩm, nhận lại tiền khi shop nhận hàng' },
  { value: 'REFUND_ONLY', label: 'Chỉ hoàn tiền', hint: 'Không cần gửi trả (hàng thiếu, hỏng hoàn toàn...)' }
]
export const RETURN_STATUS = {
  REQUESTED: ['Chờ shop phản hồi', '#f5a623'],
  REJECTED: ['Shop từ chối', C.danger],
  ESCALATED: ['Đang chờ Shoppi giải quyết', '#2673dd'],
  REFUNDED: ['Đã hoàn tiền', C.green],
  CLOSED: ['Đã đóng', C.muted]
}

// "Tra hang/Hoan tien" (same rules as the web): pick the items, a reason, photos as evidence
export default function ReturnRequestScreen({ route, navigation }) {
  const { order } = route.params
  const returnable = order.items.filter(i => i.returnable !== false)
  const [qty, setQty] = useState(Object.fromEntries(returnable.map(i => [i.id, i.quantity])))
  const [type, setType] = useState('RETURN_AND_REFUND')
  const [reason, setReason] = useState('DAMAGED')
  const [description, setDescription] = useState('')
  const [photos, setPhotos] = useState([])
  const [busy, setBusy] = useState(false)

  const refund = returnable.reduce((s, i) => s + Number(i.priceAtPurchase) * (qty[i.id] || 0), 0)

  async function addPhoto(camera) {
    if (photos.length >= 6) return Alert.alert('Tối đa 6 ảnh bằng chứng')
    const perm = camera ? await ImagePicker.requestCameraPermissionsAsync() : await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!perm.granted) return Alert.alert('Shoppi cần quyền truy cập ' + (camera ? 'camera' : 'thư viện ảnh'))
    const opts = { mediaTypes: ['images'], quality: 0.7 }
    const r = camera ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts)
    if (r.canceled) return
    setBusy(true)
    try {
      const { url } = await upload('/uploads', r.assets[0])
      setPhotos(p => [...p, url])
    } catch (e) {
      Alert.alert('Không tải ảnh lên được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  async function submit() {
    const items = returnable.filter(i => qty[i.id] > 0).map(i => ({ orderItemId: i.id, quantity: qty[i.id] }))
    if (!items.length) return Alert.alert('Chọn ít nhất 1 sản phẩm cần trả')
    if (description.trim().length < 10) return Alert.alert('Hãy mô tả rõ vấn đề (ít nhất 10 ký tự) để shop xử lý nhanh hơn')
    if (!photos.length) return Alert.alert('Hãy thêm ít nhất 1 ảnh chụp sản phẩm thực tế làm bằng chứng')
    setBusy(true)
    try {
      await post(`/orders/${order.id}/returns`, { type, reason, description: description.trim(), evidenceUrls: photos, items })
      Alert.alert('Đã gửi yêu cầu', 'Shop sẽ phản hồi trong thời gian quy định. Bạn theo dõi trạng thái ở trang đơn hàng.')
      navigation.goBack()
    } catch (e) {
      Alert.alert('Không gửi được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={styles.page}>
      <ScrollView contentContainerStyle={{ paddingBottom: 20 }}>
        <View style={styles.card}>
          <Text style={styles.h2}>Sản phẩm cần trả</Text>
          {returnable.map(i => (
            <Row key={i.id} style={{ gap: 10, marginBottom: 10 }}>
              <Img uri={i.thumbnailUrl} style={{ width: 50, height: 50 }} />
              <View style={{ flex: 1 }}>
                <Text numberOfLines={2}>{i.productName}</Text>
                {i.variantName ? <Text style={styles.muted}>{i.variantName}</Text> : null}
                <Text style={{ color: C.primary }}>{formatVnd(i.priceAtPurchase)}</Text>
              </View>
              <Row style={{ gap: 6 }}>
                <Stepper label="−" onPress={() => setQty(q => ({ ...q, [i.id]: Math.max(0, (q[i.id] || 0) - 1) }))} />
                <Text style={{ minWidth: 18, textAlign: 'center' }}>{qty[i.id] || 0}</Text>
                <Stepper label="+" onPress={() => setQty(q => ({ ...q, [i.id]: Math.min(i.quantity, (q[i.id] || 0) + 1) }))} />
              </Row>
            </Row>
          ))}
        </View>
        <View style={styles.card}>
          <Select label="Bạn muốn" value={type} options={TYPES} onChange={setType} />
          <Select label="Lý do" value={reason} options={RETURN_REASONS} onChange={setReason} />
          <Field label="Mô tả vấn đề" value={description} onChangeText={setDescription} multiline maxLength={2000}
                 placeholder="Ví dụ: áo bị rách một đường ở tay khi mở hộp" />
          <Text style={styles.label}>Ảnh bằng chứng ({photos.length}/6)</Text>
          <Row style={{ flexWrap: 'wrap', gap: 8 }}>
            {photos.map((u, k) => (
              <Pressable key={u} onLongPress={() => setPhotos(p => p.filter((_, x) => x !== k))}>
                <Img uri={u} style={{ width: 70, height: 70, borderRadius: 4 }} />
              </Pressable>
            ))}
            <Button small kind="ghost" title="📷 Chụp" onPress={() => addPhoto(true)} disabled={busy} />
            <Button small kind="ghost" title="🖼 Thư viện" onPress={() => addPhoto(false)} disabled={busy} />
          </Row>
          <Text style={[styles.muted, { marginTop: 6, fontSize: 12 }]}>Chụp ảnh sản phẩm bạn nhận được (giữ lâu vào ảnh để xóa). Ảnh tải từ mạng hoặc ảnh dùng lại của đơn khác sẽ bị hệ thống phát hiện.</Text>
        </View>
      </ScrollView>
      <BottomBar>
        <View style={{ flex: 1 }}>
          <Text style={styles.muted}>Hoàn tối đa (trừ phần giảm giá đã hưởng)</Text>
          <Text style={{ color: C.primary, fontWeight: '700', fontSize: 17 }}>{formatVnd(refund)}</Text>
        </View>
        <Button title={busy ? 'Đang gửi...' : 'Gửi yêu cầu'} onPress={submit} disabled={busy} />
      </BottomBar>
    </View>
  )
}

const Stepper = ({ label, onPress }) => (
  <Pressable onPress={onPress} style={{ width: 28, height: 28, borderWidth: 1, borderColor: C.border, alignItems: 'center', justifyContent: 'center' }}>
    <Text>{label}</Text>
  </Pressable>
)

// the latest return request of an order, shown on the order page; the buyer can withdraw it or complain to Shoppi
export function ReturnCard({ order, onChanged }) {
  const r = order.returnRequest
  const [complaint, setComplaint] = useState('')
  const [writing, setWriting] = useState(false)
  const [busy, setBusy] = useState(false)
  if (!r) return null
  const [label, color] = RETURN_STATUS[r.status] || [r.status, C.muted]

  async function run(fn, done) {
    setBusy(true)
    try {
      await fn()
      Alert.alert(done)
      setWriting(false)
      onChanged()
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={styles.card}>
      <Row style={{ justifyContent: 'space-between' }}>
        <Text style={styles.h2}>↩ Trả hàng/Hoàn tiền</Text>
        <Text style={{ color, fontWeight: '600' }}>{label}</Text>
      </Row>
      <Text>{RETURN_REASONS.find(x => x.value === r.reason)?.label} · {r.type === 'REFUND_ONLY' ? 'Chỉ hoàn tiền' : 'Trả hàng và hoàn tiền'}</Text>
      <Text style={styles.muted}>Hoàn {formatVnd(r.refundAmount)} · gửi lúc {formatDateTime(r.createdAt)}</Text>
      {r.description ? <Text style={{ marginTop: 6 }}>“{r.description}”</Text> : null}
      {r.heldUntil ? <Text style={{ marginTop: 6, color: '#2673dd' }}>Shoppi đang kiểm tra yêu cầu này, hoàn tiền sau khi xác minh (tới {formatDateTime(r.heldUntil)}).</Text> : null}
      {r.sellerNote ? <Text style={{ marginTop: 6 }}><Text style={{ fontWeight: '600' }}>Shop: </Text>{r.sellerNote}</Text> : null}
      {r.adminNote ? <Text style={{ marginTop: 6 }}><Text style={{ fontWeight: '600' }}>Shoppi: </Text>{r.adminNote}</Text> : null}

      {r.status === 'REQUESTED' ? (
        <Button small kind="ghost" title="Rút yêu cầu" style={{ marginTop: 10 }} disabled={busy}
                onPress={() => Alert.alert('Rút yêu cầu trả hàng?', '', [{ text: 'Không' }, { text: 'Rút', onPress: () => run(() => post(`/buyer/returns/${r.id}/withdraw`), 'Đã rút yêu cầu') }])} />
      ) : null}
      {r.status === 'REJECTED' && !writing ? <Button small title="Khiếu nại lên Shoppi" style={{ marginTop: 10 }} onPress={() => setWriting(true)} /> : null}
      {writing ? (
        <View style={{ marginTop: 10 }}>
          <Field label="Vì sao bạn không đồng ý với shop?" value={complaint} onChangeText={setComplaint} multiline maxLength={2000} />
          <Row style={{ gap: 8 }}>
            <Button kind="ghost" title="Đóng" onPress={() => setWriting(false)} style={{ flex: 1 }} />
            <Button title="Gửi khiếu nại" disabled={busy || complaint.trim().length < 10} style={{ flex: 1 }}
                    onPress={() => run(() => post(`/buyer/returns/${r.id}/escalate`, { complaint: complaint.trim() }), 'Đã gửi khiếu nại, Shoppi sẽ xem xét và phản hồi')} />
          </Row>
        </View>
      ) : null}
      {r.complaint ? <DisputeThread returnId={r.id} side="buyer" open={r.status === 'ESCALATED'} /> : null}
    </View>
  )
}
