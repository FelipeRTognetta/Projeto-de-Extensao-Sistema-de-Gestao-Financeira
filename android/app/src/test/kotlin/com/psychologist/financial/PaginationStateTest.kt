package com.psychologist.financial

import com.psychologist.financial.domain.models.PageLoadStatus
import com.psychologist.financial.domain.models.PaginationState
import com.psychologist.financial.utils.Constants
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for PaginationState<T> and PageLoadStatus.
 *
 * Validates:
 * - Initial state defaults
 * - Derived properties: isLoading, isError, allLoaded
 * - hasMore detection from page size
 * - State immutability via data class copy
 * - PageLoadStatus equality
 */
class PaginationStateTest {

    // ========================================
    // Initial State Defaults
    // ========================================

    @Test
    fun `initial state has empty items list`() {
        val state = PaginationState<String>()
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `initial state has currentPage 0`() {
        val state = PaginationState<String>()
        assertEquals(0, state.currentPage)
    }

    @Test
    fun `initial state has Idle status`() {
        val state = PaginationState<String>()
        assertEquals(PageLoadStatus.Idle, state.status)
    }

    @Test
    fun `initial state has hasMore true`() {
        val state = PaginationState<String>()
        assertTrue(state.hasMore)
    }

    // ========================================
    // Derived Property: isLoading
    // ========================================

    @Test
    fun `isLoading is false when status is Idle`() {
        val state = PaginationState<String>(status = PageLoadStatus.Idle)
        assertFalse(state.isLoading)
    }

    @Test
    fun `isLoading is true when status is Loading`() {
        val state = PaginationState<String>(status = PageLoadStatus.Loading)
        assertTrue(state.isLoading)
    }

    @Test
    fun `isLoading is false when status is Error`() {
        val state = PaginationState<String>(status = PageLoadStatus.Error("some error"))
        assertFalse(state.isLoading)
    }

    // ========================================
    // Derived Property: isError
    // ========================================

    @Test
    fun `isError is false when status is Idle`() {
        val state = PaginationState<String>(status = PageLoadStatus.Idle)
        assertFalse(state.isError)
    }

    @Test
    fun `isError is false when status is Loading`() {
        val state = PaginationState<String>(status = PageLoadStatus.Loading)
        assertFalse(state.isError)
    }

    @Test
    fun `isError is true when status is Error`() {
        val state = PaginationState<String>(status = PageLoadStatus.Error("db failure"))
        assertTrue(state.isError)
    }

    // ========================================
    // Derived Property: allLoaded
    // ========================================

    @Test
    fun `allLoaded is false when hasMore is true`() {
        val state = PaginationState<String>(hasMore = true)
        assertFalse(state.allLoaded)
    }

    @Test
    fun `allLoaded is true when hasMore is false`() {
        val state = PaginationState<String>(hasMore = false)
        assertTrue(state.allLoaded)
    }

    // ========================================
    // hasMore from Page Size
    // ========================================

    @Test
    fun `full page of PAGE_SIZE items means hasMore should be true`() {
        val newItems = List(Constants.PAGE_SIZE) { "item$it" }
        val hasMore = newItems.size == Constants.PAGE_SIZE
        assertTrue(hasMore)
    }

    @Test
    fun `partial page smaller than PAGE_SIZE means hasMore should be false`() {
        val newItems = List(Constants.PAGE_SIZE - 1) { "item$it" }
        val hasMore = newItems.size == Constants.PAGE_SIZE
        assertFalse(hasMore)
    }

    @Test
    fun `empty page means hasMore should be false`() {
        val newItems = emptyList<String>()
        val hasMore = newItems.size == Constants.PAGE_SIZE
        assertFalse(hasMore)
    }

    // ========================================
    // State Immutability via copy
    // ========================================

    @Test
    fun `copy preserves original state unchanged`() {
        val original = PaginationState<String>(
            items = listOf("a", "b"),
            currentPage = 1,
            status = PageLoadStatus.Idle,
            hasMore = true
        )
        val updated = original.copy(
            items = original.items + listOf("c"),
            currentPage = 2,
            status = PageLoadStatus.Loading
        )

        // Original unchanged
        assertEquals(2, original.items.size)
        assertEquals(1, original.currentPage)
        assertEquals(PageLoadStatus.Idle, original.status)

        // Updated has new values
        assertEquals(3, updated.items.size)
        assertEquals(2, updated.currentPage)
        assertEquals(PageLoadStatus.Loading, updated.status)
    }

    @Test
    fun `appending items creates new list without mutating original`() {
        val page0 = listOf("a", "b", "c")
        val page1 = listOf("d", "e")

        val state0 = PaginationState<String>(items = page0, currentPage = 0)
        val state1 = state0.copy(items = state0.items + page1, currentPage = 1)

        assertEquals(3, state0.items.size)
        assertEquals(5, state1.items.size)
        assertNotEquals(state0, state1)
    }

    // ========================================
    // PageLoadStatus Equality
    // ========================================

    @Test
    fun `Idle equals Idle`() {
        assertEquals(PageLoadStatus.Idle, PageLoadStatus.Idle)
    }

    @Test
    fun `Loading equals Loading`() {
        assertEquals(PageLoadStatus.Loading, PageLoadStatus.Loading)
    }

    @Test
    fun `Error with same message equals Error with same message`() {
        assertEquals(
            PageLoadStatus.Error("db error"),
            PageLoadStatus.Error("db error")
        )
    }

    @Test
    fun `Error with different messages are not equal`() {
        assertNotEquals(
            PageLoadStatus.Error("error A"),
            PageLoadStatus.Error("error B")
        )
    }

    @Test
    fun `Idle does not equal Loading`() {
        assertNotEquals<PageLoadStatus>(PageLoadStatus.Idle, PageLoadStatus.Loading)
    }

    @Test
    fun `Loading does not equal Error`() {
        assertNotEquals<PageLoadStatus>(PageLoadStatus.Loading, PageLoadStatus.Error("x"))
    }

    // ========================================
    // Generic type works with domain models
    // ========================================

    @Test
    fun `PaginationState works with Int type`() {
        val state = PaginationState<Int>(items = listOf(1, 2, 3), currentPage = 0)
        assertEquals(3, state.items.size)
        assertEquals(1, state.items.first())
    }

    @Test
    fun `PaginationState works with data class type`() {
        data class Item(val id: Long, val name: String)

        val items = listOf(Item(1L, "Alpha"), Item(2L, "Beta"))
        val state = PaginationState(items = items, currentPage = 0, hasMore = false)

        assertEquals(2, state.items.size)
        assertEquals("Alpha", state.items.first().name)
        assertTrue(state.allLoaded)
    }

    // ========================================
    // PAGE_SIZE constant value
    // ========================================

    @Test
    fun `PAGE_SIZE constant is 25`() {
        assertEquals(25, Constants.PAGE_SIZE)
    }

    @Test
    fun `PAGINATION_TRIGGER_THRESHOLD constant is 5`() {
        assertEquals(5, Constants.PAGINATION_TRIGGER_THRESHOLD)
    }
}
