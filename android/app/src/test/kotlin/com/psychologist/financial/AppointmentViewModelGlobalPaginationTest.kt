package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import com.psychologist.financial.domain.models.PageLoadStatus
import com.psychologist.financial.domain.models.PaginationState
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase
import com.psychologist.financial.domain.usecases.DeleteAppointmentUseCase
import com.psychologist.financial.domain.usecases.GetAllAppointmentsUseCase
import com.psychologist.financial.domain.usecases.GetPatientAppointmentsUseCase
import com.psychologist.financial.domain.usecases.UpdateAppointmentUseCase
import com.psychologist.financial.utils.Constants
import com.psychologist.financial.viewmodel.AppointmentViewState
import com.psychologist.financial.viewmodel.AppointmentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for AppointmentViewModel global-list pagination (T011 — TDD).
 *
 * Covers:
 * - resetGlobalList() loads page 0 and sets Idle after success
 * - loadNextGlobalPage() appends items from page 1
 * - loadNextGlobalPage() is a no-op while Loading
 * - setNameFilter() resets global pagination state and reloads
 * - setFilter() resets global pagination state with new status filter
 * - ALL/PENDING/PAID filter values are passed correctly to repository
 *
 * Run with: ./gradlew testDebugUnitTest --tests AppointmentViewModelGlobalPaginationTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentViewModelGlobalPaginationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockRepository: AppointmentRepository

    @Mock
    private lateinit var mockGetPatientAppointmentsUseCase: GetPatientAppointmentsUseCase

    @Mock
    private lateinit var mockCreateAppointmentUseCase: CreateAppointmentUseCase

    @Mock
    private lateinit var mockUpdateAppointmentUseCase: UpdateAppointmentUseCase

    @Mock
    private lateinit var mockGetAllAppointmentsUseCase: GetAllAppointmentsUseCase

    @Mock
    private lateinit var mockDeleteAppointmentUseCase: DeleteAppointmentUseCase

    private lateinit var viewModel: AppointmentViewModel

    private fun makeAppointment(id: Long): AppointmentWithPaymentStatus = AppointmentWithPaymentStatus(
        appointment = Appointment(
            id = id,
            patientId = 1L,
            date = LocalDate.of(2024, 1, 1),
            timeStart = LocalTime.of(9, 0),
            durationMinutes = 60
        ),
        hasPendingPayment = false,
        patientName = "Patient $id"
    )

    private fun makePage(startId: Long, count: Int): List<AppointmentWithPaymentStatus> =
        (startId until startId + count).map { makeAppointment(it) }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        viewModel = AppointmentViewModel(
            repository = mockRepository,
            getPatientAppointmentsUseCase = mockGetPatientAppointmentsUseCase,
            createAppointmentUseCase = mockCreateAppointmentUseCase,
            updateAppointmentUseCase = mockUpdateAppointmentUseCase,
            getAllAppointmentsUseCase = mockGetAllAppointmentsUseCase,
            deleteAppointmentUseCase = mockDeleteAppointmentUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // resetGlobalList — initial load
    // ========================================

    @Test
    fun `resetGlobalList sets status to Idle after loading page 0`() = runTest {
        val page0 = makePage(1L, Constants.PAGE_SIZE)
        whenever(
            mockRepository.getPagedWithPaymentStatus(
                searchTerm = "%",
                statusFilter = "ALL",
                page = 0
            )
        ).thenReturn(page0)

        viewModel.resetGlobalList()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.globalPaginationState.value
        assertEquals(PageLoadStatus.Idle, state.status)
        assertEquals(Constants.PAGE_SIZE, state.items.size)
        assertEquals(1, state.currentPage)
    }

    @Test
    fun `resetGlobalList resets items before loading new page`() = runTest {
        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%"), eq("ALL"), eq(0))
        ).thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetGlobalList()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Constants.PAGE_SIZE, viewModel.globalPaginationState.value.items.size)

        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%"), eq("ALL"), eq(0))
        ).thenReturn(makePage(100L, 3))

        viewModel.resetGlobalList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.globalPaginationState.value.items.size)
    }

    // ========================================
    // loadNextGlobalPage — append
    // ========================================

    @Test
    fun `loadNextGlobalPage appends page 1 items`() = runTest {
        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%"), eq("ALL"), eq(0))
        ).thenReturn(makePage(1L, Constants.PAGE_SIZE))
        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%"), eq("ALL"), eq(1))
        ).thenReturn(makePage(26L, Constants.PAGE_SIZE))

        viewModel.resetGlobalList()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadNextGlobalPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.PAGE_SIZE * 2, viewModel.globalPaginationState.value.items.size)
        assertEquals(2, viewModel.globalPaginationState.value.currentPage)
    }

    // ========================================
    // No-op guard
    // ========================================

    @Test
    fun `loadNextGlobalPage is no-op when status is Loading`() = runTest {
        whenever(
            mockRepository.getPagedWithPaymentStatus(any(), any(), eq(0))
        ).thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetGlobalList()
        viewModel.loadNextGlobalPage() // called while first load is in-flight
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.globalPaginationState.value.currentPage)
    }

    @Test
    fun `loadNextGlobalPage is no-op when hasMore is false`() = runTest {
        whenever(
            mockRepository.getPagedWithPaymentStatus(any(), any(), eq(0))
        ).thenReturn(makePage(1L, 3))

        viewModel.resetGlobalList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.globalPaginationState.value.hasMore)
        val pageBefore = viewModel.globalPaginationState.value.currentPage

        viewModel.loadNextGlobalPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(pageBefore, viewModel.globalPaginationState.value.currentPage)
    }

    // ========================================
    // Filter changes reset pagination
    // ========================================

    @Test
    fun `setNameFilter resets global pagination state and reloads`() = runTest {
        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%"), eq("ALL"), eq(0))
        ).thenReturn(makePage(1L, Constants.PAGE_SIZE))

        viewModel.resetGlobalList()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Constants.PAGE_SIZE, viewModel.globalPaginationState.value.items.size)

        val filtered = listOf(makeAppointment(5L).copy(patientName = "Maria Santos"))
        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%maria%"), eq("ALL"), eq(0))
        ).thenReturn(filtered)

        viewModel.setNameFilter("maria")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.globalPaginationState.value.items.size)
        assertEquals("Maria Santos", viewModel.globalPaginationState.value.items.first().patientName)
    }

    // ========================================
    // Status filter passed correctly
    // ========================================

    @Test
    fun `setFilter PENDING passes PENDING to repository`() = runTest {
        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%"), eq("PENDING"), eq(0))
        ).thenReturn(listOf(makeAppointment(1L).copy(hasPendingPayment = true)))

        viewModel.setFilter(AppointmentViewState.AppointmentFilter.PENDING)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.globalPaginationState.value.items.first().hasPendingPayment)
    }

    @Test
    fun `setFilter PAID passes PAID to repository`() = runTest {
        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%"), eq("PAID"), eq(0))
        ).thenReturn(listOf(makeAppointment(2L).copy(hasPendingPayment = false)))

        viewModel.setFilter(AppointmentViewState.AppointmentFilter.PAID)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.globalPaginationState.value.items.first().hasPendingPayment)
    }

    @Test
    fun `setFilter ALL passes ALL to repository`() = runTest {
        whenever(
            mockRepository.getPagedWithPaymentStatus(eq("%"), eq("ALL"), eq(0))
        ).thenReturn(makePage(1L, 5))

        viewModel.setFilter(AppointmentViewState.AppointmentFilter.ALL)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.globalPaginationState.value.items.size)
    }
}
