package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for GetAllAppointmentsUseCase.
 *
 * Tests:
 * - Returns all appointments with correct hasPendingPayment values
 * - Appointment with no junction row has hasPendingPayment = true
 * - Appointment with junction row has hasPendingPayment = false
 *
 * Run with: ./gradlew testDebugUnitTest --tests GetAllAppointmentsUseCaseTest
 */
class GetAllAppointmentsUseCaseTest {

    private lateinit var appointmentRepository: AppointmentRepository
    private lateinit var useCase: GetAllAppointmentsUseCase

    private val appointment1 = Appointment(
        id = 1L,
        patientId = 1L,
        date = LocalDate.of(2024, 3, 15),
        timeStart = LocalTime.of(14, 0),
        durationMinutes = 60
    )

    private val appointment2 = Appointment(
        id = 2L,
        patientId = 2L,
        date = LocalDate.of(2024, 3, 10),
        timeStart = LocalTime.of(10, 0),
        durationMinutes = 50
    )

    @Before
    fun setUp() {
        appointmentRepository = mock()
        useCase = GetAllAppointmentsUseCase(appointmentRepository)
    }

    @Test
    fun execute_returnsAllAppointmentsWithCorrectPaymentStatus() = runTest {
        val expected = listOf(
            AppointmentWithPaymentStatus(appointment = appointment1, hasPendingPayment = true),
            AppointmentWithPaymentStatus(appointment = appointment2, hasPendingPayment = false)
        )
        whenever(appointmentRepository.getAllWithPaymentStatus()).thenReturn(flowOf(expected))

        val result = useCase.execute().first()

        assertEquals(2, result.size)
        assertEquals(expected, result)
    }

    @Test
    fun execute_appointmentWithNoJunctionRow_hasPendingPaymentTrue() = runTest {
        val unpaidAppointment = AppointmentWithPaymentStatus(
            appointment = appointment1,
            hasPendingPayment = true
        )
        whenever(appointmentRepository.getAllWithPaymentStatus())
            .thenReturn(flowOf(listOf(unpaidAppointment)))

        val result = useCase.execute().first()

        assertTrue(result.first().hasPendingPayment)
    }

    @Test
    fun execute_appointmentWithJunctionRow_hasPendingPaymentFalse() = runTest {
        val paidAppointment = AppointmentWithPaymentStatus(
            appointment = appointment2,
            hasPendingPayment = false
        )
        whenever(appointmentRepository.getAllWithPaymentStatus())
            .thenReturn(flowOf(listOf(paidAppointment)))

        val result = useCase.execute().first()

        assertFalse(result.first().hasPendingPayment)
    }

    @Test
    fun execute_emptyRepository_returnsEmptyList() = runTest {
        whenever(appointmentRepository.getAllWithPaymentStatus()).thenReturn(flowOf(emptyList()))

        val result = useCase.execute().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun execute_mixedPaymentStatus_returnsAllWithCorrectFlags() = runTest {
        val items = listOf(
            AppointmentWithPaymentStatus(appointment = appointment1, hasPendingPayment = true),
            AppointmentWithPaymentStatus(appointment = appointment2, hasPendingPayment = false)
        )
        whenever(appointmentRepository.getAllWithPaymentStatus()).thenReturn(flowOf(items))

        val result = useCase.execute().first()

        assertEquals(2, result.size)
        assertTrue(result[0].hasPendingPayment)
        assertFalse(result[1].hasPendingPayment)
    }
}
