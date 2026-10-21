export const RETURN_TYPES = {
  RETURN_AND_REFUND: 'Trả hàng & Hoàn tiền',
  REFUND_ONLY: 'Chỉ hoàn tiền (không trả hàng)'
}

export const RETURN_REASONS = {
  DAMAGED: 'Hàng bị hư hỏng, lỗi',
  WRONG_ITEM: 'Giao sai hàng / sai phân loại',
  NOT_AS_DESCRIBED: 'Hàng không đúng mô tả',
  MISSING_ITEM: 'Thiếu hàng',
  FAKE: 'Hàng giả, hàng nhái',
  OTHER: 'Lý do khác'
}

export const RETURN_STATUS = {
  REQUESTED: 'Chờ Người bán phản hồi',
  REJECTED: 'Người bán từ chối',
  ESCALATED: 'Đang khiếu nại — chờ Shoppi xử lý',
  REFUNDED: 'Đã hoàn tiền',
  CLOSED: 'Đã đóng'
}

// reasons offered when the buyer cancels an order (like Shopee's list)
export const BUYER_CANCEL_REASONS = [
  'Muốn thay đổi địa chỉ giao hàng',
  'Muốn nhập/thay đổi mã voucher',
  'Muốn thay đổi sản phẩm trong đơn hàng (size, màu sắc, số lượng...)',
  'Thủ tục thanh toán quá rắc rối',
  'Tìm thấy giá rẻ hơn ở chỗ khác',
  'Đổi ý, không muốn mua nữa'
]

export const SELLER_CANCEL_REASONS = [
  'Hết hàng',
  'Không liên hệ được với người mua',
  'Người mua yêu cầu hủy',
  'Địa chỉ giao hàng không hợp lệ'
]

// ---- refunds & disputes ----
export const REFUND_STATUS = { PENDING: 'Chờ chuyển khoản', COMPLETED: 'Đã hoàn tiền', FAILED: 'Thất bại' }
export const REFUND_SOURCE = { ORDER_CANCELLED: 'Hủy đơn', RETURN: 'Trả hàng/Hoàn tiền', DISPUTE: 'Giải quyết khiếu nại', ADMIN: 'Shoppi hoàn thủ công' }
export const REFUND_CHANNEL = { ORIGINAL_METHOD: 'Về phương thức thanh toán ban đầu', BANK_TRANSFER: 'Chuyển khoản ngân hàng' }
export const DISPUTE_DECISION = { REFUND_FULL: 'Hoàn toàn bộ', REFUND_PARTIAL: 'Hoàn một phần', REJECT: 'Không hoàn tiền' }
