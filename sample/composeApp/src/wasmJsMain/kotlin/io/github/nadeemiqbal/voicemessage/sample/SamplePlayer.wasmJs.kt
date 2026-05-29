@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.nadeemiqbal.voicemessage.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.nadeemiqbal.voicemessage.audio.VoiceAudio
import kotlin.random.Random

actual class SamplePlayer actual constructor() {
    // Each player session lives behind a numeric id in a JS-side global table so callbacks can
    // be wired without passing complex Kotlin lambdas through Wasm-JS interop. Incrementing the
    // id per play() call lets us cancel any prior session cleanly.
    private var activeSessionId: Int = 0

    actual fun play(
        audio: VoiceAudio,
        onProgress: (fraction: Float) -> Unit,
        onFinish: () -> Unit,
    ) {
        stop()
        val sessionId = Random.nextInt(1_000_000_000)
        activeSessionId = sessionId
        val base64 = encodeBase64(audio.bytes)
        startPlayback(sessionId, base64, audio.mimeType, onProgress, onFinish)
    }

    actual fun stop() {
        val id = activeSessionId
        if (id != 0) {
            stopPlaybackJs(id)
            activeSessionId = 0
        }
    }

    actual fun seek(fraction: Float) {
        val id = activeSessionId
        if (id != 0) seekPlaybackJs(id, fraction)
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

/**
 * Base64-encodes a ByteArray without bringing in kotlinx-io. Voice messages are small so the
 * naive lookup-table encoder is fast enough.
 */
private fun encodeBase64(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i + 2 < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = bytes[i + 1].toInt() and 0xFF
        val b2 = bytes[i + 2].toInt() and 0xFF
        sb.append(alphabet[b0 shr 2])
        sb.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
        sb.append(alphabet[((b1 and 0x0F) shl 2) or (b2 shr 6)])
        sb.append(alphabet[b2 and 0x3F])
        i += 3
    }
    when (bytes.size - i) {
        1 -> {
            val b0 = bytes[i].toInt() and 0xFF
            sb.append(alphabet[b0 shr 2])
            sb.append(alphabet[(b0 and 0x03) shl 4])
            sb.append("==")
        }
        2 -> {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            sb.append(alphabet[b0 shr 2])
            sb.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
            sb.append(alphabet[(b1 and 0x0F) shl 2])
            sb.append("=")
        }
    }
    return sb.toString()
}

// --- JS bridge ------------------------------------------------------------------------------

private fun startPlayback(
    sessionId: Int,
    base64Bytes: String,
    mimeType: String,
    onProgress: (fraction: Float) -> Unit,
    onFinish: () -> Unit,
) {
    startPlaybackJs(sessionId, base64Bytes, mimeType, onProgress, onFinish)
}

private fun startPlaybackJs(
    sessionId: Int,
    base64Bytes: String,
    mimeType: String,
    onProgress: (fraction: Float) -> Unit,
    onFinish: () -> Unit,
): Unit = js(
    """
    {
        var table = (globalThis.__voiceMessageSamplePlayers = globalThis.__voiceMessageSamplePlayers || {});
        // Stop any prior session belonging to the same player slot, just in case stop() didn't run.
        try {
            var binary = atob(base64Bytes);
            var len = binary.length;
            var u8 = new Uint8Array(len);
            for (var i = 0; i < len; i++) u8[i] = binary.charCodeAt(i);
            var blob = new Blob([u8], { type: mimeType });
            var url = URL.createObjectURL(blob);
            var audio = new Audio(url);
            audio.preload = 'auto';
            var lastReport = -1;
            var progressTimer = setInterval(function() {
                if (!audio || isNaN(audio.duration) || audio.duration <= 0) return;
                var f = audio.currentTime / audio.duration;
                if (Math.abs(f - lastReport) > 0.005) {
                    lastReport = f;
                    onProgress(f);
                }
            }, 33);
            audio.addEventListener('ended', function() {
                clearInterval(progressTimer);
                URL.revokeObjectURL(url);
                delete table[sessionId];
                onFinish();
            }, { once: true });
            audio.addEventListener('error', function() {
                clearInterval(progressTimer);
                URL.revokeObjectURL(url);
                delete table[sessionId];
                onFinish();
            }, { once: true });
            table[sessionId] = { audio: audio, url: url, timer: progressTimer };
            audio.play().catch(function() {
                clearInterval(progressTimer);
                URL.revokeObjectURL(url);
                delete table[sessionId];
                onFinish();
            });
        } catch (e) {
            onFinish();
        }
    }
    """
)

private fun stopPlaybackJs(sessionId: Int): Unit = js(
    """
    {
        var table = globalThis.__voiceMessageSamplePlayers;
        if (!table) return;
        var s = table[sessionId];
        if (!s) return;
        try { clearInterval(s.timer); } catch (_) {}
        try { s.audio.pause(); s.audio.src = ''; } catch (_) {}
        try { URL.revokeObjectURL(s.url); } catch (_) {}
        delete table[sessionId];
    }
    """
)

private fun seekPlaybackJs(sessionId: Int, fraction: Float): Unit = js(
    """
    {
        var table = globalThis.__voiceMessageSamplePlayers;
        if (!table) return;
        var s = table[sessionId];
        if (!s || !s.audio || isNaN(s.audio.duration)) return;
        s.audio.currentTime = fraction * s.audio.duration;
    }
    """
)
