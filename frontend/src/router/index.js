import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  // ==== Public / Buyer ====
  { path: '/', name: 'home', component: () => import('../views/buyer/HomeView.vue') },
  { path: '/login', name: 'login', component: () => import('../views/AuthView.vue'), meta: { mode: 'login' } },
  { path: '/forgot-password', name: 'forgot-password', component: () => import('../views/ForgotPasswordView.vue') },
  { path: '/reset-password', name: 'reset-password', component: () => import('../views/ResetPasswordView.vue') },
  { path: '/verify-email', name: 'verify-email', component: () => import('../views/VerifyEmailView.vue') },
  { path: '/account/security', name: 'account-security', component: () => import('../views/buyer/AccountSecurityView.vue'), meta: { requiresAuth: true } },
  { path: '/register', name: 'register', component: () => import('../views/AuthView.vue'), meta: { mode: 'register' } },
  // backend redirects here after Google OAuth2 login with #token=... or #error=...
  { path: '/oauth2/redirect', name: 'oauth2-redirect', component: () => import('../views/OAuth2RedirectView.vue') },
  { path: '/products/:id', name: 'product-detail', component: () => import('../views/buyer/ProductDetailView.vue') },
  { path: '/search', name: 'search', component: () => import('../views/buyer/SearchView.vue') },
  { path: '/category/:id', name: 'category', component: () => import('../views/buyer/CategoryView.vue') },
  // public shop page: profile, verified badge, policies, products
  { path: '/policies/:slug', name: 'policy', component: () => import('../views/buyer/PolicyView.vue') },
  { path: '/campaigns/:slug', name: 'campaign', component: () => import('../views/buyer/CampaignView.vue') },
  { path: '/shop/:id', name: 'shop', component: () => import('../views/buyer/ShopView.vue') },

  { path: '/cart', name: 'cart', component: () => import('../views/buyer/CartView.vue'), meta: { requiresRole: 'BUYER' } },
  // ?items=<cartItemIds> picked on the cart page (may span several shops)
  { path: '/checkout', name: 'checkout', component: () => import('../views/buyer/CheckoutView.vue'), meta: { requiresRole: 'BUYER' } },
  // simulated payment gateway for one checkout (e-wallet / card / bank transfer)
  { path: '/payment/vnpay-return', name: 'vnpay-return', component: () => import('../views/buyer/VnpayReturnView.vue') },
  { path: '/payment/:code', name: 'payment', component: () => import('../views/buyer/PaymentView.vue'), meta: { requiresRole: 'BUYER' } },
  { path: '/orders/:id', name: 'order-detail', component: () => import('../views/buyer/OrderDetailView.vue'), meta: { requiresRole: 'BUYER' } },
  { path: '/orders', name: 'my-orders', component: () => import('../views/buyer/OrdersView.vue'), meta: { requiresRole: 'BUYER' } },
  { path: '/chat', name: 'chatbot', component: () => import('../views/buyer/ChatbotView.vue'), meta: { requiresRole: 'BUYER' } },
  // chat with shops; ?shopId=&productId= / ?shopId=&orderId= starts a thread with a card attached, ?c= opens one
  { path: '/messages', name: 'messages', component: () => import('../views/buyer/MessagesView.vue'), meta: { requiresRole: 'BUYER' } },

  // ==== Any logged-in user ====
  { path: '/notifications', name: 'notifications', component: () => import('../views/NotificationsView.vue'), meta: { requiresAuth: true } },
  // help centre: FAQ is public, tickets need an account
  { path: '/help', name: 'help', component: () => import('../views/support/HelpCenterView.vue') },
  { path: '/help/new', name: 'help-new', component: () => import('../views/support/NewTicketView.vue'), meta: { requiresAuth: true } },
  { path: '/help/tickets/:id', name: 'help-ticket', component: () => import('../views/support/TicketDetailView.vue'), meta: { requiresAuth: true } },

  // ==== Seller ====
  // "Kenh Nguoi Ban" entry pages (public). Signup = create account, then shop
  // info; a logged-in BUYER skips straight to the shop step.
  { path: '/seller/login', name: 'seller-login', component: () => import('../views/seller/SellerAuthView.vue'), meta: { mode: 'login' } },
  { path: '/seller/register', name: 'seller-register', component: () => import('../views/seller/SellerAuthView.vue'), meta: { mode: 'register' } },
  { path: '/become-seller', redirect: '/seller/register' },
  // shipping labels: a bare page (no seller centre chrome) so only the labels print
  { path: '/seller/print-labels', name: 'seller-print-labels', component: () => import('../views/seller/PrintLabelsView.vue'), meta: { requiresRole: 'SELLER' } },
  // seller centre: every page shares SellerLayout (header + sidebar)
  {
    path: '/seller',
    component: () => import('../layouts/SellerLayout.vue'),
    meta: { requiresRole: 'SELLER' },
    children: [
      { path: '', name: 'seller-dashboard', component: () => import('../views/seller/DashboardView.vue'), meta: { title: 'Tổng quan' } },
      { path: 'security', name: 'seller-security', component: () => import('../views/AccountSecurityPage.vue'), meta: { title: 'Bảo mật tài khoản' } },
      { path: 'shop', name: 'seller-shop', component: () => import('../views/seller/ShopSettingsView.vue'), meta: { title: 'Hồ sơ Shop' } },
      { path: 'policies', name: 'seller-policies', component: () => import('../views/seller/ShopPoliciesView.vue'), meta: { title: 'Chính sách Shop' } },
      { path: 'verification', name: 'seller-verification', component: () => import('../views/seller/ShopVerificationView.vue'), meta: { title: 'Xác minh Shop' } },
      { path: 'products', name: 'seller-products', component: () => import('../views/seller/ProductsView.vue'), meta: { title: 'Sản phẩm' } },
      { path: 'products/import', name: 'seller-product-import', component: () => import('../views/seller/ProductImportView.vue'), meta: { title: 'Đăng hàng loạt' } },
      { path: 'categories', name: 'seller-categories', component: () => import('../views/seller/ShopCategoriesView.vue'), meta: { title: 'Danh mục của Shop' } },
      { path: 'products/new', name: 'seller-product-new', component: () => import('../views/seller/ProductFormView.vue'), meta: { title: 'Thêm sản phẩm' } },
      { path: 'products/:id/edit', name: 'seller-product-edit', component: () => import('../views/seller/ProductFormView.vue'), meta: { title: 'Sửa sản phẩm' } },
      { path: 'finance', name: 'seller-finance', component: () => import('../views/seller/SellerFinanceView.vue'), meta: { title: 'Doanh thu & Đối soát' } },
      { path: 'returns', name: 'seller-returns', component: () => import('../views/seller/SellerReturnsView.vue'), meta: { title: 'Trả hàng/Hoàn tiền' } },
      { path: 'flash-events', name: 'seller-flash-events', component: () => import('../views/seller/SellerFlashEventsView.vue'), meta: { title: 'Flash Sale Của Shoppi' } },
      { path: 'flash-sale', name: 'seller-flash-sale', component: () => import('../views/seller/SellerFlashSaleView.vue'), meta: { title: 'Flash Sale' } },
      { path: 'combos', name: 'seller-combos', component: () => import('../views/seller/SellerCombosView.vue'), meta: { title: 'Combo khuyến mãi' } },
      { path: 'ads', name: 'seller-ads', component: () => import('../views/seller/SellerAdsView.vue'), meta: { title: 'Quảng cáo' } },
      { path: 'vouchers', name: 'seller-vouchers', component: () => import('../views/seller/SellerVouchersView.vue'), meta: { title: 'Mã giảm giá' } },
      { path: 'ai/assistant', name: 'seller-ai-assistant', component: () => import('../views/seller/ai/SellerAIAssistantView.vue'), meta: { title: 'Trợ lý doanh thu AI' } },
      { path: 'ai/reviews', name: 'seller-ai-reviews', component: () => import('../views/seller/ai/SellerAIReviewsView.vue'), meta: { title: 'AI phân tích đánh giá' } },
      { path: 'ai/promotions', name: 'seller-ai-promo', component: () => import('../views/seller/ai/SellerAIPromoView.vue'), meta: { title: 'Gợi ý thời điểm khuyến mãi' } },
      { path: 'care-settings', name: 'seller-care-settings', component: () => import('../views/seller/SellerCareSettingsView.vue'), meta: { title: 'Tin nhắn tự động & mẫu' } },
      { path: 'questions', name: 'seller-questions', component: () => import('../views/seller/SellerQuestionsView.vue'), meta: { title: 'Hỏi đáp' } },
      { path: 'chat', name: 'seller-chat', component: () => import('../views/seller/SellerChatView.vue'), meta: { title: 'Chat' } },
      { path: 'notifications', name: 'seller-notifications', component: () => import('../views/seller/SellerNotificationsView.vue'), meta: { title: 'Thông báo' } },
      { path: 'support', name: 'seller-support', component: () => import('../views/seller/SellerSupportView.vue'), meta: { title: 'Hỗ trợ' } },
      { path: 'support/:id', name: 'seller-ticket', component: () => import('../views/seller/SellerTicketView.vue'), meta: { title: 'Hỗ trợ' } },
      { path: 'orders/:id', name: 'seller-order-detail', component: () => import('../views/seller/SellerOrderDetailView.vue'), meta: { title: 'Chi tiết đơn hàng' } },
      { path: 'analytics', redirect: { name: 'seller-analytics-revenue' } },
      { path: 'analytics/revenue', name: 'seller-analytics-revenue', component: () => import('../views/seller/analytics/RevenueReportView.vue'), meta: { title: 'Phân tích doanh thu' } },
      { path: 'analytics/products', name: 'seller-analytics-products', component: () => import('../views/seller/analytics/ProductReportView.vue'), meta: { title: 'Sản phẩm & Tồn kho' } },
      { path: 'analytics/traffic', name: 'seller-analytics-traffic', component: () => import('../views/seller/analytics/TrafficReportView.vue'), meta: { title: 'Lượt xem & Chuyển đổi' } },
      { path: 'analytics/reviews', name: 'seller-analytics-reviews', component: () => import('../views/seller/analytics/ReviewReportView.vue'), meta: { title: 'Đánh giá khách hàng' } },
      { path: 'inventory', name: 'seller-inventory', component: () => import('../views/seller/InventoryView.vue'), meta: { title: 'Tồn kho' } },
      { path: 'orders', name: 'seller-orders', component: () => import('../views/seller/SellerOrdersView.vue'), meta: { title: 'Đơn hàng' } }
    ]
  },

  // ==== Admin ====
  // admin console: AdminLayout (header + sidebar); meta.perm = the AdminPermission a page needs
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresRole: 'ADMIN' },
    children: [
      { path: '', name: 'admin-dashboard', component: () => import('../views/admin/AdminDashboardView.vue') },
      { path: 'users', name: 'admin-users', component: () => import('../views/admin/AdminUsersView.vue'), meta: { perm: 'USERS' } },
      { path: 'users/:id', name: 'admin-user', component: () => import('../views/admin/AdminUserDetailView.vue'), meta: { perm: 'USERS' } },
      { path: 'shops', name: 'admin-shops', component: () => import('../views/admin/AdminShopsView.vue'), meta: { perm: 'SHOPS' } },
      { path: 'products', name: 'admin-products', component: () => import('../views/admin/AdminProductsView.vue'), meta: { perm: 'PRODUCTS' } },
      { path: 'categories', name: 'admin-categories', component: () => import('../views/admin/AdminCategoriesView.vue'), meta: { perm: 'PRODUCTS' } },
      { path: 'verifications', name: 'admin-verifications', component: () => import('../views/admin/AdminVerificationsView.vue'), meta: { perm: 'SHOPS' } },
      { path: 'orders', name: 'admin-orders', component: () => import('../views/admin/AdminOrdersView.vue'), meta: { perm: 'ORDERS' } },
      { path: 'disputes', name: 'admin-disputes', component: () => import('../views/admin/AdminDisputesView.vue'), meta: { perm: 'ORDERS' } },
      // the old complaints page became the dispute centre
      { path: 'complaints', redirect: { name: 'admin-disputes' } },
      { path: 'refunds', name: 'admin-refunds', component: () => import('../views/admin/AdminRefundsView.vue'), meta: { perm: 'FINANCE' } },
      { path: 'settlements', name: 'admin-settlements', component: () => import('../views/admin/AdminSettlementsView.vue'), meta: { perm: 'FINANCE' } },
      { path: 'reconciliation', name: 'admin-reconciliation', component: () => import('../views/admin/AdminReconciliationView.vue'), meta: { perm: 'FINANCE' } },
      { path: 'vouchers', name: 'admin-vouchers', component: () => import('../views/admin/AdminVouchersView.vue'), meta: { perm: 'VOUCHERS' } },
      { path: 'reports', name: 'admin-report-overview', component: () => import('../views/admin/reports/AdminReportOverviewView.vue'), meta: { perm: 'REPORTS' } },
      { path: 'reports/top', name: 'admin-report-top', component: () => import('../views/admin/reports/AdminReportTopView.vue'), meta: { perm: 'REPORTS' } },
      { path: 'reports/growth', name: 'admin-report-growth', component: () => import('../views/admin/reports/AdminReportGrowthView.vue'), meta: { perm: 'REPORTS' } },
      { path: 'campaigns', name: 'admin-campaigns', component: () => import('../views/admin/AdminCampaignsView.vue'), meta: { perm: 'VOUCHERS' } },
      { path: 'banners', name: 'admin-banners', component: () => import('../views/admin/AdminBannersView.vue'), meta: { perm: 'VOUCHERS' } },
      { path: 'flash-sales', name: 'admin-flash-sales', component: () => import('../views/admin/AdminFlashSalesView.vue'), meta: { perm: 'VOUCHERS' } },
      { path: 'support', name: 'admin-support', component: () => import('../views/admin/AdminSupportView.vue'), meta: { perm: 'SUPPORT' } },
      { path: 'notifications', name: 'admin-notifications', component: () => import('../views/admin/AdminBroadcastView.vue'), meta: { perm: 'BROADCAST' } },
      { path: 'staff', name: 'admin-staff', component: () => import('../views/admin/AdminStaffView.vue'), meta: { perm: 'STAFF' } },
      { path: 'audit', name: 'admin-audit', component: () => import('../views/admin/AdminAuditView.vue'), meta: { perm: 'STAFF' } },
      { path: 'settings', name: 'admin-settings', component: () => import('../views/admin/AdminSettingsView.vue'), meta: { perm: 'SETTINGS' } },
      { path: 'policies', name: 'admin-policies', component: () => import('../views/admin/AdminPoliciesView.vue'), meta: { perm: 'SETTINGS' } },
      { path: 'ai', name: 'admin-ai', component: () => import('../views/admin/AdminAiView.vue'), meta: { perm: 'SETTINGS' } },
      { path: 'security', name: 'admin-security', component: () => import('../views/AccountSecurityPage.vue') },
      { path: 'gateways', name: 'admin-gateways', component: () => import('../views/admin/AdminGatewaysView.vue'), meta: { perm: 'SETTINGS' } },
      { path: 'email', name: 'admin-email', component: () => import('../views/admin/AdminEmailView.vue'), meta: { perm: 'SETTINGS' } },
      { path: 'monitoring', name: 'admin-monitoring', component: () => import('../views/admin/AdminMonitoringView.vue'), meta: { perm: 'SETTINGS' } }
    ]
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresRole) {
    const sellerArea = to.meta.requiresRole === 'SELLER'
    if (!auth.isLoggedIn) {
      return { name: sellerArea ? 'seller-login' : 'login', query: { redirect: to.fullPath } }
    }
    if (auth.role !== to.meta.requiresRole) {
      // a buyer heading into the seller channel hasn't opened a shop yet
      if (sellerArea && auth.isBuyer) return { name: 'seller-register' }
      return { name: 'home' }
    }
  }
  return true
})

export default router
