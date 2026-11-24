// Help centre, support tickets and platform policies: the web pages /help, /help/new, /help/tickets/:id and
// /policies/:slug (seller: /seller/support). Shared by the buyer and the seller app.
import React, { useState } from 'react'
import { Alert, FlatList, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native'
import * as ImagePicker from 'expo-image-picker'
import { get, post, upload, errorText } from '../api'
import { formatDateTime, formatVnd, ORDER_STATUS } from '../format'
import { FAQ, TICKET_CATEGORIES, TICKET_STATUS } from '../support'
import { BottomBar, Button, C, Empty, Field, Img, Loading, Row, Select, styles, Tabs, useLoad } from '../ui'

const STATUS_COLOR = { OPEN: '#f69113', IN_PROGRESS: '#2673dd', WAITING_CUSTOMER: C.primary, RESOLVED: C.green, CLOSED: C.muted }

export function HelpCenterScreen({ navigation }) {
  const [open, setOpen] = useState(null)
  const [tab, setTab] = useState('active')
  const { data, error, refreshing, refresh } = useLoad(() => get('/support/tickets', { active: tab === 'active', size: 30 }), [tab])
  return (
    <ScrollView style={styles.page} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <View style={styles.card}>
        <Text style={styles.h2}>Xin chào, Shoppi có thể giúp gì cho bạn?</Text>
        <Button title="✉️ Gửi yêu cầu hỗ trợ" onPress={() => navigation.navigate('NewTicket')} />
        <Text onPress={() => navigation.navigate('Policies')} style={{ color: C.primary, marginTop: 10 }}>📜 Chính sách & điều khoản ›</Text>
      </View>

      <Text style={[styles.h2, { paddingHorizontal: 14, marginTop: 4 }]}>Yêu cầu của tôi</Text>
      <Tabs items={[{ key: 'active', label: 'Đang xử lý' }, { key: 'closed', label: 'Đã đóng' }]} value={tab} onChange={setTab} />
      {!data ? (error ? <Empty error={error} onRetry={refresh} /> : <Loading />) : !data.items.length ? (
        <Text style={[styles.card, styles.muted]}>{tab === 'active' ? 'Bạn không có yêu cầu hỗ trợ nào đang xử lý.' : 'Chưa có yêu cầu nào đã đóng.'}</Text>
      ) : data.items.map(t => (
        <Pressable key={t.id} onPress={() => navigation.navigate('Ticket', { id: t.id })} style={styles.card}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Text style={styles.muted}>{t.code} · {TICKET_CATEGORIES[t.category]}</Text>
            <Text style={{ color: STATUS_COLOR[t.status], fontWeight: '600', fontSize: 12 }}>{TICKET_STATUS[t.status]}</Text>
          </Row>
          <Text style={{ fontWeight: '500', marginTop: 4 }} numberOfLines={2}>{t.subject}</Text>
          <Text style={[styles.muted, { fontSize: 12, marginTop: 2 }]}>{t.orderCode ? `Đơn ${t.orderCode} · ` : ''}cập nhật {formatDateTime(t.updatedAt)}</Text>
        </Pressable>
      ))}

      <Text style={[styles.h2, { paddingHorizontal: 14, marginTop: 8 }]}>Câu hỏi thường gặp</Text>
      {FAQ.map(group => (
        <View key={group.topic} style={styles.card}>
          <Text style={{ fontWeight: '600', marginBottom: 6 }}>{group.icon} {group.topic}</Text>
          {group.items.map(f => (
            <Pressable key={f.q} onPress={() => setOpen(open === f.q ? null : f.q)} style={{ paddingVertical: 8, borderTopWidth: 1, borderTopColor: C.border }}>
              <Text style={{ color: open === f.q ? C.primary : C.text }}>{open === f.q ? '▾' : '▸'} {f.q}</Text>
              {open === f.q ? <Text style={[styles.muted, { marginTop: 6, lineHeight: 19 }]}>{f.a}</Text> : null}
            </Pressable>
          ))}
        </View>
      ))}
    </ScrollView>
  )
}

export function NewTicketScreen({ navigation, route }) {
  const seller = route.params?.seller
  const [f, setF] = useState({ category: route.params?.category || (seller ? 'SELLER' : 'ORDER'), subject: '', content: '', orderId: route.params?.orderId || null })
  const [files, setFiles] = useState([])
  const [busy, setBusy] = useState(false)
  const { data: orders } = useLoad(() => get(seller ? '/seller/orders' : '/orders').then(r => (Array.isArray(r) ? r : r.items || []).slice(0, 30)))
  const set = (k) => (v) => setF(x => ({ ...x, [k]: v }))
  // the FAQ answers of the chosen topic, before writing
  const topic = { ORDER: 'Đơn hàng', RETURN_REFUND: 'Trả hàng / Hoàn tiền', PAYMENT: 'Thanh toán', SHIPPING: 'Vận chuyển' }[f.category]
  const hints = FAQ.find(g => g.topic === topic)?.items || []

  async function attach() {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!perm.granted) return
    const r = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.7 })
    if (r.canceled) return
    try {
      const { url } = await upload('/uploads', r.assets[0])
      setFiles(x => [...x, url])
    } catch (e) { Alert.alert('Không tải ảnh lên được', errorText(e)) }
  }

  async function send() {
    setBusy(true)
    try {
      const t = await post('/support/tickets', { ...f, subject: f.subject.trim(), content: f.content.trim(), attachmentUrls: files })
      navigation.replace('Ticket', { id: t.id })
    } catch (e) {
      Alert.alert('Không gửi được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={styles.page}>
      <ScrollView keyboardShouldPersistTaps="handled">
        <View style={styles.card}>
          <Select label="Chủ đề" value={f.category} onChange={set('category')}
                  options={Object.entries(TICKET_CATEGORIES).map(([value, label]) => ({ value, label }))} />
          {hints.length ? (
            <View style={{ backgroundColor: C.primarySoft, padding: 10, marginBottom: 12 }}>
              <Text style={{ fontWeight: '600', marginBottom: 4 }}>Có thể bạn đang cần:</Text>
              {hints.map(h => <Text key={h.q} style={[styles.muted, { marginBottom: 6 }]}><Text style={{ color: C.text }}>{h.q} </Text>{h.a}</Text>)}
            </View>
          ) : null}
          {orders?.length ? (
            <Select label="Đơn hàng liên quan (không bắt buộc)" value={f.orderId} onChange={set('orderId')}
                    options={[{ value: null, label: 'Không chọn' }, ...orders.map(o => ({ value: o.id, label: `${o.code} · ${seller ? formatVnd(o.total) : o.shopName}`, hint: ORDER_STATUS[o.status] }))]} />
          ) : null}
          <Field label="Tiêu đề" value={f.subject} onChangeText={set('subject')} maxLength={200} />
          <Field label="Nội dung" value={f.content} onChangeText={set('content')} multiline maxLength={4000}
                 placeholder="Mô tả vấn đề, thời điểm xảy ra, những gì bạn đã thử..." />
          <Row style={{ gap: 8, flexWrap: 'wrap' }}>
            {files.map(u => <Img key={u} uri={u} style={{ width: 56, height: 56 }} />)}
            {files.length < 5 ? <Button small kind="ghost" title="📎 Đính kèm ảnh" onPress={attach} /> : null}
          </Row>
        </View>
      </ScrollView>
      <BottomBar>
        <Button title={busy ? 'Đang gửi...' : 'Gửi yêu cầu'} style={{ flex: 1 }} onPress={send}
                disabled={busy || f.subject.trim().length < 5 || f.content.trim().length < 10} />
      </BottomBar>
    </View>
  )
}

export function TicketScreen({ route }) {
  const { id } = route.params
  const { data: t, error, reload, refreshing, refresh } = useLoad(() => get(`/support/tickets/${id}`), [id])
  const [text, setText] = useState('')
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  if (!t) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  async function run(fn, done) {
    setBusy(true)
    try {
      await fn()
      if (done) Alert.alert(done)
      setText('')
      reload()
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  const closed = t.status === 'CLOSED'
  return (
    <View style={styles.page}>
      <FlatList data={t.messages || []} keyExtractor={m => String(m.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                ListHeaderComponent={(
                  <View style={styles.card}>
                    <Row style={{ justifyContent: 'space-between' }}>
                      <Text style={styles.muted}>{t.code} · {TICKET_CATEGORIES[t.category]}</Text>
                      <Text style={{ color: STATUS_COLOR[t.status], fontWeight: '600' }}>{TICKET_STATUS[t.status]}</Text>
                    </Row>
                    <Text style={[styles.h2, { marginTop: 6 }]}>{t.subject}</Text>
                    {t.orderCode ? <Text style={styles.muted}>Đơn hàng {t.orderCode}</Text> : null}
                  </View>
                )}
                renderItem={({ item: m }) => (
                  <View style={[styles.card, { marginHorizontal: 8, borderLeftWidth: 3, borderLeftColor: m.staff ? C.primary : C.border }]}>
                    <Row style={{ justifyContent: 'space-between' }}>
                      <Text style={{ fontWeight: '600', color: m.staff ? C.primary : C.text }}>{m.staff ? 'Shoppi hỗ trợ' : 'Bạn'}{m.staff && m.senderName ? ` · ${m.senderName}` : ''}</Text>
                      <Text style={{ fontSize: 11, color: C.faint }}>{formatDateTime(m.createdAt)}</Text>
                    </Row>
                    <Text style={{ marginTop: 4, lineHeight: 20 }}>{m.content}</Text>
                    <Row style={{ gap: 6, marginTop: 4, flexWrap: 'wrap' }}>{(m.attachmentUrls || []).map(u => <Img key={u} uri={u} style={{ width: 72, height: 72 }} />)}</Row>
                  </View>
                )}
                ListFooterComponent={(
                  <View style={{ paddingBottom: 20 }}>
                    {t.rating ? <Text style={[styles.card, styles.muted]}>Bạn đã đánh giá {'★'.repeat(t.rating)}{t.ratingComment ? ` · “${t.ratingComment}”` : ''}</Text>
                      : ['RESOLVED', 'CLOSED'].includes(t.status) ? (
                        <View style={styles.card}>
                          <Text style={{ fontWeight: '600' }}>{t.status === 'RESOLVED' ? 'Vấn đề của bạn đã được giải quyết chưa?' : 'Đánh giá chất lượng hỗ trợ'}</Text>
                          <Row style={{ gap: 6, marginVertical: 8 }}>
                            {[1, 2, 3, 4, 5].map(s => <Text key={s} onPress={() => setRating(s)} style={{ fontSize: 28, color: s <= rating ? '#ffce3d' : C.faint }}>★</Text>)}
                          </Row>
                          <Field value={comment} onChangeText={setComment} placeholder="Góp ý thêm (không bắt buộc)" />
                          <Button title="Gửi đánh giá" disabled={!rating || busy} onPress={() => run(() => post(`/support/tickets/${id}/rating`, { rating, comment }), 'Cảm ơn bạn đã đánh giá!')} />
                        </View>
                      ) : null}
                    {!closed ? <Button small kind="ghost" title="Đóng yêu cầu" style={{ marginHorizontal: 12 }} disabled={busy}
                                       onPress={() => Alert.alert('Đóng yêu cầu này?', 'Bạn có thể gửi yêu cầu mới bất cứ lúc nào.', [{ text: 'Không' }, { text: 'Đóng', onPress: () => run(() => post(`/support/tickets/${id}/close`)) }])} /> : null}
                  </View>
                )} />
      {!closed ? (
        <BottomBar>
          <View style={{ flex: 1 }}><Field value={text} onChangeText={setText} placeholder="Nhập phản hồi..." style={{ marginBottom: 0 }} /></View>
          <Button title="Gửi" disabled={busy || !text.trim()} onPress={() => run(() => post(`/support/tickets/${id}/replies`, { content: text.trim(), attachmentUrls: [] }))} />
        </BottomBar>
      ) : null}
    </View>
  )
}

export function PoliciesScreen({ navigation }) {
  const { data, error, reload } = useLoad(() => get('/policies'))
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <ScrollView style={styles.page}>
      {data.map(p => (
        <Pressable key={p.key} onPress={() => navigation.navigate('Policy', { slug: p.slug, title: p.title })} style={[styles.card, { flexDirection: 'row', justifyContent: 'space-between' }]}>
          <Text>📜 {p.title}</Text>
          <Text style={styles.muted}>v{p.version} ›</Text>
        </Pressable>
      ))}
    </ScrollView>
  )
}

export function PolicyScreen({ route, navigation }) {
  const { slug } = route.params
  const { data: d, error, reload } = useLoad(() => get(`/policies/${slug}`).then(x => { navigation.setOptions({ title: x.title }); return x }), [slug])
  if (!d) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 30 }}>
      <View style={styles.card}>
        <Text style={styles.h2}>{d.title}</Text>
        <Text style={[styles.muted, { marginBottom: 10 }]}>Phiên bản {d.version}{d.publishedAt ? ` · hiệu lực từ ${formatDateTime(d.publishedAt)}` : ''}</Text>
        {(d.content || '').split('\n').map(l => l.trim()).filter(Boolean).map((l, i) => (
          <Text key={i} style={{ marginBottom: 8, lineHeight: 21, fontWeight: /^\d+\.|^[A-ZĐ ]{6,}$/.test(l) ? '600' : 'normal' }}>{l}</Text>
        ))}
      </View>
    </ScrollView>
  )
}
