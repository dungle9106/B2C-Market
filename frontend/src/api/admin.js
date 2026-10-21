import http from './http'

// ---- the signed-in admin: { id, fullName, superAdmin, permissions, allPermissions: [{ key, label }] } ----
export const adminMe = () => http.get('/admin/me')
export const adminStats = () => http.get('/admin/stats')

// ---- users ----
// role: BUYER | SELLER | ADMIN; status: ALL | ACTIVE | LOCKED | DELETED
export const adminUsers = ({ role, status = 'ALL', q, page = 0, size = 20 } = {}) =>
  http.get('/admin/users', { params: { role: role || undefined, status, q: q || undefined, page, size } })
export const adminUserCounts = () => http.get('/admin/users/counts')
export const adminUser = (id) => http.get(`/admin/users/${id}`)
// days: null = until unlocked
export const lockUser = (id, reason, days) => http.post(`/admin/users/${id}/lock`, { reason, days: days || undefined })
export const unlockUser = (id) => http.post(`/admin/users/${id}/unlock`)
export const deleteUser = (id, reason) => http.post(`/admin/users/${id}/delete`, { reason })

// ---- shops ----
// status: PENDING | APPROVED | REJECTED | SUSPENDED | ALL
export const adminShops = ({ status = 'ALL', q, page = 0, size = 20 } = {}) =>
  http.get('/admin/shops', { params: { status, q: q || undefined, page, size } })
export const adminShopCounts = () => http.get('/admin/shops/counts')
export const approveShop = (id) => http.post(`/admin/shops/${id}/approve`)
export const rejectShop = (id, reason) => http.post(`/admin/shops/${id}/reject`, { reason })
export const suspendShop = (id, reason) => http.post(`/admin/shops/${id}/suspend`, { reason })
export const reinstateShop = (id) => http.post(`/admin/shops/${id}/reinstate`)

// ---- platform settings: { requireShopApproval } ----
export const adminSettings = () => http.get('/admin/settings')
export const saveAdminSettings = (body) => http.put('/admin/settings', body)

// ---- staff & permissions ----
export const adminStaff = () => http.get('/admin/staff')
export const createStaff = (body) => http.post('/admin/staff', body) // { email, fullName, password, permissions }
export const updateStaff = (id, permissions) => http.put(`/admin/staff/${id}`, { permissions })
export const revokeStaff = (id) => http.post(`/admin/staff/${id}/revoke`)
export const adminAudit = ({ adminId, targetType, page = 0, size = 30 } = {}) =>
  http.get('/admin/audit', { params: { adminId: adminId || undefined, targetType: targetType || undefined, page, size } })

// ---- platform categories (2 levels) ----
export const adminCategories = () => http.get('/admin/categories')
export const createCategory = (name, parentId) => http.post('/admin/categories', { name, parentId: parentId || null })
export const updateCategory = (id, name, parentId) => http.put(`/admin/categories/${id}`, { name, parentId: parentId || null })
export const toggleCategory = (id) => http.post(`/admin/categories/${id}/toggle`)
export const reorderCategories = (parentId, ids) => http.post('/admin/categories/reorder', { parentId: parentId || null, ids })
export const deleteCategory = (id, moveTo) => http.delete(`/admin/categories/${id}`, { params: { moveTo: moveTo || undefined } })

// ---- product moderation ----
// status: PENDING | APPROVED | REJECTED | REMOVED | REPORTED | ALL
export const adminProducts = ({ status = 'PENDING', categoryId, q, page = 0, size = 20 } = {}) =>
  http.get('/admin/products', { params: { status, categoryId: categoryId || undefined, q: q || undefined, page, size } })
export const adminProductCounts = () => http.get('/admin/products/counts')
export const adminProduct = (id) => http.get(`/admin/products/${id}`)
export const approveProduct = (id) => http.post(`/admin/products/${id}/approve`)
export const bulkApproveProducts = (ids) => http.post('/admin/products/bulk-approve', { ids })
export const rejectProduct = (id, reason) => http.post(`/admin/products/${id}/reject`, { reason })
export const removeProduct = (id, violation, reason) => http.post(`/admin/products/${id}/remove`, { violation, reason })
export const restoreProduct = (id) => http.post(`/admin/products/${id}/restore`)
export const dismissReports = (id) => http.post(`/admin/products/${id}/dismiss-reports`)

// ---- all orders on the platform ----
// status: ALL | LATE | <OrderStatus>; method: COD | E_WALLET | CARD | BANK_TRANSFER; from/to: yyyy-MM-dd
export const adminOrders = ({ status = 'ALL', method, shopId, q, from, to, page = 0, size = 20 } = {}) =>
  http.get('/admin/orders', { params: { status, method: method || undefined, shopId: shopId || undefined, q: q || undefined, from: from || undefined, to: to || undefined, page, size } })
export const adminOrderSummary = (from, to) => http.get('/admin/orders/summary', { params: { from: from || undefined, to: to || undefined } })
export const adminOrder = (id) => http.get(`/admin/orders/${id}`)
export const adminCancelOrder = (id, reason) => http.post(`/admin/orders/${id}/cancel`, { reason })
export const adminManualRefund = (id, amount, note) => http.post(`/admin/orders/${id}/refund`, { amount, note })

// ---- buyer / seller disputes ----
// status: OPEN | RESOLVED | ALL
export const adminDisputes = ({ status = 'OPEN', page = 0, size = 20 } = {}) => http.get('/admin/disputes', { params: { status, page, size } })
export const adminDisputeCounts = () => http.get('/admin/disputes/counts')
export const adminDispute = (id) => http.get(`/admin/disputes/${id}`)
export const adminDisputeMessages = (id) => http.get(`/admin/disputes/${id}/messages`)
export const adminPostDisputeMessage = (id, content, attachments = []) => http.post(`/admin/disputes/${id}/messages`, { content, attachments })
// decision: REFUND_FULL | REFUND_PARTIAL | REJECT
export const resolveDispute = (id, decision, amount, note) => http.post(`/admin/disputes/${id}/resolve`, { decision, amount: amount || null, note })

// ---- refunds ----
// status: PENDING | COMPLETED | FAILED | ALL
export const adminRefunds = ({ status = 'PENDING', q, page = 0, size = 20 } = {}) => http.get('/admin/refunds', { params: { status, q: q || undefined, page, size } })
export const adminRefundCounts = () => http.get('/admin/refunds/counts')
export const completeRefund = (id, reference, note) => http.post(`/admin/refunds/${id}/complete`, { reference, note })
export const failRefund = (id, reason) => http.post(`/admin/refunds/${id}/fail`, { reason })

// ---- seller settlements ----
export const settlementPreview = (to) => http.get('/admin/settlements/preview', { params: { to: to || undefined } })
export const createSettlements = (shopIds, to) => http.post('/admin/settlements', { shopIds, to: to || null })
export const adminSettlements = ({ status = 'ALL', shopId, page = 0, size = 20 } = {}) =>
  http.get('/admin/settlements', { params: { status, shopId: shopId || undefined, page, size } })
export const adminSettlementCounts = () => http.get('/admin/settlements/counts')
export const adminSettlement = (id) => http.get(`/admin/settlements/${id}`)
export const paySettlement = (id, reference) => http.post(`/admin/settlements/${id}/pay`, { reference })

// ---- payment reconciliation ----
export const reconciliation = (from, to) => http.get('/admin/reconciliation', { params: { from: from || undefined, to: to || undefined } })

// ---- platform marketing: programmes, banners, Shoppi Flash Sale ----
export const adminCampaigns = () => http.get('/admin/campaigns')
export const createCampaign = (body) => http.post('/admin/campaigns', body)
export const updateCampaign = (id, body) => http.put(`/admin/campaigns/${id}`, body)
export const toggleCampaign = (id) => http.post(`/admin/campaigns/${id}/toggle`)
export const deleteCampaign = (id) => http.delete(`/admin/campaigns/${id}`)

export const adminBanners = () => http.get('/admin/banners')            // { HOME_CAROUSEL: [...], HOME_SIDE: [...] }
export const createBanner = (body) => http.post('/admin/banners', body)
export const updateBanner = (id, body) => http.put(`/admin/banners/${id}`, body)
export const toggleBanner = (id) => http.post(`/admin/banners/${id}/toggle`)
export const deleteBanner = (id) => http.delete(`/admin/banners/${id}`)
export const reorderBanners = (placement, ids) => http.post('/admin/banners/reorder', { placement, ids })

export const adminFlashEvents = () => http.get('/admin/flash-sales')
export const createFlashEvent = (body) => http.post('/admin/flash-sales', body)
export const updateFlashEvent = (id, body) => http.put(`/admin/flash-sales/${id}`, body)
export const adminFlashEvent = (id, status) => http.get(`/admin/flash-sales/${id}`, { params: { status: status || undefined } })
export const cancelFlashEvent = (id, reason) => http.post(`/admin/flash-sales/${id}/cancel`, { reason })
export const approveAllFlash = (id) => http.post(`/admin/flash-sales/${id}/approve-all`)
export const approveFlashRegistration = (id) => http.post(`/admin/flash-sales/registrations/${id}/approve`)
export const rejectFlashRegistration = (id, reason) => http.post(`/admin/flash-sales/registrations/${id}/reject`, { reason })

// ---- platform statistics & reports: params { from, to, granularity } ----
export const platformOverview = (params) => http.get('/admin/analytics/overview', { params })
export const platformTop = (params, limit = 10, productSort = 'units') =>
  http.get('/admin/analytics/top', { params: { from: params.from, to: params.to, limit, productSort } })
export const platformGrowth = (params) => http.get('/admin/analytics/growth', { params })
export const exportPlatformReport = (params) => http.get('/admin/analytics/export', { params, responseType: 'blob' })

// ---- system operations: AI keys & LLM cost, monitoring, policies ----
export const aiConfig = () => http.get('/admin/system/ai/config')
export const saveAiConfig = (body) => http.put('/admin/system/ai/config', body)
export const testAi = () => http.post('/admin/system/ai/test')
// semantic search index: { engine: 'pgvector' | ..., indexed, activeProducts, model, dimensions, available }
export const aiEmbeddings = () => http.get('/admin/system/ai/embeddings')
export const reindexEmbeddings = () => http.post('/admin/system/ai/embeddings/reindex')
export const aiUsage = (from, to) => http.get('/admin/system/ai/usage', { params: { from, to } })
export const aiCalls = ({ status = 'ALL', feature, page = 0, size = 20 } = {}) =>
  http.get('/admin/system/ai/calls', { params: { status, feature: feature || undefined, page, size } })
export const systemHealth = () => http.get('/admin/system/health')
export const systemMetrics = (minutes = 60) => http.get('/admin/system/metrics', { params: { minutes } })
export const systemLogs = ({ level = 'INFO', q, limit = 200 } = {}) => http.get('/admin/system/logs', { params: { level, q: q || undefined, limit } })
export const systemErrors = ({ status = 'OPEN', q, page = 0, size = 20 } = {}) =>
  http.get('/admin/system/errors', { params: { status, q: q || undefined, page, size } })
export const systemErrorCounts = () => http.get('/admin/system/errors/counts')
export const systemError = (id) => http.get(`/admin/system/errors/${id}`)
export const setErrorStatus = (id, status, note) => http.post(`/admin/system/errors/${id}/status`, { status, note })
export const adminPolicies = () => http.get('/admin/policies')
export const publishPolicy = (key, body) => http.post(`/admin/policies/${key}`, body)

// ---- e-mail: SMTP settings and outbox ----
export const emailConfig = () => http.get('/admin/system/email/config')
export const saveEmailConfig = (body) => http.put('/admin/system/email/config', body)
export const testEmail = (to) => http.post('/admin/system/email/test', { to })
export const emailMessages = ({ status = 'ALL', type, q, page = 0, size = 20 } = {}) =>
  http.get('/admin/system/email/messages', { params: { status, type: type || undefined, q: q || undefined, page, size } })
export const emailMessage = (id) => http.get(`/admin/system/email/messages/${id}`)
export const retryEmail = (id) => http.post(`/admin/system/email/messages/${id}/retry`)

// ---- sign-in security ----
export const securityOverview = (hours = 24) => http.get('/admin/system/security', { params: { hours } })
export const securityLogins = ({ failedOnly = true, page = 0, size = 30 } = {}) => http.get('/admin/system/security/logins', { params: { failedOnly, page, size } })
export const unlockLogin = (userId) => http.post(`/admin/system/security/unlock/${userId}`)

// ---- payment & shipping gateways ----
export const vnpayConfig = () => http.get('/admin/system/payment/vnpay')
export const saveVnpayConfig = (body) => http.put('/admin/system/payment/vnpay', body) // { enabled, tmnCode, hashSecret?, payUrl?, apiUrl? }
export const ghnConfig = () => http.get('/admin/system/shipping/ghn')
export const saveGhnConfig = (body) => http.put('/admin/system/shipping/ghn', body) // { enabled, shopId, token?, apiUrl? }
export const testGhn = () => http.post('/admin/system/shipping/ghn/test')
