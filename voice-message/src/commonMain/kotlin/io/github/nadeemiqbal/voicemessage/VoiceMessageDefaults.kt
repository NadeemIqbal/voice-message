package io.github.nadeemiqbal.voicemessage

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Default values + factory functions for the voice messaging composables. */
object VoiceMessageDefaults {

    /** Default number of bars rendered in the waveform. */
    const val BarCount: Int = 40

    /** Default gap between bars. */
    val BarSpacing: Dp = 2.dp

    /** Default minimum bar height — keeps very-quiet bars visible. */
    val BarMinHeight: Dp = 3.dp

    /** Default vertical drag distance from the mic before the recording locks hands-free. */
    val LockThreshold: Dp = 80.dp

    /** Default horizontal drag distance from the mic (leftward) before the recording will cancel on release. */
    val CancelThreshold: Dp = 80.dp

    /** Default minimum hold duration — taps shorter than this are silently dropped. */
    val MinDuration: Duration = 500.milliseconds

    /** Default upper bound on a single recording — auto-finishes when reached. */
    val MaxDuration: Duration = 5.minutes

    /**
     * Default [VoiceRecorderColors], derived from the active [MaterialTheme].
     */
    @Composable
    @ReadOnlyComposable
    fun recorderColors(
        micColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        micActiveColor: Color = MaterialTheme.colorScheme.primary,
        waveformColor: Color = MaterialTheme.colorScheme.primary,
        timerColor: Color = MaterialTheme.colorScheme.onSurface,
        hintTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        cancelIconColor: Color = MaterialTheme.colorScheme.error,
        lockIconColor: Color = MaterialTheme.colorScheme.primary,
        containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        sendButtonColor: Color = MaterialTheme.colorScheme.primary,
        sendButtonContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    ): VoiceRecorderColors = VoiceRecorderColors(
        micColor = micColor,
        micActiveColor = micActiveColor,
        waveformColor = waveformColor,
        timerColor = timerColor,
        hintTextColor = hintTextColor,
        cancelIconColor = cancelIconColor,
        lockIconColor = lockIconColor,
        containerColor = containerColor,
        sendButtonColor = sendButtonColor,
        sendButtonContentColor = sendButtonContentColor,
    )

    /**
     * Default [VoiceMessageBubbleColors] keyed on the message [role]. Sender bubbles read on a
     * primary-tinted background; receiver bubbles on the neutral surface variant.
     */
    @Composable
    @ReadOnlyComposable
    fun bubbleColors(
        role: VoiceMessageRole = VoiceMessageRole.Sender,
    ): VoiceMessageBubbleColors = when (role) {
        VoiceMessageRole.Sender -> VoiceMessageBubbleColors(
            bubbleColor = MaterialTheme.colorScheme.primaryContainer,
            playedBarColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unplayedBarColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
            playIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            playIconBackgroundColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
            durationTextColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            speedChipColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
            speedChipContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        VoiceMessageRole.Receiver -> VoiceMessageBubbleColors(
            bubbleColor = MaterialTheme.colorScheme.surfaceVariant,
            playedBarColor = MaterialTheme.colorScheme.onSurface,
            unplayedBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            playIconColor = MaterialTheme.colorScheme.primary,
            playIconBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            durationTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            speedChipColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
            speedChipContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    /**
     * WhatsApp-style playback-speed cycle: 1× → 1.5× → 2× → 1×. Pure helper so a button-tap
     * handler can just call `onPlaybackSpeedChange(VoiceMessageDefaults.nextPlaybackSpeed(current))`.
     */
    fun nextPlaybackSpeed(current: Float): Float = when {
        current < 1.25f -> 1.5f
        current < 1.75f -> 2f
        else -> 1f
    }

    /** Formats a playback-speed multiplier as a short label: `1×`, `1.5×`, `2×`. */
    fun formatPlaybackSpeed(speed: Float): String =
        if (speed == speed.toInt().toFloat()) "${speed.toInt()}×"
        else "${speed}×"
}

/** Formats a [Duration] as `m:ss` or `mm:ss` for display in the recorder timer / bubble. */
internal fun formatVoiceDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val secondsStr = seconds.toString().padStart(2, '0')
    return "$minutes:$secondsStr"
}
