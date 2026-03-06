package com.psychologist.financial.viewmodel

import com.psychologist.financial.data.repositories.PaymentWithDetails
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Payment
import java.time.LocalDate

/**
 * Payment ViewModel state classes
 *
 * Defines sealed classes and data classes for managing payment screen states.
 *
 * State Hierarchy:
 * - ListState: Payment list view (Loading, Success, Empty, Error)
 * - DetailState: Single payment detail (Idle, Loading, Success, Error)
 * - PaymentFormState: New payment form (amount, date, appointment selection)
 */
object PaymentViewState {

    // ========================================
    // List State
    // ========================================

    /**
     * Payment list view state
     */
    sealed class ListState {
        object Loading : ListState()

        data class Success(
            val payments: List<Payment>
        ) : ListState() {
            fun getCount(): Int = payments.size
            fun isEmpty(): Boolean = payments.isEmpty()
        }

        object Empty : ListState()

        data class Error(val message: String) : ListState()
    }

    // ========================================
    // Detail State
    // ========================================

    /**
     * Payment detail view state
     */
    sealed class DetailState {
        object Idle : DetailState()
        object Loading : DetailState()

        data class Success(
            val payment: Payment
        ) : DetailState() {
            fun getDisplayAmount(): String = payment.displayAmount
        }

        data class Error(val message: String) : DetailState()
    }

    // ========================================
    // Global Payment List State
    // ========================================

    /**
     * Global payment list state (all patients, bottom-nav tab).
     */
    sealed class GlobalListState {
        object Loading : GlobalListState()

        data class Success(
            val payments: List<PaymentWithDetails>
        ) : GlobalListState() {
            fun getCount(): Int = payments.size
            fun isEmpty(): Boolean = payments.isEmpty()
        }

        object Empty : GlobalListState()

        data class Error(val message: String) : GlobalListState()
    }

    // ========================================
    // Payment Form State
    // ========================================

    /**
     * Payment creation form state
     *
     * Manages available appointments, selection set, amount, date, and error state.
     * No status or method fields — all payments are implicitly PAID.
     *
     * @param availableAppointments Unpaid appointments available to link
     * @param selectedAppointmentIds Set of appointment IDs the user has selected
     * @param amountText Raw text entered by user for the amount field
     * @param paymentDate Selected payment date
     * @param isLoading true while loading appointments or submitting
     * @param errorMessage Non-null when there is a validation or submission error
     */
    data class PaymentFormState(
        val availableAppointments: List<Appointment> = emptyList(),
        val linkedAppointments: List<Appointment> = emptyList(),
        val selectedAppointmentIds: Set<Long> = emptySet(),
        val amountText: String = "",
        val paymentDate: LocalDate = LocalDate.now(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val editingPaymentId: Long? = null
    )
}
