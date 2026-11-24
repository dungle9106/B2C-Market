import React, { useState } from 'react'
import { Alert, ScrollView, Text } from 'react-native'
import { post, errorText } from '../api'
import { Button, Field, styles } from '../ui'

// "Doi mat khau" (every app): this phone stays signed in, every other device is signed out.
// An account created by a Google / Facebook / GitHub sign-in sets its first password here (route param first).
export default function ChangePasswordScreen({ navigation, route }) {
  const first = !!route?.params?.first
  const [form, setForm] = useState({ current: '', next: '', again: '' })
  const [busy, setBusy] = useState(false)
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }))

  async function save() {
    if (!first && !form.current) return Alert.alert('Nhập mật khẩu hiện tại')
    if (form.next.length < 8) return Alert.alert('Mật khẩu mới cần ít nhất 8 ký tự, gồm chữ và số')
    if (form.next !== form.again) return Alert.alert('Hai lần nhập mật khẩu mới không khớp')
    setBusy(true)
    try {
      const r = await post('/account/password', { currentPassword: first ? null : form.current, newPassword: form.next })
      Alert.alert(first ? 'Đã tạo mật khẩu' : 'Đã đổi mật khẩu', r.signedOutDevices ? `Đã đăng xuất ${r.signedOutDevices} thiết bị khác.` : 'Mật khẩu mới có hiệu lực ngay.')
      navigation.goBack()
    } catch (e) {
      Alert.alert('Không đổi được mật khẩu', errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <ScrollView style={styles.page} contentContainerStyle={{ padding: 16 }}>
      {first ? <Text style={[styles.muted, { marginBottom: 12 }]}>Tạo mật khẩu để đăng nhập được bằng email, bên cạnh tài khoản mạng xã hội.</Text>
        : <Field label="Mật khẩu hiện tại" value={form.current} onChangeText={set('current')} secureTextEntry autoCapitalize="none" />}
      <Field label="Mật khẩu mới" value={form.next} onChangeText={set('next')} secureTextEntry autoCapitalize="none" />
      <Field label="Nhập lại mật khẩu mới" value={form.again} onChangeText={set('again')} secureTextEntry autoCapitalize="none" />
      <Text style={[styles.muted, { marginBottom: 14 }]}>Ít nhất 8 ký tự, có cả chữ và số, không chứa email. Các thiết bị khác đang đăng nhập sẽ bị đăng xuất.</Text>
      <Button title={busy ? 'Đang lưu...' : first ? 'Tạo mật khẩu' : 'Đổi mật khẩu'} onPress={save} disabled={busy} />
    </ScrollView>
  )
}
