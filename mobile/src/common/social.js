// "Tiep tuc voi Google / Facebook / GitHub" in the apps. The sign-in runs in the system browser (Custom Tabs /
// SFSafariViewController): the backend sends it to the provider and, at the end, to our app's own address
// (shoppi://oauth2/redirect, or exp://.../--/oauth2/redirect in Expo Go) with the result after '#'.
// The browser session catches that address and closes itself. Same flow as the web: SocialLoginSettings,
// OAuth2LoginSuccessHandler and OAuth2FrontendRedirect (app_redirect) on the backend.
import * as WebBrowser from 'expo-web-browser'
import * as Linking from 'expo-linking'
import { get, serverUrl } from '../api'

WebBrowser.maybeCompleteAuthSession()

export const PROVIDER_STYLE = {
  google: { label: 'Google', icon: 'G', color: '#ea4335', bg: 'white' },
  facebook: { label: 'Facebook', icon: 'f', color: 'white', bg: '#1877f2' },
  github: { label: 'GitHub', icon: '⌥', color: 'white', bg: '#24292f' }
}

/** { providers: [{ id, name }], startBase } - only the providers the admin has set up. */
export async function socialProviders() {
  try {
    return await get('/auth/oauth2/providers')
  } catch {
    return { providers: [], startBase: null }
  }
}

/**
 * Opens the provider's sign-in page. Resolves to null when the user closed it, otherwise to what the
 * backend sent back: { token, refresh } | { challenge } (two-step verification) | { linked } | { error }.
 */
export async function socialSignIn(provider, { startBase, link } = {}) {
  const returnUrl = Linking.createURL('oauth2/redirect')
  const q = [`app_redirect=${encodeURIComponent(returnUrl)}`]
  if (link) q.push(`link=${encodeURIComponent(link)}`)
  const url = `${startBase || serverUrl()}/oauth2/authorization/${provider}?${q.join('&')}`
  const res = await WebBrowser.openAuthSessionAsync(url, returnUrl)
  if (res.type !== 'success' || !res.url) return null
  const fragment = res.url.split('#')[1] || ''
  const out = {}
  for (const part of fragment.split('&')) {
    if (!part) continue
    const i = part.indexOf('=')
    const k = i < 0 ? part : part.slice(0, i)
    out[k] = i < 0 ? '' : decodeURIComponent(part.slice(i + 1).replace(/\+/g, ' '))
  }
  return out
}
