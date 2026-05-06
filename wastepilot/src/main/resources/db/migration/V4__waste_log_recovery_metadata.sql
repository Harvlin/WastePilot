ALTER TABLE waste_logs
  ADD COLUMN reason VARCHAR(500),
  ADD COLUMN is_repurposed BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN recovery_inventory_log_id BINARY(16),
  ADD COLUMN recovered_at DATETIME(6);
