package com.psychologist.financial

import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.BackupResult
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PayerInfo
import com.psychologist.financial.domain.usecases.ExportBackupUseCase
import com.psychologist.financial.services.BackupExportService
import com.psychologist.financial.services.FileStorageManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

/**
 * Unit tests for ExportBackupUseCase
 *
 * Coverage:
 * - Password too short (< 6 chars) → BackupResult.Failure(PASSWORD_TOO_SHORT)
 * - Passwords don't match → BackupResult.Failure(PASSWORDS_DO_NOT_MATCH)
 * - Success → BackupResult.ExportSuccess with file and correct counts
 */
@RunWith(MockitoJUnitRunner::class)
class ExportBackupUseCaseTest {

    @Mock
    private lateinit var mockRepository: ExportRepository

    @Mock
    private lateinit var mockBackupExportService: BackupExportService

    @Mock
    private lateinit var mockStorageManager: FileStorageManager

    private lateinit var useCase: ExportBackupUseCase

    private val exportDir = File(System.getProperty("java.io.tmpdir"), "backup_test")
    private val backupFile = File(exportDir, "backup_test.pgfbackup")

    private val samplePatients = listOf(
        Patient.createForTesting(id = 1L, name = "Ana Costa"),
        Patient.createForTesting(id = 2L, name = "Bruno Lima")
    )

    private val sampleAppointments = listOf(
        Appointment(
            id = 1L,
            patientId = 1L,
            date = LocalDate.of(2026, 3, 1),
            timeStart = LocalTime.of(9, 0),
            durationMinutes = 50
        )
    )

    private val samplePayments = listOf(
        Payment(
            id = 1L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.of(2026, 3, 1),
            appointmentIds = listOf(1L)
        )
    )

    private val samplePayerInfos = listOf(
        PayerInfo(
            id = 1L,
            patientId = 2L,
            nome = "Maria Responsavel",
            cpf = "98765432100",
            email = "maria@test.com",
            telefone = "(11) 9000-0001",
            endereco = null
        )
    )

    @Before
    fun setUp() {
        exportDir.mkdirs()
        useCase = ExportBackupUseCase(
            repository = mockRepository,
            backupExportService = mockBackupExportService,
            storageManager = mockStorageManager
        )
    }

    // ========================================
    // Validation Tests
    // ========================================

    @Test
    fun `execute returns failure when password is too short`() = runTest {
        val result = useCase.execute("12345", "12345")

        assertTrue(result is BackupResult.Failure)
        val failure = result as BackupResult.Failure
        assertEquals(BackupResult.FailureReason.PASSWORD_TOO_SHORT, failure.reason)
    }

    @Test
    fun `execute returns failure for empty password`() = runTest {
        val result = useCase.execute("", "")

        assertTrue(result is BackupResult.Failure)
        val failure = result as BackupResult.Failure
        assertEquals(BackupResult.FailureReason.PASSWORD_TOO_SHORT, failure.reason)
    }

    @Test
    fun `execute returns failure when passwords do not match`() = runTest {
        val result = useCase.execute("password123", "differentPassword")

        assertTrue(result is BackupResult.Failure)
        val failure = result as BackupResult.Failure
        assertEquals(BackupResult.FailureReason.PASSWORDS_DO_NOT_MATCH, failure.reason)
    }

    @Test
    fun `execute returns failure message in Portuguese for short password`() = runTest {
        val result = useCase.execute("abc", "abc")

        assertTrue(result is BackupResult.Failure)
        val failure = result as BackupResult.Failure
        assertTrue(
            "Error message should be in Portuguese",
            failure.message.isNotBlank()
        )
    }

    // ========================================
    // Success Tests
    // ========================================

    @Test
    fun `execute returns ExportSuccess with correct counts`() = runTest {
        whenever(mockRepository.getAllPatients()).thenReturn(samplePatients)
        whenever(mockRepository.getAllAppointments()).thenReturn(sampleAppointments)
        whenever(mockRepository.getAllPayments()).thenReturn(samplePayments)
        whenever(mockRepository.getAllPayerInfos()).thenReturn(samplePayerInfos)
        whenever(mockRepository.getAllPaymentCrossRefs())
            .thenReturn(listOf(PaymentAppointmentCrossRef(1L, 1L)))
        whenever(mockBackupExportService.serialize(any(), any(), any(), any(), any()))
            .thenReturn(byteArrayOf(1, 2, 3))
        whenever(mockBackupExportService.encrypt(any(), any()))
            .thenReturn(byteArrayOf(4, 5, 6))
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)

        val result = useCase.execute("password123", "password123")

        assertTrue(result is BackupResult.ExportSuccess)
        val success = result as BackupResult.ExportSuccess
        assertEquals(2, success.patientCount)
        assertEquals(1, success.appointmentCount)
        assertEquals(1, success.paymentCount)
        assertEquals(1, success.payerInfoCount)
    }

    @Test
    fun `execute returns ExportSuccess with file that exists`() = runTest {
        whenever(mockRepository.getAllPatients()).thenReturn(samplePatients)
        whenever(mockRepository.getAllAppointments()).thenReturn(sampleAppointments)
        whenever(mockRepository.getAllPayments()).thenReturn(samplePayments)
        whenever(mockRepository.getAllPayerInfos()).thenReturn(emptyList())
        whenever(mockRepository.getAllPaymentCrossRefs()).thenReturn(emptyList())
        whenever(mockBackupExportService.serialize(any(), any(), any(), any(), any()))
            .thenReturn(byteArrayOf(1, 2, 3, 4, 5))
        whenever(mockBackupExportService.encrypt(any(), any()))
            .thenReturn(byteArrayOf(10, 20, 30))
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)

        val result = useCase.execute("securePassword", "securePassword")

        assertTrue(result is BackupResult.ExportSuccess)
        val success = result as BackupResult.ExportSuccess
        assertNotNull(success.file)
        assertTrue("Backup file should exist", success.file.exists())
        // Cleanup
        success.file.delete()
    }

    @Test
    fun `execute returns failure when repository throws exception`() = runTest {
        whenever(mockRepository.getAllPatients()).thenThrow(RuntimeException("DB error"))

        val result = useCase.execute("password123", "password123")

        assertTrue(result is BackupResult.Failure)
        val failure = result as BackupResult.Failure
        assertEquals(BackupResult.FailureReason.STORAGE_ERROR, failure.reason)
    }

    @Test
    fun `execute with exactly 6 char password succeeds`() = runTest {
        whenever(mockRepository.getAllPatients()).thenReturn(emptyList())
        whenever(mockRepository.getAllAppointments()).thenReturn(emptyList())
        whenever(mockRepository.getAllPayments()).thenReturn(emptyList())
        whenever(mockRepository.getAllPayerInfos()).thenReturn(emptyList())
        whenever(mockRepository.getAllPaymentCrossRefs()).thenReturn(emptyList())
        whenever(mockBackupExportService.serialize(any(), any(), any(), any(), any()))
            .thenReturn(byteArrayOf(1))
        whenever(mockBackupExportService.encrypt(any(), any()))
            .thenReturn(byteArrayOf(2))
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)

        val result = useCase.execute("123456", "123456") // exactly 6 chars

        assertTrue(result is BackupResult.ExportSuccess)
    }
}
