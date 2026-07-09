// Chooses the push sender from env. Real FCM lands here when a Firebase
// service account is configured (FIREBASE_SERVICE_ACCOUNT / _PATH); until
// then the server boots with the NoopSender so nothing else is blocked.
import { NoopSender, type PushSender } from "./sender.js";

export function createSender(log?: (msg: string) => void): PushSender {
  const configured =
    process.env.FIREBASE_SERVICE_ACCOUNT ?? process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
  if (!configured) {
    log?.("push: no FIREBASE_SERVICE_ACCOUNT configured — using NoopSender (backlog 2.3)");
    return new NoopSender();
  }
  // TODO(2.3-live): return new FcmSender(loadServiceAccount(configured)) once
  // firebase-admin is wired; the FcmSender implements PushSender behind the
  // same seam, so no caller changes.
  log?.("push: FIREBASE_SERVICE_ACCOUNT set but FcmSender not yet wired — using NoopSender");
  return new NoopSender();
}
