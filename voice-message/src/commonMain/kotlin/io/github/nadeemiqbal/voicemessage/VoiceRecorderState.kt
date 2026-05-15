package io.github.nadeemiqbal.voicemessage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Owns the [VoicePhase] state machine and the live recording payload (elapsed time + amplitude
 * samples). Constructed via [rememberVoiceRecorderState] and passed to [VoiceRecorderInput].
 *
 * The state object does **not** capture audio — the library is BYO-audio. Consumers wire up
 * platform audio in three callbacks:
 *
 * - [onStart] — called when recording begins (long-press fires). Open `MediaRecorder` /
 *   `AVAudioRecorder` / Web Audio worklet here.
 * - [onSend] — called once when recording ends successfully, with the elapsed duration and the
 *   list of pushed amplitudes. Close the recorder, encode the file, ship it.
 * - [onCancel] — called when recording is discarded (slide-to-cancel, force-cancel). Close and
 *   delete any in-progress recording.
 *
 * While recording is in progress, the caller pumps amplitude readings in via [pushAmplitude]
 * (one value per ~30–60 Hz mic sample); these drive the live waveform and are the same list
 * delivered to [onSend].
 */
class VoiceRecorderState internal constructor(
    private val onStart: () -> Unit,
    private val onCancel: () -> Unit,
    private val onSend: (Duration, List<Float>) -> Unit,
    private val minDuration: Duration,
    private val maxDuration: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    // --- Public state ----------------------------------------------------------------------

    private var phaseState: VoicePhase by mutableStateOf(VoicePhase.Idle)

    /** The current phase of the gesture state machine. */
    val phase: VoicePhase get() = phaseState

    private val samplesState = mutableStateListOf<Float>()

    /** The amplitude samples ([pushAmplitude] values) captured since the current recording began. */
    val capturedSamples: List<Float> get() = samplesState

    private var elapsedState: Duration by mutableStateOf(Duration.ZERO)

    /** How long the user has been holding the mic for the current recording. */
    val elapsed: Duration get() = elapsedState

    private var startMark: TimeMark? = null

    // --- Caller-driven transitions ---------------------------------------------------------

    /**
     * Begin a recording. Idempotent while already in a recording phase (no-op). Resets samples
     * and elapsed time; fires [onStart] exactly once per recording.
     */
    fun start() {
        if (phaseState == VoicePhase.RecordingHeld ||
            phaseState == VoicePhase.RecordingLocked ||
            phaseState == VoicePhase.Cancelling
        ) return
        samplesState.clear()
        elapsedState = Duration.ZERO
        startMark = timeSource.markNow()
        phaseState = VoicePhase.RecordingHeld
        onStart()
    }

    /** Push an amplitude reading (`0f..1f`) while recording. Ignored when idle/sent. */
    fun pushAmplitude(value: Float) {
        if (isRecording()) {
            samplesState.add(value.coerceIn(0f, 1f))
        }
    }

    /**
     * Advance the elapsed counter from the monotonic time source. Call this from a ticker
     * `LaunchedEffect` while recording; the FSM auto-finishes (Send) when [maxDuration] is hit.
     */
    fun tick() {
        val mark = startMark ?: return
        if (!isRecording()) return
        val now = mark.elapsedNow()
        elapsedState = now
        if (now >= maxDuration) {
            triggerSend()
        }
    }

    /**
     * Report a drag offset from the initial press point — the recorder composable calls this from
     * its pointer-input gesture detector on every drag delta. The FSM picks between
     * [VoicePhase.RecordingHeld], [VoicePhase.RecordingLocked] and [VoicePhase.Cancelling].
     */
    fun updateDrag(
        dragX: Float,
        dragY: Float,
        lockThresholdPx: Float,
        cancelThresholdPx: Float,
    ) {
        val next = phaseFromDrag(phaseState, dragX, dragY, lockThresholdPx, cancelThresholdPx)
        if (next != phaseState) phaseState = next
    }

    /**
     * The user lifted their finger from the mic. Resolves to Send / Cancel / TooShort / Locked
     * depending on the current phase and elapsed time.
     */
    fun release() {
        when (phaseOnRelease(phaseState, elapsedState.inWholeMilliseconds, minDuration.inWholeMilliseconds)) {
            ReleaseOutcome.Send -> triggerSend()
            ReleaseOutcome.Cancel -> triggerCancel()
            ReleaseOutcome.TooShort -> resetToIdle()
            ReleaseOutcome.Locked -> { /* stay locked */ }
        }
    }

    /** Send button tapped in [VoicePhase.RecordingLocked]. No-op in any other phase. */
    fun sendFromLock() {
        if (phaseState == VoicePhase.RecordingLocked) triggerSend()
    }

    /** Cancel button tapped in [VoicePhase.RecordingLocked]. No-op in any other phase. */
    fun cancelFromLock() {
        if (phaseState == VoicePhase.RecordingLocked) triggerCancel()
    }

    /**
     * Discard any in-progress recording — useful when the system interrupts (incoming call,
     * screen recording) or the parent screen is being torn down.
     */
    fun forceCancel() {
        if (isRecording()) triggerCancel()
    }

    // --- Internals -------------------------------------------------------------------------

    private fun isRecording(): Boolean = phaseState == VoicePhase.RecordingHeld ||
        phaseState == VoicePhase.RecordingLocked ||
        phaseState == VoicePhase.Cancelling

    private fun triggerSend() {
        val captured = samplesState.toList()
        val dur = elapsedState
        samplesState.clear()
        elapsedState = Duration.ZERO
        startMark = null
        // Briefly surface Sent so consumers can observe the delivery; next start() resets to RecordingHeld.
        phaseState = VoicePhase.Sent
        onSend(dur, captured)
    }

    private fun triggerCancel() {
        resetToIdle()
        onCancel()
    }

    private fun resetToIdle() {
        samplesState.clear()
        elapsedState = Duration.ZERO
        startMark = null
        phaseState = VoicePhase.Idle
    }
}

/**
 * Creates and remembers a [VoiceRecorderState].
 *
 * @param onStart fires once when recording begins (long-press fires).
 * @param onCancel fires when the recording is discarded (cancel slide, force-cancel).
 * @param onSend fires when the recording is delivered, with the total elapsed duration and the
 *   list of amplitude samples the consumer fed into [VoiceRecorderState.pushAmplitude].
 * @param minDuration releases shorter than this are silently discarded (a tap, not a hold).
 * @param maxDuration upper bound on a single recording; auto-finishes with Send when reached.
 */
@Composable
fun rememberVoiceRecorderState(
    onStart: () -> Unit = {},
    onCancel: () -> Unit = {},
    onSend: (Duration, List<Float>) -> Unit,
    minDuration: Duration = VoiceMessageDefaults.MinDuration,
    maxDuration: Duration = VoiceMessageDefaults.MaxDuration,
): VoiceRecorderState = remember(minDuration, maxDuration) {
    VoiceRecorderState(
        onStart = onStart,
        onCancel = onCancel,
        onSend = onSend,
        minDuration = minDuration,
        maxDuration = maxDuration,
    )
}
