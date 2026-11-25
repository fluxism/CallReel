# Draft text for the F-Droid merge request. Not submitted yet.

GitLab shows a template when the merge request is opened. These are the answers for it. See [RELEASING.md](RELEASING.md) for the steps.

**Title:** New app: CallReel

## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

CallReel records phone calls on non-rooted devices through Shizuku. On Android 14 and later it also records the video of video calls, for example in WhatsApp or Signal: the screen is recorded together with the call audio into an MP4 file, next to the audio recording. Recording starts by itself when a call starts, following the rules the user has set, or from a button in the notification. Recordings stay on the device.

Screenshots: attach the three images from `fastlane/metadata/android/en-US/images/phoneScreenshots`.

### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.

CallReel is a fork of ShizuCallRecorder (`com.kitsumed.shizucallrecorder`), which is in F-Droid and records the audio of calls. I wanted to keep video calls as well, and no app in F-Droid records them without root. CallReel adds that: it mirrors the screen into a video encoder from the Shizuku service for the duration of the call and writes it with the call audio into one file.

The fork has its own name, application ID, icon and strings, as the license of the original (GPL-3.0-or-later with additional terms under section 7) and the F-Droid rules for forks require. The original project is credited and linked in the app and in the README.

## Notes for the checklist

- **Inclusion policy, age of the app:** CallReel's own development started in November 2026. The code it builds on has been developed in the original project since May 2026.
- **Original app author:** I am the author of the fork.
- **Fastlane metadata:** in the repository under `fastlane/metadata/android/en-US` (title, short and full description, icon, feature graphic, screenshots, changelog).
- **Reproducible builds:** enabled, with `Binaries` and `AllowedAPKSigningKeys`. The release APK is built by a GitHub workflow and carries a build provenance attestation.
- **Anti-features:** `NonFreeDep` for Shizuku, the same as the original app's recipe.
- **Commit:** full hash. **Auto update:** enabled, from tags.
