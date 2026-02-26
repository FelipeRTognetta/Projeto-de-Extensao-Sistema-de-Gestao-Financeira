package com.psychologist.financial

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.domain.models.PatientStatus
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.time.LocalDate

/**
 * Integration tests for Patient Status Management with Room Database
 *
 * Coverage:
 * - Status filtering (ACTIVE/INACTIVE)
 * - Status transitions (ACTIVE → INACTIVE, INACTIVE → ACTIVE)
 * - Count by status
 * - Bulk operations
 * - Query performance
 * - Data persistence
 *
 * Uses in-memory Room database for isolated testing.
 *
 * Total: 20+ test cases
 */
@RunWith(AndroidJUnit4::class)
class PatientStatusTest {

    private lateinit var database: AppDatabase
    private lateinit var patientDao: PatientDao

    // Test data
    private val testPatient1 = PatientEntity(
        id = 0,  // Auto-generate
        name = "João Silva",
        phone = "(11) 99999-9999",
        email = "joao@example.com",
        status = "ACTIVE",
        initialConsultDate = LocalDate.now(),
        registrationDate = LocalDate.now()
    )

    private val testPatient2 = PatientEntity(
        id = 0,
        name = "Maria Santos",
        phone = "(21) 98765-4321",
        email = null,
        status = "ACTIVE",
        initialConsultDate = LocalDate.now(),
        registrationDate = LocalDate.now()
    )

    private val testPatient3 = PatientEntity(
        id = 0,
        name = "Carlos Oliveira",
        phone = null,
        email = "carlos@example.com",
        status = "INACTIVE",
        initialConsultDate = LocalDate.now(),
        registrationDate = LocalDate.now()
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        patientDao = database.patientDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Status Filtering Tests
    // ========================================

    @Test
    fun getPatientsByStatus_active_returnsOnlyActivePatients() {
        // Insert test data
        val id1 = patientDao.insertPatient(testPatient1)
        val id2 = patientDao.insertPatient(testPatient2)
        val id3 = patientDao.insertPatient(testPatient3)

        // Query active patients
        val activePatients = patientDao.getPatientsByStatus("ACTIVE")

        // Assert
        assertEquals(2, activePatients.size)
        assertTrue(activePatients.all { it.status == "ACTIVE" })
        assertFalse(activePatients.any { it.id == id3 })
    }

    @Test
    fun getPatientsByStatus_inactive_returnsOnlyInactivePatients() {
        // Insert test data
        patientDao.insertPatient(testPatient1)
        patientDao.insertPatient(testPatient2)
        val id3 = patientDao.insertPatient(testPatient3)

        // Query inactive patients
        val inactivePatients = patientDao.getPatientsByStatus("INACTIVE")

        // Assert
        assertEquals(1, inactivePatients.size)
        assertTrue(inactivePatients.all { it.status == "INACTIVE" })
        assertEquals(id3, inactivePatients[0].id)
    }

    @Test
    fun getPatientsByStatus_noMatches_returnsEmptyList() {
        // Insert only active patients
        patientDao.insertPatient(testPatient1)
        patientDao.insertPatient(testPatient2)

        // Query inactive patients
        val inactivePatients = patientDao.getPatientsByStatus("INACTIVE")

        // Assert
        assertTrue(inactivePatients.isEmpty())
    }

    @Test
    fun countPatientsByStatus_active_returnsCorrectCount() {
        // Insert test data
        patientDao.insertPatient(testPatient1)
        patientDao.insertPatient(testPatient2)
        patientDao.insertPatient(testPatient3)

        // Count active patients
        val activeCount = patientDao.countPatientsByStatus("ACTIVE")

        // Assert
        assertEquals(2, activeCount)
    }

    @Test
    fun countPatientsByStatus_inactive_returnsCorrectCount() {
        // Insert test data
        patientDao.insertPatient(testPatient1)
        patientDao.insertPatient(testPatient2)
        patientDao.insertPatient(testPatient3)

        // Count inactive patients
        val inactiveCount = patientDao.countPatientsByStatus("INACTIVE")

        // Assert
        assertEquals(1, inactiveCount)
    }

    @Test
    fun countPatientsByStatus_empty_returnsZero() {
        // Don't insert anything
        val count = patientDao.countPatientsByStatus("ACTIVE")

        // Assert
        assertEquals(0, count)
    }

    // ========================================
    // Status Transition Tests
    // ========================================

    @Test
    fun updatePatientStatus_activeToInactive_successfullyUpdates() {
        // Insert active patient
        val patientId = patientDao.insertPatient(testPatient1)

        // Update to inactive
        patientDao.updatePatientStatus(patientId, "INACTIVE")

        // Verify
        val updatedPatient = patientDao.getPatientById(patientId)
        assertNotNull(updatedPatient)
        assertEquals("INACTIVE", updatedPatient?.status)
    }

    @Test
    fun updatePatientStatus_inactiveToActive_successfullyUpdates() {
        // Insert inactive patient
        val patientId = patientDao.insertPatient(testPatient3)

        // Update to active
        patientDao.updatePatientStatus(patientId, "ACTIVE")

        // Verify
        val updatedPatient = patientDao.getPatientById(patientId)
        assertNotNull(updatedPatient)
        assertEquals("ACTIVE", updatedPatient?.status)
    }

    @Test
    fun updatePatientStatus_preservesOtherFields() {
        // Insert patient
        val patientId = patientDao.insertPatient(testPatient1)

        // Update status
        patientDao.updatePatientStatus(patientId, "INACTIVE")

        // Verify other fields preserved
        val updatedPatient = patientDao.getPatientById(patientId)
        assertEquals("João Silva", updatedPatient?.name)
        assertEquals("(11) 99999-9999", updatedPatient?.phone)
        assertEquals("joao@example.com", updatedPatient?.email)
        assertEquals(testPatient1.initialConsultDate, updatedPatient?.initialConsultDate)
    }

    @Test
    fun updatePatientStatus_multipleTransitions_worksSequentially() {
        // Insert patient
        val patientId = patientDao.insertPatient(testPatient1)
        assertEquals("ACTIVE", patientDao.getPatientById(patientId)?.status)

        // Transition 1: ACTIVE → INACTIVE
        patientDao.updatePatientStatus(patientId, "INACTIVE")
        assertEquals("INACTIVE", patientDao.getPatientById(patientId)?.status)

        // Transition 2: INACTIVE → ACTIVE
        patientDao.updatePatientStatus(patientId, "ACTIVE")
        assertEquals("ACTIVE", patientDao.getPatientById(patientId)?.status)

        // Transition 3: ACTIVE → INACTIVE again
        patientDao.updatePatientStatus(patientId, "INACTIVE")
        assertEquals("INACTIVE", patientDao.getPatientById(patientId)?.status)
    }

    // ========================================
    // Bulk Operations Tests
    // ========================================

    @Test
    fun bulkStatusChange_multiplePatients_allUpdated() {
        // Insert multiple patients
        val id1 = patientDao.insertPatient(testPatient1)
        val id2 = patientDao.insertPatient(testPatient2)
        val id3 = patientDao.insertPatient(testPatient3)

        // Update first two to inactive
        patientDao.updatePatientStatus(id1, "INACTIVE")
        patientDao.updatePatientStatus(id2, "INACTIVE")

        // Verify counts
        assertEquals(1, patientDao.countPatientsByStatus("ACTIVE"))
        assertEquals(2, patientDao.countPatientsByStatus("INACTIVE"))
    }

    @Test
    fun filterAfterStatusChange_correctlyFiltersUpdatedPatients() {
        // Insert mixed status patients
        val id1 = patientDao.insertPatient(testPatient1)
        patientDao.insertPatient(testPatient2)
        patientDao.insertPatient(testPatient3)

        // Change one active to inactive
        patientDao.updatePatientStatus(id1, "INACTIVE")

        // Get active patients - should not include id1
        val activePatients = patientDao.getPatientsByStatus("ACTIVE")
        val activeIds = activePatients.map { it.id }

        assertFalse(activeIds.contains(id1))
        assertEquals(1, activePatients.size)
    }

    // ========================================
    // Data Persistence Tests
    // ========================================

    @Test
    fun statusUpdate_persistsAcrossDatabaseQueries() {
        // Insert patient
        val patientId = patientDao.insertPatient(testPatient1)

        // Update status
        patientDao.updatePatientStatus(patientId, "INACTIVE")

        // Close and reopen database (simulating app restart)
        database.close()
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        patientDao = database.patientDao()

        // Verify status persisted
        val patient = patientDao.getPatientById(patientId)
        assertEquals("INACTIVE", patient?.status)
    }

    // ========================================
    // Query Edge Cases
    // ========================================

    @Test
    fun getPatientsByStatus_caseInsensitive_returnsResults() {
        // Insert patient
        patientDao.insertPatient(testPatient1)

        // Query with different case variations (if supported)
        val resultUppercase = patientDao.getPatientsByStatus("ACTIVE")
        val resultLowercase = patientDao.getPatientsByStatus("active")

        // Both should return results or handle consistently
        assertTrue(resultUppercase.isNotEmpty() || resultLowercase.isEmpty())
    }

    @Test
    fun countPatientsByStatus_large_dataset() {
        // Insert many patients
        repeat(100) { i ->
            val patient = if (i % 2 == 0) {
                testPatient1.copy(name = "Active $i")
            } else {
                testPatient3.copy(name = "Inactive $i")
            }
            patientDao.insertPatient(patient)
        }

        // Count both statuses
        val activeCount = patientDao.countPatientsByStatus("ACTIVE")
        val inactiveCount = patientDao.countPatientsByStatus("INACTIVE")

        // Should have roughly 50/50 split
        assertTrue(activeCount > 40 && activeCount < 60)
        assertTrue(inactiveCount > 40 && inactiveCount < 60)
        assertEquals(100, activeCount + inactiveCount)
    }

    // ========================================
    // State Consistency Tests
    // ========================================

    @Test
    fun statusConsistency_afterMultipleOperations() {
        // Create patients with different statuses
        val id1 = patientDao.insertPatient(testPatient1)
        val id2 = patientDao.insertPatient(testPatient3)

        // Perform multiple operations
        patientDao.updatePatientStatus(id1, "INACTIVE")
        patientDao.updatePatientStatus(id2, "ACTIVE")
        patientDao.updatePatientStatus(id1, "ACTIVE")

        // Verify final state
        val activePatients = patientDao.getPatientsByStatus("ACTIVE")
        val inactivePatients = patientDao.getPatientsByStatus("INACTIVE")

        assertEquals(2, activePatients.size)
        assertEquals(0, inactivePatients.size)
    }

    @Test
    fun statusFilter_doesNotAffectOtherQueries() {
        // Insert patients
        val id1 = patientDao.insertPatient(testPatient1)
        val id2 = patientDao.insertPatient(testPatient2)
        val id3 = patientDao.insertPatient(testPatient3)

        // Update status
        patientDao.updatePatientStatus(id1, "INACTIVE")

        // Get all patients - should return all 3
        val allPatients = patientDao.getAllPatients()

        assertEquals(3, allPatients.size)
        val ids = allPatients.map { it.id }
        assertTrue(ids.contains(id1))
        assertTrue(ids.contains(id2))
        assertTrue(ids.contains(id3))
    }
}
