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

Paste the corresponding JSON bodies from the API console into these filenames before running server tests.
(They were captured during product research; regenerate anytime from the provider dashboard.)
