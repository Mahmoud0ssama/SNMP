-- ===========================
-- 1. USERS TABLE
-- Created for authentication and Role-Based Access Control (RBAC)
-- ===========================
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'SUPPORT', -- Expected values: 'ADMIN' or 'SUPPORT'
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===========================
-- 2. ALTER TRAP_HISTORY TABLE
-- Add audit trail to track which user resolved the issue
-- Made nullable to prevent breaking existing INSERT statements in TrapHistoryDAO
-- ===========================
ALTER TABLE trap_history 
ADD COLUMN resolved_by BIGINT,
ADD CONSTRAINT fk_history_resolver 
    FOREIGN KEY (resolved_by) 
    REFERENCES users(id) 
    ON DELETE SET NULL;

-- ===========================
-- 3. ALTER TRAP_ACTIONS TABLE
-- Add execution details required for the Action Engine (Email, SMS, Script)
-- Made nullable to prevent breaking existing SELECT statements in TrapActionDAO
-- ===========================
ALTER TABLE trap_actions
ADD COLUMN action_type VARCHAR(20),    -- Expected values: 'EMAIL', 'SMS', 'SCRIPT'
ADD COLUMN target_payload VARCHAR(255); -- Example: 'admin@company.com' or '/opt/scripts/restart.sh'
