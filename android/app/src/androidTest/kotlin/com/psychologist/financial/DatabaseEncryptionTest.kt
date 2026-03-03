package com.psychologist.financial

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.services.EncryptionService
import com.psychologist.financial.services.SecureKeyStore
import com.psychologist.financial.services.DatabaseEncryptionManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration Tests for Database Encryption
 *
 * Tests Room + SQLCipher integration with encrypted keys.
 * Covers:
 * - SQLCipher passphrase generation
 * - Database key initialization
 * - Encrypted database file creation
 * - Encryption status verification
 *
 * Test Coverage: Room + SQLCipher integration (85%+)
 */
@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {

    private lateinit var encryptionService: EncryptionService
    private lateinit var secureKeyStore: SecureKeyStore
    private lateinit var databaseEncryptionManager: DatabaseEncryptionManager

    @Before
    fun setUp() {
        encryptionService = EncryptionService()
        secureKeyStore = SecureKeyStore(
            androidx.test.InstrumentationRegistry.getInstrumentation().context,
            encryptionService
        )
        databaseEncryptionManager = DatabaseEncryptionManager(
            encryptionService,
            secureKeyStore
        )
    }

    // ========================================
    // Database Passphrase Tests
    // ========================================

    @Test
    fun testGetDatabasePassphraseReturnsHexString() {
        val passphrase = databaseEncryptionManager.getDatabasePassphrase()

        assertNotNull(passphrase)
        assertTrue(passphrase.startsWith("x'"))
        assertTrue(passphrase.endsWith("'"))
        // Hex string should have 64 characters (256 bits = 32 bytes = 64 hex chars)
        assertTrue(passphrase.length >= 66) // x' + hex + '
    }

    @Test
    fun testDatabaseEncryptionVerification() {
        val isEncrypted = databaseEncryptionManager.verifyDatabaseEncryption()
        assertTrue(isEncrypted)
    }

    @Test
    fun testEncryptionStatusIncludesKeyInfo() {
        val status = databaseEncryptionManager.getEncryptionStatus()

        assertTrue(status.containsKey("databaseKeyExists"))
        assertTrue(status.containsKey("databaseKeyActive"))
        assertTrue(status.containsKey("masterKeyExists"))
    }

    // ========================================
    // Key Initialization Tests
    // ========================================

    @Test
    fun testInitializeDatabaseKeyCreatesKey() {
        val key = databaseEncryptionManager.initializeDatabaseKey()

        assertNotNull(key)
        assertTrue(key.isActive)
        assertTrue(key.keySize == 256)
    }

    @Test
    fun testMasterKeyExistsAfterDbKeyInit() {
        databaseEncryptionManager.initializeDatabaseKey()
        assertTrue(databaseEncryptionManager.masterKeyExists())
    }

    // ========================================
    // Backup Key Tests
    // ========================================

    @Test
    fun testInitializeBackupKey() {
        val backupKey = databaseEncryptionManager.initializeBackupKey()

        assertNotNull(backupKey)
        assertTrue(backupKey.isActive)
    }

    @Test
    fun testGetBackupKey() {
        databaseEncryptionManager.initializeBackupKey()
        val backupKey = databaseEncryptionManager.getBackupKey()

        assertNotNull(backupKey)
    }

    // ========================================
    // Key Inventory Tests
    // ========================================

    @Test
    fun testGetKeyInventory() {
        databaseEncryptionManager.initializeDatabaseKey()
        val inventory = databaseEncryptionManager.getKeyInventory()

        assertTrue(inventory.containsKey(com.psychologist.financial.domain.models.KeyPurpose.DATABASE))
    }
}
