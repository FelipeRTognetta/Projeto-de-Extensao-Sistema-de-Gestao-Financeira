package com.psychologist.financial.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.usecases.CreatePatientUseCase
import com.psychologist.financial.domain.usecases.GetAllPatientsUseCase
import com.psychologist.financial.domain.usecases.MarkPatientInactiveUseCase
import com.psychologist.financial.domain.usecases.ReactivatePatientUseCase
import com.psychologist.financial.viewmodel.PatientViewState.CreatePatientState
import com.psychologist.financial.viewmodel.PatientViewState.DetailState
import com.psychologist.financial.viewmodel.PatientViewState.ListState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ViewModel for Patient Management screens
 *
 * Responsibilities:
 * - Manage patient data state (list, detail, form)
 * - Coordinate use cases (GetAllPatients, CreatePatient, MarkInactive, Reactivate)
 * - Handle user interactions (load, create, update, status changes)
 * - Maintain reactive state via StateFlow
 * - Handle errors and loading states
 * - Filter patients by status (active/inactive)
 *
 * Architecture:
 * - Extends BaseViewModel for coroutine management
 * - Uses use cases for business logic
 * - StateFlow for reactive state
 * - Separate states for list, detail, and form screens
 * - Status filtering with includeInactivePatients flag
 *
 * State Management:
 * - patientListState: List of patients with loading/error
 * - patientDetailState: Single patient detail view
 * - createFormState: Patient creation form state
 * - includeInactivePatients: Filter flag for patient list
 *
 * Usage:
 * ```kotlin
 * class PatientListScreen {
 *     val viewModel = PatientViewModel(
 *         getAllPatientsUseCase,
 *         createPatientUseCase,
 *         markInactiveUseCase,
 *         reactivateUseCase
 *     )
 *
 *     // Load patient list (active only by default)
 *     LaunchedEffect(Unit) {
 *         viewModel.loadPatients()
 *     }
 *
 *     // Toggle to show inactive patients
 *     Button(onClick = { viewModel.toggleInactiveFilter() })
 *
 *     // Mark patient inactive
 *     Button(onClick = { viewModel.markPatientInactive(patient.id) })
 *
 *     // Reactivate patient
 *     Button(onClick = { viewModel.reactivatePatient(patient.id) })
 * }
 * ```
 *
 * @property getAllPatientsUseCase Use case for retrieving patients
 * @property createPatientUseCase Use case for creating patients
 * @property markPatientInactiveUseCase Use case for marking patient inactive
 * @property reactivatePatientUseCase Use case for reactivating patient
 */
class PatientViewModel(
    private val getAllPatientsUseCase: GetAllPatientsUseCase,
    private val createPatientUseCase: CreatePatientUseCase,
    private val markPatientInactiveUseCase: MarkPatientInactiveUseCase,
    private val reactivatePatientUseCase: ReactivatePatientUseCase
) : BaseViewModel() {

    private companion object {
        private const val TAG = "PatientViewModel"
    }

    // ========================================
    // Patient List State
    // ========================================

    /**
     * Patient list state (for list screen)
     *
     * Emits:
     * - Loading: Initial load or refresh
     * - Success: List of patients with optional filter
     * - Empty: No patients found
     * - Error: Load failed
     */
    private val _patientListState = MutableStateFlow<ListState>(ListState.Loading)
    val patientListState: StateFlow<ListState> = _patientListState.asStateFlow()

    /**
     * Whether to include inactive patients in list
     *
     * Default: false (show only ACTIVE)
     * Used to toggle between active/all patients view
     */
    private val _includeInactivePatients = MutableStateFlow(false)
    val includeInactivePatients: StateFlow<Boolean> = _includeInactivePatients.asStateFlow()

    // ========================================
    // Patient Detail State
    // ========================================

    /**
     * Selected patient detail state
     *
     * Emits:
     * - Idle: No patient selected
     * - Loading: Fetching patient detail
     * - Success: Patient loaded with full info
     * - Error: Load failed
     */
    private val _patientDetailState = MutableStateFlow<DetailState>(DetailState.Idle)
    val patientDetailState: StateFlow<DetailState> = _patientDetailState.asStateFlow()

    // ========================================
    // Create Form State
    // ========================================

    /**
     * Patient creation form state
     *
     * Tracks:
     * - Form field values
     * - Validation errors per field
     * - Submission state (idle, loading, success, error)
     */
    private val _createFormState = MutableStateFlow(CreatePatientState())
    val createFormState: StateFlow<CreatePatientState> = _createFormState.asStateFlow()

    /**
     * Form field value: Patient name
     */
    private val _formName = MutableStateFlow("")
    val formName: StateFlow<String> = _formName.asStateFlow()

    /**
     * Form field value: Phone number
     */
    private val _formPhone = MutableStateFlow("")
    val formPhone: StateFlow<String> = _formPhone.asStateFlow()

    /**
     * Form field value: Email address
     */
    private val _formEmail = MutableStateFlow("")
    val formEmail: StateFlow<String> = _formEmail.asStateFlow()

    /**
     * Form field value: Initial consultation date
     */
    private val _formInitialConsultDate = MutableStateFlow(LocalDate.now())
    val formInitialConsultDate: StateFlow<LocalDate> = _formInitialConsultDate.asStateFlow()

    /**
     * Cached patient list from use case
     *
     * Automatically updates when use case emits changes.
     * Used as source of truth for list state.
     */
    private val cachedPatientList: Flow<List<Patient>>
        get() = getAllPatientsUseCase.executeFlow(includeInactivePatients.value)

    init {
        Log.d(TAG, "PatientViewModel initialized")
        // Observe reactive patient list updates
        observePatientListUpdates()
    }

    // ========================================
    // List Screen Operations
    // ========================================

    /**
     * Load initial patient list
     *
     * Called when list screen opens.
     * Sets loading state, fetches patients, updates state.
     *
     * Example:
     * ```kotlin
     * LaunchedEffect(Unit) {
     *     viewModel.loadPatients()
     * }
     * ```
     */
    fun loadPatients() {
        Log.d(TAG, "Loading patients...")
        launchSafe {
            _patientListState.value = ListState.Loading
            val patients = getAllPatientsUseCase.execute(
                includeInactive = _includeInactivePatients.value
            )

            if (patients.isEmpty()) {
                _patientListState.value = ListState.Empty
            } else {
                _patientListState.value = ListState.Success(patients)
            }
        }
    }

    /**
     * Refresh patient list
     *
     * Re-fetches patients from repository.
     * Called on pull-to-refresh or manual refresh.
     *
     * Example:
     * ```kotlin
     * viewModel.refreshPatients()
     * ```
     */
    fun refreshPatients() {
        Log.d(TAG, "Refreshing patients...")
        loadPatients()
    }

    /**
     * Toggle filter to include/exclude inactive patients
     *
     * Changes includeInactivePatients flag and reloads list.
     *
     * Example:
     * ```kotlin
     * viewModel.toggleInactiveFilter()
     * ```
     */
    fun toggleInactiveFilter() {
        _includeInactivePatients.value = !_includeInactivePatients.value
        Log.d(TAG, "Filter toggled: include_inactive=${_includeInactivePatients.value}")
        loadPatients()
    }

    /**
     * Search patients by name
     *
     * Filters current list by search term (client-side).
     * For server-side search, would call use case method.
     *
     * @param searchTerm Name search term
     *
     * Example:
     * ```kotlin
     * viewModel.searchPatients("joão")
     * ```
     */
    fun searchPatients(searchTerm: String) {
        Log.d(TAG, "Searching: $searchTerm")

        if (searchTerm.isEmpty()) {
            loadPatients()
            return
        }

        launchSafe {
            _patientListState.value = ListState.Loading
            val patients = getAllPatientsUseCase.execute(
                includeInactive = _includeInactivePatients.value
            )

            val filtered = patients.filter { patient ->
                patient.name.contains(searchTerm, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                _patientListState.value = ListState.Empty
            } else {
                _patientListState.value = ListState.Success(filtered)
            }
        }
    }

    // ========================================
    // Detail Screen Operations
    // ========================================

    /**
     * Load and display patient detail
     *
     * Called when user taps patient in list.
     * Loads full patient information.
     *
     * @param patientId Patient ID to load
     *
     * Example:
     * ```kotlin
     * viewModel.selectPatient(123)
     * ```
     */
    fun selectPatient(patientId: Long) {
        Log.d(TAG, "Selecting patient: id=$patientId")
        launchSafe {
            _patientDetailState.value = DetailState.Loading
            val patient = getAllPatientsUseCase.execute()
                .firstOrNull { it.id == patientId }

            if (patient != null) {
                _patientDetailState.value = DetailState.Success(patient)
            } else {
                setError("Patient not found")
                _patientDetailState.value = DetailState.Error("Patient not found")
            }
        }
    }

    /**
     * Clear patient detail
     *
     * Called when navigating away from detail screen.
     * Resets detail state to Idle.
     */
    fun clearPatientDetail() {
        _patientDetailState.value = DetailState.Idle
    }

    // ========================================
    // Patient Status Operations
    // ========================================

    /**
     * Mark patient as inactive (archive)
     *
     * Changes patient status to INACTIVE.
     * Prevents creation of new appointments/payments.
     * Used to archive inactive patients while preserving data.
     *
     * @param patientId ID of patient to mark inactive
     *
     * Example:
     * ```kotlin
     * viewModel.markPatientInactive(patientId)
     * ```
     *
     * Side Effects:
     * - Updates patient status to INACTIVE
     * - Refreshes patient list
     * - Updates patient detail if currently displayed
     * - Shows success/error message
     */
    fun markPatientInactive(patientId: Long) {
        Log.d(TAG, "Marking patient inactive: id=$patientId")
        launchSafe {
            try {
                val inactivePatient = markPatientInactiveUseCase.execute(patientId)

                if (inactivePatient != null) {
                    Log.d(TAG, "Patient marked inactive: ${inactivePatient.name}")

                    // Update detail state if patient is currently displayed
                    val detailState = _patientDetailState.value
                    if (detailState is DetailState.Success && detailState.patient.id == patientId) {
                        _patientDetailState.value = DetailState.Success(inactivePatient)
                    }

                    // Refresh list to reflect status change
                    loadPatients()

                    // Show success message
                    setError(null)  // Clear any previous errors
                } else {
                    Log.w(TAG, "Patient not found for inactivation: id=$patientId")
                    setError("Paciente não encontrado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking patient inactive", e)
                setError("Erro ao arquivar paciente: ${e.message}")
            }
        }
    }

    /**
     * Reactivate an inactive patient
     *
     * Changes patient status from INACTIVE to ACTIVE.
     * Allows creation of new appointments/payments again.
     * Used to restore access to archived patients.
     *
     * @param patientId ID of patient to reactivate
     *
     * Example:
     * ```kotlin
     * viewModel.reactivatePatient(patientId)
     * ```
     *
     * Side Effects:
     * - Updates patient status to ACTIVE
     * - Refreshes patient list
     * - Updates patient detail if currently displayed
     * - Shows success/error message
     */
    fun reactivatePatient(patientId: Long) {
        Log.d(TAG, "Reactivating patient: id=$patientId")
        launchSafe {
            try {
                val activePatient = reactivatePatientUseCase.execute(patientId)

                if (activePatient != null) {
                    Log.d(TAG, "Patient reactivated: ${activePatient.name}")

                    // Update detail state if patient is currently displayed
                    val detailState = _patientDetailState.value
                    if (detailState is DetailState.Success && detailState.patient.id == patientId) {
                        _patientDetailState.value = DetailState.Success(activePatient)
                    }

                    // Refresh list to reflect status change
                    loadPatients()

                    // Show success message
                    setError(null)  // Clear any previous errors
                } else {
                    Log.w(TAG, "Patient not found for reactivation: id=$patientId")
                    setError("Paciente não encontrado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reactivating patient", e)
                setError("Erro ao reativar paciente: ${e.message}")
            }
        }
    }

    /**
     * Check if patient is read-only (inactive)
     *
     * Determines if patient can receive new appointments/payments.
     * Used to disable UI elements for inactive patients.
     *
     * @param patient Patient to check
     * @return true if patient is inactive (read-only)
     */
    fun isPatientReadOnly(patient: Patient): Boolean {
        return patient.isInactive
    }

    /**
     * Check if patient can be marked inactive
     *
     * Validates business logic before marking inactive.
     *
     * @param patient Patient to check
     * @return true if patient can be marked inactive
     */
    fun canMarkInactive(patient: Patient): Boolean {
        return patient.isActive
    }

    /**
     * Check if patient can be reactivated
     *
     * Validates business logic before reactivation.
     *
     * @param patient Patient to check
     * @return true if patient can be reactivated
     */
    fun canReactivate(patient: Patient): Boolean {
        return patient.isInactive
    }

    // ========================================
    // Create Form Operations
    // ========================================

    /**
     * Reset form to empty state
     *
     * Called when opening new patient form.
     * Clears all fields and validation errors.
     *
     * Example:
     * ```kotlin
     * viewModel.resetForm()
     * ```
     */
    fun resetForm() {
        Log.d(TAG, "Resetting form")
        _formName.value = ""
        _formPhone.value = ""
        _formEmail.value = ""
        _formInitialConsultDate.value = LocalDate.now()
        _createFormState.value = CreatePatientState()
        clearError()
    }

    /**
     * Update form field: name
     *
     * @param name Patient name input
     *
     * Example:
     * ```kotlin
     * viewModel.setFormName("João Silva")
     * ```
     */
    fun setFormName(name: String) {
        _formName.value = name
    }

    /**
     * Update form field: phone
     *
     * @param phone Phone number input
     */
    fun setFormPhone(phone: String) {
        _formPhone.value = phone
    }

    /**
     * Update form field: email
     *
     * @param email Email address input
     */
    fun setFormEmail(email: String) {
        _formEmail.value = email
    }

    /**
     * Update form field: initial consultation date
     *
     * @param date Initial consultation date
     */
    fun setFormInitialConsultDate(date: LocalDate) {
        _formInitialConsultDate.value = date
    }

    /**
     * Validate form without submitting
     *
     * Checks form fields and updates validation errors.
     * Useful for real-time validation feedback.
     *
     * @return true if form is valid
     *
     * Example:
     * ```kotlin
     * if (viewModel.validateForm()) {
     *     // Show "Ready to save" state
     * }
     * ```
     */
    fun validateForm(): Boolean {
        val errors = createPatientUseCase.validate(
            name = _formName.value,
            phone = _formPhone.value,
            email = _formEmail.value,
            initialConsultDate = _formInitialConsultDate.value
        )

        _createFormState.value = _createFormState.value.copy(
            fieldErrors = errors.associate { it.field to it.message }
        )

        return errors.isEmpty()
    }

    /**
     * Submit form to create patient
     *
     * Validates and creates patient if valid.
     * Updates form state with result.
     *
     * Example:
     * ```kotlin
     * viewModel.submitCreatePatientForm()
     * ```
     */
    fun submitCreatePatientForm() {
        Log.d(TAG, "Submitting patient creation form")
        launchSafe {
            _createFormState.value = _createFormState.value.copy(
                isSubmitting = true
            )

            val result = createPatientUseCase.execute(
                name = _formName.value,
                phone = _formPhone.value,
                email = _formEmail.value,
                initialConsultDate = _formInitialConsultDate.value
            )

            when (result) {
                is com.psychologist.financial.domain.usecases.CreatePatientResult.Success -> {
                    Log.d(TAG, "Patient created: id=${result.patientId}")
                    _createFormState.value = _createFormState.value.copy(
                        isSubmitting = false,
                        submissionResult = CreatePatientState.SubmissionResult.Success(result.patientId)
                    )
                    resetForm()
                    loadPatients()  // Refresh list
                }

                is com.psychologist.financial.domain.usecases.CreatePatientResult.ValidationError -> {
                    Log.w(TAG, "Validation error: ${result.errors.size} errors")
                    _createFormState.value = _createFormState.value.copy(
                        isSubmitting = false,
                        fieldErrors = result.getErrorsByField()
                            .flatMap { (field, messages) ->
                                messages.map { field to it }
                            }
                            .associate { it },
                        submissionResult = CreatePatientState.SubmissionResult.Error(
                            result.getFirstErrorMessage()
                        )
                    )
                    setError(result.getFirstErrorMessage())
                }
            }
        }
    }

    /**
     * Clear form submission result
     *
     * Called when dismissing success/error message.
     * Resets submission state to Idle.
     */
    fun clearSubmissionResult() {
        _createFormState.value = _createFormState.value.copy(
            submissionResult = null
        )
    }

    // ========================================
    // Private Helpers
    // ========================================

    /**
     * Observe reactive patient list updates
     *
     * Subscribes to use case Flow and updates state.
     * Automatically handles new data emissions.
     */
    private fun observePatientListUpdates() {
        viewModelScope.launch {
            cachedPatientList.collectLatest { patients ->
                Log.d(TAG, "Patient list updated: ${patients.size} patients")
                if (patients.isEmpty()) {
                    _patientListState.value = ListState.Empty
                } else {
                    _patientListState.value = ListState.Success(patients)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "PatientViewModel cleared")
    }
}
