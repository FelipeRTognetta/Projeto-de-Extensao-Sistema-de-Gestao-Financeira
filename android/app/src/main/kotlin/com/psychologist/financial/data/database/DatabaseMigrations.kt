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
