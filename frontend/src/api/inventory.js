import http from './http'

// { updatedAt, lowStockThreshold, summary: { skus, outOfStock, lowStock, units, value }, rows }
export const inventory = () => http.get('/seller/inventory')
export const inventoryLogs = ({ productId, page = 0, size = 20 } = {}) =>
  http.get('/seller/inventory/logs', { params: { productId: productId || undefined, page, size } })
// mode: ADD | SUBTRACT | SET, reason: RESTOCK | STOCKTAKE | DAMAGED | OTHER
export const adjustStock = (body) => http.post('/seller/inventory/adjust', body)

// "Du bao nhap hang (AI)": params { leadTime, cover, serviceLevel }
export const stockForecast = (params) => http.get('/seller/inventory/forecast', { params })
export const stockForecastDetail = (productId, variantId, params) =>
  http.get(`/seller/inventory/forecast/${productId}`, { params: { ...params, variantId: variantId || undefined } })
export const stockForecastAdvice = (params) => http.post('/seller/inventory/forecast/advice', null, { params })
