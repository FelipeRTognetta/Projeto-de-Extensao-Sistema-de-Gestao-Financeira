package com.psychologist.financial.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.psychologist.financial.ui.screens.AppointmentFormScreen
import com.psychologist.financial.ui.screens.AppointmentListScreen
import com.psychologist.financial.ui.screens.AuthenticationScreen
import com.psychologist.financial.ui.screens.BiometricEnrollmentScreen
import com.psychologist.financial.ui.screens.DashboardScreen
import com.psychologist.financial.ui.screens.ExportScreen
import com.psychologist.financial.ui.screens.PINFallbackScreen
import com.psychologist.financial.ui.screens.PatientDetailScreen
import com.psychologist.financial.ui.screens.PatientFormScreen
import com.psychologist.financial.ui.screens.PatientListScreen
import com.psychologist.financial.ui.screens.PaymentFormScreen
import com.psychologist.financial.ui.screens.PaymentListScreen
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AuthenticationViewModel
import com.psychologist.financial.viewmodel.DashboardViewModel
import com.psychologist.financial.viewmodel.ExportViewModel
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PaymentViewModel

/**
 * AppNavGraph
 *
 * Defines the complete navigation graph for the application using Compose Navigation.
 *
 * Navigation Architecture:
 * - Single NavController manages all navigation
 * - Start destination is Authentication (app-level biometric)
 * - After authentication, routes to PatientList (main screen)
 * - Bottom nav tabs: Patients, Appointments, Payments, Dashboard, Export
 *
 * Route Groups:
 * 1. Auth: Authentication → BiometricEnrollment, PINFallback
 * 2. Patients: PatientList → PatientDetail → PatientForm
 * 3. Appointments: AppointmentList(patientId) → AppointmentForm(patientId)
 * 4. Payments: PaymentList(patientId) → PaymentForm(patientId)
 * 5. Dashboard: standalone
 * 6. Export: standalone
 *
 * @param navController The NavHostController managing navigation state
 * @param patientViewModel Shared ViewModel for patient operations
 * @param appointmentViewModel Shared ViewModel for appointment operations
 * @param paymentViewModel Shared ViewModel for payment operations
 * @param dashboardViewModel Shared ViewModel for dashboard metrics
 * @param exportViewModel Shared ViewModel for data export
 * @param authViewModel Shared ViewModel for authentication
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    patientViewModel: PatientViewModel,
    appointmentViewModel: AppointmentViewModel,
    paymentViewModel: PaymentViewModel,
    dashboardViewModel: DashboardViewModel,
    exportViewModel: ExportViewModel,
    authViewModel: AuthenticationViewModel
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.Authentication.route
    ) {

        // ========================================
        // Authentication Routes
        // ========================================

        composable(AppDestinations.Authentication.route) {
            AuthenticationScreen(
                viewModel = authViewModel,
                onAuthenticationSuccess = {
                    navController.navigate(AppDestinations.PatientList.route) {
                        popUpTo(AppDestinations.Authentication.route) { inclusive = true }
                    }
                },
                onNavigateToApp = {
                    // onAuthenticationSuccess already handles navigation
                }
            )
        }

        composable(AppDestinations.BiometricEnrollment.route) {
            BiometricEnrollmentScreen(
                viewModel = authViewModel,
                onEnrollmentComplete = {
                    navController.popBackStack()
                },
                onSkipEnrollment = {
                    navController.navigate(AppDestinations.PatientList.route) {
                        popUpTo(AppDestinations.Authentication.route) { inclusive = true }
                    }
                }
            )
        }

        composable(AppDestinations.PINFallback.route) {
            PINFallbackScreen(
                viewModel = authViewModel,
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        // ========================================
        // Patient Routes
        // ========================================

        composable(AppDestinations.PatientList.route) {
            PatientListScreen(
                viewModel = patientViewModel,
                onPatientClick = { patientId ->
                    navController.navigate(AppDestinations.PatientDetail.createRoute(patientId))
                },
                onAddClick = {
                    navController.navigate(AppDestinations.PatientForm.route)
                }
            )
        }

        composable(
            route = AppDestinations.PatientDetail.route,
            arguments = listOf(
                navArgument(AppDestinations.PatientDetail.ARG_PATIENT_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getLong(
                AppDestinations.PatientDetail.ARG_PATIENT_ID
            ) ?: return@composable

            PatientDetailScreen(
                viewModel = patientViewModel,
                patientId = patientId,
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate(AppDestinations.PatientEdit.createRoute(id))
                },
                onNavigateToAppointments = { pid, pname ->
                    navController.navigate(AppDestinations.AppointmentList.createRoute(pid, pname))
                },
                onNavigateToPayments = { pid, pname ->
                    navController.navigate(AppDestinations.PaymentList.createRoute(pid, pname))
                }
            )
        }

        composable(AppDestinations.PatientForm.route) {
            PatientFormScreen(
                viewModel = patientViewModel,
                onSuccess = { patientId ->
                    navController.navigate(AppDestinations.PatientDetail.createRoute(patientId)) {
                        popUpTo(AppDestinations.PatientList.route)
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = AppDestinations.PatientEdit.route,
            arguments = listOf(
                navArgument(AppDestinations.PatientEdit.ARG_PATIENT_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getLong(
                AppDestinations.PatientEdit.ARG_PATIENT_ID
            ) ?: return@composable

            val detailState = patientViewModel.patientDetailState.collectAsState().value
            val patient = (detailState as? com.psychologist.financial.viewmodel.PatientViewState.DetailState.Success)?.patient

            PatientFormScreen(
                viewModel = patientViewModel,
                editingPatient = patient,
                onSuccess = {
                    navController.navigate(AppDestinations.PatientDetail.createRoute(patientId)) {
                        popUpTo(AppDestinations.PatientDetail.createRoute(patientId)) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        // ========================================
        // Appointment Routes
        // ========================================

        composable(
            route = AppDestinations.AppointmentList.route,
            arguments = listOf(
                navArgument(AppDestinations.AppointmentList.ARG_PATIENT_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(AppDestinations.AppointmentList.ARG_PATIENT_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getLong(
                AppDestinations.AppointmentList.ARG_PATIENT_ID
            ) ?: 0L
            val patientName = backStackEntry.arguments?.getString(
                AppDestinations.AppointmentList.ARG_PATIENT_NAME
            ) ?: ""

            val patientDetailState = patientViewModel.patientDetailState.collectAsState().value
            val isPatientActive = if (patientId == 0L) true else {
                (patientDetailState as? com.psychologist.financial.viewmodel.PatientViewState.DetailState.Success)
                    ?.patient?.isActive ?: true
            }

            AppointmentListScreen(
                viewModel = appointmentViewModel,
                patientId = patientId,
                patientName = patientName,
                isPatientActive = isPatientActive,
                onBack = { navController.popBackStack() },
                onAddAppointment = {
                    navController.navigate(AppDestinations.AppointmentForm.createRoute(patientId))
                },
                onSelectAppointment = { appointmentId ->
                    navController.navigate(
                        AppDestinations.AppointmentEdit.createRoute(patientId, appointmentId)
                    )
                },
                onPatientClick = { pid ->
                    navController.navigate(AppDestinations.PatientDetail.createRoute(pid))
                }
            )
        }

        composable(
            route = AppDestinations.AppointmentForm.route,
            arguments = listOf(
                navArgument(AppDestinations.AppointmentForm.ARG_PATIENT_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getLong(
                AppDestinations.AppointmentForm.ARG_PATIENT_ID
            ) ?: return@composable

            AppointmentFormScreen(
                viewModel = appointmentViewModel,
                patientId = patientId,
                onSuccess = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = AppDestinations.AppointmentEdit.route,
            arguments = listOf(
                navArgument(AppDestinations.AppointmentEdit.ARG_PATIENT_ID) {
                    type = NavType.LongType
                },
                navArgument(AppDestinations.AppointmentEdit.ARG_APPOINTMENT_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getLong(
                AppDestinations.AppointmentEdit.ARG_PATIENT_ID
            ) ?: return@composable
            val appointmentId = backStackEntry.arguments?.getLong(
                AppDestinations.AppointmentEdit.ARG_APPOINTMENT_ID
            ) ?: return@composable

            AppointmentFormScreen(
                viewModel = appointmentViewModel,
                patientId = patientId,
                editingAppointmentId = appointmentId,
                onSuccess = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        // ========================================
        // Payment Routes
        // ========================================

        composable(
            route = AppDestinations.PaymentList.route,
            arguments = listOf(
                navArgument(AppDestinations.PaymentList.ARG_PATIENT_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(AppDestinations.PaymentList.ARG_PATIENT_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getLong(
                AppDestinations.PaymentList.ARG_PATIENT_ID
            ) ?: 0L
            val patientName = backStackEntry.arguments?.getString(
                AppDestinations.PaymentList.ARG_PATIENT_NAME
            ) ?: ""

            val patientDetailState = patientViewModel.patientDetailState.collectAsState().value
            val isPatientActive = if (patientId == 0L) true else {
                (patientDetailState as? com.psychologist.financial.viewmodel.PatientViewState.DetailState.Success)
                    ?.patient?.isActive ?: true
            }

            PaymentListScreen(
                viewModel = paymentViewModel,
                patientId = patientId,
                patientName = patientName,
                isPatientActive = isPatientActive,
                onBack = { navController.popBackStack() },
                onAddPayment = {
                    navController.navigate(AppDestinations.PaymentForm.createRoute(patientId))
                },
                onSelectPayment = { selectedPaymentId ->
                    navController.navigate(
                        AppDestinations.PaymentEdit.createRoute(patientId, selectedPaymentId)
                    )
                },
                onPatientClick = { pid ->
                    navController.navigate(AppDestinations.PatientDetail.createRoute(pid))
                }
            )
        }

        composable(
            route = AppDestinations.PaymentForm.route,
            arguments = listOf(
                navArgument(AppDestinations.PaymentForm.ARG_PATIENT_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getLong(
                AppDestinations.PaymentForm.ARG_PATIENT_ID
            ) ?: return@composable

            PaymentFormScreen(
                viewModel = paymentViewModel,
                patientId = patientId,
                onSuccess = {
                    navController.navigate(AppDestinations.PaymentList.createRoute(patientId)) {
                        popUpTo(AppDestinations.PaymentList.createRoute(patientId)) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = AppDestinations.PaymentEdit.route,
            arguments = listOf(
                navArgument(AppDestinations.PaymentEdit.ARG_PATIENT_ID) {
                    type = NavType.LongType
                },
                navArgument(AppDestinations.PaymentEdit.ARG_PAYMENT_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getLong(
                AppDestinations.PaymentEdit.ARG_PATIENT_ID
            ) ?: return@composable
            val paymentId = backStackEntry.arguments?.getLong(
                AppDestinations.PaymentEdit.ARG_PAYMENT_ID
            ) ?: return@composable

            PaymentFormScreen(
                viewModel = paymentViewModel,
                patientId = patientId,
                paymentId = paymentId,
                onSuccess = {
                    navController.navigate(AppDestinations.PaymentList.createRoute(patientId)) {
                        popUpTo(AppDestinations.PaymentList.createRoute(patientId)) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        // ========================================
        // Dashboard & Export Routes
        // ========================================

        composable(AppDestinations.Dashboard.route) {
            DashboardScreen(viewModel = dashboardViewModel)
        }

        composable(AppDestinations.Export.route) {
            ExportScreen(viewModel = exportViewModel)
        }
    }
}
