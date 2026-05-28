package io.github.nadeemiqbal.voicemessage.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
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
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVAudioQualityHigh
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * iOS implementation backed by `AVAudioRecorder` recording AAC inside an MPEG-4 container.
 * Polls `averagePower(forChannel:)` at the [pollIntervalMs] rate, normalising the dBFS value
 * into a `0f..1f` amplitude that mirrors the visual range used on every other platform.
 *
 * Note: requires the consumer to provide an `NSMicrophoneUsageDescription` in the host app's
 * Info.plist. The iOS runtime will show the permission prompt automatically on the first
 * `start()` call.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosVoiceAudioCapture(
    private val pollIntervalMs: Long = 50,
) : VoiceAudioCapture {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _amplitudes = MutableSharedFlow<Float>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val amplitudes: SharedFlow<Float> = _amplitudes

    private var recorder: AVAudioRecorder? = null
    private var outputUrl: NSURL? = null
    private var pollerJob: Job? = null
    private var startMark: TimeSource.Monotonic.ValueTimeMark? = null

    override suspend fun start() {
        if (recorder != null) return
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, null)
        // setActive is implicit: AVAudioRecorder.record() activates the session for us.
        // Calling setActive(_:error:) from Kotlin/Native trips a binding-name mismatch, and
        // skipping it is the documented modern pattern (the category-set call above is enough).

        val tempPath = NSTemporaryDirectory() + "voice-message-${platform.Foundation.NSUUID().UUIDString}.m4a"
        val url = NSURL.fileURLWithPath(tempPath)
        outputUrl = url

        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC.toUInt()),
            AVEncoderAudioQualityKey to NSNumber(int = AVAudioQualityHigh.toInt()),
        )
        val r = AVAudioRecorder(uRL = url, settings = settings, error = null)
        r.meteringEnabled = true
        r.prepareToRecord()
        r.record()
        recorder = r
        startMark = TimeSource.Monotonic.markNow()

        pollerJob = scope.launch {
            while (recorder === r) {
                r.updateMeters()
                val avgPower = r.averagePowerForChannel(0u) // dBFS, -160..0
                val normalised = ((avgPower + 60f) / 60f).coerceIn(0f, 1f)
                _amplitudes.tryEmit(normalised)
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
        r.stop()
        val url = outputUrl
        outputUrl = null
        val bytes = url?.let { nsUrlToByteArray(it) } ?: ByteArray(0)
        url?.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        // Session deactivation is implicit on AVAudioRecorder release; explicit setActive(false)
        // is omitted to avoid the same binding-name issue noted in start().
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
        r?.stop()
        outputUrl?.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        outputUrl = null
        // Session deactivation is implicit on AVAudioRecorder release; explicit setActive(false)
        // is omitted to avoid the same binding-name issue noted in start().
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

    private fun nsUrlToByteArray(url: NSURL): ByteArray {
        val data: NSData = NSData.dataWithContentsOfURL(url) ?: return ByteArray(0)
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val bytes = ByteArray(length)
        memcpy(bytes.refTo(0), data.bytes, data.length)
        return bytes
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberVoiceAudioCapture(): VoiceAudioCapture {
    val capture = remember { IosVoiceAudioCapture() }
    DisposableEffect(capture) {
        onDispose { capture.dispose() }
    }
    return capture
}
