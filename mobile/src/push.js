// Push notifications on the phone (expo-notifications). The backend sends through Expo's push service, which needs
// the app built with an Expo project id + Firebase (see README "Thông báo đẩy"). Without them (or in Expo Go,
// which has no remote push on Android since SDK 53) everything here quietly does nothing: the in-app notification
// list still works.
import { useEffect, useRef } from 'react'
import { Platform } from 'react-native'
import Constants from 'expo-constants'
import * as Device from 'expo-device'
import * as Notifications from 'expo-notifications'
import * as SecureStore from 'expo-secure-store'
import { createNavigationContainerRef } from '@react-navigation/native'
import { post } from './api'

export const navigationRef = createNavigationContainerRef()

const TOKEN_KEY = 'pushToken'
const variant = Constants.expoConfig?.extra?.variant === 'seller' ? 'seller' : 'buyer'

// show the banner even while the app is open
Notifications.setNotificationHandler({
  handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false })
})

const projectId = () => Constants.expoConfig?.extra?.eas?.projectId || Constants.easConfig?.projectId || null

/** Asks permission, gets this phone's Expo push token and registers it with the backend. Returns the token or null. */
export async function registerPush() {
  try {
    if (!Device.isDevice || !projectId()) return null
    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'Thông báo Shoppi',
        importance: Notifications.AndroidImportance.HIGH,
        lightColor: '#ee4d2d'
      })
    }
    let { status } = await Notifications.getPermissionsAsync()
    if (status !== 'granted') status = (await Notifications.requestPermissionsAsync()).status
    if (status !== 'granted') return null
    const { data: token } = await Notifications.getExpoPushTokenAsync({ projectId: projectId() })
    await post('/push/subscribe', { kind: 'EXPO', endpoint: token, app: variant, device: Device.modelName || Platform.OS })
    await SecureStore.setItemAsync(TOKEN_KEY, token)
    return token
  } catch (e) {
    return null // no Firebase setup, Expo Go, no network...: the app works without push
  }
}

/** Before signing out: this phone stops receiving the account's notifications. */
export async function unregisterPush() {
  try {
    const token = await SecureStore.getItemAsync(TOKEN_KEY)
    if (token) await post('/push/unsubscribe', { endpoint: token })
    await SecureStore.deleteItemAsync(TOKEN_KEY)
  } catch (e) { /* offline: the backend drops the token when Expo reports it dead */ }
}

/**
 * Registers the phone while someone is signed in, and opens the right screen when a notification is tapped.
 * openLink: '/orders/12' style link -> [screen, params] of this app, or null; fallback: the notifications screen.
 */
export function usePushNotifications(signedIn, openLink, fallback) {
  const handled = useRef(null)

  useEffect(() => {
    if (signedIn) registerPush()
  }, [signedIn])

  useEffect(() => {
    if (!signedIn) return undefined
    const open = (response) => {
      const id = response?.notification?.request?.identifier
      if (!id || handled.current === id) return
      handled.current = id
      const link = response.notification.request.content.data?.link
      const target = openLink(link) || fallback
      const go = () => navigationRef.navigate(...target)
      if (navigationRef.isReady()) go()
      else setTimeout(go, 500)
    }
    // tapped while the app was closed
    Notifications.getLastNotificationResponseAsync().then(open).catch(() => {})
    const sub = Notifications.addNotificationResponseReceivedListener(open)
    return () => sub.remove()
  }, [signedIn, openLink, fallback])
}
