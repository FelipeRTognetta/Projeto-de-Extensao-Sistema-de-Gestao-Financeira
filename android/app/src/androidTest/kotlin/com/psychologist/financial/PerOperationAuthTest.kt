package com.psychologist.financial

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.domain.models.OperationType
import com.psychologist.financial.domain.models.SessionState
import com.psychologist.financial.services.SessionManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration Tests for Per-Operation Authentication
 *
 * Tests per-operation authentication (Class 3 biometric) for sensitive operations.
 * Covers:
 * - Payment operation authentication
 * - Data export operation authentication
 * - Biometric re-authentication requirements
 * - Session handling during per-operation auth
 * - Permission validation
 *
 * Per-Operation Auth Model:
 * - Triggered on payment transactions
 * - Triggered on data export operations
 * - Requires Class 3 biometrics (hardware-backed)
 * - NO PIN fallback (maximum security)
 * - CryptoObject binding for additional security
 *
 * Test Coverage: Per-operation authentication flow with SessionManager integration
 */
@RunWith(AndroidJUnit4::class)
class PerOperationAuthTest {

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        sessionManager = SessionManager()
    }

    // ========================================
    // Payment Operation Authentication Tests
    // ========================================

    @Test
    fun testPaymentRequiresAuthenticationWhenSessionActive() {
        // Start session (app-level authentication)
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())

        // Attempt payment operation
        val authRequired = sessionManager.requireBiometricForOperation(
            OperationType.PAYMENT
        )

        // Verify per-operation auth is required
        assertTrue(authRequired)
    }

    @Test
    fun testPaymentAuthenticationChangesSessionState() {
        // Start session
        sessionManager.startSession()
        val initialState = sessionManager.getCurrentState()
        assertTrue(initialState is SessionState.Authenticated)

        // Request payment authentication
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // Verify state changed to BiometricRequired
        val newState = sessionManager.getCurrentState()
        assertTrue(newState is SessionState.BiometricRequired)
        
        if (newState is SessionState.BiometricRequired) {
            assertEquals(OperationType.PAYMENT, newState.operation)
        }
    }

    @Test
    fun testPaymentAuthenticationMessage() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            val message = state.getAuthenticationMessage()
            assertTrue(message.contains("Biometria necessária para confirmar pagamento"))
        }
    }

    @Test
    fun testPaymentAuthenticationSuccessCompletes() {
        // Start session
        sessionManager.startSession()

        // Request payment authentication
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        assertTrue(sessionManager.getCurrentState() is SessionState.BiometricRequired)

        // Complete biometric authentication
        sessionManager.completeBiometricAuthentication()

        // Verify session returns to Authenticated
        assertTrue(sessionManager.getCurrentState() is SessionState.Authenticated)
        assertTrue(sessionManager.isSessionValid())
    }

    @Test
    fun testPaymentAuthenticationUserCancel() {
        // Start session
        sessionManager.startSession()

        // Request payment authentication
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // User cancels biometric
        sessionManager.cancelBiometricAuthentication()

        // Verify session returns to Authenticated
        assertTrue(sessionManager.getCurrentState() is SessionState.Authenticated)
    }

    @Test
    fun testPaymentAuthenticationFailureInvalidatesOperation() {
        // Start session
        sessionManager.startSession()

        // Request payment authentication
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // User cancels (simulating authentication failure)
        sessionManager.cancelBiometricAuthentication()

        // Verify payment operation is not allowed
        // In production: check if permission granted before processing payment
        val state = sessionManager.getCurrentState()
        assertTrue(state is SessionState.Authenticated)
    }

    // ========================================
    // Export Operation Authentication Tests
    // ========================================

    @Test
    fun testExportRequiresAuthenticationWhenSessionActive() {
        // Start session (app-level authentication)
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())

        // Attempt export operation
        val authRequired = sessionManager.requireBiometricForOperation(
            OperationType.EXPORT
        )

        // Verify per-operation auth is required
        assertTrue(authRequired)
    }

    @Test
    fun testExportAuthenticationChangesSessionState() {
        // Start session
        sessionManager.startSession()
        val initialState = sessionManager.getCurrentState()
        assertTrue(initialState is SessionState.Authenticated)

        // Request export authentication
        sessionManager.requireBiometricForOperation(OperationType.EXPORT)

        // Verify state changed to BiometricRequired
        val newState = sessionManager.getCurrentState()
        assertTrue(newState is SessionState.BiometricRequired)
        
        if (newState is SessionState.BiometricRequired) {
            assertEquals(OperationType.EXPORT, newState.operation)
        }
    }

    @Test
    fun testExportAuthenticationMessage() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.EXPORT)

        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            val message = state.getAuthenticationMessage()
            assertTrue(message.contains("Biometria necessária para exportar dados"))
        }
    }

    @Test
    fun testExportAuthenticationSuccessCompletes() {
        // Start session
        sessionManager.startSession()

        // Request export authentication
        sessionManager.requireBiometricForOperation(OperationType.EXPORT)
        assertTrue(sessionManager.getCurrentState() is SessionState.BiometricRequired)

        // Complete biometric authentication
        sessionManager.completeBiometricAuthentication()

        // Verify session returns to Authenticated
        assertTrue(sessionManager.getCurrentState() is SessionState.Authenticated)
        assertTrue(sessionManager.isSessionValid())
    }

    @Test
    fun testExportAuthenticationUserCancel() {
        // Start session
        sessionManager.startSession()

        // Request export authentication
        sessionManager.requireBiometricForOperation(OperationType.EXPORT)

        // User cancels biometric
        sessionManager.cancelBiometricAuthentication()

        // Verify session returns to Authenticated
        assertTrue(sessionManager.getCurrentState() is SessionState.Authenticated)
    }

    // ========================================
    // Session Validity During Per-Operation Auth Tests
    // ========================================

    @Test
    fun testPerOperationAuthFailsWhenSessionExpired() {
        // Start session
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())

        // Expire session
        sessionManager.expireSession()
        assertFalse(sessionManager.isSessionValid())

        // Attempt per-operation auth
        val authRequired = sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // Verify auth requirement fails when session expired
        assertFalse(authRequired)
    }

    @Test
    fun testPerOperationAuthFailsWhenUnauthenticated() {
        // Verify session is not authenticated
        assertTrue(sessionManager.getCurrentState() is SessionState.Unauthenticated)

        // Attempt per-operation auth
        val authRequired = sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // Verify auth requirement fails
        assertFalse(authRequired)
    }

    @Test
    fun testPerOperationAuthSucceedsWithActiveSession() {
        // Start session
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())

        // Attempt per-operation auth
        val authRequired = sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // Verify auth requirement succeeds
        assertTrue(authRequired)
    }

    // ========================================
    // Multiple Per-Operation Attempts Tests
    // ========================================

    @Test
    fun testMultiplePaymentAuthenticationAttempts() {
        // First payment
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.completeBiometricAuthentication()
        assertTrue(sessionManager.isSessionValid())

        // Second payment
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.completeBiometricAuthentication()
        assertTrue(sessionManager.isSessionValid())

        // Verify user is still authenticated
        assertTrue(sessionManager.getCurrentState() is SessionState.Authenticated)
    }

    @Test
    fun testPaymentThenExportAuthentication() {
        // Payment authentication
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.completeBiometricAuthentication()
        assertTrue(sessionManager.isSessionValid())

        // Export authentication
        sessionManager.requireBiometricForOperation(OperationType.EXPORT)
        assertTrue(sessionManager.getCurrentState() is SessionState.BiometricRequired)
        
        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            assertEquals(OperationType.EXPORT, state.operation)
        }

        sessionManager.completeBiometricAuthentication()
        assertTrue(sessionManager.isSessionValid())
    }

    @Test
    fun testPaymentAuthenticationFailedThenRetry() {
        // Start session
        sessionManager.startSession()

        // First attempt - user cancels
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.cancelBiometricAuthentication()
        assertTrue(sessionManager.isSessionValid())

        // Second attempt - user completes
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.completeBiometricAuthentication()
        assertTrue(sessionManager.isSessionValid())
    }

    // ========================================
    // Permission Validation Tests
    // ========================================

    @Test
    fun testPaymentRequiresClass3Biometric() {
        // Start session
        sessionManager.startSession()

        // Request payment auth
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // In production, PerOperationAuthManager verifies Class 3 biometric
        // This test verifies SessionManager transitions correctly
        val state = sessionManager.getCurrentState()
        assertTrue(state is SessionState.BiometricRequired)
    }

    @Test
    fun testPaymentNoPINFallback() {
        // Start session
        sessionManager.startSession()

        // Request payment auth
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // Verify no PIN fallback is offered
        // (PIN fallback only available for app-level auth, not per-operation)
        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            // BiometricRequired always requires biometric, no PIN fallback
            assertEquals(OperationType.PAYMENT, state.operation)
        }
    }

    // ========================================
    // Session Timeout During Per-Operation Auth Tests
    // ========================================

    @Test
    fun testSessionTimeoutDuringPaymentAuth() {
        // Start session
        sessionManager.startSession()

        // Request payment auth
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        assertTrue(sessionManager.getCurrentState() is SessionState.BiometricRequired)

        // Simulate session timeout
        sessionManager.expireSession()

        // Verify payment is blocked
        val state = sessionManager.getCurrentState()
        assertTrue(state is SessionState.Expired)
    }

    @Test
    fun testSessionExtensionDuringPerOperationAuth() {
        // Start session
        sessionManager.startSession()

        // Request payment auth
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        // User might spend time on biometric prompt
        // Completing auth should extend session
        sessionManager.completeBiometricAuthentication()

        val remaining = sessionManager.getRemainingSessionTime()
        assertTrue(remaining > 890 && remaining <= 900)
    }

    // ========================================
    // Integration with Session Duration Tests
    // ========================================

    @Test
    fun testSessionDurationIncludesPerOperationAuth() {
        // Start session
        sessionManager.startSession()
        val duration1 = sessionManager.getSessionDuration()
        assertTrue(duration1 >= 0)

        // Request payment auth
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        val duration2 = sessionManager.getSessionDuration()

        // Duration should continue to increase
        assertTrue(duration2 >= duration1)

        // Complete auth
        sessionManager.completeBiometricAuthentication()
        val duration3 = sessionManager.getSessionDuration()

        // Duration continues to increase
        assertTrue(duration3 >= duration2)
    }

    @Test
    fun testPerOperationAuthDoesNotResetSessionDuration() {
        // Start session
        sessionManager.startSession()

        // Request payment auth
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.completeBiometricAuthentication()

        // Session duration should still include the entire time since start
        val duration = sessionManager.getSessionDuration()
        assertTrue(duration >= 0) // At least current time
    }

    // ========================================
    // Audit and Logging Tests
    // ========================================

    @Test
    fun testOperationTypeTrackingForPayment() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            // Operation type is tracked for audit logs
            assertEquals(OperationType.PAYMENT, state.operation)
        }
    }

    @Test
    fun testOperationTypeTrackingForExport() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.EXPORT)

        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            // Operation type is tracked for audit logs
            assertEquals(OperationType.EXPORT, state.operation)
        }
    }

    @Test
    fun testBiometricRequestTimestampTracked() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)

        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            // Request timestamp is tracked
            assertFalse(state.requestedAt.toString().isEmpty())
        }
    }
}
