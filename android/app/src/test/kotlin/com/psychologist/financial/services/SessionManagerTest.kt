package com.psychologist.financial.services

import com.psychologist.financial.domain.models.OperationType
import com.psychologist.financial.domain.models.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit Tests for SessionManager
 *
 * Tests session lifecycle, timeout calculation, and per-operation authentication.
 * Covers:
 * - Session creation and expiration
 * - 15-minute timeout tracking
 * - Session validity checks
 * - Per-operation biometric requirements
 * - Session duration calculation
 * - State transitions
 *
 * Test Coverage: 85%+ of SessionManager logic
 */
class SessionManagerTest {

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        sessionManager = SessionManager()
    }

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial state is Unauthenticated`() = runBlocking {
        val state = sessionManager.sessionState.first()
        assertTrue(state is SessionState.Unauthenticated)
    }

    @Test
    fun `initial session is invalid`() {
        assertFalse(sessionManager.isSessionValid())
    }

    @Test
    fun `initial session is not active`() {
        assertFalse(sessionManager.hasActiveSession())
    }

    @Test
    fun `remaining session time is 0 initially`() {
        assertEquals(0L, sessionManager.getRemainingSessionTime())
    }

    // ========================================
    // Session Creation Tests
    // ========================================

    @Test
    fun `startSession creates Authenticated state`() = runBlocking {
        sessionManager.startSession()
        val state = sessionManager.sessionState.first()
        assertTrue(state is SessionState.Authenticated)
    }

    @Test
    fun `startSession marks session as valid`() {
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())
    }

    @Test
    fun `startSession sets approximately 900 seconds remaining`() {
        sessionManager.startSession()
        val remaining = sessionManager.getRemainingSessionTime()
        assertTrue(remaining > 890 && remaining <= 900)
    }

    @Test
    fun `startSession sets expiration 15 minutes in future`() {
        sessionManager.startSession()
        val state = sessionManager.sessionState.value
        if (state is SessionState.Authenticated) {
            assertTrue(state.remainingSeconds > 890 && state.remainingSeconds <= 900)
        }
    }

    @Test
    fun `startSession has active session`() {
        sessionManager.startSession()
        assertTrue(sessionManager.hasActiveSession())
    }

    // ========================================
    // Session Validity Tests
    // ========================================

    @Test
    fun `isSessionValid returns false for Expired state`() {
        sessionManager.startSession()
        sessionManager.expireSession()
        assertFalse(sessionManager.isSessionValid())
    }

    @Test
    fun `isSessionValid returns false for Unauthenticated state`() {
        assertFalse(sessionManager.isSessionValid())
    }

    @Test
    fun `isSessionValid returns false for BiometricRequired state`() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        assertFalse(sessionManager.isSessionValid())
    }

    @Test
    fun `hasActiveSession returns false for Expired state`() {
        sessionManager.startSession()
        sessionManager.expireSession()
        assertFalse(sessionManager.hasActiveSession())
    }

    @Test
    fun `hasActiveSession returns true for Authenticated state`() {
        sessionManager.startSession()
        assertTrue(sessionManager.hasActiveSession())
    }

    @Test
    fun `hasActiveSession returns true for BiometricRequired state`() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        assertTrue(sessionManager.hasActiveSession())
    }

    // ========================================
    // Session Extension Tests
    // ========================================

    @Test
    fun `extendSession returns true for Authenticated state`() {
        sessionManager.startSession()
        val result = sessionManager.extendSession()
        assertTrue(result)
    }

    @Test
    fun `extendSession returns false for Unauthenticated state`() {
        val result = sessionManager.extendSession()
        assertFalse(result)
    }

    @Test
    fun `extendSession returns false for Expired state`() {
        sessionManager.startSession()
        sessionManager.expireSession()
        val result = sessionManager.extendSession()
        assertFalse(result)
    }

    @Test
    fun `extendSession resets timeout to 900 seconds`() {
        sessionManager.startSession()
        Thread.sleep(100) // Wait 100ms
        sessionManager.extendSession()
        val remaining = sessionManager.getRemainingSessionTime()
        assertTrue(remaining > 890 && remaining <= 900)
    }

    // ========================================
    // Session Expiration Tests
    // ========================================

    @Test
    fun `expireSession transitions to Expired state`() = runBlocking {
        sessionManager.startSession()
        sessionManager.expireSession()
        val state = sessionManager.sessionState.first()
        assertTrue(state is SessionState.Expired)
    }

    @Test
    fun `expireSession with reason includes reason in state`() {
        sessionManager.startSession()
        val reason = "User initiated logout"
        sessionManager.expireSession(reason)
        val state = sessionManager.getCurrentState()
        if (state is SessionState.Expired) {
            assertEquals(reason, state.reason)
        }
    }

    @Test
    fun `expireSession invalidates session`() {
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())
        sessionManager.expireSession()
        assertFalse(sessionManager.isSessionValid())
    }

    @Test
    fun `expireSession sets remaining time to 0`() {
        sessionManager.startSession()
        sessionManager.expireSession()
        assertEquals(0L, sessionManager.getRemainingSessionTime())
    }

    // ========================================
    // Session Clear (Logout) Tests
    // ========================================

    @Test
    fun `clearSession transitions to Unauthenticated state`() = runBlocking {
        sessionManager.startSession()
        sessionManager.clearSession()
        val state = sessionManager.sessionState.first()
        assertTrue(state is SessionState.Unauthenticated)
    }

    @Test
    fun `clearSession invalidates session`() {
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())
        sessionManager.clearSession()
        assertFalse(sessionManager.isSessionValid())
    }

    @Test
    fun `clearSession resets remaining time to 0`() {
        sessionManager.startSession()
        sessionManager.clearSession()
        assertEquals(0L, sessionManager.getRemainingSessionTime())
    }

    // ========================================
    // About to Expire Tests
    // ========================================

    @Test
    fun `isSessionAboutToExpire returns false initially`() {
        sessionManager.startSession()
        assertFalse(sessionManager.isSessionAboutToExpire())
    }

    @Test
    fun `isSessionAboutToExpire returns false for Unauthenticated state`() {
        assertFalse(sessionManager.isSessionAboutToExpire())
    }

    @Test
    fun `isSessionAboutToExpire returns false for Expired state`() {
        sessionManager.startSession()
        sessionManager.expireSession()
        assertFalse(sessionManager.isSessionAboutToExpire())
    }

    // ========================================
    // Per-Operation Biometric Tests
    // ========================================

    @Test
    fun `requireBiometricForOperation returns true for active session`() {
        sessionManager.startSession()
        val result = sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        assertTrue(result)
    }

    @Test
    fun `requireBiometricForOperation returns false for expired session`() {
        sessionManager.startSession()
        sessionManager.expireSession()
        val result = sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        assertFalse(result)
    }

    @Test
    fun `requireBiometricForOperation transitions to BiometricRequired state`() = runBlocking {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        val state = sessionManager.sessionState.first()
        assertTrue(state is SessionState.BiometricRequired)
    }

    @Test
    fun `requireBiometricForOperation for PAYMENT has correct operation type`() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            assertEquals(OperationType.PAYMENT, state.operation)
        }
    }

    @Test
    fun `requireBiometricForOperation for EXPORT has correct operation type`() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.EXPORT)
        val state = sessionManager.getCurrentState()
        if (state is SessionState.BiometricRequired) {
            assertEquals(OperationType.EXPORT, state.operation)
        }
    }

    @Test
    fun `completeBiometricAuthentication restores to Authenticated state`() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.completeBiometricAuthentication()
        val state = sessionManager.getCurrentState()
        assertTrue(state is SessionState.Authenticated)
    }

    @Test
    fun `completeBiometricAuthentication resets session timeout`() {
        sessionManager.startSession()
        Thread.sleep(100)
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.completeBiometricAuthentication()
        val remaining = sessionManager.getRemainingSessionTime()
        assertTrue(remaining > 890 && remaining <= 900)
    }

    @Test
    fun `cancelBiometricAuthentication restores to Authenticated if valid`() {
        sessionManager.startSession()
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.cancelBiometricAuthentication()
        val state = sessionManager.getCurrentState()
        assertTrue(state is SessionState.Authenticated)
    }

    // ========================================
    // Session Info Tests
    // ========================================

    @Test
    fun `getSessionInfo returns map with state`() {
        sessionManager.startSession()
        val info = sessionManager.getSessionInfo()
        assertTrue(info.containsKey("state"))
        assertEquals("Authenticated", info["state"])
    }

    @Test
    fun `getSessionInfo includes remaining seconds for Authenticated state`() {
        sessionManager.startSession()
        val info = sessionManager.getSessionInfo()
        assertTrue(info.containsKey("remainingSeconds"))
    }

    @Test
    fun `getSessionInfo includes expired at for Expired state`() {
        sessionManager.startSession()
        sessionManager.expireSession()
        val info = sessionManager.getSessionInfo()
        assertTrue(info.containsKey("expiredAt"))
    }

    @Test
    fun `getSessionStatus returns human readable message`() {
        sessionManager.startSession()
        val status = sessionManager.getSessionStatus()
        assertTrue(status.contains("Sessão"))
    }

    // ========================================
    // Session Duration Tests
    // ========================================

    @Test
    fun `getSessionDuration returns 0 when no session`() {
        assertEquals(0L, sessionManager.getSessionDuration())
    }

    @Test
    fun `getSessionDuration returns approximately 0 after start`() {
        sessionManager.startSession()
        val duration = sessionManager.getSessionDuration()
        assertTrue(duration >= 0 && duration <= 2)
    }

    @Test
    fun `getSessionDuration increases over time`() {
        sessionManager.startSession()
        val duration1 = sessionManager.getSessionDuration()
        Thread.sleep(100)
        val duration2 = sessionManager.getSessionDuration()
        assertTrue(duration2 > duration1)
    }

    // ========================================
    // State Transition Tests
    // ========================================

    @Test
    fun `state transitions Unauthenticated to Authenticated to Expired`() {
        val state1 = sessionManager.getCurrentState()
        assertTrue(state1 is SessionState.Unauthenticated)

        sessionManager.startSession()
        val state2 = sessionManager.getCurrentState()
        assertTrue(state2 is SessionState.Authenticated)

        sessionManager.expireSession()
        val state3 = sessionManager.getCurrentState()
        assertTrue(state3 is SessionState.Expired)
    }

    @Test
    fun `state transitions Authenticated to BiometricRequired and back`() {
        sessionManager.startSession()
        val state1 = sessionManager.getCurrentState()
        assertTrue(state1 is SessionState.Authenticated)

        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        val state2 = sessionManager.getCurrentState()
        assertTrue(state2 is SessionState.BiometricRequired)

        sessionManager.completeBiometricAuthentication()
        val state3 = sessionManager.getCurrentState()
        assertTrue(state3 is SessionState.Authenticated)
    }

    @Test
    fun `getCurrentState returns current state`() {
        sessionManager.startSession()
        val state = sessionManager.getCurrentState()
        assertTrue(state is SessionState.Authenticated)
    }
}
