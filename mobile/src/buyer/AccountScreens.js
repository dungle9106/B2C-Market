import React, { useState } from 'react'
import { Alert, FlatList, KeyboardAvoidingView, Pressable, ScrollView, Text, TextInput, View } from 'react-native'
import { get, post, del, errorText } from '../api'
import { useAuth } from '../auth'
import { formatDateTime, formatVnd } from '../format'
import { BottomBar, Button, C, Empty, Img, Loading, ProductCard, Row, styles, Tabs, useLoad } from '../ui'

export function MenuItem({ icon, label, hint, onPress }) {
  return (
    <Pressable onPress={onPress} style={{ backgroundColor: 'white', flexDirection: 'row', alignItems: 'center', padding: 14, borderBottomWidth: 1, borderBottomColor: C.border }}>
      <Text style={{ width: 30, fontSize: 18 }}>{icon}</Text>
      <Text style={{ flex: 1, fontSize: 15 }}>{label}</Text>
      {hint ? <Text style={styles.muted}>{hint} </Text> : null}
      <Text style={{ color: C.faint }}>›</Text>
    </Pressable>
  )
}

const ORDER_SHORTCUTS = [['PENDING', '📝', 'Chờ xác nhận'], ['CONFIRMED', '📦', 'Chờ lấy hàng'], ['SHIPPING', '🚚', 'Đang giao'], ['DELIVERED', '⭐', 'Đánh giá']]

export default function AccountScreen({ navigation }) {
  const { user, signOut } = useAuth()
  const { data: orders } = useLoad(() => get('/orders'))
  const { data: coins } = useLoad(() => get('/buyer/coins'))
  const { data: wallet } = useLoad(() => get('/buyer/wallet'))
  const count = (s) => (orders || []).filter(o => o.status === s && (s !== 'DELIVERED' || o.items.some(i => !i.reviewed))).length
  return (
    <ScrollView style={styles.page}>
      <View style={{ backgroundColor: C.primary, paddingTop: 56, paddingBottom: 20, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <View style={{ width: 56, height: 56, borderRadius: 28, backgroundColor: 'white', alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ fontSize: 24, color: C.primary }}>{(user?.fullName || '?').charAt(0).toUpperCase()}</Text>
        </View>
        <View>
          <Text style={{ color: 'white', fontSize: 18, fontWeight: '600' }}>{user?.fullName}</Text>
          <Text style={{ color: 'white', opacity: 0.85 }}>{user?.email}</Text>
        </View>
      </View>
      <View style={[styles.card, { marginTop: 0 }]}>
        <Row style={{ justifyContent: 'space-between', marginBottom: 12 }}>
          <Text style={{ fontWeight: '500' }}>Đơn mua</Text>
          <Text onPress={() => navigation.navigate('Orders')} style={styles.muted}>Xem lịch sử mua hàng ›</Text>
        </Row>
        <Row style={{ justifyContent: 'space-around' }}>
          {ORDER_SHORTCUTS.map(([s, icon, label]) => (
            <Pressable key={s} onPress={() => navigation.navigate('Orders')} style={{ alignItems: 'center' }}>
              <Text style={{ fontSize: 24 }}>{icon}</Text>
              {count(s) ? <Text style={{ position: 'absolute', right: -8, top: -4, backgroundColor: C.primary, color: 'white', fontSize: 10, borderRadius: 8, paddingHorizontal: 5, overflow: 'hidden' }}>{count(s)}</Text> : null}
              <Text style={{ fontSize: 12, marginTop: 4 }}>{label}</Text>
            </Pressable>
          ))}
        </Row>
      </View>
      <MenuItem icon="💳" label="Ví ShoppiPay" hint={wallet ? formatVnd(wallet.balance) : undefined} onPress={() => navigation.navigate('Wallet')} />
      <MenuItem icon="🪙" label="Shoppi Xu" hint={coins ? `${coins.balance.toLocaleString('vi-VN')} Xu` : undefined} onPress={() => navigation.navigate('Coins')} />
      <MenuItem icon="🎟" label="Kho Voucher" onPress={() => navigation.navigate('VoucherWallet')} />
      <MenuItem icon="♥" label="Đã thích" onPress={() => navigation.navigate('Likes')} />
      <MenuItem icon="🏪" label="Shop đang theo dõi" onPress={() => navigation.navigate('Following')} />
      <MenuItem icon="📍" label="Địa chỉ nhận hàng" onPress={() => navigation.navigate('Addresses')} />
      <MenuItem icon="🤖" label="Trợ lý mua sắm AI" hint="Hỏi gì cũng được" onPress={() => navigation.navigate('AIChat')} />
      <MenuItem icon="🛒" label="Giỏ hàng" onPress={() => navigation.navigate('Cart')} />
      <Button title="Đăng xuất" kind="ghost" style={{ margin: 16 }}
              onPress={() => Alert.alert('Đăng xuất?', '', [{ text: 'Hủy' }, { text: 'Đăng xuất', style: 'destructive', onPress: signOut }])} />
    </ScrollView>
  )
}

// "Shoppi Xu": balance, daily check-in, history (same rules as the web page /account/coins)
export function CoinsScreen() {
  const { data, setData, error, refresh } = useLoad(() => get('/buyer/coins'))
  const { data: history, reload: reloadHistory } = useLoad(() => get('/buyer/coins/history', { size: 50 }))
  const { data: member } = useLoad(() => get('/buyer/membership'))
  const [busy, setBusy] = useState(false)

  async function checkIn() {
    setBusy(true)
    try {
      setData(await post('/buyer/coins/check-in'))
      reloadHistory()
    } catch (e) {
      Alert.alert('Không điểm danh được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  if (!data) return error ? <Empty error={error} onRetry={refresh} /> : <Loading />
  return (
    <ScrollView style={styles.page}>
      <View style={[styles.card, { alignItems: 'center' }]}>
        <Text style={{ fontSize: 32 }}>🪙</Text>
        <Text style={{ color: C.primary, fontSize: 28, fontWeight: '600' }}>{data.balance.toLocaleString('vi-VN')} Xu</Text>
        <Text style={[styles.muted, { textAlign: 'center', marginTop: 4 }]}>
          1 Xu = 1₫ khi thanh toán, dùng tối đa {data.maxSpendPercent}% giá trị đơn. Mỗi đơn hoàn tất tích {data.earnPercent}% giá trị hàng thành Xu.
        </Text>
      </View>
      {member ? (
        <View style={styles.card}>
          <Text style={{ fontWeight: '500' }}>Hạng thành viên: <Text style={{ color: C.primary }}>{member.tier.name}</Text> (Xu x{member.tier.coinMultiplierPercent / 100})</Text>
          <Text style={[styles.muted, { marginTop: 4 }]}>
            Đã chi {formatVnd(member.spend)} trong {member.windowMonths} tháng gần nhất. {member.nextTier ? `Chi thêm ${formatVnd(member.remaining)} để lên hạng ${member.nextTier.name}.` : 'Bạn đang ở hạng cao nhất.'}
          </Text>
        </View>
      ) : null}
      <View style={styles.card}>
        <Text style={{ fontWeight: '500', marginBottom: 10 }}>Điểm danh mỗi ngày</Text>
        <Row style={{ gap: 4, marginBottom: 12 }}>
          {data.rewards.map((reward, i) => {
            const done = i < data.streak
            const today = !data.checkedInToday && i === data.streak
            return (
              <View key={i} style={{ flex: 1, alignItems: 'center', paddingVertical: 8, borderWidth: 1, borderRadius: 4,
                borderColor: today ? C.primary : C.border, backgroundColor: done ? C.primarySoft : 'white' }}>
                <Text style={{ fontSize: 13, fontWeight: '600', color: today ? C.primary : C.text }}>+{reward}</Text>
                <Text style={{ fontSize: 10, color: C.muted, marginTop: 2 }}>{done ? '✓' : `Ngày ${i + 1}`}</Text>
              </View>
            )
          })}
        </Row>
        <Button title={data.checkedInToday ? 'Hôm nay đã điểm danh, mai quay lại nhé' : `Điểm danh nhận ${data.todayReward} Xu`}
                disabled={data.checkedInToday || busy} onPress={checkIn} />
      </View>
      <View style={styles.card}>
        <Text style={{ fontWeight: '500', marginBottom: 6 }}>Lịch sử Xu</Text>
        {!history?.items.length ? <Text style={styles.muted}>Chưa có giao dịch Xu nào.</Text> : null}
        {history?.items.map(t => (
          <Row key={t.id} style={{ justifyContent: 'space-between', gap: 10, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: C.border }}>
            <View style={{ flex: 1 }}>
              <Text>{t.note}</Text>
              <Text style={{ fontSize: 12, color: C.faint }}>{formatDateTime(t.createdAt)}</Text>
            </View>
            <Text style={{ fontWeight: '600', color: t.amount > 0 ? C.green : C.primary }}>{t.amount > 0 ? '+' : ''}{t.amount.toLocaleString('vi-VN')}</Text>
          </Row>
        ))}
      </View>
    </ScrollView>
  )
}

const TOP_UPS = [50000, 100000, 200000, 500000, 1000000]

// "Ví ShoppiPay": balance, simulated top-up, history (same rules as the web page /account/wallet)
export function WalletScreen() {
  const { data, setData, error, refresh } = useLoad(() => get('/buyer/wallet'))
  const { data: history, reload: reloadHistory } = useLoad(() => get('/buyer/wallet/history', { size: 50 }))
  const [busy, setBusy] = useState(false)

  async function topUp(amount) {
    setBusy(true)
    try {
      setData(await post('/buyer/wallet/top-up', { amount }))
      reloadHistory()
    } catch (e) {
      Alert.alert('Không nạp được tiền', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  if (!data) return error ? <Empty error={error} onRetry={refresh} /> : <Loading />
  return (
    <ScrollView style={styles.page}>
      <View style={[styles.card, { alignItems: 'center' }]}>
        <Text style={{ fontSize: 32 }}>💳</Text>
        <Text style={styles.muted}>Số dư ví ShoppiPay</Text>
        <Text style={{ color: C.primary, fontSize: 28, fontWeight: '600' }}>{formatVnd(data.balance)}</Text>
      </View>
      <View style={styles.card}>
        <Text style={{ fontWeight: '500', marginBottom: 4 }}>Nạp tiền</Text>
        <Text style={[styles.muted, { marginBottom: 10 }]}>Môi trường thử nghiệm: tiền nạp là mô phỏng, không trừ tiền thật.</Text>
        <Row style={{ flexWrap: 'wrap', gap: 8 }}>
          {TOP_UPS.map(v => <Button key={v} title={formatVnd(v)} kind="ghost" small disabled={busy} onPress={() => topUp(v)} />)}
        </Row>
      </View>
      <View style={styles.card}>
        <Text style={{ fontWeight: '500', marginBottom: 6 }}>Lịch sử giao dịch</Text>
        {!history?.items.length ? <Text style={styles.muted}>Chưa có giao dịch nào.</Text> : null}
        {history?.items.map(t => (
          <Row key={t.id} style={{ justifyContent: 'space-between', gap: 10, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: C.border }}>
            <View style={{ flex: 1 }}>
              <Text>{t.note}</Text>
              <Text style={{ fontSize: 12, color: C.faint }}>{formatDateTime(t.createdAt)}</Text>
            </View>
            <Text style={{ fontWeight: '600', color: t.amount > 0 ? C.green : C.primary }}>{t.amount > 0 ? '+' : '-'}{formatVnd(Math.abs(t.amount))}</Text>
          </Row>
        ))}
      </View>
    </ScrollView>
  )
}

const WALLET_TABS = [{ key: 'ALL', label: 'Tất cả' }, { key: 'PLATFORM', label: 'Shoppi' }, { key: 'SHOP', label: 'Của Shop' }, { key: 'EXPIRED', label: 'Hết hiệu lực' }]
const STATE = { UPCOMING: 'Chưa đến thời gian sử dụng', UNAVAILABLE: 'Chưa dùng được', EXPIRED: 'Đã hết hạn' }

function Ticket({ v, children, off }) {
  return (
    <View style={{ flexDirection: 'row', backgroundColor: 'white', marginHorizontal: 10, marginTop: 8, opacity: off ? 0.55 : 1, minHeight: 90 }}>
      <View style={{ width: 90, backgroundColor: v.scope === 'PLATFORM' ? C.primary : C.green, alignItems: 'center', justifyContent: 'center', padding: 6 }}>
        <Text style={{ fontSize: 22, color: 'white' }}>{v.type === 'SHIPPING' ? '🚚' : '%'}</Text>
        <Text numberOfLines={1} style={{ color: 'white', fontSize: 11 }}>{v.scope === 'PLATFORM' ? 'SHOPPI' : v.shopName}</Text>
      </View>
      <View style={{ flex: 1, padding: 10, justifyContent: 'center' }}>
        <Text style={{ fontWeight: '500' }}>{v.title}</Text>
        <Text style={styles.muted}>Đơn tối thiểu {formatVnd(v.minOrder || 0)}</Text>
        <Text style={{ fontSize: 12, color: C.faint }}>HSD {formatDateTime(v.endAt)} · {v.code}</Text>
      </View>
      <View style={{ justifyContent: 'center', paddingRight: 10, gap: 6 }}>{children}</View>
    </View>
  )
}

export function VoucherWalletScreen({ navigation }) {
  const { data, error, reload } = useLoad(() => get('/buyer/voucher-wallet'))
  const [tab, setTab] = useState('ALL')
  const [code, setCode] = useState('')
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  const run = async (fn, ok) => {
    try { await fn(); if (ok) Alert.alert(ok); reload() } catch (e) { Alert.alert('Không thực hiện được', errorText(e)) }
  }
  const shown = data.items.filter(it => (tab === 'EXPIRED' ? it.state === 'EXPIRED' : it.state !== 'EXPIRED' && (tab === 'ALL' || it.voucher.scope === tab)))
  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 20 }}>
      <Row style={{ backgroundColor: 'white', padding: 10, gap: 8 }}>
        <TextInput value={code} onChangeText={setCode} placeholder="Nhập mã voucher" autoCapitalize="characters" placeholderTextColor={C.faint} style={[styles.input, { flex: 1 }]} />
        <Button title="Lưu" small disabled={!code.trim()} onPress={() => run(async () => { await post('/buyer/voucher-wallet/code', { code: code.trim() }); setCode('') }, 'Đã lưu voucher vào kho')} />
      </Row>
      <Tabs items={WALLET_TABS} value={tab} onChange={setTab} />
      {shown.map(it => (
        <Ticket key={it.voucher.id} v={it.voucher} off={it.state !== 'USABLE'}>
          {it.state === 'USABLE' ? (
            <Button title="Dùng" small onPress={() => (it.voucher.scope === 'SHOP' ? navigation.navigate('Shop', { id: it.voucher.shopId }) : navigation.navigate('Tabs'))} />
          ) : <Text style={{ fontSize: 11, color: '#b26a00', width: 70 }}>{STATE[it.state]}</Text>}
          <Text onPress={() => run(() => del(`/buyer/voucher-wallet/${it.voucher.id}`))} style={{ color: C.faint, textAlign: 'center', fontSize: 12 }}>Xóa</Text>
        </Ticket>
      ))}
      {!shown.length ? <Empty text="Chưa có voucher nào ở mục này" /> : null}
      {data.collectable.length ? <Text style={[styles.h2, { margin: 12, marginBottom: 0 }]}>Voucher có thể lưu</Text> : null}
      {data.collectable.map(v => (
        <Ticket key={v.id} v={v}><Button title="Lưu" small onPress={() => run(() => post(`/buyer/voucher-wallet/${v.id}`))} /></Ticket>
      ))}
    </ScrollView>
  )
}

export function LikesScreen({ navigation }) {
  const { data, error, reload } = useLoad(() => get('/buyer/likes', { size: 60 }))
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <FlatList style={styles.page} data={data.items} numColumns={2} keyExtractor={p => String(p.id)} contentContainerStyle={{ padding: 4 }}
              ListEmptyComponent={<Empty text="Bạn chưa thích sản phẩm nào. Bấm ♡ ở trang sản phẩm để lưu lại." />}
              renderItem={({ item }) => <ProductCard p={item} onPress={() => navigation.navigate('Product', { id: item.id })} />} />
  )
}

export function FollowingScreen({ navigation }) {
  const { data, error, reload } = useLoad(() => get('/buyer/follows', { size: 60 }))
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <FlatList style={styles.page} data={data.items} keyExtractor={s => String(s.id)}
              ListEmptyComponent={<Empty text="Bạn chưa theo dõi shop nào" />}
              renderItem={({ item: s }) => (
                <Pressable onPress={() => navigation.navigate('Shop', { id: s.id })} style={[styles.card, { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 1 }]}>
                  <Img uri={s.logoUrl} style={{ width: 48, height: 48, borderRadius: 24 }} />
                  <View style={{ flex: 1 }}>
                    <Text style={{ fontWeight: '600' }}>{s.name}</Text>
                    <Text style={styles.muted}>{s.productCount} sản phẩm · {s.followers} người theo dõi</Text>
                  </View>
                  <Button title="Bỏ theo dõi" kind="ghost" small onPress={async () => { await del(`/buyer/follows/${s.id}`); reload() }} />
                </Pressable>
              )} />
  )
}

// RAG chatbot: answers from the catalogue, the buyer's orders and the shop policies
export function AIChatScreen({ navigation }) {
  const { data: history, setData: setHistory, error } = useLoad(() => get('/ai/history'))
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [extras, setExtras] = useState({})   // index -> { products, orders } of the answers in this session

  async function send(message = text) {
    if (!message.trim()) return
    setBusy(true)
    setText('')
    const at = (history || []).length
    setHistory(h => [...(h || []), { message, reply: null }])
    try {
      const r = await post('/ai/chat', { message })
      setHistory(h => h.map((x, i) => (i === at ? { message, reply: r.reply, createdAt: r.createdAt } : x)))
      setExtras(x => ({ ...x, [at]: r }))
    } catch (e) {
      setHistory(h => h.map((x, i) => (i === at ? { message, reply: '⚠ ' + errorText(e) } : x)))
    } finally {
      setBusy(false)
    }
  }

  if (!history) return error ? <Empty error={error} /> : <Loading />
  const SAMPLES = ['Tìm áo thun dưới 200k', 'Đơn hàng của tôi đang ở đâu?', 'Chính sách đổi trả thế nào?']
  return (
    <KeyboardAvoidingView behavior="padding" style={styles.page} keyboardVerticalOffset={90}>
      <FlatList data={history.map((h, i) => ({ ...h, i })).reverse()} inverted keyExtractor={h => String(h.i)} contentContainerStyle={{ padding: 12 }}
                ListFooterComponent={!history.length ? (
                  <View style={[styles.card, { borderRadius: 8 }]}>
                    <Text>Xin chào 👋 Mình là trợ lý mua sắm AI của Shoppi. Bạn cần tìm gì hôm nay?</Text>
                    {SAMPLES.map(s => <Text key={s} onPress={() => send(s)} style={{ color: C.primary, marginTop: 8 }}>› {s}</Text>)}
                  </View>
                ) : null}
                renderItem={({ item: h }) => (
                  <View>
                    <View style={{ alignSelf: 'flex-end', backgroundColor: C.primary, borderRadius: 8, padding: 10, maxWidth: '80%', marginVertical: 4 }}>
                      <Text style={{ color: 'white' }}>{h.message}</Text>
                    </View>
                    <View style={{ alignSelf: 'flex-start', backgroundColor: 'white', borderRadius: 8, padding: 10, maxWidth: '85%', marginVertical: 4 }}>
                      <Text>{h.reply ?? 'Đang trả lời...'}</Text>
                      {(extras[h.i]?.products || []).slice(0, 4).map(p => (
                        <Pressable key={p.id} onPress={() => navigation.navigate('Product', { id: p.id })} style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
                          <Img uri={p.thumbnailUrl} style={{ width: 44, height: 44 }} />
                          <View style={{ flex: 1 }}>
                            <Text numberOfLines={1}>{p.name}</Text>
                            <Text style={{ color: C.primary }}>{formatVnd(p.price)}</Text>
                          </View>
                        </Pressable>
                      ))}
                      {(extras[h.i]?.orders || []).map(o => (
                        <Text key={o.id} onPress={() => navigation.navigate('OrderDetail', { id: o.id })} style={{ color: '#2673dd', marginTop: 6 }}>Xem đơn {o.code} ›</Text>
                      ))}
                    </View>
                  </View>
                )} />
      <BottomBar>
        <TextInput value={text} onChangeText={setText} placeholder="Hỏi trợ lý AI..." placeholderTextColor={C.faint} style={[styles.input, { flex: 1 }]} onSubmitEditing={() => send()} />
        <Button title="Gửi" small onPress={() => send()} disabled={busy || !text.trim()} />
      </BottomBar>
    </KeyboardAvoidingView>
  )
}
