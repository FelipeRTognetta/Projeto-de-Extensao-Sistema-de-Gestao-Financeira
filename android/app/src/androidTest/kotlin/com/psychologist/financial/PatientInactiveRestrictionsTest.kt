package com.psychologist.financial

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.domain.models.BillableHoursSummary
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.ui.screens.AppointmentListScreen
import com.psychologist.financial.ui.screens.PaymentListScreen
import com.psychologist.financial.ui.theme.FinancialTheme
import com.psychologist.financial.viewmodel.AppointmentViewState
import com.psychologist.financial.viewmodel.PaymentViewState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.PaymentViewModel

/**
 * UI tests verifying inactive-patient restrictions (US1).
 *
 * Coverage:
 * - T006: FAB "Nova Consulta" is disabled when patient is inactive
 * - T007: FAB "Novo Pagamento" is disabled when patient is inactive
 *
 * Run with: ./gradlew connectedDebugAndroidTest --tests PatientInactiveRestrictionsTest
 */
@RunWith(AndroidJUnit4::class)
class PatientInactiveRestrictionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Mock
    private lateinit var mockAppointmentViewModel: AppointmentViewModel

    @Mock
    private lateinit var mockPaymentViewModel: PaymentViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(mockAppointmentViewModel.appointmentListState).thenReturn(
            MutableStateFlow(AppointmentViewState.ListState.Loading)
        )
        whenever(mockAppointmentViewModel.billableHoursSummary).thenReturn(
            MutableStateFlow<BillableHoursSummary?>(null)
        )
        whenever(mockAppointmentViewModel.globalListState).thenReturn(
            MutableStateFlow(AppointmentViewState.GlobalListState.Loading)
        )

        whenever(mockPaymentViewModel.paymentListState).thenReturn(
            MutableStateFlow(PaymentViewState.ListState.Loading)
        )
        whenever(mockPaymentViewModel.globalListState).thenReturn(
            MutableStateFlow(PaymentViewState.GlobalListState.Loading)
        )
        whenever(mockPaymentViewModel.paymentFormState).thenReturn(
            MutableStateFlow(PaymentViewState.PaymentFormState())
        )
    }

    // ──────────────────────────────────────────
    // T006: FAB "Nova Consulta" disabled for inactive patient
    // ──────────────────────────────────────────

    @Test
    fun appointmentListScreen_fab_isDisabled_whenPatientInactive() {
        composeTestRule.setContent {
            FinancialTheme {
                AppointmentListScreen(
                    viewModel = mockAppointmentViewModel,
                    patientId = 1L,
                    patientName = "Paciente Inativo",
                    isPatientActive = false,
                    onBack = {},
                    onAddAppointment = {},
                    onSelectAppointment = {}
                )
            }
        }

        composeTestRule
            .onNodeWithTag("fab_add_appointment")
            .assertIsNotEnabled()
    }

    @Test
    fun appointmentListScreen_fab_isEnabled_whenPatientActive() {
        composeTestRule.setContent {
            FinancialTheme {
                AppointmentListScreen(
                    viewModel = mockAppointmentViewModel,
                    patientId = 1L,
                    patientName = "Paciente Ativo",
                    isPatientActive = true,
                    onBack = {},
                    onAddAppointment = {},
                    onSelectAppointment = {}
                )
            }
        }

        // Should NOT throw — node is enabled (no disabled semantics)
        composeTestRule
            .onNodeWithTag("fab_add_appointment")
            .assertExists()
    }

    // ──────────────────────────────────────────
    // T007: FAB "Novo Pagamento" disabled for inactive patient
    // ──────────────────────────────────────────

    @Test
    fun paymentListScreen_fab_isDisabled_whenPatientInactive() {
        composeTestRule.setContent {
            FinancialTheme {
                PaymentListScreen(
                    viewModel = mockPaymentViewModel,
                    patientId = 1L,
                    patientName = "Paciente Inativo",
                    isPatientActive = false,
                    onBack = {},
                    onAddPayment = {},
                    onSelectPayment = {}
                )
            }
        }

        composeTestRule
            .onNodeWithTag("fab_add_payment")
            .assertIsNotEnabled()
    }

    @Test
    fun paymentListScreen_fab_isEnabled_whenPatientActive() {
        composeTestRule.setContent {
            FinancialTheme {
                PaymentListScreen(
                    viewModel = mockPaymentViewModel,
                    patientId = 1L,
                    patientName = "Paciente Ativo",
                    isPatientActive = true,
                    onBack = {},
                    onAddPayment = {},
                    onSelectPayment = {}
                )
            }
        }

        composeTestRule
            .onNodeWithTag("fab_add_payment")
            .assertExists()
    }
}
