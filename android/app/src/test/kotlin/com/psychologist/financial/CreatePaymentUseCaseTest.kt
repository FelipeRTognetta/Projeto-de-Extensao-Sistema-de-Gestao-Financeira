package com.psychologist.financial

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.usecases.CreatePaymentResult
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.validation.PaymentValidator
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for CreatePaymentUseCase
 *
 * Coverage:
 * - Valid payment creation (Success)
 * - Validator failure propagation (ValidationError)
 * - Patient not found handling
 * - Inactive patient rejection
 * - Repository interaction on success
 * - IllegalArgumentException → ValidationError
 * - executeWithObject delegation
 * - validate() without saving
 *
 * Total: 18 test cases
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
    fun `execute with valid data returns Success`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        whenever(mockPaymentRepository.insert(any(), any(), any(), any(), any(), any()))
            .thenReturn(99L)

        val result = useCase.execute(
            patientId = 1L,
            amount = BigDecimal("150.00"),
            status = "PAID",
            paymentMethod = "TRANSFER",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.Success>(result)
        assertEquals(99L, result.paymentId)
    }

    @Test
    fun `execute with optional appointmentId links payment to appointment`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        whenever(mockPaymentRepository.insert(any(), any(), any(), any(), any(), any()))
            .thenReturn(5L)

        val result = useCase.execute(
            patientId = 1L,
            appointmentId = 10L,
            amount = BigDecimal("200.00"),
            status = "PAID",
            paymentMethod = "PIX",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.Success>(result)
    }

    @Test
    fun `execute with PENDING status creates valid payment`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        whenever(mockPaymentRepository.insert(any(), any(), any(), any(), any(), any()))
            .thenReturn(3L)

        val result = useCase.execute(
            patientId = 1L,
            amount = BigDecimal("300.00"),
            status = "PENDING",
            paymentMethod = "CASH",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.Success>(result)
    }

    // ========================================
    // Validator Failure Tests
    // ========================================

    @Test
    fun `execute returns ValidationError when validator fails`() = runTest {
        val validationError = com.psychologist.financial.domain.validation.ValidationError(
            field = "amount",
            message = "Valor deve ser maior que zero"
        )
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(listOf(validationError))

        val result = useCase.execute(
            patientId = 1L,
            amount = BigDecimal("0"),
            status = "PAID",
            paymentMethod = "CASH",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.ValidationError>(result)
        assertEquals("Valor deve ser maior que zero", result.getFirstErrorMessage())
    }

    @Test
    fun `execute returns all validation errors from validator`() = runTest {
        val errors = listOf(
            com.psychologist.financial.domain.validation.ValidationError(
                field = "amount",
                message = "Valor inválido"
            ),
            com.psychologist.financial.domain.validation.ValidationError(
                field = "method",
                message = "Método inválido"
            )
        )
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(errors)

        val result = useCase.execute(
            patientId = 1L,
            amount = BigDecimal("-1"),
            status = "PAID",
            paymentMethod = "",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.ValidationError>(result)
        assertEquals(2, result.errors.size)
    }

    // ========================================
    // Patient Not Found Tests
    // ========================================

    @Test
    fun `execute returns ValidationError when patient not found`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(999L)).thenReturn(null)

        val result = useCase.execute(
            patientId = 999L,
            amount = BigDecimal("150.00"),
            status = "PAID",
            paymentMethod = "TRANSFER",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.ValidationError>(result)
        assertTrue(result.getErrorsByField().containsKey("patientId"))
    }

    // ========================================
    // Inactive Patient Tests
    // ========================================

    @Test
    fun `execute returns ValidationError for inactive patient`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(2L)).thenReturn(inactivePatient)

        val result = useCase.execute(
            patientId = 2L,
            amount = BigDecimal("150.00"),
            status = "PAID",
            paymentMethod = "TRANSFER",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.ValidationError>(result)
        val errorMsg = result.getFirstErrorMessage()
        assertTrue(errorMsg.contains("inativo", ignoreCase = true))
    }

    // ========================================
    // Repository Exception Tests
    // ========================================

    @Test
    fun `execute returns ValidationError on IllegalArgumentException from repository`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        whenever(mockPaymentRepository.insert(any(), any(), any(), any(), any(), any()))
            .thenThrow(IllegalArgumentException("Dados inválidos"))

        val result = useCase.execute(
            patientId = 1L,
            amount = BigDecimal("150.00"),
            status = "PAID",
            paymentMethod = "TRANSFER",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.ValidationError>(result)
    }

    // ========================================
    // executeWithObject Tests
    // ========================================

    @Test
    fun `executeWithObject delegates to execute`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)
        whenever(mockPaymentRepository.insert(any(), any(), any(), any(), any(), any()))
            .thenReturn(7L)

        val payment = com.psychologist.financial.domain.models.Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            status = "PAID",
            paymentMethod = "TRANSFER",
            paymentDate = yesterday
        )

        val result = useCase.executeWithObject(payment)

        assertIs<CreatePaymentResult.Success>(result)
    }

    // ========================================
    // validate() Tests (no-save)
    // ========================================

    @Test
    fun `validate returns empty list for valid payment`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(1L)).thenReturn(activePatient)

        val errors = useCase.validate(
            patientId = 1L,
            amount = BigDecimal("150.00"),
            status = "PAID",
            paymentMethod = "TRANSFER",
            paymentDate = yesterday
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate returns errors for inactive patient`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(2L)).thenReturn(inactivePatient)

        val errors = useCase.validate(
            patientId = 2L,
            amount = BigDecimal("150.00"),
            status = "PAID",
            paymentMethod = "TRANSFER",
            paymentDate = yesterday
        )

        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.field == "patientId" })
    }

    @Test
    fun `validate returns errors for missing patient`() = runTest {
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockPatientRepository.getPatient(999L)).thenReturn(null)

        val errors = useCase.validate(
            patientId = 999L,
            amount = BigDecimal("150.00"),
            status = "PAID",
            paymentMethod = "TRANSFER",
            paymentDate = yesterday
        )

        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.field == "patientId" })
    }

    // ========================================
    // ValidationError helper tests
    // ========================================

    @Test
    fun `CreatePaymentResult_ValidationError getErrorsByField groups correctly`() = runTest {
        val errors = listOf(
            com.psychologist.financial.domain.validation.ValidationError("amount", "Erro 1"),
            com.psychologist.financial.domain.validation.ValidationError("amount", "Erro 2"),
            com.psychologist.financial.domain.validation.ValidationError("method", "Erro método")
        )
        whenever(mockPaymentValidator.validateNewPayment(any(), any(), any(), any(), any()))
            .thenReturn(errors)

        val result = useCase.execute(
            patientId = 1L,
            amount = BigDecimal("-1"),
            status = "INVALID",
            paymentMethod = "",
            paymentDate = yesterday
        )

        assertIs<CreatePaymentResult.ValidationError>(result)
        val grouped = result.getErrorsByField()
        assertEquals(2, grouped["amount"]?.size)
        assertEquals(1, grouped["method"]?.size)
    }
}
