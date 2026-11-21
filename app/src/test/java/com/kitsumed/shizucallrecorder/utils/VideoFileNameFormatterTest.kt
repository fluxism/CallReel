package com.kitsumed.shizucallrecorder.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFileNameFormatterTest {

    @Test
    fun audioExtensionIsReplaced() {
        assertEquals("20261121_in_+15550100.mp4", VideoFileNameFormatter.fromAudioFileName("20261121_in_+15550100.ogg", ".ogg"))
    }

    @Test
    fun subFoldersAreKept() {
        assertEquals("2026/11/call.mp4", VideoFileNameFormatter.fromAudioFileName("2026/11/call.m4a", ".m4a"))
    }

    @Test
    fun nameWithoutTheAudioExtensionJustGetsTheVideoExtension() {
        assertEquals("call.mp4", VideoFileNameFormatter.fromAudioFileName("call", ".ogg"))
        assertEquals("call.ogg.mp4", VideoFileNameFormatter.fromAudioFileName("call.ogg", ""))
    }

    @Test
    fun onlyTheLastExtensionIsReplaced() {
        assertEquals("a.ogg.mp4", VideoFileNameFormatter.fromAudioFileName("a.ogg.ogg", ".ogg"))
    }
}
