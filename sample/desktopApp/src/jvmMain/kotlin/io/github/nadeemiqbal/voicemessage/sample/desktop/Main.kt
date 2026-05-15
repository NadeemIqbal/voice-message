package io.github.nadeemiqbal.voicemessage.sample.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.nadeemiqbal.voicemessage.sample.SampleApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "VoiceMessage Sample",
        state = rememberWindowState(width = 480.dp, height = 800.dp),
    ) {
        SampleApp()
    }
}
