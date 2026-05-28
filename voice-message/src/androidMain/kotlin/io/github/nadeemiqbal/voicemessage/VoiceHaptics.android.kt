package io.github.nadeemiqbal.voicemessage

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Android implementation. Maps each [VoiceHaptic] to a `HapticFeedbackConstants` value and calls
 * `View.performHapticFeedback`. The host view's `HapticFeedbackEnabled` flag is respected by the
 * platform, so apps that opt out of haptics globally still see no vibration here.
 */
@Composable
actual fun rememberVoiceHaptics(): (VoiceHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { haptic ->
            val constant = when (haptic) {
                VoiceHaptic.Start -> HapticFeedbackConstants.LONG_PRESS
                VoiceHaptic.Lock -> HapticFeedbackConstants.CONFIRM
                VoiceHaptic.CrossCancel -> HapticFeedbackConstants.GESTURE_START
                VoiceHaptic.Cancel -> HapticFeedbackConstants.REJECT
                VoiceHaptic.Send -> HapticFeedbackConstants.CONFIRM
            }
            view.performHapticFeedback(constant)
        }
    }
}
