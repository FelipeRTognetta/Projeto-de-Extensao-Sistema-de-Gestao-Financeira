package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.usecases.GetAllPaymentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientPaymentsUseCase
import com.psychologist.financial.domain.usecases.GetUnpaidAppointmentsUseCase
import com.psychologist.financial.viewmodel.PaymentViewModel
import com.psychologist.financial.viewmodel.PaymentViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for PaymentViewModel
 *
 * Coverage:
 * - Loading patient payments (success, empty, error)
 * - Loading payment detail (success, error)
 * - Multiple sequential loads
 * - Form state management (amount, date, appointment selection, submission)
 * - Available appointments loading
 * - Error handling and state transitions
 *
 * Total: 16+ test cases
 * Uses Mockito to mock repository and use cases
 * Uses coroutines testing for async operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockRepository: PaymentRepository

    @Mock
    private lateinit var mockGetPatientPaymentsUseCase: GetPatientPaymentsUseCase

    @Mock
    private lateinit var mockCreatePaymentUseCase: CreatePaymentUseCase

    @Mock
    private lateinit var mockGetUnpaidAppointmentsUseCase: GetUnpaidAppointmentsUseCase

    @Mock
    private lateinit var mockGetAllPaymentsUseCase: GetAllPaymentsUseCase

    private lateinit var viewModel: PaymentViewModel

    // Test data
    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val weekAgo = today.minusDays(7)

    private val mockPayments = listOf(
        Payment(
            id = 1L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = yesterday
        ),
        Payment(
            id = 2L,
            patientId = 1L,
            amount = BigDecimal("250.00"),
            paymentDate = weekAgo
        )
    )

    private val mockAppointments = listOf(
        Appointment(
            id = 10L,
            patientId = 1L,
            date = today,
            timeStart = LocalTime.of(9, 0),
            durationMinutes = 50
        ),
        Appointment(
            id = 11L,
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(10, 0),
            durationMinutes = 50
        )
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        viewModel = PaymentViewModel(
            createPaymentUseCase = mockCreatePaymentUseCase,
            getUnpaidAppointmentsUseCase = mockGetUnpaidAppointmentsUseCase,
            repository = mockRepository,
            getPatientPaymentsUseCase = mockGetPatientPaymentsUseCase,
            getAllPaymentsUseCase = mockGetAllPaymentsUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // Patient Payments Loading Tests
    // ========================================

    @Test
    fun loadPatientPayments_onSuccess_updatesState() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientPaymentsUseCase.execute(patientId)).thenReturn(mockPayments)

        // Act
        viewModel.loadPatientPayments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.paymentListState.value
        assertTrue(state is PaymentViewState.ListState.Success)
        assertEquals(2, (state as PaymentViewState.ListState.Success).payments.size)
    }

    @Test
    fun loadPatientPayments_emptyList_updatesStateToEmpty() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientPaymentsUseCase.execute(patientId)).thenReturn(emptyList())

        // Act
        viewModel.loadPatientPayments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        assertTrue(viewModel.paymentListState.value is PaymentViewState.ListState.Empty)
    }

    @Test
    fun loadPatientPayments_onError_updatesStateToError() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientPaymentsUseCase.execute(patientId))
            .thenThrow(RuntimeException("Database error"))

        // Act
        viewModel.loadPatientPayments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.paymentListState.value
        assertTrue(state is PaymentViewState.ListState.Error)
        assertTrue((state as PaymentViewState.ListState.Error).message.contains("erro", ignoreCase = true))
    }

    @Test
    fun multipleLoadPayments_lastLoadWins() = runTest {
        // Arrange - first load returns one payment
        val patientId = 1L
        whenever(mockGetPatientPaymentsUseCase.execute(patientId))
            .thenReturn(listOf(mockPayments[0]))

        viewModel.loadPatientPayments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Arrange - second load returns both payments
        whenever(mockGetPatientPaymentsUseCase.execute(patientId)).thenReturn(mockPayments)

        // Act
        viewModel.loadPatientPayments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.paymentListState.value
        assertEquals(2, (state as PaymentViewState.ListState.Success).payments.size)
    }

    // ========================================
    // Payment Detail Tests
    // ========================================

    @Test
    fun loadPaymentDetail_onSuccess_updatesDetailState() = runTest {
        // Arrange
        val paymentId = 1L
        whenever(mockRepository.getById(paymentId)).thenReturn(mockPayments[0])

        // Act
        viewModel.loadPaymentDetail(paymentId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.paymentDetailState.value
        assertTrue(state is PaymentViewState.DetailState.Success)
        assertEquals(mockPayments[0].id, (state as PaymentViewState.DetailState.Success).payment.id)
    }

    @Test
    fun loadPaymentDetail_onError_updatesDetailStateToError() = runTest {
        // Arrange
        val paymentId = 999L
        whenever(mockRepository.getById(paymentId)).thenThrow(RuntimeException("Payment not found"))

        // Act
        viewModel.loadPaymentDetail(paymentId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        assertTrue(viewModel.paymentDetailState.value is PaymentViewState.DetailState.Error)
    }

    // ========================================
    // Payment Form State Tests
    // ========================================

    @Test
    fun updateAmount_updatesAmountTextInFormState() {
        // Act
        viewModel.updateAmount("150.00")

        // Assert
        assertEquals("150.00", viewModel.paymentFormState.value.amountText)
        assertNull(viewModel.paymentFormState.value.errorMessage)
    }

    @Test
    fun updateAmount_clearsExistingErrorMessage() {
        // Arrange - trigger an error first
        viewModel.submitForm(1L) // blank amount → sets error

        // Act
        viewModel.updateAmount("100.00")

        // Assert - error cleared when user types
        assertNull(viewModel.paymentFormState.value.errorMessage)
    }

    @Test
    fun updatePaymentDate_updatesDateInFormState() {
        // Act
        viewModel.updatePaymentDate(yesterday)

        // Assert
        assertEquals(yesterday, viewModel.paymentFormState.value.paymentDate)
    }

    @Test
    fun toggleAppointmentSelection_addsAppointmentId() {
        // Act
        viewModel.toggleAppointmentSelection(10L)

        // Assert
        assertTrue(viewModel.paymentFormState.value.selectedAppointmentIds.contains(10L))
    }

    @Test
    fun toggleAppointmentSelection_removesAlreadySelectedId() {
        // Arrange - select first
        viewModel.toggleAppointmentSelection(10L)

        // Act - toggle again to deselect
        viewModel.toggleAppointmentSelection(10L)

        // Assert
        assertTrue(viewModel.paymentFormState.value.selectedAppointmentIds.isEmpty())
    }

    @Test
    fun toggleAppointmentSelection_multipleIds_allSelected() {
        // Act
        viewModel.toggleAppointmentSelection(10L)
        viewModel.toggleAppointmentSelection(11L)

        // Assert
        val selected = viewModel.paymentFormState.value.selectedAppointmentIds
        assertTrue(selected.contains(10L))
        assertTrue(selected.contains(11L))
    }

    @Test
    fun submitForm_withBlankAmount_setsErrorMessage() {
        // Arrange - amount is blank by default, or explicitly clear it
        viewModel.updateAmount("")

        // Act
        viewModel.submitForm(1L)

        // Assert
        assertEquals("Informe o valor do pagamento", viewModel.paymentFormState.value.errorMessage)
    }

    @Test
    fun submitForm_withZeroAmount_setsErrorMessage() {
        // Arrange
        viewModel.updateAmount("0")

        // Act
        viewModel.submitForm(1L)

        // Assert
        assertEquals("Valor inválido", viewModel.paymentFormState.value.errorMessage)
    }

    @Test
    fun submitForm_withNegativeAmount_setsErrorMessage() {
        // Arrange
        viewModel.updateAmount("-50")

        // Act
        viewModel.submitForm(1L)

        // Assert
        assertEquals("Valor inválido", viewModel.paymentFormState.value.errorMessage)
    }

    @Test
    fun submitForm_onSuccess_clearsSelectionAndAmount() = runTest {
        // Arrange
        viewModel.updateAmount("150.00")
        viewModel.toggleAppointmentSelection(10L)

        // Act
        viewModel.submitForm(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert — createPaymentUseCase does not throw, so success path runs
        val formState = viewModel.paymentFormState.value
        assertEquals("", formState.amountText)
        assertTrue(formState.selectedAppointmentIds.isEmpty())
    }

    // ========================================
    // Available Appointments Loading Tests
    // ========================================

    @Test
    fun loadAvailableAppointments_onSuccess_updatesFormState() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetUnpaidAppointmentsUseCase.execute(patientId))
            .thenReturn(flowOf(mockAppointments))

        // Act
        viewModel.loadAvailableAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        assertEquals(2, viewModel.paymentFormState.value.availableAppointments.size)
    }

    @Test
    fun loadAvailableAppointments_emptyList_setsEmptyAppointments() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetUnpaidAppointmentsUseCase.execute(patientId))
            .thenReturn(flowOf(emptyList()))

        // Act
        viewModel.loadAvailableAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        assertTrue(viewModel.paymentFormState.value.availableAppointments.isEmpty())
    }
}
