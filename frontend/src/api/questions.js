import http from './http'

export const productQuestions = (productId, { page = 0, size = 5 } = {}) =>
  http.get(`/products/${productId}/questions`, { params: { page, size } })
export const askQuestion = (productId, question) => http.post('/questions', { productId, question })

// seller inbox; status: UNANSWERED | ANSWERED | HIDDEN
export const myShopQuestions = ({ status = 'UNANSWERED', q, productId, page = 0, size = 20 } = {}) =>
  http.get('/seller/questions', { params: { status, q: q || undefined, productId: productId || undefined, page, size } })
export const questionCounts = () => http.get('/seller/questions/counts') // { unanswered, answered, hidden }
// answers, or edits the answer
export const answerQuestion = (id, answer) => http.post(`/seller/questions/${id}/answer`, { answer })
export const hideQuestion = (id) => http.post(`/seller/questions/${id}/hide`)
export const unhideQuestion = (id) => http.post(`/seller/questions/${id}/unhide`)
export const suggestAnswer = (id) => http.post(`/seller/questions/${id}/suggest`) // { answer, source: 'ai' | 'rule' }
