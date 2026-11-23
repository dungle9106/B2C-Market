import React from 'react'
import { Text } from 'react-native'
import { createNativeStackNavigator } from '@react-navigation/native-stack'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { useAuth } from '../auth'
import { Button, C, Empty, Loading } from '../ui'
import LoginScreen from '../common/LoginScreen'
import NotificationsScreen from '../common/NotificationsScreen'
import { usePushNotifications } from '../push'
import { ChatListScreen, ChatThreadScreen } from '../common/Chat'
import DashboardScreen, { PerformanceScreen, RegisterShopScreen, SalesAssistantScreen, SellerAccountScreen } from './DashboardScreens'
import SellerOrdersScreen, { SellerOrderScreen } from './OrdersScreens'
import SellerProductsScreen, { ProductFormScreen } from './ProductsScreens'
import { ReviewInboxScreen, SellerReturnsScreen, StockForecastScreen } from './AfterSalesScreens'
import ChangePasswordScreen from '../common/ChangePasswordScreen'
import SecurityScreen from '../common/SecurityScreen'
import { HelpCenterScreen, NewTicketScreen, PoliciesScreen, PolicyScreen, TicketScreen } from '../common/SupportScreens'
import { SellerAddOnDealsScreen, SellerAdsScreen, SellerCombosScreen, SellerFlashEventsScreen, SellerFlashSaleScreen, SellerVouchersScreen } from './MarketingScreens'
import { CareSettingsScreen, SellerQuestionsScreen, SellerVideosScreen, ShopCategoriesScreen, ShopPoliciesScreen, ShopSettingsScreen, ShopVerificationScreen } from './ShopScreens'
import { AIReviewsScreen, AnalyticsScreen, FinanceScreen, InventoryScreen, PromoTimingScreen } from './ReportScreens'

const Stack = createNativeStackNavigator()
const Tab = createBottomTabNavigator()

// notification links are web routes: '/seller/orders/12', '/seller/orders?status=...', '/seller/products...'
function openLink(link = '') {
  const m = /^\/seller\/orders\/(\d+)/.exec(link || '')
  if (m) return ['SellerOrder', { id: Number(m[1]) }]
  if (link?.startsWith('/seller/returns')) return ['SellerReturns']
  if (link?.startsWith('/seller/review-hub')) return ['ReviewInbox']
  if (link?.startsWith('/seller/inventory/forecast')) return ['StockForecast']
  if (link?.startsWith('/seller/inventory')) return ['Inventory']
  if (link?.startsWith('/seller/questions')) return ['SellerQuestions']
  if (link?.startsWith('/seller/finance')) return ['Finance']
  if (link?.startsWith('/seller/vouchers')) return ['SellerVouchers']
  if (link?.startsWith('/seller/flash-events')) return ['SellerFlashEvents']
  if (link?.startsWith('/seller/flash-sale')) return ['SellerFlashSale']
  if (link?.startsWith('/seller/ads')) return ['SellerAds']
  if (link?.startsWith('/seller/videos')) return ['SellerVideos']
  if (link?.startsWith('/seller/verification')) return ['ShopVerification']
  if (link?.startsWith('/seller/shop')) return ['ShopSettings']
  if (link?.startsWith('/seller/analytics')) return ['Analytics']
  const t = /^\/(?:seller\/support|help\/tickets)\/(\d+)/.exec(link || '')
  if (t) return ['Ticket', { id: Number(t[1]) }]
  if (link?.startsWith('/seller/orders')) return ['SellerTabs', { screen: 'SellerOrders' }]
  if (link?.startsWith('/seller/products')) return ['SellerTabs', { screen: 'SellerProducts' }]
  if (link?.startsWith('/seller/performance')) return ['Performance']
  return null
}

const PUSH_FALLBACK = ['SellerNotifications']

const icon = (glyph) => ({ color }) => <Text style={{ fontSize: 20, color }}>{glyph}</Text>

function SellerTabs() {
  return (
    <Tab.Navigator screenOptions={{ tabBarActiveTintColor: C.primary }}>
      <Tab.Screen name="Dashboard" component={DashboardScreen} options={{ title: 'Tổng quan', headerShown: false, tabBarIcon: icon('📈') }} />
      <Tab.Screen name="SellerOrders" component={SellerOrdersScreen} options={{ title: 'Đơn hàng', tabBarIcon: icon('📦') }} />
      <Tab.Screen name="SellerProducts" component={SellerProductsScreen} options={{ title: 'Sản phẩm', tabBarIcon: icon('🏷') }} />
      <Tab.Screen name="SellerChats" options={{ title: 'Chat', tabBarIcon: icon('💬') }}>
        {(props) => <ChatListScreen {...props} side="seller" />}
      </Tab.Screen>
      <Tab.Screen name="SellerAccount" component={SellerAccountScreen} options={{ title: 'Tài khoản', headerShown: false, tabBarIcon: icon('🏪') }} />
    </Tab.Navigator>
  )
}

export default function SellerApp() {
  const { ready, user, signOut } = useAuth()
  usePushNotifications(user?.role === 'SELLER', openLink, PUSH_FALLBACK)
  if (!ready) return <Loading />
  if (!user) return <LoginScreen title="Shoppi" subtitle="Kênh Người Bán" />
  if (user.role === 'BUYER') return <RegisterShopScreen />
  if (user.role !== 'SELLER') {
    return (
      <>
        <Empty text="Tài khoản quản trị không dùng app Người Bán. Vui lòng dùng trang quản trị trên web." />
        <Button title="Đăng xuất" kind="ghost" onPress={signOut} style={{ margin: 20 }} />
      </>
    )
  }
  return (
    <Stack.Navigator screenOptions={{ headerTintColor: C.primary, headerTitleStyle: { color: C.text } }}>
      <Stack.Screen name="SellerTabs" component={SellerTabs} options={{ headerShown: false }} />
      <Stack.Screen name="SellerOrder" component={SellerOrderScreen} options={{ title: 'Chi tiết đơn hàng' }} />
      <Stack.Screen name="ProductForm" component={ProductFormScreen} options={{ title: 'Sản phẩm' }} />
      <Stack.Screen name="SellerReturns" component={SellerReturnsScreen} options={{ title: 'Trả hàng/Hoàn tiền' }} />
      <Stack.Screen name="StockForecast" component={StockForecastScreen} options={{ title: '✨ Dự báo nhập hàng' }} />
      <Stack.Screen name="ReviewInbox" component={ReviewInboxScreen} options={{ title: 'Đánh giá của khách' }} />
      <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} options={{ title: 'Đổi mật khẩu' }} />
      <Stack.Screen name="Performance" component={PerformanceScreen} options={{ title: 'Hiệu quả hoạt động' }} />
      <Stack.Screen name="SalesAssistant" component={SalesAssistantScreen} options={{ title: '🤖 Trợ lý bán hàng AI' }} />
      <Stack.Screen name="SellerVouchers" component={SellerVouchersScreen} options={{ title: 'Mã giảm giá của Shop' }} />
      <Stack.Screen name="SellerFlashSale" component={SellerFlashSaleScreen} options={{ title: 'Flash Sale của Shop' }} />
      <Stack.Screen name="SellerFlashEvents" component={SellerFlashEventsScreen} options={{ title: 'Shoppi Flash Sale' }} />
      <Stack.Screen name="SellerCombos" component={SellerCombosScreen} options={{ title: 'Combo khuyến mãi' }} />
      <Stack.Screen name="SellerAddOnDeals" component={SellerAddOnDealsScreen} options={{ title: 'Mua kèm deal sốc' }} />
      <Stack.Screen name="SellerAds" component={SellerAdsScreen} options={{ title: 'Quảng cáo Shoppi' }} />
      <Stack.Screen name="SellerVideos" component={SellerVideosScreen} options={{ title: 'Shoppi Video' }} />
      <Stack.Screen name="SellerQuestions" component={SellerQuestionsScreen} options={{ title: 'Hỏi đáp sản phẩm' }} />
      <Stack.Screen name="CareSettings" component={CareSettingsScreen} options={{ title: 'Tin nhắn tự động & mẫu' }} />
      <Stack.Screen name="ShopSettings" component={ShopSettingsScreen} options={{ title: 'Hồ sơ Shop' }} />
      <Stack.Screen name="ShopPolicies" component={ShopPoliciesScreen} options={{ title: 'Chính sách Shop' }} />
      <Stack.Screen name="ShopVerification" component={ShopVerificationScreen} options={{ title: 'Xác minh Shop' }} />
      <Stack.Screen name="ShopCategories" component={ShopCategoriesScreen} options={{ title: 'Danh mục của Shop' }} />
      <Stack.Screen name="Analytics" component={AnalyticsScreen} options={{ title: 'Phân tích bán hàng' }} />
      <Stack.Screen name="Finance" component={FinanceScreen} options={{ title: 'Doanh thu & Đối soát' }} />
      <Stack.Screen name="AIReviews" component={AIReviewsScreen} options={{ title: '✨ AI phân tích đánh giá' }} />
      <Stack.Screen name="PromoTiming" component={PromoTimingScreen} options={{ title: '🗓 Thời điểm khuyến mãi' }} />
      <Stack.Screen name="Inventory" component={InventoryScreen} options={{ title: 'Tồn kho' }} />
      <Stack.Screen name="Security" component={SecurityScreen} options={{ title: 'Bảo mật tài khoản' }} />
      <Stack.Screen name="HelpCenter" component={HelpCenterScreen} options={{ title: 'Hỗ trợ từ Shoppi' }} />
      <Stack.Screen name="NewTicket" options={{ title: 'Gửi yêu cầu hỗ trợ' }}>
        {(props) => <NewTicketScreen {...props} route={{ ...props.route, params: { seller: true, ...props.route.params } }} />}
      </Stack.Screen>
      <Stack.Screen name="Ticket" component={TicketScreen} options={{ title: 'Yêu cầu hỗ trợ' }} />
      <Stack.Screen name="Policies" component={PoliciesScreen} options={{ title: 'Chính sách Shoppi' }} />
      <Stack.Screen name="Policy" component={PolicyScreen} options={({ route }) => ({ title: route.params?.title || 'Chính sách' })} />
      <Stack.Screen name="SellerNotifications" options={{ title: 'Thông báo' }}>
        {(props) => <NotificationsScreen {...props} openLink={openLink} />}
      </Stack.Screen>
      <Stack.Screen name="ChatThread" options={({ route }) => ({ title: route.params?.title || 'Chat' })}>
        {(props) => <ChatThreadScreen {...props} side="seller" />}
      </Stack.Screen>
    </Stack.Navigator>
  )
}
