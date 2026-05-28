# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-05-29

### Fixed

- **Live waveform now actually scrolls.** The recording strip showed a static
  averaged spectrogram across the whole recording once it exceeded ~1.3 seconds
  of samples. Fixed `downsampleAmplitudes` to take a `WaveformMode` (Live for the
  recorder strip, Static for the playback bubble). Live mode keeps only the
  latest `barCount` samples so the bars genuinely slide right-to-left as new
  amplitudes arrive, matching WhatsApp / Telegram.
- **Long-press gesture race.** `MicButton`'s long-press timer ran in a separate
  launched coroutine with a captured `started` flag read by the pointer-event
  loop, producing a cross-dispatcher race where drag deltas could be
  misattributed across the long-press boundary. Rewritten with
  `withTimeoutOrNull` inside the event loop: one coroutine, two phases, no
  shared mutable flag.
- **`VoicePhase.Sent` was a stuck terminal phase.** Observers of `state.phase`
  saw `Sent` forever between recordings. Removed in this release; the state
  resets to `Idle` synchronously after the `onSend` callback, which is the
  delivery contract. **Breaking change**: callers matching on
  `VoicePhase.Sent` should remove that branch.
- **RTL handling.** `VoiceRecorderInput` now reads `LocalLayoutDirection` and
  flips the cancel-direction sign in RTL locales (Arabic / Hebrew / Urdu chat
  apps). The slide-to-cancel hint text and arrow direction also flip.
- **Accessibility (a11y).** Added `semantics` modifiers to every interactive
  surface: mic button (`Role.Button` + `stateDescription` reflecting phase),
  locked Send / Cancel buttons, play / pause button, speed chip, waveform tap
  target. The recording timer is now a `LiveRegionMode.Polite` so TalkBack and
  VoiceOver announce it. Previously the entire library was unusable to screen
  readers.
- **Bubble seek snaps to bar boundaries.** Tap-to-seek returned a continuous
  float fraction but the visual played / unplayed split is bar-discrete, so
  tapping the middle of a bar left the split half a bar off. The tap handler
  now snaps to `barCount` discrete positions.

### Added

- **Haptic feedback** via `rememberVoiceHaptics()` (new `VoiceHaptic` enum:
  `Start`, `Lock`, `CrossCancel`, `Cancel`, `Send`). Per-platform actuals: Android
  uses `View.performHapticFeedback`; iOS uses `UIImpactFeedbackGenerator` +
  `UINotificationFeedbackGenerator`; Wasm uses `navigator.vibrate` when
  available; Desktop is a no-op. `rememberVoiceRecorderState` accepts a custom
  `onHaptic` callback (defaulting to the platform emitter) so consumers can fan
  out to their own haptics library or pass `{}` to disable.
- **`VoiceWaveform.live: Boolean`** parameter, default `false` (static).
- New platform source sets: `androidMain`, `iosMain`, `desktopMain`,
  `wasmJsMain`. Wired automatically by `applyDefaultHierarchyTemplate()`.

### Changed (breaking)

- Removed `VoicePhase.Sent`. Phase resets to `Idle` synchronously after
  `onSend`. Callers must remove any `Sent` branches.
- `rememberVoiceRecorderState` gained an `onHaptic: (VoiceHaptic) -> Unit`
  parameter. Source-compatible (defaulted), but the synthetic interface is
  different.

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
