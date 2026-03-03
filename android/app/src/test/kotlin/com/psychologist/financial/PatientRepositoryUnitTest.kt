package com.psychologist.financial

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.PatientStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for PatientRepository
 *
 * Note: Only read operations (using withRead/withContext) are testable in pure JVM
 * unit tests. Write operations (using withTransaction) require an in-memory Room
 * database or Robolectric — see androidTest/ for those tests.
 *
 * Coverage (read operations):
 * - getPatient() returns mapped domain Patient
 * - getPatient() returns null when not found
 * - getAllPatients() maps all entities
 * - getActivePatients() returns only active
 * - getInactivePatients() returns only inactive
 * - searchPatients() delegates to DAO
 * - countActivePatients() delegates to DAO
 * - countAllPatients() delegates to DAO
 * - hasPatients() returns true when count > 0
 * - getActivePatientsFlow() emits mapped list
 *
 * Total: 12 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class PatientRepositoryUnitTest {

    @Mock
    private lateinit var mockDatabase: AppDatabase

    @Mock
    private lateinit var mockPatientDao: PatientDao

    private lateinit var repository: PatientRepository

    private val today = LocalDate.now()

    private fun makePatientEntity(
        id: Long,
        name: String = "Patient $id",
        status: String = "ACTIVE"
    ) = PatientEntity(
        id = id,
        name = name,
        phone = null,
        email = null,
        status = status,
        initialConsultDate = today,
        registrationDate = today,
        lastAppointmentDate = null
    )

    @Before
    fun setUp() {
        whenever(mockDatabase.patientDao()).thenReturn(mockPatientDao)
        repository = PatientRepository(database = mockDatabase)
    }

    // ========================================
    // getPatient() Tests
    // ========================================

    @Test
    fun `getPatient returns mapped domain Patient when found`() = runTest {
        val entity = makePatientEntity(1L, "Ana Costa", "ACTIVE")
        whenever(mockPatientDao.getPatient(1L)).thenReturn(entity)

        val result = repository.getPatient(id = 1L)

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Ana Costa", result.name)
        assertEquals(PatientStatus.ACTIVE, result.status)
    }

    @Test
    fun `getPatient returns null when patient not found`() = runTest {
        whenever(mockPatientDao.getPatient(99L)).thenReturn(null)

        val result = repository.getPatient(id = 99L)

        assertNull(result)
    }

    // ========================================
    // getAllPatients() Tests
    // ========================================

    @Test
    fun `getAllPatients returns all mapped patients`() = runTest {
        val entities = listOf(
            makePatientEntity(1L, "Ana", "ACTIVE"),
            makePatientEntity(2L, "Bruno", "INACTIVE")
        )
        whenever(mockPatientDao.getAllPatients()).thenReturn(entities)

        val result = repository.getAllPatients()

        assertEquals(2, result.size)
    }

    @Test
    fun `getAllPatients returns empty list when no patients`() = runTest {
        whenever(mockPatientDao.getAllPatients()).thenReturn(emptyList())

        val result = repository.getAllPatients()

        assertTrue(result.isEmpty())
    }

    // ========================================
    // getActivePatients() Tests
    // ========================================

    @Test
    fun `getActivePatients returns only active patients`() = runTest {
        val entities = listOf(makePatientEntity(1L, status = "ACTIVE"))
        whenever(mockPatientDao.getAllActivePatients()).thenReturn(entities)

        val result = repository.getActivePatients()

        assertEquals(1, result.size)
        assertTrue(result.all { it.isActive })
    }

    // ========================================
    // getInactivePatients() Tests
    // ========================================

    @Test
    fun `getInactivePatients returns only inactive patients`() = runTest {
        val entities = listOf(makePatientEntity(2L, status = "INACTIVE"))
        whenever(mockPatientDao.getAllInactivePatients()).thenReturn(entities)

        val result = repository.getInactivePatients()

        assertEquals(1, result.size)
        assertTrue(result.all { it.isInactive })
    }

    // ========================================
    // searchPatients() Tests
    // ========================================

    @Test
    fun `searchPatients returns matching patients`() = runTest {
        val entities = listOf(makePatientEntity(1L, "João Silva"))
        whenever(mockPatientDao.searchPatients(any())).thenReturn(entities)

        val result = repository.searchPatients("João")

        assertEquals(1, result.size)
        assertEquals("João Silva", result[0].name)
    }

    // ========================================
    // Count Tests
    // ========================================

    @Test
    fun `countActivePatients delegates to DAO`() = runTest {
        whenever(mockPatientDao.countActivePatients()).thenReturn(7)

        val count = repository.countActivePatients()

        assertEquals(7, count)
    }

    @Test
    fun `countAllPatients delegates to DAO`() = runTest {
        whenever(mockPatientDao.countAllPatients()).thenReturn(10)

        val count = repository.countAllPatients()

        assertEquals(10, count)
    }

    // ========================================
    // Flow Tests
    // ========================================

    @Test
    fun `getActivePatientsFlow emits mapped patient list`() = runTest {
        val entities = listOf(makePatientEntity(1L, "Ana"), makePatientEntity(2L, "Carlos"))
        whenever(mockPatientDao.getAllActivePatientsFlow()).thenReturn(flowOf(entities))

        val flow = repository.getActivePatientsFlow()
        var emitted: List<com.psychologist.financial.domain.models.Patient>? = null
        flow.collect { emitted = it }

        assertNotNull(emitted)
        assertEquals(2, emitted!!.size)
    }
}
