package com.psychologist.financial.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import com.psychologist.financial.domain.models.BillableHoursSummary
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase
import com.psychologist.financial.domain.usecases.DeleteAppointmentUseCase
import com.psychologist.financial.domain.usecases.GetAllAppointmentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import com.psychologist.financial.domain.usecases.UpdateAppointmentUseCase
import com.psychologist.financial.services.BillableHoursCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Appointment ViewModel
 *
 * Manages appointment data and UI state for appointment management screens.
 * Provides APIs for listing, creating, and viewing appointments.
 *
 * Responsibilities:
 * - Manage appointment list state (Loading, Success, Empty, Error)
 * - Manage appointment form state (fields, validation, submission)
 * - Manage appointment detail state
 * - Load appointments from repository
 * - Validate and submit new appointments
 * - Calculate billable hours and statistics
 * - Handle errors gracefully
 *
 * State Management:
 * - appointmentListState: List of appointments (all, filtered, searched)
 * - appointmentDetailState: Single appointment detail
 * - createFormState: Form fields and validation errors
 * - billableHoursSummary: Aggregated metrics
 * - loading/error states: UI feedback
 *
 * Example Usage:
 * ```kotlin
 * // In UI
 * val viewModel = AppointmentViewModel(
 *     repository = appointmentRepository,
 *     getPatientAppointmentsUseCase = getPatientAppointmentsUseCase,
 *     createAppointmentUseCase = createAppointmentUseCase,
 *     billableHoursCalculator = billableHoursCalculator
 * )
 *
 * // Load patient appointments
 * viewModel.loadPatientAppointments(patientId = 1L)
 *
 * // Create new appointment
 * viewModel.setFormDate(LocalDate.of(2024, 3, 15))
 * viewModel.setFormTime(LocalTime.of(14, 30))
 * viewModel.setFormDuration(60)
 * viewModel.setFormNotes("Session notes")
 * viewModel.validateForm()
 * viewModel.submitCreateAppointmentForm(patientId = 1L)
 *
 * // Get billable hours summary
 * val summary = viewModel.billableHoursSummary.value
 * ```
 */
class AppointmentViewModel(
    private val repository: AppointmentRepository,
    private val getPatientAppointmentsUseCase: GetPatientAppointmentsUseCase,
    private val createAppointmentUseCase: CreateAppointmentUseCase,
    private val updateAppointmentUseCase: UpdateAppointmentUseCase,
    private val billableHoursCalculator: BillableHoursCalculator = BillableHoursCalculator(),
    private val getAllAppointmentsUseCase: GetAllAppointmentsUseCase? = null,
    private val deleteAppointmentUseCase: DeleteAppointmentUseCase? = null
) : ViewModel() {

    // ========================================
    // Global List State (bottom-nav Consultas tab)
    // ========================================

    private val _globalListState = MutableStateFlow<AppointmentViewState.GlobalListState>(
        AppointmentViewState.GlobalListState.Loading
    )
    val globalListState: StateFlow<AppointmentViewState.GlobalListState> = _globalListState.asStateFlow()

    private var cachedAllAppointments: List<AppointmentWithPaymentStatus> = emptyList()
    private var activeAppointmentFilter: AppointmentViewState.AppointmentFilter = AppointmentViewState.AppointmentFilter.ALL
    private var appointmentNameFilter: String = ""

    /**
     * Load all appointments from all patients (global list tab).
     * Collects from [GetAllAppointmentsUseCase] reactively.
     * Emits [GlobalListState.Empty] when no appointments exist.
     */
    fun loadAllAppointments() {
        _globalListState.value = AppointmentViewState.GlobalListState.Loading
        viewModelScope.launch {
            try {
                getAllAppointmentsUseCase?.execute()?.collect { appointments ->
                    cachedAllAppointments = appointments
                    activeAppointmentFilter = AppointmentViewState.AppointmentFilter.ALL
                    applyGlobalFilter(activeAppointmentFilter)
                } ?: run {
                    _globalListState.value = AppointmentViewState.GlobalListState.Empty
                }
            } catch (e: Exception) {
                _globalListState.value = AppointmentViewState.GlobalListState.Error(
                    message = e.message ?: "Erro ao carregar consultas"
                )
            }
        }
    }

    /**
     * Apply a payment-status filter to the cached global appointment list.
     * Safe to call before [loadAllAppointments] — works on cached data.
     *
     * @param filter [AppointmentFilter.ALL], [PENDING], or [PAID]
     */
    fun setFilter(filter: AppointmentViewState.AppointmentFilter) {
        activeAppointmentFilter = filter
        applyGlobalFilter(filter)
    }

    fun setNameFilter(query: String) {
        appointmentNameFilter = query
        applyGlobalFilter(activeAppointmentFilter)
    }

    fun resetNameFilter() {
        appointmentNameFilter = ""
        applyGlobalFilter(activeAppointmentFilter)
    }

    private fun applyGlobalFilter(filter: AppointmentViewState.AppointmentFilter) {
        val all = cachedAllAppointments
        if (all.isEmpty()) {
            _globalListState.value = AppointmentViewState.GlobalListState.Empty
            return
        }
        val statusFiltered = when (filter) {
            AppointmentViewState.AppointmentFilter.ALL -> all
            AppointmentViewState.AppointmentFilter.PENDING -> all.filter { it.hasPendingPayment }
            AppointmentViewState.AppointmentFilter.PAID -> all.filter { !it.hasPendingPayment }
        }
        val filtered = if (appointmentNameFilter.isBlank()) statusFiltered
        else statusFiltered.filter { it.patientName.contains(appointmentNameFilter, ignoreCase = true) }
        _globalListState.value = AppointmentViewState.GlobalListState.Success(
            allAppointments = all,
            filteredAppointments = filtered,
            activeFilter = filter
        )
    }

    // ========================================
    // Appointment List State
    // ========================================

    private val _appointmentListState = MutableStateFlow<AppointmentViewState.ListState>(
        AppointmentViewState.ListState.Loading
    )
    val appointmentListState: StateFlow<AppointmentViewState.ListState> = _appointmentListState.asStateFlow()

    private val _currentPatientId = MutableStateFlow<Long?>(null)
    val currentPatientId: StateFlow<Long?> = _currentPatientId.asStateFlow()

    // ========================================
    // Appointment Detail State
    // ========================================

    private val _appointmentDetailState = MutableStateFlow<AppointmentViewState.DetailState>(
        AppointmentViewState.DetailState.Idle
    )
    val appointmentDetailState: StateFlow<AppointmentViewState.DetailState> = _appointmentDetailState.asStateFlow()

    // ========================================
    // Form State
    // ========================================

    private val _createFormState = MutableStateFlow(AppointmentViewState.CreateAppointmentState())
    val createFormState: StateFlow<AppointmentViewState.CreateAppointmentState> = _createFormState.asStateFlow()

    private val _formDate = MutableStateFlow(LocalDate.now())
    val formDate: StateFlow<LocalDate> = _formDate.asStateFlow()

    private val _formTime = MutableStateFlow(LocalTime.now().withSecond(0).withNano(0))
    val formTime: StateFlow<LocalTime> = _formTime.asStateFlow()

    private val _formDuration = MutableStateFlow(60)
    val formDuration: StateFlow<Int> = _formDuration.asStateFlow()

    private val _formNotes = MutableStateFlow("")
    val formNotes: StateFlow<String> = _formNotes.asStateFlow()

    // ========================================
    // Billable Hours Summary
    // ========================================

    private val _billableHoursSummary = MutableStateFlow<BillableHoursSummary?>(null)
    val billableHoursSummary: StateFlow<BillableHoursSummary?> = _billableHoursSummary.asStateFlow()

    // ========================================
    // List Loading Operations
    // ========================================

    /**
     * Load all appointments for patient
     *
     * @param patientId Patient ID
     */
    fun loadPatientAppointments(patientId: Long) {
        _currentPatientId.value = patientId
        _appointmentListState.value = AppointmentViewState.ListState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appointments = repository.getByPatientWithPaymentStatus(patientId)

                if (appointments.isEmpty()) {
                    _appointmentListState.value = AppointmentViewState.ListState.Empty
                } else {
                    _appointmentListState.value = AppointmentViewState.ListState.Success(
                        appointments = appointments
                    )
                    // Calculate billable hours
                    updateBillableHoursSummary(appointments.map { it.appointment }, patientId)
                }
            } catch (e: Exception) {
                _appointmentListState.value = AppointmentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar agendamentos"
                )
            }
        }
    }

    /**
     * Refresh appointment list
     */
    fun refreshPatientAppointments() {
        val patientId = _currentPatientId.value ?: return
        loadPatientAppointments(patientId)
    }

    /**
     * Get upcoming appointments
     *
     * @param patientId Patient ID
     */
    fun loadUpcomingAppointments(patientId: Long) {
        _appointmentListState.value = AppointmentViewState.ListState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appointments = getPatientAppointmentsUseCase.getUpcomingAppointments(patientId)
                    .map { AppointmentWithPaymentStatus(it, hasPendingPayment = false) }

                if (appointments.isEmpty()) {
                    _appointmentListState.value = AppointmentViewState.ListState.Empty
                } else {
                    _appointmentListState.value = AppointmentViewState.ListState.Success(
                        appointments = appointments
                    )
                }
            } catch (e: Exception) {
                _appointmentListState.value = AppointmentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar agendamentos"
                )
            }
        }
    }

    /**
     * Get past appointments
     *
     * @param patientId Patient ID
     */
    fun loadPastAppointments(patientId: Long) {
        _appointmentListState.value = AppointmentViewState.ListState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appointments = getPatientAppointmentsUseCase.getPastAppointments(patientId)
                    .map { AppointmentWithPaymentStatus(it, hasPendingPayment = false) }

                if (appointments.isEmpty()) {
                    _appointmentListState.value = AppointmentViewState.ListState.Empty
                } else {
                    _appointmentListState.value = AppointmentViewState.ListState.Success(
                        appointments = appointments
                    )
                }
            } catch (e: Exception) {
                _appointmentListState.value = AppointmentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar agendamentos"
                )
            }
        }
    }

    /**
     * Load appointments for date range
     *
     * @param patientId Patient ID
     * @param startDate Start date
     * @param endDate End date
     */
    fun loadAppointmentsByDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        _appointmentListState.value = AppointmentViewState.ListState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appointments = getPatientAppointmentsUseCase.getByDateRange(
                    patientId = patientId,
                    startDate = startDate,
                    endDate = endDate
                ).map { AppointmentWithPaymentStatus(it, hasPendingPayment = false) }

                if (appointments.isEmpty()) {
                    _appointmentListState.value = AppointmentViewState.ListState.Empty
                } else {
                    _appointmentListState.value = AppointmentViewState.ListState.Success(
                        appointments = appointments
                    )
                }
            } catch (e: Exception) {
                _appointmentListState.value = AppointmentViewState.ListState.Error(
                    message = e.message ?: "Erro ao carregar agendamentos"
                )
            }
        }
    }

    // ========================================
    // Detail View Operations
    // ========================================

    /**
     * Select and view appointment detail
     *
     * @param appointmentId Appointment ID
     */
    fun selectAppointment(appointmentId: Long) {
        _appointmentDetailState.value = AppointmentViewState.DetailState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appointment = repository.getById(appointmentId)

                if (appointment != null) {
                    _appointmentDetailState.value = AppointmentViewState.DetailState.Success(
                        appointment = appointment
                    )
                } else {
                    _appointmentDetailState.value = AppointmentViewState.DetailState.Error(
                        message = "Agendamento não encontrado"
                    )
                }
            } catch (e: Exception) {
                _appointmentDetailState.value = AppointmentViewState.DetailState.Error(
                    message = e.message ?: "Erro ao carregar agendamento"
                )
            }
        }
    }

    // ========================================
    // Form Management
    // ========================================

    /**
     * Set appointment date in form
     *
     * @param date Appointment date
     */
    fun setFormDate(date: LocalDate) {
        _formDate.value = date
        // Clear the date error so the form unlocks after the user picks a valid date
        _createFormState.update { it.copy(fieldErrors = it.fieldErrors - "date") }
    }

    /**
     * Set appointment time in form
     *
     * @param time Appointment time
     */
    fun setFormTime(time: LocalTime) {
        _formTime.value = time
        _createFormState.update { it.copy(fieldErrors = it.fieldErrors - "time") }
    }

    /**
     * Set appointment duration in form
     *
     * @param minutes Duration in minutes
     */
    fun setFormDuration(minutes: Int) {
        _formDuration.value = minutes
        _createFormState.update { it.copy(fieldErrors = it.fieldErrors - "duration" - "durationMinutes") }
    }

    /**
     * Set appointment notes in form
     *
     * @param notes Session notes
     */
    fun setFormNotes(notes: String) {
        _formNotes.value = notes
    }

    /**
     * Reset form to initial state
     */
    fun resetForm() {
        _formDate.value = LocalDate.now()
        _formTime.value = LocalTime.now().withSecond(0).withNano(0)
        _formDuration.value = 60
        _formNotes.value = ""
        _createFormState.value = AppointmentViewState.CreateAppointmentState()
    }

    // ========================================
    // Form Validation
    // ========================================

    /**
     * Validate form fields
     *
     * @return true if form is valid
     */
    fun validateForm(): Boolean {
        val errors = mutableMapOf<String, String>()

        // Date validation
        if (_formDate.value.isAfter(LocalDate.now())) {
            errors["date"] = "Data da consulta não pode ser no futuro"
        }

        // Duration validation
        if (_formDuration.value < 5) {
            errors["duration"] = "Duração mínima é 5 minutos"
        }
        if (_formDuration.value > 480) {
            errors["duration"] = "Duração máxima é 8 horas"
        }

        _createFormState.update { state ->
            state.copy(fieldErrors = errors)
        }

        return errors.isEmpty()
    }

    // ========================================
    // Form Submission
    // ========================================

    /**
     * Submit appointment creation form
     *
     * @param patientId Patient ID
     */
    fun submitCreateAppointmentForm(patientId: Long) {
        if (!validateForm()) {
            return
        }

        _createFormState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = createAppointmentUseCase.execute(
                    patientId = patientId,
                    date = _formDate.value,
                    timeStart = _formTime.value,
                    durationMinutes = _formDuration.value,
                    notes = _formNotes.value
                )

                when (result) {
                    is CreateAppointmentUseCase.CreateAppointmentResult.Success -> {
                        resetForm()
                        _createFormState.update { state ->
                            state.copy(
                                isSubmitting = false,
                                submissionResult = result
                            )
                        }
                        // Reload appointments
                        loadPatientAppointments(patientId)
                    }

                    is CreateAppointmentUseCase.CreateAppointmentResult.ValidationError -> {
                        val fieldErrors = result.errors.associate { error ->
                            error.field to error.message
                        }
                        _createFormState.update { state ->
                            state.copy(
                                isSubmitting = false,
                                fieldErrors = fieldErrors
                            )
                        }
                    }

                    is CreateAppointmentUseCase.CreateAppointmentResult.Error -> {
                        _createFormState.update { state ->
                            state.copy(
                                isSubmitting = false,
                                submissionResult = result
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _createFormState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        submissionResult = CreateAppointmentUseCase.CreateAppointmentResult.Error(
                            message = e.message ?: "Erro ao criar agendamento"
                        )
                    )
                }
            }
        }
    }

    /**
     * Clear submission result
     */
    fun clearSubmissionResult() {
        _createFormState.update { state ->
            state.copy(submissionResult = null)
        }
    }

    /**
     * Pre-fill form with an existing appointment for editing.
     */
    fun prepareEditForm(appointment: Appointment) {
        _formDate.value = appointment.date
        _formTime.value = appointment.timeStart
        _formDuration.value = appointment.durationMinutes
        _formNotes.value = appointment.notes ?: ""
        _createFormState.value = AppointmentViewState.CreateAppointmentState()
    }

    /**
     * Submit the edit form to update an existing appointment.
     *
     * @param appointmentId ID of the appointment to update
     * @param patientId Patient ID (used to refresh the list after update)
     */
    fun submitEditAppointmentForm(appointmentId: Long, patientId: Long) {
        _createFormState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val result = updateAppointmentUseCase.execute(
                appointmentId = appointmentId,
                date = _formDate.value,
                timeStart = _formTime.value,
                durationMinutes = _formDuration.value,
                notes = _formNotes.value
            )

            when (result) {
                is UpdateAppointmentUseCase.UpdateAppointmentResult.Success -> {
                    _createFormState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            submissionResult = CreateAppointmentUseCase.CreateAppointmentResult.Success(
                                appointmentId = appointmentId
                            )
                        )
                    }
                    loadPatientAppointments(patientId)
                }

                is UpdateAppointmentUseCase.UpdateAppointmentResult.ValidationError -> {
                    _createFormState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            fieldErrors = mapOf(result.field to result.message)
                        )
                    }
                }

                is UpdateAppointmentUseCase.UpdateAppointmentResult.Error -> {
                    _createFormState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            submissionResult = CreateAppointmentUseCase.CreateAppointmentResult.Error(
                                message = result.message
                            )
                        )
                    }
                }
            }
        }
    }

    // ========================================
    // Billable Hours Operations
    // ========================================

    /**
     * Update billable hours summary
     *
     * @param appointments List of appointments
     * @param patientId Patient ID (optional)
     */
    private fun updateBillableHoursSummary(
        appointments: List<Appointment>,
        patientId: Long? = null
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val summary = billableHoursCalculator.calculateBillableHoursSummary(appointments)
                _billableHoursSummary.value = summary.copy(
                    patientId = patientId,
                    currentMonthSessions = appointments.count { appointment ->
                        appointment.date.year == LocalDate.now().year &&
                                appointment.date.monthValue == LocalDate.now().monthValue
                    },
                    currentMonthBillableHours = appointments
                        .filter { appointment ->
                            appointment.date.year == LocalDate.now().year &&
                                    appointment.date.monthValue == LocalDate.now().monthValue
                        }
                        .sumOf { it.billableHours }
                )
            } catch (e: Exception) {
                // Silent fail for billable hours calculation
            }
        }
    }

    /**
     * Get billable hours for patient
     *
     * @param patientId Patient ID
     */
    fun loadBillableHoursSummary(patientId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appointments = getPatientAppointmentsUseCase.execute(patientId)
                updateBillableHoursSummary(appointments, patientId)
            } catch (e: Exception) {
                _billableHoursSummary.value = null
            }
        }
    }

    /**
     * Calculate monthly revenue
     *
     * @param hourlyRate Rate per hour
     * @return Total revenue
     */
    fun calculateMonthlyRevenue(hourlyRate: Double): Double {
        return _billableHoursSummary.value?.calculateRevenue(hourlyRate) ?: 0.0
    }

    // ========================================
    // Delete Appointment State (US2)
    // ========================================

    private val _deleteAppointmentState = MutableStateFlow<AppointmentViewState.DeleteAppointmentState>(
        AppointmentViewState.DeleteAppointmentState.Idle
    )
    val deleteAppointmentState: StateFlow<AppointmentViewState.DeleteAppointmentState> =
        _deleteAppointmentState.asStateFlow()

    private var pendingDeleteAppointmentId: Long? = null

    /** Request deletion — moves to AwaitingConfirmation state for UI to show dialog. */
    fun requestDeleteAppointment(appointmentId: Long) {
        pendingDeleteAppointmentId = appointmentId
        _deleteAppointmentState.value = AppointmentViewState.DeleteAppointmentState.AwaitingConfirmation
    }

    /** User confirmed the dialog — moves to AwaitingAuth to trigger biometric. */
    fun onAppointmentDeleteAuthSuccess() {
        _deleteAppointmentState.value = AppointmentViewState.DeleteAppointmentState.AwaitingAuth
    }

    /** Called after successful biometric authentication — execute the delete. */
    fun confirmDeleteAppointment() {
        val id = pendingDeleteAppointmentId ?: return
        _deleteAppointmentState.value = AppointmentViewState.DeleteAppointmentState.InProgress
        viewModelScope.launch {
            try {
                deleteAppointmentUseCase?.execute(id)
                _deleteAppointmentState.value = AppointmentViewState.DeleteAppointmentState.Success
                pendingDeleteAppointmentId = null
            } catch (e: Exception) {
                _deleteAppointmentState.value = AppointmentViewState.DeleteAppointmentState.Error(
                    e.message ?: "Erro ao excluir consulta"
                )
            }
        }
    }

    /** Cancel or reset the delete flow. */
    fun cancelDeleteAppointment() {
        pendingDeleteAppointmentId = null
        _deleteAppointmentState.value = AppointmentViewState.DeleteAppointmentState.Idle
    }
}
