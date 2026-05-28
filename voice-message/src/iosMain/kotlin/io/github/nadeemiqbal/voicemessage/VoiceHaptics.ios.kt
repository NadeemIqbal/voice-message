package io.github.nadeemiqbal.voicemessage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * iOS implementation. Light and medium ticks use `UIImpactFeedbackGenerator`; `Send` and `Cancel`
 * use `UINotificationFeedbackGenerator` so they read as completion / error semantics rather than
 * generic impacts. Generators are kept around per remember to avoid the small allocation cost on
 * every transition.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberVoiceHaptics(): (VoiceHaptic) -> Unit {
    val lightImpact = remember { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight) }
    val mediumImpact = remember { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium) }
    val notification = remember { UINotificationFeedbackGenerator() }
    return remember {
        { haptic ->
            when (haptic) {
                VoiceHaptic.Start -> lightImpact.impactOccurred()
                VoiceHaptic.Lock -> mediumImpact.impactOccurred()
                VoiceHaptic.CrossCancel -> lightImpact.impactOccurred()
                VoiceHaptic.Cancel -> notification.notificationOccurred(
                    UINotificationFeedbackType.UINotificationFeedbackTypeError,
                )
                VoiceHaptic.Send -> notification.notificationOccurred(
                    UINotificationFeedbackType.UINotificationFeedbackTypeSuccess,
                )
            }
        }
    }
}
