package com.psychologist.financial.domain.models

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain model for Patient
 *
 * Clean business logic representation of a patient.
 * Separate from database entity (PatientEntity) for clean architecture.
 *
 * Architecture:
 * - Domain Layer: Business logic, no infrastructure concerns
 * - No Room annotations (not tied to database)
 * - No Jetpack dependencies
 * - Pure Kotlin, easily testable
 *
 * Mapping:
 * - PatientEntity (database) -> Patient (domain) via Repository
 * - Patient (domain) -> PatientEntity (database) via Repository
 * - UI uses Patient (domain model), not PatientEntity
 *
 * Benefits:
 * - Decouples business logic from database schema
 * - Easy to swap database implementation
 * - Type-safe and testable
 * - Domain models can have business methods
 *
 * Usage:
 * ```kotlin
 * val patient = Patient(
 *     id = 1,
 *     name = "João Silva",
 *     phone = "(11) 99999-9999",
 *     email = "joao@example.com",
 *     status = PatientStatus.ACTIVE,
 *     initialConsultDate = LocalDate.now(),
 *     registrationDate = LocalDate.now()
 * )
 *
 * if (patient.isActive) {
 *     // Allow new appointments
 * }
 *
 * val contact = patient.primaryContact  // Phone or email
 * ```
 *
 * @property id Unique identifier
 * @property name Patient full name
 * @property phone Optional phone number
 * @property email Optional email address
 * @property status ACTIVE or INACTIVE
 * @property initialConsultDate First consultation date
 * @property registrationDate When record created
 * @property lastAppointmentDate Most recent appointment (nullable)
 * @property createdDate Audit timestamp
 * @property appointmentCount Derived: number of appointments (optional)
 * @property totalBillableHours Derived: sum of appointment hours (optional)
 * @property amountDueNow Derived: outstanding balance (optional)
 */
data class Patient(
    /**
     * Unique identifier
     *
     * > 0 for saved patients, 0 for new patients.
     */
    val id: Long,

    /**
     * Patient full name
     *
     * Required, 2-200 characters.
     * Example: "Maria Santos Silva"
     */
    val name: String,

    /**
     * Patient phone number
     *
     * Optional, unique if provided.
     * Example: "(11) 99999-9999"
     */
    val phone: String? = null,

    /**
     * Patient email address
     *
     * Optional, unique if provided.
     * Example: "maria@example.com"
     */
    val email: String? = null,

    /**
     * Patient status
     *
     * ACTIVE: Can receive new appointments/payments
     * INACTIVE: Read-only, archived patient
     */
    val status: PatientStatus = PatientStatus.ACTIVE,

    /**
     * Initial consultation date
     *
     * When patient first consulted with psychologist.
     * Can be in the past.
     */
    val initialConsultDate: LocalDate,

    /**
     * Patient registration date
     *
     * When patient record created in system.
     * Immutable.
     */
    val registrationDate: LocalDate,

    /**
     * Most recent appointment date
     *
     * Auto-updated when new appointment created.
     * Null if no appointments yet.
     */
    val lastAppointmentDate: LocalDate? = null,

    /**
     * Record creation timestamp
     *
     * When patient record was created (audit).
     * Immutable.
     */
    val createdDate: LocalDateTime = LocalDateTime.now(),

    /**
     * Number of appointments (derived)
     *
     * Optional - loaded separately from appointments table.
     * Null if not loaded, 0+ if loaded.
     * Used for dashboard metrics.
     */
    val appointmentCount: Int? = null,

    /**
     * Total billable hours (derived)
     *
     * Sum of all appointment durations.
     * Optional - calculated from appointments.
     * Null if not loaded, Double >= 0 if loaded.
     */
    val totalBillableHours: Double? = null,

    /**
     * Amount due now (derived)
     *
     * Outstanding balance for overdue payments.
     * Optional - calculated from payments.
     * Null if not loaded, BigDecimal >= 0 if loaded.
     */
    val amountDueNow: java.math.BigDecimal? = null
) {
    /**
     * Check if patient is currently active
     *
     * @return true if status is ACTIVE
     */
    val isActive: Boolean
        get() = status == PatientStatus.ACTIVE

    /**
     * Check if patient is inactive
     *
     * @return true if status is INACTIVE
     */
    val isInactive: Boolean
        get() = !isActive

    /**
     * Check if patient has at least one contact method
     *
     * Business rule: Phone or email must exist.
     *
     * @return true if phone or email is not empty
     */
    fun hasContactInfo(): Boolean {
        return !phone.isNullOrEmpty() || !email.isNullOrEmpty()
    }

    /**
     * Get primary contact method
     *
     * Prefers phone, falls back to email.
     *
     * @return Phone if available, else email, else null
     */
    val primaryContact: String?
        get() = phone ?: email

    /**
     * Get patient name for display
     *
     * @return Trimmed name
     */
    fun getDisplayName(): String = name.trim()

    /**
     * Get patient initials
     *
     * @return First letter of first and last names (uppercase)
     * Example: "João Silva" -> "JS"
     */
    fun getInitials(): String {
        val names = name.trim().split("\\s+".toRegex())
        return if (names.size >= 2) {
            (names[0].firstOrNull()?.uppercase() ?: "") +
            (names.last().firstOrNull()?.uppercase() ?: "")
        } else {
            name.take(2).uppercase()
        }
    }

    /**
     * Get patient status display name (Portuguese)
     *
     * @return "Ativo" or "Inativo"
     */
    fun getStatusDisplayName(): String = when (status) {
        PatientStatus.ACTIVE -> "Ativo"
        PatientStatus.INACTIVE -> "Inativo"
    }

    /**
     * Check if patient has recent activity
     *
     * Last appointment within 30 days.
     *
     * @return true if lastAppointmentDate within last 30 days
     */
    fun hasRecentActivity(): Boolean {
        if (lastAppointmentDate == null) return false
        val thirtyDaysAgo = LocalDate.now().minusDays(30)
        return lastAppointmentDate.isAfter(thirtyDaysAgo)
    }

    /**
     * Get days since last appointment
     *
     * @return Days since lastAppointmentDate, or null if no appointments
     */
    fun getDaysSinceLastAppointment(): Long? {
        if (lastAppointmentDate == null) return null
        return java.time.temporal.ChronoUnit.DAYS.between(lastAppointmentDate, LocalDate.now())
    }

    /**
     * Check if patient data is complete
     *
     * For registration: name and contact required.
     *
     * @return true if all required fields populated
     */
    fun isDataComplete(): Boolean {
        return name.isNotEmpty() &&
                hasContactInfo() &&
                initialConsultDate != null &&
                registrationDate != null
    }

    /**
     * Check if patient was created today
     *
     * @return true if registrationDate is today
     */
    fun isNewPatient(): Boolean {
        return registrationDate == LocalDate.now()
    }

    companion object {
        /**
         * Create a test patient
         *
         * Useful for testing and fixtures.
         *
         * @param id Entity ID (0 = unsaved)
         * @param name Patient name
         * @param phone Optional phone
         * @param email Optional email
         * @param status ACTIVE or INACTIVE
         * @return Patient instance
         */
        fun createForTesting(
            id: Long = 0,
            name: String = "Test Patient",
            phone: String? = "(11) 99999-9999",
            email: String? = "test@example.com",
            status: PatientStatus = PatientStatus.ACTIVE
        ): Patient {
            val today = LocalDate.now()
            return Patient(
                id = id,
                name = name,
                phone = phone,
                email = email,
                status = status,
                initialConsultDate = today,
                registrationDate = today,
                lastAppointmentDate = null,
                createdDate = LocalDateTime.now()
            )
        }

        /**
         * Create a patient with full derived data
         *
         * For dashboard displays showing metrics.
         *
         * @param id Entity ID
         * @param name Patient name
         * @param phone Optional phone
         * @param email Optional email
         * @param appointmentCount Number of appointments
         * @param billableHours Total billable hours
         * @param amountDue Outstanding balance
         * @return Patient with derived data
         */
        fun createWithMetrics(
            id: Long,
            name: String,
            phone: String?,
            email: String?,
            appointmentCount: Int = 0,
            billableHours: Double = 0.0,
            amountDue: java.math.BigDecimal = java.math.BigDecimal.ZERO
        ): Patient {
            val today = LocalDate.now()
            return Patient(
                id = id,
                name = name,
                phone = phone,
                email = email,
                status = PatientStatus.ACTIVE,
                initialConsultDate = today,
                registrationDate = today,
                appointmentCount = appointmentCount,
                totalBillableHours = billableHours,
                amountDueNow = amountDue
            )
        }
    }
}

/**
 * Patient status enum
 *
 * ACTIVE: Patient receiving treatment
 * INACTIVE: Archived/inactive patient
 */
enum class PatientStatus {
    ACTIVE,
    INACTIVE
}
