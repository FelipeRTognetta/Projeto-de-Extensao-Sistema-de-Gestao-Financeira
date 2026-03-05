package com.psychologist.financial.services

import android.annotation.SuppressLint
import com.psychologist.financial.utils.AppLogger
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.psychologist.financial.domain.models.EncryptionKey
import com.psychologist.financial.domain.models.KeyPurpose
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore
import kotlin.random.Random

/**
 * Encryption Service
 *
 * Handles encryption and decryption operations using Android Keystore.
 * Manages symmetric encryption (AES-256-GCM) for data protection.
 *
 * Key Features:
 * - Hardware-backed key generation via Android Keystore
 * - AES-256-GCM encryption for authenticated encryption
 * - Automatic IV (Initialization Vector) generation
 * - User authentication requirement for sensitive keys
 * - Non-exportable key material (protected by device TEE/StrongBox)
 *
 * Security Model:
 * - Master Key: Stored in Android Keystore (hardware-backed, non-exportable)
 * - Database Key: 256-bit AES key, encrypted with Master Key
 * - Per-operation IVs: Random 96-bit IVs for each encryption operation
 * - Authentication Tag: 128-bit GCM auth tag for integrity verification
 *
 * Usage:
 * ```kotlin
 * val encryptionService = EncryptionService()
 *
 * // Generate Master Key
 * val masterKey = encryptionService.generateMasterKey("master_key_alias")
 *
 * // Encrypt data
 * val plaintext = "sensitive data".toByteArray()
 * val encryptedData = encryptionService.encrypt(plaintext, "database_key_alias")
 *
 * // Decrypt data
 * val decryptedData = encryptionService.decrypt(encryptedData, "database_key_alias")
 * ```
 *
 * @see EncryptionKey
 * @see SecureKeyStore
 */
class EncryptionService {

    private companion object {
        private const val TAG = "EncryptionService"
        private const val KEYSTORE_TYPE = "AndroidKeyStore"
        private const val ALGORITHM = "AES"
        private const val BLOCK_MODE = "GCM"
        private const val PADDING = "NoPadding"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val IV_LENGTH = 12 // bytes (96 bits for GCM)
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
        load(null)
    }

    // ========================================
    // Master Key Generation
    // ========================================

    /**
     * Generate a Master Key for encryption key management
     *
     * Master Key is:
     * - Stored in Android Keystore (hardware-backed if available)
     * - Non-exportable (never leaves secure enclave)
     * - Requires user authentication for access (biometric/PIN)
     * - Valid for 5 minutes after authentication
     *
     * @param alias Unique identifier for the key
     * @param requiresUserAuth Whether key access requires biometric/PIN (default: true)
     * @return EncryptionKey model with metadata
     *
     * @throws Exception If key generation fails
     */
    fun generateMasterKey(
        alias: String,
        requiresUserAuth: Boolean = true
    ): EncryptionKey {
        AppLogger.security(TAG, "Generating Master Key: $alias")

        try {
            // Remove existing key if present
            if (keyStore.containsAlias(alias)) {
                AppLogger.security(TAG, "Removing existing key: $alias")
                keyStore.deleteEntry(alias)
            }

            val secretKey = tryGenerateKey(alias, requiresUserAuth, useStrongBox = true)
                ?: tryGenerateKey(alias, requiresUserAuth, useStrongBox = false)
                ?: throw Exception("Key generation failed (StrongBox and TEE both unavailable)")

            AppLogger.security(TAG, "Master Key generated successfully: $alias")

            return EncryptionKey.create(
                alias = alias,
                keyMaterial = secretKey.encoded ?: ByteArray(0),
                isMasterKey = true,
                purpose = KeyPurpose.MASTER
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error generating Master Key", e)
            throw Exception("Failed to generate Master Key: ${e.message}", e)
        }
    }

    @SuppressLint("WrongConstant")
    private fun tryGenerateKey(
        alias: String,
        requiresUserAuth: Boolean,
        useStrongBox: Boolean
    ): SecretKey? {
        return try {
            val keySpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setKeySize(KEY_SIZE)
                .apply {
                    if (requiresUserAuth) {
                        setUserAuthenticationRequired(true)
                        setUserAuthenticationValidityDurationSeconds(300)
                    }
                    if (useStrongBox) {
                        setIsStrongBoxBacked(true)
                    }
                }
                .build()

            val keyGenerator = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_TYPE)
            keyGenerator.init(keySpec)
            keyGenerator.generateKey().also {
                if (useStrongBox) {
                    AppLogger.security(TAG, "Master Key generated with StrongBox: $alias")
                } else {
                    AppLogger.security(TAG, "Master Key generated with TEE (no StrongBox): $alias")
                }
            }
        } catch (e: Exception) {
            if (useStrongBox) {
                AppLogger.w(TAG, "StrongBox unavailable for $alias, will retry with TEE: ${e.message}")
            } else {
                AppLogger.e(TAG, "TEE key generation also failed for $alias", e)
            }
            null
        }
    }

    /**
     * Generate a Database Key for SQLCipher encryption
     *
     * Database Key is:
     * - A random 256-bit AES key (generated in software, not in Keystore)
     * - Will be encrypted with Master Key for storage
     * - Used for SQLCipher database encryption
     *
     * @param alias Alias for metadata purposes
     * @return EncryptionKey with random key material
     */
    fun generateDatabaseKey(alias: String): EncryptionKey {
        AppLogger.security(TAG, "Generating Database Key: $alias")

        // Generate 256-bit (32 byte) random key
        val keyMaterial = ByteArray(32)
        Random.nextBytes(keyMaterial)

        AppLogger.security(TAG, "Database Key generated successfully: $alias")

        return EncryptionKey.create(
            alias = alias,
            keyMaterial = keyMaterial,
            isMasterKey = false,
            purpose = KeyPurpose.DATABASE
        )
    }

    /**
     * Retrieve a key from Android Keystore by alias
     *
     * @param alias Key identifier
     * @return SecretKey if found, null otherwise
     */
    fun getKeyFromKeystore(alias: String): SecretKey? {
        return try {
            keyStore.getKey(alias, null) as? SecretKey
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error retrieving key from Keystore: $alias", e)
            null
        }
    }

    // ========================================
    // Encryption and Decryption
    // ========================================

    /**
     * Encrypt data using a Keystore key
     *
     * Uses AES-256-GCM with random IV.
     * Returns: IV (12 bytes) + Ciphertext + Auth Tag (authentication included in GCM)
     *
     * @param plaintext Data to encrypt
     * @param keyAlias Keystore key alias
     * @return Encrypted data (IV + ciphertext)
     *
     * @throws Exception If encryption fails or key not found
     */
    fun encrypt(plaintext: ByteArray, keyAlias: String): ByteArray {
        AppLogger.security(TAG, "Encrypting data with key: $keyAlias (plaintext size: ${plaintext.size} bytes)")

        try {
            val key = getKeyFromKeystore(keyAlias)
                ?: throw Exception("Key not found: $keyAlias")

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)

            // Android Keystore does not allow caller-provided IVs (randomized encryption
            // is required by default). Let the Keystore generate the IV internally.
            cipher.init(Cipher.ENCRYPT_MODE, key)

            // Retrieve the Keystore-generated IV
            val iv = cipher.iv

            // Encrypt plaintext
            val ciphertext = cipher.doFinal(plaintext)

            // Prepend IV to ciphertext for later decryption
            val result = iv + ciphertext

            AppLogger.security(TAG, "Encryption successful (ciphertext size: ${ciphertext.size} bytes)")
            return result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Encryption failed", e)
            throw Exception("Encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypt data using a Keystore key
     *
     * Expects input format: IV (12 bytes) + Ciphertext + Auth Tag
     *
     * @param encryptedData Data to decrypt (IV + ciphertext)
     * @param keyAlias Keystore key alias
     * @return Decrypted plaintext
     *
     * @throws Exception If decryption fails or authentication fails
     */
    fun decrypt(encryptedData: ByteArray, keyAlias: String): ByteArray {
        AppLogger.security(TAG, "Decrypting data with key: $keyAlias (encrypted size: ${encryptedData.size} bytes)")

        try {
            if (encryptedData.size < IV_LENGTH) {
                throw Exception("Encrypted data too short (minimum: $IV_LENGTH bytes)")
            }

            val key = getKeyFromKeystore(keyAlias)
                ?: throw Exception("Key not found: $keyAlias")

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)

            // Extract IV (first 12 bytes)
            val iv = encryptedData.sliceArray(0 until IV_LENGTH)

            // Extract ciphertext (remaining bytes)
            val ciphertext = encryptedData.sliceArray(IV_LENGTH until encryptedData.size)

            // Initialize cipher with IV
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            // Decrypt ciphertext
            val plaintext = cipher.doFinal(ciphertext)

            AppLogger.security(TAG, "Decryption successful (plaintext size: ${plaintext.size} bytes)")
            return plaintext
        } catch (e: Exception) {
            AppLogger.e(TAG, "Decryption failed", e)
            throw Exception("Decryption failed: ${e.message}", e)
        }
    }

    // ========================================
    // Key Management Utilities
    // ========================================

    /**
     * Check if Android Keystore supports StrongBox (hardware-backed security)
     *
     * StrongBox provides maximum security via dedicated secure processor.
     *
     * @return true if StrongBox available
     */
    @SuppressLint("WrongConstant")
    fun isStrongBoxAvailable(): Boolean {
        // Probe StrongBox by attempting to generate a test key with StrongBox backing.
        // keyStore.containsAlias() never throws, so it cannot be used as a probe.
        return try {
            val testAlias = "__strongbox_probe__"
            val keySpec = KeyGenParameterSpec.Builder(
                testAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setKeySize(KEY_SIZE)
                .setIsStrongBoxBacked(true)
                .build()
            val kg = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_TYPE)
            kg.init(keySpec)
            kg.generateKey()
            keyStore.deleteEntry(testAlias)
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "StrongBox not available: ${e.message}")
            false
        }
    }

    /**
     * Delete a key from Android Keystore
     *
     * Used for key rotation and cleanup.
     *
     * @param alias Key identifier
     * @return true if deleted, false if not found
     */
    fun deleteKey(alias: String): Boolean {
        return try {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
                AppLogger.security(TAG, "Key deleted: $alias")
                true
            } else {
                AppLogger.w(TAG, "Key not found for deletion: $alias")
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting key: $alias", e)
            false
        }
    }

    /**
     * Get all key aliases in Android Keystore
     *
     * Useful for debugging and key inventory.
     *
     * @return List of key aliases
     */
    fun getAllKeyAliases(): List<String> {
        return try {
            keyStore.aliases().toList()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error listing keys", e)
            emptyList()
        }
    }

    /**
     * Check if a key exists in Android Keystore
     *
     * @param alias Key identifier
     * @return true if key exists
     */
    fun keyExists(alias: String): Boolean {
        return try {
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking key existence: $alias", e)
            false
        }
    }
}
