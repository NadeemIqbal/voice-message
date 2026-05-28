package io.github.nadeemiqbal.voicemessage.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nadeemiqbal.voicemessage.VoiceMessageBubble
import io.github.nadeemiqbal.voicemessage.VoiceMessageRole
import io.github.nadeemiqbal.voicemessage.VoiceRecorderInput
import io.github.nadeemiqbal.voicemessage.audio.VoiceAudio
import io.github.nadeemiqbal.voicemessage.audio.rememberAudioBoundVoiceRecorderState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A fake WhatsApp-style chat that exercises `VoiceRecorderInput` and `VoiceMessageBubble`
 * without any platform audio code. A coroutine in the sample pushes random amplitudes into the
 * recorder state while recording, then synthesises a "playback" coroutine that advances each
 * bubble's progress so the UI feels live.
 */
@Composable
fun SampleApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ChatScreen()
        }
    }
}

private data class FakeVoiceMessage(
    val id: Int,
    val samples: List<Float>,
    val duration: Duration,
    val role: VoiceMessageRole,
)

@Composable
private fun ChatScreen() {
    var nextId by remember { mutableStateOf(100) }
    val messages = remember {
        mutableStateListOf(
            FakeVoiceMessage(
                id = 1,
                samples = randomSamples(seed = 1, count = 60, minAmp = 0.1f, maxAmp = 0.95f),
                duration = 18.seconds,
                role = VoiceMessageRole.Receiver,
            ),
            FakeVoiceMessage(
                id = 2,
                samples = randomSamples(seed = 7, count = 40, minAmp = 0.2f, maxAmp = 0.8f),
                duration = 11.seconds,
                role = VoiceMessageRole.Sender,
            ),
            FakeVoiceMessage(
                id = 3,
                samples = randomSamples(seed = 13, count = 90, minAmp = 0.05f, maxAmp = 1f),
                duration = 27.seconds,
                role = VoiceMessageRole.Receiver,
            ),
        )
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Single-track playback: only one bubble plays at a time. Toggling another pauses any current.
    var currentlyPlayingId by remember { mutableStateOf<Int?>(null) }
    val progresses = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }
    val speeds = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    // Playback simulator — drives the progress of whichever bubble is currently playing, at the
    // currently-selected speed (cycled via the speed chip). When progress hits 1f, playback
    // resets and the bubble returns to Idle.
    LaunchedEffect(currentlyPlayingId) {
        val id = currentlyPlayingId ?: return@LaunchedEffect
        val msg = messages.firstOrNull { it.id == id } ?: return@LaunchedEffect
        val totalMs = msg.duration.inWholeMilliseconds.coerceAtLeast(500L)
        val stepMs = 50L
        while (currentlyPlayingId == id) {
            delay(stepMs)
            val speed = speeds[id] ?: 1f
            val current = progresses[id] ?: 0f
            val next = (current + (stepMs.toFloat() * speed) / totalMs).coerceAtMost(1f)
            progresses[id] = next
            if (next >= 1f) {
                progresses[id] = 0f
                currentlyPlayingId = null
                break
            }
        }
    }

    // Real audio capture, wired in by voice-message-audio. Drops the fake synthesized-amplitude
    // polling that earlier versions of this sample used. The Desktop / iOS / Web actuals open
    // the platform mic, drive the live waveform, and hand back a VoiceAudio payload on send.
    var lastAudio by remember { mutableStateOf<VoiceAudio?>(null) }
    val recorderState = rememberAudioBoundVoiceRecorderState(
        onCancel = { /* sample has nowhere to upload, so just drop the audio */ },
        onSend = { audio, samples ->
            lastAudio = audio
            val id = nextId++
            messages.add(
                FakeVoiceMessage(
                    id = id,
                    samples = samples.ifEmpty { randomSamples(seed = id, count = 30, minAmp = 0.1f, maxAmp = 0.9f) },
                    duration = audio.duration.coerceAtLeast(1.seconds),
                    role = VoiceMessageRole.Sender,
                ),
            )
            scope.launch {
                delay(50)
                listState.animateScrollToItem(messages.size - 1)
            }
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("SA", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column {
                Text("Sara", style = MaterialTheme.typography.titleMedium)
                Text("online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.role == VoiceMessageRole.Sender) Arrangement.End else Arrangement.Start,
                ) {
                    VoiceMessageBubble(
                        samples = msg.samples,
                        duration = msg.duration,
                        isPlaying = currentlyPlayingId == msg.id,
                        progress = progresses[msg.id] ?: 0f,
                        onPlayPauseToggle = {
                            currentlyPlayingId = if (currentlyPlayingId == msg.id) null else msg.id
                        },
                        onSeek = { f -> progresses[msg.id] = f.coerceIn(0f, 1f) },
                        role = msg.role,
                        playbackSpeed = speeds[msg.id] ?: 1f,
                        onPlaybackSpeedChange = { newSpeed -> speeds[msg.id] = newSpeed },
                    )
                }
            }
        }

        // Input row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            VoiceRecorderInput(
                state = recorderState,
                idlePlaceholder = {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "Type a message…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        Text(
            "Hold the mic to record · slide up to lock · slide left to cancel.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

private fun randomSamples(seed: Int, count: Int, minAmp: Float, maxAmp: Float): List<Float> {
    val rng = Random(seed)
    return List(count) { i ->
        val envelope = 0.5f + 0.4f * kotlin.math.sin(i * 0.4).toFloat()
        val r = rng.nextFloat() * 0.4f
        (envelope * (minAmp + (maxAmp - minAmp) * rng.nextFloat()) + r).coerceIn(0f, 1f)
    }
}

@Suppress("unused")
private val _retainMillisecondsImport = 1.milliseconds
