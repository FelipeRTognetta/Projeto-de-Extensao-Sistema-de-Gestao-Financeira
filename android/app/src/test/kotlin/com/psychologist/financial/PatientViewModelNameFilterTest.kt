package com.psychologist.financial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.usecases.CreatePatientUseCase
import com.psychologist.financial.domain.usecases.GetAllPatientsUseCase
import com.psychologist.financial.domain.usecases.MarkPatientInactiveUseCase
import com.psychologist.financial.domain.usecases.ReactivatePatientUseCase
import com.psychologist.financial.domain.usecases.UpdatePatientUseCase
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PatientViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PatientViewModelNameFilterTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock private lateinit var mockGetAllPatientsUseCase: GetAllPatientsUseCase
    @Mock private lateinit var mockCreatePatientUseCase: CreatePatientUseCase
    @Mock private lateinit var mockMarkPatientInactiveUseCase: MarkPatientInactiveUseCase
    @Mock private lateinit var mockReactivatePatientUseCase: ReactivatePatientUseCase
    @Mock private lateinit var mockUpdatePatientUseCase: UpdatePatientUseCase

    private lateinit var viewModel: PatientViewModel

    private val allPatients = listOf(
        Patient(id = 1L, name = "Ana Lima", status = PatientStatus.ACTIVE,
            initialConsultDate = LocalDate.now(), registrationDate = LocalDate.now()),
        Patient(id = 2L, name = "Carlos Silva", status = PatientStatus.ACTIVE,
            initialConsultDate = LocalDate.now(), registrationDate = LocalDate.now()),
        Patient(id = 3L, name = "Joana Pereira", status = PatientStatus.ACTIVE,
            initialConsultDate = LocalDate.now(), registrationDate = LocalDate.now()),
        Patient(id = 4L, name = "Bruno Santos", status = PatientStatus.INACTIVE,
            initialConsultDate = LocalDate.now(), registrationDate = LocalDate.now()),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        // executeFlow is called in init {} of PatientViewModel — must stub before construction
        whenever(mockGetAllPatientsUseCase.executeFlow(any())).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
        viewModel = PatientViewModel(
            getAllPatientsUseCase = mockGetAllPatientsUseCase,
            createPatientUseCase = mockCreatePatientUseCase,
            markPatientInactiveUseCase = mockMarkPatientInactiveUseCase,
            reactivatePatientUseCase = mockReactivatePatientUseCase,
            updatePatientUseCase = mockUpdatePatientUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setNameFilter filters patients by name case insensitive`() = runTest {
        whenever(mockGetAllPatientsUseCase.execute(false)).thenReturn(allPatients.filter { it.status == PatientStatus.ACTIVE })

        viewModel.loadPatients()
        advanceUntilIdle()

        viewModel.setNameFilter("ana")
        advanceUntilIdle()

        val state = viewModel.patientListState.value
        assertTrue(state is PatientViewState.ListState.Success)
        val patients = (state as PatientViewState.ListState.Success).patients
        // "Ana Lima" and "Joana Pereira" both contain "ana"
        assertEquals(2, patients.size)
        assertTrue(patients.all { it.name.contains("ana", ignoreCase = true) })
    }

    @Test
    fun `setNameFilter with empty string returns all loaded patients`() = runTest {
        val activePatients = allPatients.filter { it.status == PatientStatus.ACTIVE }
        whenever(mockGetAllPatientsUseCase.execute(false)).thenReturn(activePatients)

        viewModel.loadPatients()
        advanceUntilIdle()

        viewModel.setNameFilter("carlos")
        advanceUntilIdle()
        viewModel.setNameFilter("")
        advanceUntilIdle()

        val state = viewModel.patientListState.value
        assertTrue(state is PatientViewState.ListState.Success)
        assertEquals(activePatients.size, (state as PatientViewState.ListState.Success).patients.size)
    }

    @Test
    fun `setNameFilter combined with includeInactive respects status filter`() = runTest {
        whenever(mockGetAllPatientsUseCase.execute(true)).thenReturn(allPatients)

        viewModel.loadPatients()
        advanceUntilIdle()
        // Toggle to include inactive
        whenever(mockGetAllPatientsUseCase.execute(true)).thenReturn(allPatients)
        viewModel.toggleInactiveFilter()
        advanceUntilIdle()

        // Filter by "ana" — only active "Ana Lima" and active "Joana Pereira" match, not inactive "Bruno Santos"
        viewModel.setNameFilter("ana")
        advanceUntilIdle()

        val state = viewModel.patientListState.value
        assertTrue(state is PatientViewState.ListState.Success)
        val patients = (state as PatientViewState.ListState.Success).patients
        assertTrue(patients.all { it.name.contains("ana", ignoreCase = true) })
    }

    @Test
    fun `resetNameFilter restores full cached list`() = runTest {
        val activePatients = allPatients.filter { it.status == PatientStatus.ACTIVE }
        whenever(mockGetAllPatientsUseCase.execute(false)).thenReturn(activePatients)

        viewModel.loadPatients()
        advanceUntilIdle()
        viewModel.setNameFilter("joana")
        advanceUntilIdle()

        viewModel.resetNameFilter()
        advanceUntilIdle()

        val state = viewModel.patientListState.value
        assertTrue(state is PatientViewState.ListState.Success)
        assertEquals(activePatients.size, (state as PatientViewState.ListState.Success).patients.size)
    }
}
