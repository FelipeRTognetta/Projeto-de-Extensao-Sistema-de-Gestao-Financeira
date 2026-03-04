package com.psychologist.financial.data.database

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Instrumented tests for database migration from version 1 to version 2.
 *
 * Verifies that MIGRATION_1_2 correctly:
 * - Adds `cpf`, `endereco`, `nao_pagante` columns to the `patient` table
 * - Creates the `payer_info` table with the expected schema
 * - Preserves existing patient data (data migration safety)
 * - Applies correct default values for new columns
 *
 * Uses a plain in-memory SQLiteDatabase (not Room) to apply the migration
 * SQL statements directly, verifying schema changes without Room overhead.
 *
 * Run via: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        // Create a fresh in-memory SQLite database with v1 schema
        db = SQLiteDatabase.create(null)
        createVersion1Schema(db)
    }

    @After
    fun tearDown() {
        if (db.isOpen) db.close()
    }

    /**
     * Creates the version 1 patient table schema (before MIGRATION_1_2).
     * Must match the original CREATE TABLE in AppDatabase version 1.
     */
    private fun createVersion1Schema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS patient (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT,
                email TEXT,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                initial_consult_date TEXT NOT NULL,
                registration_date TEXT NOT NULL,
                last_appointment_date TEXT,
                created_date TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent()
        )
    }

    /**
     * Applies the MIGRATION_1_2 SQL statements to the given database.
     * Mirrors the SQL in DatabaseMigrations.MIGRATION_1_2.migrate().
     */
    private fun applyMigration1To2(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE patient ADD COLUMN cpf TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE patient ADD COLUMN endereco TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE patient ADD COLUMN nao_pagante INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_patient_cpf ON patient (cpf) WHERE cpf IS NOT NULL"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS payer_info (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                patient_id INTEGER NOT NULL UNIQUE,
                nome TEXT NOT NULL,
                cpf TEXT,
                endereco TEXT,
                email TEXT,
                telefone TEXT,
                FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_payer_info_patient_id ON payer_info (patient_id)"
        )
    }

    // ========================================
    // Column Tests
    // ========================================

    @Test
    fun migration1To2_addsCpfColumnToPatient() {
        applyMigration1To2(db)

        val cursor = db.rawQuery("SELECT cpf FROM patient", null)
        assertNotNull(cursor, "cpf column should exist after migration")
        cursor.close()
    }

    @Test
    fun migration1To2_addsEnderecoColumnToPatient() {
        applyMigration1To2(db)

        val cursor = db.rawQuery("SELECT endereco FROM patient", null)
        assertNotNull(cursor, "endereco column should exist after migration")
        cursor.close()
    }

    @Test
    fun migration1To2_addsNaoPaganteColumnToPatient() {
        applyMigration1To2(db)

        val cursor = db.rawQuery("SELECT nao_pagante FROM patient", null)
        assertNotNull(cursor, "nao_pagante column should exist after migration")
        cursor.close()
    }

    // ========================================
    // payer_info Table Tests
    // ========================================

    @Test
    fun migration1To2_createsPayerInfoTable() {
        applyMigration1To2(db)

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='payer_info'",
            null
        )
        assertTrue(cursor.moveToFirst(), "payer_info table should exist after migration")
        assertEquals("payer_info", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migration1To2_payerInfoTableHasExpectedColumns() {
        applyMigration1To2(db)

        // Verify payer_info can be inserted with expected columns
        db.execSQL(
            "INSERT INTO patient (name, status, initial_consult_date, registration_date) " +
                "VALUES ('Teste', 'ACTIVE', '2024-01-01', '2024-01-01')"
        )
        val patientId = db.rawQuery("SELECT id FROM patient WHERE name='Teste'", null).use {
            it.moveToFirst(); it.getLong(0)
        }

        db.execSQL(
            "INSERT INTO payer_info (patient_id, nome, cpf, endereco, email, telefone) " +
                "VALUES ($patientId, 'Responsável', '12345678909', 'Rua A', 'a@b.com', '11999999999')"
        )

        val cursor = db.rawQuery(
            "SELECT patient_id, nome, cpf FROM payer_info WHERE patient_id=$patientId",
            null
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(patientId, cursor.getLong(0))
        assertEquals("Responsável", cursor.getString(1))
        assertEquals("12345678909", cursor.getString(2))
        cursor.close()
    }

    // ========================================
    // Data Preservation Tests
    // ========================================

    @Test
    fun migration1To2_preservesExistingPatientData() {
        // Insert 2 patients BEFORE migration
        db.execSQL(
            "INSERT INTO patient (name, phone, status, initial_consult_date, registration_date) " +
                "VALUES ('Ana Silva', '11111111111', 'ACTIVE', '2024-01-01', '2024-01-01')"
        )
        db.execSQL(
            "INSERT INTO patient (name, phone, status, initial_consult_date, registration_date) " +
                "VALUES ('Bruno Costa', '22222222222', 'INACTIVE', '2024-02-01', '2024-02-01')"
        )

        applyMigration1To2(db)

        val cursor = db.rawQuery("SELECT COUNT(*) FROM patient", null)
        assertTrue(cursor.moveToFirst())
        assertEquals(2, cursor.getInt(0), "Both patients should be preserved after migration")
        cursor.close()
    }

    @Test
    fun migration1To2_existingPatientsGetNaoPaganteDefault() {
        // Insert patient before migration
        db.execSQL(
            "INSERT INTO patient (name, phone, status, initial_consult_date, registration_date) " +
                "VALUES ('Teste', '11999999999', 'ACTIVE', '2024-01-01', '2024-01-01')"
        )

        applyMigration1To2(db)

        val cursor = db.rawQuery("SELECT nao_pagante FROM patient WHERE name='Teste'", null)
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0), "Existing patients should have nao_pagante=0 (false) by default")
        cursor.close()
    }

    @Test
    fun migration1To2_existingPatientsGetNullCpfByDefault() {
        db.execSQL(
            "INSERT INTO patient (name, phone, status, initial_consult_date, registration_date) " +
                "VALUES ('Teste', '11999999999', 'ACTIVE', '2024-01-01', '2024-01-01')"
        )

        applyMigration1To2(db)

        val cursor = db.rawQuery("SELECT cpf FROM patient WHERE name='Teste'", null)
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.isNull(0), "Existing patients should have cpf=NULL by default")
        cursor.close()
    }
}
