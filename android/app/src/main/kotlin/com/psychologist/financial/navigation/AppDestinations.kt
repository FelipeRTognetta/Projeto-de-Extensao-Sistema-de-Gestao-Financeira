package com.psychologist.financial.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * AppDestinations
 *
 * Defines all navigation routes for the application.
 * Uses sealed class hierarchy for type-safe navigation.
 *
 * Route Structure:
 * - Authentication flow: auth, auth/enroll, auth/pin
 * - Patients flow: patients, patients/{id}, patients/form
 * - Appointments flow: appointments/{patientId}?patientName={name}, appointments/{patientId}/form
 * - Payments flow: payments/{patientId}?patientName={name}, payments/{patientId}/form
 * - Dashboard: dashboard
 * - Export: export
 *
 * Bottom Nav Destinations (5 tabs):
 * - Patients, Appointments, Payments, Dashboard, Export
 */
sealed class AppDestinations(val route: String) {

    // ========================================
    // Authentication Routes
    // ========================================

    /** App-level biometric authentication screen */
    object Authentication : AppDestinations("auth")

    /** Biometric enrollment guide screen */
    object BiometricEnrollment : AppDestinations("auth/enroll")

    /** PIN fallback authentication screen */
    object PINFallback : AppDestinations("auth/pin")

    // ========================================
    // Patient Routes
    // ========================================

    /** Patient list screen (bottom nav root) */
    object PatientList : AppDestinations("patients")

    /** Patient detail screen */
    object PatientDetail : AppDestinations("patients/{patientId}") {
        const val ARG_PATIENT_ID = "patientId"
        fun createRoute(patientId: Long): String = "patients/$patientId"
    }

    /** Patient creation/edit form screen */
    object PatientForm : AppDestinations("patients/form")

    // ========================================
    // Appointment Routes
    // ========================================

    /**
     * Appointment list screen.
     * patientId=0 means "global view" (all patients);
     * patientId>0 means patient-specific view.
     */
    object AppointmentList : AppDestinations("appointments/{patientId}?patientName={patientName}") {
        const val ARG_PATIENT_ID = "patientId"
        const val ARG_PATIENT_NAME = "patientName"
        fun createRoute(patientId: Long = 0L, patientName: String = ""): String =
            "appointments/$patientId?patientName=${patientName.ifEmpty { "Todos" }}"
    }

    /** Appointment creation form screen */
    object AppointmentForm : AppDestinations("appointments/{patientId}/form") {
        const val ARG_PATIENT_ID = "patientId"
        fun createRoute(patientId: Long): String = "appointments/$patientId/form"
    }

    // ========================================
    // Payment Routes
    // ========================================

    /**
     * Payment list screen.
     * patientId=0 means "global view" (all patients);
     * patientId>0 means patient-specific view.
     */
    object PaymentList : AppDestinations("payments/{patientId}?patientName={patientName}") {
        const val ARG_PATIENT_ID = "patientId"
        const val ARG_PATIENT_NAME = "patientName"
        fun createRoute(patientId: Long = 0L, patientName: String = ""): String =
            "payments/$patientId?patientName=${patientName.ifEmpty { "Todos" }}"
    }

    /** Payment creation form screen */
    object PaymentForm : AppDestinations("payments/{patientId}/form") {
        const val ARG_PATIENT_ID = "patientId"
        fun createRoute(patientId: Long): String = "payments/$patientId/form"
    }

    // ========================================
    // Dashboard & Export Routes
    // ========================================

    /** Financial dashboard screen (bottom nav root) */
    object Dashboard : AppDestinations("dashboard")

    /** Data export screen (bottom nav root) */
    object Export : AppDestinations("export")
}

/**
 * BottomNavItem
 *
 * Represents each item in the bottom navigation bar.
 * Defines label, icon, and associated route.
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
)

/**
 * Bottom navigation items ordered left-to-right:
 * Patients | Appointments | Payments | Dashboard | Export
 */
val bottomNavItems: List<BottomNavItem> = listOf(
    BottomNavItem(
        route = AppDestinations.PatientList.route,
        label = "Pacientes",
        icon = Icons.Filled.Group,
        contentDescription = "Navegar para lista de pacientes"
    ),
    BottomNavItem(
        route = AppDestinations.AppointmentList.createRoute(0L),
        label = "Consultas",
        icon = Icons.Filled.CalendarMonth,
        contentDescription = "Navegar para lista de consultas"
    ),
    BottomNavItem(
        route = AppDestinations.PaymentList.createRoute(0L),
        label = "Pagamentos",
        icon = Icons.Filled.Payments,
        contentDescription = "Navegar para lista de pagamentos"
    ),
    BottomNavItem(
        route = AppDestinations.Dashboard.route,
        label = "Dashboard",
        icon = Icons.Filled.Dashboard,
        contentDescription = "Navegar para dashboard financeiro"
    ),
    BottomNavItem(
        route = AppDestinations.Export.route,
        label = "Exportar",
        icon = Icons.Filled.FileUpload,
        contentDescription = "Navegar para exportação de dados"
    )
)

/**
 * Set of routes that belong to the bottom navigation bar.
 * Used to determine when to show/hide the bottom nav.
 */
val bottomNavRoutes: Set<String> = setOf(
    AppDestinations.PatientList.route,
    AppDestinations.AppointmentList.route,
    AppDestinations.PaymentList.route,
    AppDestinations.Dashboard.route,
    AppDestinations.Export.route
)
