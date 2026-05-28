package io.github.nadeemiqbal.voicemessage.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Android implementation backed by `MediaRecorder` writing AAC inside an MPEG-4 container.
 * Polls `maxAmplitude` at the [pollIntervalMs] rate to drive the live amplitude flow.
 *
 * Note: requires the consumer to hold the `RECORD_AUDIO` runtime permission BEFORE calling
 * [start]. This adapter does not request the permission itself; pair it with your usual
 * `ActivityCompat.requestPermissions` flow.
 */
private class AndroidVoiceAudioCapture(
    private val outputFile: File,
    private val pollIntervalMs: Long = 50,
) : VoiceAudioCapture {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _amplitudes = MutableSharedFlow<Float>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val amplitudes: SharedFlow<Float> = _amplitudes

    private var recorder: MediaRecorder? = null
    private var pollerJob: Job? = null
    private var startMark: TimeSource.Monotonic.ValueTimeMark? = null

    @Suppress("DEPRECATION") // The non-Context constructor is deprecated since API 31, but we
                              // intentionally keep it to avoid a hard Context dependency in the
                              // adapter API. Callers on API 31+ can pass their own Context-aware
                              // MediaRecorder via a custom adapter if they need the new behaviour.
    override suspend fun start() {
        if (recorder != null) return
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder().also { /* still safe; deprecation is non-fatal */ }
        } else {
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setOutputFile(outputFile.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        startMark = TimeSource.Monotonic.markNow()
        pollerJob = scope.launch {
            while (recorder === r) {
                val peak = try { r.maxAmplitude / 32_767f } catch (_: Throwable) { 0f }
                _amplitudes.tryEmit(peak.coerceIn(0f, 1f))
                delay(pollIntervalMs.milliseconds)
            }
        }
    }

    override suspend fun stop(): VoiceAudio {
        val r = recorder ?: return emptyAudio()
        recorder = null
        pollerJob?.cancel()
        pollerJob = null
        val elapsed = startMark?.elapsedNow() ?: 0.milliseconds
        startMark = null
        try { r.stop() } catch (_: Throwable) { /* ignore */ }
        r.release()
        val bytes = outputFile.takeIf { it.exists() }?.readBytes() ?: ByteArray(0)
        outputFile.delete()
        return VoiceAudio(
            bytes = bytes,
            mimeType = "audio/mp4",
            duration = elapsed,
            sampleRate = 44_100,
        )
    }

    override fun cancel() {
        val r = recorder
        recorder = null
        pollerJob?.cancel()
        pollerJob = null
        startMark = null
        try { r?.stop() } catch (_: Throwable) { /* ignore */ }
        r?.release()
        outputFile.delete()
    }

    fun dispose() {
        cancel()
        scope.cancel()
    }

    private fun emptyAudio(): VoiceAudio = VoiceAudio(
        bytes = ByteArray(0),
        mimeType = "audio/mp4",
        duration = 0.milliseconds,
        sampleRate = 0,
    )
}

@Composable
actual fun rememberVoiceAudioCapture(): VoiceAudioCapture {
    val context = LocalContext.current
    val capture = remember(context) {
        val tempFile = File.createTempFile("voice-message-", ".m4a", context.cacheDir)
        AndroidVoiceAudioCapture(outputFile = tempFile)
    }
    DisposableEffect(capture) {
        onDispose { capture.dispose() }
    }
    return capture
}
