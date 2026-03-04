package com.psychologist.financial.domain.models

/**
 * Domain model for Responsável Financeiro (Payer Information).
 *
 * Represents the person responsible for paying for a non-paying patient's sessions.
 * This is a clean domain model with no Room annotations, following the MVVM architecture
 * pattern used throughout the project.
 *
 * Mapping:
 * - PayerInfoEntity (database) → PayerInfo (domain) via PayerInfoRepository
 * - PayerInfo (domain) → PayerInfoEntity (database) via PayerInfoRepository
 *
 * Usage:
 * ```kotlin
 * val payer = PayerInfo(
 *     id = 0,
 *     patientId = 42,
 *     nome = "Maria Silva",
 *     cpf = "12345678909",
 *     email = "maria@exemplo.com"
 * )
 * ```
 *
 * @property id Record ID (0 = not yet saved, > 0 = saved)
 * @property patientId ID of the associated non-paying patient
 * @property nome Full name of the responsible person (required)
 * @property cpf Brazilian tax ID (11 raw digits, optional)
 * @property endereco Free-text address (optional)
 * @property email Email address (optional)
 * @property telefone Phone number (optional)
 */
data class PayerInfo(
    val id: Long = 0,
    val patientId: Long,
    val nome: String,
    val cpf: String? = null,
    val endereco: String? = null,
    val email: String? = null,
    val telefone: String? = null
) {
    /** Returns true if this PayerInfo has been persisted to the database. */
    val isSaved: Boolean get() = id > 0L

    /**
     * Returns the CPF formatted with the Brazilian mask (XXX.XXX.XXX-XX).
     * Returns null if cpf is null or not exactly 11 digits.
     */
    fun getFormattedCpf(): String? {
        val digits = cpf?.filter { it.isDigit() } ?: return null
        if (digits.length != 11) return null
        return "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9)}"
    }

    companion object {
        /** Creates a PayerInfo instance for use in tests. */
        fun createForTesting(
            id: Long = 0,
            patientId: Long = 1,
            nome: String = "Responsável Teste",
            cpf: String? = null,
            email: String? = null,
            telefone: String? = null
        ): PayerInfo = PayerInfo(
            id = id,
            patientId = patientId,
            nome = nome,
            cpf = cpf,
            email = email,
            telefone = telefone
        )
    }
}
