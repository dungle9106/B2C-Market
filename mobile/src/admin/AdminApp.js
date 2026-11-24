// Shoppi Admin (APP_VARIANT=admin): the platform staff's phone app. Moderation and day-to-day operations;
// settings, staff, banners, campaigns, settlements and Excel reports stay on the web console.
import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Text, View } from 'react-native'
import { createNativeStackNavigator } from '@react-navigation/native-stack'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { get } from '../api'
import { useAuth } from '../auth'
import { Button, C, Empty, Loading, styles } from '../ui'
import { usePushNotifications } from '../push'
import LoginScreen from '../common/LoginScreen'
import NotificationsScreen from '../common/NotificationsScreen'
import ChangePasswordScreen from '../common/ChangePasswordScreen'
import SecurityScreen from '../common/SecurityScreen'
import { PoliciesScreen, PolicyScreen } from '../common/SupportScreens'
import { AdminContext } from './common'
import {
  AdminAccountScreen, AdminDashboardScreen, AdminDisputeScreen, AdminDisputesScreen, AdminModerationScreen, AdminOrderScreen,
  AdminOrdersScreen, AdminProductScreen, AdminProductsScreen, AdminRefundsScreen, AdminShopsScreen, AdminTicketScreen,
  AdminTicketsScreen, AdminUserScreen, AdminUsersScreen, AdminVerificationsScreen
} from './AdminScreens'

const Stack = createNativeStackNavigator()
const Tab = createBottomTabNavigator()

// notification links are web routes ('/admin/disputes?id=5', '/admin/products?status=PENDING', ...)
function openLink(link = '') {
  const id = /[?&]id=(\d+)/.exec(link || '')?.[1]
  if (link?.startsWith('/admin/disputes')) return id ? ['AdminDispute', { id: Number(id) }] : ['AdminDisputes']
  if (link?.startsWith('/admin/products')) return id ? ['AdminProduct', { id: Number(id) }] : ['AdminProducts']
  if (link?.startsWith('/admin/shops')) return ['AdminShops']
  if (link?.startsWith('/admin/verifications')) return ['AdminVerifications']
  if (link?.startsWith('/admin/refunds')) return ['AdminRefunds']
  const t = /^\/admin\/support(?:\/tickets)?\/(\d+)/.exec(link || '') || (link?.startsWith('/admin/support') && id ? [0, id] : null)
  if (t) return ['AdminTicket', { id: Number(t[1]) }]
  if (link?.startsWith('/admin/support')) return ['AdminTickets']
  const u = /^\/admin\/users\/(\d+)/.exec(link || '')
  if (u) return ['AdminUser', { id: Number(u[1]) }]
  if (link?.startsWith('/admin/orders')) return id ? ['AdminOrder', { id: Number(id) }] : ['AdminTabs', { screen: 'AdminOrders' }]
  return null
}
const PUSH_FALLBACK = ['AdminNotifications']
const icon = (glyph) => ({ color }) => <Text style={{ fontSize: 20, color }}>{glyph}</Text>

function AdminTabs() {
  return (
    <Tab.Navigator screenOptions={{ tabBarActiveTintColor: C.primary }}>
      <Tab.Screen name="AdminHome" component={AdminDashboardScreen} options={{ title: 'Tổng quan', headerShown: false, tabBarIcon: icon('📊') }} />
      <Tab.Screen name="AdminModeration" component={AdminModerationScreen} options={{ title: 'Kiểm duyệt', tabBarIcon: icon('🛡') }} />
      <Tab.Screen name="AdminOrders" component={AdminOrdersScreen} options={{ title: 'Đơn hàng', tabBarIcon: icon('📦') }} />
      <Tab.Screen name="AdminUsers" component={AdminUsersScreen} options={{ title: 'Người dùng', tabBarIcon: icon('👥') }} />
      <Tab.Screen name="AdminAccount" component={AdminAccountScreen} options={{ title: 'Tài khoản', headerShown: false, tabBarIcon: icon('⚙️') }} />
    </Tab.Navigator>
  )
}

export default function AdminApp() {
  const { ready, user, signOut } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [me, setMe] = useState(null)
  const [meError, setMeError] = useState(null)
  usePushNotifications(isAdmin, openLink, PUSH_FALLBACK)

  const reloadMe = useCallback(() => {
    if (!isAdmin) return
    get('/admin/me').then((m) => { setMe(m); setMeError(null) }).catch((e) => setMeError(e.message))
  }, [isAdmin])
  useEffect(() => { setMe(null); reloadMe() }, [reloadMe, user?.userId])
  const ctx = useMemo(() => ({
    me,
    can: (p) => !!me && (me.superAdmin || (me.permissions || []).includes(p)),
    reloadMe
  }), [me, reloadMe])

  if (!ready) return <Loading />
  if (!user) return <LoginScreen title="Shoppi" subtitle="Kênh Quản Trị" allowRegister={false} />
  if (!isAdmin) {
    return (
      <View style={[styles.page, { justifyContent: 'center' }]}>
        <Empty text="App này chỉ dành cho nhân viên quản trị Shoppi. Người mua và người bán hãy dùng app Shoppi hoặc Shoppi Người Bán." />
        <Button title="Đăng xuất" kind="ghost" onPress={signOut} style={{ margin: 20 }} />
      </View>
    )
  }
  if (!me) return meError ? <Empty error={meError} onRetry={reloadMe} /> : <Loading />

  return (
    <AdminContext.Provider value={ctx}>
      <Stack.Navigator screenOptions={{ headerTintColor: C.primary, headerTitleStyle: { color: C.text } }}>
        {me.twoFactorSetupRequired ? (
          // the platform requires 2-step verification for admins: nothing else until it is on
          <Stack.Screen name="Security2fa" options={{ title: 'Bật xác thực 2 lớp' }}>
            {(props) => (
              <View style={{ flex: 1 }}>
                <Text style={{ backgroundColor: '#fff4e5', color: '#b35c00', padding: 12, fontWeight: '600' }}>
                  Sàn yêu cầu quản trị viên bật xác thực 2 lớp trước khi dùng app quản trị. Sau khi bật, kéo xuống để tải lại.
                </Text>
                <SecurityScreen {...props} />
                <Button title="Tôi đã bật, tiếp tục" onPress={reloadMe} style={{ margin: 12 }} />
              </View>
            )}
          </Stack.Screen>
        ) : (
          <>
            <Stack.Screen name="AdminTabs" component={AdminTabs} options={{ headerShown: false }} />
            <Stack.Screen name="AdminShops" component={AdminShopsScreen} options={{ title: 'Quản lý shop' }} />
            <Stack.Screen name="AdminVerifications" component={AdminVerificationsScreen} options={{ title: 'Xác minh shop' }} />
            <Stack.Screen name="AdminProducts" component={AdminProductsScreen} options={{ title: 'Kiểm duyệt sản phẩm' }} />
            <Stack.Screen name="AdminProduct" component={AdminProductScreen} options={{ title: 'Sản phẩm' }} />
            <Stack.Screen name="AdminOrder" component={AdminOrderScreen} options={{ title: 'Chi tiết đơn hàng' }} />
            <Stack.Screen name="AdminUser" component={AdminUserScreen} options={{ title: 'Tài khoản' }} />
            <Stack.Screen name="AdminDisputes" component={AdminDisputesScreen} options={{ title: 'Khiếu nại trả hàng' }} />
            <Stack.Screen name="AdminDispute" component={AdminDisputeScreen} options={{ title: 'Xử lý khiếu nại' }} />
            <Stack.Screen name="AdminRefunds" component={AdminRefundsScreen} options={{ title: 'Hoàn tiền' }} />
            <Stack.Screen name="AdminTickets" component={AdminTicketsScreen} options={{ title: 'Yêu cầu hỗ trợ' }} />
            <Stack.Screen name="AdminTicket" component={AdminTicketScreen} options={{ title: 'Yêu cầu hỗ trợ' }} />
            <Stack.Screen name="Security" component={SecurityScreen} options={{ title: 'Bảo mật tài khoản' }} />
            <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} options={{ title: 'Đổi mật khẩu' }} />
            <Stack.Screen name="Policies" component={PoliciesScreen} options={{ title: 'Chính sách Shoppi' }} />
            <Stack.Screen name="Policy" component={PolicyScreen} options={({ route }) => ({ title: route.params?.title || 'Chính sách' })} />
            <Stack.Screen name="AdminNotifications" options={{ title: 'Thông báo' }}>
              {(props) => <NotificationsScreen {...props} openLink={openLink} />}
            </Stack.Screen>
          </>
        )}
      </Stack.Navigator>
    </AdminContext.Provider>
  )
}
