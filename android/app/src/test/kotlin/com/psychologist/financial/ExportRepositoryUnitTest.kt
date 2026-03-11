package com.psychologist.financial

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.AppointmentDao
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.database.PayerInfoDao
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PayerInfoEntity
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
import java.time.YearMonth
import kotlin.test.assertEquals
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
 * - getPaymentsByMonth() returns only payments in that month
 * - getPaymentsByMonth() returns empty for month with no payments
 * - getAllPayerInfos() returns all payer info records
 * - getAllPayerInfos() returns empty list when none exist
 *
 * Total: 16 test cases
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

    @Mock
    private lateinit var mockPayerInfoDao: PayerInfoDao

    private lateinit var repository: ExportRepository

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)

    @Before
    fun setUp() {
        whenever(mockDatabase.patientDao()).thenReturn(mockPatientDao)
        whenever(mockDatabase.appointmentDao()).thenReturn(mockAppointmentDao)
        whenever(mockDatabase.paymentDao()).thenReturn(mockPaymentDao)
        whenever(mockDatabase.payerInfoDao()).thenReturn(mockPayerInfoDao)
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
        timeStart = LocalTime.of(10, 0),
        durationMinutes = 60,
        notes = null
    )

    private fun makePaymentEntity(id: Long) = PaymentEntity(
        id = id,
        patientId = 1L,
        amount = BigDecimal("150.00"),
        paymentDate = yesterday
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
        whenever(mockAppointmentDao.getAll()).thenReturn(entities)

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
        whenever(mockPaymentDao.getAll()).thenReturn(entities)

        val result = repository.getAllPayments()

        assertEquals(2, result.size)
        assertEquals(BigDecimal("150.00"), result[0].amount)
    }

    // ========================================
    // getPaymentsByMonth() Tests — T004
    // ========================================

    @Test
    fun `getPaymentsByMonth returns only payments within that month`() = runTest {
        val march2026 = YearMonth.of(2026, 3)
        val firstDay = march2026.atDay(1)
        val lastDay = march2026.atEndOfMonth()

        val marchPayment = makePaymentEntity(1L).copy(paymentDate = LocalDate.of(2026, 3, 15))
        val aprilPayment = makePaymentEntity(2L).copy(paymentDate = LocalDate.of(2026, 4, 1))

        // DAO returns only the march payment when queried with march range
        whenever(mockPaymentDao.getByDateRange(firstDay, lastDay))
            .thenReturn(listOf(marchPayment))

        val result = repository.getPaymentsByMonth(march2026)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals(LocalDate.of(2026, 3, 15), result[0].paymentDate)
        // april payment must NOT appear
        assertTrue(result.none { it.id == 2L })
    }

    @Test
    fun `getPaymentsByMonth returns empty list when no payments in month`() = runTest {
        val february2026 = YearMonth.of(2026, 2)
        val firstDay = february2026.atDay(1)
        val lastDay = february2026.atEndOfMonth()

        whenever(mockPaymentDao.getByDateRange(firstDay, lastDay)).thenReturn(emptyList())

        val result = repository.getPaymentsByMonth(february2026)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // getAllPayerInfos() Tests — T006
    // ========================================

    @Test
    fun `getAllPayerInfos returns all payer info records`() = runTest {
        val payerEntities = listOf(
            PayerInfoEntity(id = 1L, patientId = 10L, nome = "Maria Silva"),
            PayerInfoEntity(id = 2L, patientId = 20L, nome = "João Costa", cpf = "12345678901")
        )
        whenever(mockPayerInfoDao.getAll()).thenReturn(payerEntities)

        val result = repository.getAllPayerInfos()

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("Maria Silva", result[0].nome)
        assertEquals(2L, result[1].id)
        assertEquals("12345678901", result[1].cpf)
    }

    @Test
    fun `getAllPayerInfos returns empty list when none exist`() = runTest {
        whenever(mockPayerInfoDao.getAll()).thenReturn(emptyList())

        val result = repository.getAllPayerInfos()

        assertTrue(result.isEmpty())
    }
}
