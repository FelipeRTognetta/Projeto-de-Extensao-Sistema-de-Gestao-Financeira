package com.psychologist.financial.di

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PayerInfoRepository
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase
import com.psychologist.financial.domain.usecases.UpdateAppointmentUseCase
import com.psychologist.financial.domain.usecases.CreatePatientUseCase
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.usecases.ExportDataUseCase
import com.psychologist.financial.domain.usecases.GetAllPatientsUseCase
import com.psychologist.financial.domain.usecases.GetDashboardMetricsUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientPaymentsUseCase
import com.psychologist.financial.domain.usecases.GetUnpaidAppointmentsUseCase
import com.psychologist.financial.domain.usecases.MarkPatientInactiveUseCase
import com.psychologist.financial.domain.usecases.ReactivatePatientUseCase
import com.psychologist.financial.domain.usecases.UpdatePatientUseCase
import com.psychologist.financial.domain.validation.PatientValidator
import com.psychologist.financial.domain.validation.PayerInfoValidator
import com.psychologist.financial.domain.validation.PaymentValidator
import com.psychologist.financial.services.BiometricAuthManager
import com.psychologist.financial.services.DatabaseEncryptionManager
import com.psychologist.financial.services.EncryptionService
import com.psychologist.financial.services.SecureKeyStore
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AuthenticationViewModel
import com.psychologist.financial.viewmodel.DashboardViewModel
import com.psychologist.financial.viewmodel.ExportViewModel
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PaymentViewModel

/**
 * AppModule — Manual Service Locator
 *
 * Provides application-wide singletons and ViewModel factories using a
 * manual dependency injection (service locator) pattern. This replaces
 * the inline dependency creation in MainActivity.
 *
 * Architecture:
 * - Object singleton — one instance for the entire app lifecycle
 * - Lazy initialization for most dependencies to defer allocation
 * - Two-phase init: `initialize(context)` (sync) + `initDatabase()` (async)
 * - ViewModel factories create new VM instances per call
 *
 * Usage:
 * ```kotlin
 * // In FinancialApp.onCreate():
 * AppModule.initialize(this)
 *
 * // In MainActivity (after async DB init):
 * AppModule.initDatabase()
 * val vm = AppModule.providePatientViewModel()
 * ```
 *
 * Migration to Hilt:
 * Replace this object with Hilt modules annotated with @Module + @InstallIn(SingletonComponent::class).
 * ViewModels would use @HiltViewModel and @Inject constructor.
 * No ViewModel-layer changes needed — only this file and FinancialApp.kt change.
 */
object AppModule {

    private const val TAG = "AppModule"

    // ========================================
    // Context — set in initialize()
    // ========================================

    private lateinit var appContext: Context

    /**
     * Phase 1 (sync): Bind the application context.
     * Must be called in FinancialApp.onCreate() before any other access.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "AppModule initialized with application context")
    }

    // ========================================
    // Encryption Services (lazy singletons)
    // ========================================

    val encryptionService: EncryptionService by lazy {
        EncryptionService().also { Log.d(TAG, "EncryptionService created") }
    }

    val secureKeyStore: SecureKeyStore by lazy {
        SecureKeyStore(appContext, encryptionService)
            .also { Log.d(TAG, "SecureKeyStore created") }
    }

    val databaseEncryptionManager: DatabaseEncryptionManager by lazy {
        DatabaseEncryptionManager(encryptionService, secureKeyStore)
            .also { Log.d(TAG, "DatabaseEncryptionManager created") }
    }

    // ========================================
    // Database (requires async init)
    // ========================================

    private var _database: AppDatabase? = null

    /** Returns the initialized database. Throws if initDatabase() hasn't been called. */
    val database: AppDatabase
        get() = _database ?: error("AppModule: database not initialized. Call initDatabase() first.")

    /**
     * Phase 2 (suspend): Initialize the encrypted AppDatabase.
     *
     * Must be called from a coroutine scope AFTER initialize().
     * Computes the SQLCipher passphrase via [DatabaseEncryptionManager.getDatabasePassphrase],
     * then builds the Room + SQLCipher database.
     *
     * This function is idempotent — safe to call multiple times.
     */
    suspend fun initDatabase() {
        if (_database != null) {
            Log.d(TAG, "Database already initialized — skipping")
            return
        }

        Log.d(TAG, "Initializing encrypted database...")

        val encryptionStatus = databaseEncryptionManager.getEncryptionStatus()
        Log.d(TAG, "Encryption status: $encryptionStatus")

        // Get passphrase in coroutine context (suspend), then build database
        val passphrase = databaseEncryptionManager.getDatabasePassphrase()
        Log.d(TAG, "Passphrase obtained (${passphrase.length} chars)")

        val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)

        @Suppress("DEPRECATION")
        _database = AppDatabase.getInstance(appContext, passphraseBytes)
        Log.d(TAG, "AppDatabase initialized successfully")
    }

    // ========================================
    // Repositories (lazy — depend on database)
    // ========================================

    val patientRepository: PatientRepository by lazy {
        PatientRepository(database).also { Log.d(TAG, "PatientRepository created") }
    }

    val appointmentRepository: AppointmentRepository by lazy {
        AppointmentRepository(database, database.appointmentDao())
            .also { Log.d(TAG, "AppointmentRepository created") }
    }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepository(database, database.paymentDao())
            .also { Log.d(TAG, "PaymentRepository created") }
    }

    val dashboardRepository: DashboardRepository by lazy {
        DashboardRepository(database, database.paymentDao(), database.patientDao())
            .also { Log.d(TAG, "DashboardRepository created") }
    }

    val exportRepository: ExportRepository by lazy {
        ExportRepository(database).also { Log.d(TAG, "ExportRepository created") }
    }

    val payerInfoRepository: PayerInfoRepository by lazy {
        PayerInfoRepository(database).also { Log.d(TAG, "PayerInfoRepository created") }
    }

    // ========================================
    // Validators (stateless, no shared state)
    // ========================================

    val patientValidator: PatientValidator by lazy { PatientValidator() }
    val payerInfoValidator: PayerInfoValidator by lazy { PayerInfoValidator() }
    val paymentValidator: PaymentValidator by lazy { PaymentValidator() }

    // ========================================
    // Use Cases (lazy — depend on repositories)
    // ========================================

    val getAllPatientsUseCase: GetAllPatientsUseCase by lazy {
        GetAllPatientsUseCase(patientRepository)
    }

    val createPatientUseCase: CreatePatientUseCase by lazy {
        CreatePatientUseCase(patientRepository, patientValidator)
    }

    val markPatientInactiveUseCase: MarkPatientInactiveUseCase by lazy {
        MarkPatientInactiveUseCase(patientRepository)
    }

    val reactivatePatientUseCase: ReactivatePatientUseCase by lazy {
        ReactivatePatientUseCase(patientRepository)
    }

    val updatePatientUseCase: UpdatePatientUseCase by lazy {
        UpdatePatientUseCase(patientRepository)
    }

    val getPatientAppointmentsUseCase: GetPatientAppointmentsUseCase by lazy {
        GetPatientAppointmentsUseCase(appointmentRepository)
    }

    val createAppointmentUseCase: CreateAppointmentUseCase by lazy {
        CreateAppointmentUseCase(appointmentRepository)
    }

    val updateAppointmentUseCase: UpdateAppointmentUseCase by lazy {
        UpdateAppointmentUseCase(appointmentRepository)
    }

    val getPatientPaymentsUseCase: GetPatientPaymentsUseCase by lazy {
        GetPatientPaymentsUseCase(paymentRepository)
    }

    val createPaymentUseCase: CreatePaymentUseCase by lazy {
        CreatePaymentUseCase(paymentRepository, patientRepository, paymentValidator)
    }

    val getUnpaidAppointmentsUseCase: GetUnpaidAppointmentsUseCase by lazy {
        GetUnpaidAppointmentsUseCase(appointmentRepository)
    }

    val getDashboardMetricsUseCase: GetDashboardMetricsUseCase by lazy {
        GetDashboardMetricsUseCase(dashboardRepository)
    }

    val exportDataUseCase: ExportDataUseCase by lazy {
        ExportDataUseCase(appContext, exportRepository)
    }

    // ========================================
    // ViewModel Factories
    // ========================================

    /**
     * Create a new [PatientViewModel].
     * A new instance is returned on each call (not cached).
     * ViewModels are managed by the Activity's ViewModelStore.
     */
    fun providePatientViewModel(): PatientViewModel = PatientViewModel(
        getAllPatientsUseCase = getAllPatientsUseCase,
        createPatientUseCase = createPatientUseCase,
        markPatientInactiveUseCase = markPatientInactiveUseCase,
        reactivatePatientUseCase = reactivatePatientUseCase,
        updatePatientUseCase = updatePatientUseCase,
        payerInfoRepository = payerInfoRepository,
        payerInfoValidator = payerInfoValidator
    )

    fun provideAppointmentViewModel(): AppointmentViewModel = AppointmentViewModel(
        repository = appointmentRepository,
        getPatientAppointmentsUseCase = getPatientAppointmentsUseCase,
        createAppointmentUseCase = createAppointmentUseCase,
        updateAppointmentUseCase = updateAppointmentUseCase
    )

    fun providePaymentViewModel(): PaymentViewModel = PaymentViewModel(
        createPaymentUseCase = createPaymentUseCase,
        getUnpaidAppointmentsUseCase = getUnpaidAppointmentsUseCase,
        repository = paymentRepository,
        getPatientPaymentsUseCase = getPatientPaymentsUseCase
    )

    fun provideDashboardViewModel(): DashboardViewModel = DashboardViewModel(
        repository = dashboardRepository,
        useCase = getDashboardMetricsUseCase
    )

    fun provideExportViewModel(): ExportViewModel = ExportViewModel(
        exportDataUseCase = exportDataUseCase
    )

    fun provideAuthViewModel(activity: FragmentActivity): AuthenticationViewModel =
        AuthenticationViewModel(
            biometricAuthManager = BiometricAuthManager(activity)
        )

    // ========================================
    // Utilities
    // ========================================

    /** Returns true if the database has been initialized via [initDatabase]. */
    val isDatabaseInitialized: Boolean
        get() = _database != null

    /**
     * Reset all singletons — for testing purposes only.
     * Never call in production code.
     */
    internal fun resetForTesting() {
        _database = null
        Log.w(TAG, "AppModule reset for testing")
    }
}
