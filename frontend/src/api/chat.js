import http from './http'

// side: 'buyer' (/api/buyer/chats) or 'seller' (/api/seller/chats, the shop's inbox)
const base = (side) => (side === 'seller' ? '/seller/chats' : '/buyer/chats')

export const startChat = (shopId) => http.post('/buyer/chats', { shopId })
export const listChats = (side) => http.get(base(side))
export const getChat = (side, id) => http.get(`${base(side)}/${id}`)
// { after } polls for new messages, { before } loads older ones; result is oldest-first
export const chatMessages = (side, id, { after, before } = {}) =>
  http.get(`${base(side)}/${id}/messages`, { params: { after, before } })
// body: { content?, imageUrl?, productId?, orderId? }
export const sendChatMessage = (side, id, body) => http.post(`${base(side)}/${id}/messages`, body)
export const markChatRead = (side, id) => http.post(`${base(side)}/${id}/read`)

// seller tools
export const chatResponseStats = () => http.get('/seller/chats/stats') // { responseRate, avgResponseMinutes, responseTime, turns, answered }
export const chatCustomer = (id) => http.get(`/seller/chats/${id}/customer`)
export const suggestChatReply = (id) => http.post(`/seller/chats/${id}/suggest`) // { answer, source: 'ai' | 'rule' }
