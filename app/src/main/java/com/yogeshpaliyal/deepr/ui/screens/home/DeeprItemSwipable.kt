package com.yogeshpaliyal.deepr.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.deepr.GetLinksAndTags
import com.yogeshpaliyal.deepr.R
import compose.icons.TablerIcons
import compose.icons.tablericons.Edit
import compose.icons.tablericons.Trash
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Fraction of the item width the row must be dragged to trigger the action. */
private const val DISMISS_THRESHOLD_FRACTION = 0.35f

/**
 * A horizontal drag engages the swipe only if it dominates the vertical movement by this factor;
 * otherwise the gesture is left untouched so the list can scroll natively and smoothly.
 */
private const val HORIZONTAL_DOMINANCE_RATIO = 1.5f

/** Minimum release velocity (px/s) that turns a swipe into a dismiss. */
private const val FLING_VELOCITY_THRESHOLD = 2500f

private enum class DragAxis {
    Undecided,
    Horizontal,
    Vertical,
}

@Composable
fun DeeprItemSwipable(
    account: GetLinksAndTags,
    onItemClick: (MenuItem) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val viewConfig = LocalViewConfiguration.current
    val touchSlopPx = viewConfig.touchSlop
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var itemWidthPx by remember { mutableIntStateOf(0) }
    var animJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(account.id) {
        offsetX = 0f
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
    ) {
        SwipeBackground(
            offsetX = offsetX,
            widthPx = itemWidthPx,
            modifier = Modifier.matchParentSize(),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { itemWidthPx = it.width }
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .pointerInput(account.id) {
                        val velocityTracker = VelocityTracker()

                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            animJob?.cancel()

                            var axis = DragAxis.Undecided
                            var totalDragX = 0f
                            var totalDragY = 0f
                            velocityTracker.resetTracking()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                // Track the full gesture even while another handler (the list)
                                // consumes the events, so classification is never blocked.
                                totalDragX += change.positionChangeIgnoreConsumed().x
                                totalDragY += change.positionChangeIgnoreConsumed().y

                                if (axis == DragAxis.Undecided) {
                                    if (event.changes.size > 1) {
                                        // Multi-touch (pinch / second finger): never swipe.
                                        axis = DragAxis.Vertical
                                    } else if (
                                        abs(totalDragX) > touchSlopPx &&
                                        abs(totalDragX) >
                                        abs(totalDragY) * HORIZONTAL_DOMINANCE_RATIO
                                    ) {
                                        axis = DragAxis.Horizontal
                                        velocityTracker.resetTracking()
                                    } else if (abs(totalDragY) > touchSlopPx) {
                                        // Predominantly vertical: hand everything to the list
                                        // without consuming any event, so scrolling stays native.
                                        axis = DragAxis.Vertical
                                    }
                                }

                                if (axis == DragAxis.Horizontal) {
                                    val dragX = change.positionChange().x
                                    velocityTracker.addPosition(
                                        change.uptimeMillis,
                                        change.position,
                                    )
                                    val maxSwipePx = size.width.toFloat()
                                    offsetX = (offsetX + dragX).coerceIn(-maxSwipePx, maxSwipePx)
                                    change.consume()
                                }

                                if (event.changes.none { it.pressed }) break
                            }

                            if (axis == DragAxis.Horizontal &&
                                itemWidthPx > 0 &&
                                abs(offsetX) > 0f
                            ) {
                                val thresholdPx = itemWidthPx * DISMISS_THRESHOLD_FRACTION
                                val velocity = velocityTracker.calculateVelocity().x
                                val dismissedByDistance = abs(offsetX) >= thresholdPx
                                val dismissedByFling =
                                    abs(velocity) > FLING_VELOCITY_THRESHOLD &&
                                        sign(velocity) == sign(offsetX)

                                val currentOffset = offsetX
                                if (dismissedByDistance || dismissedByFling) {
                                    val direction = sign(currentOffset)
                                    animJob =
                                        scope.launch {
                                            animate(
                                                initialValue = currentOffset,
                                                targetValue = direction * itemWidthPx,
                                                animationSpec = tween(durationMillis = 150),
                                            ) { value, _ ->
                                                offsetX = value
                                            }
                                            if (direction > 0) {
                                                onItemClick(MenuItem.Edit(account))
                                            } else {
                                                onItemClick(MenuItem.Delete(account))
                                            }
                                            animate(
                                                initialValue = direction * itemWidthPx,
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 200),
                                            ) { value, _ ->
                                                offsetX = value
                                            }
                                        }
                                } else {
                                    animJob =
                                        scope.launch {
                                            animate(
                                                initialValue = currentOffset,
                                                targetValue = 0f,
                                                animationSpec =
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                            ) { value, _ ->
                                                offsetX = value
                                            }
                                        }
                                }
                            } else if (abs(offsetX) > 0f) {
                                val currentOffset = offsetX
                                animJob =
                                    scope.launch {
                                        animate(
                                            initialValue = currentOffset,
                                            targetValue = 0f,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                        ) { value, _ ->
                                            offsetX = value
                                        }
                                    }
                            }
                        }
                    },
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeBackground(
    offsetX: Float,
    widthPx: Int,
    modifier: Modifier = Modifier,
) {
    val progress =
        if (widthPx > 0) {
            (abs(offsetX) / (widthPx * DISMISS_THRESHOLD_FRACTION)).coerceIn(0f, 1f)
        } else {
            0f
        }
    val isStartToEnd = offsetX > 0f
    val backgroundColor = (if (isStartToEnd) Color.Gray else Color.Red).copy(alpha = 0.5f * progress)
    val icon = if (isStartToEnd) TablerIcons.Edit else TablerIcons.Trash
    val label = if (isStartToEnd) R.string.edit else R.string.delete
    val alignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        modifier =
            modifier
                .background(backgroundColor, RoundedCornerShape(8.dp)),
        contentAlignment = alignment,
    ) {
        if (progress > 0f) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(label),
                tint = Color.White.copy(alpha = progress),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
