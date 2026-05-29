package io.github.nadeemiqbal.voicemessage.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.nadeemiqbal.voicemessage.audio.VoiceAudio
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual class SamplePlayer actual constructor() {
    private var player: AVAudioPlayer? = null
    private var generation: Int = 0
    private var onFinishCb: (() -> Unit)? = null

    actual fun play(
        audio: VoiceAudio,
        onProgress: (fraction: Float) -> Unit,
        onFinish: () -> Unit,
    ) {
        stop()
        try {
            val data = byteArrayToNsData(audio.bytes)
            val p = AVAudioPlayer(data = data, error = null)
            p.prepareToPlay()
            player = p
            onFinishCb = onFinish
            p.play()
            generation += 1
            val mine = generation
            scheduleProgress(mine, onProgress)
        } catch (t: Throwable) {
            onFinish()
        }
    }

    private fun scheduleProgress(mine: Int, onProgress: (fraction: Float) -> Unit) {
        val p = player ?: return
        if (generation != mine) return
        val duration = p.duration
        if (duration > 0) {
            val f = (p.currentTime / duration).toFloat().coerceIn(0f, 1f)
            onProgress(f)
            if (!p.playing) {
                onFinishCb?.invoke()
                onFinishCb = null
                player = null
                return
            }
        }
        val after = dispatch_time(DISPATCH_TIME_NOW, (33L * NSEC_PER_MSEC.toLong()))
        dispatch_after(after, dispatch_get_main_queue()) {
            scheduleProgress(mine, onProgress)
        }
    }

    actual fun stop() {
        generation += 1
        player?.stop()
        player = null
        onFinishCb = null
    }

    actual fun seek(fraction: Float) {
        val p = player ?: return
        val duration = p.duration
        if (duration > 0) {
            p.currentTime = (duration * fraction.coerceIn(0f, 1f).toDouble())
        }
    }

    actual fun release() {
        stop()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun byteArrayToNsData(bytes: ByteArray): NSData {
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}

@Composable
actual fun rememberSamplePlayer(): SamplePlayer {
    val player = remember { SamplePlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
