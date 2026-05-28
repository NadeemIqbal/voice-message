package io.github.nadeemiqbal.voicemessage

/**
 * The four states of the [VoiceRecorderInput] gesture state machine.
 *
 * - [Idle]: nothing is happening; the mic icon is at rest. Also the resting state after a
 *   successful send or cancel: the `onSend` / `onCancel` callback is the delivery contract,
 *   there is no separate "Sent" phase to observe.
 * - [RecordingHeld]: the user is pressing the mic and recording is in progress; slide hints
 *   (up = lock, sideways = cancel) are visible and the live amplitude waveform scrolls.
 * - [RecordingLocked]: the user slid past the lock threshold and lifted; recording continues
 *   hands-free with a dedicated Send + Cancel button pair.
 * - [Cancelling]: the user slid past the cancel threshold while still holding; releasing now
 *   throws the recording away. Sliding back below the threshold returns to [RecordingHeld].
 */
enum class VoicePhase {
    Idle,
    RecordingHeld,
    RecordingLocked,
    Cancelling,
}

/**
 * Visual variant of a [VoiceMessageBubble] — whose message it is. Drives default colours so
 * sent-by-me and sent-by-them bubbles read as distinct in a chat list.
 */
enum class VoiceMessageRole { Sender, Receiver }
