import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import type { Alert, DispatchEvent, LiveSnapshot, Plan } from './types'

/**
 * One STOMP-over-WebSocket connection for the whole dashboard.
 * /topic/live arrives every second; events, alerts and positioning arrive when they happen.
 */
export function useLive() {
  const [snapshot, setSnapshot] = useState<LiveSnapshot | null>(null)
  const [events, setEvents] = useState<DispatchEvent[]>([])
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [plan, setPlan] = useState<Plan | null>(null)
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${proto}://${window.location.host}/ws`,
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    })
    client.onConnect = () => {
      setConnected(true)
      client.subscribe('/topic/live', m => setSnapshot(JSON.parse(m.body)))
      client.subscribe('/topic/events', m => setEvents(prev => [JSON.parse(m.body), ...prev].slice(0, 80)))
      client.subscribe('/topic/alerts', m => setAlerts(prev => [JSON.parse(m.body), ...prev].slice(0, 40)))
      client.subscribe('/topic/positioning', m => setPlan(JSON.parse(m.body)))
    }
    client.onWebSocketClose = () => setConnected(false)
    client.activate()
    clientRef.current = client
    return () => { client.deactivate() }
  }, [])

  return { snapshot, events, alerts, plan, setPlan, setAlerts, connected }
}
