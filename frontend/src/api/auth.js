import http from './http'

export const login = (data) => http.post('/auth/login', data)
export const register = (data) => http.post('/auth/register', data)
export const me = () => http.get('/auth/me')
export const loginTwoFactor = (challenge, code) => http.post('/auth/login/2fa', { challenge, code })
// ends this tab's session; seedRefreshToken (optional) also ends the seed new tabs start from (stores/auth.js)
export const logout = (seedRefreshToken) => http.post('/auth/logout', seedRefreshToken ? { refreshToken: seedRefreshToken } : {})
export const forgotPassword = (email) => http.post('/auth/forgot-password', { email })
export const verifyResetCode = (email, code) => http.post('/auth/reset-password/verify-code', { email, code })
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
export const oauthLinkToken = () => http.post('/account/oauth/link-token')
export const unlinkOAuth = (provider) => http.delete(`/account/oauth/${provider}`)

// "Tiep tuc voi Google / Facebook / GitHub" is a full-page redirect, not an XHR: the browser
// goes to the backend, Spring Security sends it on to the provider, and after consent the
// backend redirects back to /oauth2/redirect#token=...
// It must point straight at the backend (not the Vite /api proxy), because the provider only
// returns to the callback registered with it. When the backend has a public address (ngrok or
// a domain, set on the admin page) the sign-in starts there: startBase from /auth/oauth2/providers.
const backendUrl = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8081'

// { providers: [{ id, name }], startBase }
export const socialProviders = () => http.get('/auth/oauth2/providers')

// redirect_origin tells the backend which frontend (e.g. localhost:5174 when Vite had to pick
// another port) to send the browser back to; link = attach to the signed-in account instead
export function socialLoginUrl(provider, startBase, link) {
  const q = new URLSearchParams({ redirect_origin: window.location.origin })
  if (link) q.set('link', link)
  return `${startBase || backendUrl}/oauth2/authorization/${provider}?${q}`
}
