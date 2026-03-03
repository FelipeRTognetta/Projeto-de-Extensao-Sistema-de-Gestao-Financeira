package com.psychologist.financial.services

import com.psychologist.financial.domain.models.EncryptionKey
import com.psychologist.financial.domain.models.KeyPurpose
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for EncryptionKey model (SecureKeyStore domain objects)
 *
 * Note: SecureKeyStore itself requires Android DataStore (hardware-backed) which
 * cannot be instantiated in pure JVM unit tests without Robolectric. These tests
 * cover the EncryptionKey model and its lifecycle logic which is the core
 * domain logic that SecureKeyStore operates on.
 *
 * For full SecureKeyStore integration tests, see the androidTest/ directory.
 *
 * Coverage:
 * - EncryptionKey.isExpired() for past/future expiry
 * - EncryptionKey.isAboutToExpire() within/outside warning threshold
 * - EncryptionKey.getDaysUntilExpiration() positive/negative
 * - EncryptionKey.getStatus() status string for each lifecycle state
 * - EncryptionKey.isActive flag semantics
 * - EncryptionKey.create() factory populates fields correctly
 * - KeyPurpose enum values coverage
 * - EncryptionKey.getMetadata() completeness
 *
 * Total: 16 test cases
 */
@RunWith(JUnit4::class)
class SecureKeyStoreUnitTest {

    private val now = LocalDateTime.now()

    private fun makeKey(
        alias: String = "test_key",
        expiresAt: LocalDateTime = now.plusDays(90),
        isActive: Boolean = true,
        purpose: KeyPurpose = KeyPurpose.DATABASE
    ) = EncryptionKey(
        alias = alias,
        keyMaterial = ByteArray(32) { it.toByte() },
        createdAt = now,
        lastRotatedAt = now,
        expiresAt = expiresAt,
        isActive = isActive,
        purpose = purpose
    )

    // ========================================
    // isExpired() Tests
    // ========================================

    @Test
    fun `isExpired returns false for key expiring in future`() {
        val key = makeKey(expiresAt = now.plusDays(30))

        assertFalse(key.isExpired())
    }

    @Test
    fun `isExpired returns true for key with past expiry`() {
        val key = makeKey(expiresAt = now.minusDays(1))

        assertTrue(key.isExpired())
    }

    // ========================================
    // isAboutToExpire() Tests
    // ========================================

    @Test
    fun `isAboutToExpire returns true when within 7 days`() {
        val key = makeKey(expiresAt = now.plusDays(3))

        assertTrue(key.isAboutToExpire())
    }

    @Test
    fun `isAboutToExpire returns false when expiry is far future`() {
        val key = makeKey(expiresAt = now.plusDays(60))

        assertFalse(key.isAboutToExpire())
    }

    @Test
    fun `isAboutToExpire returns false when already expired`() {
        val key = makeKey(expiresAt = now.minusDays(1))

        // Expired key is NOT "about to expire" — it IS expired
        assertFalse(key.isAboutToExpire())
    }

    // ========================================
    // getDaysUntilExpiration() Tests
    // ========================================

    @Test
    fun `getDaysUntilExpiration returns positive value for future expiry`() {
        val key = makeKey(expiresAt = now.plusDays(45))

        val days = key.getDaysUntilExpiration()

        assertTrue(days > 0)
        assertTrue(days <= 45)
    }

    @Test
    fun `getDaysUntilExpiration returns -1 when expired`() {
        val key = makeKey(expiresAt = now.minusDays(1))

        val days = key.getDaysUntilExpiration()

        assertEquals(-1L, days)
    }

    // ========================================
    // getStatus() Tests
    // ========================================

    @Test
    fun `getStatus returns active for valid non-expired key`() {
        val key = makeKey(expiresAt = now.plusDays(60))

        val status = key.getStatus()

        assertEquals("Ativo", status)
    }

    @Test
    fun `getStatus returns expired message for past expiry`() {
        val key = makeKey(expiresAt = now.minusDays(1))

        val status = key.getStatus()

        assertTrue(status.contains("Expirado"))
    }

    @Test
    fun `getStatus returns about to expire when within threshold`() {
        val key = makeKey(expiresAt = now.plusDays(3))

        val status = key.getStatus()

        assertTrue(status.contains("Expirando"))
    }

    @Test
    fun `getStatus returns inactive for inactive key`() {
        val key = makeKey(isActive = false)

        val status = key.getStatus()

        assertEquals("Inativo", status)
    }

    // ========================================
    // EncryptionKey.create() Factory Tests
    // ========================================

    @Test
    fun `create factory populates all required fields`() {
        val keyMaterial = ByteArray(32) { it.toByte() }

        val key = EncryptionKey.create(
            alias = "db_key",
            keyMaterial = keyMaterial,
            purpose = KeyPurpose.DATABASE
        )

        assertEquals("db_key", key.alias)
        assertEquals(KeyPurpose.DATABASE, key.purpose)
        assertTrue(key.isActive)
        assertFalse(key.isExpired())
    }

    @Test
    fun `create factory sets expiry to 90 days from now`() {
        val key = EncryptionKey.create("test", ByteArray(32))

        val daysRemaining = key.getDaysUntilExpiration()

        assertTrue(daysRemaining >= 89) // Allow for test execution time
    }

    // ========================================
    // KeyPurpose Tests
    // ========================================

    @Test
    fun `KeyPurpose enum contains all required values`() {
        val purposes = KeyPurpose.values()

        assertTrue(purposes.contains(KeyPurpose.DATABASE))
        assertTrue(purposes.contains(KeyPurpose.BACKUP))
        assertTrue(purposes.contains(KeyPurpose.MASTER))
        assertTrue(purposes.contains(KeyPurpose.TEMPORARY))
    }

    // ========================================
    // getMetadata() Tests
    // ========================================

    @Test
    fun `getMetadata returns complete map with all required keys`() {
        val key = makeKey()

        val metadata = key.getMetadata()

        assertNotNull(metadata["alias"])
        assertNotNull(metadata["algorithm"])
        assertNotNull(metadata["keySize"])
        assertNotNull(metadata["createdAt"])
        assertNotNull(metadata["expiresAt"])
        assertNotNull(metadata["isActive"])
        assertNotNull(metadata["status"])
        assertNotNull(metadata["purpose"])
    }
}
