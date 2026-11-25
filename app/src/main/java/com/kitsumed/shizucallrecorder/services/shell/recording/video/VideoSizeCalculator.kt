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
 * Computes the size of the encoded video from the size of the display being mirrored.
 */
object VideoSizeCalculator {

    data class Size(val width: Int, val height: Int)

    /**
     * Scales the display size down so its long side fits in [maxLongSide], keeping the aspect ratio.
     * The display is never upscaled. Both sides are aligned down to an even multiple of [alignment],
     * since video encoders reject odd sizes and some require a coarser alignment.
     *
     * @throws IllegalArgumentException if a size or [maxLongSide] is not positive.
     */
    fun calculate(displayWidth: Int, displayHeight: Int, maxLongSide: Int, alignment: Int): Size {
        require(displayWidth > 0 && displayHeight > 0) { "Display size must be positive, got ${displayWidth}x$displayHeight." }
        require(maxLongSide > 0) { "maxLongSide must be positive, got $maxLongSide." }

        val step = evenStep(alignment)
        val longSide = maxOf(displayWidth, displayHeight)

        val width: Int
        val height: Int
        if (longSide <= maxLongSide) {
            width = displayWidth
            height = displayHeight
        } else {
            // Integer math, to avoid float rounding making the result depend on the device.
            width = (displayWidth.toLong() * maxLongSide / longSide).toInt()
            height = (displayHeight.toLong() * maxLongSide / longSide).toInt()
        }

        return Size(alignDown(width, step), alignDown(height, step))
    }

    /** The smallest even step that satisfies [alignment]. */
    private fun evenStep(alignment: Int): Int = when {
        alignment < 2 -> 2
        alignment % 2 != 0 -> alignment * 2
        else -> alignment
    }

    private fun alignDown(value: Int, step: Int): Int = maxOf(step, value - value % step)
}
