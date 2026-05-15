package io.github.nadeemiqbal.voicemessage

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Compose UI tests — Skiko-backed, run on Desktop and iOS targets.
 *
 * The gesture state machine itself is exhaustively covered by `VoiceMessageLogicTest` in
 * `commonTest`. These UI tests verify the **wiring** — that the right composables render for
 * each phase, the locked-state buttons fire the right state callbacks, and the bubble's
 * play / seek interactions reach their lambdas. The long-press → drag gesture is intentionally
 * driven programmatically (via the state's `start` / `updateDrag` / `release` API) because
 * `detectDragGesturesAfterLongPress`'s long-press timer doesn't advance reliably under
 * `runComposeUiTest` without elaborate clock control — the same FSM that the gesture detector
 * eventually calls is what these tests exercise.
 */
@OptIn(ExperimentalTestApi::class)
class VoiceMessageUiTest {

    // ---- VoiceMessageBubble ------------------------------------------------------------------

    @Test
    fun bubble_rendersAndPlayButtonFiresCallback() = runComposeUiTest {
        var toggled = 0
        setContent {
            MaterialTheme {
                VoiceMessageBubble(
                    samples = List(40) { 0.5f },
                    duration = 12.seconds,
                    isPlaying = false,
                    progress = 0f,
                    onPlayPauseToggle = { toggled++ },
                    onSeek = {},
                )
            }
        }
        onNodeWithTag("voice_message_bubble").assertIsDisplayed()
        onNodeWithTag("voice_play_button").performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun bubble_tapOnWaveformReportsFraction() = runComposeUiTest {
        var seeked: Float? = null
        setContent {
            MaterialTheme {
                VoiceMessageBubble(
                    samples = List(40) { 0.5f },
                    duration = 12.seconds,
                    isPlaying = false,
                    progress = 0f,
                    onPlayPauseToggle = {},
                    onSeek = { seeked = it },
                )
            }
        }
        onNodeWithTag("voice_message_waveform").performTouchInput {
            click(Offset(width / 2f, height / 2f))
        }
        val f = assertNotNull(seeked, "onSeek should have fired")
        assertTrue(f in 0.3f..0.7f, "centre tap should map to a fraction near 0.5, was $f")
    }

    @Test
    fun bubble_pauseIconShowsWhenPlaying() = runComposeUiTest {
        setContent {
            MaterialTheme {
                VoiceMessageBubble(
                    samples = List(40) { 0.5f },
                    duration = 12.seconds,
                    isPlaying = true,
                    progress = 0.5f,
                    onPlayPauseToggle = {},
                    onSeek = {},
                )
            }
        }
        onNodeWithTag("voice_pause_button").assertIsDisplayed()
    }

    // ---- VoiceRecorderInput — render-per-phase wiring ---------------------------------------

    @Test
    fun recorder_idle_showsMic() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val state = rememberVoiceRecorderState(onSend = { _, _ -> })
                VoiceRecorderInput(state = state)
            }
        }
        onNodeWithTag("voice_mic_button").assertIsDisplayed()
        onNodeWithTag("voice_recorder_input").assertIsDisplayed()
    }

    @Test
    fun recorder_recordingHeld_showsHintAndKeepsMic() = runComposeUiTest {
        lateinit var capturedState: VoiceRecorderState
        setContent {
            MaterialTheme {
                val state = rememberVoiceRecorderState(onSend = { _, _ -> })
                capturedState = state
                LaunchedEffect(Unit) { state.start() }
                VoiceRecorderInput(state = state)
            }
        }
        assertEquals(VoicePhase.RecordingHeld, capturedState.phase)
        onNodeWithTag("voice_mic_button").assertIsDisplayed()
        onNodeWithTag("voice_recorder_hint").assertIsDisplayed()
    }

    @Test
    fun recorder_locked_showsSendAndCancelButtons() = runComposeUiTest {
        lateinit var capturedState: VoiceRecorderState
        setContent {
            MaterialTheme {
                val state = rememberVoiceRecorderState(onSend = { _, _ -> })
                capturedState = state
                LaunchedEffect(Unit) {
                    state.start()
                    state.updateDrag(dragX = 0f, dragY = -200f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
                }
                VoiceRecorderInput(state = state)
            }
        }
        assertEquals(VoicePhase.RecordingLocked, capturedState.phase)
        onNodeWithTag("voice_send_button").assertIsDisplayed()
        onNodeWithTag("voice_cancel_button").assertIsDisplayed()
    }

    @Test
    fun recorder_locked_sendButton_firesOnSend() = runComposeUiTest {
        var sent: Pair<Duration, List<Float>>? = null
        setContent {
            MaterialTheme {
                val state = rememberVoiceRecorderState(onSend = { d, s -> sent = d to s })
                LaunchedEffect(Unit) {
                    state.start()
                    state.pushAmplitude(0.42f)
                    state.pushAmplitude(0.91f)
                    state.updateDrag(dragX = 0f, dragY = -200f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
                }
                VoiceRecorderInput(state = state)
            }
        }
        onNodeWithTag("voice_send_button").performClick()
        val (_, samples) = assertNotNull(sent, "send button should fire onSend with captured samples")
        assertEquals(listOf(0.42f, 0.91f), samples)
    }

    @Test
    fun recorder_locked_cancelButton_firesOnCancel() = runComposeUiTest {
        var cancelled = false
        lateinit var capturedState: VoiceRecorderState
        setContent {
            MaterialTheme {
                val state = rememberVoiceRecorderState(
                    onSend = { _, _ -> },
                    onCancel = { cancelled = true },
                )
                capturedState = state
                LaunchedEffect(Unit) {
                    state.start()
                    state.updateDrag(dragX = 0f, dragY = -200f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
                }
                VoiceRecorderInput(state = state)
            }
        }
        onNodeWithTag("voice_cancel_button").performClick()
        assertEquals(true, cancelled)
        assertEquals(VoicePhase.Idle, capturedState.phase)
    }

    @Test
    fun recorder_cancelling_showsRedHint() = runComposeUiTest {
        lateinit var capturedState: VoiceRecorderState
        setContent {
            MaterialTheme {
                val state = rememberVoiceRecorderState(onSend = { _, _ -> })
                capturedState = state
                LaunchedEffect(Unit) {
                    state.start()
                    state.updateDrag(dragX = -120f, dragY = 0f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
                }
                VoiceRecorderInput(state = state)
            }
        }
        assertEquals(VoicePhase.Cancelling, capturedState.phase)
        onNodeWithTag("voice_recorder_hint").assertIsDisplayed()
    }

    @Test
    fun recorder_forceCancel_returnsToIdle() = runComposeUiTest {
        var cancelled = false
        lateinit var capturedState: VoiceRecorderState
        setContent {
            MaterialTheme {
                val state = rememberVoiceRecorderState(
                    onSend = { _, _ -> },
                    onCancel = { cancelled = true },
                )
                capturedState = state
                LaunchedEffect(Unit) { state.start() }
                VoiceRecorderInput(state = state)
            }
        }
        assertEquals(VoicePhase.RecordingHeld, capturedState.phase)
        capturedState.forceCancel()
        assertEquals(VoicePhase.Idle, capturedState.phase)
        assertEquals(true, cancelled)
        onNodeWithTag("voice_mic_button").assertIsDisplayed()
    }

    @Test
    fun recorder_quickTapNeverStartsARecording() = runComposeUiTest {
        var sent: Pair<Duration, List<Float>>? = null
        lateinit var capturedState: VoiceRecorderState
        setContent {
            MaterialTheme {
                val state = rememberVoiceRecorderState(onSend = { d, s -> sent = d to s })
                capturedState = state
                VoiceRecorderInput(state = state)
            }
        }
        onNodeWithTag("voice_mic_button").performTouchInput { click(center) }
        assertNull(sent, "a quick tap (no long-press) must never reach onSend")
        assertEquals(VoicePhase.Idle, capturedState.phase)
    }
}
