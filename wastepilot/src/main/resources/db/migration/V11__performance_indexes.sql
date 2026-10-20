-- V11: Performance indexes for hot query paths
-- Rationale for each index follows. FK columns on inventory_logs.batch_id and waste_logs.batch_id
-- already have implicit indexes from the FK constraints in V1 (MySQL creates an index automatically
-- on the referencing column of a FK). No duplicate FK-column indexes are added here.

-- -----------------------------------------------------------------------
-- 1. batches(status, started_at)
--    Hot paths: floor-view (WHERE status='running' ORDER BY started_at DESC),
--               pattern-review & overdue check (WHERE status='completed' ORDER BY started_at DESC).
--    A composite index lets MySQL satisfy both the equality filter on status and the sort in one
--    index range scan, avoiding a filesort on the full batches table.
-- -----------------------------------------------------------------------
CREATE INDEX idx_batches_status_started_at
    ON batches (status, started_at DESC);

-- -----------------------------------------------------------------------
-- 2. waste_logs(material_name, timestamp)
--    Hot path: anomaly detection rolling-window query
--    (WHERE timestamp BETWEEN ? AND ? ... filter material_name in app code).
--    Composite (material_name, timestamp) lets MySQL do an index range scan on just the
--    relevant material rows within the time window, avoiding a full table scan + app-side filter.
--    The anomaly query already filters by timestamp range; placing material_name first here also
--    covers the common case where a single material is queried.
-- -----------------------------------------------------------------------
CREATE INDEX idx_waste_logs_material_timestamp
    ON waste_logs (material_name, timestamp);

-- -----------------------------------------------------------------------
-- 3. waste_logs(timestamp)
--    Hot path: dashboard 90-day rolling window query
--    (WHERE timestamp > ? ORDER BY timestamp DESC).
--    Note: the composite index above (material_name, timestamp) will NOT be used for
--    timestamp-only range scans without a material_name prefix. A dedicated timestamp index
--    is needed for the dashboard and analytics time-bounded loads.
-- -----------------------------------------------------------------------
CREATE INDEX idx_waste_logs_timestamp
    ON waste_logs (timestamp);

-- -----------------------------------------------------------------------
-- 4. inventory_logs(timestamp)
--    Hot path: dashboard 90-day rolling window query and anomaly rolling-window query.
--    Same reasoning as idx_waste_logs_timestamp above.
-- -----------------------------------------------------------------------
CREATE INDEX idx_inventory_logs_timestamp
    ON inventory_logs (timestamp);

-- -----------------------------------------------------------------------
-- 5. inventory_logs(type, batch_id)
--    Hot path: findByBatch_IdInAndType (cross-validation, pattern-review IN-log batch-fetch).
--    The FK index on batch_id alone exists, but a composite (type, batch_id) covering the
--    frequent WHERE type='IN' AND batch_id IN (...) filter avoids a full batch_id index scan
--    followed by a row filter on type.
-- -----------------------------------------------------------------------
CREATE INDEX idx_inventory_logs_type_batch
    ON inventory_logs (type, batch_id);

-- -----------------------------------------------------------------------
-- 6. red_flags(related_batch_id, resolved)
--    Hot path: findByRelatedBatchIdAndResolvedFalse (called in buildSummaryFlags per batch close).
--    Allows MySQL to seek directly to open flags for a given batch.
-- -----------------------------------------------------------------------
CREATE INDEX idx_red_flags_batch_resolved
    ON red_flags (related_batch_id, resolved);
