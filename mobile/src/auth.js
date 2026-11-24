import React, { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { loadSession, setTokens, clearTokens, onSessionExpired, get, post } from './api'
import { unregisterPush } from './push'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [ready, setReady] = useState(false)
  const [user, setUser] = useState(null)

  const refreshUser = useCallback(async () => {
    const me = await get('/auth/me')
    setUser(me)
    return me
  }, [])

  useEffect(() => {
    onSessionExpired(() => setUser(null))
    loadSession()
      .then(({ loggedIn }) => (loggedIn ? refreshUser().catch(() => clearTokens()) : null))
      .finally(() => setReady(true))
  }, [refreshUser])

  // the login response doubles as the profile ({ token, refreshToken, userId, fullName, role, ... })
  const signIn = useCallback(async (res) => {
    await setTokens(res.token, res.refreshToken)
    setUser(res)
    return res
  }, [])

  const signOut = useCallback(async () => {
    await unregisterPush() // while still signed in: the backend only removes this account's own device
    try { await post('/auth/logout', {}) } catch (e) { /* the session ends locally anyway */ }
    await clearTokens()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ ready, user, signIn, signOut, refreshUser, setUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
