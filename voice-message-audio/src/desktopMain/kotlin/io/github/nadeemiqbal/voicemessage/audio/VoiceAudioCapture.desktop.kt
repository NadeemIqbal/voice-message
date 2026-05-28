package io.github.nadeemiqbal.voicemessage.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

/**
 * Desktop (JVM) implementation backed by `javax.sound.sampled.TargetDataLine`. Records 16-bit
 * mono PCM at 44.1 kHz and wraps the captured bytes in a WAV header on stop so the result is
 * a self-contained file consumers can write to disk or upload directly.
 *
 * Amplitude readings come from RMS of each ~50 ms chunk of PCM samples, normalised to
 * `0f..1f`. This matches the visual punch of the live waveform on other platforms.
 */
private class DesktopVoiceAudioCapture(
    private val sampleRate: Int = 44_100,
    private val pollIntervalMs: Int = 50,
) : VoiceAudioCapture {

    private val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _amplitudes = MutableSharedFlow<Float>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val amplitudes: SharedFlow<Float> = _amplitudes

    private var line: TargetDataLine? = null
    private val pcmBuffer = ByteArrayOutputStream()
    private var captureJob: Job? = null
    private var startMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var elapsedNanos: Long = 0L

    override suspend fun start() {
        if (line != null) return // already recording
        pcmBuffer.reset()
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val targetLine = AudioSystem.getLine(info) as TargetDataLine
        targetLine.open(format)
        targetLine.start()
        line = targetLine
        startMark = TimeSource.Monotonic.markNow()

        captureJob = scope.launch {
            val frameSize = format.frameSize // 2 bytes for 16-bit mono
            val chunkBytes = (sampleRate * frameSize * pollIntervalMs / 1000).coerceAtLeast(frameSize)
            val buf = ByteArray(chunkBytes)
            while (line === targetLine && targetLine.isOpen) {
                val read = targetLine.read(buf, 0, buf.size)
                if (read <= 0) continue
                // Persist captured bytes for later WAV-wrap on stop().
                pcmBuffer.write(buf, 0, read)
                // RMS of this chunk as a 0..1 amplitude.
                _amplitudes.tryEmit(rmsAmplitude(buf, read))
            }
        }
    }

    override suspend fun stop(): VoiceAudio {
        elapsedNanos = startMark?.elapsedNow()?.inWholeNanoseconds ?: 0L
        startMark = null
        val l = line
        line = null
        captureJob?.cancel()
        captureJob = null
        l?.stop()
        l?.flush()
        l?.close()
        val pcm = pcmBuffer.toByteArray()
        val wav = wrapPcmInWavContainer(pcm, sampleRate, channels = 1, bitsPerSample = 16)
        pcmBuffer.reset()
        return VoiceAudio(
            bytes = wav,
            mimeType = "audio/wav",
            duration = elapsedNanos.nanoseconds,
            sampleRate = sampleRate,
        )
    }

    override fun cancel() {
        startMark = null
        val l = line
        line = null
        captureJob?.cancel()
        captureJob = null
        l?.stop()
        l?.flush()
        l?.close()
        pcmBuffer.reset()
    }

    fun dispose() {
        cancel()
        scope.cancel()
    }
}

/**
 * RMS of a 16-bit signed little-endian PCM buffer, normalised to `0f..1f` against Short.MAX_VALUE.
 */
private fun rmsAmplitude(buf: ByteArray, validBytes: Int): Float {
    var sumSquares = 0.0
    var count = 0
    var i = 0
    while (i + 1 < validBytes) {
        val low = buf[i].toInt() and 0xFF
        val high = buf[i + 1].toInt()
        val sample = ((high shl 8) or low).toShort().toInt()
        sumSquares += (sample * sample).toDouble()
        count++
        i += 2
    }
    if (count == 0) return 0f
    val rms = sqrt(sumSquares / count)
    return (rms / Short.MAX_VALUE.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Wraps raw 16-bit PCM bytes in a minimal RIFF/WAVE container so the returned ByteArray is a
 * playable .wav file. Standard 44-byte header followed by the data chunk.
 */
private fun wrapPcmInWavContainer(
    pcm: ByteArray,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val out = ByteArrayOutputStream(44 + pcm.size)
    val dos = DataOutputStream(out)
    // RIFF chunk descriptor
    dos.writeBytes("RIFF")
    dos.writeIntLittleEndian(36 + pcm.size)
    dos.writeBytes("WAVE")
    // fmt sub-chunk
    dos.writeBytes("fmt ")
    dos.writeIntLittleEndian(16)             // sub-chunk size = 16 for PCM
    dos.writeShortLittleEndian(1)            // AudioFormat = 1 (PCM)
    dos.writeShortLittleEndian(channels)
    dos.writeIntLittleEndian(sampleRate)
    dos.writeIntLittleEndian(byteRate)
    dos.writeShortLittleEndian(blockAlign)
    dos.writeShortLittleEndian(bitsPerSample)
    // data sub-chunk
    dos.writeBytes("data")
    dos.writeIntLittleEndian(pcm.size)
    dos.write(pcm)
    dos.flush()
    return out.toByteArray()
}

private fun DataOutputStream.writeIntLittleEndian(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

private fun DataOutputStream.writeShortLittleEndian(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}

@Composable
actual fun rememberVoiceAudioCapture(): VoiceAudioCapture {
    val capture = remember { DesktopVoiceAudioCapture() }
    androidx.compose.runtime.DisposableEffect(capture) {
        onDispose { capture.dispose() }
    }
    return capture
}
