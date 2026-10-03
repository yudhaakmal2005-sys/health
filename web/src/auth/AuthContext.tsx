import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { clearSession, getSession, setSession, setUnauthorizedHandler, type Session } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { User } from '../api/types'
import { clearQueryCache } from '../lib/query'

interface AuthState {
  session: Session | null
  /** Hanya ADMIN yang disimpan sesinya; role lain langsung dicabut tokennya. */
  login: (sehatiId: string, password: string) => Promise<{ admitted: boolean; user: User }>
  logout: (reason?: 'expired') => Promise<void>
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setState] = useState<Session | null>(() => getSession())
  const navigate = useNavigate()

  // 401 dari API mana pun → keluar otomatis dan kembali ke halaman masuk.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setState(null)
      clearQueryCache()
      navigate('/masuk?sesi=berakhir', { replace: true })
    })
    return () => setUnauthorizedHandler(null)
  }, [navigate])

  // Keluar otomatis saat token kedaluwarsa (12 jam untuk staf).
  useEffect(() => {
    if (!session) return
    const ms = session.expiresAt - Date.now()
    const t = window.setTimeout(() => {
      clearSession()
      setState(null)
      clearQueryCache()
      navigate('/masuk?sesi=berakhir', { replace: true })
    }, Math.max(0, Math.min(ms, 2 ** 31 - 1)))
    return () => window.clearTimeout(t)
  }, [session, navigate])

  const login = useCallback(async (sehatiId: string, password: string) => {
    const res = await endpoints.login(sehatiId, password)
    setSession(res)
    if (res.user.role !== 'ADMIN') {
      // Dashboard web hanya untuk Admin Puskesmas: cabut token yang baru dibuat.
      try { await endpoints.logout() } catch { /* abaikan */ }
      clearSession()
      return { admitted: false, user: res.user }
    }
    clearQueryCache()
    setState(getSession())
    return { admitted: true, user: res.user }
  }, [])

  const logout = useCallback(async (reason?: 'expired') => {
    try {
      if (getSession()) await endpoints.logout()
    } catch {
      /* token tetap dihapus di klien */
    }
    clearSession()
    clearQueryCache()
    setState(null)
    navigate(reason ? '/masuk?sesi=berakhir' : '/masuk', { replace: true })
  }, [navigate])

  const value = useMemo(() => ({ session, login, logout }), [session, login, logout])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth(): AuthState {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth di luar AuthProvider')
  return v
}
