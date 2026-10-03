import type { ApiErrorBody, LoginResponse, User } from './types'

export const API_BASE = '/api/v1'
export const IS_MOCK = import.meta.env.VITE_MOCK === '1'

/** Galat API terstruktur. `status` 0 = jaringan/tidak terjangkau. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const FALLBACK_MESSAGES: Record<number, string> = {
  0: 'Tidak dapat terhubung ke server. Periksa koneksi internet Anda, lalu coba lagi.',
  400: 'Data yang dikirim belum sesuai. Periksa kembali isian Anda.',
  401: 'Sesi Anda telah berakhir. Silakan masuk kembali.',
  403: 'Akun Anda tidak memiliki akses ke fitur ini.',
  404: 'Data tidak ditemukan. Mungkin sudah dihapus atau dipindahkan.',
  409: 'Data bertabrakan dengan perubahan lain. Muat ulang lalu coba lagi.',
  429: 'Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi.',
  503: 'Fitur ini sedang tidak aktif di server.',
}

export function messageFor(status: number): string {
  return FALLBACK_MESSAGES[status] ?? (status >= 500
    ? 'Server sedang mengalami gangguan. Coba lagi beberapa saat lagi.'
    : 'Terjadi kesalahan yang tidak terduga.')
}

/* ---------------- Sesi (token di memori + sessionStorage) ---------------- */

const SESSION_KEY = 'sehati-session'

export interface Session {
  token: string
  expiresAt: number
  user: User
}

let session: Session | null = readStoredSession()

function readStoredSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as Session
    if (!s.token || typeof s.expiresAt !== 'number' || s.expiresAt <= Date.now()) {
      sessionStorage.removeItem(SESSION_KEY)
      return null
    }
    return s
  } catch {
    return null
  }
}

export function getSession(): Session | null {
  if (session && session.expiresAt <= Date.now()) {
    clearSession()
    return null
  }
  return session
}

export function setSession(s: LoginResponse): Session {
  session = { token: s.token, expiresAt: s.expiresAt, user: s.user }
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } catch {
    /* penyimpanan sesi tidak tersedia: token tetap di memori */
  }
  return session
}

export function clearSession() {
  session = null
  try {
    sessionStorage.removeItem(SESSION_KEY)
  } catch {
    /* abaikan */
  }
}

type UnauthorizedListener = () => void
let onUnauthorized: UnauthorizedListener | null = null
export function setUnauthorizedHandler(fn: UnauthorizedListener | null) {
  onUnauthorized = fn
}

/** ID perangkat untuk login (kontrak §2). Disimpan per peramban. */
export function deviceId(): string {
  const KEY = 'sehati-web-device'
  try {
    const existing = localStorage.getItem(KEY)
    if (existing) return existing
    const id = crypto.randomUUID()
    localStorage.setItem(KEY, id)
    return id
  } catch {
    return crypto.randomUUID()
  }
}

/* ---------------- Transport ---------------- */

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** Sertakan token (bawaan: true). */
  auth?: boolean
  signal?: AbortSignal
  /** Jangan picu logout otomatis saat 401 (mis. untuk login). */
  ignore401?: boolean
}

async function transport(path: string, init: RequestInit): Promise<Response> {
  if (IS_MOCK) {
    const { mockFetch } = await import('../mock/server')
    return mockFetch(path, init)
  }
  return fetch(API_BASE + path, init)
}

async function send(path: string, opts: RequestOptions, accept: string): Promise<Response> {
  const headers: Record<string, string> = { Accept: accept }
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  const auth = opts.auth ?? true
  if (auth) {
    const s = getSession()
    if (s) headers.Authorization = `Bearer ${s.token}`
  }
  let res: Response
  try {
    res = await transport(path, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      signal: opts.signal,
      credentials: 'same-origin',
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK', messageFor(0))
  }
  if (!res.ok) {
    let code = `HTTP_${res.status}`
    let message = messageFor(res.status)
    try {
      const body = (await res.json()) as Partial<ApiErrorBody>
      if (body.error?.code) code = body.error.code
      if (body.error?.message) message = body.error.message
    } catch {
      /* badan galat bukan JSON */
    }
    if (res.status === 401 && auth && !opts.ignore401) {
      clearSession()
      onUnauthorized?.()
    }
    throw new ApiError(res.status, code, message)
  }
  return res
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const res = await send(path, opts, 'application/json')
  if (res.status === 204) return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  try {
    return JSON.parse(text) as T
  } catch {
    throw new ApiError(res.status, 'BAD_RESPONSE', 'Respons server tidak dapat dibaca.')
  }
}

export async function apiBlob(path: string, opts: RequestOptions = {}): Promise<Blob> {
  const res = await send(path, opts, 'text/csv, */*')
  return res.blob()
}
