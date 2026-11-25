# Video Call Recording Implementation Plan

> Historical document. The fork was later renamed from ShizuVideoCallRecorder to **CallReel** (`com.fluxism.callreel`, repository `fluxism/CallReel`), and the video bitrate was lowered to 2 Mbit/s.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record the screen as an MP4 (H.264 + AAC) next to the unchanged audio file whenever a recorded call is a video call.

**Architecture:** A second, independent pipeline in the Shizuku shell process mirrors the main display into a MediaCodec surface encoder and muxes it with an AAC copy of the processed call audio. The app decides when to start it (Telecom video state, camera in use, or a notification button) and owns the MP4 file through SAF.

**Tech Stack:** Kotlin, Android MediaCodec/MediaMuxer, hidden `DisplayManager.createVirtualDisplay` mirror API (Android 14+), Shizuku user service + AIDL, Jetpack Compose settings, JUnit 4.

**Spec:** `dev-docs/specs/2026-11-21-video-call-recording-design.md`

**Conventions:** all paths are relative to the worktree root. `SRC` = `app/src/main/java/com/kitsumed/shizucallrecorder`, `TEST` = `app/src/test/java/com/kitsumed/shizucallrecorder`. Build with `ANDROID_HOME=$HOME/Android/Sdk ./gradlew --no-daemon <task>`. Every new Kotlin file starts with the project licence header. Commit after each task with the repo's `type(scope): Message` style.

Video needs Android 14+ (API 34): that is where the mirror API exists and where VoIP audio capture works. Below that the feature is hidden.

---

### Task 0: Baseline build and unit-test infrastructure

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`.

- [ ] Run `:app:assembleDebug` on the untouched tree; it must succeed before anything changes.
- [ ] Add `junit = "4.13.2"` and `junit = { module = "junit:junit", version.ref = "junit" }` to the catalog and `testImplementation(libs.junit)` to the app module.
- [ ] Run `:app:testDebugUnitTest`; expected: success with no tests.
- [ ] Commit.

### Task 1: Fork identity (licence terms 3 and 4)

**Files:** Modify `app/build.gradle.kts`, `app/src/main/res/values/strings.xml`, `SRC/AppUrls.kt`, `SRC/ui/screens/SettingsScreen.kt` (AboutSection), `README.md`.

- [ ] `applicationId = "com.fluxism.shizuvideocallrecorder"`, `versionName = "2.0.0-video.1"`; namespace stays.
- [ ] Grep for code that assumes applicationId == namespace (`ComponentName(`, `packageName`, `BuildConfig.APPLICATION_ID`) and fix any use that builds a class name from the application id.
- [ ] `app_name` -> `ShizuVideoCallRecorder`; new string `about_fork_notice` = "This software is a fork of ShizuCallRecorder, available at https://github.com/kitsumed/ShizuCallRecorder."
- [ ] `AppUrls`: add `UPSTREAM_REPOSITORY` (kitsumed), point `GITHUB_REPOSITORY` and `GITHUB_NEW_ISSUE` at fluxism/ShizuVideoCallRecorder.
- [ ] AboutSection: show the notice, tappable, opening `UPSTREAM_REPOSITORY`.
- [ ] README: fork notice at the top, feature bullet for video.
- [ ] `:app:assembleDebug`, commit.

### Task 2: VideoSizeCalculator (pure, TDD)

**Files:** Create `SRC/services/shell/recording/video/VideoSizeCalculator.kt`, `TEST/services/shell/recording/video/VideoSizeCalculatorTest.kt`.

```kotlin
object VideoSizeCalculator {
    data class Size(val width: Int, val height: Int)
    /** Aspect kept, never upscaled, both sides aligned down to an even multiple of [alignment]. */
    fun calculate(displayWidth: Int, displayHeight: Int, maxLongSide: Int, alignment: Int): Size
}
```

Test cases (write first, watch them fail, then implement):
- 1080x2400, max 1280, align 2 -> 576x1280
- 2400x1080, max 1280, align 2 -> 1280x576
- 1440x3120, max 1280, align 16 -> 576x1280
- 720x1280, max 1920, align 2 -> 720x1280 (no upscale)
- 1081x2401, max 5000, align 2 -> 1080x2400
- alignment 1 behaves as 2; zero or negative display size throws IllegalArgumentException

### Task 3: Mp4Timeline (pure, TDD)

**Files:** Create `SRC/services/shell/recording/video/Mp4Timeline.kt`, `TEST/.../video/Mp4TimelineTest.kt`.

```kotlin
class Mp4Timeline(private val startUs: Long, private val audioSampleRate: Int, private val maxAudioDriftUs: Long = 200_000L) {
    @Synchronized fun pause(nowUs: Long)
    @Synchronized fun resume(nowUs: Long)
    /** PTS for a video frame stamped [rawUs] and seen at [nowUs]; null = drop (paused or not increasing). */
    @Synchronized fun videoPtsUs(rawUs: Long, nowUs: Long): Long?
    /** PTS for an audio block of [frameCount] frames that finished arriving at [nowUs]. */
    @Synchronized fun audioPtsUs(nowUs: Long, frameCount: Int): Long
}
```

Rules: active time = now - start - paused time. Video: first frame calibrates a clock offset when its stamp is more than 500 ms away from `nowUs`; frames while paused or not after the previous PTS are dropped. Audio: sample-count clock; when the clock is more than `maxAudioDriftUs` ahead of it, jump forward to the clock; never move backward.

Test cases:
- video frame 1 s after start -> 1_000_000
- video frames during pause -> null; after a 2 s pause, a frame 3 s after start -> 1_000_000
- non-increasing video stamp -> null
- video stamps in a clock domain offset by 10 s -> calibrated so the first frame maps to its arrival time
- audio: consecutive 20 ms blocks arriving on time -> 0, 20_000, 40_000
- audio: a 1 s gap in arrivals -> next PTS jumps to about the clock; a burst after it never goes backward
- audio across pause/resume stays contiguous

### Task 4: DisplayMirror

**Files:** Create `SRC/services/shell/recording/video/DisplayMirror.kt`.

```kotlin
object DisplayMirror {
    data class DisplaySize(val width: Int, val height: Int)
    fun getMainDisplaySize(): DisplaySize          // DisplayManagerGlobal.getInstance().getDisplayInfo(0): logicalWidth/logicalHeight
    fun create(name: String, width: Int, height: Int, surface: Surface): VirtualDisplay   // static DisplayManager.createVirtualDisplay(String,int,int,int,Surface), displayIdToMirror = 0
}
```

No unit test (hidden framework API); covered by the self-test in Task 8.

### Task 5: Mp4Writer

**Files:** Create `SRC/services/shell/recording/video/Mp4Writer.kt`.

```kotlin
class Mp4Writer(fd: FileDescriptor) {
    enum class Track { VIDEO, AUDIO }
    @Synchronized fun setFormat(track: Track, format: MediaFormat)      // starts the muxer once both are set
    @Synchronized fun write(track: Track, buffer: ByteBuffer, info: MediaCodec.BufferInfo)   // copies and queues (max 300 samples) until started
    val hasVideo: Boolean                                                // at least one video sample written to the muxer
    @Synchronized fun finish()                                           // stop + release, idempotent, never throws
}
```

### Task 6: ShellVideoRecorder

**Files:** Create `SRC/services/shell/recording/video/VideoConfig.kt`, `SRC/services/shell/recording/video/ShellVideoRecorder.kt`.

```kotlin
class ShellVideoRecorder(outputFile: ParcelFileDescriptor, private val audioChannelCount: Int, private val onFailure: (hasContent: Boolean) -> Unit) {
    fun start()                                   // throws on setup failure, after releasing what it created
    fun offerAudio(pcm: ByteBuffer, frameCount: Int)   // non-blocking copy into a bounded queue; drops when full
    fun pause(); fun resume()
    fun stop(): Boolean                           // finalises the MP4, closes the fd, returns hasVideo; idempotent
}
```

- Video: `video/avc`, size from `VideoSizeCalculator` using the codec's width/height alignment, `COLOR_FormatSurface`, bitrate/fps/key-frame interval from `VideoConfig`, `KEY_REPEAT_PREVIOUS_FRAME_AFTER` = 100 ms. Drain thread: 100 ms dequeue timeout, timestamps through `Mp4Timeline.videoPtsUs`, fail when no video sample was written 5 s after start while not paused.
- Audio: AAC-LC at `AudioConfig.SAMPLE_RATE`, one priming block of silence at start so the muxer can start immediately, blocks taken from an `ArrayBlockingQueue` (100 pooled buffers), timestamps through `Mp4Timeline.audioPtsUs`.
- Any throwable in either thread: log, stop, call `onFailure(hasVideo)` once.

### Task 7: AIDL, ShellService and engine wiring

**Files:** Create `app/src/main/aidl/com/kitsumed/shizucallrecorder/IVideoRecordingCallback.aidl`. Modify `IShellService.aidl`, `SRC/services/shell/ShellService.kt`, `SRC/services/shell/recording/ShellRecordingEngine.kt`.

- [ ] `interface IVideoRecordingCallback { oneway void onVideoRecordingFailure(boolean hasContent); }`
- [ ] `boolean startVideoRecording(in ParcelFileDescriptor outputFile, in IVideoRecordingCallback callback) = 9;` and `boolean stopVideoRecording() = 10;`
- [ ] Engine: `startVideo`/`stopVideo`, `@Volatile videoRecorder`, offer PCM in `encoderLoop` (a `duplicate()` of the flipped output buffer, before `encoder.encode`), forward pause/resume, stop video first in `stop()`. `startVideo` returns false when no audio session is active.
- [ ] `:app:assembleDebug`, commit.

### Task 8: Shell self-test on the phone

**Files:** Create `app/src/debug/java/com/kitsumed/shizucallrecorder/debug/VideoSelfTest.kt`.

- [ ] `object VideoSelfTest { @JvmStatic fun main(args: Array<String>) }`: applies `ShellWorkarounds`, opens `args[0]` as a read-write `ParcelFileDescriptor`, runs `ShellVideoRecorder` for `args[1]` seconds while offering a 440 Hz tone in 20 ms blocks, prints `RESULT hasVideo=<bool>`.
- [ ] Install the debug APK, then run as shell: `adb shell CLASSPATH=$(adb shell pm path com.fluxism.shizuvideocallrecorder | cut -d: -f2) app_process / com.kitsumed.shizucallrecorder.debug.VideoSelfTest /data/local/tmp/selftest.mp4 6`
- [ ] `adb pull` the file; `ffprobe` must show one h264 stream and one aac stream, both about 6 s, and a frame extracted with ffmpeg must show the phone screen.
- [ ] Fix whatever fails, commit.

### Task 9: Preference, setting and strings

**Files:** Modify `SRC/data/AppPreferences.kt`, `SRC/ui/viewmodels/SettingsViewModel.kt`, `SRC/ui/screens/SettingsScreen.kt`, `app/src/main/res/values/strings.xml`. Create `app/src/main/res/drawable/ic_videocam.xml`, `ic_videocam_off.xml`.

- [ ] Key `VIDEO_RECORDING_ENABLED("video_recording_enabled")`, default false, `isVideoRecordingEnabled()` / `setVideoRecordingEnabled()`.
- [ ] `SettingsActions.setVideoRecordingEnabled`, implemented in the view model and the preview stub.
- [ ] Toggle in `RecordingSection`, shown only on API 34+, with a subtitle that says the whole screen is recorded.
- [ ] Strings: setting title/subtitle, `general_record_video`, `general_stop_video`, video started/stopped toasts, `recording_error_video_failed`.

### Task 10: Trigger policy, file naming, call data (pure parts TDD)

**Files:** Create `SRC/services/recording/video/VideoTriggerPolicy.kt`, `SRC/utils/VideoFileNameFormatter.kt`, tests for both. Modify `SRC/data/call/RawCallData.kt`, `EnrichedCallData.kt`, `SRC/services/callDetection/incall/InCallService.kt`.

```kotlin
enum class VideoTrigger { MANUAL, TELECOM, CAMERA }
object VideoTriggerPolicy {
    fun shouldStartVideo(trigger: VideoTrigger, isFeatureEnabled: Boolean, isVideoRecording: Boolean, wasStoppedByUser: Boolean, isAppOrVoipCall: Boolean): Boolean
}
object VideoFileNameFormatter {
    const val EXTENSION = ".mp4"; const val MIME_TYPE = "video/mp4"
    fun fromAudioFileName(audioFileName: String, audioExtension: String): String
}
```

Policy tests: feature off -> false for every trigger; already recording -> false; MANUAL ignores `wasStoppedByUser`; TELECOM and CAMERA respect it; CAMERA needs `isAppOrVoipCall`.
Naming tests: `"a.ogg", ".ogg"` -> `"a.mp4"`; `"2026/11/x.m4a", ".m4a"` -> `"2026/11/x.mp4"`; a name without the extension just gets `.mp4` appended; `"a.ogg.ogg"` -> `"a.ogg.mp4"`.

- [ ] `isVideoCall: Boolean = false` on both call data classes, copied in every `EnrichedCallData` construction.
- [ ] `InCallService`: set it from `VideoProfile.isVideo(details.videoState)`; `onDetailsChanged` sends `ACTION_VIDEO_CALL_DETECTED` to the foreground service once per call when the tracked, already-recorded call becomes video.

### Task 11: Session, service state, service actions, camera detector, notification

**Files:** Create `SRC/services/recording/video/CameraUseDetector.kt`. Modify `SRC/services/recording/RecordingSession.kt`, `RecordingServiceState.kt`, `RecordingForegroundService.kt`, `RecordingNotificationHelper.kt`.

- [ ] `RecordingSession`: keep the audio file name; `startVideo(onFailure: (hasContent: Boolean) -> Unit): Boolean`, `stopVideo()`, `isVideoActive`; video is stopped inside `release()` before `stopRecording()`; an MP4 without video is deleted.
- [ ] `RecordingServiceState.Active.isVideoRecording: Boolean = false`.
- [ ] Service: `ACTION_START_VIDEO`, `ACTION_STOP_VIDEO`, `ACTION_VIDEO_CALL_DETECTED`; one `requestVideo(trigger)` that asks `VideoTriggerPolicy`; `CameraUseDetector` registered when a session becomes active and the feature is on, released when it ends; TELECOM trigger fired right after session start when `metadata.isVideoCall`.
- [ ] Notification: second action while active and the feature is on (request code 2).
- [ ] `:app:assembleDebug` and `:app:testDebugUnitTest`, commit.

### Task 12: On-device end-to-end and docs

- [ ] Install on the phone, grant what adb can grant, confirm the app starts and the setting appears.
- [ ] With the user: WhatsApp video call -> audio file plus MP4 in the recording folder; check logs for which trigger fired.
- [ ] Update `docs/configuration.md` with the video setting and its limits.
- [ ] Final `:app:assembleDebug :app:testDebugUnitTest`, commit.
