package com.psychologist.financial.utils

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * ErrorHandler — Standardized error messages
 *
 * Converts technical exceptions into user-friendly Portuguese messages.
 * Centralizes all error string logic to maintain consistency across the app.
 *
 * Usage:
 * ```kotlin
 * try {
 *     repository.createPatient(patient)
 * } catch (e: Exception) {
 *     val msg = ErrorHandler.getMessageForException(e)
 *     showError(msg)
 * }
 * ```
 *
 * Categories:
 * - Database errors (SQLite, constraint violations, storage full)
 * - Security/encryption errors (Keystore, biometric)
 * - I/O errors (export, file access)
 * - Validation errors (domain-level invariants)
 * - Generic/unknown errors
 */
object ErrorHandler {

    // ========================================
    // User-Friendly Error Messages (Portuguese)
    // ========================================

    private const val MSG_DATABASE_GENERAL = "Erro ao acessar o banco de dados. Tente novamente."
    private const val MSG_DATABASE_CONSTRAINT = "Dados duplicados ou inválidos. Verifique as informações."
    private const val MSG_DATABASE_FULL = "Armazenamento do dispositivo cheio. Libere espaço e tente novamente."
    private const val MSG_DATABASE_ENCRYPTED = "Erro ao acessar banco de dados criptografado. Reinicie o aplicativo."

    private const val MSG_SECURITY_GENERAL = "Erro de segurança. Reinicie o aplicativo."
    private const val MSG_SECURITY_KEYSTORE = "Erro no armazenamento seguro de chaves. Reinstale o aplicativo se o problema persistir."
    private const val MSG_BIOMETRIC_UNAVAILABLE = "Biometria não disponível neste dispositivo."
    private const val MSG_BIOMETRIC_FAILED = "Autenticação biométrica falhou. Tente novamente."
    private const val MSG_BIOMETRIC_LOCKED = "Biometria bloqueada. Use PIN ou senha do dispositivo."

    private const val MSG_IO_GENERAL = "Erro ao acessar arquivos. Verifique as permissões."
    private const val MSG_IO_PERMISSION = "Permissão negada. Conceda permissão de armazenamento nas configurações."
    private const val MSG_IO_EXPORT = "Erro ao exportar dados. Verifique o armazenamento disponível."

    private const val MSG_VALIDATION_GENERAL = "Dados inválidos. Verifique as informações e tente novamente."
    private const val MSG_NOT_FOUND = "Registro não encontrado."
    private const val MSG_PATIENT_INACTIVE = "Paciente inativo. Reative o paciente para realizar esta operação."
    private const val MSG_DUPLICATE_PHONE = "Já existe um paciente com este telefone."
    private const val MSG_DUPLICATE_EMAIL = "Já existe um paciente com este e-mail."

    private const val MSG_UNKNOWN = "Ocorreu um erro inesperado. Tente novamente."
    private const val MSG_NETWORK = "Erro de conexão. Verifique sua internet."

    // ========================================
    // Exception → Message Mapping
    // ========================================

    /**
     * Returns a user-friendly Portuguese message for the given exception.
     *
     * Maps technical exception types to localized, friendly messages.
     * Falls back to [MSG_UNKNOWN] for unrecognized exceptions.
     */
    fun getMessageForException(e: Throwable): String {
        return when (e) {
            // SQLite errors
            is SQLiteConstraintException -> MSG_DATABASE_CONSTRAINT
            is SQLiteFullException -> MSG_DATABASE_FULL
            is SQLiteException -> {
                if (e.message?.contains("CIPHER") == true ||
                    e.message?.contains("passphrase") == true) {
                    MSG_DATABASE_ENCRYPTED
                } else {
                    MSG_DATABASE_GENERAL
                }
            }

            // Security / encryption errors
            is GeneralSecurityException -> {
                if (e.message?.contains("Keystore") == true ||
                    e.message?.contains("KeyStore") == true) {
                    MSG_SECURITY_KEYSTORE
                } else {
                    MSG_SECURITY_GENERAL
                }
            }
            is SecurityException -> MSG_IO_PERMISSION

            // I/O errors
            is IOException -> {
                if (e.message?.contains("Permission") == true ||
                    e.message?.contains("EACCES") == true) {
                    MSG_IO_PERMISSION
                } else {
                    MSG_IO_GENERAL
                }
            }

            // Domain validation errors (from IllegalArgumentException / IllegalStateException)
            is IllegalStateException -> {
                val msg = e.message ?: ""
                when {
                    msg.contains("inativo", ignoreCase = true) -> MSG_PATIENT_INACTIVE
                    msg.contains("not found", ignoreCase = true) -> MSG_NOT_FOUND
                    msg.contains("telefone", ignoreCase = true) -> MSG_DUPLICATE_PHONE
                    msg.contains("e-mail", ignoreCase = true) || msg.contains("email", ignoreCase = true) -> MSG_DUPLICATE_EMAIL
                    else -> MSG_VALIDATION_GENERAL
                }
            }

            is IllegalArgumentException -> MSG_VALIDATION_GENERAL

            else -> MSG_UNKNOWN
        }
    }

    // ========================================
    // Domain-Specific Error Constructors
    // ========================================

    /** Returns a user-friendly message for database errors. */
    fun databaseError(detail: String? = null): String =
        if (detail != null) "$MSG_DATABASE_GENERAL ($detail)" else MSG_DATABASE_GENERAL

    /** Returns a user-friendly message for biometric authentication errors. */
    fun biometricError(type: BiometricErrorType): String = when (type) {
        BiometricErrorType.UNAVAILABLE -> MSG_BIOMETRIC_UNAVAILABLE
        BiometricErrorType.FAILED -> MSG_BIOMETRIC_FAILED
        BiometricErrorType.LOCKED_OUT -> MSG_BIOMETRIC_LOCKED
    }

    /** Returns a user-friendly message for export errors. */
    fun exportError(detail: String? = null): String =
        if (detail != null) "$MSG_IO_EXPORT ($detail)" else MSG_IO_EXPORT

    /** Returns a message for not-found scenarios. */
    fun notFoundError(entityName: String): String = "$entityName não encontrado."

    /** Returns a message for validation failures. */
    fun validationError(field: String, reason: String): String =
        "Campo '$field' inválido: $reason"

    // ========================================
    // Error Result Wrapper
    // ========================================

    /**
     * Sealed class representing an operation result with error handling.
     *
     * Allows functions to return either success data or a user-friendly error message
     * without throwing exceptions.
     *
     * Usage:
     * ```kotlin
     * fun createPatient(patient: Patient): AppResult<Long> = try {
     *     AppResult.Success(repository.create(patient))
     * } catch (e: Exception) {
     *     AppResult.Error(ErrorHandler.getMessageForException(e), e)
     * }
     * ```
     */
    sealed class AppResult<out T> {
        data class Success<T>(val data: T) : AppResult<T>()
        data class Error(
            val userMessage: String,
            val cause: Throwable? = null
        ) : AppResult<Nothing>()

        val isSuccess: Boolean get() = this is Success
        val isError: Boolean get() = this is Error

        fun getOrNull(): T? = (this as? Success)?.data
        fun errorOrNull(): Error? = this as? Error
    }

    /**
     * Wraps a suspending operation in a try-catch, returning [AppResult].
     * Logs errors via [AppLogger] before wrapping.
     *
     * Usage:
     * ```kotlin
     * val result = safeCall("PatientRepository") { repository.create(patient) }
     * when (result) {
     *     is AppResult.Success -> navigateToDetail(result.data)
     *     is AppResult.Error -> showError(result.userMessage)
     * }
     * ```
     */
    suspend fun <T> safeCall(tag: String, block: suspend () -> T): AppResult<T> {
        return try {
            AppResult.Success(block())
        } catch (e: Exception) {
            AppLogger.e(tag, "Operation failed", e)
            AppResult.Error(getMessageForException(e), e)
        }
    }
}

/**
 * Types of biometric authentication errors.
 */
enum class BiometricErrorType {
    UNAVAILABLE,
    FAILED,
    LOCKED_OUT
}
