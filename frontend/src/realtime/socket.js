import { Client } from '@stomp/stompjs'
import { ref } from 'vue'
import { useAuthStore } from '../stores/auth'

// Realtime channel to the backend: STOMP over WebSocket (/ws, see backend realtime.WebSocketConfig).
// The server pushes small events ({ type: 'chat' | 'notification', ... }) to the user's private queue;
// pages react by reloading what they show through the REST API. Pages keep a slow poll as a fallback.

export const connected = ref(false)
const listeners = new Set()
let client = null

function brokerUrl() {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL
  // backend on another host (production build): same host as the API
  const api = import.meta.env.VITE_API_BASE_URL
  if (api && /^https?:\/\//.test(api)) return api.replace(/^http/, 'ws').replace(/\/api\/?$/, '') + '/ws'
  // dev: Vite proxies /ws to the backend
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`
}

export function startRealtime() {
  if (client || typeof WebSocket === 'undefined') return
  const auth = useAuthStore()
  client = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: 5000,
    heartbeatIncoming: 25000,
    heartbeatOutgoing: 25000,
    // the access token is renewed every 30 minutes: always connect with the current one
    beforeConnect: () => { client.connectHeaders = { Authorization: `Bearer ${auth.token}` } },
    onConnect: () => {
      connected.value = true
      client.subscribe('/user/queue/events', (msg) => {
        let event
        try { event = JSON.parse(msg.body) } catch { return }
        listeners.forEach(fn => { try { fn(event) } catch { /* one bad listener must not stop the others */ } })
      })
    },
    onWebSocketClose: () => { connected.value = false },
    onStompError: () => { connected.value = false }
  })
  client.activate()
}

export function stopRealtime() {
  client?.deactivate()
  client = null
  connected.value = false
}

/** Calls fn(event) for every pushed event; returns the function that unsubscribes. */
export function onRealtime(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}
