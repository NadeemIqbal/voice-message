package io.github.nadeemiqbal.voicemessage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Pure-logic tests — no composition required. Run on every target including Android unit tests. */
class VoiceMessageLogicTest {

    // ---- downsampleAmplitudes -----------------------------------------------------------------

    @Test
    fun downsample_emptySamples_yieldsZeroFilledTarget() {
        val out = downsampleAmplitudes(emptyList(), 8)
        assertEquals(8, out.size)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun downsample_shorterThanTarget_padsLeftWithZeros() {
        val out = downsampleAmplitudes(listOf(0.4f, 0.8f, 0.2f), 6)
        assertEquals(6, out.size)
        assertEquals(listOf(0f, 0f, 0f, 0.4f, 0.8f, 0.2f), out)
    }

    @Test
    fun downsample_equalToTarget_isIdentityClampedTo0to1() {
        val out = downsampleAmplitudes(listOf(0f, 0.5f, 1f, 1.4f, -0.1f), 5)
        assertEquals(listOf(0f, 0.5f, 1f, 1f, 0f), out)
    }

    @Test
    fun downsample_longerThanTarget_takesPeakOfEachBucket() {
        val samples = List(40) { i -> if (i % 5 == 0) 0.9f else 0.1f }
        val out = downsampleAmplitudes(samples, 8)
        assertEquals(8, out.size)
        // Each bucket of width 5 should hit at least one 0.9 — every bar should peak.
        out.forEach { assertEquals(0.9f, it, "bucket should pick the peak (0.9), got $it") }
    }

    @Test
    fun downsample_rejectsNonPositiveTargetCount() {
        assertFailsWith<IllegalArgumentException> { downsampleAmplitudes(listOf(0.5f), 0) }
        assertFailsWith<IllegalArgumentException> { downsampleAmplitudes(listOf(0.5f), -1) }
    }

    // ---- downsampleAmplitudes: WaveformMode.Live (B1 regression: scrolling waveform) -------

    @Test
    fun downsample_liveMode_shorterThanTarget_padsLeftWithZeros() {
        val out = downsampleAmplitudes(listOf(0.4f, 0.8f, 0.2f), 6, WaveformMode.Live)
        assertEquals(6, out.size)
        // Below targetCount, Live and Static behave identically: grow from the right.
        assertEquals(listOf(0f, 0f, 0f, 0.4f, 0.8f, 0.2f), out)
    }

    @Test
    fun downsample_liveMode_longerThanTarget_keepsOnlyLastTargetCount() {
        // 10 samples, want 4. Live mode should return samples[6..9] without averaging.
        val samples = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f)
        val out = downsampleAmplitudes(samples, 4, WaveformMode.Live)
        assertEquals(listOf(0.7f, 0.8f, 0.9f, 1.0f), out)
    }

    @Test
    fun downsample_liveMode_equalToTarget_isIdentityClampedTo0to1() {
        // size == targetCount uses the shared <= branch; clamps out-of-range values.
        val out = downsampleAmplitudes(listOf(0f, 0.5f, 1f, 1.4f, -0.1f), 5, WaveformMode.Live)
        assertEquals(listOf(0f, 0.5f, 1f, 1f, 0f), out)
    }

    @Test
    fun downsample_staticMode_regressionGuardOnLongSamples() {
        // Static mode (default) should still take peak-of-bucket on overflow, unchanged
        // from v0.1.0 behavior. Guard against accidental future regression.
        val samples = List(40) { i -> if (i % 5 == 0) 0.9f else 0.1f }
        val outDefault = downsampleAmplitudes(samples, 8)
        val outExplicit = downsampleAmplitudes(samples, 8, WaveformMode.Static)
        assertEquals(outExplicit, outDefault)
        outDefault.forEach { assertEquals(0.9f, it) }
    }

    // ---- Haptic emission (G2) --------------------------------------------------------------

    @Test
    fun state_emitsHapticsOnEveryPhaseTransition() {
        val time = TestTimeSource()
        val emitted = mutableListOf<VoiceHaptic>()
        val state = newState(time = time, onHaptic = { emitted.add(it) })

        // Start -> RecordingHeld: VoiceHaptic.Start
        state.start()
        // Cross-cancel: RecordingHeld -> Cancelling: VoiceHaptic.CrossCancel
        state.updateDrag(dragX = -200f, dragY = 0f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
        // Drift back below cancel threshold: Cancelling -> RecordingHeld (no haptic).
        state.updateDrag(dragX = 0f, dragY = 0f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
        // Cross lock threshold: RecordingHeld -> RecordingLocked: VoiceHaptic.Lock
        state.updateDrag(dragX = 0f, dragY = -200f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
        // Send from lock: VoiceHaptic.Send
        time += 1.seconds
        state.tick()
        state.sendFromLock()
        assertEquals(
            listOf(VoiceHaptic.Start, VoiceHaptic.CrossCancel, VoiceHaptic.Lock, VoiceHaptic.Send),
            emitted,
        )
    }

    @Test
    fun state_emitsCancelHapticOnForceCancel() {
        val time = TestTimeSource()
        val emitted = mutableListOf<VoiceHaptic>()
        val state = newState(time = time, onHaptic = { emitted.add(it) })
        state.start()
        emitted.clear() // discard the Start tick
        state.forceCancel()
        assertEquals(listOf(VoiceHaptic.Cancel), emitted)
    }

    // ---- phaseFromDrag ------------------------------------------------------------------------

    @Test
    fun phaseFromDrag_atOrigin_keepsHeld() {
        val next = phaseFromDrag(VoicePhase.RecordingHeld, dragX = 0f, dragY = 0f, 80f, 80f)
        assertEquals(VoicePhase.RecordingHeld, next)
    }

    @Test
    fun phaseFromDrag_slightlyBelowLockThreshold_keepsHeld() {
        val next = phaseFromDrag(VoicePhase.RecordingHeld, dragX = 0f, dragY = -79f, 80f, 80f)
        assertEquals(VoicePhase.RecordingHeld, next)
    }

    @Test
    fun phaseFromDrag_atLockThreshold_locks() {
        val next = phaseFromDrag(VoicePhase.RecordingHeld, dragX = 0f, dragY = -80f, 80f, 80f)
        assertEquals(VoicePhase.RecordingLocked, next)
    }

    @Test
    fun phaseFromDrag_slidLeftPastCancelThreshold_cancels() {
        val next = phaseFromDrag(VoicePhase.RecordingHeld, dragX = -85f, dragY = 0f, 80f, 80f)
        assertEquals(VoicePhase.Cancelling, next)
    }

    @Test
    fun phaseFromDrag_lockTakesPrecedenceOverCancel() {
        // Slid up AND left past both thresholds — lock wins (Apple's behaviour too).
        val next = phaseFromDrag(VoicePhase.RecordingHeld, dragX = -120f, dragY = -120f, 80f, 80f)
        assertEquals(VoicePhase.RecordingLocked, next)
    }

    @Test
    fun phaseFromDrag_returnFromCancellingResumesHeld() {
        val next = phaseFromDrag(VoicePhase.Cancelling, dragX = -30f, dragY = 0f, 80f, 80f)
        assertEquals(VoicePhase.RecordingHeld, next)
    }

    @Test
    fun phaseFromDrag_inIdleIsNoOp() {
        val next = phaseFromDrag(VoicePhase.Idle, dragX = -200f, dragY = -200f, 80f, 80f)
        assertEquals(VoicePhase.Idle, next)
    }

    @Test
    fun phaseFromDrag_inLockedIsNoOp() {
        val next = phaseFromDrag(VoicePhase.RecordingLocked, dragX = -200f, dragY = -200f, 80f, 80f)
        assertEquals(VoicePhase.RecordingLocked, next)
    }

    // ---- phaseOnRelease -----------------------------------------------------------------------

    @Test
    fun release_fromHeldAboveMinDuration_sends() {
        val outcome = phaseOnRelease(VoicePhase.RecordingHeld, elapsedMillis = 600, minDurationMillis = 500)
        assertEquals(ReleaseOutcome.Send, outcome)
    }

    @Test
    fun release_fromHeldBelowMinDuration_isTooShort() {
        val outcome = phaseOnRelease(VoicePhase.RecordingHeld, elapsedMillis = 200, minDurationMillis = 500)
        assertEquals(ReleaseOutcome.TooShort, outcome)
    }

    @Test
    fun release_fromCancelling_cancels() {
        val outcome = phaseOnRelease(VoicePhase.Cancelling, elapsedMillis = 1000, minDurationMillis = 500)
        assertEquals(ReleaseOutcome.Cancel, outcome)
    }

    @Test
    fun release_fromLocked_staysLocked() {
        val outcome = phaseOnRelease(VoicePhase.RecordingLocked, elapsedMillis = 1000, minDurationMillis = 500)
        assertEquals(ReleaseOutcome.Locked, outcome)
    }

    @Test
    fun release_fromIdle_isTooShort() {
        val outcome = phaseOnRelease(VoicePhase.Idle, elapsedMillis = 0, minDurationMillis = 500)
        assertEquals(ReleaseOutcome.TooShort, outcome)
    }

    // ---- VoiceRecorderState end-to-end ---------------------------------------------------------

    @Test
    fun state_startTransitionsToRecordingHeldAndFiresOnStart() {
        var startCount = 0
        val state = newState(onStart = { startCount++ })
        assertEquals(VoicePhase.Idle, state.phase)
        state.start()
        assertEquals(VoicePhase.RecordingHeld, state.phase)
        assertEquals(1, startCount)
    }

    @Test
    fun state_pushAmplitudeAppendsWhileRecording() {
        val state = newState().apply { start() }
        state.pushAmplitude(0.5f)
        state.pushAmplitude(0.9f)
        state.pushAmplitude(1.5f) // clamps to 1f
        // .toList() copies out of the SnapshotStateList — equality of SnapshotStateList vs a plain
        // List differs between JVM (true) and Kotlin/Native (false), so always compare copies.
        assertEquals(listOf(0.5f, 0.9f, 1f), state.capturedSamples.toList())
    }

    @Test
    fun state_pushAmplitudeIgnoredWhenIdle() {
        val state = newState()
        state.pushAmplitude(0.5f)
        assertEquals(emptyList(), state.capturedSamples.toList())
    }

    @Test
    fun state_releaseAboveMinDuration_firesOnSendWithCapturedSamples() {
        val time = TestTimeSource()
        var sent: Pair<Duration, List<Float>>? = null
        val state = newState(time = time, onSend = { d, s -> sent = d to s })
        state.start()
        state.pushAmplitude(0.4f)
        state.pushAmplitude(0.7f)
        time += 800.milliseconds
        state.tick()
        state.release()
        val (dur, samples) = requireNotNull(sent) { "onSend should have fired" }
        assertEquals(800.milliseconds, dur)
        assertEquals(listOf(0.4f, 0.7f), samples)
        // v0.2: phase resets to Idle immediately after onSend.
        assertEquals(VoicePhase.Idle, state.phase)
    }

    @Test
    fun state_releaseBelowMinDuration_silentlyDiscards() {
        val time = TestTimeSource()
        var sent = false
        var cancelled = false
        val state = newState(time = time, onSend = { _, _ -> sent = true }, onCancel = { cancelled = true })
        state.start()
        time += 200.milliseconds
        state.tick()
        state.release()
        assertEquals(false, sent, "must not send a tap-too-short recording")
        assertEquals(false, cancelled, "TooShort is silent — no cancel callback")
        assertEquals(VoicePhase.Idle, state.phase)
    }

    @Test
    fun state_slideToCancelThenRelease_firesOnCancel() {
        val time = TestTimeSource()
        var cancelled = false
        val state = newState(time = time, onCancel = { cancelled = true })
        state.start()
        time += 1.seconds
        state.tick()
        state.updateDrag(dragX = -120f, dragY = 0f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
        assertEquals(VoicePhase.Cancelling, state.phase)
        state.release()
        assertEquals(true, cancelled)
        assertEquals(VoicePhase.Idle, state.phase)
    }

    @Test
    fun state_slideToLockThenRelease_staysLocked_thenSendFromLock_fires() {
        val time = TestTimeSource()
        var sent = false
        val state = newState(time = time, onSend = { _, _ -> sent = true })
        state.start()
        time += 1.seconds
        state.tick()
        state.updateDrag(dragX = 0f, dragY = -120f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
        assertEquals(VoicePhase.RecordingLocked, state.phase)
        state.release()
        assertEquals(VoicePhase.RecordingLocked, state.phase, "release in locked stays locked")
        assertEquals(false, sent)
        state.sendFromLock()
        assertEquals(true, sent)
    }

    @Test
    fun state_cancelFromLock_firesOnCancel() {
        val time = TestTimeSource()
        var cancelled = false
        val state = newState(time = time, onCancel = { cancelled = true })
        state.start()
        time += 1.seconds
        state.tick()
        state.updateDrag(dragX = 0f, dragY = -120f, lockThresholdPx = 80f, cancelThresholdPx = 80f)
        state.release()
        state.cancelFromLock()
        assertEquals(true, cancelled)
        assertEquals(VoicePhase.Idle, state.phase)
    }

    @Test
    fun state_tickPastMaxDurationAutoSends() {
        val time = TestTimeSource()
        var sent: Duration? = null
        val state = newState(time = time, maxDuration = 2.seconds, onSend = { d, _ -> sent = d })
        state.start()
        time += 2.seconds
        state.tick()
        assertEquals(2.seconds, sent)
        // v0.2: phase resets to Idle immediately after onSend.
        assertEquals(VoicePhase.Idle, state.phase)
    }

    @Test
    fun state_forceCancelDuringRecording_fires() {
        val time = TestTimeSource()
        var cancelled = false
        val state = newState(time = time, onCancel = { cancelled = true })
        state.start()
        time += 1.seconds
        state.tick()
        state.forceCancel()
        assertEquals(true, cancelled)
        assertEquals(VoicePhase.Idle, state.phase)
    }

    @Test
    fun state_forceCancelWhenIdle_isNoOp() {
        var cancelled = false
        val state = newState(onCancel = { cancelled = true })
        state.forceCancel()
        assertEquals(false, cancelled)
    }

    @Test
    fun state_afterSend_phaseResetsToIdle() {
        val time = TestTimeSource()
        val state = newState(time = time)
        state.start()
        time += 1.seconds
        state.tick()
        state.release()
        // v0.2: phase resets to Idle synchronously after onSend (no stuck Sent phase).
        assertEquals(VoicePhase.Idle, state.phase)
        state.start()
        assertEquals(VoicePhase.RecordingHeld, state.phase)
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private fun newState(
        onStart: () -> Unit = {},
        onCancel: () -> Unit = {},
        onSend: (Duration, List<Float>) -> Unit = { _, _ -> },
        onHaptic: (VoiceHaptic) -> Unit = {},
        minDuration: Duration = 500.milliseconds,
        maxDuration: Duration = 5.seconds,
        time: TestTimeSource = TestTimeSource(),
    ) = VoiceRecorderState(
        onStart = onStart,
        onCancel = onCancel,
        onSend = onSend,
        onHaptic = onHaptic,
        minDuration = minDuration,
        maxDuration = maxDuration,
        timeSource = time,
    )

    @Test
    fun durationFormatter_secondsZeroPad() {
        assertEquals("0:00", formatVoiceDuration(Duration.ZERO))
        assertEquals("0:05", formatVoiceDuration(5.seconds))
        assertEquals("1:09", formatVoiceDuration(69.seconds))
        assertEquals("10:00", formatVoiceDuration(600.seconds))
    }
}
