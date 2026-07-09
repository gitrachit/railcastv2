import { describe, expect, it } from "vitest";
import type { PushPayload } from "@railcast/shared";
import { DEFAULT_PREFS, inQuietHours, shouldDeliver, type NotificationPrefs } from "./quiet-hours.js";

const at = (istHour: number) => new Date(Date.UTC(2026, 6, 8, (istHour - 5 + 24) % 24, 30));
const delay: PushPayload = { kind: "DELAY", trainNo: "22188", delayMin: 20, nextStation: "NU" };
const alarm: PushPayload = {
  kind: "ARRIVAL_ALARM",
  trainNo: "22188",
  stationCode: "NU",
  etaActual: "2026-07-08T16:45:00+05:30",
  leadMin: 20,
};

describe("inQuietHours", () => {
  it("is false when unset", () => {
    expect(inQuietHours(DEFAULT_PREFS, at(3))).toBe(false);
  });

  it("handles an overnight window (22→7 IST)", () => {
    const prefs = { ...DEFAULT_PREFS, quietStartHour: 22, quietEndHour: 7 };
    expect(inQuietHours(prefs, at(23))).toBe(true);
    expect(inQuietHours(prefs, at(4))).toBe(true);
    expect(inQuietHours(prefs, at(7))).toBe(false); // end is exclusive
    expect(inQuietHours(prefs, at(12))).toBe(false);
  });
});

describe("shouldDeliver (FR-7.4)", () => {
  const quiet: NotificationPrefs = { ...DEFAULT_PREFS, quietStartHour: 22, quietEndHour: 7 };

  it("suppresses normal pushes during quiet hours", () => {
    expect(shouldDeliver(delay, "train:x", quiet, at(3))).toEqual({
      deliver: false,
      reason: "quiet_hours",
    });
  });

  it("lets the arrival alarm through quiet hours (contracts §5)", () => {
    expect(shouldDeliver(alarm, "train:x", quiet, at(3)).deliver).toBe(true);
  });

  it("honors per-kind opt-out", () => {
    const muted = { ...DEFAULT_PREFS, mutedKinds: ["DELAY"] as PushPayload["kind"][] };
    expect(shouldDeliver(delay, "train:x", muted, at(12))).toEqual({
      deliver: false,
      reason: "muted_kind",
    });
  });

  it("honors one-tap mute-this-journey", () => {
    const muted = { ...DEFAULT_PREFS, mutedEntityKeys: ["train:22188:2026-07-08"] };
    expect(shouldDeliver(delay, "train:22188:2026-07-08", muted, at(12)).reason).toBe(
      "muted_journey",
    );
  });

  it("delivers by default", () => {
    expect(shouldDeliver(delay, "train:x", DEFAULT_PREFS, at(12)).deliver).toBe(true);
  });
});
