-- Diff engine state (backlog 2.2). The previous normalized snapshot lives
-- per ENTITY (one poll serves all its watches); `delivered` dedups fired
-- events per watch so a standing condition (e.g. arrival due) fires once.
CREATE TABLE watch_entity_state (
  entity_key TEXT PRIMARY KEY,
  state JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE watch ADD COLUMN delivered TEXT[] NOT NULL DEFAULT '{}';
-- state_hash was a per-watch change flag; the diff engine supersedes it with
-- per-entity prev state + per-watch delivered signatures.
ALTER TABLE watch DROP COLUMN state_hash;
