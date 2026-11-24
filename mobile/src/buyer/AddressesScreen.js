import React, { useState } from 'react'
import { Alert, Pressable, ScrollView, Text, View } from 'react-native'
import { get, post, patch, del, errorText } from '../api'
import { useAuth } from '../auth'
import { PROVINCES } from '../provinces'
import { Badge, Button, C, Empty, Field, Loading, Row, Select, styles, useLoad } from '../ui'

// route.params.pick: opened from checkout to choose the delivery address (sent back as params)
export default function AddressesScreen({ navigation, route }) {
  const pick = route.params?.pick
  const { data, error, reload } = useLoad(() => get('/buyer/addresses'))
  if (!data) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  async function makeDefault(a) {
    try { await patch(`/buyer/addresses/${a.id}/default`); reload() } catch (e) { Alert.alert('Lỗi', errorText(e)) }
  }
  function remove(a) {
    Alert.alert('Xóa địa chỉ?', a.addressLine, [{ text: 'Hủy' }, {
      text: 'Xóa', style: 'destructive',
      onPress: async () => { try { await del(`/buyer/addresses/${a.id}`); reload() } catch (e) { Alert.alert('Lỗi', errorText(e)) } }
    }])
  }

  return (
    <ScrollView style={styles.page}>
      {data.map(a => (
        <Pressable key={a.id} onPress={() => { if (pick) navigation.popTo('Checkout', { addressId: a.id }, { merge: true }) }} style={styles.card}>
          <Row style={{ gap: 8 }}>
            <Text style={{ fontWeight: '600' }}>{a.recipientName}</Text>
            <Text style={styles.muted}>| {a.phone}</Text>
            {a.default ? <Badge text="Mặc định" /> : null}
          </Row>
          <Text style={{ marginTop: 4 }}>{a.addressLine}</Text>
          <Text style={styles.muted}>{[a.ward, a.district, a.province].filter(Boolean).join(', ')}</Text>
          <Row style={{ gap: 16, marginTop: 8 }}>
            {!a.default ? <Text onPress={() => makeDefault(a)} style={{ color: '#2673dd' }}>Đặt làm mặc định</Text> : null}
            <Text onPress={() => remove(a)} style={{ color: C.danger }}>Xóa</Text>
          </Row>
        </Pressable>
      ))}
      {!data.length ? <Empty text="Bạn chưa có địa chỉ nào" /> : null}
      <Button title="+ Thêm địa chỉ mới" kind="ghost" style={{ margin: 12 }}
              onPress={() => navigation.navigate('AddressForm', { first: !data.length })} />
    </ScrollView>
  )
}

export function AddressFormScreen({ navigation, route }) {
  const { user } = useAuth()
  const [f, setF] = useState({ recipientName: user?.fullName || '', phone: user?.phone || '', province: null, district: '', ward: '', addressLine: '', isDefault: !!route.params?.first })
  const [busy, setBusy] = useState(false)
  const set = (k) => (v) => setF(x => ({ ...x, [k]: v }))

  async function save() {
    if (!f.province) { Alert.alert('Thiếu thông tin', 'Vui lòng chọn tỉnh/thành phố'); return }
    setBusy(true)
    try {
      await post('/buyer/addresses', f)
      navigation.goBack()
    } catch (e) {
      Alert.alert('Không lưu được', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <ScrollView style={styles.page} contentContainerStyle={{ padding: 14 }} keyboardShouldPersistTaps="handled">
      <Field label="Họ và tên người nhận" value={f.recipientName} onChangeText={set('recipientName')} />
      <Field label="Số điện thoại" value={f.phone} onChangeText={set('phone')} keyboardType="phone-pad" />
      <Select label="Tỉnh/Thành phố" value={f.province} options={PROVINCES} onChange={set('province')} />
      <Field label="Quận/Huyện (nếu có)" value={f.district} onChangeText={set('district')} />
      <Field label="Phường/Xã" value={f.ward} onChangeText={set('ward')} />
      <Field label="Địa chỉ cụ thể (số nhà, tên đường)" value={f.addressLine} onChangeText={set('addressLine')} />
      <Pressable onPress={() => set('isDefault')(!f.isDefault)} style={{ marginBottom: 16 }}>
        <Text>{f.isDefault ? '☑' : '☐'} Đặt làm địa chỉ mặc định</Text>
      </Pressable>
      <View><Button title={busy ? 'Đang lưu...' : 'Hoàn thành'} onPress={save} disabled={busy} /></View>
    </ScrollView>
  )
}
