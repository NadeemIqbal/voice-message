# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-05-16

### Added
- Initial release of `VoiceMessage` for Compose Multiplatform.
- `VoiceRecorderInput` — hold-to-record mic with the full WhatsApp/Telegram gesture
  choreography. Works on **touch AND mouse / trackpad** — the long-press detector ignores
  pointer jitter during the hold (the built-in `detectDragGesturesAfterLongPress` cancelled on
  any movement, breaking Desktop usage):
  - Long-press to start recording.
  - Slide up past the lock threshold to lock hands-free.
  - Slide left past the cancel threshold to enter the cancelling state.
  - Release in `RecordingHeld` to send; release in `Cancelling` to cancel; release in
    `RecordingLocked` to stay locked (Send / Cancel buttons take over).
  - `maxDuration` auto-finish (default 5 min).
  - `minDuration` discard (releases shorter than 500 ms are silently dropped).
- `VoiceMessageBubble` — chat-bubble playback with tap-to-seek waveform, play/pause button,
  duration label, and sender/receiver visual variants. Optional **playback-speed chip**
  (1× / 1.5× / 2×) that appears once playback has started — drive it via the `playbackSpeed` +
  `onPlaybackSpeedChange` parameters. Helpers `VoiceMessageDefaults.nextPlaybackSpeed` and
  `formatPlaybackSpeed` cycle and format the value for you.
- `VoiceWaveform` — shared amplitude visualization primitive used by both composables, with
  optional `progress`-driven played / unplayed bar split.
- `VoiceRecorderState` + `rememberVoiceRecorderState` — exposes `phase`, `elapsed`,
  `capturedSamples` and the imperative methods (`start`, `pushAmplitude`, `release`,
  `sendFromLock`, `cancelFromLock`, `forceCancel`) for direct control or testing.
- Pure-logic helpers (`downsampleAmplitudes`, `phaseFromDrag`, `phaseOnRelease`) usable without
  composition; covered by 31 commonTest cases plus 11 UI tests.
- Audio capture is **BYO** — the library never opens a `MediaRecorder` / `AVAudioRecorder` etc.
  Consumers wire their existing audio stack into the `onStart`/`onCancel`/`onSend` callbacks.
- Targets: Android, iOS (x64, arm64, simulatorArm64), Desktop (JVM), Web (wasmJs).

[Unreleased]: https://github.com/NadeemIqbal/voice-message/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/NadeemIqbal/voice-message/releases/tag/v0.1.0
