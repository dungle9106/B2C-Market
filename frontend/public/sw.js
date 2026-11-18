// Shoppi service worker: shows push notifications sent by the backend (Web Push) and opens the right page on click.
// Payload: { title, body, link, image, type }

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { title: 'Shoppi', body: event.data ? event.data.text() : '' }
  }
  const title = data.title || 'Shoppi'
  const options = {
    body: data.body || '',
    icon: '/favicon.svg',
    badge: '/favicon.svg',
    image: data.image || undefined,
    // the same page replaces its older notification instead of stacking (e.g. one chat thread)
    tag: data.link || undefined,
    renotify: !!data.link,
    data: { link: data.link || '/' }
  }
  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data?.link || '/', self.location.origin).href
  event.waitUntil((async () => {
    const tabs = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    // reuse a Shoppi tab when there is one: tell it to open the page in-app (keeps that tab's sign-in)
    const tab = tabs.find(c => new URL(c.url).origin === self.location.origin)
    if (tab) {
      await tab.focus()
      tab.postMessage({ type: 'open-link', link: event.notification.data?.link || '/' })
      return
    }
    await self.clients.openWindow(url)
  })())
})
