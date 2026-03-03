package com.psychologist.financial

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.usecases.MarkPatientInactiveUseCase
import com.psychologist.financial.domain.usecases.ReactivatePatientUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Unit tests for MarkPatientInactiveUseCase and ReactivatePatientUseCase
 *
 * Coverage:
 * - Marking patient as inactive (ACTIVE → INACTIVE transition)
 * - Reactivating inactive patient (INACTIVE → ACTIVE transition)
 * - Validation of patient ID
 * - Handling of patient not found scenarios
 * - Idempotent operations
 * - State transitions
 * - Repository interaction
 * - Error handling
 *
 * Total: 25+ test cases covering all use case methods
 */
class MarkPatientInactiveUseCaseTest {

    @Mock
    private lateinit var repository: PatientRepository

    private lateinit var markInactiveUseCase: MarkPatientInactiveUseCase
    private lateinit var reactivateUseCase: ReactivatePatientUseCase

    // Test data
    private val activePatient = Patient(
        id = 1,
        name = "João Silva",
        phone = "(11) 99999-9999",
        email = "joao@example.com",
        status = PatientStatus.ACTIVE,
        initialConsultDate = LocalDate.now(),
        registrationDate = LocalDate.now(),
        createdDate = LocalDateTime.now()
    )

    private val inactivePatient = Patient(
        id = 1,
        name = "João Silva",
        phone = "(11) 99999-9999",
        email = "joao@example.com",
        status = PatientStatus.INACTIVE,
        initialConsultDate = LocalDate.now(),
        registrationDate = LocalDate.now(),
        createdDate = LocalDateTime.now()
    )

    private val anotherActivePatient = Patient(
        id = 2,
        name = "Maria Santos",
        phone = "(21) 98765-4321",
        email = null,
        status = PatientStatus.ACTIVE,
        initialConsultDate = LocalDate.now(),
        registrationDate = LocalDate.now(),
        createdDate = LocalDateTime.now()
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        markInactiveUseCase = MarkPatientInactiveUseCase(repository)
        reactivateUseCase = ReactivatePatientUseCase(repository)
    }

    // ========================================
    // Mark Inactive - Basic Tests
    // ========================================

    @Test
    fun markPatientInactive_withValidId_returnsInactivePatient() = runTest {
        // Arrange
        mockPatientLookup(activePatient.id, activePatient, inactivePatient)

        // Act
        val result = markInactiveUseCase.execute(activePatient.id)

        // Assert
        assertNotNull(result)
        assertEquals(PatientStatus.INACTIVE, result?.status)
        assertEquals(activePatient.name, result?.name)
    }

    @Test
    fun markPatientInactive_callsRepositoryMarkAsInactive() = runTest {
        // Arrange
        mockPatientLookup(activePatient.id, activePatient, inactivePatient)

        // Act
        markInactiveUseCase.execute(activePatient.id)

        // Assert
        verify(repository).markAsInactive(activePatient.id)
    }

    @Test
    fun markPatientInactive_withPatientNotFound_returnsNull() = runTest {
        // Arrange
        whenever(repository.getPatient(any())).thenReturn(null)

        // Act
        val result = markInactiveUseCase.execute(999)

        // Assert
        assertNull(result)
    }

    @Test
    fun markPatientInactive_withZeroId_throwsException() = runTest {
        // Act & Assert
        try {
            markInactiveUseCase.execute(0)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }
    }

    @Test
    fun markPatientInactive_withNegativeId_throwsException() = runTest {
        // Act & Assert
        try {
            markInactiveUseCase.execute(-1)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }
    }

    // ========================================
    // Mark Inactive - Idempotency Tests
    // ========================================

    @Test
    fun markPatientInactive_alreadyInactive_stillReturnsInactive() = runTest {
        // Arrange - patient is already inactive
        mockPatientLookup(inactivePatient.id, inactivePatient, inactivePatient)

        // Act
        val result = markInactiveUseCase.execute(inactivePatient.id)

        // Assert - should still return inactive (idempotent)
        assertNotNull(result)
        assertEquals(PatientStatus.INACTIVE, result?.status)
    }

    @Test
    fun markPatientInactive_multipleCallsSamePatient_idempotent() = runTest {
        // Arrange
        mockPatientLookup(activePatient.id, activePatient, inactivePatient)

        // Act - first call
        val result1 = markInactiveUseCase.execute(activePatient.id)

        // Reset mock for second call (now patient is inactive)
        mockPatientLookup(activePatient.id, inactivePatient, inactivePatient)

        // Act - second call
        val result2 = markInactiveUseCase.execute(activePatient.id)

        // Assert - both should return inactive
        assertEquals(PatientStatus.INACTIVE, result1?.status)
        assertEquals(PatientStatus.INACTIVE, result2?.status)
    }

    // ========================================
    // Reactivate - Basic Tests
    // ========================================

    @Test
    fun reactivatePatient_withValidId_returnsActivePatient() = runTest {
        // Arrange
        mockPatientLookup(inactivePatient.id, inactivePatient, activePatient)

        // Act
        val result = reactivateUseCase.execute(inactivePatient.id)

        // Assert
        assertNotNull(result)
        assertEquals(PatientStatus.ACTIVE, result?.status)
        assertEquals(inactivePatient.name, result?.name)
    }

    @Test
    fun reactivatePatient_callsRepositoryMarkAsActive() = runTest {
        // Arrange
        mockPatientLookup(inactivePatient.id, inactivePatient, activePatient)

        // Act
        reactivateUseCase.execute(inactivePatient.id)

        // Assert
        verify(repository).markAsActive(inactivePatient.id)
    }

    @Test
    fun reactivatePatient_withPatientNotFound_returnsNull() = runTest {
        // Arrange
        whenever(repository.getPatient(any())).thenReturn(null)

        // Act
        val result = reactivateUseCase.execute(999)

        // Assert
        assertNull(result)
    }

    @Test
    fun reactivatePatient_withZeroId_throwsException() = runTest {
        // Act & Assert
        try {
            reactivateUseCase.execute(0)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }
    }

    @Test
    fun reactivatePatient_withNegativeId_throwsException() = runTest {
        // Act & Assert
        try {
            reactivateUseCase.execute(-1)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive"))
        }
    }

    // ========================================
    // Reactivate - Idempotency Tests
    // ========================================

    @Test
    fun reactivatePatient_alreadyActive_stillReturnsActive() = runTest {
        // Arrange - patient is already active
        mockPatientLookup(activePatient.id, activePatient, activePatient)

        // Act
        val result = reactivateUseCase.execute(activePatient.id)

        // Assert - should still return active (idempotent)
        assertNotNull(result)
        assertEquals(PatientStatus.ACTIVE, result?.status)
    }

    @Test
    fun reactivatePatient_multipleCallsSamePatient_idempotent() = runTest {
        // Arrange
        mockPatientLookup(inactivePatient.id, inactivePatient, activePatient)

        // Act - first call
        val result1 = reactivateUseCase.execute(inactivePatient.id)

        // Reset mock for second call (now patient is active)
        mockPatientLookup(inactivePatient.id, activePatient, activePatient)

        // Act - second call
        val result2 = reactivateUseCase.execute(inactivePatient.id)

        // Assert - both should return active
        assertEquals(PatientStatus.ACTIVE, result1?.status)
        assertEquals(PatientStatus.ACTIVE, result2?.status)
    }

    // ========================================
    // State Transition Tests
    // ========================================

    @Test
    fun stateTransition_activeToInactiveToActive() = runTest {
        // Start: ACTIVE
        mockPatientLookup(activePatient.id, activePatient, inactivePatient)
        val step1 = markInactiveUseCase.execute(activePatient.id)
        assertEquals(PatientStatus.INACTIVE, step1?.status)

        // Middle: INACTIVE
        mockPatientLookup(activePatient.id, inactivePatient, activePatient)
        val step2 = reactivateUseCase.execute(activePatient.id)
        assertEquals(PatientStatus.ACTIVE, step2?.status)

        // End: ACTIVE again
        assertEquals(PatientStatus.ACTIVE, step2?.status)
    }

    @Test
    fun stateTransition_preservesPatientDataExceptStatus() = runTest {
        // Arrange
        mockPatientLookup(activePatient.id, activePatient, inactivePatient)

        // Act
        val result = markInactiveUseCase.execute(activePatient.id)

        // Assert - all other data preserved
        assertEquals(activePatient.name, result?.name)
        assertEquals(activePatient.phone, result?.phone)
        assertEquals(activePatient.email, result?.email)
        assertEquals(activePatient.initialConsultDate, result?.initialConsultDate)
        // Only status changes
        assertNotEquals(activePatient.status, result?.status)
    }

    // ========================================
    // Validation Helper Tests
    // ========================================

    @Test
    fun canMarkInactive_withActivePatient_returnsTrue() {
        assertTrue(markInactiveUseCase.canMarkInactive(activePatient))
    }

    @Test
    fun canMarkInactive_withInactivePatient_returnsFalse() {
        assertFalse(markInactiveUseCase.canMarkInactive(inactivePatient))
    }

    @Test
    fun canMarkInactive_withZeroId_returnsFalse() {
        val patientZeroId = activePatient.copy(id = 0)
        assertFalse(markInactiveUseCase.canMarkInactive(patientZeroId))
    }

    @Test
    fun canReactivate_withInactivePatient_returnsTrue() {
        assertTrue(reactivateUseCase.canReactivate(inactivePatient))
    }

    @Test
    fun canReactivate_withActivePatient_returnsFalse() {
        assertFalse(reactivateUseCase.canReactivate(activePatient))
    }

    @Test
    fun canReactivate_withZeroId_returnsFalse() {
        val patientZeroId = inactivePatient.copy(id = 0)
        assertFalse(reactivateUseCase.canReactivate(patientZeroId))
    }

    @Test
    fun validateReactivation_withInactivePatient_returnsTrue() = runTest {
        val isValid = reactivateUseCase.validateReactivation(inactivePatient)
        assertTrue(isValid)
    }

    @Test
    fun validateReactivation_withZeroId_returnsFalse() = runTest {
        val patientZeroId = inactivePatient.copy(id = 0)
        val isValid = reactivateUseCase.validateReactivation(patientZeroId)
        assertFalse(isValid)
    }

    // ========================================
    // Message Tests
    // ========================================

    @Test
    fun getStatusChangeMessage_markInactive_containsPatientName() {
        val message = markInactiveUseCase.getStatusChangeMessage(activePatient.name)
        assertTrue(message.contains(activePatient.name))
        assertTrue(message.contains("inativo"))
    }

    @Test
    fun getStatusChangeMessage_reactivate_containsPatientName() {
        val message = reactivateUseCase.getStatusChangeMessage(inactivePatient.name)
        assertTrue(message.contains(inactivePatient.name))
        assertTrue(message.contains("reativado"))
    }

    @Test
    fun getWarningMessage_reactivate_describesImplications() {
        val message = reactivateUseCase.getWarningMessage(inactivePatient.name)
        assertTrue(message.contains("reativar"))
        assertTrue(message.contains("atendimento"))
    }

    // ========================================
    // Multiple Patient Tests
    // ========================================

    @Test
    fun markMultiplePatientsInactive_differentPatients() = runTest {
        // Mark first patient inactive
        mockPatientLookup(activePatient.id, activePatient, inactivePatient)
        val result1 = markInactiveUseCase.execute(activePatient.id)

        // Mark different patient inactive
        val anotherInactive = anotherActivePatient.copy(status = PatientStatus.INACTIVE)
        mockPatientLookup(anotherActivePatient.id, anotherActivePatient, anotherInactive)
        val result2 = markInactiveUseCase.execute(anotherActivePatient.id)

        // Assert both marked inactive
        assertEquals(PatientStatus.INACTIVE, result1?.status)
        assertEquals(PatientStatus.INACTIVE, result2?.status)
        assertNotEquals(result1?.id, result2?.id)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private suspend fun mockPatientLookup(
        patientId: Long,
        firstLookupResult: Patient?,
        secondLookupResult: Patient?
    ) {
        // First call to getPatient (check if exists)
        whenever(repository.getPatient(patientId)).thenReturn(firstLookupResult)

        // After marking status, second call returns updated patient
        doAnswer { secondLookupResult }
            .whenever(repository).markAsInactive(patientId)

        doAnswer { secondLookupResult }
            .whenever(repository).markAsActive(patientId)

        // Reset for second lookup
        whenever(repository.getPatient(patientId)).thenReturn(secondLookupResult)
    }
}
