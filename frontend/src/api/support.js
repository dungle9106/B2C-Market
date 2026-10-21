import http from './http'

// body: { category, subject, content, orderId?, shopId?, attachmentUrls? }
export const createTicket = (body) => http.post('/support/tickets', body)
export const myTickets = ({ active = true, page = 0, size = 10 } = {}) =>
  http.get('/support/tickets', { params: { active, page, size } })
export const getTicket = (id) => http.get(`/support/tickets/${id}`)
export const replyTicket = (id, content, attachmentUrls = []) =>
  http.post(`/support/tickets/${id}/replies`, { content, attachmentUrls })
export const closeTicket = (id) => http.post(`/support/tickets/${id}/close`)
export const rateTicket = (id, rating, comment) => http.post(`/support/tickets/${id}/rating`, { rating, comment })

// ---- admin (support staff) ----
export const adminTickets = ({ status, category, q, page = 0, size = 20 } = {}) =>
  http.get('/admin/support/tickets', { params: { status: status || undefined, category: category || undefined, q: q || undefined, page, size } })
export const adminTicketStats = () => http.get('/admin/support/stats')
export const adminGetTicket = (id) => http.get(`/admin/support/tickets/${id}`)
// status (optional) moves the ticket together with the reply
export const adminReplyTicket = (id, content, status = null, attachmentUrls = []) =>
  http.post(`/admin/support/tickets/${id}/replies`, { content, status: status || undefined, attachmentUrls })
export const adminUpdateTicket = (id, body) => http.patch(`/admin/support/tickets/${id}`, body)
