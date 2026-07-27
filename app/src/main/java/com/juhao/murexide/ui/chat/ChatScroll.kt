package com.juhao.murexide.ui.chat

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal fun calculateItemCenterScrollDistance(
    itemOffset: Int,
    itemSize: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int
): Float {
    val itemCenter = itemOffset + itemSize / 2f
    val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2f
    return itemCenter - viewportCenter
}

internal suspend fun LazyListState.animateScrollToCenteredItem(index: Int) {
    // Keep an already visible message in place and animate only the distance to the center.
    val visibleItemScrollDistance = centerScrollDistance(index)
    if (visibleItemScrollDistance != null) {
        animateCenterCorrection(visibleItemScrollDistance)
        return
    }

    // A freshly loaded message can be present in state before LazyColumn has accepted its index.
    val itemIsAvailable = withTimeoutOrNull(2_000L) {
        snapshotFlow { layoutInfo.totalItemsCount > index }.first { it }
    } ?: false
    if (!itemIsAvailable) return

    animateScrollToItem(index)

    val centerScrollDistance = withTimeoutOrNull(2_000L) {
        snapshotFlow { centerScrollDistance(index) }.first { it != null }
    } ?: return

    animateCenterCorrection(centerScrollDistance)
}

private fun LazyListState.centerScrollDistance(index: Int): Float? {
    val currentLayoutInfo = layoutInfo
    val itemInfo = currentLayoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return null

    return calculateItemCenterScrollDistance(
        itemOffset = itemInfo.offset,
        itemSize = itemInfo.size,
        viewportStartOffset = currentLayoutInfo.viewportStartOffset,
        viewportEndOffset = currentLayoutInfo.viewportEndOffset
    )
}

private suspend fun LazyListState.animateCenterCorrection(distance: Float) {
    if (abs(distance) > 0.5f) {
        animateScrollBy(distance)
    }
}
