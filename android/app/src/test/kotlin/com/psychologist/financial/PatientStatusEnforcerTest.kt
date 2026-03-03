package com.psychologist.financial

import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.services.PatientStatusEnforcer
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Unit tests for PatientStatusEnforcer service
 *
 * Coverage:
 * - Appointment creation validation
 * - Payment creation validation
 * - Read-only status checking
 * - Access control validation
 * - Batch operations (filtering, counting)
 * - Error message generation
 *
 * Total: 30+ test cases covering all enforcer methods
 */
class PatientStatusEnforcerTest {

    private lateinit var enforcer: PatientStatusEnforcer

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
        id = 2,
        name = "Maria Santos",
        phone = "(21) 98765-4321",
        email = null,
        status = PatientStatus.INACTIVE,
        initialConsultDate = LocalDate.now(),
        registrationDate = LocalDate.now(),
        createdDate = LocalDateTime.now()
    )

    @Before
    fun setUp() {
        enforcer = PatientStatusEnforcer()
    }

    // ========================================
    // Appointment Validation Tests
    // ========================================

    @Test
    fun canCreateAppointment_withActivePatient_returnsTrue() {
        assertTrue(enforcer.canCreateAppointment(activePatient))
    }

    @Test
    fun canCreateAppointment_withInactivePatient_returnsFalse() {
        assertFalse(enforcer.canCreateAppointment(inactivePatient))
    }

    @Test
    fun canCreateAppointmentByStatus_withActiveStatus_returnsTrue() {
        assertTrue(enforcer.canCreateAppointmentByStatus(PatientStatus.ACTIVE))
    }

    @Test
    fun canCreateAppointmentByStatus_withInactiveStatus_returnsFalse() {
        assertFalse(enforcer.canCreateAppointmentByStatus(PatientStatus.INACTIVE))
    }

    @Test
    fun getAppointmentCreationError_withInactivePatient_returnsExplanation() {
        val error = enforcer.getAppointmentCreationError(inactivePatient)
        assertTrue(error.contains("inativo"))
        assertTrue(error.contains(inactivePatient.name))
    }

    @Test
    fun getAppointmentCreationError_errorContainsActionableMessage() {
        val error = enforcer.getAppointmentCreationError(inactivePatient)
        assertTrue(error.contains("reative"))  // Contains "reativar" (reactivate)
    }

    @Test
    fun getAppointmentRestrictionMessage_withInactivePatient_describesRestriction() {
        val message = enforcer.getAppointmentRestrictionMessage(inactivePatient)
        assertTrue(message.contains("inativo"))
        assertTrue(message.contains("atendimento"))
    }

    // ========================================
    // Payment Validation Tests
    // ========================================

    @Test
    fun canCreatePayment_withActivePatient_returnsTrue() {
        assertTrue(enforcer.canCreatePayment(activePatient))
    }

    @Test
    fun canCreatePayment_withInactivePatient_returnsFalse() {
        assertFalse(enforcer.canCreatePayment(inactivePatient))
    }

    @Test
    fun canCreatePaymentByStatus_withActiveStatus_returnsTrue() {
        assertTrue(enforcer.canCreatePaymentByStatus(PatientStatus.ACTIVE))
    }

    @Test
    fun canCreatePaymentByStatus_withInactiveStatus_returnsFalse() {
        assertFalse(enforcer.canCreatePaymentByStatus(PatientStatus.INACTIVE))
    }

    @Test
    fun getPaymentCreationError_withInactivePatient_returnsExplanation() {
        val error = enforcer.getPaymentCreationError(inactivePatient)
        assertTrue(error.contains("inativo"))
        assertTrue(error.contains(inactivePatient.name))
    }

    @Test
    fun getPaymentCreationError_errorContainsActionableMessage() {
        val error = enforcer.getPaymentCreationError(inactivePatient)
        assertTrue(error.contains("reative"))  // Contains "reativar"
    }

    @Test
    fun getPaymentRestrictionMessage_withInactivePatient_describesRestriction() {
        val message = enforcer.getPaymentRestrictionMessage(inactivePatient)
        assertTrue(message.contains("inativo"))
        assertTrue(message.contains("pagamento"))
    }

    // ========================================
    // Access Control Tests
    // ========================================

    @Test
    fun isPatientReadOnly_withActivePatient_returnsFalse() {
        assertFalse(enforcer.isPatientReadOnly(activePatient))
    }

    @Test
    fun isPatientReadOnly_withInactivePatient_returnsTrue() {
        assertTrue(enforcer.isPatientReadOnly(inactivePatient))
    }

    @Test
    fun isPatientWriteAccessible_withActivePatient_returnsTrue() {
        assertTrue(enforcer.isPatientWriteAccessible(activePatient))
    }

    @Test
    fun isPatientWriteAccessible_withInactivePatient_returnsFalse() {
        assertFalse(enforcer.isPatientWriteAccessible(inactivePatient))
    }

    @Test
    fun getAccessLevelDescription_withActivePatient_describesBothAccess() {
        val description = enforcer.getAccessLevelDescription(activePatient)
        assertTrue(description.contains("ativo"))
        assertTrue(description.contains("leitura"))
        assertTrue(description.contains("escrita"))
    }

    @Test
    fun getAccessLevelDescription_withInactivePatient_describesReadOnly() {
        val description = enforcer.getAccessLevelDescription(inactivePatient)
        assertTrue(description.contains("inativo"))
        assertTrue(description.contains("leitura"))
        assertFalse(description.contains("escrita"))
    }

    // ========================================
    // Validation Result Tests
    // ========================================

    @Test
    fun validatePatientAccess_withActivePatient_returnsValid() {
        val result = enforcer.validatePatientAccess(activePatient, "create appointment")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun validatePatientAccess_withInactivePatient_returnsInvalid() {
        val result = enforcer.validatePatientAccess(inactivePatient, "create appointment")
        assertFalse(result.isValid)
        assertTrue(result.isFailed)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validationResult_errorMessage_containsOperationType() {
        val result = enforcer.validatePatientAccess(inactivePatient, "custom operation")
        assertTrue(result.errorMessage!!.contains("custom operation"))
    }

    @Test
    fun validationResult_getErrorMessageOrEmpty_returnsMessageWhenFailed() {
        val result = enforcer.validatePatientAccess(inactivePatient, "test")
        val message = result.getErrorMessageOrEmpty()
        assertTrue(message.isNotEmpty())
    }

    @Test
    fun validationResult_getErrorMessageOrEmpty_returnsEmptyWhenValid() {
        val result = enforcer.validatePatientAccess(activePatient, "test")
        val message = result.getErrorMessageOrEmpty()
        assertTrue(message.isEmpty())
    }

    // ========================================
    // Batch Operations Tests
    // ========================================

    @Test
    fun filterActivePatients_withMixedList_returnsOnlyActive() {
        val patients = listOf(activePatient, inactivePatient, activePatient)
        val filtered = enforcer.filterActivePatients(patients)

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.status == PatientStatus.ACTIVE })
    }

    @Test
    fun filterActivePatients_withEmptyList_returnsEmpty() {
        val filtered = enforcer.filterActivePatients(emptyList())
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun filterActivePatients_withOnlyInactive_returnsEmpty() {
        val patients = listOf(inactivePatient, inactivePatient)
        val filtered = enforcer.filterActivePatients(patients)
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun filterInactivePatients_withMixedList_returnsOnlyInactive() {
        val patients = listOf(activePatient, inactivePatient, activePatient)
        val filtered = enforcer.filterInactivePatients(patients)

        assertEquals(1, filtered.size)
        assertTrue(filtered.all { it.status == PatientStatus.INACTIVE })
    }

    @Test
    fun filterInactivePatients_withEmptyList_returnsEmpty() {
        val filtered = enforcer.filterInactivePatients(emptyList())
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun countActivePatients_withMixedList_returnsCorrectCount() {
        val patients = listOf(activePatient, inactivePatient, activePatient, activePatient)
        val count = enforcer.countActivePatients(patients)

        assertEquals(3, count)
    }

    @Test
    fun countActivePatients_withOnlyInactive_returnsZero() {
        val patients = listOf(inactivePatient, inactivePatient)
        val count = enforcer.countActivePatients(patients)

        assertEquals(0, count)
    }

    @Test
    fun countInactivePatients_withMixedList_returnsCorrectCount() {
        val patients = listOf(activePatient, inactivePatient, activePatient, inactivePatient)
        val count = enforcer.countInactivePatients(patients)

        assertEquals(2, count)
    }

    @Test
    fun countInactivePatients_withOnlyActive_returnsZero() {
        val patients = listOf(activePatient, activePatient)
        val count = enforcer.countInactivePatients(patients)

        assertEquals(0, count)
    }

    // ========================================
    // Edge Cases and Combinations
    // ========================================

    @Test
    fun canCreateAppointmentAndPayment_withActivePatient_bothTrue() {
        assertTrue(enforcer.canCreateAppointment(activePatient))
        assertTrue(enforcer.canCreatePayment(activePatient))
    }

    @Test
    fun canCreateAppointmentAndPayment_withInactivePatient_bothFalse() {
        assertFalse(enforcer.canCreateAppointment(inactivePatient))
        assertFalse(enforcer.canCreatePayment(inactivePatient))
    }

    @Test
    fun errorMessages_differentForAppointmentAndPayment() {
        val appointmentError = enforcer.getAppointmentCreationError(inactivePatient)
        val paymentError = enforcer.getPaymentCreationError(inactivePatient)

        assertTrue(appointmentError.contains("atendimento"))
        assertTrue(paymentError.contains("pagamento"))
        assertNotEquals(appointmentError, paymentError)
    }

    @Test
    fun patientWithNoContact_stillChecksByStatus() {
        val patientNoContact = activePatient.copy(phone = null, email = null)
        assertTrue(enforcer.canCreateAppointment(patientNoContact))
    }

    @Test
    fun largePatientListFiltering_performanceCheck() {
        val largeList = (1..1000).map { i ->
            activePatient.copy(
                id = i.toLong(),
                name = "Patient $i",
                status = if (i % 3 == 0) PatientStatus.INACTIVE else PatientStatus.ACTIVE
            )
        }

        val filtered = enforcer.filterActivePatients(largeList)
        val count = enforcer.countActivePatients(largeList)

        // Should have approximately 2/3 active (1/3 inactive)
        assertTrue(filtered.size > 600 && filtered.size < 700)
        assertEquals(filtered.size, count)
    }
}
