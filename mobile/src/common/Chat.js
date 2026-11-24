// Buyer <-> shop chat. side = 'buyer' (/api/buyer/chats) or 'seller' (/api/seller/chats, the shop inbox)
import React, { useCallback, useEffect, useRef, useState } from 'react'
import { FlatList, KeyboardAvoidingView, Pressable, RefreshControl, Text, TextInput, View } from 'react-native'
import { get, post, errorText } from '../api'
import { formatDateTime, formatVnd } from '../format'
import { BottomBar, Button, C, Empty, Img, Loading, Row, styles, useLoad } from '../ui'

const base = (side) => (side === 'seller' ? '/seller/chats' : '/buyer/chats')

export function ChatListScreen({ navigation, side }) {
  const { data, error, refreshing, refresh, reload } = useLoad(() => get(base(side)), [side])
  // new messages while the list is open
  useEffect(() => {
    const t = setInterval(() => reload(), 8000)
    return () => clearInterval(t)
  }, [reload])
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />
  return (
    <FlatList style={styles.page} data={data} keyExtractor={c => String(c.id)}
              refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
              ListEmptyComponent={<Empty text={side === 'seller' ? 'Chưa có khách nào nhắn tin' : 'Chưa có cuộc trò chuyện nào. Bấm "Chat ngay" ở trang sản phẩm để hỏi shop.'} />}
              renderItem={({ item: c }) => (
                <Pressable onPress={() => navigation.navigate('ChatThread', { id: c.id, title: side === 'seller' ? c.buyerName : c.shopName })}
                           style={{ backgroundColor: 'white', padding: 14, borderBottomWidth: 1, borderBottomColor: C.border, flexDirection: 'row', gap: 12 }}>
                  <Img uri={side === 'seller' ? null : c.shopLogoUrl} style={{ width: 44, height: 44, borderRadius: 22 }} />
                  <View style={{ flex: 1 }}>
                    <Row style={{ justifyContent: 'space-between' }}>
                      <Text style={{ fontWeight: '600' }}>{side === 'seller' ? c.buyerName : c.shopName}</Text>
                      <Text style={{ fontSize: 11, color: C.faint }}>{formatDateTime(c.lastMessageAt)}</Text>
                    </Row>
                    <Row style={{ justifyContent: 'space-between', marginTop: 4 }}>
                      <Text numberOfLines={1} style={[styles.muted, { flex: 1 }]}>{c.lastMessage || 'Bắt đầu trò chuyện'}</Text>
                      {c.unread ? <Text style={{ backgroundColor: C.primary, color: 'white', fontSize: 11, borderRadius: 9, paddingHorizontal: 6, overflow: 'hidden' }}>{c.unread}</Text> : null}
                    </Row>
                  </View>
                </Pressable>
              )} />
  )
}

export function ChatThreadScreen({ route, side }) {
  const { id } = route.params
  const [messages, setMessages] = useState(null)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const last = useRef(0)

  const poll = useCallback(async () => {
    try {
      const fresh = await get(`${base(side)}/${id}/messages`, last.current ? { after: last.current } : undefined)
      if (fresh.length) {
        last.current = fresh[fresh.length - 1].id
        setMessages(m => [...(m || []), ...fresh])
        post(`${base(side)}/${id}/read`).catch(() => {})
      } else {
        setMessages(m => m || [])
      }
    } catch (e) {
      setError(errorText(e))
    }
  }, [id, side])

  useEffect(() => {
    poll()
    const t = setInterval(poll, 4000)
    return () => clearInterval(t)
  }, [poll])

  async function send(content = text) {
    if (!content.trim()) return
    setBusy(true)
    try {
      await post(`${base(side)}/${id}/messages`, { content })
      setText('')
      await poll()
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  // seller: AI drafts a reply to the buyer's last question
  async function suggest() {
    setBusy(true)
    try {
      const r = await post(`/seller/chats/${id}/suggest`)
      setText(r.answer || '')
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  if (!messages) return error ? <Empty error={error} /> : <Loading />
  const mine = side === 'seller' ? 'SELLER' : 'BUYER'
  return (
    <KeyboardAvoidingView behavior="padding" style={styles.page} keyboardVerticalOffset={90}>
      <FlatList data={[...messages].reverse()} inverted keyExtractor={m => String(m.id)} contentContainerStyle={{ padding: 12 }}
                renderItem={({ item: m }) => {
                  const own = m.senderSide === mine
                  return (
                    <View style={{ alignItems: own ? 'flex-end' : 'flex-start', marginVertical: 4 }}>
                      <View style={{ maxWidth: '80%', backgroundColor: own ? '#ffe9e2' : 'white', borderRadius: 8, padding: 10 }}>
                        {m.autoReply ? <Text style={{ fontSize: 10, color: C.muted }}>Tự động trả lời</Text> : null}
                        {m.content ? <Text style={{ fontSize: 15 }}>{m.content}</Text> : null}
                        {m.imageUrl ? <Img uri={m.imageUrl} style={{ width: 180, height: 180, marginTop: 4 }} /> : null}
                        {m.product ? (
                          <Row style={{ gap: 8, marginTop: 4 }}>
                            <Img uri={m.product.thumbnailUrl} style={{ width: 40, height: 40 }} />
                            <View style={{ flex: 1 }}>
                              <Text numberOfLines={1}>{m.product.name}</Text>
                              <Text style={{ color: C.primary }}>{formatVnd(m.product.price)}</Text>
                            </View>
                          </Row>
                        ) : null}
                        {m.order ? <Text style={{ color: C.muted, marginTop: 4 }}>Đơn {m.order.code}</Text> : null}
                        <Text style={{ fontSize: 10, color: C.faint, marginTop: 4, alignSelf: 'flex-end' }}>{formatDateTime(m.createdAt)}</Text>
                      </View>
                    </View>
                  )
                }} />
      {error ? <Text style={{ color: C.danger, paddingHorizontal: 12 }}>{error}</Text> : null}
      <BottomBar>
        {side === 'seller' ? <Button title="✨ AI" kind="ghost" small onPress={suggest} disabled={busy} /> : null}
        <TextInput value={text} onChangeText={setText} placeholder="Nhập tin nhắn..." multiline
                   style={[styles.input, { flex: 1, maxHeight: 100 }]} placeholderTextColor={C.faint} />
        <Button title="Gửi" small onPress={() => send()} disabled={busy || !text.trim()} />
      </BottomBar>
    </KeyboardAvoidingView>
  )
}
