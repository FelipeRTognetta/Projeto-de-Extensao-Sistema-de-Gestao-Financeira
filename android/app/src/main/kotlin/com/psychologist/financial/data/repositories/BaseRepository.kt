package com.psychologist.financial.data.repositories

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.psychologist.financial.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Abstract base repository providing common data access patterns
 *
 * Responsibilities:
 * - Transaction management for multi-step database operations
 * - Error handling and logging
 * - Coroutine context management (IO thread pool)
 * - Query result null-safety
 *
 * Usage:
 * ```kotlin
 * class PatientRepository(private val db: AppDatabase) : BaseRepository(db) {
 *     suspend fun getPatient(id: Long): Patient = withTransaction {
 *         db.patientDao().getPatient(id)
 *     }
 *
 *     suspend fun createPatient(patient: Patient) = withTransaction {
 *         db.patientDao().insert(patient)
 *     }
 * }
 * ```
 */
abstract class BaseRepository(protected val database: AppDatabase) {

    private companion object {
        private const val TAG = "BaseRepository"
    }

    /**
     * Execute a database operation within a transaction
     *
     * Benefits:
     * - Atomicity: All operations succeed or all rollback
     * - Consistency: Database is never in an inconsistent state
     * - Isolation: Operations don't interfere with each other
     * - Durability: Committed changes survive crashes
     *
     * Runs on IO thread pool (Dispatchers.IO) for non-blocking database access.
     *
     * @param T Result type
     * @param block Suspend lambda containing database operations
     * @return Result of the transaction
     *
     * @throws Exception Re-throws database errors from the block
     *
     * Example:
     * ```kotlin
     * val result = withTransaction {
     *     val patient = db.patientDao().getPatient(1)
     *     db.appointmentDao().insertAll(listOf(...))
     *     patient
     * }
     * ```
     */
    protected suspend fun <T> withTransaction(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            try {
                database.withTransaction {
                    block()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transaction failed", e)
                throw e
            }
        }
    }

    /**
     * Execute a read-only database operation
     *
     * Runs on IO thread pool without transaction overhead.
     * Use for queries that don't modify data.
     *
     * @param T Result type
     * @param block Suspend lambda containing read-only operations
     * @return Result of the query
     *
     * @throws Exception Re-throws database errors from the block
     *
     * Example:
     * ```kotlin
     * val patients = withRead {
     *     db.patientDao().getAllPatients()
     * }
     * ```
     */
    protected suspend fun <T> withRead(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Query failed", e)
                throw e
            }
        }
    }

    /**
     * Execute a single insert/update/delete operation
     *
     * Convenience method for simple single-entity operations.
     * For multi-step operations, use [withTransaction] instead.
     *
     * @param block Suspend lambda containing write operation
     *
     * Example:
     * ```kotlin
     * withWrite {
     *     db.patientDao().insert(newPatient)
     * }
     * ```
     */
    protected suspend fun withWrite(block: suspend () -> Unit) {
        withTransaction { block() }
    }

    /**
     * Get direct database access for advanced operations
     *
     * WARNING: Use only for complex operations that require direct SQL.
     * For standard operations, use DAO methods.
     *
     * @return SupportSQLiteDatabase instance
     */
    protected fun getRawDatabase(): SupportSQLiteDatabase {
        return database.openHelper.writableDatabase
    }

    /**
     * Null-safe query result handling
     *
     * Provides consistent error messaging for null results.
     *
     * @param T Type of result
     * @param result Query result (may be null)
     * @param entityName Name of entity being queried (for error message)
     * @return Result or throws exception if null
     *
     * @throws IllegalStateException If result is null
     */
    protected fun <T> requireNotNull(result: T?, entityName: String): T {
        return result ?: throw IllegalStateException(
            "$entityName not found in database"
        )
    }
}
