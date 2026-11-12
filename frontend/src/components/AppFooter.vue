<template>
  <footer class="footer">
    <div class="container cols">
      <div>
        <h4>Chăm sóc khách hàng</h4>
        <router-link to="/help">Trung tâm hỗ trợ</router-link>
        <router-link to="/help/new">Gửi yêu cầu hỗ trợ</router-link>
        <router-link to="/orders">Đơn hàng của tôi</router-link>
        <router-link to="/policies/return">Trả hàng & hoàn tiền</router-link>
        <router-link to="/account/vouchers">Kho Voucher</router-link>
      </div>
      <div>
        <h4>Về Shoppi</h4>
        <router-link to="/policies/terms">Điều khoản sử dụng</router-link>
        <router-link to="/policies/privacy">Chính sách bảo mật</router-link>
        <router-link to="/search?mall=true">Shoppi Mall</router-link>
        <router-link to="/videos">Shoppi Video</router-link>
        <router-link to="/account/coins">Shoppi Xu</router-link>
      </div>
      <div>
        <h4>Bán hàng cùng Shoppi</h4>
        <router-link to="/seller/register">Mở shop trên Shoppi</router-link>
        <router-link to="/seller/login">Kênh Người Bán</router-link>
        <router-link to="/policies/seller">Quy định dành cho người bán</router-link>
        <router-link to="/policies/prohibited">Hàng cấm & hạn chế</router-link>
      </div>
      <div>
        <h4>Thanh toán</h4>
        <div class="chips">
          <span>Thanh toán khi nhận hàng</span>
          <span>Ví ShoppiPay</span>
          <span>VNPay</span>
          <span>Thẻ Visa / Mastercard / JCB</span>
          <span>Chuyển khoản</span>
        </div>
        <h4 class="gap">Đơn vị vận chuyển</h4>
        <div class="chips">
          <span>Giao Hàng Nhanh</span>
          <span>Giao Hàng Tiết Kiệm</span>
        </div>
      </div>
      <div>
        <h4>Ứng dụng Shoppi</h4>
        <p class="app">Mua sắm và bán hàng ngay trên điện thoại với hai ứng dụng Android: <b>Shoppi</b> cho người mua và <b>Shoppi Người Bán</b> cho chủ shop.</p>
      </div>
    </div>
    <div class="bottom">
      <div class="container bottom-inner">
        <span>© {{ new Date().getFullYear() }} Shoppi · Đồ án chuyên ngành CNTT</span>
        <span>Việt Nam</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer { background: #fbfbfb; border-top: 4px solid var(--color-primary); margin-top: 24px; padding-top: 32px; font-size: 13px; }
.cols { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 20px; padding-bottom: 24px; }
.cols h4 { margin: 0 0 10px; font-size: 12px; font-weight: 700; text-transform: uppercase; color: var(--color-text); }
.cols h4.gap { margin-top: 16px; }
.cols a { display: block; color: var(--color-text-muted); padding: 4px 0; }
.cols a:hover { color: var(--color-primary); }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chips span { background: white; border: 1px solid rgba(0, 0, 0, 0.09); border-radius: 2px; padding: 4px 8px; font-size: 12px; color: var(--color-text-muted); }
.app { margin: 0; color: var(--color-text-muted); line-height: 1.5; }
.bottom { border-top: 1px solid rgba(0, 0, 0, 0.06); padding: 14px 0; color: var(--color-text-faint); font-size: 12px; }
.bottom-inner { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
@media (max-width: 900px) { .cols { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 600px) { .cols { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
