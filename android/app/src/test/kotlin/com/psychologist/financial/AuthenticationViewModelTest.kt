package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.domain.models.BiometricAuthResult
import com.psychologist.financial.services.BiometricAuthManager
import com.psychologist.financial.viewmodel.AuthState
import com.psychologist.financial.viewmodel.AuthenticationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for AuthenticationViewModel
 *
 * Tests the authentication state machine and session management.
 * Covers:
 * - startAuthentication() with biometric available/unavailable/not enrolled
 * - Authentication result handling (Success, UserCancelled, NeedsFallback, Error, Unavailable)
 * - PIN fallback flow (proceedWithPIN, validateAndCompletePIN)
 * - Session management (checkSessionValidity, extendSession, logout)
 * - Biometric enrollment flow
 * - Error dismissal
 *
 * Total: 20 test cases
 * Coverage target: 85%+ of AuthenticationViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockBiometricAuthManager: BiometricAuthManager

    private lateinit var viewModel: AuthenticationViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        viewModel = AuthenticationViewModel(mockBiometricAuthManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial auth state is Idle`() {
        assertEquals(AuthState.Idle, viewModel.authState.value)
    }

    @Test
    fun `isBiometricEnrolled is set during initialization`() {
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        viewModel = AuthenticationViewModel(mockBiometricAuthManager)
        assertTrue(viewModel.isBiometricEnrolled.value)
    }

    @Test
    fun `isBiometricEnrolled is false when not enrolled`() {
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(false)
        viewModel = AuthenticationViewModel(mockBiometricAuthManager)
        assertFalse(viewModel.isBiometricEnrolled.value)
    }

    // ========================================
    // startAuthentication() Tests
    // ========================================

    @Test
    fun `startAuthentication with biometric not available transitions to NeedsEnrollment`() {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(false)
        whenever(mockBiometricAuthManager.getBiometricStatus()).thenReturn("Dispositivo não possui hardware biométrico")

        viewModel.startAuthentication()

        assertTrue(viewModel.authState.value is AuthState.NeedsEnrollment)
    }

    @Test
    fun `startAuthentication with biometric not enrolled transitions to NeedsEnrollment`() {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(true)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(false)
        whenever(mockBiometricAuthManager.getBiometricStatus()).thenReturn("status")

        viewModel.startAuthentication()

        assertTrue(viewModel.authState.value is AuthState.NeedsEnrollment)
        val state = viewModel.authState.value as AuthState.NeedsEnrollment
        assertTrue(state.message.contains("biometria"))
    }

    @Test
    fun `startAuthentication with biometric available transitions to Authenticating`() = runTest {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(true)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        whenever(mockBiometricAuthManager.authenticate()).thenReturn(BiometricAuthResult.Success())

        viewModel.startAuthentication()
        testDispatcher.scheduler.advanceUntilIdle()

        // After success, transitions to Authenticated
        assertEquals(AuthState.Authenticated, viewModel.authState.value)
    }

    // ========================================
    // Authentication Result Tests
    // ========================================

    @Test
    fun `authentication success transitions to Authenticated and extends session`() = runTest {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(true)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        whenever(mockBiometricAuthManager.authenticate()).thenReturn(BiometricAuthResult.Success())

        viewModel.startAuthentication()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthState.Authenticated, viewModel.authState.value)
        verify(mockBiometricAuthManager).extendSession()
    }

    @Test
    fun `authentication UserCancelled transitions to UserCancelled state`() = runTest {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(true)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        whenever(mockBiometricAuthManager.authenticate()).thenReturn(BiometricAuthResult.UserCancelled())

        viewModel.startAuthentication()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthState.UserCancelled, viewModel.authState.value)
    }

    @Test
    fun `authentication NeedsFallback transitions to NeedsPIN state`() = runTest {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(true)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        whenever(mockBiometricAuthManager.authenticate()).thenReturn(
            BiometricAuthResult.NeedsFallback("Biometria falhou", retryCount = 2)
        )

        viewModel.startAuthentication()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.NeedsPIN)
        assertEquals(2, viewModel.authAttempts.value)
    }

    @Test
    fun `authentication Error increments authAttempts`() = runTest {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(true)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        whenever(mockBiometricAuthManager.authenticate()).thenReturn(
            BiometricAuthResult.Error("Erro de hardware", errorCode = 1)
        )

        viewModel.startAuthentication()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.Error)
        assertEquals(1, viewModel.authAttempts.value)
    }

    @Test
    fun `authentication Unavailable with fallback transitions to NeedsPIN`() = runTest {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(true)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        whenever(mockBiometricAuthManager.authenticate()).thenReturn(
            BiometricAuthResult.Unavailable("Hardware indisponível", canUseFallback = true)
        )

        viewModel.startAuthentication()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.NeedsPIN)
    }

    @Test
    fun `authentication Unavailable without fallback transitions to NeedsEnrollment`() = runTest {
        whenever(mockBiometricAuthManager.isBiometricAvailable()).thenReturn(true)
        whenever(mockBiometricAuthManager.isBiometricEnrolled()).thenReturn(true)
        whenever(mockBiometricAuthManager.authenticate()).thenReturn(
            BiometricAuthResult.Unavailable("Sem hardware", canUseFallback = false)
        )

        viewModel.startAuthentication()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.NeedsEnrollment)
    }

    // ========================================
    // PIN Fallback Tests
    // ========================================

    @Test
    fun `proceedWithPIN transitions to EnteringPIN state`() {
        viewModel.proceedWithPIN()
        assertEquals(AuthState.EnteringPIN, viewModel.authState.value)
    }

    @Test
    fun `validateAndCompletePIN with valid 4-digit PIN transitions to Authenticated`() {
        viewModel.validateAndCompletePIN("1234")
        assertEquals(AuthState.Authenticated, viewModel.authState.value)
        verify(mockBiometricAuthManager).extendSession()
    }

    @Test
    fun `validateAndCompletePIN with valid 6-digit PIN transitions to Authenticated`() {
        viewModel.validateAndCompletePIN("123456")
        assertEquals(AuthState.Authenticated, viewModel.authState.value)
    }

    @Test
    fun `validateAndCompletePIN with PIN too short transitions to PINError`() {
        viewModel.validateAndCompletePIN("123")
        assertTrue(viewModel.authState.value is AuthState.PINError)
    }

    @Test
    fun `validateAndCompletePIN with PIN containing letters transitions to PINError`() {
        viewModel.validateAndCompletePIN("12ab")
        assertTrue(viewModel.authState.value is AuthState.PINError)
        val state = viewModel.authState.value as AuthState.PINError
        assertTrue(state.message.contains("dígito"))
    }

    // ========================================
    // Session Management Tests
    // ========================================

    @Test
    fun `checkSessionValidity with valid session transitions to Authenticated`() {
        whenever(mockBiometricAuthManager.isSessionValid()).thenReturn(true)
        whenever(mockBiometricAuthManager.getRemainingSessionTime()).thenReturn(800L)

        viewModel.checkSessionValidity()

        assertEquals(AuthState.Authenticated, viewModel.authState.value)
        assertEquals(800L, viewModel.remainingSessionTime.value)
    }

    @Test
    fun `checkSessionValidity with expired session transitions to SessionExpired`() {
        whenever(mockBiometricAuthManager.isSessionValid()).thenReturn(false)

        viewModel.checkSessionValidity()

        assertEquals(AuthState.SessionExpired, viewModel.authState.value)
    }

    @Test
    fun `logout transitions to LoggedOut and clears session`() {
        viewModel.logout()

        assertEquals(AuthState.LoggedOut, viewModel.authState.value)
        verify(mockBiometricAuthManager).clearSession()
    }

    @Test
    fun `dismissError transitions to Idle`() {
        viewModel.dismissError()
        assertEquals(AuthState.Idle, viewModel.authState.value)
    }

    // ========================================
    // Biometric Status Tests
    // ========================================

    @Test
    fun `getBiometricStatus delegates to BiometricAuthManager`() {
        whenever(mockBiometricAuthManager.getBiometricStatus()).thenReturn("Autenticação biométrica disponível")

        val status = viewModel.getBiometricStatus()

        assertEquals("Autenticação biométrica disponível", status)
    }
}
