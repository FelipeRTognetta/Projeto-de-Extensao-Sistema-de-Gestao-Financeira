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
import com.psychologist.financial.di.AppModule
import com.psychologist.financial.navigation.AppNavGraph
import com.psychologist.financial.navigation.bottomNavRoutes
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
 * Entry point for the Activity. Delegates all dependency creation to [AppModule]
 * (manual service locator — migrates to Hilt in a future refactor).
 *
 * Initialization Order:
 * 1. [FinancialApp.onCreate] initializes AppModule with application context (sync)
 * 2. [AppModule.initDatabase] initializes encrypted AppDatabase (async/suspend)
 * 3. ViewModels are obtained from AppModule factories
 * 4. Compose UI is set with AppNavGraph + BottomNavigation
 *
 * Architecture:
 * - Jetpack Compose + Material 3 for declarative UI
 * - Compose Navigation with NavHostController
 * - Bottom navigation bar: Pacientes | Consultas | Pagamentos | Dashboard | Exportar
 * - MVVM — ViewModels → Use Cases → Repositories → Room + SQLCipher
 * - Two-tier biometric auth (app-level 15 min + per-operation for payments/export)
 * - AES-256-GCM encryption via Android Keystore + SQLCipher
 */
class MainActivity : FragmentActivity() {
    private companion object {
        private const val TAG = "MainActivity"
    }

    // ViewModels — created via AppModule after async DB init
    private lateinit var patientViewModel: PatientViewModel
    private lateinit var appointmentViewModel: AppointmentViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var dashboardViewModel: DashboardViewModel
    private lateinit var exportViewModel: ExportViewModel
    private lateinit var authViewModel: AuthenticationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity.onCreate() — starting initialization")

        lifecycleScope.launch {
            try {
                // 1. Initialize encrypted database (async — passphrase requires coroutine)
                AppModule.initDatabase()

                // 2. Obtain ViewModels from AppModule factories
                patientViewModel = AppModule.providePatientViewModel()
                appointmentViewModel = AppModule.provideAppointmentViewModel()
                paymentViewModel = AppModule.providePaymentViewModel()
                dashboardViewModel = AppModule.provideDashboardViewModel()
                exportViewModel = AppModule.provideExportViewModel()
                authViewModel = AppModule.provideAuthViewModel(this@MainActivity)

                Log.d(TAG, "ViewModels ready — setting UI content")

                // 3. Set Compose UI with navigation
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
        Log.d(TAG, "MainActivity.onDestroy()")
    }
}

/**
 * Main application composable
 *
 * Root composable wiring together:
 * - [rememberNavController] for Compose Navigation
 * - [AppNavGraph] defining all screen routes
 * - [AppBottomNavigation] (5 tabs: Pacientes, Consultas, Pagamentos, Dashboard, Exportar)
 * - [Scaffold] for consistent layout structure
 *
 * Bottom nav visibility:
 * - Shown on root tab destinations (patients, appointments, payments, dashboard, export)
 * - Hidden on auth screens, form screens, and detail screens
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
