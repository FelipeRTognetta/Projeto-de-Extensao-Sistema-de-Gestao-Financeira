package com.psychologist.financial.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Room database migrations.
 *
 * Tests verify schema changes and data integrity across migration boundaries.
 * Must be run on a connected device or emulator.
 *
 * Example:
 * ```bash
 * ./gradlew connectedDebugAndroidTest --tests MigrationTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Test migration from database version 1 to 2 (existing).
     *
     * This migration adds patient CPF, address, and payer_info table.
     * Not the focus of this feature but verify it doesn't break.
     */
    @Test
    fun migration1_2_success() {
        val db1 = helper.createDatabase(TEST_DB, 1)
        db1.close()

        val db2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db2.close()
    }

    /**
     * Test migration from database version 2 to 3 (this feature).
     *
     * Verifies:
     * - payments table no longer has appointment_id, status, payment_method columns
     * - payment_appointments junction table is created
     * - Existing appointment links migrated to junction table
     */
    @Test
    fun migration2_3_removesPaymentFields_createsJunctionTable() {
        // 1. Create database at version 2
        val db2 = helper.createDatabase(TEST_DB, 2)

        // 2. Verify payments table exists and has expected columns at v2
        val paymentsCursorV2 = db2.query("PRAGMA table_info(payments)")
        val columnsV2 = mutableSetOf<String>()
        while (paymentsCursorV2.moveToNext()) {
            columnsV2.add(paymentsCursorV2.getString(1))  // column index 1 = column name
        }
        paymentsCursorV2.close()

        // At v2, payments should have these columns
        assert(columnsV2.contains("id"))
        assert(columnsV2.contains("patient_id"))
        assert(columnsV2.contains("amount"))
        assert(columnsV2.contains("status"))  // exists at v2
        assert(columnsV2.contains("payment_method"))  // exists at v2
        assert(columnsV2.contains("appointment_id"))  // exists at v2
        assert(columnsV2.contains("payment_date"))
        assert(columnsV2.contains("created_date"))

        db2.close()

        // 3. Run migration to v3
        val db3 = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        // 4. Verify payments table at v3: removed 3 columns
        val paymentsCursorV3 = db3.query("PRAGMA table_info(payments)")
        val columnsV3 = mutableSetOf<String>()
        while (paymentsCursorV3.moveToNext()) {
            columnsV3.add(paymentsCursorV3.getString(1))
        }
        paymentsCursorV3.close()

        assert(!columnsV3.contains("appointment_id"), "appointment_id should be removed")
        assert(!columnsV3.contains("status"), "status should be removed")
        assert(!columnsV3.contains("payment_method"), "payment_method should be removed")
        assert(columnsV3.contains("id"))
        assert(columnsV3.contains("patient_id"))
        assert(columnsV3.contains("amount"))
        assert(columnsV3.contains("payment_date"))
        assert(columnsV3.contains("created_date"))

        // 5. Verify payment_appointments junction table exists
        val junctionCursor = db3.query("PRAGMA table_info(payment_appointments)")
        val junctionColumns = mutableSetOf<String>()
        while (junctionCursor.moveToNext()) {
            junctionColumns.add(junctionCursor.getString(1))
        }
        junctionCursor.close()

        assert(junctionColumns.contains("payment_id"), "junction table should have payment_id")
        assert(junctionColumns.contains("appointment_id"), "junction table should have appointment_id")

        // 6. Verify indices exist
        val indexesCursor = db3.query(
            """SELECT name FROM sqlite_master
               WHERE type='index' AND tbl_name='payment_appointments'"""
        )
        val indexNames = mutableSetOf<String>()
        while (indexesCursor.moveToNext()) {
            indexNames.add(indexesCursor.getString(0))
        }
        indexesCursor.close()

        assert(
            indexNames.any { it.contains("pa_payment_id") },
            "Should have index on payment_id"
        )
        assert(
            indexNames.any { it.contains("pa_appointment_id") },
            "Should have index on appointment_id"
        )

        db3.close()
    }

    /**
     * Test data integrity during migration.
     *
     * Verifies that existing payment data (amount, date, patient) is preserved.
     */
    @Test
    fun migration2_3_preservesPaymentData() {
        val db2 = helper.createDatabase(TEST_DB, 2)

        // Insert test data at v2
        db2.execSQL(
            """INSERT INTO patient (id, name, phone, email, status, initial_consult_date,
               registration_date, last_appointment_date)
               VALUES (1, 'Test Patient', '11999999999', 'test@example.com', 'ACTIVE',
               '2024-01-01', '2024-01-01', '2024-03-01')"""
        )

        db2.execSQL(
            """INSERT INTO appointments (id, patient_id, date, time_start, duration_minutes,
               notes, created_date)
               VALUES (1, 1, '2024-02-15', '14:30:00', 60, 'Session note', '2024-02-15 14:30:00')"""
        )

        db2.execSQL(
            """INSERT INTO payments (id, patient_id, appointment_id, amount, status,
               payment_method, payment_date, created_date)
               VALUES (1, 1, 1, '150.00', 'PAID', 'TRANSFER', '2024-02-15', '2024-02-15 15:00:00')"""
        )

        db2.close()

        // Run migration
        val db3 = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        // Verify payment data preserved (without appointment_id, status, method)
        val paymentCursor = db3.query(
            """SELECT id, patient_id, amount, payment_date
               FROM payments WHERE id = 1"""
        )
        assert(paymentCursor.moveToFirst(), "Payment should exist after migration")
        assert(paymentCursor.getLong(0) == 1L, "Payment ID should be 1")
        assert(paymentCursor.getLong(1) == 1L, "Patient ID should be 1")
        assert(paymentCursor.getString(2) == "150.00", "Amount should be preserved")
        assert(paymentCursor.getString(3) == "2024-02-15", "Payment date should be preserved")
        paymentCursor.close()

        // Verify junction row created from existing appointment_id
        val junctionCursor = db3.query(
            """SELECT payment_id, appointment_id
               FROM payment_appointments WHERE payment_id = 1"""
        )
        assert(junctionCursor.moveToFirst(), "Junction row should exist for migrated data")
        assert(junctionCursor.getLong(0) == 1L, "Junction payment_id should be 1")
        assert(junctionCursor.getLong(1) == 1L, "Junction appointment_id should be 1")
        junctionCursor.close()

        db3.close()
    }
}
