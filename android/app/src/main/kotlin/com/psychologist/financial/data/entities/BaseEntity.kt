package com.psychologist.financial.data.entities

import androidx.room.ColumnInfo
import androidx.room.Ignore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Abstract base entity for all Room database entities
 *
 * Provides common fields and utilities for database models:
 * - Auto-increment ID generation
 * - Immutable creation timestamp
 * - Consistent field naming conventions
 * - Utility methods for serialization/debugging
 *
 * Architecture:
 * - Entity layer: Database tables (Room entities, immutable after creation)
 * - Domain layer: Business models (clean separation, no DB annotations)
 * - Repository: Mapping between layers
 *
 * Inheritance Pattern:
 * ```kotlin
 * @Entity(tableName = "patient")
 * data class PatientEntity(
 *     override val id: Long = 0,
 *     val name: String,
 *     val phone: String?,
 *     // ... other fields
 *     override val createdDate: LocalDateTime = LocalDateTime.now()
 * ) : BaseEntity()
 * ```
 *
 * Key Principles:
 * - Each entity must have a numeric ID (Long, auto-increment)
 * - Created timestamp is immutable (set at insertion, never updated)
 * - All entities inherit these fields for consistency
 * - Database timestamps use LocalDateTime (converted via TypeConverters)
 *
 * @property id Unique identifier (primary key, auto-increment)
 * @property createdDate Immutable creation timestamp
 */
abstract class BaseEntity {

    /**
     * Unique identifier for this entity
     *
     * - Auto-increment: Room generates new IDs automatically
     * - Never modified after creation
     * - Used as primary key in database
     * - Default 0 indicates unsaved entity (Room will generate ID on insert)
     *
     * Room will auto-assign on first insert:
     * ```kotlin
     * val patient = PatientEntity(id = 0, name = "John", ...)
     * val generatedId = dao.insert(patient)  // Returns generated ID
     * ```
     */
    abstract val id: Long

    /**
     * Immutable creation timestamp
     *
     * - Set automatically when entity is first created
     * - Never updated after initial insertion
     * - Useful for audit trails and sorting (newest first)
     * - Stored in database as TEXT (Room TypeConverter handles LocalDateTime)
     *
     * Default implementation uses system time:
     * ```kotlin
     * override val createdDate: LocalDateTime = LocalDateTime.now()
     * ```
     *
     * For testing, override with fixed value:
     * ```kotlin
     * override val createdDate: LocalDateTime = LocalDateTime.of(2026, 2, 25, 14, 30)
     * ```
     */
    abstract val createdDate: LocalDateTime

    /**
     * Check if this entity has been saved to database
     *
     * An unsaved entity has ID = 0 (default value).
     * After insert, Room generates a new ID > 0.
     *
     * @return true if entity exists in database, false if newly created
     *
     * Usage:
     * ```kotlin
     * val entity = PatientEntity(name = "John")
     * if (!entity.isSaved()) {
     *     dao.insert(entity)  // Entity still has id=0 before this line
     * }
     * ```
     */
    fun isSaved(): Boolean = id > 0L

    /**
     * Format created date as ISO 8601 string
     *
     * Useful for logging, debugging, and serialization.
     *
     * @return ISO 8601 formatted timestamp (e.g., "2026-02-25T14:30:00")
     *
     * Example:
     * ```kotlin
     * Log.d("Entity", "Created: ${entity.getCreatedDateFormatted()}")
     * ```
     */
    fun getCreatedDateFormatted(): String {
        return createdDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    /**
     * Create a debug string representation
     *
     * Shows key identifying information for logging.
     * Override in subclasses for entity-specific details.
     *
     * @return String suitable for Log.d() calls
     *
     * Example output: "[PatientEntity id=1, created=2026-02-25T14:30:00]"
     */
    open fun toDebugString(): String {
        return "[${this::class.simpleName} id=$id, created=${getCreatedDateFormatted()}]"
    }

    companion object {
        /**
         * Constant for unsaved entity ID
         *
         * Used as default when creating new entities before insertion.
         * Room will generate the actual ID on insert.
         */
        const val UNSAVED_ID = 0L

        /**
         * Parse LocalDateTime from ISO string
         *
         * Utility for deserialization.
         *
         * @param isoString ISO 8601 formatted string
         * @return Parsed LocalDateTime
         */
        fun parseCreatedDate(isoString: String): LocalDateTime {
            return LocalDateTime.parse(isoString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
    }
}
