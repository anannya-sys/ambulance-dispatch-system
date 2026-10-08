// k6 load test: emergency calls arriving from random points across Mumbai while dashboards poll.
// Run with:  docker compose --profile loadtest run --rm k6
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SEVERITIES = ['CRITICAL', 'SERIOUS', 'MINOR'];
const TYPES = ['CARDIAC', 'TRAUMA', 'STROKE', 'RESPIRATORY', 'MATERNITY', 'GENERAL'];
// Neighbourhood centres; each call lands within ~500 m of one (random sea points would be rejected).
const PLACES = [
  [18.9220, 72.8347], [18.9400, 72.8350], [18.9640, 72.8150], [18.9750, 72.8320], [18.9940, 72.8250],
  [19.0190, 72.8430], [19.0430, 72.8620], [19.0596, 72.8350], [19.0760, 72.8777], [19.0990, 72.8440],
  [19.1197, 72.8468], [19.1360, 72.8300], [19.1550, 72.8500], [19.1860, 72.8480], [19.1650, 72.8490],
  [19.0330, 72.8960], [19.0660, 72.9000], [19.0860, 72.9080], [19.1180, 72.9050], [19.1700, 72.9550],
];

export const options = {
  scenarios: {
    callers: {            // new emergencies: ramp to 20 calls per second
      executor: 'ramping-arrival-rate',
      startRate: 2, timeUnit: '1s', preAllocatedVUs: 40, maxVUs: 100,
      stages: [{ target: 20, duration: '30s' }, { target: 20, duration: '60s' }, { target: 0, duration: '10s' }],
      exec: 'reportEmergency',
    },
    dashboards: {         // control-room screens reading state
      executor: 'constant-vus', vus: 20, duration: '100s', exec: 'readDashboard',
    },
  },
  thresholds: {
    'http_req_duration{name:create_incident}': ['p(95)<300'],
    'http_req_duration{name:snapshot}': ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

function pick(a) { return a[Math.floor(Math.random() * a.length)]; }

export function reportEmergency() {
  const [lat, lon] = pick(PLACES);
  const body = JSON.stringify({
    lat: lat + (Math.random() - 0.5) * 0.008,
    lon: lon + (Math.random() - 0.5) * 0.008,
    severity: pick(SEVERITIES),
    patientType: pick(TYPES),
    description: 'k6 load test',
  });
  const res = http.post(`${BASE}/api/incidents`, body, {
    headers: { 'Content-Type': 'application/json' }, tags: { name: 'create_incident' },
  });
  check(res, { 'incident accepted': (r) => r.status === 201 });
}

export function readDashboard() {
  const res = http.get(`${BASE}/api/sim/snapshot`, { tags: { name: 'snapshot' } });
  check(res, { 'snapshot ok': (r) => r.status === 200 });
  sleep(1);
}
