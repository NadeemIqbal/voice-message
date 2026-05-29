package io.github.nadeemiqbal.voicemessage.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import io.github.nadeemiqbal.voicemessage.VoiceMessageDefaults
import io.github.nadeemiqbal.voicemessage.VoicePhase
import io.github.nadeemiqbal.voicemessage.VoiceRecorderState
import io.github.nadeemiqbal.voicemessage.rememberVoiceRecorderState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Drop-in replacement for `rememberVoiceRecorderState` that wires platform mic capture in for
 * you. Use this when you don't want to plumb `MediaRecorder` / `AVAudioRecorder` / `JavaSound` /
 * Web `MediaRecorder` yourself.
 *
 * The returned [VoiceRecorderState] behaves identically to one from `rememberVoiceRecorderState`,
 * with three differences hidden inside:
 *
 * - Long-press → mic is opened, recording starts, amplitude flow begins.
 * - Slide-up + release / lock send → mic closes, [onSend] receives the [VoiceAudio] payload
 *   along with the captured amplitude samples that drove the live waveform.
 * - Slide-to-cancel / `forceCancel` → mic closes and any captured bytes are dropped.
 *   [onCancel] still fires for consumer cleanup (deleting upload drafts, etc.).
 *
 * @param onSend fired with the captured audio + amplitude samples once a recording completes.
 * @param onCancel optional consumer hook that fires after platform mic cleanup.
 * @param minDuration releases shorter than this are silently discarded (a tap, not a hold).
 * @param maxDuration upper bound on a single recording; auto-finishes with Send when reached.
 */
@Composable
fun rememberAudioBoundVoiceRecorderState(
    onSend: (audio: VoiceAudio, samples: List<Float>) -> Unit,
    onCancel: () -> Unit = {},
    minDuration: Duration = VoiceMessageDefaults.MinDuration,
    maxDuration: Duration = VoiceMessageDefaults.MaxDuration,
): VoiceRecorderState {
    val capture = rememberVoiceAudioCapture()
    val scope = rememberCoroutineScope()
    val state = rememberVoiceRecorderState(
        onStart = {
            scope.launch { capture.start() }
        },
        onCancel = {
            capture.cancel()
            onCancel()
        },
        onSend = { _, samples ->
            scope.launch {
                val audio = capture.stop()
                onSend(audio, samples)
            }
        },
        minDuration = minDuration,
        maxDuration = maxDuration,
    )

    // Forward the platform amplitude flow into the FSM. The collect is keyed to (state, capture)
    // so re-creation of either restarts the pipe.
    LaunchedEffect(state, capture) {
        capture.amplitudes.collect { amplitude ->
            state.pushAmplitude(amplitude)
        }
    }

    // Drive the elapsed-time counter (and the maxDuration auto-finish) while recording. Without
    // this, `state.elapsed` never advances unless the consumer also renders VoiceRecorderInput
    // (which has its own ticker). Custom composers built on VoiceMicButton + VoiceWaveform rely
    // on this ticker so their timer isn't stuck at 0:00.
    LaunchedEffect(state) {
        while (true) {
            if (state.phase != VoicePhase.Idle) state.tick()
            delay(33L) // ~30 Hz
        }
    }

    // Final safety: if the host composition leaves while a recording is in progress, drop the
    // mic so we don't leak the platform resource. The FSM's own state observation handles the
    // recorder-side cleanup; this just ensures the platform mic closes too.
    DisposableEffect(capture) {
        onDispose { capture.cancel() }
    }

    return state
}
