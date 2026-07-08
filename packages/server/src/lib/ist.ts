// Run dates are Indian Railways days — always IST, regardless of server TZ.
const IST_OFFSET_MS = 5.5 * 3600 * 1000;

/** YYYY-MM-DD in IST, offset by whole days (0 = today, -1 = yesterday). */
export function istDateString(now: Date, dayOffset = 0): string {
  return new Date(now.getTime() + IST_OFFSET_MS + dayOffset * 86_400_000)
    .toISOString()
    .slice(0, 10);
}

/**
 * Station boards give bare "HH:mm" times. Anchor to the IST calendar day
 * (yesterday/today/tomorrow) closest to now — boards only span a few hours.
 */
export function nearestIstIsoForTime(hhmm: string, now: Date): string | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec(hhmm.trim());
  if (!m) return null;
  const time = `${m[1]!.padStart(2, "0")}:${m[2]}:00+05:30`;
  let best: string | null = null;
  let bestDiff = Number.POSITIVE_INFINITY;
  for (const offset of [-1, 0, 1]) {
    const iso = `${istDateString(now, offset)}T${time}`;
    const diff = Math.abs(Date.parse(iso) - now.getTime());
    if (diff < bestDiff) {
      bestDiff = diff;
      best = iso;
    }
  }
  return best;
}

/** Shift an ISO timestamp by N minutes, keeping its +05:30 offset. */
export function shiftIsoMinutes(iso: string, minutes: number): string {
  const shifted = new Date(Date.parse(iso) + minutes * 60_000);
  const ist = new Date(shifted.getTime() + IST_OFFSET_MS).toISOString().slice(0, 19);
  return `${ist}+05:30`;
}
