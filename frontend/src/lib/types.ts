// Shapes of the backend's JSON (see the Java records in api/dto).

export type Severity = 'CRITICAL' | 'SERIOUS' | 'MINOR'
export type PatientType = 'CARDIAC' | 'STROKE' | 'TRAUMA' | 'RESPIRATORY' | 'MATERNITY' | 'GENERAL'

export interface FleetUnit {
  id: number; callSign: string; kind: string; status: string
  lat: number; lon: number; heading: number; tripId: number | null; etaSeconds: number | null; targetPostId: number | null
}

export interface CorridorSignal { lat: number; lon: number; state: 'CLEARED' | 'GREEN' | 'PENDING' }

export interface ActiveTrip {
  tripId: number; incidentId: number; ambulanceId: number; callSign: string
  severity: Severity; patientType: PatientType; area: string; phase: string
  incidentLat: number; incidentLon: number
  hospitalId: number | null; hospitalName: string | null; hospitalReason: string | null
  etaSeconds: number | null; sinceCallSeconds: number; corridor: boolean
  route: [number, number][]; signals: CorridorSignal[]; treatmentEtaMinutes: number | null
}

export interface QueuedCall {
  id: number; severity: Severity; patientType: PatientType; area: string; lat: number; lon: number
  waitingSeconds: number; priority: number
}

export interface LiveSnapshot {
  clock: string; speed: number
  kpis: { activeTrips: number; queued: number; available: number; busy: number; outOfService: number; utilisationPct: number }
  fleet: FleetUnit[]; trips: ActiveTrip[]; queue: QueuedCall[]
}

export interface DispatchEvent {
  type: string; tripId: number | null; incidentId: number | null; ambulanceId: number | null
  callSign: string | null; hospitalId: number | null; severity: Severity | null; message: string; at: string | number[]
}

export interface Alert {
  id: number; hospitalId: number; hospitalName?: string; tripId: number; severity: Severity
  patientType: PatientType; message: string; etaAt: string; createdAt: string
}

export interface Hospital {
  id: number; name: string; lat: number; lon: number; capabilities: string[]
  beds: Record<string, { free: number; total: number }>; erQueue: number; erBays: number; specialists24x7: boolean
}

export interface Plan {
  at: string; active: boolean; idleUnits: number; moved: number
  heat: { lat: number; lon: number; expectedCalls: number }[]
  posts: { id: number; name: string; lat: number; lon: number }[]
  coverage8MinPct: number; expectedCallsNextHour: number
}

export interface Settings { speed: number; autoCalls: boolean; callsPerDay: number; prePositioning: boolean; greenCorridor: boolean; clock: string }

export interface Summary {
  calls: number; completedTrips: number; avgResponseMin: number; avgCriticalResponseMin: number
  criticalWithin15Pct: number; goldenHourPct: number; corridorTrips: number; signalsCleared: number
  corridorMinutesSaved: number; preAlerts: number; reassignments: number; surgeCalls: number
}

export interface SimResult {
  config: string; seed: number; calls: number; meanResponseMin: number; meanCriticalResponseMin: number
  p90CriticalResponseMin: number; criticalWithin15Pct: number; meanCriticalTreatmentMin: number
  goldenHourPct: number; turnAwaysOrTransfers: number; meanCriticalQueueMin: number; utilisationPct: number
}

export interface EvaluationReport {
  headline: string; seeds: number; days: number; callsPerDay: number; fleetSize: number
  averages: SimResult[]; runs: SimResult[]; criticalResponseImprovementPct: number
  goldenHourGainPts: number; turnAwayReductionPct: number; generatedAt: string
}

export interface RankedHospital {
  id: number; name: string; feasible: boolean; note: string; travelMin: number; erWaitMin: number
  specialistDelayMin: number; timeToTreatmentMin: number
}
