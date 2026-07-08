# packages/server — BFF + Watcher + web layer

Fastify + TypeScript (strict). Redis (cache, BullMQ), Postgres (users/watches/PNR). Node 22, pnpm.

## Commands
- `pnpm -F server dev` · `pnpm -F server test` (vitest) · `pnpm -F server typecheck`
- Local deps: `docker compose -f ../../infra/docker-compose.yml up -d`

## Env (.env is gitignored; dev script loads it)
- `RAILKIT_API_KEY` — upstream key (invariant 1)
- `AUTH_TOKEN_SECRET` — HMAC secret for device tokens (rotating it invalidates all tokens)
- `PNR_ENCRYPTION_KEY` — 64 hex chars; AES-256-GCM for PNR data at rest + HMAC cache keys (rotating it drops cached PNRs)
- `DATABASE_URL` (default postgres://railcast:railcast_dev@127.0.0.1:5432/railcast)
- `RAILKIT_SDK_SIGNING_SECRET` — optional; overrides the built-in SDK signing secret if upstream rotates it
- `FIREBASE_SERVICE_ACCOUNT` or `FIREBASE_SERVICE_ACCOUNT_PATH` — optional (2.3); absent → NoopSender, server still boots
- `REDIS_URL` (default redis://127.0.0.1:6379) · `PORT` (default 3000)

## Module map
- src/railkit/ — the ONLY place that talks upstream or knows DD-MM-YYYY. Typed wrapper per endpoint; validates before calling; fixtures in __fixtures__/ mirror docs/fixtures/.
- src/cache/ — Redis TTL cache + single-flight + SWR. TTLs from docs/api-contracts.md §10 (constant table CACHE_TTLS, one place).
- src/screens/ — composed /screen/* endpoints; import types from packages/shared only.
- src/watcher/ — BullMQ jobs (ONE repeatable job per entity, deduped across users), diff engine (normalize→hash→typed events), FCM fan-out, quiet hours.
- src/web/ — /t/:token server-rendered pages.
- src/privacy/ — PNR encrypt/mask/purge utilities. Nothing else touches raw PNRs.

## Rules
- Every upstream call goes through cache.getOrFetch (single-flight). No direct railkit calls from screens/watcher.
- Serve stale + meta.stale=true when upstream fails and cache exists; UPSTREAM_DOWN only when there is nothing to serve.
- Error taxonomy: only the ErrorCode union from contracts §0. trainHistory 404 → NOT_YET_AVAILABLE, never NOT_FOUND.
- Log lines must never contain full PNRs or the API key (there is a redaction test — keep it passing).
- New watch event types: extend the diff-engine test fixtures FIRST, then implement.
- Load-bearing tests: cache concurrency (single-flight), run-date probe, each diff transition, PNR redaction.
