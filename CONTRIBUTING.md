# Contributing to VoiceMessage

Thanks for your interest in improving this library! Contributions of all kinds are welcome —
bug reports, feature requests, docs, and code.

## Project layout

```
voice-message/             The published Kotlin Multiplatform library.
  src/commonMain/           Public API + implementation.
    VoiceRecorderInput.kt    Hold-to-record gesture composable.
    VoiceRecorderState.kt    FSM owner + state class.
    VoiceMessageBubble.kt    Playback chat bubble.
    VoiceWaveform.kt         Shared amplitude-bar primitive.
    VoiceMessageInternal.kt  Pure helpers (downsampling, FSM transitions, release math).
  src/commonTest/           Pure-logic tests — run on every target including Android unit tests.
  src/skikoTest/            Compose UI tests — run on Desktop and iOS test targets.
sample/composeApp/          Shared chat-clone sample with a fake recorder + fake playback.
sample/androidApp/          Android launcher.
sample/desktopApp/          Desktop (JVM) launcher.
sample/webApp/              Web (wasmJs) launcher.
sample/iosApp/              iOS launcher (Xcode project).
```

## Building & testing

```bash
./gradlew build                                  # build + test everything
./gradlew allTests                               # run tests on all targets
./gradlew :voice-message:desktopTest             # fastest feedback (commonTest + skikoTest)
./gradlew :voice-message:testDebugUnitTest       # Android unit tests
./gradlew :sample:desktopApp:run                 # run the desktop sample
```

The gesture state machine (every legal/illegal transition, drag thresholds, min/max-duration
behaviour) is fully covered by pure-logic tests in `VoiceMessageLogicTest`. Prefer adding new
behaviour there so it can be unit-tested without composition.

The library never captures audio itself — keep it that way. Audio capture / playback / encoding
must stay BYO via the `VoiceRecorderState` callbacks (`onStart` / `onCancel` / `onSend`) and the
bubble's `isPlaying` / `progress` / `onPlayPauseToggle` / `onSeek` inputs.

## Conventions

- Public API gets KDoc.
- Add or update tests for every behaviour change — both pure logic and UI wiring.
- Update the sample app when you change a public API.
- Add a `CHANGELOG.md` entry under `## Unreleased`.

## Releasing

Releases are tag-driven: pushing a `v*` tag runs `.github/workflows/publish.yml`, which publishes
to Maven Central via the `com.vanniktech.maven.publish` plugin and creates a GitHub Release.
