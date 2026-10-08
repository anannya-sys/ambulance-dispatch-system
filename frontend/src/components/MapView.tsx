import { useEffect, useMemo, useState } from 'react'
import L from 'leaflet'
import { Circle, CircleMarker, MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import type { ActiveTrip, FleetUnit, Hospital, LiveSnapshot, Plan } from '../lib/types'
import { PATIENT_LABEL, PHASE_LABEL, STATUS_COLOR, STATUS_LABEL, mmss } from '../lib/format'
import { api } from '../lib/api'

export interface Layers { forecast: boolean; posts: boolean; hospitals: boolean; routes: boolean; corridor: boolean }

interface Props {
  snapshot: LiveSnapshot | null
  plan: Plan | null
  hospitals: Hospital[]
  layers: Layers
  baseMap: 'streets' | 'roads'
  onTilesFailed: () => void
  picking: boolean
  pick: [number, number] | null
  onPick: (p: [number, number]) => void
  selectedTrip: number | null
  onSelectTrip: (id: number | null) => void
  onSelectAmbulance: (u: FleetUnit) => void
}

const ambIconCache = new Map<string, L.DivIcon>()
function ambulanceIcon(u: FleetUnit, selected: boolean): L.DivIcon {
  const heading = Math.round(u.heading / 15) * 15
  const key = `${u.status}-${heading}-${selected}`
  let icon = ambIconCache.get(key)
  if (!icon) {
    const c = STATUS_COLOR[u.status] ?? '#94a3b8'
    const moving = !['AVAILABLE', 'AT_SCENE', 'AT_HOSPITAL', 'OUT_OF_SERVICE'].includes(u.status)
    const ring = selected ? `box-shadow:0 0 0 3px #fff;` : ''
    const arrow = moving
      ? `<svg width="22" height="22" viewBox="0 0 22 22" style="transform:rotate(${heading}deg)"><path d="M11 2 L17 18 L11 14.5 L5 18 Z" fill="${c}" stroke="#0b1220" stroke-width="1.5"/></svg>`
      : `<div style="width:14px;height:14px;border-radius:4px;background:${c};border:2px solid #0b1220;${ring}"></div>`
    icon = L.divIcon({ className: 'amb-icon', html: `<div style="display:grid;place-items:center;width:22px;height:22px;${moving ? ring + 'border-radius:999px' : ''}">${arrow}</div>`, iconSize: [22, 22], iconAnchor: [11, 11] })
    ambIconCache.set(key, icon)
  }
  return icon
}

const hospitalIconCache = new Map<string, L.DivIcon>()
function hospitalIcon(h: Hospital): L.DivIcon {
  const icu = h.beds.ICU?.free ?? 0
  const general = h.beds.GENERAL?.free ?? 0
  const tone = icu === 0 && general === 0 ? '#ef4444' : icu <= 1 ? '#f59e0b' : '#22c55e'
  const key = tone
  let icon = hospitalIconCache.get(key)
  if (!icon) {
    icon = L.divIcon({
      className: '',
      html: `<div style="width:20px;height:20px;border-radius:6px;background:#f8fafc;display:grid;place-items:center;border:2px solid ${tone};box-shadow:0 2px 8px rgba(0,0,0,.5)"><span style="color:#dc2626;font-weight:900;font-size:13px;line-height:1">+</span></div>`,
      iconSize: [20, 20], iconAnchor: [10, 10],
    })
    hospitalIconCache.set(key, icon)
  }
  return icon
}

function incidentIcon(color: string): L.DivIcon {
  return L.divIcon({
    className: '',
    html: `<div style="position:relative;width:16px;height:16px"><span class="pulse-ring" style="background:${color}"></span><span style="position:absolute;inset:3px;border-radius:999px;background:${color};border:2px solid #fff"></span></div>`,
    iconSize: [16, 16], iconAnchor: [8, 8],
  })
}
const INCIDENT_ICONS = { CRITICAL: incidentIcon('#ef4444'), SERIOUS: incidentIcon('#f59e0b'), MINOR: incidentIcon('#38bdf8') }
const PICK_ICON = L.divIcon({ className: '', html: '<div style="width:22px;height:22px;border-radius:999px;border:3px solid #fff;background:#ef4444;box-shadow:0 0 0 6px rgba(239,68,68,.35)"></div>', iconSize: [22, 22], iconAnchor: [11, 11] })
const POST_ICON = L.divIcon({ className: '', html: '<div style="width:10px;height:10px;transform:rotate(45deg);background:#22d3ee;border:1.5px solid #083344;opacity:.9"></div>', iconSize: [10, 10], iconAnchor: [5, 5] })

/** Draws all main roads as one canvas layer: the offline base map. */
function OfflineRoads({ visible }: { visible: boolean }) {
  const map = useMap()
  const [roads, setRoads] = useState<number[][][] | null>(null)
  useEffect(() => { if (visible && !roads) api.roads().then(setRoads).catch(() => {}) }, [visible, roads])
  useEffect(() => {
    if (!visible || !roads) return
    const layer = L.polyline(roads as L.LatLngExpression[][], { color: '#334155', weight: 1.2, opacity: 0.9, interactive: false, renderer: L.canvas() })
    layer.addTo(map)
    return () => { layer.remove() }
  }, [visible, roads, map])
  return null
}

function ClickPicker({ active, onPick }: { active: boolean; onPick: (p: [number, number]) => void }) {
  const map = useMapEvents({ click: e => { if (active) onPick([e.latlng.lat, e.latlng.lng]) } })
  useEffect(() => { map.getContainer().style.cursor = active ? 'crosshair' : '' }, [active, map])
  return null
}

function routeStyle(t: ActiveTrip, selected: boolean): L.PathOptions {
  const toHospital = t.phase === 'PICKED_UP'
  const color = t.corridor ? '#10b981' : toHospital ? '#ef4444' : '#f59e0b'
  return { color, weight: selected ? 5 : 3.2, opacity: selected ? 1 : 0.8, dashArray: toHospital ? undefined : '6 6' }
}

export default function MapView(p: Props) {
  const { snapshot, plan, hospitals, layers } = p
  const maxHeat = useMemo(() => Math.max(0.01, ...(plan?.heat.map(h => h.expectedCalls) ?? [0])), [plan])

  return (
    <MapContainer center={[19.06, 72.875]} zoom={12} minZoom={11} maxZoom={17} zoomControl={false} preferCanvas className="h-full w-full">
      {p.baseMap === 'streets' && (
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a> · roads: OSM via Overture Maps'
          eventHandlers={{ tileerror: p.onTilesFailed }}
        />
      )}
      <OfflineRoads visible={p.baseMap === 'roads'} />
      <ClickPicker active={p.picking} onPick={p.onPick} />

      {layers.forecast && plan?.heat.map((h, i) => (
        <Circle key={`h${i}`} center={[h.lat, h.lon]} radius={520} pathOptions={{ stroke: false, fillColor: '#f97316', fillOpacity: 0.07 + 0.38 * (h.expectedCalls / maxHeat) }}>
          <Tooltip className="gh-tip">Forecast next hour: {h.expectedCalls.toFixed(2)} calls</Tooltip>
        </Circle>
      ))}

      {layers.posts && plan?.posts.map(s => (
        <Marker key={`p${s.id}`} position={[s.lat, s.lon]} icon={POST_ICON}>
          <Tooltip className="gh-tip">Standby post: {s.name}</Tooltip>
        </Marker>
      ))}

      {layers.routes && snapshot?.trips.filter(t => t.route.length > 1).map(t => (
        <Polyline key={`r${t.tripId}`} positions={t.route} pathOptions={routeStyle(t, p.selectedTrip === t.tripId)}
          eventHandlers={{ click: () => p.onSelectTrip(t.tripId) }} />
      ))}

      {layers.corridor && snapshot?.trips.flatMap(t => t.signals.map((s, i) => (
        <CircleMarker key={`s${t.tripId}-${i}`} center={[s.lat, s.lon]} radius={s.state === 'GREEN' ? 6 : 4}
          pathOptions={{ color: '#022c22', weight: 1, fillOpacity: 1, fillColor: s.state === 'GREEN' ? '#34d399' : s.state === 'PENDING' ? '#fbbf24' : '#475569' }}>
          <Tooltip className="gh-tip">Signal {s.state === 'GREEN' ? 'held green for ' + t.callSign : s.state === 'PENDING' ? 'will turn green before ' + t.callSign + ' arrives' : 'passed'}</Tooltip>
        </CircleMarker>
      )))}

      {layers.hospitals && hospitals.map(h => (
        <Marker key={`hp${h.id}`} position={[h.lat, h.lon]} icon={hospitalIcon(h)}>
          <Tooltip className="gh-tip">
            <div className="font-semibold">{h.name}</div>
            <div className="text-[11px] text-slate-400">{h.capabilities.map(c => c.replace('_', ' ').toLowerCase()).join(' · ') || 'general'}</div>
            <div className="mt-1 grid grid-cols-4 gap-2 text-[11px]">
              {['ICU', 'CARDIAC', 'TRAUMA', 'GENERAL'].map(b => <div key={b}><div className="text-slate-500">{b.slice(0, 4)}</div><div className="font-mono">{h.beds[b]?.free}/{h.beds[b]?.total}</div></div>)}
            </div>
            <div className="mt-1 text-[11px] text-slate-400">ER queue {h.erQueue} · simulated data</div>
          </Tooltip>
        </Marker>
      ))}

      {snapshot?.queue.map(q => (
        <Marker key={`q${q.id}`} position={[q.lat, q.lon]} icon={INCIDENT_ICONS[q.severity]}>
          <Tooltip className="gh-tip">Waiting: {q.severity.toLowerCase()} {PATIENT_LABEL[q.patientType].toLowerCase()} in {q.area} · {mmss(q.waitingSeconds)}</Tooltip>
        </Marker>
      ))}
      {snapshot?.trips.filter(t => t.phase === 'ASSIGNED' || t.phase === 'EN_ROUTE').map(t => (
        <Marker key={`i${t.tripId}`} position={[t.incidentLat, t.incidentLon]} icon={INCIDENT_ICONS[t.severity]}
          eventHandlers={{ click: () => p.onSelectTrip(t.tripId) }}>
          <Tooltip className="gh-tip">{t.severity} {PATIENT_LABEL[t.patientType]} · {t.area}<br />{t.callSign}: {PHASE_LABEL[t.phase]} · ETA {mmss(t.etaSeconds)}</Tooltip>
        </Marker>
      ))}

      {snapshot?.fleet.map(u => (
        <Marker key={`a${u.id}`} position={[u.lat, u.lon]} icon={ambulanceIcon(u, p.selectedTrip != null && u.tripId === p.selectedTrip)}
          zIndexOffset={u.tripId ? 500 : 0}
          eventHandlers={{ click: () => { p.onSelectAmbulance(u); if (u.tripId) p.onSelectTrip(u.tripId) } }}>
          <Tooltip className="gh-tip">
            <span className="font-semibold">{u.callSign}</span> <span className="text-slate-400">{u.kind}</span><br />
            <span style={{ color: STATUS_COLOR[u.status] }}>{STATUS_LABEL[u.status]}</span>
            {u.etaSeconds != null && <> · ETA {mmss(u.etaSeconds)}</>}
          </Tooltip>
        </Marker>
      ))}

      {p.pick && <Marker position={p.pick} icon={PICK_ICON} />}
    </MapContainer>
  )
}
