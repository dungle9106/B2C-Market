// A return dispute (web: components/orders/DisputeThread.vue): buyer, seller and Shoppi write to each other and add
// photo evidence while Shoppi decides. side: 'buyer' | 'seller'.
import React, { useCallback, useEffect, useState } from 'react'
import { Alert, Text, View } from 'react-native'
import * as ImagePicker from 'expo-image-picker'
import { get, post, upload, errorText } from '../api'
import { formatDateTime } from '../format'
import { Button, C, Field, Img, Row, styles } from '../ui'

const WHO = { BUYER: 'Người mua', SELLER: 'Người bán', ADMIN: 'Shoppi' }

export default function DisputeThread({ returnId, side, open = true }) {
  const [messages, setMessages] = useState(null)
  const [text, setText] = useState('')
  const [files, setFiles] = useState([])
  const [busy, setBusy] = useState(false)
  const load = useCallback(() => get(`/${side}/returns/${returnId}/messages`).then(setMessages).catch(() => setMessages([])), [returnId, side])
  useEffect(() => { load() }, [load])
  const me = side.toUpperCase()

  async function attach() {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!perm.granted) return
    const r = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.7 })
    if (r.canceled) return
    try {
      const { url } = await upload('/uploads', r.assets[0])
      setFiles(x => [...x, url])
    } catch (e) {
      Alert.alert('Không tải ảnh lên được', errorText(e))
    }
  }

  async function send() {
    setBusy(true)
    try {
      await post(`/${side}/returns/${returnId}/messages`, { content: text.trim(), attachments: files })
      setText('')
      setFiles([])
      load()
    } catch (e) {
      Alert.alert('Không gửi được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={{ marginTop: 10, borderTopWidth: 1, borderTopColor: C.border, paddingTop: 10 }}>
      <Text style={{ fontWeight: '600', marginBottom: 6 }}>💬 Trao đổi khiếu nại</Text>
      {(messages || []).map(m => (
        <View key={m.id} style={{ alignSelf: m.side === me ? 'flex-end' : 'flex-start', maxWidth: '85%', marginBottom: 8,
                                  backgroundColor: m.side === 'ADMIN' ? '#e8f1fb' : m.side === me ? C.primarySoft : C.bg, padding: 8, borderRadius: 6 }}>
          <Text style={{ fontSize: 11, color: m.side === 'ADMIN' ? '#2673dd' : C.muted }}>{WHO[m.side]}{m.side !== 'ADMIN' && m.authorName ? ` · ${m.authorName}` : ''} · {formatDateTime(m.createdAt)}</Text>
          {m.content ? <Text style={{ marginTop: 2 }}>{m.content}</Text> : null}
          <Row style={{ gap: 4, flexWrap: 'wrap', marginTop: 4 }}>{(m.attachments || []).map(u => <Img key={u} uri={u} style={{ width: 64, height: 64 }} />)}</Row>
        </View>
      ))}
      {messages && !messages.length ? <Text style={styles.muted}>Chưa có trao đổi nào.</Text> : null}
      {open ? (
        <>
          <Field value={text} onChangeText={setText} placeholder="Bổ sung thông tin, bằng chứng cho Shoppi..." multiline maxLength={2000} style={{ marginTop: 6, marginBottom: 6 }} />
          <Row style={{ gap: 8, flexWrap: 'wrap' }}>
            {files.map(u => <Img key={u} uri={u} style={{ width: 44, height: 44 }} />)}
            <Button small kind="ghost" title="📎 Ảnh" onPress={attach} />
            <Button small title={busy ? 'Đang gửi...' : 'Gửi'} disabled={busy || (!text.trim() && !files.length)} onPress={send} />
          </Row>
        </>
      ) : <Text style={[styles.muted, { marginTop: 4 }]}>Khiếu nại đã đóng.</Text>}
    </View>
  )
}
