package com.psychologist.financial

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.validation.PaymentValidator
import com.psychologist.financial.domain.validation.ValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for CreatePaymentUseCase
 *
 * Coverage:
 * - Valid payment creation (returns ID)
 * - Patient not found → IllegalStateException
 * - Inactive patient → IllegalStateException
 * - Validation failure → IllegalArgumentException
 * - With appointmentIds delegates to createPaymentWithAppointments
 * - Without appointmentIds delegates to repository.insert
 *
 * Total: 7 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class CreatePaymentUseCaseTest {

    @Mock
    private lateinit var mockPaymentRepository: PaymentRepository

    @Mock
    private lateinit var mockPatientRepository: PatientRepository

    @Mock
    private lateinit var mockPaymentValidator: PaymentValidator

    private lateinit var useCase: CreatePaymentUseCase

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)

    private val activePatient = Patient.createForTesting(
        id = 1L,
        name = "João Silva",
        status = PatientStatus.ACTIVE
    )

    private val inactivePatient = Patient.createForTesting(
        id = 2L,
        name = "Maria Inativa",
        status = PatientStatus.INACTIVE
    )

    private fun makePayment(patientId: Long = 1L) = Payment(
        id = 0L,
        patientId = patientId,
        amount = BigDecimal("150.00"),
        paymentDate = yesterday
    )

    @Before
    fun setUp() {
        useCase = CreatePaymentUseCase(
            paymentRepository = mockPaymentRepository,
            patientRepository = mockPatientRepository,
            paymentValidator = mockPaymentValidator
        )
    }

    // ========================================
    // Success Cases
    // ========================================

    @Test
    fun `createPayment with valid data and no appointments returns payment id`() = runTest {
        val payment = makePayment()
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        whenever(mockPaymentValidator.validate(payment)).thenReturn(ValidationResult(true, emptyList()))
        whenever(mockPaymentRepository.insert(payment.patientId, payment.amount, payment.paymentDate))
            .thenReturn(99L)

        val id = useCase.createPayment(payment, emptyList())

        assertEquals(99L, id)
    }

    @Test
    fun `createPayment with appointmentIds delegates to createPaymentWithAppointments`() = runTest {
        val payment = makePayment()
        val appointmentIds = listOf(10L, 11L)
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        whenever(mockPaymentValidator.validate(payment)).thenReturn(ValidationResult(true, emptyList()))
        whenever(mockPaymentRepository.createPaymentWithAppointments(payment, appointmentIds))
            .thenReturn(5L)

        val id = useCase.createPayment(payment, appointmentIds)

        assertEquals(5L, id)
        verify(mockPaymentRepository).createPaymentWithAppointments(payment, appointmentIds)
    }

    // ========================================
    // Patient Not Found
    // ========================================

    @Test
    fun `createPayment throws IllegalStateException when patient not found`() = runTest {
        val payment = makePayment(patientId = 999L)
        whenever(mockPatientRepository.getPatient(999L)).thenReturn(null)

        assertFailsWith<IllegalStateException> {
            useCase.createPayment(payment, emptyList())
        }
    }

    // ========================================
    // Inactive Patient
    // ========================================

    @Test
    fun `createPayment throws IllegalStateException for inactive patient`() = runTest {
        val payment = makePayment(patientId = 2L)
        whenever(mockPatientRepository.getPatient(2L)).thenReturn(inactivePatient)

        val ex = assertFailsWith<IllegalStateException> {
            useCase.createPayment(payment, emptyList())
        }
        assertTrue(ex.message!!.contains("inativo", ignoreCase = true))
    }

    // ========================================
    // Validation Failure
    // ========================================

    @Test
    fun `createPayment throws IllegalArgumentException when validation fails`() = runTest {
        val payment = makePayment()
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        val failResult = ValidationResult(
            isValid = false,
            errors = listOf("Valor deve ser maior que zero")
        )
        whenever(mockPaymentValidator.validate(payment)).thenReturn(failResult)

        assertFailsWith<IllegalArgumentException> {
            useCase.createPayment(payment, emptyList())
        }
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun `createPayment with multiple appointmentIds passes all ids along`() = runTest {
        val payment = makePayment()
        val appointmentIds = listOf(1L, 2L, 3L, 4L, 5L)
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        whenever(mockPaymentValidator.validate(payment)).thenReturn(ValidationResult(true, emptyList()))
        whenever(mockPaymentRepository.createPaymentWithAppointments(payment, appointmentIds))
            .thenReturn(42L)

        val id = useCase.createPayment(payment, appointmentIds)

        assertEquals(42L, id)
        verify(mockPaymentRepository).createPaymentWithAppointments(payment, appointmentIds)
    }

    @Test
    fun `createPayment validates after patient check not before`() = runTest {
        // Patient not found → should throw before touching validator
        val payment = makePayment(patientId = 999L)
        whenever(mockPatientRepository.getPatient(999L)).thenReturn(null)

        assertFailsWith<IllegalStateException> {
            useCase.createPayment(payment, emptyList())
        }
        // Validator should never be called
        verify(mockPaymentValidator, org.mockito.kotlin.never()).validate(any())
    }
}
