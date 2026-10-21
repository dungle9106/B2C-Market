// Shared by the buyer (AuthView) and seller (SellerAuthView) login/signup pages.

export { extractError } from './apiError'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_RE = /^0\d{9}$/

export function validateLogin({ email }) {
  if (!EMAIL_RE.test(email)) return 'Email không hợp lệ'
  return ''
}

export function validateRegister({ email, password, confirmPassword, phone }) {
  if (!EMAIL_RE.test(email)) return 'Email không hợp lệ'
  const weak = passwordProblem(password, email)
  if (weak) return weak
  if (password !== confirmPassword) return 'Mật khẩu nhập lại không khớp'
  if (phone && !PHONE_RE.test(phone)) return 'Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0)'
  return ''
}

// same rules as the server (security/PasswordPolicy), checked early for a quick answer
export function passwordProblem(password, email) {
  if (!password || password.length < 8) return 'Mật khẩu tối thiểu 8 ký tự'
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return 'Mật khẩu cần có cả chữ và số'
  const local = (email || '').toLowerCase().split('@')[0]
  if (local.length >= 4 && password.toLowerCase().includes(local)) return 'Mật khẩu không được chứa email của bạn'
  return ''
}

// where to land after a successful login/register, by role
export function homeForRole(auth) {
  if (auth.isSeller) return '/seller'
  if (auth.isAdmin) return '/admin'
  return '/'
}
