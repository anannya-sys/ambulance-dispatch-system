import { useEffect, useState } from 'react'
import { Area, AreaChart, CartesianGrid, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Circle, MapContainer, TileLayer, Tooltip as LTooltip } from 'react-leaflet'
import { api } from '../lib/api'
import type { Summary } from '../lib/types'
import { SectionTitle, Stat } from '../components/ui'

type Zone = { area: string; lat: number; lon: number; trips: number; avgResponseMin: number }

function zoneColor(min: number) {
  if (min <= 6) return '#22c55e'
  if (min <= 9) return '#a3e635'
  if (min <= 12) return '#f59e0b'
  return '#ef4444'
}

export default function AnalyticsPage() {
  const [s, setS] = useState<Summary | null>(null)
  const [zones, setZones] = useState<Zone[]>([])
  const [hourly, setHourly] = useState<{ hour: string; calls: number; forecast: number }[]>([])

  useEffect(() => {
    const load = () => {
      api.summary().then(setS)
      api.zones().then(setZones)
      api.hourly().then(h => setHourly(h.map(p => ({ ...p, hour: p.hour.slice(11, 16) }))))
    }
    load()
    const t = setInterval(load, 8000)
    return () => clearInterval(t)
  }, [])

  return (
    <div className="scroll-thin h-full overflow-y-auto p-4">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-8">
        <Stat label="Calls (24h)" value={s?.calls ?? '-'} sub={`${s?.surgeCalls ?? 0} from surges`} />
        <Stat label="Completed trips" value={s?.completedTrips ?? '-'} />
        <Stat label="Avg response" value={`${s?.avgResponseMin ?? '-'}m`} sub="call to patient" />
        <Stat label="Critical response" value={`${s?.avgCriticalResponseMin ?? '-'}m`} tone="text-red-300" sub={`${s?.criticalWithin15Pct ?? 0}% within 15 min`} />
        <Stat label="Golden hour" value={`${s?.goldenHourPct ?? '-'}%`} tone="text-amber-300" sub="critical at hospital < 60 min" />
        <Stat label="Green corridors" value={s?.corridorTrips ?? '-'} tone="text-emerald-300" sub={`${s?.signalsCleared ?? 0} signals · ${(s?.corridorMinutesSaved ?? 0).toFixed(0)} min saved`} />
        <Stat label="ER pre-alerts" value={s?.preAlerts ?? '-'} tone="text-blue-300" />
        <Stat label="Auto-reassigned" value={s?.reassignments ?? '-'} tone="text-orange-300" sub="no ack / breakdown" />
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-2">
        <div className="card p-4">
          <SectionTitle right={<span className="text-[11px] text-slate-500">demand model vs reality</span>}>Calls per hour (last 24 simulated hours)</SectionTitle>
          <div className="h-72">
            <ResponsiveContainer>
              <AreaChart data={hourly} margin={{ left: -20, right: 8, top: 8 }}>
                <defs><linearGradient id="g" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#ef4444" stopOpacity={0.5} /><stop offset="1" stopColor="#ef4444" stopOpacity={0} /></linearGradient></defs>
                <CartesianGrid stroke="#1c2944" vertical={false} />
                <XAxis dataKey="hour" stroke="#64748b" fontSize={11} />
                <YAxis stroke="#64748b" fontSize={11} />
                <Tooltip contentStyle={{ background: '#0f1729', border: '1px solid #2a3a5c', borderRadius: 8 }} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Area type="monotone" dataKey="calls" name="Actual calls" stroke="#ef4444" fill="url(#g)" strokeWidth={2} />
                <Line type="monotone" dataKey="forecast" name="Forecast" stroke="#f59e0b" strokeDasharray="5 4" dot={false} strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card p-4">
          <SectionTitle right={<span className="text-[11px] text-slate-500">avg response per ~1 km zone</span>}>Slow-zone heatmap</SectionTitle>
          <div className="h-72 overflow-hidden rounded-lg">
            <MapContainer center={[19.06, 72.87]} zoom={11} zoomControl={false} preferCanvas className="h-full w-full">
              <TileLayer url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png" attribution='&copy; OpenStreetMap &copy; CARTO' />
              {zones.map(z => (
                <Circle key={`${z.lat}${z.lon}`} center={[z.lat, z.lon]} radius={480 + z.trips * 40}
                  pathOptions={{ color: zoneColor(z.avgResponseMin), weight: 1, fillColor: zoneColor(z.avgResponseMin), fillOpacity: 0.45 }}>
                  <LTooltip className="gh-tip">{z.area}: {z.avgResponseMin} min avg · {z.trips} trips</LTooltip>
                </Circle>
              ))}
            </MapContainer>
          </div>
        </div>
      </div>

      <div className="card mt-4 p-4">
        <SectionTitle>Slowest zones right now</SectionTitle>
        <div className="grid grid-cols-2 gap-2 md:grid-cols-4 xl:grid-cols-6">
          {zones.slice(0, 12).map(z => (
            <div key={`${z.lat}${z.lon}`} className="rounded-lg border border-ink-700 bg-ink-850 p-2.5">
              <div className="text-[13px] font-semibold text-white">{z.area}</div>
              <div className="font-mono text-lg" style={{ color: zoneColor(z.avgResponseMin) }}>{z.avgResponseMin} min</div>
              <div className="text-[11px] text-slate-500">{z.trips} trips</div>
            </div>
          ))}
          {!zones.length && <div className="text-sm text-slate-500">Zones appear once trips complete.</div>}
        </div>
        <p className="mt-3 text-xs text-slate-500">Red zones are where a new standby post (or a permanent station) would help most. The pre-positioning planner already uses the same demand data hour by hour.</p>
      </div>
    </div>
  )
}
