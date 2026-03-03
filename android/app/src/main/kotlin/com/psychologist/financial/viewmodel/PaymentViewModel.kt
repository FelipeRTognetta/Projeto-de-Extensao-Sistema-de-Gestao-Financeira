package com.psychologist.financial.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PatientBalance
import com.psychologist.financial.domain.usecases.CreatePaymentResult
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.usecases.GetPatientPaymentsUseCase
import com.psychologist.financial.services.BalanceCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Payment ViewModel
 *
 * Manages payment data and UI state for payment management screens.
 * Provides APIs for listing, creating, filtering payments, and viewing balance.
 *
 * Responsibilities:
 * - Manage payment list state (Loading, Success, Empty, Error)
 * - Manage payment form state (fields, validation, submission)
 * - Manage payment detail state
 * - Manage balance/financial metrics state
 * - Load payments from repository
 * - Validate and submit new payments
 * - Calculate balance and statistics
 * - Handle errors gracefully
 * - Support status filtering (PAID/PENDING/OVERDUE)
 *
 * State Management:
 * - paymentListState: List of payments (all, filtered, searched)
 * - paymentDetailState: Single payment detail
 * - balanceState: Balance metrics and summary
 * - createFormState: Form fields and validation errors
 * - statusFilter: Current status filter
 * - loading/error states: UI feedback
 *
 * Example Usage:
 * ```kotlin
 * // In UI
 * val viewModel = PaymentViewModel(
 *     repository = paymentRepository,
 *     getPatientPaymentsUseCase = getPatientPaymentsUseCase,
 *     createPaymentUseCase = createPaymentUseCase,
 *     balanceCalculator = balanceCalculator
 * )
 *
 * // Load patient payments
 * viewModel.loadPatientPayments(patientId = 1L)
 *
 * // Get balance
 * val balance = viewModel.balanceState.value
 *
 * // Create new payment
 * viewModel.setFormAmount(BigDecimal("150.00"))
 * viewModel.setFormStatus(Payment.STATUS_PAID)
 * viewModel.setFormMethod(Payment.METHOD_TRANSFER)
 * viewModel.setFormDate(LocalDate.now())
 * viewModel.validateForm()
 * viewModel.submitCreatePaymentForm(patientId = 1L)
 *
 * // Filter by status
 * viewModel.setStatusFilter(PaymentViewState.PaymentStatusFilter.PAID)
 *
 * // Get statistics
 * val summary = viewModel.getPaymentSummary()
 * ```
 */
class PaymentViewModel(
    private val repository: PaymentRepository,
    private val getPatientPaymentsUseCase: GetPatientPaymentsUseCase,
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val balanceCalculator: BalanceCalculator = BalanceCalculator()
) : ViewModel() {

    // ========================================
    // Payment List State
    // ========================================

    private val _paymentListState = MutableStateFlow<PaymentViewState.ListState>(
        PaymentViewState.ListState.Loading
    )
    val paymentListState: StateFlow<PaymentViewState.ListState> = _paymentListState.asStateFlow()

    private val _currentPatientId = MutableStateFlow<Long?>(null)
    val currentPatientId: StateFlow<Long?> = _currentPatientId.asStateFlow()

    // ========================================
    // Payment Detail State
    // ========================================

    private val _paymentDetailState = MutableStateFlow<PaymentViewState.DetailState>(
        PaymentViewState.DetailState.Idle
    )
    val paymentDetailState: StateFlow<PaymentViewState.DetailState> = _paymentDetailState.asStateFlow()

    // ========================================
    // Balance State
    // ========================================

    private val _balanceState = MutableStateFlow(
        PaymentViewState.BalanceState(
            balance = PatientBalance.empty(),
            isLoading = false
        )
    )
    val balanceState: StateFlow<PaymentViewState.BalanceState> = _balanceState.asStateFlow()

    // ========================================
    // Form State
    // ========================================

    private val _createFormState = MutableStateFlow(PaymentViewState.CreatePaymentState())
    val createFormState: StateFlow<PaymentViewState.CreatePaymentState> = _createFormState.asStateFlow()

    private val _formAmount = MutableStateFlow("")
    val formAmount: StateFlow<String> = _formAmount.asStateFlow()

    private val _formStatus = MutableStateFlow(Payment.STATUS_PAID)
    val formStatus: StateFlow<String> = _formStatus.asStateFlow()

    private val _formMethod = MutableStateFlow(Payment.METHOD_TRANSFER)
    val formMethod: StateFlow<String> = _formMethod.asStateFlow()

    private val _formDate = MutableStateFlow(LocalDate.now())
    val formDate: StateFlow<LocalDate> = _formDate.asStateFlow()

    private val _formAppointmentId = MutableStateFlow<Long?>(null)
    val formAppointmentId: StateFlow<Long?> = _formAppointmentId.asStateFlow()

    // ========================================
    // Filter State
    // ========================================

    private val _statusFilter = MutableStateFlow(PaymentViewState.PaymentStatusFilter.ALL)
    val statusFilter: StateFlow<PaymentViewState.PaymentStatusFilter> = _statusFilter.asStateFlow()

    // ========================================
    // List Loading Operations
    // ========================================

    /**
     * Load all payments for patient
     *
     * @param patientId Patient ID
     */
    fun loadPatientPayments(patientId: Long) {
        _currentPatientId.value = patientId
        _paymentListState.value = PaymentViewState.ListState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payments = getPatientPaymentsUseCase.execute(patientId)

                if (payments.isEmpty()) {
                    _paymentListState.value = PaymentViewState.ListState.Empty
                } else {
                    _paymentListState.value = PaymentViewState.ListState.Success(
                        payments = applyStatusFilter(payments)
                    )
                    // Calculate and update balance
                    updateBalance(payments, patientId)
                }
            } catch (e: Exception) {
                _paymentListState.value = PaymentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar pagamentos"
                )
            }
        }
    }

    /**
     * Refresh payment list
     */
    fun refreshPatientPayments() {
        val patientId = _currentPatientId.value ?: return
        loadPatientPayments(patientId)
    }

    /**
     * Load only paid payments
     *
     * @param patientId Patient ID
     */
    fun loadPaidPayments(patientId: Long) {
        _currentPatientId.value = patientId
        _paymentListState.value = PaymentViewState.ListState.Loading
        _statusFilter.value = PaymentViewState.PaymentStatusFilter.PAID

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payments = getPatientPaymentsUseCase.getPaidPayments(patientId)

                if (payments.isEmpty()) {
                    _paymentListState.value = PaymentViewState.ListState.Empty
                } else {
                    _paymentListState.value = PaymentViewState.ListState.Success(
                        payments = payments
                    )
                    updateBalance(payments, patientId)
                }
            } catch (e: Exception) {
                _paymentListState.value = PaymentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar pagamentos"
                )
            }
        }
    }

    /**
     * Load only pending payments
     *
     * @param patientId Patient ID
     */
    fun loadPendingPayments(patientId: Long) {
        _currentPatientId.value = patientId
        _paymentListState.value = PaymentViewState.ListState.Loading
        _statusFilter.value = PaymentViewState.PaymentStatusFilter.PENDING

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payments = getPatientPaymentsUseCase.getPendingPayments(patientId)

                if (payments.isEmpty()) {
                    _paymentListState.value = PaymentViewState.ListState.Empty
                } else {
                    _paymentListState.value = PaymentViewState.ListState.Success(
                        payments = payments
                    )
                    updateBalance(payments, patientId)
                }
            } catch (e: Exception) {
                _paymentListState.value = PaymentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar pagamentos"
                )
            }
        }
    }

    /**
     * Load only overdue payments
     *
     * @param patientId Patient ID
     */
    fun loadOverduePayments(patientId: Long) {
        _currentPatientId.value = patientId
        _paymentListState.value = PaymentViewState.ListState.Loading
        _statusFilter.value = PaymentViewState.PaymentStatusFilter.OVERDUE

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payments = getPatientPaymentsUseCase.getOverduePayments(patientId)

                if (payments.isEmpty()) {
                    _paymentListState.value = PaymentViewState.ListState.Empty
                } else {
                    _paymentListState.value = PaymentViewState.ListState.Success(
                        payments = payments
                    )
                    updateBalance(payments, patientId)
                }
            } catch (e: Exception) {
                _paymentListState.value = PaymentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar pagamentos"
                )
            }
        }
    }

    /**
     * Load payments by date range
     *
     * @param patientId Patient ID
     * @param startDate Start date
     * @param endDate End date
     */
    fun loadPaymentsByDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        _currentPatientId.value = patientId
        _paymentListState.value = PaymentViewState.ListState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payments = getPatientPaymentsUseCase.getByDateRange(
                    patientId,
                    startDate,
                    endDate
                )

                if (payments.isEmpty()) {
                    _paymentListState.value = PaymentViewState.ListState.Empty
                } else {
                    _paymentListState.value = PaymentViewState.ListState.Success(
                        payments = applyStatusFilter(payments)
                    )
                    updateBalance(payments, patientId)
                }
            } catch (e: Exception) {
                _paymentListState.value = PaymentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar pagamentos"
                )
            }
        }
    }

    // ========================================
    // Detail Loading Operations
    // ========================================

    /**
     * Load payment detail by ID
     *
     * @param paymentId Payment ID
     */
    fun loadPaymentDetail(paymentId: Long) {
        _paymentDetailState.value = PaymentViewState.DetailState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payment = repository.getById(paymentId)
                if (payment != null) {
                    _paymentDetailState.value = PaymentViewState.DetailState.Success(payment)
                } else {
                    _paymentDetailState.value = PaymentViewState.DetailState.Error(
                        message = "Pagamento não encontrado"
                    )
                }
            } catch (e: Exception) {
                _paymentDetailState.value = PaymentViewState.DetailState.Error(
                    message = e.message ?: "Erro ao carregar pagamento"
                )
            }
        }
    }

    // ========================================
    // Form Operations - Field Setters
    // ========================================

    /**
     * Set payment amount (as text, converted to BigDecimal on submit)
     *
     * @param amount Payment amount as string (e.g. "150.00")
     */
    fun setFormAmount(amount: String) {
        _formAmount.value = amount
    }

    /**
     * Clear submission result after handling
     */
    fun clearSubmissionResult() {
        _createFormState.update { it.copy(submissionResult = null) }
    }

    /**
     * Set payment status
     *
     * @param status Payment status (PAID/PENDING)
     */
    fun setFormStatus(status: String) {
        _formStatus.value = status
    }

    /**
     * Set payment method
     *
     * @param method Payment method
     */
    fun setFormMethod(method: String) {
        _formMethod.value = method
    }

    /**
     * Set payment date
     *
     * @param date Payment date
     */
    fun setFormDate(date: LocalDate) {
        _formDate.value = date
    }

    /**
     * Set optional appointment link
     *
     * @param appointmentId Appointment ID or null
     */
    fun setFormAppointmentId(appointmentId: Long?) {
        _formAppointmentId.value = appointmentId
    }

    /**
     * Reset form to initial state
     */
    fun resetForm() {
        _formAmount.value = ""
        _formStatus.value = Payment.STATUS_PAID
        _formMethod.value = Payment.METHOD_TRANSFER
        _formDate.value = LocalDate.now()
        _formAppointmentId.value = null
        _createFormState.value = PaymentViewState.CreatePaymentState()
    }

    // ========================================
    // Form Validation & Submission
    // ========================================

    /**
     * Validate form and update error state
     *
     * @param patientId Patient ID for validation
     */
    fun validateForm(patientId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val errors = createPaymentUseCase.validate(
                patientId = patientId,
                amount = _formAmount.value.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                status = _formStatus.value,
                paymentMethod = _formMethod.value,
                paymentDate = _formDate.value
            )

            _createFormState.update { state ->
                state.copy(
                    fieldErrors = errors.associate { it.field to it.message }
                )
            }
        }
    }

    /**
     * Submit create payment form
     *
     * @param patientId Patient ID
     */
    fun submitCreatePaymentForm(patientId: Long) {
        // Mark as submitting
        _createFormState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = createPaymentUseCase.execute(
                    patientId = patientId,
                    appointmentId = _formAppointmentId.value,
                    amount = _formAmount.value.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    status = _formStatus.value,
                    paymentMethod = _formMethod.value,
                    paymentDate = _formDate.value
                )

                _createFormState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        submissionResult = result,
                        fieldErrors = when (result) {
                            is CreatePaymentResult.ValidationError -> {
                                result.errors.associate { it.field to it.message }
                            }
                            else -> emptyMap()
                        }
                    )
                }

                // If successful, reload payments and reset form
                if (result is CreatePaymentResult.Success) {
                    resetForm()
                    refreshPatientPayments()
                }
            } catch (e: Exception) {
                _createFormState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        submissionResult = null
                    )
                }
            }
        }
    }

    // ========================================
    // Filter Operations
    // ========================================

    /**
     * Set status filter for payment list
     *
     * @param filter Status filter
     */
    fun setStatusFilter(filter: PaymentViewState.PaymentStatusFilter) {
        _statusFilter.value = filter

        // Reapply filter to current list
        val currentState = _paymentListState.value
        if (currentState is PaymentViewState.ListState.Success) {
            _paymentListState.value = PaymentViewState.ListState.Success(
                payments = applyStatusFilter(currentState.payments)
            )
        }
    }

    /**
     * Apply current status filter to payment list
     *
     * @param payments Payments to filter
     * @return Filtered payments
     */
    private fun applyStatusFilter(payments: List<Payment>): List<Payment> {
        return when (_statusFilter.value) {
            PaymentViewState.PaymentStatusFilter.ALL -> payments
            PaymentViewState.PaymentStatusFilter.PAID -> payments.filter { it.isPaid }
            PaymentViewState.PaymentStatusFilter.PENDING -> payments.filter { it.isPending }
            PaymentViewState.PaymentStatusFilter.OVERDUE -> payments.filter { it.isPastDue }
        }
    }

    // ========================================
    // Balance Operations
    // ========================================

    /**
     * Update balance state from payments
     *
     * @param payments Payments to calculate from
     * @param patientId Patient ID (for reload if needed)
     */
    private fun updateBalance(payments: List<Payment>, patientId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val balance = balanceCalculator.calculateBalance(payments)
                _balanceState.value = PaymentViewState.BalanceState(
                    balance = balance,
                    isLoading = false
                )
            } catch (e: Exception) {
                _balanceState.value = PaymentViewState.BalanceState(
                    balance = PatientBalance.empty(),
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Load and update balance for patient
     *
     * @param patientId Patient ID
     */
    fun loadBalance(patientId: Long) {
        _balanceState.value = _balanceState.value.copy(isLoading = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payments = getPatientPaymentsUseCase.execute(patientId)
                val balance = balanceCalculator.calculateBalance(payments)

                _balanceState.value = PaymentViewState.BalanceState(
                    balance = balance,
                    isLoading = false
                )
            } catch (e: Exception) {
                _balanceState.value = PaymentViewState.BalanceState(
                    balance = PatientBalance.empty(),
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Get payment summary from current list
     *
     * @return Summary statistics or null if no data
     */
    fun getPaymentSummary(): Map<String, Any>? {
        val state = _paymentListState.value
        return if (state is PaymentViewState.ListState.Success) {
            val payments = state.payments
            mapOf(
                "total" to payments.size,
                "paid" to state.getPaidCount(),
                "pending" to state.getPendingCount(),
                "overdue" to state.getOverdueCount(),
                "collection_rate" to if (payments.isNotEmpty()) {
                    (state.getPaidCount() * 100) / payments.size
                } else {
                    0
                }
            )
        } else {
            null
        }
    }

    /**
     * Check if patient has outstanding balance
     *
     * @return true if has outstanding payments
     */
    fun hasOutstandingBalance(): Boolean {
        return _balanceState.value.balance.hasOutstandingBalance
    }

    /**
     * Mark payment as paid
     *
     * @param paymentId Payment ID
     */
    fun markPaymentAsPaid(paymentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.markAsPaid(paymentId)
                refreshPatientPayments()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    /**
     * Mark payment as pending
     *
     * @param paymentId Payment ID
     */
    fun markPaymentAsPending(paymentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.markAsPending(paymentId)
                refreshPatientPayments()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    /**
     * Delete payment
     *
     * @param paymentId Payment ID
     */
    fun deletePayment(paymentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteById(paymentId)
                refreshPatientPayments()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
