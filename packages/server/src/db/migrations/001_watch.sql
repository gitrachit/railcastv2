-- Watch model (backlog 2.1, build-plan §3.4). PNRs at rest are encrypted:
-- entity_encrypted is an AES-GCM blob, entity_key an HMAC digest (FR-4.3).
CREATE TABLE watch (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('chart', 'delay', 'platform', 'cancel', 'arrival')),
  entity_key TEXT NOT NULL,
  entity_encrypted TEXT NOT NULL,
  params JSONB NOT NULL DEFAULT '{}',
  state_hash TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX watch_entity_key_idx ON watch (entity_key);
CREATE INDEX watch_expires_at_idx ON watch (expires_at);
-- One watch per (device, type, entity): re-creating replaces params.
CREATE UNIQUE INDEX watch_dedup_idx ON watch (device_id, type, entity_key);

-- FCM push registration (contracts §5; used by fan-out in 2.3).
CREATE TABLE device_push_token (
  device_id TEXT PRIMARY KEY,
  fcm_token TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
