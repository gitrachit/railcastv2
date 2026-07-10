# DPDP compliance checklist (India Digital Personal Data Protection Act, 2023)

Living checklist for RailCast's handling of personal data. Backlog 5.6 / WS-D.
Status legend: ✅ done in code · 🔶 partial / needs ops · ⬜ pending (human/legal).

## Data minimisation & purpose limitation
- ✅ **No forced login for core features** — status/PNR/board/plan work without an account (FR-10.5, `isPublic` + client onboarding). Account only for saving/alerts/sync.
- ✅ **Minimal collection** — no contacts, no advertising IDs, no location stored server-side. Saved trains + prefs live on-device (DataStore).
- ✅ **Purpose-bound PNR use** — a PNR is used only to fetch that journey / register that chart watch; never for profiling.

## PNR handling (FR-4.3 — the sensitive-data path)
- ✅ **Masked everywhere** — UI, responses, and logs carry `••••NNNN` (`src/privacy/mask.ts`; client `maskPnr`; log-redaction test).
- ✅ **Encrypted at rest** — saved-watch PNRs are AES-256-GCM encrypted (`src/privacy/crypto.ts`, `PNR_ENCRYPTION_KEY`).
- ✅ **Never logged in full** — server redaction test is load-bearing (server CLAUDE.md).
- ✅ **Client never persists the raw PNR** — request path / watch body only; SWR cache keyed by SHA-256.
- ✅ **Auto-purge post-journey** — `WatchRepo.purgeExpired()` hard-deletes expired watches + their encrypted blobs; verified in `watcher.int.test.ts` ("purges past the grace window (FR-4.3)").

## Consent & user control
- ✅ **Analytics opt-out honoured** — `ConsentGatedAnalytics` drops all events when off; toggle in Settings → Privacy (FR-11.3).
- ✅ **Anonymised analytics** — events are numeric-only by type (`Map<String, Long>`); no PNR/identifier can be carried.
- ✅ **Notification consent** — per-type opt-in + quiet hours, default minimal (FR-7.4).
- ⬜ **Consent records / withdrawal log** — if server-side analytics or account sync is added, record consent state + timestamp and honour withdrawal.

## Transparency (notice)
- ✅ **Public privacy policy** — `GET /privacy` (this repo) covers PNR handling, analytics, retention, non-affiliation.
- ✅ **Honesty labels** — interpolated position + predictions labelled; freshness stamps universal; "not affiliated with Indian Railways" disclaimer (FR-11.1).
- 🔶 **In-app link to policy** — PNR screen links a plain-language note; wire a Settings link to `/privacy` at launch.

## Security (reasonable safeguards)
- ✅ **TLS everywhere**; upstream API key server-side only (invariant 1, NFR-4).
- ✅ **App↔BFF auth tokens** (HMAC device tokens); read-only public surface (`/t/:token`, `/privacy`).
- ⬜ **Key management / rotation runbook** — document rotation for `PNR_ENCRYPTION_KEY`, `AUTH_TOKEN_SECRET` (rotating drops cached PNRs / invalidates tokens — already noted in server CLAUDE.md).

## Data-principal rights (to implement if accounts/sync ship)
- ⬜ **Access / correction / erasure** — with no account, on-device data is user-controlled (uninstall = erased) and server PNR data self-purges. Add explicit request handling only if durable accounts land.
- ✅ **Report-wrong-data path (FR-11.2)** design — feedback loop planned; not yet built.

## Governance (human/legal — WS-D)
- ⬜ **DPDP legal review** of the policy copy + retention windows.
- ⬜ **Grievance / contact channel** published in the policy and Play listing.
- ⬜ **Data-processing records** for any third-party processors (FCM, hosting).
- ⬜ **Breach-notification procedure**.

---
Owner: founder/legal (WS-D). Engineering items marked ✅ are enforced in code + tests;
🔶/⬜ items are launch-blocking to the extent DPDP applies at the chosen launch scale.
