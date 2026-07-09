-- Notification prefs + delivery log (backlog 2.3, FR-7.4).
CREATE TABLE device_prefs (
  device_id TEXT PRIMARY KEY,
  quiet_start_hour SMALLINT,               -- IST hour, null = no quiet hours
  quiet_end_hour SMALLINT,
  muted_kinds TEXT[] NOT NULL DEFAULT '{}', -- push kinds opted out
  muted_entity_keys TEXT[] NOT NULL DEFAULT '{}',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Delivery log powers the §2 metric: chart push ≤ 5 min at ≥ 95%.
CREATE TABLE push_delivery_log (
  id BIGSERIAL PRIMARY KEY,
  watch_id UUID,
  device_id TEXT NOT NULL,
  kind TEXT NOT NULL,
  entity_key TEXT NOT NULL,
  signature TEXT NOT NULL,
  detected_at TIMESTAMPTZ NOT NULL,        -- when the poll observed the change
  delivered_at TIMESTAMPTZ,                -- null when suppressed/failed
  latency_ms INTEGER,
  status TEXT NOT NULL                     -- 'delivered' | 'suppressed:*' | 'failed' | 'token_invalid'
);

CREATE INDEX push_delivery_log_kind_idx ON push_delivery_log (kind, detected_at);
