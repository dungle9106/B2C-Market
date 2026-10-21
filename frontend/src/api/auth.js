import http from './http'

export const login = (data) => http.post('/auth/login', data)
export const register = (data) => http.post('/auth/register', data)
export const me = () => http.get('/auth/me')
export const loginTwoFactor = (challenge, code) => http.post('/auth/login/2fa', { challenge, code })
// ends this tab's session; seedRefreshToken (optional) also ends the seed new tabs start from (stores/auth.js)
export const logout = (seedRefreshToken) => http.post('/auth/logout', seedRefreshToken ? { refreshToken: seedRefreshToken } : {})
export const forgotPassword = (email) => http.post('/auth/forgot-password', { email })
export const resetPassword = (token, newPassword) => http.post('/auth/reset-password', { token, newPassword })
export const verifyEmail = (token) => http.post('/auth/verify-email', { token })

// ---- the signed-in user's security page (every role) ----
export const accountSecurity = () => http.get('/account/security')
export const changePassword = (currentPassword, newPassword) => http.post('/account/password', { currentPassword, newPassword })
export const sendVerificationEmail = () => http.post('/account/verify-email/send')
export const setupTwoFactor = () => http.post('/account/2fa/setup')
export const enableTwoFactor = (code) => http.post('/account/2fa/enable', { code })
export const disableTwoFactor = (password, code) => http.post('/account/2fa/disable', { password, code })
export const newBackupCodes = (code) => http.post('/account/2fa/backup-codes', { code })
export const revokeSession = (id) => http.delete(`/account/sessions/${id}`)
export const revokeOtherSessions = () => http.post('/account/sessions/revoke-others')

// "Dang nhap voi Google" is a full-page redirect, not an XHR: the browser
// goes to the backend, Spring Security sends it on to Google, and after
// consent the backend redirects back to /oauth2/redirect#token=...
// This must point straight at the backend (not the Vite /api proxy) because
// Google's callback URL is registered as http://localhost:8081/login/oauth2/code/google.
const backendUrl = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8081'
// redirect_origin tells the backend which frontend (e.g. localhost:5174 when
// Vite had to pick another port) to send the browser back to afterwards
export const googleLoginUrl =
  `${backendUrl}/oauth2/authorization/google?redirect_origin=${encodeURIComponent(window.location.origin)}`
