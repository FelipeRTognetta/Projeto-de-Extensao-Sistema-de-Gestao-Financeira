package com.psychologist.financial

import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import com.psychologist.financial.services.EncryptionService
import com.psychologist.financial.services.SecureKeyStore
import com.psychologist.financial.services.DatabaseEncryptionManager
import kotlinx.coroutines.launch

/**
 * Main Activity for Financial Management Application
 *
 * This is the entry point for the app. It sets up:
 * - Jetpack Compose for UI (declarative)
 * - Encryption services (EncryptionService, SecureKeyStore)
 * - Database encryption (DatabaseEncryptionManager, SQLCipher)
 * - Biometric authentication (at startup)
 *
 * Architecture:
 * - Uses Jetpack Compose for UI (declarative)
 * - Material 3 design system
 * - MVVM with ViewModel integration
 * - Two-tier authentication (app-level + per-operation)
 * - Encryption with Android Keystore + SQLCipher
 *
 * Initialization Order:
 * 1. Initialize EncryptionService (Android Keystore)
 * 2. Initialize SecureKeyStore (DataStore + Tink)
 * 3. Initialize DatabaseEncryptionManager (SQLCipher)
 * 4. Show authentication screen (BiometricPrompt)
 * 5. Display main app UI if authenticated
 */
class MainActivity : ComponentActivity() {
    private companion object {
        private const val TAG = "MainActivity"
    }

    // Encryption service instances (lazy initialization)
    private lateinit var encryptionService: EncryptionService
    private lateinit var secureKeyStore: SecureKeyStore
    private lateinit var databaseEncryptionManager: DatabaseEncryptionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity.onCreate() - Starting app initialization")

        // Initialize encryption services on app startup
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Initializing encryption services...")

                // 1. Initialize EncryptionService (Android Keystore)
                encryptionService = EncryptionService()
                Log.d(TAG, "EncryptionService initialized")

                // 2. Initialize SecureKeyStore (DataStore + Tink)
                secureKeyStore = SecureKeyStore(
                    context = this@MainActivity,
                    encryptionService = encryptionService
                )
                Log.d(TAG, "SecureKeyStore initialized")

                // 3. Initialize DatabaseEncryptionManager (SQLCipher)
                databaseEncryptionManager = DatabaseEncryptionManager(
                    encryptionService = encryptionService,
                    secureKeyStore = secureKeyStore
                )
                Log.d(TAG, "DatabaseEncryptionManager initialized")

                // 4. Verify encryption is working
                val encryptionStatus = databaseEncryptionManager.getEncryptionStatus()
                Log.d(TAG, "Encryption status: $encryptionStatus")

                // 5. Initialize database with encrypted key
                val passphrase = databaseEncryptionManager.getDatabasePassphrase()
                Log.d(TAG, "Database passphrase initialized (${passphrase.length} chars)")

                // Set up UI after encryption initialization
                setContent {
                    FinancialAppTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            FinancialManagementApp()
                        }
                    }
                }

                Log.d(TAG, "UI content set - app initialization complete")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize encryption services", e)
                // In production: Show error screen and allow retry
                setContent {
                    FinancialAppTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text("Erro ao inicializar serviços de criptografia: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity.onDestroy() - Cleaning up encryption services")
        // Services will be garbage collected, but we log for debugging
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
