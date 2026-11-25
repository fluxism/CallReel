<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="112" alt="CallReel icon">
</p>

# CallReel

**Record phone calls, and the video of video calls, on a non-rooted Android phone.** Free and open source, powered by [Shizuku](https://github.com/thedjchi/Shizuku).

> [!IMPORTANT]
> **CallReel is a FORK.** This software is a fork of ShizuCallRecorder, available at https://github.com/kitsumed/ShizuCallRecorder.
>
> The call recording engine, the app and most of the documentation are the work of **[kitsumed (Med)](https://github.com/kitsumed)**, the original author. CallReel is maintained separately by [fluxism](https://github.com/fluxism). It is not the original project and is not affiliated with or endorsed by its author.
>
> If you only need audio call recording, use the original. Please report CallReel problems [here](https://github.com/fluxism/CallReel/issues), not to the original project.

## What CallReel adds

| | ShizuCallRecorder (original) | CallReel (this fork) |
|---|---|---|
| Audio call recording | Yes | Yes, same engine |
| Video of video calls (WhatsApp and similar apps) | No | **Yes**, on Android 14+ |
| Package ID | `com.kitsumed.shizucallrecorder` | `com.fluxism.callreel` |
| Can be installed next to the other | Yes | Yes |

Everything else follows the original project, whose changes are merged into CallReel from time to time.

## Features

- **Video call recording** (Android 14+): when a recorded call is a video call, the screen is recorded with the call audio into an `.mp4` file next to the audio file
    - Starts by itself when Android reports the call as a video call, or when a camera turns on during a call from a third-party app
    - A **Record video** / **Stop video** button on the recording notification gives manual control
    - Off by default. The audio file is written exactly as without it
- Records **both sides of phone calls** (incoming and outgoing), also with Bluetooth or a headset
- **Separate uplink and downlink audio** inside a stereo recording
- **Cellular/VoLTE capture**, with best-effort support for VoIP and third-party apps starting with Android 14
- **Security** toggles that start Shizuku only when it is needed
- **Automatic call recording** with exclusion rules (anonymous calls, specific contacts, all contacts)
- Several audio codecs: **Opus**, **AAC**, **HE-AAC-v1** and more
- Runs only when a call happens: no persistent background process

## Requirements

- **Android 11** or more recent for audio recording. **Android 14** or more recent for video recording and for third-party apps such as WhatsApp.
- [Shizuku](https://github.com/thedjchi/Shizuku).

Audio recording behaves as in the original project, so its compatibility notes apply:

| Android version | Audio recording | Note |
|-----------------|-----------------|------|
| 11              | Limited | Needs an unlocked screen. No VoIP support |
| 12 and 13       | Basic   | No VoIP support (Pixel devices may work) |
| 14 to 17        | Yes     | |

Video recording has been tested on a Pixel running Android 17 with WhatsApp video calls. Other devices and apps may behave differently.

> [!IMPORTANT]
> This application uses hidden internal Android APIs. It can break with a new Android release or with changes made by a device manufacturer.

## Video recording: what to expect

- **The whole screen is recorded**, exactly as you see it. If you open another app during the call, that app and any notification that pops up are recorded too.
- Windows that an app marks as secure are recorded as black.
- The video is H.264 at up to 1280 pixels on the long side, 30 frames per second, about 15 MB per minute.
- The video size is chosen when the video starts. If you rotate the phone during the call, the picture is letterboxed.
- The video only starts while the call is being recorded.

See [Video call recording](docs/configuration.md#video-call-recording) for the setup.

## Installation

1. Install and set up Shizuku.
2. Download the latest CallReel APK from [Releases](https://github.com/fluxism/CallReel/releases/latest) and install it.
3. Follow the [configuration guide](docs/configuration.md).

All documentation is in [`docs/`](docs/SUPPORT.md).

## Building from source

The repository does not contain the Gradle wrapper scripts. With Gradle 9.8.2 and JDK 17 or more recent:

```sh
gradle wrapper
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
```

Release APKs are built and signed by the [Build App](.github/workflows/build-app.yml) workflow.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). A change that is not about video recording usually belongs in the original project, where both apps benefit from it.

## Credits

- **[kitsumed (Med)](https://github.com/kitsumed)** created ShizuCallRecorder, which CallReel is built on. If CallReel is useful to you, consider [supporting the original author](https://github.com/sponsors/kitsumed) and [starring the original project](https://github.com/kitsumed/ShizuCallRecorder).
- The translations come from the contributors of the original project. Texts added by CallReel are in English only for now.
- [scrcpy](https://github.com/Genymobile/scrcpy) showed how to capture audio and mirror the screen from the shell user.
- [Shizuku](https://github.com/thedjchi/Shizuku) makes running code as the shell user possible without root.

## License

CallReel is licensed under the [GNU General Public License, version 3 (GPL-3.0)](LICENSE), with the **additional terms** of the original project under GPL version 3 section 7. They are at the end of the [LICENSE](LICENSE) file and they also apply to forks of CallReel.

- Copyright (C) 2026 kitsumed (Med), original work
- Copyright (C) 2026 fluxism, CallReel modifications

The license grants no rights in trademarks or logos. The name "ShizuCallRecorder", its logo and `com.kitsumed.shizucallrecorder` belong to kitsumed. CallReel uses its own name, icon and package ID, as those terms require of a fork. If you fork CallReel, choose your own as well.

## Disclaimer

**Recording phone calls and video calls may be subject to complex and varying laws in different countries and jurisdictions.** For example, you may need to **ensure you have consent** from all parties before recording. A video recording also captures the image of the other party and everything shown on your screen. The **developers and contributors are not responsible** for any misuse or legal consequences arising from the use of this application.
You can learn more on Wikipedia: [Telephone call recording laws](https://en.wikipedia.org/wiki/Telephone_call_recording_laws).

**If you are legally required to inform or ask someone for consent before recording**, please note that the application **DOES NOT handle this for you**. In some cases, certain features, like automatic call recording, may not be legally allowed. It is your responsibility to ensure compliance with applicable laws. **This is not legal advice.** Please consult a legal professional about your specific situation.

> [!CAUTION]
> Because of the evolving Android ecosystem and varying hardware, this software is subject to **non-deterministic behavior**.

<details>
<summary><b>Application behavior and user awareness</b></summary>

### Application behavior
* **Concurrent calls:** the app may fail to detect transitions, for example a second incoming call during a call, or switching between held calls. It may then **continue to record into a single continuous file** without a new notification for the later caller.
* **Filter logic limitations:** because Android restricts real-time access to phone numbers, the app may rely on deprecated or unstable values to decide whether to record automatically. When a value arrives too late, is invalid or is missing, the app is likely to treat the call as "anonymous" when it decides.
* **Unforeseen failures:** future OS updates, bugs, design choices in the app or undocumented system behavior may cause the app to start, continue or fail to record in an unexpected manner.

### User awareness
Because deterministic behavior cannot be guaranteed, it is your responsibility to:
1. Ensure the recording behavior and your settings match your intent and the requirements of your jurisdiction.
2. **Monitor the app's behavior** on your device. If you observe behavior that does not comply with your local laws, you **must immediately stop the activity that constitutes an infraction** (for example by hanging up, or by deleting the files).
</details>
