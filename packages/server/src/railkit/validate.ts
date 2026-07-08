import { PNR_PATTERN, STATION_CODE_PATTERN, TRAIN_NO_PATTERN, type WindowHrs } from "@railcast/shared";
import { RailkitError } from "./errors.js";

// Error messages never echo the offending value (it may be a PNR).

export function assertTrainNo(trainNo: string): void {
  if (!TRAIN_NO_PATTERN.test(trainNo)) {
    throw new RailkitError("INVALID_INPUT", "train number must be exactly 5 digits", false);
  }
}

export function assertPnr(pnr: string): void {
  if (!PNR_PATTERN.test(pnr)) {
    throw new RailkitError("INVALID_INPUT", "PNR must be exactly 10 digits", false);
  }
}

export function assertStationCode(code: string): void {
  if (!STATION_CODE_PATTERN.test(code)) {
    throw new RailkitError("INVALID_INPUT", "station code must be 2-5 uppercase letters", false);
  }
}

export function assertWindowHrs(hrs: number): asserts hrs is WindowHrs {
  if (hrs !== 2 && hrs !== 4 && hrs !== 8) {
    throw new RailkitError("INVALID_INPUT", "hrs must be one of 2, 4, 8", false);
  }
}

const CODE_SEGMENT = /^[A-Z0-9]{1,3}$/;

export function assertCodeSegment(value: string, label: string): void {
  if (!CODE_SEGMENT.test(value)) {
    throw new RailkitError("INVALID_INPUT", `${label} must be 1-3 uppercase letters/digits`, false);
  }
}
