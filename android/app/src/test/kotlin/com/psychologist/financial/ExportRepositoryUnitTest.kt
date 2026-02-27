package com.psychologist.financial

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.AppointmentDao
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.data.repositories.ExportRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ExportRepository
 *
 * Coverage:
 * - getAllPatients() maps entities to domain models
 * - getAllPatients() returns empty list
 * - getAllAppointments() maps entities to domain models
 * - getAllPayments() maps entities to domain models
 * - countAllPatients() delegates to DAO
 * - countAllAppointments() delegates to DAO
 * - countAllPayments() delegates to DAO
 * - hasDataToExport() returns true when any count > 0
 * - hasDataToExport() returns false when all counts zero
 * - getExportStatistics() returns complete map
 *
 * Total: 12 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class ExportRepositoryUnitTest {

    @Mock
    private lateinit var mockDatabase: AppDatabase

    @Mock
    private lateinit var mockPatientDao: PatientDao

    @Mock
    private lateinit var mockAppointmentDao: AppointmentDao

    @Mock
    private lateinit var mockPaymentDao: PaymentDao

    private lateinit var repository: ExportRepository

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)

    @Before
    fun setUp() {
        whenever(mockDatabase.patientDao()).thenReturn(mockPatientDao)
        whenever(mockDatabase.appointmentDao()).thenReturn(mockAppointmentDao)
        whenever(mockDatabase.paymentDao()).thenReturn(mockPaymentDao)
        repository = ExportRepository(database = mockDatabase)
    }

    private fun makePatientEntity(id: Long) = PatientEntity(
        id = id,
        name = "Patient $id",
        phone = null,
        email = null,
        status = "ACTIVE",
        initialConsultDate = today,
        registrationDate = today,
        lastAppointmentDate = null
    )

    private fun makeAppointmentEntity(id: Long) = AppointmentEntity(
        id = id,
        patientId = 1L,
        date = yesterday,
        time = LocalTime.of(10, 0),
        durationMinutes = 60,
        notes = null
    )

    private fun makePaymentEntity(id: Long) = PaymentEntity(
        id = id,
        patientId = 1L,
        appointmentId = null,
        amount = BigDecimal("150.00"),
        status = "PAID",
        method = "PIX",
        paymentDate = yesterday,
        recordedDate = yesterday.atStartOfDay()
    )

    // ========================================
    // getAllPatients() Tests
    // ========================================

    @Test
    fun `getAllPatients returns mapped patient list`() = runTest {
        val entities = listOf(makePatientEntity(1L), makePatientEntity(2L))
        whenever(mockPatientDao.getAllPatients()).thenReturn(entities)

        val result = repository.getAllPatients()

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals(2L, result[1].id)
    }

    @Test
    fun `getAllPatients returns empty when no patients`() = runTest {
        whenever(mockPatientDao.getAllPatients()).thenReturn(emptyList())

        val result = repository.getAllPatients()

        assertTrue(result.isEmpty())
    }

    // ========================================
    // getAllAppointments() Tests
    // ========================================

    @Test
    fun `getAllAppointments returns mapped appointment list`() = runTest {
        val entities = listOf(makeAppointmentEntity(1L))
        whenever(mockAppointmentDao.getAllAppointments()).thenReturn(entities)

        val result = repository.getAllAppointments()

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
    }

    // ========================================
    // getAllPayments() Tests
    // ========================================

    @Test
    fun `getAllPayments returns mapped payment list`() = runTest {
        val entities = listOf(makePaymentEntity(1L), makePaymentEntity(2L))
        whenever(mockPaymentDao.getAllPayments()).thenReturn(entities)

        val result = repository.getAllPayments()

        assertEquals(2, result.size)
        assertEquals(BigDecimal("150.00"), result[0].amount)
    }

    // ========================================
    // Count Tests
    // ========================================

    @Test
    fun `countAllPatients delegates to DAO`() = runTest {
        whenever(mockPatientDao.countAllPatients()).thenReturn(5)

        val count = repository.countAllPatients()

        assertEquals(5, count)
    }

    @Test
    fun `countAllAppointments delegates to DAO`() = runTest {
        whenever(mockAppointmentDao.countAllAppointments()).thenReturn(20)

        val count = repository.countAllAppointments()

        assertEquals(20, count)
    }

    @Test
    fun `countAllPayments delegates to DAO`() = runTest {
        whenever(mockPaymentDao.countAllPayments()).thenReturn(35)

        val count = repository.countAllPayments()

        assertEquals(35, count)
    }

    // ========================================
    // hasDataToExport() Tests
    // ========================================

    @Test
    fun `hasDataToExport returns true when patients exist`() = runTest {
        whenever(mockPatientDao.countAllPatients()).thenReturn(3)
        whenever(mockAppointmentDao.countAllAppointments()).thenReturn(0)
        whenever(mockPaymentDao.countAllPayments()).thenReturn(0)
        whenever(mockPatientDao.getPatientsByStatus("ACTIVE")).thenReturn(emptyList())
        whenever(mockPatientDao.getPatientsByStatus("INACTIVE")).thenReturn(emptyList())

        val hasData = repository.hasDataToExport()

        assertTrue(hasData)
    }

    @Test
    fun `hasDataToExport returns false when all counts zero`() = runTest {
        whenever(mockPatientDao.countAllPatients()).thenReturn(0)
        whenever(mockAppointmentDao.countAllAppointments()).thenReturn(0)
        whenever(mockPaymentDao.countAllPayments()).thenReturn(0)

        val hasData = repository.hasDataToExport()

        assertFalse(hasData)
    }

    // ========================================
    // getExportStatistics() Tests
    // ========================================

    @Test
    fun `getExportStatistics returns complete stats map`() = runTest {
        whenever(mockPatientDao.countAllPatients()).thenReturn(10)
        whenever(mockAppointmentDao.countAllAppointments()).thenReturn(50)
        whenever(mockPaymentDao.countAllPayments()).thenReturn(75)
        whenever(mockPatientDao.getPatientsByStatus("ACTIVE"))
            .thenReturn(listOf(makePatientEntity(1L)))
        whenever(mockPatientDao.getPatientsByStatus("INACTIVE")).thenReturn(emptyList())

        val stats = repository.getExportStatistics()

        assertEquals(10, stats["patients"])
        assertEquals(50, stats["appointments"])
        assertEquals(75, stats["payments"])
    }
}
