import http from './http'

// ---- flash sale ----
export const myFlashSales = () => http.get('/seller/flash-sales')
// body: { startAt, endAt, items: [{ productId, flashPrice, flashStock?, maxPerBuyer? }] }
export const scheduleFlashSale = (body) => http.post('/seller/flash-sales', body)
export const endFlashSale = (productId) => http.delete(`/seller/flash-sales/${productId}`)

// ---- combos ("Mua 2 giam 5%") ----
export const myCombos = () => http.get('/seller/combos')
export const createCombo = (body) => http.post('/seller/combos', body)
export const updateCombo = (id, body) => http.put(`/seller/combos/${id}`, body)
export const toggleCombo = (id) => http.patch(`/seller/combos/${id}/toggle`)
export const endCombo = (id) => http.post(`/seller/combos/${id}/end`)
export const productCombos = (productId) => http.get(`/products/${productId}/combos`)

// ---- ads ----
export const adWallet = (page = 0) => http.get('/seller/ads/wallet', { params: { page } })
export const topUpAds = (amount) => http.post('/seller/ads/wallet/topup', { amount })
export const myAdCampaigns = () => http.get('/seller/ads')
export const createAdCampaign = (body) => http.post('/seller/ads', body)
export const updateAdCampaign = (id, body) => http.put(`/seller/ads/${id}`, body)
export const setAdStatus = (id, status) => http.patch(`/seller/ads/${id}/status`, { status })
// fire-and-forget when a buyer opens a sponsored result
export const trackAdClick = (adId) => http.post(`/ads/${adId}/click`).catch(() => {})

// ---- Shoppi Flash Sale slots (seller registers products) ----
export const sellerFlashEvents = () => http.get('/seller/flash-events')
// items: [{ productId, flashPrice, flashStock?, maxPerBuyer? }]
export const registerFlashEvent = (eventId, items) => http.post(`/seller/flash-events/${eventId}/registrations`, { items })
export const withdrawFlashRegistration = (id) => http.post(`/seller/flash-events/registrations/${id}/withdraw`)

// ---- public: home banners, programmes, Shoppi Flash Sale ----
export const homeBanners = () => http.get('/banners')
export const clickBanner = (id) => http.post(`/banners/${id}/click`)
export const visibleCampaigns = () => http.get('/campaigns')
export const campaignPage = (slug) => http.get(`/campaigns/${slug}`)
export const flashSlots = (limit = 12) => http.get('/flash-sale/slots', { params: { limit } })
export const flashSlot = (id, limit = 60) => http.get(`/flash-sale/slots/${id}`, { params: { limit } })
