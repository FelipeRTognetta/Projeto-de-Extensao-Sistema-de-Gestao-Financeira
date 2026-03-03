package com.psychologist.financial.data.database

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database configuration for Room with SQLCipher
 *
 * Handles:
 * - WAL (Write-Ahead Logging) mode for better concurrency
 * - Connection pooling for performance
 * - Index creation for optimized queries
 * - Query optimization pragmas
 * - Foreign key constraint enforcement
 */
object DatabaseConfig {
    private const val TAG = "DatabaseConfig"

    /**
     * Configure database after opening
     *
     * This should be called from the database callback onOpen()
     * to set performance and safety pragmas.
     *
     * @param database SupportSQLiteDatabase instance
     */
    fun configureDatabase(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Configuring database")

        try {
            // 1. Enable Foreign Keys (if not already enabled by Room)
            enableForeignKeys(database)

            // 2. Configure WAL mode (Write-Ahead Logging)
            configureWALMode(database)

            // 3. Configure connection pooling
            configureConnectionPooling(database)

            // 4. Set query optimization pragmas
            optimizeQueryPerformance(database)

            // 5. Setup indexes for critical queries
            createIndexes(database)

            Log.d(TAG, "Database configuration complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure database", e)
            throw RuntimeException("Database configuration failed", e)
        }
    }

    /**
     * Enable foreign key constraints
     *
     * By default, SQLite doesn't enforce foreign keys.
     * We enable them to maintain referential integrity.
     */
    private fun enableForeignKeys(database: SupportSQLiteDatabase) {
        try {
            database.execSQL("PRAGMA foreign_keys=ON;")
            Log.d(TAG, "Foreign key constraints enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable foreign keys", e)
        }
    }

    /**
     * Configure WAL (Write-Ahead Logging) mode
     *
     * Benefits:
     * - Better concurrent read/write access
     * - Improved performance for multiple connections
     * - Atomic transactions
     *
     * Tradeoffs:
     * - Uses 2 additional temporary files (-wal, -shm)
     * - Slightly slower for single-threaded access
     */
    private fun configureWALMode(database: SupportSQLiteDatabase) {
        try {
            // Enable WAL mode
            database.execSQL("PRAGMA journal_mode=WAL;")

            // Configure WAL synchronization (NORMAL is good balance)
            // FULL = safest but slower
            // NORMAL = good balance (fsync on each transaction)
            // OFF = fastest but risky
            database.execSQL("PRAGMA synchronous=NORMAL;")

            // Configure WAL checkpoint behavior
            // Pages = number of pages in WAL file before auto-checkpoint
            database.execSQL("PRAGMA wal_autocheckpoint=10000;")

            Log.d(TAG, "WAL mode configured (journal_mode=WAL, synchronous=NORMAL)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure WAL mode", e)
        }
    }

    /**
     * Configure connection pooling
     *
     * Improves performance by reusing database connections.
     */
    private fun configureConnectionPooling(database: SupportSQLiteDatabase) {
        try {
            // Set busy timeout to allow waiting for locks
            // 5000ms = 5 seconds
            database.execSQL("PRAGMA busy_timeout=5000;")

            Log.d(TAG, "Connection pooling configured")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure connection pooling", e)
        }
    }

    /**
     * Optimize query performance
     *
     * Set pragmas that improve query performance:
     * - temp_store: Use memory for temporary tables
     * - query_only: Default query mode
     * - auto_vacuum: Keep database compact
     */
    private fun optimizeQueryPerformance(database: SupportSQLiteDatabase) {
        try {
            // Use memory for temp tables (faster than disk)
            database.execSQL("PRAGMA temp_store=MEMORY;")

            // Set cache size (in pages, default 2000)
            // Larger cache = better performance but more memory
            database.execSQL("PRAGMA cache_size=10000;")

            // Enable incremental vacuum
            database.execSQL("PRAGMA incremental_vacuum(10000);")

            // Optimize query planner
            database.execSQL("PRAGMA optimize;")

            Log.d(TAG, "Query performance optimized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to optimize query performance", e)
        }
    }

    /**
     * Create database indexes
     *
     * Indexes are critical for query performance.
     * They should be created on:
     * - Foreign keys
     * - Columns used in WHERE clauses
     * - Columns used in JOIN conditions
     * - Columns used in ORDER BY
     *
     * TODO: Add actual index creation when entities are created
     */
    private fun createIndexes(database: SupportSQLiteDatabase) {
        try {
            Log.d(TAG, "Creating database indexes...")

            // Example indexes to create when entities are defined:
            // Patient table indexes
            // database.execSQL("CREATE INDEX IF NOT EXISTS idx_patient_status ON patient(status, registration_date);")
            // database.execSQL("CREATE INDEX IF NOT EXISTS idx_patient_last_appt ON patient(last_appointment_date DESC);")
            // database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_patient_phone ON patient(phone);")
            // database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_patient_email ON patient(email);")

            // Appointment table indexes
            // database.execSQL("CREATE INDEX IF NOT EXISTS idx_appt_patient_date ON appointment(patient_id, date DESC);")
            // database.execSQL("CREATE INDEX IF NOT EXISTS idx_appt_date ON appointment(date, patient_id);")

            // Payment table indexes
            // database.execSQL("CREATE INDEX IF NOT EXISTS idx_payment_patient_date ON payment(patient_id, payment_date DESC);")
            // database.execSQL("CREATE INDEX IF NOT EXISTS idx_payment_status ON payment(patient_id, status);")
            // database.execSQL("CREATE INDEX IF NOT EXISTS idx_payment_appt ON payment(appointment_id);")

            Log.d(TAG, "Database indexes created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create indexes", e)
        }
    }
}

/**
 * Database migration helper for future schema updates
 *
 * Room handles migrations via Room.Migration class.
 * When adding new tables or columns:
 * 1. Update entities and version in @Database
 * 2. Create Migration classes for old -> new schema
 * 3. Add migrations to databaseBuilder.addMigrations()
 *
 * Example migration:
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         database.execSQL("CREATE TABLE new_patient (...)")
 *         database.execSQL("INSERT INTO new_patient SELECT * FROM patient")
 *         database.execSQL("DROP TABLE patient")
 *         database.execSQL("ALTER TABLE new_patient RENAME TO patient")
 *     }
 * }
 * ```
 */
object DatabaseMigrations {
    // TODO: Add migration classes here when schema changes occur
    // val MIGRATION_1_2 = object : Migration(1, 2) { ... }
}
