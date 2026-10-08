import { useEffect, useState } from 'react'
import { BellRing, Building2, Search } from 'lucide-react'
import { api } from '../lib/api'
import { PATIENT_LABEL, SEVERITY_BG, SEVERITY_COLOR } from '../lib/format'
import type { Alert, Hospital, PatientType, RankedHospital, Severity } from '../lib/types'
import { SectionTitle } from '../components/ui'

const BEDS = ['ICU', 'CARDIAC', 'TRAUMA', 'GENERAL']
const PLACES: [string, number, number][] = [
  ['Andheri East', 19.1150, 72.8650], ['Bandra West', 19.0600, 72.8360], ['BKC', 19.0660, 72.8680], ['Byculla', 18.9790, 72.8330],
  ['Chembur', 19.0620, 72.9000], ['Colaba', 18.9067, 72.8147], ['Dadar', 19.0190, 72.8430], ['Ghatkopar', 19.0860, 72.9080],
  ['Goregaon', 19.1630, 72.8500], ['Kurla', 19.0720, 72.8830], ['Malad', 19.1860, 72.8480], ['Powai', 19.1180, 72.9050],
  ['Sion', 19.0430, 72.8620], ['Worli', 19.0000, 72.8150],
]

function BedBar({ free, total }: { free: number; total: number }) {
  if (total === 0) return <div className="text-[11px] text-slate-600">none</div>
  const pct = free / total
  const color = free === 0 ? 'bg-red-500' : pct < 0.25 ? 'bg-amber-400' : 'bg-emerald-500'
  return (
    <div>
      <div className="flex justify-between text-[11px]"><span className="font-mono text-slate-300">{free}/{total}</span></div>
      <div className="mt-0.5 h-1.5 overflow-hidden rounded bg-ink-700"><div className={`h-full ${color}`} style={{ width: `${Math.max(4, pct * 100)}%` }} /></div>
    </div>
  )
}

export default function HospitalsPage({ alerts: liveAlerts }: { alerts: Alert[] }) {
  const [hospitals, setHospitals] = useState<Hospital[]>([])
  const [stored, setStored] = useState<any[]>([])
  const [place, setPlace] = useState(PLACES[6])
  const [type, setType] = useState<PatientType>('CARDIAC')
  const [severity, setSeverity] = useState<Severity>('CRITICAL')
  const [ranking, setRanking] = useState<RankedHospital[]>([])

  useEffect(() => {
    const load = () => { api.hospitals().then(setHospitals); api.alerts().then(setStored) }
    load()
    const t = setInterval(load, 4000)
    return () => clearInterval(t)
  }, [])
  useEffect(() => { api.recommend(place[1], place[2], type, severity).then(setRanking).catch(() => setRanking([])) }, [place, type, severity])

  const names = new Map(hospitals.map(h => [h.id, h.name]))
  const alerts = liveAlerts.length ? liveAlerts : stored.map(a => ({ ...a, hospitalName: names.get(a.hospitalId) }))
  const best = ranking.find(r => r.feasible)
  const nearest = [...ranking].sort((a, b) => a.travelMin - b.travelMin)[0]

  return (
    <div className="grid h-full grid-cols-[1fr_380px] gap-4 overflow-hidden p-4">
      <div className="flex min-h-0 flex-col gap-4">
        <div className="card p-4">
          <SectionTitle right={<span className="text-[11px] text-slate-500">USP 2 · same logic the dispatcher uses</span>}>Time-to-treatment explorer</SectionTitle>
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <Search className="h-4 w-4 text-slate-500" />
            <select value={place[0]} onChange={e => setPlace(PLACES.find(p => p[0] === e.target.value)!)} className="rounded-md border border-ink-600 bg-ink-850 px-2 py-1.5">
              {PLACES.map(p => <option key={p[0]}>{p[0]}</option>)}
            </select>
            <select value={type} onChange={e => setType(e.target.value as PatientType)} className="rounded-md border border-ink-600 bg-ink-850 px-2 py-1.5">
              {Object.entries(PATIENT_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <select value={severity} onChange={e => setSeverity(e.target.value as Severity)} className="rounded-md border border-ink-600 bg-ink-850 px-2 py-1.5">
              {['CRITICAL', 'SERIOUS', 'MINOR'].map(s => <option key={s}>{s}</option>)}
            </select>
            {best && nearest && best.id !== nearest.id && (
              <span className="ml-auto rounded-md bg-blue-500/10 px-2 py-1 text-xs text-blue-200">
                Nearest is {nearest.name} ({nearest.feasible ? `${nearest.timeToTreatmentMin} min to treatment` : nearest.note}); GoldenHour picks {best.name}
              </span>
            )}
          </div>
          <div className="mt-3 overflow-hidden rounded-lg border border-ink-700">
            <table className="w-full text-sm">
              <thead className="bg-ink-850 text-left text-[11px] uppercase tracking-wider text-slate-500">
                <tr><th className="px-3 py-2">#</th><th className="px-3 py-2">Hospital</th><th className="px-3 py-2 text-right">Travel</th><th className="px-3 py-2 text-right">ER wait</th><th className="px-3 py-2 text-right">Specialist</th><th className="px-3 py-2 text-right">Treatment in</th><th className="px-3 py-2">Note</th></tr>
              </thead>
              <tbody>
                {ranking.slice(0, 7).map((r, i) => (
                  <tr key={r.id} className={`border-t border-ink-700 ${i === 0 && r.feasible ? 'bg-emerald-500/10' : ''} ${!r.feasible ? 'text-slate-500' : ''}`}>
                    <td className="px-3 py-1.5 font-mono">{i + 1}</td>
                    <td className="px-3 py-1.5 font-medium">{r.name}</td>
                    <td className="px-3 py-1.5 text-right font-mono">{r.travelMin.toFixed(1)}m</td>
                    <td className="px-3 py-1.5 text-right font-mono">{r.feasible ? `${r.erWaitMin.toFixed(1)}m` : '-'}</td>
                    <td className="px-3 py-1.5 text-right font-mono">{r.feasible ? `${r.specialistDelayMin.toFixed(0)}m` : '-'}</td>
                    <td className={`px-3 py-1.5 text-right font-mono font-semibold ${i === 0 && r.feasible ? 'text-emerald-300' : ''}`}>{r.feasible ? `${r.timeToTreatmentMin.toFixed(1)}m` : 'can\'t treat'}</td>
                    <td className="px-3 py-1.5 text-xs">{r.note}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card flex min-h-0 flex-1 flex-col p-4">
          <SectionTitle right={<span className="text-[11px] text-slate-500">real locations · generic names · simulated beds</span>}>Live bed board ({hospitals.length} hospitals)</SectionTitle>
          <div className="scroll-thin min-h-0 flex-1 overflow-y-auto">
            <div className="grid grid-cols-2 gap-2 xl:grid-cols-3">
              {hospitals.map(h => (
                <div key={h.id} className="rounded-lg border border-ink-700 bg-ink-850 p-3">
                  <div className="flex items-start gap-2">
                    <Building2 className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" />
                    <div className="min-w-0">
                      <div className="truncate text-[13px] font-semibold text-white">{h.name}</div>
                      <div className="mt-0.5 flex flex-wrap gap-1">
                        {h.capabilities.map(c => <span key={c} className="chip bg-ink-700 text-slate-300">{c.replace('_', ' ').toLowerCase()}</span>)}
                        {h.specialists24x7 && <span className="chip bg-violet-500/15 text-violet-300">24x7 specialists</span>}
                      </div>
                    </div>
                  </div>
                  <div className="mt-2 grid grid-cols-4 gap-2">
                    {BEDS.map(b => <div key={b}><div className="text-[10px] font-semibold text-slate-500">{b}</div><BedBar {...h.beds[b]} /></div>)}
                  </div>
                  <div className="mt-2 text-[11px] text-slate-400">ER queue <span className="font-mono text-slate-200">{h.erQueue}</span> · {h.erBays} bays</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="card flex min-h-0 flex-col p-4">
        <SectionTitle right={<BellRing className="h-4 w-4 text-blue-400" />}>ER pre-alerts</SectionTitle>
        <p className="mb-3 text-xs text-slate-400">Sent the moment the patient is picked up, with the bed already reserved, so the ER team is ready at the door.</p>
        <div className="scroll-thin min-h-0 flex-1 space-y-2 overflow-y-auto">
          {alerts.map((a: any) => (
            <div key={a.id} className="rounded-lg border-l-2 bg-ink-850 p-2.5 text-xs" style={{ borderColor: SEVERITY_COLOR[a.severity as Severity] }}>
              <div className="flex items-center gap-2">
                <span className={`chip ${SEVERITY_BG[a.severity as Severity]}`}>{a.severity}</span>
                <span className="font-semibold text-slate-200">{a.hospitalName}</span>
              </div>
              <div className="mt-1 text-slate-300">{a.message}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
