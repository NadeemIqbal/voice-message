package io.github.nadeemiqbal.voicemessage

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * A WhatsApp/Telegram-style hold-to-record mic with slide-to-lock and slide-to-cancel gestures.
 *
 * The composable is a horizontal [Row] designed to sit at the right edge of a chat input bar.
 * What it draws depends on [VoiceRecorderState.phase]:
 *
 * - [VoicePhase.Idle] / [VoicePhase.Sent] — the [idlePlaceholder] slot (typically the consumer's
 *   text field) on the left + a mic button on the right.
 * - [VoicePhase.RecordingHeld] — the placeholder is replaced by a recording indicator: a live
 *   amplitude waveform built from samples pushed through [VoiceRecorderState.pushAmplitude],
 *   a timer, and a "slide ← to cancel" hint. The mic is highlighted on the right and shows a
 *   lock chevron above it.
 * - [VoicePhase.Cancelling] — the recording indicator turns red and the hint reads "release to
 *   cancel". The user can drag back below the cancel threshold to return to [RecordingHeld].
 * - [VoicePhase.RecordingLocked] — the gesture has ended hands-free. A Cancel "✕" sits on the
 *   left, the timer in the middle and a Send button (right-pointing arrow) on the right.
 *
 * The gesture is detected with [detectDragGesturesAfterLongPress] on the mic. The state machine
 * itself is platform-agnostic and unit-testable — see [VoiceRecorderState] and the
 * `VoiceMessageLogicTest`.
 *
 * @param state controller obtained from [rememberVoiceRecorderState]. Wire your platform audio
 *   stack into its `onStart` / `onCancel` / `onSend` callbacks.
 * @param modifier modifier applied to the root [Row].
 * @param colors override colours; defaults derive from the active [MaterialTheme].
 * @param lockThresholdDp vertical drag distance (upward) before the recording locks.
 * @param cancelThresholdDp horizontal drag distance (leftward) before the recording will cancel
 *   on release.
 * @param idlePlaceholder content shown to the left of the mic when not recording — usually the
 *   text field of your chat input bar. Replaced by the recording indicator while a recording
 *   is in progress.
 */
@Composable
fun VoiceRecorderInput(
    state: VoiceRecorderState,
    modifier: Modifier = Modifier,
    colors: VoiceRecorderColors = VoiceMessageDefaults.recorderColors(),
    lockThresholdDp: Dp = VoiceMessageDefaults.LockThreshold,
    cancelThresholdDp: Dp = VoiceMessageDefaults.CancelThreshold,
    idlePlaceholder: @Composable RowScope.() -> Unit = { Spacer(Modifier.weight(1f)) },
) {
    val density = LocalDensity.current
    val lockThresholdPx = with(density) { lockThresholdDp.toPx() }
    val cancelThresholdPx = with(density) { cancelThresholdDp.toPx() }

    // Drive elapsed time + the maxDuration auto-finish while any recording phase is active.
    LaunchedEffect(state) {
        while (true) {
            if (state.phase == VoicePhase.RecordingHeld ||
                state.phase == VoicePhase.RecordingLocked ||
                state.phase == VoicePhase.Cancelling
            ) {
                state.tick()
            }
            delay(33L) // ~30 Hz ticker
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("voice_recorder_input"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.phase) {
            VoicePhase.Idle, VoicePhase.Sent -> {
                idlePlaceholder()
                MicButton(
                    state = state,
                    colors = colors,
                    lockThresholdPx = lockThresholdPx,
                    cancelThresholdPx = cancelThresholdPx,
                    isRecording = false,
                )
            }

            VoicePhase.RecordingHeld -> {
                RecordingActiveStrip(state = state, colors = colors, cancelling = false)
                MicButton(
                    state = state,
                    colors = colors,
                    lockThresholdPx = lockThresholdPx,
                    cancelThresholdPx = cancelThresholdPx,
                    isRecording = true,
                )
            }

            VoicePhase.Cancelling -> {
                RecordingActiveStrip(state = state, colors = colors, cancelling = true)
                MicButton(
                    state = state,
                    colors = colors,
                    lockThresholdPx = lockThresholdPx,
                    cancelThresholdPx = cancelThresholdPx,
                    isRecording = true,
                )
            }

            VoicePhase.RecordingLocked -> {
                LockedActionButton(
                    contentDescription = "Cancel",
                    glyph = { color -> CrossGlyph(color = color, size = 16.dp) },
                    background = Color.Transparent,
                    iconColor = colors.cancelIconColor,
                    onClick = { state.cancelFromLock() },
                    testTag = "voice_cancel_button",
                )
                RecordingActiveStrip(state = state, colors = colors, cancelling = false)
                LockedActionButton(
                    contentDescription = "Send",
                    glyph = { color -> ArrowGlyph(color = color, size = 16.dp) },
                    background = colors.sendButtonColor,
                    iconColor = colors.sendButtonContentColor,
                    onClick = { state.sendFromLock() },
                    testTag = "voice_send_button",
                )
            }
        }
    }
}

@Composable
private fun RowScope.RecordingActiveStrip(
    state: VoiceRecorderState,
    colors: VoiceRecorderColors,
    cancelling: Boolean,
) {
    val recordingIndicatorAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
    )
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(colors.containerColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Red blinking recording dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(recordingIndicatorAlpha)
                .background(colors.cancelIconColor, CircleShape),
        )
        Text(
            text = formatVoiceDuration(state.elapsed),
            color = colors.timerColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(48.dp),
        )
        // Live waveform — the latest BarCount samples, scrolling from the right.
        VoiceWaveform(
            samples = state.capturedSamples,
            modifier = Modifier.weight(1f).height(28.dp),
            barCount = VoiceMessageDefaults.BarCount,
            barColor = if (cancelling) colors.cancelIconColor else colors.waveformColor,
        )
        Text(
            text = if (cancelling) "Release to cancel" else "← Slide to cancel",
            color = if (cancelling) colors.cancelIconColor else colors.hintTextColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("voice_recorder_hint"),
        )
    }
}

@Composable
private fun MicButton(
    state: VoiceRecorderState,
    colors: VoiceRecorderColors,
    lockThresholdPx: Float,
    cancelThresholdPx: Float,
    isRecording: Boolean,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isRecording) colors.micActiveColor.copy(alpha = 0.22f) else Color.Transparent,
    )
    val iconColor by animateColorAsState(
        targetValue = if (isRecording) colors.micActiveColor else colors.micColor,
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bgColor)
            .testTag("voice_mic_button")
            .pointerInput(state, lockThresholdPx, cancelThresholdPx) {
                // Custom hold-to-record gesture. The built-in `detectDragGesturesAfterLongPress`
                // cancels its long-press timer on the slightest pointer movement (including the
                // sub-pixel mouse jitter most desktop pointers produce), which made the gesture
                // unusable on Desktop and Web. Here we own the timer ourselves: a launched
                // coroutine sleeps `viewConfiguration.longPressTimeoutMillis`, then calls
                // `state.start()` unconditionally — drag deltas only start to flow into
                // `state.updateDrag` after the long-press has fired, so jitter during the hold
                // is harmless.
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                // `PointerInputScope` is suspend but not a `CoroutineScope` — wrap in
                // `coroutineScope { ... }` so we can `launch` the long-press timer alongside the
                // gesture loop. When the modifier is recomposed or removed, the parent
                // cancellation propagates through both children automatically.
                coroutineScope {
                    val scope = this
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragX = 0f
                        var dragY = 0f
                        var started = false

                        val longPressJob = scope.launch {
                            delay(longPressMs)
                            started = true
                            state.start()
                        }

                        try {
                            // Loop until the tracked pointer goes up or out of scope.
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    // Pointer released.
                                    break
                                }
                                if (started) {
                                    val delta = change.positionChange()
                                    dragX += delta.x
                                    dragY += delta.y
                                    state.updateDrag(dragX, dragY, lockThresholdPx, cancelThresholdPx)
                                    change.consume()
                                }
                            }
                        } finally {
                            longPressJob.cancel()
                            if (started) {
                                state.release()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        MicGlyph(color = iconColor, size = 22.dp)
        if (isRecording) {
            // Small lock chevron above the mic — hints at slide-up-to-lock.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp),
            ) {
                ChevronUpGlyph(color = colors.lockIconColor, size = 10.dp)
            }
        }
    }
}

@Composable
private fun LockedActionButton(
    contentDescription: String,
    glyph: @Composable (Color) -> Unit,
    background: Color,
    iconColor: Color,
    onClick: () -> Unit,
    testTag: String,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        glyph(iconColor)
        // Unused parameter — accessibility plumbing would normally add a contentDescription
        // semantic; left as a hook for future a11y work.
        @Suppress("UnusedExpression")
        contentDescription
    }
}

// --- Hand-drawn glyphs (keeps the library icon-dependency-free) ----------------------------

@Composable
private fun MicGlyph(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val capsuleWidth = w * 0.4f
        val capsuleHeight = h * 0.5f
        val capsuleLeft = (w - capsuleWidth) / 2f
        // Capsule (mic head)
        drawRoundRect(
            color = color,
            topLeft = Offset(capsuleLeft, h * 0.08f),
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f),
        )
        // Arc / cradle below the capsule
        val strokeWidth = h * 0.07f
        val arcTop = h * 0.55f
        val arcBottom = h * 0.82f
        // Just two short vertical stalks + a horizontal connector — simplified cradle.
        drawLine(
            color = color,
            start = Offset(w * 0.22f, arcTop),
            end = Offset(w * 0.22f, arcBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.78f, arcTop),
            end = Offset(w * 0.78f, arcBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.22f, arcBottom),
            end = Offset(w * 0.78f, arcBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        // Stand
        drawLine(
            color = color,
            start = Offset(w / 2f, arcBottom),
            end = Offset(w / 2f, h * 0.96f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ArrowGlyph(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Right-pointing arrow head + short shaft for "Send".
        val path = Path().apply {
            moveTo(w * 0.15f, h / 2f)
            lineTo(w * 0.85f, h / 2f)
        }
        drawPath(path = path, color = color, style = Stroke(width = h * 0.16f, cap = StrokeCap.Round))
        val head = Path().apply {
            moveTo(w * 0.6f, h * 0.2f)
            lineTo(w * 0.92f, h / 2f)
            lineTo(w * 0.6f, h * 0.8f)
        }
        drawPath(path = head, color = color, style = Stroke(width = h * 0.16f, cap = StrokeCap.Round))
    }
}

@Composable
private fun CrossGlyph(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val pad = w * 0.18f
        val stroke = h * 0.16f
        drawLine(color, Offset(pad, pad), Offset(w - pad, h - pad), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w - pad, pad), Offset(pad, h - pad), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun ChevronUpGlyph(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = h * 0.22f
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.7f)
            lineTo(w * 0.5f, h * 0.3f)
            lineTo(w * 0.85f, h * 0.7f)
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Suppress("unused")
private val _unused: Duration = Duration.ZERO // retain Duration import in case future API additions need it
