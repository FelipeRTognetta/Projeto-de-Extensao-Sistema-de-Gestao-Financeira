package com.psychologist.financial.services

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for EncryptionService
 *
 * Tests encryption/decryption operations and Keystore integration.
 * Uses Robolectric for Android API mocking (no device required).
 *
 * Test Coverage:
 * - Key generation and retrieval
 * - Encryption with random IVs
 * - Decryption and verification
 * - Database key generation consistency
 * - Key existence checks
 * - Key deletion
 * - Key rotation
 * - Error handling on corrupted data
 *
 * Security Notes:
 * - Tests use real Android Keystore (mocked by Robolectric)
 * - Each test runs in isolated context
 * - Keys are created/deleted per test
 *
 * Limitations:
 * - Robolectric mocks don't test actual hardware backing (StrongBox)
 * - For real hardware testing, use instrumented tests (androidTest/)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])  // Android 12 (API 31)
class EncryptionServiceTest {

    private lateinit var context: Context
    private lateinit var encryptionService: EncryptionService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        encryptionService = EncryptionService(context)
    }

    // ========================================
    // Database Key Generation Tests
    // ========================================

    /**
     * Test: Database key can be retrieved
     *
     * Verifies:
     * - Key generation succeeds
     * - Returns 32-byte array (256-bit key)
     */
    @Test
    fun testGetDatabaseEncryptionKey_Success() {
        val key = encryptionService.getDatabaseEncryptionKey()

        assertNotNull(key, "Database key should not be null")
        assertEquals(32, key.size, "Database key should be 256 bits (32 bytes)")
    }

    /**
     * Test: Database key is consistent across calls
     *
     * Verifies:
     * - Same key returned on multiple calls (for SQLCipher)
     * - This is critical: database password must be same on restart
     */
    @Test
    fun testGetDatabaseEncryptionKey_Consistency() {
        val key1 = encryptionService.getDatabaseEncryptionKey()
        val key2 = encryptionService.getDatabaseEncryptionKey()

        assertEquals(key1.toList(), key2.toList(), "Database key should be consistent")
    }

    // ========================================
    // Encryption/Decryption Tests
    // ========================================

    /**
     * Test: Plaintext can be encrypted and decrypted
     *
     * Verifies:
     * - Encryption succeeds
     * - Decryption recovers original plaintext
     */
    @Test
    fun testEncryptDecrypt_Success() {
        val plaintext = "Hello, World!".toByteArray()
        val keyAlias = "test_key_1"

        val encrypted = encryptionService.encrypt(plaintext, keyAlias)
        val decrypted = encryptionService.decrypt(encrypted, keyAlias)

        assertEquals(plaintext.toList(), decrypted.toList(), "Decrypted should match plaintext")
    }

    /**
     * Test: Each encryption produces different ciphertext (random IV)
     *
     * Verifies:
     * - IV is randomly generated each time
     * - Same plaintext produces different ciphertext
     * - This prevents pattern analysis attacks
     */
    @Test
    fun testEncryptDecrypt_RandomIV() {
        val plaintext = "Same plaintext".toByteArray()
        val keyAlias = "test_key_random"

        val encrypted1 = encryptionService.encrypt(plaintext, keyAlias)
        val encrypted2 = encryptionService.encrypt(plaintext, keyAlias)

        assertNotEquals(encrypted1, encrypted2, "Ciphertext should differ due to random IV")

        // But both should decrypt to same plaintext
        val decrypted1 = encryptionService.decrypt(encrypted1, keyAlias)
        val decrypted2 = encryptionService.decrypt(encrypted2, keyAlias)
        assertEquals(decrypted1.toList(), decrypted2.toList(), "Both should decrypt to same plaintext")
    }

    /**
     * Test: Empty plaintext can be encrypted/decrypted
     *
     * Verifies:
     * - Edge case: zero-length data
     * - GCM handles empty plaintext correctly
     */
    @Test
    fun testEncryptDecrypt_EmptyPlaintext() {
        val plaintext = ByteArray(0)
        val keyAlias = "test_key_empty"

        val encrypted = encryptionService.encrypt(plaintext, keyAlias)
        val decrypted = encryptionService.decrypt(encrypted, keyAlias)

        assertEquals(0, decrypted.size, "Decrypted should be empty")
    }

    /**
     * Test: Large plaintext can be encrypted/decrypted
     *
     * Verifies:
     * - Performance with 1MB of data
     * - No size limits in encryption
     */
    @Test
    fun testEncryptDecrypt_LargeData() {
        val plaintext = ByteArray(1024 * 1024) { it.toByte() }  // 1MB
        val keyAlias = "test_key_large"

        val encrypted = encryptionService.encrypt(plaintext, keyAlias)
        val decrypted = encryptionService.decrypt(encrypted, keyAlias)

        assertEquals(plaintext.toList(), decrypted.toList(), "Large data should decrypt correctly")
    }

    /**
     * Test: Wrong key fails decryption
     *
     * Verifies:
     * - Data encrypted with key1 cannot be decrypted with key2
     * - Each key is isolated
     */
    @Test
    fun testEncryptDecrypt_WrongKey_Fails() {
        val plaintext = "Secret data".toByteArray()

        val encrypted = encryptionService.encrypt(plaintext, "key_one")

        var exceptionThrown = false
        try {
            encryptionService.decrypt(encrypted, "key_two")
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown, "Decryption with wrong key should throw exception")
    }

    /**
     * Test: Corrupted ciphertext fails decryption
     *
     * Verifies:
     * - GCM authentication tag detects tampering
     * - Corrupted data cannot be decrypted
     */
    @Test
    fun testEncryptDecrypt_CorruptedData_Fails() {
        val plaintext = "Secure message".toByteArray()
        val keyAlias = "test_key_corrupt"

        val encrypted = encryptionService.encrypt(plaintext, keyAlias)

        // Corrupt the ciphertext by modifying a byte
        val corruptedBase64 = encrypted.toCharArray()
        val corruptIndex = corruptedBase64.size / 2
        corruptedBase64[corruptIndex] = if (corruptedBase64[corruptIndex] == 'A') 'B' else 'A'

        var exceptionThrown = false
        try {
            encryptionService.decrypt(String(corruptedBase64), keyAlias)
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown, "Decryption of corrupted data should throw exception")
    }

    /**
     * Test: Encryption output is Base64 encoded
     *
     * Verifies:
     * - Encrypt returns valid Base64 string
     * - Can be safely stored and transmitted
     */
    @Test
    fun testEncryptDecrypt_Base64Encoded() {
        val plaintext = "Test message".toByteArray()
        val keyAlias = "test_key_base64"

        val encrypted = encryptionService.encrypt(plaintext, keyAlias)

        // Should be valid Base64
        var validBase64 = false
        try {
            Base64.decode(encrypted, Base64.NO_WRAP)
            validBase64 = true
        } catch (e: Exception) {
            // Invalid Base64
        }

        assertTrue(validBase64, "Encrypted output should be valid Base64")
    }

    // ========================================
    // Key Management Tests
    // ========================================

    /**
     * Test: Key existence can be checked
     *
     * Verifies:
     * - keyExists() returns false before creation
     * - keyExists() returns true after creation
     */
    @Test
    fun testKeyExists() {
        val keyAlias = "test_key_exists"

        // Key doesn't exist yet
        assertFalse(encryptionService.keyExists(keyAlias), "Key should not exist initially")

        // Create key by encrypting
        encryptionService.encrypt("test".toByteArray(), keyAlias)

        // Now key exists
        assertTrue(encryptionService.keyExists(keyAlias), "Key should exist after encryption")
    }

    /**
     * Test: Key can be deleted
     *
     * Verifies:
     * - deleteKey() removes key from Keystore
     * - Future operations fail after deletion
     */
    @Test
    fun testDeleteKey() {
        val keyAlias = "test_key_delete"
        val plaintext = "test data".toByteArray()

        // Create key
        encryptionService.encrypt(plaintext, keyAlias)
        assertTrue(encryptionService.keyExists(keyAlias), "Key should exist after creation")

        // Delete key
        encryptionService.deleteKey(keyAlias)
        assertFalse(encryptionService.keyExists(keyAlias), "Key should not exist after deletion")

        // Encryption with deleted key fails
        var exceptionThrown = false
        try {
            encryptionService.encrypt(plaintext, keyAlias)
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown, "Encryption with deleted key should fail")
    }

    /**
     * Test: Key rotation creates new key
     *
     * Verifies:
     * - rotateKey() creates new key alias
     * - Old key is deleted
     */
    @Test
    fun testRotateKey() {
        val baseAlias = "test_key_rotate"
        val plaintext = "test data".toByteArray()

        // Create initial key
        encryptionService.encrypt(plaintext, baseAlias)
        assertTrue(encryptionService.keyExists(baseAlias), "Initial key should exist")

        // Rotate key
        encryptionService.rotateKey(baseAlias)

        // Old key deleted
        assertFalse(encryptionService.keyExists(baseAlias), "Old key should be deleted after rotation")
    }

    // ========================================
    // Integration Tests
    // ========================================

    /**
     * Test: Multiple keys can coexist
     *
     * Verifies:
     * - Multiple keys with different aliases
     * - Each key is isolated
     */
    @Test
    fun testMultipleKeys() {
        val data1 = "Data for key1".toByteArray()
        val data2 = "Data for key2".toByteArray()

        val encrypted1 = encryptionService.encrypt(data1, "key_alpha")
        val encrypted2 = encryptionService.encrypt(data2, "key_beta")

        val decrypted1 = encryptionService.decrypt(encrypted1, "key_alpha")
        val decrypted2 = encryptionService.decrypt(encrypted2, "key_beta")

        assertEquals(data1.toList(), decrypted1.toList(), "Key1 data should decrypt correctly")
        assertEquals(data2.toList(), decrypted2.toList(), "Key2 data should decrypt correctly")
    }

    /**
     * Test: Database key and custom keys don't interfere
     *
     * Verifies:
     * - Database key operations independent from other keys
     */
    @Test
    fun testDatabaseKeyIndependent() {
        val dbKey1 = encryptionService.getDatabaseEncryptionKey()

        // Use another key
        val customData = "Custom secret".toByteArray()
        encryptionService.encrypt(customData, "custom_key")

        val dbKey2 = encryptionService.getDatabaseEncryptionKey()

        assertEquals(dbKey1.toList(), dbKey2.toList(), "Database key should remain consistent")
    }

    /**
     * Test: Special characters in plaintext
     *
     * Verifies:
     * - Unicode, emojis, and special chars handled correctly
     */
    @Test
    fun testEncryptDecrypt_SpecialCharacters() {
        val plaintext = "😀 Ñoño @ #\$%^&*() \u0000\u0001\u0002".toByteArray(Charsets.UTF_8)
        val keyAlias = "test_key_special"

        val encrypted = encryptionService.encrypt(plaintext, keyAlias)
        val decrypted = encryptionService.decrypt(encrypted, keyAlias)

        assertEquals(plaintext.toList(), decrypted.toList(), "Special characters should be preserved")
    }
}
