CREATE TABLE listening_authorization (
    request_id UUID PRIMARY KEY,
    label VARCHAR(100) NOT NULL CHECK (length(label) BETWEEN 1 AND 100),
    code_digest VARCHAR(64) NOT NULL UNIQUE CHECK (code_digest ~ '^[0-9a-f]{64}$'),
    proof_digest VARCHAR(64) NOT NULL CHECK (proof_digest ~ '^[0-9a-f]{64}$'),
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'APPROVED', 'DENIED', 'CONSUMED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() + interval '10 minutes',
    last_poll_at TIMESTAMPTZ,
    valid_days INTEGER NOT NULL DEFAULT 30 CHECK (valid_days BETWEEN 1 AND 90)
);
CREATE INDEX listening_authorization_created ON listening_authorization(created_at);
CREATE TABLE listening_authorization_budget (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    window_start TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    attempts INTEGER NOT NULL DEFAULT 0
);
INSERT INTO listening_authorization_budget(singleton) VALUES (1);
COMMENT ON TABLE listening_authorization IS 'Short-lived pairing requests. Only digests are stored. Consumed request_id identifies the issued listening_device.';
COMMENT ON TABLE listening_authorization_budget IS 'Global administrator code-lookup budget; committed also on unsuccessful lookup.';
