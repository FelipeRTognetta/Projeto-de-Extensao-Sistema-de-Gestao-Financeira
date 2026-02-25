package com.psychologist.financial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Main Activity for Financial Management Application
 *
 * This is the entry point for the app. It sets up Jetpack Compose
 * and initializes the main application theme and navigation.
 *
 * Architecture:
 * - Uses Jetpack Compose for UI (declarative)
 * - Material 3 design system
 * - Prepared for MVVM with ViewModel integration
 * - Biometric authentication will be integrated at app startup
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set content using Jetpack Compose
        setContent {
            FinancialAppTheme {
                // Surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FinancialManagementApp()
                }
            }
        }
    }
}

/**
 * Main application composable
 *
 * This serves as the root composable for the entire application.
 * Future implementation will include:
 * - Navigation setup (Compose Navigation)
 * - Bottom navigation bar (Patients, Appointments, Payments, Dashboard, Export)
 * - Authentication flow (BiometricPrompt at startup)
 * - Theme management (light/dark mode)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialManagementApp() {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Financial Management") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .also { Modifier.padding(paddingValues) }
        ) {
            // TODO: Add navigation container and screens
            // Future implementation:
            // - PatientListScreen
            // - AppointmentListScreen
            // - PaymentListScreen
            // - DashboardScreen
            // - ExportScreen
            Text("Welcome to Financial Management System")
        }
    }
}

/**
 * Material 3 Theme wrapper for the application
 *
 * Applies the Material 3 design system with custom colors
 * defined in colors.xml and themes.xml
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

@Preview(showBackground = true)
@Composable
fun FinancialManagementAppPreview() {
    FinancialAppTheme {
        FinancialManagementApp()
    }
}
