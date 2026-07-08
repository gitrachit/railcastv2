// One typed wrapper per upstream endpoint. This module (with http.ts) is the
// ONLY place that talks upstream or knows DD-MM-YYYY. All dates crossing this
// boundary are YYYY-MM-DD (root CLAUDE.md invariant 6).
import type { WindowHrs } from "@railcast/shared";
import { maskPnr } from "../privacy/mask.js";
import { toUpstreamDate } from "./dates.js";
import { upstreamGet } from "./http.js";
import type {
  RawAvailability,
  RawFareLookup,
  RawPnrStatus,
  RawSearchBetweenItem,
  RawStationBoard,
  RawTrackTrain,
  RawTrainHistory,
  RawTrainInfo,
} from "./types.js";
import {
  assertCodeSegment,
  assertPnr,
  assertStationCode,
  assertTrainNo,
  assertWindowHrs,
} from "./validate.js";

const q = encodeURIComponent;

export async function getTrainInfo(trainNo: string): Promise<RawTrainInfo> {
  assertTrainNo(trainNo);
  return upstreamGet(`/api/getTrainInfo/${q(trainNo)}`);
}

/** @param runDate YYYY-MM-DD */
export async function trackTrain(trainNo: string, runDate: string): Promise<RawTrackTrain> {
  assertTrainNo(trainNo);
  return upstreamGet(`/api/trackTrain/${q(trainNo)}/${toUpstreamDate(runDate)}`);
}

/** @param runDate YYYY-MM-DD. 404 upstream = journey not finished → NOT_YET_AVAILABLE. */
export async function getTrainHistory(trainNo: string, runDate: string): Promise<RawTrainHistory> {
  assertTrainNo(trainNo);
  return upstreamGet(`/api/trainHistory/${q(trainNo)}/${toUpstreamDate(runDate)}`, {
    notFoundCode: "NOT_YET_AVAILABLE",
  });
}

export async function liveAtStation(stationCode: string, hrs: WindowHrs): Promise<RawStationBoard> {
  assertStationCode(stationCode);
  assertWindowHrs(hrs);
  return upstreamGet(`/api/liveAtStation/${q(stationCode)}?hrs=${hrs}`);
}

// NOTE: the upstream rejects a date segment on this endpoint ("Route not found"),
// so search is date-less; running_days filtering happens in the screens layer.
export async function searchTrainsBetween(
  fromCode: string,
  toCode: string,
): Promise<RawSearchBetweenItem[]> {
  assertStationCode(fromCode);
  assertStationCode(toCode);
  return upstreamGet(`/api/searchTrainBetweenStations/${q(fromCode)}/${q(toCode)}`);
}

/** @param date YYYY-MM-DD */
export async function getAvailability(
  trainNo: string,
  fromCode: string,
  toCode: string,
  date: string,
  coach: string,
  quota: string,
): Promise<RawAvailability> {
  assertTrainNo(trainNo);
  assertStationCode(fromCode);
  assertStationCode(toCode);
  assertCodeSegment(coach, "coach");
  assertCodeSegment(quota, "quota");
  return upstreamGet(
    `/api/getAvailability/${q(trainNo)}/${q(fromCode)}/${q(toCode)}/${toUpstreamDate(date)}/${q(coach)}/${q(quota)}`,
  );
}

/** @param date YYYY-MM-DD. Upstream wants the date SECOND here (unlike getAvailability). */
export async function fareLookup(
  trainNo: string,
  date: string,
  fromCode: string,
  toCode: string,
  travelClass: string,
  quota: string,
): Promise<RawFareLookup> {
  assertTrainNo(trainNo);
  assertStationCode(fromCode);
  assertStationCode(toCode);
  assertCodeSegment(travelClass, "class");
  assertCodeSegment(quota, "quota");
  return upstreamGet(
    `/api/fareLookup/${q(trainNo)}/${toUpstreamDate(date)}/${q(fromCode)}/${q(toCode)}/${q(travelClass)}/${q(quota)}`,
  );
}

export async function checkPnrStatus(pnr: string): Promise<RawPnrStatus> {
  assertPnr(pnr);
  return upstreamGet(`/api/checkPNRStatus/${q(pnr)}`, {
    logPath: `/api/checkPNRStatus/${maskPnr(pnr)}`, // FR-4.3: raw PNR never hits a log line
  });
}
