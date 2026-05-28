@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.nadeemiqbal.voicemessage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Wasm/JS implementation. Uses `navigator.vibrate(durationMs)` when the browser exposes it. On
 * Safari and other browsers without the Vibration API, the call is a no-op. Durations are short
 * and OS-friendly so this never blocks the main thread visibly.
 */
@Composable
actual fun rememberVoiceHaptics(): (VoiceHaptic) -> Unit = remember {
    { haptic ->
        val durationMs = when (haptic) {
            VoiceHaptic.Start -> 8
            VoiceHaptic.Lock -> 14
            VoiceHaptic.CrossCancel -> 6
            VoiceHaptic.Cancel -> 20
            VoiceHaptic.Send -> 12
        }
        vibrateIfSupported(durationMs)
    }
}

/**
 * Calls `navigator.vibrate(durationMs)` when available. Browsers without the Vibration API
 * (notably Safari) simply skip the call. Defined as a `js` external function so the compiler can
 * inline-emit the feature-detect into Wasm-JS interop.
 */
private fun vibrateIfSupported(durationMs: Int): Unit = js(
    "{ if (typeof navigator !== 'undefined' && navigator && typeof navigator.vibrate === 'function') { navigator.vibrate(durationMs); } }"
)
