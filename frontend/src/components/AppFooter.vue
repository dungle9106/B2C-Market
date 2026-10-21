<template>
  <footer class="footer">
    <div class="container cols">
      <div>
        <h4>Chăm sóc khách hàng</h4>
        <router-link to="/support">Trung tâm hỗ trợ</router-link>
        <router-link to="/policies/return">Đổi trả & hoàn tiền</router-link>
      </div>
      <div>
        <h4>Về Shoppi</h4>
        <router-link to="/policies/terms">Điều khoản sử dụng</router-link>
        <router-link to="/policies/privacy">Chính sách bảo mật</router-link>
      </div>
      <div>
        <h4>Bán hàng</h4>
        <router-link to="/policies/seller">Quy định dành cho người bán</router-link>
        <router-link to="/policies/prohibited">Hàng cấm & hạn chế</router-link>
      </div>
    </div>
    <div class="copy">© {{ new Date().getFullYear() }} Shoppi · Đồ án tốt nghiệp</div>
  </footer>
</template>

<style scoped>
.footer { background: white; border-top: 4px solid var(--color-primary); margin-top: 24px; padding: 24px 0 12px; font-size: 13px; }
.cols { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.cols h4 { margin: 0 0 8px; font-size: 13px; text-transform: uppercase; color: var(--color-text); }
.cols a { display: block; color: var(--color-text-muted); padding: 3px 0; }
.cols a:hover { color: var(--color-primary); }
.copy { text-align: center; color: var(--color-text-faint); margin-top: 16px; font-size: 12px; }
@media (max-width: 600px) { .cols { grid-template-columns: 1fr 1fr; } }
</style>
