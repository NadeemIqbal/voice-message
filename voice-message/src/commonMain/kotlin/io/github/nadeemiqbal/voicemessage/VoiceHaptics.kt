package io.github.nadeemiqbal.voicemessage

import androidx.compose.runtime.Composable

/**
 * Phase transition that triggers a haptic emission. The values mirror the gesture state machine:
 *
 * - [Start]: long-press elapsed, recording began. A light tick is appropriate.
 * - [Lock]: the user slid up past the lock threshold; recording continues hands-free. Medium tick.
 * - [CrossCancel]: the user slid past the cancel threshold while still holding. Light warning.
 * - [Cancel]: the recording was discarded (release in cancelling state, or `forceCancel`). Heavy.
 * - [Send]: the recording was delivered successfully. Medium confirm.
 */
enum class VoiceHaptic { Start, Lock, CrossCancel, Cancel, Send }

/**
 * Returns a platform-backed haptic emitter that [VoiceRecorderState] will call on phase
 * transitions. Each platform maps the abstract [VoiceHaptic] values to native feedback:
 *
 * - **Android**: `View.performHapticFeedback` with appropriate `HapticFeedbackConstants`.
 * - **iOS**: `UIImpactFeedbackGenerator` with light/medium/heavy styles.
 * - **Desktop (JVM)**: no-op. Desktop platforms do not expose a haptics API.
 * - **Web (Wasm)**: `navigator.vibrate` when supported, otherwise a no-op.
 *
 * Pass your own `(VoiceHaptic) -> Unit` to `rememberVoiceRecorderState` to override (for example
 * `onHaptic = {}` to disable haptics entirely, or a custom emitter that fans out to a haptics
 * library you already use).
 */
@Composable
expect fun rememberVoiceHaptics(): (VoiceHaptic) -> Unit
