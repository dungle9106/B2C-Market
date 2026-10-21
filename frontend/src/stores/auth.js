import axios from 'axios'
import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, me as apiMe, loginTwoFactor as apiLogin2fa, logout as apiLogout } from '../api/auth'
import { useNotificationStore } from './notifications'

// Every browser tab has a sign-in of its own, so signing out (or into another account) in one tab never changes
// the others:
//  - this tab's session lives in sessionStorage (per tab), with its own refresh token;
//  - localStorage keeps a "seed": the refresh token of a separate session, never used for API calls, from which a
//    newly opened tab forks a session of its own (POST /auth/session/fork) - so a new tab starts signed in as the
//    account signed in most recently, without sharing tokens with any tab;
//  - a duplicated tab (its sessionStorage is copied) is detected at start and forks a session of its own too,
//    otherwise both tabs would rotate the same refresh token and the server would end the session as stolen.
// The access token lives 30 minutes; api/http.js renews it with this tab's refresh token.

const TAB_KEY = 'shoppi.auth'
const TAB_ID_KEY = 'shoppi.tab'
const SEED_KEY = 'shoppi.seed'
const FIELDS = ['token', 'refreshToken', 'userId', 'fullName', 'email', 'phone', 'role', 'emailVerified', 'twoFactorEnabled']
const LEGACY = FIELDS.filter(k => k !== 'phone') // keys of the old shared localStorage session
const base = () => import.meta.env.VITE_API_BASE_URL || '/api'

const read = (store, key) => {
  try { return JSON.parse(store.getItem(key)) } catch { return null }
}
const write = (store, key, value) => {
  try {
    if (value == null) store.removeItem(key)
    else store.setItem(key, JSON.stringify(value))
  } catch { /* storage blocked: the session lasts as long as the page */ }
}
const empty = () => ({ token: null, refreshToken: null, userId: null, fullName: '', email: '', phone: '', role: null, emailVerified: false, twoFactorEnabled: false })

// calls made with a refresh token only: never with this tab's access token attached
const forkSession = (refreshToken) => axios.post(`${base()}/auth/session/fork`, { refreshToken }).then(r => r.data)
const dropSession = (refreshToken) => axios.post(`${base()}/auth/logout`, { refreshToken }).catch(() => {})

export const useAuthStore = defineStore('auth', {
  state: () => ({ ...empty(), ...(read(sessionStorage, TAB_KEY) || {}) }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isBuyer: (state) => state.role === 'BUYER',
    isSeller: (state) => state.role === 'SELLER',
    isAdmin: (state) => state.role === 'ADMIN'
  },

  actions: {
    persist() {
      const s = {}
      FIELDS.forEach(k => { s[k] = this[k] })
      write(sessionStorage, TAB_KEY, this.token ? s : null)
    },

    setSession(r) {
      if (r.token) this.token = r.token
      if (r.refreshToken) this.refreshToken = r.refreshToken
      this.userId = r.userId ?? null
      this.fullName = r.fullName || ''
      this.email = r.email || ''
      this.phone = r.phone || ''
      this.role = r.role || null
      this.emailVerified = !!r.emailVerified
      this.twoFactorEnabled = !!r.twoFactorEnabled
      this.persist()
    },

    // the new tokens after a silent refresh (api/http.js)
    setTokens(token, refreshToken) {
      this.token = token
      this.refreshToken = refreshToken
      this.persist()
    },

    setTwoFactor(enabled) {
      this.twoFactorEnabled = enabled
      this.persist()
    },

    /**
     * Runs once before the app mounts: a duplicated tab gets a session of its own, a new tab starts from the seed,
     * and this tab answers "this tab id is mine" to tabs duplicated from it later.
     */
    async initTab() {
      this.migrateLegacy()
      const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('shoppi-tabs') : null
      let tabId = sessionStorage.getItem(TAB_ID_KEY)
      if (this.token && tabId && channel && await isTaken(channel, tabId)) {
        // duplicated: the copied session belongs to the original tab
        tabId = null
        const own = this.refreshToken
        this.clearTab()
        await this.forkFrom(own) || await this.forkFromSeed()
      } else if (!this.token) {
        await this.forkFromSeed()
      }
      if (!tabId) {
        tabId = Math.random().toString(36).slice(2) + Date.now().toString(36)
        try { sessionStorage.setItem(TAB_ID_KEY, tabId) } catch { /* ignore */ }
      }
      channel?.addEventListener('message', (e) => {
        if (e.data?.type === 'ping' && e.data.tabId === tabId) channel.postMessage({ type: 'taken', tabId })
      })
    },

    async forkFrom(refreshToken) {
      if (!refreshToken) return false
      try {
        this.setSession(await forkSession(refreshToken))
        return true
      } catch {
        return false
      }
    },

    async forkFromSeed() {
      const seed = read(localStorage, SEED_KEY)
      if (!seed?.refreshToken) return false
      if (await this.forkFrom(seed.refreshToken)) return true
      write(localStorage, SEED_KEY, null) // that sign-in ended (logout, password change, expired)
      return false
    },

    /** After a sign-in in this tab: tabs opened from now on start with this account. */
    async saveSeed() {
      if (!this.refreshToken) return
      try {
        const forked = await forkSession(this.refreshToken)
        const old = read(localStorage, SEED_KEY)
        write(localStorage, SEED_KEY, { refreshToken: forked.refreshToken, userId: forked.userId })
        if (old?.refreshToken) dropSession(old.refreshToken)
      } catch { /* new tabs will just ask to sign in */ }
    },

    // the old shared session (before per-tab sessions) becomes the seed, so nobody is signed out by the update
    migrateLegacy() {
      try {
        const legacy = localStorage.getItem('refreshToken')
        if (legacy && !read(localStorage, SEED_KEY)) {
          write(localStorage, SEED_KEY, { refreshToken: legacy, userId: Number(localStorage.getItem('userId')) || null })
        }
        LEGACY.forEach(k => localStorage.removeItem(k))
      } catch { /* ignore */ }
    },

    /** Returns the response: with twoFactorRequired the caller asks for the code, then calls loginTwoFactor. */
    async login(credentials) {
      const { data } = await apiLogin(credentials)
      if (!data.twoFactorRequired) {
        this.setSession(data)
        this.saveSeed()
      }
      return data
    },

    async loginTwoFactor(challenge, code) {
      const { data } = await apiLogin2fa(challenge, code)
      this.setSession(data)
      this.saveSeed()
      return data
    },

    async register(payload) {
      const { data } = await apiRegister(payload)
      this.setSession(data)
      this.saveSeed()
      return data
    },

    // After Google OAuth2 the backend hands us the tokens in the URL fragment;
    // store them, then ask /auth/me for the rest of the session info.
    async loginWithToken(token, refreshToken) {
      this.setTokens(token, refreshToken || null)
      const { data } = await apiMe()
      this.setSession({ ...data, token, refreshToken })
      this.saveSeed()
      return data
    },

    async refreshMe() {
      const { data } = await apiMe()
      this.setSession({ ...data, token: this.token })
    },

    clearTab() {
      Object.assign(this, empty())
      write(sessionStorage, TAB_KEY, null)
    },

    /**
     * Signs this tab out (other tabs keep their own sessions). Tabs opened afterwards no longer start with this
     * account: its seed is dropped too.
     */
    logout(callServer = true) {
      const seed = read(localStorage, SEED_KEY)
      const sameAccount = seed && this.userId != null && String(seed.userId) === String(this.userId)
      if (callServer && this.token) apiLogout(sameAccount ? seed.refreshToken : null).catch(() => {})
      else if (sameAccount && callServer) dropSession(seed.refreshToken)
      if (sameAccount && callServer) write(localStorage, SEED_KEY, null)
      useNotificationStore().stop()
      this.clearTab()
    }
  }
})

// asks the other tabs whether one of them already uses this tab id (then this tab is a duplicate)
function isTaken(channel, tabId) {
  return new Promise((resolve) => {
    const onMessage = (e) => {
      if (e.data?.type === 'taken' && e.data.tabId === tabId) done(true)
    }
    const done = (v) => {
      channel.removeEventListener('message', onMessage)
      clearTimeout(timer)
      resolve(v)
    }
    channel.addEventListener('message', onMessage)
    const timer = setTimeout(() => done(false), 150)
    channel.postMessage({ type: 'ping', tabId })
  })
}
