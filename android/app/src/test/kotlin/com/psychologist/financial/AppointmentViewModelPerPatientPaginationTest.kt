package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import com.psychologist.financial.domain.models.PageLoadStatus
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase
import com.psychologist.financial.domain.usecases.DeleteAppointmentUseCase
import com.psychologist.financial.domain.usecases.GetAllAppointmentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import com.psychologist.financial.domain.usecases.UpdateAppointmentUseCase
import com.psychologist.financial.utils.Constants
import com.psychologist.financial.viewmodel.AppointmentViewModel
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
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for AppointmentViewModel per-patient pagination (T022 — TDD).
 *
 * Covers:
 * - loadPatientAppointments() resets perPatientPaginationState and loads page 0
 * - loadNextPatientAppointmentsPage() appends items from page 1
 * - loadNextPatientAppointmentsPage() is a no-op while Loading
 * - Loading for a different patientId resets state
 * - Last page (< PAGE_SIZE items) sets hasMore = false
 *
 * Run with: ./gradlew testDebugUnitTest --tests AppointmentViewModelPerPatientPaginationTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentViewModelPerPatientPaginationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockRepository: AppointmentRepository

    @Mock
    private lateinit var mockGetPatientAppointmentsUseCase: GetPatientAppointmentsUseCase

    @Mock
    private lateinit var mockCreateAppointmentUseCase: CreateAppointmentUseCase

    @Mock
    private lateinit var mockUpdateAppointmentUseCase: UpdateAppointmentUseCase

    @Mock
    private lateinit var mockGetAllAppointmentsUseCase: GetAllAppointmentsUseCase

    @Mock
    private lateinit var mockDeleteAppointmentUseCase: DeleteAppointmentUseCase

    private lateinit var viewModel: AppointmentViewModel

    private fun makeAppointment(id: Long, patientId: Long = 1L) = AppointmentWithPaymentStatus(
        appointment = Appointment(
            id = id,
            patientId = patientId,
            date = LocalDate.of(2024, 1, 1),
            timeStart = LocalTime.of(9, 0),
            durationMinutes = 60
        ),
        hasPendingPayment = false,
        patientName = "Patient $patientId"
    )

    private fun makePage(startId: Long, count: Int, patientId: Long = 1L) =
        (startId until startId + count).map { makeAppointment(it, patientId) }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        viewModel = AppointmentViewModel(
            repository = mockRepository,
            getPatientAppointmentsUseCase = mockGetPatientAppointmentsUseCase,
            createAppointmentUseCase = mockCreateAppointmentUseCase,
            updateAppointmentUseCase = mockUpdateAppointmentUseCase,
            getAllAppointmentsUseCase = mockGetAllAppointmentsUseCase,
            deleteAppointmentUseCase = mockDeleteAppointmentUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // loadPatientAppointments — initial load
    // ========================================

    @Test
    fun `loadPatientAppointments resets state and loads page 0`() = runTest {
        val page0 = makePage(1L, Constants.PAGE_SIZE)
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(page0)

        viewModel.loadPatientAppointments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.perPatientPaginationState.value
        assertEquals(PageLoadStatus.Idle, state.status)
        assertEquals(Constants.PAGE_SIZE, state.items.size)
        assertEquals(1, state.currentPage)
        assertTrue(state.hasMore)
    }

    @Test
    fun `loadPatientAppointments resets items before loading new page`() = runTest {
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.loadPatientAppointments(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Constants.PAGE_SIZE, viewModel.perPatientPaginationState.value.items.size)

        // Second call resets
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(makePage(100L, 2))

        viewModel.loadPatientAppointments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.perPatientPaginationState.value.items.size)
    }

    // ========================================
    // loadNextPatientAppointmentsPage — append
    // ========================================

    @Test
    fun `loadNextPatientAppointmentsPage appends page 1 items`() = runTest {
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(1)))
            .thenReturn(makePage(26L, Constants.PAGE_SIZE))

        viewModel.loadPatientAppointments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadNextPatientAppointmentsPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.PAGE_SIZE * 2, viewModel.perPatientPaginationState.value.items.size)
        assertEquals(2, viewModel.perPatientPaginationState.value.currentPage)
    }

    // ========================================
    // No-op guards
    // ========================================

    @Test
    fun `loadNextPatientAppointmentsPage is no-op when status is Loading`() = runTest {
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.loadPatientAppointments(1L)
        viewModel.loadNextPatientAppointmentsPage() // called while first load is in-flight
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.perPatientPaginationState.value.currentPage)
    }

    @Test
    fun `loadNextPatientAppointmentsPage is no-op when hasMore is false`() = runTest {
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(makePage(1L, 3))

        viewModel.loadPatientAppointments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.perPatientPaginationState.value.hasMore)
        val pageBefore = viewModel.perPatientPaginationState.value.currentPage

        viewModel.loadNextPatientAppointmentsPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(pageBefore, viewModel.perPatientPaginationState.value.currentPage)
    }

    // ========================================
    // Patient change resets state
    // ========================================

    @Test
    fun `loading for different patientId resets state`() = runTest {
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE, patientId = 1L))
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(2L), eq(0)))
            .thenReturn(makePage(100L, 5, patientId = 2L))

        viewModel.loadPatientAppointments(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Constants.PAGE_SIZE, viewModel.perPatientPaginationState.value.items.size)

        viewModel.loadPatientAppointments(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.perPatientPaginationState.value.items.size)
        assertEquals(2L, viewModel.perPatientPaginationState.value.items.first().appointment.patientId)
    }

    // ========================================
    // hasMore detection
    // ========================================

    @Test
    fun `last page smaller than PAGE_SIZE sets hasMore to false`() = runTest {
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE - 1))

        viewModel.loadPatientAppointments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.perPatientPaginationState.value.hasMore)
    }

    @Test
    fun `full page sets hasMore to true`() = runTest {
        whenever(mockRepository.getPagedByPatientWithPaymentStatus(eq(1L), eq(0)))
            .thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.loadPatientAppointments(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.perPatientPaginationState.value.hasMore)
    }
}
