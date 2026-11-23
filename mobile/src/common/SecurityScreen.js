// "Bảo mật tài khoản" (web: components/account/SecurityPanel.vue): email verification, two-step verification with an
// authenticator app (TOTP) and backup codes, signed-in devices, recent sign-ins. Every role.
import React, { useState } from 'react'
import { Alert, Linking, RefreshControl, ScrollView, Share, Text, View } from 'react-native'
import { get, post, del, errorText } from '../api'
import { formatDateTime } from '../format'
import { Button, C, Empty, Field, Loading, Row, styles, useLoad } from '../ui'

const RESULT = { SUCCESS: 'Thành công', BAD_PASSWORD: 'Sai mật khẩu', UNKNOWN_EMAIL: 'Sai email', LOCKED: 'Tài khoản bị khóa', TEMP_LOCKED: 'Đang khóa tạm',
  RATE_LIMITED: 'Bị chặn (quá nhiều lần)', TWO_FACTOR_FAILED: 'Sai mã 2 lớp', TWO_FACTOR_REQUIRED: 'Chờ mã 2 lớp' }
const METHOD = { PASSWORD: 'Mật khẩu', GOOGLE: 'Google', TOTP: 'Mã ứng dụng', BACKUP_CODE: 'Mã dự phòng', TWO_FACTOR: 'Mã 2 lớp' }

export default function SecurityScreen({ navigation }) {
  const { data: d, error, reload, refreshing, refresh } = useLoad(() => get('/account/security'))
  const [tf, setTf] = useState({ secret: null, uri: null, code: '', password: '', codes: null })
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  if (!d) return error ? <Empty error={error} onRetry={reload} /> : <Loading />

  async function act(fn) {
    setBusy(true)
    try {
      await fn()
    } catch (e) {
      Alert.alert('Không thực hiện được', errorText(e))
    } finally {
      setBusy(false)
    }
  }
  const setT = (k) => (v) => setTf(x => ({ ...x, [k]: v }))

  const startSetup = () => act(async () => {
    const s = await post('/account/2fa/setup')
    setTf(x => ({ ...x, secret: s.secret, uri: s.uri, code: '' }))
  })
  const confirmSetup = () => act(async () => {
    const r = await post('/account/2fa/enable', { code: tf.code.trim() })
    setTf({ secret: null, uri: null, code: '', password: '', codes: r.backupCodes })
    reload()
  })
  const regenerate = () => act(async () => {
    const r = await post('/account/2fa/backup-codes', { code: tf.code.trim() })
    setTf(x => ({ ...x, codes: r.backupCodes, code: '' }))
    reload()
  })
  const disable = () => Alert.alert('Tắt xác thực 2 lớp?', 'Tài khoản sẽ kém an toàn hơn.', [{ text: 'Không' }, {
    text: 'Tắt', style: 'destructive', onPress: () => act(async () => {
      await post('/account/2fa/disable', { password: tf.password, code: tf.code.trim() })
      setTf({ secret: null, uri: null, code: '', password: '', codes: null })
      reload()
    })
  }])

  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 30 }} keyboardShouldPersistTaps="handled"
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <View style={styles.card}>
        <Text style={styles.h2}>📧 Email</Text>
        <Text>{d.email}{d.provider === 'GOOGLE' ? ' (Google)' : ''}</Text>
        {d.emailVerified ? <Text style={{ color: C.green, marginTop: 4 }}>✓ Đã xác minh</Text> : (
          <Row style={{ justifyContent: 'space-between', marginTop: 6 }}>
            <Text style={{ color: '#f69113' }}>Chưa xác minh</Text>
            <Button small kind="ghost" title={sent ? 'Đã gửi, kiểm tra hộp thư' : 'Gửi email xác minh'} disabled={busy || sent}
                    onPress={() => act(async () => { await post('/account/verify-email/send'); setSent(true) })} />
          </Row>
        )}
      </View>

      <View style={styles.card}>
        <Text style={styles.h2}>🔑 Mật khẩu</Text>
        <Text style={styles.muted}>{d.passwordChangedAt ? `Đổi lần cuối ${formatDateTime(d.passwordChangedAt)}` : 'Chưa đổi lần nào'}</Text>
        <Button small kind="ghost" title="Đổi mật khẩu" style={{ alignSelf: 'flex-start', marginTop: 8 }} onPress={() => navigation.navigate('ChangePassword')} />
      </View>

      <View style={styles.card}>
        <Text style={styles.h2}>🛡 Xác thực 2 lớp</Text>
        {tf.codes ? (
          <View style={{ backgroundColor: C.primarySoft, padding: 10, marginBottom: 10 }}>
            <Text style={{ fontWeight: '600' }}>Mã dự phòng (mỗi mã dùng 1 lần, hãy lưu lại ngay):</Text>
            <Text selectable style={{ fontFamily: 'monospace', marginVertical: 6, lineHeight: 22 }}>{tf.codes.join('   ')}</Text>
            <Row style={{ gap: 8 }}>
              <Button small kind="ghost" title="Chia sẻ / lưu" onPress={() => Share.share({ message: 'Mã dự phòng Shoppi:\n' + tf.codes.join('\n') })} />
              <Button small kind="ghost" title="Đã lưu" onPress={() => setTf(x => ({ ...x, codes: null }))} />
            </Row>
          </View>
        ) : null}
        {d.twoFactorEnabled ? (
          <>
            <Text style={{ color: C.green }}>✓ Đang bật · còn {d.backupCodesLeft} mã dự phòng</Text>
            <Field label="Mã 6 số từ ứng dụng xác thực" value={tf.code} onChangeText={setT('code')} keyboardType="number-pad" maxLength={8} style={{ marginTop: 10 }} />
            <Button small kind="ghost" title="Tạo lại mã dự phòng" disabled={busy || tf.code.length < 6} onPress={regenerate} style={{ marginBottom: 12 }} />
            <Field label="Mật khẩu (để tắt)" value={tf.password} onChangeText={setT('password')} secureTextEntry />
            <Button small kind="danger" title="Tắt xác thực 2 lớp" disabled={busy || tf.code.length < 6 || !tf.password} onPress={disable} />
          </>
        ) : tf.secret ? (
          <>
            <Text style={{ marginBottom: 6 }}>1. Mở Google Authenticator / Microsoft Authenticator, thêm tài khoản bằng khóa:</Text>
            <Text selectable style={{ fontFamily: 'monospace', fontSize: 16, backgroundColor: C.bg, padding: 8, letterSpacing: 1 }}>{tf.secret}</Text>
            <Button small kind="ghost" title="Mở ứng dụng xác thực" style={{ alignSelf: 'flex-start', marginVertical: 8 }}
                    onPress={() => Linking.openURL(tf.uri).catch(() => Alert.alert('Chưa có ứng dụng xác thực', 'Hãy cài Google Authenticator rồi nhập khóa ở trên.'))} />
            <Field label="2. Nhập mã 6 số ứng dụng hiển thị" value={tf.code} onChangeText={setT('code')} keyboardType="number-pad" maxLength={6} />
            <Row style={{ gap: 8 }}>
              <Button kind="ghost" title="Hủy" onPress={() => setTf(x => ({ ...x, secret: null, uri: null }))} style={{ flex: 1 }} />
              <Button title="Bật" disabled={busy || tf.code.length !== 6} onPress={confirmSetup} style={{ flex: 1 }} />
            </Row>
          </>
        ) : (
          <>
            <Text style={styles.muted}>Khi đăng nhập trên thiết bị mới, ngoài mật khẩu bạn cần nhập mã từ ứng dụng xác thực.</Text>
            <Button small title="Bật xác thực 2 lớp" style={{ alignSelf: 'flex-start', marginTop: 8 }} disabled={busy} onPress={startSetup} />
          </>
        )}
      </View>

      <View style={styles.card}>
        <Row style={{ justifyContent: 'space-between' }}>
          <Text style={styles.h2}>📱 Thiết bị đang đăng nhập</Text>
          {d.sessions.length > 1 ? <Text style={{ color: C.primary }} onPress={() => Alert.alert('Đăng xuất tất cả thiết bị khác?', '', [{ text: 'Không' }, { text: 'Đăng xuất', onPress: () => act(async () => { await post('/account/sessions/revoke-others'); reload() }) }])}>Đăng xuất tất cả</Text> : null}
        </Row>
        {d.sessions.map(s => (
          <Row key={s.id} style={{ justifyContent: 'space-between', paddingVertical: 8, borderTopWidth: 1, borderTopColor: C.border }}>
            <View style={{ flex: 1 }}>
              <Text>{s.device}{s.current ? <Text style={{ color: C.green }}>  · thiết bị này</Text> : null}</Text>
              <Text style={[styles.muted, { fontSize: 12 }]}>{s.ip} · dùng lần cuối {formatDateTime(s.lastUsedAt)}</Text>
            </View>
            {!s.current ? <Button small kind="ghost" title="Đăng xuất" disabled={busy} onPress={() => act(async () => { await del(`/account/sessions/${s.id}`); reload() })} /> : null}
          </Row>
        ))}
      </View>

      <View style={styles.card}>
        <Text style={styles.h2}>🕘 Lịch sử đăng nhập</Text>
        {d.logins.slice(0, 15).map(l => (
          <View key={l.id} style={{ paddingVertical: 6, borderTopWidth: 1, borderTopColor: C.border }}>
            <Row style={{ justifyContent: 'space-between' }}>
              <Text style={{ color: l.result === 'SUCCESS' ? C.green : C.danger }}>{RESULT[l.result] || l.result}{l.newDevice ? ' · thiết bị mới' : ''}</Text>
              <Text style={[styles.muted, { fontSize: 12 }]}>{formatDateTime(l.time)}</Text>
            </Row>
            <Text style={[styles.muted, { fontSize: 12 }]}>{METHOD[l.method] || l.method} · {l.device} · {l.ip}</Text>
          </View>
        ))}
      </View>
    </ScrollView>
  )
}
