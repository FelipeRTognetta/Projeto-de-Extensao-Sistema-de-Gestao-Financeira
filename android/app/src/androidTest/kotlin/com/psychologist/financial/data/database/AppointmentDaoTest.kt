package com.psychologist.financial.data.database

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.data.entities.PaymentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Instrumented tests for AppointmentDao.
 *
 * Tests payment status queries (hasPendingPayment derived from junction table).
 *
 * Test environment: In-memory Room database with no encryption
 * Run with: ./gradlew connectedDebugAndroidTest --tests AppointmentDaoTest
 */
@RunWith(AndroidJUnit4::class)
class AppointmentDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var appointmentDao: AppointmentDao
    private lateinit var patientDao: PatientDao
    private lateinit var paymentDao: PaymentDao

    private val testPatient = PatientEntity(
        id = 1L,
        name = "Test Patient",
        phone = "11999999999",
        email = "test@example.com",
        status = "ACTIVE",
        initialConsultDate = LocalDate.of(2024, 1, 1),
        registrationDate = LocalDate.of(2024, 1, 1)
    )

    private val testAppointment1 = AppointmentEntity(
        id = 10L,
        patientId = 1L,
        date = LocalDate.of(2024, 2, 10),
        timeStart = LocalTime.of(14, 0),
        durationMinutes = 60
    )

    private val testAppointment2 = AppointmentEntity(
        id = 11L,
        patientId = 1L,
        date = LocalDate.of(2024, 2, 15),
        timeStart = LocalTime.of(15, 0),
        durationMinutes = 60
    )

    private val testAppointment3 = AppointmentEntity(
        id = 12L,
        patientId = 1L,
        date = LocalDate.of(2024, 2, 20),
        timeStart = LocalTime.of(16, 0),
        durationMinutes = 60
    )

    private val testPayment = PaymentEntity(
        id = 1L,
        patientId = 1L,
        amount = BigDecimal("300.00"),
        paymentDate = LocalDate.of(2024, 2, 25)
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        appointmentDao = database.appointmentDao()
        patientDao = database.patientDao()
        paymentDao = database.paymentDao()

        // Insert test data
        runBlocking {
            patientDao.insert(testPatient)
            appointmentDao.insert(testAppointment1)
            appointmentDao.insert(testAppointment2)
            appointmentDao.insert(testAppointment3)
            paymentDao.insert(testPayment)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Payment Status Query Tests
    // ========================================

    /**
     * Test that getAllWithPaymentStatus returns all appointments with correct payment status
     */
    @Test
    fun getAllWithPaymentStatus_returnsAllAppointmentsWithStatus() = runBlocking {
        // Initially no appointments linked to payment
        val result = appointmentDao.getAllWithPaymentStatus().first()

        assert(result.size == 3)
        // All should have pending payment initially
        assert(result[0].hasPendingPayment)
        assert(result[1].hasPendingPayment)
        assert(result[2].hasPendingPayment)
    }

    /**
     * Test that appointment with payment link has hasPendingPayment = false
     */
    @Test
    fun getAllWithPaymentStatus_linkedAppointmentHasNoPendingPayment() = runBlocking {
        // Link appointment 10 to payment
        paymentDao.insertAppointmentLink(PaymentAppointmentCrossRef(1L, 10L))

        val result = appointmentDao.getAllWithPaymentStatus().first()

        // Find appointment 10
        val linkedAppt = result.find { it.appointment.id == 10L }
        assert(linkedAppt != null)
        assert(!linkedAppt!!.hasPendingPayment) // Should be false (has payment)
    }

    /**
     * Test that unlinked appointment has hasPendingPayment = true
     */
    @Test
    fun getAllWithPaymentStatus_unlinkedAppointmentHasPendingPayment() = runBlocking {
        // Link only appointment 10
        paymentDao.insertAppointmentLink(PaymentAppointmentCrossRef(1L, 10L))

        val result = appointmentDao.getAllWithPaymentStatus().first()

        // Appointment 11 should still have pending payment
        val unlinkedAppt = result.find { it.appointment.id == 11L }
        assert(unlinkedAppt != null)
        assert(unlinkedAppt!!.hasPendingPayment) // Should be true (no payment)
    }

    /**
     * Test multiple appointments linked to same payment
     */
    @Test
    fun getAllWithPaymentStatus_multipleLinkedAppointments() = runBlocking {
        // Link appointments 10 and 11 to same payment
        paymentDao.insertAppointmentLink(PaymentAppointmentCrossRef(1L, 10L))
        paymentDao.insertAppointmentLink(PaymentAppointmentCrossRef(1L, 11L))

        val result = appointmentDao.getAllWithPaymentStatus().first()

        val appt10 = result.find { it.appointment.id == 10L }
        val appt11 = result.find { it.appointment.id == 11L }
        val appt12 = result.find { it.appointment.id == 12L }

        // 10 and 11 have payments, 12 does not
        assert(!appt10!!.hasPendingPayment)
        assert(!appt11!!.hasPendingPayment)
        assert(appt12!!.hasPendingPayment)
    }

    /**
     * Test unlinking removes payment status
     */
    @Test
    fun getAllWithPaymentStatus_afterUnlinking_appointmentHasPendingPayment() = runBlocking {
        // Link appointment 10
        paymentDao.insertAppointmentLink(PaymentAppointmentCrossRef(1L, 10L))

        var result = appointmentDao.getAllWithPaymentStatus().first()
        var appt10 = result.find { it.appointment.id == 10L }
        assert(!appt10!!.hasPendingPayment) // Has payment

        // Delete the link
        paymentDao.deleteAppointmentLinksByPayment(1L)

        result = appointmentDao.getAllWithPaymentStatus().first()
        appt10 = result.find { it.appointment.id == 10L }
        assert(appt10!!.hasPendingPayment) // Back to pending
    }
}
