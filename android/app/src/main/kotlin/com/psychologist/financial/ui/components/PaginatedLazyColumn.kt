package com.psychologist.financial.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.psychologist.financial.utils.Constants
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow

/**
 * A LazyColumn that automatically triggers next-page loading when the user
 * scrolls within [Constants.PAGINATION_TRIGGER_THRESHOLD] items of the bottom.
 *
 * Appends a loading indicator at the bottom while [isLoading] is true,
 * or an error message when [isError] is true (user retries by scrolling).
 * Shows nothing extra when all pages are loaded ([allLoaded]).
 *
 * @param items All records loaded so far (accumulated across pages).
 * @param isLoading True while the next page is being fetched.
 * @param isError True when the last page fetch failed.
 * @param allLoaded True when no more pages are available (hasMore = false).
 * @param onLoadMore Called when the scroll position enters the trigger threshold.
 * @param modifier Modifier for the outer LazyColumn.
 * @param key Optional stable key extractor for list items (improves diffing).
 * @param itemContent Composable content for each item.
 */
@Composable
fun <T> PaginatedLazyColumn(
    items: List<T>,
    isLoading: Boolean,
    isError: Boolean,
    allLoaded: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - Constants.PAGINATION_TRIGGER_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(
            items = items,
            key = key?.let { keyFn -> { item: T -> keyFn(item) } }
        ) { item ->
            itemContent(item)
        }

        item {
            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                isError -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Erro ao carregar. Role para tentar novamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
