/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *  This file was added by the CallReel fork (video call recording).
 */

package com.kitsumed.shizucallrecorder.debug

import android.os.Looper
import android.os.ParcelFileDescriptor
import com.kitsumed.shizucallrecorder.services.shell.recording.AudioConfig
import com.kitsumed.shizucallrecorder.services.shell.recording.video.ShellVideoRecorder
import com.kitsumed.shizucallrecorder.services.shell.utils.ShellWorkarounds
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * Debug-only self-test of the video recording pipeline. It runs the real [ShellVideoRecorder] as the shell user,
 * without the app, Shizuku or a call, and feeds it a generated tone as audio.
 *
 * Usage (from a computer, debug build installed):
 *
 *     adb shell CLASSPATH=<path of base.apk> app_process / com.kitsumed.shizucallrecorder.debug.VideoSelfTest <output.mp4> [seconds] [channels] [pauseTest]
 *
 * It prints "RESULT hasVideo=<true|false>" and exits with 0 when the file contains video.
 */
object VideoSelfTest {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Usage: VideoSelfTest <output.mp4> [seconds=6] [channels=1] [pauseTest=false]")
            exitProcess(2)
        }
        val output = File(args[0])
        val seconds = args.getOrNull(1)?.toIntOrNull() ?: 6
        val channels = args.getOrNull(2)?.toIntOrNull() ?: 1
        val pauseTest = args.getOrNull(3)?.toBoolean() ?: false

        var hasVideo = false
        try {
            // Some framework classes expect a prepared main looper, like in the Shizuku user service process.
            if (Looper.getMainLooper() == null) @Suppress("DEPRECATION") Looper.prepareMainLooper()
            ShellWorkarounds.apply()

            output.delete()
            val descriptor = ParcelFileDescriptor.open(
                output,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            )

            var failed = false
            val recorder = ShellVideoRecorder(descriptor, channels) { hasContent ->
                failed = true
                println("FAILURE callback hasContent=$hasContent")
            }
            recorder.start()
            println("STARTED uid=${android.os.Process.myUid()} seconds=$seconds channels=$channels pauseTest=$pauseTest")

            // A 440 Hz tone in 20 ms blocks, paced like the real capture loop.
            val frames = AudioConfig.BLOCK_SAMPLE_COUNT
            val block = ByteBuffer.allocateDirect(AudioConfig.BLOCK_SIZE_BYTES * channels).order(ByteOrder.nativeOrder())
            val totalBlocks = seconds * 1_000 / AudioConfig.BLOCK_DURATION_MS
            val startNs = System.nanoTime()
            var sampleIndex = 0L
            var paused = false

            for (blockIndex in 0 until totalBlocks) {
                if (failed) break

                // Optional: pause during the middle third, like the user pausing the recording.
                if (pauseTest) {
                    val shouldPause = blockIndex >= totalBlocks / 3 && blockIndex < totalBlocks * 2 / 3
                    if (shouldPause && !paused) { recorder.pause(); paused = true; println("PAUSED at block $blockIndex") }
                    if (!shouldPause && paused) { recorder.resume(); paused = false; println("RESUMED at block $blockIndex") }
                }

                if (!paused) {
                    block.clear()
                    repeat(frames) {
                        val sample = (sin(2.0 * PI * 440.0 * sampleIndex++ / AudioConfig.SAMPLE_RATE) * 8_000).toInt().toShort()
                        repeat(channels) { block.putShort(sample) }
                    }
                    block.flip()
                    recorder.offerAudio(block, frames)
                }

                val nextNs = startNs + (blockIndex + 1L) * AudioConfig.BLOCK_DURATION_MS * 1_000_000L
                val sleepMs = (nextNs - System.nanoTime()) / 1_000_000L
                if (sleepMs > 0) Thread.sleep(sleepMs)
            }

            hasVideo = recorder.stop()
        } catch (t: Throwable) {
            println("ERROR $t")
            t.printStackTrace()
        }

        println("RESULT hasVideo=$hasVideo size=${output.length()}")
        exitProcess(if (hasVideo) 0 else 1)
    }
}
