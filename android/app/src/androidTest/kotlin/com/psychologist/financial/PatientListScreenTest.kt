package com.psychologist.financial

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.ui.screens.PatientListScreen
import com.psychologist.financial.ui.theme.PatientTheme
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PatientViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * UI tests for PatientListScreen using Espresso
 *
 * Coverage:
 * - Display patient list with items
 * - FAB (Add button) triggers form screen navigation
 * - Filter chips toggle Active/All patients
 * - Search functionality
 * - Empty state message
 * - Loading state display
 * - Error state display
 * - Patient item interactions (click to detail)
 * - Scroll and pagination
 *
 * Total: 18 test cases
 * Uses Compose Test Framework and Espresso
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class PatientListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Mock
    private lateinit var mockViewModel: PatientViewModel

    private lateinit var patientListStateFlow: MutableStateFlow<PatientViewState.ListState>

    private val mockPatients = listOf(
        Patient(
            id = 1L,
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = LocalDate.of(2024, 1, 15),
            registrationDate = LocalDate.of(2024, 1, 15),
            lastAppointmentDate = LocalDate.of(2024, 2, 20),
            appointmentCount = 5,
            amountDueNow = 500.0
        ),
        Patient(
            id = 2L,
            name = "Maria Santos",
            phone = "(11) 98888-8888",
            email = "maria@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = LocalDate.of(2024, 2, 1),
            registrationDate = LocalDate.of(2024, 2, 1),
            lastAppointmentDate = null,
            appointmentCount = 0,
            amountDueNow = 0.0
        ),
        Patient(
            id = 3L,
            name = "Carlos Oliveira",
            phone = "(11) 97777-7777",
            email = "carlos@example.com",
            status = PatientStatus.INACTIVE,
            initialConsultDate = LocalDate.of(2023, 6, 10),
            registrationDate = LocalDate.of(2023, 6, 10),
            lastAppointmentDate = LocalDate.of(2024, 1, 30),
            appointmentCount = 12,
            amountDueNow = 1200.0
        )
    )

    private fun setupViewModel(initialState: PatientViewState.ListState = PatientViewState.ListState.Loading) {
        MockitoAnnotations.openMocks(this)
        patientListStateFlow = MutableStateFlow(initialState)

        whenever(mockViewModel.patientListState).thenReturn(patientListStateFlow)
        whenever(mockViewModel.loadPatients()).then {
            patientListStateFlow.value = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        }
        whenever(mockViewModel.refreshPatients()).then {
            patientListStateFlow.value = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        }
    }

    // ========================================
    // Display Tests
    // ========================================

    @Test
    fun patientList_displaysList_showsAllPatients() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("João Silva").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maria Santos").assertIsDisplayed()
        composeTestRule.onNodeWithText("(11) 99999-9999").assertIsDisplayed()
    }

    @Test
    fun patientList_emptyState_showsEmptyMessage() {
        // Arrange
        setupViewModel(initialState = PatientViewState.ListState.Empty)

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Nenhum paciente").assertIsDisplayed()
    }

    @Test
    fun patientList_loadingState_showsLoadingIndicator() {
        // Arrange
        setupViewModel(initialState = PatientViewState.ListState.Loading)

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription("Carregando").assertIsDisplayed()
    }

    @Test
    fun patientList_errorState_showsErrorMessage() {
        // Arrange
        val errorMessage = "Erro ao carregar pacientes"
        setupViewModel(
            initialState = PatientViewState.ListState.Error(message = errorMessage)
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // ========================================
    // FAB (Add Button) Tests
    // ========================================

    @Test
    fun patientList_fabButton_isDisplayed() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )
        var addPatientCalled = false

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { addPatientCalled = true },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription("Adicionar Paciente")
            .assertIsDisplayed()
    }

    @Test
    fun patientList_clickFab_callsOnAddPatient() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )
        var addPatientCalled = false

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { addPatientCalled = true },
                    onSelectPatient = { }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Adicionar Paciente")
            .performClick()

        // Assert
        assert(addPatientCalled)
    }

    // ========================================
    // Filter Tests
    // ========================================

    @Test
    fun patientList_filterChips_areDisplayed() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Ativos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Todos").assertIsDisplayed()
    }

    @Test
    fun patientList_filterChip_togglesToAll() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        composeTestRule.onNodeWithText("Todos").performClick()

        // Assert
        verify(mockViewModel).toggleInactiveFilter()
    }

    // ========================================
    // Search Tests
    // ========================================

    @Test
    fun patientList_searchField_isVisible() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithTag("searchField").assertIsDisplayed()
    }

    @Test
    fun patientList_searchInput_filtersResults() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        composeTestRule.onNodeWithTag("searchField").performTextInput("João")

        // Assert
        verify(mockViewModel).searchPatients("João")
    }

    // ========================================
    // Patient Item Interaction Tests
    // ========================================

    @Test
    fun patientList_clickPatientItem_callsOnSelectPatient() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )
        var selectedPatientId: Long? = null

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { patientId -> selectedPatientId = patientId }
                )
            }
        }

        composeTestRule.onNodeWithText("João Silva").performClick()

        // Assert
        assert(selectedPatientId == 1L)
    }

    @Test
    fun patientList_patientItem_displayssContactInfo() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("(11) 99999-9999").assertIsDisplayed()
        composeTestRule.onNodeWithText("joao@example.com").assertIsDisplayed()
    }

    @Test
    fun patientList_patientItem_displaysStatusBadge() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert - Active patients should show status badge
        composeTestRule.onAllNodesWithTag("statusBadge")
            .assertCountEquals(2)  // Two active patients
    }

    // ========================================
    // List Navigation Tests
    // ========================================

    @Test
    fun patientList_scrolling_displaysMoreItems() {
        // Arrange
        val manyPatients = (1..15).map { i ->
            Patient(
                id = i.toLong(),
                name = "Patient $i",
                phone = "(11) 9999${String.format("%04d", i)}",
                email = "patient$i@example.com",
                status = PatientStatus.ACTIVE,
                initialConsultDate = LocalDate.now(),
                registrationDate = LocalDate.now(),
                lastAppointmentDate = null,
                appointmentCount = null,
                amountDueNow = null
            )
        }
        setupViewModel(
            initialState = PatientViewState.ListState.Success(patients = manyPatients)
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert - Initial view
        composeTestRule.onNodeWithText("Patient 1").assertIsDisplayed()

        // Act - Scroll down
        composeTestRule.onNodeWithTag("patientList")
            .performScrollToIndex(10)

        // Assert - Later items visible after scroll
        composeTestRule.onNodeWithText("Patient 11").assertIsDisplayed()
    }

    @Test
    fun patientList_topAppBar_displaysTitle() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Pacientes").assertIsDisplayed()
    }

    // ========================================
    // State Transition Tests
    // ========================================

    @Test
    fun patientList_stateTransitionLoadingToSuccess_displaysContent() {
        // Arrange
        setupViewModel(initialState = PatientViewState.ListState.Loading)

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert - Loading state visible initially
        composeTestRule.onNodeWithContentDescription("Carregando").assertIsDisplayed()

        // Act - Update state
        patientListStateFlow.value = PatientViewState.ListState.Success(
            patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
        )

        // Assert - Content visible after state change
        composeTestRule.onNodeWithText("João Silva").assertIsDisplayed()
    }

    @Test
    fun patientList_refreshButton_reloadsData() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Recarregar")
            .performClick()

        // Assert
        verify(mockViewModel).refreshPatients()
    }

    // ========================================
    // Layout Tests
    // ========================================

    @Test
    fun patientList_layout_hasTopAppBar() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Pacientes").assertIsDisplayed()
    }

    @Test
    fun patientList_layout_hasFloatingActionButton() {
        // Arrange
        setupViewModel(
            initialState = PatientViewState.ListState.Success(
                patients = mockPatients.filter { it.status == PatientStatus.ACTIVE }
            )
        )

        // Act
        composeTestRule.setContent {
            PatientTheme {
                PatientListScreen(
                    viewModel = mockViewModel,
                    onAddPatient = { },
                    onSelectPatient = { }
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithContentDescription("Adicionar Paciente")
            .assertIsDisplayed()
    }
}
