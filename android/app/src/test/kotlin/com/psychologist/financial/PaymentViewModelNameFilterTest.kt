package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.data.repositories.PaymentWithDetails
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.usecases.DeletePaymentUseCase
import com.psychologist.financial.domain.usecases.GetAllPaymentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientPaymentsUseCase
import com.psychologist.financial.domain.usecases.GetUnpaidAppointmentsUseCase
import com.psychologist.financial.viewmodel.PaymentViewModel
import com.psychologist.financial.viewmodel.PaymentViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelNameFilterTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock private lateinit var mockCreatePaymentUseCase: CreatePaymentUseCase
    @Mock private lateinit var mockGetUnpaidAppointmentsUseCase: GetUnpaidAppointmentsUseCase
    @Mock private lateinit var mockRepository: PaymentRepository
    @Mock private lateinit var mockGetPatientPaymentsUseCase: GetPatientPaymentsUseCase
    @Mock private lateinit var mockGetAllPaymentsUseCase: GetAllPaymentsUseCase
    @Mock private lateinit var mockDeletePaymentUseCase: DeletePaymentUseCase

    private lateinit var viewModel: PaymentViewModel

    private fun makePayment(id: Long, patientId: Long) = Payment(
        id = id,
        patientId = patientId,
        amount = BigDecimal("100.00"),
        paymentDate = LocalDate.now()
    )

    private val allPayments = listOf(
        PaymentWithDetails(payment = makePayment(1L, 1L), appointments = emptyList(), patientName = "Ana Lima"),
        PaymentWithDetails(payment = makePayment(2L, 2L), appointments = emptyList(), patientName = "Carlos Silva"),
        PaymentWithDetails(payment = makePayment(3L, 3L), appointments = emptyList(), patientName = "Joana Pereira"),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(mockGetAllPaymentsUseCase.execute()).thenReturn(flowOf(allPayments))
        viewModel = PaymentViewModel(
            createPaymentUseCase = mockCreatePaymentUseCase,
            getUnpaidAppointmentsUseCase = mockGetUnpaidAppointmentsUseCase,
            repository = mockRepository,
            getPatientPaymentsUseCase = mockGetPatientPaymentsUseCase,
            getAllPaymentsUseCase = mockGetAllPaymentsUseCase,
            deletePaymentUseCase = mockDeletePaymentUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setNameFilter filters payments by patient name case insensitive`() = runTest {
        viewModel.loadAllPayments()
        advanceUntilIdle()

        viewModel.setNameFilter("ana")
        advanceUntilIdle()

        val state = viewModel.globalListState.value
        assertTrue(state is PaymentViewState.GlobalListState.Success)
        val filtered = (state as PaymentViewState.GlobalListState.Success).filteredPayments
        // "Ana Lima" and "Joana Pereira" both contain "ana"
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.patientName.contains("ana", ignoreCase = true) })
    }

    @Test
    fun `setNameFilter with empty string returns all payments`() = runTest {
        viewModel.loadAllPayments()
        advanceUntilIdle()

        viewModel.setNameFilter("carlos")
        advanceUntilIdle()
        viewModel.setNameFilter("")
        advanceUntilIdle()

        val state = viewModel.globalListState.value
        assertTrue(state is PaymentViewState.GlobalListState.Success)
        val filtered = (state as PaymentViewState.GlobalListState.Success).filteredPayments
        assertEquals(allPayments.size, filtered.size)
    }

    @Test
    fun `resetNameFilter restores full payment list`() = runTest {
        viewModel.loadAllPayments()
        advanceUntilIdle()
        viewModel.setNameFilter("joana")
        advanceUntilIdle()

        viewModel.resetNameFilter()
        advanceUntilIdle()

        val state = viewModel.globalListState.value
        assertTrue(state is PaymentViewState.GlobalListState.Success)
        assertEquals(allPayments.size, (state as PaymentViewState.GlobalListState.Success).filteredPayments.size)
    }
}
