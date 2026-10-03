import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { ApiError } from '../api/client'

/**
 * Lapisan data kecil: cache per kunci, muat ulang di latar, invalidasi, dan
 * mutasi lokal (optimistis) yang dapat dikembalikan bila gagal.
 */

interface Entry {
  data?: unknown
  error?: ApiError
  loading: boolean
  updatedAt: number
  promise?: Promise<void>
}

const cache = new Map<string, Entry>()
const listeners = new Map<string, Set<() => void>>()
const fetchers = new Map<string, () => Promise<unknown>>()

function emit(key: string) {
  listeners.get(key)?.forEach((l) => l())
}

function subscribe(key: string, fn: () => void) {
  let set = listeners.get(key)
  if (!set) listeners.set(key, (set = new Set()))
  set.add(fn)
  return () => set.delete(fn)
}

function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e
  return new ApiError(-1, 'UNKNOWN', 'Terjadi kesalahan yang tidak terduga.')
}

async function load(key: string) {
  const fetcher = fetchers.get(key)
  if (!fetcher) return
  const prev = cache.get(key)
  if (prev?.promise) return prev.promise
  const entry: Entry = { ...(prev ?? { updatedAt: 0 }), loading: true }
  const promise = fetcher()
    .then((data) => {
      cache.set(key, { data, loading: false, updatedAt: Date.now() })
    })
    .catch((e: unknown) => {
      cache.set(key, { data: prev?.data, error: toApiError(e), loading: false, updatedAt: prev?.updatedAt ?? 0 })
    })
    .finally(() => emit(key))
  entry.promise = promise
  cache.set(key, entry)
  emit(key)
  return promise
}

/** Muat ulang semua kunci yang diawali `prefix`. */
export function invalidate(prefix: string) {
  for (const key of fetchers.keys()) if (key.startsWith(prefix)) void load(key)
}

/** Ubah data cache secara lokal; kembalikan fungsi untuk membatalkan. */
export function setQueryData<T>(key: string, update: (prev: T | undefined) => T): () => void {
  const prev = cache.get(key)
  const before = prev?.data as T | undefined
  cache.set(key, { ...(prev ?? { loading: false, updatedAt: Date.now() }), data: update(before), error: undefined })
  emit(key)
  return () => {
    const cur = cache.get(key)
    cache.set(key, { ...(cur ?? { loading: false, updatedAt: 0 }), data: before })
    emit(key)
  }
}

export function clearQueryCache() {
  cache.clear()
}

export interface QueryResult<T> {
  data: T | undefined
  error: ApiError | undefined
  loading: boolean
  /** Belum pernah ada data (tampilkan skeleton). */
  initial: boolean
  updatedAt: number
  reload: () => void
}

const EMPTY: Entry = { loading: true, updatedAt: 0 }

export function useQuery<T>(key: string, fetcher: () => Promise<T>): QueryResult<T> {
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher
  fetchers.set(key, () => fetcherRef.current())

  const entry = useSyncExternalStore(
    useCallback((fn) => subscribe(key, fn), [key]),
    () => cache.get(key) ?? EMPTY,
  )

  useEffect(() => {
    fetchers.set(key, () => fetcherRef.current())
    void load(key)
  }, [key])

  return {
    data: entry.data as T | undefined,
    error: entry.error,
    loading: entry.loading,
    initial: entry.data === undefined && !entry.error,
    updatedAt: entry.updatedAt,
    reload: useCallback(() => void load(key), [key]),
  }
}

/** Status mutasi sederhana untuk tombol. */
export function useMutation<A extends unknown[], R>(fn: (...args: A) => Promise<R>) {
  const [pending, setPending] = useState(false)
  const ref = useRef(fn)
  ref.current = fn
  const run = useCallback(async (...args: A): Promise<R> => {
    setPending(true)
    try {
      return await ref.current(...args)
    } finally {
      setPending(false)
    }
  }, [])
  return { run, pending }
}
