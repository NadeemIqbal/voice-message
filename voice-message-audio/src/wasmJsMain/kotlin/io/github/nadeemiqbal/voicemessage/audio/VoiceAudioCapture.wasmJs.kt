@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.nadeemiqbal.voicemessage.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.js.JsAny
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Wasm/JS implementation backed by the browser `MediaRecorder` API plus an `AnalyserNode` for
 * live amplitude readings. Records WebM/Opus by default (Chrome / Firefox / Edge) and falls
 * back to whatever `MediaRecorder.isTypeSupported` accepts in browsers without WebM support.
 *
 * Permission: the browser shows the native mic-permission prompt the first time
 * `getUserMedia({audio:true})` is called. Subsequent recordings reuse the granted permission.
 */
private class WasmVoiceAudioCapture(
    private val pollIntervalMs: Long = 50,
) : VoiceAudioCapture {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _amplitudes = MutableSharedFlow<Float>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val amplitudes: SharedFlow<Float> = _amplitudes

    private var session: WasmRecordingSession? = null
    private var pollerJob: Job? = null
    private var startMark: TimeSource.Monotonic.ValueTimeMark? = null

    override suspend fun start() {
        if (session != null) return
        val s = startRecordingSession()
        session = s
        startMark = TimeSource.Monotonic.markNow()
        pollerJob = scope.launch {
            while (session === s && s.isRecording()) {
                val amp = s.readAmplitude()
                _amplitudes.tryEmit(amp.coerceIn(0f, 1f))
                delay(pollIntervalMs.milliseconds)
            }
        }
    }

    override suspend fun stop(): VoiceAudio {
        val s = session ?: return emptyAudio()
        session = null
        pollerJob?.cancel()
        pollerJob = null
        val elapsed = startMark?.elapsedNow() ?: 0.milliseconds
        startMark = null
        val payload = s.stopAndExtractAudio()
        return VoiceAudio(
            bytes = payload.bytes,
            mimeType = payload.mimeType,
            duration = elapsed,
            sampleRate = payload.sampleRate,
        )
    }

    override fun cancel() {
        val s = session
        session = null
        pollerJob?.cancel()
        pollerJob = null
        startMark = null
        s?.cancel()
    }

    fun dispose() {
        cancel()
        scope.cancel()
    }

    private fun emptyAudio(): VoiceAudio = VoiceAudio(
        bytes = ByteArray(0),
        mimeType = "audio/webm",
        duration = 0.milliseconds,
        sampleRate = 0,
    )
}

/**
 * Awaits the browser permission prompt, opens an `AudioContext` + `MediaRecorder`, and returns
 * a session handle. Errors surface as exceptions that propagate to the calling coroutine.
 */
private suspend fun startRecordingSession(): WasmRecordingSession {
    val deferred = CompletableDeferred<WasmRecordingSession>()
    startRecordingSessionJs { sessionRef, errorMessage ->
        if (errorMessage != null) {
            deferred.completeExceptionally(IllegalStateException(errorMessage))
        } else {
            deferred.complete(WasmRecordingSession(sessionRef!!))
        }
    }
    return deferred.await()
}

/**
 * Opaque handle to the JS-side recording session. The bridge functions below operate against
 * it via numeric IDs to keep the Wasm-JS interop boundary simple (no JsAny field types).
 */
private class WasmRecordingSession(val id: Int) {
    fun isRecording(): Boolean = sessionIsRecordingJs(id)
    fun readAmplitude(): Float = sessionReadAmplitudeJs(id)
    suspend fun stopAndExtractAudio(): WasmAudioPayload {
        val deferred = CompletableDeferred<WasmAudioPayload>()
        // ByteArray cannot cross the Wasm-JS interop boundary directly, so we ferry the
        // recorded bytes as base64 and decode them on the Kotlin side. For voice-message-sized
        // payloads (sub-megabyte) the base64 overhead is negligible.
        sessionStopJs(id) { base64Bytes, mimeType, sampleRate ->
            val decoded = base64Bytes?.let { decodeBase64ToByteArray(it) } ?: ByteArray(0)
            deferred.complete(WasmAudioPayload(decoded, mimeType ?: "audio/webm", sampleRate))
        }
        return deferred.await()
    }

    fun cancel() {
        sessionCancelJs(id)
    }
}

/**
 * Decodes a standard base64 string into a ByteArray without padding sensitivity. Wasm-JS doesn't
 * give us `java.util.Base64`, so we hand-roll the alphabet lookup. Fast enough for the byte sizes
 * a voice message carries.
 */
private fun decodeBase64ToByteArray(base64: String): ByteArray {
    val cleaned = base64.replace("=", "")
    val out = ByteArray(cleaned.length * 3 / 4)
    var outIndex = 0
    var buffer = 0
    var bitsHeld = 0
    for (ch in cleaned) {
        val value = base64Value(ch)
        if (value < 0) continue
        buffer = (buffer shl 6) or value
        bitsHeld += 6
        if (bitsHeld >= 8) {
            bitsHeld -= 8
            out[outIndex++] = ((buffer ushr bitsHeld) and 0xFF).toByte()
        }
    }
    return if (outIndex == out.size) out else out.copyOf(outIndex)
}

private fun base64Value(ch: Char): Int = when (ch) {
    in 'A'..'Z' -> ch.code - 'A'.code
    in 'a'..'z' -> ch.code - 'a'.code + 26
    in '0'..'9' -> ch.code - '0'.code + 52
    '+', '-' -> 62 // accept URL-safe alphabet too
    '/', '_' -> 63
    else -> -1
}

private class WasmAudioPayload(val bytes: ByteArray, val mimeType: String, val sampleRate: Int)

// --- JS interop ---------------------------------------------------------------------------
// We keep all JS state in a global table (`window.__voiceMessageAudioSessions`) keyed by an
// incrementing integer ID, so the Kotlin/Wasm side only has to pass numbers across the boundary.

private fun startRecordingSessionJs(
    callback: (sessionId: Int?, errorMessage: String?) -> Unit,
): Unit = js(
    """
    (function() {
        if (!navigator || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            callback(null, 'navigator.mediaDevices.getUserMedia not available');
            return;
        }
        var table = (globalThis.__voiceMessageAudioSessions = globalThis.__voiceMessageAudioSessions || { next: 1, map: {} });
        var id = table.next++;
        navigator.mediaDevices.getUserMedia({ audio: true })
            .then(function(stream) {
                try {
                    var AudioCtx = window.AudioContext || window.webkitAudioContext;
                    var ctx = new AudioCtx();
                    var source = ctx.createMediaStreamSource(stream);
                    var analyser = ctx.createAnalyser();
                    analyser.fftSize = 256;
                    source.connect(analyser);
                    var preferredMime = 'audio/webm;codecs=opus';
                    if (typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported && !MediaRecorder.isTypeSupported(preferredMime)) {
                        preferredMime = 'audio/webm';
                        if (!MediaRecorder.isTypeSupported(preferredMime)) {
                            preferredMime = '';
                        }
                    }
                    var recorder = preferredMime
                        ? new MediaRecorder(stream, { mimeType: preferredMime })
                        : new MediaRecorder(stream);
                    var chunks = [];
                    recorder.ondataavailable = function(e) { if (e.data && e.data.size > 0) chunks.push(e.data); };
                    var session = {
                        stream: stream,
                        ctx: ctx,
                        analyser: analyser,
                        recorder: recorder,
                        chunks: chunks,
                        buffer: new Uint8Array(analyser.fftSize),
                        recording: true,
                        mimeType: recorder.mimeType || preferredMime || 'audio/webm',
                        sampleRate: ctx.sampleRate
                    };
                    table.map[id] = session;
                    recorder.start(100);
                    callback(id, null);
                } catch (e) {
                    callback(null, e && e.message ? e.message : 'recording session failed');
                }
            })
            .catch(function(err) {
                callback(null, err && err.message ? err.message : 'getUserMedia rejected');
            });
    })()
    """
)

private fun sessionIsRecordingJs(id: Int): Boolean = js(
    """
    (function() {
        var table = globalThis.__voiceMessageAudioSessions;
        if (!table) return false;
        var s = table.map[id];
        return !!(s && s.recording);
    })()
    """
)

private fun sessionReadAmplitudeJs(id: Int): Float = js(
    """
    (function() {
        var table = globalThis.__voiceMessageAudioSessions;
        if (!table) return 0;
        var s = table.map[id];
        if (!s || !s.analyser) return 0;
        s.analyser.getByteTimeDomainData(s.buffer);
        var sumSq = 0;
        for (var i = 0; i < s.buffer.length; i++) {
            var v = (s.buffer[i] - 128) / 128.0;
            sumSq += v * v;
        }
        return Math.sqrt(sumSq / s.buffer.length);
    })()
    """
)

private fun sessionStopJs(
    id: Int,
    callback: (base64Bytes: String?, mimeType: String?, sampleRate: Int) -> Unit,
): Unit = js(
    """
    (function() {
        var table = globalThis.__voiceMessageAudioSessions;
        if (!table) { callback(null, null, 0); return; }
        var s = table.map[id];
        if (!s) { callback(null, null, 0); return; }
        s.recording = false;
        var encodeBase64 = function(u8) {
            // Chunked btoa to avoid arg-count limits on huge arrays.
            var chunk = 0x8000;
            var parts = [];
            for (var off = 0; off < u8.length; off += chunk) {
                parts.push(String.fromCharCode.apply(null, u8.subarray(off, off + chunk)));
            }
            return btoa(parts.join(''));
        };
        var finalize = function() {
            try {
                var blob = new Blob(s.chunks, { type: s.mimeType });
                blob.arrayBuffer().then(function(ab) {
                    var u8 = new Uint8Array(ab);
                    var b64 = encodeBase64(u8);
                    try { s.stream.getTracks().forEach(function(t) { t.stop(); }); } catch (_) {}
                    try { s.ctx.close(); } catch (_) {}
                    delete table.map[id];
                    callback(b64, s.mimeType, s.sampleRate | 0);
                }).catch(function() {
                    callback(null, s.mimeType, s.sampleRate | 0);
                });
            } catch (_) {
                callback(null, null, 0);
            }
        };
        if (s.recorder.state !== 'inactive') {
            s.recorder.addEventListener('stop', finalize, { once: true });
            s.recorder.stop();
        } else {
            finalize();
        }
    })()
    """
)

private fun sessionCancelJs(id: Int): Unit = js(
    """
    (function() {
        var table = globalThis.__voiceMessageAudioSessions;
        if (!table) return;
        var s = table.map[id];
        if (!s) return;
        s.recording = false;
        try { s.recorder.stop(); } catch (_) {}
        try { s.stream.getTracks().forEach(function(t) { t.stop(); }); } catch (_) {}
        try { s.ctx.close(); } catch (_) {}
        delete table.map[id];
    })()
    """
)

@Composable
actual fun rememberVoiceAudioCapture(): VoiceAudioCapture {
    val capture = remember { WasmVoiceAudioCapture() }
    DisposableEffect(capture) {
        onDispose { capture.dispose() }
    }
    return capture
}
