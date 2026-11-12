export const formatVnd = (n) => (n == null ? '' : '₫' + Math.round(Number(n)).toLocaleString('vi-VN').replace(/,/g, '.'))

const pad = (n) => String(n).padStart(2, '0')

export function formatDateTime(s) {
  if (!s) return ''
  const d = new Date(s)
  return `${pad(d.getHours())}:${pad(d.getMinutes())} ${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`
}

export function formatDay(s) {
  if (!s) return ''
  const d = new Date(s)
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`
}

export const compact = (n) => (n >= 1000 ? (n / 1000).toFixed(n >= 10000 ? 0 : 1).replace('.', ',') + 'k' : String(n || 0))

export const ORDER_STATUS = {
  AWAITING_PAYMENT: 'Chờ thanh toán',
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Chờ lấy hàng',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  RETURN_REQUESTED: 'Trả hàng/Hoàn tiền',
  REFUNDED: 'Đã hoàn tiền'
}

export const BUYER_CANCEL_REASONS = [
  'Muốn thay đổi địa chỉ giao hàng',
  'Muốn nhập/thay đổi mã voucher',
  'Muốn thay đổi sản phẩm trong đơn hàng (size, màu sắc, số lượng...)',
  'Tìm thấy giá rẻ hơn ở chỗ khác',
  'Đổi ý, không muốn mua nữa'
]

export const SELLER_CANCEL_REASONS = ['Hết hàng', 'Không liên hệ được với người mua', 'Người mua yêu cầu hủy', 'Địa chỉ giao hàng không hợp lệ']
