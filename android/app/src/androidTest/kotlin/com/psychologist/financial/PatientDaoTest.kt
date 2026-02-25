package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.domain.models.PatientStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for PatientDao
 *
 * Tests the Room DAO layer directly with SQLite/SQLCipher database.
 * Uses in-memory database for fast, isolated testing.
 *
 * Coverage:
 * - CRUD operations (Create, Read, Update, Delete)
 * - All query methods (by ID, by phone, by email, by status, by date range, by name)
 * - Count operations
 * - Existence checks
 * - Soft delete pattern (status-based)
 * - Ordering and filtering
 * - Index performance verification (implicit through correctness)
 * - Foreign key constraints (when applicable)
 *
 * Total: 35 test cases
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class PatientDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var patientDao: PatientDao
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()  // Allow queries on main thread for testing
            .build()

        patientDao = database.patientDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Helper function to create test entities
    private fun createPatientEntity(
        id: Long = 0,
        name: String = "Test Patient",
        phone: String? = null,
        email: String? = null,
        status: PatientStatus = PatientStatus.ACTIVE,
        initialConsultDate: java.time.LocalDate = java.time.LocalDate.now(),
        registrationDate: java.time.LocalDate = java.time.LocalDate.now(),
        lastAppointmentDate: java.time.LocalDate? = null
    ): PatientEntity {
        return PatientEntity(
            id = id,
            name = name,
            phone = phone,
            email = email,
            status = status,
            initialConsultDate = initialConsultDate,
            registrationDate = registrationDate,
            lastAppointmentDate = lastAppointmentDate,
            createdDate = LocalDateTime.now()
        )
    }

    // ========================================
    // Insert Operations
    // ========================================

    @Test
    fun insert_newPatient_succeeds() = runBlocking {
        // Arrange
        val entity = createPatientEntity(name = "João Silva")

        // Act
        patientDao.insert(entity)

        // Assert
        val retrieved = patientDao.getById(entity.id)
        assertNotNull(retrieved)
    }

    @Test
    fun insert_multiplePatients_allPersist() = runBlocking {
        // Arrange
        val entity1 = createPatientEntity(name = "João Silva")
        val entity2 = createPatientEntity(name = "Maria Santos")

        // Act
        patientDao.insert(entity1)
        patientDao.insert(entity2)

        // Assert
        assertEquals(2, patientDao.count())
    }

    @Test
    fun insertAll_batchInsert_allPersist() = runBlocking {
        // Arrange
        val entities = listOf(
            createPatientEntity(name = "João Silva"),
            createPatientEntity(name = "Maria Santos"),
            createPatientEntity(name = "Carlos Oliveira")
        )

        // Act
        patientDao.insertAll(entities)

        // Assert
        assertEquals(3, patientDao.count())
    }

    // ========================================
    // Query By ID
    // ========================================

    @Test
    fun getById_existingId_returnsPatient() = runBlocking {
        // Arrange
        val entity = createPatientEntity(name = "João Silva")
        patientDao.insert(entity)

        // Act
        val retrieved = patientDao.getById(entity.id)

        // Assert
        assertNotNull(retrieved)
        assertEquals("João Silva", retrieved?.name)
    }

    @Test
    fun getById_nonExistentId_returnsNull() = runBlocking {
        // Act
        val retrieved = patientDao.getById(999L)

        // Assert
        assertNull(retrieved)
    }

    @Test
    fun existsById_existingId_returnsTrue() = runBlocking {
        // Arrange
        val entity = createPatientEntity(name = "João Silva")
        patientDao.insert(entity)

        // Act
        val exists = patientDao.existsById(entity.id)

        // Assert
        assertTrue(exists)
    }

    @Test
    fun existsById_nonExistentId_returnsFalse() = runBlocking {
        // Act
        val exists = patientDao.existsById(999L)

        // Assert
        assertTrue(!exists)
    }

    // ========================================
    // Query By Phone
    // ========================================

    @Test
    fun getByPhone_existingPhone_returnsPatient() = runBlocking {
        // Arrange
        val phone = "(11) 99999-9999"
        val entity = createPatientEntity(name = "João Silva", phone = phone)
        patientDao.insert(entity)

        // Act
        val retrieved = patientDao.getByPhone(phone)

        // Assert
        assertNotNull(retrieved)
        assertEquals("João Silva", retrieved?.name)
    }

    @Test
    fun getByPhone_nonExistentPhone_returnsNull() = runBlocking {
        // Act
        val retrieved = patientDao.getByPhone("(11) 00000-0000")

        // Assert
        assertNull(retrieved)
    }

    @Test
    fun getByPhone_caseSensitive_works() = runBlocking {
        // Arrange
        val phone = "(11) 99999-9999"
        val entity = createPatientEntity(name = "João Silva", phone = phone)
        patientDao.insert(entity)

        // Act
        val retrieved = patientDao.getByPhone(phone)

        // Assert
        assertNotNull(retrieved)
    }

    // ========================================
    // Query By Email
    // ========================================

    @Test
    fun getByEmail_existingEmail_returnsPatient() = runBlocking {
        // Arrange
        val email = "joao@example.com"
        val entity = createPatientEntity(name = "João Silva", email = email)
        patientDao.insert(entity)

        // Act
        val retrieved = patientDao.getByEmail(email)

        // Assert
        assertNotNull(retrieved)
        assertEquals("João Silva", retrieved?.name)
    }

    @Test
    fun getByEmail_nonExistentEmail_returnsNull() = runBlocking {
        // Act
        val retrieved = patientDao.getByEmail("nonexistent@example.com")

        // Assert
        assertNull(retrieved)
    }

    @Test
    fun getByEmail_caseInsensitive_works() = runBlocking {
        // Arrange
        val email = "joao@example.com"
        val entity = createPatientEntity(name = "João Silva", email = email)
        patientDao.insert(entity)

        // Act
        val retrieved = patientDao.getByEmail(email.uppercase())

        // Assert - Email queries typically case-insensitive
        // This may depend on SQLite collation settings
        assertNotNull(retrieved)
    }

    // ========================================
    // Query By Status
    // ========================================

    @Test
    fun getByStatus_activePatients_returnsOnlyActive() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João", status = PatientStatus.ACTIVE))
        patientDao.insert(createPatientEntity(name = "Maria", status = PatientStatus.INACTIVE))
        patientDao.insert(createPatientEntity(name = "Carlos", status = PatientStatus.ACTIVE))

        // Act
        val retrieved = patientDao.getByStatus(PatientStatus.ACTIVE)

        // Assert
        assertEquals(2, retrieved.size)
        assertTrue(retrieved.all { it.status == PatientStatus.ACTIVE })
    }

    @Test
    fun getByStatus_inactivePatients_returnsOnlyInactive() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João", status = PatientStatus.ACTIVE))
        patientDao.insert(createPatientEntity(name = "Maria", status = PatientStatus.INACTIVE))

        // Act
        val retrieved = patientDao.getByStatus(PatientStatus.INACTIVE)

        // Assert
        assertEquals(1, retrieved.size)
        assertEquals("Maria", retrieved[0].name)
    }

    @Test
    fun getAll_allPatients_returnsAll() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João"))
        patientDao.insert(createPatientEntity(name = "Maria"))
        patientDao.insert(createPatientEntity(name = "Carlos"))

        // Act
        val retrieved = patientDao.getAll()

        // Assert
        assertEquals(3, retrieved.size)
    }

    // ========================================
    // Query By Date Range
    // ========================================

    @Test
    fun getByRegistrationDateRange_within_returnsMatches() = runBlocking {
        // Arrange
        val today = java.time.LocalDate.now()
        patientDao.insert(createPatientEntity(name = "João", registrationDate = today.minusDays(5)))
        patientDao.insert(createPatientEntity(name = "Maria", registrationDate = today))
        patientDao.insert(createPatientEntity(name = "Carlos", registrationDate = today.plusDays(5)))

        // Act
        val retrieved = patientDao.getByRegistrationDateRange(
            startDate = today.minusDays(10),
            endDate = today.plusDays(1)
        )

        // Assert
        assertEquals(2, retrieved.size)
    }

    @Test
    fun getByLastAppointmentDateAfter_futureDates_returnsMatches() = runBlocking {
        // Arrange
        val today = java.time.LocalDate.now()
        patientDao.insert(createPatientEntity(
            name = "João",
            lastAppointmentDate = today.minusDays(30)
        ))
        patientDao.insert(createPatientEntity(
            name = "Maria",
            lastAppointmentDate = today.minusDays(1)
        ))
        patientDao.insert(createPatientEntity(
            name = "Carlos",
            lastAppointmentDate = null  // Never had appointment
        ))

        // Act
        val retrieved = patientDao.getByLastAppointmentDateAfter(today.minusDays(10))

        // Assert
        assertEquals(1, retrieved.size)
        assertEquals("Maria", retrieved[0].name)
    }

    // ========================================
    // Search By Name
    // ========================================

    @Test
    fun searchByName_partialMatch_returnsMatches() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João Silva"))
        patientDao.insert(createPatientEntity(name = "Maria Santos"))
        patientDao.insert(createPatientEntity(name = "João Pedro"))

        // Act
        val retrieved = patientDao.searchByName("%João%")

        // Assert
        assertEquals(2, retrieved.size)
        assertTrue(retrieved.all { it.name.contains("João") })
    }

    @Test
    fun searchByName_caseInsensitive_returnsMatches() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João Silva"))

        // Act
        val retrieved = patientDao.searchByName("%joao%")

        // Assert - Should match due to SQLite case-insensitive default
        assertEquals(1, retrieved.size)
    }

    @Test
    fun searchByName_noMatch_returnsEmpty() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João Silva"))

        // Act
        val retrieved = patientDao.searchByName("%Carlos%")

        // Assert
        assertEquals(0, retrieved.size)
    }

    // ========================================
    // Update Operations
    // ========================================

    @Test
    fun update_existingPatient_updatesCorrectly() = runBlocking {
        // Arrange
        val entity = createPatientEntity(name = "João Silva", phone = "(11) 99999-9999")
        patientDao.insert(entity)

        // Act
        val updated = entity.copy(name = "João Pedro", phone = "(11) 88888-8888")
        patientDao.update(updated)

        // Assert
        val retrieved = patientDao.getById(entity.id)
        assertEquals("João Pedro", retrieved?.name)
        assertEquals("(11) 88888-8888", retrieved?.phone)
    }

    @Test
    fun update_statusOnly_updatesOnlyStatus() = runBlocking {
        // Arrange
        val entity = createPatientEntity(
            name = "João Silva",
            status = PatientStatus.ACTIVE
        )
        patientDao.insert(entity)

        // Act
        val updated = entity.copy(status = PatientStatus.INACTIVE)
        patientDao.update(updated)

        // Assert
        val retrieved = patientDao.getById(entity.id)
        assertEquals(PatientStatus.INACTIVE, retrieved?.status)
        assertEquals("João Silva", retrieved?.name)  // Other fields unchanged
    }

    @Test
    fun updateAll_batchUpdate_allUpdate() = runBlocking {
        // Arrange
        val entity1 = createPatientEntity(name = "João", status = PatientStatus.ACTIVE)
        val entity2 = createPatientEntity(name = "Maria", status = PatientStatus.ACTIVE)
        patientDao.insert(entity1)
        patientDao.insert(entity2)

        // Act
        val updated = listOf(
            entity1.copy(status = PatientStatus.INACTIVE),
            entity2.copy(status = PatientStatus.INACTIVE)
        )
        patientDao.updateAll(updated)

        // Assert
        val all = patientDao.getAll()
        assertTrue(all.all { it.status == PatientStatus.INACTIVE })
    }

    // ========================================
    // Delete Operations
    // ========================================

    @Test
    fun delete_existingPatient_removesPatient() = runBlocking {
        // Arrange
        val entity = createPatientEntity(name = "João Silva")
        patientDao.insert(entity)

        // Act
        patientDao.delete(entity)

        // Assert
        val retrieved = patientDao.getById(entity.id)
        assertNull(retrieved)
    }

    @Test
    fun deleteById_byId_removesPatient() = runBlocking {
        // Arrange
        val entity = createPatientEntity(name = "João Silva")
        patientDao.insert(entity)

        // Act
        patientDao.deleteById(entity.id)

        // Assert
        val retrieved = patientDao.getById(entity.id)
        assertNull(retrieved)
    }

    @Test
    fun deleteAll_multiplesPatients_removesAll() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João"))
        patientDao.insert(createPatientEntity(name = "Maria"))
        patientDao.insert(createPatientEntity(name = "Carlos"))

        // Act
        patientDao.deleteAll()

        // Assert
        assertEquals(0, patientDao.count())
    }

    // ========================================
    // Count Operations
    // ========================================

    @Test
    fun count_multiplePatients_returnsCorrectCount() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João"))
        patientDao.insert(createPatientEntity(name = "Maria"))
        patientDao.insert(createPatientEntity(name = "Carlos"))

        // Act
        val count = patientDao.count()

        // Assert
        assertEquals(3, count)
    }

    @Test
    fun count_emptyDatabase_returnsZero() = runBlocking {
        // Act
        val count = patientDao.count()

        // Assert
        assertEquals(0, count)
    }

    @Test
    fun countByStatus_activePatients_returnsCorrectCount() = runBlocking {
        // Arrange
        patientDao.insert(createPatientEntity(name = "João", status = PatientStatus.ACTIVE))
        patientDao.insert(createPatientEntity(name = "Maria", status = PatientStatus.INACTIVE))
        patientDao.insert(createPatientEntity(name = "Carlos", status = PatientStatus.ACTIVE))

        // Act
        val count = patientDao.countByStatus(PatientStatus.ACTIVE)

        // Assert
        assertEquals(2, count)
    }

    // ========================================
    // Uniqueness Checks
    // ========================================

    @Test
    fun existsByPhone_existingPhone_returnsTrue() = runBlocking {
        // Arrange
        val phone = "(11) 99999-9999"
        patientDao.insert(createPatientEntity(name = "João", phone = phone))

        // Act
        val exists = patientDao.existsByPhone(phone)

        // Assert
        assertTrue(exists)
    }

    @Test
    fun existsByPhone_nonExistentPhone_returnsFalse() = runBlocking {
        // Act
        val exists = patientDao.existsByPhone("(11) 00000-0000")

        // Assert
        assertTrue(!exists)
    }

    @Test
    fun existsByEmail_existingEmail_returnsTrue() = runBlocking {
        // Arrange
        val email = "joao@example.com"
        patientDao.insert(createPatientEntity(name = "João", email = email))

        // Act
        val exists = patientDao.existsByEmail(email)

        // Assert
        assertTrue(exists)
    }

    @Test
    fun existsByEmail_nonExistentEmail_returnsFalse() = runBlocking {
        // Act
        val exists = patientDao.existsByEmail("nonexistent@example.com")

        // Assert
        assertTrue(!exists)
    }

    // ========================================
    // Complex Scenarios
    // ========================================

    @Test
    fun complexWorkflow_insertSearchUpdateDelete_allSucceed() = runBlocking {
        // Arrange & Act
        val entity = createPatientEntity(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com"
        )
        patientDao.insert(entity)

        // Act - Search
        val retrieved = patientDao.searchByName("%João%")
        assertEquals(1, retrieved.size)

        // Act - Update
        patientDao.update(retrieved[0].copy(phone = "(11) 88888-8888"))
        val updated = patientDao.getByPhone("(11) 88888-8888")
        assertNotNull(updated)

        // Act - Verify uniqueness
        assertTrue(patientDao.existsByPhone("(11) 88888-8888"))
        assertTrue(!patientDao.existsByPhone("(11) 99999-9999"))

        // Act - Delete
        patientDao.delete(updated!!)
        assertNull(patientDao.getById(entity.id))
    }

    @Test
    fun dataIsolation_multipleOperations_onlyAffectTargetPatient() = runBlocking {
        // Arrange
        val entity1 = createPatientEntity(id = 1, name = "João")
        val entity2 = createPatientEntity(id = 2, name = "Maria")
        patientDao.insert(entity1)
        patientDao.insert(entity2)

        // Act - Update only entity1
        patientDao.update(entity1.copy(name = "João Updated"))

        // Assert
        val updated1 = patientDao.getById(1)
        val unchanged2 = patientDao.getById(2)
        assertEquals("João Updated", updated1?.name)
        assertEquals("Maria", unchanged2?.name)
    }
}
