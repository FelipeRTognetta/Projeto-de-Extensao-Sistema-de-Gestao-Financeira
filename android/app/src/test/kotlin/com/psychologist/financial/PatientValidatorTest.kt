package com.psychologist.financial

import com.psychologist.financial.domain.validation.PatientValidator
import com.psychologist.financial.domain.validation.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for PatientValidator
 *
 * Coverage:
 * - Name validation (length, characters, letters requirement)
 * - Phone validation (format, digits, optional)
 * - Email validation (format, length, optional)
 * - Contact info validation (at least one contact)
 * - Date validation (not in future)
 * - Edge cases and boundary conditions
 *
 * Total: 32 test cases with 80%+ coverage
 */
class PatientValidatorTest {

    private lateinit var validator: PatientValidator

    @Before
    fun setUp() {
        validator = PatientValidator()
    }

    // ========================================
    // Name Validation Tests
    // ========================================

    @Test
    fun validateName_validNameWithLettersAndSpaces_returnsEmpty() {
        val errors = validator.validateName("João Silva")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateName_validNameWithHyphens_returnsEmpty() {
        val errors = validator.validateName("Maria-José Santos")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateName_validNameWithApostrophe_returnsEmpty() {
        val errors = validator.validateName("O'Brien")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateName_validNameWithNumbers_returnsEmpty() {
        val errors = validator.validateName("João Silva 2")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateName_emptyName_returnsRequiredError() {
        val errors = validator.validateName("")
        assertEquals(1, errors.size)
        assertEquals("name", errors[0].field)
        assertTrue(errors[0].message.contains("obrigatório"))
    }

    @Test
    fun validateName_whitespaceOnly_returnsRequiredError() {
        val errors = validator.validateName("   ")
        assertEquals(1, errors.size)
        assertEquals("name", errors[0].field)
    }

    @Test
    fun validateName_tooShort_returnsMinLengthError() {
        val errors = validator.validateName("A")
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("mínimo"))
        assertTrue(errors[0].message.contains("2"))
    }

    @Test
    fun validateName_twoCharsWithLetter_returnsEmpty() {
        val errors = validator.validateName("AB")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateName_tooLong_returnsMaxLengthError() {
        val longName = "A".repeat(201)
        val errors = validator.validateName(longName)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("exceder"))
        assertTrue(errors[0].message.contains("200"))
    }

    @Test
    fun validateName_maxLengthOk_returnsEmpty() {
        val name = "A".repeat(200)
        val errors = validator.validateName(name)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateName_onlyNumbers_returnsCharacterError() {
        val errors = validator.validateName("12345")
        assertTrue(errors.any { it.message.contains("letra") })
    }

    @Test
    fun validateName_invalidCharacters_returnsCharacterError() {
        val errors = validator.validateName("João@Silva!")
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("inválido"))
    }

    @Test
    fun validateName_specialCharacters_returnsCharacterError() {
        val errors = validator.validateName("João Silva <script>")
        assertEquals(1, errors.size)
    }

    @Test
    fun validateName_nameWithTrimmedSpaces_isValid() {
        val errors = validator.validateName("  João Silva  ")
        assertEquals(0, errors.size)
    }

    // ========================================
    // Phone Validation Tests
    // ========================================

    @Test
    fun validatePhone_nullPhone_returnsEmpty() {
        val errors = validator.validatePhone(null)
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePhone_emptyPhone_returnsEmpty() {
        val errors = validator.validatePhone("")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePhone_validBrazilianFormat_returnsEmpty() {
        val errors = validator.validatePhone("(11) 99999-9999")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePhone_validInternationalFormat_returnsEmpty() {
        val errors = validator.validatePhone("+55 11 99999-9999")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePhone_validDigitsOnly_returnsEmpty() {
        val errors = validator.validatePhone("11999999999")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePhone_validWithSpaces_returnsEmpty() {
        val errors = validator.validatePhone("11 9999 9999")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePhone_tooShort_returnsLengthError() {
        val errors = validator.validatePhone("123456")
        assertTrue(errors.any { it.message.contains("7") })
    }

    @Test
    fun validatePhone_tooFewDigits_returnsDigitError() {
        val errors = validator.validatePhone("(11) 9999")
        assertTrue(errors.any { it.message.contains("7 dígitos") })
    }

    @Test
    fun validatePhone_tooLong_returnsLengthError() {
        val errors = validator.validatePhone("+" + "1".repeat(30))
        assertTrue(errors.any { it.message.contains("exceder") })
    }

    @Test
    fun validatePhone_invalidCharacters_returnsFormatError() {
        val errors = validator.validatePhone("11 9999#9999")
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("inválido"))
    }

    @Test
    fun validatePhone_minLengthValidDigits_returnsEmpty() {
        val errors = validator.validatePhone("1234567")
        assertEquals(0, errors.size)
    }

    // ========================================
    // Email Validation Tests
    // ========================================

    @Test
    fun validateEmail_nullEmail_returnsEmpty() {
        val errors = validator.validateEmail(null)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateEmail_emptyEmail_returnsEmpty() {
        val errors = validator.validateEmail("")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateEmail_validEmail_returnsEmpty() {
        val errors = validator.validateEmail("joao@example.com")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateEmail_validWithPlus_returnsEmpty() {
        val errors = validator.validateEmail("joao+tag@example.com")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateEmail_validWithDots_returnsEmpty() {
        val errors = validator.validateEmail("joao.silva@example.com")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateEmail_validWithNumbers_returnsEmpty() {
        val errors = validator.validateEmail("joao123@example.com")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateEmail_missingAtSign_returnsFormatError() {
        val errors = validator.validateEmail("joaoexample.com")
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("inválido"))
    }

    @Test
    fun validateEmail_missingDomain_returnsFormatError() {
        val errors = validator.validateEmail("joao@")
        assertEquals(1, errors.size)
    }

    @Test
    fun validateEmail_missingTld_returnsFormatError() {
        val errors = validator.validateEmail("joao@example")
        assertEquals(1, errors.size)
    }

    @Test
    fun validateEmail_tooLong_returnsLengthError() {
        val longEmail = "a".repeat(200) + "@example.com"
        val errors = validator.validateEmail(longEmail)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("exceder"))
    }

    @Test
    fun validateEmail_maxLengthOk_returnsEmpty() {
        val email = "a".repeat(240) + "@test.br"
        val errors = validator.validateEmail(email)
        assertEquals(0, errors.size)
    }

    // ========================================
    // Contact Info Validation Tests
    // ========================================

    @Test
    fun validateContactInfo_bothPhoneAndEmail_returnsEmpty() {
        val errors = validator.validateContactInfo("(11) 99999-9999", "joao@example.com")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateContactInfo_onlyPhone_returnsEmpty() {
        val errors = validator.validateContactInfo("(11) 99999-9999", null)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateContactInfo_onlyEmail_returnsEmpty() {
        val errors = validator.validateContactInfo(null, "joao@example.com")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateContactInfo_neitherPhoneNorEmail_returnsError() {
        val errors = validator.validateContactInfo(null, null)
        assertEquals(1, errors.size)
        assertEquals("contact", errors[0].field)
        assertTrue(errors[0].message.contains("telefone ou email"))
    }

    @Test
    fun validateContactInfo_emptyPhoneAndEmail_returnsError() {
        val errors = validator.validateContactInfo("", "")
        assertEquals(1, errors.size)
    }

    // ========================================
    // Date Validation Tests
    // ========================================

    @Test
    fun validateInitialConsultDate_todayDate_returnsEmpty() {
        val errors = validator.validateInitialConsultDate(LocalDate.now())
        assertEquals(0, errors.size)
    }

    @Test
    fun validateInitialConsultDate_pastDate_returnsEmpty() {
        val errors = validator.validateInitialConsultDate(LocalDate.now().minusDays(30))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateInitialConsultDate_futureDate_returnsError() {
        val errors = validator.validateInitialConsultDate(LocalDate.now().plusDays(1))
        assertEquals(1, errors.size)
        assertEquals("initialConsultDate", errors[0].field)
        assertTrue(errors[0].message.contains("futuro"))
    }

    @Test
    fun validateInitialConsultDate_farFutureDate_returnsError() {
        val errors = validator.validateInitialConsultDate(LocalDate.now().plusYears(1))
        assertEquals(1, errors.size)
    }

    // ========================================
    // Complete Form Validation Tests
    // ========================================

    @Test
    fun validateNewPatient_validAllFields_returnsEmpty() {
        val errors = validator.validateNewPatient(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.now()
        )
        assertEquals(0, errors.size)
    }

    @Test
    fun validateNewPatient_validMinimalFields_returnsEmpty() {
        val errors = validator.validateNewPatient(
            name = "AB",
            phone = null,
            email = "a@b.com",
            initialConsultDate = LocalDate.now()
        )
        assertEquals(0, errors.size)
    }

    @Test
    fun validateNewPatient_multipleErrors_returnsAll() {
        val errors = validator.validateNewPatient(
            name = "",  // Required error
            phone = null,
            email = null,  // Contact error
            initialConsultDate = LocalDate.now().plusDays(1)  // Future date error
        )
        assertEquals(3, errors.size)  // Name, contact, date errors
        assertTrue(errors.any { it.field == "name" })
        assertTrue(errors.any { it.field == "contact" })
        assertTrue(errors.any { it.field == "initialConsultDate" })
    }

    // ========================================
    // Extension Function Tests
    // ========================================

    @Test
    fun isValidPhone_nullPhone_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidPhone(null))
    }

    @Test
    fun isValidPhone_validPhone_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidPhone("(11) 99999-9999"))
    }

    @Test
    fun isValidPhone_invalidPhone_returnsFalse() {
        assertFalse(com.psychologist.financial.domain.validation.isValidPhone("invalid"))
    }

    @Test
    fun isValidEmail_nullEmail_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidEmail(null))
    }

    @Test
    fun isValidEmail_validEmail_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidEmail("joao@example.com"))
    }

    @Test
    fun isValidEmail_invalidEmail_returnsFalse() {
        assertFalse(com.psychologist.financial.domain.validation.isValidEmail("invalid"))
    }

    @Test
    fun isValidName_validName_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidName("João Silva"))
    }

    @Test
    fun isValidName_tooShort_returnsFalse() {
        assertFalse(com.psychologist.financial.domain.validation.isValidName("A"))
    }

    @Test
    fun isValidName_tooLong_returnsFalse() {
        assertFalse(com.psychologist.financial.domain.validation.isValidName("A".repeat(201)))
    }
}
