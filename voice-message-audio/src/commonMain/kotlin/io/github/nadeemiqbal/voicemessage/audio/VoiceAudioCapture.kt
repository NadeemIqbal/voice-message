package io.github.nadeemiqbal.voicemessage.audio

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * A captured voice recording. The byte layout and MIME type are platform-native:
 *
 * - Android: AAC inside an MPEG-4 container (`audio/mp4`).
 * - iOS: AAC inside an MPEG-4 container (`audio/mp4`).
 * - Desktop (JVM): linear PCM inside a WAV container (`audio/wav`).
 * - Web (Wasm): WebM/Opus or MP4/AAC depending on browser support, advertised in [mimeType].
 *
 * Consumers that need a single format across platforms should re-encode after `stop()`.
 * For typical voice-message use (upload + server-side normalisation) the native formats are
 * fine and avoid the cost of in-app re-encoding.
 */
data class VoiceAudio(
    val bytes: ByteArray,
    val mimeType: String,
    val duration: Duration,
    val sampleRate: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceAudio) return false
        return mimeType == other.mimeType &&
            duration == other.duration &&
            sampleRate == other.sampleRate &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + sampleRate
        return result
    }
}

/**
 * Cross-platform microphone capture for use with `VoiceRecorderState`.
 *
 * Each platform owns its native recorder and exposes a uniform Kotlin API:
 *
 * - [start] opens the platform mic, begins encoding, and starts emitting amplitude readings
 *   on [amplitudes]. Suspending so iOS / Android permission prompts can be awaited.
 * - [stop] finalises the recording and returns the captured [VoiceAudio]. Calling without a
 *   prior [start] is undefined behaviour.
 * - [cancel] aborts recording and discards any captured bytes.
 *
 * [amplitudes] emits one `Float` in `0f..1f` per platform sample, throttled to a steady
 * 20-30 Hz so the [VoiceRecorderState.pushAmplitude] downstream isn't flooded. Emissions stop
 * when [stop] or [cancel] is called.
 */
interface VoiceAudioCapture {
    val amplitudes: Flow<Float>
    suspend fun start()
    suspend fun stop(): VoiceAudio
    fun cancel()
}

/**
 * Returns a platform [VoiceAudioCapture] bound to the current composition. The capture is
 * disposed when the composition leaves, releasing platform mic resources.
 *
 * For most apps you don't need to call this directly. Use [rememberAudioBoundVoiceRecorderState]
 * instead, which wires capture into a [VoiceRecorderState] for you.
 */
@Composable
expect fun rememberVoiceAudioCapture(): VoiceAudioCapture
