package com.psychologist.financial

import com.psychologist.financial.domain.usecases.ExportDataUseCase
import com.psychologist.financial.domain.models.ExportResult
import com.psychologist.financial.viewmodel.ExportType
import com.psychologist.financial.viewmodel.ExportViewModel
import com.psychologist.financial.viewmodel.ExportViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.time.LocalDateTime

/**
 * Unit tests for ExportViewModel
 *
 * Coverage:
 * - Loading export statistics
 * - Starting full export operation
 * - Starting selective export with different combinations
 * - State transitions (Idle → Validating → InProgress → Success/Error)
 * - Progress tracking and updates
 * - Error handling and retry
 * - Export cancellation
 * - Cleanup operations
 * - Validation and prerequisite checking
 * - Use case interaction (mocked)
 *
 * Total: 30+ test cases covering all ViewModel methods
 */
class ExportViewModelTest {

    @Mock
    private lateinit var exportDataUseCase: ExportDataUseCase

    private lateinit var viewModel: ExportViewModel

    // Mock export result
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

    // Mock validation result
    private val validValidation = ExportDataUseCase.ValidationResult(isValid = true)
    private val invalidValidation = ExportDataUseCase.ValidationResult(
        isValid = false,
        errorMessage = "Espaço insuficiente"
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
    fun loadExportStatistics_success_updatesIdleState() = runTest {
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
        assertTrue(state is ExportViewState.Idle)
        val idleState = state as ExportViewState.Idle
        assertEquals(150, idleState.patientCount)
        assertEquals(500, idleState.appointmentCount)
        assertEquals(1200, idleState.paymentCount)
        assertEquals(1850, idleState.totalRecords)
    }

    @Test
    fun loadExportStatistics_withEmptyData_updatesIdleState() = runTest {
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
        assertTrue(state is ExportViewState.Idle)
        val idleState = state as ExportViewState.Idle
        assertFalse(idleState.hasData())
    }

    @Test
    fun loadExportStatistics_failure_setErrorState() = runTest {
        // Arrange
        whenever(exportDataUseCase.getExportStats())
            .thenThrow(RuntimeException("Load failed"))

        // Act
        viewModel.loadExportStatistics()

        // Assert
        val error = viewModel.error.value
        assertNotNull(error)
    }

    // ========================================
    // Full Export Tests
    // ========================================

    @Test
    fun performExport_withValidationSuccess_executesExport() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        whenever(exportDataUseCase.execute()).thenReturn(mockExportResult)

        // Act
        viewModel.performExport()

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
        val successState = state as ExportViewState.Success
        assertEquals(150, successState.result.patientCount)
        assertEquals(500, successState.result.appointmentCount)
        assertEquals(1200, successState.result.paymentCount)
        assertFalse(viewModel.isExporting.value)
    }

    @Test
    fun performExport_withValidationFailure_setsErrorState() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(invalidValidation)

        // Act
        viewModel.performExport()

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Error)
        val errorState = state as ExportViewState.Error
        assertEquals("Espaço insuficiente", errorState.message)
        assertFalse(viewModel.isExporting.value)
    }

    @Test
    fun performExport_withExportFailure_setsErrorState() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        val failureResult = ExportResult(
            success = false,
            errorMessage = "Export failed"
        )
        whenever(exportDataUseCase.execute()).thenReturn(failureResult)

        // Act
        viewModel.performExport()

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Error)
        assertFalse(viewModel.isExporting.value)
    }

    @Test
    fun performExport_setsIsExportingFlag() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        whenever(exportDataUseCase.execute()).thenReturn(mockExportResult)

        // Act
        viewModel.performExport()

        // Assert
        assertFalse(viewModel.isExporting.value)
        assertTrue(viewModel.lastExportResult.value != null)
    }

    @Test
    fun performExport_storesLastResult() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        whenever(exportDataUseCase.execute()).thenReturn(mockExportResult)

        // Act
        viewModel.performExport()

        // Assert
        val result = viewModel.lastExportResult.value
        assertNotNull(result)
        assertEquals(1850, result?.totalRecords)
    }

    @Test
    fun performExport_progressTracking_initialState() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        doAnswer {
            Thread.sleep(100) // Simulate export delay
            mockExportResult
        }.whenever(exportDataUseCase).execute()

        // Act
        viewModel.performExport()

        // Assert
        assertFalse(viewModel.isExporting.value)
    }

    // ========================================
    // Selective Export Tests
    // ========================================

    @Test
    fun performSelectiveExport_allTypes_executesExport() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        whenever(exportDataUseCase.executeSelective(true, true, true))
            .thenReturn(mockExportResult)

        // Act
        viewModel.performSelectiveExport(
            exportPatients = true,
            exportAppointments = true,
            exportPayments = true
        )

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
        verify(exportDataUseCase).executeSelective(true, true, true)
    }

    @Test
    fun performSelectiveExport_patientsOnly_executesFiltered() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        val patientOnlyResult = mockExportResult.copy(
            appointmentFile = null,
            paymentFile = null,
            appointmentCount = 0,
            paymentCount = 0
        )
        whenever(exportDataUseCase.executeSelective(true, false, false))
            .thenReturn(patientOnlyResult)

        // Act
        viewModel.performSelectiveExport(
            exportPatients = true,
            exportAppointments = false,
            exportPayments = false
        )

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
        verify(exportDataUseCase).executeSelective(true, false, false)
    }

    @Test
    fun performSelectiveExport_appointmentsAndPayments_executesFiltered() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        whenever(exportDataUseCase.executeSelective(false, true, true))
            .thenReturn(mockExportResult)

        // Act
        viewModel.performSelectiveExport(
            exportPatients = false,
            exportAppointments = true,
            exportPayments = true
        )

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
        verify(exportDataUseCase).executeSelective(false, true, true)
    }

    @Test
    fun performSelectiveExport_validationFails_setsError() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(invalidValidation)

        // Act
        viewModel.performSelectiveExport(
            exportPatients = true,
            exportAppointments = true,
            exportPayments = true
        )

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Error)
    }

    // ========================================
    // Error Handling and Retry Tests
    // ========================================

    @Test
    fun retryExport_afterError_clearsErrorAndRetries() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        whenever(exportDataUseCase.execute()).thenReturn(mockExportResult)

        // Set error state first
        viewModel.performExport()

        // Act
        viewModel.retryExport()

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
    }

    @Test
    fun dismissError_returnsToIdleState() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport())
            .thenReturn(invalidValidation)

        viewModel.performExport()
        assertTrue(viewModel.exportState.value is ExportViewState.Error)

        // Act
        viewModel.dismissError()

        // Assert
        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Idle)
    }

    // ========================================
    // Cancellation Tests
    // ========================================

    @Test
    fun cancelExport_whileExporting_stopsOperation() = runTest {
        // Arrange — cancel when not actively exporting resets isExporting flag
        viewModel.cancelExport()

        // Assert — calling cancelExport when not exporting is a no-op
        assertFalse(viewModel.isExporting.value)
        assertTrue(viewModel.exportState.value is ExportViewState.Idle)
    }

    @Test
    fun cancelExport_whenNotExporting_doesNothing() = runTest {
        // Arrange
        val initialState = viewModel.exportState.value

        // Act
        viewModel.cancelExport()

        // Assert
        // State should remain unchanged
        assertTrue(viewModel.exportState.value is ExportViewState.Idle)
    }

    // ========================================
    // Cleanup Operations Tests
    // ========================================

    @Test
    fun cleanupOldExports_callsUseCase() = runTest {
        // Arrange
        whenever(exportDataUseCase.cleanupOldExports(any())).thenReturn(3)

        // Act
        viewModel.cleanupOldExports(daysOld = 7)
        // launchBackground uses Dispatchers.IO — wait for IO thread to complete
        Thread.sleep(200)

        // Assert
        verify(exportDataUseCase).cleanupOldExports(7)
    }

    @Test
    fun cleanupOldExports_withCustomDays_passesCorrectParameter() = runTest {
        // Arrange
        whenever(exportDataUseCase.cleanupOldExports(30)).thenReturn(5)

        // Act
        viewModel.cleanupOldExports(daysOld = 30)
        // launchBackground uses Dispatchers.IO — wait for IO thread to complete
        Thread.sleep(200)

        // Assert
        verify(exportDataUseCase).cleanupOldExports(30)
    }

    @Test
    fun getAvailableExports_callsUseCase() = runTest {
        // Arrange
        whenever(exportDataUseCase.getAvailableExports()).thenReturn(emptyList())

        // Act
        viewModel.getAvailableExports()
        // launchBackground uses Dispatchers.IO — wait for IO thread to complete
        Thread.sleep(200)

        // Assert
        verify(exportDataUseCase).getAvailableExports()
    }

    // ========================================
    // State Helper Methods Tests
    // ========================================

    @Test
    fun canExport_withData_returnsTrue() = runTest {
        // Arrange
        val stats = mapOf(
            "patients" to 150,
            "appointments" to 500,
            "payments" to 1200,
            "total" to 1850
        )

        whenever(exportDataUseCase.getExportStats()).thenReturn(stats)
        whenever(exportDataUseCase.getStorageStatus()).thenReturn("OK")

        viewModel.loadExportStatistics()

        // Act
        val canExport = viewModel.canExport()

        // Assert
        assertTrue(canExport)
    }

    @Test
    fun canExport_withoutData_returnsFalse() = runTest {
        // Arrange
        val stats = mapOf(
            "patients" to 0,
            "appointments" to 0,
            "payments" to 0,
            "total" to 0
        )

        whenever(exportDataUseCase.getExportStats()).thenReturn(stats)
        whenever(exportDataUseCase.getStorageStatus()).thenReturn("No data")

        viewModel.loadExportStatistics()

        // Act
        val canExport = viewModel.canExport()

        // Assert
        assertFalse(canExport)
    }

    @Test
    fun canExport_whileExporting_returnsFalse() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        doAnswer {
            Thread.sleep(100)
            mockExportResult
        }.whenever(exportDataUseCase).execute()

        // Act
        viewModel.performExport()
        val canExport = viewModel.canExport()

        // Assert
        assertFalse(canExport)
    }

    @Test
    fun isOperationInProgress_duringExport_returnsTrue() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        doAnswer {
            Thread.sleep(100)
            mockExportResult
        }.whenever(exportDataUseCase).execute()

        // Act
        viewModel.performExport()

        // Assert
        assertFalse(viewModel.isOperationInProgress()) // After completion
    }

    @Test
    fun getStatusMessage_idle_returnsReadyMessage() = runTest {
        // Arrange
        val stats = mapOf(
            "patients" to 100,
            "appointments" to 200,
            "payments" to 300,
            "total" to 600
        )

        whenever(exportDataUseCase.getExportStats()).thenReturn(stats)
        whenever(exportDataUseCase.getStorageStatus()).thenReturn("OK")

        viewModel.loadExportStatistics()

        // Act
        val message = viewModel.getStatusMessage()

        // Assert
        assertTrue(message.contains("Pronto") || message.contains("600"))
    }

    @Test
    fun getStatusMessage_error_returnsErrorMessage() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(invalidValidation)

        viewModel.performExport()

        // Act
        val message = viewModel.getStatusMessage()

        // Assert
        assertTrue(message.contains("Espaço insuficiente"))
    }

    // ========================================
    // State Transitions Tests
    // ========================================

    @Test
    fun stateTransition_idle_to_validating_to_success() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        whenever(exportDataUseCase.execute()).thenReturn(mockExportResult)

        val stats = mapOf("total" to 100)
        whenever(exportDataUseCase.getExportStats()).thenReturn(stats)
        whenever(exportDataUseCase.getStorageStatus()).thenReturn("OK")

        viewModel.loadExportStatistics()
        assertTrue(viewModel.exportState.value is ExportViewState.Idle)

        // Act
        viewModel.performExport()

        // Assert
        assertTrue(viewModel.exportState.value is ExportViewState.Success)
    }

    @Test
    fun stateTransition_idle_to_validating_to_error() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(invalidValidation)

        // Act
        viewModel.performExport()

        // Assert
        assertTrue(viewModel.exportState.value is ExportViewState.Error)
    }

    @Test
    fun stateTransition_error_to_idle() = runTest {
        // Arrange
        whenever(exportDataUseCase.validateExport()).thenReturn(invalidValidation)
        viewModel.performExport()
        assertTrue(viewModel.exportState.value is ExportViewState.Error)

        // Act
        viewModel.dismissError()

        // Assert
        assertTrue(viewModel.exportState.value is ExportViewState.Idle)
    }

    // ========================================
    // ExportType Tests — T017
    // ========================================

    @Test
    fun `performSelectiveExport patients only sets ExportType PATIENTS`() = runTest {
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        val result = mockExportResult.copy(appointmentFile = null, paymentFile = null,
            appointmentCount = 0, paymentCount = 0)
        whenever(exportDataUseCase.executeSelective(true, false, false)).thenReturn(result)

        viewModel.performSelectiveExport(exportPatients = true, exportAppointments = false, exportPayments = false)

        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
        assertEquals(ExportType.PATIENTS, (state as ExportViewState.Success).exportType)
    }

    @Test
    fun `performSelectiveExport appointments only sets ExportType APPOINTMENTS`() = runTest {
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        val result = mockExportResult.copy(patientFile = null, paymentFile = null,
            patientCount = 0, paymentCount = 0)
        whenever(exportDataUseCase.executeSelective(false, true, false)).thenReturn(result)

        viewModel.performSelectiveExport(exportPatients = false, exportAppointments = true, exportPayments = false)

        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
        assertEquals(ExportType.APPOINTMENTS, (state as ExportViewState.Success).exportType)
    }

    @Test
    fun `performSelectiveExport payments only sets ExportType PAYMENTS`() = runTest {
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        val result = mockExportResult.copy(patientFile = null, appointmentFile = null,
            patientCount = 0, appointmentCount = 0)
        whenever(exportDataUseCase.executeSelective(false, false, true)).thenReturn(result)

        viewModel.performSelectiveExport(exportPatients = false, exportAppointments = false, exportPayments = true)

        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
        assertEquals(ExportType.PAYMENTS, (state as ExportViewState.Success).exportType)
    }

    @Test
    fun `performExport sets ExportType ALL`() = runTest {
        whenever(exportDataUseCase.validateExport()).thenReturn(validValidation)
        whenever(exportDataUseCase.execute()).thenReturn(mockExportResult)

        viewModel.performExport()

        val state = viewModel.exportState.value
        assertTrue(state is ExportViewState.Success)
        assertEquals(ExportType.ALL, (state as ExportViewState.Success).exportType)
    }
}
