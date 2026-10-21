export const TICKET_CATEGORIES = {
  ORDER: 'Đơn hàng',
  PAYMENT: 'Thanh toán',
  SHIPPING: 'Vận chuyển',
  RETURN_REFUND: 'Trả hàng / Hoàn tiền',
  PRODUCT: 'Sản phẩm',
  ACCOUNT: 'Tài khoản',
  SELLER: 'Kênh Người Bán',
  REPORT: 'Báo cáo vi phạm',
  OTHER: 'Khác'
}

export const TICKET_STATUS = {
  OPEN: 'Chờ xử lý',
  IN_PROGRESS: 'Đang xử lý',
  WAITING_CUSTOMER: 'Chờ bạn phản hồi',
  RESOLVED: 'Đã giải quyết',
  CLOSED: 'Đã đóng'
}

// the same statuses seen from the support desk
export const TICKET_STATUS_STAFF = { ...TICKET_STATUS, WAITING_CUSTOMER: 'Chờ người dùng phản hồi' }

export const TICKET_PRIORITY = { LOW: 'Thấp', NORMAL: 'Bình thường', HIGH: 'Cao', URGENT: 'Khẩn cấp' }

// Help-centre articles, also the quick answers suggested before opening a ticket
export const FAQ = [
  {
    topic: 'Đơn hàng',
    icon: '📦',
    items: [
      { q: 'Làm sao theo dõi đơn hàng?', a: 'Vào "Đơn hàng của tôi" và chọn đơn. Trang chi tiết có tiến trình 5 bước và hành trình vận chuyển; khi shop giao cho đơn vị vận chuyển, đơn sẽ có mã vận đơn. Bạn cũng nhận được thông báo ở mỗi bước.' },
      { q: 'Tôi có thể hủy đơn không?', a: 'Được, khi đơn còn ở trạng thái Chờ thanh toán, Chờ xác nhận hoặc Chờ lấy hàng: vào chi tiết đơn → "Hủy đơn hàng" và chọn lý do. Khi shop đã giao cho đơn vị vận chuyển thì không thể hủy. Đơn đã thanh toán online được hoàn tiền, voucher được trả lại.' },
      { q: 'Đã quá ngày dự kiến mà chưa nhận được hàng?', a: 'Hãy xem hành trình trong chi tiết đơn và nhắn tin cho shop bằng nút "Chat với Shop". Nếu vẫn chưa được giải quyết, gửi yêu cầu hỗ trợ chủ đề "Vận chuyển" kèm mã đơn.' }
    ]
  },
  {
    topic: 'Trả hàng / Hoàn tiền',
    icon: '↩️',
    items: [
      { q: 'Điều kiện trả hàng/hoàn tiền?', a: 'Yêu cầu trong thời hạn đổi trả của sản phẩm (ghi trên trang sản phẩm), tính từ lúc giao thành công. Cần ảnh hoặc video làm bằng chứng. Một số sản phẩm không áp dụng đổi trả.' },
      { q: 'Shop từ chối yêu cầu của tôi thì sao?', a: 'Trong chi tiết đơn, bấm "Khiếu nại" để Shoppi xem xét. Quyết định của Shoppi là cuối cùng và được thông báo cho cả bạn và shop.' },
      { q: 'Bao lâu thì nhận được tiền hoàn?', a: 'Ngay khi yêu cầu được chấp nhận, trạng thái đơn chuyển sang "Đã hoàn tiền" và bạn nhận được thông báo.' }
    ]
  },
  {
    topic: 'Thanh toán',
    icon: '💳',
    items: [
      { q: 'Shoppi hỗ trợ những hình thức thanh toán nào?', a: 'Thanh toán khi nhận hàng (COD), ví điện tử MoMo/ZaloPay/VNPay, thẻ Visa/Mastercard/JCB và chuyển khoản ngân hàng.' },
      { q: 'Vì sao đơn của tôi bị hủy tự động?', a: 'Đơn thanh toán online cần hoàn tất trong 15 phút. Quá thời gian này đơn sẽ tự hủy và sản phẩm được hoàn về kho.' }
    ]
  },
  {
    topic: 'Vận chuyển',
    icon: '🚚',
    items: [
      { q: 'Phí vận chuyển được tính thế nào?', a: 'Phí phụ thuộc đơn vị vận chuyển (GHN, GHTK, J&T, Viettel Post), gói (Tiết kiệm, Nhanh, Hỏa tốc) và việc shop với bạn cùng hay khác tỉnh. Hỏa tốc chỉ có khi cùng tỉnh.' },
      { q: 'Freeship hoạt động ra sao?', a: 'Sản phẩm có nhãn Freeship được miễn phí vận chuyển tối đa bằng phí gói Tiết kiệm. Bạn có thể dùng thêm voucher giảm phí ship.' }
    ]
  },
  {
    topic: 'Voucher & Khuyến mãi',
    icon: '🎁',
    items: [
      { q: 'Dùng voucher như thế nào?', a: 'Ở bước Thanh toán, chọn voucher của shop và Shoppi Voucher (toàn sàn). Voucher có điều kiện đơn tối thiểu, số lượt và thời hạn.' },
      { q: 'Làm sao biết khi có khuyến mãi mới?', a: 'Thông báo khuyến mãi từ Shoppi và từ các shop bạn từng mua hiển thị ở biểu tượng chuông trên thanh đầu trang.' }
    ]
  },
  {
    topic: 'Tài khoản & Bán hàng',
    icon: '👤',
    items: [
      { q: 'Làm sao để mở shop bán hàng?', a: 'Bấm "Trở Thành Người Bán" trên thanh đầu trang, điền thông tin shop là có thể đăng sản phẩm ngay.' },
      { q: 'Liên hệ shop trước khi mua?', a: 'Trên trang sản phẩm, bấm "Chat ngay" để nhắn tin trực tiếp với shop. Bạn cũng có thể hỏi Trợ lý AI để được gợi ý sản phẩm.' }
    ]
  }
]
