import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api'
})

// attach this tab's JWT to every request once the user is logged in (each tab has its own session: stores/auth.js)
http.interceptors.request.use((config) => {
  const token = useAuthStore().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// The access token expires after 30 minutes: on a 401, renew it once with the refresh token and
// replay the request. Several requests failing together share a single refresh call.
let refreshing = null
const NO_RETRY = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout', '/auth/login/2fa']

http.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config || {}
    const auth = useAuthStore()
    if (error.response?.status !== 401 || original._retried || NO_RETRY.some(p => (original.url || '').endsWith(p))) {
      return Promise.reject(error)
    }
    if (!auth.refreshToken) {
      if (auth.token) auth.logout(false)
      return Promise.reject(error)
    }
    try {
      refreshing = refreshing || axios.post(`${http.defaults.baseURL}/auth/refresh`, { refreshToken: auth.refreshToken })
        .then(({ data }) => auth.setTokens(data.token, data.refreshToken))
        .finally(() => { refreshing = null })
      await refreshing
      original._retried = true
      original.headers = { ...original.headers, Authorization: `Bearer ${auth.token}` }
      return http(original)
    } catch (e) {
      // this tab's session was ended (signed out from the security page, password changed, expired): sign this tab out
      auth.logout(false)
      return Promise.reject(error)
    }
  }
)

export default http
