// Demo catalogue for scripts/seed-demo.mjs (and the picture generator that made demo-data/images).
// demand: units a day ~120 days ago (base), change per day (trend), weekend multiplier, and the stock left at the end
// - chosen so the "Du bao nhap hang (AI)" page shows every case: out of stock, urgent, reorder, ok, overstock, slow.

export const SHOPS = [
  {
    key: 'fashion', name: 'Thời Trang Sài Gòn', mall: true, province: 'TP. Hồ Chí Minh', district: 'Quận 1', ward: 'Bến Nghé',
    description: 'Thời trang nam nữ thiết kế tại Sài Gòn. Đổi size miễn phí trong 7 ngày.', emoji: '👗', color: ['#ff7a59', '#ee4d2d'],
    products: [
      { key: 'tee', name: 'Áo thun cotton nam basic cổ tròn', price: 159000, original: 220000, cat: 'Áo nam', emoji: '👕', color: ['#fde2d8', '#f7b9a4'],
        tiers: ['Màu', 'Size'], options: [['Trắng', 'Đen'], ['M', 'L', 'XL']], demand: { base: 3, trend: 0.035, weekend: 1.6, endStock: 18 }, freeShipping: true },
      { key: 'jacket', name: 'Áo khoác gió unisex chống nước', price: 289000, original: 390000, cat: 'Áo nam', emoji: '🧥', color: ['#dbe7ff', '#a9c4f5'],
        demand: { base: 1.6, trend: 0, weekend: 1.4, endStock: 70 } },
      { key: 'dress', name: 'Váy hoa nhí dáng xòe vintage', price: 245000, original: 320000, cat: 'Váy đầm', emoji: '👗', color: ['#ffe0ef', '#f5a8c9'],
        tiers: ['Size'], options: [['S', 'M', 'L']], demand: { base: 1.8, trend: 0.01, weekend: 1.8, endStock: 40 } },
      { key: 'jeans', name: 'Quần jean nữ ống rộng lưng cao', price: 329000, original: 450000, cat: 'Quần', emoji: '👖', color: ['#dfe8f5', '#9fb6d8'],
        demand: { base: 1.2, trend: 0, weekend: 1.5, endStock: 6 } },
      { key: 'sneaker', name: 'Giày thể thao nữ đế êm siêu nhẹ', price: 399000, original: 590000, cat: 'Giày dép', emoji: '👟', color: ['#e6f6ec', '#a8dcb9'],
        demand: { base: 1.4, trend: 0.004, weekend: 1.5, endStock: 55 }, freeShipping: true },
      { key: 'tote', name: 'Túi tote vải canvas in chữ', price: 129000, cat: 'Túi ví', emoji: '👜', color: ['#fff4d6', '#f3d58a'],
        demand: { base: 1.5, trend: -0.011, weekend: 1.3, endStock: 160 } }
    ]
  },
  {
    key: 'tech', name: 'Điện Máy Chính Hãng', mall: true, province: 'Hà Nội', district: 'Cầu Giấy', ward: 'Dịch Vọng',
    description: 'Phụ kiện điện tử chính hãng, bảo hành 12 tháng, 1 đổi 1 trong 30 ngày nếu lỗi.', emoji: '🔌', color: ['#4f8ff7', '#2463d6'],
    products: [
      { key: 'earbuds', name: 'Tai nghe bluetooth chống ồn ANC pin 30 giờ', price: 690000, original: 990000, cat: 'Tai nghe', emoji: '🎧', color: ['#e3e8f0', '#b4bfd1'],
        demand: { base: 1.8, trend: 0.012, weekend: 1.3, endStock: 0 }, freeShipping: true },
      { key: 'powerbank', name: 'Sạc dự phòng 20000mAh sạc nhanh 22.5W', price: 350000, original: 450000, cat: 'Phụ kiện điện tử', emoji: '🔋', color: ['#e4f7e9', '#9fdcb0'],
        demand: { base: 2.4, trend: 0.008, weekend: 1.2, endStock: 26 } },
      { key: 'mouse', name: 'Chuột không dây im lặng 2.4G', price: 189000, cat: 'Máy tính', emoji: '🖱️', color: ['#eeeeee', '#c9c9c9'],
        demand: { base: 1.5, trend: 0, weekend: 0.8, endStock: 80 } },
      { key: 'keyboard', name: 'Bàn phím cơ RGB switch đỏ', price: 790000, original: 990000, cat: 'Máy tính', emoji: '⌨️', color: ['#e9e3fb', '#bfaef0'],
        demand: { base: 0.7, trend: 0.003, weekend: 1.1, endStock: 30 } },
      { key: 'cable', name: 'Cáp sạc nhanh Type-C dây dù 1m', price: 59000, cat: 'Phụ kiện điện tử', emoji: '🔌', color: ['#fff0e0', '#f7c58f'],
        demand: { base: 3.2, trend: 0, weekend: 1.1, endStock: 300 } },
      { key: 'speaker', name: 'Loa bluetooth mini chống nước IPX7', price: 450000, original: 590000, cat: 'Tai nghe', emoji: '🔊', color: ['#e0f4f8', '#99d3e0'],
        demand: { base: 0.6, trend: 0, weekend: 1.6, endStock: 90 } }
    ]
  },
  {
    key: 'home', name: 'Nhà Xinh Official', mall: false, province: 'Đà Nẵng', district: 'Hải Châu', ward: 'Thạch Thang',
    description: 'Đồ gia dụng thông minh cho căn nhà gọn gàng. Giao nhanh 2h nội thành Đà Nẵng.', emoji: '🏡', color: ['#36c48f', '#16a06c'],
    products: [
      { key: 'bottle', name: 'Bình giữ nhiệt inox 304 dung tích 500ml', price: 129000, original: 180000, cat: 'Nhà bếp', emoji: '🥤', color: ['#e2f3f7', '#a8d8e6'],
        tiers: ['Màu'], options: [['Xanh', 'Hồng', 'Đen']], demand: { base: 2.6, trend: 0.006, weekend: 1.2, endStock: 45 } },
      { key: 'airfryer', name: 'Nồi chiên không dầu 5L điều khiển cảm ứng', price: 1290000, original: 1890000, cat: 'Nhà bếp', emoji: '🍳', color: ['#f2f2f2', '#cfcfcf'],
        demand: { base: 0.35, trend: 0, weekend: 1.4, endStock: 180 }, freeShipping: true },
      { key: 'knives', name: 'Bộ dao nhà bếp 5 món thép không gỉ', price: 259000, cat: 'Nhà bếp', emoji: '🔪', color: ['#ececec', '#bdbdbd'],
        demand: { base: 0.8, trend: 0, weekend: 1.2, endStock: 40 } },
      { key: 'lamp', name: 'Đèn ngủ cảm ứng 3 chế độ sáng', price: 149000, cat: 'Phòng ngủ', emoji: '💡', color: ['#fff6d8', '#f5dd8a'],
        demand: { base: 1.6, trend: -0.02, weekend: 1.1, endStock: 120 } },
      { key: 'blanket', name: 'Chăn hè cotton đũi mềm mát', price: 299000, original: 399000, cat: 'Phòng ngủ', emoji: '🛏️', color: ['#e9f0ff', '#b5c8f2'],
        demand: { base: 0.5, trend: 0.012, weekend: 1.3, endStock: 35 } },
      { key: 'mop', name: 'Cây lau nhà xoay 360 độ kèm 2 bông', price: 199000, cat: 'Dụng cụ vệ sinh', emoji: '🧹', color: ['#e8f7ef', '#ade0c2'],
        demand: { base: 1, trend: 0, weekend: 1.5, endStock: 50 } }
    ]
  },
  {
    key: 'beauty', name: 'Beauty Corner', mall: false, province: 'TP. Hồ Chí Minh', district: 'Quận 3', ward: 'Võ Thị Sáu',
    description: 'Mỹ phẩm chính hãng, có hóa đơn nhập khẩu. Tư vấn chọn sản phẩm theo loại da.', emoji: '💄', color: ['#ff6fa8', '#e0447f'],
    products: [
      { key: 'lipstick', name: 'Son kem lì mềm môi lâu trôi', price: 99000, original: 150000, cat: 'Trang điểm', emoji: '💄', color: ['#ffe1e6', '#f5a3b1'],
        tiers: ['Màu'], options: [['Đỏ cam', 'Hồng đất', 'Đỏ gạch']], demand: { base: 3, trend: 0.01, weekend: 1.5, endStock: 60 } },
      { key: 'sunscreen', name: 'Kem chống nắng dịu nhẹ SPF50+ PA++++', price: 185000, original: 250000, cat: 'Chăm sóc da', emoji: '🧴', color: ['#fff3dc', '#f5cf87'],
        demand: { base: 2.2, trend: 0.02, weekend: 1.2, endStock: 30 }, freeShipping: true },
      { key: 'cleanser', name: 'Sữa rửa mặt tạo bọt cho da dầu mụn', price: 125000, cat: 'Chăm sóc da', emoji: '🫧', color: ['#e3f6fb', '#9fd8e8'],
        demand: { base: 1.8, trend: 0, weekend: 1.2, endStock: 70 } },
      { key: 'serum', name: 'Serum vitamin C sáng da mờ thâm 30ml', price: 289000, original: 390000, cat: 'Chăm sóc da', emoji: '🍊', color: ['#fff0dd', '#f7c48a'],
        demand: { base: 1.1, trend: 0.005, weekend: 1.3, endStock: 25 } },
      { key: 'shampoo', name: 'Dầu gội thảo dược bồ kết 500ml', price: 159000, cat: 'Chăm sóc tóc', emoji: '🌿', color: ['#e6f5e1', '#aedb9f'],
        demand: { base: 1, trend: 0, weekend: 1.1, endStock: 60 } },
      { key: 'perfume', name: 'Nước hoa mini 10ml hương hoa nhài', price: 219000, cat: 'Nước hoa', emoji: '🌸', color: ['#f6e6ff', '#d4a9f0'],
        demand: { base: 0.4, trend: -0.003, weekend: 1.6, endStock: 85 } }
    ]
  }
]

export const BUYERS = [
  ['Nguyễn Minh Anh', 'TP. Hồ Chí Minh', 'Quận 7', 'Tân Phong'], ['Trần Quốc Bảo', 'Hà Nội', 'Đống Đa', 'Láng Hạ'],
  ['Lê Thu Hà', 'Đà Nẵng', 'Sơn Trà', 'An Hải Bắc'], ['Phạm Gia Huy', 'Cần Thơ', 'Ninh Kiều', 'Tân An'],
  ['Võ Ngọc Lan', 'TP. Hồ Chí Minh', 'Bình Thạnh', 'Phường 25'], ['Đặng Hoàng Long', 'Hải Phòng', 'Lê Chân', 'An Biên'],
  ['Bùi Khánh Linh', 'Hà Nội', 'Hoàn Kiếm', 'Hàng Bạc'], ['Huỳnh Tấn Phát', 'TP. Hồ Chí Minh', 'Thủ Dầu Một', 'Phú Cường'],
  ['Ngô Thảo Vy', 'Khánh Hòa', 'Nha Trang', 'Lộc Thọ'], ['Đỗ Đức Thắng', 'Nghệ An', 'Vinh', 'Hưng Bình'],
  ['Phan Mai Chi', 'TP. Hồ Chí Minh', 'Thủ Đức', 'Linh Trung'], ['Hồ Văn Tài', 'Đồng Nai', 'Biên Hòa', 'Tân Mai']
]

// review texts by star rating (a few complaints so the review hub and its AI have something to sort)
export const REVIEWS = {
  5: ['Hàng đẹp y hình, đóng gói cẩn thận, giao nhanh. Sẽ ủng hộ shop tiếp!', 'Chất lượng tốt so với giá, dùng rất thích.', 'Shop tư vấn nhiệt tình, sản phẩm chuẩn mô tả.',
      'Mua lần thứ hai rồi, vẫn tốt như lần đầu.', 'Giao hàng siêu nhanh, mới đặt hôm qua hôm nay đã có.', 'Đáng tiền, mọi người nên mua nhé.'],
  4: ['Sản phẩm ổn, giao hơi chậm một chút.', 'Hàng tốt nhưng hộp hơi móp do vận chuyển.', 'Dùng ổn, màu hơi khác hình một xíu.'],
  3: ['Tạm được, không quá xuất sắc.', 'Chất lượng bình thường so với giá tiền.'],
  2: ['Hàng giao thiếu phụ kiện, nhắn shop mãi mới trả lời.', 'Size không đúng bảng, mặc bị chật, đổi trả hơi phiền.'],
  1: ['Sản phẩm bị lỗi ngay khi mở hộp, rất thất vọng.', 'Giao sai màu, shop không phản hồi tin nhắn.']
}
