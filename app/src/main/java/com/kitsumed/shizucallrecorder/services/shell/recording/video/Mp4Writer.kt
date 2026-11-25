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

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Writes one video track and one audio track into an MP4 file.
 *
 * A MediaMuxer can only start once the format of every track is known, and the two encoders report their formats at
 * different times. Samples that arrive earlier are copied and kept until the muxer starts.
 *
 * All methods are thread-safe: the video and audio encoders write from their own threads.
 */
class Mp4Writer(outputFileDescriptor: FileDescriptor) {

    enum class Track { VIDEO, AUDIO }

    private class PendingSample(val track: Track, val data: ByteBuffer, val info: MediaCodec.BufferInfo)

    private val muxer = MediaMuxer(outputFileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    private val formats = arrayOfNulls<MediaFormat>(Track.entries.size)
    private val trackIndexes = IntArray(Track.entries.size) { -1 }
    private val pendingSamples = ArrayDeque<PendingSample>()

    private var started = false
    private var finished = false
    private var droppedPendingSamples = 0

    /** True once at least one video sample was written to the file. */
    @Volatile
    var hasVideo = false
        private set

    /**
     * Declares the output format of a track. The muxer starts when both tracks are declared.
     */
    @Synchronized
    fun setFormat(track: Track, format: MediaFormat) {
        if (finished || started || formats[track.ordinal] != null) return
        formats[track.ordinal] = format

        if (formats.any { it == null }) return

        for (entry in Track.entries) {
            trackIndexes[entry.ordinal] = muxer.addTrack(formats[entry.ordinal]!!)
        }
        muxer.start()
        started = true
        AppLogger.i("MP4 muxer started. pendingSamples=${pendingSamples.size}")

        while (pendingSamples.isNotEmpty()) {
            val sample = pendingSamples.removeFirst()
            writeNow(sample.track, sample.data, sample.info)
        }
    }

    /**
     * Writes an encoded sample. [info] must describe the valid region of [buffer] and carry the final timestamp.
     */
    @Synchronized
    fun write(track: Track, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (finished || info.size <= 0) return

        if (started) {
            writeNow(track, buffer, info)
            return
        }

        if (pendingSamples.size >= VideoConfig.MAX_PENDING_SAMPLES) {
            if (droppedPendingSamples++ == 0) AppLogger.w("MP4 muxer has not started yet and the pending queue is full. Dropping samples.")
            return
        }

        // The codec buffer is reused as soon as we return, so the sample must be copied.
        val source = buffer.duplicate()
        source.position(info.offset)
        source.limit(info.offset + info.size)
        val copy = ByteBuffer.allocate(info.size)
        copy.put(source)
        copy.flip()

        val copyInfo = MediaCodec.BufferInfo()
        copyInfo.set(0, info.size, info.presentationTimeUs, info.flags)
        pendingSamples.addLast(PendingSample(track, copy, copyInfo))
    }

    /**
     * Finalizes the file. Safe to call more than once; never throws.
     */
    @Synchronized
    fun finish() {
        if (finished) return
        finished = true
        pendingSamples.clear()

        if (started) {
            runCatching { muxer.stop() }.onFailure {
                // MediaMuxer refuses to finalize a file without any sample.
                AppLogger.w("Failed to stop the MP4 muxer. The file may be unusable.", it)
                hasVideo = false
            }
        } else {
            hasVideo = false
        }
        runCatching { muxer.release() }.onFailure { AppLogger.w("Failed to release the MP4 muxer.", it) }
    }

    private fun writeNow(track: Track, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        muxer.writeSampleData(trackIndexes[track.ordinal], buffer, info)
        if (track == Track.VIDEO) hasVideo = true
    }
}
