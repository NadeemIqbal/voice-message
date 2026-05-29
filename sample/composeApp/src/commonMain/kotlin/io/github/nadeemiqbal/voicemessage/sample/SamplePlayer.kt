package io.github.nadeemiqbal.voicemessage.sample

import androidx.compose.runtime.Composable
import io.github.nadeemiqbal.voicemessage.audio.VoiceAudio

/**
 * Sample-level audio playback for the demo's voice-message bubbles. NOT part of the published
 * library: voice-message-audio currently only handles capture; playback is BYO at the library
 * level. This expect class is what the sample uses to actually play back what it just recorded
 * so the demo isn't a silent simulation. Future versions of voice-message-audio may absorb
 * this as a first-class `VoiceAudioPlayer` API.
 */
expect class SamplePlayer() {
    /**
     * Start playing the given audio. If another audio is already playing, it's stopped first.
     * Implementations should call [onProgress] with the current playback fraction (0..1) at a
     * reasonable cadence (e.g., 30 Hz) and [onFinish] when playback reaches the end naturally.
     */
    fun play(
        audio: VoiceAudio,
        onProgress: (fraction: Float) -> Unit,
        onFinish: () -> Unit,
    )

    /** Stop any in-flight playback. Idempotent. */
    fun stop()

    /** Seek the active audio to [fraction] (0..1). No-op if nothing is playing. */
    fun seek(fraction: Float)

    /** Release any platform resources held by this player. */
    fun release()
}

@Composable
expect fun rememberSamplePlayer(): SamplePlayer
