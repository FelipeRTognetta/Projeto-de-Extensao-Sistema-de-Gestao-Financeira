package com.psychologist.financial

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.usecases.CreatePatientUseCase
import com.psychologist.financial.domain.validation.PatientValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Unit tests for CreatePatientUseCase
 *
 * Coverage:
 * - Valid patient creation
 * - Validation errors (name, phone, email, contact, date)
 * - Repository uniqueness checks (phone, email)
 * - Database insertion success
 * - Error handling
 *
 * Total: 26 test cases
 * Tests the boundary between validation layer and data persistence
 */
class CreatePatientUseCaseTest {

    @Mock
    private lateinit var mockRepository: PatientRepository

    private lateinit var validator: PatientValidator
    private lateinit var useCase: CreatePatientUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        validator = PatientValidator()
        useCase = CreatePatientUseCase(
            repository = mockRepository,
            validator = validator
        )
    }

    // ========================================
    // Successful Creation Tests
    // ========================================

    @Test
    fun execute_validAllFields_returnsSuccess() {
        // Arrange
        whenever(mockRepository.isPhoneUnique("(11) 99999-9999")).thenReturn(true)
        whenever(mockRepository.isEmailUnique("joao@example.com")).thenReturn(true)
        val savedPatient = Patient(
            id = 1L,
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = LocalDate.of(2024, 1, 15),
            registrationDate = LocalDate.now(),
            lastAppointmentDate = null,
            appointmentCount = null,
            amountDueNow = null
        )
        whenever(mockRepository.insert(name = "João Silva", phone = "(11) 99999-9999", email = "joao@example.com", initialConsultDate = LocalDate.of(2024, 1, 15))).thenReturn(1L)

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.Success)
        assertEquals(1L, (result as CreatePatientUseCase.CreatePatientResult.Success).patientId)
    }

    @Test
    fun execute_validMinimalFields_returnsSuccess() {
        // Arrange
        whenever(mockRepository.isEmailUnique("a@b.com")).thenReturn(true)
        whenever(mockRepository.insert(
            name = "AB",
            phone = null,
            email = "a@b.com",
            initialConsultDate = LocalDate.now()
        )).thenReturn(2L)

        // Act
        val result = useCase.execute(
            name = "AB",
            phone = null,
            email = "a@b.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.Success)
        assertEquals(2L, (result as CreatePatientUseCase.CreatePatientResult.Success).patientId)
    }

    @Test
    fun execute_onlyPhoneProvided_returnsSuccess() {
        // Arrange
        whenever(mockRepository.isPhoneUnique("11999999999")).thenReturn(true)
        whenever(mockRepository.insert(
            name = "Maria Silva",
            phone = "11999999999",
            email = null,
            initialConsultDate = LocalDate.now()
        )).thenReturn(3L)

        // Act
        val result = useCase.execute(
            name = "Maria Silva",
            phone = "11999999999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.Success)
    }

    // ========================================
    // Name Validation Tests
    // ========================================

    @Test
    fun execute_emptyName_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" })
    }

    @Test
    fun execute_nameWithOneChar_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "A",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" && it.message.contains("mínimo") })
    }

    @Test
    fun execute_nameTooLong_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "A".repeat(201),
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" && it.message.contains("exceder") })
    }

    @Test
    fun execute_nameOnlyNumbers_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "123456789",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" && it.message.contains("letra") })
    }

    @Test
    fun execute_nameWithSpecialChars_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva @#$",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" })
    }

    // ========================================
    // Phone Validation Tests
    // ========================================

    @Test
    fun execute_invalidPhoneFormat_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "123",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "phone" })
    }

    @Test
    fun execute_phoneTooShort_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "phone" && it.message.contains("dígito") })
    }

    @Test
    fun execute_phoneWithInvalidChars_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 9999#9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "phone" && it.message.contains("inválido") })
    }

    @Test
    fun execute_phoneAlreadyExists_returnsValidationError() {
        // Arrange
        whenever(mockRepository.isPhoneUnique("(11) 99999-9999")).thenReturn(false)

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "phone" && it.message.contains("já") })
    }

    // ========================================
    // Email Validation Tests
    // ========================================

    @Test
    fun execute_invalidEmailFormat_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "invalid-email",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "email" })
    }

    @Test
    fun execute_emailMissingAtSign_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joaoexample.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "email" && it.message.contains("inválido") })
    }

    @Test
    fun execute_emailTooLong_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "a".repeat(260) + "@example.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "email" })
    }

    @Test
    fun execute_emailAlreadyExists_returnsValidationError() {
        // Arrange
        whenever(mockRepository.isEmailUnique("joao@example.com")).thenReturn(false)

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "email" && it.message.contains("já") })
    }

    // ========================================
    // Contact Info Validation Tests
    // ========================================

    @Test
    fun execute_missingBothPhoneAndEmail_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = null,
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "contact" })
    }

    @Test
    fun execute_emptyPhoneAndEmail_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "",
            email = "",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "contact" })
    }

    // ========================================
    // Date Validation Tests
    // ========================================

    @Test
    fun execute_futureDate_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now().plusDays(1)
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "initialConsultDate" && it.message.contains("futuro") })
    }

    @Test
    fun execute_farFutureDate_returnsValidationError() {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now().plusYears(10)
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "initialConsultDate" })
    }

    @Test
    fun execute_todayDate_isValid() {
        // Arrange
        whenever(mockRepository.isPhoneUnique("(11) 99999-9999")).thenReturn(true)
        whenever(mockRepository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )).thenReturn(1L)

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.Success)
    }

    @Test
    fun execute_pastDate_isValid() {
        // Arrange
        whenever(mockRepository.isPhoneUnique("(11) 99999-9999")).thenReturn(true)
        whenever(mockRepository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.of(2024, 1, 1)
        )).thenReturn(1L)

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.of(2024, 1, 1)
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.Success)
    }

    // ========================================
    // Multiple Errors Tests
    // ========================================

    @Test
    fun execute_multipleErrors_returnsAll() {
        // Act
        val result = useCase.execute(
            name = "",  // Error: required
            phone = "",  // Will combine with email
            email = "",  // Error: contact required
            initialConsultDate = LocalDate.now().plusDays(1)  // Error: future date
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertEquals(3, errors.size)  // name, contact, date
        assertTrue(errors.any { it.field == "name" })
        assertTrue(errors.any { it.field == "contact" })
        assertTrue(errors.any { it.field == "initialConsultDate" })
    }

    @Test
    fun execute_formattingErrors_allReturnedTogether() {
        // Act - name too short AND contact missing
        val result = useCase.execute(
            name = "A",
            phone = null,
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientUseCase.CreatePatientResult.ValidationError).errors
        assertTrue(errors.size >= 2)  // name (too short) and contact
    }

    // ========================================
    // Integration Tests (Validation + Repository)
    // ========================================

    @Test
    fun execute_validationPassesButRepositoryFails_returnsError() {
        // Arrange
        whenever(mockRepository.isPhoneUnique("(11) 99999-9999")).thenReturn(true)
        whenever(mockRepository.isEmailUnique("joao@example.com")).thenReturn(true)
        whenever(mockRepository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.now()
        )).thenThrow(RuntimeException("Database connection failed"))

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.Error)
    }

    @Test
    fun execute_errorMessageIsDescriptive() {
        // Arrange
        whenever(mockRepository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.now()
        )).thenThrow(RuntimeException("Unique constraint failed on phone"))

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientUseCase.CreatePatientResult.Error)
        assertTrue((result as CreatePatientUseCase.CreatePatientResult.Error).message.isNotEmpty())
    }
}
