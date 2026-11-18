import http from './http'

// { publicKey } - VAPID key for PushManager.subscribe
export const pushWebConfig = () => http.get('/push/web-config')
// body: { kind: 'WEB', endpoint, p256dh, auth, device }
export const pushSubscribe = (body) => http.post('/push/subscribe', body)
export const pushUnsubscribe = (endpoint, token) =>
  http.post('/push/unsubscribe', { endpoint }, token ? { headers: { Authorization: `Bearer ${token}` } } : undefined)
export const pushTest = () => http.post('/push/test')
