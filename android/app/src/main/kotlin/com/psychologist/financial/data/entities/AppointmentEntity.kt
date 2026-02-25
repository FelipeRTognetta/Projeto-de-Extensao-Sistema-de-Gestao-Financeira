package com.psychologist.financial.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Appointment database entity
 *
 * Represents a psychotherapy session appointment record.
 * Links to Patient via foreign key with cascading delete.
 *
 * Database Constraints:
 * - id: Primary key (auto-increment)
 * - patient_id: Foreign key to PatientEntity, cascading delete
 * - date: Appointment date (not nullable)
 * - time_start: Session start time (not nullable)
 * - duration_minutes: Session duration in minutes (not nullable)
 * - notes: Optional session notes
 * - created_date: Record creation timestamp
 *
 * Indexes:
 * - patient_id (for fast patient appointment queries)
 * - (patient_id, date DESC) (for filtered date range queries)
 * - date (for timeline queries)
 * - created_date (for recent appointments)
 *
 * Relationships:
 * - belongs_to: Patient (patient_id → PatientEntity.id)
 * - inverse: Patient has many Appointments
 * - cascade_delete: Deleting patient deletes all appointments
 *
 * Business Rules:
 * - Duration: 5 minutes minimum, 480 minutes maximum (8 hours)
 * - Date: Cannot be in future (enforced in use case)
 * - Time: Valid 24-hour format (00:00-23:59)
 * - Notes: Optional, up to 1000 characters
 *
 * Billable Hours Calculation:
 * - billable_hours = duration_minutes / 60.0
 * - Only counts completed (past) appointments
 *
 * Sorting:
 * - Chronological: date ASC, time_start ASC (default)
 * - Recent: created_date DESC
 * - By Patient: patient_id ASC, date DESC
 *
 * Example:
 * ```kotlin
 * val entity = AppointmentEntity(
 *     patientId = 1L,
 *     date = LocalDate.of(2024, 3, 15),
 *     timeStart = LocalTime.of(14, 30),
 *     durationMinutes = 60,
 *     notes = "Discussed anxiety management strategies"
 * )
 * ```
 */
@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_patient_id", value = ["patient_id"]),
        Index(name = "idx_patient_date", value = ["patient_id", "date"], orders = [androidx.room.Index.Order.ASC, androidx.room.Index.Order.DESC]),
        Index(name = "idx_date", value = ["date"]),
        Index(name = "idx_created_date", value = ["created_date"], orders = [androidx.room.Index.Order.DESC])
    ]
)
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "patient_id")
    val patientId: Long,

    @ColumnInfo(name = "date")
    val date: LocalDate,

    @ColumnInfo(name = "time_start")
    val timeStart: LocalTime,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_date")
    val createdDate: LocalDateTime = LocalDateTime.now()
) : BaseEntity(id = id, createdDate = createdDate) {

    /**
     * Calculate billable hours for this appointment
     *
     * @return Hours as decimal (e.g., 1.5 for 90 minutes)
     */
    fun getBillableHours(): Double = durationMinutes / 60.0

    /**
     * Get appointment display time string
     *
     * Format: "HH:mm" (e.g., "14:30")
     *
     * @return Formatted time string
     */
    fun getTimeDisplay(): String {
        val hour = timeStart.hour.toString().padStart(2, '0')
        val minute = timeStart.minute.toString().padStart(2, '0')
        return "$hour:$minute"
    }

    /**
     * Get appointment display duration string
     *
     * Format: "1h 30min" or just "45min"
     *
     * @return Formatted duration string
     */
    fun getDurationDisplay(): String {
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
            hours > 0 -> "${hours}h"
            else -> "${minutes}min"
        }
    }

    /**
     * Get end time by adding duration to start time
     *
     * @return End time (LocalTime)
     */
    fun getEndTime(): LocalTime {
        return timeStart.plusMinutes(durationMinutes.toLong())
    }

    /**
     * Check if appointment is in the past
     *
     * Compares appointment date/time with current date/time.
     *
     * @return true if appointment has passed
     */
    fun isPast(): Boolean {
        val now = LocalDateTime.now()
        val appointmentDateTime = java.time.LocalDateTime.of(date, timeStart)
        return appointmentDateTime.isBefore(now)
    }

    /**
     * Check if appointment is today
     *
     * @return true if appointment is today
     */
    fun isToday(): Boolean {
        return date == LocalDate.now()
    }

    /**
     * Check if appointment is in the future
     *
     * @return true if appointment hasn't occurred yet
     */
    fun isFuture(): Boolean {
        val now = LocalDateTime.now()
        val appointmentDateTime = java.time.LocalDateTime.of(date, timeStart)
        return appointmentDateTime.isAfter(now)
    }

    /**
     * Get days until appointment
     *
     * Returns negative values for past appointments.
     *
     * @return Number of days until appointment
     */
    fun getDaysUntilAppointment(): Long {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)
    }

    /**
     * Check if appointment overlaps with another
     *
     * Useful for scheduling validation (ensure no double-booking).
     *
     * @param other Other appointment to check against
     * @return true if time ranges overlap on same date
     */
    fun overlaps(other: AppointmentEntity): Boolean {
        if (patientId != other.patientId || date != other.date) {
            return false
        }

        val thisEnd = getEndTime()
        val otherEnd = other.getEndTime()

        // Check if time ranges overlap
        return timeStart < otherEnd && thisEnd > other.timeStart
    }

    /**
     * Validate appointment constraints
     *
     * Checks business rules (duration range, date not future, etc.)
     *
     * @return true if appointment meets all constraints
     */
    fun isValid(): Boolean {
        // Duration must be between 5 minutes and 8 hours
        if (durationMinutes < 5 || durationMinutes > 480) {
            return false
        }

        // Date must not be in future (use case handles this)
        // but we allow past dates for historical data entry

        // Patient ID must be positive
        if (patientId <= 0) {
            return false
        }

        return true
    }

    companion object {
        const val TABLE_NAME = "appointments"
        const val COLUMN_ID = "id"
        const val COLUMN_PATIENT_ID = "patient_id"
        const val COLUMN_DATE = "date"
        const val COLUMN_TIME_START = "time_start"
        const val COLUMN_DURATION_MINUTES = "duration_minutes"
        const val COLUMN_NOTES = "notes"
        const val COLUMN_CREATED_DATE = "created_date"

        // Constraints
        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 480  // 8 hours
        const val NOTES_MAX_LENGTH = 1000
    }
}
