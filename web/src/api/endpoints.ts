import { api, apiBlob, deviceId } from './client'
import type {
  AiUsageDay, AppConfig, AuditEntry, Cadre, CadreCreate, ClinicalThresholds, FollowUp, FollowUpFilter,
  Health, LoginResponse, Overview, PosyanduInput, PosyanduSchedule, RwStat, ThresholdsResponse, User,
} from './types'

const enc = encodeURIComponent

export const endpoints = {
  health: () => api<Health>('/health', { auth: false }),
  config: () => api<AppConfig>('/config'),

  login: (sehatiId: string, password: string) =>
    api<LoginResponse>('/auth/login', {
      method: 'POST', auth: false, ignore401: true,
      body: { sehatiId, password, deviceId: deviceId() },
    }),
  logout: () => api<void>('/auth/logout', { method: 'POST', ignore401: true }),
  me: () => api<User>('/auth/me'),

  overview: () => api<Overview>('/admin/overview'),
  rw: () => api<RwStat[]>('/admin/rw'),

  followups: (status: FollowUpFilter, rw?: string) => {
    const q = new URLSearchParams({ status })
    if (rw) q.set('rw', rw)
    return api<FollowUp[]>(`/admin/followups?${q.toString()}`)
  },
  assignFollowup: (id: string, cadreId: string | null) =>
    api<unknown>(`/admin/followups/${enc(id)}/assign`, { method: 'POST', body: { cadreId } }),
  scheduleFollowup: (id: string, dueAt: number) =>
    api<unknown>(`/admin/followups/${enc(id)}/schedule`, { method: 'POST', body: { dueAt } }),

  cadres: () => api<Cadre[]>('/admin/cadres'),
  createCadre: (body: CadreCreate) => api<{ sehatiId: string }>('/admin/cadres', { method: 'POST', body }),
  setCadreActive: (id: string, active: boolean) =>
    api<unknown>(`/admin/cadres/${enc(id)}`, { method: 'PATCH', body: { active } }),

  posyandu: () => api<PosyanduSchedule[]>('/admin/posyandu'),
  createPosyandu: (body: PosyanduInput) =>
    api<PosyanduSchedule | undefined>('/admin/posyandu', { method: 'POST', body }),
  updatePosyandu: (id: string, body: Partial<PosyanduInput>) =>
    api<PosyanduSchedule | undefined>(`/admin/posyandu/${enc(id)}`, { method: 'PATCH', body }),
  deletePosyandu: (id: string) => api<void>(`/admin/posyandu/${enc(id)}`, { method: 'DELETE' }),

  thresholds: () => api<ThresholdsResponse>('/admin/thresholds'),
  putThresholds: (thresholds: ClinicalThresholds | null) =>
    api<ThresholdsResponse | undefined>('/admin/thresholds', { method: 'PUT', body: { thresholds } }),

  audit: (limit = 100) => api<AuditEntry[]>(`/admin/audit?limit=${limit}`),
  aiUsage: (days = 30) => api<AiUsageDay[]>(`/admin/ai-usage?days=${days}`),
  summaryCsv: () => apiBlob('/admin/reports/summary.csv'),
}
