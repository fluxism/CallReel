# Contributing to CallReel

Thank you for considering a contribution.

CallReel is a fork of ShizuCallRecorder, available at https://github.com/kitsumed/ShizuCallRecorder. That shapes where a change should go:

- **Video recording, and anything specific to CallReel** (its name, icon, documentation, releases): contribute here.
- **Audio recording, call detection, Shizuku handling, settings, translations of the original texts**: please contribute to the [original project](https://github.com/kitsumed/ShizuCallRecorder) and follow its contributing guide. CallReel merges the original project's changes, so both apps benefit. A fix that only lives here makes those merges harder.

If you are not sure, open an [issue](https://github.com/fluxism/CallReel/issues) first.

## Rules

1. Communication happens in English so everyone can understand.
2. Be respectful. This project is maintained in spare time, and a contribution may be declined.
3. Explain in your pull request what the change does and why it is needed. Say how you tested it, and on which device and Android version.
4. Keep changes small and isolated. Discuss large changes in an issue first.
5. Keep the code style of the surrounding code, including KDoc and comments.
6. Keep the difference to the original project small. Put new code in new files where possible, and mark a change to an original file with a `// FORK:` comment, so that merges from the original stay simple.
7. Keep dependencies minimal.
8. If you used an AI tool, say so, and review its output yourself before submitting.

## Building and testing

The repository does not contain the Gradle wrapper scripts. With Gradle 9.8.2 and JDK 17 or more recent:

```sh
gradle wrapper
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The video pipeline can be tested on a device without a call. With the debug build installed, run as the shell user:

```sh
adb shell 'CLASSPATH=$(pm path io.github.fluxism.callreel | cut -d: -f2) app_process / com.kitsumed.shizucallrecorder.debug.VideoSelfTest /data/local/tmp/selftest.mp4 6'
adb pull /data/local/tmp/selftest.mp4
```

It records the screen for 6 seconds with a test tone and prints whether the file contains video.

## Why the code is still in `com.kitsumed.shizucallrecorder`

Only the application ID was changed (`io.github.fluxism.callreel`). The Kotlin package and the original file headers are kept, so that changes from the original project merge cleanly and its author's copyright notices stay intact.

## Translations

The translations come from the original project, which manages them on Weblate. Please translate the original texts there. Texts added by CallReel are in English only for now; translations of those are welcome here as pull requests.
