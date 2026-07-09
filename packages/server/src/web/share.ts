// Shared-journey routes (backlog 2.5, FR-8). POST /share/journey and
// DELETE /share/:token are app-authed; GET /t/:token is public HTML served
// from the same cache (no auth, no install wall), 410 after expiry.
import { TRAIN_NO_PATTERN, type ShareJourneyRequest, type ShareJourneyResponse } from "@railcast/shared";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import type { Cache } from "../cache/index.js";
import { err, HTTP_STATUS, immediateMeta, ok } from "../lib/envelope.js";
import { istDateString } from "../lib/ist.js";
import { RailkitError } from "../railkit/errors.js";
import { loadTrainScreen } from "../screens/train.js";
import { renderExpiredPage, renderJourneyPage } from "./page.js";
import type { ShareRepo } from "./share-repo.js";

const API_DATE = /^\d{4}-\d{2}-\d{2}$/;

export interface ShareRoutesDeps {
  repo: ShareRepo;
  cache: Cache;
  now?: () => Date;
  publicBaseUrl?: string; // for the returned share URL
}

export function registerShareRoutes(app: FastifyInstance, deps: ShareRoutesDeps): void {
  const baseUrl = deps.publicBaseUrl ?? process.env.PUBLIC_BASE_URL ?? "https://railcast.app";

  app.post("/share/journey", async (req: FastifyRequest, reply: FastifyReply) => {
    const body = req.body as Partial<ShareJourneyRequest> | null;
    if (!body || !TRAIN_NO_PATTERN.test(body.trainNo ?? "") || !API_DATE.test(body.runDate ?? "")) {
      return reply
        .status(HTTP_STATUS.INVALID_INPUT)
        .send(err("INVALID_INPUT", "body must be { trainNo: 5 digits, runDate: YYYY-MM-DD }", false));
    }
    // Journey-scoped expiry: run date + 3 days (FR-8.2).
    const expiresAt = new Date(Date.parse(`${body.runDate}T00:00:00+05:30`) + 3 * 86_400_000);
    const { token, expiresAt: iso } = await deps.repo.create(
      req.deviceId!,
      body.trainNo!,
      body.runDate!,
      expiresAt,
    );

    const data: ShareJourneyResponse = { token, url: `${baseUrl}/t/${token}`, expiresAt: iso };
    return ok(data, immediateMeta());
  });

  app.delete("/share/:token", async (req: FastifyRequest, reply: FastifyReply) => {
    const { token } = req.params as { token: string };
    const revoked = await deps.repo.revoke(req.deviceId!, token);
    if (!revoked) {
      return reply.status(HTTP_STATUS.NOT_FOUND).send(err("NOT_FOUND", "share not found", false));
    }
    return ok({}, immediateMeta());
  });

  // Public — no auth (isPublic() allows /t/*). Renders HTML, never JSON.
  app.get("/t/:token", async (req: FastifyRequest, reply: FastifyReply) => {
    const { token } = req.params as { token: string };
    const share = await deps.repo.getActive(token);
    if (!share) {
      return reply.status(410).type("text/html; charset=utf-8").send(renderExpiredPage());
    }
    try {
      const { screen, meta } = await loadTrainScreen(
        deps.cache,
        share.trainNo,
        // The share pins a specific run date; ask for it explicitly by matching
        // it to today/yesterday, else fall back to auto-probe.
        runChoiceFor(share.runDate, deps.now?.() ?? new Date()),
        deps.now?.() ?? new Date(),
      );
      return reply.type("text/html; charset=utf-8").send(renderJourneyPage(screen, meta));
    } catch (e) {
      if (e instanceof RailkitError) {
        // Upstream hiccup on a public page: show the expired/placeholder page
        // rather than a raw error (PRD §7 — every state designed).
        return reply.status(200).type("text/html; charset=utf-8").send(renderExpiredPage());
      }
      throw e;
    }
  });
}

function runChoiceFor(runDate: string, now: Date): "auto" | "today" | "yesterday" {
  if (runDate === istDateString(now)) return "today";
  if (runDate === istDateString(now, -1)) return "yesterday";
  return "auto";
}
