import React from 'react'
import { StatusBar } from 'expo-status-bar'
import Constants from 'expo-constants'
import { NavigationContainer, DefaultTheme } from '@react-navigation/native'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { AuthProvider } from './src/auth'
import { navigationRef } from './src/push'
import { C } from './src/ui'
import BuyerApp from './src/buyer/BuyerApp'
import SellerApp from './src/seller/SellerApp'

// APP_VARIANT at build time decides which app this is (app.config.js)
const isSeller = Constants.expoConfig?.extra?.variant === 'seller'
const theme = { ...DefaultTheme, colors: { ...DefaultTheme.colors, primary: C.primary, background: C.bg } }

export default function App() {
  return (
    <SafeAreaProvider>
      <AuthProvider>
        <NavigationContainer ref={navigationRef} theme={theme}>
          {isSeller ? <SellerApp /> : <BuyerApp />}
        </NavigationContainer>
      </AuthProvider>
      <StatusBar style="auto" />
    </SafeAreaProvider>
  )
}
