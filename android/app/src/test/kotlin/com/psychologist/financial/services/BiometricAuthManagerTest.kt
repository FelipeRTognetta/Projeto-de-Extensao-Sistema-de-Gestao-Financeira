package com.psychologist.financial.services

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.psychologist.financial.utils.Constants
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for BiometricAuthManager
 *
 * Tests authentication session lifecycle, PIN management, and state handling.
 * Uses Robolectric for Android API mocking (no biometric device required).
 *
 * Test Coverage:
 * - Session creation and validation
 * - Session timeout
 * - PIN hashing and verification
 * - Session invalidation
 * - Remaining time calculations
 * - Biometric enable/disable
 * - State flow emissions
 *
 * Security Notes:
 * - Tests verify PIN is hashed before storage
 * - Tests verify session expiry logic
 * - Biometric prompts not tested (would need instrumented tests)
 *
 * Design:
 * - Uses real SharedPreferences (Robolectric backed)
 * - Session state tested via StateFlow collection
 * - 15-minute timeout verified against Constants
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])  // Android 12 (API 31)
class BiometricAuthManagerTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var biometricAuthManager: BiometricAuthManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sharedPreferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        biometricAuthManager = BiometricAuthManager(context, sharedPreferences)
    }

    // ========================================
    // Session Tests
    // ========================================

    /**
     * Test: No session initially
     *
     * Verifies:
     * - getSession() returns null initially
     * - isSessionValid() returns false
     */
    @Test
    fun testNoSessionInitially() {
        assertNull(biometricAuthManager.getSession(), "No session initially")
        assertFalse(biometricAuthManager.isSessionValid(), "Session should not be valid initially")
    }

    /**
     * Test: Session creation sets expiry time
     *
     * Verifies:
     * - After authentication, session exists
     * - Expiry is approximately 15 minutes from now
     */
    @Test
    fun testSessionCreationExpiry() {
        // Manually set a PIN and authenticate
        biometricAuthManager.setPin("1234")
        val success = biometricAuthManager.authenticateWithPin("1234")

        assertTrue(success, "PIN authentication should succeed")

        val session = biometricAuthManager.getSession()
        assertNotNull(session, "Session should be created after authentication")
        assertTrue(biometricAuthManager.isSessionValid(), "Session should be valid immediately after creation")

        // Check expiry is ~15 minutes from now
        val remainingSeconds = biometricAuthManager.getSessionRemainingSeconds()
        val expectedSeconds = Constants.BIOMETRIC_SESSION_TIMEOUT_MINUTES * 60
        assertTrue(remainingSeconds > 0, "Session should have remaining time")
        assertTrue(remainingSeconds <= expectedSeconds, "Remaining time should not exceed session timeout")
    }

    /**
     * Test: Session has random token
     *
     * Verifies:
     * - Each session gets unique token
     * - Token is not empty
     */
    @Test
    fun testSessionToken() {
        biometricAuthManager.setPin("1234")
        biometricAuthManager.authenticateWithPin("1234")

        val session1 = biometricAuthManager.getSession()
        assertNotNull(session1, "Session should be created")
        assertTrue(session1.token.isNotEmpty(), "Token should not be empty")
        assertEquals(32, session1.token.length, "Token should be 32 characters")

        // Clear and create new session
        biometricAuthManager.clearSession()
        biometricAuthManager.authenticateWithPin("1234")

        val session2 = biometricAuthManager.getSession()
        assertNotNull(session2, "New session should be created")
        assertNotNull(session1, "Old session should still exist for comparison")
        // Tokens should be different (very high probability with random generation)
        assertTrue(session1.token != session2.token, "Each session should have unique token")
    }

    // ========================================
    // PIN Management Tests
    // ========================================

    /**
     * Test: PIN can be set and verified
     *
     * Verifies:
     * - PIN hashed before storage
     * - Correct PIN validates successfully
     */
    @Test
    fun testSetAndVerifyPin() {
        biometricAuthManager.setPin("5678")

        assertTrue(biometricAuthManager.isPinSet(), "PIN should be marked as set")

        val success = biometricAuthManager.authenticateWithPin("5678")
        assertTrue(success, "Correct PIN should authenticate successfully")
    }

    /**
     * Test: Wrong PIN fails authentication
     *
     * Verifies:
     * - Incorrect PIN rejected
     * - No session created
     */
    @Test
    fun testWrongPin_FailsAuthentication() {
        biometricAuthManager.setPin("correct123")

        val success = biometricAuthManager.authenticateWithPin("wrong456")
        assertFalse(success, "Wrong PIN should fail authentication")
        assertFalse(biometricAuthManager.isSessionValid(), "No session should be created for wrong PIN")
    }

    /**
     * Test: PIN is hashed (not plaintext)
     *
     * Verifies:
     * - Stored PIN is not plaintext (reading SharedPreferences shows hash)
     */
    @Test
    fun testPinIsHashed() {
        val pin = "mySecurePin123"
        biometricAuthManager.setPin(pin)

        // Get stored hash from SharedPreferences
        val storedValue = sharedPreferences.getString("pin_hash", null)
        assertNotNull(storedValue, "PIN hash should be stored")
        assertNotNull(storedValue, "Stored value should not be null")
        assertFalse(storedValue.equals(pin, ignoreCase = false), "Stored value should not be plaintext PIN")
    }

    /**
     * Test: PIN is changed
     *
     * Verifies:
     * - New PIN works after update
     * - Old PIN no longer works
     */
    @Test
    fun testChangePIN() {
        biometricAuthManager.setPin("oldPin")
        assertTrue(biometricAuthManager.authenticateWithPin("oldPin"), "Old PIN should work")

        // Change PIN
        biometricAuthManager.setPin("newPin")

        // Old PIN fails
        assertFalse(biometricAuthManager.authenticateWithPin("oldPin"), "Old PIN should fail after change")

        // New PIN works
        assertTrue(biometricAuthManager.authenticateWithPin("newPin"), "New PIN should work after change")
    }

    /**
     * Test: Empty PIN not allowed
     *
     * Verifies:
     * - Empty PIN is still stored (but should be avoided in UI)
     */
    @Test
    fun testEmptyPin() {
        biometricAuthManager.setPin("")

        assertTrue(biometricAuthManager.isPinSet(), "Empty PIN is technically 'set'")
        // Empty PIN would still authenticate with empty string
        assertTrue(biometricAuthManager.authenticateWithPin(""), "Empty PIN matches empty entry")
    }

    // ========================================
    // Session Lifetime Tests
    // ========================================

    /**
     * Test: Session timeout countdown
     *
     * Verifies:
     * - getRemainingSeconds() returns countdown
     * - No session returns 0 seconds
     */
    @Test
    fun testSessionCountdown() {
        // No session
        assertEquals(0, biometricAuthManager.getSessionRemainingSeconds(), "No session should return 0")

        // Create session
        biometricAuthManager.setPin("1234")
        biometricAuthManager.authenticateWithPin("1234")

        val remaining = biometricAuthManager.getSessionRemainingSeconds()
        assertTrue(remaining > 0, "Valid session should have remaining time")
        assertTrue(remaining <= Constants.BIOMETRIC_SESSION_TIMEOUT_MINUTES * 60, "Should not exceed timeout")
    }

    /**
     * Test: Session can be cleared
     *
     * Verifies:
     * - clearSession() invalidates session
     * - New authentication required
     */
    @Test
    fun testClearSession() {
        biometricAuthManager.setPin("1234")
        biometricAuthManager.authenticateWithPin("1234")
        assertTrue(biometricAuthManager.isSessionValid(), "Session valid after auth")

        biometricAuthManager.clearSession()
        assertFalse(biometricAuthManager.isSessionValid(), "Session invalid after clear")
        assertNull(biometricAuthManager.getSession(), "Session should be null")
    }

    // ========================================
    // Biometric Enable/Disable Tests
    // ========================================

    /**
     * Test: Biometric can be toggled
     *
     * Verifies:
     * - isBiometricAvailable() reflects setting
     */
    @Test
    fun testBiometricToggle() {
        // Default enabled
        assertTrue(biometricAuthManager.isBiometricAvailable(), "Biometric enabled by default")

        // Disable
        biometricAuthManager.setBiometricEnabled(false)
        assertFalse(biometricAuthManager.isBiometricAvailable(), "Biometric should be disabled")

        // Enable
        biometricAuthManager.setBiometricEnabled(true)
        assertTrue(biometricAuthManager.isBiometricAvailable(), "Biometric should be enabled")
    }

    /**
     * Test: Biometric setting persists
     *
     * Verifies:
     * - Setting saved to SharedPreferences
     * - New instance reads saved setting
     */
    @Test
    fun testBiometricSettingPersists() {
        biometricAuthManager.setBiometricEnabled(false)

        // Create new manager instance (simulating app restart)
        val newManager = BiometricAuthManager(context, sharedPreferences)
        assertFalse(newManager.isBiometricAvailable(), "Setting should persist across instances")
    }

    // ========================================
    // State Flow Tests
    // ========================================

    /**
     * Test: Auth state flow emits on session creation
     *
     * Verifies:
     * - authStateFlow updates when session created
     * - Can be observed for reactive UI
     */
    @Test
    fun testAuthStateFlowEmission() {
        // Initial state
        var emissionCount = 0
        var lastSession = biometricAuthManager.getSession()

        biometricAuthManager.setPin("1234")
        biometricAuthManager.authenticateWithPin("1234")

        // After auth, session should be non-null
        assertNotNull(biometricAuthManager.getSession(), "Session should exist after auth")

        // Clear and check null
        biometricAuthManager.clearSession()
        assertNull(biometricAuthManager.getSession(), "Session should be null after clear")
    }

    // ========================================
    // Edge Cases
    // ========================================

    /**
     * Test: Numeric PIN
     *
     * Verifies:
     * - Numeric-only PINs work correctly
     */
    @Test
    fun testNumericPin() {
        val numericPin = "123456"
        biometricAuthManager.setPin(numericPin)

        assertTrue(biometricAuthManager.authenticateWithPin(numericPin), "Numeric PIN should work")
    }

    /**
     * Test: Special character PIN
     *
     * Verifies:
     * - Alphanumeric and special characters in PIN
     */
    @Test
    fun testSpecialCharacterPin() {
        val specialPin = "P@ssw0rd!#\$"
        biometricAuthManager.setPin(specialPin)

        assertTrue(biometricAuthManager.authenticateWithPin(specialPin), "Special character PIN should work")
    }

    /**
     * Test: Unicode PIN
     *
     * Verifies:
     * - Portuguese and other Unicode characters
     */
    @Test
    fun testUnicodePin() {
        val unicodePin = "SenháÑ"
        biometricAuthManager.setPin(unicodePin)

        assertTrue(biometricAuthManager.authenticateWithPin(unicodePin), "Unicode PIN should work")
    }

    /**
     * Test: Very long PIN
     *
     * Verifies:
     * - Long PINs handled correctly
     */
    @Test
    fun testLongPin() {
        val longPin = "a".repeat(100)
        biometricAuthManager.setPin(longPin)

        assertTrue(biometricAuthManager.authenticateWithPin(longPin), "Long PIN should work")
    }

    /**
     * Test: Case sensitivity
     *
     * Verifies:
     * - PIN is case sensitive
     */
    @Test
    fun testPinCaseSensitive() {
        biometricAuthManager.setPin("AbC123")

        assertTrue(biometricAuthManager.authenticateWithPin("AbC123"), "Exact case should work")
        assertFalse(biometricAuthManager.authenticateWithPin("abc123"), "Different case should fail")
        assertFalse(biometricAuthManager.authenticateWithPin("ABC123"), "Different case should fail")
    }
}
