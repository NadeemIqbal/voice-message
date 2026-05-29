package io.github.nadeemiqbal.voicemessage.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.nadeemiqbal.voicemessage.audio.VoiceAudio
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import kotlin.concurrent.thread

/**
 * Desktop sample-level playback for WAV-encoded voice recordings (voice-message-audio's
 * Desktop adapter writes 16-bit PCM in a RIFF/WAVE container). Uses javax.sound.sampled.Clip
 * for simple in-memory playback with a polling timer for progress updates.
 */
actual class SamplePlayer actual constructor() {
    @Volatile private var activeClip: Clip? = null
    @Volatile private var progressThread: Thread? = null

    actual fun play(
        audio: VoiceAudio,
        onProgress: (fraction: Float) -> Unit,
        onFinish: () -> Unit,
    ) {
        stop()
        try {
            val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audio.bytes))
            val clip = AudioSystem.getClip()
            clip.open(stream)
            // The clip's "natural end" fires LineEvent.Type.STOP; convert to onFinish().
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) onFinish()
            }
            activeClip = clip
            clip.start()
            val totalMicros = clip.microsecondLength.coerceAtLeast(1L)
            progressThread = thread(start = true, isDaemon = true, name = "voice-sample-playback") {
                while (activeClip === clip && clip.isOpen) {
                    val f = clip.microsecondPosition.toFloat() / totalMicros.toFloat()
                    onProgress(f.coerceIn(0f, 1f))
                    Thread.sleep(33)
                }
            }
        } catch (t: Throwable) {
            onFinish()
        }
    }

    actual fun stop() {
        progressThread?.interrupt()
        progressThread = null
        activeClip?.let {
            try { it.stop() } catch (_: Throwable) {}
            try { it.close() } catch (_: Throwable) {}
        }
        activeClip = null
    }

    actual fun seek(fraction: Float) {
        val clip = activeClip ?: return
        val totalMicros = clip.microsecondLength.coerceAtLeast(1L)
        val target = (totalMicros * fraction.coerceIn(0f, 1f)).toLong()
        try { clip.microsecondPosition = target } catch (_: Throwable) {}
    }

    actual fun release() {
        stop()
    }
}

@Composable
actual fun rememberSamplePlayer(): SamplePlayer {
    val player = remember { SamplePlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
