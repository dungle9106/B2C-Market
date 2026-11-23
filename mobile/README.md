# Shoppi Mobile – App Người mua & App Người bán

Hai app Android viết bằng **React Native (Expo SDK 54)**, dùng chung một mã nguồn và gọi cùng backend Spring Boot (`/api`) với bản web.

| App | Package | Đối tượng |
|---|---|---|
| **Shoppi** | `vn.shoppi.buyer` | Người mua |
| **Shoppi Người Bán** | `vn.shoppi.seller` | Người bán (chủ shop) |

Biến môi trường `APP_VARIANT` (`buyer` / `seller`) lúc build quyết định ra app nào ([app.config.js](app.config.js)). Hai app có package khác nhau nên cài được cùng lúc trên một máy.

## Chức năng

**App Người mua**
- Trang chủ: banner, chương trình khuyến mãi của Shoppi, **Shoppi Flash Sale** theo khung giờ, danh mục, **"Dành riêng cho bạn"** (mô hình gợi ý đã train), gợi ý hôm nay
- Tìm kiếm có gợi ý từ khóa, sắp xếp, lọc **Shoppi Mall**; **tìm bằng hình ảnh** (MobileNetV2, chụp camera / chọn ảnh)
- Chi tiết sản phẩm: ảnh, phân loại, yêu thích, theo dõi giá, **phí ship tới tỉnh của bạn**, voucher của shop, **combo**, **mua kèm deal sốc / quà tặng**, **hỏi đáp với shop**, đánh giá, sản phẩm tương tự, báo cáo sản phẩm
- Trang shop: theo dõi, chat, voucher của shop, danh mục của shop
- Thanh toán: địa chỉ, đơn vị vận chuyển, voucher, Shoppi Xu; **COD, ví ShoppiPay, ví điện tử (MoMo/ZaloPay/VNPay), thẻ, chuyển khoản, cổng VNPay thật** (khi admin bật) và màn **thanh toán online** (đếm ngược, thẻ thử, hủy giao dịch)
- Đơn mua: hành trình, thanh toán lại đơn chờ thanh toán, hủy, đã nhận, mua lại, đánh giá, trả hàng/hoàn tiền, **khiếu nại và trao đổi với Shoppi**
- Ví ShoppiPay, Shoppi Xu, Vòng quay may mắn, Kho Voucher, Đã thích, Theo dõi giá, Shop đang theo dõi, Sổ địa chỉ, Shoppi Video
- Chat với shop, **Trợ lý mua sắm AI**, Thông báo, **Trung tâm hỗ trợ** (câu hỏi thường gặp, gửi và theo dõi yêu cầu), Chính sách Shoppi, **Bảo mật tài khoản** (xác thực 2 lớp, thiết bị đăng nhập, lịch sử đăng nhập)

**App Người bán**
- Tổng quan, Đơn hàng, Sản phẩm (thêm / sửa, **AI viết mô tả**, **AI gợi ý danh mục từ ảnh**, **AI gợi ý giá**, nhân bản, ẩn / hiện), Chat (**AI gợi ý câu trả lời**)
- Chăm sóc khách hàng: trả hàng/hoàn tiền (kèm trao đổi khiếu nại), trả lời đánh giá, **hỏi đáp sản phẩm** (AI gợi ý), **tin nhắn tự động & mẫu trả lời**
- Kênh Marketing: **mã giảm giá của shop**, **Flash Sale của shop**, **đăng ký Shoppi Flash Sale**, **combo**, **mua kèm deal sốc / quà tặng**, **quảng cáo** (ví quảng cáo, giá thầu, từ khóa), **Shoppi Video**
- Báo cáo & AI: **phân tích doanh thu / sản phẩm / lượt xem / đánh giá**, **doanh thu & đối soát**, hiệu quả hoạt động, trợ lý bán hàng AI, **AI phân tích đánh giá**, **AI gợi ý thời điểm khuyến mãi**, **tồn kho** (điều chỉnh, lịch sử), dự báo nhập hàng AI
- Quản lý shop: **hồ sơ shop**, **danh mục của shop**, **chính sách đổi trả & vận chuyển**, **xác minh shop**; bảo mật tài khoản, hỗ trợ từ Shoppi
- Tài khoản người mua chưa có shop có thể **mở shop ngay trong app**

Chỉ dùng trên web: trang quản trị, in phiếu giao hàng hàng loạt, nhập / xuất sản phẩm bằng Excel, thao tác hàng loạt trên nhiều đơn / sản phẩm.

## Cấu trúc

```
mobile/
├── App.js                  chọn BuyerApp / SellerApp theo APP_VARIANT
├── app.config.js           cấu hình 2 biến thể, quyền camera, cho phép http
├── scripts/build-apk.js    build file APK
└── src/
    ├── api.js              gọi API (JWT + tự làm mới token), upload ảnh
    ├── auth.js             trạng thái đăng nhập (lưu token bằng SecureStore)
    ├── ui.js               component dùng chung (nút, ô nhập, thẻ sản phẩm, tab...)
    ├── support.js          danh mục hỗ trợ + câu hỏi thường gặp (chép từ web)
    ├── common/             đăng nhập, chat, thông báo, hỗ trợ, bảo mật, khiếu nại (dùng cho cả 2 app)
    ├── buyer/              màn hình app Người mua
    └── seller/             màn hình app Người bán
```

## Chạy thử khi phát triển

```bash
cd mobile
npm install
npx expo start            # app Người mua; quét QR bằng Expo Go hoặc bấm "a" để mở máy ảo
```

App Người bán (PowerShell): `$env:APP_VARIANT="seller"; npx expo start`

## Build file APK

Cần Android SDK (`ANDROID_HOME`, mặc định `D:\Android\SDK`) và JDK 17+ (`JAVA_HOME`).

```bash
npm run build:buyer       # -> dist/Shoppi-NguoiMua.apk
npm run build:seller      # -> dist/Shoppi-NguoiBan.apk
```

Lần build đầu mất 10–20 phút (tải Gradle, NDK, thư viện). Máy cần khoảng 4 GB RAM trống.

## Chạy trên iPhone (iOS)

Cùng một mã nguồn chạy được trên iOS. Có 3 cách:

1. **Expo Go (nhanh nhất, không cần máy Mac):** cài app *Expo Go* từ App Store, chạy `npx expo start` trên máy tính, rồi dùng Camera của iPhone quét mã QR. iPhone và máy tính phải chung Wi-Fi. Cách này phù hợp để demo. Backend đã chiếm cổng 8081 nên Expo sẽ hỏi dùng cổng khác, chọn **Yes**. App tự nhận địa chỉ backend `http://<IP-máy-tính>:8081`.
2. **Máy ảo iOS:** cần máy Mac có Xcode, chạy `npx expo run:ios`.
3. **File cài đặt thật (.ipa):** dùng `npx eas build --platform ios` (build trên cloud của Expo). Để cài lên iPhone thật cần tài khoản Apple Developer (99 USD/năm).

Trên iPhone, địa chỉ máy chủ là `http://<IP-LAN-của-máy-tính>:8081`. Địa chỉ `10.0.2.2` chỉ dùng được trên máy ảo Android.

## Kết nối backend

Ở màn hình đăng nhập bấm **⚙ Máy chủ** để đổi địa chỉ backend:

- Máy ảo Android: `http://10.0.2.2:8081` (mặc định – 10.0.2.2 là máy tính chạy máy ảo)
- Điện thoại thật: `http://<IP-LAN-của-máy-tính>:8081` (cùng mạng Wi-Fi; mở cổng 8081 trên tường lửa Windows)
