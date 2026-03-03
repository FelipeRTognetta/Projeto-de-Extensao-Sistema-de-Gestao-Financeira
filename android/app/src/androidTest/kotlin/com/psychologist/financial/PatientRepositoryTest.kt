package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.PatientStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for PatientRepository
 *
 * Tests the boundary between domain/repository layer and database (Room + SQLCipher).
 * Uses in-memory database for fast, isolated testing.
 *
 * Coverage:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Domain model mapping (PatientEntity ↔ Patient)
 * - Uniqueness constraints (phone, email)
 * - Soft delete pattern (status-based)
 * - Query operations (by ID, by phone, by email, search by name)
 * - Data persistence and isolation
 * - Transaction handling
 *
 * Total: 22 test cases
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class PatientRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PatientRepository
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

        repository = PatientRepository(
            database.patientDao(),
            mockEncryptionService = null  // No encryption needed for test data
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Create Operations
    // ========================================

    @Test
    fun insert_validPatient_returnId() = runBlocking {
        // Act
        val patientId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Assert
        assertTrue(patientId > 0)
    }

    @Test
    fun insert_multiplePatients_returnsUniqueIds() = runBlocking {
        // Act
        val id1 = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )
        val id2 = repository.insert(
            name = "Maria Santos",
            phone = "(11) 98888-8888",
            email = "maria@example.com",
            initialConsultDate = LocalDate.of(2024, 2, 1)
        )

        // Assert
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertEquals(id1 + 1, id2)  // IDs should be sequential
    }

    @Test
    fun insert_patientWithNullPhone_succeeds() = runBlocking {
        // Act
        val patientId = repository.insert(
            name = "Maria Santos",
            phone = null,
            email = "maria@example.com",
            initialConsultDate = LocalDate.of(2024, 2, 1)
        )

        // Assert
        assertTrue(patientId > 0)
    }

    @Test
    fun insert_patientWithNullEmail_succeeds() = runBlocking {
        // Act
        val patientId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = null,
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Assert
        assertTrue(patientId > 0)
    }

    // ========================================
    // Read Operations - Single Patient
    // ========================================

    @Test
    fun getById_existingPatient_returnsPatient() = runBlocking {
        // Arrange
        val insertedId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        val patient = repository.getById(insertedId)

        // Assert
        assertNotNull(patient)
        assertEquals("João Silva", patient?.name)
        assertEquals("(11) 99999-9999", patient?.phone)
        assertEquals("joao@example.com", patient?.email)
    }

    @Test
    fun getById_nonExistentPatient_returnsNull() = runBlocking {
        // Act
        val patient = repository.getById(999L)

        // Assert
        assertNull(patient)
    }

    @Test
    fun getById_deletedPatient_returnsNull() = runBlocking {
        // Arrange
        val insertedId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act - Soft delete
        repository.softDelete(insertedId)
        val patient = repository.getById(insertedId)

        // Assert
        assertNull(patient)  // Soft-deleted patients should not be returned
    }

    // ========================================
    // Read Operations - Queries
    // ========================================

    @Test
    fun getByPhone_existingPhone_returnsPatient() = runBlocking {
        // Arrange
        repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        val patient = repository.getByPhone("(11) 99999-9999")

        // Assert
        assertNotNull(patient)
        assertEquals("João Silva", patient?.name)
    }

    @Test
    fun getByPhone_nonExistentPhone_returnsNull() = runBlocking {
        // Act
        val patient = repository.getByPhone("(11) 00000-0000")

        // Assert
        assertNull(patient)
    }

    @Test
    fun getByEmail_existingEmail_returnsPatient() = runBlocking {
        // Arrange
        repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        val patient = repository.getByEmail("joao@example.com")

        // Assert
        assertNotNull(patient)
        assertEquals("João Silva", patient?.name)
    }

    @Test
    fun getByEmail_nonExistentEmail_returnsNull() = runBlocking {
        // Act
        val patient = repository.getByEmail("nonexistent@example.com")

        // Assert
        assertNull(patient)
    }

    @Test
    fun searchByName_partialMatch_returnsPatients() = runBlocking {
        // Arrange
        repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )
        repository.insert(
            name = "Maria Santos",
            phone = "(11) 98888-8888",
            email = "maria@example.com",
            initialConsultDate = LocalDate.of(2024, 2, 1)
        )

        // Act
        val results = repository.searchByName("João")

        // Assert
        assertEquals(1, results.size)
        assertEquals("João Silva", results[0].name)
    }

    @Test
    fun searchByName_caseInsensitive_returnsResults() = runBlocking {
        // Arrange
        repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        val results = repository.searchByName("joao")

        // Assert
        assertEquals(1, results.size)
    }

    @Test
    fun searchByName_noMatch_returnsEmpty() = runBlocking {
        // Arrange
        repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        val results = repository.searchByName("Carlos")

        // Assert
        assertEquals(0, results.size)
    }

    // ========================================
    // Update Operations
    // ========================================

    @Test
    fun update_existingPatient_updatesPersists() = runBlocking {
        // Arrange
        val insertedId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )
        val originalPatient = repository.getById(insertedId)!!

        // Act
        val updatedPatient = originalPatient.copy(
            name = "João Silva Santos",
            phone = "(11) 88888-8888"
        )
        repository.update(updatedPatient)

        // Assert
        val retrieved = repository.getById(insertedId)
        assertNotNull(retrieved)
        assertEquals("João Silva Santos", retrieved?.name)
        assertEquals("(11) 88888-8888", retrieved?.phone)
    }

    @Test
    fun update_patientStatus_changesStatusOnly() = runBlocking {
        // Arrange
        val insertedId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )
        val originalPatient = repository.getById(insertedId)!!
        assertEquals(PatientStatus.ACTIVE, originalPatient.status)

        // Act
        val inactivePatient = originalPatient.copy(status = PatientStatus.INACTIVE)
        repository.update(inactivePatient)

        // Assert
        val retrieved = repository.getById(insertedId)
        assertNotNull(retrieved)
        assertEquals(PatientStatus.INACTIVE, retrieved?.status)
    }

    // ========================================
    // Delete Operations (Soft Delete)
    // ========================================

    @Test
    fun softDelete_marksPatientInactive() = runBlocking {
        // Arrange
        val insertedId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        repository.softDelete(insertedId)

        // Assert
        val patient = repository.getById(insertedId)
        assertNull(patient)  // Soft-deleted patients not returned by default
    }

    @Test
    fun softDelete_preservesData() = runBlocking {
        // Arrange
        val insertedId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        repository.softDelete(insertedId)

        // Assert - Verify data is preserved (not physically deleted)
        val allIncluding = repository.getAllIncludingInactive()
        assertTrue(allIncluding.any { it.id == insertedId })
    }

    // ========================================
    // Uniqueness Checks
    // ========================================

    @Test
    fun isPhoneUnique_newPhone_returnsTrue() = runBlocking {
        // Act
        val isUnique = repository.isPhoneUnique("(11) 99999-9999")

        // Assert
        assertTrue(isUnique)
    }

    @Test
    fun isPhoneUnique_existingPhone_returnsFalse() = runBlocking {
        // Arrange
        repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        val isUnique = repository.isPhoneUnique("(11) 99999-9999")

        // Assert
        assertTrue(!isUnique)
    }

    @Test
    fun isEmailUnique_newEmail_returnsTrue() = runBlocking {
        // Act
        val isUnique = repository.isEmailUnique("joao@example.com")

        // Assert
        assertTrue(isUnique)
    }

    @Test
    fun isEmailUnique_existingEmail_returnsFalse() = runBlocking {
        // Arrange
        repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )

        // Act
        val isUnique = repository.isEmailUnique("joao@example.com")

        // Assert
        assertTrue(!isUnique)
    }

    // ========================================
    // Data Isolation and Persistence
    // ========================================

    @Test
    fun multipleOperations_databaseIsolation_allDataPersists() = runBlocking {
        // Arrange & Act
        val id1 = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2024, 1, 15)
        )
        val id2 = repository.insert(
            name = "Maria Santos",
            phone = "(11) 98888-8888",
            email = "maria@example.com",
            initialConsultDate = LocalDate.of(2024, 2, 1)
        )

        // Modify first patient
        val patient1 = repository.getById(id1)!!
        repository.update(patient1.copy(phone = "(11) 77777-7777"))

        // Act - Soft delete second patient
        repository.softDelete(id2)

        // Assert
        val retrieved1 = repository.getById(id1)
        val retrieved2 = repository.getById(id2)
        assertNotNull(retrieved1)
        assertEquals("(11) 77777-7777", retrieved1?.phone)
        assertNull(retrieved2)  // Soft deleted
    }

    @Test
    fun domainModelMapping_entityToModel_convertsCorrectly() = runBlocking {
        // Arrange
        val testDate = LocalDate.of(2024, 1, 15)
        val insertedId = repository.insert(
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            initialConsultDate = testDate
        )

        // Act
        val patient = repository.getById(insertedId)

        // Assert - Verify all fields mapped correctly
        assertNotNull(patient)
        assertEquals(insertedId, patient?.id)
        assertEquals("João Silva", patient?.name)
        assertEquals("(11) 99999-9999", patient?.phone)
        assertEquals("joao@example.com", patient?.email)
        assertEquals(PatientStatus.ACTIVE, patient?.status)
        assertEquals(testDate, patient?.initialConsultDate)
        assertNotNull(patient?.registrationDate)  // Should be set to current date
    }
}
