# Deploying the BFF

The server ships as a single container (root `Dockerfile`): it runs its own
migrations on boot, listens on `$PORT`, and exposes `GET /health`. Any
container host works; the steps below are for Railway.

## Railway

One project, three services: the app + managed Postgres + managed Redis.

1. **Databases** — in your Railway project: *Create → Database → PostgreSQL*,
   then *Create → Database → Redis*.
2. **App service** — *Create → GitHub repo* → select this repo. Railway finds
   the root `Dockerfile` automatically; no build/start command needed.
3. **Variables** — on the app service, add:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` (private network — no SSL needed) |
   | `REDIS_URL` | `${{Redis.REDIS_URL}}` |
   | `RAILKIT_API_KEY` | your RailKit key (server-only, invariant 1) |
   | `AUTH_TOKEN_SECRET` | `openssl rand -hex 32` |
   | `PNR_ENCRYPTION_KEY` | `openssl rand -hex 32` (must be 64 hex chars) |
   | `PUBLIC_BASE_URL` | `https://api.<your-domain>` |
   | `FIREBASE_SERVICE_ACCOUNT` | optional — service-account JSON; absent → push is a no-op, server still boots |

   `AUTH_TOKEN_SECRET` invalidates all device tokens if rotated;
   `PNR_ENCRYPTION_KEY` drops encrypted PNRs and cached PNR keys if rotated.
   Set both once and treat them like the API key.
4. **Health check** — service *Settings → Deploy → Healthcheck Path* = `/health`.
5. **Domain** — *Settings → Networking*: generate a `*.up.railway.app` domain to
   test, then add `api.<your-domain>` as a custom domain and create the CNAME
   it shows you at your DNS provider.
6. **Verify** — `curl https://<domain>/health` returns `"ok":true` and open
   `https://<domain>/privacy` in a browser.

Redeploys happen automatically on push to `main` (configurable per service).

## Local development

`docker-compose.yml` in this directory runs Postgres 16 + Redis 7 for
`pnpm -F server dev` — see `packages/server/CLAUDE.md` for the env vars.
