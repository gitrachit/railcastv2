// Live FCM sender (backlog 2.3-live, FR-7.3). Sends each watch event as an
// FCM *data* message — the app renders it client-side so opt-in, mute and
// quiet hours still apply on-device (see RailcastMessagingService). Sits
// behind the PushSender seam; everything upstream of factory.ts is unchanged.
import { readFileSync } from "node:fs";
import { cert, initializeApp } from "firebase-admin/app";
import { getMessaging, type Messaging } from "firebase-admin/messaging";
import type { PushPayload } from "@railcast/shared";
import type { PushMessage, PushSender, SendResult } from "./sender.js";

/** FCM data messages carry string→string maps only; the app parses them back
 *  by the `kind` discriminator (android PushPayload.parse mirrors this). */
export function toFcmData(payload: PushPayload): Record<string, string> {
  const data: Record<string, string> = {};
  for (const [k, v] of Object.entries(payload)) data[k] = String(v);
  return data;
}

// Admin SDK error codes that mean the token is permanently dead → prune it.
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

export class FcmSender implements PushSender {
  constructor(private readonly messaging: Pick<Messaging, "send">) {}

  async send(message: PushMessage): Promise<SendResult> {
    try {
      await this.messaging.send({
        token: message.fcmToken,
        data: toFcmData(message.payload),
        // Arrival alarms must wake a dozing device (full-screen intent, FR-7.3).
        android: { priority: message.highPriority ? "high" : "normal" },
      });
      return { ok: true, tokenInvalid: false };
    } catch (e) {
      // Log the error *code* only — payloads can carry masked PNRs, but the
      // redaction rule says keep anything payload-shaped out of log lines.
      const code = (e as { code?: string }).code ?? "unknown";
      return { ok: false, tokenInvalid: DEAD_TOKEN_CODES.has(code), error: code };
    }
  }
}

/** Builds the real sender from FIREBASE_SERVICE_ACCOUNT (inline JSON) or
 *  FIREBASE_SERVICE_ACCOUNT_PATH (file). Throws on malformed credentials —
 *  a deploy that *asked* for push must fail at boot, not silently no-op. */
export function createFcmSender(source: { inline?: string; path?: string }): FcmSender {
  const raw = source.inline ?? readFileSync(source.path!, "utf8");
  const serviceAccount = JSON.parse(raw) as { project_id?: string };
  const app = initializeApp({ credential: cert(serviceAccount as Parameters<typeof cert>[0]) });
  return new FcmSender(getMessaging(app));
}
