package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.PageLoadStatus
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.PaginationState
import com.psychologist.financial.domain.usecases.CreatePatientUseCase
import com.psychologist.financial.domain.usecases.GetAllPatientsUseCase
import com.psychologist.financial.domain.usecases.MarkPatientInactiveUseCase
import com.psychologist.financial.domain.usecases.ReactivatePatientUseCase
import com.psychologist.financial.domain.usecases.UpdatePatientUseCase
import com.psychologist.financial.utils.Constants
import com.psychologist.financial.viewmodel.PatientViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for PatientViewModel pagination (T005 — TDD: written before implementation).
 *
 * Covers:
 * - resetAndLoad() triggers first-page load with Loading → Idle transitions
 * - loadNextPage() appends items from page 1
 * - loadNextPage() is a no-op while Loading
 * - loadNextPage() is a no-op when hasMore = false
 * - full page (PAGE_SIZE items) sets hasMore = true; partial page sets hasMore = false
 * - setNameFilter() resets state and reloads with new search term
 * - toggleInactiveFilter() resets state and reloads
 * - repository exception sets PageLoadStatus.Error
 * - empty first page yields empty items list with hasMore = false
 *
 * Run with: ./gradlew testDebugUnitTest --tests PatientViewModelPaginationTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientViewModelPaginationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockGetAllPatientsUseCase: GetAllPatientsUseCase

    @Mock
    private lateinit var mockCreatePatientUseCase: CreatePatientUseCase

    @Mock
    private lateinit var mockMarkPatientInactiveUseCase: MarkPatientInactiveUseCase

    @Mock
    private lateinit var mockReactivatePatientUseCase: ReactivatePatientUseCase

    @Mock
    private lateinit var mockUpdatePatientUseCase: UpdatePatientUseCase

    @Mock
    private lateinit var mockPatientRepository: PatientRepository

    @Mock
    private lateinit var mockAppointmentRepository: AppointmentRepository

    private lateinit var viewModel: PatientViewModel

    private fun makePatient(id: Long, name: String = "Patient $id") = Patient(
        id = id,
        name = name,
        phone = "119999${id.toString().padStart(5, '0')}",
        email = "p$id@example.com",
        status = PatientStatus.ACTIVE,
        initialConsultDate = LocalDate.of(2024, 1, 1),
        registrationDate = LocalDate.of(2024, 1, 1),
        lastAppointmentDate = null
    )

    private fun makePage(startId: Long, count: Int): List<Patient> =
        (startId until startId + count).map { makePatient(it) }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(mockGetAllPatientsUseCase.executeFlow(any())).thenReturn(flowOf(emptyList()))
        whenever(mockAppointmentRepository.getPatientIdsWithPendingPayments())
            .thenReturn(flowOf(emptySet()))

        viewModel = PatientViewModel(
            getAllPatientsUseCase = mockGetAllPatientsUseCase,
            createPatientUseCase = mockCreatePatientUseCase,
            markPatientInactiveUseCase = mockMarkPatientInactiveUseCase,
            reactivatePatientUseCase = mockReactivatePatientUseCase,
            updatePatientUseCase = mockUpdatePatientUseCase,
            appointmentRepository = mockAppointmentRepository,
            patientRepository = mockPatientRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // resetAndLoad — initial page load
    // ========================================

    @Test
    fun `resetAndLoad sets status to Idle after loading page 0`() = runTest {
        val page0 = makePage(1L, Constants.PAGE_SIZE)
        whenever(
            mockPatientRepository.getPagedPatients(
                searchTerm = "%",
                includeInactive = false,
                page = 0
            )
        ).thenReturn(page0)

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.paginationState.value
        assertEquals(PageLoadStatus.Idle, state.status)
        assertEquals(Constants.PAGE_SIZE, state.items.size)
        assertEquals(1, state.currentPage)
    }

    @Test
    fun `resetAndLoad resets items before loading`() = runTest {
        // Prime state with existing items then reset
        val page0 = makePage(1L, Constants.PAGE_SIZE)
        whenever(
            mockPatientRepository.getPagedPatients(
                searchTerm = "%",
                includeInactive = false,
                page = 0
            )
        ).thenReturn(page0)

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()
        val firstCount = viewModel.paginationState.value.items.size

        whenever(
            mockPatientRepository.getPagedPatients(
                searchTerm = "%",
                includeInactive = false,
                page = 0
            )
        ).thenReturn(makePage(100L, 5))

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        // After second resetAndLoad, only the 5 new items should be present (not 25 + 5)
        assertEquals(5, viewModel.paginationState.value.items.size)
        assertTrue(firstCount > viewModel.paginationState.value.items.size)
    }

    // ========================================
    // loadNextPage — append page 1
    // ========================================

    @Test
    fun `loadNextPage appends page 1 items to existing state`() = runTest {
        val page0 = makePage(1L, Constants.PAGE_SIZE)
        val page1 = makePage(26L, Constants.PAGE_SIZE)
        whenever(
            mockPatientRepository.getPagedPatients(
                searchTerm = "%",
                includeInactive = false,
                page = 0
            )
        ).thenReturn(page0)
        whenever(
            mockPatientRepository.getPagedPatients(
                searchTerm = "%",
                includeInactive = false,
                page = 1
            )
        ).thenReturn(page1)

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.paginationState.value
        assertEquals(Constants.PAGE_SIZE * 2, state.items.size)
        assertEquals(2, state.currentPage)
        assertTrue(state.hasMore)
    }

    // ========================================
    // No-op guards
    // ========================================

    @Test
    fun `loadNextPage is no-op when status is Loading`() = runTest {
        // Don't let the coroutine advance so status stays Loading
        val page0 = makePage(1L, Constants.PAGE_SIZE)
        whenever(
            mockPatientRepository.getPagedPatients(any(), any(), eq(0))
        ).thenReturn(page0)

        // Start resetAndLoad but don't advance — the coroutine is suspended mid-flight
        viewModel.resetAndLoad()
        // Calling loadNextPage while loading should not schedule a second load
        viewModel.loadNextPage()
        testDispatcher.scheduler.advanceUntilIdle()

        // currentPage should still be 1 (only one successful load ran)
        assertEquals(1, viewModel.paginationState.value.currentPage)
    }

    @Test
    fun `loadNextPage is no-op when hasMore is false`() = runTest {
        // Return a partial page to signal end of data
        val partialPage = makePage(1L, 3)
        whenever(
            mockPatientRepository.getPagedPatients(any(), any(), eq(0))
        ).thenReturn(partialPage)

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.paginationState.value.hasMore)
        val pageAfterFirst = viewModel.paginationState.value.currentPage

        // Calling loadNextPage when hasMore = false should not fire another request
        viewModel.loadNextPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(pageAfterFirst, viewModel.paginationState.value.currentPage)
    }

    // ========================================
    // hasMore detection
    // ========================================

    @Test
    fun `full page of PAGE_SIZE items keeps hasMore true`() = runTest {
        whenever(
            mockPatientRepository.getPagedPatients(any(), any(), eq(0))
        ).thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.paginationState.value.hasMore)
    }

    @Test
    fun `partial page smaller than PAGE_SIZE sets hasMore to false`() = runTest {
        whenever(
            mockPatientRepository.getPagedPatients(any(), any(), eq(0))
        ).thenReturn(makePage(1L, Constants.PAGE_SIZE - 1))

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.paginationState.value.hasMore)
    }

    // ========================================
    // Filter changes reset pagination
    // ========================================

    @Test
    fun `setNameFilter resets state and reloads with encoded search term`() = runTest {
        // Initial unfiltered load
        whenever(
            mockPatientRepository.getPagedPatients(eq("%"), any(), eq(0))
        ).thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Constants.PAGE_SIZE, viewModel.paginationState.value.items.size)

        // Filtered load — term encoded as %silva%
        val filtered = listOf(makePatient(3L, "João Silva"))
        whenever(
            mockPatientRepository.getPagedPatients(eq("%silva%"), any(), eq(0))
        ).thenReturn(filtered)

        viewModel.setNameFilter("silva")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.paginationState.value
        assertEquals(1, state.items.size)
        assertEquals("João Silva", state.items.first().name)
        assertEquals(1, state.currentPage)
    }

    @Test
    fun `toggleInactiveFilter resets state and reloads`() = runTest {
        whenever(
            mockPatientRepository.getPagedPatients(any(), eq(false), eq(0))
        ).thenReturn(makePage(1L, 3))
        whenever(
            mockPatientRepository.getPagedPatients(any(), eq(true), eq(0))
        ).thenReturn(makePage(1L, 5))

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, viewModel.paginationState.value.items.size)

        viewModel.toggleInactiveFilter()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.paginationState.value.items.size)
        assertTrue(viewModel.includeInactivePatients.value)
    }

    // ========================================
    // Error handling
    // ========================================

    @Test
    fun `repository exception sets PageLoadStatus Error`() = runTest {
        whenever(
            mockPatientRepository.getPagedPatients(any(), any(), eq(0))
        ).thenThrow(RuntimeException("DB failure"))

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.paginationState.value
        assertTrue(state.isError)
        assertTrue((state.status as PageLoadStatus.Error).message.contains("DB failure"))
    }

    // ========================================
    // Empty first page
    // ========================================

    @Test
    fun `empty first page yields empty items and hasMore false`() = runTest {
        whenever(
            mockPatientRepository.getPagedPatients(any(), any(), eq(0))
        ).thenReturn(emptyList())

        viewModel.resetAndLoad()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.paginationState.value
        assertTrue(state.items.isEmpty())
        assertFalse(state.hasMore)
        assertEquals(PageLoadStatus.Idle, state.status)
    }
}
