// Vietnamese labels for the admin console
export const ROLE = { BUYER: 'Người mua', SELLER: 'Người bán', ADMIN: 'Quản trị' }
export const STATUS = { ACTIVE: 'Hoạt động', LOCKED: 'Bị khóa', DELETED: 'Đã xóa' }
export const SHOP_STATUS = { PENDING: 'Chờ duyệt', APPROVED: 'Đang hoạt động', REJECTED: 'Bị từ chối', SUSPENDED: 'Tạm đình chỉ' }
export const AUDIT_ACTION = {
  LOCK_USER: 'Khóa tài khoản',
  UNLOCK_USER: 'Mở khóa tài khoản',
  DELETE_USER: 'Xóa tài khoản',
  APPROVE_SHOP: 'Duyệt shop',
  REJECT_SHOP: 'Từ chối shop',
  SUSPEND_SHOP: 'Đình chỉ shop',
  REINSTATE_SHOP: 'Mở lại shop',
  CREATE_STAFF: 'Thêm nhân viên',
  UPDATE_STAFF: 'Đổi quyền nhân viên',
  REVOKE_STAFF: 'Thu hồi quyền quản trị',
  UPDATE_SETTING: 'Đổi cài đặt sàn',
  APPROVE_PRODUCT: 'Duyệt sản phẩm',
  REJECT_PRODUCT: 'Từ chối sản phẩm',
  REMOVE_PRODUCT: 'Gỡ sản phẩm vi phạm',
  RESTORE_PRODUCT: 'Khôi phục sản phẩm',
  DISMISS_REPORTS: 'Bỏ qua báo cáo',
  CREATE_CATEGORY: 'Thêm danh mục',
  UPDATE_CATEGORY: 'Sửa danh mục',
  HIDE_CATEGORY: 'Ẩn danh mục',
  SHOW_CATEGORY: 'Hiện danh mục',
  DELETE_CATEGORY: 'Xóa danh mục',
  CANCEL_ORDER: 'Hủy đơn hàng',
  MANUAL_REFUND: 'Hoàn tiền thủ công',
  COMPLETE_REFUND: 'Xác nhận đã hoàn tiền',
  FAIL_REFUND: 'Hoàn tiền thất bại',
  RESOLVE_DISPUTE: 'Giải quyết tranh chấp',
  CREATE_SETTLEMENT: 'Tạo bảng đối soát',
  PAY_SETTLEMENT: 'Thanh toán cho người bán',
  CREATE_VOUCHER: 'Tạo Shoppi Voucher',
  UPDATE_VOUCHER: 'Sửa Shoppi Voucher',
  END_VOUCHER: 'Kết thúc Shoppi Voucher',
  PAUSE_VOUCHER: 'Tạm dừng Shoppi Voucher',
  RESUME_VOUCHER: 'Tiếp tục Shoppi Voucher',
  CREATE_CAMPAIGN: 'Tạo chương trình khuyến mãi',
  UPDATE_CAMPAIGN: 'Sửa chương trình khuyến mãi',
  SHOW_CAMPAIGN: 'Hiện chương trình',
  HIDE_CAMPAIGN: 'Ẩn chương trình',
  DELETE_CAMPAIGN: 'Xóa chương trình',
  CREATE_BANNER: 'Thêm banner',
  UPDATE_BANNER: 'Sửa banner',
  SHOW_BANNER: 'Hiện banner',
  HIDE_BANNER: 'Ẩn banner',
  DELETE_BANNER: 'Xóa banner',
  CREATE_FLASH_EVENT: 'Tạo khung giờ Flash Sale',
  UPDATE_FLASH_EVENT: 'Sửa khung giờ Flash Sale',
  CANCEL_FLASH_EVENT: 'Hủy khung giờ Flash Sale',
  APPROVE_FLASH_ITEM: 'Duyệt sản phẩm Flash Sale',
  REJECT_FLASH_ITEM: 'Từ chối sản phẩm Flash Sale',
  EXPORT_REPORT: 'Xuất báo cáo toàn sàn',
  UPDATE_AI_CONFIG: 'Đổi cấu hình AI',
  PUBLISH_POLICY: 'Xuất bản chính sách',
  RESOLVE_ERROR: 'Đánh dấu lỗi đã xử lý',
  IGNORE_ERROR: 'Bỏ qua lỗi',
  REOPEN_ERROR: 'Mở lại lỗi'
}
export const PRODUCT_STATUS = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', REMOVED: 'Đã gỡ' }

// ---- transactions ----
export const PAYMENT_METHOD = { COD: 'COD', E_WALLET: 'Ví điện tử', CARD: 'Thẻ', BANK_TRANSFER: 'Chuyển khoản', VNPAY: 'VNPay', MOCK_ONLINE: 'Online' }
export const PAYMENT_STATUS = { PENDING: 'Chưa thanh toán', PAID: 'Đã thanh toán', FAILED: 'Lỗi', CANCELLED: 'Đã hủy', REFUNDED: 'Đã hoàn tiền' }
export const SETTLEMENT_STATUS = { PENDING_PAYMENT: 'Chờ thanh toán', PAID: 'Đã thanh toán' }
export const ANOMALY = {
  PAID_BUT_CANCELLED: 'Đơn hủy nhưng chưa hoàn tiền',
  AMOUNT_MISMATCH: 'Lệch số tiền',
  STUCK_AWAITING: 'Kẹt chờ thanh toán',
  REFUND_MISSING: 'Thiếu giao dịch hoàn tiền',
  OVER_REFUNDED: 'Hoàn vượt giá trị đơn',
  DELIVERED_UNPAID: 'Đã giao nhưng chưa thu tiền',
  REFUND_PENDING_LONG: 'Hoàn tiền chờ quá lâu'
}
