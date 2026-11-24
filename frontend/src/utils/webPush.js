// Web Push in the browser: registers /sw.js, subscribes with the backend's VAPID key and sends the subscription to
// the backend. One subscription per browser; it belongs to the account that turned it on most recently.
import { pushWebConfig, pushSubscribe, pushUnsubscribe } from '../api/push'

export const pushSupported = () =>
  typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window && window.isSecureContext

/** 'unsupported' | 'denied' | 'default' (never asked) | 'granted' */
export const pushPermission = () => (pushSupported() ? Notification.permission : 'unsupported')

let registration = null
async function getRegistration() {
  if (!registration) registration = await navigator.serviceWorker.register('/sw.js')
  await navigator.serviceWorker.ready
  return registration
}

function keyBytes(base64url) {
  const pad = '='.repeat((4 - (base64url.length % 4)) % 4)
  const raw = atob((base64url + pad).replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(raw, c => c.charCodeAt(0))
}

function deviceName() {
  const ua = navigator.userAgent
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Trình duyệt'
  const os = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : ''
  return `${browser}${os ? ' trên ' + os : ''}`
}

/** The current browser subscription, or null. */
export async function currentSubscription() {
  if (!pushSupported()) return null
  const reg = await navigator.serviceWorker.getRegistration('/sw.js')
  return reg ? reg.pushManager.getSubscription() : null
}

/**
 * Turns push on for the signed-in account: asks permission if needed (must be called from a click the first time),
 * subscribes and registers the subscription with the backend. Returns the permission state.
 */
export async function enablePush({ ask = true } = {}) {
  if (!pushSupported()) return 'unsupported'
  let permission = Notification.permission
  if (permission === 'default' && ask) permission = await Notification.requestPermission()
  if (permission !== 'granted') return permission
  const reg = await getRegistration()
  let sub = await reg.pushManager.getSubscription()
  const { data } = await pushWebConfig()
  const key = keyBytes(data.publicKey)
  // a subscription made with another server key (backend reinstalled) cannot be reused
  if (sub) {
    const old = sub.options?.applicationServerKey ? new Uint8Array(sub.options.applicationServerKey) : null
    if (old && (old.length !== key.length || old.some((b, i) => b !== key[i]))) {
      await sub.unsubscribe()
      sub = null
    }
  }
  if (!sub) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key })
  const json = sub.toJSON()
  await pushSubscribe({ kind: 'WEB', endpoint: json.endpoint, p256dh: json.keys.p256dh, auth: json.keys.auth, device: deviceName() })
  return 'granted'
}

/** Stops push for this account on this browser (the backend forgets the subscription). */
export async function disablePush(token) {
  const sub = await currentSubscription()
  if (sub) await pushUnsubscribe(sub.endpoint, token).catch(() => {})
}

// "Tắt" keeps the browser permission; this flag stops the quiet re-subscribe after the next sign-in
const OFF_KEY = 'shoppi.push.off'
export function isPushOff() {
  try { return !!localStorage.getItem(OFF_KEY) } catch { return false }
}
export function setPushOff(off) {
  try { off ? localStorage.setItem(OFF_KEY, '1') : localStorage.removeItem(OFF_KEY) } catch { /* private mode */ }
}

/** After sign-in: if this browser already allowed notifications, attach the subscription to this account quietly. */
export function resumePush() {
  if (pushPermission() === 'granted' && !isPushOff()) enablePush({ ask: false }).catch(() => {})
}

/** Clicking a notification while Shoppi is open: the service worker asks this tab to open the page. */
export function listenForPushClicks(router) {
  if (!pushSupported()) return
  navigator.serviceWorker.addEventListener('message', (e) => {
    if (e.data?.type === 'open-link' && e.data.link) router.push(e.data.link).catch(() => {})
  })
}
