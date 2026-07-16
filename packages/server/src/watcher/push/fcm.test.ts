import { describe, expect, it } from "vitest";
import type { PushPayload } from "@railcast/shared";
import { FcmSender, toFcmData } from "./fcm.js";
import { createSender } from "./factory.js";
import { NoopSender } from "./sender.js";
import type { Message } from "firebase-admin/messaging";

const arrivalAlarm: PushPayload = {
  kind: "ARRIVAL_ALARM",
  trainNo: "12951",
  stationCode: "RTM",
  etaActual: "03:45",
  leadMin: 20,
};

function fakeMessaging(behave: (msg: Message) => Promise<string>) {
  const sent: Message[] = [];
  return {
    sent,
    send: (msg: Message) => {
      sent.push(msg);
      return behave(msg);
    },
  };
}

describe("toFcmData", () => {
  it("flattens every payload field to strings the app's parser accepts", () => {
    const data = toFcmData({
      kind: "CHART_PREPARED",
      pnrMasked: "••••2882",
      trainNo: "12951",
      trainName: "Mumbai Rajdhani",
      allConfirmed: true,
      coachSummary: "B4 · 32,35",
    });
    // Booleans/numbers become strings — FCM data maps are string→string, and
    // android PushPayload.parse reads them back with toBoolean/toIntOrNull.
    expect(data).toEqual({
      kind: "CHART_PREPARED",
      pnrMasked: "••••2882",
      trainNo: "12951",
      trainName: "Mumbai Rajdhani",
      allConfirmed: "true",
      coachSummary: "B4 · 32,35",
    });
    expect(toFcmData(arrivalAlarm).leadMin).toBe("20");
  });
});

describe("FcmSender", () => {
  it("sends a data message and maps priority from highPriority", async () => {
    const messaging = fakeMessaging(async () => "projects/x/messages/1");
    const sender = new FcmSender(messaging);

    const result = await sender.send({ fcmToken: "tok-1", payload: arrivalAlarm, highPriority: true });

    expect(result).toEqual({ ok: true, tokenInvalid: false });
    expect(messaging.sent[0]).toMatchObject({
      token: "tok-1",
      data: { kind: "ARRIVAL_ALARM", trainNo: "12951" },
      android: { priority: "high" },
    });

    await sender.send({ fcmToken: "tok-1", payload: arrivalAlarm, highPriority: false });
    expect(messaging.sent[1]).toMatchObject({ android: { priority: "normal" } });
  });

  it("marks unregistered tokens invalid so the fan-out prunes them", async () => {
    const err = Object.assign(new Error("gone"), {
      code: "messaging/registration-token-not-registered",
    });
    const sender = new FcmSender(fakeMessaging(() => Promise.reject(err)));

    const result = await sender.send({ fcmToken: "dead", payload: arrivalAlarm, highPriority: false });

    expect(result).toEqual({ ok: false, tokenInvalid: true, error: err.code });
  });

  it("reports transient failures without pruning the token", async () => {
    const err = Object.assign(new Error("try later"), { code: "messaging/internal-error" });
    const sender = new FcmSender(fakeMessaging(() => Promise.reject(err)));

    const result = await sender.send({ fcmToken: "tok-1", payload: arrivalAlarm, highPriority: false });

    expect(result).toEqual({ ok: false, tokenInvalid: false, error: "messaging/internal-error" });
  });
});

describe("createSender", () => {
  it("stays a NoopSender without Firebase credentials", () => {
    delete process.env.FIREBASE_SERVICE_ACCOUNT;
    delete process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
    expect(createSender()).toBeInstanceOf(NoopSender);
  });

  it("fails loudly on malformed credentials instead of silently no-oping", () => {
    process.env.FIREBASE_SERVICE_ACCOUNT = "not-json";
    try {
      expect(() => createSender()).toThrow();
    } finally {
      delete process.env.FIREBASE_SERVICE_ACCOUNT;
    }
  });
});
