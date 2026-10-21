import http from './http'

export const availableVouchers = (shopIds) =>
  http.get('/vouchers/available', { params: { shopIds: shopIds.join(',') } })

export const myShopVouchers = () => http.get('/seller/vouchers')
export const createShopVoucher = (data) => http.post('/seller/vouchers', data)
export const toggleShopVoucher = (id) => http.patch(`/seller/vouchers/${id}/toggle`)

export const platformVouchers = () => http.get('/admin/vouchers')
export const createPlatformVoucher = (data) => http.post('/admin/vouchers', data)
export const togglePlatformVoucher = (id) => http.patch(`/admin/vouchers/${id}/toggle`)

// edit: before it starts everything but the code; once running only end time, quota, visibility
export const updateShopVoucher = (id, data) => http.put(`/seller/vouchers/${id}`, data)
export const endShopVoucher = (id) => http.post(`/seller/vouchers/${id}/end`)
export const updatePlatformVoucher = (id, data) => http.put(`/admin/vouchers/${id}`, data)
export const endPlatformVoucher = (id) => http.post(`/admin/vouchers/${id}/end`)

// public: running vouchers of a shop shown on the shop / product page
export const shopVouchers = (shopId, productId) => http.get(`/shops/${shopId}/vouchers`, { params: { productId } })
