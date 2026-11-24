import React, { useEffect, useState } from 'react'
import { KeyboardAvoidingView, Pressable, ScrollView, Text, View } from 'react-native'
import { post, get, serverUrl, setServer, setTokens, errorText } from '../api'
import { useAuth } from '../auth'
import { Button, C, Field, styles } from '../ui'
import { socialProviders, socialSignIn, PROVIDER_STYLE } from './social'

// mode: login | register | forgot (e-mail) | code (6-digit code) | newpw (new password)
const TITLE = { login: 'Đăng nhập', register: 'Đăng ký', forgot: 'Quên mật khẩu', code: 'Nhập mã xác minh', newpw: 'Đặt mật khẩu mới' }

export default function LoginScreen({ title = 'Shoppi', subtitle, allowRegister = true }) {
  const { signIn } = useAuth()
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ email: '', password: '', fullName: '', phone: '', code: '', otp: '', newPassword: '', confirm: '' })
  const [challenge, setChallenge] = useState(null)
  const [resetToken, setResetToken] = useState(null)
  const [server, setServerText] = useState(serverUrl())
  const [showServer, setShowServer] = useState(false)
  const [current, setCurrent] = useState(serverUrl())   // the server in use, shown under the form
  const [social, setSocial] = useState({ providers: [], startBase: null })
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }))
  const go = (m) => { setMode(m); setMsg('') }

  useEffect(() => { socialProviders().then(setSocial) }, [current])

  async function run(fn) {
    setBusy(true)
    setMsg('')
    try {
      if (server !== serverUrl()) { await setServer(server); setCurrent(serverUrl()) }
      await fn()
    } catch (e) {
      setMsg(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  const sendCode = () => run(async () => {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) { setMsg('Email không hợp lệ'); return }
    await post('/auth/forgot-password', { email: form.email.trim() })
    // same answer whether or not the e-mail has an account
    setForm(f => ({ ...f, otp: '' }))
    setMode('code')
    setMsg('✓ Nếu email này có tài khoản Shoppi, mã 6 số đã được gửi tới hộp thư (hiệu lực 30 phút). Kiểm tra cả thư rác.')
  })

  const checkCode = () => run(async () => {
    const r = await post('/auth/reset-password/verify-code', { email: form.email.trim(), code: form.otp })
    setResetToken(r.resetToken)
    setMode('newpw')
  })

  const savePassword = () => run(async () => {
    const p = form.newPassword
    if (p.length < 8 || !/[A-Za-z]/.test(p) || !/\d/.test(p)) { setMsg('Mật khẩu tối thiểu 8 ký tự, gồm cả chữ và số'); return }
    if (p !== form.confirm) { setMsg('Mật khẩu nhập lại không khớp'); return }
    await post('/auth/reset-password', { token: resetToken, newPassword: p })
    setForm(f => ({ ...f, password: '', otp: '', newPassword: '', confirm: '' }))
    setResetToken(null)
    setMode('login')
    setMsg('✓ Đã đặt mật khẩu mới. Hãy đăng nhập bằng mật khẩu vừa tạo.')
  })

  const submit = () => run(async () => {
    let res
    if (challenge) res = await post('/auth/login/2fa', { challenge, code: form.code })
    else if (mode === 'login') res = await post('/auth/login', { email: form.email, password: form.password })
    else res = await post('/auth/register', { email: form.email, password: form.password, fullName: form.fullName, phone: form.phone })
    if (res.twoFactorRequired) {
      setChallenge(res.challenge)
      setMsg('Nhập mã 6 số từ ứng dụng xác thực (hoặc mã dự phòng)')
      return
    }
    await signIn(res)
  })

  const withProvider = (p) => run(async () => {
    const r = await socialSignIn(p.id, { startBase: social.startBase })
    if (!r) return   // closed by the user
    if (r.error) { setMsg(r.error); return }
    if (r.challenge) {
      setChallenge(r.challenge)
      setMsg('Nhập mã 6 số từ ứng dụng xác thực (hoặc mã dự phòng)')
      return
    }
    if (!r.token) { setMsg('Đăng nhập thất bại, vui lòng thử lại'); return }
    await setTokens(r.token, r.refresh)
    const me = await get('/auth/me')
    await signIn({ ...me, token: r.token, refreshToken: r.refresh })
  })

  async function testServer() {
    setMsg('')
    try {
      await setServer(server)
      setCurrent(serverUrl())
      await get('/categories')
      setMsg('✓ Kết nối máy chủ thành công')
    } catch (e) {
      setMsg(errorText(e))
    }
  }

  const primary = challenge ? ['Xác minh', submit]
    : mode === 'forgot' ? ['Gửi mã xác minh', sendCode]
    : mode === 'code' ? ['Xác nhận mã', checkCode]
    : mode === 'newpw' ? ['Đặt mật khẩu', savePassword]
    : [mode === 'login' ? 'Đăng nhập' : 'Đăng ký', submit]
  const resetFlow = ['forgot', 'code', 'newpw'].includes(mode)

  return (
    <KeyboardAvoidingView behavior="padding" style={{ flex: 1, backgroundColor: 'white' }}>
      <ScrollView contentContainerStyle={{ padding: 24, paddingTop: 72 }} keyboardShouldPersistTaps="handled">
        <Text style={{ fontSize: 34, fontWeight: '700', color: C.primary, textAlign: 'center' }}>{title}</Text>
        {subtitle ? <Text style={[styles.muted, { textAlign: 'center', marginTop: 4 }]}>{subtitle}</Text> : null}
        <Text style={{ fontSize: 20, fontWeight: '500', marginTop: 32, marginBottom: 16 }}>{challenge ? 'Xác minh 2 bước' : TITLE[mode]}</Text>

        {challenge ? (
          <Field label="Mã xác minh" value={form.code} onChangeText={set('code')} keyboardType="number-pad" maxLength={12} />
        ) : mode === 'forgot' ? (
          <>
            <Field label="Email" value={form.email} onChangeText={set('email')} autoCapitalize="none" keyboardType="email-address" />
            <Text style={[styles.muted, { marginBottom: 12 }]}>Shoppi sẽ gửi mã 6 số tới email. Tài khoản đăng ký bằng Google / Facebook / GitHub cũng dùng cách này để tạo mật khẩu.</Text>
          </>
        ) : mode === 'code' ? (
          <>
            <Text style={[styles.muted, { marginBottom: 8 }]}>Mã đã gửi tới {form.email.trim()}</Text>
            <Field label="Mã 6 số" value={form.otp} onChangeText={(v) => set('otp')(v.replace(/\D/g, ''))} keyboardType="number-pad" maxLength={6}
                   textContentType="oneTimeCode" autoComplete="sms-otp" />
            <Pressable onPress={sendCode} disabled={busy} style={{ alignSelf: 'flex-end', marginTop: -6, marginBottom: 12 }}>
              <Text style={{ color: '#2673dd', fontSize: 13 }}>Gửi lại mã</Text>
            </Pressable>
          </>
        ) : mode === 'newpw' ? (
          <>
            <Field label="Mật khẩu mới (≥ 8 ký tự, có chữ và số)" value={form.newPassword} onChangeText={set('newPassword')} secureTextEntry />
            <Field label="Nhập lại mật khẩu mới" value={form.confirm} onChangeText={set('confirm')} secureTextEntry />
            <Text style={[styles.muted, { marginBottom: 12 }]}>Sau khi đổi, tài khoản sẽ được đăng xuất khỏi mọi thiết bị.</Text>
          </>
        ) : (
          <>
            {mode === 'register' ? <Field label="Họ và tên" value={form.fullName} onChangeText={set('fullName')} /> : null}
            <Field label="Email" value={form.email} onChangeText={set('email')} autoCapitalize="none" keyboardType="email-address" />
            {mode === 'register' ? <Field label="Số điện thoại" value={form.phone} onChangeText={set('phone')} keyboardType="phone-pad" /> : null}
            <Field label="Mật khẩu" value={form.password} onChangeText={set('password')} secureTextEntry />
            {mode === 'login' ? (
              <Pressable onPress={() => go('forgot')} style={{ alignSelf: 'flex-end', marginTop: -6, marginBottom: 12 }}>
                <Text style={{ color: '#2673dd', fontSize: 13 }}>Quên mật khẩu?</Text>
              </Pressable>
            ) : null}
          </>
        )}

        {msg ? <Text style={{ color: msg.startsWith('✓') || challenge ? C.green : C.danger, marginBottom: 12 }}>{msg}</Text> : null}
        <Button title={busy ? 'Đang xử lý...' : primary[0]} onPress={primary[1]} disabled={busy} />

        {!challenge && !resetFlow && social.providers.length ? (
          <View style={{ marginTop: 18 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 12 }}>
              <View style={{ flex: 1, height: 1, backgroundColor: C.border }} />
              <Text style={{ marginHorizontal: 12, color: C.faint, fontSize: 12 }}>HOẶC</Text>
              <View style={{ flex: 1, height: 1, backgroundColor: C.border }} />
            </View>
            {social.providers.map(p => {
              const st = PROVIDER_STYLE[p.id] || { label: p.name, icon: '•', color: C.text, bg: 'white' }
              return (
                <Pressable key={p.id} onPress={() => withProvider(p)} disabled={busy}
                           style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', height: 44, borderRadius: 4, marginBottom: 10,
                             borderWidth: 1, borderColor: st.bg === 'white' ? C.border : st.bg, backgroundColor: st.bg }}>
                  <Text style={{ fontWeight: '700', fontSize: 18, color: st.bg === 'white' ? st.color : 'white', marginRight: 10, width: 20, textAlign: 'center' }}>{st.icon}</Text>
                  <Text style={{ color: st.bg === 'white' ? C.text : 'white', fontSize: 15 }}>Tiếp tục với {p.name}</Text>
                </Pressable>
              )
            })}
          </View>
        ) : null}

        {resetFlow ? (
          <Pressable onPress={() => go('login')} style={{ marginTop: 18 }}>
            <Text style={{ textAlign: 'center', color: C.primary }}>‹ Quay lại đăng nhập</Text>
          </Pressable>
        ) : !challenge && allowRegister ? (
          <Pressable onPress={() => go(mode === 'login' ? 'register' : 'login')} style={{ marginTop: 18 }}>
            <Text style={{ textAlign: 'center', color: C.muted }}>
              {mode === 'login' ? 'Bạn mới biết đến Shoppi? ' : 'Bạn đã có tài khoản? '}
              <Text style={{ color: C.primary }}>{mode === 'login' ? 'Đăng ký' : 'Đăng nhập'}</Text>
            </Text>
          </Pressable>
        ) : challenge ? (
          <Pressable onPress={() => { setChallenge(null); setMsg('') }} style={{ marginTop: 18 }}>
            <Text style={{ textAlign: 'center', color: C.primary }}>‹ Quay lại</Text>
          </Pressable>
        ) : null}

        <Pressable onPress={() => setShowServer(!showServer)} style={{ marginTop: 40 }}>
          <Text style={{ textAlign: 'center', color: C.faint, fontSize: 12 }}>⚙ Máy chủ: {current}</Text>
        </Pressable>
        {showServer ? (
          <View style={{ marginTop: 10 }}>
            <Field label="Địa chỉ backend (máy ảo: http://10.0.2.2:8081, điện thoại thật: http://IP-máy-tính:8081)"
                   value={server} onChangeText={setServerText} autoCapitalize="none" keyboardType="url" />
            <Button title="Kiểm tra kết nối" kind="ghost" small onPress={testServer} />
          </View>
        ) : null}
      </ScrollView>
    </KeyboardAvoidingView>
  )
}
