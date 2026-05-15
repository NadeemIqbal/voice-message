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
 * Renders [barCount] vertical rounded bars, each height proportional to a peak-pooled amplitude
 * from [samples] (`0f..1f`). When [progress] is `< 1f`, bars at indices below the progress mark
 * are drawn in [barColor] and the rest in [unplayedColor] — used by the bubble to show playback
 * position.
 *
 * @param samples raw amplitude readings, `0f..1f`. Anything longer than [barCount] is down-sampled
 *   via [downsampleAmplitudes] (max-of-bucket).
 * @param progress how much of the waveform has "played", in `0f..1f`. Bars at indices `< barCount * progress`
 *   use [barColor]; the rest use [unplayedColor]. Pass `1f` for a fully-coloured static waveform.
 * @param barColor colour of the played / fully-rendered bars.
 * @param unplayedColor colour of the unplayed bars; ignored when [progress] is `>= 1f`.
 * @param minBarHeightFraction the minimum height of any bar as a fraction of the canvas height,
 *   so very-quiet samples still read as bars and not blank space.
 */
@Composable
fun VoiceWaveform(
    samples: List<Float>,
    barColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = VoiceMessageDefaults.BarCount,
    barSpacing: Dp = VoiceMessageDefaults.BarSpacing,
    minBarHeight: Dp = VoiceMessageDefaults.BarMinHeight,
    progress: Float = 1f,
    unplayedColor: Color = barColor,
) {
    val density = LocalDensity.current
    val barSpacingPx = with(density) { barSpacing.toPx() }
    val minBarHeightPx = with(density) { minBarHeight.toPx() }
    val bars = remember(samples, barCount) { downsampleAmplitudes(samples, barCount) }
    val splitIndex = (barCount * progress.coerceIn(0f, 1f)).roundToInt().coerceIn(0, barCount)

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (canvasWidth <= 0f || canvasHeight <= 0f || bars.isEmpty()) return@Canvas
        val totalSpacing = barSpacingPx * (barCount - 1)
        val barWidth = max((canvasWidth - totalSpacing) / barCount, 1f)
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        val minHeight = minBarHeightPx.coerceAtMost(canvasHeight)
        for (i in 0 until barCount) {
            val raw = bars.getOrElse(i) { 0f }
            val barHeight = max(minHeight, raw * canvasHeight)
            val left = i * (barWidth + barSpacingPx)
            val top = (canvasHeight - barHeight) / 2f
            drawRoundRect(
                color = if (i < splitIndex) barColor else unplayedColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}
