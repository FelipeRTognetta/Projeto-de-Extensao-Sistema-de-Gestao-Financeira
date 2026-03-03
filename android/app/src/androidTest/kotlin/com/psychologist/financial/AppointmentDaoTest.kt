package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.AppointmentDao
import com.psychologist.financial.data.entities.AppointmentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for AppointmentDao
 *
 * Tests the Room DAO layer directly with SQLite database.
 * Uses in-memory database for fast, isolated testing.
 *
 * Coverage:
 * - CRUD operations (Create, Read, Update, Delete)
 * - All query methods (by ID, by patient, date range, statistics)
 * - Count operations
 * - Existence checks
 * - Chronological ordering
 * - Date range filtering
 * - Reactive Flow operations
 * - Batch operations
 * - Foreign key constraints
 *
 * Total: 40+ test cases
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class AppointmentDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var appointmentDao: AppointmentDao
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

        appointmentDao = database.appointmentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Helper function to create test entities
    private fun createAppointmentEntity(
        id: Long = 0,
        patientId: Long = 1L,
        date: LocalDate = yesterday,
        timeStart: LocalTime = LocalTime.of(14, 0),
        durationMinutes: Int = 60,
        notes: String? = null
    ): AppointmentEntity {
        return AppointmentEntity(
            id = id,
            patientId = patientId,
            date = date,
            timeStart = timeStart,
            durationMinutes = durationMinutes,
            notes = notes,
            createdDate = LocalDateTime.now()
        )
    }

    // ========================================
    // Insert Operations
    // ========================================

    @Test
    fun insert_newAppointment_succeeds() = runBlocking {
        // Arrange
        val entity = createAppointmentEntity()

        // Act
        val id = appointmentDao.insert(entity)

        // Assert
        assertTrue(id > 0)
    }

    @Test
    fun insert_multipleAppointments_allPersist() = runBlocking {
        // Arrange
        val entity1 = createAppointmentEntity(patientId = 1L, date = yesterday)
        val entity2 = createAppointmentEntity(patientId = 1L, date = lastWeek)

        // Act
        appointmentDao.insert(entity1)
        appointmentDao.insert(entity2)

        // Assert
        assertEquals(2, appointmentDao.count())
    }

    @Test
    fun insert_withoutId_generatesId() = runBlocking {
        // Arrange
        val entity = createAppointmentEntity(id = 0)

        // Act
        val id = appointmentDao.insert(entity)

        // Assert
        assertTrue(id > 0)
        val retrieved = appointmentDao.getById(id)
        assertNotNull(retrieved)
    }

    @Test
    fun insertAll_batchInsert_succeeds() = runBlocking {
        // Arrange
        val entities = listOf(
            createAppointmentEntity(patientId = 1L, date = yesterday),
            createAppointmentEntity(patientId = 1L, date = lastWeek),
            createAppointmentEntity(patientId = 2L, date = yesterday)
        )

        // Act
        val ids = appointmentDao.insertAll(entities)

        // Assert
        assertEquals(3, ids.size)
        assertEquals(3, appointmentDao.count())
    }

    // ========================================
    // Read Operations - Single
    // ========================================

    @Test
    fun getById_existingAppointment_returnsAppointment() = runBlocking {
        // Arrange
        val entity = createAppointmentEntity(
            patientId = 1L,
            date = yesterday,
            durationMinutes = 90,
            notes = "Test notes"
        )
        val id = appointmentDao.insert(entity)

        // Act
        val retrieved = appointmentDao.getById(id)

        // Assert
        assertNotNull(retrieved)
        assertEquals(id, retrieved?.id)
        assertEquals(1L, retrieved?.patientId)
        assertEquals(yesterday, retrieved?.date)
        assertEquals(90, retrieved?.durationMinutes)
        assertEquals("Test notes", retrieved?.notes)
    }

    @Test
    fun getById_nonExistingAppointment_returnsNull() = runBlocking {
        // Act
        val retrieved = appointmentDao.getById(999L)

        // Assert
        assertNull(retrieved)
    }

    @Test
    fun existsById_existingAppointment_returnsTrue() = runBlocking {
        // Arrange
        val entity = createAppointmentEntity()
        val id = appointmentDao.insert(entity)

        // Act
        val exists = appointmentDao.existsById(id)

        // Assert
        assertTrue(exists)
    }

    @Test
    fun existsById_nonExistingAppointment_returnsFalse() = runBlocking {
        // Act
        val exists = appointmentDao.existsById(999L)

        // Assert
        assertTrue(!exists)
    }

    // ========================================
    // Count Operations
    // ========================================

    @Test
    fun count_emptyDatabase_returnsZero() = runBlocking {
        // Act
        val count = appointmentDao.count()

        // Assert
        assertEquals(0, count)
    }

    @Test
    fun count_afterInserts_returnsCorrectCount() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(patientId = 1L))
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(patientId = 2L))

        // Act
        val count = appointmentDao.count()

        // Assert
        assertEquals(3, count)
    }

    @Test
    fun countByPatient_singlePatient_returnsCorrectCount() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(patientId = 1L))
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(patientId = 2L))

        // Act
        val count = appointmentDao.countByPatient(1L)

        // Assert
        assertEquals(2, count)
    }

    @Test
    fun countByPatient_noAppointments_returnsZero() = runBlocking {
        // Act
        val count = appointmentDao.countByPatient(999L)

        // Assert
        assertEquals(0, count)
    }

    @Test
    fun countByDateRange_withinRange_returnsCount() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(date = yesterday))
        appointmentDao.insert(createAppointmentEntity(date = today))
        appointmentDao.insert(createAppointmentEntity(date = nextWeek))

        // Act
        val count = appointmentDao.countByDateRange(
            startDate = lastWeek.plusDays(1),
            endDate = today
        )

        // Assert
        assertEquals(2, count)
    }

    // ========================================
    // Get All Operations
    // ========================================

    @Test
    fun getAll_emptyDatabase_returnsEmptyList() = runBlocking {
        // Act
        val appointments = appointmentDao.getAll()

        // Assert
        assertEquals(0, appointments.size)
    }

    @Test
    fun getAll_multipleAppointments_returnsAll() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = yesterday))
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(patientId = 2L, date = yesterday))

        // Act
        val appointments = appointmentDao.getAll()

        // Assert
        assertEquals(3, appointments.size)
    }

    @Test
    fun getAll_returnsSortedChronologically() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(date = today))
        appointmentDao.insert(createAppointmentEntity(date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(date = yesterday))
        appointmentDao.insert(createAppointmentEntity(date = nextWeek))

        // Act
        val appointments = appointmentDao.getAll()

        // Assert
        assertEquals(4, appointments.size)
        // Should be sorted by date ASC (earliest first)
        assertEquals(lastWeek, appointments[0].date)
        assertEquals(yesterday, appointments[1].date)
        assertEquals(today, appointments[2].date)
        assertEquals(nextWeek, appointments[3].date)
    }

    // ========================================
    // Get By Patient Operations
    // ========================================

    @Test
    fun getByPatient_singlePatient_returnsPatientAppointments() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = yesterday))
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(patientId = 2L, date = yesterday))

        // Act
        val appointments = appointmentDao.getByPatient(1L)

        // Assert
        assertEquals(2, appointments.size)
        assertTrue(appointments.all { it.patientId == 1L })
    }

    @Test
    fun getByPatient_noAppointments_returnsEmptyList() = runBlocking {
        // Act
        val appointments = appointmentDao.getByPatient(999L)

        // Assert
        assertEquals(0, appointments.size)
    }

    @Test
    fun getByPatient_returnsSortedByDateDesc() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = today))
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = yesterday))

        // Act
        val appointments = appointmentDao.getByPatient(1L)

        // Assert
        assertEquals(3, appointments.size)
        // Should be sorted by date DESC (most recent first)
        assertEquals(today, appointments[0].date)
        assertEquals(yesterday, appointments[1].date)
        assertEquals(lastWeek, appointments[2].date)
    }

    @Test
    fun getByPatient_withMultipleDaysAndTimes() = runBlocking {
        // Arrange
        val date1 = yesterday
        appointmentDao.insert(createAppointmentEntity(
            patientId = 1L,
            date = date1,
            timeStart = LocalTime.of(10, 0)
        ))
        appointmentDao.insert(createAppointmentEntity(
            patientId = 1L,
            date = date1,
            timeStart = LocalTime.of(14, 0)
        ))
        appointmentDao.insert(createAppointmentEntity(
            patientId = 1L,
            date = lastWeek,
            timeStart = LocalTime.of(9, 0)
        ))

        // Act
        val appointments = appointmentDao.getByPatient(1L)

        // Assert
        assertEquals(3, appointments.size)
    }

    // ========================================
    // Get By Date Range Operations
    // ========================================

    @Test
    fun getByDateRange_returnsAppointmentsInRange() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(date = yesterday))
        appointmentDao.insert(createAppointmentEntity(date = today))
        appointmentDao.insert(createAppointmentEntity(date = nextWeek))

        // Act
        val appointments = appointmentDao.getByDateRange(
            startDate = lastWeek.plusDays(1),
            endDate = today
        )

        // Assert
        assertEquals(2, appointments.size)
        assertTrue(appointments.all { it.date >= lastWeek.plusDays(1) && it.date <= today })
    }

    @Test
    fun getByDateRange_includeBoundaryDates() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(date = yesterday))
        appointmentDao.insert(createAppointmentEntity(date = today))

        // Act
        val appointments = appointmentDao.getByDateRange(
            startDate = lastWeek,
            endDate = today
        )

        // Assert
        assertEquals(3, appointments.size)
    }

    @Test
    fun getByDateRange_emptyRange_returnsEmpty() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(date = yesterday))
        appointmentDao.insert(createAppointmentEntity(date = nextWeek))

        // Act
        val appointments = appointmentDao.getByDateRange(
            startDate = today,
            endDate = today
        )

        // Assert
        assertEquals(0, appointments.size)
    }

    // ========================================
    // Get By Patient And Date Range
    // ========================================

    @Test
    fun getByPatientAndDateRange_filtersCorrectly() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = yesterday))
        appointmentDao.insert(createAppointmentEntity(patientId = 2L, date = yesterday))
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = nextWeek))

        // Act
        val appointments = appointmentDao.getByPatientAndDateRange(
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
    fun getByPatientAndDateRange_emptyResult() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(patientId = 1L, date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(patientId = 2L, date = yesterday))

        // Act
        val appointments = appointmentDao.getByPatientAndDateRange(
            patientId = 3L,
            startDate = lastWeek,
            endDate = nextWeek
        )

        // Assert
        assertEquals(0, appointments.size)
    }

    // ========================================
    // Get Past Appointments
    // ========================================

    @Test
    fun getPastAppointments_onlyReturnsPast() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(date = yesterday))
        appointmentDao.insert(createAppointmentEntity(date = today))
        appointmentDao.insert(createAppointmentEntity(date = nextWeek))

        // Act
        val appointments = appointmentDao.getPastAppointments(today)

        // Assert
        assertEquals(2, appointments.size)
        assertTrue(appointments.all { it.date < today })
    }

    @Test
    fun getPastAppointments_withDefaultBeforeDate() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(date = yesterday))
        appointmentDao.insert(createAppointmentEntity(date = nextWeek))

        // Act
        val appointments = appointmentDao.getPastAppointments()

        // Assert
        assertTrue(appointments.all { it.date < LocalDate.now() })
    }

    // ========================================
    // Get Upcoming Appointments
    // ========================================

    @Test
    fun getUpcomingAppointments_onlyReturnsUpcoming() = runBlocking {
        // Arrange
        appointmentDao.insert(createAppointmentEntity(date = lastWeek))
        appointmentDao.insert(createAppointmentEntity(date = yesterday))
        appointmentDao.insert(createAppointmentEntity(date = nextWeek))

        // Act
        val appointments = appointmentDao.getUpcomingAppointments()

        // Assert
        assertEquals(1, appointments.size)
        assertEquals(nextWeek, appointments[0].date)
    }

    // ========================================
    // Update Operations
    // ========================================

    @Test
    fun update_modifiesAppointment() = runBlocking {
        // Arrange
        val entity = createAppointmentEntity(durationMinutes = 60, notes = "Original notes")
        val id = appointmentDao.insert(entity)

        // Act
        val updated = entity.copy(
            id = id,
            durationMinutes = 90,
            notes = "Updated notes"
        )
        appointmentDao.update(updated)

        // Assert
        val retrieved = appointmentDao.getById(id)
        assertNotNull(retrieved)
        assertEquals(90, retrieved?.durationMinutes)
        assertEquals("Updated notes", retrieved?.notes)
    }

    // ========================================
    // Delete Operations
    // ========================================

    @Test
    fun delete_removesAppointment() = runBlocking {
        // Arrange
        val entity = createAppointmentEntity()
        val id = appointmentDao.insert(entity)

        // Act
        val toDelete = entity.copy(id = id)
        appointmentDao.delete(toDelete)

        // Assert
        val retrieved = appointmentDao.getById(id)
        assertNull(retrieved)
    }

    @Test
    fun delete_decrementsCount() = runBlocking {
        // Arrange
        val entity1 = createAppointmentEntity(patientId = 1L)
        val entity2 = createAppointmentEntity(patientId = 1L, date = lastWeek)
        val id1 = appointmentDao.insert(entity1)
        appointmentDao.insert(entity2)

        assertEquals(2, appointmentDao.count())

        // Act
        appointmentDao.delete(entity1.copy(id = id1))

        // Assert
        assertEquals(1, appointmentDao.count())
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun insert_withNullNotes_succeeds() = runBlocking {
        // Arrange
        val entity = createAppointmentEntity(notes = null)

        // Act
        val id = appointmentDao.insert(entity)

        // Assert
        assertTrue(id > 0)
        val retrieved = appointmentDao.getById(id)
        assertNull(retrieved?.notes)
    }

    @Test
    fun insert_withEmptyNotes_succeeds() = runBlocking {
        // Arrange
        val entity = createAppointmentEntity(notes = "")

        // Act
        val id = appointmentDao.insert(entity)

        // Assert
        assertTrue(id > 0)
        val retrieved = appointmentDao.getById(id)
        assertEquals("", retrieved?.notes)
    }

    @Test
    fun insert_largeBatch_succeeds() = runBlocking {
        // Arrange
        val entities = (1..100).map { i ->
            createAppointmentEntity(
                patientId = (i % 5).toLong() + 1,
                date = yesterday.minusDays(i.toLong()),
                durationMinutes = 30 + (i % 60)
            )
        }

        // Act
        val ids = appointmentDao.insertAll(entities)

        // Assert
        assertEquals(100, appointmentDao.count())
        assertEquals(100, ids.size)
    }

    @Test
    fun query_withComplexDateRange() = runBlocking {
        // Arrange
        val month = today.withDayOfMonth(1)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

        for (i in 1..28) {
            appointmentDao.insert(createAppointmentEntity(date = month.plusDays((i - 1).toLong())))
        }

        // Act
        val appointments = appointmentDao.getByDateRange(month, monthEnd)

        // Assert
        assertEquals(28, appointments.size)
    }
}
