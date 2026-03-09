package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.validation.PaymentValidator
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for CreatePaymentUseCase.
 *
 * Tests payment creation with:
 * - Optional appointment linkage (0, 1, or multiple)
 * - Validation of amount and patient
 * - Patient status checking (only ACTIVE allowed)
 *
 * Run with: ./gradlew testDebugUnitTest --tests CreatePaymentUseCaseTest
 */
class CreatePaymentUseCaseTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var patientRepository: PatientRepository

    @Mock
    private lateinit var paymentValidator: PaymentValidator

    private lateinit var useCase: CreatePaymentUseCase

    private val testPatient = Patient(
        id = 1L,
        name = "Test Patient",
        phone = "11999999999",
        email = "test@example.com",
        status = PatientStatus.ACTIVE,
        initialConsultDate = LocalDate.of(2024, 1, 1),
        registrationDate = LocalDate.of(2024, 1, 1)
    )

    private val inactivePatient = Patient(
        id = 2L,
        name = "Inactive Patient",
        phone = "11988888888",
        email = "inactive@example.com",
        status = PatientStatus.INACTIVE,
        initialConsultDate = LocalDate.of(2023, 1, 1),
        registrationDate = LocalDate.of(2023, 1, 1)
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = CreatePaymentUseCase(paymentRepository, patientRepository, paymentValidator)
    }

    // ========================================
    // Valid Payment Creation Tests
    // ========================================

    @Test
    fun createPayment_validAmountWithoutAppointments_succeeds() = runBlocking {
        // Arrange
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = emptyList()
        )

        whenever(patientRepository.getPatient(1L)).thenReturn(testPatient)
        whenever(paymentValidator.validate(payment)).thenReturn(
            com.psychologist.financial.domain.validation.ValidationResult(
                isValid = true,
                errors = emptyList()
            )
        )
        whenever(paymentRepository.insert(payment.patientId, payment.amount, payment.paymentDate)).thenReturn(1L)

        // Act
        val result = useCase.createPayment(payment, emptyList())

        // Assert
        assert(result == 1L) { "Should return payment ID 1" }
    }

    @Test
    fun createPayment_validAmountWithSingleAppointment_linksAppointment() = runBlocking {
        // Arrange
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )
        val appointmentIds = listOf(10L)

        whenever(patientRepository.getPatient(1L)).thenReturn(testPatient)
        whenever(paymentValidator.validate(payment)).thenReturn(
            com.psychologist.financial.domain.validation.ValidationResult(
                isValid = true,
                errors = emptyList()
            )
        )
        whenever(paymentRepository.createPaymentWithAppointments(payment, appointmentIds)).thenReturn(1L)

        // Act
        val result = useCase.createPayment(payment, appointmentIds)

        // Assert
        assert(result == 1L) { "Should return payment ID 1" }
    }

    @Test
    fun createPayment_validAmountWithMultipleAppointments_linksAllAppointments() = runBlocking {
        // Arrange
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("300.00"),
            paymentDate = LocalDate.now()
        )
        val appointmentIds = listOf(10L, 11L, 12L)

        whenever(patientRepository.getPatient(1L)).thenReturn(testPatient)
        whenever(paymentValidator.validate(payment)).thenReturn(
            com.psychologist.financial.domain.validation.ValidationResult(
                isValid = true,
                errors = emptyList()
            )
        )
        whenever(paymentRepository.createPaymentWithAppointments(payment, appointmentIds)).thenReturn(1L)

        // Act
        val result = useCase.createPayment(payment, appointmentIds)

        // Assert
        assert(result == 1L) { "Should return payment ID 1" }
    }

    // ========================================
    // Validation Failure Tests
    // ========================================

    @Test
    fun createPayment_zeroAmount_throwsException() = runBlocking {
        // Arrange
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal.ZERO,
            paymentDate = LocalDate.now()
        )

        whenever(patientRepository.getPatient(1L)).thenReturn(testPatient)
        whenever(paymentValidator.validate(payment)).thenReturn(
            com.psychologist.financial.domain.validation.ValidationResult(
                isValid = false,
                errors = listOf("Valor deve ser maior que zero")
            )
        )

        // Act & Assert
        try {
            useCase.createPayment(payment, emptyList())
            assert(false) { "Should throw exception for invalid amount" }
        } catch (e: IllegalArgumentException) {
            assert(e.message?.contains("Valor deve ser maior que zero") == true)
        }
    }

    @Test
    fun createPayment_negativeAmount_throwsException() = runBlocking {
        // Arrange
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("-100.00"),
            paymentDate = LocalDate.now()
        )

        whenever(patientRepository.getPatient(1L)).thenReturn(testPatient)
        whenever(paymentValidator.validate(payment)).thenReturn(
            com.psychologist.financial.domain.validation.ValidationResult(
                isValid = false,
                errors = listOf("Valor deve ser maior que zero")
            )
        )

        // Act & Assert
        try {
            useCase.createPayment(payment, emptyList())
            assert(false) { "Should throw exception for negative amount" }
        } catch (e: IllegalArgumentException) {
            assert(true)
        }
    }

    @Test
    fun createPayment_inactivePatient_throwsException() = runBlocking {
        // Arrange
        val payment = Payment(
            id = 0L,
            patientId = 2L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        whenever(patientRepository.getPatient(2L)).thenReturn(inactivePatient)

        // Act & Assert
        try {
            useCase.createPayment(payment, emptyList())
            assert(false) { "Should throw exception for inactive patient" }
        } catch (e: IllegalStateException) {
            assert(e.message?.contains("inativo") == true || e.message?.contains("INACTIVE") == true)
        }
    }

    @Test
    fun createPayment_patientNotFound_throwsException() = runBlocking {
        // Arrange
        val payment = Payment(
            id = 0L,
            patientId = 999L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        whenever(patientRepository.getPatient(999L)).thenReturn(null)

        // Act & Assert
        try {
            useCase.createPayment(payment, emptyList())
            assert(false) { "Should throw exception for non-existent patient" }
        } catch (e: IllegalStateException) {
            assert(true)
        }
    }
}
