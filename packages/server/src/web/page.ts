// Server-rendered shared-journey page (FR-8.1). No JS required, no install
// wall — a single quiet install prompt (FR-8.2). Status is icon + word +
// colour (invariant 4); the map position is labelled "estimated" (FR-2.2);
// a freshness stamp is always present (FR-2.5). Copy is EN; the public page
// stays deliberately simple.
import type { Meta, TrainScreen } from "@railcast/shared";

const STATUS_STYLE: Record<TrainScreen["status"]["state"], { icon: string; word: string; color: string }> = {
  running: { icon: "▶", word: "Running", color: "#1a7f37" },
  arrived: { icon: "●", word: "Arrived", color: "#1a7f37" },
  not_started: { icon: "◷", word: "Not started", color: "#9a6700" },
  cancelled: { icon: "✕", word: "Cancelled", color: "#cf222e" },
  diverted: { icon: "⚠", word: "Diverted", color: "#9a6700" },
  rescheduled: { icon: "↻", word: "Rescheduled", color: "#9a6700" },
};

function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

function fmtTime(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? "—"
    : d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", timeZone: "Asia/Kolkata" });
}

export function renderJourneyPage(screen: TrainScreen, meta: Meta): string {
  const s = STATUS_STYLE[screen.status.state];
  const rows = screen.route
    .map((stop) => {
      const cur = stop.state === "next" || stop.state === "departed";
      const delay =
        stop.delayMin === null ? "" : stop.delayMin === 0 ? "on time" : `${stop.delayMin} min late`;
      return `<tr${cur ? ' class="cur"' : ""}>
        <td>${esc(stop.name)}<span class="code">${esc(stop.code)}</span></td>
        <td>${fmtTime(stop.scheduled.arr ?? stop.scheduled.dep)}</td>
        <td>${esc(delay)}</td>
        <td>${stop.platform ? "PF " + esc(stop.platform) : ""}</td>
      </tr>`;
    })
    .join("");

  const est = screen.position
    ? `<p class="est">Estimated position between ${esc(screen.position.betweenCodes[0])} and ${esc(
        screen.position.betweenCodes[1],
      )} — not GPS.</p>`
    : "";
  const stale = meta.stale ? ' · <span class="stale">showing cached data</span>' : "";

  return `<!doctype html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${esc(screen.name)} (${esc(screen.trainNo)}) — live status · Railcast</title>
<style>
  :root { color-scheme: light dark; }
  body { font-family: system-ui, sans-serif; margin: 0; padding: 1.25rem; max-width: 640px; margin-inline: auto; line-height: 1.4; }
  h1 { font-size: 1.25rem; margin: 0 0 .25rem; }
  .status { display: inline-flex; align-items: center; gap: .4rem; font-weight: 700; font-size: 1.1rem; color: ${s.color}; }
  .status .icon { font-size: 1.1rem; }
  .summary { color: #555; margin: .35rem 0 .75rem; }
  .fresh { font-size: .8rem; color: #777; }
  .est { font-size: .85rem; color: #555; background: #f3f4f6; padding: .5rem .7rem; border-radius: 8px; }
  table { width: 100%; border-collapse: collapse; margin-top: 1rem; font-size: .9rem; }
  td { padding: .4rem .3rem; border-bottom: 1px solid #e5e7eb; }
  tr.cur td { font-weight: 700; }
  .code { color: #999; font-size: .75rem; margin-left: .4rem; }
  .cta { margin-top: 1.5rem; padding: .8rem 1rem; background: #eef2ff; border-radius: 10px; font-size: .9rem; }
  .disclaimer { margin-top: 1.5rem; font-size: .75rem; color: #999; }
  @media (prefers-color-scheme: dark) {
    body { background: #0d1117; color: #e6edf3; }
    .summary, .fresh, .est { color: #9aa4ad; }
    .est { background: #161b22; } td { border-color: #21262d; } .cta { background: #161b22; }
  }
</style></head><body>
  <h1>${esc(screen.name)} <span class="code">${esc(screen.trainNo)}</span></h1>
  <div class="status"><span class="icon" aria-hidden="true">${esc(s.icon)}</span><span>${esc(s.word)}</span></div>
  <p class="summary">${esc(screen.status.summary)}</p>
  <p class="fresh">Updated ${fmtTime(meta.fetchedAt)}${stale}</p>
  ${est}
  <table><tbody>${rows}</tbody></table>
  <div class="cta">Follow this journey live, ad-free, in the Railcast app.</div>
  <p class="disclaimer">Not affiliated with Indian Railways. Times are estimates.</p>
</body></html>`;
}

export function renderExpiredPage(): string {
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Link expired · Railcast</title>
<style>body{font-family:system-ui,sans-serif;max-width:480px;margin:4rem auto;padding:0 1.25rem;text-align:center;color-scheme:light dark}</style>
</head><body>
  <h1>This journey link has expired</h1>
  <p>Shared Railcast journeys stay live only for the trip. Ask for a fresh link, or track the train yourself in the Railcast app — ad-free.</p>
</body></html>`;
}
