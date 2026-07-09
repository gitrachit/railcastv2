-- Shared-journey tokens (backlog 2.5, FR-8). Unguessable, journey-scoped
-- expiry, revocable. No PNR here — only a public train + run date.
CREATE TABLE share_token (
  token TEXT PRIMARY KEY,
  device_id TEXT NOT NULL,       -- the sharer (for revocation ownership)
  train_no TEXT NOT NULL,
  run_date TEXT NOT NULL,        -- YYYY-MM-DD
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX share_token_device_idx ON share_token (device_id);
