package io.github.nadeemiqbal.voicemessage

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The amplitude waveform visualization shared by [VoiceRecorderInput] and [VoiceMessageBubble].
 *
 * Renders vertical pill-shaped bars of fixed [barWidth], each height proportional to a
 * peak-pooled amplitude from [samples] (`0f..1f`). The number of bars drawn is
 * `min(barCount, ⌊canvasWidth / (barWidth + barSpacing)⌋)`: narrow canvases draw fewer bars,
 * wide canvases draw more (up to [barCount]). This keeps the per-bar look consistent across any
 * bubble width and prevents the old bug where wide bubbles produced large round blobs because
 * the bar width was stretched to fill the canvas.
 *
 * When [progress] is `< 1f`, bars at indices below the progress mark are drawn in [barColor]
 * and the rest in [unplayedColor]: used by the bubble to show playback position.
 *
 * @param samples raw amplitude readings, `0f..1f`. Anything longer than the effective bar
 *   count is down-sampled via [downsampleAmplitudes] (max-of-bucket for [live] = false,
 *   sliding-window for [live] = true).
 * @param progress how much of the waveform has "played", in `0f..1f`. Bars at indices
 *   `< effectiveBarCount * progress` use [barColor]; the rest use [unplayedColor]. Pass `1f`
 *   for a fully-coloured static waveform.
 * @param barColor colour of the played / fully-rendered bars.
 * @param unplayedColor colour of the unplayed bars; ignored when [progress] is `>= 1f`.
 * @param barCount upper bound on the number of bars. Defaults to [VoiceMessageDefaults.BarCount].
 * @param barWidth width of each bar (also drives the corner radius for pill ends). Defaults to
 *   [VoiceMessageDefaults.BarWidth]. The composable will not stretch bars beyond this width
 *   even when the canvas is wider than the total bar layout: extra space is split as left/right
 *   padding so the row stays centered.
 * @param live when `true`, only the latest bars are visible and the waveform scrolls
 *   right-to-left as new samples arrive. Used by the live recording strip. Defaults to `false`
 *   (static waveform addressing the full history), which is what the playback bubble wants.
 */
@Composable
fun VoiceWaveform(
    samples: List<Float>,
    barColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = VoiceMessageDefaults.BarCount,
    barWidth: Dp = VoiceMessageDefaults.BarWidth,
    barSpacing: Dp = VoiceMessageDefaults.BarSpacing,
    minBarHeight: Dp = VoiceMessageDefaults.BarMinHeight,
    progress: Float = 1f,
    unplayedColor: Color = barColor,
    live: Boolean = false,
) {
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidth.toPx() }.coerceAtLeast(1f)
    val barSpacingPx = with(density) { barSpacing.toPx() }.coerceAtLeast(0f)
    val minBarHeightPx = with(density) { minBarHeight.toPx() }
    val mode = if (live) WaveformMode.Live else WaveformMode.Static

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

        // Pitch = bar width + spacing. How many full pitches fit in the canvas? That is the
        // effective bar count, capped by the caller-provided maximum.
        val pitch = barWidthPx + barSpacingPx
        val maxFit = ((canvasWidth + barSpacingPx) / pitch).toInt().coerceAtLeast(1)
        val effectiveBarCount = maxFit.coerceAtMost(barCount)

        val bars = downsampleAmplitudes(samples, effectiveBarCount, mode)
        if (bars.isEmpty()) return@Canvas

        val splitIndex = (effectiveBarCount * progress.coerceIn(0f, 1f)).roundToInt()
            .coerceIn(0, effectiveBarCount)

        val totalRowWidth = effectiveBarCount * barWidthPx + (effectiveBarCount - 1) * barSpacingPx
        val leftPadding = ((canvasWidth - totalRowWidth) / 2f).coerceAtLeast(0f)
        val cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
        val minHeight = minBarHeightPx.coerceAtMost(canvasHeight)

        for (i in 0 until effectiveBarCount) {
            val raw = bars.getOrElse(i) { 0f }
            val barHeight = max(minHeight, raw * canvasHeight)
            val left = leftPadding + i * pitch
            val top = (canvasHeight - barHeight) / 2f
            drawRoundRect(
                color = if (i < splitIndex) barColor else unplayedColor,
                topLeft = Offset(left, top),
                size = Size(barWidthPx, barHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}
