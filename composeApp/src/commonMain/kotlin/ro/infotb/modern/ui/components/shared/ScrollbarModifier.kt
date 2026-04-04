package ro.infotb.modern.ui.components.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.drawScrollbar(
    state: LazyListState,
    color: Color = Color.Gray.copy(alpha = 0.4f)
): Modifier = composed {
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleItems = state.layoutInfo.visibleItemsInfo.size

    val showScrollbar = totalItems > visibleItems

    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress) 0.8f else 0.3f,
        animationSpec = tween(durationMillis = 300)
    )

    drawWithContent {
        drawContent()
        if (showScrollbar && totalItems > 0) {
            val viewportHeight = size.height
            val scrollbarHeight = (visibleItems.toFloat() / totalItems * viewportHeight)
                .coerceIn(24.dp.toPx(), viewportHeight * 0.5f)

            val firstVisibleIndex = state.firstVisibleItemIndex
            val firstVisibleOffset = state.firstVisibleItemScrollOffset
            val avgItemHeight = if (visibleItems > 0) {
                viewportHeight / visibleItems
            } else viewportHeight

            val scrollProgress = (firstVisibleIndex * avgItemHeight + firstVisibleOffset) /
                    (totalItems * avgItemHeight - viewportHeight).coerceAtLeast(1f)

            val scrollbarY = scrollProgress * (viewportHeight - scrollbarHeight)

            val scrollbarWidth = 4.dp.toPx()
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(size.width - scrollbarWidth - 2.dp.toPx(), scrollbarY),
                size = Size(scrollbarWidth, scrollbarHeight),
                cornerRadius = CornerRadius(scrollbarWidth / 2)
            )
        }
    }
}
