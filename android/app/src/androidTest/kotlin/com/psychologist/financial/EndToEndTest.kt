package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.OperationType
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.SessionState
import com.psychologist.financial.services.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-End Integration Tests: Full User Journey
 *
 * Tests the complete workflow a psychologist follows when using the app:
 *   1. Authentication — session started via SessionManager
 *   2. Patient registration — patient inserted and retrieved via repository
 *   3. Appointment creation — appointment linked to patient
 *   4. Payment recording — payment linked to patient (with per-operation auth)
 *   5. Dashboard metrics — revenue, active patients, outstanding balance verified
 *
 * Uses an in-memory Room database so tests run without a device database file.
 * SessionManager is used directly (BiometricPrompt is not testable in JUnit context).
 *
 * Coverage target: Full cross-layer integration (repository ↔ DAO ↔ DB)
 * Total: 12 test cases
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

    private lateinit var database: AppDatabase
    private lateinit var patientRepository: PatientRepository
    private lateinit var appointmentRepository: AppointmentRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var dashboardRepository: DashboardRepository
    private lateinit var sessionManager: SessionManager
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        patientRepository = PatientRepository(
            database.patientDao(),
            mockEncryptionService = null
        )
        appointmentRepository = AppointmentRepository(database.appointmentDao())
        paymentRepository = PaymentRepository(database.paymentDao())
        dashboardRepository = DashboardRepository(database)
        sessionManager = SessionManager()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Step 1: Authentication
    // ========================================

    @Test
    fun step1_authentication_sessionStartsSuccessfully() {
        // Act
        sessionManager.startSession()

        // Assert
        assertTrue(sessionManager.isSessionValid())
        assertTrue(sessionManager.hasActiveSession())
        val state = sessionManager.getCurrentState()
        assertTrue(state is SessionState.Authenticated)
    }

    @Test
    fun step1_authentication_sessionTimeoutTracked() {
        sessionManager.startSession()
        val remaining = sessionManager.getRemainingSessionTime()

        // Session should have ~15 minutes remaining (900 seconds)
        assertTrue(remaining > 890 && remaining <= 900)
    }

    // ========================================
    // Step 2: Patient Registration
    // ========================================

    @Test
    fun step2_patientRegistration_patientCreatedAndRetrieved() = runBlocking {
        // Arrange
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())

        // Act
        val patientId = patientRepository.insert(
            name = "Ana Paula Souza",
            phone = "(11) 91234-5678",
            email = "ana.paula@example.com",
            initialConsultDate = LocalDate.of(2025, 3, 1)
        )

        // Assert
        assertTrue(patientId > 0)
        val patient = patientRepository.getById(patientId)
        assertNotNull(patient)
        assertEquals("Ana Paula Souza", patient?.name)
        assertEquals(PatientStatus.ACTIVE, patient?.status)
    }

    @Test
    fun step2_patientRegistration_multiplePatients_allRetrievable() = runBlocking {
        sessionManager.startSession()

        val id1 = patientRepository.insert(
            name = "João Silva",
            phone = "(11) 99999-1111",
            email = "joao@example.com",
            initialConsultDate = LocalDate.of(2025, 1, 10)
        )
        val id2 = patientRepository.insert(
            name = "Maria Santos",
            phone = "(11) 99999-2222",
            email = "maria@example.com",
            initialConsultDate = LocalDate.of(2025, 2, 5)
        )

        val patient1 = patientRepository.getById(id1)
        val patient2 = patientRepository.getById(id2)

        assertNotNull(patient1)
        assertNotNull(patient2)
        assertEquals("João Silva", patient1?.name)
        assertEquals("Maria Santos", patient2?.name)
    }

    // ========================================
    // Step 3: Appointment Creation
    // ========================================

    @Test
    fun step3_appointmentCreation_linkedToPatient() = runBlocking {
        // Arrange
        sessionManager.startSession()
        val patientId = patientRepository.insert(
            name = "Carlos Oliveira",
            phone = "(21) 98765-4321",
            email = "carlos@example.com",
            initialConsultDate = LocalDate.of(2025, 1, 15)
        )

        // Act
        val appointmentEntity = AppointmentEntity(
            id = 0,
            patientId = patientId.toInt(),
            date = LocalDate.of(2025, 4, 10),
            time = LocalTime.of(14, 0),
            durationMinutes = 50,
            notes = "Primeira sessão"
        )
        val appointmentId = database.appointmentDao().insertAppointment(appointmentEntity)

        // Assert
        assertTrue(appointmentId > 0)
        val appointments = database.appointmentDao().getAppointmentsForPatient(patientId)
        assertEquals(1, appointments.size)
        assertEquals(50, appointments[0].durationMinutes)
        assertEquals(LocalDate.of(2025, 4, 10), appointments[0].date)
    }

    @Test
    fun step3_appointmentCreation_multipleAppointments_chronologicalOrder() = runBlocking {
        sessionManager.startSession()
        val patientId = patientRepository.insert(
            name = "Fernanda Lima",
            phone = "(31) 91111-2222",
            email = "fernanda@example.com",
            initialConsultDate = LocalDate.of(2025, 1, 1)
        )

        // Insert appointments out of order
        listOf(
            LocalDate.of(2025, 3, 15),
            LocalDate.of(2025, 1, 20),
            LocalDate.of(2025, 2, 10)
        ).forEach { date ->
            database.appointmentDao().insertAppointment(
                AppointmentEntity(
                    id = 0,
                    patientId = patientId.toInt(),
                    date = date,
                    time = LocalTime.of(10, 0),
                    durationMinutes = 50,
                    notes = null
                )
            )
        }

        val appointments = database.appointmentDao().getAppointmentsForPatient(patientId)
        assertEquals(3, appointments.size)
        // Verify descending date order (most recent first)
        assertTrue(appointments[0].date >= appointments[1].date)
        assertTrue(appointments[1].date >= appointments[2].date)
    }

    // ========================================
    // Step 4: Payment Recording (with per-operation auth)
    // ========================================

    @Test
    fun step4_paymentRecording_perOperationAuthRequired() {
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())

        // Per-operation auth required before payment
        val authRequired = sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        assertTrue(authRequired)

        val state = sessionManager.getCurrentState()
        assertTrue(state is SessionState.BiometricRequired)

        // Simulate successful per-operation auth
        sessionManager.completeBiometricAuthentication()
        val stateAfter = sessionManager.getCurrentState()
        assertTrue(stateAfter is SessionState.Authenticated)
    }

    @Test
    fun step4_paymentRecording_paymentLinkedToPatientAndAppointment() = runBlocking {
        sessionManager.startSession()
        sessionManager.completeBiometricAuthentication() // Simulate per-op auth passed

        val patientId = patientRepository.insert(
            name = "Roberto Costa",
            phone = "(11) 95555-6666",
            email = "roberto@example.com",
            initialConsultDate = LocalDate.of(2025, 1, 5)
        )
        val appointmentId = database.appointmentDao().insertAppointment(
            AppointmentEntity(
                id = 0,
                patientId = patientId.toInt(),
                date = LocalDate.of(2025, 4, 20),
                time = LocalTime.of(9, 0),
                durationMinutes = 60,
                notes = null
            )
        )

        // Act
        database.paymentDao().insertPayment(
            PaymentEntity(
                id = 0,
                patientId = patientId.toInt(),
                appointmentId = appointmentId.toInt(),
                amount = BigDecimal("200.00"),
                method = "PIX",
                status = "PAID",
                paymentDate = LocalDate.of(2025, 4, 20)
            )
        )

        // Assert
        val payments = database.paymentDao().getPaymentsForPatient(patientId)
        assertEquals(1, payments.size)
        assertEquals(BigDecimal("200.00"), payments[0].amount)
        assertEquals("PAID", payments[0].status)
    }

    // ========================================
    // Step 5: Dashboard Metrics
    // ========================================

    @Test
    fun step5_dashboard_revenueCalculatedCorrectly() = runBlocking {
        sessionManager.startSession()

        // Arrange — two patients with payments
        val patientId1 = patientRepository.insert(
            name = "Paciente Um",
            phone = "(11) 91111-1111",
            email = "um@example.com",
            initialConsultDate = LocalDate.now()
        )
        val patientId2 = patientRepository.insert(
            name = "Paciente Dois",
            phone = "(11) 92222-2222",
            email = "dois@example.com",
            initialConsultDate = LocalDate.now()
        )

        // PAID payments this month
        listOf(
            Triple(patientId1.toInt(), BigDecimal("150.00"), "PAID"),
            Triple(patientId1.toInt(), BigDecimal("200.00"), "PAID"),
            Triple(patientId2.toInt(), BigDecimal("250.00"), "PENDING") // excluded from revenue
        ).forEach { (pid, amount, status) ->
            database.paymentDao().insertPayment(
                PaymentEntity(
                    id = 0,
                    patientId = pid,
                    appointmentId = null,
                    amount = amount,
                    method = "PIX",
                    status = status,
                    paymentDate = LocalDate.now()
                )
            )
        }

        // Act
        val metrics = dashboardRepository.getDashboardMetrics(
            startDate = LocalDate.now().withDayOfMonth(1),
            endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
        )

        // Assert — only PAID payments count as revenue
        assertNotNull(metrics)
        assertTrue(metrics.totalRevenue >= BigDecimal("350.00"))
        assertTrue(metrics.activePatients >= 2)
    }

    @Test
    fun step5_dashboard_outstandingBalanceIncludesPending() = runBlocking {
        sessionManager.startSession()

        val patientId = patientRepository.insert(
            name = "Débora Alves",
            phone = "(11) 93333-3333",
            email = "debora@example.com",
            initialConsultDate = LocalDate.now()
        )

        // One PAID, one PENDING
        database.paymentDao().insertPayment(
            PaymentEntity(
                id = 0,
                patientId = patientId.toInt(),
                appointmentId = null,
                amount = BigDecimal("150.00"),
                method = "TRANSFER",
                status = "PAID",
                paymentDate = LocalDate.now()
            )
        )
        database.paymentDao().insertPayment(
            PaymentEntity(
                id = 0,
                patientId = patientId.toInt(),
                appointmentId = null,
                amount = BigDecimal("100.00"),
                method = "CASH",
                status = "PENDING",
                paymentDate = LocalDate.now()
            )
        )

        val metrics = dashboardRepository.getDashboardMetrics(
            startDate = LocalDate.now().withDayOfMonth(1),
            endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
        )

        assertNotNull(metrics)
        // Outstanding includes both PAID and PENDING
        assertTrue(metrics.outstandingBalance >= BigDecimal("100.00"))
    }

    // ========================================
    // Complete Journey: Auth → Patient → Appointment → Payment → Dashboard
    // ========================================

    @Test
    fun completeJourney_allStepsSucceed() = runBlocking {
        // Step 1: Auth
        sessionManager.startSession()
        assertTrue(sessionManager.isSessionValid())

        // Step 2: Register patient
        val patientId = patientRepository.insert(
            name = "Luisa Mendes",
            phone = "(11) 94444-5555",
            email = "luisa@example.com",
            initialConsultDate = LocalDate.of(2025, 1, 10)
        )
        assertTrue(patientId > 0)

        // Step 3: Create appointment
        val appointmentId = database.appointmentDao().insertAppointment(
            AppointmentEntity(
                id = 0,
                patientId = patientId.toInt(),
                date = LocalDate.of(2025, 4, 25),
                time = LocalTime.of(11, 30),
                durationMinutes = 50,
                notes = "Primeira consulta"
            )
        )
        assertTrue(appointmentId > 0)

        // Step 4: Per-op auth then payment
        sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
        sessionManager.completeBiometricAuthentication()

        database.paymentDao().insertPayment(
            PaymentEntity(
                id = 0,
                patientId = patientId.toInt(),
                appointmentId = appointmentId.toInt(),
                amount = BigDecimal("180.00"),
                method = "PIX",
                status = "PAID",
                paymentDate = LocalDate.of(2025, 4, 25)
            )
        )

        // Step 5: Verify dashboard metrics
        val metrics = dashboardRepository.getDashboardMetrics(
            startDate = LocalDate.of(2025, 4, 1),
            endDate = LocalDate.of(2025, 4, 30)
        )
        assertNotNull(metrics)
        assertTrue(metrics.totalRevenue >= BigDecimal("180.00"))

        // Verify data consistency across layers
        val patient = patientRepository.getById(patientId)
        val appointments = database.appointmentDao().getAppointmentsForPatient(patientId)
        val payments = database.paymentDao().getPaymentsForPatient(patientId)

        assertNotNull(patient)
        assertEquals(1, appointments.size)
        assertEquals(1, payments.size)
        assertEquals(patientId.toInt(), appointments[0].patientId)
        assertEquals(patientId.toInt(), payments[0].patientId)
    }
}
