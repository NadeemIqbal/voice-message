package io.github.nadeemiqbal.voicemessage

/**
 * The five states of the [VoiceRecorderInput] gesture state machine.
 *
 * - [Idle] — nothing is happening; the mic icon is at rest.
 * - [RecordingHeld] — the user is pressing the mic and recording is in progress; slide hints
 *   (up = lock, sideways = cancel) are visible and the live amplitude waveform scrolls.
 * - [RecordingLocked] — the user slid past the lock threshold and lifted; recording continues
 *   hands-free with a dedicated Send + Cancel button pair.
 * - [Cancelling] — the user slid past the cancel threshold while still holding; releasing now
 *   throws the recording away. Sliding back below the threshold returns to [RecordingHeld].
 * - [Sent] — terminal state for one recording: the consumer has been handed the duration and
 *   the captured amplitude samples and is expected to ship the audio file. The state machine
 *   resets to [Idle] on the next recording.
 */
enum class VoicePhase {
    Idle,
    RecordingHeld,
    RecordingLocked,
    Cancelling,
    Sent,
}

/**
 * Visual variant of a [VoiceMessageBubble] — whose message it is. Drives default colours so
 * sent-by-me and sent-by-them bubbles read as distinct in a chat list.
 */
enum class VoiceMessageRole { Sender, Receiver }
