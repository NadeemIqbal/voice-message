package io.github.nadeemiqbal.voicemessage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Desktop (JVM) implementation. Desktop platforms do not expose a haptics API, so this is a
 * no-op. Consumers who want a custom haptic surrogate on Desktop (e.g. a brief sound effect or
 * window-shake animation) can pass their own `onHaptic` callback to `rememberVoiceRecorderState`.
 */
@Composable
actual fun rememberVoiceHaptics(): (VoiceHaptic) -> Unit = remember { { _: VoiceHaptic -> } }
