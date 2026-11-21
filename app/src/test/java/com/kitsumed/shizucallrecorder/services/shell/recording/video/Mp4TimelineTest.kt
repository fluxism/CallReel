package com.kitsumed.shizucallrecorder.services.shell.recording.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4TimelineTest {

    private val start = 50_000_000L
    private fun ms(value: Long) = start + value * 1_000L
    private fun timeline() = Mp4Timeline(startUs = start, audioSampleRate = 16_000)

    /** 20 ms of audio at 16 kHz. */
    private val block = 320

    @Test
    fun videoFrameIsStampedRelativeToTheStart() {
        assertEquals(1_000_000L, timeline().videoPtsUs(rawUs = ms(1_000), nowUs = ms(1_010)))
    }

    @Test
    fun videoFramesAreDroppedWhilePausedAndContinueWithoutAGapAfterResume() {
        val timeline = timeline()
        assertEquals(500_000L, timeline.videoPtsUs(ms(500), ms(500)))

        timeline.pause(ms(1_000))
        assertNull(timeline.videoPtsUs(ms(1_500), ms(1_500)))

        timeline.resume(ms(3_000))
        assertEquals(1_500_000L, timeline.videoPtsUs(ms(3_500), ms(3_500)))
    }

    @Test
    fun aFrameStampedDuringThePauseIsDroppedEvenWhenSeenAfterResume() {
        val timeline = timeline()
        assertEquals(900_000L, timeline.videoPtsUs(ms(900), ms(900)))
        timeline.pause(ms(1_000))
        timeline.resume(ms(3_000))
        assertNull(timeline.videoPtsUs(ms(2_000), ms(3_001)))
    }

    @Test
    fun videoFrameThatIsNotLaterThanThePreviousOneIsDropped() {
        val timeline = timeline()
        assertEquals(100_000L, timeline.videoPtsUs(ms(100), ms(100)))
        assertNull(timeline.videoPtsUs(ms(100), ms(110)))
        assertNull(timeline.videoPtsUs(ms(90), ms(120)))
        assertEquals(133_000L, timeline.videoPtsUs(ms(133), ms(140)))
    }

    @Test
    fun videoTimestampsFromAnotherClockDomainAreCalibratedOnTheFirstFrame() {
        val timeline = timeline()
        val domainOffset = 10_000_000L
        assertEquals(40_000L, timeline.videoPtsUs(ms(40) + domainOffset, ms(40)))
        assertEquals(73_000L, timeline.videoPtsUs(ms(73) + domainOffset, ms(80)))
    }

    @Test
    fun audioBlocksArrivingOnTimeAreContiguous() {
        val timeline = timeline()
        assertEquals(0L, timeline.audioPtsUs(ms(20), block))
        assertEquals(20_000L, timeline.audioPtsUs(ms(40), block))
        assertEquals(40_000L, timeline.audioPtsUs(ms(60), block))
    }

    @Test
    fun audioJitterBelowTheDriftLimitDoesNotCreateGaps() {
        val timeline = timeline()
        assertEquals(0L, timeline.audioPtsUs(ms(20), block))
        assertEquals(20_000L, timeline.audioPtsUs(ms(190), block))
        assertEquals(40_000L, timeline.audioPtsUs(ms(191), block))
    }

    @Test
    fun audioJumpsForwardToTheClockAfterLostBlocksAndNeverGoesBackward() {
        val timeline = timeline()
        assertEquals(0L, timeline.audioPtsUs(ms(20), block))
        assertEquals(20_000L, timeline.audioPtsUs(ms(40), block))

        // One second of blocks was lost.
        val afterGap = timeline.audioPtsUs(ms(1_040), block)
        assertEquals(1_020_000L, afterGap)

        // A burst right after must keep increasing by one block each.
        val burst = timeline.audioPtsUs(ms(1_041), block)
        assertEquals(1_040_000L, burst)
        assertTrue(burst > afterGap)
    }

    @Test
    fun audioStaysContiguousAcrossPauseAndResume() {
        val timeline = timeline()
        assertEquals(0L, timeline.audioPtsUs(ms(20), block))
        assertEquals(20_000L, timeline.audioPtsUs(ms(40), block))

        timeline.pause(ms(40))
        timeline.resume(ms(5_000))

        assertEquals(40_000L, timeline.audioPtsUs(ms(5_020), block))
    }

    @Test
    fun pauseAndResumeAreIdempotent() {
        val timeline = timeline()
        timeline.resume(ms(10))
        timeline.pause(ms(100))
        timeline.pause(ms(150))
        assertTrue(timeline.isPaused)
        timeline.resume(ms(200))
        timeline.resume(ms(900))
        // Only the 100 ms between the first pause and the first resume is excluded.
        assertEquals(900_000L, timeline.videoPtsUs(ms(1_000), ms(1_000)))
    }
}
