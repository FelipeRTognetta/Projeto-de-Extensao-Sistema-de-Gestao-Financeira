package com.psychologist.financial.utils

/**
 * Application-wide constants for Financial Management System
 *
 * Centralized location for all magic numbers, strings, and configuration values
 * to maintain consistency across the application.
 */
object Constants {

    // ================================
    // Database Constants
    // ================================
    const val DATABASE_NAME = "financial_management.db"
    const val DATABASE_VERSION = 3

    // ================================
    // Authentication Constants
    // ================================
    const val BIOMETRIC_SESSION_TIMEOUT_MINUTES = 15
    const val BIOMETRIC_SESSION_TIMEOUT_MILLIS = BIOMETRIC_SESSION_TIMEOUT_MINUTES * 60 * 1000L
    const val BIOMETRIC_AUTH_VALIDITY_DURATION_SECONDS = 5 * 60 // 5 minutes

    // ================================
    // Encryption Constants
    // ================================
    const val ENCRYPTION_KEY_SIZE = 256 // bits
    const val ENCRYPTION_KEY_ALIAS = "financial_app_master_key"
    const val KEY_ROTATION_INTERVAL_DAYS = 90

    // ================================
    // Validation Constants
    // ================================
    const val PATIENT_NAME_MIN_LENGTH = 2
    const val PATIENT_NAME_MAX_LENGTH = 200
    const val PHONE_NUMBER_MIN_LENGTH = 7
    const val PHONE_NUMBER_MAX_LENGTH = 20
    const val EMAIL_MAX_LENGTH = 254

    const val APPOINTMENT_DURATION_MIN_MINUTES = 5
    const val APPOINTMENT_DURATION_MAX_MINUTES = 480 // 8 hours

    const val PAYMENT_AMOUNT_MAX = 999999.99

    // ================================
    // Financial Calculations
    // ================================
    const val MINUTES_PER_HOUR = 60.0

    // ================================
    // Payment Methods
    // ================================
    enum class PaymentMethod {
        CASH,
        TRANSFER,
        CHECK,
        OTHER
    }

    // ================================
    // Patient Status
    // ================================
    enum class PatientStatus {
        ACTIVE,
        INACTIVE
    }

    // ================================
    // Payment Status
    // ================================
    enum class PaymentStatus {
        PAID,
        PENDING
    }

    // ================================
    // CSV Export Constants
    // ================================
    const val CSV_EXPORT_BATCH_SIZE = 500
    const val CSV_EXPORT_TIMEOUT_SECONDS = 30

    // ================================
    // Performance Constants
    // ================================
    const val APP_STARTUP_TARGET_MILLIS = 2000 // 2 seconds
    const val DASHBOARD_CALCULATION_TARGET_MILLIS = 2000 // 2 seconds

    // ================================
    // Shared Preferences Keys (for non-sensitive config)
    // ================================
    const val PREF_LAST_BIOMETRIC_TIME = "last_biometric_time"
    const val PREF_SESSION_EXPIRY_TIME = "session_expiry_time"
    const val PREF_LAST_DATA_EXPORT_TIME = "last_data_export_time"

    // ================================
    // Intent/Bundle Keys
    // ================================
    const val BUNDLE_PATIENT_ID = "patient_id"
    const val BUNDLE_APPOINTMENT_ID = "appointment_id"
    const val BUNDLE_PAYMENT_ID = "payment_id"

    // ================================
    // Log Tags
    // ================================
    const val LOG_TAG_SECURITY = "FinancialApp.Security"
    const val LOG_TAG_DATABASE = "FinancialApp.Database"
    const val LOG_TAG_BIOMETRIC = "FinancialApp.Biometric"
    const val LOG_TAG_ENCRYPTION = "FinancialApp.Encryption"
    const val LOG_TAG_EXPORT = "FinancialApp.Export"
}
