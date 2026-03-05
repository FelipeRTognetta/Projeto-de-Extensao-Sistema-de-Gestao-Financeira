package com.psychologist.financial.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations for Financial Management System.
 *
 * Each migration object handles schema changes between consecutive database versions.
 * Migrations preserve all existing data by using ALTER TABLE and CREATE TABLE statements
 * rather than destructive recreation.
 *
 * Usage in AppDatabase:
 * ```kotlin
 * Room.databaseBuilder(...)
 *     .addMigrations(MIGRATION_1_2)
 *     .build()
 * ```
 */

/**
 * Migration from database version 1 to 2.
 *
 * Changes introduced:
 * - patient table: adds cpf (TEXT, nullable, unique when non-null)
 * - patient table: adds endereco (TEXT, nullable)
 * - patient table: adds nao_pagante (INTEGER, NOT NULL, default 0)
 * - Creates unique partial index on patient.cpf (WHERE cpf IS NOT NULL)
 * - Creates new payer_info table with FK to patient (1:0..1 relationship, CASCADE DELETE)
 *
 * Backward compatibility:
 * - All new patient columns are nullable or have defaults — existing rows unaffected.
 * - payer_info table starts empty — no existing data loss.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new nullable fields to existing patient table
        db.execSQL("ALTER TABLE patient ADD COLUMN cpf TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE patient ADD COLUMN endereco TEXT DEFAULT NULL")
        db.execSQL(
            "ALTER TABLE patient ADD COLUMN nao_pagante INTEGER NOT NULL DEFAULT 0"
        )

        // Unique index for CPF: SQLite treats each NULL as distinct, so multiple
        // patients with NULL cpf are allowed even with UNIQUE constraint.
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_patient_cpf ON patient (cpf)"
        )

        // New table for Responsável Financeiro (payer linked to a non-paying patient).
        // UNIQUE on patient_id enforces the 1:0..1 relationship.
        // ON DELETE CASCADE ensures the payer record is removed when the patient is deleted.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS payer_info (
                id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                patient_id  INTEGER NOT NULL UNIQUE,
                nome        TEXT NOT NULL,
                cpf         TEXT,
                endereco    TEXT,
                email       TEXT,
                telefone    TEXT,
                FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_payer_info_patient_id
            ON payer_info (patient_id)
            """.trimIndent()
        )
    }
}

/**
 * Migration from database version 2 to 3.
 *
 * Changes introduced:
 * - payments table: removes appointment_id column (FK to single appointment — replaced by many-to-many)
 * - payments table: removes status column (all payments are now PAID; no need for status field)
 * - payments table: removes payment_method column (business rule simplification)
 * - Creates payment_appointments junction table for many-to-many relationship
 * - Recreates payments indices without status-related indices
 *
 * Data migration:
 * - Existing appointment links (payment.appointment_id) are migrated to payment_appointments rows
 * - Payment amounts and dates preserved exactly
 *
 * Implementation note:
 * - SQLite does not support DROP COLUMN reliably on Android (SQLite < 3.35.0)
 * - Instead, recreate the table with new schema, copy data, rename
 * - Transaction ensures atomicity
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Create payment_appointments junction table
        // Must exist BEFORE we migrate data from payments.appointment_id
        db.execSQL(
            """
            CREATE TABLE payment_appointments (
                payment_id      INTEGER NOT NULL,
                appointment_id  INTEGER NOT NULL,
                PRIMARY KEY (payment_id, appointment_id),
                FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE ON UPDATE CASCADE,
                FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_pa_payment_id ON payment_appointments (payment_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_pa_appointment_id ON payment_appointments (appointment_id)"
        )

        // Step 2: Migrate existing appointment links to junction table
        // Insert one row per payment that has an appointment_id
        db.execSQL(
            """
            INSERT INTO payment_appointments (payment_id, appointment_id)
            SELECT id, appointment_id FROM payments
            WHERE appointment_id IS NOT NULL
            """.trimIndent()
        )

        // Step 3: Recreate payments table without appointment_id, status, payment_method
        // Create new table with only the columns we need
        db.execSQL(
            """
            CREATE TABLE payments_new (
                id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                patient_id   INTEGER NOT NULL,
                amount       TEXT NOT NULL,
                payment_date TEXT NOT NULL,
                created_date TEXT NOT NULL,
                FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )

        // Copy all data from old table to new table (dropping the 3 removed columns)
        db.execSQL(
            """
            INSERT INTO payments_new (id, patient_id, amount, payment_date, created_date)
            SELECT id, patient_id, amount, payment_date, created_date FROM payments
            """.trimIndent()
        )

        // Step 4: Replace old table with new one
        db.execSQL("DROP TABLE payments")
        db.execSQL("ALTER TABLE payments_new RENAME TO payments")

        // Step 5: Recreate indices on the new table
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_payment_patient_id ON payments (patient_id)"
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_payment_patient_date
            ON payments (patient_id, payment_date)
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_payment_date ON payments (payment_date)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_payment_created_date ON payments (created_date)"
        )
    }
}
