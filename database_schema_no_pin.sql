-- Updated Database Schema (Firebase Auth Integration)
-- Date: 2026-01-13
-- Authentication: Firebase Auth only (no credentials in SQLite)

-- Users Table (Profile Data Only - NO PIN)
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('farmer', 'buyer')),
    address TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(phone, role)
);

CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_role ON users(role);

-- Note: PIN is stored ONLY in Firebase Auth
-- Firebase Auth email format: phone@chashi-bhai.app
-- Firebase Auth password format: CB_PIN_xxxx

-- Rest of the schema remains the same...
-- (crops, orders, conversations, messages, etc.)
