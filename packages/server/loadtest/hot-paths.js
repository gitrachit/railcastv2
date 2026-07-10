// k6 load test for the BFF hot paths (backlog 5.3, NFR-5). Exercises the four
// /screen/* endpoints against a small fixed entity set so the SWR cache serves
// the vast majority of requests. Run against a staging server with a recorded/
// mock upstream — see loadtest/README.md.
//
//   BASE_URL=https://staging.example k6 run hot-paths.js
//
// Thresholds encode NFR-5: near-zero failures and a p95 latency budget that only
// a high cache-hit rate can meet (cold upstream calls are far slower). The exact
// cache-hit % is verified out-of-band by counting upstream fetches (README).
import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://127.0.0.1:3000";

// A deliberately small hot set — real traffic concentrates on popular trains/
// stations/routes, which is exactly what the cache is sized for.
const TRAINS = ["12780", "22188", "12951"];
const STATIONS = ["NDLS", "BPL", "CNB"];
const PLANS = [
  { from: "JBP", to: "NU", date: "2026-07-10", quota: "GN" },
  { from: "NDLS", to: "BPL", date: "2026-07-10", quota: "GN" },
];
// Static, non-real digits — never a live person's PNR in a load test.
const PNRS = ["1000000001", "1000000002"];

const screenLatency = new Trend("screen_latency", true);

export const options = {
  scenarios: {
    steady: { executor: "ramping-vus", startVUs: 5, stages: [
      { duration: "30s", target: 50 },
      { duration: "2m", target: 50 },
      { duration: "30s", target: 0 },
    ] },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"], // <1% errors (NFR-5)
    // p95 budget: warm-cache reads are fast; a low cache-hit rate would blow this.
    "screen_latency": ["p(95)<250"],
  },
};

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

// One device token per VU, minted once (contracts §7).
export function setup() {
  return {}; // token is minted per-VU in default() to spread across IPs/rate-limits
}

let token = null;
function authHeaders() {
  if (!token) {
    const res = http.post(`${BASE}/auth/device`, JSON.stringify({ platform: "android", appVersion: "loadtest" }), {
      headers: { "content-type": "application/json" },
    });
    token = res.json("data.deviceToken");
  }
  return { headers: { authorization: `Bearer ${token}` } };
}

function hit(path) {
  const res = http.get(`${BASE}${path}`, authHeaders());
  screenLatency.add(res.timings.duration);
  check(res, {
    "status 200": (r) => r.status === 200,
    "ok envelope": (r) => r.json("ok") === true,
    "has meta": (r) => r.json("meta.ttlSeconds") !== undefined,
  });
}

export default function () {
  // Weighted toward tracking (the dominant screen), then station/pnr/plan.
  const roll = Math.random();
  if (roll < 0.45) hit(`/screen/train/${pick(TRAINS)}?run=auto`);
  else if (roll < 0.7) hit(`/screen/station/${pick(STATIONS)}?hrs=4`);
  else if (roll < 0.85) hit(`/screen/pnr/${pick(PNRS)}`);
  else {
    const p = pick(PLANS);
    hit(`/screen/plan?from=${p.from}&to=${p.to}&date=${p.date}&quota=${p.quota}`);
  }
  sleep(Math.random() * 0.5 + 0.1);
}
