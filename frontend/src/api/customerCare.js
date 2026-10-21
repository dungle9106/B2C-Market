import http from './http'

// "Chăm sóc khách hàng" tools of the seller centre

// sidebar badges: { unreadChats, awaitingChats, unansweredQuestions, unrepliedReviews, unrepliedNegativeReviews }
export const engagementSummary = () => http.get('/seller/engagement')

// reply templates: { CHAT: [{ id, kind, content }], QUESTION: [...], REVIEW: [...] }
export const replyTemplates = () => http.get('/seller/reply-templates')
export const createTemplate = (kind, content) => http.post('/seller/reply-templates', { kind, content })
export const updateTemplate = (id, content) => http.put(`/seller/reply-templates/${id}`, { content })
export const deleteTemplate = (id) => http.delete(`/seller/reply-templates/${id}`)

// chat auto-replies: { autoReplyEnabled, autoReplyMessage, awayEnabled, awayMessage, hoursStart, hoursEnd, openNow }
export const chatSettings = () => http.get('/seller/chat-settings')
export const saveChatSettings = (body) => http.put('/seller/chat-settings', body)

// one template to many reviews -> { replied, skipped }
export const bulkReplyReviews = (ids, reply) => http.post('/seller/reviews/bulk-reply', { ids, reply })
