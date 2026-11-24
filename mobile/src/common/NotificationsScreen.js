import React, { useState } from 'react'
import { FlatList, Pressable, RefreshControl, Text, View } from 'react-native'
import { get, post } from '../api'
import { formatDateTime } from '../format'
import { C, Empty, Loading, Row, styles, Tabs, useLoad } from '../ui'

const TYPES = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'ORDER', label: 'Đơn hàng' },
  { key: 'PROMOTION', label: 'Khuyến mãi' },
  { key: 'SYSTEM', label: 'Hệ thống' }
]
const ICON = { ORDER: '📦', PROMOTION: '🎁', MESSAGE: '💬', SUPPORT: '🛟', SYSTEM: '🔔' }

// openLink: '/orders/12' style links from the backend -> a screen of this app (or null)
export default function NotificationsScreen({ navigation, openLink }) {
  const [type, setType] = useState('ALL')
  const { data, setData, error, refreshing, refresh, reload } = useLoad(
    () => get('/notifications', { type: type === 'ALL' ? undefined : type, size: 50 }), [type])

  async function open(n) {
    if (!n.read) {
      post(`/notifications/${n.id}/read`).catch(() => {})
      setData(d => ({ ...d, items: d.items.map(x => (x.id === n.id ? { ...x, read: true } : x)) }))
    }
    const target = openLink?.(n.link)
    if (target) navigation.navigate(...target)
  }

  return (
    <View style={styles.page}>
      <Tabs items={TYPES} value={type} onChange={setType} />
      <Text onPress={async () => { await post('/notifications/read-all'); reload() }} style={{ textAlign: 'right', color: '#2673dd', padding: 10 }}>
        Đánh dấu đã đọc tất cả
      </Text>
      {!data ? (error ? <Empty error={error} onRetry={reload} /> : <Loading />) : (
        <FlatList data={data.items} keyExtractor={n => String(n.id)} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
                  ListEmptyComponent={<Empty text="Chưa có thông báo" />}
                  renderItem={({ item: n }) => (
                    <Pressable onPress={() => open(n)} style={{ backgroundColor: n.read ? 'white' : '#fff5f2', padding: 14, borderBottomWidth: 1, borderBottomColor: C.border }}>
                      <Row style={{ alignItems: 'flex-start', gap: 10 }}>
                        <Text style={{ fontSize: 20 }}>{ICON[n.type] || '🔔'}</Text>
                        <View style={{ flex: 1 }}>
                          <Text style={{ fontWeight: n.read ? '400' : '600' }}>{n.title}</Text>
                          <Text style={[styles.muted, { marginTop: 2 }]}>{n.content}</Text>
                          <Text style={{ fontSize: 11, color: C.faint, marginTop: 4 }}>{formatDateTime(n.createdAt)}</Text>
                        </View>
                      </Row>
                    </Pressable>
                  )} />
      )}
    </View>
  )
}
