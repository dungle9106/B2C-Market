import React, { useCallback, useEffect, useState } from 'react'
import { Alert, Pressable, ScrollView, Switch, Text, View } from 'react-native'
import { get, post, errorText } from '../api'
import { formatDay, formatVnd } from '../format'
import { BottomBar, Button, C, Empty, Field, Img, Loading, Row, Select, styles } from '../ui'

export default function CheckoutScreen({ route, navigation }) {
  const { cartItemIds, addressId: pickedAddress } = route.params
  const [address, setAddress] = useState(undefined)
  const [groups, setGroups] = useState(null)          // [{ shopId, cartItemIds }]
  const [choice, setChoice] = useState({})            // shopId -> { shippingOption, voucherCode, note }
  const [platformCode, setPlatformCode] = useState(null)
  const [useCoins, setUseCoins] = useState(false)
  const [paymentMethod, setPaymentMethod] = useState('COD')
  const [vouchers, setVouchers] = useState([])
  const [quote, setQuote] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  // the selected cart lines, one group per shop
  useEffect(() => {
    get('/cart').then(cart => {
      const map = new Map()
      for (const it of cart.filter(i => cartItemIds.includes(i.id))) {
        if (!map.has(it.shopId)) map.set(it.shopId, { shopId: it.shopId, cartItemIds: [] })
        map.get(it.shopId).cartItemIds.push(it.id)
      }
      const gs = [...map.values()]
      setGroups(gs)
      get('/vouchers/available', { shopIds: gs.map(g => g.shopId).join(',') }).then(setVouchers).catch(() => {})
    }).catch(e => setError(errorText(e)))
  }, [cartItemIds])

  // default address first, the one picked in the address book afterwards
  useEffect(() => {
    get('/buyer/addresses').then(list => {
      setAddress(list.find(a => a.id === pickedAddress) || list.find(a => a.default) || list[0] || null)
    }).catch(e => setError(errorText(e)))
  }, [pickedAddress])

  const body = useCallback(() => ({
    shops: groups.map(g => ({ cartItemIds: g.cartItemIds, ...choice[g.shopId] })),
    addressId: address?.id,
    paymentMethod,
    platformVoucherCode: platformCode || undefined,
    useCoins
  }), [groups, choice, address, platformCode, useCoins, paymentMethod])

  useEffect(() => {
    if (!groups || address === undefined) return
    post('/orders/preview', body()).then(q => { setQuote(q); setError(null) }).catch(e => setError(errorText(e)))
  }, [groups, address, body])

  const setShop = (shopId, patch) => setChoice(c => ({ ...c, [shopId]: { ...c[shopId], ...patch } }))

  async function placeOrder() {
    if (!address) { Alert.alert('Thiếu địa chỉ', 'Vui lòng thêm địa chỉ nhận hàng'); return }
    setBusy(true)
    try {
      const res = await post('/orders/checkout', body())
      Alert.alert('Đặt hàng thành công', `${res.orders.length} đơn hàng · ${formatVnd(res.amount)}\n${paymentMethod === 'COD' ? 'Thanh toán khi nhận hàng.' : 'Đã thanh toán bằng ví ShoppiPay.'}`)
      navigation.reset({ index: 1, routes: [{ name: 'Tabs' }, { name: 'Orders' }] })
    } catch (e) {
      Alert.alert('Không đặt được hàng', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  if (!quote) return error ? <Empty error={error} /> : <Loading />
  const walletShort = paymentMethod === 'SHOPPI_PAY' && Number(quote.walletBalance) < Number(quote.total)
  const platformVouchers = vouchers.filter(v => v.scope === 'PLATFORM')
  const voucherOpts = (list) => [{ value: null, label: 'Không dùng voucher' },
    ...list.map(v => ({ value: v.code, label: `${v.code}${v.saved ? ' (đã lưu)' : ''}`, hint: `${v.title} · đơn tối thiểu ${formatVnd(v.minOrder || 0)}` }))]

  return (
    <View style={styles.page}>
      <ScrollView>
        <Pressable onPress={() => navigation.navigate('Addresses', { pick: true })} style={[styles.card, { borderTopWidth: 3, borderTopColor: C.primary }]}>
          <Text style={{ color: C.primary, marginBottom: 4 }}>📍 Địa chỉ nhận hàng ›</Text>
          {address ? (
            <>
              <Text style={{ fontWeight: '500' }}>{address.recipientName} | {address.phone}</Text>
              <Text style={styles.muted}>{[address.addressLine, address.ward, address.district, address.province].filter(Boolean).join(', ')}</Text>
            </>
          ) : <Text style={{ color: C.danger }}>Chưa có địa chỉ, bấm để thêm</Text>}
        </Pressable>

        {quote.shops.map(s => (
          <View key={s.shopId} style={styles.card}>
            <Text style={{ fontWeight: '600', marginBottom: 8 }}>🏪 {s.shopName}</Text>
            {s.items.map(it => (
              <Row key={it.id} style={{ gap: 10, marginBottom: 8 }}>
                <Img uri={it.thumbnailUrl} style={{ width: 56, height: 56 }} />
                <View style={{ flex: 1 }}>
                  <Text numberOfLines={1}>{it.productName}</Text>
                  {it.variantName ? <Text style={styles.muted}>{it.variantName}</Text> : null}
                  <Row style={{ justifyContent: 'space-between' }}>
                    <Text>{formatVnd(it.unitPrice)}</Text><Text style={styles.muted}>x{it.quantity}</Text>
                  </Row>
                </View>
              </Row>
            ))}
            <Select label="Voucher của Shop" value={choice[s.shopId]?.voucherCode || null}
                    options={voucherOpts(vouchers.filter(v => v.scope === 'SHOP' && v.shopId === s.shopId))}
                    onChange={code => setShop(s.shopId, { voucherCode: code || undefined })} />
            {s.shopVoucherError ? <Text style={{ color: C.danger, marginTop: -8, marginBottom: 8 }}>{s.shopVoucherError}</Text> : null}
            <Select label="Phương thức vận chuyển" value={s.shipping?.code}
                    options={s.shippingOptions.map(o => ({ value: o.code, label: `${o.carrierName} – ${o.methodName}: ${formatVnd(o.fee)}`, hint: `Nhận hàng ${formatDay(o.etaFrom)} – ${formatDay(o.etaTo)}` }))}
                    onChange={code => setShop(s.shopId, { shippingOption: code })} />
            {s.shipping ? <Text style={[styles.muted, { marginTop: -8, marginBottom: 8 }]}>Nhận hàng {formatDay(s.shipping.etaFrom)} – {formatDay(s.shipping.etaTo)}</Text> : null}
            <Field placeholder="Lời nhắn cho Người bán..." value={choice[s.shopId]?.note || ''} onChangeText={note => setShop(s.shopId, { note })} />
            {(s.notices || []).map(n => <Text key={n} style={{ color: C.green, fontSize: 12 }}>{n}</Text>)}
            <Row style={{ justifyContent: 'space-between' }}>
              <Text style={styles.muted}>Tổng số tiền ({s.quantity} sản phẩm)</Text>
              <Text style={{ color: C.primary, fontWeight: '600' }}>{formatVnd(s.total)}</Text>
            </Row>
          </View>
        ))}

        <View style={styles.card}>
          <Select label="Shoppi Voucher" value={platformCode} options={voucherOpts(platformVouchers)} onChange={setPlatformCode} />
          {quote.platformVoucherError ? <Text style={{ color: C.danger, marginTop: -8 }}>{quote.platformVoucherError}</Text> : null}
          {quote.coinBalance > 0 ? (
            <Row style={{ justifyContent: 'space-between', gap: 10, marginBottom: 10 }}>
              <View style={{ flex: 1 }}>
                <Text>🪙 Shoppi Xu</Text>
                <Text style={styles.muted}>Bạn có {quote.coinBalance.toLocaleString('vi-VN')} Xu, dùng được {quote.coinsUsable.toLocaleString('vi-VN')} Xu cho đơn này</Text>
              </View>
              <Switch value={useCoins} onValueChange={setUseCoins} disabled={!quote.coinsUsable} />
            </Row>
          ) : null}
          <Select label="Phương thức thanh toán" value={paymentMethod} onChange={setPaymentMethod}
                  options={[{ value: 'COD', label: '💵 Thanh toán khi nhận hàng (COD)' },
                    { value: 'SHOPPI_PAY', label: '💳 Ví ShoppiPay', hint: `Số dư ${formatVnd(quote.walletBalance)}` }]} />
          {paymentMethod === 'SHOPPI_PAY' ? (
            <Text style={{ color: walletShort ? C.danger : C.muted, marginTop: -8 }} onPress={() => navigation.navigate('Wallet')}>
              Số dư ví: {formatVnd(quote.walletBalance)}{walletShort ? ', chưa đủ để thanh toán.' : ''} Nạp tiền ›
            </Text>
          ) : null}
        </View>

        <View style={styles.card}>
          <Line label="Tổng tiền hàng" value={quote.merchandiseTotal} />
          <Line label="Phí vận chuyển" value={quote.shippingFeeTotal} />
          {quote.shippingDiscountTotal > 0 ? <Line label="Giảm giá phí vận chuyển" value={-quote.shippingDiscountTotal} /> : null}
          {quote.shopDiscountTotal > 0 ? <Line label="Voucher của Shop" value={-quote.shopDiscountTotal} /> : null}
          {quote.comboDiscountTotal > 0 ? <Line label="Combo / Mua kèm" value={-quote.comboDiscountTotal} /> : null}
          {quote.platformDiscountTotal > 0 ? <Line label="Shoppi Voucher" value={-quote.platformDiscountTotal} /> : null}
          {quote.coinDiscountTotal > 0 ? <Line label="Shoppi Xu" value={-quote.coinDiscountTotal} /> : null}
          <Line label="Tổng thanh toán" value={quote.total} strong />
          {quote.coinsToEarn > 0 ? <Text style={[styles.muted, { textAlign: 'right', marginTop: 4 }]}>Nhận 🪙 {quote.coinsToEarn.toLocaleString('vi-VN')} Xu khi đơn hoàn tất</Text> : null}
        </View>
        {error ? <Text style={{ color: C.danger, padding: 12 }}>{error}</Text> : null}
      </ScrollView>
      <BottomBar>
        <View style={{ flex: 1, alignItems: 'flex-end' }}>
          <Text>Tổng thanh toán</Text>
          <Text style={{ color: C.primary, fontWeight: '700', fontSize: 17 }}>{formatVnd(quote.total)}</Text>
        </View>
        <Button title={busy ? 'Đang đặt...' : 'Đặt hàng'} onPress={placeOrder} disabled={busy || walletShort} />
      </BottomBar>
    </View>
  )
}

const Line = ({ label, value, strong }) => (
  <Row style={{ justifyContent: 'space-between', marginVertical: 3 }}>
    <Text style={strong ? { fontSize: 15 } : styles.muted}>{label}</Text>
    <Text style={strong ? { color: C.primary, fontSize: 18, fontWeight: '600' } : {}}>{value < 0 ? '-' + formatVnd(-value) : formatVnd(value)}</Text>
  </Row>
)
