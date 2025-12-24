package me.rerere.rikkahub.ui.components.ui.liquidglass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import kotlinx.coroutines.coroutineScope

/**
 * Custom gesture detection for Liquid Glass buttons.
 * Detects drag gestures and consumes events to prevent conflicts with parent gestures.
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (PointerInputChange) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    coroutineScope {
        awaitEachGesture {
            val initialDown = awaitFirstDown(requireUnconsumed = false)
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
            onDragStart(initialDown)

            var lastPosition = initialDown.position
            try {
                while (true) {
                    val event = withTimeoutOrNull(longPressTimeout) {
                        awaitPointerEvent(PointerEventPass.Final)
                    }
                    if (event == null) {
                        // Long press timeout exceeded, continue tracking
                        continue
                    }
                    val change = event.changes.firstOrNull()
                    if (change == null) {
                        onDragEnd()
                        break
                    }
                    if (change.changedToUp()) {
                        change.consume()
                        onDragEnd()
                        break
                    }
                    val dragDistance = change.position - lastPosition
                    change.consume()
                    onDrag(change, dragDistance)
                    lastPosition = change.position
                }
            } catch (e: PointerEventTimeoutCancellationException) {
                onDragCancel()
            }
        }
    }
}
