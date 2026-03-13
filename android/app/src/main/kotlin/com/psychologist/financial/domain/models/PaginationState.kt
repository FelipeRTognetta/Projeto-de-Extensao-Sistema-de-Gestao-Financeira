package com.psychologist.financial.domain.models

import com.psychologist.financial.utils.Constants

/**
 * Generic pagination state for a single paginated list.
 *
 * Holds all records loaded so far (accumulated across pages), the current
 * page number, the load status, and whether more pages are available.
 *
 * A page returning fewer than PAGE_SIZE items sets hasMore = false,
 * indicating the end of the dataset has been reached.
 *
 * All five paginated list screens (global: patients, appointments, payments;
 * per-patient: appointments, payments) use this same type.
 *
 * @param T The domain model type for items in this list.
 */
data class PaginationState<T>(
    val items: List<T> = emptyList(),
    val currentPage: Int = 0,
    val status: PageLoadStatus = PageLoadStatus.Idle,
    val hasMore: Boolean = true
) {
    val isLoading: Boolean get() = status == PageLoadStatus.Loading
    val isError: Boolean get() = status is PageLoadStatus.Error
    val allLoaded: Boolean get() = !hasMore
}

/**
 * Load status for a paginated list.
 *
 * State transitions:
 *   Idle ──loadNextPage()──► Loading ──success──► Idle (hasMore updated)
 *                                    └─failure──► Error
 *   Error ──loadNextPage()──► Loading  (retry by scrolling into threshold)
 */
sealed class PageLoadStatus {
    object Idle : PageLoadStatus()
    object Loading : PageLoadStatus()
    data class Error(val message: String) : PageLoadStatus()
}
