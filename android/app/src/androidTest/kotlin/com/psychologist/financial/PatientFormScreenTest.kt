package com.psychologist.financial

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.domain.usecases.CreatePatientUseCase
import com.psychologist.financial.domain.validation.ValidationError
import com.psychologist.financial.ui.screens.PatientFormScreen
import com.psychologist.financial.ui.theme.PatientTheme
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PatientViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * UI tests for PatientFormScreen using Espresso
 *
 * Coverage:
 * - Form field input and updates
 * - Real-time validation with error display
 * - Submit button enable/disable based on validation
 * - Loading state during submission
 * - Success navigation callback
 * - Error message display
 * - Field-specific error messages
 * - Form reset on cancel
 * - Date picker integration
 *
 * Total: 20 test cases
 * Uses Compose Test Framework and Espresso
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class PatientFormScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Mock
    private lateinit var mockViewModel: PatientViewModel

    private lateinit var formStateFlow: MutableStateFlow<PatientViewState.CreatePatientState>
    private lateinit var formNameFlow: MutableStateFlow<String>
    private lateinit var formPhoneFlow: MutableStateFlow<String>
    private lateinit var formEmailFlow: MutableStateFlow<String>
    private lateinit var formDateFlow: MutableStateFlow<LocalDate>

    private fun setupViewModel() {
        MockitoAnnotations.openMocks(this)

        formStateFlow = MutableStateFlow(PatientViewState.CreatePatientState())
        formNameFlow = MutableStateFlow("")
        formPhoneFlow = MutableStateFlow("")
        formEmailFlow = MutableStateFlow("")
        formDateFlow = MutableStateFlow(LocalDate.now())

        whenever(mockViewModel.createFormState).thenReturn(formStateFlow)
        whenever(mockViewModel.formName).thenReturn(formNameFlow)
        whenever(mockViewModel.formPhone).thenReturn(formPhoneFlow)
        whenever(mockViewModel.formEmail).thenReturn(formEmailFlow)
        whenever(mockViewModel.formInitialConsultDate).thenReturn(formDateFlow)

        whenever(mockViewModel.setFormName(org.mockito.kotlin.any())).then { invocation ->
            formNameFlow.value = invocation.getArgument(0) as String
        }
        whenever(mockViewModel.setFormPhone(org.mockito.kotlin.any())).then { invocation ->
            formPhoneFlow.value = invocation.getArgument(0) as String
        }
        whenever(mockViewModel.setFormEmail(org.mockito.kotlin.any())).then { invocation ->
            formEmailFlow.value = invocation.getArgument(0) as String
        }
        whenever(mockViewModel.setFormInitialConsultDate(org.mockito.kotlin.any())).then { invocation ->
            formDateFlow.value = invocation.getArgument(0) as LocalDate
        }
    }

    // ========================================
    // Form Field Display Tests
    // ========================================

    @Test
    fun patientForm_displaysAllFields() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Nome *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Telefone (opcional)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Email (opcional)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Data da Primeira Consulta *").assertIsDisplayed()
    }

    @Test
    fun patientForm_displaysSubmitButton() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Salvar").assertIsDisplayed()
    }

    @Test
    fun patientForm_displaysCancelButton() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    @Test
    fun patientForm_displaysHelpText() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert - Help text explains requirements
        composeTestRule.onNodeWithText("obrigatórios").assertIsDisplayed()
    }

    // ========================================
    // Input Tests
    // ========================================

    @Test
    fun patientForm_inputName_updatesField() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        composeTestRule.onNodeWithText("Nome *")
            .performTextInput("João Silva")

        // Assert
        verify(mockViewModel).setFormName("João Silva")
    }

    @Test
    fun patientForm_inputPhone_updatesField() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        composeTestRule.onNodeWithText("Telefone (opcional)")
            .performTextInput("(11) 99999-9999")

        // Assert
        verify(mockViewModel).setFormPhone("(11) 99999-9999")
    }

    @Test
    fun patientForm_inputEmail_updatesField() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        composeTestRule.onNodeWithText("Email (opcional)")
            .performTextInput("joao@example.com")

        // Assert
        verify(mockViewModel).setFormEmail("joao@example.com")
    }

    // ========================================
    // Validation Error Display Tests
    // ========================================

    @Test
    fun patientForm_emptyName_displaysError() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = mapOf("name" to "Nome é obrigatório")
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Nome é obrigatório")
            .assertIsDisplayed()
    }

    @Test
    fun patientForm_invalidEmail_displaysError() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = mapOf("email" to "Formato de email inválido")
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Formato de email inválido")
            .assertIsDisplayed()
    }

    @Test
    fun patientForm_invalidPhone_displaysError() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = mapOf("phone" to "Telefone deve ter no mínimo 7 dígitos")
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Telefone deve ter no mínimo 7 dígitos")
            .assertIsDisplayed()
    }

    @Test
    fun patientForm_noContactInfo_displaysError() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = mapOf("contact" to "Deve ser fornecido telefone ou email")
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Deve ser fornecido telefone ou email")
            .assertIsDisplayed()
    }

    @Test
    fun patientForm_futureDate_displaysError() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = mapOf("initialConsultDate" to "Data da primeira consulta não pode ser no futuro")
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Data da primeira consulta não pode ser no futuro")
            .assertIsDisplayed()
    }

    // ========================================
    // Submit Button State Tests
    // ========================================

    @Test
    fun patientForm_emptyForm_submitButtonDisabled() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = mapOf("name" to "Nome é obrigatório")
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Salvar").assertIsNotEnabled()
    }

    @Test
    fun patientForm_validForm_submitButtonEnabled() {
        // Arrange
        setupViewModel()
        formNameFlow.value = "João Silva"
        formPhoneFlow.value = "(11) 99999-9999"
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = emptyMap()
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Salvar").assertIsEnabled()
    }

    @Test
    fun patientForm_withValidation_submitButtonReflectsState() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert - Initially disabled (empty form)
        composeTestRule.onNodeWithText("Salvar").assertIsNotEnabled()

        // Act - Fill with valid data
        formNameFlow.value = "João Silva"
        formPhoneFlow.value = "(11) 99999-9999"
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = emptyMap()
        )

        // Assert - Now enabled
        composeTestRule.onNodeWithText("Salvar").assertIsEnabled()
    }

    // ========================================
    // Form Submission Tests
    // ========================================

    @Test
    fun patientForm_clickSubmit_callsViewModelSubmit() {
        // Arrange
        setupViewModel()
        formNameFlow.value = "João Silva"
        formPhoneFlow.value = "(11) 99999-9999"
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = emptyMap()
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        composeTestRule.onNodeWithText("Salvar").performClick()

        // Assert
        verify(mockViewModel).submitCreatePatientForm()
    }

    @Test
    fun patientForm_submitting_showsLoadingIndicator() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = emptyMap(),
            isSubmitting = true
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert - Progress indicator visible during submission
        composeTestRule.onNodeWithContentDescription("Salvando")
            .assertIsDisplayed()
    }

    @Test
    fun patientForm_submitting_disablesButtons() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = emptyMap(),
            isSubmitting = true
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Salvar").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Cancelar").assertIsNotEnabled()
    }

    @Test
    fun patientForm_submissionSuccess_callsOnSuccess() {
        // Arrange
        setupViewModel()
        var successPatientId: Long? = null
        val submissionResult = CreatePatientUseCase.CreatePatientResult.Success(patientId = 1L)
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = emptyMap(),
            submissionResult = submissionResult
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { patientId -> successPatientId = patientId },
                    onCancel = { }
                )
            }
        }

        // Assert
        assert(successPatientId == 1L)
    }

    @Test
    fun patientForm_submissionError_displaysMessage() {
        // Arrange
        setupViewModel()
        val errorMessage = "Erro ao salvar paciente"
        val submissionResult = CreatePatientUseCase.CreatePatientResult.Error(message = errorMessage)
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = emptyMap(),
            submissionResult = submissionResult
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // ========================================
    // Cancel Tests
    // ========================================

    @Test
    fun patientForm_clickCancel_callsOnCancel() {
        // Arrange
        setupViewModel()
        var cancelCalled = false

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { cancelCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Cancelar").performClick()

        // Assert
        assert(cancelCalled)
    }

    // ========================================
    // Validation Status Display Tests
    // ========================================

    @Test
    fun patientForm_displayValidationStatus() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert - Display validation status
        composeTestRule.onNodeWithText("Preencha os campos obrigatórios")
            .assertIsDisplayed()
    }

    @Test
    fun patientForm_validFormStatus() {
        // Arrange
        setupViewModel()
        formNameFlow.value = "João Silva"
        formPhoneFlow.value = "(11) 99999-9999"
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = emptyMap()
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Formulário válido")
            .assertIsDisplayed()
    }

    // ========================================
    // Layout Tests
    // ========================================

    @Test
    fun patientForm_displaysTopAppBar() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Novo Paciente").assertIsDisplayed()
    }

    @Test
    fun patientForm_displaysBackButton() {
        // Arrange
        setupViewModel()

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription("Voltar").assertIsDisplayed()
    }

    @Test
    fun patientForm_clickBackButton_callsOnCancel() {
        // Arrange
        setupViewModel()
        var cancelCalled = false

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { cancelCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Voltar").performClick()

        // Assert
        assert(cancelCalled)
    }

    // ========================================
    // Multiple Errors Display Tests
    // ========================================

    @Test
    fun patientForm_multipleErrors_displayAll() {
        // Arrange
        setupViewModel()
        formStateFlow.value = PatientViewState.CreatePatientState(
            fieldErrors = mapOf(
                "name" to "Nome é obrigatório",
                "contact" to "Deve ser fornecido telefone ou email",
                "initialConsultDate" to "Data não pode ser no futuro"
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientFormScreen(
                    viewModel = mockViewModel,
                    onSuccess = { },
                    onCancel = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Nome é obrigatório").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deve ser fornecido telefone ou email")
            .assertIsDisplayed()
    }
}
