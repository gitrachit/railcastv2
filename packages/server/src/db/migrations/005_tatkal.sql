-- Tatkal-open reminder watches (FR-6.4, contracts §5): widen the type CHECK.
ALTER TABLE watch DROP CONSTRAINT watch_type_check;
ALTER TABLE watch ADD CONSTRAINT watch_type_check
  CHECK (type IN ('chart', 'delay', 'platform', 'cancel', 'arrival', 'tatkal'));
