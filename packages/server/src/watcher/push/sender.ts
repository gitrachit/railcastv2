// FCM sender seam (backlog 2.3). The fan-out talks to this interface only;
// FcmSender (real Admin SDK) drops in behind it once a Firebase service
// account is configured. FakeSender backs the unit tests.
import type { PushPayload } from "@railcast/shared";

export interface PushMessage {
  fcmToken: string;
  payload: PushPayload; // sent as an FCM `data` message; the app renders it
  highPriority: boolean; // arrival alarm → high priority + full-screen intent
}

export interface SendResult {
  ok: boolean;
  /** true when FCM reports the token is dead (unregistered) — prune it. */
  tokenInvalid: boolean;
  error?: string;
}

export interface PushSender {
  send(message: PushMessage): Promise<SendResult>;
}

/** No-credential default: logs and no-ops. Keeps the server bootable pre-Firebase. */
export class NoopSender implements PushSender {
  async send(): Promise<SendResult> {
    return { ok: true, tokenInvalid: false };
  }
}

/** Test double: records every message and lets tests script failures. */
export class FakeSender implements PushSender {
  readonly sent: PushMessage[] = [];
  private readonly scripted = new Map<string, SendResult>();

  fail(fcmToken: string, result: Omit<SendResult, "ok">): void {
    this.scripted.set(fcmToken, { ok: false, ...result });
  }

  async send(message: PushMessage): Promise<SendResult> {
    this.sent.push(message);
    return this.scripted.get(message.fcmToken) ?? { ok: true, tokenInvalid: false };
  }
}
