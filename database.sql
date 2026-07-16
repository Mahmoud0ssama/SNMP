-- ===========================
-- ENUMS
-- ===========================

CREATE TYPE node_status AS ENUM (
    'UP', --Node is operating normally.
    'DOWN', --Node is unreachable or has a critical fault..
    'WARNING', --Node is reachable but has one or more active non-critical alarms.
    'UNKNOWN' --Initial state or communication lost.
);

CREATE TYPE trap_status AS ENUM (
    'OPEN', --Trap received and not yet handled.
    'ACKNOWLEDGED', --Trap has been seen but yet to be resolved.
    'RESOLVED' --issue has been resolved.
);

CREATE TYPE trap_severity AS ENUM (
    'INFO', --Informational message.
    'MINOR', --Minor issue that does not affect the node's operation.
    'MAJOR', --Significant issue that may affect the node's operation.
    'CRITICAL' --Critical issue that affects the node's operation and requires immediate attention.
);

-- ===========================
-- NODES
-- ===========================

CREATE TABLE nodes (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    ip_address      VARCHAR(45) NOT NULL UNIQUE,
    port            INTEGER NOT NULL DEFAULT 162,
    location        VARCHAR(255),
    description     TEXT,
    status          node_status NOT NULL DEFAULT 'UP',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===========================
-- TRAP ACTIONS
-- ===========================

CREATE TABLE trap_actions (
    id              BIGSERIAL PRIMARY KEY,
    trap_oid        VARCHAR(150) NOT NULL UNIQUE,
    trap_name       VARCHAR(100) NOT NULL,
    severity        trap_severity NOT NULL,
    action_name     VARCHAR(100) NOT NULL,
    description     TEXT,
    auto_resolve    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===========================
-- TRAP HISTORY
-- ===========================

CREATE TABLE trap_history (
    id                  BIGSERIAL PRIMARY KEY,
    node_id             BIGINT NOT NULL,
    trap_action_id      BIGINT,
    trap_oid            VARCHAR(150) NOT NULL,
    source_ip           VARCHAR(45) NOT NULL,
    message             TEXT,
    status              trap_status NOT NULL DEFAULT 'OPEN',
    received_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at         TIMESTAMP,

    CONSTRAINT fk_history_node
        FOREIGN KEY (node_id)
        REFERENCES nodes(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_history_action
        FOREIGN KEY (trap_action_id)
        REFERENCES trap_actions(id)
        ON DELETE SET NULL
);

-- ===========================
-- INDEXES
-- ===========================

CREATE INDEX idx_nodes_ip
ON nodes(ip_address);

CREATE INDEX idx_history_node
ON trap_history(node_id);

CREATE INDEX idx_history_status
ON trap_history(status);

CREATE INDEX idx_history_received
ON trap_history(received_at);

CREATE INDEX idx_history_oid
ON trap_history(trap_oid);

CREATE INDEX idx_actions_oid
ON trap_actions(trap_oid);