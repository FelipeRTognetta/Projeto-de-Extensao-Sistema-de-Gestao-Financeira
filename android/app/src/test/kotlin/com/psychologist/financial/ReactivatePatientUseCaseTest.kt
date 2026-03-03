package com.psychologist.financial

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.usecases.ReactivatePatientUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ReactivatePatientUseCase
 *
 * Coverage:
 * - execute() with valid inactive patient
 * - execute() when patient not found
 * - execute() with invalid patientId (throws)
 * - execute() when patient already active (idempotent)
 * - canReactivate() for inactive/active patients
 * - validateReactivation() always returns true
 * - getStatusChangeMessage() format
 * - getWarningMessage() format
 *
 * Total: 14 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class ReactivatePatientUseCaseTest {

    @Mock
    private lateinit var mockRepository: PatientRepository

    private lateinit var useCase: ReactivatePatientUseCase

    private val inactivePatient = Patient.createForTesting(
        id = 1L,
        name = "Maria Inativa",
        status = PatientStatus.INACTIVE
    )

    private val activePatient = Patient.createForTesting(
        id = 2L,
        name = "João Ativo",
        status = PatientStatus.ACTIVE
    )

    @Before
    fun setUp() {
        useCase = ReactivatePatientUseCase(repository = mockRepository)
    }

    // ========================================
    // execute() Tests
    // ========================================

    @Test
    fun `execute reactivates inactive patient and returns updated record`() = runTest {
        val reactivatedPatient = inactivePatient.copy(status = PatientStatus.ACTIVE)
        whenever(mockRepository.getPatient(1L))
            .thenReturn(inactivePatient)
            .thenReturn(reactivatedPatient)

        val result = useCase.execute(patientId = 1L)

        assertNotNull(result)
        assertEquals(PatientStatus.ACTIVE, result.status)
        verify(mockRepository).markAsActive(1L)
    }

    @Test
    fun `execute returns null when patient not found`() = runTest {
        whenever(mockRepository.getPatient(99L)).thenReturn(null)

        val result = useCase.execute(patientId = 99L)

        assertNull(result)
        verify(mockRepository, never()).markAsActive(99L)
    }

    @Test
    fun `execute with zero patientId throws IllegalArgumentException`() = runTest {
        try {
            useCase.execute(patientId = 0L)
            assertTrue(false, "Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("positive") == true)
        }
    }

    @Test
    fun `execute with negative patientId throws IllegalArgumentException`() = runTest {
        try {
            useCase.execute(patientId = -5L)
            assertTrue(false, "Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("positive") == true)
        }
    }

    @Test
    fun `execute with already active patient is idempotent`() = runTest {
        val updatedActive = activePatient.copy(status = PatientStatus.ACTIVE)
        whenever(mockRepository.getPatient(2L))
            .thenReturn(activePatient)
            .thenReturn(updatedActive)

        val result = useCase.execute(patientId = 2L)

        assertNotNull(result)
        assertEquals(PatientStatus.ACTIVE, result.status)
        // markAsActive called regardless (idempotent)
        verify(mockRepository).markAsActive(2L)
    }

    // ========================================
    // canReactivate() Tests
    // ========================================

    @Test
    fun `canReactivate returns true for inactive patient with valid id`() {
        val result = useCase.canReactivate(inactivePatient)

        assertTrue(result)
    }

    @Test
    fun `canReactivate returns false for active patient`() {
        val result = useCase.canReactivate(activePatient)

        assertFalse(result)
    }

    @Test
    fun `canReactivate returns false for patient with zero id`() {
        val patientWithZeroId = inactivePatient.copy(id = 0L)

        val result = useCase.canReactivate(patientWithZeroId)

        assertFalse(result)
    }

    // ========================================
    // validateReactivation() Tests
    // ========================================

    @Test
    fun `validateReactivation returns true for valid inactive patient`() = runTest {
        val result = useCase.validateReactivation(inactivePatient)

        assertTrue(result)
    }

    @Test
    fun `validateReactivation returns false for patient with invalid id`() = runTest {
        val invalidPatient = inactivePatient.copy(id = 0L)

        val result = useCase.validateReactivation(invalidPatient)

        assertFalse(result)
    }

    @Test
    fun `validateReactivation returns true for active patient with valid id`() = runTest {
        // Current implementation: no restriction on already-active patients
        val result = useCase.validateReactivation(activePatient)

        assertTrue(result)
    }

    // ========================================
    // Message Tests
    // ========================================

    @Test
    fun `getStatusChangeMessage contains patient name`() {
        val message = useCase.getStatusChangeMessage("Maria Inativa")

        assertTrue(message.contains("Maria Inativa"))
        assertTrue(message.contains("reativado"))
    }

    @Test
    fun `getWarningMessage contains patient name and appointment info`() {
        val message = useCase.getWarningMessage("João Silva")

        assertTrue(message.contains("João Silva"))
        assertTrue(message.contains("atendimentos") || message.contains("pagamentos"))
    }
}
