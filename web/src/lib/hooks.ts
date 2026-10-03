import { endpoints } from '../api/endpoints'
import type { FollowUpFilter } from '../api/types'
import { useQuery } from './query'

export const KEYS = {
  overview: 'admin/overview',
  rw: 'admin/rw',
  followups: (status: FollowUpFilter, rw: string) => `admin/followups?${status}&${rw}`,
  cadres: 'admin/cadres',
  posyandu: 'admin/posyandu',
  thresholds: 'admin/thresholds',
  audit: (limit: number) => `admin/audit?${limit}`,
  aiUsage: (days: number) => `admin/ai-usage?${days}`,
}

export const useOverview = () => useQuery(KEYS.overview, endpoints.overview)
export const useRw = () => useQuery(KEYS.rw, endpoints.rw)
export const useFollowups = (status: FollowUpFilter, rw: string) =>
  useQuery(KEYS.followups(status, rw), () => endpoints.followups(status, rw || undefined))
export const useCadres = () => useQuery(KEYS.cadres, endpoints.cadres)
export const usePosyandu = () => useQuery(KEYS.posyandu, endpoints.posyandu)
export const useThresholds = () => useQuery(KEYS.thresholds, endpoints.thresholds)
export const useAudit = (limit: number) => useQuery(KEYS.audit(limit), () => endpoints.audit(limit))
export const useAiUsage = (days: number) => useQuery(KEYS.aiUsage(days), () => endpoints.aiUsage(days))
