package com.psychologist.financial.domain.models

import java.io.File

/**
 * Export Result Data Model
 *
 * Encapsulates the result of a financial CSV export operation.
 *
 * @property success Export success status
 * @property paymentFile Path to exported payments CSV file; null if no payments in the month
 * @property paymentCount Number of payments exported
 * @property errorMessage Error description in Portuguese; null on success
 * @property durationSeconds Export duration in seconds
 */
data class ExportResult(
    val success: Boolean,
    val paymentFile: File? = null,
    val paymentCount: Int = 0,
    val errorMessage: String? = null,
    val durationSeconds: Long = 0
) {
    companion object {
        fun failure(errorMessage: String): ExportResult {
            return ExportResult(
                success = false,
                errorMessage = errorMessage
            )
        }

        fun failure(exception: Exception): ExportResult {
            return ExportResult(
                success = false,
                errorMessage = exception.message ?: "Erro desconhecido na exportação"
            )
        }
    }
}
