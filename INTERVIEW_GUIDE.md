# Interview guide: GoldenHour

How to present this project, and answers to the questions interviewers usually ask.

## 30-second pitch

"In Indian cities a critical patient loses their golden hour in three places: the ambulance starts far
away, it waits at signals, and it goes to the nearest hospital, which often can't treat them. I built
GoldenHour, a real-time dispatch system on the real Mumbai road network. It pre-positions idle ambulances
where the next hour's calls are predicted, routes patients to the hospital where treatment starts soonest
rather than the nearest one, and plans a green corridor for critical trips. In a 14-day simulation over 5
seeds it cut critical response time by 15% and raised golden-hour treatment from 76% to 99%. It's Spring
Boot with PostGIS, Redis, Kafka and WebSockets, with a React live map."

## 2-minute walkthrough (with the app open)

1. **Live operations tab.** "50 simulated ambulances driving on real roads. Each second the backend pushes
   a snapshot over WebSocket." Report a critical cardiac call in Bandra.
2. "Dispatch: PostGIS finds the 8 nearest by straight line, one Dijkstra gives road times, then a Redis
   lock plus an SQL compare-and-set claims the ambulance so it can't be double-booked."
3. Point to the red route and signals turning green: "green corridor, timed to the ETA at each junction."
4. When the patient is picked up, open the trip: "it chose this hospital because treatment starts in 14
   minutes; the nearer one has no cath lab. The ER got a pre-alert."
5. Click an ambulance → **Simulate breakdown**: "the call goes back to the priority queue and is reassigned."
6. **Impact tab**: "same calls through a good baseline and through GoldenHour. The ablation table shows the
   hospital choice is the biggest win."

## Numbers to remember

| | |
|---|---|
| Road graph | 64,075 junctions, 80,706 segments, real OSM data; loads in ~2 s |
| Fleet / hospitals / posts | 50 simulated ambulances, 32 real hospital locations, 99 standby posts |
| Critical response | 8.2 → 7.0 min (-15%); surge week 14.7 → 11.3 min (-23.5%) |
| Golden-hour treatment | 75.6% → 98.6% (+22.9 pts); surge 59.5% → 89.2% |
| Turn-aways / transfers | 768 → 0 over 14 days |
| Load test | 3,630 requests in 100 s, calls up to 20/s, p95 14 ms, 0 errors |
| Tests | 24 unit + 7 Testcontainers integration tests |

## Questions and answers

**How do you make sure one ambulance is never sent to two patients?**
Two layers. First a Redis lock per ambulance (`SET key token NX PX 5000`), released by a Lua script that
deletes the key only if the token matches, so one dispatcher can't release another's lock. Second, the
real guarantee is in the database: `UPDATE ambulance SET status='DISPATCHED' WHERE id=? AND status IN
('AVAILABLE','REPOSITIONING')`. If it updates 0 rows, someone else won. The incident is claimed the same
way in the same transaction; if the incident was already taken, the transaction rolls back the ambulance
claim too. The integration test fires 20 calls at the same street at the same moment and asserts every
assigned ambulance is unique.

**Why both? Isn't the database enough?**
The database alone is correct. The lock avoids wasted work: without it, 20 threads all compute routes and
all try the same nearest ambulance, and 19 fail. With the lock, they skip a busy candidate immediately and
try the next. If Redis is down or a lock expires mid-transaction, correctness still holds.

**What happens if every nearby ambulance is grabbed by parallel calls?**
The dispatcher notes the contention and searches again (up to 4 rounds); the new search sees the claimed
ambulances as busy and returns the next ones. I found this case with the concurrency test: at first only 8
of 20 simultaneous calls were assigned because all 20 threads fetched the same 8 candidates.

**How do you find the nearest ambulance?**
Straight-line distance is a filter, road time decides. `ORDER BY location <-> point LIMIT 8` uses a GiST
index on a generated `geography` column; the index is partial (`WHERE status IN ('AVAILABLE',
'REPOSITIONING')`) so busy ambulances aren't even in it. Then one Dijkstra from the patient's road node
stops as soon as all 8 ambulance nodes are settled. Roads are symmetric in this model, so the time from the
patient to the ambulance equals the time from the ambulance to the patient.

**Why your own router?**
No paid API, works offline, and I need to change the cost of passing a signal for the green corridor.
The graph is stored in compressed sparse row arrays (int/float arrays, not objects), so it's small and
cache-friendly. A* uses straight-line distance divided by the fastest road speed as an admissible heuristic.
For hospitals and standby posts I precompute a full shortest-path tree per traffic band, so ranking 32
hospitals is 32 array lookups instead of 32 searches.

**Why Kafka? Isn't it overkill for 50 ambulances?**
For 50 it would be fine without it, but GPS telemetry is the part that grows: 1,000 ambulances pinging
every 3 seconds is 333 writes a second, bursty, and several consumers need it (tracking, dashboard,
metrics, later analytics). Kafka decouples ingestion from processing, absorbs bursts, and lets each
consumer group read at its own pace. Messages are keyed by ambulance id so one ambulance's pings stay in
order within a partition. The device endpoint returns 202 Accepted because processing is asynchronous.

**What if the database commit succeeds but the Kafka publish fails?**
Today the event would be lost (the trip itself is safe in PostgreSQL). The fix is the transactional outbox
pattern: write the event to an outbox table in the same transaction, and have a relay publish it to Kafka.
I documented this as the next step.

**How does the demand forecast work?**
The city is split into ~1 km cells. For each cell and each of the 168 hours of the week, the expected call
rate is the observed count shrunk toward that cell's hour-of-day average:
`(count_this_hour_of_week + K × pooled_rate) / (weeks + K)` with K = 2. That handles sparse data: a cell
with one call last Tuesday at 3 am doesn't get a high Tuesday-3am forecast. Live calls are added online.

**How do you pick standby posts?**
Greedy p-median. Each round adds the post that most reduces total (expected calls × road time from the
nearest chosen post). Greedy p-median is a classic approximation with a (1 - 1/e) guarantee for the
coverage form. Then idle ambulances are matched to chosen posts nearest-pair-first, and nobody is moved
more than 20 minutes. It runs every 10 simulated minutes.

**What does "time to treatment" include?**
Travel time + ER wait (queue ÷ bays × 12 min, critical patients skip 75% of the queue) + 30 minutes if
the needed specialist is off duty + 5 minutes handover. Hospitals without the needed facility or a free
bed of the right type are infeasible. If everything is full, it falls back to the nearest capable
hospital, since it's better to arrive and be stabilised than to drive around.

**How does the bed reservation avoid two ambulances taking the last bed?**
The hospital row has a JPA `@Version` column. Reserving decrements the free count; if another transaction
changed the row first, Hibernate throws an optimistic-lock exception, I reload and re-rank, up to 3 tries.
Optimistic locking fits because conflicts are rare and the transaction is short.

**Isn't the simulation result biased toward your own system?**
I tried to make the baseline strong: nearest ambulance by real road time (not straight line), stations
placed by the same p-median on average demand, and nearest hospital *with the right facility*. Both use the
same calls, same hospitals, same random seeds, and the demand model is trained on 8 earlier weeks that
aren't replayed. The ablation table shows each feature alone, and every one of 5 seeds improves. The
honest limits: calls, beds and traffic are synthetic, so the absolute numbers aren't a claim about Mumbai,
the relative comparison is the point.

**Why does the priority queue show no gain at normal load?**
Because at 500 calls a day there's almost never a queue; a free ambulance is always available. In the
surge week (750 a day) it cuts critical response from 14.7 to 13.5 minutes on its own. Aging (+2 points per
minute waited) means a minor call waiting 50 minutes eventually outranks a fresh critical call, so nobody
starves.

**How would you scale this to all of India?**
Partition by city (each city's road graph fits in memory, ~2 s to load). Run several stateless dispatch
instances per city behind a load balancer; the Redis lock and SQL compare-and-set already make that safe.
Partition Kafka topics by ambulance id. Move the live simulation state (currently in memory) into Redis.
Read-heavy dashboard traffic can come from the WebSocket snapshot instead of hitting the database.

**What would you do differently?**
Add one-way streets and turn restrictions, the outbox pattern, and real traffic data. I'd also learn
the ER wait and specialist delays from hospital data instead of fixed constants.

**Hardest bug?**
Early simulation runs showed the baseline turning away 30-55% of patients, which made GoldenHour look
amazing but was unrealistic: the baseline was sending cardiac patients to hospitals without a cath lab.
I made the baseline capability-aware and recalibrated bed counts and lengths of stay so the baseline
refuses ~10-15% (closer to real reports). The gain shrank but became believable.

## Resume bullets

- Built **GoldenHour**, a real-time ambulance dispatch system (Java 17, Spring Boot, PostgreSQL/PostGIS,
  Redis, Kafka, WebSockets, React/TypeScript) on the real Mumbai road graph (64K junctions); predictive
  pre-positioning, time-to-treatment hospital routing and green corridors cut simulated critical response
  time by **15%** (8.2 → 7.0 min) and raised golden-hour treatment from **76% to 99%** over 5 seeds.
- Engineered conflict-free dispatch with Redis distributed locks and SQL compare-and-set, PostGIS KNN
  search and a custom A*/Dijkstra router, verified by Testcontainers concurrency tests and a k6 load test
  (**20 calls/s, p95 14 ms, 0 errors**); shipped with Docker Compose, Prometheus/Grafana and GitHub Actions CI.
