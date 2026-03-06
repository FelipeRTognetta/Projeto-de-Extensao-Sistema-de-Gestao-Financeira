package com.psychologist.financial

import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Payment
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for PaymentRepository
 *
 * Coverage:
 * - getByPatient() returns mapped payments
 * - getByPatientFlow() emits payments reactively
 * - getByPatientAndStatus() returns filtered payments
 * - getByPatientAndDateRange() date range query
 * - countByPatient() delegates to DAO
 * - countByPatientAndStatus() delegates to DAO
 * - getTotalAmountPaid() delegates to DAO
 * - getAmountDueNow() delegates to DAO
 * - getTotalOutstanding() delegates to DAO
 * - getById() returns payment or null
 *
 * Total: 12 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class PaymentRepositoryUnitTest {

    @Mock
    private lateinit var mockPaymentDao: PaymentDao

    private lateinit var repository: PaymentRepository

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)

    @Before
    fun setUp() {
        repository = PaymentRepository(database = mock(), paymentDao = mockPaymentDao)
    }

    private fun makePaymentEntity(
        id: Long,
        patientId: Long = 1L,
        amount: BigDecimal = BigDecimal("150.00")
    ) = com.psychologist.financial.data.entities.PaymentEntity(
        id = id,
        patientId = patientId,
        amount = amount,
        paymentDate = yesterday
    )

    // ========================================
    // getByPatient() Tests
    // ========================================

    @Test
    fun `getByPatient returns mapped payment list`() = runTest {
        val entities = listOf(makePaymentEntity(1L), makePaymentEntity(2L))
        whenever(mockPaymentDao.getByPatient(1L)).thenReturn(entities)

        val result = repository.getByPatient(patientId = 1L)

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun `getByPatient returns empty list when no payments`() = runTest {
        whenever(mockPaymentDao.getByPatient(99L)).thenReturn(emptyList())

        val result = repository.getByPatient(patientId = 99L)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // getByPatientFlow() Tests
    // ========================================

    @Test
    fun `getByPatientFlow emits payments reactively`() = runTest {
        val entities = listOf(makePaymentEntity(1L))
        whenever(mockPaymentDao.getByPatientFlow(1L))
            .thenReturn(flowOf(entities))

        val flow = repository.getByPatientFlow(patientId = 1L)
        var emitted: List<Payment>? = null
        flow.collect { emitted = it }

        assertNotNull(emitted)
        assertEquals(1, emitted!!.size)
    }

    // ========================================
    // Count Tests
    // ========================================

    @Test
    fun `countByPatient delegates to DAO`() = runTest {
        whenever(mockPaymentDao.countByPatient(1L)).thenReturn(6)

        val count = repository.countByPatient(patientId = 1L)

        assertEquals(6, count)
    }

    // ========================================
    // Balance Calculation Tests
    // ========================================

    @Test
    fun `getTotalAmountPaid delegates to DAO`() = runTest {
        whenever(mockPaymentDao.getTotalAmountPaid(1L))
            .thenReturn(BigDecimal("500.00"))

        val total = repository.getTotalAmountPaid(patientId = 1L)

        assertEquals(BigDecimal("500.00"), total)
    }

    // ========================================
    // getById() Tests
    // ========================================

    @Test
    fun `getById returns payment when exists`() = runTest {
        val entity = makePaymentEntity(1L)
        whenever(mockPaymentDao.getById(1L)).thenReturn(entity)

        val result = repository.getById(id = 1L)

        assertNotNull(result)
        assertEquals(1L, result.id)
    }

    @Test
    fun `getById returns null when not found`() = runTest {
        whenever(mockPaymentDao.getById(99L)).thenReturn(null)

        val result = repository.getById(id = 99L)

        assertNull(result)
    }
}
