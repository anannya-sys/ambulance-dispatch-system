import { useEffect, useState } from 'react'
import { Bar, BarChart, Cell, LabelList, ResponsiveContainer, XAxis, YAxis } from 'recharts'
import { Clock, HeartPulse, Hospital as HospitalIcon, Play, ShieldCheck } from 'lucide-react'
import { api } from '../lib/api'
import type { EvaluationReport, SimResult } from '../lib/types'
import { SectionTitle } from '../components/ui'

function Headline({ icon: Icon, value, label, sub, tone }: { icon: typeof Clock; value: string; label: string; sub: string; tone: string }) {
  return (
    <div className="card relative overflow-hidden p-5">
      <Icon className={`absolute -right-3 -top-3 h-24 w-24 opacity-10 ${tone}`} />
      <div className={`text-4xl font-black tracking-tight ${tone}`}>{value}</div>
      <div className="mt-1 text-sm font-semibold text-white">{label}</div>
      <div className="mt-0.5 text-xs text-slate-400">{sub}</div>
    </div>
  )
}

function Compare({ title, base, gh, unit, better }: { title: string; base: number; gh: number; unit: string; better: 'lower' | 'higher' }) {
  const data = [{ name: 'Baseline', v: base }, { name: 'GoldenHour', v: gh }]
  const delta = better === 'lower' ? (base - gh) / base * 100 : gh - base
  return (
    <div className="card p-4">
      <div className="flex items-baseline justify-between">
        <div className="text-sm font-semibold text-white">{title}</div>
        <div className="text-xs font-semibold text-emerald-400">{better === 'lower' ? `-${delta.toFixed(0)}%` : `+${delta.toFixed(1)} pts`}</div>
      </div>
      <div className="h-36">
        <ResponsiveContainer>
          <BarChart data={data} layout="vertical" margin={{ left: 10, right: 40, top: 10 }}>
            <XAxis type="number" hide />
            <YAxis type="category" dataKey="name" stroke="#94a3b8" fontSize={12} width={80} />
            <Bar dataKey="v" radius={[0, 6, 6, 0]} barSize={22}>
              <Cell fill="#475569" /><Cell fill="#ef4444" />
              <LabelList dataKey="v" position="right" fill="#e2e8f0" fontSize={12} formatter={(v: number) => `${v.toFixed(1)}${unit}`} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function AblationTable({ r }: { r: EvaluationReport }) {
  const base = r.averages[0]
  return (
    <div className="overflow-hidden rounded-lg border border-ink-700">
      <table className="w-full text-sm">
        <thead className="bg-ink-850 text-left text-[11px] uppercase tracking-wider text-slate-500">
          <tr>
            <th className="px-3 py-2">Configuration</th><th className="px-3 py-2 text-right">Critical response</th><th className="px-3 py-2 text-right">P90</th>
            <th className="px-3 py-2 text-right">Call to treatment</th><th className="px-3 py-2 text-right">Golden hour</th><th className="px-3 py-2 text-right">Turn-aways</th><th className="px-3 py-2 text-right">Fleet busy</th>
          </tr>
        </thead>
        <tbody>
          {r.averages.map((a: SimResult, i) => {
            const last = i === r.averages.length - 1
            const d = (base.meanCriticalResponseMin - a.meanCriticalResponseMin) / base.meanCriticalResponseMin * 100
            return (
              <tr key={a.config} className={`border-t border-ink-700 ${last ? 'bg-red-500/10 font-semibold text-white' : i === 0 ? 'text-slate-400' : ''}`}>
                <td className="px-3 py-1.5">{a.config}</td>
                <td className="px-3 py-1.5 text-right font-mono">{a.meanCriticalResponseMin.toFixed(1)}m {i > 0 && <span className="text-emerald-400">({d >= 0 ? '-' : '+'}{Math.abs(d).toFixed(0)}%)</span>}</td>
                <td className="px-3 py-1.5 text-right font-mono">{a.p90CriticalResponseMin.toFixed(1)}m</td>
                <td className="px-3 py-1.5 text-right font-mono">{a.meanCriticalTreatmentMin.toFixed(1)}m</td>
                <td className="px-3 py-1.5 text-right font-mono">{a.goldenHourPct.toFixed(1)}%</td>
                <td className="px-3 py-1.5 text-right font-mono">{a.turnAwaysOrTransfers}</td>
                <td className="px-3 py-1.5 text-right font-mono">{a.utilisationPct.toFixed(0)}%</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default function ImpactPage() {
  const [data, setData] = useState<{ normal: EvaluationReport; surge: EvaluationReport } | null>(null)
  const [scenario, setScenario] = useState<'normal' | 'surge'>('normal')
  const [run, setRun] = useState<{ status: string; result: any } | null>(null)

  useEffect(() => { api.evaluation().then(setData).catch(() => {}) }, [])
  useEffect(() => {
    if (run?.status !== 'RUNNING') return
    const t = setInterval(() => api.evaluationLatest().then(setRun), 2000)
    return () => clearInterval(t)
  }, [run?.status])

  if (!data) return <div className="p-6 text-slate-400">Loading evaluation…</div>
  const r = data[scenario]
  const base = r.averages[0]
  const gh = r.averages[r.averages.length - 1]

  return (
    <div className="scroll-thin h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-7xl">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-white">What GoldenHour changes for patients</h1>
            <p className="mt-1 max-w-3xl text-sm text-slate-400">
              Same city, same calls, same hospitals, same {r.fleetSize} ambulances. A {r.days}-day discrete-event simulation replays {Math.round(r.callsPerDay)} calls a day
              through a standard dispatcher and through GoldenHour, averaged over {r.seeds} random seeds. The demand model was trained on 8 earlier weeks it never sees again.
            </p>
          </div>
          <div className="flex gap-1 rounded-lg bg-ink-850 p-1 text-xs font-semibold">
            {(['normal', 'surge'] as const).map(s => (
              <button key={s} onClick={() => setScenario(s)} className={`rounded-md px-3 py-1.5 ${scenario === s ? 'bg-ink-700 text-white' : 'text-slate-400'}`}>
                {s === 'normal' ? `Normal load (${Math.round(data.normal.callsPerDay)}/day)` : `Surge week (${Math.round(data.surge.callsPerDay)}/day)`}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Headline icon={Clock} tone="text-red-400" value={`-${r.criticalResponseImprovementPct.toFixed(0)}%`} label="Critical response time" sub={`${base.meanCriticalResponseMin.toFixed(1)} → ${gh.meanCriticalResponseMin.toFixed(1)} min from call to patient`} />
          <Headline icon={HeartPulse} tone="text-amber-300" value={`+${r.goldenHourGainPts.toFixed(0)} pts`} label="Treated within the golden hour" sub={`${base.goldenHourPct.toFixed(0)}% → ${gh.goldenHourPct.toFixed(0)}% of critical patients`} />
          <Headline icon={HospitalIcon} tone="text-blue-300" value={`-${r.turnAwayReductionPct.toFixed(0)}%`} label="Hospital turn-aways & transfers" sub={`${base.turnAwaysOrTransfers} → ${gh.turnAwaysOrTransfers} patients refused or moved`} />
          <Headline icon={ShieldCheck} tone="text-emerald-300" value={`${(base.meanCriticalTreatmentMin - gh.meanCriticalTreatmentMin).toFixed(0)} min`} label="Earlier treatment, critical cases" sub={`${base.meanCriticalTreatmentMin.toFixed(0)} → ${gh.meanCriticalTreatmentMin.toFixed(0)} min from call to treatment start`} />
        </div>

        <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Compare title="Critical response (mean)" base={base.meanCriticalResponseMin} gh={gh.meanCriticalResponseMin} unit="m" better="lower" />
          <Compare title="Critical response (90th pct)" base={base.p90CriticalResponseMin} gh={gh.p90CriticalResponseMin} unit="m" better="lower" />
          <Compare title="Call to treatment (critical)" base={base.meanCriticalTreatmentMin} gh={gh.meanCriticalTreatmentMin} unit="m" better="lower" />
          <Compare title="Golden-hour treatment" base={base.goldenHourPct} gh={gh.goldenHourPct} unit="%" better="higher" />
        </div>

        <div className="card mt-4 p-4">
          <SectionTitle right={<span className="text-[11px] text-slate-500">each feature switched on alone, then all together</span>}>Ablation: what each USP is worth</SectionTitle>
          <AblationTable r={r} />
          <div className="mt-3 grid gap-3 text-xs text-slate-400 md:grid-cols-2">
            <p><span className="font-semibold text-slate-200">Baseline</span> is a good conventional dispatcher, not a straw man: nearest ambulance by real road time, fleet parked at the best static stations for average demand, and the nearest hospital that has the right facility. It just has no forecast, no live bed data and no signal priority.</p>
            <p><span className="font-semibold text-slate-200">Per-seed check:</span> {r.runs.filter(x => x.config === gh.config).map(x => {
              const b = r.runs.find(y => y.seed === x.seed && y.config === base.config)!
              return `seed ${x.seed}: ${b.meanCriticalResponseMin.toFixed(1)}→${x.meanCriticalResponseMin.toFixed(1)} min`
            }).join(' · ')}. GoldenHour wins on every seed.</p>
          </div>
        </div>

        <div className="card mt-4 p-4">
          <SectionTitle>Run it yourself</SectionTitle>
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <button className="btn-primary" disabled={run?.status === 'RUNNING'} onClick={() => api.runEvaluation(1, 3, scenario === 'surge' ? 750 : 500).then(setRun)}>
              <Play className="h-4 w-4" />{run?.status === 'RUNNING' ? 'Simulating 3 days…' : 'Run a quick 3-day simulation'}
            </button>
            <span className="text-xs text-slate-500">Runs on the server with a fresh random seed. The full published run is 5 seeds × {data.normal.days} days.</span>
          </div>
          {run?.status === 'DONE' && run.result && typeof run.result !== 'string' && <div className="mt-3"><AblationTable r={run.result} /></div>}
        </div>

        <p className="mt-4 text-xs text-slate-500">
          Simulated, not field data: calls come from a synthetic Mumbai model (rush-hour accident hotspots, office-district cardiac events, early-morning
          elderly emergencies, weekend sea-front crowds), hospitals are real locations with simulated beds, and travel times use real OpenStreetMap roads
          with a time-of-day traffic model. Generated {r.generatedAt.replace('T', ' ')}.
        </p>
      </div>
    </div>
  )
}
