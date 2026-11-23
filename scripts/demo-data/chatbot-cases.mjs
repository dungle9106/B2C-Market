// Test conversations for the shopping assistant (scripts/chatbot-eval.mjs), written like real buyers type:
// no accents, abbreviations and teen code, typos, changing their mind, follow-ups that only make sense with the
// previous message. Built on the products that are actually on sale (seed-real-products / seed-demo).
//
// A case is one conversation: turns: [{ say, expect }]. Only the turns with `expect` are scored. Checks:
//   product: 'tai nghe'          the first product card's name contains this (accents ignored); an array = any of them
//   productsAll: true            every card shown matches `product` (not only the first)
//   maxPrice / minPrice          every card's price is within
//   reply: ['cod', ...]          the answer mentions at least one of these (accents ignored)
//   orders: true                 the answer comes with the buyer's order cards
//   support: true                "Gửi yêu cầu hỗ trợ" is offered (asked for a human / could not help)
//   noProducts: true             no product cards (nothing on sale fits: it must not show unrelated products)
//   notProduct: 'ao thun'        the first card is NOT this (after the buyer changed their mind)
//   action: 'CART_ADDED'         the assistant did this (agent with an API key only: agentOnly: true)
//   step: 'Gợi ý bằng mô hình AI' it used this tool (agent only)

export const GROUPS = {
  standard: 'Câu chuẩn',
  noAccent: 'Không dấu',
  abbreviation: 'Viết tắt, teen code',
  typo: 'Sai chính tả',
  changeMind: 'Đổi ý giữa chừng',
  followUp: 'Câu nối tiếp',
  policy: 'Chính sách',
  orders: 'Đơn hàng',
  human: 'Cần người thật',
  outOfScope: 'Ngoài phạm vi (không bịa)',
  agent: 'Hành động (cần API key)'
}

const one = (group, say, expect, extra = {}) => ({ group, turns: [{ say, expect }], ...extra })

export const CASES = [
  // ---------- câu chuẩn ----------
  one('standard', 'Tìm tai nghe bluetooth', { product: 'tai nghe' }),
  one('standard', 'Có áo thun nam không shop', { product: 'ao thun' }),
  one('standard', 'Gợi ý son môi dưới 300k', { product: 'son', maxPrice: 300000 }),
  one('standard', 'Điện thoại Samsung', { product: 'samsung' }),
  one('standard', 'Chảo chống dính', { product: 'chao chong dinh' }),
  one('standard', 'Nước hoa nữ', { product: 'nuoc hoa' }),

  // ---------- không dấu ----------
  one('noAccent', 'tim tai nghe khong day', { product: 'tai nghe' }),
  one('noAccent', 'kem chong nang cho da dau', { product: 'kem chong nang' }),
  one('noAccent', 'giay the thao nu', { product: 'giay the thao' }),
  one('noAccent', 'dong ho nam rolex', { product: 'rolex' }),
  one('noAccent', 'thuc an cho meo', { product: 'cho meo' }),

  // ---------- viết tắt, teen code ----------
  one('abbreviation', 'có tn bluetooth ko shop', { product: 'tai nghe' }),
  one('abbreviation', 'dt samsung giá bn vậy', { product: 'samsung' }),
  one('abbreviation', 'mk cần đt dưới 5tr', { product: 'dien thoai', maxPrice: 5000000 }),
  one('abbreviation', 'ip x còn hàng k', { product: 'iphone' }),
  one('abbreviation', 'sạc dp 20000mah', { product: 'sac du phong' }),
  one('abbreviation', 'kcn cho da dầu', { product: 'kem chong nang' }),
  one('abbreviation', 'srm cho da mụn', { product: 'sua rua mat' }),
  one('abbreviation', 'lap gập 360 độ', { product: 'laptop' }),
  one('abbreviation', 'đh nam dây da', { product: 'dong ho' }),

  // ---------- sai chính tả ----------
  one('typo', 'tai nge bluetooth', { product: 'tai nghe' }),
  one('typo', 'dien thaoi samsug', { product: 'samsung' }),
  one('typo', 'ao khaoc gio chong nuoc', { product: 'ao khoac' }),
  one('typo', 'nuoc hao cho nu', { product: 'nuoc hoa' }),
  one('typo', 'binh giu nhiec', { product: 'giu nhiet' }),
  one('typo', 'chuot khong dai', { product: 'chuot' }),
  one('typo', 'mascra lam cong mi', { product: 'mascara' }),
  one('typo', 'dong ho apple wach', { product: 'apple watch' }),

  // ---------- đổi ý giữa chừng ----------
  { group: 'changeMind', turns: [{ say: 'tìm áo thun nam' }, { say: 'à thôi, cho mình xem quần jean nữ', expect: { product: 'quan jean', notProduct: 'ao thun' } }] },
  { group: 'changeMind', turns: [{ say: 'mình cần tai nghe' }, { say: 'không, đổi sang loa đi', expect: { product: 'loa', notProduct: 'tai nghe' } }] },
  { group: 'changeMind', turns: [{ say: 'có điện thoại samsung không' }, { say: 'thôi lấy iphone', expect: { product: 'iphone', notProduct: 'samsung' } }] },
  { group: 'changeMind', turns: [{ say: 'son môi màu đỏ' }, { say: 'à mà thôi, xem nước hoa đi', expect: { product: 'nuoc hoa', notProduct: 'son' } }] },
  { group: 'changeMind', turns: [{ say: 'tìm balo' }, { say: 'nhầm, ý mình là túi xách nữ', expect: { product: 'tui xach' } }] },

  // ---------- câu nối tiếp (chỉ hiểu được nhờ câu trước) ----------
  { group: 'followUp', turns: [{ say: 'tìm điện thoại' }, { say: 'dưới 5 triệu thôi', expect: { product: 'dien thoai', maxPrice: 5000000 } }] },
  { group: 'followUp', turns: [{ say: 'tai nghe bluetooth' }, { say: 'loại trên 1 triệu', expect: { product: 'tai nghe', minPrice: 1000000 } }] },
  { group: 'followUp', turns: [{ say: 'đồng hồ nam' }, { say: 'tầm 2 triệu', expect: { product: 'dong ho', minPrice: 1400000, maxPrice: 2600000 } }] },
  { group: 'followUp', turns: [{ say: 'kem chống nắng' }, { say: 'còn sữa rửa mặt thì sao', expect: { product: 'sua rua mat' } }] },
  { group: 'followUp', turns: [{ say: 'túi xách nữ' }, { say: 'có cái nào rẻ hơn 1tr ko', expect: { product: ['tui', 'balo'], maxPrice: 1000000 } }] },

  // ---------- chính sách ----------
  one('policy', 'phí ship tính sao', { reply: ['phi ship', 'van chuyen'] }),
  one('policy', 'có trả tiền khi nhận hàng ko', { reply: ['cod'] }),
  one('policy', 'làm sao để hủy đơn', { reply: ['huy don'] }),
  one('policy', 'hàng bị lỗi muốn trả lại', { reply: ['tra hang', 'hoan tien'] }),
  one('policy', 'mã giảm giá dùng ở đâu', { reply: ['voucher'] }),
  one('policy', 'muốn mở shop bán hàng thì làm sao', { reply: ['nguoi ban', 'mo shop'] }),
  one('policy', 'thanh toan bang momo dc ko', { reply: ['momo', 'vi dien tu'] }),

  // ---------- đơn hàng ----------
  one('orders', 'đơn hàng của mình đang ở đâu', { orders: true }),
  one('orders', 'đh của mk giao chưa', { orders: true }),
  one('orders', 'kiểm tra giúp mình đơn gần nhất', { orders: true }),

  // ---------- cần người thật ----------
  one('human', 'cho mình gặp nhân viên', { support: true }),
  one('human', 'shop lừa đảo, mình muốn khiếu nại', { support: true }),

  // ---------- ngoài phạm vi: không được đưa sản phẩm không liên quan ----------
  one('outOfScope', 'bán xe máy honda không', { noProducts: true }),
  one('outOfScope', 'thời tiết hôm nay thế nào', { noProducts: true }),
  one('outOfScope', 'có bán tủ lạnh samsung không', { noProducts: true }),

  // ---------- bộ giữ lại (held-out): viết sau khi đã sửa bot theo các câu trên, không dùng để sửa ----------
  // điểm của nhóm này mới cho biết bot làm tốt với câu MỚI, không chỉ với các câu đã biết
  ...[
    one('abbreviation', 'có bán tai phone ko', { product: 'tai nghe' }),
    one('standard', 'kiếm cho mk cái áo khoác', { product: 'ao khoac' }),
    one('abbreviation', 'dt iphone giá rẻ', { product: 'iphone' }),
    one('typo', 'kem chống nắg', { product: 'kem chong nang' }),
    one('standard', 'shop có bán quần jean ko ạ', { product: 'quan jean' }),
    one('standard', 'son lì', { product: 'son' }),
    one('noAccent', 'xem dong ho nu', { product: 'dong ho' }),
    one('standard', 'nồi chiên không dầu', { product: 'noi chien' }),   // on sale (Nhà Xinh): first written as out of scope by mistake
    one('outOfScope', 'có bán máy giặt không', { noProducts: true }),
    { group: 'followUp', turns: [{ say: 'tìm giày thể thao' }, { say: 'loại dưới 500k', expect: { product: 'giay', maxPrice: 500000 } }] },
    { group: 'changeMind', turns: [{ say: 'tìm laptop' }, { say: 'à nhầm, mình cần chuột', expect: { product: 'chuot', notProduct: 'laptop' } }] },
    one('policy', 'ship cod đc k', { reply: ['cod'] }),
    one('orders', 'đơn mình tới đâu rồi', { orders: true }),
    one('policy', 'bao lâu thì nhận được hàng', { reply: ['van chuyen', 'giao'] }),
    one('typo', 'tai nghe blutooth', { product: 'tai nghe' })
  ].map((c) => ({ ...c, heldOut: true })),

  // ---------- hành động (agent, cần API key) ----------
  one('agent', 'thêm tai nghe Apple AirPods vào giỏ giúp mình', { action: 'CART_ADDED' }, { agentOnly: true }),
  one('agent', 'gợi ý vài món hợp với mình', { step: 'Gợi ý bằng mô hình AI' }, { agentOnly: true })
]
