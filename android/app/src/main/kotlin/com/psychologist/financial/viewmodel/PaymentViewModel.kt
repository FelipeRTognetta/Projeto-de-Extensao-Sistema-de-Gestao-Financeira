package com.psychologist.financial.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.usecases.GetAllPaymentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientPaymentsUseCase
import com.psychologist.financial.domain.usecases.GetUnpaidAppointmentsUseCase
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
 * Manages payment form state and payment list state.
 *
 * Form flow:
 * 1. Call [loadAvailableAppointments] to populate unpaid appointment list
 * 2. User selects appointments via [toggleAppointmentSelection]
 * 3. User enters amount via [updateAmount] and date via [updatePaymentDate]
 * 4. Call [submitForm] to create payment with selected appointments
 *
 * @param createPaymentUseCase Use case for creating payments
 * @param getUnpaidAppointmentsUseCase Use case for fetching unpaid appointments
 * @param repository Optional PaymentRepository for list/detail screens
 * @param getPatientPaymentsUseCase Optional use case for loading patient payment list
 */
class PaymentViewModel(
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val getUnpaidAppointmentsUseCase: GetUnpaidAppointmentsUseCase,
    private val repository: PaymentRepository? = null,
    private val getPatientPaymentsUseCase: GetPatientPaymentsUseCase? = null,
    private val getAllPaymentsUseCase: GetAllPaymentsUseCase? = null
) : ViewModel() {

    // ========================================
    // Payment Form State (new appointment-based form)
    // ========================================

    private val _paymentFormState = MutableStateFlow(PaymentViewState.PaymentFormState())
    val paymentFormState: StateFlow<PaymentViewState.PaymentFormState> = _paymentFormState.asStateFlow()

    /**
     * Load available (unpaid) appointments for the patient.
     * Populates [paymentFormState.availableAppointments].
     *
     * @param patientId Patient ID
     */
    fun loadAvailableAppointments(patientId: Long) {
        viewModelScope.launch {
            _paymentFormState.update { it.copy(isLoading = true) }
            try {
                getUnpaidAppointmentsUseCase.execute(patientId).collect { appointments ->
                    _paymentFormState.update {
                        it.copy(availableAppointments = appointments, isLoading = false)
                    }
                }
            } catch (e: Exception) {
                _paymentFormState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    /**
     * Toggle selection of an appointment for the payment.
     * If already selected, removes it; otherwise adds it.
     *
     * @param appointmentId Appointment ID to toggle
     */
    fun toggleAppointmentSelection(appointmentId: Long) {
        _paymentFormState.update { state ->
            val current = state.selectedAppointmentIds
            val updated = if (current.contains(appointmentId)) {
                current - appointmentId
            } else {
                current + appointmentId
            }
            state.copy(selectedAppointmentIds = updated)
        }
    }

    /**
     * Update the amount text field.
     *
     * @param amount Raw amount string (e.g. "150.00")
     */
    fun updateAmount(amount: String) {
        _paymentFormState.update { it.copy(amountText = amount, errorMessage = null) }
    }

    /**
     * Update the payment date.
     *
     * @param date Selected payment date
     */
    fun updatePaymentDate(date: LocalDate) {
        _paymentFormState.update { it.copy(paymentDate = date) }
    }

    /**
     * Submit the payment form.
     *
     * Validates amount, then calls [CreatePaymentUseCase.createPayment] with
     * the current [paymentFormState.selectedAppointmentIds].
     *
     * On success: clears selection and amount.
     * On failure: sets [paymentFormState.errorMessage].
     *
     * @param patientId Patient ID for the new payment
     */
    fun submitForm(patientId: Long) {
        val state = _paymentFormState.value

        if (state.amountText.isBlank()) {
            _paymentFormState.update { it.copy(errorMessage = "Informe o valor do pagamento") }
            return
        }

        val amount = state.amountText.replace(",", ".").toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _paymentFormState.update { it.copy(errorMessage = "Valor inválido") }
            return
        }

        _paymentFormState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val payment = Payment(
                    id = 0L,
                    patientId = patientId,
                    amount = amount,
                    paymentDate = state.paymentDate
                )
                createPaymentUseCase.createPayment(payment, state.selectedAppointmentIds.toList())
                _paymentFormState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        amountText = "",
                        selectedAppointmentIds = emptySet()
                    )
                }
            } catch (e: IllegalArgumentException) {
                _paymentFormState.update { it.copy(isLoading = false, errorMessage = e.message) }
            } catch (e: IllegalStateException) {
                _paymentFormState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    // ========================================
    // Global Payment List State (bottom-nav tab)
    // ========================================

    private val _globalListState = MutableStateFlow<PaymentViewState.GlobalListState>(
        PaymentViewState.GlobalListState.Loading
    )
    val globalListState: StateFlow<PaymentViewState.GlobalListState> = _globalListState.asStateFlow()

    /**
     * Load all payments from all patients (global list tab).
     * Collects from [GetAllPaymentsUseCase] reactively.
     * Emits [GlobalListState.Empty] when no payments exist.
     */
    fun loadAllPayments() {
        _globalListState.value = PaymentViewState.GlobalListState.Loading
        viewModelScope.launch {
            try {
                getAllPaymentsUseCase?.execute()?.collect { payments ->
                    _globalListState.value = if (payments.isEmpty()) {
                        PaymentViewState.GlobalListState.Empty
                    } else {
                        PaymentViewState.GlobalListState.Success(payments)
                    }
                } ?: run {
                    _globalListState.value = PaymentViewState.GlobalListState.Empty
                }
            } catch (e: Exception) {
                _globalListState.value = PaymentViewState.GlobalListState.Error(
                    message = e.message ?: "Erro ao carregar pagamentos"
                )
            }
        }
    }

    // ========================================
    // Payment List State
    // ========================================

    private val _paymentListState = MutableStateFlow<PaymentViewState.ListState>(
        PaymentViewState.ListState.Loading
    )
    val paymentListState: StateFlow<PaymentViewState.ListState> = _paymentListState.asStateFlow()

    private val _currentPatientId = MutableStateFlow<Long?>(null)
    val currentPatientId: StateFlow<Long?> = _currentPatientId.asStateFlow()

    /**
     * Load all payments for patient.
     *
     * @param patientId Patient ID
     */
    fun loadPatientPayments(patientId: Long) {
        _currentPatientId.value = patientId
        _paymentListState.value = PaymentViewState.ListState.Loading

        viewModelScope.launch {
            try {
                val payments = getPatientPaymentsUseCase?.execute(patientId) ?: emptyList()
                _paymentListState.value = if (payments.isEmpty()) {
                    PaymentViewState.ListState.Empty
                } else {
                    PaymentViewState.ListState.Success(payments = payments)
                }
            } catch (e: Exception) {
                _paymentListState.value = PaymentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar pagamentos"
                )
            }
        }
    }

    /**
     * Refresh payment list for current patient.
     */
    fun refreshPatientPayments() {
        val patientId = _currentPatientId.value ?: return
        loadPatientPayments(patientId)
    }

    // ========================================
    // Payment Detail State
    // ========================================

    private val _paymentDetailState = MutableStateFlow<PaymentViewState.DetailState>(
        PaymentViewState.DetailState.Idle
    )
    val paymentDetailState: StateFlow<PaymentViewState.DetailState> = _paymentDetailState.asStateFlow()

    /**
     * Load payment detail by ID.
     *
     * @param paymentId Payment ID
     */
    fun loadPaymentDetail(paymentId: Long) {
        _paymentDetailState.value = PaymentViewState.DetailState.Loading

        viewModelScope.launch {
            try {
                val payment = repository?.getById(paymentId)
                _paymentDetailState.value = if (payment != null) {
                    PaymentViewState.DetailState.Success(payment)
                } else {
                    PaymentViewState.DetailState.Error("Pagamento não encontrado")
                }
            } catch (e: Exception) {
                _paymentDetailState.value = PaymentViewState.DetailState.Error(
                    message = e.message ?: "Erro ao carregar pagamento"
                )
            }
        }
    }

    /**
     * Delete a payment.
     *
     * @param paymentId Payment ID
     */
    fun deletePayment(paymentId: Long) {
        viewModelScope.launch {
            try {
                repository?.deleteById(paymentId)
                refreshPatientPayments()
            } catch (e: Exception) {
                // silent — list will refresh on next load
            }
        }
    }
}
