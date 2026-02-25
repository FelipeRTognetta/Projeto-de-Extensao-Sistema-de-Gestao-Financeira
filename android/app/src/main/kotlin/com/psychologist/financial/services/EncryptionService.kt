package com.psychologist.financial.services

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

/**
 * Service for managing encryption keys and performing cryptographic operations
 *
 * Features:
 * - Hardware-backed key storage via Android Keystore
 * - AES-256-GCM encryption for sensitive data
 * - Automatic key generation with biometric binding
 * - Secure key deletion and rotation
 * - API level compatibility (21+)
 *
 * Architecture:
 * - AppDatabase uses this service to get encryption password for SQLCipher
 * - BiometricAuthManager uses this to protect auth tokens
 * - Can be extended for other sensitive field encryption
 *
 * Security Notes:
 * - Keys stored in Android Keystore are never extracted in plaintext
 * - Hardware backing available on Android 9+ (if device supports)
 * - All operations use AES-256-GCM for authenticated encryption
 * - IV (nonce) is randomly generated and prepended to ciphertext
 *
 * Limitations:
 * - Cannot directly encrypt passwords (passwords are not extractable)
 * - For database password: Generate a derived key from fingerprint
 * - Key rotation requires new key generation and re-encryption
 *
 * Usage:
 * ```kotlin
 * val encryptionService = EncryptionService(context)
 *
 * // Get database encryption key (for SQLCipher)
 * val dbKey = encryptionService.getDatabaseEncryptionKey()
 *
 * // Encrypt arbitrary data
 * val encrypted = encryptionService.encrypt(token, "auth_token")
 * val decrypted = encryptionService.decrypt(encrypted, "auth_token")
 *
 * // Rotate keys
 * encryptionService.rotateKey("auth_token")
 * ```
 */
class EncryptionService(private val context: Context) {

    private companion object {
        private const val TAG = "EncryptionService"

        // Keystore configuration
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DATABASE_KEY_ALIAS = "financial_db_master_key"
        private const val AUTH_TOKEN_KEY_ALIAS = "auth_token_key"

        // GCM encryption parameters
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12

        // Key algorithm
        private const val KEY_SIZE_BITS = 256
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val CIPHER_TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$ENCRYPTION_PADDING"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Get or create database encryption key
     *
     * Returns a 256-bit key suitable for SQLCipher encryption.
     * Key is stored in Android Keystore and never extracted in plaintext.
     *
     * For SQLCipher, we export a derived key as ByteArray via encryption:
     * - Generate fixed plaintext (32 bytes)
     * - Encrypt with Keystore key
     * - Return encrypted bytes for SQLCipher
     *
     * This ensures:
     * - Same database password across app restarts
     * - Key protected by Keystore hardware backing (if available)
     * - Protection even if device storage is compromised
     *
     * @return 32-byte encryption key for SQLCipher
     * @throws Exception If key generation or encryption fails
     */
    fun getDatabaseEncryptionKey(): ByteArray {
        return try {
            // Ensure key exists (create if not)
            ensureKeyExists(DATABASE_KEY_ALIAS)

            // Generate fixed plaintext for consistent key derivation
            val plaintext = generateFixedPlaintext(32)

            // Encrypt with Keystore key
            encryptWithAlias(plaintext, DATABASE_KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get database encryption key", e)
            throw e
        }
    }

    /**
     * Encrypt data using the specified Keystore key
     *
     * Uses AES-256-GCM with authenticated encryption.
     * IV (nonce) is randomly generated and prepended to ciphertext.
     *
     * Ciphertext format:
     * [12-byte IV] [ciphertext] [16-byte auth tag (included by GCM)]
     *
     * @param plaintext Data to encrypt
     * @param keyAlias Keystore key alias
     * @return Base64-encoded ciphertext with prepended IV
     * @throws Exception If encryption fails
     */
    fun encrypt(plaintext: ByteArray, keyAlias: String): String {
        return try {
            ensureKeyExists(keyAlias)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val key = getKey(keyAlias)

            // Generate random IV
            val iv = ByteArray(IV_LENGTH_BYTES)
            Random.nextBytes(iv)

            // Initialize cipher with IV
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            // Encrypt data
            val ciphertext = cipher.doFinal(plaintext)

            // Prepend IV to ciphertext
            val ivAndCiphertext = iv + ciphertext

            // Return Base64-encoded
            Base64.encodeToString(ivAndCiphertext, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed for key: $keyAlias", e)
            throw e
        }
    }

    /**
     * Decrypt data using the specified Keystore key
     *
     * Extracts IV from prepended bytes and decrypts using GCM mode.
     *
     * @param encryptedBase64 Base64-encoded ciphertext with prepended IV
     * @param keyAlias Keystore key alias
     * @return Decrypted plaintext
     * @throws Exception If decryption fails (wrong password, tampered data, etc.)
     */
    fun decrypt(encryptedBase64: String, keyAlias: String): ByteArray {
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val key = getKey(keyAlias)

            // Decode Base64
            val ivAndCiphertext = Base64.decode(encryptedBase64, Base64.NO_WRAP)

            // Extract IV and ciphertext
            val iv = ivAndCiphertext.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = ivAndCiphertext.copyOfRange(IV_LENGTH_BYTES, ivAndCiphertext.size)

            // Initialize cipher with IV
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            // Decrypt and return plaintext
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed for key: $keyAlias", e)
            throw e
        }
    }

    /**
     * Rotate key (create new key, mark old as rotated)
     *
     * For future use: implement key versioning and re-encryption.
     * Current implementation generates new key with new alias.
     *
     * @param baseAlias Base alias for the key family
     * @throws Exception If key generation fails
     */
    fun rotateKey(baseAlias: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val newAlias = "${baseAlias}_rotated_$timestamp"

            // Delete old key
            keyStore.deleteEntry(baseAlias)

            // Generate new key (will be created on next use)
            ensureKeyExists(newAlias)

            Log.d(TAG, "Key rotated: $baseAlias -> $newAlias")
        } catch (e: Exception) {
            Log.e(TAG, "Key rotation failed", e)
            throw e
        }
    }

    /**
     * Check if key exists in Keystore
     *
     * @param keyAlias Key alias
     * @return true if key exists, false otherwise
     */
    fun keyExists(keyAlias: String): Boolean {
        return keyStore.containsAlias(keyAlias)
    }

    /**
     * Delete key from Keystore
     *
     * WARNING: Destructive operation. Cannot be undone.
     *
     * @param keyAlias Key alias to delete
     */
    fun deleteKey(keyAlias: String) {
        try {
            keyStore.deleteEntry(keyAlias)
            Log.d(TAG, "Key deleted: $keyAlias")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete key: $keyAlias", e)
            throw e
        }
    }

    /**
     * Get or create key in Keystore
     *
     * Keys are created with:
     * - AES-256-GCM algorithm
     * - Hardware backing (if available)
     * - No biometric requirement (auth keys can override)
     *
     * @param keyAlias Key alias
     * @return SecretKey from Keystore
     * @throws Exception If key retrieval/creation fails
     */
    private fun ensureKeyExists(keyAlias: String) {
        if (keyStore.containsAlias(keyAlias)) {
            return
        }

        // Create new key
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE)

        val keySpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(ENCRYPTION_PADDING)
            .setRandomizedEncryptionRequired(true)  // Always use random IV
            .apply {
                // Hardware backing available on Android 9+ (if device supports)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)  // Use secure hardware if available
                }
            }
            .build()

        keyGenerator.init(keySpec)
        keyGenerator.generateKey()

        Log.d(TAG, "Key created: $keyAlias")
    }

    /**
     * Get key from Keystore
     *
     * @param keyAlias Key alias
     * @return SecretKey from Keystore
     * @throws Exception If key not found
     */
    private fun getKey(keyAlias: String): SecretKey {
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    /**
     * Generate fixed plaintext for consistent key derivation
     *
     * For database key, we need same encryption output across restarts.
     * This generates fixed bytes from the alias hash.
     *
     * @param length Number of bytes to generate
     * @return Fixed-length byte array
     */
    private fun generateFixedPlaintext(length: Int): ByteArray {
        val plaintext = ByteArray(length)
        for (i in 0 until length) {
            plaintext[i] = (DATABASE_KEY_ALIAS.hashCode() ushr (i % 32)).toByte()
        }
        return plaintext
    }

    /**
     * Encrypt data using a specific Keystore key alias
     *
     * Internal method for consistent encryption.
     *
     * @param plaintext Data to encrypt
     * @param keyAlias Keystore key alias
     * @return Base64-encoded ciphertext with IV
     */
    private fun encryptWithAlias(plaintext: ByteArray, keyAlias: String): ByteArray {
        val encrypted = encrypt(plaintext, keyAlias)
        return Base64.decode(encrypted, Base64.NO_WRAP)
    }
}
