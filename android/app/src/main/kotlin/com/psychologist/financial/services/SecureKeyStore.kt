package com.psychologist.financial.services

import android.content.Context
import com.psychologist.financial.utils.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.psychologist.financial.domain.models.EncryptionKey
import com.psychologist.financial.domain.models.KeyPurpose
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.Base64

/**
 * Secure Key Store
 *
 * Manages encryption keys using Tink + DataStore (Encrypted).
 * Replaces deprecated EncryptedSharedPreferences with modern approach.
 *
 * Key Storage:
 * - Master Key: Stored in Android Keystore (hardware-backed)
 * - Database Keys: Encrypted with Master Key, stored in DataStore
 * - Backup Keys: Separate keys for backup/restore operations
 * - Key Metadata: Timestamps, rotation info, purpose
 *
 * Responsibilities:
 * - Store and retrieve encryption keys
 * - Track key metadata (creation, rotation, expiration)
 * - Implement key rotation policy (90-day intervals)
 * - Encrypt keys before storage (double encryption)
 * - Provide audit trail for key access
 *
 * Two-Layer Encryption:
 * 1. EncryptionService: Encrypts data with keys
 * 2. SecureKeyStore: Encrypts keys themselves for storage
 *
 * Usage:
 * ```kotlin
 * val keyStore = SecureKeyStore(context, encryptionService)
 *
 * // Store Database Key
 * val dbKey = encryptionService.generateDatabaseKey("db_key")
 * keyStore.storeDatabaseKey(dbKey)
 *
 * // Retrieve and use
 * val retrievedKey = keyStore.getDatabaseKey()
 * val encrypted = encryptionService.encrypt(plaintext, retrievedKey.alias)
 * ```
 *
 * @param context Android application context
 * @param encryptionService EncryptionService for key encryption
 *
 * @see EncryptionService
 * @see EncryptionKey
 */
class SecureKeyStore(
    context: Context,
    private val encryptionService: EncryptionService
) {
    private companion object {
        private const val TAG = "SecureKeyStore"
        private const val DATASTORE_NAME = "encryption_keys"
        private const val KEY_DB_ALIAS = "key_db"
        private const val KEY_BACKUP_ALIAS = "key_backup"
        private const val KEY_METADATA_PREFIX = "metadata_"
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(DATASTORE_NAME)
    private val dataStore = context.dataStore

    // DataStore keys (ByteArray stored as Base64 string — DataStore 1.0.0 only supports primitives/String)
    private val dbKeyStoredKey = stringPreferencesKey("database_key_encrypted")
    private val backupKeyStoredKey = stringPreferencesKey("backup_key_encrypted")
    private val dbKeyAliasKey = stringPreferencesKey("database_key_alias")
    private val backupKeyAliasKey = stringPreferencesKey("backup_key_alias")
    private val dbKeyCreatedAtKey = stringPreferencesKey("database_key_created_at")
    private val backupKeyCreatedAtKey = stringPreferencesKey("backup_key_created_at")

    init {
        AppLogger.d(TAG, "SecureKeyStore initialized")
    }

    // ========================================
    // Database Key Management
    // ========================================

    /**
     * Store Database Key in SecureKeyStore
     *
     * Database Key is encrypted with Master Key before storage.
     * Metadata is tracked for rotation policy enforcement.
     *
     * @param key EncryptionKey to store (purpose must be DATABASE)
     * @return true if stored successfully
     */
    suspend fun storeDatabaseKey(key: EncryptionKey): Boolean {
        AppLogger.d(TAG, "Storing Database Key: ${key.alias}")

        return try {
            // Encrypt key material with Master Key
            val encryptedKeyMaterial = encryptionService.encrypt(
                key.keyMaterial,
                key.alias
            )

            // Encode to Base64 for DataStore storage (DataStore 1.0.0 supports String only)
            val encodedKey = Base64.getEncoder().encodeToString(encryptedKeyMaterial)

            // Store in DataStore
            dataStore.edit { prefs ->
                prefs[dbKeyStoredKey] = encodedKey
                prefs[dbKeyAliasKey] = key.alias
                prefs[dbKeyCreatedAtKey] = key.createdAt.toString()
            }

            AppLogger.d(TAG, "Database Key stored successfully")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error storing Database Key", e)
            false
        }
    }

    /**
     * Retrieve Database Key from SecureKeyStore
     *
     * Decrypts key material with Master Key.
     *
     * @return EncryptionKey if found, null otherwise
     */
    suspend fun getDatabaseKey(): EncryptionKey? {
        AppLogger.d(TAG, "Retrieving Database Key")

        return try {
            val prefs = dataStore.data.first()
            val encodedKey = prefs[dbKeyStoredKey]
                ?: throw Exception("Database Key not found in storage")
            val encryptedKeyMaterial = Base64.getDecoder().decode(encodedKey)

            val alias = prefs[dbKeyAliasKey]
                ?: throw Exception("Database Key alias not found")

            val createdAtStr = prefs[dbKeyCreatedAtKey]
                ?: throw Exception("Database Key creation time not found")

            // Decrypt key material with Master Key
            val decryptedKeyMaterial = encryptionService.decrypt(
                encryptedKeyMaterial,
                alias
            )

            val createdAt = LocalDateTime.parse(createdAtStr)
            val expiresAt = createdAt.plusDays(90)

            val key = EncryptionKey(
                alias = alias,
                keyMaterial = decryptedKeyMaterial,
                createdAt = createdAt,
                lastRotatedAt = createdAt,
                expiresAt = expiresAt,
                isActive = true,
                purpose = KeyPurpose.DATABASE
            )

            AppLogger.d(TAG, "Database Key retrieved successfully")
            key
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error retrieving Database Key", e)
            null
        }
    }

    /**
     * Check if Database Key exists in storage
     *
     * @return true if Database Key is stored
     */
    suspend fun hasDatabaseKey(): Boolean {
        return try {
            val prefs = dataStore.data.first()
            prefs[dbKeyStoredKey] != null
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking Database Key existence", e)
            false
        }
    }

    /**
     * Delete Database Key (for rotation)
     *
     * @return true if deleted successfully
     */
    suspend fun deleteDatabaseKey(): Boolean {
        AppLogger.d(TAG, "Deleting Database Key")

        return try {
            dataStore.edit { prefs ->
                prefs.remove(dbKeyStoredKey)
                prefs.remove(dbKeyAliasKey)
                prefs.remove(dbKeyCreatedAtKey)
            }

            AppLogger.d(TAG, "Database Key deleted successfully")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting Database Key", e)
            false
        }
    }

    // ========================================
    // Backup Key Management
    // ========================================

    /**
     * Store Backup Key for backup/restore operations
     *
     * Separate from Database Key for additional security.
     * Enables encrypted backups without exposing main database key.
     *
     * @param key EncryptionKey for backup purposes
     * @return true if stored successfully
     */
    suspend fun storeBackupKey(key: EncryptionKey): Boolean {
        AppLogger.d(TAG, "Storing Backup Key: ${key.alias}")

        return try {
            // Encrypt key material with Master Key
            val encryptedKeyMaterial = encryptionService.encrypt(
                key.keyMaterial,
                key.alias
            )

            // Store in DataStore (ByteArray encoded as Base64 string)
            dataStore.edit { prefs ->
                prefs[backupKeyStoredKey] = Base64.getEncoder().encodeToString(encryptedKeyMaterial)
                prefs[backupKeyAliasKey] = key.alias
                prefs[backupKeyCreatedAtKey] = key.createdAt.toString()
            }

            AppLogger.d(TAG, "Backup Key stored successfully")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error storing Backup Key", e)
            false
        }
    }

    /**
     * Retrieve Backup Key
     *
     * @return EncryptionKey if found, null otherwise
     */
    suspend fun getBackupKey(): EncryptionKey? {
        AppLogger.d(TAG, "Retrieving Backup Key")

        return try {
            val prefs = dataStore.data.first()
            val encodedBackupKey = prefs[backupKeyStoredKey]
                ?: return null
            val encryptedKeyMaterial = Base64.getDecoder().decode(encodedBackupKey)

            val alias = prefs[backupKeyAliasKey]
                ?: throw Exception("Backup Key alias not found")

            val createdAtStr = prefs[backupKeyCreatedAtKey]
                ?: throw Exception("Backup Key creation time not found")

            // Decrypt key material
            val decryptedKeyMaterial = encryptionService.decrypt(
                encryptedKeyMaterial,
                alias
            )

            val createdAt = LocalDateTime.parse(createdAtStr)
            val expiresAt = createdAt.plusDays(90)

            val key = EncryptionKey(
                alias = alias,
                keyMaterial = decryptedKeyMaterial,
                createdAt = createdAt,
                lastRotatedAt = createdAt,
                expiresAt = expiresAt,
                isActive = true,
                purpose = KeyPurpose.BACKUP
            )

            AppLogger.d(TAG, "Backup Key retrieved successfully")
            key
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error retrieving Backup Key", e)
            null
        }
    }

    /**
     * Check if Backup Key exists
     *
     * @return true if Backup Key is stored
     */
    suspend fun hasBackupKey(): Boolean {
        return try {
            val prefs = dataStore.data.first()
            prefs[backupKeyStoredKey] != null
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking Backup Key existence", e)
            false
        }
    }

    /**
     * Delete Backup Key (for rotation)
     *
     * @return true if deleted successfully
     */
    suspend fun deleteBackupKey(): Boolean {
        AppLogger.d(TAG, "Deleting Backup Key")

        return try {
            dataStore.edit { prefs ->
                prefs.remove(backupKeyStoredKey)
                prefs.remove(backupKeyAliasKey)
                prefs.remove(backupKeyCreatedAtKey)
            }

            AppLogger.d(TAG, "Backup Key deleted successfully")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting Backup Key", e)
            false
        }
    }

    // ========================================
    // Key Inventory and Status
    // ========================================

    /**
     * Get inventory of stored keys
     *
     * @return Map of key purposes to EncryptionKeys
     */
    suspend fun getKeyInventory(): Map<KeyPurpose, EncryptionKey?> {
        return mapOf(
            KeyPurpose.DATABASE to getDatabaseKey(),
            KeyPurpose.BACKUP to getBackupKey()
        )
    }

    /**
     * Get all key aliases from Android Keystore
     *
     * @return List of key aliases for debugging
     */
    fun getAllKeyAliases(): List<String> {
        return encryptionService.getAllKeyAliases()
    }

    /**
     * Clear all keys (emergency/reset function)
     *
     * CAUTION: This makes encrypted data unrecoverable!
     * Only use for complete app reset.
     *
     * @return true if all keys deleted
     */
    suspend fun clearAllKeys(): Boolean {
        AppLogger.w(TAG, "CLEARING ALL KEYS - Emergency reset initiated!")

        return try {
            deleteDatabaseKey()
            deleteBackupKey()

            // Clear all Keystore keys
            for (alias in getAllKeyAliases()) {
                encryptionService.deleteKey(alias)
            }

            AppLogger.w(TAG, "All keys cleared successfully")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error clearing all keys", e)
            false
        }
    }
}
