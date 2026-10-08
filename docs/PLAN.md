# GoldenHour: real-time ambulance dispatch for Mumbai (plan)

**Goal:** cut the time from an emergency call to the patient being *treated* at a hospital that can actually treat them.
The fleet is simulated (50 ambulances on real Mumbai roads); everything else is real engineering.

## What it does

**Core**
- Emergency intake: caller location, severity (CRITICAL / SERIOUS / MINOR) and patient type (cardiac, stroke, trauma, respiratory, maternity, general).
- Nearest free ambulance by *road* ETA, with no double booking (Redis lock + database compare-and-set).
- Hospital matching by free ICU / trauma / cardiac / general beds; bed reserved when chosen.
- Trip lifecycle: ASSIGNED, EN_ROUTE, AT_SCENE, TRANSPORTING, AT_HOSPITAL, CLOSED.
- Live map: ambulances move along real roads, updates pushed over WebSockets.

**Standout**
- Severity priority queue with aging (a cardiac case jumps ahead, a minor case is never starved forever).
- Auto-reassignment if a crew doesn't acknowledge within 30 seconds or the ambulance breaks down.
- Live ETA from real road distance and time-of-day traffic.
- Hospital pre-alert: the ER sees "cardiac, critical, arriving in 7 min" before the patient arrives.
- Surge mode: one mass-casualty incident dispatches several ambulances and spreads patients across hospitals.

**USPs**
1. Predictive pre-positioning: a demand model learns where and when emergencies happen (per ~1 km cell, per hour of the week) and moves idle ambulances to standby posts *before* calls come in.
2. Time-to-treatment routing: picks the hospital by when treatment actually starts (travel + ER queue + specialist on duty + bed of the right type), not by distance.
3. Automatic green corridor: for critical trips, the signals on the route get pre-emption windows timed to the ambulance's ETA.

**Analytics**
- Response time by zone with a slow-zone heatmap, fleet utilisation, standby suggestions.

**Proof (headline number)**
- An offline discrete-event simulation replays the same 30 days of synthetic calls through a *baseline*
  (nearest ambulance, FIFO queue, nearest hospital, ambulances return to base) and through *GoldenHour*,
  with an ablation table showing what each USP contributes. Multiple seeds, so the result is not a fluke.

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| API | Java 17, Spring Boot 3.5, Spring Data JPA, Bean Validation, springdoc OpenAPI | Standard, widely used |
| Database | PostgreSQL 16 + PostGIS, Flyway | KNN nearest-ambulance (`<->`) on a GiST index |
| Locks / live cache | Redis 7 | `SET NX PX` lock with token + Lua release, so one ambulance can't be booked twice even with many dispatcher instances |
| Event streaming | Apache Kafka (KRaft, no ZooKeeper) | GPS telemetry is high-volume; Kafka decouples ingestion from tracking, analytics and pre-alerts |
| Real time | Spring WebSocket + STOMP | Push fleet positions, trips, alerts to the dashboard |
| Routing | Own A* over a real Mumbai road graph (OpenStreetMap data via Overture Maps) | Works offline, no paid API; ETA uses time-of-day traffic |
| Frontend | React 18 + TypeScript + Vite, Leaflet, Recharts, Tailwind | Professional live map dashboard |
| Quality | JUnit 5, Mockito, Testcontainers (PostGIS, Redis, Kafka), concurrency test, k6 load test | Proves no double booking under load |
| Ops | Docker Compose (one command), Actuator + Micrometer/Prometheus, optional Grafana, GitHub Actions CI | |

## Folder structure

```
ambulance-dispatch/
├── docker-compose.yml          one command: postgis, redis, kafka, app
├── Dockerfile                  builds frontend + backend into one image
├── README.md
├── docs/                       PLAN.md, simulation results, screenshots
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/goldenhour/dispatch/
│       ├── routing/            road graph, A*, traffic model, ETA
│       ├── fleet/              ambulances, Redis lock, location ingest (Kafka)
│       ├── incident/           intake, severity, priority queue with aging
│       ├── dispatch/           dispatch engine, reassignment watchdog, surge
│       ├── hospital/           beds, time-to-treatment selector, pre-alerts
│       ├── corridor/           green corridor planner
│       ├── forecast/           demand model, pre-positioning planner
│       ├── trip/               trip lifecycle + event log
│       ├── live/               simulated clock, fleet mover, call generator
│       ├── analytics/          KPIs, zone heatmap, utilisation
│       ├── evaluation/         offline baseline-vs-smart simulation
│       ├── realtime/  messaging/  config/  api/
│   └── src/main/resources/     application.yml, Flyway SQL, mumbai-roads.bin.gz
├── frontend/                   React + TS dashboard (Live ops, Hospitals, Analytics, Impact)
├── loadtest/                   k6 script
├── monitoring/                 Prometheus + Grafana (optional profile)
└── tools/                      script that rebuilt the road graph from OpenStreetMap
```

## Build order (tests run after each step)
1. Road graph + A* + traffic model (unit tests).
2. Domain + Flyway/PostGIS schema + nearest-ambulance query (Testcontainers).
3. Dispatch engine, Redis lock, priority queue, reassignment (unit + concurrency tests).
4. Hospital selection, pre-alerts, green corridor, surge.
5. Demand model + pre-positioning + offline simulation (headline numbers).
6. Live simulator, Kafka telemetry, WebSockets.
7. React dashboard.
8. Load test and README.
