ALTER TABLE audit_trail ADD COLUMN chain_sequence BIGINT NULL;
ALTER TABLE audit_trail ADD COLUMN previous_hash VARCHAR(64) NULL;
ALTER TABLE audit_trail ADD COLUMN entry_hash VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_audit_trail_chain_sequence ON audit_trail (chain_sequence);

CREATE TABLE audit_chain_head (
    id BIGINT NOT NULL PRIMARY KEY,
    last_sequence BIGINT NOT NULL,
    last_hash VARCHAR(64) NOT NULL
);

INSERT INTO audit_chain_head (id, last_sequence, last_hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');
