// One codebase, two apps: APP_VARIANT=buyer (Shoppi) or seller (Shoppi Người Bán).
// Each variant has its own Android package, so both can be installed on the same phone.
const VARIANTS = {
  buyer: { name: 'Shoppi', slug: 'shoppi', package: 'vn.shoppi.buyer', color: '#ee4d2d' },
  seller: { name: 'Shoppi Người Bán', slug: 'shoppi-seller', package: 'vn.shoppi.seller', color: '#ee4d2d' }
}
const variant = process.env.APP_VARIANT === 'seller' ? 'seller' : 'buyer'
const v = VARIANTS[variant]
const fs = require('fs')
// push notifications on Android need Firebase: put google-services.json (with both packages) next to this file
const googleServicesFile = process.env.GOOGLE_SERVICES_JSON || (fs.existsSync('./google-services.json') ? './google-services.json' : undefined)

module.exports = {
  expo: {
    name: v.name,
    slug: v.slug,
    version: '1.0.0',
    orientation: 'portrait',
    icon: './assets/icon.png',
    userInterfaceStyle: 'light',
    newArchEnabled: true,
    splash: { image: './assets/splash-icon.png', resizeMode: 'contain', backgroundColor: v.color },
    ios: {
      bundleIdentifier: v.package,
      supportsTablet: true,
      infoPlist: {
        // the demo backend is plain http on the LAN
        NSAppTransportSecurity: { NSAllowsArbitraryLoads: true, NSAllowsLocalNetworking: true },
        NSPhotoLibraryUsageDescription: 'Shoppi cần truy cập ảnh để tìm sản phẩm bằng hình ảnh và đăng ảnh sản phẩm.'
      }
    },
    android: {
      package: v.package,
      adaptiveIcon: { foregroundImage: './assets/adaptive-icon.png', backgroundColor: v.color },
      edgeToEdgeEnabled: true,
      permissions: ['CAMERA'],
      ...(googleServicesFile ? { googleServicesFile } : {})
    },
    plugins: [
      'expo-secure-store',
      'expo-video',
      ['expo-notifications', { color: v.color }],
      ['expo-image-picker', { cameraPermission: 'Shoppi cần camera để chụp ảnh tìm sản phẩm và đăng ảnh sản phẩm.' }],
      // the backend runs on plain http in the demo (LAN IP or 10.0.2.2 from the emulator)
      ['expo-build-properties', { android: { usesCleartextTraffic: true } }]
    ],
    extra: {
      variant,
      // Expo project id (from `eas init`), needed to get a push token; without it push stays off
      ...(process.env.EAS_PROJECT_ID ? { eas: { projectId: process.env.EAS_PROJECT_ID } } : {}),
      // default server: the PC's backend seen from the Android emulator; changeable on the login screen
      apiUrl: process.env.API_URL || 'http://10.0.2.2:8081'
    }
  }
}
