package com.psychologist.financial.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PatientBalance
import com.psychologist.financial.domain.usecases.CreatePaymentResult
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
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
    private val getPatientAppointmentsUseCase: GetPatientAppointmentsUseCase? = null,
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

    // Master unfiltered list — required for correct filter reapplication
    private val _allPayments = MutableStateFlow<List<Payment>>(emptyList())
    val allPayments: StateFlow<List<Payment>> = _allPayments.asStateFlow()

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

    private val _patientAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val patientAppointments: StateFlow<List<Appointment>> = _patientAppointments.asStateFlow()

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
        _statusFilter.value = PaymentViewState.PaymentStatusFilter.ALL
        _paymentListState.value = PaymentViewState.ListState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payments = getPatientPaymentsUseCase.execute(patientId)

                _allPayments.value = payments
                if (payments.isEmpty()) {
                    _paymentListState.value = PaymentViewState.ListState.Empty
                } else {
                    val filtered = applyStatusFilter(payments)
                    _paymentListState.value = if (filtered.isEmpty()) {
                        PaymentViewState.ListState.Empty
                    } else {
                        PaymentViewState.ListState.Success(payments = filtered)
                    }
                    // Calculate and update balance from full list
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
     * Set payment amount from raw digit input.
     *
     * Stores only digits representing centavos (e.g. "15000" = R$ 150,00).
     * Non-digit characters are stripped automatically.
     *
     * @param input New input string (may contain non-digit chars — they are stripped)
     */
    fun setFormAmount(input: String) {
        _formAmount.value = input.filter { it.isDigit() }.trimStart('0').ifEmpty { "0" }
        _createFormState.update { it.copy(fieldErrors = it.fieldErrors - "amount") }
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
        _createFormState.update { it.copy(fieldErrors = it.fieldErrors - "status") }
    }

    /**
     * Set payment method
     *
     * @param method Payment method
     */
    fun setFormMethod(method: String) {
        _formMethod.value = method
        _createFormState.update { it.copy(fieldErrors = it.fieldErrors - "method") }
    }

    /**
     * Set payment date
     *
     * @param date Payment date
     */
    fun setFormDate(date: LocalDate) {
        _formDate.value = date
        _createFormState.update { it.copy(fieldErrors = it.fieldErrors - "paymentDate") }
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
     * Load available appointments for patient (used in appointment picker)
     *
     * @param patientId Patient ID
     */
    fun loadAppointmentsForPatient(patientId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appointments = getPatientAppointmentsUseCase?.execute(patientId) ?: emptyList()
                _patientAppointments.value = appointments
            } catch (e: Exception) {
                _patientAppointments.value = emptyList()
            }
        }
    }

    /**
     * Load an existing payment into the form for editing.
     *
     * Normalizes stored method constants (e.g. "PIX", "CASH") to the form's
     * display names ("Pix", "Dinheiro"), since the form dropdown stores display names.
     *
     * @param paymentId Payment ID to edit
     */
    fun loadPaymentForEdit(paymentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payment = repository.getById(paymentId) ?: return@launch
                // Convert BigDecimal to centavos digits (e.g. 150.00 → "15000")
                val centavos = payment.amount.multiply(BigDecimal(100)).toLong()
                _formAmount.value = centavos.toString()
                _formDate.value = payment.paymentDate
                _formMethod.value = normalizeMethodForForm(payment.paymentMethod)
                _formStatus.value = payment.status
                _formAppointmentId.value = payment.appointmentId
                _createFormState.value = PaymentViewState.CreatePaymentState()
            } catch (e: Exception) {
                // ignore — form stays at defaults
            }
        }
    }

    /**
     * Convert centavos digit string to BigDecimal.
     * e.g. "15000" → BigDecimal("150.00"), "0" → BigDecimal.ZERO
     */
    private fun centavosToDecimal(digits: String): BigDecimal {
        val long = digits.toLongOrNull() ?: 0L
        return BigDecimal(long).divide(BigDecimal(100))
    }

    /** Map stored method constants or legacy values to form display names. */
    private fun normalizeMethodForForm(method: String): String = when (method) {
        "CASH" -> "Dinheiro"
        "TRANSFER" -> "Dinheiro"
        "CARD" -> "Crédito"
        "CHECK" -> "Cheque"
        "PIX" -> "Pix"
        else -> method  // already a display name (e.g. "Pix", "Débito")
    }

    /**
     * Submit update for an existing payment
     *
     * @param paymentId Payment ID to update
     * @param patientId Patient ID (for validation and reload)
     */
    fun submitUpdatePaymentForm(paymentId: Long, patientId: Long) {
        _createFormState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val errors = createPaymentUseCase.validate(
                    patientId = patientId,
                    amount = centavosToDecimal(_formAmount.value),
                    status = _formStatus.value,
                    paymentMethod = _formMethod.value,
                    paymentDate = _formDate.value
                )

                if (errors.isNotEmpty()) {
                    _createFormState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            fieldErrors = errors.associate { it.field to it.message }
                        )
                    }
                    return@launch
                }

                val existing = repository.getById(paymentId) ?: return@launch
                val updated = existing.copy(
                    amount = centavosToDecimal(_formAmount.value),
                    paymentDate = _formDate.value,
                    paymentMethod = _formMethod.value,
                    status = _formStatus.value,
                    appointmentId = _formAppointmentId.value
                )
                repository.update(updated)

                _createFormState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        submissionResult = CreatePaymentResult.Success(paymentId),
                        fieldErrors = emptyMap()
                    )
                }
                refreshPatientPayments()
            } catch (e: Exception) {
                _createFormState.update { state ->
                    state.copy(isSubmitting = false)
                }
            }
        }
    }

    /**
     * Reset form to initial state
     */
    fun resetForm() {
        _formAmount.value = "0"
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
                amount = centavosToDecimal(_formAmount.value),
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
                    amount = centavosToDecimal(_formAmount.value),
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

        // Always reapply to the master (unfiltered) list
        val all = _allPayments.value
        if (all.isEmpty()) return
        val filtered = applyStatusFilter(all)
        _paymentListState.value = if (filtered.isEmpty()) {
            PaymentViewState.ListState.Empty
        } else {
            PaymentViewState.ListState.Success(payments = filtered)
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
