package com.psychologist.financial.domain.validation

/**
 * Validation error information
 *
 * Represents a single validation error returned during validation of user input.
 * Used to provide structured, field-specific error feedback to the UI layer.
 *
 * Architecture:
 * - Part of domain validation layer
 * - Independent of UI and framework
 * - Used by validators and use cases
 * - Displayed by UI screens with field-level error support
 *
 * Example:
 * ```kotlin
 * val error = ValidationError(
 *     field = "name",
 *     message = "Nome deve ter no mínimo 2 caracteres"
 * )
 *
 * // Display in UI
 * OutlinedTextField(
 *     value = name,
 *     isError = error.field == "name",
 *     supportingText = { Text(error.message) }
 * )
 * ```
 *
 * @param field Field name that contains the error (matches form field names and validation field identifiers)
 * @param message User-friendly error message in Portuguese
 */
data class ValidationError(
    val field: String,
    val message: String
) {
    /**
     * Check if error is for a specific field
     *
     * Useful for conditional display in forms.
     *
     * @param fieldName Field name to check
     * @return true if error is for this field
     */
    fun isForField(fieldName: String): Boolean = field == fieldName

    /**
     * Get error message with field prefix
     *
     * Format: "fieldName: message"
     *
     * @return Prefixed error message
     */
    fun getFormattedMessage(): String = "$field: $message"

    override fun toString(): String = "ValidationError(field='$field', message='$message')"

    companion object {
        /**
         * Create name field error
         *
         * @param message Error message
         * @return ValidationError for name field
         */
        fun nameError(message: String): ValidationError =
            ValidationError(field = "name", message = message)

        /**
         * Create phone field error
         *
         * @param message Error message
         * @return ValidationError for phone field
         */
        fun phoneError(message: String): ValidationError =
            ValidationError(field = "phone", message = message)

        /**
         * Create email field error
         *
         * @param message Error message
         * @return ValidationError for email field
         */
        fun emailError(message: String): ValidationError =
            ValidationError(field = "email", message = message)

        /**
         * Create contact info error
         *
         * @param message Error message
         * @return ValidationError for contact field
         */
        fun contactError(message: String): ValidationError =
            ValidationError(field = "contact", message = message)

        /**
         * Create initial consult date field error
         *
         * @param message Error message
         * @return ValidationError for initialConsultDate field
         */
        fun dateError(message: String): ValidationError =
            ValidationError(field = "initialConsultDate", message = message)
    }
}
