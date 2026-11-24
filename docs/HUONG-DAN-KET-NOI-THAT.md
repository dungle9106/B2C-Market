# Hướng dẫn kết nối dịch vụ thật

Hướng dẫn này giúp Shoppi chạy với **thanh toán, giao hàng, email và đăng nhập mạng xã hội thật** (môi trường thử nghiệm
của từng hãng, miễn phí). Code đã có sẵn, bạn chỉ cần đăng ký tài khoản và dán khóa vào trang quản trị. Không cần sửa code hay
khởi động lại backend.

| Dịch vụ | Dùng để | Dán khóa ở | Cần địa chỉ công khai? |
| --- | --- | --- | --- |
| ngrok | Cho Internet gọi được vào backend trên máy bạn | `/admin/gateways` → *Địa chỉ backend công khai* | (chính là nó) |
| Gmail SMTP | Gửi email thật: mã OTP quên mật khẩu, xác thực email, đơn hàng | `/admin/email` | Không |
| Google, Facebook, GitHub | Đăng nhập / đăng ký bằng tài khoản mạng xã hội (web + 3 app) | `/admin/gateways` | Web chạy local: không. App điện thoại, Facebook: **có** |
| VNPay sandbox | Thanh toán thẻ ATM / QR thật (tiền thử) | `/admin/gateways` | Có (để VNPay gọi IPN) |
| GHN sandbox | Phí ship và vận đơn thật | `/admin/gateways` | Có (webhook trạng thái đơn) |
| GHTK staging | Phí ship và vận đơn thật | `/admin/gateways` | Có (webhook) |

Thứ tự nên làm: **ngrok → Gmail → Google → VNPay → GHN → Facebook / GitHub / GHTK**.

Đăng nhập trang quản trị: <http://localhost:5173/admin> bằng `admin@marketplace.local` / `Admin@123`.

---

## 1. ngrok: địa chỉ công khai cho backend

VNPay, GHN, GHTK cần gọi ngược vào backend; Google và Facebook chỉ chấp nhận địa chỉ `localhost` hoặc `https://` công khai
(không nhận `10.0.2.2` hay `192.168.x.x` của điện thoại). ngrok tạo một đường hầm `https://...` trỏ về cổng 8081 trên máy bạn.

1. Đăng ký miễn phí tại <https://dashboard.ngrok.com/signup>, tải ngrok cho Windows rồi giải nén.
2. Chạy lệnh kết nối tài khoản (lấy authtoken ở dashboard, mục *Your Authtoken*):
   ```
   ngrok config add-authtoken <authtoken>
   ```
3. **Nên dùng tên miền cố định miễn phí** để không phải khai báo lại mỗi lần: dashboard → *Domains* → *New Domain*
   (mỗi tài khoản free được 1 tên miền dạng `ten-cua-ban.ngrok-free.app`). Sau đó chạy:
   ```
   ngrok http --url=ten-cua-ban.ngrok-free.app 8081
   ```
   (Không có tên miền cố định thì chạy `ngrok http 8081`, mỗi lần chạy lại địa chỉ sẽ đổi.)
4. Mở `/admin/gateways` → mục **Đăng Nhập Bằng Mạng Xã Hội** → ô *Địa chỉ backend công khai* → dán
   `https://ten-cua-ban.ngrok-free.app` → **Lưu**. Trang này sẽ hiện sẵn các URL callback, IPN đúng để bạn chép sang từng hãng.

> Bản ngrok miễn phí hiện một trang cảnh báo *"You are about to visit..."* ở lần đầu mở bằng trình duyệt. Bấm **Visit Site**
> là xong. Các lệnh gọi giữa máy chủ với máy chủ (IPN, webhook) không bị chặn.

Khi đã lưu địa chỉ công khai, mọi lượt đăng nhập mạng xã hội (cả web) đều đi qua địa chỉ này, nên **ngrok phải đang chạy**.
Muốn quay lại chạy thuần local thì xóa trống ô đó và lưu.

---

## 2. Gmail SMTP: gửi email thật (mã OTP quên mật khẩu)

Chưa cấu hình SMTP thì email chỉ được lưu trong *Hộp thư đi* ở `/admin/email` (vẫn đọc được mã OTP ở đó khi demo).

1. Tài khoản Google phải **bật Xác minh 2 bước**: <https://myaccount.google.com/signinoptions/twosv>.
2. Tạo **Mật khẩu ứng dụng**: <https://myaccount.google.com/apppasswords> → đặt tên `Shoppi` → Google hiện 16 ký tự, chép lại.
3. `/admin/email` → nhập:
   - Máy chủ SMTP `smtp.gmail.com`, cổng `587`, chọn **STARTTLS**
   - Tên đăng nhập: địa chỉ Gmail của bạn; Mật khẩu ứng dụng: 16 ký tự vừa tạo (không phải mật khẩu Gmail)
   - Địa chỉ gửi: cùng địa chỉ Gmail; Tên người gửi: `Shoppi`
4. Bấm **Gửi email thử** tới email của bạn. Thử luồng thật: trang đăng nhập → *Quên mật khẩu?* → nhập email → nhận mã 6 số.

Gmail cá nhân gửi tối đa khoảng 500 email/ngày, đủ cho đồ án. Khi triển khai lớn hãy dùng dịch vụ như Brevo, SendGrid,
Amazon SES: chỉ cần đổi máy chủ SMTP trong cùng trang.

---

## 3. Đăng nhập bằng Google

1. Vào <https://console.cloud.google.com/> → tạo project mới (ví dụ `Shoppi`).
2. **APIs & Services → OAuth consent screen** (hoặc *Google Auth Platform → Branding*):
   - User type: **External**
   - App name `Shoppi`, email hỗ trợ, logo (không bắt buộc)
   - Authorized domains: `ngrok-free.app` (nếu dùng ngrok) hoặc tên miền của bạn
   - Scopes: `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile` (là các scope cơ bản, **không cần Google duyệt**)
3. **Credentials → Create credentials → OAuth client ID** → loại **Web application** → mục *Authorized redirect URIs* thêm:
   - `http://localhost:8081/login/oauth2/code/google` (chạy local)
   - `https://ten-cua-ban.ngrok-free.app/login/oauth2/code/google` (ngrok / app điện thoại)
4. Chép **Client ID** và **Client secret** → `/admin/gateways` → mục Google → dán → tích *Cho phép* → **Lưu**.
5. Nút **Tiếp tục với Google** xuất hiện ngay trên trang đăng nhập web và cả 3 app.

**Để mọi người đều đăng nhập được:** ở chế độ *Testing*, Google chỉ cho phép tối đa 100 email nằm trong danh sách *Test users*.
Vào *OAuth consent screen* → **Publish app** → *In production*. Vì chỉ dùng scope cơ bản (email, tên, ảnh) nên không phải qua
bước Google thẩm định. Google có thể yêu cầu xác minh quyền sở hữu tên miền nếu bạn dùng tên miền riêng.

---

## 4. Đăng nhập bằng Facebook

Facebook bắt buộc `https` cho callback (trừ `localhost` khi ứng dụng ở chế độ phát triển).

1. <https://developers.facebook.com/apps> → **Create app** → use case **Authenticate and request data from users with
   Facebook Login** → loại *Consumer* (nếu được hỏi).
2. **Use cases → Facebook Login → Settings** → *Valid OAuth Redirect URIs*:
   `https://ten-cua-ban.ngrok-free.app/login/oauth2/code/facebook`
3. **Use cases → Customize → Permissions**: thêm `email` (bên cạnh `public_profile` có sẵn).
4. **App settings → Basic**: chép **App ID** và **App secret**; điền *Privacy Policy URL* =
   `http://localhost:5173/policies/privacy` (khi triển khai: tên miền thật), *User data deletion* → *Data deletion instructions URL*
   (có thể dùng cùng trang chính sách).
5. Dán App ID / App secret vào `/admin/gateways` → mục Facebook → **Lưu**.
6. **Cho mọi người dùng:** đầu trang ứng dụng chuyển *App mode* từ **Development** sang **Live**. Ở chế độ Development chỉ
   tài khoản có vai trò trong app (*App roles → Roles*) mới đăng nhập được.

Facebook chỉ trả email đã xác minh. Tài khoản Facebook đăng ký bằng số điện thoại (không có email) sẽ nhận thông báo
"chưa có email đã xác minh", người dùng có thể đăng ký bằng email rồi liên kết Facebook sau.

---

## 5. Đăng nhập bằng GitHub

1. GitHub → **Settings → Developer settings → OAuth Apps → New OAuth App**:
   - Homepage URL: `http://localhost:5173` (hoặc tên miền thật)
   - **Authorization callback URL**: `https://ten-cua-ban.ngrok-free.app/login/oauth2/code/github`
     (GitHub chỉ cho **một** callback mỗi app; nếu muốn chạy không cần ngrok, tạo thêm một OAuth App thứ hai với
     `http://localhost:8081/login/oauth2/code/github`)
2. **Generate a new client secret** → chép Client ID và secret vào `/admin/gateways` → mục GitHub → **Lưu**.

GitHub không giới hạn số người dùng, không cần duyệt. Shoppi đọc email *primary, verified* từ `/user/emails` nên kể cả khi
người dùng ẩn email trên hồ sơ GitHub vẫn đăng nhập được.

---

## 6. Liên kết nhiều tài khoản, quên mật khẩu

- Lần đầu đăng nhập bằng Google / Facebook / GitHub: nếu email chưa có trên Shoppi thì tài khoản **Người mua** mới được tạo
  (email đã xác minh, chưa có mật khẩu). Nếu email đã có thì lượt đăng nhập được gắn vào tài khoản đó.
- **Bảo mật tài khoản → Tài khoản liên kết** (web: `/account/security`, `/seller/security`, `/admin/security`; app: *Tài khoản
  → Bảo mật tài khoản*): liên kết thêm hoặc gỡ từng nhà cung cấp. Một tài khoản Shoppi gắn được cả 3; không thể gỡ cách đăng nhập
  cuối cùng khi tài khoản chưa có mật khẩu.
- **Quên mật khẩu** (web và app): nhập email → nhận **mã 6 số** (30 phút, sai 5 lần phải xin mã mới) → đặt mật khẩu mới.
  Email cũng có nút đặt lại mật khẩu bằng liên kết. Tài khoản tạo bằng mạng xã hội dùng cách này (hoặc *Tạo mật khẩu* trong
  trang Bảo mật) để có thêm mật khẩu Shoppi.

---

## 7. VNPay sandbox (thanh toán)

1. Đăng ký tại <https://sandbox.vnpayment.vn/devreg/>. VNPay gửi email gồm **vnp_TmnCode**, **vnp_HashSecret** và tài khoản
   đăng nhập trang quản trị merchant <https://sandbox.vnpayment.vn/merchantv2/>.
2. `/admin/gateways` → **Cổng Thanh Toán VNPay** → nhập TMN code, Hash secret, tích *Cho phép* → **Lưu**. Để trống hai ô URL
   (mặc định là sandbox).
3. Trang merchant VNPay → *Cấu hình* → khai báo **IPN URL** đúng như trang admin của Shoppi hiển thị:
   `https://ten-cua-ban.ngrok-free.app/api/payments/vnpay/ipn`.
4. Thử: đặt hàng → chọn **VNPay** → thẻ test ngân hàng **NCB** `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`,
   OTP `123456`.

Không có ngrok vẫn thanh toán được: Shoppi ghi nhận kết quả qua return URL (có kiểm tra chữ ký). IPN giúp ghi nhận cả khi người
mua đóng trình duyệt trước khi quay về.

**Lên môi trường thật**: ký hợp đồng với VNPay (cần giấy phép kinh doanh), VNPay cấp TMN code / secret production. Thay vào
trang admin và đổi URL thanh toán thành `https://pay.vnpay.vn/vpcpay.html`.

---

## 8. GHN sandbox (giao hàng)

1. Đăng ký tại <https://5sao.ghn.dev> (môi trường thử nghiệm) → đăng nhập → tạo **cửa hàng** với địa chỉ lấy hàng.
2. Lấy **Token**: menu tài khoản → *Chủ cửa hàng* → *Xem* token API. Lấy **ShopId** ở danh sách cửa hàng.
3. `/admin/gateways` → mục **GHN** → dán Token, ShopId, tích *Dùng GHN thật* → **Lưu** → **Kiểm tra kết nối**.
4. Webhook: trong trang GHN mục *Cấu hình webhook* (hoặc gửi cho bộ phận hỗ trợ GHN) điền đúng URL trang admin hiển thị,
   dạng `https://ten-cua-ban.ngrok-free.app/api/shipping/ghn/webhook?key=...`.
5. Người bán vào **Hồ sơ shop** nhập đủ *Quận/Huyện* và *Phường/Xã* lấy hàng, chọn GHN trong *Chính sách shop*. Đặt thử một
   đơn chọn GHN → người bán bấm *Chuẩn bị hàng* → đơn có mã vận đơn GHN thật, in phiếu được ngay.

GHTK: cần liên hệ GHTK để được cấp tài khoản staging (`services-staging.ghtklab.com`), sau đó dán Token + mã đối tác ở mục GHTK
trong cùng trang, cách làm như GHN.

---

## 9. App điện thoại với dịch vụ thật

- Đăng nhập bằng Google / Facebook / GitHub trên app mở trình duyệt của điện thoại, xong tự quay lại app
  (`shoppi://`, `shoppiseller://`, `shoppiadmin://`). Cần **địa chỉ công khai** (mục 1) vì điện thoại không gọi được
  `localhost` của máy tính.
- Thử bằng **Expo Go**: Expo Go dùng địa chỉ `exp://...`, cần cho phép thêm bằng biến môi trường trước khi chạy backend:
  `OAUTH2_APP_SCHEMES=shoppi,shoppiseller,shoppiadmin,exp`. Bản APK không cần.
- Ở màn hình đăng nhập app, *⚙ Máy chủ* có thể đặt luôn `https://ten-cua-ban.ngrok-free.app`: điện thoại dùng 4G cũng vào được.

---

## 10. Kiểm tra nhanh sau khi cấu hình

- [ ] `/admin/email` → *Gửi email thử* nhận được email
- [ ] Quên mật khẩu → nhận mã 6 số → đặt mật khẩu mới → đăng nhập được
- [ ] Trang đăng nhập có nút Google (và Facebook / GitHub nếu đã cấu hình) → đăng nhập được, tạo tài khoản mới
- [ ] Bảo mật tài khoản → *Liên kết* GitHub cho một tài khoản có sẵn → đăng xuất → đăng nhập lại bằng GitHub
- [ ] Đặt hàng VNPay bằng thẻ NCB test → đơn chuyển *Đã thanh toán*
- [ ] Đơn GHN → *Chuẩn bị hàng* → có mã vận đơn GHN → *In phiếu*

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Google báo `redirect_uri_mismatch` | Callback chưa khai báo đúng | Chép nguyên văn *Callback URL* ở `/admin/gateways` vào Google Console |
| Nút Google không hiện | Chưa đủ Client ID + secret, hoặc chưa tích *Cho phép* | Kiểm tra trạng thái "● Đang hoạt động" trong `/admin/gateways` |
| "Đăng nhập bằng X chưa được bật trên Shoppi" | Nhà cung cấp chưa có khóa | Như trên |
| Đăng nhập Google xong báo "thất bại hoặc đã bị hủy" | ngrok đã tắt / đổi địa chỉ, hoặc người dùng bấm Hủy | Chạy lại ngrok, cập nhật địa chỉ công khai |
| Facebook: "URL bị chặn" | Thiếu Valid OAuth Redirect URI hoặc dùng `http` | Dùng địa chỉ ngrok `https` |
| Người dùng khác không đăng nhập Google được | App Google còn ở chế độ Testing | Publish app (mục 3) |
| Không nhận được email | Chưa cấu hình SMTP / dùng mật khẩu Gmail thường | Dùng *Mật khẩu ứng dụng* (mục 2); xem lỗi ở *Hộp thư đi* |
| VNPay báo "Sai chữ ký" | Hash secret dán thiếu / thừa ký tự | Dán lại hash secret |
