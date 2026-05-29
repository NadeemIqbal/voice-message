# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.1] - 2026-05-29

### Fixed

- **Wasm sample failed to load** with `Module parse failed: Unexpected token` in the generated
  `*.import-object.mjs`. The `js("...")` blocks in `voice-message-audio`'s wasmJs source ended
  their IIFEs with `})();`; Kotlin/Wasm wraps each `js()` body into an arrow function inside a
  JS object literal, where the trailing `;` becomes a stray statement separator and breaks the
  parse. Dropped the terminators. Any consumer building `voice-message-audio` for wasmJs (e.g.
  through `prompt-bar-voice`) needs this.

### Added

- **`VoiceMicButton`** is now a public composable. Previously the hold-to-record mic was a
  private piece of `VoiceRecorderInput`. Exposing it lets you build custom composers, e.g. a
  chat input bar whose send button morphs into a mic when the text field is empty. Pair it with
  `VoiceWaveform` (driven by `VoiceRecorderState.capturedSamples`) to render the recording strip
  wherever your layout wants it. Takes a `showLockChevron` flag to suppress the slide-up hint.

## [0.3.0] - 2026-05-29

This release bundles the v0.2 correctness pass with the v0.3 "batteries included" companion
artifact, shipped together to keep the version axis simple. There was no published 0.2.0.

### Added

- **New artifact: `voice-message-audio`** at the matching `0.3.0`. Provides
  `VoiceAudioCapture` (expect class) and `rememberAudioBoundVoiceRecorderState` (a drop-in
  replacement for `rememberVoiceRecorderState` that opens the platform mic for you). Per-platform
  actuals: Android `MediaRecorder`, iOS `AVAudioRecorder`, Desktop `javax.sound.sampled`, Wasm
  `MediaRecorder` + `AnalyserNode`. Consumers no longer have to BYO audio plumbing.

### Fixed

- **Critical: recording cancelled immediately on every long-press (pre-existing in 0.1.0).** The
  `MicButton` lived inside per-phase `when` branches. Each branch is a distinct slot-table
  group, so when `state.start()` flipped phase from `Idle` to `RecordingHeld`, Compose disposed
  the Idle-branch `MicButton` and composed a new one in the `RecordingHeld` branch. The
  disposal tore down the in-flight `.pointerInput { ... }` modifier mid-gesture, the gesture
  coroutine's `finally { state.release() }` fired with `phase=RecordingHeld` and `elapsed~=0ms`,
  the FSM resolved that to `TooShort`, and every recording silently reset to `Idle` one frame
  after start. Refactored so the `MicButton` sits at one stable source position across the
  `Idle` / `RecordingHeld` / `Cancelling` phases.
- **Waveform bars looked like circles in wide bubbles.** The old layout stretched bar width
  across the canvas (`barWidth = (canvasWidth - totalSpacing) / barCount`), so a 500dp+ bubble
  ended up with ~10-13dp-wide bars. Combined with `cornerRadius = barWidth / 2` (pill ends),
  mid-amplitude bars became squarish circles and low-amplitude bars became horizontal ovals.
  Rewritten to a density-based layout: bars are a fixed `BarWidth` (default 3dp,
  WhatsApp-style narrow), the count is computed as
  `min(barCount, canvasWidth / (barWidth + barSpacing))`, and any remaining canvas space is
  split as left/right padding so the row stays centered. Narrow bubbles draw fewer bars,
  wide bubbles draw more, every bar is the same crisp 3dp pill. `BarCount` default bumped
  from 40 to 64.
- **"Slide to cancel" hint shown in locked mode.** The `RecordingLocked` phase has explicit
  Cancel (x) and Send (right arrow) buttons flanking the strip; the slide hint inside the strip
  was misleading since the gesture no longer does anything. Added `showSlideHint: Boolean = true`
  to `RecordingActiveStrip` and passed `false` from the locked branch.
- **Live waveform scrolls properly.** `downsampleAmplitudes` now takes a `WaveformMode`
  (`Live` for the recorder strip, `Static` for the playback bubble). `Live` mode keeps only
  the latest samples (sliding window) so the bars slide right-to-left as new amplitudes
  arrive, matching WhatsApp / Telegram. Previously, once samples exceeded ~1.3 seconds the
  recording strip froze into a static averaged spectrogram.
- **RTL handling.** `VoiceRecorderInput` now reads `LocalLayoutDirection` and flips the
  cancel-direction sign in RTL locales (Arabic / Hebrew / Urdu chat apps). The slide-to-cancel
  hint text and arrow direction also flip.
- **Accessibility (a11y).** Added `Modifier.semantics` to every interactive surface: mic
  button (`Role.Button` + `stateDescription` reflecting phase), locked Send / Cancel buttons,
  play / pause button, speed chip, waveform tap target. The recording timer is now a
  `LiveRegionMode.Polite` so TalkBack and VoiceOver announce it. Previously the entire library
  was unusable to screen readers.
- **Bubble seek snaps to bar boundaries.** Tap-to-seek returned a continuous float fraction
  but the visual played / unplayed split is bar-discrete, so tapping the middle of a bar left
  the split half a bar off. The tap handler now snaps to `barCount` discrete positions.

### Added (haptics + waveform API)

- **Haptic feedback** via `rememberVoiceHaptics()` (new `VoiceHaptic` enum: `Start`, `Lock`,
  `CrossCancel`, `Cancel`, `Send`). Per-platform actuals: Android `View.performHapticFeedback`;
  iOS `UIImpactFeedbackGenerator` + `UINotificationFeedbackGenerator`; Wasm `navigator.vibrate`
  when available; Desktop no-op. `rememberVoiceRecorderState` accepts a custom `onHaptic`
  callback (defaulting to the platform emitter), so consumers can fan out to their own haptics
  library or pass `{}` to disable.
- **`VoiceWaveform.live: Boolean`** parameter, default `false` (static).
- **`VoiceWaveform.barWidth: Dp`** parameter, default `VoiceMessageDefaults.BarWidth = 3.dp`.
- New platform source sets on the main library: `androidMain`, `iosMain`, `desktopMain`,
  `wasmJsMain` (wired automatically by `applyDefaultHierarchyTemplate()`).

### Changed (breaking)

- **Removed `VoicePhase.Sent`.** Was a stuck terminal phase: observers saw `Sent` forever
  between recordings. The phase now resets to `Idle` synchronously after `onSend`. Callers
  matching on `VoicePhase.Sent` must drop that branch.
- `rememberVoiceRecorderState` gained an `onHaptic: (VoiceHaptic) -> Unit` parameter,
  defaulted, so existing call sites keep compiling.

## [0.2.0] - never published

The v0.2.0 work landed locally but was never tagged or published. All v0.2 changes are folded
into the v0.3.0 release above.

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
