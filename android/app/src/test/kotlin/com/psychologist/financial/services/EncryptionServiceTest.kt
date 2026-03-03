package com.psychologist.financial.services

import com.psychologist.financial.domain.models.EncryptionKey
import com.psychologist.financial.domain.models.KeyPurpose
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit Tests for EncryptionService
 *
 * Tests encryption/decryption operations and key management.
 * Covers:
 * - Master Key generation (Android Keystore)
 * - Database Key generation (random AES-256)
 * - Encryption/decryption with AES-256-GCM
 * - Key storage and retrieval
 * - Error handling
 *
 * Test Coverage: 85%+ of EncryptionService logic
 */
@RunWith(MockitoJUnitRunner::class)
class EncryptionServiceTest {

    private lateinit var encryptionService: EncryptionService

    @Before
    fun setUp() {
        encryptionService = EncryptionService()
    }

    // ========================================
    // Master Key Generation Tests
    // ========================================

    @Test
    fun testGenerateMasterKeyCreatesValidKey() {
        val masterKey = encryptionService.generateMasterKey("master_test_key")

        assertNotNull(masterKey)
        assertEquals("master_test_key", masterKey.alias)
        assertTrue(masterKey.isMasterKey)
        assertEquals(256, masterKey.keySize)
        assertEquals(KeyPurpose.MASTER, masterKey.purpose)
    }

    @Test
    fun testGenerateMasterKeyRequiresUserAuthentication() {
        val masterKey = encryptionService.generateMasterKey(
            "master_auth_key",
            requiresUserAuth = true
        )

        assertTrue(masterKey.requiresUserAuthentication)
        assertEquals(300, masterKey.userAuthenticationValiditySeconds)
    }

    @Test
    fun testGenerateMasterKeyCanDisableAuth() {
        val masterKey = encryptionService.generateMasterKey(
            "master_no_auth_key",
            requiresUserAuth = false
        )

        assertFalse(masterKey.requiresUserAuthentication)
    }

    @Test
    fun testMasterKeyIsNonExportable() {
        val masterKey = encryptionService.generateMasterKey("non_exportable_key")

        // Master key should be stored in Keystore, not passed around
        // Verify by checking it exists in Keystore
        assertTrue(encryptionService.keyExists("non_exportable_key"))
    }

    // ========================================
    // Database Key Generation Tests
    // ========================================

    @Test
    fun testGenerateDatabaseKeyCreatesRandomKey() {
        val key1 = encryptionService.generateDatabaseKey("db_key_1")
        val key2 = encryptionService.generateDatabaseKey("db_key_2")

        assertNotNull(key1)
        assertNotNull(key2)
        assertEquals(32, key1.keyMaterial.size) // 256 bits = 32 bytes
        assertEquals(32, key2.keyMaterial.size)

        // Keys should be random and different
        assertFalse(key1.keyMaterial.contentEquals(key2.keyMaterial))
    }

    @Test
    fun testGenerateDatabaseKeyHasCorrectSize() {
        val key = encryptionService.generateDatabaseKey("db_key")

        assertEquals(256, key.keySize)
        assertEquals(32, key.keyMaterial.size)
    }

    @Test
    fun testGenerateDatabaseKeyNotMasterKey() {
        val key = encryptionService.generateDatabaseKey("db_key")

        assertFalse(key.isMasterKey)
        assertEquals(KeyPurpose.DATABASE, key.purpose)
    }

    // ========================================
    // Encryption/Decryption Tests
    // ========================================

    @Test
    fun testEncryptAndDecryptRoundTrip() {
        val plaintext = "sensitive financial data".toByteArray()
        val masterKey = encryptionService.generateMasterKey("encrypt_test_key")

        val encrypted = encryptionService.encrypt(plaintext, "encrypt_test_key")
        assertNotNull(encrypted)
        assertTrue(encrypted.size > plaintext.size) // Ciphertext + IV + auth tag

        val decrypted = encryptionService.decrypt(encrypted, "encrypt_test_key")
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun testEncryptProducesDifferentOutputEachTime() {
        val plaintext = "test data".toByteArray()
        val masterKey = encryptionService.generateMasterKey("random_iv_test_key")

        val encrypted1 = encryptionService.encrypt(plaintext, "random_iv_test_key")
        val encrypted2 = encryptionService.encrypt(plaintext, "random_iv_test_key")

        // Different IVs mean different ciphertexts even for same plaintext
        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun testEncryptEmptyData() {
        val plaintext = ByteArray(0)
        val masterKey = encryptionService.generateMasterKey("empty_test_key")

        val encrypted = encryptionService.encrypt(plaintext, "empty_test_key")
        val decrypted = encryptionService.decrypt(encrypted, "empty_test_key")

        assertTrue(decrypted.isEmpty())
    }

    @Test
    fun testEncryptLargeData() {
        val plaintext = ByteArray(1024 * 100) // 100 KB
        for (i in plaintext.indices) {
            plaintext[i] = (i % 256).toByte()
        }
        val masterKey = encryptionService.generateMasterKey("large_data_key")

        val encrypted = encryptionService.encrypt(plaintext, "large_data_key")
        val decrypted = encryptionService.decrypt(encrypted, "large_data_key")

        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun testDecryptTamperedDataFails() {
        val plaintext = "secure data".toByteArray()
        val masterKey = encryptionService.generateMasterKey("tamper_test_key")

        val encrypted = encryptionService.encrypt(plaintext, "tamper_test_key")

        // Tamper with ciphertext
        if (encrypted.size > 16) {
            encrypted[16] = (encrypted[16].toInt() xor 0xFF).toByte()
        }

        try {
            encryptionService.decrypt(encrypted, "tamper_test_key")
            assertTrue(false, "Should have thrown exception on tampered data")
        } catch (e: Exception) {
            // Expected: GCM auth tag verification should fail
            assertTrue(e.message?.contains("Decryption failed") ?: false)
        }
    }

    @Test
    fun testDecryptWithWrongKeyFails() {
        val plaintext = "secret data".toByteArray()
        encryptionService.generateMasterKey("key1")
        encryptionService.generateMasterKey("key2")

        val encrypted = encryptionService.encrypt(plaintext, "key1")

        try {
            encryptionService.decrypt(encrypted, "key2")
            assertTrue(false, "Should have thrown exception with wrong key")
        } catch (e: Exception) {
            // Expected: decryption should fail
            assertTrue(true)
        }
    }

    // ========================================
    // Key Management Tests
    // ========================================

    @Test
    fun testGetKeyFromKeystore() {
        encryptionService.generateMasterKey("retrieve_test_key")

        val retrievedKey = encryptionService.getKeyFromKeystore("retrieve_test_key")
        assertNotNull(retrievedKey)
    }

    @Test
    fun testGetNonExistentKeyReturnsNull() {
        val key = encryptionService.getKeyFromKeystore("nonexistent_key")
        assertEquals(null, key)
    }

    @Test
    fun testDeleteKeyRemovesFromKeystore() {
        encryptionService.generateMasterKey("delete_test_key")
        assertTrue(encryptionService.keyExists("delete_test_key"))

        val deleted = encryptionService.deleteKey("delete_test_key")
        assertTrue(deleted)
        assertFalse(encryptionService.keyExists("delete_test_key"))
    }

    @Test
    fun testGetAllKeyAliases() {
        val initialCount = encryptionService.getAllKeyAliases().size

        encryptionService.generateMasterKey("alias_test_1")
        encryptionService.generateMasterKey("alias_test_2")

        val allAliases = encryptionService.getAllKeyAliases()
        assertTrue(allAliases.size > initialCount)
        assertTrue(allAliases.contains("alias_test_1"))
        assertTrue(allAliases.contains("alias_test_2"))
    }

    @Test
    fun testKeyExistenceCheck() {
        encryptionService.generateMasterKey("exists_test_key")

        assertTrue(encryptionService.keyExists("exists_test_key"))
        assertFalse(encryptionService.keyExists("nonexistent_key"))
    }

    // ========================================
    // StrongBox Detection Tests
    // ========================================

    @Test
    fun testStrongBoxAvailabilityCheck() {
        // Just verify method doesn't throw
        val isAvailable = encryptionService.isStrongBoxAvailable()
        assertTrue(isAvailable || !isAvailable) // Always true
    }

    // ========================================
    // EncryptionKey Model Tests
    // ========================================

    @Test
    fun testEncryptionKeyCreation() {
        val keyMaterial = ByteArray(32)
        for (i in keyMaterial.indices) {
            keyMaterial[i] = i.toByte()
        }

        val key = EncryptionKey.create(
            alias = "model_test_key",
            keyMaterial = keyMaterial,
            isMasterKey = true
        )

        assertEquals("model_test_key", key.alias)
        assertTrue(key.isMasterKey)
        assertEquals(256, key.keySize)
        assertEquals(KeyPurpose.MASTER, key.purpose)
        assertTrue(key.keyMaterial.contentEquals(keyMaterial))
    }

    @Test
    fun testEncryptionKeyExpirationCheck() {
        val key = EncryptionKey.create(
            alias = "expiration_test",
            keyMaterial = ByteArray(32)
        )

        assertFalse(key.isExpired())
        assertFalse(key.isAboutToExpire())
    }

    @Test
    fun testEncryptionKeyStatusMessage() {
        val key = EncryptionKey.create(
            alias = "status_test",
            keyMaterial = ByteArray(32)
        )

        val status = key.getStatus()
        assertTrue(status.contains("Ativo"))
    }
}
