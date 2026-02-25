package com.psychologist.financial.domain.validation

import android.util.Log
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Validator for BigDecimal constraints
 *
 * Enforces strict rules for financial decimal values:
 * - Non-negative amounts (for most use cases)
 * - Precision constraints (decimal places)
 * - Scale consistency
 * - Maximum value limits
 * - Rounding safety
 *
 * Purpose:
 * - Prevent floating-point precision errors
 * - Enforce currency-specific constraints (2 decimal places for Brazilian Real)
 * - Validate decimal operations before database persistence
 * - Support form input validation and sanitization
 *
 * Architecture:
 * - Validation layer (domain)
 * - Independent of UI and database
 * - Testable without framework
 * - Reusable across all decimal-based fields
 *
 * Usage:
 * ```kotlin
 * val validator = DecimalValidator()
 *
 * // Validate payment amount
 * val amountErrors = validator.validateCurrencyAmount(BigDecimal("150.00"))
 * if (amountErrors.isNotEmpty()) {
 *     showErrors(amountErrors)
 * }
 *
 * // Normalize and validate
 * val normalizedAmount = validator.normalizeCurrencyAmount(BigDecimal("150.999"))
 * // Returns: BigDecimal("151.00") rounded to 2 decimals
 * ```
 *
 * Validation Rules by Type:
 * - Currency Amount: Non-negative, 2 decimal places max, no NaN/Infinity
 * - Rate/Percentage: 0-100 range, 2 decimal places max
 * - Quantity: Non-negative, 0 decimal places for whole units
 * - Balance: Allow negative (representing debt), 2 decimal places max
 *
 * Error Messages:
 * - Portuguese localization for user display
 * - Clear, specific feedback
 * - Field-specific errors
 */
class DecimalValidator {

    private companion object {
        private const val TAG = "DecimalValidator"

        // Currency constraints (Brazilian Real)
        private const val CURRENCY_DECIMAL_PLACES = 2
        private const val CURRENCY_MIN_VALUE = "0.00"
        private const val CURRENCY_MAX_VALUE = "999999999.99"

        // Percentage constraints
        private const val PERCENTAGE_DECIMAL_PLACES = 2
        private const val PERCENTAGE_MIN = 0
        private const val PERCENTAGE_MAX = 100

        // Quantity constraints
        private const val QUANTITY_DECIMAL_PLACES = 0
    }

    /**
     * Validate currency amount (for payments, balances, etc.)
     *
     * Rules:
     * - Not null
     * - Not NaN or Infinity
     * - Must be non-negative
     * - Max 2 decimal places
     * - Within range: 0.00 to 999999999.99
     *
     * @param amount Amount to validate
     * @param allowNegative Allow negative values (for balance/debt)
     * @return List of ValidationError (empty if valid)
     */
    fun validateCurrencyAmount(
        amount: BigDecimal?,
        allowNegative: Boolean = false
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Null check
        if (amount == null) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor não pode ser vazio"
            ))
            return errors
        }

        // NaN/Infinity check
        if (amount.isNaN() || amount.isInfinite()) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor inválido"
            ))
            return errors
        }

        // Non-negative check (unless explicitly allowed)
        if (!allowNegative && amount < BigDecimal.ZERO) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor não pode ser negativo"
            ))
        }

        // Decimal places check
        val scale = amount.scale()
        if (scale > CURRENCY_DECIMAL_PLACES) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor pode ter no máximo $CURRENCY_DECIMAL_PLACES casas decimais"
            ))
        }

        // Range check
        val min = BigDecimal(CURRENCY_MIN_VALUE)
        val max = BigDecimal(CURRENCY_MAX_VALUE)

        if (amount < min && allowNegative.not()) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor mínimo é $CURRENCY_MIN_VALUE"
            ))
        }

        if (amount > max) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor máximo é $CURRENCY_MAX_VALUE"
            ))
        }

        return errors
    }

    /**
     * Validate percentage value
     *
     * Rules:
     * - Not null
     * - Not NaN or Infinity
     * - Range: 0 to 100
     * - Max 2 decimal places
     *
     * Examples:
     * - 100.00 (100%)
     * - 50.50 (50.5%)
     * - 0.01 (0.01%)
     *
     * @param percentage Percentage to validate
     * @return List of ValidationError (empty if valid)
     */
    fun validatePercentage(percentage: BigDecimal?): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Null check
        if (percentage == null) {
            errors.add(ValidationError(
                field = "percentage",
                message = "Percentual não pode ser vazio"
            ))
            return errors
        }

        // NaN/Infinity check
        if (percentage.isNaN() || percentage.isInfinite()) {
            errors.add(ValidationError(
                field = "percentage",
                message = "Percentual inválido"
            ))
            return errors
        }

        // Range check (0-100)
        if (percentage < BigDecimal(PERCENTAGE_MIN)) {
            errors.add(ValidationError(
                field = "percentage",
                message = "Percentual não pode ser menor que 0%"
            ))
        }

        if (percentage > BigDecimal(PERCENTAGE_MAX)) {
            errors.add(ValidationError(
                field = "percentage",
                message = "Percentual não pode ser maior que 100%"
            ))
        }

        // Decimal places check
        val scale = percentage.scale()
        if (scale > PERCENTAGE_DECIMAL_PLACES) {
            errors.add(ValidationError(
                field = "percentage",
                message = "Percentual pode ter no máximo $PERCENTAGE_DECIMAL_PLACES casas decimais"
            ))
        }

        return errors
    }

    /**
     * Validate quantity (whole numbers)
     *
     * Rules:
     * - Not null
     * - Not NaN or Infinity
     * - Non-negative
     * - Must be whole number (no decimal places)
     * - Max value: 999999
     *
     * @param quantity Quantity to validate
     * @return List of ValidationError (empty if valid)
     */
    fun validateQuantity(quantity: BigDecimal?): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Null check
        if (quantity == null) {
            errors.add(ValidationError(
                field = "quantity",
                message = "Quantidade não pode ser vazia"
            ))
            return errors
        }

        // NaN/Infinity check
        if (quantity.isNaN() || quantity.isInfinite()) {
            errors.add(ValidationError(
                field = "quantity",
                message = "Quantidade inválida"
            ))
            return errors
        }

        // Non-negative check
        if (quantity < BigDecimal.ZERO) {
            errors.add(ValidationError(
                field = "quantity",
                message = "Quantidade não pode ser negativa"
            ))
        }

        // Whole number check
        if (quantity.scale() > QUANTITY_DECIMAL_PLACES || quantity != quantity.setScale(0, RoundingMode.DOWN)) {
            errors.add(ValidationError(
                field = "quantity",
                message = "Quantidade deve ser um número inteiro"
            ))
        }

        // Max value check
        if (quantity > BigDecimal("999999")) {
            errors.add(ValidationError(
                field = "quantity",
                message = "Quantidade não pode exceder 999999"
            ))
        }

        return errors
    }

    /**
     * Validate balance (can be positive or negative)
     *
     * Rules:
     * - Not null
     * - Not NaN or Infinity
     * - Max 2 decimal places
     * - Allow negative values (for representing debt)
     * - Within range: -999999999.99 to 999999999.99
     *
     * @param balance Balance to validate
     * @return List of ValidationError (empty if valid)
     */
    fun validateBalance(balance: BigDecimal?): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Null check
        if (balance == null) {
            errors.add(ValidationError(
                field = "balance",
                message = "Saldo não pode ser vazio"
            ))
            return errors
        }

        // NaN/Infinity check
        if (balance.isNaN() || balance.isInfinite()) {
            errors.add(ValidationError(
                field = "balance",
                message = "Saldo inválido"
            ))
            return errors
        }

        // Decimal places check
        val scale = balance.scale()
        if (scale > CURRENCY_DECIMAL_PLACES) {
            errors.add(ValidationError(
                field = "balance",
                message = "Saldo pode ter no máximo $CURRENCY_DECIMAL_PLACES casas decimais"
            ))
        }

        // Range check
        val min = BigDecimal("-999999999.99")
        val max = BigDecimal("999999999.99")

        if (balance < min || balance > max) {
            errors.add(ValidationError(
                field = "balance",
                message = "Saldo está fora do intervalo permitido"
            ))
        }

        return errors
    }

    /**
     * Normalize currency amount to 2 decimal places
     *
     * Rounds using HALF_UP strategy (standard for currency).
     * Examples:
     * - BigDecimal("150.999") → BigDecimal("151.00")
     * - BigDecimal("150.124") → BigDecimal("150.12")
     * - BigDecimal("150") → BigDecimal("150.00")
     *
     * @param amount Amount to normalize
     * @return Normalized BigDecimal with 2 decimal places, or original if already normalized
     */
    fun normalizeCurrencyAmount(amount: BigDecimal?): BigDecimal? {
        if (amount == null) return null

        // Check if already normalized
        if (amount.scale() == CURRENCY_DECIMAL_PLACES) {
            return amount
        }

        return amount.setScale(CURRENCY_DECIMAL_PLACES, RoundingMode.HALF_UP)
    }

    /**
     * Normalize percentage to 2 decimal places
     *
     * @param percentage Percentage to normalize
     * @return Normalized BigDecimal with 2 decimal places
     */
    fun normalizePercentage(percentage: BigDecimal?): BigDecimal? {
        if (percentage == null) return null

        if (percentage.scale() == PERCENTAGE_DECIMAL_PLACES) {
            return percentage
        }

        return percentage.setScale(PERCENTAGE_DECIMAL_PLACES, RoundingMode.HALF_UP)
    }

    /**
     * Normalize quantity to whole number
     *
     * Rounds down to nearest integer.
     *
     * @param quantity Quantity to normalize
     * @return Normalized BigDecimal as whole number
     */
    fun normalizeQuantity(quantity: BigDecimal?): BigDecimal? {
        if (quantity == null) return null

        if (quantity.scale() == QUANTITY_DECIMAL_PLACES) {
            return quantity
        }

        return quantity.setScale(QUANTITY_DECIMAL_PLACES, RoundingMode.DOWN)
    }
}

/**
 * Check if BigDecimal is valid currency amount
 *
 * @param amount Amount to check
 * @return true if valid (non-null, non-NaN, 2 decimals max, non-negative)
 */
fun isValidCurrencyAmount(amount: BigDecimal?): Boolean {
    if (amount == null) return false
    if (amount.isNaN() || amount.isInfinite()) return false
    return amount >= BigDecimal.ZERO && amount.scale() <= 2
}

/**
 * Check if BigDecimal is valid percentage
 *
 * @param percentage Percentage to check
 * @return true if valid (0-100 range, 2 decimals max)
 */
fun isValidPercentage(percentage: BigDecimal?): Boolean {
    if (percentage == null) return false
    if (percentage.isNaN() || percentage.isInfinite()) return false
    return percentage >= BigDecimal.ZERO &&
            percentage <= BigDecimal("100") &&
            percentage.scale() <= 2
}

/**
 * Check if BigDecimal is valid whole number quantity
 *
 * @param quantity Quantity to check
 * @return true if valid (whole number, non-negative)
 */
fun isValidQuantity(quantity: BigDecimal?): Boolean {
    if (quantity == null) return false
    if (quantity.isNaN() || quantity.isInfinite()) return false
    return quantity >= BigDecimal.ZERO &&
            quantity.scale() <= 0 &&
            quantity == quantity.setScale(0, RoundingMode.DOWN)
}

/**
 * Check if BigDecimal is valid balance
 *
 * @param balance Balance to check (can be negative)
 * @return true if valid (2 decimals max, within range)
 */
fun isValidBalance(balance: BigDecimal?): Boolean {
    if (balance == null) return false
    if (balance.isNaN() || balance.isInfinite()) return false
    val min = BigDecimal("-999999999.99")
    val max = BigDecimal("999999999.99")
    return balance >= min && balance <= max && balance.scale() <= 2
}

/**
 * Safe comparison for BigDecimal values
 *
 * Handles null values and precision.
 *
 * @param a First amount
 * @param b Second amount
 * @return true if a equals b (with BigDecimal.compareTo)
 */
fun areBigDecimalEqual(a: BigDecimal?, b: BigDecimal?): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    return a.compareTo(b) == 0
}

/**
 * Safe sum of BigDecimal values
 *
 * Initializes with BigDecimal.ZERO and sums all values.
 *
 * @param amounts List of amounts to sum
 * @return Sum of all amounts with normalized scale
 */
fun sumBigDecimals(amounts: List<BigDecimal?>): BigDecimal {
    var sum = BigDecimal.ZERO

    for (amount in amounts) {
        if (amount != null && !amount.isNaN() && !amount.isInfinite()) {
            sum += amount
        }
    }

    // Normalize to 2 decimal places
    return sum.setScale(2, RoundingMode.HALF_UP)
}

/**
 * Safe average of BigDecimal values
 *
 * Filters out null/invalid values before calculating average.
 *
 * @param amounts List of amounts
 * @return Average with 2 decimal places, or zero if empty
 */
fun averageBigDecimals(amounts: List<BigDecimal?>): BigDecimal {
    val validAmounts = amounts.filter { it != null && !it.isNaN() && !it.isInfinite() }

    if (validAmounts.isEmpty()) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    }

    val sum = validAmounts.fold(BigDecimal.ZERO) { acc, amount ->
        acc + (amount ?: BigDecimal.ZERO)
    }

    return (sum / BigDecimal(validAmounts.size)).setScale(2, RoundingMode.HALF_UP)
}

/**
 * Convert percentage to factor
 *
 * Examples:
 * - 100% → 1.00
 * - 50% → 0.50
 * - 10.5% → 0.105
 *
 * @param percentage Percentage value (0-100)
 * @return Decimal factor
 */
fun percentageToFactor(percentage: BigDecimal?): BigDecimal {
    if (percentage == null) return BigDecimal.ZERO
    return (percentage / BigDecimal("100")).setScale(4, RoundingMode.HALF_UP)
}

/**
 * Convert factor to percentage
 *
 * Examples:
 * - 1.00 → 100%
 * - 0.50 → 50%
 * - 0.105 → 10.5%
 *
 * @param factor Decimal factor
 * @return Percentage value (0-100)
 */
fun factorToPercentage(factor: BigDecimal?): BigDecimal {
    if (factor == null) return BigDecimal.ZERO
    return (factor * BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
}
