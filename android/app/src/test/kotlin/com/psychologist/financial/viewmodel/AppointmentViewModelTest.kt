package com.psychologist.financial.viewmodel

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase
import com.psychologist.financial.domain.usecases.GetAllAppointmentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import com.psychologist.financial.domain.usecases.UpdateAppointmentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for AppointmentViewModel — global list and filter functionality.
 *
 * Tests:
 * - setFilter(PENDING) returns only appointments with hasPendingPayment = true
 * - setFilter(PAID) returns only appointments with hasPendingPayment = false
 * - setFilter(ALL) returns all appointments
 * - loadAllAppointments() emits Empty when no appointments exist
 *
 * Run with: ./gradlew testDebugUnitTest --tests AppointmentViewModelTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: AppointmentRepository
    private lateinit var getPatientAppointmentsUseCase: GetPatientAppointmentsUseCase
    private lateinit var createAppointmentUseCase: CreateAppointmentUseCase
    private lateinit var updateAppointmentUseCase: UpdateAppointmentUseCase
    private lateinit var getAllAppointmentsUseCase: GetAllAppointmentsUseCase
    private lateinit var viewModel: AppointmentViewModel

    private val appointmentPending = Appointment(
        id = 1L,
        patientId = 1L,
        date = LocalDate.of(2024, 3, 15),
        timeStart = LocalTime.of(14, 0),
        durationMinutes = 60
    )

    private val appointmentPaid = Appointment(
        id = 2L,
        patientId = 1L,
        date = LocalDate.of(2024, 3, 10),
        timeStart = LocalTime.of(10, 0),
        durationMinutes = 50
    )

    private val pendingWithStatus = AppointmentWithPaymentStatus(
        appointment = appointmentPending,
        hasPendingPayment = true
    )

    private val paidWithStatus = AppointmentWithPaymentStatus(
        appointment = appointmentPaid,
        hasPendingPayment = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        getPatientAppointmentsUseCase = mock()
        createAppointmentUseCase = mock()
        updateAppointmentUseCase = mock()
        getAllAppointmentsUseCase = mock()

        viewModel = AppointmentViewModel(
            repository = repository,
            getPatientAppointmentsUseCase = getPatientAppointmentsUseCase,
            createAppointmentUseCase = createAppointmentUseCase,
            updateAppointmentUseCase = updateAppointmentUseCase,
            getAllAppointmentsUseCase = getAllAppointmentsUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setFilter_PENDING_returnsOnlyAppointmentsWithHasPendingPaymentTrue() = runTest {
        whenever(getAllAppointmentsUseCase.execute())
            .thenReturn(flowOf(listOf(pendingWithStatus, paidWithStatus)))

        viewModel.loadAllAppointments()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(AppointmentViewState.AppointmentFilter.PENDING)

        val state = viewModel.globalListState.value
        assertTrue(state is AppointmentViewState.GlobalListState.Success)
        val success = state as AppointmentViewState.GlobalListState.Success
        assertEquals(1, success.filteredAppointments.size)
        assertTrue(success.filteredAppointments.first().hasPendingPayment)
    }

    @Test
    fun setFilter_PAID_returnsOnlyAppointmentsWithHasPendingPaymentFalse() = runTest {
        whenever(getAllAppointmentsUseCase.execute())
            .thenReturn(flowOf(listOf(pendingWithStatus, paidWithStatus)))

        viewModel.loadAllAppointments()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(AppointmentViewState.AppointmentFilter.PAID)

        val state = viewModel.globalListState.value
        assertTrue(state is AppointmentViewState.GlobalListState.Success)
        val success = state as AppointmentViewState.GlobalListState.Success
        assertEquals(1, success.filteredAppointments.size)
        assertFalse(success.filteredAppointments.first().hasPendingPayment)
    }

    @Test
    fun setFilter_ALL_returnsAllAppointments() = runTest {
        whenever(getAllAppointmentsUseCase.execute())
            .thenReturn(flowOf(listOf(pendingWithStatus, paidWithStatus)))

        viewModel.loadAllAppointments()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(AppointmentViewState.AppointmentFilter.ALL)

        val state = viewModel.globalListState.value
        assertTrue(state is AppointmentViewState.GlobalListState.Success)
        val success = state as AppointmentViewState.GlobalListState.Success
        assertEquals(2, success.filteredAppointments.size)
    }

    @Test
    fun loadAllAppointments_emptyList_emitsEmptyState() = runTest {
        whenever(getAllAppointmentsUseCase.execute()).thenReturn(flowOf(emptyList()))

        viewModel.loadAllAppointments()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.globalListState.value is AppointmentViewState.GlobalListState.Empty)
    }
}
