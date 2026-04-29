CREATE TABLE materials (
    id BINARY(16) NOT NULL PRIMARY KEY,
    name VARCHAR(160) NOT NULL UNIQUE,
    category VARCHAR(32) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    circular_grade VARCHAR(1) NOT NULL,
    stock DECIMAL(14,3) NOT NULL,
    supplier VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE templates (
    id BINARY(16) NOT NULL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    sku VARCHAR(80) NOT NULL UNIQUE,
    expected_waste_kg DECIMAL(14,3) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE template_lines (
    id BINARY(16) NOT NULL PRIMARY KEY,
    template_id BINARY(16) NOT NULL,
    material_id BINARY(16) NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    CONSTRAINT fk_tl_template FOREIGN KEY (template_id) REFERENCES templates (id) ON DELETE CASCADE,
    CONSTRAINT fk_tl_material FOREIGN KEY (material_id) REFERENCES materials (id)
);

CREATE TABLE batches (
    id BINARY(16) NOT NULL PRIMARY KEY,
    template_name VARCHAR(160) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    output_units DECIMAL(14,3) NOT NULL,
    waste_kg DECIMAL(14,3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    closed_at DATETIME(6),
    closed_by VARCHAR(120),
    close_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE inventory_logs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    batch_id BINARY(16),
    material_name VARCHAR(160) NOT NULL,
    type VARCHAR(8) NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    source VARCHAR(32) NOT NULL,
    timestamp DATETIME(6) NOT NULL,
    CONSTRAINT fk_il_batch FOREIGN KEY (batch_id) REFERENCES batches (id)
);

CREATE TABLE waste_logs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    batch_id BINARY(16) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    quantity_kg DECIMAL(14,3) NOT NULL,
    destination VARCHAR(16) NOT NULL,
    recovery_status VARCHAR(24) NOT NULL,
    ai_suggested_action VARCHAR(500),
    timestamp DATETIME(6) NOT NULL,
    CONSTRAINT fk_wl_batch FOREIGN KEY (batch_id) REFERENCES batches (id)
);

CREATE TABLE activity_logs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    entity VARCHAR(32) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor VARCHAR(160) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    timestamp DATETIME(6) NOT NULL
);

CREATE TABLE audit_trail (
    id BINARY(16) NOT NULL PRIMARY KEY,
    entity VARCHAR(32) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    field VARCHAR(64) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    actor VARCHAR(160) NOT NULL,
    timestamp DATETIME(6) NOT NULL
);

CREATE TABLE red_flags (
    id BINARY(16) NOT NULL PRIMARY KEY,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    resolved BOOLEAN NOT NULL,
    related_batch_id BINARY(16),
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6)
);

CREATE TABLE insights (
    id BINARY(16) NOT NULL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(1500) NOT NULL,
    impact_category VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    timestamp DATETIME(6) NOT NULL
);

CREATE TABLE anomalies (
    id BINARY(16) NOT NULL PRIMARY KEY,
    process VARCHAR(120) NOT NULL,
    z_score DECIMAL(8,2) NOT NULL,
    waste_kg DECIMAL(14,3) NOT NULL,
    date VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    timestamp DATETIME(6) NOT NULL
);

CREATE TABLE user_settings (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id VARCHAR(160) NOT NULL UNIQUE,
    company VARCHAR(200) NOT NULL,
    email VARCHAR(160) NOT NULL,
    role VARCHAR(80) NOT NULL,
    daily_token_budget INT NOT NULL,
    notify_anomalies BOOLEAN NOT NULL,
    notify_recommendations BOOLEAN NOT NULL,
    notify_overdue_batches BOOLEAN NOT NULL
);

CREATE TABLE ai_usage_logs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id VARCHAR(120) NOT NULL,
    feature VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    total_tokens INT NOT NULL,
    success BOOLEAN NOT NULL,
    error_code VARCHAR(500),
    timestamp DATETIME(6) NOT NULL
);

CREATE TABLE ai_job_runs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    job_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6),
    error_message VARCHAR(500)
);
