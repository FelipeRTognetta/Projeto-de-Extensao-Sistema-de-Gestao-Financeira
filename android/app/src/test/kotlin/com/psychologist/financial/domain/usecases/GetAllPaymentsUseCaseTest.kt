package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.data.repositories.PaymentWithDetails
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Payment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for GetAllPaymentsUseCase.
 *
 * Tests:
 * - Returns all payments from all patients
 * - Ordering by payment date DESC (most recent first)
 * - Includes linked appointments per payment
 *
 * Run with: ./gradlew testDebugUnitTest --tests GetAllPaymentsUseCaseTest
 */
class GetAllPaymentsUseCaseTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    private lateinit var useCase: GetAllPaymentsUseCase

    private val appointment1 = Appointment(
        id = 10L,
        patientId = 1L,
        date = LocalDate.of(2024, 3, 10),
        timeStart = LocalTime.of(14, 0),
        durationMinutes = 60
    )

    private val appointment2 = Appointment(
        id = 11L,
        patientId = 2L,
        date = LocalDate.of(2024, 3, 5),
        timeStart = LocalTime.of(10, 0),
        durationMinutes = 60
    )

    private val payment1 = Payment(
        id = 1L,
        patientId = 1L,
        amount = BigDecimal("150.00"),
        paymentDate = LocalDate.of(2024, 3, 15)
    )

    private val payment2 = Payment(
        id = 2L,
        patientId = 2L,
        amount = BigDecimal("200.00"),
        paymentDate = LocalDate.of(2024, 3, 10)
    )

    private val payment3 = Payment(
        id = 3L,
        patientId = 1L,
        amount = BigDecimal("75.00"),
        paymentDate = LocalDate.of(2024, 3, 1)
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = GetAllPaymentsUseCase(paymentRepository)
    }

    // ========================================
    // Basic Retrieval Tests
    // ========================================

    @Test
    fun execute_returnsAllPaymentsFromAllPatients() = runTest {
        // Arrange
        val paymentsWithDetails = listOf(
            PaymentWithDetails(payment = payment1, appointments = listOf(appointment1)),
            PaymentWithDetails(payment = payment2, appointments = emptyList()),
            PaymentWithDetails(payment = payment3, appointments = emptyList())
        )
        whenever(paymentRepository.getAllWithAppointments()).thenReturn(flowOf(paymentsWithDetails))

        // Act
        val result = useCase.execute().first()

        // Assert
        assert(result.size == 3) { "Expected 3 payments, got ${result.size}" }
    }

    @Test
    fun execute_emptyRepository_returnsEmptyList() = runTest {
        // Arrange
        whenever(paymentRepository.getAllWithAppointments()).thenReturn(flowOf(emptyList()))

        // Act
        val result = useCase.execute().first()

        // Assert
        assert(result.isEmpty()) { "Expected empty list" }
    }

    // ========================================
    // Ordering Tests
    // ========================================

    @Test
    fun execute_paymentsOrderedByDateDesc() = runTest {
        // Arrange: payments already ordered DESC by date (DAO responsibility)
        val paymentsWithDetails = listOf(
            PaymentWithDetails(payment = payment1, appointments = emptyList()), // 2024-03-15 (newest)
            PaymentWithDetails(payment = payment2, appointments = emptyList()), // 2024-03-10
            PaymentWithDetails(payment = payment3, appointments = emptyList())  // 2024-03-01 (oldest)
        )
        whenever(paymentRepository.getAllWithAppointments()).thenReturn(flowOf(paymentsWithDetails))

        // Act
        val result = useCase.execute().first()

        // Assert: first payment is the most recent
        assert(result[0].payment.id == 1L) { "First payment should be most recent (id=1)" }
        assert(result[1].payment.id == 2L)
        assert(result[2].payment.id == 3L)
        assert(!result[0].payment.paymentDate.isBefore(result[1].payment.paymentDate))
        assert(!result[1].payment.paymentDate.isBefore(result[2].payment.paymentDate))
    }

    // ========================================
    // Appointment Inclusion Tests
    // ========================================

    @Test
    fun execute_includesLinkedAppointmentsPerPayment() = runTest {
        // Arrange
        val paymentsWithDetails = listOf(
            PaymentWithDetails(payment = payment1, appointments = listOf(appointment1)),
            PaymentWithDetails(payment = payment2, appointments = emptyList())
        )
        whenever(paymentRepository.getAllWithAppointments()).thenReturn(flowOf(paymentsWithDetails))

        // Act
        val result = useCase.execute().first()

        // Assert
        assert(result[0].appointments.size == 1) { "First payment should have 1 appointment" }
        assert(result[0].appointments[0].id == 10L)
        assert(result[1].appointments.isEmpty()) { "Second payment should have 0 appointments" }
    }

    @Test
    fun execute_paymentWithMultipleAppointments_includesAll() = runTest {
        // Arrange
        val paymentWithMultiple = PaymentWithDetails(
            payment = payment1,
            appointments = listOf(appointment1, appointment2)
        )
        whenever(paymentRepository.getAllWithAppointments()).thenReturn(flowOf(listOf(paymentWithMultiple)))

        // Act
        val result = useCase.execute().first()

        // Assert
        assert(result[0].appointments.size == 2) { "Payment should have 2 appointments" }
    }
}
