import http from './http'

// filter: 'all' | 'media' | '1'..'5'
export const productReviews = (productId, { filter = 'all', page = 0, size = 6 } = {}) =>
  http.get(`/products/${productId}/reviews`, { params: { filter, page, size } })
export const reviewSummary = (productId) => http.get(`/products/${productId}/reviews/summary`)
export const submitReview = (data) => http.post('/buyer/reviews', data)
