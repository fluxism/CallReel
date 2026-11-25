/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the CallReel fork (video call recording).
 */

package com.kitsumed.shizucallrecorder.services.shell.recording.video

/**
 * Constants of the video recording pipeline (screen mirror -> H.264 + AAC -> MP4).
 */
object VideoConfig {
    /** The long side of the encoded video is at most this many pixels. The display is scaled down to fit. */
    const val MAX_LONG_SIDE = 1280

    /** Target and maximum frame rate of the encoded video. */
    const val FRAME_RATE = 30

    /** Video bitrate in bits per second. */
    const val VIDEO_BIT_RATE = 2_000_000

    /** Seconds between key frames. Short, so the file seeks well and resumes quickly after a pause. */
    const val I_FRAME_INTERVAL_SECONDS = 2

    /** When the screen does not change, the encoder repeats the last frame after this delay, so the video never stalls. */
    const val REPEAT_FRAME_DELAY_US = 100_000L

    /** AAC bitrate per audio channel, in bits per second. */
    const val AUDIO_BIT_RATE_PER_CHANNEL = 32_000

    /** The session fails when no video frame was written this long after the start (while not paused). */
    const val FIRST_FRAME_TIMEOUT_US = 5_000_000L

    /** How many 20 ms audio blocks can wait for the MP4 audio encoder. When full, new blocks are dropped. */
    const val AUDIO_QUEUE_BLOCKS = 100

    /** How many encoded samples can wait for the muxer to start. */
    const val MAX_PENDING_SAMPLES = 300

    /** How long stop() waits for each worker thread. */
    const val THREAD_JOIN_TIMEOUT_MS = 3_000L

    /** Name of the virtual display, visible in "dumpsys display". */
    const val VIRTUAL_DISPLAY_NAME = "callreel-video"
}
