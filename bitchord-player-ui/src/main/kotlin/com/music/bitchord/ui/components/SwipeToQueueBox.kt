package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Constants governing Swipe to Queue gesture behavior and physical thresholds.
 */
const val SWIPE_THRESHOLD_FRACTION = 0.45f
const val SWIPE_RESET_HYSTERESIS_FRACTION = 0.80f
const val FLING_VELOCITY_THRESHOLD = 1000f
const val MIN_FLING_OFFSET_FRACTION = 0.15f

/**
 * State machine managing gesture lifecycle, single-execution protection,
 * tactile haptic states, velocity tracking, and elastic return guarantees.
 */
class SwipeToQueueGestureState(
    boxWidth: Float,
    val thresholdFraction: Float = SWIPE_THRESHOLD_FRACTION,
    val resetHysteresisFraction: Float = SWIPE_RESET_HYSTERESIS_FRACTION,
    val flingVelocityThreshold: Float = FLING_VELOCITY_THRESHOLD,
    val minFlingOffsetFraction: Float = MIN_FLING_OFFSET_FRACTION,
    private val onEnqueue: () -> Unit,
) {
    var boxWidth: Float = boxWidth
        private set

    val hasTriggeredInCurrentGesture = AtomicBoolean(false)

    var isArmed: Boolean = false
        private set

    var tickCount: Int = 0
        private set

    var enqueueCount: Int = 0
        private set

    var currentOffset: Float = 0f
        private set

    val threshold: Float get() = boxWidth * thresholdFraction
    val resetThreshold: Float get() = threshold * resetHysteresisFraction
    val minFlingOffset: Float get() = boxWidth * minFlingOffsetFraction

    // Backward-compatibility aliases for tests
    val armThreshold: Float get() = threshold
    val armed: Boolean get() = isArmed

    fun updateBoxWidth(width: Float) {
        if (width > 0f) {
            boxWidth = width
        }
    }

    fun onDragStart() {
        hasTriggeredInCurrentGesture.set(false)
        isArmed = false
        currentOffset = 0f
    }

    fun onDrag(dragAmount: Float): Float {
        val rawOffset = (currentOffset + dragAmount).coerceAtLeast(0f)
        val dampedOffset = if (rawOffset > threshold) {
            threshold + (rawOffset - threshold) * 0.35f
        } else {
            rawOffset
        }
        currentOffset = dampedOffset
        updateHapticState(rawOffset)
        return currentOffset
    }

    fun onOffsetUpdate(offset: Float) {
        val nonNegative = offset.coerceAtLeast(0f)
        currentOffset = nonNegative
        updateHapticState(nonNegative)
    }

    // Alias for test compatibility
    fun onOffsetChange(offset: Float) = onOffsetUpdate(offset)

    private fun updateHapticState(offset: Float) {
        when {
            !isArmed && offset >= threshold -> {
                isArmed = true
                tickCount++
            }
            isArmed && offset < resetThreshold -> {
                isArmed = false
            }
        }
    }

    fun onRelease(velocity: Float = 0f): Boolean {
        val isThresholdMet = currentOffset >= threshold || isArmed
        val isFling = velocity >= flingVelocityThreshold && currentOffset >= minFlingOffset
        val shouldTrigger = (isThresholdMet || isFling) && hasTriggeredInCurrentGesture.compareAndSet(false, true)
        if (shouldTrigger) {
            enqueueCount++
            onEnqueue()
            return true
        }
        return false
    }

    fun onCancel() {
        // Cancel does not enqueue. State will be reset upon spring completion.
    }

    fun onSpringComplete() {
        currentOffset = 0f
        hasTriggeredInCurrentGesture.set(false)
        isArmed = false
    }

    fun simulateSpringReturn(): Float {
        return try {
            0f
        } finally {
            onSpringComplete()
        }
    }

    // Invariant backward-compatibility for legacy confirmValueChange check
    fun confirmValueChange(targetIsDismissed: Boolean, offset: Float): Boolean {
        if (targetIsDismissed) {
            onOffsetUpdate(offset)
            onRelease(0f)
        }
        // Invariant: NEVER dismiss, always bounce back
        return false
    }
}

/**
 * Wraps content with a right-swipe gesture revealing queue action background.
 * Triggers [onSwipeToQueue] when swiped past 45% of width or on quick fling,
 * with guaranteed single execution and elastic spring return.
 * If [onSwipeToQueue] is null, renders [content] directly.
 *
 * Implements FASE 06.9.1 — Requirement R1 (Swipe to Queue Single Execution & Elastic Return).
 */
@Composable
fun SwipeToQueueBox(
    onSwipeToQueue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    playNext: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (onSwipeToQueue == null) {
        content()
        return
    }

    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()
    var boxWidth by remember { mutableFloatStateOf(0f) }
    val offsetX = remember { Animatable(0f) }

    var isArmed by remember { mutableStateOf(false) }
    var isTriggeredFeedback by remember { mutableStateOf(false) }

    val currentOnSwipeToQueue by rememberUpdatedState(onSwipeToQueue)

    val gestureState = remember {
        SwipeToQueueGestureState(
            boxWidth = boxWidth,
            thresholdFraction = SWIPE_THRESHOLD_FRACTION,
            resetHysteresisFraction = SWIPE_RESET_HYSTERESIS_FRACTION,
            flingVelocityThreshold = FLING_VELOCITY_THRESHOLD,
            minFlingOffsetFraction = MIN_FLING_OFFSET_FRACTION,
            onEnqueue = { currentOnSwipeToQueue() },
        )
    }
    gestureState.updateBoxWidth(boxWidth)

    Box(
        modifier = modifier
            .onSizeChanged { boxWidth = it.width.toFloat() }
            .pointerInput(boxWidth) {
                if (boxWidth <= 0f) return@pointerInput
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        gestureState.onDragStart()
                        isArmed = false
                        isTriggeredFeedback = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val dampedOffset = gestureState.onDrag(dragAmount)
                        coroutineScope.launch {
                            offsetX.snapTo(dampedOffset)
                        }

                        if (!isArmed && gestureState.isArmed) {
                            isArmed = true
                            haptics.play(Haptic.Tick)
                        } else if (isArmed && !gestureState.isArmed) {
                            isArmed = false
                        }
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().x
                        coroutineScope.launch {
                            val triggered = gestureState.onRelease(velocity)
                            if (triggered) {
                                isTriggeredFeedback = true
                                haptics.play(Haptic.Select)
                            }
                            try {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            } finally {
                                offsetX.snapTo(0f)
                                gestureState.onSpringComplete()
                                isArmed = false
                                isTriggeredFeedback = false
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            gestureState.onCancel()
                            try {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            } finally {
                                offsetX.snapTo(0f)
                                gestureState.onSpringComplete()
                                isArmed = false
                                isTriggeredFeedback = false
                            }
                        }
                    }
                )
            }
    ) {
        QueueSwipeBackground(
            offsetProvider = { offsetX.value },
            threshold = gestureState.threshold,
            isArmed = isArmed,
            isTriggered = isTriggeredFeedback,
            playNext = playNext,
        )

        Box(
            modifier = Modifier.offset {
                IntOffset(offsetX.value.roundToInt(), 0)
            }
        ) {
            content()
        }
    }
}

@Composable
private fun QueueSwipeBackground(
    offsetProvider: () -> Float,
    threshold: Float,
    isArmed: Boolean,
    isTriggered: Boolean,
    playNext: Boolean,
) {
    val cyanAccent = Color(0xFF00E5FF)

    val bgAlpha = when {
        isTriggered -> 0.40f
        isArmed -> 0.30f
        else -> 0.18f
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val offset = offsetProvider()
                if (offset > 0f) {
                    clipRect(left = 0f, top = 0f, right = offset, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
            .background(cyanAccent.copy(alpha = bgAlpha))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        val currentOffset = offsetProvider()
        val progress = if (threshold > 0f) (currentOffset / threshold).coerceIn(0f, 1f) else 0f

        val contentAlpha = (0.4f + 0.6f * progress).coerceIn(0.4f, 1f)
        val iconScale = when {
            isTriggered -> 1.20f
            isArmed -> 1.15f
            else -> 0.85f + 0.15f * progress
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.graphicsLayer {
                alpha = contentAlpha
                scaleX = iconScale
                scaleY = iconScale
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = cyanAccent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = if (playNext) "Reproducir siguiente" else "Añadir a la cola",
                style = MaterialTheme.typography.labelLarge,
                color = cyanAccent,
            )
        }
    }
}
