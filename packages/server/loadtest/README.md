# Load test — BFF hot paths (backlog 5.3, NFR-5)

`hot-paths.js` is a [k6](https://k6.io) script that drives the four `/screen/*`
endpoints under load against a small, fixed entity set — mirroring how real
traffic concentrates on popular trains/stations/routes, which is what the SWR
cache is sized for.

## Run

```bash
# against a staging server (recommended)
BASE_URL=https://staging.railcast.example k6 run hot-paths.js

# or a local server with the infra up
docker compose -f ../../../infra/docker-compose.yml up -d
BASE_URL=http://127.0.0.1:3000 k6 run hot-paths.js
```

The server needs a **recorded or mock upstream** (RailKit) so the test doesn't
hammer the real API — point `RAILKIT_*` at a fixture server, or seed the cache
first. Never run this against production upstream.

## What the thresholds assert (NFR-5)

- `http_req_failed rate < 0.01` — under 1% errors under sustained 50-VU load.
- `screen_latency p95 < 250ms` — a **proxy for cache health**: warm SWR reads are
  fast, so a low cache-hit rate (lots of cold upstream fetches) blows this budget.

## Verifying the ≥90% cache-hit target directly

k6 alone can't read the server's internal cache hit/miss. Verify the exact rate
one of these ways during the run:

1. **Upstream fetch counting (preferred).** Put a counting proxy / mock in front
   of RailKit. Cache-hit rate ≈ `1 − (upstream_fetches / total_screen_requests)`.
   With single-flight + the small hot set, this should exceed 90% after warm-up.
2. **Server metrics.** If a metrics endpoint is added, scrape cache hit/miss
   counters over the run window.
3. **Redis.** `redis-cli info stats` — compare `keyspace_hits` vs
   `keyspace_misses` deltas across the run.

## Notes

- Auth: each VU mints one device token (contracts §7) and reuses it.
- PNRs used are static non-real digits — a load test never carries a live PNR
  (invariant 2).
- Tune `scenarios.steady.stages` and the hot-set sizes to model your target
  DAU / cache-hit assumptions (WS-E cost model).
