import http from './http'

// "Bao khi giam gia" (backend PriceWatchController, buyers only)
export const myPriceWatches = () => http.get('/buyer/price-watches')
export const priceWatchStatus = (productId) => http.get(`/buyer/price-watches/${productId}`)   // { watching, targetPrice, watchedPrice, watchers }
export const watchPrice = (productId, targetPrice = null) => http.put(`/buyer/price-watches/${productId}`, { targetPrice })
export const unwatchPrice = (productId) => http.delete(`/buyer/price-watches/${productId}`)
