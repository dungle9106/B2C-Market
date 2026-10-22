# Shoppi — Sàn thương mại điện tử đa người bán tích hợp AI

Đồ án chuyên ngành CNTT — Nhóm 04. Backend **Java Spring Boot**, frontend **Vue.js 3**, cơ sở dữ liệu
**PostgreSQL**, AI **OpenAI API (RAG)**.

## Cấu trúc thư mục

```
project/
├── backend/             Spring Boot REST API + WebSocket (Dockerfile)
├── frontend/            Vue.js 3 SPA - Vite, Tailwind, Chart.js (Dockerfile, nginx.conf, vercel.json)
├── ai-finetune/         Fine-tuning Vistral-7B + LoRA (phần "tự train AI")
├── review-ai-service/   FastAPI: phân loại đánh giá, mô hình huấn luyện được (scikit-learn)
├── docs/                ERD.md (sơ đồ CSDL), postman/ (bộ request API)
├── docker-compose.yml   Chạy cả hệ thống: PostgreSQL + pgvector, Redis, RabbitMQ, backend, frontend
├── render.yaml          Triển khai backend + Redis lên Render
└── .github/workflows/   CI: build backend, frontend, Docker image
```

## Công nghệ sử dụng

| Nhóm | Công nghệ | Dùng ở đâu |
| --- | --- | --- |
| Frontend | Vue 3 (Composition API), Vue Router, Pinia, Axios | `frontend/src` (store: `auth`, `cart`, `notifications`; `api/http.js` tự làm mới token) |
| | Tailwind CSS | tiền tố `tw-`, không preflight, dùng song song CSS sẵn có (`tailwind.config.js`) |
| | Chart.js | `components/analytics/TrendChart.vue`: mọi biểu đồ của seller/admin |
| | WebSocket client `@stomp/stompjs` | `realtime/socket.js`: chat và thông báo tức thì |
| Backend | Spring Boot 3, Spring Web, Spring Data JPA/Hibernate, Bean Validation, Maven | `backend/` |
| | Spring Security + JWT, OAuth2 Google | `security/`, `config/SecurityConfig.java` |
| | WebSocket (STOMP) | `realtime/` |
| | WebClient | gọi OpenAI, VNPay, GHN |
| | Spring Scheduler | hủy đơn quá hạn, gửi email, tạo vector sản phẩm, flash sale... |
| Dữ liệu | PostgreSQL, **pgvector** | `service/search/EmbeddingStore.java` (tự dùng `real[]` khi chưa có pgvector) |
| | **Redis** | `config/CacheConfig.java` (`CACHE_TYPE=redis`; mặc định cache trong bộ nhớ) |
| Hàng đợi | **RabbitMQ** | `messaging/` - hàng đợi email (`RABBITMQ_ENABLED=true`; mặc định hàng đợi trong CSDL) |
| AI | OpenAI API, **Claude API**, RAG, Prompt Engineering, embeddings | `AIService`, `service/ai/` (OpenAI, Claude, Hugging Face), `ChatbotService`, `service/search/` |
| | **FastAPI** + scikit-learn | `review-ai-service/`: phân loại đánh giá, huấn luyện mô hình (Trung tâm đánh giá) |
| Thanh toán | Mock payment, VNPay Sandbox | `PaymentService`, `VnpayService` |
| Vận chuyển | GHN API | `GhnService`, `GhnWebhookService` |
| Triển khai | Docker, Vercel/Netlify, Render, Neon/Supabase, GitHub Actions | xem mục *Chạy bằng Docker* và *Triển khai* |
| Công cụ | Postman, draw.io (ERD) | `docs/postman/`, `docs/ERD.md` |

Redis, RabbitMQ và pgvector đều **tùy chọn khi chạy local**: không cài cũng chạy được (có phương án dự phòng),
`docker compose` thì bật đủ cả ba.

## Chạy nhanh bằng Docker

```bash
cp .env.example .env          # điền OPENAI_API_KEY, MAIL_*, VNPAY_*, GHN_* nếu có
docker compose up --build     # http://localhost:3000  (RabbitMQ console: http://localhost:15672)
```

Gồm PostgreSQL 16 + pgvector, Redis 7, RabbitMQ 3.13, backend (Spring Boot) và frontend (nginx, chuyển
`/api`, `/ws`, `/uploads` sang backend). Dữ liệu và ảnh tải lên nằm trong volume `db-data`, `uploads`.

## Chạy backend (không dùng Docker)

Yêu cầu: JDK 17+, Maven, PostgreSQL.

```bash
# 1. Tạo database
createdb marketplace

# 2. Cấu hình: biến môi trường DATABASE_URL / DATABASE_USERNAME / DATABASE_PASSWORD
#    (mặc định jdbc:postgresql://localhost:5433/marketplace, postgres / 123456)

# 3. Đặt API key OpenAI (để chạy được chatbot AI + gợi ý mô tả + tìm kiếm ngữ nghĩa)
export OPENAI_API_KEY=sk-xxxxxxxx

# 4. (Tùy chọn) Redis và RabbitMQ
export CACHE_TYPE=redis REDIS_URL=redis://localhost:6379
export RABBITMQ_ENABLED=true RABBITMQ_URL=amqp://guest:guest@localhost:5672

# 5. Chạy
cd backend
mvn spring-boot:run
```

Backend chạy ở `http://localhost:8081` (biến `PORT` để đổi; không dùng 8080 vì máy dev hay bị dịch vụ khác chiếm). Lần chạy đầu tiên tự tạo tài khoản admin:
`admin@marketplace.local / Admin@123`, và vài danh mục mẫu.

## Triển khai (miễn phí)

1. **CSDL – Neon** (hoặc Supabase): tạo project PostgreSQL, bật `CREATE EXTENSION vector;` (cả hai đều có sẵn).
   Lấy chuỗi kết nối dạng `jdbc:postgresql://<host>/<db>?sslmode=require`.
2. **Backend – Render**: *New → Blueprint*, chọn repo (đọc `render.yaml`): tạo web service Docker từ `backend/`
   và Redis. Điền `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `FRONTEND_URL`,
   `CORS_ALLOWED_ORIGINS` (URL Vercel), `OPENAI_API_KEY`... `JWT_SECRET` được sinh tự động; **đừng đổi về sau**
   vì nó cũng là khóa mã hóa các API key đã lưu trong CSDL. RabbitMQ (tùy chọn): gói miễn phí CloudAMQP →
   `RABBITMQ_ENABLED=true`, `RABBITMQ_URL=amqps://...`.
3. **Frontend – Vercel** (hoặc Netlify): import repo, *Root Directory* = `frontend`, biến
   `VITE_API_BASE_URL=https://<backend>.onrender.com/api` (WebSocket tự suy ra `wss://<backend>/ws`).
4. **Google OAuth / VNPay IPN / GHN webhook**: khai báo lại bằng địa chỉ Render.
5. **CI**: mỗi lần push, GitHub Actions build backend, frontend và hai Docker image (`.github/workflows/ci.yml`).

Lưu ý: ổ đĩa của Render gói miễn phí không bền, ảnh tải lên mất khi deploy lại. Để demo lâu dài, gắn
*Persistent Disk* vào `/app/uploads`.

## Tính năng

### Duyệt & tìm kiếm sản phẩm

- **Trang chủ**: Flash Sale (đếm ngược), danh mục, sản phẩm bán chạy, "Gợi ý hôm nay" theo lịch sử xem
  (người mua đã đăng nhập) hoặc sản phẩm mới (khách), nút "Xem thêm"
- **Danh mục 2 cấp**: danh mục con được tạo sẵn lần chạy đầu; xem danh mục cha sẽ gồm cả sản phẩm của danh mục con
- **Tìm kiếm** `GET /api/products/search`: không phân biệt dấu ("ao thun" khớp "Áo thun"), gợi ý từ khóa khi gõ
  (`/api/products/suggest`), lọc theo giá / đánh giá / nơi bán (tỉnh của shop) / freeship / đang giảm giá,
  sắp xếp liên quan / mới nhất / bán chạy / giá / đánh giá, có phân trang
- **Chi tiết sản phẩm**: nhiều ảnh + video, phân loại (size/màu) có kho riêng, giá gốc, flash sale, freeship,
  chính sách đổi trả, hỏi đáp công khai (shop trả lời trong Kênh Người Bán), đánh giá kèm ảnh thực tế
  (chỉ người đã nhận hàng mới đánh giá được, mỗi sản phẩm trong đơn một lần)
- **Upload ảnh/video**: `POST /api/uploads`, lưu ở thư mục `app.upload.dir` (mặc định `uploads/` trong thư mục
  chạy backend, đổi bằng biến `UPLOAD_DIR`). Chỉ nhận JPG/PNG/WEBP/GIF ≤ 5MB và MP4/WEBM ≤ 30MB.

### Giỏ hàng & Đặt hàng

- **Giỏ hàng**: chọn theo sản phẩm / theo shop / tất cả, sửa số lượng (giới hạn theo tồn kho), xóa nhiều,
  sản phẩm hết hàng hoặc ngừng bán hiển thị mờ và không chọn được
- **Đặt hàng đa shop** `POST /api/orders/checkout`: một lần đặt tạo một đơn cho mỗi shop (chung `checkoutCode`);
  `POST /api/orders/preview` trả về toàn bộ tiền hàng, phí ship, giảm giá trước khi đặt (cùng code tính giá với lúc đặt)
- **Voucher**: của shop (người bán tạo trong Kênh Người Bán → Mã Giảm Giá) hoặc toàn sàn (admin → Shoppi Voucher);
  3 loại: giảm %, giảm tiền, giảm phí ship; đơn tối thiểu, tổng lượt, lượt/người, thời gian. Voucher sàn được chia
  cho các shop theo tỷ lệ giá trị. Lần chạy đầu tạo sẵn `SHOPPI10` và `FREESHIP15`.
- **Vận chuyển** (mô phỏng): GHN, GHTK, J&T, Viettel Post × Tiết kiệm / Nhanh / Hỏa tốc (chỉ cùng tỉnh); phí theo
  cùng/khác tỉnh giữa shop và người nhận. Sản phẩm Freeship được miễn tối đa bằng phí gói Tiết kiệm.
- **Thanh toán**: COD, ví điện tử (MoMo/ZaloPay/VNPay), thẻ, chuyển khoản — các cổng online là **trang mô phỏng**
  (`/payment/:code`, thẻ thử `4111 1111 1111 1111`, thẻ bị từ chối `4000 0000 0000 0002`). Đơn online chưa thanh toán
  sau 15 phút tự hủy và hoàn kho. Hủy đơn luôn hoàn kho và trả lại lượt voucher.

### Quản lý đơn hàng (người mua)

- **Danh sách theo trạng thái**: Chờ thanh toán, Chờ xác nhận, Chờ lấy hàng, Đang giao, Hoàn thành, Đã hủy,
  Trả hàng/Hoàn tiền; tìm theo mã đơn, shop, tên sản phẩm. **Chi tiết đơn** `/orders/:id`: tiến trình 5 bước,
  địa chỉ, chi tiết tiền, thanh toán
- **Theo dõi vận chuyển**: khi shop giao cho ĐVVC sẽ có **mã vận đơn**; các mốc quét kiện (bưu cục, trung chuyển,
  kho đích, đang giao) được **mô phỏng** tự động, mỗi mốc cách nhau `app.shipping.sim-step-seconds` (mặc định 60 giây)
- **Hủy đơn** (kèm lý do) khi chưa giao cho ĐVVC; hoàn kho và trả lại voucher. Người mua bấm "Đã nhận được hàng".
- **Trả hàng/Hoàn tiền** trong thời hạn đổi trả của sản phẩm: chọn sản phẩm + số lượng, lý do, ảnh/video bằng chứng.
  Shop đồng ý → hoàn tiền (hàng trả về được cộng lại kho) hoặc từ chối → người mua **khiếu nại** → admin quyết định
  (Admin → Khiếu nại). Việc hoàn tiền là mô phỏng (chỉ cập nhật trạng thái).
- **Đánh giá** sau khi nhận hàng: sao + nhận xét + tối đa 6 ảnh và 1 video; kèm chấm sao dịch vụ shop và tốc độ giao
  hàng (hiện trên trang sản phẩm)
- **Mua lại**: đưa các sản phẩm còn bán của đơn cũ vào giỏ (đã chọn sẵn)

### Người bán: Đăng ký & Hồ sơ Shop

- **Đăng ký** 2 bước (`/seller/register`): tạo tài khoản → thông tin shop (tên **không trùng** shop khác, tỉnh,
  địa chỉ + **SĐT lấy hàng**). Trang Tổng quan có checklist **"Hoàn thiện Shop"** (logo/ảnh bìa, giới thiệu,
  điều kiện đổi trả, sản phẩm đầu tiên, xác minh) và tự ẩn khi đủ.
- **Hồ sơ Shop** (`/seller/shop`): tải logo và ảnh bìa trực tiếp (hoặc dán link), mô tả ≤ 1000 ký tự, địa chỉ lấy
  hàng. Người mua xem **trang Shop công khai** `/shop/:id`: ảnh bìa, logo, huy hiệu xác minh, số sản phẩm, đánh giá,
  ngày tham gia, chính sách và toàn bộ sản phẩm (có lọc/sắp xếp). Địa chỉ và SĐT lấy hàng không bao giờ công khai.
- **Chính sách Shop** (`/seller/policies`, `PUT /api/seller/shop/policies`):
  - *Đổi trả*: thời hạn mặc định (0/3/7/15/30 ngày) dùng cho sản phẩm mới, tùy chọn áp dụng cho mọi sản phẩm hiện có;
    điều kiện đổi trả hiển thị trên trang shop và trang sản phẩm.
  - *Vận chuyển*: bật/tắt từng đơn vị (GHTK, GHN, J&T, Viettel Post) — checkout chỉ đưa ra các đơn vị shop bật;
    thời gian chuẩn bị hàng 1–3 ngày (cộng vào ngày giao dự kiến); **miễn phí vận chuyển cho đơn từ X** (shop trả tối
    đa bằng phí gói Tiết kiệm, checkout hiện "Mua thêm ... để được miễn phí vận chuyển").
- **Xác minh thông tin** (tùy chọn, `/seller/verification`): chọn loại hình Cá nhân / Hộ kinh doanh / Doanh nghiệp,
  khai CCCD (12 số), mã số thuế, số GCN ĐKKD, địa chỉ kinh doanh, tải ảnh giấy tờ (2 mặt CCCD, + giấy phép với hộ
  KD/doanh nghiệp). Admin duyệt ở `/admin/verifications` (duyệt hoặc từ chối kèm lý do, có thông báo hai chiều);
  shop được duyệt có huy hiệu **"Đã xác minh"** trên trang shop và trang sản phẩm. Số CCCD chỉ hiện đầy đủ cho admin.
  Giấy tờ do người duyệt kiểm tra bằng mắt, không đối chiếu với cơ sở dữ liệu nhà nước. *Lưu ý*: ảnh giấy tờ đang
  lưu chung thư mục `uploads/` (tên file ngẫu nhiên, ai có link vẫn mở được) — khi triển khai thật nên tách sang kho
  riêng có kiểm soát quyền truy cập.

### Người bán: Quản lý sản phẩm

- **Phân loại 2 tầng** kiểu Shopee (VD Màu sắc × Kích cỡ): khai báo nhóm và giá trị → bảng tổ hợp tự sinh, mỗi
  tổ hợp có giá cộng thêm, kho, SKU; ảnh riêng cho từng màu; "áp dụng cho tất cả" và "tạo SKU tự động". Đổi tên
  giá trị vẫn giữ nguyên phân loại (giỏ hàng không bị mất). Người mua chọn theo từng nhóm, tổ hợp hết hàng bị làm mờ,
  ảnh chính đổi theo màu đã chọn. Sản phẩm cũ (phân loại dạng tên tự do) vẫn hoạt động.
- **SKU** sản phẩm (không trùng trong shop) và SKU phân loại; chỉ người bán thấy.
- **Danh mục của Shop** (`/seller/categories`): tạo/đổi tên/xóa/sắp xếp, chọn sản phẩm cho từng danh mục (một sản
  phẩm thuộc được nhiều danh mục); hiển thị thành tab trên trang Shop (`/shop/:id?cat=`).
- **Danh sách sản phẩm**: tab Sắp hết hàng (≤ 5), lọc theo danh mục shop, tìm theo tên/SKU, sắp xếp; sửa kho ngay
  trên bảng (kể cả từng phân loại); **chọn nhiều** để Ẩn / Hiện / Đánh dấu hết hàng / Thêm–gỡ danh mục / Xóa (sản
  phẩm đã có đơn chỉ ẩn được và được báo lại); Sao chép sản phẩm (bản sao ở trạng thái ẩn).
- **Đăng hàng loạt bằng Excel** (`/seller/products/import`, thư viện Apache POI): tải file mẫu (sheet Hướng dẫn,
  Ví dụ, Ngành hàng; các dòng cùng *Mã SP* là phân loại của một sản phẩm) → **kiểm tra trước** báo lỗi theo số dòng
  → đăng. Tối đa 200 sản phẩm / 1000 dòng; chỉ đăng khi cả file hợp lệ. **Xuất Excel** toàn bộ sản phẩm cùng định dạng.

### Người bán: Quản lý đơn hàng & Vận hành

- **Danh sách đơn** (`/seller/orders`): tab theo trạng thái (Chờ lấy hàng tách *Chưa xử lý / Đã xử lý*, tab
  *Yêu cầu hủy*), lọc ngày đặt / thanh toán / đơn vị vận chuyển, tìm theo mã đơn, mã vận đơn, tên người mua, sản
  phẩm; tự làm mới 30 giây (đơn mới được tô sáng). Chọn nhiều để **Xác nhận / Chuẩn bị hàng / In phiếu / Giao cho
  ĐVVC**. Trang chi tiết `/seller/orders/:id` có hành trình đơn, người nhận, tiền.
- **Chuẩn bị hàng**: chọn *lấy hàng tận nơi* hoặc *gửi tại bưu cục* và ngày → cấp mã vận đơn.
  **In phiếu giao hàng** (`/seller/print-labels?ids=`): khổ A6, mã vạch Code128 của mã vận đơn và mã đơn (tự sinh,
  không dùng thư viện), người gửi/nhận, nội dung hàng, tiền thu hộ COD.
- **Cập nhật vận chuyển**: người bán thêm mốc *đang trung chuyển / đang giao / giao không thành công (kèm lý do)*;
  người mua thấy trong hành trình và nhận thông báo.
- **Yêu cầu hủy**: trước khi shop xác nhận, người mua tự hủy; sau đó chỉ **gửi yêu cầu hủy** (1 lần) → shop đồng ý
  (hủy, hoàn kho) hoặc từ chối kèm lý do; bàn giao cho ĐVVC sẽ tự từ chối yêu cầu đang chờ. Trả hàng/hoàn tiền giữ
  luồng cũ.
- **Tồn kho thời gian thực** (`/seller/inventory`): trừ/hoàn kho bằng câu lệnh `UPDATE ... WHERE stock >= :qty` nguyên
  tử nên nhiều người đặt cùng lúc không bán vượt kho (`Product`/`ProductVariant` dùng `@DynamicUpdate`); mọi biến động
  (đặt hàng, hủy, trả hàng, sửa, nhập Excel, điều chỉnh) ghi vào **thẻ kho** (`inventory_logs`); điều chỉnh
  nhập hàng / xuất bớt / kiểm kho; cột *Chờ giao*; cảnh báo sắp hết (≤ 5) và hết hàng qua thông báo; trang tự cập
  nhật 10 giây và tô sáng ô vừa thay đổi.

### Người bán: Marketing & Khuyến mãi

- **Voucher của shop** (`/seller/vouchers`): giảm tiền / % (có mức tối đa) / phí vận chuyển; áp dụng toàn shop hoặc
  một số sản phẩm; voucher **công khai** (hiện trên trang shop, trang sản phẩm, bước thanh toán, có thông báo cho người
  theo dõi) hoặc **ẩn** (chỉ ai có mã). Sửa được trước khi bắt đầu; đang chạy chỉ đổi giờ kết thúc, số lượt, công
  khai/ẩn. Thống kê số đơn, tiền đã giảm, doanh số. Người mua bấm vào vé để sao chép mã.
- **Flash Sale của shop** (`/seller/flash-sale`): chọn khung giờ (00–12, 12–18, 18–21, 21–24 hoặc tùy chọn, tối đa 7
  ngày), giảm % hàng loạt hoặc nhập giá từng sản phẩm, **số suất** và **giới hạn mỗi người mua**. Trừ suất nguyên tử
  khi đặt (`UPDATE ... WHERE flash_sale_sold + :qty <= flash_sale_stock`), hủy đơn thì hoàn suất; hết suất → về giá gốc.
  Trang sản phẩm có thanh "Đã bán x/y suất".
- **Combo khuyến mãi** (`/seller/combos`): "mua nhiều giảm nhiều" theo bậc (VD mua 2 giảm 5%, mua 3 giảm 10%) trên một
  nhóm sản phẩm; tự áp dụng ở bước thanh toán, gợi ý "Mua thêm N sản phẩm để được giảm ...". Một sản phẩm không thuộc
  2 combo chạy trùng thời gian. Voucher shop tính trên số tiền đã trừ combo.
- **Quảng cáo tìm kiếm** (`/seller/ads`): nạp tiền vào **ví quảng cáo** (mô phỏng, không trừ tiền thật), tạo chiến dịch
  cho sản phẩm với từ khóa, **giá thầu/click** (500đ–50.000đ) và ngân sách ngày. Khi người mua tìm đúng từ khóa (trang 1,
  sắp xếp liên quan, không lọc), tối đa 2 sản phẩm hiện đầu kết quả với nhãn **"Tài trợ"**, xếp theo giá thầu (bằng
  nhau thì xoay vòng ngẫu nhiên). Trả tiền theo click; cùng một khách click lại trong 30 phút, hoặc shop tự click, không
  bị tính. Báo cáo hiển thị / click / CTR / chi phí, đơn và doanh thu quy cho quảng cáo (7 ngày sau click), ROAS; thông
  báo khi số dư không đủ.

### Người bán: Thống kê & Báo cáo

Menu **Dữ Liệu** (`/seller/analytics/...`): 4 trang dùng chung bộ chọn kỳ (7/30 ngày, tháng này, tháng trước, 12 tuần,
12 tháng hoặc tự chọn, tối đa 366 ngày) và kiểu xem **ngày / tuần / tháng**. Kỳ được lưu trên URL. Mọi chỉ số đều so
với **kỳ trước cùng độ dài**. Biểu đồ vẽ bằng SVG, không dùng thư viện. Nút **Xuất báo cáo Excel** tải về một file
5 sheet (Tổng quan, Doanh thu, Sản phẩm, Lượt truy cập, Đánh giá).

- **Doanh thu**: doanh số, số đơn, giá trị trung bình mỗi đơn, sản phẩm đã bán, người mua, tỷ lệ hủy/hoàn. Có biểu đồ
  theo ngày/tuần/tháng, bảng chi tiết và đơn theo trạng thái. Đơn hủy, hoàn tiền hoặc chưa thanh toán không tính là
  doanh số (giống trang Tổng quan).
- **Sản phẩm & Tồn kho**: top 10 bán chạy, ô tồn kho (tổng, giá trị theo giá bán, hết hàng, sắp hết ≤ 5, bán chậm,
  sẽ hết trong 7 ngày). Bảng từng sản phẩm gồm lượt truy cập, thêm giỏ, chuyển đổi, đã bán, doanh số, tồn kho kèm
  "đủ bán N ngày" (tồn kho chia tốc độ bán trong kỳ) và đánh giá. Có thể sắp xếp, lọc và tìm kiếm.
- **Lượt xem & Chuyển đổi**: hệ thống ghi lại lượt xem trang sản phẩm (cả khách chưa đăng nhập) và lượt thêm vào giỏ
  vào bảng `product_events`. Mỗi khách (user id, hoặc IP + trình duyệt) chỉ được tính 1 lần cho mỗi sản phẩm trong
  30 phút; lượt xem của chính chủ shop không được tính. Trang có lượt truy cập, lượt xem, tỷ lệ thêm giỏ, **tỷ lệ chuyển
  đổi = người mua / lượt truy cập**, phễu *truy cập → thêm giỏ → đặt hàng*, sản phẩm xem nhiều nhất và **sản phẩm cần
  tối ưu** (≥ 5 lượt truy cập nhưng chưa có người mua) kèm gợi ý. Trang Tổng quan có thêm lượt truy cập, lượt xem và tỷ
  lệ chuyển đổi hôm nay so với hôm qua.
- **Đánh giá khách hàng**: số đánh giá mới, điểm trung bình, tỷ lệ hài lòng (4–5★), số tiêu cực (1–2★), tỷ lệ phản
  hồi, đánh giá có ảnh/video; phân bố số sao, đánh giá dịch vụ shop và vận chuyển, xu hướng theo thời gian, sản phẩm
  điểm thấp nhất. Shop xem mọi đánh giá (lọc theo chưa phản hồi / tiêu cực / số sao / sản phẩm) và **trả lời**
  (tối đa 255 ký tự, sửa được). Câu trả lời hiện trên trang sản phẩm; người mua nhận thông báo ở lần trả lời đầu tiên.

### Người bán: Tương tác khách hàng

- **Tin nhắn mẫu** (`/seller/care-settings`): mỗi shop có 3 danh sách (Chat, Hỏi đáp, Phản hồi đánh giá), tối đa 20
  mẫu mỗi loại. Lần đầu dùng sẽ có sẵn vài mẫu mặc định. Mẫu có thể chứa `{ten_khach}`, `{ten_shop}`, `{ten_san_pham}`;
  các biến này được điền theo từng khách khi gửi. Nút **📋 Tin nhắn mẫu** có ở khung chat, form trả lời câu hỏi và
  form phản hồi đánh giá.
- **Chat với khách hàng** (`/seller/chat`):
  - **Tin nhắn tự động**: tin chào gửi ở tin nhắn đầu tiên của khách, và tin *ngoài giờ làm việc* theo khung giờ của
    shop (có thể qua nửa đêm), tối đa 1 lần/12 giờ mỗi cuộc trò chuyện. Tin tự động có nhãn riêng, không làm mất trạng
    thái "chưa đọc / chờ trả lời" và không được tính là đã phản hồi.
  - Lọc **Tất cả / Chưa đọc / Chờ trả lời** (khách nhắn sau cùng mà chưa có người của shop trả lời).
  - **Bảng thông tin khách**: số đơn đã mua, tổng chi tiêu, số đơn hủy, điểm đánh giá khách đã cho shop, 5 đơn gần
    nhất. Shop gửi được **thẻ đơn hàng** và **thẻ sản phẩm** (tìm trong sản phẩm của shop) vào cuộc trò chuyện.
  - **✨ Gợi ý trả lời** cho tin nhắn mới nhất của khách (xem bên dưới).
  - **Tỷ lệ phản hồi chat** trong 30 ngày: mỗi lượt khách hỏi phải được người của shop trả lời trong 24 giờ; lượt hỏi
    chưa quá 24 giờ thì chưa tính. Có thêm thời gian phản hồi ("Trong vài phút / vài giờ"). Chỉ số này hiện ở khung chat,
    trang shop và trang sản phẩm.
- **Hỏi đáp dưới sản phẩm** (`/seller/questions`): tab Chờ trả lời (cũ nhất lên trước, đánh dấu câu chờ ≥ 1 ngày) /
  Đã trả lời / Đã ẩn, tìm theo nội dung hoặc tên sản phẩm. Shop **sửa được câu trả lời** (người hỏi chỉ nhận thông báo
  lần đầu) và **ẩn câu hỏi** spam (ẩn khỏi trang sản phẩm, có thể hiện lại).
- **Phản hồi đánh giá**: dùng mẫu, và **phản hồi hàng loạt** (chọn tối đa 50 đánh giá chưa trả lời, mỗi câu được điền
  tên khách và tên sản phẩm riêng).
- **Gợi ý trả lời**: nếu đã cấu hình LLM (`app.ai.provider`), câu trả lời do AI viết dựa trên dữ liệu thật của shop.
  Nếu không, hệ thống ghép câu theo quy tắc: nhận diện câu hỏi về tồn kho/size, giá/mã giảm giá, vận chuyển, đổi trả,
  chất liệu rồi lấy số liệu thật (tồn kho từng phân loại, giá, voucher đang chạy, thời gian chuẩn bị hàng, chính sách
  đổi trả, mô tả sản phẩm). Người bán luôn xem lại trước khi gửi.
- **Huy hiệu ở menu**: số câu hỏi chờ trả lời và đánh giá chưa phản hồi (`/api/seller/engagement`, cập nhật mỗi 30 giây).

### Người bán: AI dành cho Seller

Menu **Trợ Lý AI**. Mọi công cụ đều chạy **không cần API key**: số liệu luôn lấy từ CSDL và được phân tích bằng quy
tắc/thống kê. Khi cấu hình `app.ai.provider` (OpenAI hoặc mô hình Vistral tự train), LLM chỉ **diễn đạt lại** kết quả từ
các số liệu đó (`AIService.completeOrNull`) và được dặn không bịa số. Giao diện ghi rõ nguồn: "Viết bởi mô hình AI" hay
"Phân tích tự động".

- **Viết mô tả sản phẩm** (form sản phẩm, `POST /api/seller/ai/generate-description`): dùng tên, chất liệu, công dụng,
  đối tượng, điểm nổi bật, ngành hàng, các phân loại đã nhập và chính sách shop; có 3 giọng văn (thân thiện / chuyên
  nghiệp / ngắn gọn). Không có LLM thì ghép bản nháp theo mẫu: lợi ích và cách bảo quản theo chất liệu (cotton, linen,
  kaki, jean, len, da, inox…), mục điểm nổi bật, thông tin sản phẩm, chính sách, hashtag. Kèm **gợi ý tên sản phẩm**
  chuẩn tìm kiếm.
- **Gợi ý giá bán** (nút "💡 Gợi ý giá bán" dưới ô giá, `POST /api/seller/ai/price-suggestion`):
  - Tìm sản phẩm tương tự của **shop khác**: cùng ngành hàng (và các ngành anh em) cộng với sản phẩm có tên chung từ
    khóa, xếp theo độ giống tên (Jaccard trên từ không dấu).
  - Thống kê min / tứ phân vị / trung vị / max, và giá trung vị của nhóm 1/3 bán chạy nhất.
  - **Giá đề xuất** = giá của nhóm bán chạy, giới hạn trong khoảng phổ biến, làm tròn kiểu "…9.000".
  - Nếu nhập giá vốn: tính giá tối thiểu để lãi 20%.
  - Có biểu đồ khoảng giá, vị trí giá đang nhập so với thị trường, và danh sách sản phẩm dùng để so sánh.
- **Tóm tắt & phân tích đánh giá** (`/seller/ai/reviews`):
  - Cắt mỗi bình luận thành từng vế, nhận diện 7 chủ đề: chất lượng, size/form, giao hàng, đóng gói, giá, mẫu mã/đúng
    mô tả, tư vấn.
  - Chấm khen/chê bằng từ điển cảm xúc có xử lý phủ định ("không đẹp" là chê, "không bị lỗi" là khen); vế không có từ
    cảm xúc thì dựa vào số sao.
  - Kết quả: điểm được khen, điểm bị chê kèm trích dẫn và hành động gợi ý, mức hài lòng theo chủ đề, từ khóa hay gặp,
    sản phẩm bị chê nhiều nhất, đoạn tóm tắt. Lọc được theo sản phẩm và 30–365 ngày.
- **Trợ lý phân tích doanh thu** (`/seller/ai/assistant`, `POST /api/seller/ai/ask`):
  - Hỏi bằng tiếng Việt. Câu hỏi được phân tích để lấy:
    - **khoảng thời gian**: hôm nay/hôm qua, tuần này/trước, tháng này/trước, "tháng 9", "quý 3", "30 ngày qua",
      "từ 01/09 đến 15/09", năm nay…;
    - **kỳ so sánh** ("so với…", mặc định là kỳ trước cùng độ dài);
    - **chỉ số**: doanh thu, số đơn, giá trị TB, tỷ lệ hủy, sản phẩm bán chạy/bán chậm, tồn kho, khách mới/quay lại,
      thứ và giờ bán tốt, lượt xem & chuyển đổi, đánh giá, xu hướng;
    - **sản phẩm** nếu câu hỏi nhắc tên.
  - Trả lời bằng số thật, kèm bảng và biểu đồ, cùng gợi ý câu hỏi tiếp theo. Link `?q=` hỏi ngay khi mở trang.
- **Gợi ý thời điểm chạy khuyến mãi** (`/seller/ai/promotions`):
  - Phân tích đơn hàng và lượt xem 90 ngày: thứ bán tốt, khung Flash Sale tốt nhất (cùng 4 khung của trang Flash
    Sale), giờ cao điểm, đầu/giữa/cuối tháng (ngày lương), xu hướng 4 tuần. Shop có dưới 10 đơn thì dùng dữ liệu toàn sàn.
  - **Lịch sự kiện 60 ngày tới**: ngày đôi 9.9–12.12, Black Friday, 20/10, 20/11, Giáng sinh, mùa mua sắm Tết…, mỗi sự
    kiện kèm ngày nên bắt đầu chuẩn bị.
  - Gợi ý theo sản phẩm: nên xả hàng (tồn nhiều, 30 ngày không bán), nhiều người xem nhưng ít người mua (tặng voucher),
    đang tăng (ưu tiên quảng cáo), sắp hết hàng (không nên khuyến mãi).

### Tương tác & Hỗ trợ

- **Chat với Shop** (`/messages`, Kênh Người Bán → Chat với Người mua): mỗi cặp người mua – shop có một cuộc trò
  chuyện; gửi chữ, ảnh, **thẻ sản phẩm** (nút "Chat ngay" trên trang sản phẩm) hoặc **thẻ đơn hàng** ("Chat với Shop"
  trong chi tiết đơn); đếm tin chưa đọc, tin nhắn nhanh, xem tin cũ. Tin mới được lấy bằng **polling** mỗi 4 giây
  (không cần WebSocket). API: `/api/buyer/chats/**`, `/api/seller/chats/**`.
- **Chatbot AI** (`/chat`): nhớ 6 lượt hội thoại gần nhất, tìm sản phẩm **không dấu theo từng từ** và hiểu
  **khoảng giá** ("dưới 200k", "từ 1 đến 2 triệu"), **tra cứu đơn hàng** của chính người hỏi, trả lời theo
  **chính sách sàn** (vận chuyển, thanh toán, hủy đơn, đổi trả, voucher); trả về thẻ sản phẩm/đơn hàng bấm được và
  gợi ý chuyển sang Chat với Shop / Trung tâm hỗ trợ khi không trả lời được. **Chưa đặt `OPENAI_API_KEY` vẫn dùng
  được**: câu trả lời được dựng theo luật từ cùng dữ liệu truy xuất.
- **Trung tâm hỗ trợ** (`/help`): câu hỏi thường gặp có tìm kiếm không dấu; **ticket** gửi tới đội ngũ Shoppi
  (chủ đề, đơn hàng liên quan, ảnh đính kèm). Luồng: Chờ xử lý → Đang xử lý / Chờ người dùng phản hồi → Đã giải quyết
  → người gửi đánh giá 1–5 sao và đóng (tự đóng sau 3 ngày). Admin xử lý tại `/admin/support` (lọc, ưu tiên,
  thống kê mức hài lòng). Người bán gửi ticket trong Kênh Người Bán → Hỗ Trợ Từ Shoppi. Khiếu nại trả hàng/hoàn tiền
  vẫn đi theo luồng riêng ở trên.
- **Thông báo** (chuông trên header, `/notifications`): mọi bước của đơn hàng và trả hàng/hoàn tiền (kể cả mốc
  "shipper đang giao" mô phỏng), đơn mới / yêu cầu trả hàng cho người bán, **khuyến mãi** (voucher mới của shop gửi
  tới khách từng mua, Shoppi Voucher gửi tới mọi người mua, admin gửi thông báo hàng loạt ở `/admin/notifications`),
  **tin nhắn từ shop** và trả lời hỏi đáp, phản hồi ticket. Badge cập nhật bằng polling 30 giây.

### Quản trị sàn: Quản lý người dùng

Trang quản trị `/admin` có giao diện riêng (`AdminLayout`: thanh trên và menu bên trái). Admin mặc định:
`admin@marketplace.local` / `Admin@123`, là **quản trị viên cấp cao**.

- **Quản lý tài khoản** (`/admin/users`):
  - Tab Tất cả / Người mua / Người bán / Quản trị / Bị khóa / Đã xóa, tìm theo email, tên, số điện thoại, có phân trang.
  - Trang chi tiết: lịch sử mua (số đơn, đã chi, đơn hủy, đơn gần đây), shop của người bán (sản phẩm, đơn, doanh thu),
    quyền quản trị, lịch sử xử lý.
  - **Khóa** có lý do bắt buộc và thời hạn 1/3/7/30 ngày hoặc vĩnh viễn:
    - người dùng **bị đăng xuất ngay** (`JwtAuthFilter` từ chối token của tài khoản bị khóa/xóa), đăng nhập thấy lý do
      và thời hạn;
    - shop của người bán bị khóa tạm ẩn khỏi sàn;
    - khóa có thời hạn tự mở sau khi hết hạn (tác vụ định kỳ 5 phút).
  - **Xóa** = ẩn danh hóa (email, họ tên, số điện thoại bị xóa; đơn hàng và đánh giá vẫn giữ để dữ liệu của người khác
    không bị hỏng). Không xóa được tài khoản còn đơn đang xử lý.
  - Không ai tự khóa/xóa chính mình. Chỉ quản trị viên cấp cao mới khóa/xóa được nhân viên quản trị.
- **Duyệt đăng ký shop mới** (`/admin/shops`):
  - Có công tắc *"Duyệt shop mới trước khi hiển thị"* (cài đặt sàn `shop.requireApproval`, mặc định tắt).
  - Khi bật, shop mới ở trạng thái `PENDING`: người bán vẫn thiết lập shop và đăng sản phẩm, nhưng người mua chưa thấy.
    Nhân viên có quyền duyệt shop nhận thông báo; danh sách chờ duyệt xếp cũ nhất lên trước và ghi rõ hồ sơ còn thiếu gì.
  - Admin **duyệt**, **từ chối** (có lý do; người bán sửa hồ sơ rồi bấm *Gửi duyệt lại*), **đình chỉ** shop vi phạm và
    **mở lại**. Người bán nhận thông báo và thấy banner trạng thái trong Kênh Người Bán.
  - `Shop.status` (PENDING / APPROVED / REJECTED / SUSPENDED); `active` (hiển thị với người mua) = đã duyệt và tài khoản
    người bán không bị khóa.
- **Phân quyền nhân viên admin** (`/admin/staff`):
  - 8 quyền: Người dùng, Shop, Khiếu nại, Voucher, Hỗ trợ, Thông báo, Cài đặt, Nhân viên.
  - Mỗi quyền ứng với một nhóm API (`SecurityConfig`: `PERM_USERS` cho `/api/admin/users/**`…) và một mục menu; nhân
    viên chỉ thấy các mục được cấp, vào mục khác thì bị chặn.
  - Quyền được nạp lại ở mỗi request nên thay đổi có hiệu lực ngay.
  - Quy tắc an toàn: không ai tự sửa quyền của mình; quản trị viên cấp cao không thể bị sửa hay thu hồi; chỉ quản trị viên
    cấp cao được cấp/thu hồi quyền "Nhân viên". Thu hồi quyền đưa tài khoản về người mua thường.
- **Nhật ký quản trị** (`/admin/audit`): ghi mọi thao tác khóa/mở/xóa tài khoản, duyệt/từ chối/đình chỉ shop, phân
  quyền và cài đặt sàn (ai làm, lúc nào, lý do).

### Quản trị sàn: Sản phẩm & danh mục

Quyền nhân viên mới: **"Sản phẩm & danh mục"** (`PERM_PRODUCTS`, áp dụng cho `/api/admin/products/**` và
`/api/admin/categories/**`).

- **Danh mục chuẩn toàn sàn** (`/admin/categories`):
  - Cây 2 cấp (danh mục > danh mục con) với số sản phẩm của từng nhánh.
  - Thêm, đổi tên, chuyển sang danh mục cha khác (danh mục đang có con thì không thành danh mục con được), sắp xếp
    lên/xuống (`sortOrder`), ẩn/hiện.
  - Danh mục ẩn không hiện trên menu người mua và khi người bán chọn ngành hàng.
  - **Xóa an toàn**: danh mục còn danh mục con thì không xóa được; còn sản phẩm thì phải chọn danh mục để chuyển sản
    phẩm sang trước.
- **Kiểm duyệt sản phẩm** (`/admin/products`), trạng thái `PENDING / APPROVED / REJECTED / REMOVED`:
  - **Kiểm tra nội dung tự động** mỗi lần người bán đăng hoặc sửa sản phẩm (cả khi sao chép và đăng hàng loạt từ Excel):
    - từ khóa cấm do admin tự quản lý, so khớp nguyên từ và không phân biệt dấu (nặng);
    - số điện thoại / Zalo / link liên hệ ngoài sàn (cần xem);
    - giá thấp hơn 20% giá phổ biến của ngành hàng (cần xem);
    - tên viết hoa toàn bộ, nhiều ký tự đặc biệt, thiếu ảnh, mô tả quá ngắn (nhẹ);
    - kết quả kèm điểm rủi ro 0–100.
  - Công tắc *"Duyệt sản phẩm mới trước khi hiển thị"* (mặc định tắt):
    - bật: mọi sản phẩm mới chờ duyệt;
    - tắt: sản phẩm hiển thị ngay, **trừ** khi dính vi phạm nặng thì tự bị giữ lại;
    - sản phẩm đã duyệt mà bị sửa thành vi phạm cũng quay về chờ duyệt.
  - Hàng chờ duyệt xếp rủi ro cao lên trước. Có duyệt từng sản phẩm hoặc duyệt hàng loạt, từ chối (có lý do), **gỡ sản
    phẩm vi phạm** (chọn 1 trong 8 loại: hàng giả, hàng cấm, sai mô tả, giao dịch ngoài sàn…) và khôi phục.
  - Sản phẩm chưa được duyệt luôn bị ẩn và người bán không tự bật hiển thị được. Người bán thấy lý do trong danh sách
    sản phẩm (tab *Chờ duyệt / Vi phạm*) và trong form sửa, sửa xong bấm **Gửi duyệt lại**. Mọi thay đổi trạng thái đều
    có thông báo cho người bán.
- **Báo cáo vi phạm từ người mua**: nút *🚩 Báo cáo* trên trang sản phẩm (mỗi người chỉ có một báo cáo đang mở cho một
  sản phẩm). Đủ 3 báo cáo thì nhân viên được thông báo. Tab *Bị báo cáo* hiển thị số báo cáo; admin gỡ sản phẩm (các báo
  cáo được đánh dấu đã xử lý) hoặc bỏ qua báo cáo.
- Cài đặt sàn (`/admin/settings`) có thêm công tắc duyệt sản phẩm và danh sách từ khóa cấm. Mọi thao tác đều ghi vào
  Nhật ký quản trị.

### Quản trị sàn: Quản lý giao dịch

Hai quyền nhân viên tách riêng phần vận hành và phần tiền:
- **"Đơn hàng & tranh chấp"** (`PERM_ORDERS`): `/api/admin/orders/**`, `/api/admin/disputes/**`.
- **"Hoàn tiền & đối soát"** (`PERM_FINANCE`, mới): `/api/admin/refunds/**`, `/api/admin/settlements/**`,
  `/api/admin/reconciliation`, và hoàn tiền thủ công `/api/admin/orders/{id}/refund`.

- **Theo dõi toàn bộ đơn hàng** (`/admin/orders`):
  - Lọc theo trạng thái, phương thức thanh toán và khoảng ngày; tìm theo mã đơn, người mua hoặc tên shop.
  - Chỉ số của kỳ: số đơn, GMV, giá trị đơn trung bình, tỷ lệ hủy.
  - **Đơn bị trễ**: chờ xác nhận quá 24 giờ, chờ lấy hàng quá 72 giờ, đang giao quá 7 ngày. Đơn chờ lâu nhất xếp trên cùng.
  - Chi tiết đơn: người mua, shop, thanh toán, sản phẩm, hành trình, trả hàng, các khoản hoàn tiền và thao tác của admin.
  - Admin **hủy đơn** chưa giao cho đơn vị vận chuyển (có lý do). Hàng được cộng lại kho và tiền online tự hoàn.
- **Xử lý tranh chấp** (`/admin/disputes`), thay cho trang khiếu nại cũ:
  - Khi người mua khiếu nại, người bán có **48 giờ** để giải trình.
  - Người mua, người bán và Shoppi trao đổi trong **một luồng chung**, có đính kèm ảnh/video làm bằng chứng
    (tối đa 5 tệp mỗi tin). Luồng này hiện ở trang chi tiết đơn của người mua, trang Trả hàng của người bán và trang admin.
  - Danh sách xếp tranh chấp chờ lâu nhất lên trước, gắn cờ *shop quá hạn giải trình* và hiển thị số tranh chấp trước đó
    của shop.
  - Có 3 quyết định:
    - **hoàn toàn bộ**;
    - **hoàn một phần**: người mua giữ hàng, nhận một khoản nhỏ hơn số yêu cầu;
    - **không hoàn tiền**.
  - Kết luận được đăng vào luồng trao đổi và gửi thông báo cho cả hai bên.
- **Quản lý hoàn tiền** (`/admin/refunds`): mọi khoản hoàn đều được ghi lại (bảng `refunds`), dù từ hủy đơn, trả hàng,
  tranh chấp hay admin hoàn thủ công.
  - Thanh toán online: hoàn ngay về phương thức gốc, có mã giao dịch cổng thanh toán.
  - COD: vào hàng chờ chuyển khoản. Bộ phận tài chính nhập mã giao dịch ngân hàng để hoàn tất, hoặc đánh dấu thất bại
    (ví dụ sai số tài khoản) rồi thử lại. Người mua được thông báo ở mỗi bước và thấy mục *Hoàn tiền* trong chi tiết đơn.
  - **Hoàn tiền thủ công** (ví dụ bồi thường giao trễ) không vượt quá số tiền còn lại của đơn.
- **Đối soát thanh toán với người bán** (`/admin/settlements`, bảng `settlements`):
  - **Số tiền shop nhận = tiền hàng − khuyến mãi do shop tài trợ (combo, voucher shop) − phí cố định − hoàn tiền.**
  - Shoppi Voucher do sàn chịu, phí vận chuyển thuộc đơn vị vận chuyển, nên cả hai không ảnh hưởng.
  - Phí cố định mặc định 5%, chỉnh được trong Cài đặt sàn (0–30%). Bảng đối soát giữ tỷ lệ tại thời điểm tạo.
  - Đơn được đối soát khi đã giao **quá 3 ngày** và không còn yêu cầu trả hàng đang mở.
  - Quy trình: xem trước theo từng shop → chọn shop → tạo bảng đối soát (mã `DS-…`) → nhập mã chuyển khoản khi đã trả
    tiền. Người bán được thông báo ở cả hai bước.
  - Khoản hoàn tiền phát sinh sau khi đơn đã đối soát được trừ vào kỳ tiếp theo.
  - Người bán xem trong mục **Tài Chính › Doanh Thu & Đối Soát**: doanh thu chưa đối soát (ước tính), số tiền chờ
    chuyển, tổng đã nhận, cách tính và chi tiết từng kỳ.
- **Đối soát thanh toán** (`/admin/reconciliation`):
  - Tổng tiền thu online/COD theo phương thức, tiền đã hoàn và đang chờ hoàn, thực thu của kỳ.
  - Tự phát hiện sai lệch:
    - đơn hủy nhưng thanh toán vẫn "đã thu";
    - số tiền thanh toán khác tổng đơn;
    - đơn kẹt ở *chờ thanh toán*;
    - thanh toán đã hoàn nhưng không có giao dịch hoàn;
    - hoàn vượt giá trị đơn;
    - đơn đã giao nhưng chưa thu tiền;
    - khoản hoàn chờ chuyển khoản quá 3 ngày.
- Trang Tổng quan admin hiện thêm số tranh chấp chờ xử lý và số khoản hoàn chờ chuyển khoản. Mọi thao tác về tiền đều
  ghi vào Nhật ký quản trị.

### Quản trị sàn: Marketing toàn sàn

Quyền nhân viên **"Marketing toàn sàn"** (`PERM_VOUCHERS`, trước đây là "Shoppi Voucher") áp dụng cho
`/api/admin/vouchers/**`, `/api/admin/campaigns/**`, `/api/admin/banners/**` và `/api/admin/flash-sales/**`.

- **Chương trình khuyến mãi** (`/admin/campaigns`, bảng `promo_campaigns`):
  - Mỗi chương trình (ví dụ *Siêu Sale 11.11*) có tên, đường dẫn riêng, khẩu hiệu, thể lệ, ảnh bìa, màu chủ đề và thời
    gian diễn ra (tối đa 90 ngày). Đường dẫn được tạo tự động từ tên tiếng Việt.
  - Trang **`/campaigns/{slug}`** cho người mua: đếm ngược, các Shoppi Voucher của chương trình (bấm để sao chép mã), các
    khung giờ Flash Sale kèm sản phẩm, thể lệ.
  - Trang chủ hiện các chương trình đang hoặc sắp diễn ra. Chương trình có thể ẩn. Chỉ xóa được khi chưa có voucher,
    Flash Sale hay banner nào gắn vào.
- **Shoppi Voucher** (`/admin/vouchers`):
  - Áp dụng cho mọi shop, có thêm:
    - **ngành hàng áp dụng**: chọn danh mục cha thì gồm cả danh mục con; chỉ phần tiền của sản phẩm thuộc ngành đó được giảm;
    - **chỉ dành cho khách hàng mới**: người mua chưa có đơn nào thành công;
    - **ngân sách Shoppi**: khi tổng tiền giảm đã chi chạm ngân sách, voucher tự ngừng áp dụng; ngân sách tăng được
      khi đang chạy;
    - **gắn vào chương trình**.
  - Bảng theo dõi hiệu quả: số lượt đặt hàng, số người mua, Shoppi đã chi, doanh số mang lại, ROI.
  - Mọi thao tác ghi vào Nhật ký quản trị.
- **Banner trang chủ** (`/admin/banners`, bảng `banners`):
  - Hai vị trí: banner chính (trình chiếu tự động, tối đa 8) và 2 banner phụ.
  - Mỗi banner có lịch hiển thị (bắt đầu/kết thúc), bật/tắt, sắp xếp thứ tự và liên kết trong Shoppi (không cho phép link
    ra ngoài). Banner của một chương trình mặc định mở trang chương trình.
  - Thống kê lượt hiển thị, lượt nhấp và CTR. Khi không có banner nào, trang chủ dùng banner mặc định.
- **Flash Sale toàn sàn** (`/admin/flash-sales`, bảng `flash_sale_events` + `flash_sale_registrations`):
  - Admin mở **khung giờ** (tối đa 24 giờ, không trùng nhau) với hạn đăng ký, mức giảm tối thiểu và số sản phẩm tối đa
    mỗi shop. Mọi người bán được thông báo mời đăng ký.
  - Người bán đăng ký ở **Kênh Marketing › Flash Sale Của Shoppi**: chọn sản phẩm, giá Flash Sale (được gợi ý sẵn mức cao
    nhất hợp lệ), số suất, giới hạn mỗi khách. Có thể rút trước giờ bắt đầu, và đăng ký lại sản phẩm bị từ chối với giá mới.
  - Admin duyệt từng sản phẩm hoặc **duyệt tất cả**, từ chối kèm lý do. Hệ thống cảnh báo nếu người bán đã đổi giá gốc
    sau khi đăng ký.
  - Sản phẩm được duyệt nhận giá Flash Sale đúng khung giờ, nên giá, số suất, giới hạn mỗi khách, tìm kiếm và thanh toán
    đều hoạt động như Flash Sale của shop.
  - Một sản phẩm có thể được duyệt vào nhiều khung giờ. Sản phẩm mang khung giờ sớm nhất; khung giờ kế tiếp được tự động
    áp dụng khi đến giờ (tác vụ chạy mỗi phút). Một sản phẩm chỉ tham gia **hoặc** Flash Sale của shop **hoặc** của
    Shoppi tại một thời điểm.
  - Trang chủ: dải Flash Sale có thêm các tab khung giờ sắp tới, mỗi tab có đếm ngược đến giờ mở bán và danh sách sản phẩm.
  - Khi khung giờ đã chạy, admin xem kết quả: số suất bán được, số đơn, doanh số. Có thể hủy khung giờ (Flash Sale đang
    chạy dừng ngay, các shop được thông báo).

### Quản trị sàn: Thống kê & Báo cáo tổng

Quyền nhân viên mới **"Thống kê & báo cáo"** (`PERM_REPORTS`, `/api/admin/analytics/**`). Ba trang báo cáo dùng chung thanh
chọn kỳ (7/30 ngày, tháng này, tháng trước, 12 tuần, 12 tháng hoặc tự chọn, xem theo ngày/tuần/tháng; kỳ lưu trên URL)
và chung một nút **xuất Excel**.

- **Tổng quan kinh doanh** (`/admin/reports`): các chỉ số so với kỳ trước cùng độ dài:
  - GMV, số đơn, giá trị đơn trung bình;
  - người dùng mới, shop mới, người mua đặt hàng, người mua lần đầu, số shop có đơn;
  - phí sàn (ước tính theo tỷ lệ phí hiện tại), chi phí Shoppi Voucher, tỷ lệ hủy/hoàn tiền;
  - tổng người dùng và shop đến cuối kỳ.

  Kèm biểu đồ theo thời gian (GMV & đơn hàng / người dùng & shop mới / người mua), doanh thu theo ngành hàng và phân bổ
  trạng thái đơn.
- **Top shop & sản phẩm** (`/admin/reports/top`):
  - top 10/20/50 shop theo GMV: tỷ trọng, số đơn, sản phẩm bán, người mua, giá trị đơn trung bình, tăng trưởng so với kỳ
    trước, điểm đánh giá;
  - top sản phẩm bán chạy theo số lượng hoặc doanh thu;
  - bảng doanh thu theo ngành hàng.
- **Tăng trưởng** (`/admin/reports/growth`, mặc định 12 tháng gần nhất theo tháng):
  - mỗi kỳ so với kỳ liền trước: GMV, đơn hàng, người mua (lần đầu / mua lại), người dùng và shop mới, tổng người dùng;
  - tăng trưởng bình quân (tính kép), kỳ đầu → kỳ cuối, kỳ cao nhất;
  - biểu đồ GMV kèm % tăng trưởng.
- **Xuất Excel**: 6 sheet (Tổng quan, Theo thời gian, Top shop, Top sản phẩm, Ngành hàng, Tăng trưởng). Mỗi lần xuất được
  ghi vào Nhật ký quản trị.
- Trang **Tổng quan sàn** của admin hiện thêm khối "30 ngày qua" (GMV, đơn, người dùng mới, người mua, biểu đồ) cho nhân
  viên có quyền báo cáo.
- Quy ước: "đơn bán" là đơn không bị hủy, không hoàn tiền và không còn chờ thanh toán (giống báo cáo của người bán).

### Quản trị sàn: Vận hành hệ thống

Quyền **"Cấu hình & vận hành hệ thống"** (`PERM_SETTINGS`, trước đây là "Cài đặt sàn") áp dụng cho `/api/admin/settings/**`,
`/api/admin/system/**` và `/api/admin/policies/**`. Riêng API key và endpoint AI chỉ quản trị viên cấp cao được đổi.

- **Cấu hình sàn** (`/admin/settings`):
  - phí cố định mặc định và **phí riêng theo ngành hàng cấp 1**. Khi đối soát, phí của từng đơn tính theo ngành hàng của
    từng sản phẩm; phí được lưu trên đơn, bảng đối soát ghi tỷ lệ phí thực tế;
  - thời gian giữ tiền trước đối soát, thời hạn thanh toán online, hạn người bán giải trình tranh chấp. Trước đây các giá
    trị này viết cứng trong code, nay đổi được ngay; thông báo gửi cho người dùng cũng dùng giá trị mới;
  - kiểm duyệt shop/sản phẩm và từ khóa cấm như trước. Mọi thay đổi ghi vào Nhật ký quản trị dạng "cũ → mới".
- **Chính sách chung** (`/admin/policies`, bảng `policy_versions`):
  - 5 chính sách: Điều khoản sử dụng, Bảo mật, Đổi trả & hoàn tiền, Quy định người bán, Hàng cấm;
  - mỗi lần xuất bản tạo phiên bản mới (kèm ghi chú thay đổi), có thể thông báo cho người dùng liên quan, các phiên bản cũ
    vẫn xem được;
  - trang công khai `/policies/{terms|privacy|return|seller|prohibited}`; footer trang chủ và form đăng ký có liên kết.
- **AI & chi phí LLM** (`/admin/ai`):
  - chọn nhà cung cấp (OpenAI hoặc mô hình tự train trên Hugging Face), model, endpoint;
  - **API key mã hóa AES-256-GCM** khi lưu vào cơ sở dữ liệu, chỉ hiển thị dạng `sk-…a1b2`, không bao giờ ghi vào nhật ký.
    Biến môi trường vẫn là giá trị dự phòng;
  - nút **Kiểm tra kết nối**; công tắc **tắt toàn bộ tính năng AI**; khi tắt, các tính năng tự trả lời bằng quy tắc;
  - **mọi lượt gọi LLM được ghi lại**: tính năng gọi (chatbot, viết mô tả, trợ lý doanh thu…, tự nhận diện), model, token
    vào/ra, chi phí ước tính theo bảng giá mỗi 1 triệu token (chỉnh được), thời gian phản hồi, kết quả;
  - báo cáo chi phí theo ngày, tính năng, model; chi phí tháng này, dự kiến cả tháng;
  - **ngân sách tháng**: cảnh báo khi đạt ngưỡng (mặc định 80%) và khi hết; tùy chọn tự dừng AI khi hết ngân sách.
- **Giám sát hệ thống** (`/admin/monitoring`):
  - **Tổng quan**: trạng thái ứng dụng (bộ nhớ, CPU, thời gian chạy), cơ sở dữ liệu (độ trễ, dung lượng, kết nối), ổ đĩa
    lưu tệp, tỷ lệ lỗi 5xx, số lỗi chưa xử lý, trạng thái AI, biểu đồ lưu lượng 60 phút.
  - **Lỗi hệ thống** (bảng `error_events`): lỗi bất ngờ (500) được gom nhóm theo loại lỗi + vị trí trong code, đếm số lần,
    lưu stack trace và yêu cầu gần nhất. Người dùng chỉ thấy mã tham chiếu `ERR-…` thay vì thông tin nội bộ. Nhóm lỗi mới
    hoặc lỗi đã xử lý mà tái phát được thông báo cho admin. Có các trạng thái Đã xử lý / Bỏ qua / Mở lại, kèm ghi chú và
    nút "Gửi lỗi thử".
  - **Hiệu năng API**: số yêu cầu, thời gian xử lý, lỗi 4xx/5xx theo phút (1/6/24 giờ), endpoint gọi nhiều nhất / chậm
    nhất / có lỗi, các yêu cầu chậm ≥ 1 giây.
  - **Log**: 2.000 dòng log gần nhất (INFO trở lên của ứng dụng, WARN trở lên của thư viện), lọc theo mức và từ khóa.
  - JSON sai định dạng hoặc tham số sai kiểu giờ trả lỗi 400 thay vì 500.

### Bảo mật tài khoản

- **Phiên đăng nhập** (bảng `user_sessions`): access token JWT sống **30 phút** (`JWT_EXPIRATION_MS`), kèm refresh token
  14 ngày (`JWT_REFRESH_DAYS`, chỉ lưu dạng băm). Mỗi lần làm mới, refresh token được thay token mới. Nếu một refresh
  token cũ bị dùng lại, hệ thống coi là bị đánh cắp và thu hồi phiên. Frontend tự làm mới khi gặp 401.
- **Khóa đăng nhập**: sai mật khẩu 5 lần thì khóa 15 phút; giới hạn số lần thử theo IP, số lần đăng ký và quên mật khẩu.
  Các ngưỡng chỉnh được trong *Cấu hình sàn → Bảo mật đăng nhập*. Lịch sử đăng nhập (bảng `login_events`) xem ở
  *Giám sát hệ thống → Đăng nhập*, admin có thể mở khóa.
- **Xác thực 2 lớp (TOTP, RFC 6238)**: quét QR bằng Google Authenticator. Có 10 mã dự phòng dùng một lần, khóa bí mật được
  mã hóa. Có thể **bắt buộc 2FA với tài khoản quản trị**.
- **Mật khẩu**: ít nhất 8 ký tự, có cả chữ và số, không nằm trong danh sách mật khẩu phổ biến, không chứa tên email.
  Đổi mật khẩu sẽ đăng xuất các thiết bị khác.
- **Trang Bảo mật** (`/account/security`, `/seller/security`, `/admin/security`): đổi mật khẩu, bật/tắt 2FA, danh sách
  thiết bị đang đăng nhập (đăng xuất từng thiết bị hoặc tất cả), lịch sử đăng nhập. Đăng nhập từ thiết bị mới sẽ gửi
  cảnh báo.
- **HTTP headers**: CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, HSTS. `X-Forwarded-For` chỉ được tin
  khi đến từ proxy nội bộ.

### Email (SMTP)

- Email được đưa vào hàng đợi (bảng `email_messages`) và gửi nền mỗi 5 giây. Nếu lỗi, hệ thống gửi lại sau 1, 5 rồi
  30 phút.
- Các loại email: xác thực email khi đăng ký, quên/đặt lại mật khẩu (link dùng 1 lần, hết hạn), cảnh báo bảo mật (đăng
  nhập thiết bị mới, đổi mật khẩu, bật/tắt 2FA), các bước của đơn hàng.
- Cấu hình SMTP ở **`/admin/email`** (mật khẩu được mã hóa), dự phòng bằng biến `MAIL_HOST`, `MAIL_PORT`,
  `MAIL_USERNAME`, `MAIL_PASSWORD`. Trang này cũng cho gửi thử và xem *Hộp thư đi* (xem trước nội dung, gửi lại).
  **Chưa cấu hình SMTP thì email chỉ được lưu lại, không gửi đi.**
- Dùng Gmail: `smtp.gmail.com`, cổng 587, STARTTLS. Mật khẩu là **Mật khẩu ứng dụng** của Google (cần bật xác minh
  2 bước), không phải mật khẩu Gmail.

### Thanh toán VNPay (cổng thật, API 2.1.0)

- **Luồng**: người mua chọn *VNPay* ở bước thanh toán → backend tạo URL có chữ ký **HMAC-SHA512** → người mua thanh toán
  trên trang VNPay → VNPay chuyển về `/payment/vnpay-return`. Kết quả trả về được kiểm tra chữ ký rồi mới ghi nhận.
- **IPN** `GET /api/payments/vnpay/ipn` (VNPay gọi thẳng server): kiểm tra chữ ký, đơn hàng và số tiền. Mã trả về theo
  chuẩn VNPay: `00` thành công, `97` sai chữ ký, `01` không có đơn, `04` sai số tiền, `02` đã xác nhận. Gọi lặp lại không
  ghi nhận hai lần.
- **Tra cứu (querydr)**: trước khi tự hủy đơn quá hạn thanh toán, hệ thống hỏi lại VNPay. Nếu VNPay báo đã trả tiền thì
  đơn được xác nhận, không bị hủy.
- **Hoàn tiền qua API VNPay**: hoàn toàn bộ (02) hoặc một phần (03) khi hủy đơn hoặc trả hàng. Nếu VNPay từ chối, khoản
  hoàn chuyển thành *chuyển khoản thủ công* để bộ phận tài chính xử lý.
- **Cấu hình** ở `/admin/gateways` (hash secret được mã hóa), dự phòng bằng `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`.
  Đăng ký sandbox tại <https://sandbox.vnpayment.vn/devreg/> để nhận TMN code và hash secret.
  Thẻ test của sandbox: NCB `9704198526191432198`, chủ thẻ `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
- **Lưu ý**: VNPay không gọi được IPN vào `localhost`. Khi chạy local, kết quả được ghi nhận qua return URL (có kiểm tra
  chữ ký). Khi triển khai thật, hãy khai báo IPN URL công khai (hoặc dùng ngrok) trong trang merchant VNPay.
- Các ví MoMo/ZaloPay, thẻ và chuyển khoản vẫn là cổng **mô phỏng** như trước.

### Vận chuyển Giao Hàng Nhanh (GHN, API thật)

- **Cấu hình** ở `/admin/gateways`: token (được mã hóa) và ShopId, dự phòng bằng `GHN_TOKEN`, `GHN_SHOP_ID`. Mặc định dùng
  API sandbox `https://dev-online-gateway.ghn.vn/shiip/public-api`. Có nút *Kiểm tra kết nối*.
  Tài khoản thử nghiệm đăng ký tại <https://5sao.ghn.dev>.
- **Phí ship thật**: khi đã kết nối GHN, lựa chọn GHN ở bước thanh toán lấy phí từ API tính phí của GHN (gói chuẩn).
  Địa chỉ dạng chữ được đổi sang mã quận/phường của GHN; cách so khớp không phân biệt dấu và tiền tố. Nếu GHN không báo
  giá được, hệ thống dùng bảng phí mô phỏng. Các hãng khác vẫn dùng bảng phí mô phỏng.
- **Tạo vận đơn**: khi người bán bấm *Chuẩn bị hàng* cho đơn GHN, hệ thống tạo đơn trên GHN và dùng mã GHN làm mã vận đơn.
  Thông tin gửi đi gồm người nhận, tiền thu hộ COD, địa chỉ lấy hàng, cân nặng và danh sách hàng. Nếu GHN từ chối, người
  bán thấy lý do. Khi đơn bị hủy trước lúc lấy hàng, vận đơn GHN cũng được hủy.
- **Webhook** `POST /api/shipping/ghn/webhook?key=…` (khóa bí mật xem ở trang cấu hình). Mỗi trạng thái GHN thành một bước
  trên hành trình đơn: *picked* → đang giao, *delivered* → đã giao (thu COD), *delivery_fail*, *returned* → hệ thống hủy
  đơn và nhập lại kho, *cancel* → người bán tạo lại vận đơn. Đơn GHN không còn dùng bộ mô phỏng hành trình.
- Shop cần nhập **Quận/Huyện, Phường/Xã lấy hàng** trong *Hồ sơ shop*. Webhook cũng cần backend có địa chỉ công khai.

### Trung tâm đánh giá (AI quản lý đánh giá & phản hồi)

Trang **Kênh người bán → Chăm sóc khách hàng → Trung Tâm Đánh Giá** (`/seller/review-hub`). Đánh giá ảnh hưởng trực
tiếp tới doanh số, nhưng người bán không kịp trả lời hết; trang này gom và xử lý đánh giá ở một chỗ.

- **Gom đánh giá từ nhiều sàn**: đánh giá trên Shoppi tự đồng bộ (sự kiện khi khách viết). Shopee, Lazada, Tiki,
  TikTok Shop: tải lên **file Excel/CSV xuất từ trang người bán của sàn đó**. Cột được nhận theo tiêu đề tiếng Việt
  hoặc tiếng Anh, tên sản phẩm được khớp với sản phẩm của shop, nhập lại file không tạo bản trùng. Có thêm API
  `POST /api/seller/review-hub/ingest` (JSON) để nối công cụ khác. API đánh giá chính thức của các sàn chỉ cấp cho
  đối tác, nên đồ án dùng file xuất là cách thực tế.
- **Phân loại**: cảm xúc (tích cực / trung lập / tiêu cực) và 9 nhóm vấn đề: chất lượng, mô tả sai, giao hàng, đóng
  gói, size, thiếu/sai hàng, nghi hàng giả / an toàn, giá, tư vấn / đổi trả. Có 3 tầng xử lý:
  1. **Bộ luật tiếng Việt** (luôn chạy): xử lý theo từng mệnh đề, có phủ định ("không lỗi" không phải lỗi), nhận cả
     tiếng Việt không dấu.
  2. **Mô hình FastAPI** (nếu bật `REVIEW_AI_URL`): phân loại tức thì, không tốn phí API.
  3. **LLM** (OpenAI hoặc Claude, nếu bật AI): đọc theo lô 15 đánh giá ở chế độ nền, trả về vấn đề, tóm tắt một câu
     và nhận định đánh giá đáng ngờ.

  Người bán **sửa phân loại** được; bản sửa được giữ qua các lần phân tích lại và dùng làm dữ liệu huấn luyện.
- **Ưu tiên trả lời**: điểm 0–100 và mức Khẩn cấp / Cao / Bình thường / Thấp, kèm lý do bằng lời: số sao, từ ngữ
  cảnh báo ("hàng giả", "dị ứng", "lừa đảo"…), vấn đề nghiêm trọng, có ảnh, thời gian đã chờ. Mỗi mức có hạn trả lời:
  khẩn cấp 12 giờ, cao 24 giờ, bình thường 72 giờ. Đánh giá khẩn cấp mới đến sẽ gửi thông báo ngay cho người bán.
- **Soạn sẵn phản hồi theo tình huống**: AI viết theo vấn đề, số sao, sàn và tên khách, tuân thủ quy tắc (không đổ
  lỗi, không hứa quá, không để liên hệ ngoài sàn). Không có AI thì dùng mẫu theo từng nhóm vấn đề. Người bán sửa rồi
  **Duyệt**: đánh giá Shoppi được đăng ngay; đánh giá sàn khác được sao chép để dán lên sàn và đánh dấu đã trả lời.
  Có nút soạn nháp hàng loạt cho 20 đánh giá ưu tiên cao.
- **Phát hiện bất thường** (điểm 0–100, từ 40 thì cảnh báo): nội dung trùng giữa các tài khoản, số đánh giá tăng
  đột biến, nhiều đánh giá 1–2 sao ngắn và không ảnh trong 24 giờ (nghi bị chơi xấu), một người đánh giá liên tục,
  số sao ngược với nội dung, đánh giá xấu không có mã đơn, và nhận định của AI (ví dụ khách được tặng quà để đánh giá).
  Người bán xác nhận "đánh giá thật" hoặc báo cáo; đánh giá Shoppi bị báo cáo được gửi tới đội kiểm duyệt.
- **Vấn đề lặp lại**: gom theo *sản phẩm × vấn đề* trên mọi sàn, so với kỳ trước (đang tăng hay giảm), đánh dấu
  **nghi lỗi sản phẩm thật** (chất lượng, an toàn, mô tả sai lặp lại từ 3 lần). Nút "Phân tích nguyên nhân" cho AI
  đọc các đánh giá và đề xuất cách sửa từ gốc. Vấn đề tăng (từ 3 lần trong 14 ngày) sẽ gửi cảnh báo cho người bán.
- **Dashboard**: tổng số đánh giá, điểm trung bình, tỷ lệ tiêu cực, số cần trả lời, khẩn cấp, quá hạn, tỷ lệ và thời
  gian phản hồi; biểu đồ diễn biến; phân bố vấn đề; so sánh theo sàn; danh sách cần trả lời ngay.

**Dịch vụ FastAPI** (`review-ai-service/`, tùy chọn):

```bash
cd review-ai-service
python -m venv .venv && .venv\Scripts\activate      # Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8000
# backend: REVIEW_AI_URL=http://localhost:8000      (docker compose đã cấu hình sẵn)
```

`POST /analyze` (phân loại), `POST /similar` (đánh giá gần giống nhau, TF-IDF + cosine), `POST /train` (huấn luyện).
Mô hình: TF-IDF (từ + ký tự) với Logistic Regression cho từng nhãn, chạy trên CPU. Admin bấm **Huấn luyện lại**
ở trang *AI & chi phí LLM*: hệ thống gửi các đánh giá đã gán nhãn (AI phân loại và người bán sửa) để huấn luyện, rồi
báo F1 trên tập kiểm tra. Cần ít nhất 20 đánh giá có nhãn.

**Claude**: ở *AI & chi phí LLM* chọn nhà cung cấp **Anthropic Claude**, nhập API key và model (mặc định
`claude-sonnet-5-5`, rẻ hơn thì dùng `claude-haiku-4-5-20251001`), hoặc đặt biến `ANTHROPIC_API_KEY`. Mọi tính năng AI
(chatbot, viết mô tả, trung tâm đánh giá…) dùng Claude. Riêng tìm kiếm ngữ nghĩa vẫn cần OpenAI key cho embeddings.
Giá theo token của model Claude nhập ở bảng giá trên cùng trang để tính chi phí.

### Chat realtime, cache, hàng đợi, tìm kiếm ngữ nghĩa

- **WebSocket (STOMP)** – `realtime/`: endpoint `/ws`, client gửi JWT trong khung CONNECT (kiểm tra như REST:
  chữ ký, tài khoản còn hoạt động, phiên chưa đăng xuất). Mỗi người chỉ được đăng ký hàng đợi riêng
  `/user/queue/events`; server đẩy sự kiện `chat` (tin nhắn mới) và `notification` (chuông) sau khi transaction
  commit. Frontend (`realtime/socket.js`) cập nhật khung chat và chuông ngay lập tức; polling chậm (30 giây)
  giữ lại làm dự phòng khi mất kết nối.
- **Redis cache** – `config/CacheConfig.java`: cây danh mục (30 phút) và sản phẩm bán chạy (2 phút), lưu JSON dưới
  khóa `shoppi:*`. Admin sửa danh mục thì cache bị xóa sau khi commit. Không đặt `CACHE_TYPE=redis` thì dùng
  cache trong bộ nhớ (Caffeine).
- **RabbitMQ** – `messaging/`: email mới được đưa vào hàng đợi `shoppi.email` và gửi ngay bởi consumer; bảng
  `email_messages` vẫn là bản ghi (trạng thái, gửi lại). Mỗi email được "nhận việc" bằng một câu UPDATE có điều
  kiện nên scheduler và consumer không bao giờ gửi trùng. Broker lỗi thì scheduler gửi bù.
- **Tìm kiếm ngữ nghĩa** – `service/search/`: sản phẩm mới/vừa sửa được tạo vector mỗi phút (khi AI bật).
  `GET /api/products/semantic?q=` trả sản phẩm gần nghĩa nhất; trang tìm kiếm hiện khối "Có thể bạn đang tìm (AI)".
  Có pgvector: cột `vector(256)` + chỉ mục HNSW, CSDL tự tìm láng giềng gần nhất (`<=>`); chưa có: lưu `real[]`
  và tính cosine trong Java. Trạng thái chỉ mục xem ở *AI & chi phí LLM*.

### Đăng nhập bằng Google (OAuth2 + Spring Security)

Có 2 cách đăng nhập:
1. **Email/mật khẩu** đăng ký trực tiếp trong app: `POST /api/auth/register`, `POST /api/auth/login`
   (mật khẩu băm BCrypt, xác thực qua `AuthenticationManager`), trả về JWT.
2. **Google OAuth2** (Authorization Code flow, `spring-boot-starter-oauth2-client` + `oauth2Login()`):
   nút "Đăng nhập với Google" → `GET /oauth2/authorization/google` → Google → callback
   `/login/oauth2/code/google` → `OAuth2LoginSuccessHandler` tạo/tìm user, cấp JWT và redirect về
   `http://localhost:5173/oauth2/redirect#token=...&refresh=...` (hoặc `#challenge=...` nếu tài khoản bật 2FA) → frontend gọi `GET /api/auth/me`.
   Email Google chưa có trong DB sẽ được tự tạo tài khoản BUYER.

Cấu hình một lần:
1. Google Cloud Console → APIs & Services → Credentials → Create OAuth client ID (loại **Web application**)
2. Thêm **Authorized redirect URI**: `http://localhost:8081/login/oauth2/code/google`
3. Đặt biến môi trường trước khi chạy backend:
   ```bash
   export GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
   export GOOGLE_CLIENT_SECRET=GOCSPX-xxxx
   ```
   (Trong IntelliJ: Run Configuration → Environment variables.)

**Lưu ý:** nếu chưa đặt `OPENAI_API_KEY`, các endpoint AI vẫn hoạt động (không lỗi) nhưng trả
về thông báo "AI chưa được cấu hình" thay vì câu trả lời thật.

## Chạy frontend

Yêu cầu: Node.js 18+.

```bash
cd frontend
npm install
npm run dev
```

Frontend chạy ở `http://localhost:5173`, tự động proxy các request `/api/**` sang backend
`localhost:8081` (cấu hình trong `vite.config.js`, đổi bằng biến `BACKEND_URL`).

## Luồng demo gợi ý

1. Đăng ký 1 tài khoản **Seller** → vào "Kênh người bán" → tạo hồ sơ shop → thêm sản phẩm
   (thử nút "Tạo mô tả bằng AI")
2. Đăng ký 1 tài khoản **Buyer** (trình duyệt ẩn danh hoặc trình duyệt khác) → tìm sản phẩm
   vừa đăng → thêm vào giỏ → đặt hàng (COD)
3. Vào mục "Trợ lý AI" (buyer) → hỏi về sản phẩm vừa đăng → xem chatbot trả lời dựa trên
   catalog thật (RAG)
4. Đăng nhập lại bằng Seller → "Đơn hàng" → xác nhận đơn vừa đặt → xem "Tổng quan" cập nhật
   thống kê

## Tài liệu & công cụ

- **ERD**: `docs/ERD.md` (Mermaid, sinh từ các entity). Đưa vào draw.io: *Arrange → Insert → Advanced → Mermaid*.
- **Postman**: import `docs/postman/Shoppi.postman_collection.json` (334 endpoint, chia theo khu vực). Chạy
  *0. Bắt đầu → Đăng nhập* trước: token được lưu vào biến `{{token}}` cho mọi request. Đổi `baseUrl` khi gọi
  bản đã triển khai.
- **CI**: `.github/workflows/ci.yml`.

## Phần "tự train AI" (Vistral-7B fine-tune)

Ngoài chatbot dùng OpenAI API + RAG (mặc định, ổn định cho bản deploy chính),
project còn có một hướng **tự fine-tune model thật sự** trong thư mục
`ai-finetune/` — dùng LoRA để fine-tune Vistral-7B-Chat (model tiếng Việt mã
nguồn mở) trên chính dữ liệu sản phẩm của sàn, sau đó deploy qua Hugging Face
Inference Endpoint (có tính phí, dùng chế độ scale-to-zero để tiết kiệm chi
phí). Xem chi tiết trong `ai-finetune/README.md` và `ai-finetune/DEPLOY_HF_ENDPOINT.md`.

Backend đã hỗ trợ chuyển đổi giữa 2 nguồn AI chỉ bằng 1 dòng config
(`app.ai.provider=openai` hoặc `huggingface`) — không cần sửa code khi đổi.

## Kiến trúc AI (để viết vào báo cáo)

- **Không tự train model.** Gọi thẳng OpenAI Chat Completions API qua `AIService.java`
  (dùng `WebClient`), đúng cách tiếp cận phổ biến trong công nghiệp hiện nay.
- **Chatbot buyer = RAG thủ công 3 bước** (`ChatbotService.java`):
  1. *Retrieval*: chuẩn hóa câu hỏi về dạng không dấu, tách khoảng giá và chủ đề (đơn hàng, đổi trả...),
     tìm sản phẩm theo từng từ khóa (`ProductRepository.findForChatbot`) rồi chấm điểm theo số từ / cụm từ khớp;
     lấy thêm đơn hàng gần đây của người hỏi khi cần. Nếu từ khóa tìm được ít hơn 5 sản phẩm, bước 2 tìm
     **theo ý nghĩa**: so vector câu hỏi với vector sản phẩm (embeddings `text-embedding-3-small`, 256 chiều,
     lưu bằng `pgvector`) nên "quà cho mẹ" vẫn ra khăn lụa, túi xách dù không trùng từ nào
  2. *Augmentation*: ghép sản phẩm, đơn hàng, chính sách sàn và lịch sử hội thoại vào system prompt
  3. *Generation*: gọi LLM, ràng buộc chỉ trả lời trong phạm vi context được cung cấp
- **Gợi ý mô tả (seller)**: prompt engineering đơn giản, không cần retrieval
  (`SellerAIController.java`)
- **Recommendation**: rule-based trên `ViewHistory` + cùng category
  (`RecommendationController.java`, `ProductService.recommendSimilar`)
