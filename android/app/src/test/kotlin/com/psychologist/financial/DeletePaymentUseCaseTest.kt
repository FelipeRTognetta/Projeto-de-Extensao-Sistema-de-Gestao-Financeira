package com.psychologist.financial

import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.usecases.DeletePaymentUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

/**
 * Unit tests for DeletePaymentUseCase.
 *
 * Coverage:
 * - Success: cross-refs unlinked BEFORE payment deleted (order enforced)
 * - unlinkAllAppointments failure: exception propagates; deleteById NOT called
 * - deleteById failure: exception propagates
 */
@RunWith(MockitoJUnitRunner::class)
class DeletePaymentUseCaseTest {

    @Mock
    private lateinit var mockRepository: PaymentRepository

    private lateinit var useCase: DeletePaymentUseCase

    @Before
    fun setUp() {
        useCase = DeletePaymentUseCase(mockRepository)
    }

    @Test
    fun `execute calls unlinkAllAppointments before deleteById`() = runTest {
        val paymentId = 7L

        useCase.execute(paymentId)

        val inOrder = inOrder(mockRepository)
        inOrder.verify(mockRepository).unlinkAllAppointments(paymentId)
        inOrder.verify(mockRepository).deleteById(paymentId)
    }

    @Test
    fun `execute propagates exception from unlinkAllAppointments`() = runTest {
        val paymentId = 8L
        whenever(mockRepository.unlinkAllAppointments(paymentId))
            .thenThrow(RuntimeException("FK constraint"))

        assertFailsWith<RuntimeException> {
            useCase.execute(paymentId)
        }

        // deleteById must NOT have been called when unlinkAllAppointments throws
        verify(mockRepository).unlinkAllAppointments(paymentId)
        org.mockito.Mockito.verifyNoMoreInteractions(mockRepository)
    }

    @Test
    fun `execute propagates exception from deleteById`() = runTest {
        val paymentId = 9L
        whenever(mockRepository.deleteById(paymentId))
            .thenThrow(RuntimeException("delete failed"))

        assertFailsWith<RuntimeException> {
            useCase.execute(paymentId)
        }
    }
}
