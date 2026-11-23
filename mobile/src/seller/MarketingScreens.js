// "Kênh Marketing" of the seller centre (web: VoucherManager, SellerFlashSaleView, SellerFlashEventsView,
// SellerCombosView, SellerAddOnDealsView, SellerAdsView): the same endpoints and rules, laid out for a phone.
import React, { useMemo, useState } from 'react'
import { Alert, Pressable, RefreshControl, ScrollView, Switch, Text, TextInput, View } from 'react-native'
import { get, post, put, patch, del, errorText } from '../api'
import { formatDateTime, formatVnd } from '../format'
import { BottomBar, Button, C, Empty, Field, Img, Loading, Row, Select, styles, Tabs, useLoad } from '../ui'

// ---------- shared helpers ----------

const pad = (n) => String(n).padStart(2, '0')
// "YYYY-MM-DD HH:mm" typed on the phone <-> "YYYY-MM-DDTHH:mm:00" (LocalDateTime) for the API
export const toInput = (d) => { const x = new Date(d); return `${x.getFullYear()}-${pad(x.getMonth() + 1)}-${pad(x.getDate())} ${pad(x.getHours())}:${pad(x.getMinutes())}` }
export const toApi = (s) => s.trim().replace(' ', 'T') + ':00'
const validTime = (s) => /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(s.trim()) && !isNaN(new Date(s.trim().replace(' ', 'T')))
const num = (v) => (v === '' || v == null ? null : Number(String(v).replace(/\D/g, '')))
const STATUS = { RUNNING: ['Đang diễn ra', C.green], UPCOMING: ['Sắp diễn ra', '#2673dd'], PAUSED: ['Tạm dừng', '#f69113'], ENDED: ['Đã kết thúc', C.muted], SOLD_OUT: ['Hết suất', C.danger], ACTIVE: ['Đang chạy', C.green] }
const StatusText = ({ s }) => <Text style={{ color: (STATUS[s] || [])[1] || C.muted, fontSize: 12, fontWeight: '600' }}>{(STATUS[s] || [s])[0]}</Text>

async function act(fn, done, after) {
  try {
    await fn()
    if (done) Alert.alert(done)
    after?.()
  } catch (e) {
    Alert.alert('Không thực hiện được', errorText(e))
  }
}

function TimeRange({ f, set }) {
  return (
    <Row style={{ gap: 8 }}>
      <Field label="Bắt đầu (YYYY-MM-DD HH:mm)" value={f.startAt} onChangeText={set('startAt')} style={{ flex: 1 }} />
      <Field label="Kết thúc" value={f.endAt} onChangeText={set('endAt')} style={{ flex: 1 }} />
    </Row>
  )
}

// the shop's products, ticked one by one (with a filter)
function ProductPicker({ products, selected, onToggle, render }) {
  const [q, setQ] = useState('')
  const list = products.filter(p => !q || p.name.toLowerCase().includes(q.toLowerCase())).slice(0, 60)
  return (
    <View>
      <TextInput value={q} onChangeText={setQ} placeholder="🔍 Lọc sản phẩm" placeholderTextColor={C.faint} style={[styles.input, { marginBottom: 6 }]} />
      {list.map(p => {
        const on = selected.includes(p.id)
        return (
          <Pressable key={p.id} onPress={() => onToggle(p)} style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: C.border }}>
            <Text style={{ fontSize: 18, color: on ? C.primary : C.faint }}>{on ? '☑' : '☐'}</Text>
            <Img uri={p.thumbnailUrl} style={{ width: 36, height: 36 }} />
            <View style={{ flex: 1 }}>
              <Text numberOfLines={1}>{p.name}</Text>
              <Text style={[styles.muted, { fontSize: 12 }]}>{formatVnd(p.price)} · kho {p.stock}</Text>
            </View>
            {on && render ? render(p) : null}
          </Pressable>
        )
      })}
    </View>
  )
}

const useMyProducts = () => useLoad(() => get('/seller/products').then(r => (Array.isArray(r) ? r : r.items || [])))

// ==================== shop vouchers ====================

const VTYPES = [{ value: 'PERCENT', label: 'Giảm theo %' }, { value: 'FIXED', label: 'Giảm số tiền' }, { value: 'SHIPPING', label: 'Giảm phí vận chuyển' }]
const AUDIENCES = [{ value: 'ALL', label: 'Mọi khách hàng' }, { value: 'FOLLOWERS', label: 'Người theo dõi shop' },
  { value: 'NEW_CUSTOMERS', label: 'Khách mua lần đầu tại shop' }, { value: 'RETURNING_CUSTOMERS', label: 'Khách đã từng mua tại shop' }]
// vouchers carry no status: from their dates and switch, as the web list shows it
const voucherStatus = (v) => (new Date(v.endAt) <= new Date() || v.usedCount >= v.usageLimit ? 'ENDED'
  : new Date(v.startAt) > new Date() ? 'UPCOMING' : !v.active ? 'PAUSED' : 'RUNNING')
const voucherLabel = (v) => (v.type === 'PERCENT' ? `Giảm ${Number(v.value)}%${v.maxDiscount ? ' tối đa ' + formatVnd(v.maxDiscount) : ''}`
  : v.type === 'FIXED' ? `Giảm ${formatVnd(v.value)}` : `Giảm ${formatVnd(v.value)} phí ship`)

export function SellerVouchersScreen() {
  const { data, error, reload, refreshing, refresh } = useLoad(() => get('/seller/vouchers'))
  const { data: products } = useMyProducts()
  const [form, setForm] = useState(null)
  const [editing, setEditing] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }))

  const open = (v) => {
    const now = new Date()
    setEditing(v || null)
    setForm(v ? {
      code: v.code, type: v.type, value: String(Number(v.value)), maxDiscount: v.maxDiscount ? String(Number(v.maxDiscount)) : '', minOrder: String(Number(v.minOrder || 0)),
      usageLimit: String(v.usageLimit), perUserLimit: String(v.perUserLimit || 1), startAt: toInput(v.startAt), endAt: toInput(v.endAt),
      publicVisible: v.publicVisible !== false, productIds: [...(v.productIds || [])], audience: v.audience || 'ALL'
    } : {
      code: '', type: 'PERCENT', value: '', maxDiscount: '', minOrder: '0', usageLimit: '100', perUserLimit: '1',
      startAt: toInput(now), endAt: toInput(new Date(now.getTime() + 7 * 86400000)), publicVisible: true, productIds: [], audience: 'ALL'
    })
  }
  const problem = () => {
    const f = form
    const value = num(f.value)
    if (!/^[A-Z0-9]{4,20}$/.test(f.code.trim().toUpperCase())) return 'Mã voucher 4–20 ký tự chữ hoặc số'
    if (!value) return 'Nhập mức giảm'
    if (f.type === 'PERCENT' && value > 90) return 'Mức giảm phần trăm tối đa 90%'
    if (f.type !== 'PERCENT' && value < 1000) return 'Mức giảm tối thiểu ₫1.000'
    if (f.type === 'FIXED' && num(f.minOrder) && value > num(f.minOrder)) return 'Mức giảm không được lớn hơn đơn tối thiểu'
    if (!validTime(f.startAt) || !validTime(f.endAt)) return 'Thời gian dạng YYYY-MM-DD HH:mm'
    if (new Date(f.endAt.replace(' ', 'T')) <= new Date(f.startAt.replace(' ', 'T'))) return 'Thời gian kết thúc phải sau bắt đầu'
    return ''
  }
  async function save() {
    const p = problem()
    if (p) { Alert.alert(p); return }
    setBusy(true)
    const f = form
    const body = { code: f.code.trim().toUpperCase(), type: f.type, value: num(f.value), maxDiscount: f.type === 'PERCENT' ? num(f.maxDiscount) : null,
      minOrder: num(f.minOrder) || 0, usageLimit: num(f.usageLimit) || 1, perUserLimit: num(f.perUserLimit) || 1,
      startAt: toApi(f.startAt), endAt: toApi(f.endAt), publicVisible: f.publicVisible, productIds: f.productIds, audience: f.audience }
    try {
      if (editing) await put(`/seller/vouchers/${editing.id}`, body)
      else await post('/seller/vouchers', body)
      setForm(null)
      reload()
    } catch (e) {
      Alert.alert('Không lưu được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  if (form) {
    const locked = editing && voucherStatus(editing) !== 'UPCOMING'
    return (
      <View style={styles.page}>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            {locked ? <Text style={{ color: '#b26a00', marginBottom: 8 }}>Voucher đang chạy: chỉ sửa được thời gian kết thúc, số lượt và hiển thị.</Text> : null}
            <Field label="Mã voucher" value={form.code} onChangeText={v => set('code')(v.toUpperCase())} autoCapitalize="characters" editable={!editing} maxLength={20} />
            <Select label="Loại giảm giá" value={form.type} onChange={set('type')} options={VTYPES} />
            <Row style={{ gap: 8 }}>
              <Field label={form.type === 'PERCENT' ? 'Mức giảm (%)' : 'Mức giảm (₫)'} value={form.value} onChangeText={set('value')} keyboardType="number-pad" style={{ flex: 1 }} editable={!locked} />
              {form.type === 'PERCENT' ? <Field label="Giảm tối đa (₫)" value={form.maxDiscount} onChangeText={set('maxDiscount')} keyboardType="number-pad" style={{ flex: 1 }} editable={!locked} /> : null}
            </Row>
            <Field label="Đơn tối thiểu (₫)" value={form.minOrder} onChangeText={set('minOrder')} keyboardType="number-pad" editable={!locked} />
            <Row style={{ gap: 8 }}>
              <Field label="Tổng lượt dùng" value={form.usageLimit} onChangeText={set('usageLimit')} keyboardType="number-pad" style={{ flex: 1 }} />
              <Field label="Lượt / người" value={form.perUserLimit} onChangeText={set('perUserLimit')} keyboardType="number-pad" style={{ flex: 1 }} editable={!locked} />
            </Row>
            <TimeRange f={form} set={set} />
            <Select label="Đối tượng áp dụng" value={form.audience} onChange={set('audience')} options={AUDIENCES} />
            <Row style={{ justifyContent: 'space-between', marginBottom: 8 }}>
              <Text>Hiển thị công khai trên trang shop</Text>
              <Switch value={form.publicVisible} onValueChange={set('publicVisible')} />
            </Row>
            <Text style={styles.label}>Áp dụng cho {form.productIds.length ? `${form.productIds.length} sản phẩm` : 'mọi sản phẩm của shop'}</Text>
            {!locked ? <ProductPicker products={products || []} selected={form.productIds}
                                      onToggle={p => set('productIds')(form.productIds.includes(p.id) ? form.productIds.filter(x => x !== p.id) : [...form.productIds, p.id])} /> : null}
          </View>
        </ScrollView>
        <BottomBar>
          <Button kind="ghost" title="Hủy" onPress={() => setForm(null)} style={{ flex: 1 }} />
          <Button title={busy ? 'Đang lưu...' : 'Lưu voucher'} disabled={busy} onPress={save} style={{ flex: 2 }} />
        </BottomBar>
      </View>
    )
  }
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <View style={styles.page}>
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        {!data.length ? <Empty text="Shop chưa có mã giảm giá nào" /> : data.map(v => (
          <View key={v.id} style={styles.card}>
            <Row style={{ justifyContent: 'space-between' }}>
              <Text style={{ fontWeight: '600', color: C.primary }}>{v.code}</Text>
              <StatusText s={voucherStatus(v)} />
            </Row>
            <Text>{voucherLabel(v)} · đơn từ {formatVnd(v.minOrder || 0)}</Text>
            <Text style={styles.muted}>Đã dùng {v.usedCount}/{v.usageLimit} · {AUDIENCES.find(a => a.value === v.audience)?.label || 'Mọi khách hàng'}</Text>
            <Text style={[styles.muted, { fontSize: 12 }]}>{formatDateTime(v.startAt)} – {formatDateTime(v.endAt)}</Text>
            {voucherStatus(v) !== 'ENDED' ? (
              <Row style={{ gap: 8, marginTop: 8 }}>
                <Button small kind="ghost" title="Sửa" onPress={() => open(v)} />
                <Button small kind="ghost" title={v.active ? 'Tạm dừng' : 'Bật lại'} onPress={() => act(() => patch(`/seller/vouchers/${v.id}/toggle`), null, reload)} />
                <Button small kind="danger" title="Kết thúc" onPress={() => Alert.alert('Kết thúc voucher này ngay?', '', [{ text: 'Không' }, { text: 'Kết thúc', onPress: () => act(() => post(`/seller/vouchers/${v.id}/end`), null, reload) }])} />
              </Row>
            ) : null}
          </View>
        ))}
      </ScrollView>
      <BottomBar><Button title="+ Tạo voucher" onPress={() => open(null)} style={{ flex: 1 }} /></BottomBar>
    </View>
  )
}

// ==================== shop flash sale ====================

const SLOTS = [{ label: '00:00 - 12:00', from: 0, to: 12 }, { label: '12:00 - 18:00', from: 12, to: 18 }, { label: '18:00 - 21:00', from: 18, to: 21 }, { label: '21:00 - 24:00', from: 21, to: 24 }]

export function SellerFlashSaleScreen() {
  const { data: rows, error, reload, refreshing, refresh } = useLoad(() => get('/seller/flash-sales'))
  const [tab, setTab] = useState('live')
  const [creating, setCreating] = useState(false)
  const [day, setDay] = useState(toInput(new Date()).slice(0, 10))
  const [slot, setSlot] = useState(SLOTS[1].label)
  const [picked, setPicked] = useState({})
  const [busy, setBusy] = useState(false)
  if (!rows) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  const live = rows.filter(r => (tab === 'live' ? ['RUNNING', 'UPCOMING', 'SOLD_OUT'].includes(r.status) : r.status === 'ENDED'))
  const pickable = rows.filter(r => r.active && r.stock > 0 && !['RUNNING', 'SOLD_OUT'].includes(r.status)).map(r => ({ ...r, id: r.productId }))
  const toggle = (r) => setPicked(x => {
    const n = { ...x }
    if (n[r.productId]) delete n[r.productId]
    else n[r.productId] = { flashPrice: String(Math.round((r.price * 0.8) / 1000) * 1000), flashStock: '', maxPerBuyer: '' }
    return n
  })
  const setP = (id, k, v) => setPicked(x => ({ ...x, [id]: { ...x[id], [k]: v } }))

  async function schedule() {
    const s = SLOTS.find(x => x.label === slot)
    const start = `${day}T${pad(s.from)}:00:00`
    const endDate = new Date(`${day}T00:00:00`)
    if (s.to === 24) endDate.setDate(endDate.getDate() + 1)
    const end = s.to === 24 ? `${toInput(endDate).slice(0, 10)}T00:00:00` : `${day}T${pad(s.to)}:00:00`
    if (new Date(end) <= new Date()) { Alert.alert('Khung giờ đã qua, hãy chọn khung giờ khác'); return }
    const startAt = new Date(start) < new Date() ? toApi(toInput(new Date())) : start
    setBusy(true)
    try {
      const items = Object.entries(picked).map(([productId, v]) => ({ productId: Number(productId), flashPrice: num(v.flashPrice), flashStock: num(v.flashStock), maxPerBuyer: num(v.maxPerBuyer) }))
      await post('/seller/flash-sales', { startAt, endAt: end, items })
      setCreating(false)
      setPicked({})
      reload()
    } catch (e) {
      Alert.alert('Không đăng ký được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  if (creating) {
    return (
      <View style={styles.page}>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            <Field label="Ngày (YYYY-MM-DD)" value={day} onChangeText={setDay} />
            <Select label="Khung giờ" value={slot} onChange={setSlot} options={SLOTS.map(s => s.label)} />
            <Text style={styles.label}>Chọn sản phẩm và giá Flash Sale ({Object.keys(picked).length})</Text>
            <ProductPicker products={pickable} selected={Object.keys(picked).map(Number)} onToggle={toggle} />
          </View>
          {Object.entries(picked).map(([id, v]) => {
            const r = rows.find(x => String(x.productId) === id)
            return (
              <View key={id} style={styles.card}>
                <Text numberOfLines={1} style={{ fontWeight: '500' }}>{r?.name} · giá gốc {formatVnd(r?.price)}</Text>
                <Row style={{ gap: 8, marginTop: 6 }}>
                  <Field label="Giá Flash (₫)" value={v.flashPrice} onChangeText={x => setP(id, 'flashPrice', x)} keyboardType="number-pad" style={{ flex: 1 }} />
                  <Field label="Suất" value={v.flashStock} onChangeText={x => setP(id, 'flashStock', x)} keyboardType="number-pad" placeholder="cả kho" style={{ flex: 1 }} />
                  <Field label="Tối đa/người" value={v.maxPerBuyer} onChangeText={x => setP(id, 'maxPerBuyer', x)} keyboardType="number-pad" style={{ flex: 1 }} />
                </Row>
              </View>
            )
          })}
        </ScrollView>
        <BottomBar>
          <Button kind="ghost" title="Hủy" onPress={() => setCreating(false)} style={{ flex: 1 }} />
          <Button title={busy ? 'Đang lưu...' : 'Đăng ký Flash Sale'} disabled={busy || !Object.keys(picked).length} onPress={schedule} style={{ flex: 2 }} />
        </BottomBar>
      </View>
    )
  }
  return (
    <View style={styles.page}>
      <Tabs items={[{ key: 'live', label: 'Đang & sắp diễn ra' }, { key: 'ended', label: 'Đã kết thúc' }]} value={tab} onChange={setTab} />
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        {!live.length ? <Empty text="Chưa có sản phẩm Flash Sale" /> : live.map(r => (
          <View key={r.productId} style={[styles.card, { flexDirection: 'row', gap: 10 }]}>
            <Img uri={r.thumbnailUrl} style={{ width: 56, height: 56 }} />
            <View style={{ flex: 1 }}>
              <Row style={{ justifyContent: 'space-between' }}><Text numberOfLines={1} style={{ flex: 1 }}>{r.name}</Text><StatusText s={r.status} /></Row>
              <Text><Text style={{ color: C.primary, fontWeight: '600' }}>{formatVnd(r.flashSalePrice)}</Text> <Text style={{ color: C.faint, textDecorationLine: 'line-through' }}>{formatVnd(r.price)}</Text></Text>
              <Text style={[styles.muted, { fontSize: 12 }]}>{formatDateTime(r.flashSaleStart)} – {formatDateTime(r.flashSaleEnd)} · đã bán {r.flashSaleSold || 0}{r.flashSaleStock ? '/' + r.flashSaleStock : ''}{r.shoppiEvent ? ' · Shoppi Flash Sale' : ''}</Text>
              {r.status !== 'ENDED' && !r.shoppiEvent ? <Text style={{ color: C.danger, marginTop: 4 }} onPress={() => Alert.alert('Kết thúc Flash Sale của sản phẩm này?', '', [{ text: 'Không' }, { text: 'Kết thúc', onPress: () => act(() => del(`/seller/flash-sales/${r.productId}`), null, reload) }])}>Kết thúc</Text> : null}
            </View>
          </View>
        ))}
      </ScrollView>
      <BottomBar><Button title="+ Tạo Flash Sale của Shop" onPress={() => setCreating(true)} style={{ flex: 1 }} /></BottomBar>
    </View>
  )
}

// ==================== Shoppi Flash Sale slots: register products ====================

const REG_STATUS = { PENDING: ['Chờ Shoppi duyệt', '#f69113'], APPROVED: ['Đã duyệt', C.green], REJECTED: ['Không được duyệt', C.danger], WITHDRAWN: ['Đã rút', C.muted] }
const EVENT_STATUS = { REGISTERING: 'Đang nhận đăng ký', REVIEWING: 'Đã hết hạn đăng ký', RUNNING: 'Đang diễn ra' }

export function SellerFlashEventsScreen() {
  const { data, error, reload, refreshing, refresh } = useLoad(() => get('/seller/flash-events'))
  const { data: products } = useMyProducts()
  const [picking, setPicking] = useState(null)
  const [chosen, setChosen] = useState({})
  const [busy, setBusy] = useState(false)
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  const ceiling = (p) => Math.floor(p.price * (100 - (picking?.minDiscountPercent || 0)) / 100)
  const slotText = (e) => `${formatDateTime(e.startAt)} – ${formatDateTime(e.endAt).slice(0, 5)}`
  async function register() {
    setBusy(true)
    try {
      const items = Object.entries(chosen).map(([productId, v]) => ({ productId: Number(productId), flashPrice: num(v.flashPrice), flashStock: num(v.flashStock), maxPerBuyer: num(v.maxPerBuyer) }))
      await post(`/seller/flash-events/${picking.id}/registrations`, { items })
      setPicking(null)
      Alert.alert('Đã gửi đăng ký', 'Shoppi sẽ duyệt và thông báo cho bạn.')
      reload()
    } catch (e) {
      Alert.alert('Không đăng ký được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  if (picking) {
    const taken = new Set((picking.registrations || []).filter(r => ['PENDING', 'APPROVED'].includes(r.status)).map(r => r.productId))
    const candidates = (products || []).filter(p => p.active !== false && (p.moderationStatus || 'APPROVED') === 'APPROVED' && p.stock > 0 && !taken.has(p.id))
    return (
      <View style={styles.page}>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            <Text style={styles.h2}>{picking.name}</Text>
            <Text style={styles.muted}>{slotText(picking)} · giảm tối thiểu {picking.minDiscountPercent}%</Text>
            <ProductPicker products={candidates} selected={Object.keys(chosen).map(Number)}
                           onToggle={p => setChosen(x => { const n = { ...x }; if (n[p.id]) delete n[p.id]; else n[p.id] = { flashPrice: String(Math.floor(ceiling(p) / 1000) * 1000), flashStock: String(Math.min(p.stock, 50)), maxPerBuyer: '2' }; return n })} />
          </View>
          {Object.entries(chosen).map(([id, v]) => {
            const p = candidates.find(x => String(x.id) === id) || {}
            return (
              <View key={id} style={styles.card}>
                <Text numberOfLines={1}>{p.name} · giá tối đa {formatVnd(ceiling(p))}</Text>
                <Row style={{ gap: 8, marginTop: 6 }}>
                  <Field label="Giá Flash" value={v.flashPrice} onChangeText={x => setChosen(c => ({ ...c, [id]: { ...c[id], flashPrice: x } }))} keyboardType="number-pad" style={{ flex: 1 }} />
                  <Field label="Suất" value={v.flashStock} onChangeText={x => setChosen(c => ({ ...c, [id]: { ...c[id], flashStock: x } }))} keyboardType="number-pad" style={{ flex: 1 }} />
                  <Field label="Tối đa/người" value={v.maxPerBuyer} onChangeText={x => setChosen(c => ({ ...c, [id]: { ...c[id], maxPerBuyer: x } }))} keyboardType="number-pad" style={{ flex: 1 }} />
                </Row>
              </View>
            )
          })}
        </ScrollView>
        <BottomBar>
          <Button kind="ghost" title="Hủy" onPress={() => setPicking(null)} style={{ flex: 1 }} />
          <Button title={busy ? 'Đang gửi...' : `Đăng ký ${Object.keys(chosen).length} sản phẩm`} disabled={busy || !Object.keys(chosen).length} onPress={register} style={{ flex: 2 }} />
        </BottomBar>
      </View>
    )
  }
  const regs = (rs) => (rs || []).map(r => (
    <Row key={r.id} style={{ justifyContent: 'space-between', paddingVertical: 4 }}>
      <Text numberOfLines={1} style={{ flex: 1 }}>{r.productName || `#${r.productId}`} · {formatVnd(r.flashPrice)}</Text>
      <Text style={{ color: (REG_STATUS[r.status] || [])[1], fontSize: 12 }}>{(REG_STATUS[r.status] || [r.status])[0]}</Text>
      {r.status === 'PENDING' ? <Text style={{ color: C.danger, marginLeft: 8 }} onPress={() => act(() => post(`/seller/flash-events/registrations/${r.id}/withdraw`), null, reload)}>Rút</Text> : null}
    </Row>
  ))
  return (
    <ScrollView style={styles.page} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <Text style={[styles.muted, { padding: 12 }]}>Đăng ký sản phẩm vào các khung giờ Flash Sale do Shoppi tổ chức. Shoppi duyệt rồi sản phẩm sẽ lên trang Flash Sale của sàn.</Text>
      {!data.events?.length ? <Empty text="Chưa có khung giờ nào đang nhận đăng ký" /> : data.events.map(e => (
        <View key={e.id} style={styles.card}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Text style={{ fontWeight: '600' }}>⚡ {e.name}</Text>
            <Text style={{ fontSize: 12, color: C.primary }}>{EVENT_STATUS[e.status] || e.status}</Text>
          </Row>
          <Text style={styles.muted}>{slotText(e)} · giảm tối thiểu {e.minDiscountPercent}%{e.registrationDeadline ? ` · hạn đăng ký ${formatDateTime(e.registrationDeadline)}` : ''}</Text>
          {regs(e.registrations)}
          {e.status === 'REGISTERING' ? <Button small title="Đăng ký sản phẩm" style={{ alignSelf: 'flex-start', marginTop: 8 }} onPress={() => { setChosen({}); setPicking(e) }} /> : null}
        </View>
      ))}
      {data.history?.length ? <Text style={[styles.h2, { paddingHorizontal: 12, marginTop: 8 }]}>Đã tham gia</Text> : null}
      {(data.history || []).map(e => (
        <View key={e.id} style={styles.card}>
          <Text style={{ fontWeight: '500' }}>{e.name}</Text>
          <Text style={styles.muted}>{slotText(e)}</Text>
          {regs(e.registrations)}
        </View>
      ))}
    </ScrollView>
  )
}

// ==================== combos ("Mua 2 giảm 5%") ====================

export function SellerCombosScreen() {
  const { data, error, reload, refreshing, refresh } = useLoad(() => get('/seller/combos'))
  const { data: products } = useMyProducts()
  const [form, setForm] = useState(null)
  const [editing, setEditing] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }))
  const open = (c) => {
    const now = new Date()
    setEditing(c || null)
    setForm(c ? { name: c.name, startAt: toInput(c.startAt), endAt: toInput(c.endAt), tiers: c.tiers.map(t => ({ minQuantity: String(t.minQuantity), discountPercent: String(t.discountPercent) })), productIds: [...c.productIds] }
      : { name: '', startAt: toInput(now), endAt: toInput(new Date(now.getTime() + 30 * 86400000)), tiers: [{ minQuantity: '2', discountPercent: '5' }], productIds: [] })
  }
  async function save() {
    const f = form
    const tiers = f.tiers.map(t => ({ minQuantity: num(t.minQuantity), discountPercent: num(t.discountPercent) })).sort((a, b) => a.minQuantity - b.minQuantity)
    const problem = f.name.trim().length < 3 ? 'Tên combo từ 3 ký tự'
      : !validTime(f.startAt) || !validTime(f.endAt) ? 'Thời gian dạng YYYY-MM-DD HH:mm'
        : tiers.some(t => !(t.minQuantity >= 2) || !(t.discountPercent >= 1 && t.discountPercent <= 50)) ? 'Số lượng từ 2, mức giảm từ 1% đến 50%'
          : tiers.some((t, i) => i && (t.minQuantity <= tiers[i - 1].minQuantity || t.discountPercent <= tiers[i - 1].discountPercent)) ? 'Mua càng nhiều phải giảm càng sâu'
            : !f.productIds.length ? 'Vui lòng chọn sản phẩm' : ''
    if (problem) { Alert.alert(problem); return }
    setBusy(true)
    try {
      const body = { name: f.name.trim(), startAt: toApi(f.startAt), endAt: toApi(f.endAt), tiers, productIds: f.productIds }
      if (editing) await put(`/seller/combos/${editing.id}`, body)
      else await post('/seller/combos', body)
      setForm(null)
      reload()
    } catch (e) {
      Alert.alert('Không lưu được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  if (form) {
    return (
      <View style={styles.page}>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            <Field label="Tên combo" value={form.name} onChangeText={set('name')} maxLength={100} placeholder="VD: Mua 2 giảm 5%, mua 3 giảm 10%" />
            <TimeRange f={form} set={set} />
            <Text style={styles.label}>Mức giảm theo số lượng</Text>
            {form.tiers.map((t, i) => (
              <Row key={i} style={{ gap: 8 }}>
                <Field label="Mua từ (sp)" value={t.minQuantity} keyboardType="number-pad" style={{ flex: 1 }}
                       onChangeText={v => set('tiers')(form.tiers.map((x, j) => (j === i ? { ...x, minQuantity: v } : x)))} />
                <Field label="Giảm (%)" value={t.discountPercent} keyboardType="number-pad" style={{ flex: 1 }}
                       onChangeText={v => set('tiers')(form.tiers.map((x, j) => (j === i ? { ...x, discountPercent: v } : x)))} />
                {form.tiers.length > 1 ? <Text style={{ color: C.danger, marginTop: 8 }} onPress={() => set('tiers')(form.tiers.filter((_, j) => j !== i))}>✕</Text> : null}
              </Row>
            ))}
            {form.tiers.length < 3 ? <Button small kind="ghost" title="+ Thêm mức" style={{ alignSelf: 'flex-start', marginBottom: 10 }}
                                             onPress={() => { const l = form.tiers[form.tiers.length - 1]; set('tiers')([...form.tiers, { minQuantity: String(num(l.minQuantity) + 1), discountPercent: String(Math.min(50, num(l.discountPercent) + 5)) }]) }} /> : null}
            <Text style={styles.label}>Sản phẩm trong combo ({form.productIds.length})</Text>
            <ProductPicker products={products || []} selected={form.productIds}
                           onToggle={p => set('productIds')(form.productIds.includes(p.id) ? form.productIds.filter(x => x !== p.id) : [...form.productIds, p.id])} />
          </View>
        </ScrollView>
        <BottomBar>
          <Button kind="ghost" title="Hủy" onPress={() => setForm(null)} style={{ flex: 1 }} />
          <Button title={busy ? 'Đang lưu...' : 'Lưu combo'} disabled={busy} onPress={save} style={{ flex: 2 }} />
        </BottomBar>
      </View>
    )
  }
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <View style={styles.page}>
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        {!data.length ? <Empty text="Chưa có combo khuyến mãi. Combo giúp khách mua nhiều sản phẩm hơn mỗi đơn." /> : data.map(c => (
          <View key={c.id} style={styles.card}>
            <Row style={{ justifyContent: 'space-between' }}><Text style={{ fontWeight: '600' }}>{c.name}</Text><StatusText s={c.status} /></Row>
            <Text style={{ color: C.primary }}>{c.tiers.map(t => `Mua ${t.minQuantity} giảm ${t.discountPercent}%`).join(' · ')}</Text>
            <Text style={styles.muted}>{c.productIds.length} sản phẩm · {formatDateTime(c.startAt)} – {formatDateTime(c.endAt)}</Text>
            {c.status !== 'ENDED' ? (
              <Row style={{ gap: 8, marginTop: 8 }}>
                <Button small kind="ghost" title="Sửa" onPress={() => open(c)} />
                <Button small kind="ghost" title={c.status === 'PAUSED' ? 'Bật lại' : 'Tạm dừng'} onPress={() => act(() => patch(`/seller/combos/${c.id}/toggle`), null, reload)} />
                <Button small kind="danger" title="Kết thúc" onPress={() => act(() => post(`/seller/combos/${c.id}/end`), null, reload)} />
              </Row>
            ) : null}
          </View>
        ))}
      </ScrollView>
      <BottomBar><Button title="+ Tạo combo" onPress={() => open(null)} style={{ flex: 1 }} /></BottomBar>
    </View>
  )
}

// ==================== add-on deals ("Mua kèm deal sốc" / "Mua để nhận quà") ====================

export function SellerAddOnDealsScreen() {
  const { data, error, reload, refreshing, refresh } = useLoad(() => get('/seller/addon-deals'))
  const { data: products } = useMyProducts()
  const [form, setForm] = useState(null)
  const [editing, setEditing] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }))
  const priceOf = (id) => Number((products || []).find(p => p.id === id)?.price || 0)
  const open = (d) => {
    const now = new Date()
    setEditing(d || null)
    setForm(d ? { name: d.name, type: d.type, startAt: toInput(d.startAt), endAt: toInput(d.endAt), purchaseLimit: String(d.purchaseLimit || 1),
      minSpend: d.minSpend ? String(Number(d.minSpend)) : '', mainProductIds: [...d.mainProductIds], items: d.items.map(i => ({ productId: i.productId, dealPrice: i.dealPrice ? String(Number(i.dealPrice)) : '' })) }
      : { name: '', type: 'ADDON', startAt: toInput(now), endAt: toInput(new Date(now.getTime() + 14 * 86400000)), purchaseLimit: '1', minSpend: '', mainProductIds: [], items: [] })
  }
  async function save() {
    const f = form
    const problem = f.name.trim().length < 3 ? 'Tên chương trình từ 3 ký tự'
      : !validTime(f.startAt) || !validTime(f.endAt) ? 'Thời gian dạng YYYY-MM-DD HH:mm'
        : !f.mainProductIds.length ? 'Vui lòng chọn sản phẩm chính'
          : !f.items.length ? (f.type === 'GIFT' ? 'Vui lòng chọn quà tặng' : 'Vui lòng chọn sản phẩm mua kèm')
            : f.type === 'ADDON' && f.items.some(i => !(num(i.dealPrice) >= 1000) || num(i.dealPrice) >= priceOf(i.productId)) ? 'Giá deal từ ₫1.000 và thấp hơn giá bán' : ''
    if (problem) { Alert.alert(problem); return }
    setBusy(true)
    try {
      const body = { name: f.name.trim(), type: f.type, startAt: toApi(f.startAt), endAt: toApi(f.endAt), purchaseLimit: num(f.purchaseLimit) || 1,
        minSpend: f.type === 'GIFT' ? num(f.minSpend) || 0 : null, mainProductIds: f.mainProductIds,
        items: f.items.map(i => ({ productId: i.productId, dealPrice: f.type === 'GIFT' ? null : num(i.dealPrice) })) }
      if (editing) await put(`/seller/addon-deals/${editing.id}`, body)
      else await post('/seller/addon-deals', body)
      setForm(null)
      reload()
    } catch (e) {
      Alert.alert('Không lưu được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  if (form) {
    const itemIds = form.items.map(i => i.productId)
    return (
      <View style={styles.page}>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            <Select label="Loại chương trình" value={form.type} onChange={set('type')}
                    options={[{ value: 'ADDON', label: '🔥 Mua kèm deal sốc', hint: 'Mua sản phẩm chính được mua kèm sản phẩm khác giá rẻ' }, { value: 'GIFT', label: '🎀 Mua để nhận quà', hint: 'Đơn đạt mức chi tiêu được tặng quà' }]} />
            <Field label="Tên chương trình" value={form.name} onChangeText={set('name')} maxLength={100} />
            <TimeRange f={form} set={set} />
            <Row style={{ gap: 8 }}>
              <Field label="Giới hạn mua kèm / đơn" value={form.purchaseLimit} onChangeText={set('purchaseLimit')} keyboardType="number-pad" style={{ flex: 1 }} />
              {form.type === 'GIFT' ? <Field label="Chi tiêu tối thiểu (₫)" value={form.minSpend} onChangeText={set('minSpend')} keyboardType="number-pad" style={{ flex: 1 }} /> : null}
            </Row>
          </View>
          <View style={styles.card}>
            <Text style={styles.h2}>Sản phẩm chính ({form.mainProductIds.length})</Text>
            <ProductPicker products={products || []} selected={form.mainProductIds}
                           onToggle={p => set('mainProductIds')(form.mainProductIds.includes(p.id) ? form.mainProductIds.filter(x => x !== p.id) : [...form.mainProductIds, p.id])} />
          </View>
          <View style={styles.card}>
            <Text style={styles.h2}>{form.type === 'GIFT' ? 'Quà tặng' : 'Sản phẩm mua kèm'} ({form.items.length})</Text>
            <ProductPicker products={products || []} selected={itemIds}
                           onToggle={p => set('items')(itemIds.includes(p.id) ? form.items.filter(i => i.productId !== p.id)
                             : [...form.items, { productId: p.id, dealPrice: String(Math.max(1000, Math.round(Number(p.price) * 0.5 / 1000) * 1000)) }])} />
            {form.type === 'ADDON' ? form.items.map(i => (
              <Field key={i.productId} label={`Giá deal: ${(products || []).find(p => p.id === i.productId)?.name || ''}`} value={i.dealPrice} keyboardType="number-pad"
                     onChangeText={v => set('items')(form.items.map(x => (x.productId === i.productId ? { ...x, dealPrice: v } : x)))} style={{ marginTop: 8 }} />
            )) : null}
          </View>
        </ScrollView>
        <BottomBar>
          <Button kind="ghost" title="Hủy" onPress={() => setForm(null)} style={{ flex: 1 }} />
          <Button title={busy ? 'Đang lưu...' : 'Lưu chương trình'} disabled={busy} onPress={save} style={{ flex: 2 }} />
        </BottomBar>
      </View>
    )
  }
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <View style={styles.page}>
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        {!data.length ? <Empty text="Chưa có chương trình mua kèm / quà tặng" /> : data.map(d => (
          <View key={d.id} style={styles.card}>
            <Row style={{ justifyContent: 'space-between' }}><Text style={{ fontWeight: '600' }}>{d.type === 'GIFT' ? '🎀' : '🔥'} {d.name}</Text><StatusText s={d.status} /></Row>
            <Text style={styles.muted}>{d.mainProductIds.length} sản phẩm chính · {d.items.length} {d.type === 'GIFT' ? 'quà' : 'sản phẩm mua kèm'}{d.minSpend ? ` · đơn từ ${formatVnd(d.minSpend)}` : ''}</Text>
            <Text style={[styles.muted, { fontSize: 12 }]}>{formatDateTime(d.startAt)} – {formatDateTime(d.endAt)}</Text>
            {d.status !== 'ENDED' ? (
              <Row style={{ gap: 8, marginTop: 8 }}>
                <Button small kind="ghost" title="Sửa" onPress={() => open(d)} />
                <Button small kind="ghost" title={d.status === 'PAUSED' ? 'Bật lại' : 'Tạm dừng'} onPress={() => act(() => patch(`/seller/addon-deals/${d.id}/toggle`), null, reload)} />
                <Button small kind="danger" title="Kết thúc" onPress={() => act(() => post(`/seller/addon-deals/${d.id}/end`), null, reload)} />
              </Row>
            ) : null}
          </View>
        ))}
      </ScrollView>
      <BottomBar><Button title="+ Tạo chương trình" onPress={() => open(null)} style={{ flex: 1 }} /></BottomBar>
    </View>
  )
}

// ==================== ads (sponsored search results) ====================

export function SellerAdsScreen() {
  const { data: ads, error, reload, refreshing, refresh } = useLoad(() => get('/seller/ads'))
  const { data: wallet, reload: reloadWallet } = useLoad(() => get('/seller/ads/wallet'))
  const { data: products } = useMyProducts()
  const [form, setForm] = useState(null)
  const [editing, setEditing] = useState(null)
  const [kw, setKw] = useState('')
  const [topUp, setTopUp] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }))
  const active = useMemo(() => (products || []).filter(p => p.active), [products])
  if (!ads) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  const open = (c) => {
    setKw('')
    setEditing(c || null)
    setForm(c ? { productId: c.productId, keywords: [...c.keywords], bidPerClick: String(Number(c.bidPerClick)), dailyBudget: String(Number(c.dailyBudget)) }
      : { productId: null, keywords: [], bidPerClick: '1000', dailyBudget: '50000' })
  }
  const addKw = () => {
    const k = kw.trim().toLowerCase()
    if (k.length >= 2 && !form.keywords.includes(k) && form.keywords.length < 20) set('keywords')([...form.keywords, k])
    setKw('')
  }
  async function save() {
    const f = { ...form, bidPerClick: num(form.bidPerClick), dailyBudget: num(form.dailyBudget) }
    const problem = !f.productId ? 'Vui lòng chọn sản phẩm'
      : !(f.bidPerClick >= 500 && f.bidPerClick <= 50000) ? 'Giá thầu từ ₫500 đến ₫50.000'
        : !(f.dailyBudget >= 20000) ? 'Ngân sách ngày tối thiểu ₫20.000'
          : f.dailyBudget < f.bidPerClick ? 'Ngân sách ngày phải lớn hơn giá thầu' : ''
    if (problem) { Alert.alert(problem); return }
    setBusy(true)
    try {
      if (editing) await put(`/seller/ads/${editing.id}`, f)
      else await post('/seller/ads', f)
      setForm(null)
      reload()
    } catch (e) {
      Alert.alert('Không lưu được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  if (form) {
    return (
      <View style={styles.page}>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            <Select label="Sản phẩm quảng cáo" value={form.productId} onChange={set('productId')}
                    options={active.map(p => ({ value: p.id, label: p.name, hint: formatVnd(p.price) }))} placeholder="Chọn sản phẩm" />
            <Text style={styles.label}>Từ khóa ({form.keywords.length}/20) – để trống: hiện theo tên sản phẩm</Text>
            <Row style={{ gap: 6, flexWrap: 'wrap', marginBottom: 6 }}>
              {form.keywords.map(k => <Text key={k} onPress={() => set('keywords')(form.keywords.filter(x => x !== k))} style={{ backgroundColor: C.primarySoft, color: C.primary, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 12 }}>{k} ✕</Text>)}
            </Row>
            <Row style={{ gap: 8, marginBottom: 12 }}>
              <TextInput value={kw} onChangeText={setKw} onSubmitEditing={addKw} placeholder="Thêm từ khóa" placeholderTextColor={C.faint} style={[styles.input, { flex: 1 }]} />
              <Button small kind="ghost" title="Thêm" onPress={addKw} />
            </Row>
            <Row style={{ gap: 8 }}>
              <Field label="Giá thầu / click (₫)" value={form.bidPerClick} onChangeText={set('bidPerClick')} keyboardType="number-pad" style={{ flex: 1 }} />
              <Field label="Ngân sách / ngày (₫)" value={form.dailyBudget} onChangeText={set('dailyBudget')} keyboardType="number-pad" style={{ flex: 1 }} />
            </Row>
          </View>
        </ScrollView>
        <BottomBar>
          <Button kind="ghost" title="Hủy" onPress={() => setForm(null)} style={{ flex: 1 }} />
          <Button title={busy ? 'Đang lưu...' : 'Lưu quảng cáo'} disabled={busy} onPress={save} style={{ flex: 2 }} />
        </BottomBar>
      </View>
    )
  }
  return (
    <View style={styles.page}>
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { refresh(); reloadWallet() }} />} keyboardShouldPersistTaps="handled">
        <View style={styles.card}>
          <Text style={styles.muted}>Số dư ví quảng cáo</Text>
          <Text style={{ fontSize: 22, color: C.primary, fontWeight: '600' }}>{formatVnd(wallet?.balance || 0)}</Text>
          <Row style={{ gap: 8, marginTop: 8 }}>
            <TextInput value={topUp} onChangeText={setTopUp} keyboardType="number-pad" placeholder="Số tiền nạp (₫)" placeholderTextColor={C.faint} style={[styles.input, { flex: 1 }]} />
            <Button small title="Nạp tiền" disabled={!(num(topUp) >= 10000) || busy}
                    onPress={() => act(async () => { await post('/seller/ads/wallet/topup', { amount: num(topUp) }); setTopUp('') }, 'Đã nạp tiền vào ví quảng cáo', reloadWallet)} />
          </Row>
          <Text style={[styles.muted, { fontSize: 12, marginTop: 4 }]}>Nạp tối thiểu ₫10.000. Mỗi lượt khách bấm vào quảng cáo trừ đúng giá thầu.</Text>
        </View>
        {!ads.length ? <Empty text="Chưa có chiến dịch quảng cáo. Quảng cáo đưa sản phẩm lên đầu kết quả tìm kiếm." /> : ads.map(c => (
          <View key={c.id} style={styles.card}>
            <Row style={{ justifyContent: 'space-between' }}><Text numberOfLines={1} style={{ fontWeight: '600', flex: 1 }}>{c.productName}</Text><StatusText s={c.status} /></Row>
            <Text style={styles.muted}>Giá thầu {formatVnd(c.bidPerClick)} · ngân sách {formatVnd(c.dailyBudget)}/ngày</Text>
            <Text style={styles.muted}>{c.impressions ?? 0} lượt hiển thị · {c.clicks ?? 0} click · đã chi {formatVnd(c.spend ?? 0)}</Text>
            {c.keywords?.length ? <Text style={[styles.muted, { fontSize: 12 }]}>Từ khóa: {c.keywords.join(', ')}</Text> : null}
            {c.status !== 'ENDED' ? (
              <Row style={{ gap: 8, marginTop: 8 }}>
                <Button small kind="ghost" title="Sửa" onPress={() => open(c)} />
                <Button small kind="ghost" title={c.status === 'ACTIVE' ? 'Tạm dừng' : 'Chạy lại'} onPress={() => act(() => patch(`/seller/ads/${c.id}/status`, { status: c.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' }), null, reload)} />
                <Button small kind="danger" title="Kết thúc" onPress={() => act(() => patch(`/seller/ads/${c.id}/status`, { status: 'ENDED' }), null, reload)} />
              </Row>
            ) : null}
          </View>
        ))}
      </ScrollView>
      <BottomBar><Button title="+ Tạo quảng cáo" onPress={() => open(null)} style={{ flex: 1 }} /></BottomBar>
    </View>
  )
}
