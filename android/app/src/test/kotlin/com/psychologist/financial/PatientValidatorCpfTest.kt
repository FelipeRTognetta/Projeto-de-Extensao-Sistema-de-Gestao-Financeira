package com.psychologist.financial

import com.psychologist.financial.domain.validation.PatientValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PatientValidator.validateCpf()
 *
 * TDD: These tests are written BEFORE the implementation.
 * All tests should FAIL until validateCpf() is added to PatientValidator.
 *
 * CPF Validation Rules:
 * - null or empty → valid (CPF is optional)
 * - Formatted input (XXX.XXX.XXX-XX) → strip mask then validate
 * - Must be exactly 11 digits after stripping
 * - All-same-digit sequences (e.g., "11111111111") → invalid
 * - Must pass Brazilian modulo-11 check digit algorithm
 *
 * Modulo-11 algorithm:
 * 1. Strip non-digits
 * 2. If length != 11 → error
 * 3. If all digits equal → error
 * 4. First check digit (pos 9):
 *    sum = digits[0..8] * weights [10..2]; remainder = sum % 11
 *    check = if (remainder < 2) 0 else (11 - remainder)
 * 5. Second check digit (pos 10):
 *    sum = digits[0..9] * weights [11..2]; remainder = sum % 11
 *    check = if (remainder < 2) 0 else (11 - remainder)
 *
 * Total: 9 test cases
 */
class PatientValidatorCpfTest {

    private lateinit var validator: PatientValidator

    @Before
    fun setUp() {
        validator = PatientValidator()
    }

    // ========================================
    // Optional Field Tests (null/empty → valid)
    // ========================================

    @Test
    fun validateCpf_null_returnsEmpty() {
        val errors = validator.validateCpf(null)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateCpf_emptyString_returnsEmpty() {
        val errors = validator.validateCpf("")
        assertEquals(0, errors.size)
    }

    // ========================================
    // Valid CPF Tests
    // ========================================

    @Test
    fun validateCpf_validRawDigits_returnsEmpty() {
        // Known valid CPF: 123.456.789-09
        val errors = validator.validateCpf("12345678909")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateCpf_validFormattedWithMask_returnsEmpty() {
        // Same CPF with mask → should strip and validate
        val errors = validator.validateCpf("123.456.789-09")
        assertEquals(0, errors.size)
    }

    @Test
    fun validateCpf_anotherValidCpf_returnsEmpty() {
        // Another known valid CPF: 529.982.247-25
        val errors = validator.validateCpf("52998224725")
        assertEquals(0, errors.size)
    }

    // ========================================
    // Invalid CPF Tests
    // ========================================

    @Test
    fun validateCpf_tooShort_returnsError() {
        // Only 10 digits after stripping
        val errors = validator.validateCpf("1234567890")
        assertTrue(errors.isNotEmpty())
        assertEquals("cpf", errors[0].field)
    }

    @Test
    fun validateCpf_allSameDigits_returnsError() {
        // "11111111111" → all same → automatically invalid
        val errors = validator.validateCpf("11111111111")
        assertTrue(errors.isNotEmpty())
        assertEquals("cpf", errors[0].field)
    }

    @Test
    fun validateCpf_allZeros_returnsError() {
        // "00000000000" → all same → automatically invalid
        val errors = validator.validateCpf("00000000000")
        assertTrue(errors.isNotEmpty())
        assertEquals("cpf", errors[0].field)
    }

    @Test
    fun validateCpf_wrongCheckDigit_returnsError() {
        // "12345678919" → first check digit should be 0, not 1
        val errors = validator.validateCpf("12345678919")
        assertTrue(errors.isNotEmpty())
        assertEquals("cpf", errors[0].field)
    }
}
