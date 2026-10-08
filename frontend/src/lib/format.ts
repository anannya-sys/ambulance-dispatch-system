import type { PatientType, Severity } from './types'

export const SEVERITY_COLOR: Record<Severity, string> = { CRITICAL: '#ef4444', SERIOUS: '#f59e0b', MINOR: '#38bdf8' }
export const SEVERITY_BG: Record<Severity, string> = {
  CRITICAL: 'bg-red-500/15 text-red-300 ring-1 ring-red-500/40',
  SERIOUS: 'bg-amber-500/15 text-amber-300 ring-1 ring-amber-500/40',
  MINOR: 'bg-sky-500/15 text-sky-300 ring-1 ring-sky-500/40',
}

export const STATUS_COLOR: Record<string, string> = {
  AVAILABLE: '#22c55e', REPOSITIONING: '#a3e635', DISPATCHED: '#f59e0b', EN_ROUTE: '#f97316',
  AT_SCENE: '#e879f9', TRANSPORTING: '#ef4444', AT_HOSPITAL: '#60a5fa', OUT_OF_SERVICE: '#64748b',
}

export const STATUS_LABEL: Record<string, string> = {
  AVAILABLE: 'Available', REPOSITIONING: 'Repositioning', DISPATCHED: 'Dispatched', EN_ROUTE: 'En route',
  AT_SCENE: 'At scene', TRANSPORTING: 'Transporting', AT_HOSPITAL: 'At hospital', OUT_OF_SERVICE: 'Out of service',
}

export const PHASE_LABEL: Record<string, string> = {
  ASSIGNED: 'Awaiting crew ack', EN_ROUTE: 'En route to patient', AT_SCENE: 'On scene, stabilising',
  PICKED_UP: 'Transporting to hospital', AT_HOSPITAL: 'Handover at ER',
}

export const PATIENT_LABEL: Record<PatientType, string> = {
  CARDIAC: 'Cardiac', STROKE: 'Stroke', TRAUMA: 'Trauma', RESPIRATORY: 'Respiratory', MATERNITY: 'Maternity', GENERAL: 'General',
}

export function mmss(seconds: number | null | undefined): string {
  if (seconds == null) return '--'
  const s = Math.max(0, Math.round(seconds))
  const m = Math.floor(s / 60)
  return `${m}:${String(s % 60).padStart(2, '0')}`
}

export function minutes(seconds: number | null | undefined): string {
  if (seconds == null) return '--'
  return `${Math.max(0, Math.round(seconds / 60))} min`
}

/** Backend LocalDateTime may arrive as ISO text or as [y,m,d,h,mi,s] from Kafka JSON. */
export function clockText(at: string | number[] | null | undefined): string {
  if (!at) return ''
  if (Array.isArray(at)) return `${String(at[3] ?? 0).padStart(2, '0')}:${String(at[4] ?? 0).padStart(2, '0')}`
  const t = at.split('T')[1] ?? at
  return t.slice(0, 5)
}
