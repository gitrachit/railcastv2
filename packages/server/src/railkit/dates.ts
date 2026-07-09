// The upstream speaks DD-MM-YYYY; the Railcast API speaks YYYY-MM-DD.
// This module is the ONLY place that converts (root CLAUDE.md invariant 6).
import { RailkitError } from "./errors.js";

const API_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const UPSTREAM_DATE = /^(\d{2})-(\d{2})-(\d{4})$/;

/** "2026-07-08" → "08-07-2026" */
export function toUpstreamDate(apiDate: string): string {
  const m = API_DATE.exec(apiDate);
  if (!m) throw new RailkitError("INVALID_INPUT", "date must be YYYY-MM-DD", false);
  return `${m[3]}-${m[2]}-${m[1]}`;
}

/** "08-07-2026" → "2026-07-08" */
export function fromUpstreamDate(upstreamDate: string): string {
  const m = UPSTREAM_DATE.exec(upstreamDate);
  if (!m) throw new RailkitError("INVALID_INPUT", "date must be DD-MM-YYYY", false);
  return `${m[3]}-${m[2]}-${m[1]}`;
}

const MONTHS: Record<string, number> = {
  Jan: 1, Feb: 2, Mar: 3, Apr: 4, May: 5, Jun: 6,
  Jul: 7, Aug: 8, Sep: 9, Oct: 10, Nov: 11, Dec: 12,
};

const pad = (n: number | string) => String(n).padStart(2, "0");

/**
 * trackTrain timeline time: "16:15 07-Jul" → "2026-07-07T16:15:00+05:30".
 * The year comes from the run date (with Dec/Jan wrap handling); "SRC", ""
 * and null all mean "no time here".
 */
export function parseUpstreamTime(value: string | null | undefined, runDate: string): string | null {
  if (!value) return null;
  const m = /^(\d{1,2}):(\d{2}) (\d{1,2})-([A-Za-z]{3})$/.exec(value.trim());
  if (!m) return null;
  const month = MONTHS[m[4]!];
  if (!month) return null;
  let year = Number(runDate.slice(0, 4));
  const runMonth = Number(runDate.slice(5, 7));
  if (month === 1 && runMonth === 12) year += 1;
  if (month === 12 && runMonth === 1) year -= 1;
  return `${year}-${pad(month)}-${pad(m[3]!)}T${pad(m[1]!)}:${m[2]}:00+05:30`;
}

/** trackTrain lastUpdate: "07-Jul-2026 22:19" → "2026-07-07T22:19:00+05:30". */
export function parseUpstreamDateTime(value: string | null | undefined): string | null {
  if (!value) return null;
  const m = /^(\d{1,2})-([A-Za-z]{3})-(\d{4}) (\d{1,2}):(\d{2})/.exec(value.trim());
  if (!m) return null;
  const month = MONTHS[m[2]!];
  if (!month) return null;
  return `${m[3]}-${pad(month)}-${pad(m[1]!)}T${pad(m[4]!)}:${m[5]}:00+05:30`;
}

/** checkPNRStatus datetime: "Jul 4, 2026 5:10:00 PM" → "2026-07-04T17:10:00+05:30". */
export function parseUpstreamPnrDateTime(value: string | null | undefined): string | null {
  if (!value) return null;
  const m = /^([A-Za-z]{3}) (\d{1,2}), (\d{4}) (\d{1,2}):(\d{2}):(\d{2}) (AM|PM)$/.exec(
    value.trim(),
  );
  if (!m) return null;
  const month = MONTHS[m[1]!];
  if (!month) return null;
  let hour = Number(m[4]) % 12;
  if (m[7] === "PM") hour += 12;
  return `${m[3]}-${pad(month)}-${pad(m[2]!)}T${pad(hour)}:${m[5]}:${m[6]}+05:30`;
}

/**
 * Upstream delay strings: "" → null, "On Time" → 0, "3 Min" → 3, "4 Mins." → 4,
 * "1 Hr 5 Min" → 65, and the station board's "04:40 Hrs." (HH:MM) → 280.
 */
export function parseDelayMin(value: string | null | undefined): number | null {
  if (!value) return null;
  if (/on\s*time/i.test(value)) return 0;
  const hhmm = /^(\d{1,2}):(\d{2})\s*hrs?\.?$/i.exec(value.trim());
  if (hhmm) return Number(hhmm[1]) * 60 + Number(hhmm[2]);
  const hr = /(\d+)\s*hr/i.exec(value);
  const min = /(\d+)\s*min/i.exec(value);
  if (!hr && !min) return null;
  return (hr ? Number(hr[1]) * 60 : 0) + (min ? Number(min[1]) : 0);
}
