# Upstream fixtures
Recorded real responses from the upstream API, used by packages/server tests (railkit client + screen composition + diff engine).

- getTrainInfo-22188.json      — route + coordinates (Intercity Exp)
- trackTrain-22188.json        — live tracking incl. coach-position timeline (note the reversal at ET/Itarsi)
- trainHistory-22188.json      — completed-journey actuals
- liveAtStation-JBP.json       — station board (includes a cancelled train: 01143)
- checkPNRStatus-sample.json   — PNR with 4 CNF passengers (mask before any log/UI use)
- searchBetween-JBP.json       — A→B results
- getAvailability-22188.json   — availability + prediction
- fareLookup-22188.json        — fare breakdown
- trainHistory-notfound.json   — the HTTP 404 body for an incomplete journey (drives NOT_YET_AVAILABLE mapping)

Recorded 2026-07-08 against the live API (train 22188 ADTL→RKMP, station JBP, search JBP→NU).
checkPNRStatus-sample.json still needs a real PNR to record — mask the PNR before committing.
packages/server/src/railkit/__fixtures__/ mirrors this directory; keep them in sync.
