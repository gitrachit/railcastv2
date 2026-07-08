import type { ErrorCode } from "@railcast/shared";

export class RailkitError extends Error {
  constructor(
    readonly code: ErrorCode,
    message: string,
    readonly retryable: boolean,
    readonly status?: number,
  ) {
    super(message);
    this.name = "RailkitError";
  }
}
