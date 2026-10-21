import http from './http'

// "Thống kê & Báo cáo" of the seller centre.
// params: { from: 'yyyy-MM-dd', to: 'yyyy-MM-dd' (inclusive, max 366 days), granularity: 'day' | 'week' | 'month' }
export const revenueReport = (params) => http.get('/seller/analytics/revenue', { params })
export const productReport = (params) => http.get('/seller/analytics/products', { params: { from: params.from, to: params.to } })
export const trafficReport = (params) => http.get('/seller/analytics/traffic', { params })
export const reviewReport = (params) => http.get('/seller/analytics/reviews', { params })
export const exportReport = (params) => http.get('/seller/analytics/export', { params, responseType: 'blob' })

// the shop's reviews; rating 1-5, maxRating (2 = negative), replied true/false (omit for all)
export const shopReviews = ({ rating, maxRating, productId, replied, page = 0, size = 10 } = {}) =>
  http.get('/seller/reviews', { params: { rating: rating || undefined, maxRating: maxRating || undefined, productId: productId || undefined, replied: replied ?? undefined, page, size } })
export const replyReview = (id, reply) => http.put(`/seller/reviews/${id}/reply`, { reply })
