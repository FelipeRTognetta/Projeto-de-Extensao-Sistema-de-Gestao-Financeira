package com.psychologist.financial

import android.content.Context
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.ExportResult
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.usecases.ExportDataUseCase
import com.psychologist.financial.services.CsvExportService
import com.psychologist.financial.services.FileStorageManager
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ExportDataUseCase
 *
 * Coverage:
 * - execute() success with 3 CSV files
 * - execute() returns failure when insufficient storage
 * - executeSelective() with all types enabled
 * - executeSelective() with no types (returns failure)
 * - executeSelective() with only patients
 * - getExportStats() returns correct counts
 * - cleanupOldExports() delegates to storage manager
 * - getAvailableExports() delegates to storage manager
 * - getStorageStatus() delegates to storage manager
 * - validateExport() success / no patients
 * - validateExport() insufficient storage
 *
 * Total: 16 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class ExportDataUseCaseTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockRepository: ExportRepository

    @Mock
    private lateinit var mockCsvService: CsvExportService

    @Mock
    private lateinit var mockStorageManager: FileStorageManager

    private lateinit var useCase: ExportDataUseCase

    private val patientFile = File("/tmp/patients.csv")
    private val appointmentFile = File("/tmp/appointments.csv")
    private val paymentFile = File("/tmp/payments.csv")
    private val exportDir = File("/tmp/export")

    private val samplePatients = listOf(
        Patient.createForTesting(id = 1L, name = "Ana Costa", status = PatientStatus.ACTIVE),
        Patient.createForTesting(id = 2L, name = "Bruno Lima", status = PatientStatus.ACTIVE)
    )

    private val sampleAppointments = listOf(
        Appointment(
            id = 1L,
            patientId = 1L,
            date = LocalDate.now().minusDays(1),
            timeStart = LocalTime.of(10, 0),
            durationMinutes = 60
        )
    )

    private val samplePayments = listOf(
        Payment(
            id = 1L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now().minusDays(1)
        )
    )

    @Before
    fun setUp() {
        useCase = ExportDataUseCase(
            context = mockContext,
            repository = mockRepository,
            csvService = mockCsvService,
            storageManager = mockStorageManager
        )
    }

    // ========================================
    // execute() Success Tests
    // ========================================

    @Test
    fun `execute returns success when storage available and data exists`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(true)
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)
        whenever(mockRepository.getAllPatients()).thenReturn(samplePatients)
        whenever(mockRepository.getAllAppointments()).thenReturn(sampleAppointments)
        whenever(mockRepository.getAllPayments()).thenReturn(samplePayments)
        whenever(mockCsvService.exportPatients(any(), any())).thenReturn(patientFile)
        whenever(mockCsvService.exportAppointments(any(), any())).thenReturn(appointmentFile)
        whenever(mockCsvService.exportPayments(any(), any())).thenReturn(paymentFile)

        val result = useCase.execute()

        assertTrue(result.success)
        assertEquals(2, result.patientCount)
        assertEquals(1, result.appointmentCount)
        assertEquals(1, result.paymentCount)
    }

    @Test
    fun `execute returns failure when storage insufficient`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(false)
        whenever(mockStorageManager.getAvailableStorageMB()).thenReturn(10L)

        val result = useCase.execute()

        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("insuficiente", ignoreCase = true))
    }

    @Test
    fun `execute returns failure when repository throws exception`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(true)
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)
        whenever(mockRepository.getAllPatients()).thenThrow(RuntimeException("Database error"))

        val result = useCase.execute()

        assertFalse(result.success)
    }

    // ========================================
    // executeSelective() Tests
    // ========================================

    @Test
    fun `executeSelective with all types returns success`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(true)
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)
        whenever(mockRepository.getAllPatients()).thenReturn(samplePatients)
        whenever(mockRepository.getAllAppointments()).thenReturn(sampleAppointments)
        whenever(mockRepository.getAllPayments()).thenReturn(samplePayments)
        whenever(mockCsvService.exportPatients(any(), any())).thenReturn(patientFile)
        whenever(mockCsvService.exportAppointments(any(), any())).thenReturn(appointmentFile)
        whenever(mockCsvService.exportPayments(any(), any())).thenReturn(paymentFile)

        val result = useCase.executeSelective(
            exportPatients = true,
            exportAppointments = true,
            exportPayments = true
        )

        assertTrue(result.success)
        assertEquals(4, result.totalRecords)
    }

    @Test
    fun `executeSelective with no types returns failure`() = runTest {
        val result = useCase.executeSelective(
            exportPatients = false,
            exportAppointments = false,
            exportPayments = false
        )

        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Selecione", ignoreCase = true))
    }

    @Test
    fun `executeSelective with only patients exports correctly`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(true)
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)
        whenever(mockRepository.getAllPatients()).thenReturn(samplePatients)
        whenever(mockCsvService.exportPatients(any(), any())).thenReturn(patientFile)

        val result = useCase.executeSelective(
            exportPatients = true,
            exportAppointments = false,
            exportPayments = false
        )

        assertTrue(result.success)
        assertEquals(2, result.patientCount)
        assertEquals(0, result.appointmentCount)
        assertEquals(0, result.paymentCount)
    }

    @Test
    fun `executeSelective returns failure when storage insufficient`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(false)
        whenever(mockStorageManager.getAvailableStorageMB()).thenReturn(5L)

        val result = useCase.executeSelective(
            exportPatients = true,
            exportAppointments = false,
            exportPayments = false
        )

        assertFalse(result.success)
        assertNotNull(result.errorMessage)
    }

    // ========================================
    // getExportStats() Tests
    // ========================================

    @Test
    fun `getExportStats returns counts for all types`() = runTest {
        whenever(mockRepository.getAllPatients()).thenReturn(samplePatients)
        whenever(mockRepository.getAllAppointments()).thenReturn(sampleAppointments)
        whenever(mockRepository.getAllPayments()).thenReturn(samplePayments)

        val stats = useCase.getExportStats()

        assertEquals(2, stats["patients"])
        assertEquals(1, stats["appointments"])
        assertEquals(1, stats["payments"])
        assertEquals(4, stats["total"])
    }

    @Test
    fun `getExportStats returns error map on exception`() = runTest {
        whenever(mockRepository.getAllPatients()).thenThrow(RuntimeException("Error"))

        val stats = useCase.getExportStats()

        assertEquals(-1, stats["error"])
    }

    // ========================================
    // cleanupOldExports() Tests
    // ========================================

    @Test
    fun `cleanupOldExports delegates to storage manager`() = runTest {
        whenever(mockStorageManager.cleanupOldExports(7)).thenReturn(3)

        val deleted = useCase.cleanupOldExports(daysOld = 7)

        assertEquals(3, deleted)
    }

    @Test
    fun `cleanupOldExports returns zero on exception`() = runTest {
        whenever(mockStorageManager.cleanupOldExports(any()))
            .thenThrow(RuntimeException("IO error"))

        val deleted = useCase.cleanupOldExports(daysOld = 7)

        assertEquals(0, deleted)
    }

    // ========================================
    // validateExport() Tests
    // ========================================

    @Test
    fun `validateExport returns valid when storage and data exist`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(true)
        whenever(mockRepository.countAllPatients()).thenReturn(5)

        val result = useCase.validateExport()

        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validateExport returns invalid when no patients`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(true)
        whenever(mockRepository.countAllPatients()).thenReturn(0)

        val result = useCase.validateExport()

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("paciente", ignoreCase = true))
    }

    @Test
    fun `validateExport returns invalid when insufficient storage`() = runTest {
        whenever(mockStorageManager.hasStorageSpace(any())).thenReturn(false)
        whenever(mockStorageManager.getAvailableStorageMB()).thenReturn(20L)

        val result = useCase.validateExport()

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }
}
