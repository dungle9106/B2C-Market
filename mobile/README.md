# Shoppi Mobile – App Người mua & App Người bán

Hai app Android viết bằng **React Native (Expo SDK 54)**, dùng chung một mã nguồn và gọi cùng backend Spring Boot (`/api`) với bản web.

| App | Package | Đối tượng |
|---|---|---|
| **Shoppi** | `vn.shoppi.buyer` | Người mua |
| **Shoppi Người Bán** | `vn.shoppi.seller` | Người bán (chủ shop) |

Biến môi trường `APP_VARIANT` (`buyer` / `seller`) lúc build quyết định ra app nào ([app.config.js](app.config.js)). Hai app có package khác nhau nên cài được cùng lúc trên một máy.

## Chức năng

**App Người mua**
- Trang chủ: danh mục, Flash Sale, gợi ý sản phẩm (cuộn vô hạn)
- Tìm kiếm có gợi ý từ khóa, sắp xếp, lọc **Shoppi Mall**; **tìm bằng hình ảnh** (chụp camera / chọn ảnh)
- Chi tiết sản phẩm: ảnh, chọn phân loại, yêu thích, đánh giá, sản phẩm tương tự; trang shop (theo dõi, chat)
- Giỏ hàng → Thanh toán: chọn địa chỉ, đơn vị vận chuyển, voucher shop / Shoppi, dùng **Shoppi Xu**, thanh toán khi nhận hàng (COD) hoặc **ví ShoppiPay**
- Đơn mua: theo dõi hành trình, hủy / yêu cầu hủy, đã nhận hàng, mua lại, đánh giá
- **Ví ShoppiPay** (nạp tiền mô phỏng, thanh toán, lịch sử), **Shoppi Xu** (số dư, điểm danh hằng ngày, hạng thành viên, lịch sử), Kho Voucher, Đã thích, Shop đang theo dõi, Sổ địa chỉ
- Chat với shop, **Trợ lý mua sắm AI**, Thông báo

**App Người bán**
- Tổng quan: việc cần làm, số liệu hôm nay so với hôm qua, sản phẩm bán chạy
- Đơn hàng: xác nhận, chuẩn bị hàng (hẹn lấy / gửi bưu cục), giao ĐVVC, cập nhật vận chuyển, hủy, xử lý yêu cầu hủy
- Sản phẩm: thêm / sửa (chụp ảnh, **AI viết mô tả**), kho theo phân loại, ẩn / hiện, xóa, gửi duyệt lại
- Chat với khách (**AI gợi ý câu trả lời**), **Trợ lý doanh số AI**, Hiệu quả hoạt động (điểm phạt, Shop Yêu thích)
- Tài khoản người mua chưa có shop có thể **mở shop ngay trong app**

Các chức năng còn lại (cài đặt shop, voucher, quảng cáo, báo cáo chi tiết, quản trị) dùng trên web.

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
    ├── common/             đăng nhập, chat, thông báo (dùng cho cả 2 app)
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
