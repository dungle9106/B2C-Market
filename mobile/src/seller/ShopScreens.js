// "Quản lý Shop" and customer care of the seller centre (web: ShopSettingsView, ShopPoliciesView,
// ShopVerificationView, ShopCategoriesView, SellerCareSettingsView, SellerQuestionsView, SellerVideosView).
import React, { useEffect, useState } from 'react'
import { Alert, Pressable, RefreshControl, ScrollView, Switch, Text, TextInput, View } from 'react-native'
import * as ImagePicker from 'expo-image-picker'
import { get, post, put, del, upload, errorText } from '../api'
import { formatDateTime } from '../format'
import { PROVINCES } from '../provinces'
import { BottomBar, Button, C, Empty, Field, Img, Loading, Row, Select, styles, Tabs, useLoad } from '../ui'

async function pickAndUpload(kind = 'images') {
  const perm = await ImagePicker.requestMediaLibraryPermissionsAsync()
  if (!perm.granted) return null
  const r = await ImagePicker.launchImageLibraryAsync({ mediaTypes: [kind], quality: 0.8 })
  if (r.canceled) return null
  try {
    return (await upload('/uploads', r.assets[0])).url
  } catch (e) {
    Alert.alert('Không tải lên được', errorText(e))
    return null
  }
}

async function run(setBusy, fn, done) {
  setBusy(true)
  try {
    await fn()
    if (done) Alert.alert(done)
  } catch (e) {
    Alert.alert('Không thực hiện được', errorText(e))
  } finally {
    setBusy(false)
  }
}

// ==================== shop profile ====================

export function ShopSettingsScreen() {
  const [f, setF] = useState(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => { get('/seller/shop').then(setF).catch(e => Alert.alert('Lỗi', errorText(e))) }, [])
  if (!f) return <Loading />
  const set = (k) => (v) => setF(x => ({ ...x, [k]: v }))
  const save = () => run(setBusy, async () => {
    const { name, description, logoUrl, coverUrl, pickupAddress, pickupPhone, pickupDistrict, pickupWard, province } = f
    setF(await post('/seller/shop', { name, description, logoUrl, coverUrl, pickupAddress, pickupPhone, pickupDistrict, pickupWard, province }))
  }, 'Đã lưu hồ sơ shop')
  return (
    <View style={styles.page}>
      <ScrollView keyboardShouldPersistTaps="handled">
        {f.status && f.status !== 'APPROVED' ? <Text style={[styles.card, { color: '#b26a00' }]}>Trạng thái shop: {f.status}{f.statusReason ? ` · ${f.statusReason}` : ''}</Text> : null}
        <View style={styles.card}>
          <Row style={{ gap: 12, marginBottom: 12 }}>
            <Pressable onPress={async () => { const u = await pickAndUpload(); if (u) set('logoUrl')(u) }} style={{ alignItems: 'center' }}>
              <Img uri={f.logoUrl} style={{ width: 72, height: 72, borderRadius: 36 }} />
              <Text style={{ color: C.primary, fontSize: 12, marginTop: 4 }}>Đổi logo</Text>
            </Pressable>
            <Pressable onPress={async () => { const u = await pickAndUpload(); if (u) set('coverUrl')(u) }} style={{ flex: 1, alignItems: 'center' }}>
              <Img uri={f.coverUrl} style={{ width: '100%', height: 72 }} />
              <Text style={{ color: C.primary, fontSize: 12, marginTop: 4 }}>Đổi ảnh bìa</Text>
            </Pressable>
          </Row>
          <Field label="Tên shop" value={f.name} onChangeText={set('name')} maxLength={100} />
          <Field label="Mô tả shop" value={f.description || ''} onChangeText={set('description')} multiline maxLength={2000} />
        </View>
        <View style={styles.card}>
          <Text style={styles.h2}>📍 Địa chỉ lấy hàng</Text>
          <Select label="Tỉnh/Thành phố" value={f.province} onChange={set('province')} options={PROVINCES} />
          <Row style={{ gap: 8 }}>
            <Field label="Quận/Huyện" value={f.pickupDistrict || ''} onChangeText={set('pickupDistrict')} style={{ flex: 1 }} />
            <Field label="Phường/Xã" value={f.pickupWard || ''} onChangeText={set('pickupWard')} style={{ flex: 1 }} />
          </Row>
          <Field label="Địa chỉ" value={f.pickupAddress || ''} onChangeText={set('pickupAddress')} />
          <Field label="Số điện thoại" value={f.pickupPhone || ''} onChangeText={set('pickupPhone')} keyboardType="phone-pad" />
        </View>
      </ScrollView>
      <BottomBar><Button title={busy ? 'Đang lưu...' : 'Lưu'} disabled={busy} onPress={save} style={{ flex: 1 }} /></BottomBar>
    </View>
  )
}

// ==================== shop policies: returns, carriers, handling time, free shipping ====================

const RETURN_DAYS = [0, 3, 7, 15, 30]

export function ShopPoliciesScreen() {
  const [f, setF] = useState(null)
  const [carriers, setCarriers] = useState([])
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    Promise.all([get('/seller/shop'), get('/shops/carriers')]).then(([shop, options]) => {
      setCarriers(options)
      setF({ defaultReturnDays: shop.defaultReturnDays ?? 7, returnConditions: shop.returnConditions || '', applyReturnDaysToAllProducts: false,
        carriers: shop.carriers || [], handlingDays: String(shop.handlingDays ?? 1), freeShipMinOrder: shop.freeShipMinOrder ? String(Number(shop.freeShipMinOrder)) : '' })
    }).catch(e => Alert.alert('Lỗi', errorText(e)))
  }, [])
  if (!f) return <Loading />
  const set = (k) => (v) => setF(x => ({ ...x, [k]: v }))
  const save = () => {
    if (!f.carriers.length) { Alert.alert('Chọn ít nhất một đơn vị vận chuyển'); return }
    run(setBusy, () => put('/seller/shop/policies', { ...f, handlingDays: Number(f.handlingDays) || 1, freeShipMinOrder: Number(String(f.freeShipMinOrder).replace(/\D/g, '')) || null }), 'Đã lưu chính sách shop')
  }
  return (
    <View style={styles.page}>
      <ScrollView keyboardShouldPersistTaps="handled">
        <View style={styles.card}>
          <Text style={styles.h2}>↩ Đổi trả</Text>
          <Select label="Thời hạn đổi trả mặc định" value={f.defaultReturnDays} onChange={set('defaultReturnDays')}
                  options={RETURN_DAYS.map(d => ({ value: d, label: d > 0 ? `Đổi trả trong ${d} ngày` : 'Không hỗ trợ đổi trả' }))} />
          <Row style={{ justifyContent: 'space-between', marginBottom: 10 }}>
            <Text style={{ flex: 1 }}>Áp dụng cho mọi sản phẩm đang bán</Text>
            <Switch value={f.applyReturnDaysToAllProducts} onValueChange={set('applyReturnDaysToAllProducts')} />
          </Row>
          <Field label="Điều kiện đổi trả" value={f.returnConditions} onChangeText={set('returnConditions')} multiline maxLength={1000}
                 placeholder="VD: còn tem mác, chưa qua sử dụng, có video mở hàng" />
        </View>
        <View style={styles.card}>
          <Text style={styles.h2}>🚚 Vận chuyển</Text>
          {carriers.map(c => {
            const on = f.carriers.includes(c.code)
            return (
              <Pressable key={c.code} onPress={() => set('carriers')(on ? f.carriers.filter(x => x !== c.code) : [...f.carriers, c.code])}
                         style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 7 }}>
                <Text style={{ fontSize: 18, color: on ? C.primary : C.faint }}>{on ? '☑' : '☐'}</Text>
                <View style={{ flex: 1 }}>
                  <Text>{c.name}</Text>
                  {c.methods?.length ? <Text style={[styles.muted, { fontSize: 12 }]}>{c.methods.map(m => m.name || m).join(', ')}</Text> : null}
                </View>
              </Pressable>
            )
          })}
          <Row style={{ gap: 8, marginTop: 8 }}>
            <Field label="Chuẩn bị hàng (ngày)" value={f.handlingDays} onChangeText={set('handlingDays')} keyboardType="number-pad" style={{ flex: 1 }} />
            <Field label="Freeship cho đơn từ (₫)" value={f.freeShipMinOrder} onChangeText={set('freeShipMinOrder')} keyboardType="number-pad" placeholder="không" style={{ flex: 1 }} />
          </Row>
        </View>
      </ScrollView>
      <BottomBar><Button title={busy ? 'Đang lưu...' : 'Lưu chính sách'} disabled={busy} onPress={save} style={{ flex: 1 }} /></BottomBar>
    </View>
  )
}

// ==================== shop verification ====================

const BUSINESS_TYPES = [{ value: 'INDIVIDUAL', label: 'Cá nhân' }, { value: 'HOUSEHOLD', label: 'Hộ kinh doanh' }, { value: 'COMPANY', label: 'Doanh nghiệp' }]
const VSTATUS = { PENDING: ['Đang chờ duyệt', '#f69113'], APPROVED: ['Đã xác minh', C.green], REJECTED: ['Bị từ chối', C.danger] }

export function ShopVerificationScreen() {
  const { data: current, reload } = useLoad(() => get('/seller/verification').then(d => d || null))
  const [f, setF] = useState({ businessType: 'INDIVIDUAL', legalName: '', idNumber: '', taxCode: '', licenseNumber: '', businessAddress: '', documentUrls: [], confirmed: false })
  const [busy, setBusy] = useState(false)
  const set = (k) => (v) => setF(x => ({ ...x, [k]: v }))
  const isBusiness = f.businessType !== 'INDIVIDUAL'
  const submit = () => {
    const p = !f.legalName ? 'Vui lòng nhập họ tên / tên doanh nghiệp'
      : !/^\d{12}$/.test(f.idNumber) ? 'Số CCCD phải gồm 12 chữ số'
        : f.taxCode && !/^\d{10}(-\d{3})?$/.test(f.taxCode) ? 'Mã số thuế không hợp lệ'
          : isBusiness && (!f.taxCode || !f.licenseNumber || !f.businessAddress) ? 'Vui lòng nhập đủ mã số thuế, số giấy phép và địa chỉ kinh doanh'
            : f.documentUrls.length < (isBusiness ? 3 : 2) ? (isBusiness ? 'Cần ảnh 2 mặt CCCD và giấy phép kinh doanh' : 'Cần ảnh 2 mặt CCCD')
              : !f.confirmed ? 'Vui lòng xác nhận cam kết thông tin chính xác' : ''
    if (p) { Alert.alert(p); return }
    run(setBusy, async () => { await post('/seller/verification', { ...f, licenseNumber: isBusiness ? f.licenseNumber : null }); reload() }, 'Đã gửi hồ sơ xác minh')
  }
  const showForm = !current || current.status === 'REJECTED'
  return (
    <View style={styles.page}>
      <ScrollView keyboardShouldPersistTaps="handled">
        {current ? (
          <View style={styles.card}>
            <Row style={{ justifyContent: 'space-between' }}>
              <Text style={styles.h2}>Hồ sơ đã gửi</Text>
              <Text style={{ color: (VSTATUS[current.status] || [])[1], fontWeight: '600' }}>{(VSTATUS[current.status] || [current.status])[0]}</Text>
            </Row>
            <Text>{BUSINESS_TYPES.find(b => b.value === current.businessType)?.label} · {current.legalName}</Text>
            <Text style={styles.muted}>CCCD {current.idNumber}{current.taxCode ? ` · MST ${current.taxCode}` : ''}</Text>
            <Text style={[styles.muted, { fontSize: 12 }]}>Gửi {formatDateTime(current.submittedAt)}{current.reviewedAt ? ` · duyệt ${formatDateTime(current.reviewedAt)}` : ''}</Text>
            {current.rejectReason ? <Text style={{ color: C.danger, marginTop: 6 }}>Lý do từ chối: {current.rejectReason}</Text> : null}
          </View>
        ) : <Text style={[styles.card, styles.muted]}>Xác minh thông tin giúp shop có huy hiệu "Đã xác minh", tăng uy tín với người mua.</Text>}
        {showForm ? (
          <View style={styles.card}>
            <Select label="Loại hình kinh doanh" value={f.businessType} onChange={set('businessType')} options={BUSINESS_TYPES} />
            <Field label={isBusiness ? 'Tên hộ kinh doanh / doanh nghiệp' : 'Họ tên (như trên CCCD)'} value={f.legalName} onChangeText={set('legalName')} />
            <Field label="Số CCCD (12 số)" value={f.idNumber} onChangeText={set('idNumber')} keyboardType="number-pad" maxLength={12} />
            <Field label={isBusiness ? 'Mã số thuế' : 'Mã số thuế (không bắt buộc)'} value={f.taxCode} onChangeText={set('taxCode')} />
            {isBusiness ? <Field label="Số giấy phép kinh doanh" value={f.licenseNumber} onChangeText={set('licenseNumber')} /> : null}
            {isBusiness ? <Field label="Địa chỉ kinh doanh" value={f.businessAddress} onChangeText={set('businessAddress')} /> : null}
            <Text style={styles.label}>Ảnh giấy tờ: 2 mặt CCCD{isBusiness ? ' và giấy phép kinh doanh' : ''}</Text>
            <Row style={{ gap: 8, flexWrap: 'wrap', marginBottom: 10 }}>
              {f.documentUrls.map(u => <Pressable key={u} onLongPress={() => set('documentUrls')(f.documentUrls.filter(x => x !== u))}><Img uri={u} style={{ width: 72, height: 72 }} /></Pressable>)}
              {f.documentUrls.length < 5 ? <Button small kind="ghost" title="+ Ảnh" onPress={async () => { const u = await pickAndUpload(); if (u) set('documentUrls')([...f.documentUrls, u]) }} /> : null}
            </Row>
            <Pressable onPress={() => set('confirmed')(!f.confirmed)} style={{ flexDirection: 'row', gap: 8 }}>
              <Text style={{ fontSize: 18, color: f.confirmed ? C.primary : C.faint }}>{f.confirmed ? '☑' : '☐'}</Text>
              <Text style={{ flex: 1 }}>Tôi cam kết thông tin trên là chính xác và chịu trách nhiệm trước pháp luật.</Text>
            </Pressable>
          </View>
        ) : null}
      </ScrollView>
      {showForm ? <BottomBar><Button title={busy ? 'Đang gửi...' : 'Gửi hồ sơ xác minh'} disabled={busy} onPress={submit} style={{ flex: 1 }} /></BottomBar> : null}
    </View>
  )
}

// ==================== the shop's own categories ====================

export function ShopCategoriesScreen() {
  const { data, error, reload, refreshing, refresh } = useLoad(() => get('/seller/shop-categories'))
  const { data: products } = useLoad(() => get('/seller/products').then(r => (Array.isArray(r) ? r : r.items || [])))
  const [name, setName] = useState('')
  const [open, setOpen] = useState(null)
  const [busy, setBusy] = useState(false)
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const move = (i, d) => {
    const ids = data.map(c => c.id)
    const j = i + d
    if (j < 0 || j >= ids.length) return
    ;[ids[i], ids[j]] = [ids[j], ids[i]]
    run(setBusy, async () => { await put('/seller/shop-categories/order', { ids }); reload() })
  }
  const rename = (c) => setOpen({ ...c, renaming: c.name })
  return (
    <ScrollView style={styles.page} keyboardShouldPersistTaps="handled" refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <View style={[styles.card, { flexDirection: 'row', gap: 8 }]}>
        <TextInput value={name} onChangeText={setName} placeholder="Tên danh mục mới" placeholderTextColor={C.faint} style={[styles.input, { flex: 1 }]} maxLength={60} />
        <Button small title="Thêm" disabled={busy || name.trim().length < 2} onPress={() => run(setBusy, async () => { await post('/seller/shop-categories', { name: name.trim() }); setName(''); reload() })} />
      </View>
      {!data.length ? <Empty text="Danh mục của shop giúp người mua tìm hàng trên trang shop của bạn" /> : data.map((c, i) => (
        <View key={c.id} style={styles.card}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Pressable onPress={() => setOpen(open?.id === c.id ? null : c)} style={{ flex: 1 }}>
              <Text style={{ fontWeight: '500' }}>{c.name} <Text style={styles.muted}>({c.productCount} sản phẩm) {open?.id === c.id ? '▾' : '▸'}</Text></Text>
            </Pressable>
            <Text onPress={() => move(i, -1)} style={{ paddingHorizontal: 6, color: C.muted }}>▲</Text>
            <Text onPress={() => move(i, 1)} style={{ paddingHorizontal: 6, color: C.muted }}>▼</Text>
          </Row>
          {open?.id === c.id ? (
            <View style={{ marginTop: 8 }}>
              {open.renaming !== undefined ? (
                <Row style={{ gap: 8, marginBottom: 8 }}>
                  <TextInput value={open.renaming} onChangeText={v => setOpen({ ...open, renaming: v })} style={[styles.input, { flex: 1 }]} />
                  <Button small title="Lưu" onPress={() => run(setBusy, async () => { await put(`/seller/shop-categories/${c.id}`, { name: open.renaming.trim() }); setOpen(null); reload() })} />
                </Row>
              ) : null}
              <Row style={{ gap: 8, marginBottom: 8 }}>
                <Button small kind="ghost" title="Đổi tên" onPress={() => rename(c)} />
                <Button small kind="danger" title="Xóa" onPress={() => Alert.alert(`Xóa danh mục "${c.name}"?`, 'Sản phẩm không bị xóa.', [{ text: 'Không' }, { text: 'Xóa', onPress: () => run(setBusy, async () => { await del(`/seller/shop-categories/${c.id}`); reload() }) }])} />
              </Row>
              <Text style={styles.label}>Chạm để thêm/bỏ sản phẩm khỏi danh mục</Text>
              {(products || []).map(p => {
                const on = (p.shopCategoryIds || []).includes(c.id)
                return (
                  <Pressable key={p.id} onPress={() => run(setBusy, async () => { await post(`/seller/shop-categories/${c.id}/products`, { productIds: [p.id], add: !on }); reload() })}
                             style={{ flexDirection: 'row', gap: 8, alignItems: 'center', paddingVertical: 5 }}>
                    <Text style={{ fontSize: 16, color: on ? C.primary : C.faint }}>{on ? '☑' : '☐'}</Text>
                    <Text numberOfLines={1} style={{ flex: 1 }}>{p.name}</Text>
                  </Pressable>
                )
              })}
            </View>
          ) : null}
        </View>
      ))}
    </ScrollView>
  )
}

// ==================== chat auto-replies and reply templates ====================

const KINDS = [{ key: 'CHAT', label: 'Chat' }, { key: 'QUESTION', label: 'Hỏi đáp' }, { key: 'REVIEW', label: 'Đánh giá' }]

export function CareSettingsScreen() {
  const { data: settings, setData: setSettings } = useLoad(() => get('/seller/chat-settings'))
  const { data: templates, reload } = useLoad(() => get('/seller/reply-templates'))
  const [kind, setKind] = useState('CHAT')
  const [draft, setDraft] = useState('')
  const [edit, setEdit] = useState(null)
  const [busy, setBusy] = useState(false)
  if (!settings || !templates) return <Loading />
  const set = (k) => (v) => setSettings(x => ({ ...x, [k]: v }))
  return (
    <ScrollView style={styles.page} keyboardShouldPersistTaps="handled">
      <View style={styles.card}>
        <Text style={styles.h2}>💬 Tin nhắn tự động</Text>
        <Row style={{ justifyContent: 'space-between' }}>
          <Text>Trả lời tự động tin nhắn đầu tiên</Text>
          <Switch value={settings.autoReplyEnabled} onValueChange={set('autoReplyEnabled')} />
        </Row>
        {settings.autoReplyEnabled ? <Field value={settings.autoReplyMessage || ''} onChangeText={set('autoReplyMessage')} multiline maxLength={500}
                                            placeholder="Chào {ten_khach}, cảm ơn bạn đã nhắn tin cho {ten_shop}..." style={{ marginTop: 8 }} /> : null}
        <Row style={{ justifyContent: 'space-between', marginTop: 8 }}>
          <Text>Tin nhắn ngoài giờ làm việc</Text>
          <Switch value={settings.awayEnabled} onValueChange={set('awayEnabled')} />
        </Row>
        {settings.awayEnabled ? (
          <>
            <Row style={{ gap: 8, marginTop: 8 }}>
              <Field label="Mở cửa (HH:mm)" value={settings.hoursStart || ''} onChangeText={set('hoursStart')} style={{ flex: 1 }} placeholder="08:00" />
              <Field label="Đóng cửa (HH:mm)" value={settings.hoursEnd || ''} onChangeText={set('hoursEnd')} style={{ flex: 1 }} placeholder="21:00" />
            </Row>
            <Field value={settings.awayMessage || ''} onChangeText={set('awayMessage')} multiline maxLength={500} placeholder="Shop đang ngoài giờ làm việc, sẽ trả lời bạn sớm nhất..." />
          </>
        ) : null}
        <Text style={[styles.muted, { fontSize: 12 }]}>Biến dùng được: {'{ten_khach}'}, {'{ten_shop}'}, {'{ten_san_pham}'}</Text>
        <Button small title="Lưu cài đặt" disabled={busy} style={{ alignSelf: 'flex-end', marginTop: 8 }}
                onPress={() => run(setBusy, async () => setSettings(await put('/seller/chat-settings', settings)), 'Đã lưu')} />
      </View>

      <Text style={[styles.h2, { paddingHorizontal: 12, marginTop: 4 }]}>📝 Mẫu trả lời nhanh</Text>
      <Tabs items={KINDS.map(k => ({ ...k, count: (templates[k.key] || []).length }))} value={kind} onChange={setKind} />
      {(templates[kind] || []).map(t => (
        <View key={t.id} style={styles.card}>
          {edit?.id === t.id ? (
            <>
              <Field value={edit.content} onChangeText={v => setEdit({ ...edit, content: v })} multiline maxLength={1000} />
              <Row style={{ gap: 8 }}>
                <Button small kind="ghost" title="Hủy" onPress={() => setEdit(null)} />
                <Button small title="Lưu" disabled={busy || !edit.content.trim()} onPress={() => run(setBusy, async () => { await put(`/seller/reply-templates/${t.id}`, { content: edit.content.trim() }); setEdit(null); reload() })} />
              </Row>
            </>
          ) : (
            <>
              <Text style={{ lineHeight: 20 }}>{t.content}</Text>
              <Row style={{ gap: 16, marginTop: 6 }}>
                <Text style={{ color: C.primary }} onPress={() => setEdit({ id: t.id, content: t.content })}>Sửa</Text>
                <Text style={{ color: C.danger }} onPress={() => run(setBusy, async () => { await del(`/seller/reply-templates/${t.id}`); reload() })}>Xóa</Text>
              </Row>
            </>
          )}
        </View>
      ))}
      <View style={styles.card}>
        <Field value={draft} onChangeText={setDraft} multiline maxLength={1000} placeholder="Thêm mẫu trả lời mới..." />
        <Button small title="Thêm mẫu" disabled={busy || draft.trim().length < 5} style={{ alignSelf: 'flex-end' }}
                onPress={() => run(setBusy, async () => { await post('/seller/reply-templates', { kind, content: draft.trim() }); setDraft(''); reload() })} />
      </View>
    </ScrollView>
  )
}

// ==================== product questions ====================

const QTABS = [{ key: 'UNANSWERED', label: 'Chưa trả lời' }, { key: 'ANSWERED', label: 'Đã trả lời' }, { key: 'HIDDEN', label: 'Đã ẩn' }]

export function SellerQuestionsScreen() {
  const [status, setStatus] = useState('UNANSWERED')
  const { data, error, reload, refreshing, refresh } = useLoad(() => get('/seller/questions', { status, size: 50 }), [status])
  const { data: counts, reload: reloadCounts } = useLoad(() => get('/seller/questions/counts'))
  const [drafts, setDrafts] = useState({})
  const [busy, setBusy] = useState(false)
  const done = () => { reload(); reloadCounts() }
  const suggest = (q) => run(setBusy, async () => {
    const r = await post(`/seller/questions/${q.id}/suggest`)
    setDrafts(d => ({ ...d, [q.id]: r.answer }))
  })
  const countOf = { UNANSWERED: counts?.unanswered, ANSWERED: counts?.answered, HIDDEN: counts?.hidden }
  return (
    <View style={styles.page}>
      <Tabs items={QTABS.map(t => ({ ...t, count: countOf[t.key] }))} value={status} onChange={setStatus} />
      {!data ? (error ? <Empty error={error} onRetry={reload} /> : <Loading />) : (
        <ScrollView keyboardShouldPersistTaps="handled" refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
          {!data.items.length ? <Empty text="Không có câu hỏi nào" /> : data.items.map(q => (
            <View key={q.id} style={styles.card}>
              <Row style={{ gap: 8 }}>
                <Img uri={q.productThumbnailUrl} style={{ width: 36, height: 36 }} />
                <Text numberOfLines={1} style={[styles.muted, { flex: 1 }]}>{q.productName}</Text>
              </Row>
              <Text style={{ marginTop: 6 }}><Text style={{ fontWeight: '600' }}>{q.askerFullName}: </Text>{q.question}</Text>
              <Text style={[styles.muted, { fontSize: 11 }]}>{formatDateTime(q.createdAt)}</Text>
              {q.answer && drafts[q.id] === undefined ? <Text style={{ backgroundColor: C.bg, padding: 6, marginTop: 6 }}>Shop: {q.answer}</Text> : null}
              {status !== 'HIDDEN' && (status === 'UNANSWERED' || drafts[q.id] !== undefined) ? (
                <>
                  <Field value={drafts[q.id] ?? ''} onChangeText={v => setDrafts(d => ({ ...d, [q.id]: v }))} multiline maxLength={1000} placeholder="Nhập câu trả lời..." style={{ marginTop: 8, marginBottom: 6 }} />
                  <Row style={{ gap: 8 }}>
                    <Button small kind="ghost" title="✨ AI gợi ý" disabled={busy} onPress={() => suggest(q)} />
                    <Button small title="Trả lời" disabled={busy || !(drafts[q.id] || '').trim()}
                            onPress={() => run(setBusy, async () => { await post(`/seller/questions/${q.id}/answer`, { answer: drafts[q.id].trim() }); setDrafts(d => { const n = { ...d }; delete n[q.id]; return n }); done() })} />
                  </Row>
                </>
              ) : null}
              <Row style={{ gap: 16, marginTop: 8 }}>
                {status === 'ANSWERED' && drafts[q.id] === undefined ? <Text style={{ color: C.primary }} onPress={() => setDrafts(d => ({ ...d, [q.id]: q.answer || '' }))}>Sửa câu trả lời</Text> : null}
                {status === 'HIDDEN' ? <Text style={{ color: C.primary }} onPress={() => run(setBusy, async () => { await post(`/seller/questions/${q.id}/unhide`); done() })}>Hiện lại</Text>
                  : <Text style={{ color: C.muted }} onPress={() => run(setBusy, async () => { await post(`/seller/questions/${q.id}/hide`); done() })}>Ẩn câu hỏi</Text>}
              </Row>
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  )
}

// ==================== Shoppi Video ====================

const VSTAT = { ACTIVE: ['Đang hiển thị', C.green], HIDDEN: ['Bị ẩn', C.danger], PENDING: ['Chờ duyệt', '#f69113'] }

export function SellerVideosScreen() {
  const { data, error, reload, refreshing, refresh } = useLoad(() => get('/seller/videos'))
  const { data: products } = useLoad(() => get('/seller/products').then(r => (Array.isArray(r) ? r : r.items || []).filter(p => p.active)))
  const [form, setForm] = useState(null)
  const [busy, setBusy] = useState(false)
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  const save = () => run(setBusy, async () => {
    if (form.id) await put(`/seller/videos/${form.id}`, { caption: form.caption, productIds: form.productIds })
    else await post('/seller/videos', { videoUrl: form.videoUrl, caption: form.caption, productIds: form.productIds })
    setForm(null)
    reload()
  })
  if (form) {
    return (
      <View style={styles.page}>
        <ScrollView keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            {!form.id ? (
              form.videoUrl ? <Text style={{ color: C.green, marginBottom: 10 }}>✓ Đã tải video lên</Text>
                : <Button kind="ghost" title={busy ? 'Đang tải lên...' : '🎬 Chọn video (tối đa 60 giây)'} disabled={busy} style={{ marginBottom: 10 }}
                          onPress={() => run(setBusy, async () => { const u = await pickAndUpload('videos'); if (u) setForm(x => ({ ...x, videoUrl: u })) })} />
            ) : null}
            <Field label="Mô tả video" value={form.caption} onChangeText={v => setForm(x => ({ ...x, caption: v }))} multiline maxLength={500} />
            <Text style={styles.label}>Gắn sản phẩm ({form.productIds.length}/6)</Text>
            {(products || []).slice(0, 80).map(p => {
              const on = form.productIds.includes(p.id)
              return (
                <Pressable key={p.id} onPress={() => setForm(x => ({ ...x, productIds: on ? x.productIds.filter(i => i !== p.id) : x.productIds.length < 6 ? [...x.productIds, p.id] : x.productIds }))}
                           style={{ flexDirection: 'row', gap: 8, alignItems: 'center', paddingVertical: 5 }}>
                  <Text style={{ fontSize: 16, color: on ? C.primary : C.faint }}>{on ? '☑' : '☐'}</Text>
                  <Img uri={p.thumbnailUrl} style={{ width: 32, height: 32 }} />
                  <Text numberOfLines={1} style={{ flex: 1 }}>{p.name}</Text>
                </Pressable>
              )
            })}
          </View>
        </ScrollView>
        <BottomBar>
          <Button kind="ghost" title="Hủy" onPress={() => setForm(null)} style={{ flex: 1 }} />
          <Button title="Đăng video" disabled={busy || (!form.id && !form.videoUrl) || !form.caption.trim()} onPress={save} style={{ flex: 2 }} />
        </BottomBar>
      </View>
    )
  }
  return (
    <View style={styles.page}>
      <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        {!data.length ? <Empty text="Chưa có video. Video ngắn giúp sản phẩm tiếp cận nhiều người mua hơn." /> : data.map(v => (
          <View key={v.id} style={styles.card}>
            <Row style={{ justifyContent: 'space-between' }}>
              <Text numberOfLines={2} style={{ flex: 1, fontWeight: '500' }}>{v.caption}</Text>
              <Text style={{ color: (VSTAT[v.status] || [])[1] || C.muted, fontSize: 12 }}>{(VSTAT[v.status] || [v.status])[0]}</Text>
            </Row>
            <Text style={styles.muted}>👁 {v.viewCount} · ♥ {v.likeCount} · {(v.products || []).length} sản phẩm · {formatDateTime(v.createdAt)}</Text>
            {v.hiddenReason ? <Text style={{ color: C.danger }}>Lý do ẩn: {v.hiddenReason}</Text> : null}
            <Row style={{ gap: 16, marginTop: 6 }}>
              <Text style={{ color: C.primary }} onPress={() => setForm({ id: v.id, caption: v.caption, productIds: [...(v.productIds || [])] })}>Sửa</Text>
              <Text style={{ color: C.danger }} onPress={() => Alert.alert('Xóa video này?', '', [{ text: 'Không' }, { text: 'Xóa', onPress: () => run(setBusy, async () => { await del(`/seller/videos/${v.id}`); reload() }) }])}>Xóa</Text>
            </Row>
          </View>
        ))}
      </ScrollView>
      <BottomBar><Button title="+ Đăng video" onPress={() => setForm({ caption: '', productIds: [], videoUrl: null })} style={{ flex: 1 }} /></BottomBar>
    </View>
  )
}

