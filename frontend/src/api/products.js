import http from './http'

// ---- buyer-facing catalogue ----
// params: { q, categoryId, minPrice, maxPrice, minRating, provinces: [], freeShipping, onSale, sort, page, size }
export const searchProducts = (params) => http.get('/products/search', {
  params,
  // provinces=A&provinces=B (Spring binds repeated params to a List)
  paramsSerializer: { indexes: null }
})
export const suggestProducts = (q) => http.get('/products/suggest', { params: { q } })
export const flashSaleProducts = (limit = 12) => http.get('/products/flash-sale', { params: { limit } })
export const topSelling = () => http.get('/products/top-selling')
// "tìm theo ý nghĩa" (embeddings + pgvector); [] when AI is not configured
export const semanticProducts = (q, limit = 12) => http.get('/products/semantic', { params: { q, limit } })
export const getProduct = (id) => http.get(`/products/${id}`)
export const similarProducts = (id) => http.get(`/products/${id}/similar`)

export const listCategories = () => http.get('/categories')
export const categoryTree = () => http.get('/categories/tree')
export const getCategory = (id) => http.get(`/categories/${id}`)

// ---- seller ----
export const myProducts = () => http.get('/seller/products')
export const myProduct = (id) => http.get(`/seller/products/${id}`)
export const createProduct = (data) => http.post('/seller/products', data)
export const updateProduct = (id, data) => http.put(`/seller/products/${id}`, data)
export const toggleProductActive = (id) => http.patch(`/seller/products/${id}/toggle-active`)
export const deleteProduct = (id) => http.delete(`/seller/products/${id}`)

// quick edits: { stock } or { variants: { [variantId]: stock } }
export const updateProductStock = (id, body) => http.patch(`/seller/products/${id}/stock`, body)
export const duplicateProduct = (id) => http.post(`/seller/products/${id}/duplicate`)
// action: HIDE | SHOW | SOLD_OUT | DELETE | ADD_CATEGORY | REMOVE_CATEGORY -> { updated, skipped }
export const bulkProducts = (ids, action, shopCategoryId) => http.post('/seller/products/bulk', { ids, action, shopCategoryId })

// Excel: template / export download as blobs; import returns { dryRun, rows, products, errors, created }
export const productTemplate = () => http.get('/seller/products/import/template', { responseType: 'blob' })
export const exportProducts = () => http.get('/seller/products/export', { responseType: 'blob' })
export const importProducts = (file, dryRun) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/seller/products/import', form, { params: { dryRun } })
}

// "Danh muc cua Shop"
export const myShopCategories = () => http.get('/seller/shop-categories')   // [{ id, name, position, productCount }]
export const createShopCategory = (name) => http.post('/seller/shop-categories', { name })
export const renameShopCategory = (id, name) => http.put(`/seller/shop-categories/${id}`, { name })
export const deleteShopCategory = (id) => http.delete(`/seller/shop-categories/${id}`)
export const reorderShopCategories = (ids) => http.put('/seller/shop-categories/order', { ids })
export const assignShopCategory = (id, productIds, add = true) => http.post(`/seller/shop-categories/${id}/products`, { productIds, add })
export const publicShopCategories = (shopId) => http.get(`/shops/${shopId}/categories`)

// "Báo cáo sản phẩm vi phạm"
export const reportReasons = () => http.get('/products/report-reasons')
export const reportProduct = (id, reason, detail) => http.post(`/products/${id}/report`, { reason, detail })
// seller: REJECTED / REMOVED -> PENDING after fixing the product
export const resubmitProduct = (id) => http.post(`/seller/products/${id}/resubmit`)
