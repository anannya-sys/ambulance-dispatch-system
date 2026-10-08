import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, Ambulance, BellRing, Layers as LayersIcon, MapPin, Siren, Wrench, X, Zap } from 'lucide-react'
import MapView, { type Layers } from '../components/MapView'
import { Stat, SectionTitle, Toggle } from '../components/ui'
import { api } from '../lib/api'
import { PATIENT_LABEL, PHASE_LABEL, SEVERITY_BG, SEVERITY_COLOR, STATUS_COLOR, STATUS_LABEL, clockText, mmss } from '../lib/format'
import type { FleetUnit, Hospital, PatientType, Settings, Severity } from '../lib/types'
import type { useLive } from '../lib/useLive'

type Live = ReturnType<typeof useLive>
type Mode = null | 'incident' | 'surge'

const EVENT_TONE: Record<string, string> = {
  CALL: 'text-sky-300', ASSIGNED: 'text-amber-300', ACKNOWLEDGED: 'text-slate-300', AT_SCENE: 'text-fuchsia-300',
  PICKED_UP: 'text-red-300', PRE_ALERT: 'text-blue-300', AT_HOSPITAL: 'text-blue-300', CLOSED: 'text-emerald-300',
  CORRIDOR: 'text-emerald-300', REASSIGNED: 'text-orange-300', BREAKDOWN: 'text-orange-300',
}

export default function LiveOps({ live }: { live: Live }) {
  const { snapshot, plan, events, alerts } = live
  const [hospitals, setHospitals] = useState<Hospital[]>([])
  const [settings, setSettings] = useState<Settings | null>(null)
  const [layers, setLayers] = useState<Layers>({ forecast: true, posts: true, hospitals: true, routes: true, corridor: true })
  const [baseMap, setBaseMap] = useState<'streets' | 'roads'>('streets')
  const [mode, setMode] = useState<Mode>(null)
  const [pick, setPick] = useState<[number, number] | null>(null)
  const [selectedTrip, setSelectedTrip] = useState<number | null>(null)
  const [selectedAmb, setSelectedAmb] = useState<FleetUnit | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [listTab, setListTab] = useState<'trips' | 'queue'>('trips')

  useEffect(() => {
    const load = () => api.hospitals().then(setHospitals).catch(() => {})
    load()
    api.settings().then(setSettings).catch(() => {})
    const t = setInterval(load, 5000)
    return () => clearInterval(t)
  }, [])
  useEffect(() => { if (toast) { const t = setTimeout(() => setToast(null), 4500); return () => clearTimeout(t) } }, [toast])

  const k = snapshot?.kpis
  const trip = useMemo(() => snapshot?.trips.find(t => t.tripId === selectedTrip) ?? null, [snapshot, selectedTrip])
  const unit = selectedAmb ? snapshot?.fleet.find(u => u.id === selectedAmb.id) ?? selectedAmb : null

  const update = async (s: Partial<Settings>) => setSettings(await api.updateSettings(s))

  return (
    <div className="relative grid h-full grid-cols-[340px_1fr_330px]">
      {/* ---------------- left: KPIs, actions, trips ---------------- */}
      <aside className="flex min-h-0 flex-col gap-3 border-r border-ink-700 bg-ink-900 p-3">
        <div className="grid grid-cols-2 gap-2">
          <Stat label="Active trips" value={k?.activeTrips ?? '-'} sub={`${k?.busy ?? 0} units busy`} />
          <Stat label="Waiting calls" value={k?.queued ?? '-'} tone={k && k.queued > 0 ? 'text-red-400' : 'text-white'} sub="priority queue" />
          <Stat label="Available" value={k?.available ?? '-'} tone="text-emerald-400" sub={`${k?.outOfService ?? 0} out of service`} />
          <Stat label="Utilisation" value={`${Math.round(k?.utilisationPct ?? 0)}%`} sub="of in-service fleet" />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <button className="btn-primary" onClick={() => { setMode('incident'); setPick(null) }}><Siren className="h-4 w-4" />Report emergency</button>
          <button className="btn-ghost" onClick={() => { setMode('surge'); setPick(null) }}><Zap className="h-4 w-4 text-amber-400" />Surge (MCI)</button>
        </div>

        <div className="flex gap-1 rounded-lg bg-ink-850 p-1 text-xs font-semibold">
          {(['trips', 'queue'] as const).map(t => (
            <button key={t} onClick={() => setListTab(t)} className={`flex-1 rounded-md py-1.5 ${listTab === t ? 'bg-ink-700 text-white' : 'text-slate-400'}`}>
              {t === 'trips' ? `Active trips (${snapshot?.trips.length ?? 0})` : `Queue (${snapshot?.queue.length ?? 0})`}
            </button>
          ))}
        </div>
        <div className="scroll-thin -mr-1 min-h-0 flex-1 space-y-2 overflow-y-auto pr-1">
          {listTab === 'trips' && snapshot?.trips.map(t => (
            <button key={t.tripId} onClick={() => setSelectedTrip(t.tripId === selectedTrip ? null : t.tripId)}
              className={`w-full rounded-lg border p-2.5 text-left transition ${selectedTrip === t.tripId ? 'border-slate-400 bg-ink-800' : 'border-ink-700 bg-ink-850 hover:border-ink-600'}`}>
              <div className="flex items-center gap-2">
                <span className={`chip ${SEVERITY_BG[t.severity]}`}>{t.severity}</span>
                <span className="text-[13px] font-semibold text-white">{PATIENT_LABEL[t.patientType]}</span>
                <span className="truncate text-xs text-slate-400">· {t.area}</span>
                <span className="ml-auto font-mono text-xs text-slate-400">{mmss(t.sinceCallSeconds)}</span>
              </div>
              <div className="mt-1.5 flex items-center gap-2 text-xs">
                <Ambulance className="h-3.5 w-3.5 text-slate-400" />
                <span className="font-semibold text-slate-200">{t.callSign}</span>
                <span className="text-slate-400">{PHASE_LABEL[t.phase] ?? t.phase}</span>
                {t.etaSeconds != null && <span className="ml-auto font-mono text-amber-300">ETA {mmss(t.etaSeconds)}</span>}
              </div>
              {t.hospitalName && <div className="mt-1 truncate text-[11px] text-blue-300">→ {t.hospitalName}</div>}
              {t.corridor && <div className="mt-1 text-[11px] font-medium text-emerald-400">Green corridor · {t.signals.filter(s => s.state !== 'PENDING').length}/{t.signals.length} signals</div>}
            </button>
          ))}
          {listTab === 'queue' && (snapshot?.queue.length ? snapshot.queue.map(q => (
            <div key={q.id} className="rounded-lg border border-ink-700 bg-ink-850 p-2.5">
              <div className="flex items-center gap-2">
                <span className={`chip ${SEVERITY_BG[q.severity]}`}>{q.severity}</span>
                <span className="text-[13px] font-semibold">{PATIENT_LABEL[q.patientType]}</span>
                <span className="truncate text-xs text-slate-400">· {q.area}</span>
              </div>
              <div className="mt-1 flex justify-between text-xs text-slate-400">
                <span>waiting {mmss(q.waitingSeconds)}</span><span className="font-mono">priority {q.priority.toFixed(0)}</span>
              </div>
            </div>
          )) : <div className="rounded-lg border border-dashed border-ink-700 p-4 text-center text-xs text-slate-500">No calls waiting. Every call has an ambulance.</div>)}
          {listTab === 'trips' && !snapshot?.trips.length && <div className="rounded-lg border border-dashed border-ink-700 p-4 text-center text-xs text-slate-500">No active trips yet. Calls arrive automatically, or report one.</div>}
        </div>
      </aside>

      {/* ---------------- centre: map ---------------- */}
      <section className="relative min-h-0">
        <MapView snapshot={snapshot} plan={plan} hospitals={hospitals} layers={layers} baseMap={baseMap}
          onTilesFailed={() => setBaseMap('roads')} picking={mode !== null} pick={pick} onPick={setPick}
          selectedTrip={selectedTrip} onSelectTrip={setSelectedTrip} onSelectAmbulance={setSelectedAmb} />

        {/* layer switcher */}
        <div className="absolute left-3 top-3 z-[500] w-52 rounded-xl border border-ink-700 bg-ink-900/90 p-2.5 text-xs backdrop-blur">
          <div className="mb-1.5 flex items-center gap-1.5 font-semibold text-slate-300"><LayersIcon className="h-3.5 w-3.5" />Layers</div>
          {([['forecast', 'Demand forecast (next hour)'], ['posts', 'Standby posts'], ['hospitals', 'Hospitals'], ['routes', 'Routes'], ['corridor', 'Green-corridor signals']] as const).map(([key, label]) => (
            <label key={key} className="flex cursor-pointer items-center gap-2 py-0.5 text-slate-300">
              <input type="checkbox" checked={layers[key]} onChange={e => setLayers({ ...layers, [key]: e.target.checked })} className="accent-red-500" />{label}
            </label>
          ))}
          <div className="mt-1.5 flex gap-1">
            {(['streets', 'roads'] as const).map(b => (
              <button key={b} onClick={() => setBaseMap(b)} className={`flex-1 rounded-md py-1 ${baseMap === b ? 'bg-ink-700 text-white' : 'bg-ink-850 text-slate-400'}`}>{b === 'streets' ? 'Street map' : 'Offline roads'}</button>
            ))}
          </div>
        </div>

        {/* legend */}
        <div className="absolute bottom-3 left-3 z-[500] flex flex-wrap gap-x-3 gap-y-1 rounded-xl border border-ink-700 bg-ink-900/90 px-3 py-2 text-[11px] backdrop-blur">
          {Object.entries(STATUS_LABEL).map(([s, l]) => <span key={s} className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-sm" style={{ background: STATUS_COLOR[s] }} />{l}</span>)}
        </div>

        {/* placement panel */}
        {mode && <ReportPanel mode={mode} pick={pick} onClose={() => { setMode(null); setPick(null) }} onDone={msg => { setToast(msg); setMode(null); setPick(null) }} />}

        {/* selected trip */}
        {trip && !mode && (
          <div className="absolute right-3 top-3 z-[500] w-80 animate-slidein rounded-xl border border-ink-600 bg-ink-900/95 p-3 text-sm shadow-2xl backdrop-blur">
            <div className="flex items-center gap-2">
              <span className={`chip ${SEVERITY_BG[trip.severity]}`}>{trip.severity}</span>
              <span className="font-semibold text-white">{PATIENT_LABEL[trip.patientType]} · {trip.area}</span>
              <button className="ml-auto text-slate-500 hover:text-white" onClick={() => setSelectedTrip(null)}><X className="h-4 w-4" /></button>
            </div>
            <div className="mt-2 grid grid-cols-3 gap-2 text-center">
              <div className="rounded-md bg-ink-850 py-1.5"><div className="label">Since call</div><div className="font-mono">{mmss(trip.sinceCallSeconds)}</div></div>
              <div className="rounded-md bg-ink-850 py-1.5"><div className="label">ETA</div><div className="font-mono text-amber-300">{mmss(trip.etaSeconds)}</div></div>
              <div className="rounded-md bg-ink-850 py-1.5"><div className="label">Unit</div><div className="font-mono">{trip.callSign}</div></div>
            </div>
            <div className="mt-2 text-xs text-slate-300">{PHASE_LABEL[trip.phase]}</div>
            {trip.hospitalReason && (
              <div className="mt-2 rounded-md border border-blue-500/30 bg-blue-500/10 p-2 text-xs text-blue-200">
                <div className="label mb-0.5 text-blue-300">Time-to-treatment choice</div>{trip.hospitalReason}
              </div>
            )}
            {trip.corridor && (
              <div className="mt-2 rounded-md border border-emerald-500/30 bg-emerald-500/10 p-2 text-xs text-emerald-200">
                Green corridor active: {trip.signals.filter(s => s.state === 'CLEARED').length} passed, {trip.signals.filter(s => s.state === 'GREEN').length} held green, {trip.signals.filter(s => s.state === 'PENDING').length} upcoming
              </div>
            )}
          </div>
        )}

        {unit && !trip && !mode && (
          <div className="absolute right-3 top-3 z-[500] w-64 animate-slidein rounded-xl border border-ink-600 bg-ink-900/95 p-3 text-sm shadow-2xl">
            <div className="flex items-center gap-2">
              <span className="font-semibold text-white">{unit.callSign}</span><span className="text-xs text-slate-400">{unit.kind}</span>
              <button className="ml-auto text-slate-500 hover:text-white" onClick={() => setSelectedAmb(null)}><X className="h-4 w-4" /></button>
            </div>
            <div className="mt-1 text-xs" style={{ color: STATUS_COLOR[unit.status] }}>{STATUS_LABEL[unit.status]}</div>
            <button className="btn-ghost mt-2 w-full text-xs" onClick={async () => {
              try { setToast((await api.breakdown(unit.id)).message) } catch (e) { setToast((e as Error).message) }
            }}><Wrench className="h-3.5 w-3.5 text-orange-400" />Simulate breakdown</button>
          </div>
        )}

        {toast && <div className="absolute bottom-14 left-1/2 z-[600] -translate-x-1/2 animate-slidein rounded-lg border border-ink-600 bg-ink-800 px-4 py-2 text-sm text-white shadow-2xl">{toast}</div>}
      </section>

      {/* ---------------- right: controls + feeds ---------------- */}
      <aside className="flex min-h-0 flex-col gap-3 border-l border-ink-700 bg-ink-900 p-3">
        <div className="card p-3">
          <SectionTitle>Simulation controls</SectionTitle>
          {settings && <>
            <div className="flex items-center justify-between text-[13px]"><span>Speed</span><span className="font-mono text-slate-400">{settings.speed}x</span></div>
            <input type="range" min={1} max={30} value={settings.speed} onChange={e => update({ speed: Number(e.target.value) })} className="w-full accent-red-500" />
            <div className="flex items-center justify-between text-[13px]"><span>Calls per day</span><span className="font-mono text-slate-400">{settings.callsPerDay}</span></div>
            <input type="range" min={100} max={1200} step={50} value={settings.callsPerDay} onChange={e => update({ callsPerDay: Number(e.target.value) })} className="w-full accent-red-500" />
            <Toggle on={settings.autoCalls} onChange={v => update({ autoCalls: v })} label="Simulated callers" hint="Calls follow the city's daily pattern" />
            <Toggle on={settings.prePositioning} onChange={v => update({ prePositioning: v })} label="Predictive pre-positioning" hint={plan?.active ? `${plan.posts.length} posts · ${plan.coverage8MinPct.toFixed(0)}% of forecast within 8 min` : 'Off: units return to home base'} />
            <Toggle on={settings.greenCorridor} onChange={v => update({ greenCorridor: v })} label="Green corridor (critical)" hint="Pre-clear signals on the route" />
          </>}
        </div>

        <div className="card flex min-h-0 flex-[3] flex-col p-3">
          <SectionTitle right={<span className="text-[11px] text-slate-500">via Kafka</span>}>Live dispatch feed</SectionTitle>
          <div className="scroll-thin min-h-0 flex-1 space-y-1.5 overflow-y-auto">
            {events.map((e, i) => (
              <div key={i} className="animate-slidein text-xs leading-snug">
                <span className="mr-1.5 font-mono text-slate-500">{clockText(e.at)}</span>
                <span className={`mr-1 font-semibold ${EVENT_TONE[e.type] ?? 'text-slate-300'}`}>{e.type.replace('_', ' ').toLowerCase()}</span>
                <span className="text-slate-300">{e.message}</span>
              </div>
            ))}
            {!events.length && <div className="text-xs text-slate-500">Waiting for events…</div>}
          </div>
        </div>

        <div className="card flex min-h-0 flex-[2] flex-col p-3">
          <SectionTitle right={<BellRing className="h-3.5 w-3.5 text-blue-400" />}>Hospital pre-alerts</SectionTitle>
          <div className="scroll-thin min-h-0 flex-1 space-y-2 overflow-y-auto">
            {alerts.map(a => (
              <div key={a.id} className="animate-slidein rounded-lg border-l-2 bg-ink-850 p-2 text-xs" style={{ borderColor: SEVERITY_COLOR[a.severity] }}>
                <div className="font-semibold text-slate-200">{a.hospitalName}</div>
                <div className="text-slate-400">{a.message}</div>
              </div>
            ))}
            {!alerts.length && <div className="text-xs text-slate-500">ERs are told what's coming before the patient arrives.</div>}
          </div>
        </div>
      </aside>
    </div>
  )
}

function ReportPanel({ mode, pick, onClose, onDone }: { mode: 'incident' | 'surge'; pick: [number, number] | null; onClose: () => void; onDone: (msg: string) => void }) {
  const [severity, setSeverity] = useState<Severity>('CRITICAL')
  const [type, setType] = useState<PatientType>('CARDIAC')
  const [casualties, setCasualties] = useState(8)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async () => {
    if (!pick) return
    setBusy(true); setError(null)
    try {
      if (mode === 'incident') {
        const r = await api.createIncident({ lat: pick[0], lon: pick[1], severity, patientType: type, description: 'Reported from dashboard' })
        onDone(r.message)
      } else {
        const r = await api.surge({ lat: pick[0], lon: pick[1], casualties, description: 'Mass-casualty incident' })
        onDone(`Surge: ${r.patients} patients, ambulances dispatched in parallel`)
      }
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }

  return (
    <div className="absolute right-3 top-3 z-[500] w-80 animate-slidein rounded-xl border border-red-500/40 bg-ink-900/95 p-3 shadow-2xl backdrop-blur">
      <div className="flex items-center gap-2">
        {mode === 'incident' ? <Siren className="h-4 w-4 text-red-400" /> : <AlertTriangle className="h-4 w-4 text-amber-400" />}
        <span className="font-semibold text-white">{mode === 'incident' ? 'Report emergency' : 'Mass-casualty incident'}</span>
        <button className="ml-auto text-slate-500 hover:text-white" onClick={onClose}><X className="h-4 w-4" /></button>
      </div>
      <div className={`mt-2 flex items-center gap-2 rounded-md p-2 text-xs ${pick ? 'bg-ink-850 text-slate-300' : 'bg-red-500/10 text-red-200'}`}>
        <MapPin className="h-3.5 w-3.5" />{pick ? `${pick[0].toFixed(4)}, ${pick[1].toFixed(4)}` : 'Click the map where the caller is'}
      </div>
      {mode === 'incident' ? (<>
        <div className="label mt-3">Severity</div>
        <div className="mt-1 grid grid-cols-3 gap-1">
          {(['CRITICAL', 'SERIOUS', 'MINOR'] as Severity[]).map(s => (
            <button key={s} onClick={() => setSeverity(s)} className={`rounded-md py-1.5 text-xs font-semibold ${severity === s ? SEVERITY_BG[s] : 'bg-ink-850 text-slate-400'}`}>{s}</button>
          ))}
        </div>
        <div className="label mt-3">Patient type</div>
        <div className="mt-1 grid grid-cols-3 gap-1">
          {(Object.keys(PATIENT_LABEL) as PatientType[]).map(t => (
            <button key={t} onClick={() => setType(t)} className={`rounded-md py-1.5 text-xs ${type === t ? 'bg-slate-200 font-semibold text-ink-950' : 'bg-ink-850 text-slate-400'}`}>{PATIENT_LABEL[t]}</button>
          ))}
        </div>
      </>) : (<>
        <div className="mt-3 flex justify-between text-xs"><span className="label">Casualties</span><span className="font-mono">{casualties}</span></div>
        <input type="range" min={2} max={20} value={casualties} onChange={e => setCasualties(Number(e.target.value))} className="w-full accent-amber-500" />
        <p className="text-[11px] text-slate-500">Each patient becomes a call. Several ambulances go at once and patients are spread across hospitals as beds get reserved.</p>
      </>)}
      {error && <div className="mt-2 text-xs text-red-300">{error}</div>}
      <button className="btn-primary mt-3 w-full" disabled={!pick || busy} onClick={submit}>{busy ? 'Dispatching…' : 'Dispatch'}</button>
    </div>
  )
}
