import type { Hospital, Plan, RankedHospital, Settings, Summary, EvaluationReport, Severity, PatientType } from './types'

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, { headers: { 'Content-Type': 'application/json' }, ...init })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.detail || `${res.status} ${res.statusText}`)
  }
  return res.json()
}

export const api = {
  bootstrap: () => http<{ bounds: number[]; center: number[]; posts: { id: number; name: string; lat: number; lon: number }[] }>('/api/map/bootstrap'),
  roads: () => http<number[][][]>('/api/map/roads'),
  hospitals: () => http<Hospital[]>('/api/hospitals'),
  alerts: () => http<any[]>('/api/hospitals/alerts'),
  recommend: (lat: number, lon: number, patientType: PatientType, severity: Severity) =>
    http<RankedHospital[]>(`/api/hospitals/recommendation?lat=${lat}&lon=${lon}&patientType=${patientType}&severity=${severity}`),
  settings: () => http<Settings>('/api/sim/settings'),
  updateSettings: (s: Partial<Settings>) => http<Settings>('/api/sim/settings', { method: 'PUT', body: JSON.stringify(s) }),
  forecast: () => http<Plan>('/api/sim/forecast'),
  rebalance: () => http<Plan>('/api/sim/rebalance', { method: 'POST' }),
  createIncident: (b: { lat: number; lon: number; severity: Severity; patientType: PatientType; description?: string }) =>
    http<{ message: string }>('/api/incidents', { method: 'POST', body: JSON.stringify(b) }),
  surge: (b: { lat: number; lon: number; casualties: number; description?: string }) =>
    http<{ patients: number; surgeGroup: string }>('/api/incidents/surge', { method: 'POST', body: JSON.stringify(b) }),
  breakdown: (id: number) => http<{ message: string }>(`/api/ambulances/${id}/breakdown`, { method: 'POST' }),
  summary: () => http<Summary>('/api/analytics/summary'),
  zones: () => http<{ area: string; lat: number; lon: number; trips: number; avgResponseMin: number }[]>('/api/analytics/zones'),
  hourly: () => http<{ hour: string; calls: number; forecast: number }[]>('/api/analytics/hourly'),
  evaluation: () => http<{ normal: EvaluationReport; surge: EvaluationReport }>('/api/evaluation'),
  runEvaluation: (seeds: number, days: number, callsPerDay: number) =>
    http<{ status: string; result: EvaluationReport | null }>(`/api/evaluation/run?seeds=${seeds}&days=${days}&callsPerDay=${callsPerDay}`, { method: 'POST' }),
  evaluationLatest: () => http<{ status: string; result: EvaluationReport | string | null }>('/api/evaluation/latest'),
}
