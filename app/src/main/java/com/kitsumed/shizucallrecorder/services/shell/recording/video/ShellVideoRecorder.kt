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

import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.view.Surface
import com.kitsumed.shizucallrecorder.services.shell.recording.AudioConfig
import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Records the main display and a copy of the call audio into one MP4 file (H.264 + AAC-LC).
 *
 * This runs next to the audio recording and is fully independent from it: nothing in here can block or fail the
 * audio pipeline. [offerAudio] only copies a PCM block into a bounded queue, and every error is contained.
 *
 * One instance records one file. It owns [outputFile] and closes it.
 *
 * @param outputFile The writable (read-write) descriptor of the MP4 file.
 * @param audioChannelCount 1 for mono or 2 for interleaved stereo PCM16 at [AudioConfig.SAMPLE_RATE].
 * @param onFailure Called once, from a worker thread, when the recording fails after a successful [start].
 *                  The recorder is already stopped at that point. The argument tells whether the file contains video.
 */
class ShellVideoRecorder(
    private val outputFile: ParcelFileDescriptor,
    private val audioChannelCount: Int,
    private val onFailure: (hasContent: Boolean) -> Unit,
) {
    /** One PCM block waiting for the MP4 audio encoder. Instances are pooled. */
    private class AudioBlock(capacity: Int) {
        val data: ByteBuffer = ByteBuffer.allocateDirect(capacity)
        var frameCount = 0
        var arrivalUs = 0L
    }

    private val running = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)
    private val stopLock = Any()
    private var finished = false
    private var result = false

    private var startUs = 0L
    private var timeline: Mp4Timeline? = null
    private var writer: Mp4Writer? = null

    private var videoCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioCodec: MediaCodec? = null

    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    /** Set when frames were dropped: the next frame written must be a key frame, or the picture would be corrupted. */
    @Volatile
    private var needKeyFrame = true

    private val audioBlockCapacity = AudioConfig.BLOCK_SIZE_BYTES * audioChannelCount
    private val freeAudioBlocks = ArrayBlockingQueue<AudioBlock>(VideoConfig.AUDIO_QUEUE_BLOCKS)
    private val readyAudioBlocks = ArrayBlockingQueue<AudioBlock>(VideoConfig.AUDIO_QUEUE_BLOCKS)
    private var droppedAudioBlocks = 0L

    private fun nowUs(): Long = System.nanoTime() / 1_000L

    /**
     * Starts mirroring the display and encoding.
     *
     * @throws Throwable when anything fails to initialize. Everything created so far is released and
     *                   [outputFile] is closed before the exception leaves this method.
     */
    fun start() {
        check(running.compareAndSet(false, true)) { "This video recorder was already started." }

        try {
            require(audioChannelCount == 1 || audioChannelCount == 2) { "Unsupported audio channel count: $audioChannelCount" }

            repeat(VideoConfig.AUDIO_QUEUE_BLOCKS) { freeAudioBlocks.add(AudioBlock(audioBlockCapacity)) }

            startUs = nowUs()
            timeline = Mp4Timeline(startUs = startUs, audioSampleRate = AudioConfig.SAMPLE_RATE)
            writer = Mp4Writer(outputFile.fileDescriptor)

            startVideoEncoder()
            startAudioEncoder()

            videoThread = thread(name = "ShellVideoRecorder-VideoThread", isDaemon = true) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                runWorker("video") { videoLoop() }
            }
            audioThread = thread(name = "ShellVideoRecorder-AudioThread", isDaemon = true) {
                runWorker("audio") { audioLoop() }
            }

            AppLogger.i("Video recording started.")
        } catch (t: Throwable) {
            AppLogger.e("Failed to start the video recording.", t)
            stop()
            throw t
        }
    }

    private fun startVideoEncoder() {
        val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
        val codec = MediaCodec.createEncoderByType(mimeType)
        videoCodec = codec

        val capabilities = codec.codecInfo.getCapabilitiesForType(mimeType).videoCapabilities
            ?: throw IllegalStateException("The encoder '${codec.name}' reports no video capabilities.")
        val alignment = maxOf(capabilities.widthAlignment, capabilities.heightAlignment)

        val displaySize = DisplayMirror.getMainDisplaySize()
        val videoSize = VideoSizeCalculator.calculate(displaySize.width, displaySize.height, VideoConfig.MAX_LONG_SIDE, alignment)
        AppLogger.i(
            "Video encoder '${codec.name}': display=${displaySize.width}x${displaySize.height}, " +
                "video=${videoSize.width}x${videoSize.height}, alignment=$alignment, " +
                "sizeSupported=${capabilities.isSizeSupported(videoSize.width, videoSize.height)}"
        )

        val format = MediaFormat.createVideoFormat(mimeType, videoSize.width, videoSize.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VideoConfig.VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VideoConfig.FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoConfig.I_FRAME_INTERVAL_SECONDS)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, VideoConfig.REPEAT_FRAME_DELAY_US)
            // The display can refresh at 60-120 Hz. This drops the extra frames before they reach the encoder.
            setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, VideoConfig.FRAME_RATE.toFloat())
        }

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        inputSurface = surface
        virtualDisplay = DisplayMirror.create(VideoConfig.VIRTUAL_DISPLAY_NAME, videoSize.width, videoSize.height, surface)
        codec.start()
    }

    private fun startAudioEncoder() {
        val mimeType = MediaFormat.MIMETYPE_AUDIO_AAC
        val codec = MediaCodec.createEncoderByType(mimeType)
        audioCodec = codec

        val format = MediaFormat.createAudioFormat(mimeType, AudioConfig.SAMPLE_RATE, audioChannelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, VideoConfig.AUDIO_BIT_RATE_PER_CHANNEL * audioChannelCount)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBlockCapacity * 2)
        }

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /**
     * Hands one block of processed call audio to the MP4. Never blocks: when the queue is full the block is dropped.
     *
     * @param pcm PCM16 data between the buffer's position and limit. The buffer is not modified.
     * @param frameCount The number of audio frames (samples per channel) in the block.
     */
    fun offerAudio(pcm: ByteBuffer, frameCount: Int) {
        if (!running.get()) return

        val block = freeAudioBlocks.poll()
        if (block == null) {
            if (droppedAudioBlocks++ % 50L == 0L) AppLogger.w("The MP4 audio encoder is lagging behind. Dropped $droppedAudioBlocks audio blocks so far.")
            return
        }

        val source = pcm.duplicate()
        if (source.remaining() > block.data.capacity()) source.limit(source.position() + block.data.capacity())
        block.data.clear()
        block.data.put(source)
        block.data.flip()
        block.frameCount = frameCount
        block.arrivalUs = nowUs()

        if (!readyAudioBlocks.offer(block)) freeAudioBlocks.offer(block)
    }

    fun pause() {
        timeline?.pause(nowUs())
    }

    fun resume() {
        timeline?.resume(nowUs())
        // Everything encoded during the pause was dropped, so the next frame we keep must be a key frame.
        needKeyFrame = true
        requestKeyFrame()
    }

    /**
     * Stops the recording and finalizes the file. Safe to call more than once and from any thread.
     *
     * @return true when the file contains video.
     */
    fun stop(): Boolean {
        stopRequested.set(true)
        synchronized(stopLock) {
            if (finished) return result

            AppLogger.i("Stopping the video recording...")
            running.set(false)

            // Ask the video encoder to flush, so the drain thread sees the end of the stream and exits.
            runCatching { videoCodec?.signalEndOfInputStream() }
            joinWorker(videoThread)
            joinWorker(audioThread)
            videoThread = null
            audioThread = null

            runCatching { virtualDisplay?.release() }.onFailure { AppLogger.w("Failed to release the virtual display.", it) }
            virtualDisplay = null

            releaseCodec(videoCodec, "video")
            videoCodec = null
            runCatching { inputSurface?.release() }
            inputSurface = null
            releaseCodec(audioCodec, "audio")
            audioCodec = null

            val currentWriter = writer
            currentWriter?.finish()
            result = currentWriter?.hasVideo == true
            writer = null

            runCatching { outputFile.close() }.onFailure { AppLogger.w("Failed to close the video output descriptor.", it) }

            finished = true
            AppLogger.i("Video recording stopped. hasVideo=$result")
            return result
        }
    }

    // ---- Worker threads

    private inline fun runWorker(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (stopRequested.get()) {
                // Codecs are torn down during stop(); an error at that point is expected and already handled there.
                AppLogger.d("The $name worker ended during shutdown: $t")
                return
            }
            if (!failureReported.compareAndSet(false, true)) return

            AppLogger.e("The $name worker of the video recording failed.", t)
            // stop() joins the worker threads, so it must not run on one of them.
            thread(name = "ShellVideoRecorder-FailureThread", isDaemon = true) {
                val hasContent = stop()
                runCatching { onFailure(hasContent) }.onFailure { AppLogger.w("The video failure callback threw.", it) }
            }
        }
    }

    private fun videoLoop() {
        val codec = checkNotNull(videoCodec)
        val currentWriter = checkNotNull(writer)
        val currentTimeline = checkNotNull(timeline)
        val info = MediaCodec.BufferInfo()

        while (true) {
            val index = codec.dequeueOutputBuffer(info, 100_000L)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> currentWriter.setFormat(Mp4Writer.Track.VIDEO, codec.outputFormat)

                index >= 0 -> {
                    try {
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null && info.size > 0 && !isConfig) {
                            writeVideoSample(currentWriter, currentTimeline, buffer, info)
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }

                // Nothing to read. Once stop() was requested the encoder may never report the end of the stream
                // (some encoders do not when no frame arrives), so we must not wait for it forever.
                stopRequested.get() -> return
            }

            if (!currentWriter.hasVideo && !currentTimeline.isPaused && !stopRequested.get() &&
                nowUs() - startUs > VideoConfig.FIRST_FRAME_TIMEOUT_US
            ) {
                throw IllegalStateException("No video frame was produced ${VideoConfig.FIRST_FRAME_TIMEOUT_US / 1_000_000L} seconds after the start. Is the screen off?")
            }
        }
    }

    private fun writeVideoSample(currentWriter: Mp4Writer, currentTimeline: Mp4Timeline, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        if (needKeyFrame && !isKeyFrame) return

        val ptsUs = currentTimeline.videoPtsUs(info.presentationTimeUs, nowUs())
        if (ptsUs == null) {
            // Dropped (paused or out of order). The frames that follow depend on it, so wait for the next key frame.
            needKeyFrame = true
            if (!currentTimeline.isPaused) requestKeyFrame()
            return
        }

        needKeyFrame = false
        info.presentationTimeUs = ptsUs
        currentWriter.write(Mp4Writer.Track.VIDEO, buffer, info)
    }

    private fun requestKeyFrame() {
        runCatching {
            videoCodec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        }
    }

    private fun audioLoop() {
        val codec = checkNotNull(audioCodec)
        val currentWriter = checkNotNull(writer)
        val currentTimeline = checkNotNull(timeline)
        val info = MediaCodec.BufferInfo()

        // One block of silence right away: it makes the encoder report its format, so the muxer can start even when
        // no call audio arrives (for example when the recording is paused at this moment).
        val silence = ByteBuffer.allocateDirect(audioBlockCapacity)
        val blockDurationUs = AudioConfig.BLOCK_DURATION_MS * 1_000L
        encodeAudio(codec, currentWriter, info, silence, currentTimeline.audioPtsUs(startUs + blockDurationUs, AudioConfig.BLOCK_SAMPLE_COUNT))

        while (running.get() || readyAudioBlocks.isNotEmpty()) {
            val block = readyAudioBlocks.poll(50, TimeUnit.MILLISECONDS) ?: continue
            try {
                encodeAudio(codec, currentWriter, info, block.data, currentTimeline.audioPtsUs(block.arrivalUs, block.frameCount))
            } finally {
                freeAudioBlocks.offer(block)
            }
        }

        // End of stream: flush what the encoder still holds.
        val deadlineUs = nowUs() + 2_000_000L
        var inputIndex = -1
        while (inputIndex < 0 && nowUs() < deadlineUs) {
            inputIndex = codec.dequeueInputBuffer(10_000L)
            drainAudio(codec, currentWriter, info)
        }
        if (inputIndex >= 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            while (nowUs() < deadlineUs && !drainAudio(codec, currentWriter, info, waitUs = 10_000L)) { /* keep draining */ }
        }
    }

    private fun encodeAudio(codec: MediaCodec, currentWriter: Mp4Writer, info: MediaCodec.BufferInfo, pcm: ByteBuffer, ptsUs: Long) {
        // Wait for an input buffer, but never forever: a stuck encoder must not keep this thread alive after stop().
        var inputIndex = -1
        var attempts = 0
        while (inputIndex < 0 && attempts++ < 200) {
            drainAudio(codec, currentWriter, info)
            inputIndex = codec.dequeueInputBuffer(10_000L)
        }
        if (inputIndex < 0) throw IllegalStateException("The AAC encoder did not accept input for 2 seconds.")

        val input = codec.getInputBuffer(inputIndex) ?: throw IllegalStateException("The AAC encoder returned a null input buffer.")
        input.clear()
        val size = minOf(pcm.remaining(), input.remaining())
        val source = pcm.duplicate()
        source.limit(source.position() + size)
        input.put(source)

        codec.queueInputBuffer(inputIndex, 0, size, ptsUs, 0)
        drainAudio(codec, currentWriter, info)
    }

    /**
     * Moves the available encoded audio to the writer.
     *
     * @return true when the end of the stream was reached.
     */
    private fun drainAudio(codec: MediaCodec, currentWriter: Mp4Writer, info: MediaCodec.BufferInfo, waitUs: Long = 0L): Boolean {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, waitUs)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> currentWriter.setFormat(Mp4Writer.Track.AUDIO, codec.outputFormat)

                index >= 0 -> {
                    try {
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null && info.size > 0 && !isConfig) {
                            currentWriter.write(Mp4Writer.Track.AUDIO, buffer, info)
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }

                else -> return false
            }
        }
    }

    // ---- Helpers

    private fun joinWorker(worker: Thread?) {
        if (worker == null || worker === Thread.currentThread()) return
        runCatching { worker.join(VideoConfig.THREAD_JOIN_TIMEOUT_MS) }
        if (worker.isAlive) AppLogger.w("Worker '${worker.name}' did not stop in time.")
    }

    private fun releaseCodec(codec: MediaCodec?, name: String) {
        if (codec == null) return
        runCatching { codec.stop() }
        runCatching { codec.release() }.onFailure { AppLogger.w("Failed to release the $name encoder.", it) }
    }
}
