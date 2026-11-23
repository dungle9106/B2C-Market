import React, { useEffect, useState } from 'react'
import { Alert, FlatList, Pressable, RefreshControl, ScrollView, Switch, Text, TextInput, View } from 'react-native'
import * as ImagePicker from 'expo-image-picker'
import * as DocumentPicker from 'expo-document-picker'
import * as Sharing from 'expo-sharing'
import { get, post, put, patch, del, upload, download, uploadFile, errorText } from '../api'
import { formatVnd } from '../format'
import { Badge, BottomBar, Button, C, Empty, Field, Img, Loading, Row, Select, styles, Tabs, useLoad } from '../ui'
import { PriceSuggestion } from './ReportScreens'

const TABS = [{ key: 'ALL', label: 'Tất cả' }, { key: 'ACTIVE', label: 'Đang bán' }, { key: 'OUT', label: 'Hết hàng' }, { key: 'HIDDEN', label: 'Đã ẩn' }]
const MODERATION = { PENDING: 'Chờ duyệt', REJECTED: 'Bị từ chối', REMOVED: 'Vi phạm' }

const BULK = [
  { action: 'HIDE', label: 'Ẩn' },
  { action: 'SHOW', label: 'Hiện' },
  { action: 'SOLD_OUT', label: 'Hết hàng' },
  { action: 'DELETE', label: 'Xóa', danger: true }
]
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

// "Nhap / xuat Excel": the same files as the web (ProductExcelService). Export and the template are saved, then
// handed to the share sheet (Drive, Zalo, e-mail...); import checks the file first (dryRun) and asks before creating.
async function shareDownload(path, name) {
  const uri = await download(path, name)
  if (await Sharing.isAvailableAsync()) await Sharing.shareAsync(uri, { mimeType: XLSX, dialogTitle: name, UTI: 'org.openxmlformats.spreadsheetml.sheet' })
  else Alert.alert('Đã lưu tệp', uri)
}

export default function SellerProductsScreen({ navigation }) {
  const { data, error, refreshing, refresh, reload } = useLoad(() => get('/seller/products'))
  const [tab, setTab] = useState('ALL')
  const [q, setQ] = useState('')
  const [picking, setPicking] = useState(false)
  const [picked, setPicked] = useState([])
  const [busy, setBusy] = useState(false)

  async function run(fn) {
    try { await fn(); reload() } catch (e) { Alert.alert('Không thực hiện được', errorText(e)) }
  }
  const remove = (p) => Alert.alert('Xóa sản phẩm?', p.name, [{ text: 'Hủy' }, { text: 'Xóa', style: 'destructive', onPress: () => run(() => del(`/seller/products/${p.id}`)) }])

  const list = (data || []).filter(p => (!q || p.name.toLowerCase().includes(q.toLowerCase())) && ({
    ACTIVE: p.active && p.stock > 0, OUT: p.stock <= 0, HIDDEN: !p.active
  }[tab] ?? true))
  const toggle = (id) => setPicked(x => (x.includes(id) ? x.filter(i => i !== id) : [...x, id]))
  const stopPicking = () => { setPicking(false); setPicked([]) }

  async function busyRun(fn) {
    setBusy(true)
    try { await fn() } catch (e) { Alert.alert('Không thực hiện được', errorText(e)) } finally { setBusy(false) }
  }

  const bulk = (b) => Alert.alert(`${b.label} ${picked.length} sản phẩm?`, b.action === 'DELETE' ? 'Sản phẩm đã có đơn hàng chỉ có thể ẩn.' : '', [{ text: 'Hủy' }, {
    text: b.label, style: b.danger ? 'destructive' : 'default', onPress: () => busyRun(async () => {
      const r = await post('/seller/products/bulk', { ids: picked, action: b.action })
      Alert.alert(`Đã cập nhật ${r.updated} sản phẩm`, r.skipped?.length ? 'Bỏ qua: ' + r.skipped.map(x => `${x.name} (${x.reason})`).join('; ') : '')
      stopPicking()
      reload()
    })
  }])

  const importExcel = () => busyRun(async () => {
    const pick = await DocumentPicker.getDocumentAsync({ type: [XLSX, 'application/vnd.ms-excel', '*/*'], copyToCacheDirectory: true })
    if (pick.canceled || !pick.assets?.length) return
    const file = pick.assets[0]
    const check = await uploadFile('/seller/products/import', file, { dryRun: true })
    const errs = check.errors || []
    const ok = check.products?.length || 0
    const errText = errs.slice(0, 8).map(e => `• Dòng ${e.row}: ${e.message}`).join('\n') + (errs.length > 8 ? `\n... và ${errs.length - 8} lỗi khác` : '')
    if (!ok) return Alert.alert('File chưa hợp lệ', errText || 'Không tìm thấy sản phẩm nào trong file')
    Alert.alert(`Đăng ${ok} sản phẩm?`, `${check.rows} dòng · ${ok} sản phẩm hợp lệ${errs.length ? ` · ${errs.length} lỗi (bị bỏ qua):\n${errText}` : ''}\nSản phẩm mới sẽ được gửi duyệt nếu sàn yêu cầu.`, [{ text: 'Hủy' }, {
      text: 'Đăng sản phẩm', onPress: () => busyRun(async () => {
        const r = await uploadFile('/seller/products/import', file, { dryRun: false })
        Alert.alert('Đã đăng sản phẩm', `Đã tạo ${r.created} sản phẩm từ Excel.`)
        reload()
      })
    }])
  })

  const excelMenu = () => Alert.alert('Excel', 'Đăng hàng loạt hoặc xuất danh sách sản phẩm', [
    { text: 'Nhập từ Excel', onPress: importExcel },
    { text: 'Tải file mẫu', onPress: () => busyRun(() => shareDownload('/seller/products/import/template', 'Shoppi_mau_dang_san_pham.xlsx')) },
    { text: 'Xuất danh sách', onPress: () => busyRun(() => shareDownload('/seller/products/export', `Shoppi_san_pham_${new Date().toISOString().slice(0, 10)}.xlsx`)) }
  ], { cancelable: true })

  return (
    <View style={styles.page}>
      <Row style={{ backgroundColor: 'white', padding: 8, gap: 8 }}>
        <TextInput value={q} onChangeText={setQ} placeholder="🔍 Tìm sản phẩm của shop" placeholderTextColor={C.faint} style={[styles.input, { flex: 1 }]} />
        <Button small kind="ghost" title="Excel" disabled={busy} onPress={excelMenu} />
        <Button small kind={picking ? 'primary' : 'ghost'} title={picking ? 'Xong' : 'Chọn'} onPress={() => (picking ? stopPicking() : setPicking(true))} />
      </Row>
      <Tabs items={TABS} value={tab} onChange={(t) => { setTab(t); setPicked([]) }} />
      {picking ? (
        <Row style={{ backgroundColor: 'white', paddingHorizontal: 12, paddingVertical: 8, justifyContent: 'space-between' }}>
          <Text style={styles.muted}>Đã chọn {picked.length} sản phẩm</Text>
          <Text style={{ color: C.primary }} onPress={() => setPicked(picked.length === list.length ? [] : list.map(p => p.id))}>{picked.length === list.length ? 'Bỏ chọn' : 'Chọn tất cả'}</Text>
        </Row>
      ) : null}
      {!data ? (error ? <Empty error={error} onRetry={reload} /> : <Loading />) : (
        <FlatList data={list} keyExtractor={p => String(p.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                  ListEmptyComponent={<Empty text="Chưa có sản phẩm. Bấm + để đăng sản phẩm đầu tiên, hoặc Excel để đăng hàng loạt." />}
                  contentContainerStyle={{ paddingBottom: 80 }}
                  renderItem={({ item: p }) => (
                    <View style={[styles.card, { marginBottom: 1 }, picked.includes(p.id) && { backgroundColor: C.primarySoft }]}>
                      <Pressable onPress={() => (picking ? toggle(p.id) : navigation.navigate('ProductForm', { id: p.id }))}
                                 onLongPress={() => { setPicking(true); toggle(p.id) }} style={{ flexDirection: 'row', gap: 10 }}>
                        {picking ? <Text style={{ fontSize: 20, alignSelf: 'center', color: picked.includes(p.id) ? C.primary : C.faint }}>{picked.includes(p.id) ? '☑' : '☐'}</Text> : null}
                        <Img uri={p.thumbnailUrl} style={{ width: 70, height: 70 }} />
                        <View style={{ flex: 1 }}>
                          <Text numberOfLines={2}>{p.name}</Text>
                          <Row style={{ gap: 6, marginTop: 2 }}>
                            {!p.active ? <Badge text="Đã ẩn" color={C.muted} /> : null}
                            {MODERATION[p.moderationStatus] ? <Badge text={MODERATION[p.moderationStatus]} color={C.danger} /> : null}
                            {p.lowStock ? <Badge text="Sắp hết hàng" color="#b26a00" /> : null}
                          </Row>
                          <Text style={{ color: C.primary, marginTop: 2 }}>{formatVnd(p.price)}</Text>
                          <Text style={styles.muted}>Kho {p.stock} · Đã bán {p.soldCount}</Text>
                        </View>
                      </Pressable>
                      {!picking ? (
                        <Row style={{ gap: 8, justifyContent: 'flex-end', marginTop: 8 }}>
                          <Button small kind="ghost" title="Nhân bản" onPress={() => run(async () => {
                            const copy = await post(`/seller/products/${p.id}/duplicate`)
                            if (copy?.id) navigation.navigate('ProductForm', { id: copy.id })
                          })} />
                          <Button small kind="ghost" title="Kho hàng" onPress={() => navigation.navigate('ProductForm', { id: p.id })} />
                          <Button small kind="ghost" title={p.active ? 'Ẩn' : 'Hiện'} onPress={() => run(() => patch(`/seller/products/${p.id}/toggle-active`))} />
                          <Button small kind="danger" title="Xóa" onPress={() => remove(p)} />
                        </Row>
                      ) : null}
                    </View>
                  )} />
      )}
      {picking ? (
        <BottomBar>
          {BULK.map(b => <Button key={b.action} small style={{ flex: 1 }} kind={b.danger ? 'danger' : 'ghost'} title={b.label} disabled={busy || !picked.length} onPress={() => bulk(b)} />)}
        </BottomBar>
      ) : (
        <Pressable onPress={() => navigation.navigate('ProductForm', {})}
                   style={{ position: 'absolute', right: 20, bottom: 20, width: 56, height: 56, borderRadius: 28, backgroundColor: C.primary, alignItems: 'center', justifyContent: 'center', elevation: 4 }}>
          <Text style={{ color: 'white', fontSize: 30, marginTop: -2 }}>+</Text>
        </Pressable>
      )}
    </View>
  )
}

// leaf categories of the tree as "Parent › Child"
const leaves = (nodes, prefix = '') => nodes.flatMap(n => (n.children?.length ? leaves(n.children, prefix + n.name + ' › ') : [{ value: n.id, label: prefix + n.name }]))

// The request keeps everything the screen doesn't edit (variants, flash sale, shop categories...) as it was.
function toRequest(p) {
  return {
    name: p.name, sku: p.sku, description: p.description, price: p.basePrice ?? p.price, originalPrice: p.listPrice ?? p.originalPrice,
    stock: p.stock, categoryId: p.categoryId, shopCategoryIds: p.shopCategoryIds || [], imageUrls: p.imageUrls || [], videoUrl: p.videoUrl,
    freeShipping: !!p.freeShipping, returnDays: p.returnDays, flashSalePrice: p.flashSalePrice, flashSaleStart: p.flashSaleStart,
    flashSaleEnd: p.flashSaleEnd, flashSaleStock: p.flashSaleStock, flashSaleMaxPerBuyer: p.flashSaleMaxPerBuyer,
    tier1Name: p.tier1Name, tier2Name: p.tier2Name,
    variants: (p.variants || []).map(v => ({ id: v.id, name: v.name, option1: v.option1, option2: v.option2, sku: v.sku, imageUrl: v.imageUrl, extraPrice: v.extraPrice, stock: v.stock }))
  }
}

export function ProductFormScreen({ route, navigation }) {
  const id = route.params?.id
  const [f, setF] = useState(id ? null : { name: '', description: '', price: '', originalPrice: '', stock: '', categoryId: null, imageUrls: [], freeShipping: false, variants: [] })
  const [categories, setCategories] = useState([])
  const [busy, setBusy] = useState(false)
  const [aiBusy, setAiBusy] = useState(false)
  const set = (k) => (v) => setF(x => ({ ...x, [k]: v }))

  useEffect(() => {
    navigation.setOptions({ title: id ? 'Sửa sản phẩm' : 'Thêm sản phẩm' })
    get('/categories/tree').then(t => setCategories(leaves(t))).catch(() => {})
    if (id) get(`/seller/products/${id}`).then(p => setF({ ...toRequest(p), price: String(p.basePrice ?? p.price), originalPrice: p.listPrice ? String(p.listPrice) : '', stock: String(p.stock), moderationReason: p.moderationReason, moderationStatus: p.moderationStatus }))
      .catch(e => Alert.alert('Lỗi', errorText(e)))
  }, [id, navigation])

  // computer vision (as on the web): the categories the cover photo looks like, MobileNetV2 + a classifier trained on
  // Shoppi's product photos. Only a suggestion: the seller taps one or picks another.
  const cover = f?.imageUrls?.[0]
  const [vision, setVision] = useState({ for: null, items: [], busy: false })
  useEffect(() => {
    if (!cover || cover === vision.for) { if (!cover && vision.items.length) setVision({ for: null, items: [], busy: false }); return }
    let live = true
    setVision({ for: cover, items: [], busy: true })
    post('/seller/ai/category-from-image', { imageUrl: cover })
      .then(r => { if (live) setVision({ for: cover, items: r.items || [], busy: false }) })
      .catch(() => { if (live) setVision({ for: cover, items: [], busy: false }) })
    return () => { live = false }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cover])

  if (!f) return <Loading />

  async function addPhoto(camera) {
    const perm = camera ? await ImagePicker.requestCameraPermissionsAsync() : await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!perm.granted) return
    const opts = { mediaTypes: ['images'], quality: 0.8, allowsEditing: true, aspect: [1, 1] }
    const r = camera ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts)
    if (r.canceled) return
    setBusy(true)
    try {
      const { url } = await upload('/uploads', r.assets[0])
      setF(x => ({ ...x, imageUrls: [...x.imageUrls, url] }))
    } catch (e) {
      Alert.alert('Không tải ảnh lên được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  async function aiDescription() {
    if (!f.name.trim()) { Alert.alert('Nhập tên sản phẩm trước'); return }
    setAiBusy(true)
    try {
      const r = await post('/seller/ai/generate-description', { productName: f.name, categoryId: f.categoryId, tone: 'friendly' })
      set('description')(r.reply)
    } catch (e) {
      Alert.alert('AI chưa viết được', errorText(e))
    } finally {
      setAiBusy(false)
    }
  }

  async function save() {
    setBusy(true)
    const { moderationReason, moderationStatus, ...rest } = f
    const body = { ...rest, price: Number(f.price), originalPrice: f.originalPrice ? Number(f.originalPrice) : null, stock: Number(f.stock || 0) }
    try {
      if (id) await put(`/seller/products/${id}`, body)
      else await post('/seller/products', body)
      navigation.goBack()
    } catch (e) {
      Alert.alert('Không lưu được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const hasVariants = f.variants?.length > 0
  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 30 }} keyboardShouldPersistTaps="handled">
      {f.moderationReason ? <Text style={[styles.card, { color: C.danger }]}>⚠ {f.moderationReason}</Text> : null}
      <View style={styles.card}>
        <Text style={styles.label}>Hình ảnh sản phẩm ({f.imageUrls.length}/9) – ảnh đầu tiên là ảnh bìa</Text>
        <ScrollView horizontal contentContainerStyle={{ gap: 8 }}>
          {f.imageUrls.map((u, i) => (
            <Pressable key={u} onLongPress={() => setF(x => ({ ...x, imageUrls: x.imageUrls.filter(y => y !== u) }))}>
              <Img uri={u} style={{ width: 80, height: 80 }} />
              {i === 0 ? <Text style={{ position: 'absolute', bottom: 0, left: 0, right: 0, textAlign: 'center', backgroundColor: 'rgba(0,0,0,.5)', color: 'white', fontSize: 10 }}>Ảnh bìa</Text> : null}
            </Pressable>
          ))}
          {f.imageUrls.length < 9 ? (
            <>
              <Pressable onPress={() => addPhoto(true)} style={photoBtn}><Text style={{ fontSize: 22 }}>📷</Text><Text style={{ fontSize: 11, color: C.primary }}>Chụp ảnh</Text></Pressable>
              <Pressable onPress={() => addPhoto(false)} style={photoBtn}><Text style={{ fontSize: 22 }}>🖼</Text><Text style={{ fontSize: 11, color: C.primary }}>Thư viện</Text></Pressable>
            </>
          ) : null}
        </ScrollView>
        <Text style={{ fontSize: 11, color: C.faint, marginTop: 4 }}>Nhấn giữ ảnh để xóa</Text>
      </View>
      <View style={styles.card}>
        <Field label="Tên sản phẩm" value={f.name} onChangeText={set('name')} maxLength={200} />
        <Select label="Ngành hàng" value={f.categoryId} options={categories} onChange={set('categoryId')} placeholder="Chọn ngành hàng" />
        {vision.busy || vision.items.length ? (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 6, marginTop: -4, marginBottom: 10 }}>
            <Text style={{ fontSize: 12, color: C.muted }}>📷 AI nhận diện từ ảnh bìa:</Text>
            {vision.busy ? <Text style={{ fontSize: 12, color: C.faint }}>đang xem ảnh...</Text> : null}
            {vision.items.map(v => {
              const chosen = f.categoryId === v.categoryId
              return (
                <Pressable key={v.categoryId} onPress={() => set('categoryId')(v.categoryId)}
                           style={{ borderWidth: 1, borderColor: chosen ? C.primary : C.border, backgroundColor: chosen ? C.primarySoft : 'white', borderRadius: 14, paddingHorizontal: 10, paddingVertical: 3 }}>
                  <Text style={{ fontSize: 12, color: chosen ? C.primary : C.text }}>{v.path} · {Math.round(v.probability * 100)}%</Text>
                </Pressable>
              )
            })}
          </View>
        ) : null}
        <Row style={{ justifyContent: 'space-between' }}>
          <Text style={styles.label}>Mô tả sản phẩm</Text>
          <Text onPress={aiDescription} style={{ color: C.primary, marginBottom: 4 }}>{aiBusy ? 'AI đang viết...' : '✨ AI viết mô tả'}</Text>
        </Row>
        <Field value={f.description || ''} onChangeText={set('description')} multiline />
      </View>
      <View style={styles.card}>
        <PriceSuggestion name={f.name} categoryId={f.categoryId} onApply={set('price')} />
        <Row style={{ gap: 10 }}>
          <Field label="Giá bán (₫)" value={f.price} onChangeText={set('price')} keyboardType="number-pad" style={{ flex: 1 }} />
          <Field label="Giá gốc (nếu giảm giá)" value={f.originalPrice} onChangeText={set('originalPrice')} keyboardType="number-pad" style={{ flex: 1 }} />
        </Row>
        {hasVariants ? (
          <View>
            <Text style={styles.label}>Kho hàng theo phân loại</Text>
            {f.variants.map((v, i) => (
              <Row key={v.id || i} style={{ justifyContent: 'space-between', marginBottom: 6 }}>
                <Text style={{ flex: 1 }}>{v.name || [v.option1, v.option2].filter(Boolean).join(', ')}</Text>
                <TextInput value={String(v.stock ?? '')} keyboardType="number-pad" style={[styles.input, { width: 90 }]}
                           onChangeText={s => setF(x => ({ ...x, variants: x.variants.map((y, j) => (j === i ? { ...y, stock: Number(s || 0) } : y)) }))} />
              </Row>
            ))}
          </View>
        ) : <Field label="Kho hàng" value={f.stock} onChangeText={set('stock')} keyboardType="number-pad" />}
        <Row style={{ justifyContent: 'space-between' }}>
          <Text>Miễn phí vận chuyển</Text>
          <Switch value={f.freeShipping} onValueChange={set('freeShipping')} trackColor={{ true: C.primary }} />
        </Row>
      </View>
      {id && f.moderationStatus && ['REJECTED', 'REMOVED'].includes(f.moderationStatus) ? (
        <Button title="Gửi duyệt lại" kind="ghost" style={{ marginHorizontal: 12, marginBottom: 8 }}
                onPress={async () => { try { await post(`/seller/products/${id}/resubmit`); navigation.goBack() } catch (e) { Alert.alert('Lỗi', errorText(e)) } }} />
      ) : null}
      <Button title={busy ? 'Đang lưu...' : id ? 'Lưu thay đổi' : 'Đăng sản phẩm'} onPress={save} disabled={busy || !f.name || !f.price} style={{ marginHorizontal: 12 }} />
    </ScrollView>
  )
}

const photoBtn = { width: 80, height: 80, borderWidth: 1, borderStyle: 'dashed', borderColor: C.primary, alignItems: 'center', justifyContent: 'center' }
