// Chooses the push sender from env. With a Firebase service account
// configured (FIREBASE_SERVICE_ACCOUNT / _PATH) pushes go out via FCM;
// without one the server boots with the NoopSender so nothing else is blocked.
import { createFcmSender } from "./fcm.js";
import { NoopSender, type PushSender } from "./sender.js";

export function createSender(log?: (msg: string) => void): PushSender {
  const inline = process.env.FIREBASE_SERVICE_ACCOUNT;
  const path = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
  if (!inline && !path) {
    log?.("push: no FIREBASE_SERVICE_ACCOUNT configured — using NoopSender (backlog 2.3)");
    return new NoopSender();
  }
  const sender = createFcmSender({ inline, path });
  log?.("push: FCM sender active");
  return sender;
}
