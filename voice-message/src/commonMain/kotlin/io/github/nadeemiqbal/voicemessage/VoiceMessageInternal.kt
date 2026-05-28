package io.github.nadeemiqbal.voicemessage

import kotlin.math.max
import kotlin.math.min

/**
 * How [downsampleAmplitudes] should treat input longer than [targetCount].
 *
 * - [Live] keeps only the last [targetCount] samples and discards anything older. Used by the
 *   live recording strip so the waveform genuinely scrolls from the right as new samples arrive,
 *   matching WhatsApp / Telegram. Anything shorter than [targetCount] is left-padded with zeros
 *   so a fresh recording grows from the right edge.
 * - [Static] keeps the full history and compresses it into [targetCount] bars via max-of-bucket.
 *   Used by the playback bubble so the bar-split between played and unplayed colours can address
 *   the entire recording.
 */
internal enum class WaveformMode { Live, Static }

/**
 * Down-samples a list of raw amplitude readings (each `0f..1f`) to exactly [targetCount] bars.
 *
 * For [WaveformMode.Static] (default), groups consecutive samples into equal-width buckets and
 * takes the **maximum** of each bucket. Max-of-bucket gives a more visually punchy waveform than
 * mean-of-bucket: quiet runs read as quiet but loud peaks still stand out.
 *
 * For [WaveformMode.Live], keeps only the last [targetCount] samples (sliding window) so the
 * displayed bars actually scroll as new samples arrive.
 *
 * Returns a zero-filled list of size [targetCount] for an empty input. Caps each value to
 * `0f..1f`. Pure helper, exercised directly by the unit tests, no Compose dependency.
 */
internal fun downsampleAmplitudes(
    samples: List<Float>,
    targetCount: Int,
    mode: WaveformMode = WaveformMode.Static,
): List<Float> {
    require(targetCount > 0) { "targetCount must be > 0, was $targetCount" }
    if (samples.isEmpty()) return List(targetCount) { 0f }
    if (samples.size <= targetCount) {
        // Pad on the LEFT with zeros so a partial recording grows from the right edge.
        val pad = targetCount - samples.size
        return List(targetCount) { i ->
            if (i < pad) 0f else samples[i - pad].coerceIn(0f, 1f)
        }
    }
    return when (mode) {
        WaveformMode.Live -> {
            // Sliding window: emit only the last targetCount samples, in order.
            val start = samples.size - targetCount
            List(targetCount) { i -> samples[start + i].coerceIn(0f, 1f) }
        }
        WaveformMode.Static -> {
            val bucketSize = samples.size.toDouble() / targetCount
            List(targetCount) { i ->
                val from = (i * bucketSize).toInt()
                val to = min(samples.size, ((i + 1) * bucketSize).toInt())
                var peak = 0f
                for (j in from until max(to, from + 1)) {
                    val s = samples.getOrNull(j) ?: 0f
                    if (s > peak) peak = s
                }
                peak.coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Pure phase computation from a single drag offset while the user is holding the mic. Returns
 * the new phase given the current state and how far the finger has travelled from the down-point.
 *
 * Coordinate convention: [dragX] is **rightward-positive**, [dragY] is **downward-positive** (the
 * standard Compose pointer convention). The mic is at the right of the input row, so:
 * - "slide up to lock" → dragY <= -lockThresholdPx
 * - "slide left to cancel" → dragX <= -cancelThresholdPx
 *
 * The caller is expected to flip [dragX]'s sign for RTL — that's a UI-layer concern, not pure logic.
 *
 * @param current the phase right now. Transitions only fire while [current] is [VoicePhase.RecordingHeld]
 *   or [VoicePhase.Cancelling]; from other phases the input is dropped and [current] is returned.
 */
internal fun phaseFromDrag(
    current: VoicePhase,
    dragX: Float,
    dragY: Float,
    lockThresholdPx: Float,
    cancelThresholdPx: Float,
): VoicePhase {
    if (current != VoicePhase.RecordingHeld && current != VoicePhase.Cancelling) return current
    if (dragY <= -lockThresholdPx) return VoicePhase.RecordingLocked
    if (dragX <= -cancelThresholdPx) return VoicePhase.Cancelling
    // Already in Cancelling but drifted back below the threshold → resume holding.
    return VoicePhase.RecordingHeld
}

/**
 * Result of [release] / [phaseOnRelease] — the resolved end-state when the finger comes up.
 * Splitting it out lets the caller distinguish "send the audio" from "throw it away" from
 * "stay locked" without inspecting the new phase directly.
 */
internal enum class ReleaseOutcome {
    /** Recording ended successfully — fire `onSend` with the captured samples + elapsed. */
    Send,

    /** Recording was discarded — fire `onCancel`, do not deliver audio. */
    Cancel,

    /** Locked recording continues hands-free — no callback fired yet. */
    Locked,

    /** Recording was released before the minimum duration — silently discard. */
    TooShort,
}

/**
 * Pure end-of-press resolver. Given the [current] phase and how long the user held, returns the
 * [ReleaseOutcome] the FSM should apply.
 */
internal fun phaseOnRelease(
    current: VoicePhase,
    elapsedMillis: Long,
    minDurationMillis: Long,
): ReleaseOutcome = when (current) {
    VoicePhase.RecordingHeld ->
        if (elapsedMillis >= minDurationMillis) ReleaseOutcome.Send else ReleaseOutcome.TooShort
    VoicePhase.Cancelling -> ReleaseOutcome.Cancel
    VoicePhase.RecordingLocked -> ReleaseOutcome.Locked
    else -> ReleaseOutcome.TooShort
}
