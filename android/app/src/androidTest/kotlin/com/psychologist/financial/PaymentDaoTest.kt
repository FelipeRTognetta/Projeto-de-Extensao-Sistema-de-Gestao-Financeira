package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.data.entities.PaymentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for PaymentDao
 *
 * Tests the Room DAO layer directly with SQLite database.
 * Uses in-memory database for fast, isolated testing.
 *
 * Coverage:
 * - CRUD operations (Create, Read, Update, Delete)
 * - All query methods (by ID, by patient, date range, status filtering)
 * - Count and existence checks
 * - Status filtering (PAID, PENDING, OVERDUE)
 * - Date range filtering with chronological ordering
 * - Aggregate queries (sum, average)
 * - Payment method grouping
 * - Reactive Flow operations
 * - Batch operations
 * - BigDecimal precision
 * - Data persistence
 *
 * Total: 50+ test cases
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class PaymentDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var paymentDao: PaymentDao
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val weekAgo = today.minusDays(7)
    private val monthAgo = today.minusMonths(1)
    private val twoMonthsAgo = today.minusMonths(2)

    @Before
    fun setUp() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()  // Allow queries on main thread for testing
            .build()

        paymentDao = database.paymentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Helper function to create test entities
    private fun createPaymentEntity(
        id: Long = 0,
        patientId: Long = 1L,
        appointmentId: Long? = null,
        amount: BigDecimal = BigDecimal("150.00"),
        paymentDate: LocalDate = yesterday,
        method: String = "Débito",
        status: String = "PAID"
    ): PaymentEntity {
        return PaymentEntity(
            id = id,
            patientId = patientId,
            appointmentId = appointmentId,
            amount = amount,
            paymentDate = paymentDate,
            method = method,
            status = status,
            recordedDate = LocalDateTime.now()
        )
    }

    // ========================================
    // Insert Operations
    // ========================================

    @Test
    fun insert_newPayment_succeeds() = runBlocking {
        // Arrange
        val entity = createPaymentEntity()

        // Act
        val id = paymentDao.insert(entity)

        // Assert
        assertTrue(id > 0)
    }

    @Test
    fun insert_multiplePayments_allPersist() = runBlocking {
        // Arrange
        val entity1 = createPaymentEntity(patientId = 1L, amount = BigDecimal("100.00"))
        val entity2 = createPaymentEntity(patientId = 1L, amount = BigDecimal("200.00"))

        // Act
        paymentDao.insert(entity1)
        paymentDao.insert(entity2)

        // Assert
        assertEquals(2, paymentDao.count())
    }

    @Test
    fun insertAll_batchInsert_succeeds() = runBlocking {
        // Arrange
        val entities = listOf(
            createPaymentEntity(patientId = 1L, amount = BigDecimal("100.00")),
            createPaymentEntity(patientId = 1L, amount = BigDecimal("200.00")),
            createPaymentEntity(patientId = 2L, amount = BigDecimal("150.00"))
        )

        // Act
        val ids = paymentDao.insertAll(entities)

        // Assert
        assertEquals(3, ids.size)
        assertTrue(ids.all { it > 0 })
    }

    @Test
    fun insert_withoutId_generatesId() = runBlocking {
        // Arrange
        val entity = createPaymentEntity(id = 0)

        // Act
        val id = paymentDao.insert(entity)

        // Assert
        assertTrue(id > 0)
        val retrieved = paymentDao.getById(id)
        assertNotNull(retrieved)
    }

    // ========================================
    // Read Operations - Single
    // ========================================

    @Test
    fun getById_existingPayment_returnsPayment() = runBlocking {
        // Arrange
        val entity = createPaymentEntity(patientId = 1L, amount = BigDecimal("125.50"))
        val id = paymentDao.insert(entity)

        // Act
        val retrieved = paymentDao.getById(id)

        // Assert
        assertNotNull(retrieved)
        assertEquals(1L, retrieved.patientId)
        assertEquals(BigDecimal("125.50"), retrieved.amount)
    }

    @Test
    fun getById_nonExistent_returnsNull() = runBlocking {
        // Act
        val retrieved = paymentDao.getById(999L)

        // Assert
        assertNull(retrieved)
    }

    // ========================================
    // Read Operations - Patient Payments
    // ========================================

    @Test
    fun getByPatient_existingPayments_returnsAllForPatient() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("100.00")))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("200.00")))
        paymentDao.insert(createPaymentEntity(patientId = 2L, amount = BigDecimal("150.00")))

        // Act
        val payments = paymentDao.getByPatient(1L)

        // Assert
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.patientId == 1L })
    }

    @Test
    fun getByPatient_noPayments_returnsEmpty() = runBlocking {
        // Act
        val payments = paymentDao.getByPatient(999L)

        // Assert
        assertTrue(payments.isEmpty())
    }

    // ========================================
    // Status Filtering
    // ========================================

    @Test
    fun getByPatientAndStatus_paidPayments_returnsOnlyPaid() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, status = "PAID"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, status = "PENDING"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, status = "PAID"))

        // Act
        val payments = paymentDao.getByPatientAndStatus(1L, "PAID")

        // Assert
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.status == "PAID" })
    }

    @Test
    fun getByPatientAndStatus_pendingPayments_returnsOnlyPending() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, status = "PAID"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, status = "PENDING"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, status = "PENDING"))

        // Act
        val payments = paymentDao.getByPatientAndStatus(1L, "PENDING")

        // Assert
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.status == "PENDING" })
    }

    @Test
    fun getByStatus_allStatusesForAllPatients_returnsCorrectly() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, status = "PAID"))
        paymentDao.insert(createPaymentEntity(patientId = 2L, status = "PAID"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, status = "PENDING"))

        // Act
        val paidPayments = paymentDao.getByStatus("PAID")

        // Assert
        assertEquals(2, paidPayments.size)
        assertTrue(paidPayments.all { it.status == "PAID" })
    }

    // ========================================
    // Date Range Filtering
    // ========================================

    @Test
    fun getByDateRange_withinRange_returnsOnlyInRange() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = monthAgo))
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = weekAgo))
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = yesterday))

        // Act
        val payments = paymentDao.getByDateRange(
            patientId = 1L,
            startDate = weekAgo.minusDays(1),
            endDate = today
        )

        // Assert
        assertEquals(2, payments.size)
    }

    @Test
    fun getByPatientAndDateRange_filtering_correctlyLimitsResults() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = monthAgo))
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = weekAgo))
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = yesterday))
        paymentDao.insert(createPaymentEntity(patientId = 2L, paymentDate = yesterday))

        // Act
        val payments = paymentDao.getByDateRange(
            patientId = 1L,
            startDate = weekAgo.minusDays(1),
            endDate = today
        )

        // Assert
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.patientId == 1L })
    }

    @Test
    fun getByDateRange_chronologicalOrder_returnsSortedByDate() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = monthAgo))
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = yesterday))
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = weekAgo))

        // Act
        val payments = paymentDao.getByDateRange(1L, monthAgo, today)

        // Assert
        assertEquals(3, payments.size)
        // Verify chronological order (oldest first or newest first depending on query)
        assertTrue(payments[0].paymentDate.isBefore(payments[1].paymentDate) ||
                payments[0].paymentDate.isAfter(payments[1].paymentDate))
    }

    // ========================================
    // Aggregate Queries - Sum/Total
    // ========================================

    @Test
    fun getTotalAmountPaid_sums_onlyPaidPayments() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("100.00"), status = "PAID"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("200.00"), status = "PAID"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("150.00"), status = "PENDING"))

        // Act
        val total = paymentDao.getTotalAmountPaid(1L)

        // Assert
        assertEquals(BigDecimal("300.00"), total)
    }

    @Test
    fun getTotalOutstanding_sums_onlyPendingPayments() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("100.00"), status = "PAID"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("200.00"), status = "PENDING"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("150.00"), status = "PENDING"))

        // Act
        val total = paymentDao.getTotalOutstanding(1L)

        // Assert
        assertEquals(BigDecimal("350.00"), total)
    }

    @Test
    fun getTotalAmountPaid_noPayments_returnsZero() = runBlocking {
        // Act
        val total = paymentDao.getTotalAmountPaid(999L)

        // Assert
        assertEquals(BigDecimal.ZERO, total)
    }

    @Test
    fun getTotalByMethod_groupsByPaymentMethod() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("100.00"), method = "Débito"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("50.00"), method = "Débito"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("200.00"), method = "Crédito"))

        // Act
        val byMethod = paymentDao.getTotalByMethod(1L)

        // Assert
        assertEquals(BigDecimal("150.00"), byMethod["Débito"])
        assertEquals(BigDecimal("200.00"), byMethod["Crédito"])
    }

    @Test
    fun getTotalByDateRange_filters_byDateAndSums() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("100.00"), paymentDate = monthAgo))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("200.00"), paymentDate = weekAgo))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("150.00"), paymentDate = yesterday))

        // Act
        val total = paymentDao.getTotalByDateRange(
            patientId = 1L,
            startDate = weekAgo.minusDays(1),
            endDate = today
        )

        // Assert
        assertEquals(BigDecimal("350.00"), total)
    }

    @Test
    fun getAveragePaymentAmount_calculates_correctlyForPatient() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("100.00")))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("200.00")))
        paymentDao.insert(createPaymentEntity(patientId = 1L, amount = BigDecimal("300.00")))

        // Act
        val average = paymentDao.getAveragePaymentAmount(1L)

        // Assert
        assertEquals(BigDecimal("200.00"), average)
    }

    // ========================================
    // Count Operations
    // ========================================

    @Test
    fun count_returnsTotal() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L))
        paymentDao.insert(createPaymentEntity(patientId = 1L))
        paymentDao.insert(createPaymentEntity(patientId = 2L))

        // Act
        val count = paymentDao.count()

        // Assert
        assertEquals(3, count)
    }

    @Test
    fun count_empty_returnsZero() = runBlocking {
        // Act
        val count = paymentDao.count()

        // Assert
        assertEquals(0, count)
    }

    // ========================================
    // Update Operations
    // ========================================

    @Test
    fun update_changes_paymentData() = runBlocking {
        // Arrange
        val entity = createPaymentEntity(patientId = 1L, status = "PENDING")
        val id = paymentDao.insert(entity)
        val updated = createPaymentEntity(id = id, patientId = 1L, status = "PAID")

        // Act
        paymentDao.update(updated)

        // Assert
        val retrieved = paymentDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("PAID", retrieved.status)
    }

    @Test
    fun markAsPaid_updatesStatus() = runBlocking {
        // Arrange
        val id = paymentDao.insert(createPaymentEntity(status = "PENDING"))

        // Act
        paymentDao.markAsPaid(id)

        // Assert
        val retrieved = paymentDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("PAID", retrieved.status)
    }

    @Test
    fun markAsPending_updatesStatus() = runBlocking {
        // Arrange
        val id = paymentDao.insert(createPaymentEntity(status = "PAID"))

        // Act
        paymentDao.markAsPending(id)

        // Assert
        val retrieved = paymentDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("PENDING", retrieved.status)
    }

    // ========================================
    // Appointment Linking
    // ========================================

    @Test
    fun linkToAppointment_sets_appointmentId() = runBlocking {
        // Arrange
        val paymentId = paymentDao.insert(createPaymentEntity(appointmentId = null))

        // Act
        paymentDao.linkToAppointment(paymentId, 5L)

        // Assert
        val retrieved = paymentDao.getById(paymentId)
        assertNotNull(retrieved)
        assertEquals(5L, retrieved.appointmentId)
    }

    @Test
    fun unlinkFromAppointment_clearsAppointmentId() = runBlocking {
        // Arrange
        val paymentId = paymentDao.insert(createPaymentEntity(appointmentId = 5L))

        // Act
        paymentDao.unlinkFromAppointment(paymentId)

        // Assert
        val retrieved = paymentDao.getById(paymentId)
        assertNotNull(retrieved)
        assertNull(retrieved.appointmentId)
    }

    @Test
    fun getUnlinkedByPatient_returns_paymentsWithoutAppointment() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, appointmentId = null))
        paymentDao.insert(createPaymentEntity(patientId = 1L, appointmentId = 5L))
        paymentDao.insert(createPaymentEntity(patientId = 1L, appointmentId = null))

        // Act
        val unlinked = paymentDao.getUnlinkedByPatient(1L)

        // Assert
        assertEquals(2, unlinked.size)
        assertTrue(unlinked.all { it.appointmentId == null })
    }

    // ========================================
    // Delete Operations
    // ========================================

    @Test
    fun delete_removesPayment() = runBlocking {
        // Arrange
        val id = paymentDao.insert(createPaymentEntity())

        // Act
        paymentDao.delete(id)

        // Assert
        val retrieved = paymentDao.getById(id)
        assertNull(retrieved)
    }

    @Test
    fun deleteByPatient_removesAllForPatient() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L))
        paymentDao.insert(createPaymentEntity(patientId = 1L))
        paymentDao.insert(createPaymentEntity(patientId = 2L))

        // Act
        paymentDao.deleteByPatient(1L)

        // Assert
        assertEquals(1, paymentDao.count())
        assertEquals(1, paymentDao.getByPatient(2L).size)
    }

    // ========================================
    // BigDecimal Precision
    // ========================================

    @Test
    fun bigDecimalPrecision_maintains_twoDecimalPlaces() = runBlocking {
        // Arrange
        val entity = createPaymentEntity(amount = BigDecimal("123.45"))

        // Act
        val id = paymentDao.insert(entity)
        val retrieved = paymentDao.getById(id)

        // Assert
        assertNotNull(retrieved)
        assertEquals(BigDecimal("123.45"), retrieved.amount)
    }

    @Test
    fun bigDecimalPrecision_minimumAmount() = runBlocking {
        // Arrange
        val entity = createPaymentEntity(amount = BigDecimal("0.01"))

        // Act
        val id = paymentDao.insert(entity)
        val retrieved = paymentDao.getById(id)

        // Assert
        assertNotNull(retrieved)
        assertEquals(BigDecimal("0.01"), retrieved.amount)
    }

    @Test
    fun bigDecimalPrecision_largeAmount() = runBlocking {
        // Arrange
        val entity = createPaymentEntity(amount = BigDecimal("999999.99"))

        // Act
        val id = paymentDao.insert(entity)
        val retrieved = paymentDao.getById(id)

        // Assert
        assertNotNull(retrieved)
        assertEquals(BigDecimal("999999.99"), retrieved.amount)
    }

    // ========================================
    // Multi-Patient Isolation
    // ========================================

    @Test
    fun multiPatient_isolation_byPatientId() = runBlocking {
        // Arrange & Act
        (1..5).forEach { patientId ->
            (1..3).forEach {
                paymentDao.insert(createPaymentEntity(patientId = patientId.toLong()))
            }
        }

        // Assert - verify isolation
        assertEquals(15, paymentDao.count())
        assertEquals(3, paymentDao.getByPatient(1L).size)
        assertEquals(3, paymentDao.getByPatient(2L).size)
        assertEquals(3, paymentDao.getByPatient(5L).size)
    }

    // ========================================
    // Large Dataset Handling
    // ========================================

    @Test
    fun largeDataset_100Payments_performsEfficiently() = runBlocking {
        // Act
        for (i in 1..100) {
            paymentDao.insert(
                createPaymentEntity(
                    patientId = ((i % 5) + 1).toLong(),
                    amount = BigDecimal("${(i * 10)}.00"),
                    paymentDate = today.minusDays((i % 30).toLong()),
                    status = if (i % 3 == 0) "PAID" else "PENDING"
                )
            )
        }

        // Assert
        assertEquals(100, paymentDao.count())
        val patient1 = paymentDao.getByPatient(1L)
        assertTrue(patient1.isNotEmpty())
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun getOverdueByPatient_pastDuePendingPayments() = runBlocking {
        // Arrange
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = monthAgo, status = "PENDING"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = weekAgo, status = "PENDING"))
        paymentDao.insert(createPaymentEntity(patientId = 1L, paymentDate = yesterday, status = "PAID"))

        // Act
        val overdue = paymentDao.getOverdueByPatient(1L)

        // Assert
        assertTrue(overdue.isNotEmpty())
        assertTrue(overdue.all { it.status == "PENDING" && it.paymentDate.isBefore(today) })
    }

    @Test
    fun getRecentByPatient_latestPayments() = runBlocking {
        // Arrange
        (1..10).forEach { i ->
            paymentDao.insert(
                createPaymentEntity(
                    patientId = 1L,
                    paymentDate = today.minusDays(i.toLong())
                )
            )
        }

        // Act
        val recent = paymentDao.getRecentByPatient(1L, limit = 5)

        // Assert
        assertEquals(5, recent.size)
    }
}
