package com.kitsumed.shizucallrecorder.services.shell.recording.video

import com.kitsumed.shizucallrecorder.services.shell.recording.video.VideoSizeCalculator.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VideoSizeCalculatorTest {

    @Test
    fun portraitDisplayIsScaledToTheMaxLongSide() {
        assertEquals(Size(576, 1280), VideoSizeCalculator.calculate(1080, 2400, 1280, 2))
    }

    @Test
    fun landscapeDisplayIsScaledToTheMaxLongSide() {
        assertEquals(Size(1280, 576), VideoSizeCalculator.calculate(2400, 1080, 1280, 2))
    }

    @Test
    fun resultIsAlignedDownToTheCodecAlignment() {
        // 1440 * 1280 / 3120 = 590, which is not a multiple of 16.
        assertEquals(Size(576, 1280), VideoSizeCalculator.calculate(1440, 3120, 1280, 16))
    }

    @Test
    fun smallDisplayIsNotUpscaled() {
        assertEquals(Size(720, 1280), VideoSizeCalculator.calculate(720, 1280, 1920, 2))
    }

    @Test
    fun oddSizesBecomeEven() {
        assertEquals(Size(1080, 2400), VideoSizeCalculator.calculate(1081, 2401, 5000, 2))
    }

    @Test
    fun alignmentOfOneIsTreatedAsTwo() {
        assertEquals(Size(1080, 2400), VideoSizeCalculator.calculate(1081, 2401, 5000, 1))
    }

    @Test
    fun nonPositiveDisplaySizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { VideoSizeCalculator.calculate(0, 2400, 1280, 2) }
        assertThrows(IllegalArgumentException::class.java) { VideoSizeCalculator.calculate(1080, -1, 1280, 2) }
    }
}
