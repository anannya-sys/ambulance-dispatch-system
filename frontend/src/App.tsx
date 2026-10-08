import { useEffect, useState } from 'react'
import { Activity, BarChart3, Building2, Map as MapIcon, Radio, Trophy } from 'lucide-react'
import { useLive } from './lib/useLive'
import { api } from './lib/api'
import LiveOps from './pages/LiveOps'
import HospitalsPage from './pages/HospitalsPage'
import AnalyticsPage from './pages/AnalyticsPage'
import ImpactPage from './pages/ImpactPage'

type Tab = 'live' | 'hospitals' | 'analytics' | 'impact'

const TABS: { id: Tab; label: string; icon: typeof MapIcon }[] = [
  { id: 'live', label: 'Live operations', icon: MapIcon },
  { id: 'hospitals', label: 'Hospitals', icon: Building2 },
  { id: 'analytics', label: 'Analytics', icon: BarChart3 },
  { id: 'impact', label: 'Impact', icon: Trophy },
]

export default function App() {
  const live = useLive()
  const [tab, setTab] = useState<Tab>('live')

  // first forecast before the first push arrives
  useEffect(() => { api.forecast().then(live.setPlan).catch(() => {}) }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const clock = live.snapshot?.clock
  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center gap-6 border-b border-ink-700 bg-ink-900 px-4">
        <div className="flex items-center gap-2.5">
          <div className="grid h-8 w-8 place-items-center rounded-lg bg-red-500 shadow-lg shadow-red-500/30">
            <Activity className="h-5 w-5 text-white" strokeWidth={2.6} />
          </div>
          <div className="leading-tight">
            <div className="text-[15px] font-bold tracking-tight text-white">GoldenHour</div>
            <div className="text-[10px] font-medium uppercase tracking-widest text-slate-500">Ambulance dispatch · Mumbai</div>
          </div>
        </div>
        <nav className="flex gap-1">
          {TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)}
              className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition ${tab === t.id ? 'bg-ink-700 text-white' : 'text-slate-400 hover:bg-ink-800 hover:text-slate-200'}`}>
              <t.icon className="h-4 w-4" />{t.label}
            </button>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-4 text-xs">
          <span className="chip bg-amber-500/10 text-amber-300 ring-1 ring-amber-500/30">Simulated fleet of 50 · real Mumbai roads</span>
          {clock && (
            <div className="text-right font-mono leading-tight">
              <div className="text-sm text-white">{clock.slice(11, 16)}</div>
              <div className="text-[10px] text-slate-500">{clock.slice(0, 10)} · {live.snapshot?.speed}x</div>
            </div>
          )}
          <span className={`flex items-center gap-1.5 ${live.connected ? 'text-emerald-400' : 'text-slate-500'}`}>
            <Radio className="h-3.5 w-3.5" />{live.connected ? 'Live' : 'Connecting'}
          </span>
        </div>
      </header>
      <main className="min-h-0 flex-1">
        {tab === 'live' && <LiveOps live={live} />}
        {tab === 'hospitals' && <HospitalsPage alerts={live.alerts} />}
        {tab === 'analytics' && <AnalyticsPage />}
        {tab === 'impact' && <ImpactPage />}
      </main>
    </div>
  )
}
