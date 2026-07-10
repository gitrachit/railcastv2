// One poll of one entity: fetch through the SHARED cache (watcher polls warm
// the same keys foreground screens read — PRD §6.5), normalize, diff prev→next
// per watch into typed events (2.2), deliver the fresh ones, dedup, tighten
// expiry once the journey settles, and report the adaptive next-poll delay.
import type { PushPayload, WatchEntity } from "@railcast/shared";
import { Cache, CACHE_TTLS } from "../cache/index.js";
import { parseDelayMin, parseUpstreamTime } from "../railkit/dates.js";
import { checkPnrStatus, trackTrain } from "../railkit/endpoints.js";
import type { RawPnrStatus, RawTrackTrain } from "../railkit/types.js";
import { decryptPnrBlob, encryptPnrBlob, pnrCacheKey } from "../privacy/crypto.js";
import { nextPollDelayS, type EntitySnapshot } from "./cadence.js";
import { detectWatchEvents } from "./diff.js";
import { normalizePnr, normalizeTrain, type NormalizedEntity } from "./normalize.js";
import type { WatchRepo, WatchRow } from "./repo.js";

const SETTLED_GRACE_MS = 24 * 3600_000; // keep watches a day past journey end, then purge
const UPSTREAM_BACKOFF_S = 120; // retry cadence while the upstream is failing

export interface WatchEvent {
  watch: WatchRow;
  payload: PushPayload;
  signature: string;
  detectedAt: Date; // when this poll observed the change (for delivery-latency logging)
}

/** 2.3's FCM fan-out plugs in here: one typed event, ready to push. */
export type OnEvent = (event: WatchEvent) => Promise<void>;

export class EntityPoller {
  constructor(
    private readonly deps: {
      cache: Cache;
      repo: WatchRepo;
      onEvent?: OnEvent;
      now?: () => Date;
    },
  ) {}

  /** Returns the next delay, or null to end this entity's poll chain. */
  async poll(entityKey: string): Promise<{ nextDelayS: number } | null> {
    const watches = await this.deps.repo.activeForEntity(entityKey, this.deps.now?.() ?? new Date());
    if (watches.length === 0) return null; // last watch gone — chain ends (FR-7.5)

    const entity = watches[0]!.entity;
    const now = this.deps.now?.() ?? new Date();

    let fetched: Awaited<ReturnType<EntityPoller["fetchState"]>>;
    try {
      fetched = await this.fetchState(entity, now);
    } catch (e) {
      // An upstream failure must NOT kill the poll chain (NFR-2 at-least-once):
      // back off and retry; prev state is untouched so no transition is lost.
      console.info(
        `watcher poll ${entityKey} upstream failure, backing off: ${(e as Error).message}`,
      );
      return { nextDelayS: UPSTREAM_BACKOFF_S };
    }
    const { state: next, raw, snapshot, settledAt } = fetched;

    const prev = await this.deps.repo.getEntityState<NormalizedEntity>(entityKey);
    if (prev) {
      // First poll for an entity is a silent baseline (prev === null): a watch
      // never fires for a condition that was already true when it was created.
      for (const watch of watches) {
        const detected = detectWatchEvents({ prev, next, raw, watch, now });
        const fresh = detected.filter((e) => !watch.delivered.includes(e.signature));
        for (const e of fresh) {
          await this.deps.onEvent?.({
            watch,
            payload: e.payload,
            signature: e.signature,
            detectedAt: now,
          });
        }
        if (fresh.length > 0) {
          await this.deps.repo.markDelivered(
            watch.id,
            fresh.map((e) => e.signature),
          );
        }
      }
    }
    await this.deps.repo.setEntityState(entityKey, next);

    if (settledAt) {
      await this.deps.repo.setEntityExpiry(entityKey, new Date(settledAt + SETTLED_GRACE_MS));
    }

    return { nextDelayS: nextPollDelayS(snapshot, watches) };
  }

  private async fetchState(
    entity: WatchEntity,
    now: Date,
  ): Promise<{
    state: NormalizedEntity;
    raw: RawTrackTrain | RawPnrStatus;
    snapshot: EntitySnapshot;
    settledAt: number | null;
  }> {
    if (entity.kind === "pnr") {
      const res = await this.deps.cache.getOrFetch(
        pnrCacheKey(entity.pnr),
        CACHE_TTLS.pnrChartWindow, // watcher polls always want fresh PNR state
        async () => encryptPnrBlob(JSON.stringify(await checkPnrStatus(entity.pnr))),
      );
      const raw = JSON.parse(decryptPnrBlob(res.value)) as RawPnrStatus;
      const state = normalizePnr(raw);
      const arrived =
        state.arrivalIso !== null && Date.parse(state.arrivalIso) < now.getTime();
      return {
        state,
        raw,
        snapshot: {
          kind: "pnr",
          settled: state.chartPrepared,
          msToCriticalPoint: state.departureIso
            ? Date.parse(state.departureIso) - now.getTime()
            : null,
        },
        settledAt: arrived ? Date.parse(state.arrivalIso!) : null,
      };
    }

    const res = await this.deps.cache.getOrFetch(
      `rk:trackTrain:${entity.trainNo}:${entity.runDate}`,
      CACHE_TTLS.trackTrain,
      () => trackTrain(entity.trainNo, entity.runDate),
    );
    const raw = res.value;
    const state = normalizeTrain(raw);
    const settled = state.state === "arrived" || state.state === "cancelled";

    return {
      state,
      raw,
      snapshot: {
        kind: "train",
        settled,
        msToCriticalPoint: msToSoonestAlarm(raw, entity.runDate, now),
      },
      settledAt: settled ? now.getTime() : null,
    };
  }
}

/** ms until the soonest arrival-alarm trigger among this run's stops, from live ETAs. */
function msToSoonestAlarm(track: RawTrackTrain, runDate: string, now: Date): number | null {
  // Approximation good enough for cadence: next stoppages' scheduled arrivals
  // shifted by the current delay. (Alarm firing itself is 2.2/2.3 territory.)
  const stoppages = track.timeline.filter((e) => e.type === "stoppage");
  const crossed = stoppages.filter((e) => e.status === "passed" || e.status === "current");
  const lastCrossed = crossed[crossed.length - 1];
  const delayMin =
    parseDelayMin(lastCrossed?.arrival?.delay) ??
    parseDelayMin(lastCrossed?.departure?.delay) ??
    0;

  let soonest: number | null = null;
  for (const s of stoppages) {
    if (s.status !== "upcoming" || !s.arrival?.scheduled) continue;
    const iso = parseUpstreamTime(s.arrival.scheduled, runDate);
    if (!iso) continue;
    const eta = Date.parse(iso) + delayMin * 60_000 - now.getTime();
    if (eta > 0 && (soonest === null || eta < soonest)) soonest = eta;
  }
  return soonest;
}
