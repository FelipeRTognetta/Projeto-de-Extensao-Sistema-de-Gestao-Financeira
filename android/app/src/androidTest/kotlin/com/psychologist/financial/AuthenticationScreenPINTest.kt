package com.psychologist.financial

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.services.BiometricAuthManager
import com.psychologist.financial.ui.screens.AuthenticationScreen
import com.psychologist.financial.ui.screens.PINFallbackScreen
import com.psychologist.financial.ui.theme.PatientTheme
import com.psychologist.financial.viewmodel.AuthenticationViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for AuthenticationScreen PIN fallback path.
 *
 * Regression tests for the "Usar PIN" blank screen bug:
 * - Before fix: ERROR_NEGATIVE_BUTTON was not handled by shouldOfferFallback()
 *   → state stayed in Error instead of transitioning to EnteringPIN
 * - Before fix: AuthenticationScreen had `else -> {}` for AuthState.EnteringPIN
 *   → clicking "Usar PIN" rendered a blank screen
 *
 * After fix:
 * - ERROR_NEGATIVE_BUTTON → NeedsFallback → NeedsPIN → EnteringPIN (via proceedWithPIN)
 * - AuthState.EnteringPIN → PINFallbackScreen rendered correctly
 */
@RunWith(AndroidJUnit4::class)
class AuthenticationScreenPINTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ========================================
    // PINFallbackScreen Rendering Tests
    // ========================================

    @Test
    fun pinFallbackScreen_displaysTitle() {
        val biometricAuthManager = BiometricAuthManager(composeTestRule.activity)
        val viewModel = AuthenticationViewModel(biometricAuthManager)

        composeTestRule.setContent {
            PatientTheme {
                PINFallbackScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Autentificação com PIN").assertIsDisplayed()
    }

    @Test
    fun pinFallbackScreen_displaysConfirmButton_disabledWhenPINEmpty() {
        val biometricAuthManager = BiometricAuthManager(composeTestRule.activity)
        val viewModel = AuthenticationViewModel(biometricAuthManager)

        composeTestRule.setContent {
            PatientTheme {
                PINFallbackScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("CONFIRMAR").assertIsDisplayed()
        composeTestRule.onNodeWithText("CONFIRMAR").assertIsNotEnabled()
    }

    @Test
    fun pinFallbackScreen_displaysCancelButton() {
        val biometricAuthManager = BiometricAuthManager(composeTestRule.activity)
        val viewModel = AuthenticationViewModel(biometricAuthManager)

        composeTestRule.setContent {
            PatientTheme {
                PINFallbackScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("CANCELAR").assertIsDisplayed()
        composeTestRule.onNodeWithText("CANCELAR").assertIsEnabled()
    }

    @Test
    fun pinFallbackScreen_displaysPINLengthHint() {
        val biometricAuthManager = BiometricAuthManager(composeTestRule.activity)
        val viewModel = AuthenticationViewModel(biometricAuthManager)

        composeTestRule.setContent {
            PatientTheme {
                PINFallbackScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("PIN deve ter 4-6 dígitos").assertIsDisplayed()
    }

    // ========================================
    // AuthenticationScreen EnteringPIN State Tests
    // ========================================

    @Test
    fun authenticationScreen_whenEnteringPINState_rendersPINForm_notBlank() {
        // Regression test: before the fix, AuthenticationScreen had `else -> {}` for
        // AuthState.EnteringPIN, causing a blank screen when user tapped "Usar PIN".
        val biometricAuthManager = BiometricAuthManager(composeTestRule.activity)
        val viewModel = AuthenticationViewModel(biometricAuthManager)

        // Transition to EnteringPIN state (simulates user tapping "Usar PIN" button)
        viewModel.proceedWithPIN()

        composeTestRule.setContent {
            PatientTheme {
                AuthenticationScreen(viewModel = viewModel)
            }
        }

        // After the fix, PINFallbackScreen content must be visible (not blank)
        composeTestRule.onNodeWithText("Autentificação com PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("CONFIRMAR").assertIsDisplayed()
    }
}
