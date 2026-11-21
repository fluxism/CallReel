/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the ShizuVideoCallRecorder fork (video call recording).
 */

package com.kitsumed.shizucallrecorder.services.shell.recording.video

import kotlin.math.abs

/**
 * Puts the video and audio samples of one MP4 on a single time axis.
 *
 * The axis is "active time": time since the video recording started, minus the time spent paused. This keeps
 * both tracks contiguous across pause/resume and keeps them in sync with each other.
 *
 * All values are in microseconds. [startUs] and every `nowUs` must come from the same monotonic clock.
 *
 * @param startUs The clock value at which the video recording started.
 * @param audioSampleRate The sample rate of the audio track, in Hz.
 * @param maxAudioDriftUs How far the audio sample clock may fall behind the real clock before it is moved forward.
 */
class Mp4Timeline(
    private val startUs: Long,
    private val audioSampleRate: Int,
    private val maxAudioDriftUs: Long = 200_000L,
) {
    companion object {
        /** A first video timestamp further than this from the clock means the frames use another clock domain. */
        private const val CLOCK_MISMATCH_US = 500_000L
    }

    private var pausedTotalUs = 0L
    private var pauseStartUs: Long? = null

    private var videoClockOffsetUs: Long? = null
    private var lastVideoPtsUs = -1L

    /** The audio sample clock: [audioAnchorUs] plus the duration of [audioFramesSinceAnchor] frames. */
    private var audioAnchorUs = 0L
    private var audioFramesSinceAnchor = 0L

    val isPaused: Boolean
        @Synchronized get() = pauseStartUs != null

    @Synchronized
    fun pause(nowUs: Long) {
        if (pauseStartUs == null) pauseStartUs = nowUs
    }

    @Synchronized
    fun resume(nowUs: Long) {
        val pausedAt = pauseStartUs ?: return
        pausedTotalUs += maxOf(0L, nowUs - pausedAt)
        pauseStartUs = null
    }

    /**
     * Returns the presentation time for a video frame, or null when the frame must be dropped
     * (recording paused, or the frame is not later than the previous one).
     *
     * @param rawUs The timestamp the encoder reported for the frame.
     * @param nowUs The current clock value.
     */
    @Synchronized
    fun videoPtsUs(rawUs: Long, nowUs: Long): Long? {
        if (pauseStartUs != null) return null

        // Frames are normally stamped with the same monotonic clock we use. If the first one is far away from it,
        // the producer uses another clock domain, so we measure the offset once and keep it.
        val offsetUs = videoClockOffsetUs ?: (if (abs(rawUs - nowUs) > CLOCK_MISMATCH_US) rawUs - nowUs else 0L).also { videoClockOffsetUs = it }

        val ptsUs = activeTimeUs(rawUs - offsetUs)
        if (ptsUs <= lastVideoPtsUs) return null

        lastVideoPtsUs = ptsUs
        return ptsUs
    }

    /**
     * Returns the presentation time for an audio block.
     *
     * Audio normally follows its own sample clock, which is what keeps it gap-free. When blocks were lost and the
     * sample clock falls more than [maxAudioDriftUs] behind the real clock, it is moved forward to the real clock.
     * It never moves backward.
     *
     * @param nowUs The clock value at which the block finished arriving.
     * @param frameCount The number of audio frames in the block.
     */
    @Synchronized
    fun audioPtsUs(nowUs: Long, frameCount: Int): Long {
        val blockDurationUs = frameCount * 1_000_000L / audioSampleRate
        val clockUs = activeTimeUs(nowUs) - blockDurationUs
        val sampleClockUs = audioAnchorUs + audioFramesSinceAnchor * 1_000_000L / audioSampleRate

        val ptsUs = if (clockUs - sampleClockUs > maxAudioDriftUs) {
            audioAnchorUs = clockUs
            audioFramesSinceAnchor = 0L
            clockUs
        } else {
            sampleClockUs
        }

        audioFramesSinceAnchor += frameCount
        return ptsUs
    }

    private fun activeTimeUs(clockUs: Long): Long {
        val currentPauseUs = pauseStartUs?.let { maxOf(0L, clockUs - it) } ?: 0L
        return maxOf(0L, clockUs - startUs - pausedTotalUs - currentPauseUs)
    }
}
