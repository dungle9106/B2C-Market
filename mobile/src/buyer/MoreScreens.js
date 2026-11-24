// Buyer pages the web has and the app lacked: the online payment page, Shoppi campaigns and Flash Sale slots, and the
// product page's extras (shipping estimate, shop vouchers, combos, add-on deals, questions & answers, report).
import React, { useEffect, useState } from 'react'
import { Alert, Linking, Pressable, ScrollView, Text, View } from 'react-native'
import { get, post, errorText } from '../api'
import { formatDateTime, formatVnd } from '../format'
import { PROVINCES } from '../provinces'
import { Button, C, Empty, Field, Img, Loading, ProductCard, Row, Select, styles, useLoad } from '../ui'
import { Ticket } from './AccountScreens'

// ==================== online payment (web: PaymentView) ====================

const BRANDS = {
  MOMO: { name: 'Ví MoMo', color: '#a50064' },
  ZALOPAY: { name: 'ZaloPay', color: '#0068ff' },
  VNPAY: { name: 'VNPay', color: '#005baa' },
  CARD: { name: 'Thẻ Tín dụng/Ghi nợ', color: '#1a1f71' },
  BANK_TRANSFER: { name: 'Chuyển khoản ngân hàng', color: '#0f7a3d' }
}

function useCountdown(until) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t) }, [])
  const left = Math.max(0, Math.floor((new Date(until).getTime() - now) / 1000))
  return { left, text: `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}` }
}

export function PaymentScreen({ route, navigation }) {
  const { code } = route.params
  const { data: pay, setData: setPay, error, reload } = useLoad(() => get(`/payments/${code}`), [code])
  const [card, setCard] = useState({ number: '', holder: '', expiry: '', cvv: '' })
  const [busy, setBusy] = useState(false)
  const { left, text } = useCountdown(pay?.expiresAt)
  if (!pay) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  const brand = pay.method === 'E_WALLET' ? BRANDS[pay.provider] || BRANDS.MOMO : BRANDS[pay.method] || BRANDS.CARD
  async function run(fn) {
    setBusy(true)
    try {
      setPay(await fn())
      setCard(c => ({ ...c, cvv: '' }))   // never keep card data longer than needed
    } catch (e) {
      Alert.alert('Thanh toán chưa thành công', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  const payNow = (body = {}) => run(() => post(`/payments/${code}/pay`, body))
  const vnpay = async () => {
    setBusy(true)
    try {
      await Linking.openURL((await post(`/payments/${code}/vnpay`)).url)
    } catch (e) {
      Alert.alert('Không mở được VNPay', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  const setC = (k, f) => (v) => setCard(c => ({ ...c, [k]: f ? f(v) : v }))
  const digits = (v) => v.replace(/\D/g, '').slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 ')
  const expiry = (v) => { const d = v.replace(/\D/g, '').slice(0, 4); return d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d }

  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 30 }} keyboardShouldPersistTaps="handled">
      <Text style={{ backgroundColor: pay.method === 'VNPAY' ? '#e8f1fb' : '#fff8e1', color: pay.method === 'VNPAY' ? '#005baa' : '#b26a00', padding: 10, fontSize: 12, textAlign: 'center' }}>
        {pay.method === 'VNPAY' ? 'Thanh toán an toàn qua cổng VNPay, Shoppi không lưu thông tin thẻ' : 'MÔI TRƯỜNG THỬ NGHIỆM: không nhập thẻ thật, không có tiền thật bị trừ'}
      </Text>
      <View style={styles.card}>
        <Row style={{ gap: 10 }}>
          <View style={{ width: 44, height: 44, borderRadius: 8, backgroundColor: brand.color, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ color: 'white', fontWeight: '700' }}>{brand.name.slice(0, 2)}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={{ fontWeight: '600' }}>{brand.name}</Text>
            <Text style={[styles.muted, { fontSize: 12 }]}>Mã giao dịch {pay.checkoutCode}</Text>
          </View>
          {pay.status === 'AWAITING_PAYMENT' ? <Text style={{ color: left < 120 ? C.danger : C.text }}>Hết hạn sau {text}</Text> : null}
        </Row>
        <Text style={[styles.muted, { marginTop: 14 }]}>Số tiền thanh toán</Text>
        <Text style={{ fontSize: 26, color: C.primary, fontWeight: '600' }}>{formatVnd(pay.amount)}</Text>
        {(pay.orders || []).map(o => (
          <Row key={o.id} style={{ justifyContent: 'space-between', marginTop: 4 }}>
            <Text style={styles.muted}>{o.shopName} · {o.code}</Text><Text>{formatVnd(o.total)}</Text>
          </Row>
        ))}
      </View>

      {pay.status === 'PAID' ? (
        <View style={[styles.card, { alignItems: 'center' }]}>
          <Text style={{ fontSize: 40, color: C.green }}>✓</Text>
          <Text style={styles.h2}>Thanh toán thành công</Text>
          <Text style={styles.muted}>Đơn hàng đã được gửi tới Người bán để xác nhận.</Text>
          <Button title="Xem đơn hàng" style={{ marginTop: 12 }} onPress={() => navigation.navigate('Orders')} />
        </View>
      ) : pay.status === 'CANCELLED' ? (
        <View style={[styles.card, { alignItems: 'center' }]}>
          <Text style={{ fontSize: 40, color: C.danger }}>✕</Text>
          <Text style={styles.h2}>Giao dịch đã hủy</Text>
          <Text style={styles.muted}>Đơn hàng đã được hủy và sản phẩm đã được hoàn lại kho.</Text>
        </View>
      ) : (
        <View style={styles.card}>
          {pay.method === 'VNPAY' ? (
            <>
              <Text>Bạn sẽ được chuyển sang trang <Text style={{ fontWeight: '600' }}>VNPay</Text> để thanh toán bằng thẻ ATM, thẻ quốc tế hoặc QR ngân hàng. Thanh toán xong quay lại app và kéo để tải lại.</Text>
              {pay.gatewayMessage ? <Text style={[styles.muted, { marginTop: 6 }]}>Lần thử trước: {pay.gatewayMessage}</Text> : null}
              <Button title={busy ? 'Đang chuyển...' : 'Thanh toán qua VNPay'} disabled={busy} onPress={vnpay} style={{ marginTop: 12 }} />
              <Button title="Tôi đã thanh toán, kiểm tra lại" kind="ghost" onPress={reload} style={{ marginTop: 8 }} />
            </>
          ) : pay.method === 'E_WALLET' ? (
            <>
              <Text>Mở ứng dụng <Text style={{ fontWeight: '600' }}>{brand.name}</Text> và quét mã để thanh toán.</Text>
              <Text style={[styles.muted, { marginTop: 4 }]}>(Mô phỏng: bấm nút bên dưới để giả lập đã quét và xác nhận trên ứng dụng ví.)</Text>
              <Button title={busy ? 'Đang xử lý...' : 'Xác nhận thanh toán'} disabled={busy} onPress={() => payNow()} style={{ marginTop: 12 }} />
            </>
          ) : pay.method === 'CARD' ? (
            <>
              <Field label="Số thẻ" value={card.number} onChangeText={setC('number', digits)} keyboardType="number-pad" placeholder="1234 5678 9012 3456" />
              <Field label="Tên chủ thẻ" value={card.holder} onChangeText={setC('holder', v => v.toUpperCase())} placeholder="NGUYEN VAN A" autoCapitalize="characters" />
              <Row style={{ gap: 10 }}>
                <Field label="Ngày hết hạn" value={card.expiry} onChangeText={setC('expiry', expiry)} keyboardType="number-pad" placeholder="MM/YY" style={{ flex: 1 }} />
                <Field label="CVV" value={card.cvv} onChangeText={setC('cvv', v => v.replace(/\D/g, '').slice(0, 4))} keyboardType="number-pad" secureTextEntry style={{ flex: 1 }} />
              </Row>
              <Text style={[styles.muted, { fontSize: 12 }]}>Thẻ thử: 4111 1111 1111 1111 (thành công) · 4000 0000 0000 0002 (bị từ chối)</Text>
              <Button title={busy ? 'Đang xử lý...' : `Thanh toán ${formatVnd(pay.amount)}`} disabled={busy} style={{ marginTop: 12 }}
                      onPress={() => payNow({ cardNumber: card.number, cardHolder: card.holder, expiry: card.expiry, cvv: card.cvv })} />
            </>
          ) : pay.method === 'BANK_TRANSFER' && pay.bank ? (
            <>
              {[['Ngân hàng', pay.bank.bankName], ['Số tài khoản', pay.bank.accountNumber], ['Chủ tài khoản', pay.bank.accountName],
                ['Số tiền', formatVnd(pay.amount)], ['Nội dung', pay.bank.transferNote]].map(([k, v]) => (
                <Row key={k} style={{ justifyContent: 'space-between', paddingVertical: 5 }}>
                  <Text style={styles.muted}>{k}</Text><Text selectable style={{ fontWeight: '600' }}>{v}</Text>
                </Row>
              ))}
              <Text style={[styles.muted, { marginTop: 6 }]}>Ghi đúng nội dung chuyển khoản để hệ thống tự xác nhận. (Mô phỏng: bấm nút để giả lập ngân hàng đã nhận tiền.)</Text>
              <Button title={busy ? 'Đang xử lý...' : 'Tôi đã chuyển khoản'} disabled={busy} onPress={() => payNow()} style={{ marginTop: 12 }} />
            </>
          ) : null}
          <Text style={{ color: C.muted, textAlign: 'center', marginTop: 16 }}
                onPress={() => Alert.alert('Hủy giao dịch?', 'Đơn hàng sẽ bị hủy.', [{ text: 'Không' }, { text: 'Hủy giao dịch', style: 'destructive', onPress: () => run(() => post(`/payments/${code}/cancel`)) }])}>
            Hủy giao dịch
          </Text>
        </View>
      )}
    </ScrollView>
  )
}

// ==================== Shoppi Flash Sale slots and campaigns (web: FlashSaleSection, CampaignView) ====================

const hm = (s) => { const d = new Date(s); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }

function SlotProducts({ slot, navigation }) {
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', paddingHorizontal: 4 }}>
      {(slot.products || []).map(x => (
        <View key={x.product.id} style={{ width: '50%' }}>
          <ProductCard p={{ ...x.product, price: x.flashPrice, discountPercent: x.discountPercent, flashSale: true }}
                       onPress={() => navigation.navigate('Product', { id: x.product.id })} />
          {x.flashStock ? (
            <Text style={{ marginHorizontal: 8, marginTop: -6, marginBottom: 6, fontSize: 11, textAlign: 'center', color: 'white', backgroundColor: '#f69113', borderRadius: 8, overflow: 'hidden' }}>
              Đã bán {x.flashSold || 0}/{x.flashStock}
            </Text>
          ) : null}
        </View>
      ))}
    </View>
  )
}

export function FlashSaleScreen({ navigation }) {
  const { data: slots, error, reload } = useLoad(() => get('/flash-sale/slots', { limit: 12 }))
  const [pick, setPick] = useState(0)
  const { data: slot } = useLoad(() => (slots?.[pick] ? get(`/flash-sale/slots/${slots[pick].id}`, { limit: 60 }) : Promise.resolve(null)), [slots, pick])
  if (!slots) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  if (!slots.length) return <Empty text="Chưa có khung giờ Flash Sale nào của Shoppi. Hãy quay lại sau nhé!" />
  return (
    <ScrollView style={styles.page}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ backgroundColor: '#222' }}>
        {slots.map((s, i) => (
          <Pressable key={s.id} onPress={() => setPick(i)} style={{ paddingVertical: 10, paddingHorizontal: 16, backgroundColor: i === pick ? C.primary : 'transparent', alignItems: 'center' }}>
            <Text style={{ color: 'white', fontSize: 17, fontWeight: '600' }}>{hm(s.startAt)}</Text>
            <Text style={{ color: 'white', fontSize: 11 }}>{s.status === 'RUNNING' ? 'Đang diễn ra' : 'Sắp diễn ra'}</Text>
          </Pressable>
        ))}
      </ScrollView>
      {slot ? (
        <>
          <Text style={[styles.muted, { padding: 10 }]}>⚡ {slot.name} · {formatDateTime(slot.startAt)} – {hm(slot.endAt)}</Text>
          {slot.products?.length ? <SlotProducts slot={slot} navigation={navigation} /> : <Empty text="Khung giờ này chưa có sản phẩm" />}
        </>
      ) : <Loading />}
    </ScrollView>
  )
}

export function CampaignScreen({ route, navigation }) {
  const { slug } = route.params
  const { data: c, error, reload } = useLoad(() => get(`/campaigns/${slug}`).then(x => { navigation.setOptions({ title: x.name }); return x }), [slug])
  const [saved, setSaved] = useState(new Set())
  if (!c) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const save = async (v) => {
    try {
      await post(`/buyer/voucher-wallet/${v.id}`)
      setSaved(s => new Set(s).add(v.id))
    } catch (e) { Alert.alert('Không lưu được', errorText(e)) }
  }
  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 24 }}>
      {c.bannerUrl ? <Img uri={c.bannerUrl} style={{ width: '100%', aspectRatio: 10 / 4 }} /> : null}
      <View style={[styles.card, { backgroundColor: c.themeColor || C.primary }]}>
        <Text style={{ color: 'white', fontSize: 20, fontWeight: '700' }}>{c.name}</Text>
        {c.tagline ? <Text style={{ color: 'white', marginTop: 2 }}>{c.tagline}</Text> : null}
        <Text style={{ color: 'white', opacity: 0.85, fontSize: 12, marginTop: 4 }}>{formatDateTime(c.startAt)} – {formatDateTime(c.endAt)}</Text>
      </View>
      {c.description ? <Text style={[styles.card, { lineHeight: 20 }]}>{c.description}</Text> : null}
      {c.vouchers?.length ? (
        <View style={{ marginBottom: 8 }}>
          <Text style={[styles.h2, { paddingHorizontal: 12, paddingTop: 4 }]}>🎟 Voucher của chương trình</Text>
          {c.vouchers.map(v => (
            <Ticket key={v.id} v={v}>
              <Button small title={saved.has(v.id) ? 'Đã lưu' : 'Lưu'} disabled={saved.has(v.id)} onPress={() => save(v)} />
            </Ticket>
          ))}
        </View>
      ) : null}
      {(c.flashSales || []).map(s => (
        <View key={s.id} style={{ marginTop: 8 }}>
          <Text style={[styles.h2, { paddingHorizontal: 12 }]}>⚡ {s.name} · {hm(s.startAt)} {new Date(s.startAt).toLocaleDateString('vi-VN')}</Text>
          <SlotProducts slot={s} navigation={navigation} />
        </View>
      ))}
    </ScrollView>
  )
}

// ==================== product page extras (web: ProductDetailView) ====================

export function ProductExtras({ p, navigation }) {
  const [province, setProvince] = useState(null)
  const [ship, setShip] = useState(null)
  const [vouchers, setVouchers] = useState([])
  const [saved, setSaved] = useState(new Set())
  const [combos, setCombos] = useState([])
  const [deals, setDeals] = useState([])
  const [qa, setQa] = useState(null)
  const [question, setQuestion] = useState('')
  const [asking, setAsking] = useState(false)

  useEffect(() => {
    get('/buyer/addresses').then(list => {
      const home = list.find(a => a.default) || list[0]
      setProvince(home?.province && PROVINCES.includes(home.province) ? home.province : 'TP. Hồ Chí Minh')
    }).catch(() => setProvince('TP. Hồ Chí Minh'))
    get(`/shops/${p.shopId}/vouchers`, { productId: p.id }).then(setVouchers).catch(() => {})
    get(`/products/${p.id}/combos`).then(setCombos).catch(() => {})
    get(`/products/${p.id}/addon-deals`).then(setDeals).catch(() => {})
    get(`/products/${p.id}/questions`, { size: 5 }).then(setQa).catch(() => {})
  }, [p.id, p.shopId])
  useEffect(() => {
    if (province) get(`/products/${p.id}/shipping`, { province }).then(setShip).catch(() => setShip(null))
  }, [p.id, province])

  async function ask() {
    setAsking(true)
    try {
      await post('/questions', { productId: p.id, question: question.trim() })
      setQuestion('')
      Alert.alert('Đã gửi câu hỏi', 'Shop sẽ trả lời sớm, bạn sẽ nhận được thông báo.')
      setQa(await get(`/products/${p.id}/questions`, { size: 5 }))
    } catch (e) {
      Alert.alert('Không gửi được', errorText(e))
    } finally {
      setAsking(false)
    }
  }
  const saveVoucher = async (v) => {
    try {
      await post(`/buyer/voucher-wallet/${v.id}`)
      setSaved(s => new Set(s).add(v.id))
    } catch (e) { Alert.alert('Không lưu được', errorText(e)) }
  }
  // the cheapest carrier for each kind of delivery, as on the web
  const seen = new Set()
  const options = (ship?.options || []).filter(o => !seen.has(o.methodName) && seen.add(o.methodName))
  const day = (d) => { const [, m, dd] = String(d).split('-'); return `${dd}/${m}` }

  return (
    <>
      <View style={styles.card}>
        <Text style={styles.h2}>🚚 Vận chuyển</Text>
        <Select label={ship?.from ? `Từ ${ship.from} tới` : 'Giao tới'} value={province} onChange={setProvince} options={PROVINCES} />
        {options.map(o => (
          <Row key={o.methodName} style={{ justifyContent: 'space-between', paddingVertical: 3 }}>
            <Text style={{ flex: 1 }}>{o.methodName} · {o.carrierName}</Text>
            <Text style={styles.muted}>{day(o.etaFrom)}–{day(o.etaTo)} · </Text>
            <Text style={{ color: Number(o.feeToPay) === 0 ? C.green : C.text }}>{Number(o.feeToPay) === 0 ? 'Miễn phí' : formatVnd(o.feeToPay)}</Text>
          </Row>
        ))}
        {ship?.freeShipMinOrder ? <Text style={[styles.muted, { marginTop: 4 }]}>Shop miễn phí vận chuyển cho đơn từ {formatVnd(ship.freeShipMinOrder)}</Text> : null}
      </View>

      {vouchers.length ? (
        <View style={{ marginBottom: 8 }}>
          <Text style={[styles.h2, { paddingHorizontal: 12, paddingTop: 4, marginBottom: 0 }]}>🎟 Voucher của Shop</Text>
          {vouchers.map(v => (
            <Ticket key={v.id} v={v}>
              <Button small title={saved.has(v.id) ? 'Đã lưu' : 'Lưu'} disabled={saved.has(v.id)} onPress={() => saveVoucher(v)} />
            </Ticket>
          ))}
        </View>
      ) : null}

      {combos.map(c => (
        <View key={c.id} style={styles.card}>
          <Text style={styles.h2}>🎁 Combo: {c.name}</Text>
          <Row style={{ gap: 6, flexWrap: 'wrap', marginBottom: 6 }}>
            {(c.tiers || []).map(t => <Text key={t.minQuantity} style={{ color: C.primary, borderWidth: 1, borderColor: C.primary, paddingHorizontal: 6, fontSize: 12 }}>Mua {t.minQuantity} giảm {t.discountPercent}%</Text>)}
          </Row>
          <Text style={styles.muted}>Mua lẫn các sản phẩm dưới đây của shop, giảm giá tự áp dụng khi thanh toán.</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingTop: 8 }}>
            {(c.products || []).map(cp => (
              <Pressable key={cp.id} onPress={() => navigation.push('Product', { id: cp.id })} style={{ width: 110 }}>
                <Img uri={cp.thumbnailUrl} style={{ width: 110, height: 110 }} />
                <Text numberOfLines={2} style={{ fontSize: 12 }}>{cp.name}</Text>
                <Text style={{ color: C.primary }}>{formatVnd(cp.price)}</Text>
              </Pressable>
            ))}
          </ScrollView>
        </View>
      ))}

      {deals.map(d => (
        <View key={d.id} style={styles.card}>
          <Text style={styles.h2}>{d.type === 'GIFT' ? '🎀 Quà tặng kèm' : '🔥 Mua kèm deal sốc'}: {d.name}</Text>
          {d.minSpend ? <Text style={styles.muted}>Áp dụng cho đơn sản phẩm chính từ {formatVnd(d.minSpend)}</Text> : null}
          {(d.items || []).map(it => (
            <Pressable key={it.product.id} onPress={() => navigation.push('Product', { id: it.product.id })} style={{ flexDirection: 'row', gap: 10, paddingVertical: 6, alignItems: 'center' }}>
              <Img uri={it.product.thumbnailUrl} style={{ width: 52, height: 52 }} />
              <Text numberOfLines={2} style={{ flex: 1 }}>{it.product.name}</Text>
              <View style={{ alignItems: 'flex-end' }}>
                <Text style={{ color: C.primary, fontWeight: '600' }}>{Number(it.dealPrice) === 0 ? 'Tặng' : formatVnd(it.dealPrice)}</Text>
                <Text style={{ color: C.faint, textDecorationLine: 'line-through', fontSize: 12 }}>{formatVnd(it.product.price)}</Text>
              </View>
            </Pressable>
          ))}
          <Text style={[styles.muted, { fontSize: 12 }]}>Thêm sản phẩm này và sản phẩm mua kèm vào giỏ, giá deal tự áp dụng khi thanh toán.</Text>
        </View>
      ))}

      <View style={styles.card}>
        <Text style={styles.h2}>❓ Hỏi đáp về sản phẩm ({qa?.totalItems || 0})</Text>
        {(qa?.items || []).map(q => (
          <View key={q.id} style={{ borderTopWidth: 1, borderTopColor: C.border, paddingVertical: 8 }}>
            <Text><Text style={{ fontWeight: '600' }}>Hỏi: </Text>{q.question}</Text>
            <Text style={[styles.muted, { fontSize: 11 }]}>{q.askerName} · {formatDateTime(q.createdAt)}</Text>
            {q.answer ? <Text style={{ marginTop: 4, backgroundColor: C.bg, padding: 6 }}><Text style={{ fontWeight: '600', color: C.primary }}>Shop: </Text>{q.answer}</Text>
              : <Text style={[styles.muted, { marginTop: 4 }]}>Shop chưa trả lời</Text>}
          </View>
        ))}
        <Field value={question} onChangeText={setQuestion} placeholder="Đặt câu hỏi cho shop về sản phẩm này..." maxLength={500} multiline style={{ marginTop: 8, marginBottom: 6 }} />
        <Button small title={asking ? 'Đang gửi...' : 'Gửi câu hỏi'} disabled={asking || question.trim().length < 5} onPress={ask} style={{ alignSelf: 'flex-end' }} />
      </View>
    </>
  )
}

export function ReportProductLink({ p }) {
  const [open, setOpen] = useState(false)
  const [reasons, setReasons] = useState([])
  const [reason, setReason] = useState(null)
  const [detail, setDetail] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open && !reasons.length) get('/products/report-reasons').then(setReasons).catch(() => {}) }, [open, reasons.length])
  if (!open) return <Text onPress={() => setOpen(true)} style={{ color: C.faint, textAlign: 'center', padding: 12 }}>⚑ Báo cáo sản phẩm này</Text>
  async function send() {
    setBusy(true)
    try {
      await post(`/products/${p.id}/report`, { reason, detail: detail.trim() })
      setOpen(false)
      Alert.alert('Đã gửi báo cáo', 'Cảm ơn bạn, Shoppi sẽ xem xét sản phẩm này.')
    } catch (e) {
      Alert.alert('Không gửi được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  return (
    <View style={styles.card}>
      <Text style={styles.h2}>Báo cáo sản phẩm</Text>
      <Select label="Lý do" value={reason} onChange={setReason} options={reasons.map(r => ({ value: r.key, label: r.label }))} placeholder="Chọn lý do" />
      <Field label="Chi tiết (không bắt buộc)" value={detail} onChangeText={setDetail} multiline maxLength={1000} />
      <Row style={{ gap: 8 }}>
        <Button kind="ghost" title="Đóng" onPress={() => setOpen(false)} style={{ flex: 1 }} />
        <Button title="Gửi báo cáo" disabled={busy || !reason} onPress={send} style={{ flex: 1 }} />
      </Row>
    </View>
  )
}

// home: Shoppi programmes and the next Flash Sale slot (web: HomeBanners + campaigns + FlashSaleSection)
export function HomePromos({ navigation }) {
  const [banners, setBanners] = useState([])
  const [campaigns, setCampaigns] = useState([])
  const [slots, setSlots] = useState([])
  useEffect(() => {
    get('/banners').then(b => setBanners([...(b.HOME_CAROUSEL || []), ...(b.HOME_SIDE || [])])).catch(() => {})
    get('/campaigns').then(setCampaigns).catch(() => {})
    get('/flash-sale/slots', { limit: 4 }).then(setSlots).catch(() => {})
  }, [])
  const openLink = (link) => {
    const m = /^\/(products|shop|campaigns)\/([\w-]+)/.exec(link || '')
    if (!m) return
    if (m[1] === 'campaigns') navigation.navigate('Campaign', { slug: m[2] })
    else navigation.navigate(m[1] === 'shop' ? 'Shop' : 'Product', { id: Number(m[2]) })
  }
  if (!banners.length && !campaigns.length && !slots.length) return null
  return (
    <View style={{ backgroundColor: 'white', marginBottom: 8, paddingVertical: 8 }}>
      {banners.length ? (
        <ScrollView horizontal pagingEnabled showsHorizontalScrollIndicator={false}>
          {banners.map(b => (
            <Pressable key={b.id} onPress={() => { post(`/banners/${b.id}/click`).catch(() => {}); openLink(b.linkUrl) }}>
              <Img uri={b.imageUrl} style={{ width: 360, height: 120, marginHorizontal: 8 }} />
            </Pressable>
          ))}
        </ScrollView>
      ) : null}
      {campaigns.length ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, padding: 8 }}>
          {campaigns.map(c => (
            <Pressable key={c.id} onPress={() => navigation.navigate('Campaign', { slug: c.slug })}
                       style={{ backgroundColor: c.themeColor || C.primary, padding: 10, borderRadius: 6, minWidth: 160 }}>
              <Text style={{ color: 'white', fontWeight: '700' }}>{c.name}</Text>
              {c.tagline ? <Text numberOfLines={1} style={{ color: 'white', fontSize: 12 }}>{c.tagline}</Text> : null}
            </Pressable>
          ))}
        </ScrollView>
      ) : null}
      {slots.length ? (
        <Pressable onPress={() => navigation.navigate('FlashSale')} style={{ flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 12, paddingTop: 4 }}>
          <Text style={{ color: C.primary, fontWeight: '700' }}>⚡ SHOPPI FLASH SALE · {slots.map(s => hm(s.startAt)).join(' · ')}</Text>
          <Text style={styles.muted}>Xem ›</Text>
        </Pressable>
      ) : null}
    </View>
  )
}
