// Quiet hours + per-type opt-in (FR-7.4). Default posture: minimal, meaningful
// notifications. ARRIVAL_ALARM bypasses quiet hours by design (contracts §5).
import type { PushPayload } from "@railcast/shared";

export interface NotificationPrefs {
  /** IST hour when quiet begins (inclusive) and ends (exclusive), e.g. 22→7. */
  quietStartHour: number | null;
  quietEndHour: number | null;
  /** Push kinds this device has opted OUT of. Empty = all on (default). */
  mutedKinds: PushPayload["kind"][];
  /** Journeys the user muted one-tap; entity keys. */
  mutedEntityKeys: string[];
}

export const DEFAULT_PREFS: NotificationPrefs = {
  quietStartHour: null,
  quietEndHour: null,
  mutedKinds: [],
  mutedEntityKeys: [],
};

const IST_OFFSET_MS = 5.5 * 3600 * 1000;

export function istHour(now: Date): number {
  return new Date(now.getTime() + IST_OFFSET_MS).getUTCHours();
}

export function inQuietHours(prefs: NotificationPrefs, now: Date): boolean {
  const { quietStartHour: s, quietEndHour: e } = prefs;
  if (s === null || e === null) return false;
  const h = istHour(now);
  return s <= e ? h >= s && h < e : h >= s || h < e; // handle the overnight wrap
}

export interface DeliveryDecision {
  deliver: boolean;
  reason?: "muted_kind" | "muted_journey" | "quiet_hours";
}

/** Should this event reach this device, given its prefs? */
export function shouldDeliver(
  payload: PushPayload,
  entityKey: string,
  prefs: NotificationPrefs,
  now: Date,
): DeliveryDecision {
  if (prefs.mutedKinds.includes(payload.kind)) return { deliver: false, reason: "muted_kind" };
  if (prefs.mutedEntityKeys.includes(entityKey)) return { deliver: false, reason: "muted_journey" };
  // The arrival alarm is the one push that must wake you — it ignores quiet hours.
  if (payload.kind === "ARRIVAL_ALARM") return { deliver: true };
  if (inQuietHours(prefs, now)) return { deliver: false, reason: "quiet_hours" };
  return { deliver: true };
}

export function isHighPriority(payload: PushPayload): boolean {
  return payload.kind === "ARRIVAL_ALARM";
}
