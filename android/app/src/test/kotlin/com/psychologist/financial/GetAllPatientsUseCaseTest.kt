package com.psychologist.financial

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.usecases.GetAllPatientsUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for GetAllPatientsUseCase
 *
 * Coverage:
 * - execute() with and without inactive patients
 * - getActiveOnly() / getInactiveOnly()
 * - getCount() / hasPatients()
 * - getWithOptions() sorting
 * - executeFlow() reactive API
 *
 * Total: 16 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class GetAllPatientsUseCaseTest {

    @Mock
    private lateinit var mockRepository: PatientRepository

    private lateinit var useCase: GetAllPatientsUseCase

    private val today = LocalDate.now()

    private val activePatient1 = Patient.createForTesting(
        id = 1L, name = "Ana Costa", status = PatientStatus.ACTIVE
    )
    private val activePatient2 = Patient.createForTesting(
        id = 2L, name = "Carlos Lima", status = PatientStatus.ACTIVE
    )
    private val inactivePatient = Patient.createForTesting(
        id = 3L, name = "Bruno Melo", status = PatientStatus.INACTIVE
    )

    @Before
    fun setUp() {
        useCase = GetAllPatientsUseCase(patientRepository = mockRepository)
    }

    // ========================================
    // execute() Tests
    // ========================================

    @Test
    fun `execute without inactive returns only active patients`() = runTest {
        whenever(mockRepository.getActivePatients())
            .thenReturn(listOf(activePatient1, activePatient2))

        val result = useCase.execute(includeInactive = false)

        assertEquals(2, result.size)
        assertTrue(result.all { it.isActive })
    }

    @Test
    fun `execute with includeInactive returns all patients`() = runTest {
        whenever(mockRepository.getAllPatients())
            .thenReturn(listOf(activePatient1, activePatient2, inactivePatient))

        val result = useCase.execute(includeInactive = true)

        assertEquals(3, result.size)
    }

    @Test
    fun `execute default parameter excludes inactive`() = runTest {
        whenever(mockRepository.getActivePatients())
            .thenReturn(listOf(activePatient1))

        val result = useCase.execute()

        assertEquals(1, result.size)
        assertTrue(result[0].isActive)
    }

    @Test
    fun `execute returns empty list when no active patients`() = runTest {
        whenever(mockRepository.getActivePatients()).thenReturn(emptyList())

        val result = useCase.execute(includeInactive = false)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // getActiveOnly() / getInactiveOnly() Tests
    // ========================================

    @Test
    fun `getActiveOnly delegates to repository`() = runTest {
        whenever(mockRepository.getActivePatients())
            .thenReturn(listOf(activePatient1, activePatient2))

        val result = useCase.getActiveOnly()

        assertEquals(2, result.size)
        assertTrue(result.all { it.isActive })
    }

    @Test
    fun `getInactiveOnly returns only inactive patients`() = runTest {
        whenever(mockRepository.getInactivePatients())
            .thenReturn(listOf(inactivePatient))

        val result = useCase.getInactiveOnly()

        assertEquals(1, result.size)
        assertTrue(result[0].isInactive)
    }

    // ========================================
    // getCount() Tests
    // ========================================

    @Test
    fun `getCount with default parameter returns active count`() = runTest {
        whenever(mockRepository.countActivePatients()).thenReturn(5)

        val count = useCase.getCount()

        assertEquals(5, count)
    }

    @Test
    fun `getCount with includeInactive returns all count`() = runTest {
        whenever(mockRepository.countAllPatients()).thenReturn(8)

        val count = useCase.getCount(includeInactive = true)

        assertEquals(8, count)
    }

    @Test
    fun `getCount returns zero when no patients`() = runTest {
        whenever(mockRepository.countActivePatients()).thenReturn(0)

        val count = useCase.getCount()

        assertEquals(0, count)
    }

    // ========================================
    // hasPatients() Tests
    // ========================================

    @Test
    fun `hasPatients returns true when patients exist`() = runTest {
        whenever(mockRepository.countActivePatients()).thenReturn(3)

        val hasPats = useCase.hasPatients()

        assertTrue(hasPats)
    }

    @Test
    fun `hasPatients returns false when no patients`() = runTest {
        whenever(mockRepository.countActivePatients()).thenReturn(0)

        val hasPats = useCase.hasPatients()

        assertFalse(hasPats)
    }

    // ========================================
    // getWithOptions() Sorting Tests
    // ========================================

    @Test
    fun `getWithOptions with sortBy name sorts alphabetically`() = runTest {
        whenever(mockRepository.getActivePatients())
            .thenReturn(listOf(activePatient2, activePatient1)) // Carlos, Ana

        val result = useCase.getWithOptions(sortBy = "name")

        assertEquals("Ana Costa", result[0].name)
        assertEquals("Carlos Lima", result[1].name)
    }

    @Test
    fun `getWithOptions with sortBy recent sorts by last appointment`() = runTest {
        val patientRecent = activePatient1.copy(lastAppointmentDate = today)
        val patientOlder = activePatient2.copy(lastAppointmentDate = today.minusDays(10))
        whenever(mockRepository.getActivePatients())
            .thenReturn(listOf(patientOlder, patientRecent))

        val result = useCase.getWithOptions(sortBy = "recent")

        assertEquals("Ana Costa", result[0].name)
    }

    @Test
    fun `getWithOptions with unknown sortBy returns unsorted`() = runTest {
        whenever(mockRepository.getActivePatients())
            .thenReturn(listOf(activePatient1, activePatient2))

        val result = useCase.getWithOptions(sortBy = "unknown")

        assertEquals(2, result.size) // Returns as-is
    }

    // ========================================
    // Flow Tests
    // ========================================

    @Test
    fun `executeFlow active emits active patients`() = runTest {
        whenever(mockRepository.getActivePatientsFlow())
            .thenReturn(flowOf(listOf(activePatient1, activePatient2)))

        val flow = useCase.executeFlow(includeInactive = false)
        var emitted: List<Patient>? = null
        flow.collect { emitted = it }

        assertEquals(2, emitted?.size)
    }

    @Test
    fun `observeActive delegates to repository flow`() = runTest {
        whenever(mockRepository.getActivePatientsFlow())
            .thenReturn(flowOf(listOf(activePatient1)))

        val flow = useCase.observeActive()
        var emitted: List<Patient>? = null
        flow.collect { emitted = it }

        assertEquals(1, emitted?.size)
    }
}
