# Video call recording: design

> Historical document. The fork was later renamed from ShizuVideoCallRecorder to **CallReel** (`com.fluxism.callreel`, repository `fluxism/CallReel`), and the video bitrate was lowered to 2 Mbit/s.

Date: 2026-11-21
Branch: `feature/video-call-recording`
Repository: fork of kitsumed/ShizuCallRecorder, published as fluxism/ShizuVideoCallRecorder

## Goal

When a recorded call is a video call (WhatsApp first, any Telecom-integrated app in general), also record
the video. The existing audio recording must not change in any way: same file, same codec, same name.

## Constraints

- A third-party app's camera and remote video streams are not reachable. The only capture route is
  mirroring the screen from the Shizuku shell process (UID 2000), as scrcpy does.
- The shell process already owns the audio pipeline: capture -> `PcmProcessor` -> `AudioEncoder`
  (MediaCodec + MediaMuxer, one audio track) -> file descriptor handed over by the app.
- This is a fork. Upstream files are edited as little as possible so upstream merges stay cheap.
  New behaviour lives in new files wherever it can.

## Output

Per video call, two files in the user's recording folder:

1. The usual audio file, byte-for-byte the same pipeline as today.
2. `<same base name>.mp4`: H.264 video of the screen plus an AAC-LC copy of the processed call audio
   (16 kHz, mono or stereo exactly like the main recording).

If video is stopped and started again in the same call, each start creates a new MP4; the storage
provider de-duplicates the name.

## Shell side (`services/shell/recording/video/`)

| Unit | Responsibility |
|---|---|
| `VideoConfig` | Constants: long side <= 1280 px, 30 fps, 2 Mbit/s, key frame every 2 s, repeat-frame delay 100 ms, AAC 32 kbit/s mono / 64 kbit/s stereo. |
| `VideoSizeCalculator` | Pure. Display size + max long side + codec alignment -> encoder size (aspect kept, aligned down, never upscaled). |
| `Mp4Timeline` | Pure. Maps raw monotonic video timestamps and audio block arrivals onto one "active time" axis that excludes paused time; keeps both tracks strictly increasing; re-anchors audio when it falls more than 200 ms behind the clock (dropped blocks). |
| `DisplayMirror` | Reflection wrapper: main display size via `DisplayManagerGlobal.getDisplayInfo(0)`, mirror via hidden static `DisplayManager.createVirtualDisplay(name, w, h, displayIdToMirror, surface)`. |
| `Mp4Writer` | MediaMuxer wrapper for two tracks. Starts when both formats are known, buffers early samples (bounded), serialises writes, reports whether any video sample was written. |
| `ShellVideoRecorder` | Owns one MP4 session: AVC surface encoder + `DisplayMirror` + AAC encoder + `Mp4Writer`. `start()`, `offerAudio(pcm, frames)`, `pause()`, `resume()`, `stop(): Boolean` (true when the file has video). |

Threads: one video drain thread; one MP4-audio thread fed by a bounded queue. `offerAudio` only copies
the PCM block into that queue and drops it when the queue is full, so the main audio pipeline can never
be slowed down by video.

`ShellRecordingEngine` changes (small, additive):
- `startVideo(pfd, callback): Boolean` and `stopVideo(): Boolean`.
- `encoderLoop` offers each processed PCM block to the active video recorder before encoding it.
- `pause()` / `resume()` are forwarded; `stop()` stops video first so the MP4 is finalised.

AIDL:
- `IShellService.startVideoRecording(in ParcelFileDescriptor outputFile, in IVideoRecordingCallback callback): boolean` (= 9)
- `IShellService.stopVideoRecording(): boolean` (= 10), true when the MP4 contains video
- `IVideoRecordingCallback.onVideoRecordingFailure(boolean hasContent)` for failures after a successful start

## App side

- `RawCallData` / `EnrichedCallData`: new `isVideoCall` (default false).
- `InCallService`: reads `VideoProfile.isVideo(details.videoState)` when the call becomes active, and
  listens to `onDetailsChanged` to tell the foreground service when a call turns into a video call.
- `CameraUseDetector` (new): `CameraManager.AvailabilityCallback`; reports "a camera is in use".
- `VideoTriggerPolicy` (new, pure): decides whether a trigger starts video.
  - Feature toggle off -> never.
  - MANUAL -> start if not already recording video.
  - TELECOM -> start unless the user stopped video by hand in this call.
  - CAMERA -> as TELECOM, and only for app/VoIP calls (third-party package or VoIP audio mode).
- Video is sticky: once started it runs until the recording ends or the user taps Stop video.
- `RecordingSession`: `startVideo()` creates the MP4 through SAF and hands it to the shell;
  `stopVideo()` stops it and deletes the file when it has no video; `release()` / `cancel()` stop video first.
- `RecordingServiceState.Active`: new `isVideoRecording`.
- `RecordingForegroundService`: actions `START_VIDEO`, `STOP_VIDEO`, `VIDEO_CALL_DETECTED`; owns the
  camera detector while a session is active.
- Notification: second action "Record video" / "Stop video" while a recording is active and the feature is on.
- Settings: one toggle, "Record video on video calls", default off. English strings only.

## Failure handling

- Video can never end the audio recording. Every video error is caught inside the video units.
- Start failure: `startVideoRecording` returns false, the app deletes the empty MP4 and shows an error notification.
- Failure after start: callback, error notification, file kept if it has video, deleted otherwise.
- If the muxer has not started 5 s after start (no frames), the session fails.

## Known limits

- The whole screen is recorded, including other apps and notifications shown during the call.
- Windows marked secure are black.
- Rotation mid-call letterboxes; the video size is fixed at start.
- Whether WhatsApp reports its video state to Telecom is unverified; camera detection and the manual
  button cover it.
- Requires Android 14+ (API 34): the DisplayManager mirror API does not exist on Android 11 and 12, and VoIP audio
  capture also needs 14+. Below that the setting is hidden.

## Fork obligations (upstream licence, additional terms 3 and 4)

- `applicationId` -> `com.fluxism.shizuvideocallrecorder` (code namespace unchanged).
- App name -> `ShizuVideoCallRecorder`.
- About section and README: "This software is a fork of ShizuCallRecorder, available at
  https://github.com/kitsumed/ShizuCallRecorder."
- Repository and issue links in the app point to the fork, so fork bugs are not reported upstream.

## Testing

- JVM unit tests (new, JUnit 4): `VideoSizeCalculator`, `Mp4Timeline`, `VideoTriggerPolicy`, video file naming.
- Debug-only self-test entry point, run as the shell user with `app_process` over adb: mirrors the screen
  for a few seconds with generated audio and writes an MP4, which is then pulled and inspected with ffprobe.
  This exercises the real capture, encode and mux path without needing a call.
- End-to-end with a real WhatsApp video call on the phone.
