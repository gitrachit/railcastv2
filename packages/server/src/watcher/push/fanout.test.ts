import { describe, expect, it } from "vitest";
import type { PushPayload } from "@railcast/shared";
import type { DeliveryLogEntry, WatchRow } from "../repo.js";
import type { WatchEvent } from "../poller.js";
import { PushFanout, type FanoutStore } from "./fanout.js";
import { DEFAULT_PREFS, type NotificationPrefs } from "./quiet-hours.js";
import { FakeSender } from "./sender.js";

class FakeStore implements FanoutStore {
  tokens = new Map<string, string>([["d1", "tok-d1"]]);
  prefs = new Map<string, NotificationPrefs>();
  deleted: string[] = [];
  logs: DeliveryLogEntry[] = [];

  async pushTokenFor(deviceId: string) {
    return this.tokens.get(deviceId) ?? null;
  }
  async prefsFor(deviceId: string) {
    return this.prefs.get(deviceId) ?? DEFAULT_PREFS;
  }
  async deletePushToken(deviceId: string) {
    this.deleted.push(deviceId);
    this.tokens.delete(deviceId);
  }
  async logDelivery(entry: DeliveryLogEntry) {
    this.logs.push(entry);
  }
}

function event(over: Partial<WatchEvent> = {}): WatchEvent {
  const payload: PushPayload = {
    kind: "CHART_PREPARED",
    pnrMasked: "••••2882",
    trainNo: "12780",
    trainName: "GOA EXPRESS",
    allConfirmed: true,
    coachSummary: "A1",
  };
  return {
    watch: {
      id: "w1",
      deviceId: "d1",
      type: "chart",
      entityKey: "pnr:abc",
      entity: { kind: "pnr", pnr: "8524132882" },
      params: {},
      delivered: [],
      expiresAt: new Date(),
    } as WatchRow,
    payload,
    signature: "CHART_PREPARED",
    detectedAt: new Date("2026-07-08T04:00:00Z"),
    ...over,
  };
}

describe("PushFanout (FR-7.3/7.4)", () => {
  it("delivers a message and logs latency", async () => {
    const store = new FakeStore();
    const sender = new FakeSender();
    const now = new Date("2026-07-08T04:00:03Z"); // 3s after detection
    await new PushFanout({ store, sender, now: () => now }).deliver(event());

    expect(sender.sent).toHaveLength(1);
    expect(sender.sent[0]).toMatchObject({ fcmToken: "tok-d1", highPriority: false });
    expect(store.logs[0]).toMatchObject({ status: "delivered", latencyMs: 3000 });
  });

  it("marks the arrival alarm high priority", async () => {
    const store = new FakeStore();
    const sender = new FakeSender();
    const payload: PushPayload = {
      kind: "ARRIVAL_ALARM",
      trainNo: "22188",
      stationCode: "NU",
      etaActual: "2026-07-08T16:45:00+05:30",
      leadMin: 20,
    };
    await new PushFanout({ store, sender }).deliver(
      event({ payload, watch: { ...event().watch, type: "arrival" } as WatchRow }),
    );
    expect(sender.sent[0]!.highPriority).toBe(true);
  });

  it("suppresses during quiet hours and logs the reason, without sending", async () => {
    const store = new FakeStore();
    store.prefs.set("d1", { ...DEFAULT_PREFS, quietStartHour: 22, quietEndHour: 7 });
    const sender = new FakeSender();
    // 04:00 UTC = 09:30 IST — wait, choose a UTC time that is inside 22→7 IST:
    const now = new Date("2026-07-07T20:00:00Z"); // 01:30 IST
    await new PushFanout({ store, sender, now: () => now }).deliver(event());

    expect(sender.sent).toHaveLength(0);
    expect(store.logs[0]!.status).toBe("suppressed:quiet_hours");
    expect(store.logs[0]!.deliveredAt).toBeNull();
  });

  it("prunes a dead token and logs token_invalid", async () => {
    const store = new FakeStore();
    const sender = new FakeSender();
    sender.fail("tok-d1", { tokenInvalid: true });
    await new PushFanout({ store, sender }).deliver(event());

    expect(store.deleted).toEqual(["d1"]);
    expect(store.logs[0]!.status).toBe("token_invalid");
  });

  it("logs no_token when the device has no push registration", async () => {
    const store = new FakeStore();
    store.tokens.clear();
    const sender = new FakeSender();
    await new PushFanout({ store, sender }).deliver(event());

    expect(sender.sent).toHaveLength(0);
    expect(store.logs[0]!.status).toBe("no_token");
  });
});
