CREATE TABLE password_reset_tokens (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES auth_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_prt_token_hash ON password_reset_tokens (token_hash);
