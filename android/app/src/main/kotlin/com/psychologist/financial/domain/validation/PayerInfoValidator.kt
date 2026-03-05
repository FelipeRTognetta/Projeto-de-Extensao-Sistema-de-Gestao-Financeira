package com.psychologist.financial.domain.validation

/**
 * Validator for Responsável Financeiro (Payer Information).
 *
 * Business rules:
 * - nome: required, 2–200 characters
 * - cpf: optional; when provided, validated via PatientValidator.validateCpf()
 * - email: optional; when provided, validated against email regex
 * - telefone, endereco: optional, no format constraints
 *
 * Usage:
 * ```kotlin
 * val validator = PayerInfoValidator()
 * val errors = validator.validate(
 *     nome = "Maria Silva",
 *     cpf = "52998224725",
 *     email = "maria@exemplo.com"
 * )
 * if (errors.isEmpty()) { /* proceed */ }
 * ```
 */
class PayerInfoValidator {

    private companion object {
        private const val NOME_MIN_LENGTH = 2
        private const val NOME_MAX_LENGTH = 200
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }

    private val patientValidator = PatientValidator()

    /**
     * Validate payer info fields.
     *
     * @param nome Full name of the responsible person (required, 2–200 chars)
     * @param cpf Brazilian tax ID of the responsible person (optional)
     * @param email Email address of the responsible person (optional)
     * @return List of ValidationError (empty if all valid)
     */
    fun validate(
        nome: String,
        cpf: String?,
        email: String?
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Nome is required
        val nomeTrimmed = nome.trim()
        if (nomeTrimmed.isEmpty()) {
            errors.add(ValidationError(field = "payerNome", message = "Nome do responsável é obrigatório"))
            return errors
        }
        if (nomeTrimmed.length < NOME_MIN_LENGTH) {
            errors.add(
                ValidationError(
                    field = "payerNome",
                    message = "Nome do responsável deve ter no mínimo $NOME_MIN_LENGTH caracteres"
                )
            )
        }
        if (nomeTrimmed.length > NOME_MAX_LENGTH) {
            errors.add(
                ValidationError(
                    field = "payerNome",
                    message = "Nome do responsável não pode exceder $NOME_MAX_LENGTH caracteres"
                )
            )
        }

        // CPF is optional; when provided, delegate to PatientValidator
        if (!cpf.isNullOrEmpty()) {
            val cpfErrors = patientValidator.validateCpf(cpf)
            cpfErrors.forEach { cpfError ->
                errors.add(ValidationError(field = "payerCpf", message = cpfError.message))
            }
        }

        // Email is optional; when provided, validate format
        if (!email.isNullOrEmpty()) {
            val trimmedEmail = email.trim()
            if (!trimmedEmail.matches(EMAIL_PATTERN)) {
                errors.add(
                    ValidationError(
                        field = "payerEmail",
                        message = "Formato de email inválido. Use: usuario@exemplo.com"
                    )
                )
            }
        }

        return errors
    }
}
