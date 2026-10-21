import http from './http'

// buyer-facing RAG chatbot: { reply, products, orders, suggestSupport, createdAt }
export const chatWithAI = (message) => http.post('/ai/chat', { message })
export const chatbotHistory = () => http.get('/ai/history')   // [{ message, reply, createdAt }]
export const clearChatbotHistory = () => http.delete('/ai/history')

// ---- AI for sellers (work without an LLM key: rules + statistics; source tells which) ----
// { productName, material, usage, targetUser, highlights, categoryId, tone: friendly|professional|short, variants }
//   -> { reply, titleSuggestion, source: 'ai' | 'rule' }
export const generateDescription = (data) => http.post('/seller/ai/generate-description', data)
// { name, categoryId, cost } -> { count, p25, median, p75, suggested, notes, histogram, comparables } or { count, message }
export const suggestPrice = (data) => http.post('/seller/ai/price-suggestion', data)
// praise / complaint themes of the shop's reviews
export const reviewInsights = ({ productId, days } = {}) =>
  http.get('/seller/ai/review-insights', { params: { productId: productId || undefined, days } })
// natural-language questions about the shop's sales -> { answer, period, table?, chart?, suggestions }
export const askSalesAssistant = (question) => http.post('/seller/ai/ask', { question })
export const assistantExamples = () => http.get('/seller/ai/ask/examples')
// when to run promotions
export const promoTiming = () => http.get('/seller/ai/promo-timing')

// rule-based "co the ban thich" recommendation feed
export const forYouRecommendations = () => http.get('/buyer/recommendations/for-you')
