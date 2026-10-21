import { defineStore } from 'pinia'
import { unreadSummary } from '../api/notifications'
import { startRealtime, stopRealtime, onRealtime, connected } from '../realtime/socket'

const POLL_MS = 30000
let timer = null
let ticks = 0
let unlisten = null

// Unread badges (bell + chat icon), refreshed at once by WebSocket events while someone is logged in;
// polling every 30 s is the fallback (only every 4th tick while the WebSocket is connected).
// Pages that change unread state (opening a chat, reading notifications) call refresh().
export const useNotificationStore = defineStore('notifications', {
  state: () => ({
    total: 0,
    byType: {},
    chat: 0
  }),

  actions: {
    async refresh() {
      try {
        const { data } = await unreadSummary()
        this.total = data.total
        this.byType = data.byType
        this.chat = data.chat
      } catch {
        // offline / logged out: keep the last numbers, the next poll retries
      }
    },

    // idempotent: every header instance calls it on mount
    start() {
      if (timer) return
      this.refresh()
      startRealtime()
      unlisten = onRealtime(() => this.refresh())
      timer = setInterval(() => {
        ticks++
        if (connected.value && ticks % 4) return
        if (!document.hidden) this.refresh()
      }, POLL_MS)
    },

    stop() {
      clearInterval(timer)
      timer = null
      unlisten?.()
      unlisten = null
      stopRealtime()
      this.$reset()
    }
  }
})
