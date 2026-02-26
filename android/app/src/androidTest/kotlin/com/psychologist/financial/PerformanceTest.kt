package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.data.repositories.PatientRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Performance Integration Tests
 *
 * Verifies that core operations meet the performance targets defined in the
 * project specification (Success Criteria SC-003 and SC-006):
 *
 *   - SC-003: Dashboard loads within 2 seconds on typical Android devices
 *   - SC-006: App operates correctly on typical Android devices (API 30+)
 *
 * Performance targets tested:
 *   - Patient list query (500 patients): < 500 ms
 *   - Dashboard metrics aggregation (1000 payments): < 2000 ms
 *   - Patient insert throughput (100 patients): < 2000 ms
 *   - Single patient retrieval: < 50 ms
 *   - Payment query by patient (50 payments): < 200 ms
 *   - Appointment query by patient (100 appointments): < 200 ms
 *
 * Note: These tests measure database query performance only, without UI rendering.
 * Real app performance includes additional Compose rendering time, which is expected
 * to stay well within the 2-second limit on modern hardware (API 33+) and should be
 * validated separately with UI benchmarks on a real device.
 *
 * All tests use an in-memory Room database. Actual encrypted SQLCipher performance
 * will be slightly slower (typically 3-5x overhead), but within acceptable bounds
 * for the 2-second dashboard target given that queries are straightforward aggregations.
 *
 * Total: 8 performance test cases
 */
@RunWith(AndroidJUnit4::class)
class PerformanceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var patientRepository: PatientRepository
    private lateinit var dashboardRepository: DashboardRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        patientRepository = PatientRepository(
            database.patientDao(),
            mockEncryptionService = null
        )
        dashboardRepository = DashboardRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Patient Query Performance
    // ========================================

    @Test
    fun patientListQuery_500patients_completesUnder500ms() = runBlocking {
        // Arrange — seed 500 patients
        seedPatients(500)

        // Act — measure query time
        val elapsed = measureTimeMillis {
            val patients = patientRepository.getAllIncludingInactive()
            assertTrue(patients.size >= 500)
        }

        assertTrue(
            elapsed < 500,
            "Patient list query took ${elapsed}ms — must complete under 500ms (got ${elapsed}ms)"
        )
    }

    @Test
    fun singlePatientRetrieval_completesUnder50ms() = runBlocking {
        // Arrange — seed some patients, pick the last one
        val ids = seedPatients(100)
        val targetId = ids.last()

        // Act
        val elapsed = measureTimeMillis {
            val patient = patientRepository.getById(targetId)
            assertTrue(patient != null)
        }

        assertTrue(
            elapsed < 50,
            "Single patient retrieval took ${elapsed}ms — must complete under 50ms"
        )
    }

    @Test
    fun patientInsert_100patients_completesUnder2000ms() = runBlocking {
        // Act — measure bulk insert time
        val elapsed = measureTimeMillis {
            repeat(100) { i ->
                patientRepository.insert(
                    name = "Perf Patient $i",
                    phone = null,
                    email = "perf$i@test.com",
                    initialConsultDate = LocalDate.now()
                )
            }
        }

        assertTrue(
            elapsed < 2000,
            "Inserting 100 patients took ${elapsed}ms — must complete under 2000ms"
        )
    }

    // ========================================
    // Dashboard Performance (SC-003: < 2 seconds)
    // ========================================

    @Test
    fun dashboardMetrics_1000payments_completesUnder2000ms() = runBlocking {
        // Arrange — seed a realistic dataset
        val patientIds = seedPatients(50)
        seedPayments(patientIds, paymentsPerPatient = 20) // 50 * 20 = 1000 payments

        // Act — measure dashboard aggregation time (the critical path for SC-003)
        val elapsed = measureTimeMillis {
            val metrics = dashboardRepository.getDashboardMetrics(
                startDate = LocalDate.now().withDayOfMonth(1),
                endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
            )
            assertNotNull(metrics)
        }

        assertTrue(
            elapsed < 2000,
            "Dashboard metrics query (1000 payments) took ${elapsed}ms — must complete under 2000ms (SC-003)"
        )
    }

    @Test
    fun dashboardMetrics_500patients_completesUnder2000ms() = runBlocking {
        // Arrange — large patient count (SC-002: support 500+ patients)
        val patientIds = seedPatients(500)
        // Each patient has at least 2 payments (1000 total)
        seedPayments(patientIds, paymentsPerPatient = 2)

        val elapsed = measureTimeMillis {
            val metrics = dashboardRepository.getDashboardMetrics(
                startDate = LocalDate.now().withDayOfMonth(1),
                endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
            )
            assertNotNull(metrics)
        }

        assertTrue(
            elapsed < 2000,
            "Dashboard with 500 patients took ${elapsed}ms — must complete under 2000ms"
        )
    }

    // ========================================
    // Appointment Query Performance
    // ========================================

    @Test
    fun appointmentQuery_100appointmentsPerPatient_completesUnder200ms() = runBlocking {
        // Arrange — one patient with 100 appointments
        val patientId = patientRepository.insert(
            name = "High Volume Patient",
            phone = "(11) 99999-0001",
            email = "highvol@test.com",
            initialConsultDate = LocalDate.now().minusYears(2)
        )

        val baseDate = LocalDate.now().minusMonths(10)
        repeat(100) { i ->
            database.appointmentDao().insertAppointment(
                AppointmentEntity(
                    id = 0,
                    patientId = patientId.toInt(),
                    date = baseDate.plusDays(i.toLong() * 3),
                    time = LocalTime.of(10, 0),
                    durationMinutes = 50,
                    notes = null
                )
            )
        }

        // Act
        val elapsed = measureTimeMillis {
            val appointments = database.appointmentDao().getAppointmentsForPatient(patientId)
            assertTrue(appointments.size >= 100)
        }

        assertTrue(
            elapsed < 200,
            "Appointment query (100 records) took ${elapsed}ms — must complete under 200ms"
        )
    }

    // ========================================
    // Payment Query Performance
    // ========================================

    @Test
    fun paymentQuery_50paymentsPerPatient_completesUnder200ms() = runBlocking {
        // Arrange
        val patientId = patientRepository.insert(
            name = "Payment Volume Test",
            phone = "(11) 99999-0002",
            email = "payvol@test.com",
            initialConsultDate = LocalDate.now().minusYears(1)
        )

        repeat(50) { i ->
            database.paymentDao().insertPayment(
                PaymentEntity(
                    id = 0,
                    patientId = patientId.toInt(),
                    appointmentId = null,
                    amount = BigDecimal("150.00"),
                    method = "PIX",
                    status = if (i % 3 == 0) "PENDING" else "PAID",
                    paymentDate = LocalDate.now().minusDays(i.toLong())
                )
            )
        }

        // Act
        val elapsed = measureTimeMillis {
            val payments = database.paymentDao().getPaymentsForPatient(patientId)
            assertTrue(payments.size >= 50)
        }

        assertTrue(
            elapsed < 200,
            "Payment query (50 records) took ${elapsed}ms — must complete under 200ms"
        )
    }

    // ========================================
    // Stress Test: Maximum Supported Scale
    // ========================================

    @Test
    fun stressTest_5000appointments_queryCompletesUnder2000ms() = runBlocking {
        // Matches SC-002: system must support 5000+ appointments
        val patientIds = seedPatients(10)

        // 10 patients × 500 appointments = 5000 total
        val baseDate = LocalDate.now().minusYears(3)
        patientIds.forEachIndexed { pi, patientId ->
            repeat(500) { i ->
                database.appointmentDao().insertAppointment(
                    AppointmentEntity(
                        id = 0,
                        patientId = patientId.toInt(),
                        date = baseDate.plusDays((pi * 500 + i).toLong()),
                        time = LocalTime.of(9 + (i % 8), 0),
                        durationMinutes = 50,
                        notes = null
                    )
                )
            }
        }

        // Query all appointments for one patient (representative of per-patient list screen)
        val elapsed = measureTimeMillis {
            val appointments = database.appointmentDao()
                .getAppointmentsForPatient(patientIds.first())
            assertTrue(appointments.size >= 500)
        }

        assertTrue(
            elapsed < 2000,
            "Appointment query with 5000 total records took ${elapsed}ms — must complete under 2000ms"
        )
    }

    // ========================================
    // Helpers
    // ========================================

    private suspend fun seedPatients(count: Int): List<Long> {
        return (0 until count).map { i ->
            patientRepository.insert(
                name = "Seed Patient $i",
                phone = null,
                email = "seed$i@test.com",
                initialConsultDate = LocalDate.now().minusDays(i.toLong() % 365)
            )
        }
    }

    private fun seedPayments(patientIds: List<Long>, paymentsPerPatient: Int) {
        val baseDate = LocalDate.now().withDayOfMonth(1)
        patientIds.forEach { patientId ->
            repeat(paymentsPerPatient) { i ->
                database.paymentDao().insertPayment(
                    PaymentEntity(
                        id = 0,
                        patientId = patientId.toInt(),
                        appointmentId = null,
                        amount = BigDecimal("150.00"),
                        method = "PIX",
                        status = if (i % 4 == 0) "PENDING" else "PAID",
                        paymentDate = baseDate.plusDays(i.toLong() % 28)
                    )
                )
            }
        }
    }
}
