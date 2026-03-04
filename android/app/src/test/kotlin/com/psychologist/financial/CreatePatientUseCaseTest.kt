package com.psychologist.financial

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.usecases.CreatePatientResult
import com.psychologist.financial.domain.usecases.CreatePatientUseCase
import com.psychologist.financial.domain.validation.PatientValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Unit tests for CreatePatientUseCase
 *
 * Coverage:
 * - Valid patient creation
 * - Validation errors (name, phone, email, contact, date)
 * - Repository exception handling
 * - Database insertion success
 * - Error handling
 *
 * Total: 20 test cases
 * Tests the boundary between validation layer and data persistence
 */
class CreatePatientUseCaseTest {

    @Mock
    private lateinit var mockPatientRepository: PatientRepository

    private lateinit var patientValidator: PatientValidator
    private lateinit var useCase: CreatePatientUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        patientValidator = PatientValidator()
        useCase = CreatePatientUseCase(
            patientRepository = mockPatientRepository,
            patientValidator = patientValidator
        )
    }

    // ========================================
    // Successful Creation Tests
    // ========================================

    @Test
    fun execute_validAllFields_returnsSuccess() = runTest {
        // Arrange
        whenever(mockPatientRepository.createPatient(any())).thenReturn(1L)

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Assert
        assertTrue(result is CreatePatientResult.Success)
        assertEquals(1L, (result as CreatePatientResult.Success).patientId)
    }

    @Test
    fun execute_validMinimalFields_returnsSuccess() = runTest {
        // Arrange
        whenever(mockPatientRepository.createPatient(any())).thenReturn(2L)

        // Act
        val result = useCase.execute(
            name = "AB",
            phone = null,
            email = "a@b.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.Success)
        assertEquals(2L, (result as CreatePatientResult.Success).patientId)
    }

    @Test
    fun execute_onlyPhoneProvided_returnsSuccess() = runTest {
        // Arrange
        whenever(mockPatientRepository.createPatient(any())).thenReturn(3L)

        // Act
        val result = useCase.execute(
            name = "Maria Silva",
            phone = "11999999999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.Success)
    }

    // ========================================
    // Name Validation Tests
    // ========================================

    @Test
    fun execute_emptyName_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" })
    }

    @Test
    fun execute_nameWithOneChar_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "A",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" && it.message.contains("mínimo") })
    }

    @Test
    fun execute_nameTooLong_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "A".repeat(201),
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" && it.message.contains("exceder") })
    }

    @Test
    fun execute_nameOnlyNumbers_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "123456789",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "name" && it.message.contains("letra") })
    }

    // ========================================
    // Phone Validation Tests
    // ========================================

    @Test
    fun execute_invalidPhoneFormat_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "123",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "phone" })
    }

    @Test
    fun execute_phoneTooShort_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "phone" && it.message.contains("dígito") })
    }

    // ========================================
    // Email Validation Tests
    // ========================================

    @Test
    fun execute_invalidEmailFormat_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "invalid-email",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "email" })
    }

    @Test
    fun execute_emailMissingAtSign_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joaoexample.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "email" && it.message.contains("inválido") })
    }

    @Test
    fun execute_emailTooLong_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "a".repeat(260) + "@example.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "email" })
    }

    // ========================================
    // Contact Info Validation Tests
    // ========================================

    @Test
    fun execute_missingBothPhoneAndEmail_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = null,
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "contact" })
    }

    @Test
    fun execute_emptyPhoneAndEmail_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "",
            email = "",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "contact" })
    }

    // ========================================
    // Date Validation Tests
    // ========================================

    @Test
    fun execute_futureDate_returnsValidationError() = runTest {
        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now().plusDays(1)
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.any { it.field == "initialConsultDate" && it.message.contains("futuro") })
    }

    @Test
    fun execute_todayDate_isValid() = runTest {
        // Arrange
        whenever(mockPatientRepository.createPatient(any())).thenReturn(1L)

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.Success)
    }

    @Test
    fun execute_pastDate_isValid() = runTest {
        // Arrange
        whenever(mockPatientRepository.createPatient(any())).thenReturn(1L)

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.of(2024, 1, 1)
        )

        // Assert
        assertTrue(result is CreatePatientResult.Success)
    }

    // ========================================
    // Multiple Errors Tests
    // ========================================

    @Test
    fun execute_multipleErrors_returnsAll() = runTest {
        // Act
        val result = useCase.execute(
            name = "",  // Error: required
            phone = "",  // Will combine with email
            email = "",  // Error: contact required
            initialConsultDate = LocalDate.now().plusDays(1)  // Error: future date
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertEquals(3, errors.size)  // name, contact, date
        assertTrue(errors.any { it.field == "name" })
        assertTrue(errors.any { it.field == "contact" })
        assertTrue(errors.any { it.field == "initialConsultDate" })
    }

    @Test
    fun execute_formattingErrors_allReturnedTogether() = runTest {
        // Act - name too short AND contact missing
        val result = useCase.execute(
            name = "A",
            phone = null,
            email = null,
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
        val errors = (result as CreatePatientResult.ValidationError).errors
        assertTrue(errors.size >= 2)  // name (too short) and contact
    }

    // ========================================
    // Repository Exception Tests
    // ========================================

    @Test
    fun execute_repositoryThrowsIllegalArgument_returnsValidationError() = runTest {
        // Arrange
        whenever(mockPatientRepository.createPatient(any()))
            .thenThrow(IllegalArgumentException("Phone already in use"))

        // Act
        val result = useCase.execute(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.now()
        )

        // Assert
        assertTrue(result is CreatePatientResult.ValidationError)
    }
}
