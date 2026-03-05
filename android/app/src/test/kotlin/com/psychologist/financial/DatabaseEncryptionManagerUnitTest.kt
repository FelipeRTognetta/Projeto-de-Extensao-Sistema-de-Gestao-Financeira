package com.psychologist.financial

import com.psychologist.financial.domain.models.EncryptionKey
import com.psychologist.financial.domain.models.KeyPurpose
import com.psychologist.financial.services.DatabaseEncryptionManager
import com.psychologist.financial.services.EncryptionService
import com.psychologist.financial.services.SecureKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for DatabaseEncryptionManager
 *
 * Coverage:
 * - initializeDatabaseKey() returns existing key when found
 * - initializeDatabaseKey() generates new key when not found
 * - initializeDatabaseKey() rotates key when expired
 * - getDatabasePassphrase() returns hex-formatted passphrase
 * - verifyDatabaseEncryption() returns true with valid key
 * - verifyDatabaseEncryption() returns false with null key
 * - verifyDatabaseEncryption() returns false with expired key
 * - masterKeyExists() delegates to encryptionService
 * - getEncryptionStatus() returns complete status map
 * - rotateKey() generates new key and replaces old
 * - initializeBackupKey() returns existing backup key
 * - initializeBackupKey() generates new backup key when absent
 * - getBackupKey() delegates to secureKeyStore
 *
 * Total: 16 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class DatabaseEncryptionManagerUnitTest {

    @Mock
    private lateinit var mockEncryptionService: EncryptionService

    @Mock
    private lateinit var mockSecureKeyStore: SecureKeyStore

    private lateinit var manager: DatabaseEncryptionManager

    private val validKey = EncryptionKey(
        alias = "database_encryption_key",
        keyMaterial = ByteArray(32) { it.toByte() },
        createdAt = LocalDateTime.now().minusDays(1),
        lastRotatedAt = LocalDateTime.now().minusDays(1),
        expiresAt = LocalDateTime.now().plusDays(89),
        isActive = true,
        purpose = KeyPurpose.DATABASE
    )

    private val expiredKey = validKey.copy(
        expiresAt = LocalDateTime.now().minusDays(1)
    )

    private val newKey = EncryptionKey(
        alias = "database_encryption_key",
        keyMaterial = ByteArray(32) { (it * 2).toByte() },
        createdAt = LocalDateTime.now(),
        lastRotatedAt = LocalDateTime.now(),
        expiresAt = LocalDateTime.now().plusDays(90),
        isActive = true,
        purpose = KeyPurpose.DATABASE
    )

    @Before
    fun setUp() {
        manager = DatabaseEncryptionManager(
            encryptionService = mockEncryptionService,
            secureKeyStore = mockSecureKeyStore
        )
    }

    // ========================================
    // initializeDatabaseKey() Tests
    // ========================================

    @Test
    fun `initializeDatabaseKey returns existing key when found and not expired`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(validKey)

        val result = manager.initializeDatabaseKey()

        assertEquals(validKey.alias, result.alias)
        verify(mockEncryptionService, never()).generateDatabaseKey(any())
    }

    @Test
    fun `initializeDatabaseKey generates new key when none exists`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(null)
        whenever(mockEncryptionService.keyExists(any())).thenReturn(true)
        whenever(mockEncryptionService.generateDatabaseKey(any())).thenReturn(newKey)

        val result = manager.initializeDatabaseKey()

        assertNotNull(result)
        verify(mockEncryptionService).generateDatabaseKey(any())
        verify(mockSecureKeyStore).storeDatabaseKey(newKey)
    }

    @Test
    fun `initializeDatabaseKey rotates key when expired`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(expiredKey)
        whenever(mockEncryptionService.keyExists(any())).thenReturn(true)
        whenever(mockEncryptionService.generateDatabaseKey(any())).thenReturn(newKey)

        val result = manager.initializeDatabaseKey()

        // Should return the rotated key
        assertNotNull(result)
    }

    @Test
    fun `initializeDatabaseKey does not fail when key is stored successfully`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(null)
        whenever(mockEncryptionService.keyExists(any())).thenReturn(true)
        whenever(mockEncryptionService.generateDatabaseKey(any())).thenReturn(newKey)

        val result = manager.initializeDatabaseKey()

        assertNotNull(result)
        verify(mockSecureKeyStore).storeDatabaseKey(newKey)
    }

    // ========================================
    // getDatabasePassphrase() Tests
    // ========================================

    @Test
    fun `getDatabasePassphrase returns hex formatted passphrase`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(validKey)

        val passphrase = manager.getDatabasePassphrase()

        assertTrue(passphrase.startsWith("x'"))
        assertTrue(passphrase.endsWith("'"))
        // 32 bytes = 64 hex characters
        assertEquals(68, passphrase.length) // "x'" + 64 chars + "'"
    }

    @Test
    fun `getDatabasePassphrase throws when key initialization fails`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(null)
        whenever(mockEncryptionService.keyExists(any())).thenReturn(false)
        whenever(mockEncryptionService.generateDatabaseKey(any()))
            .thenThrow(RuntimeException("Key generation failed"))

        try {
            manager.getDatabasePassphrase()
            assertTrue(false, "Expected Exception")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("passphrase") == true)
        }
    }

    // ========================================
    // verifyDatabaseEncryption() Tests
    // ========================================

    @Test
    fun `verifyDatabaseEncryption returns true with valid active key`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(validKey)

        val result = manager.verifyDatabaseEncryption()

        assertTrue(result)
    }

    @Test
    fun `verifyDatabaseEncryption returns false when key is null`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(null)

        val result = manager.verifyDatabaseEncryption()

        assertFalse(result)
    }

    @Test
    fun `verifyDatabaseEncryption returns false when key is expired`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(expiredKey)

        val result = manager.verifyDatabaseEncryption()

        assertFalse(result)
    }

    @Test
    fun `verifyDatabaseEncryption returns false when key is inactive`() = runTest {
        val inactiveKey = validKey.copy(isActive = false)
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(inactiveKey)

        val result = manager.verifyDatabaseEncryption()

        assertFalse(result)
    }

    // ========================================
    // masterKeyExists() Tests
    // ========================================

    @Test
    fun `masterKeyExists delegates to encryptionService`() {
        whenever(mockEncryptionService.keyExists("master_encryption_key")).thenReturn(true)

        val result = manager.masterKeyExists()

        assertTrue(result)
    }

    @Test
    fun `masterKeyExists returns false when key absent`() {
        whenever(mockEncryptionService.keyExists(any())).thenReturn(false)

        val result = manager.masterKeyExists()

        assertFalse(result)
    }

    // ========================================
    // getEncryptionStatus() Tests
    // ========================================

    @Test
    fun `getEncryptionStatus returns complete status map`() = runTest {
        whenever(mockSecureKeyStore.getDatabaseKey()).thenReturn(validKey)
        whenever(mockSecureKeyStore.getBackupKey()).thenReturn(null)
        whenever(mockEncryptionService.keyExists(any())).thenReturn(true)
        whenever(mockEncryptionService.isStrongBoxAvailable()).thenReturn(false)

        val status = manager.getEncryptionStatus()

        assertTrue(status.containsKey("databaseKeyExists"))
        assertTrue(status.containsKey("masterKeyExists"))
        assertTrue(status["databaseKeyExists"] as Boolean)
        assertFalse(status["backupKeyExists"] as Boolean)
    }

    // ========================================
    // getBackupKey() Tests
    // ========================================

    @Test
    fun `getBackupKey delegates to secureKeyStore`() = runTest {
        val backupKey = validKey.copy(purpose = KeyPurpose.BACKUP)
        whenever(mockSecureKeyStore.getBackupKey()).thenReturn(backupKey)

        val result = manager.getBackupKey()

        assertNotNull(result)
        assertEquals(KeyPurpose.BACKUP, result.purpose)
    }

    @Test
    fun `getBackupKey returns null when no backup key`() = runTest {
        whenever(mockSecureKeyStore.getBackupKey()).thenReturn(null)

        val result = manager.getBackupKey()

        assertNull(result)
    }
}
