/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services.shell.recording

import android.media.AudioFormat
import com.kitsumed.shizucallrecorder.BuildConfig
import com.kitsumed.shizucallrecorder.data.recording.RecordingCodec

/**
 * Constants shared by the native audio capture and processing pipeline.
 *
 * Codec-specific values such as bitrate belong to [RecordingCodec] instead.
 */
object AudioConfig {

    /**
     * PCM sample rate (kHz) used by every native AudioRecord capture.
     *
     * Note: Some Android native API enforces a maximum of 16 kHz when capture is defined with system level access/high-priority capture. Most/all devices support 16 kHz as their best,
     * which is conveniently known as HD voice quality so this is still good.
     * Source Android 17:
     * - https://cs.android.com/android/platform/superproject/+/android17-release:frameworks/av/services/audiopolicy/config/audio_policy_configuration.xml;l=76-79
     * - https://cs.android.com/android/platform/superproject/+/android17-release:frameworks/av/services/audiopolicy/enginedefault/src/Engine.cpp;l=571-757
     */
    const val SAMPLE_RATE = 16_000

    /**
     * Number of channels produced by each individual AudioRecord capture.
     *
     * Each capture source is always read as mono.
     */
    const val CAPTURE_CHANNEL_COUNT = 1

    /**
     * The mask used to configure the channel layout of each individual AudioRecord capture.
     * Based on [CAPTURE_CHANNEL_COUNT] (mono).
     */
    const val CAPTURE_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

    /**
     * PCM encoding used by AudioRecord.
     */
    const val PCM_ENCODING = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Number of bytes occupied by one PCM16 sample.
     */
    const val PCM_BYTES_PER_SAMPLE = 2

    /**
     * Define the duration of one processing block.
     *
     * Short blocks keep latency low while avoiding excessively small reads.
     */
    const val BLOCK_DURATION_MS = 20

    /**
     * Number of mono PCM samples in one processing block.
     */
    const val BLOCK_SAMPLE_COUNT = SAMPLE_RATE * BLOCK_DURATION_MS / 1_000

    /**
     * Number of PCM bytes used in one mono processing block.
     */
    const val BLOCK_SIZE_BYTES = BLOCK_SAMPLE_COUNT * PCM_BYTES_PER_SAMPLE

    /**
     * Number of PCM bytes used in one stereo processing block. (Duplicated from mono [BLOCK_SIZE_BYTES])
     */
    const val STEREO_BLOCK_SIZE_BYTES = BLOCK_SIZE_BYTES * 2 // 2 channels = stereo

    /**
     * Number of blocks between normal capture diagnostic log messages.
     *
     * At 20 ms/block, 50 blocks correspond to approximately one second.
     * To reduce log spam, the interval is increased to 500 blocks in release builds. (10 seconds-ish)
     */
    val LOG_INTERVAL_BLOCKS = if (BuildConfig.DEBUG) 50L else 500L

    /**
     * Number of consecutive zero-byte reads between repeated zero-read warnings before printing.
     *
     * This is used to avoid flooding the shell log with repeated warnings when the native AudioRecord capture returns zero bytes.
     */
    const val ZERO_READ_WARNING_INTERVAL = 50L

    /*
     * FORK NOTE (ShizuVideoCallRecorder): upstream commit 42ba24d ("Fix Silence Detection") uses the three constants below in
     * ShellRecordingEngine but never added them, so upstream does not compile at that commit. The values here are the fork's
     * own conservative choices, not upstream's. Replace them with upstream's values once upstream publishes them.
     */

    /**
     * Number of consecutive silent blocks from one capture before a warning is logged.
     *
     * At 20 ms/block, 250 blocks correspond to approximately five seconds.
     */
    const val SILENCE_WARNING_INTERVAL_BLOCKS = 250L

    /**
     * Number of consecutive silent blocks, on every capture and without any audio captured before, after which the session is
     * considered failed and its file is deleted.
     *
     * At 20 ms/block, 1500 blocks correspond to approximately thirty seconds.
     */
    const val SILENCE_FAILURE_INTERVAL_BLOCKS = 1_500L

    /**
     * A block is silent when no sample has an absolute value above this. Zero means only true digital silence counts,
     * which is what Android delivers when it silences a capture; a live microphone always has some noise.
     */
    const val SILENCE_FAILURE_AMPLITUDE = 0
}