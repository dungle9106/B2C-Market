export const ORDER_STATUS_LABELS = {
  AWAITING_PAYMENT: 'Chờ thanh toán',
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Chờ lấy hàng',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  RETURN_REQUESTED: 'Yêu cầu trả hàng',
  REFUNDED: 'Đã hoàn tiền'
}

export const statusLabel = (s) => ORDER_STATUS_LABELS[s] || s

// statuses grouped under the seller's "Don huy / Tra hang / Hoan tien" tab
export const RETURN_STATUSES = ['CANCELLED', 'RETURN_REQUESTED', 'REFUNDED']

const PAYMENT_METHODS = {
  COD: 'Thanh toán khi nhận hàng',
  E_WALLET: 'Ví điện tử',
  CARD: 'Thẻ tín dụng/ghi nợ',
  BANK_TRANSFER: 'Chuyển khoản ngân hàng',
  VNPAY: 'VNPay',
  MOCK_ONLINE: 'Thanh toán online'
}
const PROVIDERS = { MOMO: 'MoMo', ZALOPAY: 'ZaloPay', VNPAY: 'VNPay', VISA: 'Visa', MASTERCARD: 'Mastercard', JCB: 'JCB' }
const PAYMENT_STATUS = { PENDING: 'Chưa thanh toán', PAID: 'Đã thanh toán', FAILED: 'Thanh toán lỗi', CANCELLED: 'Đã hủy', REFUNDED: 'Đã hoàn tiền' }

// "Ví điện tử (MoMo) · Đã thanh toán"
export function paymentLabel(order) {
  if (!order.paymentMethod) return ''
  const method = PAYMENT_METHODS[order.paymentMethod] || order.paymentMethod
  const provider = order.paymentProvider ? ` (${PROVIDERS[order.paymentProvider] || order.paymentProvider})` : ''
  return `${method}${provider} · ${PAYMENT_STATUS[order.paymentStatus] || order.paymentStatus}`
}

// "Giao Hàng Nhanh - Nhanh"
export function shippingLabel(order) {
  return order.carrierName ? `${order.carrierName}${order.shippingMethodName ? ' - ' + order.shippingMethodName : ''}` : ''
}
