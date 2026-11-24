# Shoppi Mobile – App Người mua, Người bán & Quản trị

Ba app Android viết bằng **React Native (Expo SDK 54)**, dùng chung một mã nguồn và gọi cùng backend Spring Boot (`/api`) với bản web.

| App | Package | Đối tượng |
|---|---|---|
| **Shoppi** | `vn.shoppi.buyer` | Người mua |
| **Shoppi Người Bán** | `vn.shoppi.seller` | Người bán (chủ shop) |
| **Shoppi Admin** | `vn.shoppi.admin` | Nhân viên quản trị sàn |

Biến môi trường `APP_VARIANT` (`buyer` / `seller` / `admin`) lúc build quyết định ra app nào ([app.config.js](app.config.js)). Mỗi app có package và URL scheme riêng (`shoppi://`, `shoppiseller://`, `shoppiadmin://`) nên cài được cùng lúc trên một máy.

**Chung cho cả 3 app**: đăng nhập email/mật khẩu hoặc **Tiếp tục với Google / Facebook / GitHub** (mở trình duyệt hệ thống, xong tự quay lại app), **quên mật khẩu bằng mã OTP 6 số**, **Bảo mật tài khoản** (2FA, thiết bị, lịch sử đăng nhập, **tài khoản liên kết**), thông báo đẩy.

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
- Tổng quan, Đơn hàng (**chọn nhiều đơn: xác nhận / chuẩn bị hàng / bàn giao ĐVVC hàng loạt**, **in phiếu giao hàng A6 có mã vạch** qua hộp thoại in của điện thoại hoặc lưu PDF), Sản phẩm (thêm / sửa, **AI viết mô tả**, **AI gợi ý danh mục từ ảnh**, **AI gợi ý giá**, nhân bản, ẩn / hiện, **thao tác hàng loạt: ẩn, hiện, hết hàng, xóa**, **nhập từ Excel** (kiểm tra trước rồi mới đăng), **tải file mẫu, xuất danh sách Excel** để chia sẻ), Chat (**AI gợi ý câu trả lời**)
- Chăm sóc khách hàng: trả hàng/hoàn tiền (kèm trao đổi khiếu nại), trả lời đánh giá, **hỏi đáp sản phẩm** (AI gợi ý), **tin nhắn tự động & mẫu trả lời**
- Kênh Marketing: **mã giảm giá của shop**, **Flash Sale của shop**, **đăng ký Shoppi Flash Sale**, **combo**, **mua kèm deal sốc / quà tặng**, **quảng cáo** (ví quảng cáo, giá thầu, từ khóa), **Shoppi Video**
- Báo cáo & AI: **phân tích doanh thu / sản phẩm / lượt xem / đánh giá**, **doanh thu & đối soát**, hiệu quả hoạt động, trợ lý bán hàng AI, **AI phân tích đánh giá**, **AI gợi ý thời điểm khuyến mãi**, **tồn kho** (điều chỉnh, lịch sử), dự báo nhập hàng AI
- Quản lý shop: **hồ sơ shop**, **danh mục của shop**, **chính sách đổi trả & vận chuyển**, **xác minh shop**; bảo mật tài khoản, hỗ trợ từ Shoppi
- Tài khoản người mua chưa có shop có thể **mở shop ngay trong app**

**App Shoppi Admin** (chỉ tài khoản quản trị; mỗi mục chỉ hiện khi nhân viên có quyền tương ứng, giống web)
- Tổng quan: việc cần xử lý (shop / sản phẩm chờ duyệt, báo cáo vi phạm, khiếu nại, hoàn tiền, tài khoản bị khóa), GMV, đơn hàng, hoa hồng 30 ngày so với kỳ trước, biểu đồ, ngành hàng bán chạy
- Kiểm duyệt: **duyệt / từ chối / đình chỉ / mở lại shop**, Shoppi Mall, **xác minh shop** (xem giấy tờ), **kiểm duyệt sản phẩm** (duyệt, từ chối, gỡ vi phạm, khôi phục, bỏ qua báo cáo, **duyệt hàng loạt**)
- Đơn hàng toàn sàn: lọc theo trạng thái / trễ hạn, tìm kiếm, chi tiết, hủy đơn, hoàn tiền thủ công
- Người dùng: lọc theo vai trò / trạng thái, chi tiết mua hàng và shop, **khóa (7 ngày / 30 ngày / vĩnh viễn) / mở khóa**
- **Khiếu nại trả hàng** (trao đổi 3 bên, quyết định hoàn toàn bộ / một phần / từ chối), **hoàn tiền** (xác nhận đã chuyển khoản / thất bại), **yêu cầu hỗ trợ** (trả lời, đổi trạng thái)
- Nếu sàn bắt buộc admin bật xác thực 2 lớp, app chỉ mở màn hình bật 2FA cho tới khi bật xong

Chỉ dùng trên web: cấu hình sàn, nhân viên & phân quyền, banner, chiến dịch, voucher sàn, Flash Sale sàn, đối soát, báo cáo Excel toàn sàn, giám sát hệ thống.

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
    ├── seller/             màn hình app Người bán (labels.js: in phiếu giao hàng)
    └── admin/              màn hình app Quản trị
```

## Chạy thử khi phát triển

```bash
cd mobile
npm install
npx expo start            # app Người mua; quét QR bằng Expo Go hoặc bấm "a" để mở máy ảo
```

App Người bán (PowerShell): `$env:APP_VARIANT="seller"; npx expo start` · App Admin: `$env:APP_VARIANT="admin"; npx expo start`

Đăng nhập Google / Facebook / GitHub trên điện thoại cần backend có địa chỉ `https` công khai (ngrok), xem [docs/HUONG-DAN-KET-NOI-THAT.md](../docs/HUONG-DAN-KET-NOI-THAT.md). Trong Expo Go, chạy backend với `OAUTH2_APP_SCHEMES=shoppi,shoppiseller,shoppiadmin,exp`.

## Build file APK

Cần Android SDK (`ANDROID_HOME`, mặc định `D:\Android\SDK`) và JDK 17+ (`JAVA_HOME`).

```bash
npm run build:buyer       # -> dist/Shoppi-NguoiMua.apk
npm run build:seller      # -> dist/Shoppi-NguoiBan.apk
npm run build:admin       # -> dist/Shoppi-Admin.apk
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
