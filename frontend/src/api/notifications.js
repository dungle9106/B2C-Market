import http from './http'

// type: ORDER | PROMOTION | MESSAGE | SUPPORT | SYSTEM, or null for all
export const myNotifications = ({ type = null, page = 0, size = 20 } = {}) =>
  http.get('/notifications', { params: { type: type || undefined, page, size } })
// { total, byType: { ORDER: 2, ... }, chat }
export const unreadSummary = () => http.get('/notifications/unread')
export const markNotificationRead = (id) => http.post(`/notifications/${id}/read`)
export const markAllNotificationsRead = (type = null) => http.post('/notifications/read-all', null, { params: { type: type || undefined } })
export const deleteNotification = (id) => http.delete(`/notifications/${id}`)

// ---- admin ----
export const listBroadcasts = () => http.get('/admin/broadcasts')
// body: { type, audience: 'BUYER' | 'SELLER' | null, title, content, link }
export const sendBroadcast = (body) => http.post('/admin/broadcasts', body)
