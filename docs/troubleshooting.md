[Go back to the home documentation page](./SUPPORT.md)
# Troubleshooting

> [!NOTE]
> These guides were written for ShizuCallRecorder by its author and adapted for CallReel, a fork of it. They apply to both apps unless stated otherwise.

## Guides

- [The app does not start in the background, or is killed](./troubleshooting/background-application-startup-failures.md)
- [Permissions cannot be granted ("steps exhausted")](./troubleshooting/permissions-granting-issues.md)
- [The recording is silent, or one side is missing](./troubleshooting/silent-recording-or-missing-one-side.md)
- [How to hide the recording notification](./troubleshooting/how-to-hide-the-recording-notification.md)
- [Integration into the native dialer of your phone](./troubleshooting/integration-into-native-oem-dialer.md)

## Video recording

- **No video file is created.** Check that **Record video on video calls** is enabled, that the call detection method is **InCallService** with **Record third-party apps** enabled, and that the call itself is being recorded. If the video does not start by itself, tap **Record video** on the recording notification.
- **The video is black.** The app showing the call marks its window as secure. CallReel cannot record such windows.
- **The video shows something other than the call.** CallReel records the whole screen. Whatever is on screen during the call is in the video.
- **CallReel and ShizuCallRecorder are both installed.** Both react to the same call. Turn recording off in one of them.

## Where can I find a solution to my problem?

Search the [issues of CallReel](https://github.com/fluxism/CallReel/issues). For a problem with audio recording, the [issues](https://github.com/kitsumed/ShizuCallRecorder/issues) and [discussions](https://github.com/kitsumed/ShizuCallRecorder/discussions) of the original project are worth searching too, as both apps share the recording engine. Please do not report CallReel problems there.

For help with Shizuku itself, use the [Shizuku community support](https://github.com/thedjchi/Shizuku/discussions/categories/q-a).
