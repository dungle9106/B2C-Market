import http from './http'

export const getMyShop = () => http.get('/seller/shop')
export const saveMyShop = (data) => http.post('/seller/shop', data)
export const becomeSeller = (data) => http.post('/seller/apply', data)
// after a rejection, once the profile is fixed: REJECTED -> PENDING
export const resubmitShop = () => http.post('/seller/shop/resubmit')
export const sellerDashboardStats = () => http.get('/seller/dashboard/stats')

// shop policies: { defaultReturnDays, returnConditions, applyReturnDaysToAllProducts, carriers, handlingDays, freeShipMinOrder }
export const saveShopPolicies = (data) => http.put('/seller/shop/policies', data)
export const carrierOptions = () => http.get('/shops/carriers')   // [{ code, name, methods }]

// verification ("Xac minh thong tin"); 204 / empty body = never applied
export const myVerification = () => http.get('/seller/verification')
export const submitVerification = (data) => http.post('/seller/verification', data)

// public shop page
export const publicShop = (id) => http.get(`/shops/${id}`)

// ---- admin ----
export const adminVerifications = (pending = true) => http.get('/admin/verifications', { params: { pending } })
export const approveVerification = (id) => http.post(`/admin/verifications/${id}/approve`)
export const rejectVerification = (id, reason) => http.post(`/admin/verifications/${id}/reject`, { reason })
