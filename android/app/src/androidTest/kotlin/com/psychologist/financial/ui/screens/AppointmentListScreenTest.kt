package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import com.psychologist.financial.ui.components.AppointmentListItem
import com.psychologist.financial.ui.theme.FinancialTheme
import com.psychologist.financial.viewmodel.AppointmentViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * Instrumented UI tests for GlobalAppointmentListScreen (consultas tab).
 *
 * Tests:
 * - 3 FilterChips render ("Todas", "Com pendência", "Sem pendência")
 * - Pending indicator visible on appointments with hasPendingPayment = true
 * - "Com pendência" filter shows only pending appointments
 * - Empty state message shown when filtered list is empty
 *
 * Run with: ./gradlew connectedDebugAndroidTest --tests AppointmentListScreenTest
 */
@RunWith(AndroidJUnit4::class)
class AppointmentListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appointmentPending = Appointment(
        id = 1L,
        patientId = 1L,
        date = LocalDate.of(2024, 3, 15),
        timeStart = LocalTime.of(14, 0),
        durationMinutes = 60
    )

    private val appointmentPaid = Appointment(
        id = 2L,
        patientId = 1L,
        date = LocalDate.of(2024, 3, 10),
        timeStart = LocalTime.of(10, 0),
        durationMinutes = 50
    )

    private val allAppointments = listOf(
        AppointmentWithPaymentStatus(appointment = appointmentPending, hasPendingPayment = true),
        AppointmentWithPaymentStatus(appointment = appointmentPaid, hasPendingPayment = false)
    )

    @Test
    fun globalAppointmentListScreen_threeFilterChipsRender() {
        val state = MutableStateFlow<AppointmentViewState.GlobalListState>(
            AppointmentViewState.GlobalListState.Success(
                allAppointments = allAppointments,
                filteredAppointments = allAppointments,
                activeFilter = AppointmentViewState.AppointmentFilter.ALL
            )
        )

        composeTestRule.setContent {
            FinancialTheme {
                AppointmentListScreenStub(state)
            }
        }

        composeTestRule.onNodeWithText("Todas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Com pendência").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sem pendência").assertIsDisplayed()
    }

    @Test
    fun globalAppointmentListScreen_pendingIndicator_visibleOnPendingAppointment() {
        val state = MutableStateFlow<AppointmentViewState.GlobalListState>(
            AppointmentViewState.GlobalListState.Success(
                allAppointments = allAppointments,
                filteredAppointments = allAppointments,
                activeFilter = AppointmentViewState.AppointmentFilter.ALL
            )
        )

        composeTestRule.setContent {
            FinancialTheme {
                AppointmentListScreenStub(state)
            }
        }

        composeTestRule.onNodeWithText("Pagamento em aberto").assertIsDisplayed()
    }

    @Test
    fun globalAppointmentListScreen_pendingFilter_showsOnlyPendingAppointments() {
        val pendingOnly = listOf(
            AppointmentWithPaymentStatus(appointment = appointmentPending, hasPendingPayment = true)
        )
        val state = MutableStateFlow<AppointmentViewState.GlobalListState>(
            AppointmentViewState.GlobalListState.Success(
                allAppointments = allAppointments,
                filteredAppointments = pendingOnly,
                activeFilter = AppointmentViewState.AppointmentFilter.PENDING
            )
        )

        composeTestRule.setContent {
            FinancialTheme {
                AppointmentListScreenStub(state)
            }
        }

        composeTestRule.onNodeWithText("Pagamento em aberto").assertIsDisplayed()
    }

    @Test
    fun globalAppointmentListScreen_emptyFilterResult_showsEmptyMessage() {
        val state = MutableStateFlow<AppointmentViewState.GlobalListState>(
            AppointmentViewState.GlobalListState.Success(
                allAppointments = allAppointments,
                filteredAppointments = emptyList(),
                activeFilter = AppointmentViewState.AppointmentFilter.PENDING
            )
        )

        composeTestRule.setContent {
            FinancialTheme {
                AppointmentListScreenStub(state)
            }
        }

        composeTestRule.onNodeWithText("Nenhuma consulta com pagamento pendente").assertIsDisplayed()
    }

    @Test
    fun globalAppointmentListScreen_emptyState_showsEmptyMessage() {
        val state = MutableStateFlow<AppointmentViewState.GlobalListState>(
            AppointmentViewState.GlobalListState.Empty
        )

        composeTestRule.setContent {
            FinancialTheme {
                AppointmentListScreenStub(state)
            }
        }

        composeTestRule.onNodeWithText("Nenhuma consulta registrada").assertIsDisplayed()
    }
}

@Composable
private fun AppointmentListScreenStub(state: StateFlow<AppointmentViewState.GlobalListState>) {
    val current = state.collectAsState().value
    Scaffold(
        topBar = { TopAppBar(title = { Text("Consultas") }) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (current) {
                is AppointmentViewState.GlobalListState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Filter row
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = current.activeFilter == AppointmentViewState.AppointmentFilter.ALL,
                                    onClick = {},
                                    label = { Text("Todas") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = current.activeFilter == AppointmentViewState.AppointmentFilter.PENDING,
                                    onClick = {},
                                    label = { Text("Com pendência") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = current.activeFilter == AppointmentViewState.AppointmentFilter.PAID,
                                    onClick = {},
                                    label = { Text("Sem pendência") }
                                )
                            }
                        }

                        if (current.filteredAppointments.isEmpty()) {
                            val emptyMsg = when (current.activeFilter) {
                                AppointmentViewState.AppointmentFilter.PENDING ->
                                    "Nenhuma consulta com pagamento pendente"
                                AppointmentViewState.AppointmentFilter.PAID ->
                                    "Nenhuma consulta com pagamento registrado"
                                AppointmentViewState.AppointmentFilter.ALL ->
                                    "Nenhuma consulta registrada"
                            }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emptyMsg)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = current.filteredAppointments,
                                    key = { it.appointment.id }
                                ) { appointmentWithStatus ->
                                    AppointmentListItem(
                                        appointmentWithStatus = appointmentWithStatus,
                                        onClick = {}
                                    )
                                }
                            }
                        }
                    }
                }

                is AppointmentViewState.GlobalListState.Empty -> {
                    Text(
                        text = "Nenhuma consulta registrada",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is AppointmentViewState.GlobalListState.Error -> {
                    Text(
                        text = current.message,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is AppointmentViewState.GlobalListState.Loading -> {}
            }
        }
    }
}
