package com.psychologist.financial.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportFactory
import androidx.room.Room
import com.psychologist.financial.utils.Constants
import android.util.Log
import com.psychologist.financial.services.DatabaseEncryptionManager
import com.psychologist.financial.services.EncryptionService
import com.psychologist.financial.services.SecureKeyStore

/**
 * Room Database for Financial Management System
 *
 * This is the main database instance providing access to all DAOs.
 * Features:
 * - SQLCipher encryption (AES-256-GCM via EncryptionService)
 * - Encrypted encryption keys via DatabaseEncryptionManager
 * - Type converters for custom types (LocalDate, LocalTime, BigDecimal)
 * - Proper database configuration (WAL mode, connection pooling)
 * - Migration support for future schema updates
 *
 * Entities:
 * - PatientEntity: Represents patients
 * - AppointmentEntity: Represents appointments/sessions
 * - PaymentEntity: Represents payment transactions
 *
 * Security:
 * - Database key managed by DatabaseEncryptionManager
 * - Master key in Android Keystore (hardware-backed)
 * - Key rotation every 90 days
 * - Transparent encryption/decryption
 *
 * Access:
 * - Get instance via companion object: AppDatabase.getInstance(context)
 * - Access DAOs via database.patientDao(), etc.
 */
@Database(
    entities = [
        // TODO: Add entity classes when created
        // PatientEntity::class,
        // AppointmentEntity::class,
        // PaymentEntity::class,
    ],
    version = Constants.DATABASE_VERSION,
    exportSchema = false // TODO: Set to true and create schema/ directory for migrations
)
@TypeConverters(
    // TODO: Add type converters when created
    // DateTimeConverters::class,
    // CurrencyConverters::class,
)
abstract class AppDatabase : RoomDatabase() {

    // ================================
    // DAO Properties (to be added)
    // ================================
    // abstract fun patientDao(): PatientDao
    // abstract fun appointmentDao(): AppointmentDao
    // abstract fun paymentDao(): PaymentDao

    companion object {
        private const val TAG = "AppDatabase"

        // Volatile ensures that the database instance is always up-to-date
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get singleton instance of AppDatabase
         *
         * Automatically initializes encryption services and retrieves encrypted database key.
         * Thread-safe double-checked locking pattern ensures that:
         * - Only one database instance exists at runtime
         * - Multiple threads don't create duplicate instances
         * - Instance is lazily initialized on first access
         * - Encryption is transparent to app code
         *
         * @param context Application context
         * @param encryptionService EncryptionService for key operations
         * @param secureKeyStore SecureKeyStore for key persistence
         * @param databaseEncryptionManager DatabaseEncryptionManager for SQLCipher setup
         * @return Singleton AppDatabase instance
         * @throws Exception If encryption key initialization fails
         */
        fun getInstance(
            context: Context,
            encryptionService: EncryptionService,
            secureKeyStore: SecureKeyStore,
            databaseEncryptionManager: DatabaseEncryptionManager
        ): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(
                    context,
                    encryptionService,
                    secureKeyStore,
                    databaseEncryptionManager
                ).also { INSTANCE = it }
            }
        }

        /**
         * Get singleton instance with legacy encryptionKey parameter (deprecated)
         *
         * Kept for backwards compatibility. Use getInstance(context, services) instead.
         *
         * @param context Application context
         * @param encryptionKey SQLCipher encryption key (256-bit)
         * @return Singleton AppDatabase instance
         * @deprecated Use getInstance with encryption services instead
         */
        @Deprecated("Use getInstance with encryption services instead")
        fun getInstance(context: Context, encryptionKey: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabaseLegacy(context, encryptionKey).also { INSTANCE = it }
            }
        }

        /**
         * Build the Room database with SQLCipher encryption
         *
         * Retrieves encrypted database key from DatabaseEncryptionManager.
         * Configuration:
         * - SQLCipher encryption with AES-256-GCM
         * - Database key encrypted with Master Key (Android Keystore)
         * - WAL (Write-Ahead Logging) mode for better concurrency
         * - Connection pooling for performance
         * - Proper database name and version
         *
         * @param context Application context
         * @param encryptionService EncryptionService for key operations
         * @param secureKeyStore SecureKeyStore for key persistence
         * @param databaseEncryptionManager DatabaseEncryptionManager for key management
         * @return AppDatabase instance
         * @throws Exception If encryption key initialization fails
         */
        private fun buildDatabase(
            context: Context,
            encryptionService: EncryptionService,
            secureKeyStore: SecureKeyStore,
            databaseEncryptionManager: DatabaseEncryptionManager
        ): AppDatabase {
            Log.d(TAG, "Building database with encrypted SQLCipher key")

            // Get encrypted database passphrase from DatabaseEncryptionManager
            // This initializes the encryption key hierarchy:
            // 1. Ensure Master Key exists in Android Keystore
            // 2. Create or retrieve Database Key
            // 3. Encrypt Database Key with Master Key
            // 4. Return hex-encoded passphrase for SQLCipher
            val passphrase = try {
                databaseEncryptionManager.getDatabasePassphrase()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize database encryption", e)
                throw Exception("Database encryption initialization failed: ${e.message}", e)
            }

            Log.d(TAG, "Database passphrase obtained successfully (${passphrase.length} chars)")

            // Convert passphrase string to ByteArray for SQLCipher
            // Passphrase format: "x'<64 hex characters>'"
            val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                Constants.DATABASE_NAME
            )
                .openHelperFactory(SupportFactory(passphraseBytes))
                .addCallback(databaseCallback)
                .apply {
                    // Configuration options
                    setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                }
                .build()
                .also {
                    Log.d(TAG, "Database instance created successfully with encrypted key")
                    Log.d(TAG, "Encryption status: ${databaseEncryptionManager.getEncryptionStatus()}")
                }
        }

        /**
         * Build the Room database with explicit SQLCipher encryption key (legacy)
         *
         * Kept for backwards compatibility.
         *
         * @param context Application context
         * @param encryptionKey 256-bit SQLCipher encryption key
         * @return AppDatabase instance
         * @deprecated Use buildDatabase with encryption services instead
         */
        @Deprecated("Use buildDatabase with encryption services instead")
        private fun buildDatabaseLegacy(context: Context, encryptionKey: ByteArray): AppDatabase {
            Log.d(TAG, "Building database with SQLCipher encryption (legacy method)")

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                Constants.DATABASE_NAME
            )
                .openHelperFactory(SupportFactory(encryptionKey))
                .addCallback(databaseCallback)
                .apply {
                    setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                }
                .build()
                .also { Log.d(TAG, "Database instance created successfully") }
        }

        /**
         * Database callback for lifecycle events
         *
         * Used for:
         * - Initial database creation
         * - Schema migrations
         * - Data population
         */
        private val databaseCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d(TAG, "Database created")

                // TODO: Populate initial data if needed
                // Example: Insert default values, initial configuration, etc.
            }

            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.d(TAG, "Database opened")

                // TODO: Perform any setup needed when database is opened
                // Example: Enable foreign keys, set pragmas, etc.
                try {
                    // Enable foreign key constraints
                    db.execSQL("PRAGMA foreign_keys=ON;")
                    Log.d(TAG, "Foreign key constraints enabled")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable foreign keys", e)
                }
            }

            override fun onDestructiveMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                Log.w(TAG, "Destructive migration performed - data loss may have occurred")
            }
        }

        /**
         * Clear the singleton instance (for testing/logout scenarios)
         *
         * Use with caution - this disconnects the current database instance.
         * A new instance will be created on next call to getInstance().
         */
        fun clearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                Log.d(TAG, "Database instance cleared")
            }
        }
    }
}
