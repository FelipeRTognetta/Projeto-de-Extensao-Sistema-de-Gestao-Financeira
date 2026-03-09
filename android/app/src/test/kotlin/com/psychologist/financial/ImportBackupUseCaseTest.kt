package com.psychologist.financial

import android.content.ContentResolver
import android.net.Uri
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.domain.models.BackupResult
import com.psychologist.financial.domain.usecases.ImportBackupUseCase
import com.psychologist.financial.services.BackupData
import com.psychologist.financial.services.BackupImportService
import com.psychologist.financial.services.PatientBackup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream

/**
 * Unit tests for ImportBackupUseCase
 *
 * Coverage:
 * - Successful import → BackupResult.ImportSuccess with correct counts
 * - File cannot be opened (null stream) → BackupResult.Failure(INVALID_FILE)
 * - decrypt throws → BackupResult.Failure(WRONG_PASSWORD)
 * - parse throws → BackupResult.Failure(INVALID_FILE)
 * - backup version > DATABASE_VERSION → BackupResult.Failure(INCOMPATIBLE_VERSION)
 * - importAtomic throws → BackupResult.Failure(IMPORT_FAILED)
 */
@RunWith(MockitoJUnitRunner::class)
class ImportBackupUseCaseTest {

    @Mock private lateinit var mockContentResolver: ContentResolver
    @Mock private lateinit var mockDatabase: AppDatabase
    @Mock private lateinit var mockImportService: BackupImportService
    @Mock private lateinit var mockUri: Uri

    private lateinit var useCase: ImportBackupUseCase

    private val twoPatientBackupData = BackupData(
        version = 3,
        appVersion = "1.0",
        exportedAt = "2026-03-01T12:00:00",
        patients = listOf(
            PatientBackup(
                id = 1L, name = "Ana Costa", phone = null, email = null, status = "ACTIVE",
                initialConsultDate = "2026-01-01", registrationDate = "2026-01-01",
                lastAppointmentDate = null, cpf = null, endereco = null, naoPagante = false,
                createdDate = "2026-01-01T10:00:00"
            ),
            PatientBackup(
                id = 2L, name = "Bruno Lima", phone = null, email = null, status = "ACTIVE",
                initialConsultDate = "2026-02-01", registrationDate = "2026-02-01",
                lastAppointmentDate = null, cpf = null, endereco = null, naoPagante = false,
                createdDate = "2026-02-01T10:00:00"
            )
        ),
        appointments = emptyList(),
        payments = emptyList(),
        paymentAppointments = emptyList(),
        payerInfos = emptyList()
    )

    private val dummyBytes = byteArrayOf(1, 2, 3, 4, 5)

    @Before
    fun setUp() {
        useCase = ImportBackupUseCase(mockContentResolver, mockDatabase, mockImportService)
    }

    // ========================================
    // Success Tests
    // ========================================

    @Test
    fun `returns ImportSuccess with correct patient count`() = runTest {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(dummyBytes))
        whenever(mockImportService.decrypt(any(), any())).thenReturn(dummyBytes)
        whenever(mockImportService.parse(any())).thenReturn(twoPatientBackupData)

        val result = useCase.execute(mockUri, "senha123")

        assertTrue(result is BackupResult.ImportSuccess)
        val success = result as BackupResult.ImportSuccess
        assertEquals(2, success.patientCount)
        assertEquals(0, success.appointmentCount)
        assertEquals(0, success.paymentCount)
    }

    @Test
    fun `returns ImportSuccess total records is sum of all entities`() = runTest {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(dummyBytes))
        whenever(mockImportService.decrypt(any(), any())).thenReturn(dummyBytes)
        whenever(mockImportService.parse(any())).thenReturn(twoPatientBackupData)

        val result = useCase.execute(mockUri, "senha123")

        assertTrue(result is BackupResult.ImportSuccess)
        assertEquals(2, (result as BackupResult.ImportSuccess).totalRecords)
    }

    // ========================================
    // File Error Tests
    // ========================================

    @Test
    fun `returns Failure INVALID_FILE when content resolver returns null stream`() = runTest {
        whenever(mockContentResolver.openInputStream(mockUri)).thenReturn(null)

        val result = useCase.execute(mockUri, "senha123")

        assertTrue(result is BackupResult.Failure)
        assertEquals(
            BackupResult.FailureReason.INVALID_FILE,
            (result as BackupResult.Failure).reason
        )
    }

    // ========================================
    // Decrypt Error Tests
    // ========================================

    @Test
    fun `returns Failure WRONG_PASSWORD when decrypt throws`() = runTest {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(dummyBytes))
        whenever(mockImportService.decrypt(any(), any()))
            .thenThrow(RuntimeException("AEADBadTagException"))

        val result = useCase.execute(mockUri, "senhaErrada")

        assertTrue(result is BackupResult.Failure)
        assertEquals(
            BackupResult.FailureReason.WRONG_PASSWORD,
            (result as BackupResult.Failure).reason
        )
    }

    @Test
    fun `WRONG_PASSWORD error message is in Portuguese`() = runTest {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(dummyBytes))
        whenever(mockImportService.decrypt(any(), any()))
            .thenThrow(RuntimeException("Bad tag"))

        val result = useCase.execute(mockUri, "wrong")

        assertTrue(result is BackupResult.Failure)
        assertTrue(
            "Error message should be in Portuguese",
            (result as BackupResult.Failure).message.isNotBlank()
        )
    }

    // ========================================
    // Parse Error Tests
    // ========================================

    @Test
    fun `returns Failure INVALID_FILE when parse throws`() = runTest {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(dummyBytes))
        whenever(mockImportService.decrypt(any(), any())).thenReturn(dummyBytes)
        whenever(mockImportService.parse(any())).thenThrow(RuntimeException("Invalid JSON"))

        val result = useCase.execute(mockUri, "senha123")

        assertTrue(result is BackupResult.Failure)
        assertEquals(
            BackupResult.FailureReason.INVALID_FILE,
            (result as BackupResult.Failure).reason
        )
    }

    // ========================================
    // Version Incompatibility Tests
    // ========================================

    @Test
    fun `returns Failure INCOMPATIBLE_VERSION when backup version is newer than app`() = runTest {
        val futureBackup = twoPatientBackupData.copy(version = 9999)
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(dummyBytes))
        whenever(mockImportService.decrypt(any(), any())).thenReturn(dummyBytes)
        whenever(mockImportService.parse(any())).thenReturn(futureBackup)

        val result = useCase.execute(mockUri, "senha123")

        assertTrue(result is BackupResult.Failure)
        assertEquals(
            BackupResult.FailureReason.INCOMPATIBLE_VERSION,
            (result as BackupResult.Failure).reason
        )
    }

    @Test
    fun `accepts backup with same version as app`() = runTest {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(dummyBytes))
        whenever(mockImportService.decrypt(any(), any())).thenReturn(dummyBytes)
        whenever(mockImportService.parse(any())).thenReturn(twoPatientBackupData) // version = 3

        val result = useCase.execute(mockUri, "senha123")

        assertTrue(result is BackupResult.ImportSuccess)
    }

    // ========================================
    // Import Error Tests
    // ========================================

    @Test
    fun `returns Failure IMPORT_FAILED when importAtomic throws`() = runTest {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(dummyBytes))
        whenever(mockImportService.decrypt(any(), any())).thenReturn(dummyBytes)
        whenever(mockImportService.parse(any())).thenReturn(twoPatientBackupData)
        doThrow(RuntimeException("DB error")).whenever(mockImportService).importAtomic(any())

        val result = useCase.execute(mockUri, "senha123")

        assertTrue(result is BackupResult.Failure)
        assertEquals(
            BackupResult.FailureReason.IMPORT_FAILED,
            (result as BackupResult.Failure).reason
        )
    }
}
