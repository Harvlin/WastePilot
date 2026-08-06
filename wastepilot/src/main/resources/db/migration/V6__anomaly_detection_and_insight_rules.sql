ALTER TABLE anomalies
  ADD COLUMN batch_id BINARY(16),
  ADD COLUMN material_name VARCHAR(160);

CREATE UNIQUE INDEX idx_anomaly_dedup ON anomalies (batch_id, material_name, date, status);

ALTER TABLE insights
  ADD COLUMN rule_id VARCHAR(64);
