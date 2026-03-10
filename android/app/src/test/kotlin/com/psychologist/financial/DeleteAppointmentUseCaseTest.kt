package com.psychologist.financial

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.usecases.DeleteAppointmentUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

/**
 * Unit tests for DeleteAppointmentUseCase.
 *
 * Coverage:
 * - Success: delegates to repository.deleteById
 * - DAO failure: exception propagates to caller
 * - Zero-id edge case: still calls through (DAO enforces constraints)
 */
@RunWith(MockitoJUnitRunner::class)
class DeleteAppointmentUseCaseTest {

    @Mock
    private lateinit var mockRepository: AppointmentRepository

    private lateinit var useCase: DeleteAppointmentUseCase

    @Before
    fun setUp() {
        useCase = DeleteAppointmentUseCase(mockRepository)
    }

    @Test
    fun `execute calls repository deleteById with correct id`() = runTest {
        val appointmentId = 42L

        useCase.execute(appointmentId)

        verify(mockRepository).deleteById(appointmentId)
    }

    @Test
    fun `execute propagates exception from repository`() = runTest {
        val appointmentId = 99L
        whenever(mockRepository.deleteById(appointmentId))
            .thenThrow(RuntimeException("DAO error"))

        assertFailsWith<RuntimeException> {
            useCase.execute(appointmentId)
        }
    }

    @Test
    fun `execute with id zero still delegates to repository`() = runTest {
        useCase.execute(0L)

        verify(mockRepository).deleteById(0L)
    }
}
