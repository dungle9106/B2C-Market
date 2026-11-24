import React, { useState } from 'react'
import { Alert, FlatList, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native'
import { get, post, patch, errorText } from '../api'
import { BUYER_CANCEL_REASONS, ORDER_STATUS, formatDateTime, formatVnd } from '../format'
import { Button, C, Empty, Field, Img, Loading, Row, Select, styles, Tabs, useLoad } from '../ui'
import { ReturnCard } from './ReturnScreens'

const TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xác nhận' },
  { key: 'CONFIRMED', label: 'Chờ lấy hàng' },
  { key: 'SHIPPING', label: 'Đang giao' },
  { key: 'DELIVERED', label: 'Đã giao' },
  { key: 'CANCELLED', label: 'Đã hủy' }
]

export function OrderItems({ order }) {
  return order.items.map(it => (
    <Row key={it.id} style={{ gap: 10, marginVertical: 5 }}>
      <Img uri={it.thumbnailUrl} style={{ width: 60, height: 60 }} />
      <View style={{ flex: 1 }}>
        <Text numberOfLines={2}>{it.productName}</Text>
        {it.variantName ? <Text style={styles.muted}>Phân loại: {it.variantName}</Text> : null}
        <Row style={{ justifyContent: 'space-between' }}>
          <Text style={styles.muted}>x{it.quantity}</Text>
          <Text>{formatVnd(it.priceAtPurchase)}</Text>
        </Row>
      </View>
    </Row>
  ))
}

export default function OrdersScreen({ navigation }) {
  const [tab, setTab] = useState('ALL')
  const { data, error, refreshing, refresh, reload } = useLoad(() => get('/orders'))
  const list = (data || []).filter(o => tab === 'ALL' || o.status === tab || (tab === 'CANCELLED' && ['CANCELLED', 'REFUNDED'].includes(o.status)))
  return (
    <View style={styles.page}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexGrow: 0 }}>
        <View style={{ width: 640 }}><Tabs items={TABS} value={tab} onChange={setTab} /></View>
      </ScrollView>
      {!data ? (error ? <Empty error={error} onRetry={reload} /> : <Loading />) : (
        <FlatList data={list} keyExtractor={o => String(o.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                  ListEmptyComponent={<Empty text="Chưa có đơn hàng" />}
                  renderItem={({ item: o }) => (
                    <Pressable onPress={() => navigation.navigate('OrderDetail', { id: o.id })} style={styles.card}>
                      <Row style={{ justifyContent: 'space-between', marginBottom: 6 }}>
                        <Text style={{ fontWeight: '600' }}>🏪 {o.shopName}</Text>
                        <Text style={{ color: C.primary }}>{ORDER_STATUS[o.status]}</Text>
                      </Row>
                      <OrderItems order={o} />
                      <Text style={{ textAlign: 'right', marginTop: 6 }}>
                        Thành tiền: <Text style={{ color: C.primary, fontWeight: '600' }}>{formatVnd(o.total)}</Text>
                      </Text>
                    </Pressable>
                  )} />
      )}
    </View>
  )
}

export function OrderDetailScreen({ route, navigation }) {
  const { id } = route.params
  const { data: o, error, reload } = useLoad(() => get(`/orders/${id}`), [id])
  const [cancelling, setCancelling] = useState(false)
  const [reason, setReason] = useState(BUYER_CANCEL_REASONS[0])
  const [busy, setBusy] = useState(false)

  if (!o) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  async function run(fn, done) {
    setBusy(true)
    try {
      await fn()
      if (done) Alert.alert(done)
      setCancelling(false)
      reload()
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const received = () => Alert.alert('Đã nhận được hàng?', 'Xác nhận bạn đã nhận hàng và sản phẩm không có vấn đề gì.',
    [{ text: 'Chưa' }, { text: 'Đã nhận', onPress: () => run(() => post(`/orders/${id}/received`), 'Cảm ơn bạn đã mua hàng!') }])
  const rebuy = () => run(async () => {
    const r = await post(`/orders/${id}/rebuy`)
    navigation.navigate('Cart')
    return r
  })

  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 24 }}>
      <View style={[styles.card, { backgroundColor: C.green }]}>
        <Text style={{ color: 'white', fontSize: 17, fontWeight: '600' }}>{ORDER_STATUS[o.status]}</Text>
        {o.estimatedDeliveryFrom && ['PENDING', 'CONFIRMED', 'SHIPPING'].includes(o.status)
          ? <Text style={{ color: 'white', marginTop: 4 }}>Dự kiến giao {o.estimatedDeliveryFrom} – {o.estimatedDeliveryTo}</Text> : null}
        {o.cancelReason ? <Text style={{ color: 'white', marginTop: 4 }}>Lý do hủy: {o.cancelReason}</Text> : null}
      </View>

      <View style={styles.card}>
        <Text style={styles.h2}>🚚 Thông tin vận chuyển</Text>
        <Text>{o.carrierName} – {o.shippingMethodName}{o.trackingNumber ? ` · Mã vận đơn ${o.trackingNumber}` : ''}</Text>
        <View style={{ marginTop: 10 }}>
          {[...(o.events || [])].reverse().map((e, i) => (
            <Row key={i} style={{ alignItems: 'flex-start', gap: 10, marginBottom: 10 }}>
              <View style={{ width: 10, height: 10, borderRadius: 5, marginTop: 4, backgroundColor: i === 0 ? C.green : C.faint }} />
              <View style={{ flex: 1 }}>
                <Text style={{ color: i === 0 ? C.green : C.text }}>{e.title}</Text>
                <Text style={{ fontSize: 12, color: C.faint }}>{formatDateTime(e.time)}{e.location ? ` · ${e.location}` : ''}</Text>
              </View>
            </Row>
          ))}
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.h2}>📍 Địa chỉ nhận hàng</Text>
        <Text>{o.shippingAddress}</Text>
      </View>

      <View style={styles.card}>
        <Pressable onPress={() => navigation.navigate('Shop', { id: o.shopId })}><Text style={{ fontWeight: '600' }}>🏪 {o.shopName} ›</Text></Pressable>
        <OrderItems order={o} />
        {o.items.map(it => (o.status === 'DELIVERED' && !it.reviewed ? (
          <Button key={it.id} small kind="ghost" title={`Đánh giá: ${it.productName}`} style={{ marginTop: 6 }}
                  onPress={() => navigation.navigate('Review', { item: it })} />
        ) : null))}
        <View style={styles.divider} />
        <Line label="Tổng tiền hàng" value={formatVnd(o.subtotal)} />
        <Line label="Phí vận chuyển" value={formatVnd(o.shippingFee)} />
        {o.discount > 0 ? <Line label="Giảm giá" value={'-' + formatVnd(o.discount)} /> : null}
        {o.coinDiscount > 0 ? <Line label="Shoppi Xu" value={'-' + formatVnd(o.coinDiscount)} /> : null}
        <Line label="Thành tiền" value={formatVnd(o.total)} strong />
        {o.coinsToEarn > 0 && !o.coinsAwarded && !['CANCELLED', 'REFUNDED'].includes(o.status) ? <Line label="Xu nhận được khi đơn hoàn tất" value={`🪙 ${o.coinsToEarn}`} /> : null}
        <Text style={[styles.muted, { marginTop: 6 }]}>Mã đơn {o.code} · đặt lúc {formatDateTime(o.createdAt)}</Text>
      </View>

      <ReturnCard order={o} onChanged={reload} />

      {cancelling ? (
        <View style={styles.card}>
          <Select label="Lý do hủy đơn" value={reason} options={BUYER_CANCEL_REASONS} onChange={setReason} />
          <Row style={{ gap: 10 }}>
            <Button title="Đóng" kind="ghost" onPress={() => setCancelling(false)} style={{ flex: 1 }} />
            <Button title={o.canCancel ? 'Hủy đơn hàng' : 'Gửi yêu cầu hủy'} onPress={() => run(() => patch(`/orders/${id}/cancel`, { reason }))} disabled={busy} style={{ flex: 1 }} />
          </Row>
        </View>
      ) : null}

      <View style={{ paddingHorizontal: 12, gap: 8 }}>
        {o.status === 'AWAITING_PAYMENT' && o.checkoutCode ? <Button title="Thanh toán ngay" onPress={() => navigation.navigate('Payment', { code: o.checkoutCode })} /> : null}
        {(o.canCancel || o.canRequestCancel) && !cancelling ? <Button title="Hủy đơn hàng" kind="ghost" onPress={() => setCancelling(true)} /> : null}
        {o.canConfirmReceived ? <Button title="Đã nhận được hàng" onPress={received} disabled={busy} /> : null}
        {o.canReturn ? (
          <Button title={`↩ Trả hàng/Hoàn tiền${o.returnDeadline ? ' (hạn ' + formatDateTime(o.returnDeadline) + ')' : ''}`} kind="ghost"
                  onPress={() => navigation.navigate('ReturnRequest', { order: o })} />
        ) : null}
        {['DELIVERED', 'CANCELLED'].includes(o.status) ? <Button title="Mua lại" kind="ghost" onPress={rebuy} disabled={busy} /> : null}
        <Button title="💬 Liên hệ Shop" kind="ghost" onPress={async () => {
          const c = await post('/buyer/chats', { shopId: o.shopId })
          navigation.navigate('ChatThread', { id: c.id, title: o.shopName })
        }} />
        <Button title="🛟 Cần hỗ trợ với đơn này" kind="ghost" onPress={() => navigation.navigate('NewTicket', { orderId: o.id, category: 'ORDER' })} />
      </View>
    </ScrollView>
  )
}

const Line = ({ label, value, strong }) => (
  <Row style={{ justifyContent: 'space-between', marginVertical: 2 }}>
    <Text style={strong ? {} : styles.muted}>{label}</Text>
    <Text style={strong ? { color: C.primary, fontWeight: '600', fontSize: 16 } : {}}>{value}</Text>
  </Row>
)

export function ReviewScreen({ route, navigation }) {
  const { item } = route.params
  const [rating, setRating] = useState(5)
  const [shopRating, setShopRating] = useState(5)
  const [shippingRating, setShippingRating] = useState(5)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit() {
    setBusy(true)
    try {
      await post('/buyer/reviews', { orderItemId: item.id, rating, comment, imageUrls: [], shopRating, shippingRating })
      Alert.alert('Cảm ơn bạn đã đánh giá!')
      navigation.goBack()
    } catch (e) {
      Alert.alert('Không gửi được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const StarPick = ({ label, value, onChange }) => (
    <Row style={{ justifyContent: 'space-between', marginBottom: 12 }}>
      <Text>{label}</Text>
      <Row>{[1, 2, 3, 4, 5].map(n => <Text key={n} onPress={() => onChange(n)} style={{ fontSize: 28, color: n <= value ? '#ffce3d' : C.faint }}>★</Text>)}</Row>
    </Row>
  )
  return (
    <ScrollView style={styles.page} contentContainerStyle={{ padding: 14 }}>
      <Row style={{ gap: 10, marginBottom: 16 }}>
        <Img uri={item.thumbnailUrl} style={{ width: 50, height: 50 }} />
        <Text style={{ flex: 1 }}>{item.productName}</Text>
      </Row>
      <StarPick label="Chất lượng sản phẩm" value={rating} onChange={setRating} />
      <StarPick label="Dịch vụ của Shop" value={shopRating} onChange={setShopRating} />
      <StarPick label="Vận chuyển" value={shippingRating} onChange={setShippingRating} />
      <Field placeholder="Hãy chia sẻ nhận xét cho sản phẩm này bạn nhé!" value={comment} onChangeText={setComment} multiline />
      <Button title={busy ? 'Đang gửi...' : 'Gửi đánh giá'} onPress={submit} disabled={busy} />
    </ScrollView>
  )
}
