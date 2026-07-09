// Push fan-out (backlog 2.3, FR-7.3/7.4): one detected watch event → the
// watching device, gated by quiet hours + per-type opt-in, delivered via the
// pluggable sender, with latency logged for the §2 chart-push metric.
import type { DeliveryLogEntry } from "../repo.js";
import type { WatchEvent } from "../poller.js";
import {
  isHighPriority,
  shouldDeliver,
  type NotificationPrefs,
} from "./quiet-hours.js";
import type { PushSender } from "./sender.js";

// Narrow slice of WatchRepo the fan-out needs (keeps it unit-testable).
export interface FanoutStore {
  pushTokenFor(deviceId: string): Promise<string | null>;
  prefsFor(deviceId: string): Promise<NotificationPrefs>;
  deletePushToken(deviceId: string): Promise<void>;
  logDelivery(entry: DeliveryLogEntry): Promise<void>;
}

export class PushFanout {
  constructor(
    private readonly deps: {
      store: FanoutStore;
      sender: PushSender;
      now?: () => Date;
    },
  ) {}

  async deliver(event: WatchEvent): Promise<void> {
    const { watch, payload, signature, detectedAt } = event;
    const now = this.deps.now?.() ?? new Date();

    const base = {
      watchId: watch.id,
      deviceId: watch.deviceId,
      kind: payload.kind,
      entityKey: watch.entityKey,
      signature,
      detectedAt,
    };
    const log = (status: string, deliveredAt: Date | null) =>
      this.deps.store.logDelivery({
        ...base,
        deliveredAt,
        latencyMs: deliveredAt ? deliveredAt.getTime() - detectedAt.getTime() : null,
        status,
      });

    const prefs = await this.deps.store.prefsFor(watch.deviceId);
    const decision = shouldDeliver(payload, watch.entityKey, prefs, now);
    if (!decision.deliver) {
      await log(`suppressed:${decision.reason}`, null);
      return;
    }

    const token = await this.deps.store.pushTokenFor(watch.deviceId);
    if (!token) {
      await log("no_token", null);
      return;
    }

    const result = await this.deps.sender.send({
      fcmToken: token,
      payload,
      highPriority: isHighPriority(payload),
    });

    if (result.tokenInvalid) {
      await this.deps.store.deletePushToken(watch.deviceId); // prune dead token
      await log("token_invalid", null);
      return;
    }
    if (!result.ok) {
      await log("failed", null);
      return;
    }
    await log("delivered", now);
  }
}
