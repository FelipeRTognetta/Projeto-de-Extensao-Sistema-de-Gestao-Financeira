package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase
import com.psychologist.financial.domain.usecases.GetAllAppointmentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import com.psychologist.financial.domain.usecases.UpdateAppointmentUseCase
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AppointmentViewState
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
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentViewModelNameFilterTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock private lateinit var mockRepository: AppointmentRepository
    @Mock private lateinit var mockGetPatientAppointmentsUseCase: GetPatientAppointmentsUseCase
    @Mock private lateinit var mockCreateAppointmentUseCase: CreateAppointmentUseCase
    @Mock private lateinit var mockUpdateAppointmentUseCase: UpdateAppointmentUseCase
    @Mock private lateinit var mockGetAllAppointmentsUseCase: GetAllAppointmentsUseCase

    private lateinit var viewModel: AppointmentViewModel

    private fun makeAppointment(id: Long) = Appointment(
        id = id,
        patientId = id,
        date = LocalDate.now(),
        timeStart = LocalTime.of(9, 0),
        durationMinutes = 50
    )

    private val allAppointments = listOf(
        AppointmentWithPaymentStatus(appointment = makeAppointment(1L), hasPendingPayment = true, patientName = "Ana Lima"),
        AppointmentWithPaymentStatus(appointment = makeAppointment(2L), hasPendingPayment = false, patientName = "Carlos Silva"),
        AppointmentWithPaymentStatus(appointment = makeAppointment(3L), hasPendingPayment = true, patientName = "Joana Pereira"),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(mockGetAllAppointmentsUseCase.execute()).thenReturn(flowOf(allAppointments))
        viewModel = AppointmentViewModel(
            repository = mockRepository,
            getPatientAppointmentsUseCase = mockGetPatientAppointmentsUseCase,
            createAppointmentUseCase = mockCreateAppointmentUseCase,
            updateAppointmentUseCase = mockUpdateAppointmentUseCase,
            getAllAppointmentsUseCase = mockGetAllAppointmentsUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setNameFilter filters appointments by patient name case insensitive`() = runTest {
        viewModel.loadAllAppointments()
        advanceUntilIdle()

        viewModel.setNameFilter("ana")
        advanceUntilIdle()

        val state = viewModel.globalListState.value
        assertTrue(state is AppointmentViewState.GlobalListState.Success)
        val filtered = (state as AppointmentViewState.GlobalListState.Success).filteredAppointments
        // "Ana Lima" and "Joana Pereira" both contain "ana"
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.patientName.contains("ana", ignoreCase = true) })
    }

    @Test
    fun `setNameFilter with empty string returns all appointments`() = runTest {
        viewModel.loadAllAppointments()
        advanceUntilIdle()

        viewModel.setNameFilter("carlos")
        advanceUntilIdle()
        viewModel.setNameFilter("")
        advanceUntilIdle()

        val state = viewModel.globalListState.value
        assertTrue(state is AppointmentViewState.GlobalListState.Success)
        val filtered = (state as AppointmentViewState.GlobalListState.Success).filteredAppointments
        assertEquals(allAppointments.size, filtered.size)
    }

    @Test
    fun `setNameFilter combined with payment status filter applies intersection`() = runTest {
        viewModel.loadAllAppointments()
        advanceUntilIdle()

        // Apply PENDING status filter first
        viewModel.setFilter(AppointmentViewState.AppointmentFilter.PENDING)
        advanceUntilIdle()

        // Apply name filter "ana" — should match only Ana Lima (pending) and Joana Pereira (pending)
        viewModel.setNameFilter("ana")
        advanceUntilIdle()

        val state = viewModel.globalListState.value
        assertTrue(state is AppointmentViewState.GlobalListState.Success)
        val filtered = (state as AppointmentViewState.GlobalListState.Success).filteredAppointments
        assertTrue(filtered.all { it.hasPendingPayment && it.patientName.contains("ana", ignoreCase = true) })
    }

    @Test
    fun `resetNameFilter restores status-filtered list without name filter`() = runTest {
        viewModel.loadAllAppointments()
        advanceUntilIdle()
        viewModel.setFilter(AppointmentViewState.AppointmentFilter.PENDING)
        advanceUntilIdle()
        viewModel.setNameFilter("ana")
        advanceUntilIdle()

        viewModel.resetNameFilter()
        advanceUntilIdle()

        val state = viewModel.globalListState.value
        assertTrue(state is AppointmentViewState.GlobalListState.Success)
        val filtered = (state as AppointmentViewState.GlobalListState.Success).filteredAppointments
        // Back to all PENDING appointments
        assertEquals(allAppointments.count { it.hasPendingPayment }, filtered.size)
    }
}
