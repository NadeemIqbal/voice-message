package io.github.nadeemiqbal.voicemessage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration

/**
 * A WhatsApp/Telegram-style voice-message chat bubble. Renders [samples] as a tappable waveform,
 * splits the bars into "played" and "unplayed" colours based on [progress], and shows the total
 * recording [duration].
 *
 * Playback itself is **BYO** — the consumer drives [isPlaying] and [progress] from their own
 * audio player (e.g. ExoPlayer on Android, AVAudioPlayer on iOS, JavaSound on Desktop, Web
 * Audio on Web). Tapping anywhere on the waveform fires [onSeek] with the tapped fraction so the
 * player can jump there; the play/pause button fires [onPlayPauseToggle].
 *
 * @param samples the amplitude samples captured by the recorder (`0f..1f`). Downsampled to
 *   [barCount] bars for display.
 * @param duration total recording length — formatted as `m:ss` to the right of the waveform.
 * @param isPlaying drives the play / pause icon.
 * @param progress how much has played, `0f..1f`.
 * @param onPlayPauseToggle fired when the user taps the play / pause button.
 * @param onSeek fired when the user taps the waveform, with the tapped fraction `0f..1f`.
 * @param role visual variant — sender (your messages, primary-tinted) or receiver (their
 *   messages, neutral surface).
 * @param colors override colours; defaults derive from the active [MaterialTheme] + [role].
 * @param barCount how many bars the waveform should be down-sampled to.
 */
@Composable
fun VoiceMessageBubble(
    samples: List<Float>,
    duration: Duration,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    role: VoiceMessageRole = VoiceMessageRole.Sender,
    colors: VoiceMessageBubbleColors = VoiceMessageDefaults.bubbleColors(role),
    barCount: Int = VoiceMessageDefaults.BarCount,
    playbackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
) {
    // The speed chip appears once playback has started (mirrors WhatsApp). Before that, the
    // duration label sits in the same slot.
    val showSpeedChip = isPlaying || progress > 0f

    Row(
        modifier = modifier
            .background(colors.bubbleColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .widthIn(min = 220.dp)
            .testTag("voice_message_bubble"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayPauseButton(
            isPlaying = isPlaying,
            iconColor = colors.playIconColor,
            backgroundColor = colors.playIconBackgroundColor,
            onClick = onPlayPauseToggle,
        )

        // Tappable waveform — converts tap-x to a 0..1 fraction and forwards to onSeek.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .pointerInput(barCount) {
                    detectTapGestures { offset ->
                        val w = size.width
                        if (w > 0) onSeek((offset.x / w).coerceIn(0f, 1f))
                    }
                }
                .testTag("voice_message_waveform"),
        ) {
            VoiceWaveform(
                samples = samples,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                barCount = barCount,
                barColor = colors.playedBarColor,
                progress = progress,
                unplayedColor = colors.unplayedBarColor,
            )
        }

        if (showSpeedChip) {
            SpeedChip(
                speed = playbackSpeed,
                contentColor = colors.speedChipContentColor,
                backgroundColor = colors.speedChipColor,
                onCycle = { onPlaybackSpeedChange(VoiceMessageDefaults.nextPlaybackSpeed(playbackSpeed)) },
            )
        } else {
            Text(
                text = formatVoiceDuration(duration),
                color = colors.durationTextColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(38.dp),
            )
        }
    }
}

@Composable
private fun SpeedChip(
    speed: Float,
    contentColor: Color,
    backgroundColor: Color,
    onCycle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(10.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onCycle() }) }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("voice_message_speed_chip"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = VoiceMessageDefaults.formatPlaybackSpeed(speed),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Circular play/pause button with a hand-drawn icon — keeps the library icon-dependency-free. */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    iconColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(backgroundColor, CircleShape)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .testTag(if (isPlaying) "voice_pause_button" else "voice_play_button"),
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseGlyph(isPlaying = isPlaying, color = iconColor, size = 14.dp)
    }
}

@Composable
private fun PlayPauseGlyph(isPlaying: Boolean, color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (isPlaying) {
            val barW = w * 0.28f
            drawRect(color = color, topLeft = Offset(0f, 0f), size = Size(barW, h))
            drawRect(color = color, topLeft = Offset(w - barW, 0f), size = Size(barW, h))
        } else {
            // Right-pointing triangle (play).
            val path = Path().apply {
                moveTo(w * 0.1f, 0f)
                lineTo(w, h / 2f)
                lineTo(w * 0.1f, h)
                close()
            }
            drawPath(path = path, color = color)
        }
    }
}
