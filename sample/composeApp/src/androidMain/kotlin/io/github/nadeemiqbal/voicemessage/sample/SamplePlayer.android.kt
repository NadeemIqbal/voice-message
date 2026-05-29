package io.github.nadeemiqbal.voicemessage.sample

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.nadeemiqbal.voicemessage.audio.VoiceAudio
import java.io.File
import java.io.FileOutputStream

/**
 * Android sample-level playback for AAC/MP4 voice recordings produced by
 * voice-message-audio's Android adapter. MediaPlayer wants a file path or content URI, not raw
 * bytes, so we spill the payload to the app's cache dir and play from there.
 *
 * The temp file is deleted on stop / release so the cache doesn't accumulate.
 */
actual class SamplePlayer actual constructor() {
    private var player: MediaPlayer? = null
    private var tempFile: File? = null
    private var progressJob: Thread? = null

    actual fun play(
        audio: VoiceAudio,
        onProgress: (fraction: Float) -> Unit,
        onFinish: () -> Unit,
    ) {
        stop()
        try {
            val cacheDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            val file = File.createTempFile("voice-sample-", ".m4a", cacheDir)
            FileOutputStream(file).use { it.write(audio.bytes) }
            tempFile = file
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    onFinish()
                }
                prepare()
                start()
            }
            player = mp
            progressJob = Thread {
                try {
                    while (player === mp && mp.isPlaying) {
                        val total = mp.duration.coerceAtLeast(1)
                        val current = mp.currentPosition
                        val f = current.toFloat() / total.toFloat()
                        onProgress(f.coerceIn(0f, 1f))
                        Thread.sleep(33)
                    }
                } catch (_: Throwable) { /* ignore */ }
            }.also { it.isDaemon = true; it.start() }
        } catch (t: Throwable) {
            onFinish()
        }
    }

    actual fun stop() {
        progressJob?.interrupt()
        progressJob = null
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Throwable) {}
            try { it.release() } catch (_: Throwable) {}
        }
        player = null
        tempFile?.delete()
        tempFile = null
    }

    actual fun seek(fraction: Float) {
        val mp = player ?: return
        val total = mp.duration.coerceAtLeast(1)
        val target = (total * fraction.coerceIn(0f, 1f)).toInt()
        try { mp.seekTo(target) } catch (_: Throwable) {}
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
