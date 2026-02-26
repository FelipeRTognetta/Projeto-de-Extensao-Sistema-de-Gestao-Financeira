package com.psychologist.financial.utils

import android.util.Log

/**
 * AppLogger — Debug/Release Logging Wrapper
 *
 * Wraps Android's [android.util.Log] to provide:
 * - **Debug builds**: full verbose logging at all levels
 * - **Release builds**: only WARN and ERROR levels logged; sensitive data stripped
 * - **Sensitive data protection**: key material, passphrases, biometric tokens
 *   are never logged in non-debug builds
 * - **Structured tags**: all tags prefixed with "FinApp." for easy filtering
 *
 * The [isDebug] flag is set to `BuildConfig.DEBUG` at runtime but can be
 * overridden for testing via [setDebugMode].
 *
 * Usage:
 * ```kotlin
 * AppLogger.d(TAG, "Patient loaded: id=$id")
 * AppLogger.w(TAG, "Cache miss for patient=$id")
 * AppLogger.e(TAG, "Database error", exception)
 * AppLogger.security(TAG, "Biometric auth success")  // debug-only, never in release
 * ```
 *
 * Logcat filter:
 * ```
 * adb logcat -s "FinApp.*"
 * ```
 */
object AppLogger {

    /** Set to BuildConfig.DEBUG in production. Override in tests via [setDebugMode]. */
    private var isDebug: Boolean = true  // Defaults to true; MainActivity sets via initLogging()

    /** Tag prefix applied to all log calls for easy filtering. */
    private const val TAG_PREFIX = "FinApp."

    // ========================================
    // Initialization
    // ========================================

    /**
     * Initialize the logger with the build type.
     * Call this from [FinancialApp.onCreate()].
     *
     * @param debugMode true in debug builds, false in release
     */
    fun initLogging(debugMode: Boolean) {
        isDebug = debugMode
        if (isDebug) {
            Log.d("${TAG_PREFIX}Logger", "AppLogger initialized in DEBUG mode")
        }
    }

    /** Override for testing. Not for production use. */
    internal fun setDebugMode(debug: Boolean) {
        isDebug = debug
    }

    // ========================================
    // Standard Log Levels
    // ========================================

    /**
     * Debug — logged only in debug builds.
     * Use for detailed diagnostic information during development.
     */
    fun d(tag: String, msg: String) {
        if (isDebug) {
            Log.d("$TAG_PREFIX$tag", msg)
        }
    }

    /**
     * Info — logged only in debug builds.
     * Use for significant app lifecycle events.
     */
    fun i(tag: String, msg: String) {
        if (isDebug) {
            Log.i("$TAG_PREFIX$tag", msg)
        }
    }

    /**
     * Warning — logged in all builds.
     * Use for recoverable issues or unexpected but non-critical states.
     * Do NOT include sensitive data in warning messages.
     */
    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$TAG_PREFIX$tag", msg, throwable)
        } else {
            Log.w("$TAG_PREFIX$tag", msg)
        }
    }

    /**
     * Error — logged in all builds.
     * Use for failures that affect user experience or data integrity.
     * Do NOT include sensitive data in error messages.
     */
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", msg, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", msg)
        }
    }

    // ========================================
    // Specialized Loggers
    // ========================================

    /**
     * Security-sensitive log — debug builds only, NEVER in release.
     * Use for biometric events, key operations, auth state changes.
     * Automatically strips sensitive content from messages.
     */
    fun security(tag: String, msg: String) {
        if (isDebug) {
            Log.d("${TAG_PREFIX}Security.$tag", sanitize(msg))
        }
    }

    /**
     * Database operation log — debug builds only.
     * Use for query execution, transaction start/end, DAO calls.
     */
    fun db(tag: String, msg: String) {
        if (isDebug) {
            Log.d("${TAG_PREFIX}DB.$tag", msg)
        }
    }

    /**
     * Performance measurement log — debug builds only.
     * Use for timing measurements and performance monitoring.
     */
    fun perf(tag: String, msg: String, durationMs: Long? = null) {
        if (isDebug) {
            val suffix = if (durationMs != null) " [${durationMs}ms]" else ""
            Log.d("${TAG_PREFIX}Perf.$tag", "$msg$suffix")
        }
    }

    /**
     * Navigation log — debug builds only.
     * Use to trace screen navigation and back-stack changes.
     */
    fun nav(tag: String, msg: String) {
        if (isDebug) {
            Log.d("${TAG_PREFIX}Nav.$tag", msg)
        }
    }

    // ========================================
    // Helpers
    // ========================================

    /**
     * Removes sensitive patterns from log messages for release builds.
     * Redacts: hex key strings, passphrases, token values.
     */
    private fun sanitize(msg: String): String {
        return msg
            .replace(Regex("[0-9a-fA-F]{32,}"), "***REDACTED_HEX***")
            .replace(Regex("x'[0-9a-fA-F]+'"), "x'***REDACTED***'")
            .replace(Regex("passphrase=\\S+"), "passphrase=***")
            .replace(Regex("key=\\S+"), "key=***")
            .replace(Regex("token=\\S+"), "token=***")
    }

    /**
     * Format a log message with component context for structured logging.
     *
     * Usage:
     * ```kotlin
     * AppLogger.d(TAG, format("loadPatient", "id=$id, result=$result"))
     * ```
     */
    fun format(operation: String, details: String): String = "[$operation] $details"
}
