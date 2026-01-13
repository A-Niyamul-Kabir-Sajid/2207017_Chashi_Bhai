-- Migration: Remove PIN from SQLite (Authentication is Firebase-only)
-- Date: 2026-01-13
-- Purpose: Remove authentication credentials from local database
--          All login credentials are stored in Firebase Auth only

-- Step 1: Backup existing data (optional)
-- CREATE TABLE users_backup AS SELECT * FROM users;

-- Step 2: Remove PIN column from users table
-- Note: SQLite doesn't support DROP COLUMN directly, so we need to recreate the table

-- Create new users table without PIN
CREATE TABLE users_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('farmer', 'buyer')),
    address TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(phone, role)
);

-- Copy data from old table (excluding PIN)
INSERT INTO users_new (id, phone, name, role, address, created_at)
SELECT id, phone, name, role, address, created_at FROM users;

-- Drop old table
DROP TABLE users;

-- Rename new table
ALTER TABLE users_new RENAME TO users;

-- Recreate indexes
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_role ON users(role);

-- Verify migration
SELECT 'Migration completed successfully. PIN column removed from users table.' AS status;
SELECT COUNT(*) AS total_users FROM users;
