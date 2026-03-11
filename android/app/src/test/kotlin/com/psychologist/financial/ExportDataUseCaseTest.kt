package com.psychologist.financial

import android.content.Context
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.domain.models.ExportResult
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.PayerInfo
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ExportDataUseCase
 *
 * Coverage:
 * - getExportStats() returns correct counts
 * - getExportStats() returns error map on exception
 * - executeFinanceiro() empty month
 * - executeFinanceiro() month with payments
 * - executeFinanceiro() non-paying patient with payerInfo
 *
 * Total: 5 test cases
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

    private val exportDir = File("/tmp/export")

    private val samplePatients = listOf(
        Patient.createForTesting(id = 1L, name = "Ana Costa", status = PatientStatus.ACTIVE),
        Patient.createForTesting(id = 2L, name = "Bruno Lima", status = PatientStatus.ACTIVE)
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
    // getExportStats() Tests
    // ========================================

    @Test
    fun `getExportStats returns counts for all types`() = runTest {
        whenever(mockRepository.getAllPatients()).thenReturn(samplePatients)
        whenever(mockRepository.getAllAppointments()).thenReturn(emptyList())
        whenever(mockRepository.getAllPayments()).thenReturn(samplePayments)

        val stats = useCase.getExportStats()

        assertEquals(2, stats["patients"])
        assertEquals(0, stats["appointments"])
        assertEquals(1, stats["payments"])
        assertEquals(3, stats["total"])
    }

    @Test
    fun `getExportStats returns error map on exception`() = runTest {
        whenever(mockRepository.getAllPatients()).thenThrow(RuntimeException("Error"))

        val stats = useCase.getExportStats()

        assertEquals(-1, stats["error"])
    }

    // ========================================
    // executeFinanceiro() Tests
    // ========================================

    @Test
    fun `executeFinanceiro retorna resultado com 0 pagamentos quando mes nao tem pagamentos`() = runTest {
        val march2026 = YearMonth.of(2026, 3)
        whenever(mockRepository.getPaymentsByMonth(march2026)).thenReturn(emptyList())

        val result = useCase.executeFinanceiro(march2026)

        assertTrue(result.success)
        assertEquals(0, result.paymentCount)
        assertNull(result.paymentFile)
    }

    @Test
    fun `executeFinanceiro retorna arquivo CSV quando mes tem pagamentos`() = runTest {
        val march2026 = YearMonth.of(2026, 3)
        val payment = Payment(
            id = 1L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.of(2026, 3, 15)
        )
        val patient = Patient.createForTesting(id = 1L, name = "Ana Costa")
        val financeiroCsvFile = File("/tmp/financeiro.csv")

        whenever(mockRepository.getPaymentsByMonth(march2026)).thenReturn(listOf(payment))
        whenever(mockRepository.getAllPatients()).thenReturn(listOf(patient))
        whenever(mockRepository.getAllPayerInfos()).thenReturn(emptyList())
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)
        whenever(mockCsvService.exportFinanceiroCsv(any(), any())).thenReturn(financeiroCsvFile)

        val result = useCase.executeFinanceiro(march2026)

        assertTrue(result.success)
        assertEquals(1, result.paymentCount)
        assertNotNull(result.paymentFile)
    }

    @Test
    fun `executeFinanceiro paciente naoPagante com payerInfo preenche colunas do responsavel`() = runTest {
        val march2026 = YearMonth.of(2026, 3)
        val payment = Payment(
            id = 1L,
            patientId = 10L,
            amount = BigDecimal("200.00"),
            paymentDate = LocalDate.of(2026, 3, 20)
        )
        val patient = Patient.createForTesting(
            id = 10L,
            name = "Pedro Lima",
            naoPagante = true
        )
        val payerInfo = PayerInfo(
            id = 1L,
            patientId = 10L,
            nome = "Carlos Responsavel",
            cpf = "98765432100",
            email = "carlos@test.com",
            telefone = "(11) 9000-0002"
        )
        val financeiroCsvFile = File("/tmp/financeiro.csv")

        whenever(mockRepository.getPaymentsByMonth(march2026)).thenReturn(listOf(payment))
        whenever(mockRepository.getAllPatients()).thenReturn(listOf(patient))
        whenever(mockRepository.getAllPayerInfos()).thenReturn(listOf(payerInfo))
        whenever(mockStorageManager.getTimestampedExportDirectory()).thenReturn(exportDir)
        whenever(mockCsvService.exportFinanceiroCsv(any(), any())).thenReturn(financeiroCsvFile)

        useCase.executeFinanceiro(march2026)

        val rowsCaptor = argumentCaptor<List<com.psychologist.financial.domain.models.FinanceiroCsvRow>>()
        verify(mockCsvService).exportFinanceiroCsv(rowsCaptor.capture(), any())

        val capturedRows = rowsCaptor.firstValue
        assertEquals(1, capturedRows.size)
        val row = capturedRows[0]
        assertEquals("Carlos Responsavel", row.nomeResponsavel)
        assertEquals("98765432100", row.cpfResponsavel)
    }
}
