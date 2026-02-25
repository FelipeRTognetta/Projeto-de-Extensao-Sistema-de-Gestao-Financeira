package com.psychologist.financial.domain.validation

import android.util.Log
import java.time.LocalDate

/**
 * Validator for Patient data
 *
 * Enforces business rules for patient information:
 * - Name: 2-200 characters, non-empty, no excessive whitespace
 * - Phone: Optional, valid format, unique (checked by repository)
 * - Email: Optional, valid format, unique (checked by repository)
 * - At least one contact: Phone or email required
 * - Initial consult date: Valid date, not in future
 *
 * Architecture:
 * - Validation layer (domain)
 * - Independent of UI and database
 * - Testable without framework
 * - Used by use cases before repository operations
 *
 * Validation Flow:
 * 1. PatientValidator checks format/constraints
 * 2. Use case applies validator
 * 3. Repository re-validates uniqueness
 * 4. Database enforces constraints
 * (Defense in depth)
 *
 * Usage:
 * ```kotlin
 * val validator = PatientValidator()
 *
 * val errors = validator.validateNewPatient(
 *     name = userInput.name,
 *     phone = userInput.phone,
 *     email = userInput.email,
 *     initialConsultDate = userInput.date
 * )
 *
 * if (errors.isNotEmpty()) {
 *     showErrors(errors)
 * } else {
 *     createPatient()
 * }
 * ```
 *
 * Error Messages:
 * - Portuguese localization for user display
 * - Clear, actionable feedback
 * - Field-specific errors
 */
class PatientValidator {

    private companion object {
        private const val TAG = "PatientValidator"

        // Validation constants
        private const val NAME_MIN_LENGTH = 2
        private const val NAME_MAX_LENGTH = 200
        private const val PHONE_MIN_LENGTH = 7
        private const val PHONE_MAX_LENGTH = 20
        private const val EMAIL_MAX_LENGTH = 254

        // Regex patterns
        private val PHONE_PATTERN = Regex("^[0-9\\-\\+\\(\\) ]{7,20}$")
        private val EMAIL_PATTERN = Regex(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )
        private val NAME_PATTERN = Regex("^[\\p{L}\\p{N}\\s'-]+$")  // Letters, numbers, spaces, hyphens, apostrophes
    }

    /**
     * Validate new patient data
     *
     * Checks all required fields for new patient creation.
     * Returns list of validation errors (empty if valid).
     *
     * @param name Patient name
     * @param phone Optional phone number
     * @param email Optional email address
     * @param initialConsultDate First consultation date
     * @return List of ValidationError (empty if valid)
     *
     * Example:
     * ```kotlin
     * val errors = validator.validateNewPatient(
     *     name = "João Silva",
     *     phone = "(11) 99999-9999",
     *     email = "joao@example.com",
     *     initialConsultDate = LocalDate.now()
     * )
     * ```
     */
    fun validateNewPatient(
        name: String,
        phone: String? = null,
        email: String? = null,
        initialConsultDate: LocalDate = LocalDate.now()
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Validate name
        validateName(name).let { errors.addAll(it) }

        // Validate phone
        validatePhone(phone).let { errors.addAll(it) }

        // Validate email
        validateEmail(email).let { errors.addAll(it) }

        // Validate at least one contact
        validateContactInfo(phone, email).let { errors.addAll(it) }

        // Validate initial consult date
        validateInitialConsultDate(initialConsultDate).let { errors.addAll(it) }

        if (errors.isNotEmpty()) {
            Log.w(TAG, "Validation failed: ${errors.size} errors")
        }

        return errors
    }

    /**
     * Validate patient name
     *
     * Rules:
     * - Required (non-empty)
     * - Min 2 characters
     * - Max 200 characters
     * - Must contain at least one letter
     * - Valid characters: letters, numbers, spaces, hyphens, apostrophes
     * - No excessive whitespace
     *
     * @param name Name to validate
     * @return List of errors (empty if valid)
     */
    fun validateName(name: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val trimmed = name.trim()

        // Required
        if (trimmed.isEmpty()) {
            errors.add(ValidationError(
                field = "name",
                message = "Nome é obrigatório"
            ))
            return errors
        }

        // Min length
        if (trimmed.length < NAME_MIN_LENGTH) {
            errors.add(ValidationError(
                field = "name",
                message = "Nome deve ter no mínimo $NAME_MIN_LENGTH caracteres"
            ))
        }

        // Max length
        if (trimmed.length > NAME_MAX_LENGTH) {
            errors.add(ValidationError(
                field = "name",
                message = "Nome não pode exceder $NAME_MAX_LENGTH caracteres"
            ))
        }

        // Valid characters
        if (!trimmed.matches(NAME_PATTERN)) {
            errors.add(ValidationError(
                field = "name",
                message = "Nome contém caracteres inválidos"
            ))
        }

        // At least one letter
        if (!trimmed.any { it.isLetter() }) {
            errors.add(ValidationError(
                field = "name",
                message = "Nome deve conter pelo menos uma letra"
            ))
        }

        return errors
    }

    /**
     * Validate phone number
     *
     * Rules:
     * - Optional (can be null or empty)
     * - If provided: 7-20 characters
     * - Valid format: digits, spaces, hyphens, parentheses, plus
     * - Examples: (11) 99999-9999, +55 11 99999-9999, 11999999999
     *
     * @param phone Phone to validate
     * @return List of errors (empty if valid)
     */
    fun validatePhone(phone: String?): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (phone.isNullOrEmpty()) {
            return errors  // Optional field
        }

        val trimmed = phone.trim()

        // Length check
        if (trimmed.length < PHONE_MIN_LENGTH) {
            errors.add(ValidationError(
                field = "phone",
                message = "Telefone deve ter no mínimo $PHONE_MIN_LENGTH dígitos"
            ))
        }

        if (trimmed.length > PHONE_MAX_LENGTH) {
            errors.add(ValidationError(
                field = "phone",
                message = "Telefone não pode exceder $PHONE_MAX_LENGTH caracteres"
            ))
        }

        // Format check
        if (!trimmed.matches(PHONE_PATTERN)) {
            errors.add(ValidationError(
                field = "phone",
                message = "Formato de telefone inválido. Use: (11) 99999-9999 ou +55 11 99999-9999"
            ))
        }

        // At least 7 digits
        val digitCount = trimmed.count { it.isDigit() }
        if (digitCount < 7) {
            errors.add(ValidationError(
                field = "phone",
                message = "Telefone deve conter no mínimo 7 dígitos"
            ))
        }

        return errors
    }

    /**
     * Validate email address
     *
     * Rules:
     * - Optional (can be null or empty)
     * - If provided: valid email format
     * - Max 254 characters (RFC 5321)
     * - Simplified RFC 5322 validation
     *
     * @param email Email to validate
     * @return List of errors (empty if valid)
     */
    fun validateEmail(email: String?): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (email.isNullOrEmpty()) {
            return errors  // Optional field
        }

        val trimmed = email.trim()

        // Length check
        if (trimmed.length > EMAIL_MAX_LENGTH) {
            errors.add(ValidationError(
                field = "email",
                message = "Email não pode exceder $EMAIL_MAX_LENGTH caracteres"
            ))
        }

        // Format check
        if (!trimmed.matches(EMAIL_PATTERN)) {
            errors.add(ValidationError(
                field = "email",
                message = "Formato de email inválido. Use: usuario@exemplo.com"
            ))
        }

        return errors
    }

    /**
     * Validate contact information
     *
     * Business rule: At least one contact method (phone or email) required
     *
     * @param phone Optional phone
     * @param email Optional email
     * @return List of errors (empty if valid)
     */
    fun validateContactInfo(phone: String?, email: String?): List<ValidationError> {
        if (phone.isNullOrEmpty() && email.isNullOrEmpty()) {
            return listOf(ValidationError(
                field = "contact",
                message = "Deve ser fornecido telefone ou email"
            ))
        }
        return emptyList()
    }

    /**
     * Validate initial consultation date
     *
     * Rules:
     * - Required (not null)
     * - Cannot be in future
     * - Should be valid date
     *
     * @param date Date to validate
     * @return List of errors (empty if valid)
     */
    fun validateInitialConsultDate(date: LocalDate): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val today = LocalDate.now()

        // Cannot be in future
        if (date.isAfter(today)) {
            errors.add(ValidationError(
                field = "initialConsultDate",
                message = "Data da primeira consulta não pode ser no futuro"
            ))
        }

        return errors
    }

    /**
     * Validate patient update (same as new, but with id)
     *
     * For future use when implementing patient edit.
     *
     * @param patientId Current patient ID
     * @param name Updated name
     * @param phone Updated phone
     * @param email Updated email
     * @param initialConsultDate Updated date
     * @return List of errors (empty if valid)
     */
    fun validateUpdate(
        patientId: Long,
        name: String,
        phone: String? = null,
        email: String? = null,
        initialConsultDate: LocalDate = LocalDate.now()
    ): List<ValidationError> {
        require(patientId > 0) { "Patient must be saved (id > 0) to update" }

        // Use same validation as new patient
        // Repository will check uniqueness excluding current patient
        return validateNewPatient(name, phone, email, initialConsultDate)
    }
}

/**
 * Validate a phone number quickly
 *
 * @param phone Phone to check
 * @return true if phone is valid (or null/empty)
 */
fun isValidPhone(phone: String?): Boolean {
    if (phone.isNullOrEmpty()) return true
    val pattern = Regex("^[0-9\\-\\+\\(\\) ]{7,20}$")
    return phone.matches(pattern)
}

/**
 * Validate an email quickly
 *
 * @param email Email to check
 * @return true if email is valid (or null/empty)
 */
fun isValidEmail(email: String?): Boolean {
    if (email.isNullOrEmpty()) return true
    val pattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    return email.matches(pattern)
}

/**
 * Validate a name quickly
 *
 * @param name Name to check
 * @return true if name is valid
 */
fun isValidName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.length in 2..200 &&
            trimmed.any { it.isLetter() }
}
