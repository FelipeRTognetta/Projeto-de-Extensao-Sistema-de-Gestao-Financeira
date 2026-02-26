package com.psychologist.financial

import android.os.Bundle
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase
import com.psychologist.financial.domain.usecases.CreatePatientUseCase
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import com.psychologist.financial.domain.usecases.ExportDataUseCase
import com.psychologist.financial.domain.usecases.GetAllPatientsUseCase
import com.psychologist.financial.domain.usecases.GetDashboardMetricsUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientPaymentsUseCase
import com.psychologist.financial.domain.usecases.MarkPatientInactiveUseCase
import com.psychologist.financial.domain.usecases.ReactivatePatientUseCase
import com.psychologist.financial.domain.validation.PatientValidator
import com.psychologist.financial.domain.validation.PaymentValidator
import com.psychologist.financial.navigation.AppNavGraph
import com.psychologist.financial.navigation.bottomNavItems
import com.psychologist.financial.navigation.bottomNavRoutes
import com.psychologist.financial.services.BiometricAuthManager
import com.psychologist.financial.services.DatabaseEncryptionManager
import com.psychologist.financial.services.EncryptionService
import com.psychologist.financial.services.SecureKeyStore
import com.psychologist.financial.ui.components.AppBottomNavigation
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AuthenticationViewModel
import com.psychologist.financial.viewmodel.DashboardViewModel
import com.psychologist.financial.viewmodel.ExportViewModel
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PaymentViewModel
import kotlinx.coroutines.launch

/**
 * Main Activity for Financial Management Application
 *
 * Entry point for the app. Initializes encryption services, database,
 * repositories, use cases, ViewModels, and sets up Compose navigation.
 *
 * Architecture:
 * - Jetpack Compose for UI (declarative, Material 3)
 * - Compose Navigation with NavHostController
 * - Bottom navigation bar with 5 tabs
 * - MVVM with ViewModel + Use Cases + Repositories
 * - Two-tier authentication (app-level biometric + per-operation)
 * - AES-256-GCM encryption via Android Keystore + SQLCipher
 *
 * Initialization Order:
 * 1. EncryptionService (Android Keystore)
 * 2. SecureKeyStore (DataStore + Tink)
 * 3. DatabaseEncryptionManager (SQLCipher)
 * 4. AppDatabase (encrypted SQLCipher database)
 * 5. Repositories, Use Cases, ViewModels (manual DI — replaced by Hilt in Phase 11.2)
 * 6. Set UI content with AppNavGraph and BottomNavigation
 *
 * NOTE: Manual dependency injection here will be replaced by Hilt (Phase 11.2, T171-T172).
 */
class MainActivity : FragmentActivity() {
    private companion object {
        private const val TAG = "MainActivity"
    }

    // Encryption services
    private lateinit var encryptionService: EncryptionService
    private lateinit var secureKeyStore: SecureKeyStore
    private lateinit var databaseEncryptionManager: DatabaseEncryptionManager

    // ViewModels (manual DI — Phase 11.2 replaces with Hilt)
    private lateinit var patientViewModel: PatientViewModel
    private lateinit var appointmentViewModel: AppointmentViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var dashboardViewModel: DashboardViewModel
    private lateinit var exportViewModel: ExportViewModel
    private lateinit var authViewModel: AuthenticationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity.onCreate() - Starting app initialization")

        lifecycleScope.launch {
            try {
                // 1. Initialize encryption services
                Log.d(TAG, "Initializing encryption services...")
                encryptionService = EncryptionService()
                secureKeyStore = SecureKeyStore(
                    context = this@MainActivity,
                    encryptionService = encryptionService
                )
                databaseEncryptionManager = DatabaseEncryptionManager(
                    encryptionService = encryptionService,
                    secureKeyStore = secureKeyStore
                )

                val encryptionStatus = databaseEncryptionManager.getEncryptionStatus()
                Log.d(TAG, "Encryption status: $encryptionStatus")

                // 2. Initialize encrypted database
                val database = AppDatabase.getInstance(
                    context = this@MainActivity,
                    encryptionService = encryptionService,
                    secureKeyStore = secureKeyStore,
                    databaseEncryptionManager = databaseEncryptionManager
                )
                Log.d(TAG, "AppDatabase initialized with SQLCipher encryption")

                // 3. Create repositories
                val patientRepository = PatientRepository(database)
                val appointmentRepository = AppointmentRepository(database.appointmentDao())
                val paymentRepository = PaymentRepository(database.paymentDao())
                val dashboardRepository = DashboardRepository(database.paymentDao(), database.patientDao())
                val exportRepository = ExportRepository(database)

                // 4. Create use cases
                val patientValidator = PatientValidator()
                val paymentValidator = PaymentValidator()

                val getAllPatients = GetAllPatientsUseCase(patientRepository)
                val createPatient = CreatePatientUseCase(patientRepository, patientValidator)
                val markInactive = MarkPatientInactiveUseCase(patientRepository)
                val reactivate = ReactivatePatientUseCase(patientRepository)

                val getPatientAppointments = GetPatientAppointmentsUseCase(appointmentRepository)
                val createAppointment = CreateAppointmentUseCase(appointmentRepository)

                val getPatientPayments = GetPatientPaymentsUseCase(paymentRepository)
                val createPayment = CreatePaymentUseCase(paymentRepository, patientRepository, paymentValidator)

                val getDashboardMetrics = GetDashboardMetricsUseCase(dashboardRepository)
                val exportData = ExportDataUseCase(this@MainActivity, exportRepository)

                // 5. Create ViewModels (Phase 11.2 replaces with Hilt injection)
                patientViewModel = PatientViewModel(
                    getAllPatientsUseCase = getAllPatients,
                    createPatientUseCase = createPatient,
                    markPatientInactiveUseCase = markInactive,
                    reactivatePatientUseCase = reactivate
                )
                appointmentViewModel = AppointmentViewModel(
                    repository = appointmentRepository,
                    getPatientAppointmentsUseCase = getPatientAppointments,
                    createAppointmentUseCase = createAppointment
                )
                paymentViewModel = PaymentViewModel(
                    repository = paymentRepository,
                    getPatientPaymentsUseCase = getPatientPayments,
                    createPaymentUseCase = createPayment
                )
                dashboardViewModel = DashboardViewModel(
                    repository = dashboardRepository,
                    useCase = getDashboardMetrics
                )
                exportViewModel = ExportViewModel(
                    exportDataUseCase = exportData
                )

                val biometricAuthManager = BiometricAuthManager(this@MainActivity)
                authViewModel = AuthenticationViewModel(biometricAuthManager)

                Log.d(TAG, "All ViewModels created — setting UI content")

                // 6. Set up Compose UI with navigation
                setContent {
                    FinancialAppTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            FinancialManagementApp(
                                patientViewModel = patientViewModel,
                                appointmentViewModel = appointmentViewModel,
                                paymentViewModel = paymentViewModel,
                                dashboardViewModel = dashboardViewModel,
                                exportViewModel = exportViewModel,
                                authViewModel = authViewModel
                            )
                        }
                    }
                }

                Log.d(TAG, "App initialization complete")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize app", e)
                setContent {
                    FinancialAppTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text("Erro ao inicializar o aplicativo: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity.onDestroy() - Cleaning up resources")
    }
}

/**
 * Main application composable
 *
 * Root composable wiring together:
 * - NavHostController for Compose Navigation
 * - AppNavGraph defining all screen routes
 * - AppBottomNavigation (5 tabs: Pacientes, Consultas, Pagamentos, Dashboard, Exportar)
 * - Scaffold for consistent layout structure
 *
 * Bottom nav visibility:
 * - Shown on root tab destinations (patients, appointments, payments, dashboard, export)
 * - Hidden on auth screens, form screens, and detail screens
 *
 * @param patientViewModel ViewModel for patient operations
 * @param appointmentViewModel ViewModel for appointment operations
 * @param paymentViewModel ViewModel for payment operations
 * @param dashboardViewModel ViewModel for dashboard metrics
 * @param exportViewModel ViewModel for data export
 * @param authViewModel ViewModel for biometric authentication
 */
@Composable
fun FinancialManagementApp(
    patientViewModel: PatientViewModel,
    appointmentViewModel: AppointmentViewModel,
    paymentViewModel: PaymentViewModel,
    dashboardViewModel: DashboardViewModel,
    exportViewModel: ExportViewModel,
    authViewModel: AuthenticationViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom nav only on root tab destinations
    val showBottomNav = bottomNavRoutes.any { route ->
        currentRoute?.startsWith(route.substringBefore("{").trimEnd('/')) == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                AppBottomNavigation(navController = navController)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AppNavGraph(
                navController = navController,
                patientViewModel = patientViewModel,
                appointmentViewModel = appointmentViewModel,
                paymentViewModel = paymentViewModel,
                dashboardViewModel = dashboardViewModel,
                exportViewModel = exportViewModel,
                authViewModel = authViewModel
            )
        }
    }
}

/**
 * Material 3 Theme wrapper for the application
 */
@Composable
fun FinancialAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
