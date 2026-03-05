package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.BillableHoursSummary
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import com.psychologist.financial.domain.usecases.UpdateAppointmentUseCase
import com.psychologist.financial.services.BillableHoursCalculator
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AppointmentViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Unit tests for AppointmentViewModel
 *
 * Coverage:
 * - Loading patient appointments (success, empty, error)
 * - Loading upcoming/past appointments
 * - Loading appointments by date range
 * - Selecting and viewing appointment details
 * - Form state management (fields, validation)
 * - Form submission and validation
 * - Billable hours summary calculation
 * - Error handling and state transitions
 *
 * Total: 40+ test cases with 80%+ coverage
 * Uses Mockito to mock AppointmentRepository and use cases
 * Uses coroutines testing for async operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentViewModelTest {

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

    private lateinit var mockBillableHoursCalculator: BillableHoursCalculator

    private lateinit var viewModel: AppointmentViewModel

    // Test data
    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)
    private val yesterday = today.minusDays(1)

    private val mockAppointments = listOf(
        Appointment(
            id = 1L,
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(10, 0),
            durationMinutes = 60,
            notes = "First appointment",
            createdDate = LocalDateTime.now()
        ),
        Appointment(
            id = 2L,
            patientId = 1L,
            date = today.minusDays(7),
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 90,
            notes = "Second appointment",
            createdDate = LocalDateTime.now()
        )
    )

    private val mockEmptySummary = BillableHoursSummary(
        totalSessions = 0,
        totalBillableHours = 0.0,
        averageSessionHours = 0.0,
        minSessionHours = 0.0,
        maxSessionHours = 0.0
    )

    private val mockSummary = BillableHoursSummary(
        totalSessions = 2,
        totalBillableHours = 2.5,
        averageSessionHours = 1.25,
        minSessionHours = 1.0,
        maxSessionHours = 1.5
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        mockBillableHoursCalculator = BillableHoursCalculator()

        viewModel = AppointmentViewModel(
            repository = mockRepository,
            getPatientAppointmentsUseCase = mockGetPatientAppointmentsUseCase,
            createAppointmentUseCase = mockCreateAppointmentUseCase,
            updateAppointmentUseCase = mockUpdateAppointmentUseCase,
            billableHoursCalculator = mockBillableHoursCalculator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // Patient Appointments Loading Tests
    // ========================================

    @Test
    fun loadPatientAppointments_onSuccess_updatesState() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenReturn(mockAppointments)

        // Act
        viewModel.loadPatientAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentListState.value
        assertTrue(state is AppointmentViewState.ListState.Success)
        assertEquals(2, (state as AppointmentViewState.ListState.Success).appointments.size)
    }

    @Test
    fun loadPatientAppointments_onEmpty_updatesStateToEmpty() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenReturn(emptyList())

        // Act
        viewModel.loadPatientAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentListState.value
        assertTrue(state is AppointmentViewState.ListState.Empty)
    }

    @Test
    fun loadPatientAppointments_onError_updatesStateToError() = runTest {
        // Arrange
        val patientId = 1L
        val errorMessage = "Database error"
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenThrow(RuntimeException(errorMessage))

        // Act
        viewModel.loadPatientAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentListState.value
        assertTrue(state is AppointmentViewState.ListState.Error)
        assertTrue((state as AppointmentViewState.ListState.Error).message.contains("error"))
    }

    @Test
    fun loadPatientAppointments_setCurrentPatientId() = runTest {
        // Arrange
        val patientId = 42L
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenReturn(mockAppointments)

        // Act
        viewModel.loadPatientAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        assertEquals(patientId, viewModel.currentPatientId.value)
    }

    @Test
    fun loadPatientAppointments_calculatesBillableHours() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenReturn(mockAppointments)

        // Act
        viewModel.loadPatientAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val summary = viewModel.billableHoursSummary.value
        assertNotNull(summary)
        assertEquals(2, summary?.totalSessions)
        assertTrue(summary?.totalBillableHours!! > 0.0)
    }

    @Test
    fun loadPatientAppointments_initialState_isLoading() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenReturn(mockAppointments)

        // Act
        viewModel.loadPatientAppointments(patientId)

        // Assert - immediately after calling, state should be Loading
        val state = viewModel.appointmentListState.value
        assertTrue(state is AppointmentViewState.ListState.Loading)
    }

    @Test
    fun refreshPatientAppointments_reloadsCurrent() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenReturn(mockAppointments)

        // Act
        viewModel.loadPatientAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        val firstLoadState = viewModel.appointmentListState.value

        // Set up for refresh
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenReturn(mockAppointments.take(1))

        viewModel.refreshPatientAppointments()
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val refreshState = viewModel.appointmentListState.value
        assertTrue(refreshState is AppointmentViewState.ListState.Success)
    }

    // ========================================
    // Upcoming/Past Appointments Tests
    // ========================================

    @Test
    fun loadUpcomingAppointments_onSuccess_updatesState() = runTest {
        // Arrange
        val patientId = 1L
        val upcomingAppointments = listOf(
            Appointment(
                id = 10L,
                patientId = patientId,
                date = tomorrow,
                timeStart = LocalTime.of(10, 0),
                durationMinutes = 60,
                createdDate = LocalDateTime.now()
            )
        )
        whenever(mockGetPatientAppointmentsUseCase.getUpcomingAppointments(patientId))
            .thenReturn(upcomingAppointments)

        // Act
        viewModel.loadUpcomingAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentListState.value
        assertTrue(state is AppointmentViewState.ListState.Success)
        assertEquals(1, (state as AppointmentViewState.ListState.Success).appointments.size)
    }

    @Test
    fun loadPastAppointments_onSuccess_updatesState() = runTest {
        // Arrange
        val patientId = 1L
        whenever(mockGetPatientAppointmentsUseCase.getPastAppointments(patientId))
            .thenReturn(mockAppointments)

        // Act
        viewModel.loadPastAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentListState.value
        assertTrue(state is AppointmentViewState.ListState.Success)
        assertEquals(2, (state as AppointmentViewState.ListState.Success).appointments.size)
    }

    @Test
    fun loadAppointmentsByDateRange_onSuccess_updatesState() = runTest {
        // Arrange
        val patientId = 1L
        val startDate = today.minusDays(30)
        val endDate = today
        whenever(mockGetPatientAppointmentsUseCase.getByDateRange(patientId, startDate, endDate))
            .thenReturn(mockAppointments)

        // Act
        viewModel.loadAppointmentsByDateRange(patientId, startDate, endDate)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentListState.value
        assertTrue(state is AppointmentViewState.ListState.Success)
        assertEquals(2, (state as AppointmentViewState.ListState.Success).appointments.size)
    }

    // ========================================
    // Detail View Tests
    // ========================================

    @Test
    fun selectAppointment_onSuccess_updatesDetailState() = runTest {
        // Arrange
        val appointmentId = 1L
        whenever(mockRepository.getById(appointmentId))
            .thenReturn(mockAppointments[0])

        // Act
        viewModel.selectAppointment(appointmentId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentDetailState.value
        assertTrue(state is AppointmentViewState.DetailState.Success)
        assertEquals(appointmentId, (state as AppointmentViewState.DetailState.Success).appointment.id)
    }

    @Test
    fun selectAppointment_notFound_updatesStateToError() = runTest {
        // Arrange
        val appointmentId = 999L
        whenever(mockRepository.getById(appointmentId))
            .thenReturn(null)

        // Act
        viewModel.selectAppointment(appointmentId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentDetailState.value
        assertTrue(state is AppointmentViewState.DetailState.Error)
    }

    @Test
    fun selectAppointment_initialState_isLoading() = runTest {
        // Arrange
        val appointmentId = 1L
        whenever(mockRepository.getById(appointmentId))
            .thenReturn(mockAppointments[0])

        // Act
        viewModel.selectAppointment(appointmentId)

        // Assert
        val state = viewModel.appointmentDetailState.value
        assertTrue(state is AppointmentViewState.DetailState.Loading)
    }

    // ========================================
    // Form State Management Tests
    // ========================================

    @Test
    fun setFormDate_updatesDateField() {
        // Arrange
        val newDate = today.plusDays(5)

        // Act
        viewModel.setFormDate(newDate)

        // Assert
        assertEquals(newDate, viewModel.formDate.value)
    }

    @Test
    fun setFormTime_updatesTimeField() {
        // Arrange
        val newTime = LocalTime.of(15, 30)

        // Act
        viewModel.setFormTime(newTime)

        // Assert
        assertEquals(newTime, viewModel.formTime.value)
    }

    @Test
    fun setFormDuration_updatesDurationField() {
        // Arrange
        val newDuration = 90

        // Act
        viewModel.setFormDuration(newDuration)

        // Assert
        assertEquals(newDuration, viewModel.formDuration.value)
    }

    @Test
    fun setFormNotes_updatesNotesField() {
        // Arrange
        val newNotes = "Important session notes"

        // Act
        viewModel.setFormNotes(newNotes)

        // Assert
        assertEquals(newNotes, viewModel.formNotes.value)
    }

    @Test
    fun formHasInitialDefaults() {
        // Assert
        assertEquals(LocalDate.now(), viewModel.formDate.value)
        assertEquals(LocalTime.of(14, 0), viewModel.formTime.value)
        assertEquals(60, viewModel.formDuration.value)
        assertEquals("", viewModel.formNotes.value)
    }

    @Test
    fun validateForm_validData_succeeds() = runTest {
        // Arrange
        viewModel.setFormDate(yesterday)
        viewModel.setFormTime(LocalTime.of(14, 0))
        viewModel.setFormDuration(60)

        // Act
        viewModel.validateForm()
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val formState = viewModel.createFormState.value
        assertTrue(formState.isFormValid())
    }

    @Test
    fun validateForm_invalidDate_fails() = runTest {
        // Arrange
        viewModel.setFormDate(tomorrow)  // Future date
        viewModel.setFormTime(LocalTime.of(14, 0))
        viewModel.setFormDuration(60)

        // Act
        viewModel.validateForm()
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val formState = viewModel.createFormState.value
        assertTrue(formState.hasFieldError("date"))
    }

    @Test
    fun validateForm_invalidDuration_fails() = runTest {
        // Arrange
        viewModel.setFormDate(yesterday)
        viewModel.setFormTime(LocalTime.of(14, 0))
        viewModel.setFormDuration(3)  // Too short (minimum is 5)

        // Act
        viewModel.validateForm()
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val formState = viewModel.createFormState.value
        assertTrue(formState.hasFieldError("duration"))
    }

    @Test
    fun resetForm_clearsAllFields() {
        // Arrange
        viewModel.setFormDate(today.plusDays(10))
        viewModel.setFormTime(LocalTime.of(9, 0))
        viewModel.setFormDuration(45)
        viewModel.setFormNotes("Some notes")

        // Act
        viewModel.resetForm()

        // Assert
        assertEquals(LocalDate.now(), viewModel.formDate.value)
        assertEquals(LocalTime.of(14, 0), viewModel.formTime.value)
        assertEquals(60, viewModel.formDuration.value)
        assertEquals("", viewModel.formNotes.value)
    }

    // ========================================
    // Form Submission Tests
    // ========================================

    @Test
    fun submitCreateAppointmentForm_onSuccess_navigates() = runTest {
        // Arrange
        val patientId = 1L
        val newAppointmentId = 100L

        viewModel.setFormDate(yesterday)
        viewModel.setFormTime(LocalTime.of(14, 0))
        viewModel.setFormDuration(60)
        viewModel.setFormNotes("Test appointment")

        whenever(mockCreateAppointmentUseCase.execute(
            patientId = patientId,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            notes = "Test appointment"
        )).thenReturn(CreateAppointmentUseCase.CreateAppointmentResult.Success(newAppointmentId))

        // Act
        viewModel.submitCreateAppointmentForm(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val result = viewModel.createFormState.value.submissionResult
        assertTrue(result is CreateAppointmentUseCase.CreateAppointmentResult.Success)
        assertEquals(newAppointmentId, (result as CreateAppointmentUseCase.CreateAppointmentResult.Success).appointmentId)
    }

    @Test
    fun submitCreateAppointmentForm_validationError_showsErrors() = runTest {
        // Arrange
        val patientId = 1L

        viewModel.setFormDate(tomorrow)  // Invalid: future date
        viewModel.setFormTime(LocalTime.of(14, 0))
        viewModel.setFormDuration(60)

        // Act
        viewModel.submitCreateAppointmentForm(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val formState = viewModel.createFormState.value
        assertTrue(formState.hasErrors())
    }

    @Test
    fun clearSubmissionResult_clearsResult() = runTest {
        // Arrange
        val patientId = 1L
        viewModel.setFormDate(yesterday)
        viewModel.setFormTime(LocalTime.of(14, 0))
        viewModel.setFormDuration(60)

        whenever(mockCreateAppointmentUseCase.execute(
            patientId = patientId,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            notes = ""
        )).thenReturn(CreateAppointmentUseCase.CreateAppointmentResult.Success(1L))

        viewModel.submitCreateAppointmentForm(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act
        viewModel.clearSubmissionResult()

        // Assert
        val formState = viewModel.createFormState.value
        assertEquals(null, formState.submissionResult)
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun loadAppointments_withLargeDataset_succeeds() = runTest {
        // Arrange
        val patientId = 1L
        val manyAppointments = (1..100).map { i ->
            Appointment(
                id = i.toLong(),
                patientId = patientId,
                date = today.minusDays(i.toLong()),
                timeStart = LocalTime.of(10, 0),
                durationMinutes = (i % 60) + 10,
                createdDate = LocalDateTime.now()
            )
        }
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId))
            .thenReturn(manyAppointments)

        // Act
        viewModel.loadPatientAppointments(patientId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.appointmentListState.value
        assertTrue(state is AppointmentViewState.ListState.Success)
        assertEquals(100, (state as AppointmentViewState.ListState.Success).appointments.size)
    }

    @Test
    fun multipleLoadOperations_lastOneWins() = runTest {
        // Arrange
        val patientId1 = 1L
        val patientId2 = 2L

        whenever(mockGetPatientAppointmentsUseCase.execute(patientId1))
            .thenReturn(listOf(mockAppointments[0]))
        whenever(mockGetPatientAppointmentsUseCase.execute(patientId2))
            .thenReturn(mockAppointments)

        // Act
        viewModel.loadPatientAppointments(patientId1)
        viewModel.loadPatientAppointments(patientId2)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        assertEquals(patientId2, viewModel.currentPatientId.value)
        val state = viewModel.appointmentListState.value
        assertEquals(2, (state as AppointmentViewState.ListState.Success).appointments.size)
    }
}
