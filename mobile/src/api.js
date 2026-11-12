// Calls the Shoppi Spring Boot backend (/api). Same endpoints as the web frontend (frontend/src/api).
import * as SecureStore from 'expo-secure-store'
import Constants from 'expo-constants'

// In Expo Go (dev) the phone already reaches the PC at hostUri ("192.168.1.5:8081"), so the backend is on that IP;
// an installed APK falls back to the configured URL (10.0.2.2 = the PC, seen from the Android emulator).
const devHost = Constants.expoConfig?.hostUri?.split(':')[0]
const DEFAULT_URL = devHost && !/^(localhost|127\.)/.test(devHost)
  ? `http://${devHost}:8081`
  : Constants.expoConfig?.extra?.apiUrl || 'http://10.0.2.2:8081'

const session = { server: DEFAULT_URL, token: null, refreshToken: null, onLogout: null }

export async function loadSession() {
  const [server, token, refreshToken] = await Promise.all(['server', 'token', 'refreshToken'].map(k => SecureStore.getItemAsync(k)))
  session.server = server || DEFAULT_URL
  session.token = token
  session.refreshToken = refreshToken
  return { server: session.server, loggedIn: !!token }
}

export const serverUrl = () => session.server

export async function setServer(url) {
  session.server = url.trim().replace(/\/+$/, '')
  await SecureStore.setItemAsync('server', session.server)
}

export async function setTokens(token, refreshToken) {
  session.token = token
  if (refreshToken !== undefined) session.refreshToken = refreshToken
  await SecureStore.setItemAsync('token', token || '')
  await SecureStore.setItemAsync('refreshToken', session.refreshToken || '')
}

export async function clearTokens() {
  session.token = null
  session.refreshToken = null
  await Promise.all(['token', 'refreshToken'].map(k => SecureStore.deleteItemAsync(k)))
}

// the auth context signs the user out when the refresh token is rejected
export const onSessionExpired = (fn) => { session.onLogout = fn }

// '/uploads/x.jpg' -> 'http://server/uploads/x.jpg'
export const fileUrl = (path) => (!path ? null : /^https?:/.test(path) ? path : session.server + path)

export class ApiError extends Error {
  constructor(status, message, data) {
    super(message)
    this.status = status
    this.data = data
  }
}

function query(params) {
  if (!params) return ''
  const parts = []
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    for (const item of Array.isArray(v) ? v : [v]) parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(item)}`)
  }
  return parts.length ? '?' + parts.join('&') : ''
}

const NO_RETRY = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/login/2fa']
let refreshing = null

async function refresh() {
  const res = await fetch(`${session.server}/api/auth/refresh`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: session.refreshToken })
  })
  if (!res.ok) throw new Error('refresh failed')
  const data = await res.json()
  await setTokens(data.token, data.refreshToken)
}

// The access token lives 30 minutes: on a 401 renew it once with the refresh token and replay the request.
export async function request(method, path, { body, params, form } = {}, retried = false) {
  const headers = { Accept: 'application/json' }
  if (session.token) headers.Authorization = `Bearer ${session.token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  let res
  try {
    res = await fetch(`${session.server}/api${path}${query(params)}`, {
      method, headers, body: form || (body !== undefined ? JSON.stringify(body) : undefined)
    })
  } catch (e) {
    throw new ApiError(0, `Không kết nối được máy chủ ${session.server}`)
  }
  if (res.status === 401 && !retried && session.refreshToken && !NO_RETRY.some(p => path.endsWith(p))) {
    try {
      refreshing = refreshing || refresh().finally(() => { refreshing = null })
      await refreshing
    } catch (e) {
      await clearTokens()
      session.onLogout?.()
      throw new ApiError(401, 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại')
    }
    return request(method, path, { body, params, form }, true)
  }
  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch (e) { data = text }
  if (!res.ok) {
    const fieldErrors = data?.errors && typeof data.errors === 'object' ? Object.values(data.errors).join(', ') : null
    throw new ApiError(res.status, data?.message || fieldErrors || `Lỗi ${res.status}`, data)
  }
  return data
}

export const get = (path, params) => request('GET', path, { params })
export const post = (path, body, params) => request('POST', path, { body, params })
export const put = (path, body) => request('PUT', path, { body })
export const patch = (path, body) => request('PATCH', path, { body })
export const del = (path, params) => request('DELETE', path, { params })

// a picked image ({ uri, mimeType, fileName }) as a multipart "file" field
export function upload(path, image, params) {
  const form = new FormData()
  form.append('file', { uri: image.uri, type: image.mimeType || 'image/jpeg', name: image.fileName || 'photo.jpg' })
  return request('POST', path, { form, params })
}

export const errorText = (e) => e?.message || 'Đã có lỗi xảy ra'
