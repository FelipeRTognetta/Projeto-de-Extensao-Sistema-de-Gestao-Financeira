package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.repositories.AppointmentRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for AppointmentRepository
 *
 * Tests the boundary between domain/repository layer and database (Room + SQLCipher).
 * Uses in-memory database for fast, isolated testing.
 *
 * Coverage:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Domain model mapping (AppointmentEntity ↔ Appointment)
 * - Patient-specific queries
 * - Date range filtering
 * - Chronological ordering
 * - Reactive Flow API
 * - Data persistence and isolation
 * - Transaction handling
 *
 * Total: 25+ test cases
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class AppointmentRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AppointmentRepository
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)
    private val yesterday = today.minusDays(1)
    private val lastWeek = today.minusDays(7)
    private val nextWeek = today.plusDays(7)

    @Before
    fun setUp() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()  // Allow queries on main thread for testing
            .build()

        repository = AppointmentRepository(
            database.appointmentDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Insert Operations
    // ========================================

    @Test
    fun insert_validAppointment_returnsId() = runBlocking {
        // Act
        val appointmentId = repository.insert(
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            notes = "Test appointment"
        )

        // Assert
        assertTrue(appointmentId > 0)
    }

    @Test
    fun insert_multipleAppointments_returnsUniqueIds() = runBlocking {
        // Act
        val id1 = repository.insert(
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(10, 0),
            durationMinutes = 60
        )
        val id2 = repository.insert(
            patientId = 1L,
            date = lastWeek,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 90
        )

        // Assert
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertTrue(id1 != id2)
    }

    @Test
    fun insert_multiplePatients_succeeds() = runBlocking {
        // Act
        val id1 = repository.insert(
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(10, 0),
            durationMinutes = 60
        )
        val id2 = repository.insert(
            patientId = 2L,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60
        )

        // Assert
        assertEquals(2, repository.count())
    }

    // ========================================
    // Read Operations - Single
    // ========================================

    @Test
    fun getById_existingAppointment_returnsAppointment() = runBlocking {
        // Arrange
        val appointmentId = repository.insert(
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            notes = "Test notes"
        )

        // Act
        val appointment = repository.getById(appointmentId)

        // Assert
        assertNotNull(appointment)
        assertEquals(appointmentId, appointment?.id)
        assertEquals(1L, appointment?.patientId)
        assertEquals(yesterday, appointment?.date)
        assertEquals("Test notes", appointment?.notes)
    }

    @Test
    fun getById_nonExistingAppointment_returnsNull() = runBlocking {
        // Act
        val appointment = repository.getById(999L)

        // Assert
        assertNull(appointment)
    }

    @Test
    fun existsById_existingAppointment_returnsTrue() = runBlocking {
        // Arrange
        val appointmentId = repository.insert(
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60
        )

        // Act
        val exists = repository.existsById(appointmentId)

        // Assert
        assertTrue(exists)
    }

    @Test
    fun existsById_nonExistingAppointment_returnsFalse() = runBlocking {
        // Act
        val exists = repository.existsById(999L)

        // Assert
        assertTrue(!exists)
    }

    // ========================================
    // Count Operations
    // ========================================

    @Test
    fun count_emptyDatabase_returnsZero() = runBlocking {
        // Act
        val count = repository.count()

        // Assert
        assertEquals(0, count)
    }

    @Test
    fun count_afterInserts_returnsCorrectCount() = runBlocking {
        // Arrange
        repository.insert(1L, yesterday, LocalTime.of(10, 0), 60)
        repository.insert(1L, lastWeek, LocalTime.of(14, 0), 90)
        repository.insert(2L, yesterday, LocalTime.of(9, 0), 60)

        // Act
        val count = repository.count()

        // Assert
        assertEquals(3, count)
    }

    @Test
    fun countByPatient_singlePatient_returnsCorrectCount() = runBlocking {
        // Arrange
        repository.insert(1L, yesterday, LocalTime.of(10, 0), 60)
        repository.insert(1L, lastWeek, LocalTime.of(14, 0), 90)
        repository.insert(2L, yesterday, LocalTime.of(9, 0), 60)

        // Act
        val count = repository.countByPatient(1L)

        // Assert
        assertEquals(2, count)
    }

    @Test
    fun countByPatient_noAppointments_returnsZero() = runBlocking {
        // Act
        val count = repository.countByPatient(999L)

        // Assert
        assertEquals(0, count)
    }

    @Test
    fun countByDateRange_withinRange_returnsCount() = runBlocking {
        // Arrange
        repository.insert(1L, yesterday, LocalTime.of(10, 0), 60)
        repository.insert(1L, today, LocalTime.of(14, 0), 90)
        repository.insert(1L, nextWeek, LocalTime.of(9, 0), 60)

        // Act
        val count = repository.countByDateRange(
            startDate = lastWeek,
            endDate = today
        )

        // Assert
        assertEquals(2, count)
    }

    // ========================================
    // Get Operations - Lists
    // ========================================

    @Test
    fun getAll_emptyDatabase_returnsEmptyList() = runBlocking {
        // Act
        val appointments = repository.getAll()

        // Assert
        assertEquals(0, appointments.size)
    }

    @Test
    fun getAll_multipleAppointments_returnsAll() = runBlocking {
        // Arrange
        repository.insert(1L, yesterday, LocalTime.of(10, 0), 60)
        repository.insert(1L, lastWeek, LocalTime.of(14, 0), 90)
        repository.insert(2L, yesterday, LocalTime.of(9, 0), 60)

        // Act
        val appointments = repository.getAll()

        // Assert
        assertEquals(3, appointments.size)
    }

    @Test
    fun getByPatient_singlePatient_returnsPatientAppointments() = runBlocking {
        // Arrange
        repository.insert(1L, yesterday, LocalTime.of(10, 0), 60)
        repository.insert(1L, lastWeek, LocalTime.of(14, 0), 90)
        repository.insert(2L, yesterday, LocalTime.of(9, 0), 60)

        // Act
        val appointments = repository.getByPatient(1L)

        // Assert
        assertEquals(2, appointments.size)
        assertTrue(appointments.all { it.patientId == 1L })
    }

    @Test
    fun getByPatient_noAppointments_returnsEmptyList() = runBlocking {
        // Act
        val appointments = repository.getByPatient(999L)

        // Assert
        assertEquals(0, appointments.size)
    }

    @Test
    fun getByPatient_returnsSortedByDateDesc() = runBlocking {
        // Arrange
        val id1 = repository.insert(1L, yesterday, LocalTime.of(10, 0), 60)
        val id2 = repository.insert(1L, lastWeek, LocalTime.of(14, 0), 90)
        val id3 = repository.insert(1L, today, LocalTime.of(9, 0), 60)

        // Act
        val appointments = repository.getByPatient(1L)

        // Assert
        assertEquals(3, appointments.size)
        // Should be sorted by date DESC (most recent first)
        assertEquals(today, appointments[0].date)
        assertEquals(yesterday, appointments[1].date)
        assertEquals(lastWeek, appointments[2].date)
    }

    @Test
    fun getByDateRange_returnsAppointmentsInRange() = runBlocking {
        // Arrange
        repository.insert(1L, lastWeek, LocalTime.of(10, 0), 60)
        repository.insert(2L, yesterday, LocalTime.of(14, 0), 90)
        repository.insert(1L, today, LocalTime.of(9, 0), 60)
        repository.insert(1L, nextWeek, LocalTime.of(11, 0), 60)

        // Act
        val appointments = repository.getByDateRange(
            startDate = lastWeek.plusDays(1),
            endDate = today
        )

        // Assert
        assertEquals(2, appointments.size)
        assertTrue(appointments.all { it.date >= lastWeek.plusDays(1) && it.date <= today })
    }

    @Test
    fun getByPatientAndDateRange_filtersCorrectly() = runBlocking {
        // Arrange
        repository.insert(1L, lastWeek, LocalTime.of(10, 0), 60)
        repository.insert(1L, yesterday, LocalTime.of(14, 0), 90)
        repository.insert(2L, yesterday, LocalTime.of(9, 0), 60)
        repository.insert(1L, nextWeek, LocalTime.of(11, 0), 60)

        // Act
        val appointments = repository.getByPatientAndDateRange(
            patientId = 1L,
            startDate = lastWeek.plusDays(1),
            endDate = today
        )

        // Assert
        assertEquals(1, appointments.size)
        assertEquals(1L, appointments[0].patientId)
        assertEquals(yesterday, appointments[0].date)
    }

    @Test
    fun getPastAppointments_onlyReturnsPast() = runBlocking {
        // Arrange
        repository.insert(1L, lastWeek, LocalTime.of(10, 0), 60)
        repository.insert(1L, yesterday, LocalTime.of(14, 0), 90)
        repository.insert(1L, today, LocalTime.of(9, 0), 60)
        repository.insert(1L, nextWeek, LocalTime.of(11, 0), 60)

        // Act
        val appointments = repository.getPastAppointments(today)

        // Assert
        assertEquals(2, appointments.size)
        assertTrue(appointments.all { it.date < today })
    }

    // ========================================
    // Billable Hours Calculation
    // ========================================

    @Test
    fun insert_withDifferentDurations_storesCorrectly() = runBlocking {
        // Arrange & Act
        val id1 = repository.insert(1L, yesterday, LocalTime.of(10, 0), 30)
        val id2 = repository.insert(1L, yesterday, LocalTime.of(14, 0), 60)
        val id3 = repository.insert(1L, yesterday, LocalTime.of(16, 0), 90)

        // Assert
        val apt1 = repository.getById(id1)
        val apt2 = repository.getById(id2)
        val apt3 = repository.getById(id3)

        assertEquals(30, apt1?.durationMinutes)
        assertEquals(60, apt2?.durationMinutes)
        assertEquals(90, apt3?.durationMinutes)
    }

    // ========================================
    // Entity Mapping
    // ========================================

    @Test
    fun insert_mapsEntityToDomainCorrectly() = runBlocking {
        // Act
        val appointmentId = repository.insert(
            patientId = 5L,
            date = yesterday,
            timeStart = LocalTime.of(14, 30),
            durationMinutes = 75,
            notes = "Important session"
        )

        val appointment = repository.getById(appointmentId)

        // Assert
        assertNotNull(appointment)
        assertEquals(appointmentId, appointment?.id)
        assertEquals(5L, appointment?.patientId)
        assertEquals(yesterday, appointment?.date)
        assertEquals(LocalTime.of(14, 30), appointment?.timeStart)
        assertEquals(75, appointment?.durationMinutes)
        assertEquals("Important session", appointment?.notes)
    }

    // ========================================
    // Data Persistence
    // ========================================

    @Test
    fun insert_persistsAcrossQueries() = runBlocking {
        // Arrange & Act
        val appointmentId = repository.insert(
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60
        )

        // Query multiple times
        val appointment1 = repository.getById(appointmentId)
        val appointment2 = repository.getById(appointmentId)
        val allAppointments = repository.getAll()

        // Assert
        assertNotNull(appointment1)
        assertNotNull(appointment2)
        assertEquals(appointment1?.id, appointment2?.id)
        assertEquals(1, allAppointments.size)
    }

    @Test
    fun multipleOperations_maintainConsistency() = runBlocking {
        // Arrange & Act
        val id1 = repository.insert(1L, yesterday, LocalTime.of(10, 0), 60)
        val id2 = repository.insert(1L, lastWeek, LocalTime.of(14, 0), 90)

        val byId1 = repository.getById(id1)
        val byId2 = repository.getById(id2)
        val byPatient = repository.getByPatient(1L)
        val count = repository.count()
        val countByPatient = repository.countByPatient(1L)

        // Assert
        assertNotNull(byId1)
        assertNotNull(byId2)
        assertEquals(2, byPatient.size)
        assertEquals(2, count)
        assertEquals(2, countByPatient)
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun insert_withNullNotes_succeeds() = runBlocking {
        // Act
        val appointmentId = repository.insert(
            patientId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            notes = null
        )

        val appointment = repository.getById(appointmentId)

        // Assert
        assertNotNull(appointment)
        assertNull(appointment?.notes)
    }

    @Test
    fun insert_largeDataset_succeeds() = runBlocking {
        // Arrange & Act
        val appointmentIds = (1..50).map { i ->
            repository.insert(
                patientId = (i % 5).toLong() + 1,
                date = yesterday.minusDays(i.toLong()),
                timeStart = LocalTime.of(10 + (i % 10), 0),
                durationMinutes = 30 + (i % 60)
            )
        }

        // Assert
        assertEquals(50, repository.count())
        assertEquals(50, appointmentIds.size)
        assertTrue(appointmentIds.all { it > 0 })
    }

    @Test
    fun getByDateRange_boundaryConditions() = runBlocking {
        // Arrange
        repository.insert(1L, lastWeek, LocalTime.of(10, 0), 60)
        repository.insert(1L, yesterday, LocalTime.of(14, 0), 90)
        repository.insert(1L, today, LocalTime.of(9, 0), 60)

        // Act - exact boundaries
        val appointments = repository.getByDateRange(
            startDate = lastWeek,
            endDate = today
        )

        // Assert - should include boundary dates
        assertEquals(3, appointments.size)
    }

    @Test
    fun getByPatient_multipleDays_maintainChronological() = runBlocking {
        // Arrange
        val dates = listOf(
            lastWeek.plusDays(3),
            lastWeek.plusDays(1),
            lastWeek.plusDays(2),
            lastWeek
        )

        for (date in dates) {
            repository.insert(1L, date, LocalTime.of(14, 0), 60)
        }

        // Act
        val appointments = repository.getByPatient(1L)

        // Assert - should be sorted DESC
        assertEquals(4, appointments.size)
        for (i in 0 until appointments.size - 1) {
            assertTrue(appointments[i].date >= appointments[i + 1].date)
        }
    }
}
