import http from './http'

// body: { shops: [{ cartItemIds, voucherCode, shippingOption, note }], addressId,
//         paymentMethod, paymentProvider, platformVoucherCode }
export const previewCheckout = (body) => http.post('/orders/preview', body)
export const checkout = (body) => http.post('/orders/checkout', body)
export const myOrders = () => http.get('/orders')
export const getOrder = (id) => http.get(`/orders/${id}`)   // includes the tracking timeline
export const cancelOrder = (id, reason) => http.patch(`/orders/${id}/cancel`, { reason })
export const confirmReceived = (id) => http.post(`/orders/${id}/received`)
export const rebuyOrder = (id) => http.post(`/orders/${id}/rebuy`)  // { cartItemIds, skipped }

// return / refund requests ("Tra hang / Hoan tien") and complaints
export const createReturn = (orderId, body) => http.post(`/orders/${orderId}/returns`, body)
export const withdrawReturn = (id) => http.post(`/buyer/returns/${id}/withdraw`)
export const escalateReturn = (id, complaint) => http.post(`/buyer/returns/${id}/escalate`, { complaint })

export const myAddresses = () => http.get('/buyer/addresses')
export const createAddress = (data) => http.post('/buyer/addresses', data)
export const setDefaultAddress = (id) => http.patch(`/buyer/addresses/${id}/default`)
export const deleteAddress = (id) => http.delete(`/buyer/addresses/${id}`)

// ---- seller ----
export const shopOrders = () => http.get('/seller/orders')
export const updateOrderStatus = (id, status, reason) => http.patch(`/seller/orders/${id}/status`, { status, reason })
export const shopReturns = () => http.get('/seller/returns')
export const approveReturn = (id) => http.post(`/seller/returns/${id}/approve`)
export const rejectReturn = (id, note) => http.post(`/seller/returns/${id}/reject`, { note })

// ---- seller: order operations ----
export const shopOrder = (id) => http.get(`/seller/orders/${id}`)   // includes the tracking timeline
// "Chuan bi hang": pickupMethod PICKUP | DROPOFF, pickupDate 'YYYY-MM-DD'
export const prepareOrder = (id, pickupMethod, pickupDate) => http.post(`/seller/orders/${id}/prepare`, { pickupMethod, pickupDate })
// stage: IN_TRANSIT | OUT_FOR_DELIVERY | DELIVERY_FAILED
export const addShippingEvent = (id, stage, note, location) => http.post(`/seller/orders/${id}/tracking`, { stage, note, location })
export const acceptCancelRequest = (id) => http.post(`/seller/orders/${id}/cancel-request/accept`)
export const rejectCancelRequest = (id, reason) => http.post(`/seller/orders/${id}/cancel-request/reject`, { reason })
// action: CONFIRM | PREPARE | SHIP -> { updated, skipped: [{ id, code, reason }] }
export const bulkOrders = (ids, action, pickupMethod, pickupDate) => http.post('/seller/orders/bulk', { ids, action, pickupMethod, pickupDate })
export const orderLabels = (ids) => http.post('/seller/orders/labels', null, { params: { ids: ids.join(',') } })

// ---- dispute thread (buyer / seller of the order) ----
export const buyerDisputeMessages = (returnId) => http.get(`/buyer/returns/${returnId}/messages`)
export const buyerPostDisputeMessage = (returnId, content, attachments = []) => http.post(`/buyer/returns/${returnId}/messages`, { content, attachments })
export const sellerDisputeMessages = (returnId) => http.get(`/seller/returns/${returnId}/messages`)
export const sellerPostDisputeMessage = (returnId, content, attachments = []) => http.post(`/seller/returns/${returnId}/messages`, { content, attachments })

// ---- seller payouts ----
export const sellerFinance = () => http.get('/seller/finance')
export const sellerSettlement = (id) => http.get(`/seller/finance/settlements/${id}`)
