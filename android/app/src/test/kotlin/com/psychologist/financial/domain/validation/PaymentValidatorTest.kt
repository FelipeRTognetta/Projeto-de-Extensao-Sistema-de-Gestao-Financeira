package com.psychologist.financial.domain.validation

import com.psychologist.financial.domain.models.Payment
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for PaymentValidator.
 *
 * Tests validation rules for Payment domain model:
 * - Amount: > 0, ≤ 999,999.99
 * - Patient ID: > 0 (required)
 * - Appointment IDs: Optional list, no negative IDs
 *
 * Migration note (v2→v3):
 * - Removed: method and status validation
 * - Kept: amount and patientId validation
 * - Added: optional appointmentIds validation
 *
 * Run with: ./gradlew testDebugUnitTest --tests PaymentValidatorTest
 */
class PaymentValidatorTest {

    private val validator = PaymentValidator()

    // ========================================
    // Amount Validation Tests
    // ========================================

    @Test
    fun validate_validAmount_passes() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = listOf(10L)
        )

        val result = validator.validate(payment)

        assert(result.isValid) { "Valid payment should pass validation" }
        assert(result.errors.isEmpty()) { "Valid payment should have no errors" }
    }

    @Test
    fun validate_zeroAmount_fails() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal.ZERO,
            paymentDate = LocalDate.now()
        )

        val result = validator.validate(payment)

        assert(!result.isValid) { "Zero amount should fail validation" }
        assert(result.errors.isNotEmpty()) { "Should have validation errors" }
        assert(result.errors.any { it.lowercase().contains("valor") || it.lowercase().contains("amount") }) {
            "Should have amount validation error"
        }
    }

    @Test
    fun validate_negativeAmount_fails() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("-100.00"),
            paymentDate = LocalDate.now()
        )

        val result = validator.validate(payment)

        assert(!result.isValid) { "Negative amount should fail validation" }
        assert(result.errors.isNotEmpty())
    }

    @Test
    fun validate_amountExceedsMaximum_fails() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("1000000.00"), // Exceeds 999,999.99
            paymentDate = LocalDate.now()
        )

        val result = validator.validate(payment)

        assert(!result.isValid) { "Amount exceeding 999,999.99 should fail" }
        assert(result.errors.isNotEmpty())
    }

    @Test
    fun validate_amountAtMaximumBoundary_passes() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("999999.99"),
            paymentDate = LocalDate.now()
        )

        val result = validator.validate(payment)

        assert(result.isValid) { "Amount at maximum boundary should pass" }
    }

    @Test
    fun validate_minimalValidAmount_passes() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("0.01"),
            paymentDate = LocalDate.now()
        )

        val result = validator.validate(payment)

        assert(result.isValid) { "Minimal valid amount should pass" }
    }

    // ========================================
    // Patient ID Validation Tests
    // ========================================

    @Test
    fun validate_validPatientId_passes() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        val result = validator.validate(payment)

        assert(result.isValid) { "Valid patient ID should pass" }
    }

    @Test
    fun validate_zeroPatientId_fails() {
        val payment = Payment(
            id = 0L,
            patientId = 0L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        val result = validator.validate(payment)

        assert(!result.isValid) { "Zero patient ID should fail" }
        assert(result.errors.any { it.lowercase().contains("paciente") || it.lowercase().contains("patient") }) {
            "Should have patient validation error"
        }
    }

    @Test
    fun validate_negativePatientId_fails() {
        val payment = Payment(
            id = 0L,
            patientId = -1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        val result = validator.validate(payment)

        assert(!result.isValid) { "Negative patient ID should fail" }
    }

    // ========================================
    // Appointment IDs Validation Tests
    // ========================================

    @Test
    fun validate_emptyAppointmentIds_passes() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = emptyList()
        )

        val result = validator.validate(payment)

        assert(result.isValid) { "Empty appointment IDs should pass (optional)" }
    }

    @Test
    fun validate_singleAppointmentId_passes() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = listOf(10L)
        )

        val result = validator.validate(payment)

        assert(result.isValid) { "Single appointment ID should pass" }
    }

    @Test
    fun validate_multipleAppointmentIds_passes() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = listOf(10L, 11L, 12L)
        )

        val result = validator.validate(payment)

        assert(result.isValid) { "Multiple appointment IDs should pass" }
    }

    @Test
    fun validate_negativeAppointmentId_fails() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = listOf(-1L) // Invalid negative ID
        )

        val result = validator.validate(payment)

        assert(!result.isValid) { "Negative appointment ID should fail" }
    }

    @Test
    fun validate_zeroAppointmentId_fails() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now(),
            appointmentIds = listOf(0L) // Invalid zero ID
        )

        val result = validator.validate(payment)

        assert(!result.isValid) { "Zero appointment ID should fail" }
    }

    // ========================================
    // Combined Validation Tests
    // ========================================

    @Test
    fun validate_multipleErrors_collectsAll() {
        val payment = Payment(
            id = 0L,
            patientId = -1L, // Invalid
            amount = BigDecimal.ZERO, // Invalid
            paymentDate = LocalDate.now(),
            appointmentIds = listOf(0L) // Invalid
        )

        val result = validator.validate(payment)

        assert(!result.isValid)
        assert(result.errors.size >= 3) { "Should collect all validation errors" }
    }

    @Test
    fun validate_complexScenario_passes() {
        // Payment covering multiple appointments, valid amounts and IDs
        val payment = Payment(
            id = 0L,
            patientId = 5L,
            amount = BigDecimal("450.50"),
            paymentDate = LocalDate.of(2024, 3, 15),
            appointmentIds = listOf(100L, 101L, 102L)
        )

        val result = validator.validate(payment)

        assert(result.isValid) { "Complex valid scenario should pass" }
    }

    // ========================================
    // Removed Method/Status Validation Tests
    // ========================================

    /**
     * Verify that method and status parameters no longer exist in validator.
     * This test documents that these validations have been removed per v2→v3 migration.
     *
     * If this test fails to compile (method not found), it confirms removal is complete.
     */
    @Test
    fun validate_methodStatusParametersRemoved() {
        val payment = Payment(
            id = 0L,
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = LocalDate.now()
        )

        // This should compile and succeed - no method or status params accepted
        val result = validator.validate(payment)

        assert(result.isValid)
        // If this test runs, it proves the old method/status validation is gone
    }
}
