// PNR masking per FR-4.3: "••••2882". The ONLY representation allowed in
// responses, logs, and UI. Raw PNRs may exist transiently in request paths and
// encrypted storage — nothing else.
export function maskPnr(pnr: string): string {
  return `••••${pnr.slice(-4)}`;
}
