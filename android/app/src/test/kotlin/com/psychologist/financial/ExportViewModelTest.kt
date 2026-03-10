package com.psychologist.financial

import com.psychologist.financial.domain.usecases.ExportDataUseCase
import com.psychologist.financial.domain.models.ExportResult
import com.psychologist.financial.viewmodel.ExportViewModel
import com.psychologist.financial.viewmodel.ExportViewState
import com.psychologist.financial.viewmodel.FinanceiroCsvState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.io.File
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Unit tests for ExportViewModel
 *
 * Coverage:
 * - Loading export statistics
 * - Financial CSV monthly export state transitions
 * - Month selection
 * - Error handling
 */
class ExportViewModelTest {

    @Mock
    private lateinit var exportDataUseCase: ExportDataUseCase

    private lateinit var viewModel: ExportViewModel

    private val mockExportResult = ExportResult(
        success = true,
        patientFile = File("patients.csv"),
        appointmentFile = File("appointments.csv"),
        paymentFile = File("payments.csv"),
        patientCount = 150,
        appointmentCount = 500,
        paymentCount = 1200,
        exportedAt = LocalDateTime.now(),
        durationSeconds = 5
    )

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)
        viewModel = ExportViewModel(exportDataUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // Statistics Loading Tests
    // ========================================

    @Test
    fun loadExportStatistics_success_updatesState() = runTest {
        // Arrange
        val stats = mapOf(
            "patients" to 150,
            "appointments" to 500,
            "payments" to 1200,
            "total" to 1850
        )

        whenever(exportDataUseCase.getExportStats()).thenReturn(stats)
        whenever(exportDataUseCase.getStorageStatus()).thenReturn("Storage: OK")

        // Act
        viewModel.loadExportStatistics()

        // Assert
        val state = viewModel.exportState.value
        assertEquals(150, state.patientCount)
        assertEquals(500, state.appointmentCount)
        assertEquals(1200, state.paymentCount)
        assertEquals(1850, state.totalRecords)
    }

    @Test
    fun loadExportStatistics_withEmptyData_updatesState() = runTest {
        // Arrange
        val stats = mapOf(
            "patients" to 0,
            "appointments" to 0,
            "payments" to 0,
            "total" to 0
        )

        whenever(exportDataUseCase.getExportStats()).thenReturn(stats)
        whenever(exportDataUseCase.getStorageStatus()).thenReturn("No data")

        // Act
        viewModel.loadExportStatistics()

        // Assert
        val state = viewModel.exportState.value
        assertEquals(0, state.patientCount)
        assertEquals(0, state.appointmentCount)
        assertEquals(0, state.paymentCount)
        assertEquals(0, state.totalRecords)
    }

    @Test
    fun loadExportStatistics_failure_setsError() = runTest {
        // Arrange
        whenever(exportDataUseCase.getExportStats())
            .thenThrow(RuntimeException("Load failed"))

        // Act
        viewModel.loadExportStatistics()

        // Assert
        val error = viewModel.error.value
        assertNotNull(error)
    }

    @Test
    fun loadExportStatistics_defaultState_isEmptyViewState() {
        val state = viewModel.exportState.value
        assertEquals(ExportViewState(), state)
        assertEquals(0, state.patientCount)
        assertEquals(0, state.totalRecords)
    }

    // ========================================
    // Financeiro CSV Month Selection Tests
    // ========================================

    @Test
    fun selectMonth_updatesSelectedMonth() {
        val newMonth = YearMonth.of(2025, 6)
        viewModel.selectMonth(newMonth)
        assertEquals(newMonth, viewModel.selectedMonth.value)
    }

    @Test
    fun selectMonth_resetsFinanceiroStateToIdle() = runTest {
        // Put state into a non-idle value first
        val month = YearMonth.of(2025, 1)
        whenever(exportDataUseCase.executeFinanceiro(month))
            .thenReturn(mockExportResult.copy(paymentFile = null))

        viewModel.selectMonth(month)
        viewModel.performFinanceiroExport()

        // Now change month — state should reset
        viewModel.selectMonth(YearMonth.of(2025, 2))

        assertTrue(viewModel.financeiroState.value is FinanceiroCsvState.Idle)
    }

    @Test
    fun selectedMonth_defaultsToCurrentMonth() {
        assertEquals(YearMonth.now(), viewModel.selectedMonth.value)
    }

    // ========================================
    // Financeiro CSV Export State Tests
    // ========================================

    @Test
    fun performFinanceiroExport_withPayments_setsSuccess() = runTest {
        val month = YearMonth.now()
        val paymentFile = File("financeiro.csv")
        whenever(exportDataUseCase.executeFinanceiro(month))
            .thenReturn(mockExportResult.copy(paymentFile = paymentFile, paymentCount = 5))

        viewModel.performFinanceiroExport()

        val state = viewModel.financeiroState.value
        assertTrue(state is FinanceiroCsvState.Success)
        assertEquals(paymentFile, (state as FinanceiroCsvState.Success).file)
        assertEquals(5, state.rowCount)
    }

    @Test
    fun performFinanceiroExport_withNoPayments_setsEmpty() = runTest {
        val month = YearMonth.now()
        whenever(exportDataUseCase.executeFinanceiro(month))
            .thenReturn(mockExportResult.copy(paymentFile = null, paymentCount = 0))

        viewModel.performFinanceiroExport()

        assertTrue(viewModel.financeiroState.value is FinanceiroCsvState.Empty)
    }

    @Test
    fun performFinanceiroExport_withFailure_setsError() = runTest {
        val month = YearMonth.now()
        whenever(exportDataUseCase.executeFinanceiro(month))
            .thenReturn(mockExportResult.copy(success = false, errorMessage = "Erro no export"))

        viewModel.performFinanceiroExport()

        val state = viewModel.financeiroState.value
        assertTrue(state is FinanceiroCsvState.Error)
        assertEquals("Erro no export", (state as FinanceiroCsvState.Error).message)
    }

    @Test
    fun performFinanceiroExport_onException_setsError() = runTest {
        val month = YearMonth.now()
        whenever(exportDataUseCase.executeFinanceiro(month))
            .thenThrow(RuntimeException("Unexpected error"))

        viewModel.performFinanceiroExport()

        assertTrue(viewModel.financeiroState.value is FinanceiroCsvState.Error)
    }

    @Test
    fun financeiroState_initialState_isIdle() {
        assertTrue(viewModel.financeiroState.value is FinanceiroCsvState.Idle)
    }
}
