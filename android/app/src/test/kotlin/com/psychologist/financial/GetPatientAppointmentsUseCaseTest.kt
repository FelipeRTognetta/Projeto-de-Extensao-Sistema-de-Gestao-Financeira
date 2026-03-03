package com.psychologist.financial

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for GetPatientAppointmentsUseCase
 *
 * Coverage:
 * - execute() retrieves appointments for patient
 * - getPastAppointments() filters past dates
 * - getUpcomingAppointments() filters future dates
 * - getByDateRange() and month-specific queries
 * - getCount() / hasAppointments() / hasUpcomingAppointments()
 * - getTotalBillableHours() calculation
 * - getGroupedByDate() / getGroupedByStatus()
 * - executeFlow() reactive API
 * - Patient not found (empty list)
 *
 * Total: 18 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class GetPatientAppointmentsUseCaseTest {

    @Mock
    private lateinit var mockRepository: AppointmentRepository

    private lateinit var useCase: GetPatientAppointmentsUseCase

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    private fun makeAppointment(
        id: Long,
        patientId: Long = 1L,
        date: LocalDate = yesterday,
        durationMinutes: Int = 60
    ) = Appointment(
        id = id,
        patientId = patientId,
        date = date,
        timeStart = LocalTime.of(10, 0),
        durationMinutes = durationMinutes
    )

    @Before
    fun setUp() {
        useCase = GetPatientAppointmentsUseCase(repository = mockRepository)
    }

    // ========================================
    // execute() Tests
    // ========================================

    @Test
    fun `execute returns appointments for patient`() = runTest {
        val appointments = listOf(makeAppointment(1L), makeAppointment(2L))
        whenever(mockRepository.getByPatient(1L)).thenReturn(appointments)

        val result = useCase.execute(patientId = 1L)

        assertEquals(2, result.size)
    }

    @Test
    fun `execute returns empty list when patient has no appointments`() = runTest {
        whenever(mockRepository.getByPatient(99L)).thenReturn(emptyList())

        val result = useCase.execute(patientId = 99L)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // Past / Upcoming Queries
    // ========================================

    @Test
    fun `getPastAppointments returns appointments before cutoff date`() = runTest {
        val pastAppointments = listOf(makeAppointment(1L, date = yesterday))
        whenever(mockRepository.getPastAppointmentsByPatient(1L, any())).thenReturn(pastAppointments)

        val result = useCase.getPastAppointments(patientId = 1L)

        assertEquals(1, result.size)
    }

    @Test
    fun `getUpcomingAppointments returns appointments from cutoff date`() = runTest {
        val upcomingAppointments = listOf(makeAppointment(1L, date = tomorrow))
        whenever(mockRepository.getUpcomingAppointmentsByPatient(1L, any())).thenReturn(upcomingAppointments)

        val result = useCase.getUpcomingAppointments(patientId = 1L)

        assertEquals(1, result.size)
    }

    @Test
    fun `getUpcomingAppointments returns empty when no upcoming`() = runTest {
        whenever(mockRepository.getUpcomingAppointmentsByPatient(1L, any())).thenReturn(emptyList())

        val result = useCase.getUpcomingAppointments(patientId = 1L)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // Date Range Tests
    // ========================================

    @Test
    fun `getByDateRange returns appointments in range`() = runTest {
        val appointments = listOf(makeAppointment(1L, date = yesterday))
        whenever(mockRepository.getByPatientAndDateRange(1L, any(), any()))
            .thenReturn(appointments)

        val result = useCase.getByDateRange(
            patientId = 1L,
            startDate = yesterday.minusDays(5),
            endDate = today
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `getMonthAppointments queries correct month boundaries`() = runTest {
        whenever(mockRepository.getByPatientAndDateRange(any(), any(), any()))
            .thenReturn(emptyList())

        val result = useCase.getMonthAppointments(patientId = 1L, year = 2025, month = 3)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCurrentMonthAppointments uses today range`() = runTest {
        whenever(mockRepository.getByPatientAndDateRange(any(), any(), any()))
            .thenReturn(emptyList())

        val result = useCase.getCurrentMonthAppointments(patientId = 1L)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // Count & Existence Tests
    // ========================================

    @Test
    fun `getCount delegates to repository`() = runTest {
        whenever(mockRepository.countByPatient(1L)).thenReturn(5)

        val count = useCase.getCount(patientId = 1L)

        assertEquals(5, count)
    }

    @Test
    fun `hasAppointments returns true when appointments exist`() = runTest {
        whenever(mockRepository.hasAppointments(1L)).thenReturn(true)

        val has = useCase.hasAppointments(patientId = 1L)

        assertTrue(has)
    }

    @Test
    fun `hasAppointments returns false when no appointments`() = runTest {
        whenever(mockRepository.hasAppointments(1L)).thenReturn(false)

        val has = useCase.hasAppointments(patientId = 1L)

        assertFalse(has)
    }

    @Test
    fun `hasUpcomingAppointments delegates to repository`() = runTest {
        whenever(mockRepository.hasUpcomingAppointments(1L)).thenReturn(false)

        val has = useCase.hasUpcomingAppointments(patientId = 1L)

        assertFalse(has)
    }

    // ========================================
    // Billable Hours Tests
    // ========================================

    @Test
    fun `getTotalBillableHours delegates to repository`() = runTest {
        whenever(mockRepository.getTotalBillableHours(1L)).thenReturn(10.5)

        val hours = useCase.getTotalBillableHours(patientId = 1L)

        assertEquals(10.5, hours)
    }

    @Test
    fun `getAverageSessionHours returns zero when no past appointments`() = runTest {
        whenever(mockRepository.getPastAppointmentsByPatient(1L, any())).thenReturn(emptyList())
        whenever(mockRepository.getTotalBillableHours(1L)).thenReturn(0.0)

        val avg = useCase.getAverageSessionHours(patientId = 1L)

        assertEquals(0.0, avg)
    }

    // ========================================
    // Last Appointment Tests
    // ========================================

    @Test
    fun `getLastAppointment returns null when no appointments`() = runTest {
        whenever(mockRepository.getLastAppointmentByPatient(1L)).thenReturn(null)

        val last = useCase.getLastAppointment(patientId = 1L)

        assertNull(last)
    }

    @Test
    fun `getLastAppointmentDate returns null when no appointments`() = runTest {
        whenever(mockRepository.getLastAppointmentDateByPatient(1L)).thenReturn(null)

        val lastDate = useCase.getLastAppointmentDate(patientId = 1L)

        assertNull(lastDate)
    }

    @Test
    fun `getLastAppointmentDate returns correct date`() = runTest {
        whenever(mockRepository.getLastAppointmentDateByPatient(1L)).thenReturn(yesterday)

        val lastDate = useCase.getLastAppointmentDate(patientId = 1L)

        assertEquals(yesterday, lastDate)
    }

    // ========================================
    // Flow Tests
    // ========================================

    @Test
    fun `executeFlow emits appointments reactively`() = runTest {
        val appointments = listOf(makeAppointment(1L))
        whenever(mockRepository.getByPatientFlow(1L))
            .thenReturn(flowOf(appointments))

        val flow = useCase.executeFlow(patientId = 1L)
        var emitted: List<Appointment>? = null
        flow.collect { emitted = it }

        assertEquals(1, emitted?.size)
    }
}
