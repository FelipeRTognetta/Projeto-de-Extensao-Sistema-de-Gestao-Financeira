package com.psychologist.financial.services

import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import com.psychologist.financial.domain.models.BiometricAuthResult
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mockStatic
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for PerOperationAuthManager
 *
 * Tests per-operation biometric authentication (Class 3, no PIN fallback).
 * Covers:
 * - Class 3 biometric availability checks
 * - Operation biometric status messages
 * - CryptoObject creation (payment + export)
 * - Authentication when biometric not available (returns Unavailable)
 * - Error message translation for payment and export operations
 *
 * Total: 18 test cases
 * Coverage target: 85%+ of PerOperationAuthManager
 */
@RunWith(MockitoJUnitRunner::class)
class PerOperationAuthManagerTest {

    @Mock
    private lateinit var fragmentActivity: FragmentActivity

    @Mock
    private lateinit var biometricManager: BiometricManager

    private lateinit var perOpAuthManager: PerOperationAuthManager

    @Before
    fun setUp() {
        perOpAuthManager = PerOperationAuthManager(fragmentActivity)
    }

    // ========================================
    // Class 3 Biometric Availability Tests
    // ========================================

    @Test
    fun `isClass3BiometricAvailable returns true when strong biometric available`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_SUCCESS)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            assertTrue(perOpAuthManager.isClass3BiometricAvailable())
        }
    }

    @Test
    fun `isClass3BiometricAvailable returns false when biometric not enrolled`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            assertFalse(perOpAuthManager.isClass3BiometricAvailable())
        }
    }

    @Test
    fun `isClass3BiometricAvailable returns false when no hardware`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            assertFalse(perOpAuthManager.isClass3BiometricAvailable())
        }
    }

    @Test
    fun `isClass3BiometricAvailable returns false when hardware unavailable`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            assertFalse(perOpAuthManager.isClass3BiometricAvailable())
        }
    }

    @Test
    fun `isClass3BiometricAvailable returns false on exception`() {
        `when`(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenThrow(RuntimeException("Biometric check failed"))

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            assertFalse(perOpAuthManager.isClass3BiometricAvailable())
        }
    }

    // ========================================
    // Operation Biometric Status Message Tests
    // ========================================

    @Test
    fun `getOperationBiometricStatus returns available message when success`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_SUCCESS)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val status = perOpAuthManager.getOperationBiometricStatus()
            assertEquals("Biometria de segurança disponível", status)
        }
    }

    @Test
    fun `getOperationBiometricStatus returns no hardware message`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val status = perOpAuthManager.getOperationBiometricStatus()
            assertEquals("Dispositivo não possui hardware biométrico requerido", status)
        }
    }

    @Test
    fun `getOperationBiometricStatus returns hw unavailable message`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val status = perOpAuthManager.getOperationBiometricStatus()
            assertEquals("Hardware biométrico indisponível", status)
        }
    }

    @Test
    fun `getOperationBiometricStatus returns not enrolled message`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val status = perOpAuthManager.getOperationBiometricStatus()
            assertEquals("Biometria requerida não cadastrada", status)
        }
    }

    @Test
    fun `getOperationBiometricStatus returns security update message`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val status = perOpAuthManager.getOperationBiometricStatus()
            assertEquals("Atualização de segurança necessária", status)
        }
    }

    @Test
    fun `getOperationBiometricStatus handles exception gracefully`() {
        `when`(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenThrow(RuntimeException("Status check error"))

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val status = perOpAuthManager.getOperationBiometricStatus()
            assertEquals("Erro ao verificar biometria", status)
        }
    }

    // ========================================
    // CryptoObject Creation Tests
    // ========================================

    @Test
    fun `createPaymentCryptoObject returns null (stub implementation)`() {
        // Production stub returns null; actual implementation would create CryptoObject
        val cryptoObject = perOpAuthManager.createPaymentCryptoObject()
        assertNull(cryptoObject)
    }

    @Test
    fun `createExportCryptoObject returns null (stub implementation)`() {
        // Production stub returns null; actual implementation would create CryptoObject
        val cryptoObject = perOpAuthManager.createExportCryptoObject()
        assertNull(cryptoObject)
    }

    // ========================================
    // Authentication When Biometric Unavailable
    // ========================================

    @Test
    fun `authenticatePayment returns Unavailable when Class3 biometric not available`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val result = runBlocking {
                perOpAuthManager.authenticatePayment()
            }

            assertTrue(result is BiometricAuthResult.Unavailable)
            val unavailable = result as BiometricAuthResult.Unavailable
            assertFalse(unavailable.canUseFallback)
        }
    }

    @Test
    fun `authenticateExport returns Unavailable when Class3 biometric not available`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val result = runBlocking {
                perOpAuthManager.authenticateExport()
            }

            assertTrue(result is BiometricAuthResult.Unavailable)
            val unavailable = result as BiometricAuthResult.Unavailable
            assertFalse(unavailable.canUseFallback)
        }
    }

    @Test
    fun `authenticatePayment Unavailable result has descriptive reason message`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val result = runBlocking {
                perOpAuthManager.authenticatePayment()
            }

            assertTrue(result is BiometricAuthResult.Unavailable)
            val unavailable = result as BiometricAuthResult.Unavailable
            assertTrue(unavailable.reason.isNotEmpty())
        }
    }

    @Test
    fun `authenticateExport Unavailable result has descriptive reason message`() {
        mockStrongBiometric(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

        mockStatic(BiometricManager::class.java).use { mocked ->
            mocked.`when`<BiometricManager> { BiometricManager.from(fragmentActivity) }
                .thenReturn(biometricManager)

            val result = runBlocking {
                perOpAuthManager.authenticateExport()
            }

            assertTrue(result is BiometricAuthResult.Unavailable)
            val unavailable = result as BiometricAuthResult.Unavailable
            assertTrue(unavailable.reason.isNotEmpty())
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun mockStrongBiometric(result: Int) {
        `when`(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
            .thenReturn(result)
    }
}
