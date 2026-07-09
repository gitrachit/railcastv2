// Share-token persistence (backlog 2.5). Tokens are unguessable and carry no
// PNR — only a public train + run date (FR-8.2).
import { randomBytes } from "node:crypto";
import type pg from "pg";

export interface ShareToken {
  token: string;
  trainNo: string;
  runDate: string;
  expiresAt: Date;
  revoked: boolean;
}

export class ShareRepo {
  constructor(private readonly pool: pg.Pool) {}

  async create(
    deviceId: string,
    trainNo: string,
    runDate: string,
    expiresAt: Date,
  ): Promise<{ token: string; expiresAt: string }> {
    const token = randomBytes(16).toString("base64url"); // 128-bit, unguessable
    await this.pool.query(
      `INSERT INTO share_token (token, device_id, train_no, run_date, expires_at)
       VALUES ($1, $2, $3, $4, $5)`,
      [token, deviceId, trainNo, runDate, expiresAt],
    );
    return { token, expiresAt: expiresAt.toISOString() };
  }

  /** Live token for public rendering; null when unknown, expired, or revoked. */
  async getActive(token: string): Promise<ShareToken | null> {
    const res = await this.pool.query<{
      token: string;
      train_no: string;
      run_date: string;
      expires_at: Date;
      revoked: boolean;
    }>(
      "SELECT token, train_no, run_date, expires_at, revoked FROM share_token WHERE token = $1",
      [token],
    );
    const row = res.rows[0];
    if (!row || row.revoked || row.expires_at.getTime() <= Date.now()) return null;
    return {
      token: row.token,
      trainNo: row.train_no,
      runDate: row.run_date,
      expiresAt: row.expires_at,
      revoked: row.revoked,
    };
  }

  /** Revoke; only the sharer's device may revoke. Returns true when a row changed. */
  async revoke(deviceId: string, token: string): Promise<boolean> {
    const res = await this.pool.query(
      "UPDATE share_token SET revoked = true WHERE token = $1 AND device_id = $2 AND revoked = false",
      [token, deviceId],
    );
    return (res.rowCount ?? 0) > 0;
  }
}
