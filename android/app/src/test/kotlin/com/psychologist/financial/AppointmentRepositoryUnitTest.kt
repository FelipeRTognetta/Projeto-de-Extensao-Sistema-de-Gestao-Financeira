package com.psychologist.financial

import com.psychologist.financial.data.database.AppointmentDao
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AppointmentRepository
 *
 * Coverage:
 * - getByPatient() returns mapped domain list
 * - getByPatientFlow() delegates to DAO flow
 * - getPastAppointmentsByPatient() with before-date filter
 * - getUpcomingAppointmentsByPatient() with from-date filter
 * - getByPatientAndDateRange() date range query
 * - countByPatient() delegates to DAO
 * - hasAppointments() returns true/false from DAO
 * - hasUpcomingAppointments() delegates to DAO
 * - getTotalBillableHours() calculation
 * - getLastAppointmentByPatient() / getLastAppointmentDateByPatient()
 *
 * Total: 14 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class AppointmentRepositoryUnitTest {

    @Mock
    private lateinit var mockAppointmentDao: AppointmentDao

    private lateinit var repository: AppointmentRepository

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    @Before
    fun setUp() {
        repository = AppointmentRepository(appointmentDao = mockAppointmentDao)
    }

    private fun makeAppointmentEntity(id: Long, patientId: Long = 1L, date: LocalDate = yesterday) =
        com.psychologist.financial.data.entities.AppointmentEntity(
            id = id,
            patientId = patientId,
            date = date,
            time = LocalTime.of(10, 0),
            durationMinutes = 60,
            notes = null
        )

    // ========================================
    // getByPatient() Tests
    // ========================================

    @Test
    fun `getByPatient returns mapped appointments`() = runTest {
        val entities = listOf(makeAppointmentEntity(1L), makeAppointmentEntity(2L))
        whenever(mockAppointmentDao.getAppointmentsByPatient(1L)).thenReturn(entities)

        val result = repository.getByPatient(patientId = 1L)

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun `getByPatient returns empty list when no appointments`() = runTest {
        whenever(mockAppointmentDao.getAppointmentsByPatient(99L)).thenReturn(emptyList())

        val result = repository.getByPatient(patientId = 99L)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // getByPatientFlow() Tests
    // ========================================

    @Test
    fun `getByPatientFlow emits mapped appointments`() = runTest {
        val entities = listOf(makeAppointmentEntity(1L))
        whenever(mockAppointmentDao.getAppointmentsByPatientFlow(1L))
            .thenReturn(flowOf(entities))

        val flow = repository.getByPatientFlow(patientId = 1L)
        var emitted: List<Appointment>? = null
        flow.collect { emitted = it }

        assertNotNull(emitted)
        assertEquals(1, emitted!!.size)
    }

    // ========================================
    // Past/Upcoming Tests
    // ========================================

    @Test
    fun `getPastAppointmentsByPatient returns appointments before cutoff`() = runTest {
        val pastEntity = makeAppointmentEntity(1L, date = yesterday)
        whenever(mockAppointmentDao.getPastAppointmentsByPatient(any(), any()))
            .thenReturn(listOf(pastEntity))

        val result = repository.getPastAppointmentsByPatient(1L, today)

        assertEquals(1, result.size)
        assertTrue(result[0].date.isBefore(today))
    }

    @Test
    fun `getUpcomingAppointmentsByPatient returns future appointments`() = runTest {
        val upcoming = makeAppointmentEntity(1L, date = tomorrow)
        whenever(mockAppointmentDao.getUpcomingAppointmentsByPatient(any(), any()))
            .thenReturn(listOf(upcoming))

        val result = repository.getUpcomingAppointmentsByPatient(1L, today)

        assertEquals(1, result.size)
        assertTrue(result[0].date.isAfter(today.minusDays(1)))
    }

    // ========================================
    // Date Range Tests
    // ========================================

    @Test
    fun `getByPatientAndDateRange returns appointments in range`() = runTest {
        val entity = makeAppointmentEntity(1L, date = yesterday)
        whenever(mockAppointmentDao.getAppointmentsByPatientAndDateRange(any(), any(), any()))
            .thenReturn(listOf(entity))

        val result = repository.getByPatientAndDateRange(
            patientId = 1L,
            startDate = yesterday.minusDays(5),
            endDate = today
        )

        assertEquals(1, result.size)
    }

    // ========================================
    // Count/Existence Tests
    // ========================================

    @Test
    fun `countByPatient delegates to DAO`() = runTest {
        whenever(mockAppointmentDao.countByPatient(1L)).thenReturn(8)

        val count = repository.countByPatient(patientId = 1L)

        assertEquals(8, count)
    }

    @Test
    fun `hasAppointments returns true when count greater than zero`() = runTest {
        whenever(mockAppointmentDao.countByPatient(1L)).thenReturn(3)

        val has = repository.hasAppointments(patientId = 1L)

        assertTrue(has)
    }

    @Test
    fun `hasAppointments returns false when count is zero`() = runTest {
        whenever(mockAppointmentDao.countByPatient(1L)).thenReturn(0)

        val has = repository.hasAppointments(patientId = 1L)

        assertFalse(has)
    }

    @Test
    fun `hasUpcomingAppointments delegates to DAO`() = runTest {
        whenever(mockAppointmentDao.hasUpcomingAppointments(any(), any())).thenReturn(true)

        val has = repository.hasUpcomingAppointments(patientId = 1L)

        assertTrue(has)
    }

    // ========================================
    // Billable Hours Tests
    // ========================================

    @Test
    fun `getTotalBillableHours calculates from past appointments`() = runTest {
        val entities = listOf(
            makeAppointmentEntity(1L, date = yesterday).copy(durationMinutes = 60),
            makeAppointmentEntity(2L, date = yesterday).copy(durationMinutes = 90)
        )
        whenever(mockAppointmentDao.getPastAppointmentsByPatient(any(), any()))
            .thenReturn(entities)

        val hours = repository.getTotalBillableHours(patientId = 1L)

        assertEquals(2.5, hours, 0.01)
    }

    // ========================================
    // Last Appointment Tests
    // ========================================

    @Test
    fun `getLastAppointmentByPatient returns null when no appointments`() = runTest {
        whenever(mockAppointmentDao.getLastAppointmentByPatient(1L)).thenReturn(null)

        val last = repository.getLastAppointmentByPatient(patientId = 1L)

        assertNull(last)
    }

    @Test
    fun `getLastAppointmentDateByPatient returns date from DAO`() = runTest {
        whenever(mockAppointmentDao.getLastAppointmentDateByPatient(1L)).thenReturn(yesterday)

        val lastDate = repository.getLastAppointmentDateByPatient(patientId = 1L)

        assertEquals(yesterday, lastDate)
    }

    @Test
    fun `getLastAppointmentDateByPatient returns null when no appointments`() = runTest {
        whenever(mockAppointmentDao.getLastAppointmentDateByPatient(1L)).thenReturn(null)

        val lastDate = repository.getLastAppointmentDateByPatient(patientId = 1L)

        assertNull(lastDate)
    }
}
