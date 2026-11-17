import React from 'react'
import { Text } from 'react-native'
import { createNativeStackNavigator } from '@react-navigation/native-stack'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { useAuth } from '../auth'
import { C, Loading } from '../ui'
import LoginScreen from '../common/LoginScreen'
import NotificationsScreen from '../common/NotificationsScreen'
import { ChatListScreen, ChatThreadScreen } from '../common/Chat'
import HomeScreen from './HomeScreen'
import SearchScreen, { ImageSearchScreen } from './SearchScreen'
import ProductScreen from './ProductScreen'
import ShopScreen from './ShopScreen'
import CartScreen from './CartScreen'
import CheckoutScreen from './CheckoutScreen'
import OrdersScreen, { OrderDetailScreen, ReviewScreen } from './OrdersScreen'
import AddressesScreen, { AddressFormScreen } from './AddressesScreen'
import LuckySpinScreen from './LuckySpinScreen'
import VideosScreen from './VideosScreen'
import ReturnRequestScreen from './ReturnScreens'
import ChangePasswordScreen from '../common/ChangePasswordScreen'
import AccountScreen, { AIChatScreen, CoinsScreen, FollowingScreen, LikesScreen, PriceWatchesScreen, VoucherWalletScreen, WalletScreen } from './AccountScreens'

const Stack = createNativeStackNavigator()
const Tab = createBottomTabNavigator()

// notification links are web routes: '/orders/12', '/products/5', '/shops/3'
function openLink(link) {
  const m = /^\/(orders|products|shops)\/(\d+)/.exec(link || '')
  if (!m) return link?.startsWith('/account/vouchers') ? ['VoucherWallet'] : link?.startsWith('/account/coins') ? ['Coins'] : null
  return [{ orders: 'OrderDetail', products: 'Product', shops: 'Shop' }[m[1]], { id: Number(m[2]) }]
}

const icon = (glyph) => ({ color }) => <Text style={{ fontSize: 20, color }}>{glyph}</Text>

function Tabs() {
  return (
    <Tab.Navigator screenOptions={{ tabBarActiveTintColor: C.primary, headerTintColor: C.text }}>
      <Tab.Screen name="Home" component={HomeScreen} options={{ title: 'Trang chủ', headerShown: false, tabBarIcon: icon('🏠') }} />
      <Tab.Screen name="Videos" component={VideosScreen} options={{ title: 'Video', headerShown: false, tabBarIcon: icon('▶️') }} />
      <Tab.Screen name="Notifications" options={{ title: 'Thông báo', tabBarIcon: icon('🔔') }}>
        {(props) => <NotificationsScreen {...props} openLink={openLink} />}
      </Tab.Screen>
      <Tab.Screen name="Chats" options={{ title: 'Tin nhắn', tabBarIcon: icon('💬') }}>
        {(props) => <ChatListScreen {...props} side="buyer" />}
      </Tab.Screen>
      <Tab.Screen name="Account" component={AccountScreen} options={{ title: 'Tôi', headerShown: false, tabBarIcon: icon('👤') }} />
    </Tab.Navigator>
  )
}

export default function BuyerApp() {
  const { ready, user } = useAuth()
  if (!ready) return <Loading />
  if (!user) return <LoginScreen title="Shoppi" subtitle="Mua sắm online – Sàn TMĐT tích hợp AI" />
  return (
    <Stack.Navigator screenOptions={{ headerTintColor: C.primary, headerTitleStyle: { color: C.text } }}>
      <Stack.Screen name="Tabs" component={Tabs} options={{ headerShown: false }} />
      <Stack.Screen name="Search" component={SearchScreen} options={{ title: 'Tìm kiếm' }} />
      <Stack.Screen name="ImageSearch" component={ImageSearchScreen} options={{ title: 'Tìm bằng hình ảnh' }} />
      <Stack.Screen name="Product" component={ProductScreen} options={{ title: 'Chi tiết sản phẩm' }} />
      <Stack.Screen name="Shop" component={ShopScreen} options={{ title: 'Shop' }} />
      <Stack.Screen name="Cart" component={CartScreen} options={{ title: 'Giỏ hàng' }} />
      <Stack.Screen name="Checkout" component={CheckoutScreen} options={{ title: 'Thanh toán' }} />
      <Stack.Screen name="Orders" component={OrdersScreen} options={{ title: 'Đơn mua' }} />
      <Stack.Screen name="OrderDetail" component={OrderDetailScreen} options={{ title: 'Thông tin đơn hàng' }} />
      <Stack.Screen name="Review" component={ReviewScreen} options={{ title: 'Đánh giá sản phẩm' }} />
      <Stack.Screen name="Addresses" component={AddressesScreen} options={{ title: 'Địa chỉ của tôi' }} />
      <Stack.Screen name="AddressForm" component={AddressFormScreen} options={{ title: 'Địa chỉ mới' }} />
      <Stack.Screen name="VoucherWallet" component={VoucherWalletScreen} options={{ title: 'Kho Voucher' }} />
      <Stack.Screen name="Coins" component={CoinsScreen} options={{ title: 'Shoppi Xu' }} />
      <Stack.Screen name="ReturnRequest" component={ReturnRequestScreen} options={{ title: 'Trả hàng/Hoàn tiền' }} />
      <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} options={{ title: 'Đổi mật khẩu' }} />
      <Stack.Screen name="LuckySpin" component={LuckySpinScreen} options={{ title: 'Vòng quay may mắn' }} />
      <Stack.Screen name="Wallet" component={WalletScreen} options={{ title: 'Ví ShoppiPay' }} />
      <Stack.Screen name="Likes" component={LikesScreen} options={{ title: 'Đã thích' }} />
      <Stack.Screen name="PriceWatches" component={PriceWatchesScreen} options={{ title: 'Theo dõi giá' }} />
      <Stack.Screen name="Following" component={FollowingScreen} options={{ title: 'Shop đang theo dõi' }} />
      <Stack.Screen name="AIChat" component={AIChatScreen} options={{ title: '🤖 Trợ lý mua sắm AI' }} />
      <Stack.Screen name="ChatThread" options={({ route }) => ({ title: route.params?.title || 'Chat' })}>
        {(props) => <ChatThreadScreen {...props} side="buyer" />}
      </Stack.Screen>
    </Stack.Navigator>
  )
}
