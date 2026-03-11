package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.data.repositories.PaymentWithDetails
import com.psychologist.financial.domain.models.PageLoadStatus
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.usecases.GetUnpaidAppointmentsUseCase
import com.psychologist.financial.utils.Constants
import com.psychologist.financial.viewmodel.PaymentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for PaymentViewModel per-patient pagination (T023 — TDD).
 *
 * Covers:
 * - loadPatientPayments() resets perPatientPaginationState and loads page 0
 * - loadNextPatientPaymentsPage() appends items from page 1
 * - loadNextPatientPaymentsPage() is a no-op while Loading
 * - Last page (< PAGE_SIZE items) sets hasMore = false
 *
 * Run with: ./gradlew testDebugUnitTest --tests PaymentViewModelPerPatientPaginationTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelPerPatientPaginationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockCreatePaymentUseCase: CreatePaymentUseCase

    @Mock
    private lateinit var mockGetUnpaidAppointmentsUseCase: GetUnpaidAppointmentsUseCase

    @Mock
    private lateinit var mockRepository: PaymentRepository

    private lateinit var viewModel: PaymentViewModel

    private fun makePaymentWithDetails(id: Long, patientId: Long = 1L) = PaymentWithDetails(
        payment = Payment(
            id = id,
            patientId = patientId,
            amount = BigDecimal("100.00"),
            paymentDate = LocalDate.of(2024, 1, 1)
        ),
        appointments = emptyList(),
        patientName = "Patient $patientId"
    )

    private fun makePage(startId: Long, count: Int, patientId: Long = 1L) =
        (startId until startId + count).map { makePaymentWithDetails(it, patientId) }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        viewModel = PaymentViewModel(
            createPaymentUseCase = mockCreatePaymentUseCase,
            getUnpaidAppointmentsUseCase = mockGetUnpaidAppointmentsUseCase,
            repository = mockRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // loadPatientPayments — initial load
    // ========================================

    @Test
    fun `loadPatientPayments resets state and loads page 0`() = runTest {
        val page0 = makePage(1L, Constants.PAGE_SIZE)
        whenever(mockRepository.getPagedByPatient(eq(1L), eq(0)))
            .thenReturn(page0)

        viewModel.loadPatientPayments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.perPatientPaginationState.value
        assertEquals(PageLoadStatus.Idle, state.status)
        assertEquals(Constants.PAGE_SIZE, state.items.size)
        assertEquals(1, state.currentPage)
        assertTrue(state.hasMore)
    }

    @Test
    fun `loadPatientPayments resets items before loading new page`() = runTest {
        whenever(mockRepository.getPagedByPatient(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.loadPatientPayments(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Constants.PAGE_SIZE, viewModel.perPatientPaginationState.value.items.size)

        whenever(mockRepository.getPagedByPatient(eq(1L), eq(0)))
            .thenReturn(makePage(100L, 2))

        viewModel.loadPatientPayments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.perPatientPaginationState.value.items.size)
    }

    // ========================================
    // loadNextPatientPaymentsPage — append
    // ========================================

    @Test
    fun `loadNextPatientPaymentsPage appends page 1 items`() = runTest {
        whenever(mockRepository.getPagedByPatient(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))
        whenever(mockRepository.getPagedByPatient(eq(1L), eq(1)))
            .thenReturn(makePage(26L, Constants.PAGE_SIZE))

        viewModel.loadPatientPayments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadNextPatientPaymentsPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.PAGE_SIZE * 2, viewModel.perPatientPaginationState.value.items.size)
        assertEquals(2, viewModel.perPatientPaginationState.value.currentPage)
    }

    // ========================================
    // No-op guards
    // ========================================

    @Test
    fun `loadNextPatientPaymentsPage is no-op when status is Loading`() = runTest {
        whenever(mockRepository.getPagedByPatient(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.loadPatientPayments(1L)
        viewModel.loadNextPatientPaymentsPage() // called while first load is in-flight
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.perPatientPaginationState.value.currentPage)
    }

    @Test
    fun `loadNextPatientPaymentsPage is no-op when hasMore is false`() = runTest {
        whenever(mockRepository.getPagedByPatient(eq(1L), eq(0)))
            .thenReturn(makePage(1L, 3))

        viewModel.loadPatientPayments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.perPatientPaginationState.value.hasMore)
        val pageBefore = viewModel.perPatientPaginationState.value.currentPage

        viewModel.loadNextPatientPaymentsPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(pageBefore, viewModel.perPatientPaginationState.value.currentPage)
    }

    // ========================================
    // hasMore detection
    // ========================================

    @Test
    fun `last page smaller than PAGE_SIZE sets hasMore to false`() = runTest {
        whenever(mockRepository.getPagedByPatient(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE - 1))

        viewModel.loadPatientPayments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.perPatientPaginationState.value.hasMore)
    }

    @Test
    fun `full page sets hasMore to true`() = runTest {
        whenever(mockRepository.getPagedByPatient(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.loadPatientPayments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.perPatientPaginationState.value.hasMore)
    }
}
