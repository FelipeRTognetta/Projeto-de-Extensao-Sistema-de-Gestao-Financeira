-- Database Migration: Create Patient Table
-- Version: 1 (Initial Schema)
-- Date: 2026-02-25
-- Description: Create patient table with all indexes and constraints

-- ============================================================
-- Create Patient Table
-- ============================================================

CREATE TABLE IF NOT EXISTS patient (
    -- Primary Key
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    -- Patient Information
    name TEXT NOT NULL,
    phone TEXT UNIQUE,
    email TEXT UNIQUE,

    -- Status for soft delete
    status TEXT NOT NULL DEFAULT 'ACTIVE',

    -- Key Dates
    initial_consult_date TEXT NOT NULL,
    registration_date TEXT NOT NULL,
    last_appointment_date TEXT,

    -- Audit Timestamp
    created_date TEXT NOT NULL
);

-- ============================================================
-- Create Indexes
-- ============================================================

-- Unique indexes for contact fields (enforces uniqueness)
-- Allows null values (UNIQUE in SQLite treats null as unique)
CREATE UNIQUE INDEX IF NOT EXISTS idx_patient_phone
    ON patient(phone);

CREATE UNIQUE INDEX IF NOT EXISTS idx_patient_email
    ON patient(email);

-- Composite index for filtering active patients by registration date
-- Used in queries: WHERE status = 'ACTIVE' AND registration_date >= ?
CREATE INDEX IF NOT EXISTS idx_patient_status_regdate
    ON patient(status, registration_date);

-- Index for "most recently active patients" query
-- Used in queries: ORDER BY last_appointment_date DESC
CREATE INDEX IF NOT EXISTS idx_patient_last_appt
    ON patient(last_appointment_date);

-- ============================================================
-- Notes
-- ============================================================
--
-- Column Descriptions:
--   id: Auto-increment primary key, never null, unique
--   name: Patient's full name, required (2-200 chars)
--   phone: Contact phone (optional, unique)
--   email: Contact email (optional, unique)
--   status: 'ACTIVE' or 'INACTIVE', default 'ACTIVE'
--   initial_consult_date: ISO 8601 date (YYYY-MM-DD)
--   registration_date: ISO 8601 date (YYYY-MM-DD), immutable
--   last_appointment_date: ISO 8601 date (YYYY-MM-DD), nullable
--   created_date: ISO 8601 timestamp (YYYY-MM-DDTHH:mm:ss), immutable
--
-- Data Types:
--   TEXT: Stored as UTF-8 text (dates stored as ISO 8601 strings)
--   INTEGER: 64-bit signed integer (IDs, constraints)
--   NULL: Allowed for optional fields
--
-- Constraints:
--   PRIMARY KEY: id is unique and not null
--   NOT NULL: name, status, initial_consult_date, registration_date, created_date
--   UNIQUE: phone (allows null), email (allows null)
--   DEFAULT: status defaults to 'ACTIVE'
--
-- Soft Delete Pattern:
--   No permanent deletion of patient records
--   Instead, mark as INACTIVE via status field
--   Maintains data integrity and audit trail
--
-- Foreign Key References:
--   appointment.patient_id -> patient.id (one-to-many)
--   payment.patient_id -> patient.id (one-to-many)
--
-- Performance Notes:
--   Indexes on phone, email enable fast uniqueness checks
--   Composite index on (status, registration_date) optimizes filtering
--   Index on last_appointment_date enables "recent patients" sorting
--   No covering indexes (would duplicate data storage)
