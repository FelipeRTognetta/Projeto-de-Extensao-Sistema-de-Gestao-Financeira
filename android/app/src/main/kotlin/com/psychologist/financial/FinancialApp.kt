package com.psychologist.financial

import android.app.Application
import android.util.Log
import com.psychologist.financial.di.AppModule
import com.psychologist.financial.utils.AppLogger

/**
 * FinancialApp — Application class
 *
 * Entry point for application-level initialization.
 * Registered in AndroidManifest.xml as android:name=".FinancialApp".
 *
 * Responsibilities:
 * 1. Initialize the [AppModule] service locator with the application context.
 *    (Phase 1 — sync. Database init is async and happens in MainActivity.)
 * 2. Configure any global app settings (logging, crash reporting, etc.)
 *
 * What this does NOT do:
 * - Initialize the database (async, needs coroutine — done in MainActivity)
 * - Create ViewModels (Activity-scoped — done in MainActivity)
 * - Show any UI (no UI at Application level)
 *
 * Migration to Hilt:
 * Add @HiltAndroidApp annotation and replace AppModule.initialize(this)
 * with Hilt's generated component setup. Everything else remains the same.
 *
 * ```kotlin
 * // Future Hilt version:
 * @HiltAndroidApp
 * class FinancialApp : Application()
 * ```
 */
class FinancialApp : Application() {

    private companion object {
        private const val TAG = "FinancialApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize logger first (before any other logging)
        AppLogger.initLogging(debugMode = android.os.Build.TYPE != "user")
        AppLogger.d(TAG, "FinancialApp.onCreate() — Initializing application")

        // Phase 1: Initialize service locator with application context.
        // Encryption services use lazy initialization and won't be created here.
        AppModule.initialize(this)

        AppLogger.d(TAG, "AppModule initialized — app ready for Activity creation")
    }
}
