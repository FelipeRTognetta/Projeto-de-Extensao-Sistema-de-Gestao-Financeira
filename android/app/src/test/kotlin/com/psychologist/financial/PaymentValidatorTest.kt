package com.psychologist.financial

import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.validation.DecimalValidator
import com.psychologist.financial.domain.validation.PaymentValidator
import com.psychologist.financial.domain.validation.formatPaymentAmount
import com.psychologist.financial.domain.validation.parsePaymentAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for PaymentValidator
 *
 * Coverage:
 * - validate(payment): valid payment, amount boundaries, invalid patientId, invalid appointmentIds
 * - formatPaymentAmount(): currency formatting
 * - parsePaymentAmount(): various string formats and edge cases
 * - DecimalValidator: currency and percentage validation
 *
 * Total: 20+ test cases
 *
 * Migration note (v2→v3):
 * - Removed: status, paymentMethod, and date validation (not fields in Payment model)
 * - validate() now only checks amount, patientId, and appointmentIds
 */
class PaymentValidatorTest {

    private lateinit var paymentValidator: PaymentValidator
    private lateinit var decimalValidator: DecimalValidator

    @Before
    fun setUp() {
        paymentValidator = PaymentValidator()
        decimalValidator = DecimalValidator()
    }

    // ========================================
    // validate() Tests — happy path
    // ========================================

    @Test
    fun validate_validPayment_returnsIsValidTrue() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        val result = paymentValidator.validate(payment)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun validate_validPaymentWithAppointments_returnsIsValidTrue() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("300.00"),
            paymentDate = LocalDate.now().minusDays(1),
            appointmentIds = listOf(10L, 11L)
        )

        val result = paymentValidator.validate(payment)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_maximumAmount_returnsIsValidTrue() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("999999.99"),
            paymentDate = LocalDate.now()
        )

        assertTrue(paymentValidator.validate(payment).isValid)
    }

    @Test
    fun validate_minimumAmount_returnsIsValidTrue() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("0.01"),
            paymentDate = LocalDate.now()
        )

        assertTrue(paymentValidator.validate(payment).isValid)
    }

    // ========================================
    // validate() Tests — amount errors
    // ========================================

    @Test
    fun validate_zeroAmount_returnsInvalidWithError() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal.ZERO,
            paymentDate = LocalDate.now()
        )

        val result = paymentValidator.validate(payment)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("maior que zero") })
    }

    @Test
    fun validate_negativeAmount_returnsInvalidWithError() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("-50.00"),
            paymentDate = LocalDate.now()
        )

        val result = paymentValidator.validate(payment)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("maior que zero") })
    }

    @Test
    fun validate_excessiveAmount_returnsInvalidWithError() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("1000000.00"),
            paymentDate = LocalDate.now()
        )

        val result = paymentValidator.validate(payment)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("exceder") })
    }

    // ========================================
    // validate() Tests — patientId errors
    // ========================================

    @Test
    fun validate_zeroPatientId_returnsInvalidWithError() {
        val payment = Payment(
            id = 0L,
            patientId = 0L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        val result = paymentValidator.validate(payment)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Paciente") })
    }

    @Test
    fun validate_negativePatientId_returnsInvalidWithError() {
        val payment = Payment(
            id = 0L,
            patientId = -1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        val result = paymentValidator.validate(payment)

        assertFalse(result.isValid)
    }

    // ========================================
    // validate() Tests — appointmentIds errors
    // ========================================

    @Test
    fun validate_invalidAppointmentId_returnsInvalidWithError() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = listOf(0L, -1L)
        )

        val result = paymentValidator.validate(payment)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("inválidos") })
    }

    @Test
    fun validate_emptyAppointmentList_returnsIsValidTrue() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = emptyList()
        )

        assertTrue(paymentValidator.validate(payment).isValid)
    }

    @Test
    fun validate_multipleErrors_returnsAllErrors() {
        val payment = Payment(
            id = 0L,
            patientId = 0L,
            amount = BigDecimal.ZERO,
            paymentDate = LocalDate.now(),
            appointmentIds = listOf(-1L)
        )

        val result = paymentValidator.validate(payment)

        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 2)
    }

    // ========================================
    // formatPaymentAmount() Tests
    // ========================================

    @Test
    fun formatPaymentAmount_validAmount_containsRSymbol() {
        val formatted = formatPaymentAmount(BigDecimal("150.00"))
        assertTrue(formatted.contains("R$"))
    }

    @Test
    fun formatPaymentAmount_largeAmount_containsThousandsSeparator() {
        val formatted = formatPaymentAmount(BigDecimal("1500.00"))
        assertTrue(formatted.contains("1.500,00"))
    }

    @Test
    fun formatPaymentAmount_smallAmount_formatsCorrectly() {
        val formatted = formatPaymentAmount(BigDecimal("0.50"))
        assertTrue(formatted.contains("0,50"))
    }

    // ========================================
    // parsePaymentAmount() Tests
    // ========================================

    @Test
    fun parsePaymentAmount_brazilianFormat_parsesCorrectly() {
        val amount = parsePaymentAmount("1.500,00")
        assertEquals(BigDecimal("1500.00"), amount)
    }

    @Test
    fun parsePaymentAmount_usFormat_parsesCorrectly() {
        val amount = parsePaymentAmount("1,500.00")
        assertEquals(BigDecimal("1500.00"), amount)
    }

    @Test
    fun parsePaymentAmount_simpleFormat_parsesCorrectly() {
        val amount = parsePaymentAmount("150.00")
        assertEquals(BigDecimal("150.00"), amount)
    }

    @Test
    fun parsePaymentAmount_invalidFormat_returnsNull() {
        val amount = parsePaymentAmount("abc")
        assertNull(amount)
    }

    @Test
    fun parsePaymentAmount_nullInput_returnsNull() {
        val amount = parsePaymentAmount(null)
        assertNull(amount)
    }

    @Test
    fun parsePaymentAmount_emptyString_returnsNull() {
        val amount = parsePaymentAmount("")
        assertNull(amount)
    }

    // ========================================
    // DecimalValidator Integration Tests
    // ========================================

    @Test
    fun decimalValidator_validateCurrencyAmount_validAmount_returnsEmpty() {
        val errors = decimalValidator.validateCurrencyAmount(BigDecimal("150.00"))
        assertEquals(0, errors.size)
    }

    @Test
    fun decimalValidator_validateCurrencyAmount_negativeAmount_returnsError() {
        val errors = decimalValidator.validateCurrencyAmount(BigDecimal("-50.00"))
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.message.contains("negativo") })
    }

    @Test
    fun decimalValidator_validateCurrencyAmount_nullAmount_returnsError() {
        val errors = decimalValidator.validateCurrencyAmount(null)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("vazio"))
    }

    @Test
    fun decimalValidator_normalizeCurrencyAmount_roundsCorrectly() {
        val normalized = decimalValidator.normalizeCurrencyAmount(BigDecimal("150.999"))
        assertEquals(BigDecimal("151.00"), normalized)
    }

    @Test
    fun decimalValidator_normalizeCurrencyAmount_alreadyNormalized_returnsAsIs() {
        val amount = BigDecimal("150.00")
        val normalized = decimalValidator.normalizeCurrencyAmount(amount)
        assertEquals(amount, normalized)
    }

    @Test
    fun decimalValidator_validatePercentage_validPercentage_returnsEmpty() {
        val errors = decimalValidator.validatePercentage(BigDecimal("75.50"))
        assertEquals(0, errors.size)
    }

    @Test
    fun decimalValidator_validatePercentage_exceedsMax_returnsError() {
        val errors = decimalValidator.validatePercentage(BigDecimal("150.00"))
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("maior que 100"))
    }
}
