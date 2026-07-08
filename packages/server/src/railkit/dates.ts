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
