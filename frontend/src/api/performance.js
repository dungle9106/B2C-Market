import http from './http'

// "Hieu qua hoat dong" / penalty points ("Sao Qua Ta") / "Shop Yeu Thich"
export const myPerformance = () => http.get('/seller/performance')
export const shopPerformanceList = (params) => http.get('/admin/shops/performance', { params })   // { q, sort, page }
export const shopPerformance = (shopId) => http.get(`/admin/shops/performance/${shopId}`)
export const addPenalty = (shopId, body) => http.post(`/admin/shops/performance/${shopId}/penalties`, body)   // { points, reason }
export const revokePenalty = (id, reason) => http.post(`/admin/shops/performance/penalties/${id}/revoke`, { reason })
export const evaluatePerformance = () => http.post('/admin/shops/performance/evaluate')
export const performanceSettings = () => http.get('/admin/shops/performance/settings')
export const savePerformanceSettings = (body) => http.put('/admin/shops/performance/settings', body)

export const LEVEL_CLASS = { GOOD: 'tw-text-green-700', WARNING: 'tw-text-amber-700', RESTRICTED: 'tw-text-orange-700', REVIEW: 'tw-text-red-700' }
export const PENALTY_CODES = { NFR: 'Đơn không thành công', LATE_SHIPMENT: 'Giao hàng trễ', LISTING: 'Vi phạm đăng bán', MANUAL: 'Shoppi xử phạt' }
