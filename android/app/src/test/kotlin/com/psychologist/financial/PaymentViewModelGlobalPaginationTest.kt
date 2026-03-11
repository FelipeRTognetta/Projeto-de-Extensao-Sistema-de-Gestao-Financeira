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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for PaymentViewModel global-list pagination (T012 — TDD).
 *
 * Covers:
 * - resetGlobalPaymentList() loads page 0 and sets Idle after success
 * - loadNextGlobalPaymentPage() appends items from page 1
 * - loadNextGlobalPaymentPage() is a no-op while Loading
 * - setNameFilter() resets global payment pagination state and reloads
 * - last page (< PAGE_SIZE items) sets hasMore = false
 *
 * Run with: ./gradlew testDebugUnitTest --tests PaymentViewModelGlobalPaginationTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelGlobalPaginationTest {

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

    private fun makePaymentWithDetails(id: Long, patientName: String = "Patient $id") = PaymentWithDetails(
        payment = Payment(
            id = id,
            patientId = 1L,
            amount = BigDecimal("100.00"),
            paymentDate = LocalDate.of(2024, 1, 1)
        ),
        appointments = emptyList(),
        patientName = patientName
    )

    private fun makePage(startId: Long, count: Int): List<PaymentWithDetails> =
        (startId until startId + count).map { makePaymentWithDetails(it) }

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
    // resetGlobalPaymentList — initial load
    // ========================================

    @Test
    fun `resetGlobalPaymentList sets status to Idle after loading page 0`() = runTest {
        val page0 = makePage(1L, Constants.PAGE_SIZE)
        whenever(
            mockRepository.getPagedWithPatient(searchTerm = "%", page = 0)
        ).thenReturn(page0)

        viewModel.resetGlobalPaymentList()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.globalPaginationState.value
        assertEquals(PageLoadStatus.Idle, state.status)
        assertEquals(Constants.PAGE_SIZE, state.items.size)
        assertEquals(1, state.currentPage)
    }

    @Test
    fun `resetGlobalPaymentList resets items before loading`() = runTest {
        whenever(mockRepository.getPagedWithPatient(any(), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetGlobalPaymentList()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Constants.PAGE_SIZE, viewModel.globalPaginationState.value.items.size)

        whenever(mockRepository.getPagedWithPatient(any(), eq(0)))
            .thenReturn(makePage(100L, 2))

        viewModel.resetGlobalPaymentList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.globalPaginationState.value.items.size)
    }

    // ========================================
    // loadNextGlobalPaymentPage — append
    // ========================================

    @Test
    fun `loadNextGlobalPaymentPage appends page 1 items`() = runTest {
        whenever(mockRepository.getPagedWithPatient(any(), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))
        whenever(mockRepository.getPagedWithPatient(any(), eq(1)))
            .thenReturn(makePage(26L, Constants.PAGE_SIZE))

        viewModel.resetGlobalPaymentList()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadNextGlobalPaymentPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.PAGE_SIZE * 2, viewModel.globalPaginationState.value.items.size)
        assertEquals(2, viewModel.globalPaginationState.value.currentPage)
    }

    // ========================================
    // No-op guards
    // ========================================

    @Test
    fun `loadNextGlobalPaymentPage is no-op when status is Loading`() = runTest {
        whenever(mockRepository.getPagedWithPatient(any(), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetGlobalPaymentList()
        viewModel.loadNextGlobalPaymentPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.globalPaginationState.value.currentPage)
    }

    @Test
    fun `loadNextGlobalPaymentPage is no-op when hasMore is false`() = runTest {
        whenever(mockRepository.getPagedWithPatient(any(), eq(0)))
            .thenReturn(makePage(1L, 3))

        viewModel.resetGlobalPaymentList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.globalPaginationState.value.hasMore)
        val pageBefore = viewModel.globalPaginationState.value.currentPage

        viewModel.loadNextGlobalPaymentPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(pageBefore, viewModel.globalPaginationState.value.currentPage)
    }

    // ========================================
    // hasMore detection
    // ========================================

    @Test
    fun `last page smaller than PAGE_SIZE sets hasMore to false`() = runTest {
        whenever(mockRepository.getPagedWithPatient(any(), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE - 1))

        viewModel.resetGlobalPaymentList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.globalPaginationState.value.hasMore)
    }

    @Test
    fun `full page sets hasMore to true`() = runTest {
        whenever(mockRepository.getPagedWithPatient(any(), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetGlobalPaymentList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.globalPaginationState.value.hasMore)
    }

    // ========================================
    // Name filter resets pagination
    // ========================================

    @Test
    fun `setNameFilter resets state and reloads with encoded search term`() = runTest {
        whenever(mockRepository.getPagedWithPatient(eq("%"), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetGlobalPaymentList()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Constants.PAGE_SIZE, viewModel.globalPaginationState.value.items.size)

        val filtered = listOf(makePaymentWithDetails(3L, "João Silva"))
        whenever(mockRepository.getPagedWithPatient(eq("%joão%"), eq(0)))
            .thenReturn(filtered)

        viewModel.setNameFilter("joão")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.globalPaginationState.value.items.size)
        assertEquals("João Silva", viewModel.globalPaginationState.value.items.first().patientName)
    }
}
